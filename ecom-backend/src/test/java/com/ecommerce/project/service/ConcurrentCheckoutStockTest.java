package com.ecommerce.project.service;

import com.ecommerce.project.config.TestcontainersConfiguration;
import com.ecommerce.project.exception.APIException;
import com.ecommerce.project.model.Product;
import com.ecommerce.project.payload.AddressDTO;
import com.ecommerce.project.payload.CartItemDTO;
import com.ecommerce.project.payload.GuestCheckoutRequestDTO;
import com.ecommerce.project.payload.OrderDTO;
import com.ecommerce.project.repository.OrderRepository;
import com.ecommerce.project.repository.ProductRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The correctness layer under load: two guest checkouts race for the one
 * remaining unit. The ledger's conditional
 * {@code WHERE quantity + :delta >= 0} must let exactly one through — never
 * both, never neither.
 * <p>
 * Not {@code @Transactional} (the race needs real commits), so the committed
 * order and product are removed in {@link #cleanUp()} — otherwise other
 * integration tests that count orders would see the leftover.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class ConcurrentCheckoutStockTest {

    @Autowired private OrderService orderService;
    @Autowired private ProductRepository productRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager txManager;

    /** Unique per run so cleanup only touches this test's rows. */
    private final String raceTag = "racer-" + UUID.randomUUID() + "-";
    private Long createdProductId;

    @AfterEach
    void cleanUp() {
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            entityManager.createQuery(
                    "delete from OrderItem oi where oi.order.id in " +
                    "(select o.id from Order o where o.email like :tag)")
                    .setParameter("tag", raceTag + "%").executeUpdate();
            // Each placed order now carries a fiscal invoice (FK to orders.id).
            entityManager.createQuery(
                    "delete from Invoice inv where inv.order.id in " +
                    "(select o.id from Order o where o.email like :tag)")
                    .setParameter("tag", raceTag + "%").executeUpdate();
            List<Long> paymentIds = entityManager.createQuery(
                    "select o.payment.paymentId from Order o where o.email like :tag and o.payment is not null", Long.class)
                    .setParameter("tag", raceTag + "%").getResultList();
            entityManager.createQuery("delete from Order o where o.email like :tag")
                    .setParameter("tag", raceTag + "%").executeUpdate();
            if (!paymentIds.isEmpty()) {
                entityManager.createQuery("delete from Payment p where p.paymentId in :ids")
                        .setParameter("ids", paymentIds).executeUpdate();
            }
            if (createdProductId != null) {
                entityManager.createQuery("delete from Product p where p.productId = :id")
                        .setParameter("id", createdProductId).executeUpdate();
            }
        });
    }

    @Test
    @DisplayName("two checkouts for the last unit: exactly one order is placed")
    void lastUnitGoesToExactlyOneCheckout() throws Exception {
        Product product = new Product();
        product.setProductName("Last Unit " + System.nanoTime());
        product.setDescription("Single unit in stock");
        product.setQuantity(1);
        product.setSpecialPrice(10.0);
        createdProductId = productRepository.saveAndFlush(product).getProductId();

        long ordersBefore = orderRepository.count();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch bothReady = new CountDownLatch(2);
        CountDownLatch fire = new CountDownLatch(1);

        Callable<Object> checkout = () -> {
            String email = raceTag + Thread.currentThread().getId() + "@example.com";
            bothReady.countDown();
            fire.await();
            try {
                return orderService.placeGuestOrder(guestRequest(email, createdProductId));
            } catch (RuntimeException e) {
                return e;
            }
        };

        Future<Object> a = pool.submit(checkout);
        Future<Object> b = pool.submit(checkout);
        assertThat(bothReady.await(10, TimeUnit.SECONDS)).isTrue();
        fire.countDown();

        List<Object> results = List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS));
        pool.shutdownNow();

        long placed = results.stream().filter(OrderDTO.class::isInstance).count();
        List<APIException> rejected = results.stream()
                .filter(APIException.class::isInstance).map(APIException.class::cast).toList();

        assertThat(placed).as("exactly one checkout succeeds").isEqualTo(1);
        assertThat(rejected).as("the other is rejected").hasSize(1);
        assertThat(rejected.get(0).getMessage()).contains("Insufficient stock");

        assertThat(productRepository.findById(createdProductId).orElseThrow().getQuantity())
                .as("stock is fully consumed, never negative").isZero();
        assertThat(orderRepository.count())
                .as("only one order row was created").isEqualTo(ordersBefore + 1);
    }

    private GuestCheckoutRequestDTO guestRequest(String email, Long productId) {
        GuestCheckoutRequestDTO request = new GuestCheckoutRequestDTO();
        request.setEmail(email);
        request.setPaymentMethod("CASH");        // blank pgPaymentId -> payment verification is skipped
        request.setPgPaymentId(null);
        request.setCouponCodes(List.of());
        request.setAddress(new AddressDTO(null, "1 Test Street", "Block A1", "Bucuresti", "Bucuresti", "Romania", "010101"));
        request.setItems(List.of(new CartItemDTO(productId, 1)));
        return request;
    }
}
