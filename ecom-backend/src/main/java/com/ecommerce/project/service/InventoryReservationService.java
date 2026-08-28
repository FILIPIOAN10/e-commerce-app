package com.ecommerce.project.service;

import com.ecommerce.project.exception.APIException;
import com.ecommerce.project.model.CartItem;
import com.ecommerce.project.model.Product;
import com.ecommerce.project.payload.ReservationResponse;
import com.ecommerce.project.repository.ProductRepository;
import com.ecommerce.project.util.AfterCommitExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
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
 * <p>
 * The entire reserve operation — releasing old cart reservations, pruning
 * expired entries, checking availability, and creating new reservations — is
 * executed as a single atomic Lua script. Without this, the check-then-act gap
 * between reading the reserved total and writing the new reservation allowed
 * concurrent requests to over-sell the same stock.
 * <p>
 * <strong>No interface (F14):</strong> this is deliberately a concrete
 * {@code @Service}. There is one implementation, no seam a second provider would
 * plug into, and its unit tests construct it directly with a real
 * {@link com.ecommerce.project.util.AfterCommitExecutor} and a mocked
 * {@code RedisTemplate}. An {@code InventoryReservationService} /
 * {@code ...Impl} pair here would be ceremony without a caller.
 */
@Service
@RequiredArgsConstructor
public class InventoryReservationService {

    private final StringRedisTemplate redisTemplate;
    private final ProductRepository productRepository;
    private final AfterCommitExecutor afterCommitExecutor;

    @Value("${inventory.reservation.ttl:10m}")
    private Duration reservationTtl;

    private static final String RESERVATION_KEY_PREFIX = "reservation:";

    // v2 prefixes: these keys changed type from SET to ZSET, and reusing the old
    // names would make Redis reject every command with WRONGTYPE until the stale
    // keys expired. The v1 keys had no TTL, so they must be dropped manually:
    //   redis-cli --scan --pattern 'product_reservations:*' | grep -v ':v2:' | xargs redis-cli DEL
    private static final String PRODUCT_RESERVATIONS_PREFIX = "product_reservations:v2:";
    private static final String CART_RESERVATIONS_PREFIX = "cart_reservations:v2:";

    /*
     * Lua script that atomically:
     *   1. Releases any previous reservations for the cart
     *   2. For each item: prunes expired entries, sums live reservations,
     *      checks availability, and creates the reservation
     *   3. On failure: rolls back all reservations created so far
     *
     * Without atomicity, two concurrent requests could both read the same
     * reserved total, both see enough available stock, and both create
     * reservations — over-selling the product.
     *
     * Returns:
     *   {"1", uuid1, productId1, qty1, expiresAt1, ...} on success
     *   {"0", failedItemIndex, available, reserved} on failure
     */
    private static final String RESERVE_LUA = """
            local cartKey = KEYS[1]
            local cartId = ARGV[1]
            local ttlMs = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
            local itemCount = tonumber(ARGV[4])
            local expiresAt = now + ttlMs

            -- Release old reservations for this cart
            local oldReservations = redis.call('ZRANGE', cartKey, 0, -1)
            for _, resId in ipairs(oldReservations) do
                local resKey = 'reservation:' .. resId
                local productId = redis.call('HGET', resKey, 'productId')
                local quantity = redis.call('HGET', resKey, 'quantity')
                if productId and quantity then
                    local productKey = 'product_reservations:v2:' .. productId
                    redis.call('ZREM', productKey, resId .. ':' .. quantity)
                end
                redis.call('DEL', resKey)
            end
            redis.call('DEL', cartKey)

            -- Check-and-reserve each item
            local created = {}
            for i = 1, itemCount do
                local baseIdx = 4 + (i - 1) * 4
                local productId = ARGV[baseIdx + 1]
                local requested = tonumber(ARGV[baseIdx + 2])
                local stock = tonumber(ARGV[baseIdx + 3])
                local uuid = ARGV[baseIdx + 4]

                local productKey = 'product_reservations:v2:' .. productId

                -- Prune expired entries
                redis.call('ZREMRANGEBYSCORE', productKey, '-inf', now)

                -- Sum live reservations from member-encoded quantities
                local members = redis.call('ZRANGE', productKey, 0, -1)
                local reserved = 0
                for _, m in ipairs(members) do
                    local sepIdx = string.find(m, ':', 1, true)
                    if sepIdx then
                        reserved = reserved + tonumber(string.sub(m, sepIdx + 1))
                    end
                end

                local available = stock - reserved
                if available < requested then
                    -- Roll back all reservations created so far
                    for j = 1, #created do
                        local c = created[j]
                        local rbProductKey = 'product_reservations:v2:' .. c.productId
                        redis.call('ZREM', rbProductKey, c.uuid .. ':' .. tostring(c.qty))
                        redis.call('DEL', 'reservation:' .. c.uuid)
                    end
                    redis.call('DEL', cartKey)
                    return {tostring(0), tostring(i), tostring(available), tostring(reserved)}
                end

                -- Create reservation hash
                local resKey = 'reservation:' .. uuid
                redis.call('HSET', resKey, 'productId', productId, 'quantity', tostring(requested),
                        'cartId', cartId, 'createdAt', tostring(now), 'expiresAt', tostring(expiresAt))
                redis.call('PEXPIRE', resKey, ttlMs)

                -- Add to product ZSET with expiry score; quantity encoded in member
                local member = uuid .. ':' .. tostring(requested)
                redis.call('ZADD', productKey, expiresAt, member)
                redis.call('PEXPIRE', productKey, ttlMs)

                -- Add to cart ZSET
                redis.call('ZADD', cartKey, expiresAt, uuid)
                redis.call('PEXPIRE', cartKey, ttlMs)

                created[#created + 1] = {productId = productId, qty = requested, uuid = uuid}
            end

            -- Build success response
            local response = {tostring(1)}
            for _, c in ipairs(created) do
                response[#response + 1] = c.uuid
                response[#response + 1] = c.productId
                response[#response + 1] = tostring(c.qty)
                response[#response + 1] = tostring(expiresAt)
            end
            return response
            """;

    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> RESERVE_SCRIPT;
    static {
        RESERVE_SCRIPT = new DefaultRedisScript<>();
        RESERVE_SCRIPT.setScriptText(RESERVE_LUA);
        RESERVE_SCRIPT.setResultType(List.class);
    }

    public List<ReservationResponse> reserveCartItems(Long cartId, List<CartItem> items) {
        if (cartId == null || items == null || items.isEmpty()) {
            return Collections.emptyList();
        }

        // Filter to valid items before entering the Lua script
        List<CartItem> validItems = new ArrayList<>();
        for (CartItem item : items) {
            Product product = item.getProduct();
            if (product == null) {
                continue;
            }
            Integer requested = item.getQuantity();
            if (requested == null || requested <= 0) {
                continue;
            }
            validItems.add(item);
        }
        if (validItems.isEmpty()) {
            return Collections.emptyList();
        }

        long now = System.currentTimeMillis();
        long ttlMs = reservationTtl.toMillis();

        List<String> args = new ArrayList<>();
        args.add(String.valueOf(cartId));
        args.add(String.valueOf(ttlMs));
        args.add(String.valueOf(now));
        args.add(String.valueOf(validItems.size()));
        for (CartItem item : validItems) {
            Product product = item.getProduct();
            args.add(String.valueOf(product.getProductId()));
            args.add(String.valueOf(item.getQuantity()));
            // Accepted race (F12): the on-hand figure is the caller's read of
            // products.quantity, not an atomic read inside the script. Between
            // that read and here another checkout can consume stock, so the
            // reservation check can pass against a stale total. It cannot
            // oversell — consumeReservationsForCart's conditional decrementStock
            // (WHERE quantity >= :qty) is the authoritative gate and rejects the
            // loser there. The window only lets an over-optimistic reservation
            // through; it never lets stock go negative.
            args.add(String.valueOf(product.getQuantity()));
            args.add(UUID.randomUUID().toString());
        }

        String cartKey = CART_RESERVATIONS_PREFIX + cartId;
        @SuppressWarnings("rawtypes")
        List result = redisTemplate.execute(RESERVE_SCRIPT, List.of(cartKey), args.toArray());

        if (result == null || result.isEmpty()) {
            throw new APIException("Reservation failed: no response from Redis");
        }

        String status = String.valueOf(result.get(0));
        if ("0".equals(status)) {
            int failedIndex = Integer.parseInt(String.valueOf(result.get(1))) - 1;
            int available = Integer.parseInt(String.valueOf(result.get(2)));
            int reserved = Integer.parseInt(String.valueOf(result.get(3)));
            CartItem failedItem = validItems.get(failedIndex);
            Product failedProduct = failedItem.getProduct();
            throw new APIException("Insufficient stock for " + failedProduct.getProductName()
                    + ". Available: " + available
                    + ", reserved: " + reserved
                    + ", requested: " + failedItem.getQuantity());
        }

        // Success: {1, uuid1, productId1, qty1, expiresAt1, ...}
        List<ReservationResponse> responses = new ArrayList<>();
        for (int i = 1; i < result.size(); i += 4) {
            responses.add(new ReservationResponse(
                    String.valueOf(result.get(i)),
                    Long.valueOf(String.valueOf(result.get(i + 1))),
                    Integer.parseInt(String.valueOf(result.get(i + 2))),
                    Long.parseLong(String.valueOf(result.get(i + 3)))
            ));
        }
        return responses;
    }

    /**
     * Deducts the reserved stock in Postgres and then frees the Redis
     * reservations.
     * <p>
     * The DB decrements run in the caller's transaction; the Redis cleanup does
     * not and cannot be rolled back. So the loop only does DB work, collecting
     * the Redis keys it will free, and the actual purge is deferred to
     * after-commit. If the caller's transaction rolls back — because this method
     * threw on an item, or because a later step in {@code placeOrder} failed —
     * the purge never runs and the reservations survive to be retried or to
     * lapse through their TTL. An earlier version deleted each reservation
     * inline, so a failure on item 3 left items 1–2 with their DB decrements
     * rolled back but their reservations already destroyed.
     */
    public void consumeReservationsForCart(Long cartId) {
        if (cartId == null) {
            return;
        }

        String cartReservationsKey = CART_RESERVATIONS_PREFIX + cartId;
        Set<String> reservationIds = liveMembers(cartReservationsKey);

        if (reservationIds.isEmpty()) {
            throw new APIException("No active stock reservation for this cart. Please start checkout again.");
        }

        List<String> reservationKeysToPurge = new ArrayList<>();
        List<ZSetMember> productMembersToPurge = new ArrayList<>();

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

            // Atomic conditional decrement and the authoritative oversell gate
            // (see the F12 note in reserveCartItems). A result of 0 means a
            // concurrent checkout took the stock after this cart reserved it.
            if (productRepository.decrementStock(productId, quantity) == 0) {
                int available = productRepository.findById(productId)
                        .map(Product::getQuantity).orElse(0);
                throw new APIException(productName(productId) + " sold out while you were checking out"
                        + (available > 0 ? " — only " + available + " left" : "")
                        + ". Please review your cart and try again.");
            }

            reservationKeysToPurge.add(reservationKey);
            productMembersToPurge.add(new ZSetMember(
                    PRODUCT_RESERVATIONS_PREFIX + productId, member(reservationId, quantity)));
        }

        afterCommitExecutor.execute(() -> {
            reservationKeysToPurge.forEach(redisTemplate::delete);
            productMembersToPurge.forEach(pm -> redisTemplate.opsForZSet().remove(pm.key(), pm.member()));
            redisTemplate.delete(cartReservationsKey);
        });
    }

    /** A ({@code ZSET key}, {@code member}) pair queued for post-commit removal. */
    private record ZSetMember(String key, String member) {
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
