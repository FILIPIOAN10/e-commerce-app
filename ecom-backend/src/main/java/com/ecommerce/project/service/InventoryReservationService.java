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

/**
 * Temporary stock reservations held while a customer is in checkout.
 * <p>
 * The reserved total per product is <em>always derived</em> from the live
 * reservations, never stored as a separate counter. An earlier version kept a
 * {@code reserved_count:{productId}} counter that was only decremented on
 * explicit consume/release, so reservations that lapsed through their Redis TTL
 * left the counter permanently inflated. Every abandoned cart leaked stock and
 * popular products eventually became unbuyable while physically in stock.
 * <p>
 * Reservations now live in a sorted set scored by expiry timestamp. Pruning by
 * score before every read makes expiry self-correcting by construction: there
 * is no second representation of the total that can drift away from the first.
 */
@Service
@RequiredArgsConstructor
public class InventoryReservationService {

    private final StringRedisTemplate redisTemplate;
    private final ProductRepository productRepository;

    @Value("${inventory.reservation.ttl:10m}")
    private Duration reservationTtl;

    private static final String RESERVATION_KEY_PREFIX = "reservation:";

    // v2 prefixes: these keys changed type from SET to ZSET, and reusing the old
    // names would make Redis reject every command with WRONGTYPE until the stale
    // keys expired. The v1 keys had no TTL, so they must be dropped manually:
    //   redis-cli --scan --pattern 'product_reservations:*' | grep -v ':v2:' | xargs redis-cli DEL
    private static final String PRODUCT_RESERVATIONS_PREFIX = "product_reservations:v2:";
    private static final String CART_RESERVATIONS_PREFIX = "cart_reservations:v2:";

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

            responses.add(createReservation(cartId, productId, requested));
        }

        return responses;
    }

    private ReservationResponse createReservation(Long cartId, Long productId, int quantity) {
        String reservationId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        long expiresAt = now + reservationTtl.toMillis();

        Map<String, String> fields = new HashMap<>();
        fields.put("productId", String.valueOf(productId));
        fields.put("quantity", String.valueOf(quantity));
        fields.put("cartId", String.valueOf(cartId));
        fields.put("createdAt", String.valueOf(now));
        fields.put("expiresAt", String.valueOf(expiresAt));

        String reservationKey = RESERVATION_KEY_PREFIX + reservationId;
        redisTemplate.opsForHash().putAll(reservationKey, fields);
        redisTemplate.expire(reservationKey, reservationTtl);

        // The quantity is encoded into the sorted set member so the reserved total
        // can be summed from a single range read, without a round trip per
        // reservation to fetch its hash.
        String productKey = PRODUCT_RESERVATIONS_PREFIX + productId;
        redisTemplate.opsForZSet().add(productKey, member(reservationId, quantity), expiresAt);

        String cartKey = CART_RESERVATIONS_PREFIX + cartId;
        redisTemplate.opsForZSet().add(cartKey, reservationId, expiresAt);

        // The index keys carry a TTL of their own so an index can never outlive the
        // reservations it points at. The v1 SET keys had no expiry and leaked forever.
        redisTemplate.expire(productKey, reservationTtl);
        redisTemplate.expire(cartKey, reservationTtl);

        return new ReservationResponse(reservationId, productId, quantity, expiresAt);
    }

    public void consumeReservationsForCart(Long cartId) {
        if (cartId == null) {
            return;
        }

        String cartReservationsKey = CART_RESERVATIONS_PREFIX + cartId;
        Set<String> reservationIds = liveMembers(cartReservationsKey);

        if (reservationIds.isEmpty()) {
            throw new APIException("No active stock reservation for this cart. Please start checkout again.");
        }

        for (String reservationId : reservationIds) {
            String reservationKey = RESERVATION_KEY_PREFIX + reservationId;
            Map<Object, Object> fields = redisTemplate.opsForHash().entries(reservationKey);
            if (fields.isEmpty()) {
                // Lapsed between the prune above and this read; the sorted sets
                // drop it on their next score-based prune.
                continue;
            }

            Long productId = Long.valueOf(String.valueOf(fields.get("productId")));
            int quantity = Integer.parseInt(String.valueOf(fields.get("quantity")));

            // Atomic conditional decrement. A result of 0 means the stock was taken
            // by a concurrent request between the reservation and this consumption,
            // so the reservation cannot be fulfilled.
            if (productRepository.decrementStock(productId, quantity) == 0) {
                throw new APIException("Insufficient stock for " + productName(productId)
                        + ". Reservation could not be fulfilled.");
            }

            redisTemplate.delete(reservationKey);
            redisTemplate.opsForZSet().remove(PRODUCT_RESERVATIONS_PREFIX + productId,
                    member(reservationId, quantity));
        }

        redisTemplate.delete(cartReservationsKey);
    }

    /**
     * Only looked up on the failure path, so the happy path of checkout does not
     * pay for a SELECT per cart item just to build a message it never uses.
     */
    private String productName(Long productId) {
        return productRepository.findById(productId)
                .map(Product::getProductName)
                .orElse("product " + productId);
    }

    public void releaseReservationsForCart(Long cartId) {
        if (cartId == null) {
            return;
        }

        String cartReservationsKey = CART_RESERVATIONS_PREFIX + cartId;
        Set<String> reservationIds = redisTemplate.opsForZSet().range(cartReservationsKey, 0, -1);

        if (reservationIds == null) {
            return;
        }

        for (String reservationId : reservationIds) {
            String reservationKey = RESERVATION_KEY_PREFIX + reservationId;
            Map<Object, Object> fields = redisTemplate.opsForHash().entries(reservationKey);
            Object productIdObj = fields.get("productId");
            Object quantityObj = fields.get("quantity");
            if (productIdObj != null && quantityObj != null) {
                redisTemplate.opsForZSet().remove(PRODUCT_RESERVATIONS_PREFIX + productIdObj,
                        member(reservationId, Integer.parseInt(String.valueOf(quantityObj))));
            }
            // A reservation whose hash already lapsed needs no explicit cleanup:
            // its sorted set entry is dropped by the next score-based prune.
            redisTemplate.delete(reservationKey);
        }

        redisTemplate.delete(cartReservationsKey);
    }

    /**
     * Total quantity currently reserved for a product.
     * <p>
     * Computed from the live reservations on every call. Expired entries are
     * pruned by score first, so a reservation that lapsed through its TTL stops
     * counting the moment it expires rather than being held forever by a counter
     * nobody decremented. Costs two round trips regardless of how many
     * reservations exist, because the quantity is encoded in the member.
     */
    public int getReservedForProduct(Long productId) {
        if (productId == null) {
            return 0;
        }

        int total = 0;
        for (String member : liveMembers(PRODUCT_RESERVATIONS_PREFIX + productId)) {
            total += quantityOf(member);
        }
        return total;
    }

    /**
     * Drops everything already expired, then returns what is left. This single
     * step is what keeps the reserved total honest.
     */
    private Set<String> liveMembers(String zsetKey) {
        redisTemplate.opsForZSet().removeRangeByScore(
                zsetKey, Double.NEGATIVE_INFINITY, System.currentTimeMillis());
        Set<String> members = redisTemplate.opsForZSet().range(zsetKey, 0, -1);
        return members == null ? Collections.emptySet() : members;
    }

    /** Sorted set member format: {@code <reservationId>:<quantity>}. */
    private String member(String reservationId, int quantity) {
        return reservationId + ":" + quantity;
    }

    private int quantityOf(String member) {
        int separator = member.lastIndexOf(':');
        if (separator < 0) {
            return 0;
        }
        try {
            return Integer.parseInt(member.substring(separator + 1));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public List<ReservationResponse> getActiveReservationsForCart(Long cartId) {
        if (cartId == null) {
            return Collections.emptyList();
        }

        return liveMembers(CART_RESERVATIONS_PREFIX + cartId).stream()
                .map(id -> {
                    Map<Object, Object> fields = redisTemplate.opsForHash()
                            .entries(RESERVATION_KEY_PREFIX + id);
                    if (fields.isEmpty()) {
                        return null;
                    }
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
