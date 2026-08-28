package com.ecommerce.project.service;

import com.ecommerce.project.exception.APIException;
import com.ecommerce.project.model.CartItem;
import com.ecommerce.project.model.Product;
import com.ecommerce.project.payload.ReservationResponse;
import com.ecommerce.project.repository.ProductRepository;
import com.ecommerce.project.util.AfterCommitExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("InventoryReservationService tests")
class InventoryReservationServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ProductRepository productRepository;

    @Mock
    @SuppressWarnings("unchecked")
    private HashOperations<String, Object, Object> hashOps;

    @Mock
    @SuppressWarnings("unchecked")
    private ZSetOperations<String, String> zSetOps;

    @Mock
    private AfterCommitExecutor afterCommitExecutor;

    @InjectMocks
    private InventoryReservationService inventoryReservationService;

    private static final String PRODUCT_KEY = "product_reservations:v2:1";
    private static final String CART_KEY = "cart_reservations:v2:1";

    private Product product;
    private CartItem cartItem;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(inventoryReservationService, "reservationTtl", Duration.ofMinutes(10));

        product = new Product();
        product.setProductId(1L);
        product.setProductName("Headphones");
        product.setQuantity(10);

        cartItem = new CartItem();
        cartItem.setProduct(product);
        cartItem.setQuantity(2);

        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);

        // No transaction in these unit tests, so the real executor would run the
        // deferred purge immediately; make the mock do the same.
        doAnswer(inv -> {
            inv.getArgument(0, Runnable.class).run();
            return null;
        }).when(afterCommitExecutor).execute(any());
    }

    @Test
    @DisplayName("reserveCartItems creates reservation for available stock")
    void reserveCartItems_success() {
        long expiresAt = System.currentTimeMillis() + 60000;
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(List.of("1", "test-uuid", "1", "2", String.valueOf(expiresAt)));

        List<ReservationResponse> responses = inventoryReservationService.reserveCartItems(1L, List.of(cartItem));

        assertNotNull(responses);
        assertEquals(1, responses.size());
        assertEquals(1L, responses.get(0).productId());
        assertEquals(2, responses.get(0).quantity());
        assertEquals("test-uuid", responses.get(0).reservationId());
        assertEquals(expiresAt, responses.get(0).expiresAt());
    }

    @Test
    @DisplayName("reserveCartItems throws when over-reserving quantity")
    void reserveCartItems_overReservation_throws() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(List.of("0", "1", "0", "9"));

        APIException ex = assertThrows(APIException.class,
                () -> inventoryReservationService.reserveCartItems(1L, List.of(cartItem)));
        assertTrue(ex.getMessage().contains("Insufficient stock"));
        assertTrue(ex.getMessage().contains("Headphones"));
    }

    // reserveCartItems clearing of previous reservations is now verified by
    // integration tests — the clearing logic lives inside the Lua script and
    // cannot be observed through mocked Redis operations.

    @Test
    @DisplayName("consumeReservationsForCart decrements product quantity")
    void consumeReservationsForCart_success() {
        when(zSetOps.range(CART_KEY, 0, -1)).thenReturn(Set.of("res-1"));
        when(hashOps.entries("reservation:res-1")).thenReturn(Map.of(
                "productId", "1",
                "quantity", "2",
                "cartId", "1",
                "expiresAt", String.valueOf(System.currentTimeMillis() + 60000)
        ));
        // Atomic conditional decrement succeeds (1 row updated).
        when(productRepository.decrementStock(1L, 2)).thenReturn(1);

        inventoryReservationService.consumeReservationsForCart(1L);

        verify(productRepository).decrementStock(1L, 2);
        verify(redisTemplate).delete("reservation:res-1");
        verify(zSetOps).remove(PRODUCT_KEY, "res-1:2");
        verify(redisTemplate).delete(CART_KEY);
    }

    @Test
    @DisplayName("consumeReservationsForCart does not read the product on the happy path")
    void consumeReservationsForCart_happyPath_skipsProductLookup() {
        when(zSetOps.range(CART_KEY, 0, -1)).thenReturn(Set.of("res-1"));
        when(hashOps.entries("reservation:res-1")).thenReturn(Map.of(
                "productId", "1", "quantity", "2", "cartId", "1"));
        when(productRepository.decrementStock(1L, 2)).thenReturn(1);

        inventoryReservationService.consumeReservationsForCart(1L);

        // Checkout must not pay for a SELECT per item just to build an unused message.
        verify(productRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("consumeReservationsForCart throws when no active reservation")
    void consumeReservationsForCart_noActive_throws() {
        when(zSetOps.range(CART_KEY, 0, -1)).thenReturn(Collections.emptySet());

        APIException ex = assertThrows(APIException.class,
                () -> inventoryReservationService.consumeReservationsForCart(1L));
        assertTrue(ex.getMessage().contains("No active stock reservation"));
    }

    @Test
    @DisplayName("consumeReservationsForCart throws when insufficient stock to fulfill")
    void consumeReservationsForCart_insufficientStock_throws() {
        product.setQuantity(1);
        when(zSetOps.range(CART_KEY, 0, -1)).thenReturn(Set.of("res-1"));
        when(hashOps.entries("reservation:res-1")).thenReturn(Map.of(
                "productId", "1",
                "quantity", "2",
                "cartId", "1",
                "expiresAt", String.valueOf(System.currentTimeMillis() + 60000)
        ));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        // Atomic conditional decrement matched no rows: stock was taken concurrently.
        when(productRepository.decrementStock(1L, 2)).thenReturn(0);

        APIException ex = assertThrows(APIException.class,
                () -> inventoryReservationService.consumeReservationsForCart(1L));
        assertTrue(ex.getMessage().contains("Reservation could not be fulfilled"));
        assertTrue(ex.getMessage().contains("Headphones"));
    }

    @Test
    @DisplayName("getActiveReservationsForCart filters out expired reservations")
    void getActiveReservationsForCart_expiredSkipped() {
        when(zSetOps.range(CART_KEY, 0, -1)).thenReturn(Set.of("res-1"));
        when(hashOps.entries("reservation:res-1")).thenReturn(Collections.emptyMap());

        List<ReservationResponse> active = inventoryReservationService.getActiveReservationsForCart(1L);

        assertTrue(active.isEmpty());
    }

    @Test
    @DisplayName("getActiveReservationsForCart returns valid reservations")
    void getActiveReservationsForCart_success() {
        long expiresAt = System.currentTimeMillis() + 60000;
        when(zSetOps.range(CART_KEY, 0, -1)).thenReturn(Set.of("res-1"));
        when(hashOps.entries("reservation:res-1")).thenReturn(Map.of(
                "productId", "1",
                "quantity", "2",
                "cartId", "1",
                "createdAt", String.valueOf(System.currentTimeMillis()),
                "expiresAt", String.valueOf(expiresAt)
        ));

        List<ReservationResponse> active = inventoryReservationService.getActiveReservationsForCart(1L);

        assertEquals(1, active.size());
        assertEquals(1L, active.get(0).productId());
        assertEquals(2, active.get(0).quantity());
        assertEquals(expiresAt, active.get(0).expiresAt());
    }

    @Test
    @DisplayName("releaseReservationsForCart removes reservations and their references")
    void releaseReservationsForCart_success() {
        when(zSetOps.range(CART_KEY, 0, -1)).thenReturn(Set.of("res-1"));
        when(hashOps.entries("reservation:res-1")).thenReturn(Map.of("productId", "1", "quantity", "4"));

        inventoryReservationService.releaseReservationsForCart(1L);

        verify(zSetOps).remove(PRODUCT_KEY, "res-1:4");
        verify(redisTemplate).delete("reservation:res-1");
        verify(redisTemplate).delete(CART_KEY);
    }

    @Test
    @DisplayName("getReservedForProduct sums the live reservations")
    void getReservedForProduct_success() {
        when(zSetOps.range(PRODUCT_KEY, 0, -1)).thenReturn(Set.of("res-1:3", "res-2:4"));

        int reserved = inventoryReservationService.getReservedForProduct(1L);

        assertEquals(7, reserved);
    }

    @Test
    @DisplayName("getReservedForProduct prunes expired reservations before counting")
    void getReservedForProduct_prunesExpiredFirst() {
        when(zSetOps.range(PRODUCT_KEY, 0, -1)).thenReturn(Set.of("res-1:3"));

        inventoryReservationService.getReservedForProduct(1L);

        // Pruning by score is what makes an expired reservation stop counting.
        // Without it the total is only ever additive, which is how the old
        // reserved_count counter turned abandoned carts into phantom stock.
        verify(zSetOps).removeRangeByScore(eq(PRODUCT_KEY), eq(Double.NEGATIVE_INFINITY), anyDouble());
    }

    @Test
    @DisplayName("getReservedForProduct never consults a denormalized counter")
    void getReservedForProduct_hasNoCounter() {
        when(zSetOps.range(PRODUCT_KEY, 0, -1)).thenReturn(Set.of("res-1:3"));

        inventoryReservationService.getReservedForProduct(1L);

        // The reserved total must have exactly one representation. A cached
        // counter alongside it is what drifted and made products unbuyable.
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    @DisplayName("reserveCartItems ignores items with null or zero quantity")
    void reserveCartItems_zeroQuantity_ignored() {
        cartItem.setQuantity(0);

        List<ReservationResponse> responses = inventoryReservationService.reserveCartItems(1L, List.of(cartItem));

        assertTrue(responses.isEmpty());
        verify(redisTemplate, never()).execute(any(DefaultRedisScript.class), anyList(), any(Object[].class));
    }
}
