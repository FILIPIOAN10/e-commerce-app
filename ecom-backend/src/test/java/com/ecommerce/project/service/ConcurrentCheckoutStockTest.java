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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The correctness layer under load: two guest checkouts race for the one
 * remaining unit. {@code decrementStock}'s conditional {@code WHERE quantity >= :qty}
 * must let exactly one through — never both, never neither.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class ConcurrentCheckoutStockTest {

    @Autowired private OrderService orderService;
    @Autowired private ProductRepository productRepository;
    @Autowired private OrderRepository orderRepository;

    @Test
    @DisplayName("two checkouts for the last unit: exactly one order is placed")
    void lastUnitGoesToExactlyOneCheckout() throws Exception {
        Product product = new Product();
        product.setProductName("Last Unit " + System.nanoTime());
        product.setDescription("Single unit in stock");
        product.setQuantity(1);
        product.setSpecialPrice(10.0);
        Long productId = productRepository.saveAndFlush(product).getProductId();

        long ordersBefore = orderRepository.count();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch bothReady = new CountDownLatch(2);
        CountDownLatch fire = new CountDownLatch(1);

        Callable<Object> checkout = () -> {
            String email = "racer-" + Thread.currentThread().getId() + "@example.com";
            bothReady.countDown();
            fire.await();
            try {
                return orderService.placeGuestOrder(guestRequest(email, productId));
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

        assertThat(productRepository.findById(productId).orElseThrow().getQuantity())
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
