package com.ecommerce.project.service;

import com.ecommerce.project.exception.APIException;
import com.ecommerce.project.model.CartItem;
import com.ecommerce.project.model.Product;
import com.ecommerce.project.payload.ReservationResponse;
import com.ecommerce.project.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InventoryReservationService {

    private final StringRedisTemplate redisTemplate;
    private final ProductRepository productRepository;

    @Value("${inventory.reservation.ttl-minutes:10}")
    private long reservationTtlMinutes;

    private static final String RESERVATION_KEY_PREFIX = "reservation:";
    private static final String PRODUCT_RESERVATIONS_PREFIX = "product_reservations:";
    private static final String CART_RESERVATIONS_PREFIX = "cart_reservations:";

    public List<ReservationResponse> reserveCartItems(Long cartId, List<CartItem> items) {
        if (cartId == null || items == null || items.isEmpty()) {
            return Collections.emptyList();
        }

        // Release any previous reservations for this cart before re-reserving
        releaseReservationsForCart(cartId);

        List<ReservationResponse> responses = new ArrayList<>();

        for (CartItem item : items) {
            Product product = item.getProduct();
            if (product == null) {
                continue;
            }
            Long productId = product.getProductId();
            Integer requested = item.getQuantity();

            if (requested == null || requested <= 0) {
                continue;
            }

            int reserved = getReservedForProduct(productId);
            int available = product.getQuantity() - reserved;

            if (available < requested) {
                throw new APIException("Insufficient stock for " + product.getProductName()
                        + ". Available: " + available
                        + ", reserved: " + reserved
                        + ", requested: " + requested);
            }

            String reservationId = UUID.randomUUID().toString();
            long now = System.currentTimeMillis();
            long ttlMs = reservationTtlMinutes * 60 * 1000;
            long expiresAt = now + ttlMs;

            Map<String, String> fields = new HashMap<>();
            fields.put("productId", String.valueOf(productId));
            fields.put("quantity", String.valueOf(requested));
            fields.put("cartId", String.valueOf(cartId));
            fields.put("createdAt", String.valueOf(now));
            fields.put("expiresAt", String.valueOf(expiresAt));

            String reservationKey = RESERVATION_KEY_PREFIX + reservationId;
            redisTemplate.opsForHash().putAll(reservationKey, fields);
            redisTemplate.expire(reservationKey, Duration.ofMillis(ttlMs));

            redisTemplate.opsForSet().add(PRODUCT_RESERVATIONS_PREFIX + productId, reservationId);
            redisTemplate.opsForSet().add(CART_RESERVATIONS_PREFIX + cartId, reservationId);

            responses.add(new ReservationResponse(reservationId, productId, requested, expiresAt));
        }

        return responses;
    }

    public void consumeReservationsForCart(Long cartId) {
        if (cartId == null) {
            return;
        }

        String cartReservationsKey = CART_RESERVATIONS_PREFIX + cartId;
        Set<String> reservationIds = redisTemplate.opsForSet().members(cartReservationsKey);

        if (reservationIds == null || reservationIds.isEmpty()) {
            throw new APIException("No active stock reservation for this cart. Please start checkout again.");
        }

        for (String reservationId : reservationIds) {
            String reservationKey = RESERVATION_KEY_PREFIX + reservationId;
            if (!Boolean.TRUE.equals(redisTemplate.hasKey(reservationKey))) {
                // Expired reservation; clean stale references
                redisTemplate.opsForSet().remove(cartReservationsKey, reservationId);
                continue;
            }

            Map<Object, Object> fields = redisTemplate.opsForHash().entries(reservationKey);
            Long productId = Long.valueOf(String.valueOf(fields.get("productId")));
            Integer quantity = Integer.valueOf(String.valueOf(fields.get("quantity")));

            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new APIException("Product not found for reservation: " + productId));

            if (product.getQuantity() < quantity) {
                throw new APIException("Insufficient stock for " + product.getProductName()
                        + ". Reservation could not be fulfilled.");
            }

            product.setQuantity(product.getQuantity() - quantity);
            productRepository.save(product);

            redisTemplate.delete(reservationKey);
            redisTemplate.opsForSet().remove(PRODUCT_RESERVATIONS_PREFIX + productId, reservationId);
        }

        redisTemplate.delete(cartReservationsKey);
    }

    public void releaseReservationsForCart(Long cartId) {
        if (cartId == null) {
            return;
        }

        String cartReservationsKey = CART_RESERVATIONS_PREFIX + cartId;
        Set<String> reservationIds = redisTemplate.opsForSet().members(cartReservationsKey);

        if (reservationIds == null) {
            return;
        }

        for (String reservationId : reservationIds) {
            String reservationKey = RESERVATION_KEY_PREFIX + reservationId;
            Map<Object, Object> fields = redisTemplate.opsForHash().entries(reservationKey);
            Object productIdObj = fields.get("productId");
            if (productIdObj != null) {
                Long productId = Long.valueOf(String.valueOf(productIdObj));
                redisTemplate.opsForSet().remove(PRODUCT_RESERVATIONS_PREFIX + productId, reservationId);
            }
            redisTemplate.delete(reservationKey);
        }

        redisTemplate.delete(cartReservationsKey);
    }

    public int getReservedForProduct(Long productId) {
        if (productId == null) {
            return 0;
        }

        String productReservationsKey = PRODUCT_RESERVATIONS_PREFIX + productId;
        Set<String> reservationIds = redisTemplate.opsForSet().members(productReservationsKey);

        if (reservationIds == null || reservationIds.isEmpty()) {
            return 0;
        }

        int total = 0;
        for (String reservationId : reservationIds) {
            String reservationKey = RESERVATION_KEY_PREFIX + reservationId;
            if (!Boolean.TRUE.equals(redisTemplate.hasKey(reservationKey))) {
                redisTemplate.opsForSet().remove(productReservationsKey, reservationId);
                continue;
            }
            Object quantity = redisTemplate.opsForHash().get(reservationKey, "quantity");
            if (quantity != null) {
                total += Integer.parseInt(String.valueOf(quantity));
            }
        }

        return total;
    }

    public List<ReservationResponse> getActiveReservationsForCart(Long cartId) {
        if (cartId == null) {
            return Collections.emptyList();
        }

        String cartReservationsKey = CART_RESERVATIONS_PREFIX + cartId;
        Set<String> reservationIds = redisTemplate.opsForSet().members(cartReservationsKey);

        if (reservationIds == null || reservationIds.isEmpty()) {
            return Collections.emptyList();
        }

        return reservationIds.stream()
                .map(id -> {
                    String reservationKey = RESERVATION_KEY_PREFIX + id;
                    if (!Boolean.TRUE.equals(redisTemplate.hasKey(reservationKey))) {
                        redisTemplate.opsForSet().remove(cartReservationsKey, id);
                        return null;
                    }
                    Map<Object, Object> fields = redisTemplate.opsForHash().entries(reservationKey);
                    return new ReservationResponse(
                            id,
                            Long.valueOf(String.valueOf(fields.get("productId"))),
                            Integer.valueOf(String.valueOf(fields.get("quantity"))),
                            Long.valueOf(String.valueOf(fields.get("expiresAt")))
                    );
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}
