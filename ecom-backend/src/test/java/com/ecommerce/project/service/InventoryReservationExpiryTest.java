package com.ecommerce.project.service;

import com.ecommerce.project.model.CartItem;
import com.ecommerce.project.model.Product;
import com.ecommerce.project.payload.ReservationResponse;
import com.ecommerce.project.repository.ProductRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;

/**
 * Expiry behaviour of stock reservations, exercised against a real Redis.
 * <p>
 * These are the cases the mock-based tests structurally cannot cover: the bug
 * they guard against only appears once a reservation lapses through its TTL,
 * and a mocked {@code RedisTemplate} has no clock and no expiry semantics.
 * <p>
 * Against the previous implementation both tests fail. It kept a
 * {@code reserved_count:{productId}} counter that was decremented only on an
 * explicit consume or release. A reservation that simply timed out left the
 * counter untouched, and the reconciliation step only corrected the counter
 * when it was <em>below</em> the live total, never above it. So the reserved
 * amount grew monotonically and stock became permanently unavailable.
 */
@Testcontainers
@DisplayName("Inventory reservation expiry")
class InventoryReservationExpiryTest {

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);

    private static final Duration TTL = Duration.ofSeconds(2);

    private StringRedisTemplate redisTemplate;
    private InventoryReservationService reservationService;

    @BeforeAll
    static void requireDocker() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker is required to exercise real Redis expiry");
    }

    @BeforeEach
    void setUp() {
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getFirstMappedPort()));
        connectionFactory.afterPropertiesSet();

        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();

        ProductRepository productRepository = mock(ProductRepository.class);
        reservationService = new InventoryReservationService(redisTemplate, productRepository);
        ReflectionTestUtils.setField(reservationService, "reservationTtl", TTL);
    }

    private CartItem cartItemFor(long productId, int stock, int quantity) {
        Product product = new Product();
        product.setProductId(productId);
        product.setProductName("Headphones");
        product.setQuantity(stock);

        CartItem item = new CartItem();
        item.setProduct(product);
        item.setQuantity(quantity);
        return item;
    }

    @Test
    @DisplayName("a lapsed reservation stops counting against stock")
    void expiredReservationReleasesStock() throws InterruptedException {
        reservationService.reserveCartItems(1L, List.of(cartItemFor(1L, 10, 4)));
        assertEquals(4, reservationService.getReservedForProduct(1L),
                "reservation should count while it is live");

        Thread.sleep(TTL.toMillis() + 500);

        assertEquals(0, reservationService.getReservedForProduct(1L),
                "an expired reservation must stop counting; previously the counter kept it forever");
    }

    @Test
    @DisplayName("abandoned carts do not accumulate into phantom stock")
    void abandonedCartsDoNotExhaustInventory() throws InterruptedException {
        // Three shoppers reach checkout on the same product and all walk away.
        reservationService.reserveCartItems(1L, List.of(cartItemFor(1L, 10, 3)));
        reservationService.reserveCartItems(2L, List.of(cartItemFor(1L, 10, 3)));
        reservationService.reserveCartItems(3L, List.of(cartItemFor(1L, 10, 3)));

        assertEquals(9, reservationService.getReservedForProduct(1L));

        Thread.sleep(TTL.toMillis() + 500);

        // Under the old counter this stayed at 9, leaving 1 of 10 units sellable.
        // Repeat the cycle a few times and the product became unbuyable outright.
        assertEquals(0, reservationService.getReservedForProduct(1L));

        // A fourth shopper can now take the full stock, proving nothing was lost.
        assertFalse(reservationService.reserveCartItems(4L, List.of(cartItemFor(1L, 10, 10))).isEmpty());
        assertEquals(10, reservationService.getReservedForProduct(1L));
    }

    @Test
    @DisplayName("no denormalized counter key is written")
    void noCounterKeyExists() {
        reservationService.reserveCartItems(1L, List.of(cartItemFor(1L, 10, 4)));

        Set<String> counters = redisTemplate.keys("reserved_count:*");
        assertTrue(counters == null || counters.isEmpty(),
                "the reserved total must have a single representation, not a cached copy that can drift");
    }

    @Test
    @DisplayName("index keys expire so they cannot leak")
    void indexKeysCarryTtl() {
        reservationService.reserveCartItems(1L, List.of(cartItemFor(1L, 10, 4)));

        Long productIndexTtl = redisTemplate.getExpire("product_reservations:v2:1");
        Long cartIndexTtl = redisTemplate.getExpire("cart_reservations:v2:1");

        // The v1 SET indexes were never given an expiry and grew without bound.
        assertNotNull(productIndexTtl);
        assertNotNull(cartIndexTtl);
        assertTrue(productIndexTtl > 0, "product index must expire, was " + productIndexTtl);
        assertTrue(cartIndexTtl > 0, "cart index must expire, was " + cartIndexTtl);
    }

    @Test
    @DisplayName("releasing a cart frees the reservation immediately")
    void releaseFreesStockRightAway() {
        reservationService.reserveCartItems(1L, List.of(cartItemFor(1L, 10, 6)));
        assertEquals(6, reservationService.getReservedForProduct(1L));

        reservationService.releaseReservationsForCart(1L);

        assertEquals(0, reservationService.getReservedForProduct(1L));
    }

    @Test
    @DisplayName("re-reserving the same cart replaces rather than stacks")
    void reReservingSameCartDoesNotDoubleCount() {
        reservationService.reserveCartItems(1L, List.of(cartItemFor(1L, 10, 3)));
        reservationService.reserveCartItems(1L, List.of(cartItemFor(1L, 10, 5)));

        assertEquals(5, reservationService.getReservedForProduct(1L),
                "the second reservation replaces the first, it does not add to it");
    }

    @Test
    @DisplayName("concurrent reservations do not over-sell stock")
    void concurrentReservationsDoNotOversell() throws InterruptedException {
        int stock = 5;
        int requestPerThread = 3;
        int threadCount = 10;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(threadCount);
        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger failures = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final long cartId = i + 1;
            executor.submit(() -> {
                try {
                    startGate.await();
                    List<ReservationResponse> result = reservationService.reserveCartItems(
                            cartId, List.of(cartItemFor(1L, stock, requestPerThread)));
                    if (!result.isEmpty()) {
                        successes.incrementAndGet();
                    } else {
                        failures.incrementAndGet();
                    }
                } catch (Exception e) {
                    failures.incrementAndGet();
                } finally {
                    endGate.countDown();
                }
            });
        }

        startGate.countDown();
        assertTrue(endGate.await(10, TimeUnit.SECONDS), "all threads should finish within 10s");
        executor.shutdown();

        // With stock=5 and each thread requesting 3, only 1 thread can succeed
        // (3 <= 5, but 6 > 5). Without the atomic Lua script, multiple threads
        // could read the same reserved total (0), all pass the check, and all
        // create reservations — over-selling to 30 units.
        assertEquals(1, successes.get(), "exactly one reservation should succeed");
        assertEquals(threadCount - 1, failures.get(), "all others should be rejected");
        assertEquals(requestPerThread, reservationService.getReservedForProduct(1L),
                "total reserved must not exceed what was actually available");
    }
}
