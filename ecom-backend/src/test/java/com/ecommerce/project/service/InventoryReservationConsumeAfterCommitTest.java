package com.ecommerce.project.service;

import com.ecommerce.project.exception.APIException;
import com.ecommerce.project.model.Product;
import com.ecommerce.project.model.StockMovement;
import com.ecommerce.project.model.StockMovementReason;
import com.ecommerce.project.repository.ProductRepository;
import com.ecommerce.project.service.stock.StockLedgerService;
import com.ecommerce.project.util.AfterCommitExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F3: the Redis reservation purge in {@code consumeReservationsForCart} must not
 * happen until the caller's transaction commits, so a rollback leaves the held
 * stock intact.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("consumeReservationsForCart — purge is deferred to after commit")
class InventoryReservationConsumeAfterCommitTest {

    private static final String CART_KEY = "cart_reservations:v2:1";
    private static final String PRODUCT_KEY_A = "product_reservations:v2:1";

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ProductRepository productRepository;
    @Mock private StockLedgerService stockLedgerService;
    @Mock @SuppressWarnings("unchecked") private HashOperations<String, Object, Object> hashOps;
    @Mock @SuppressWarnings("unchecked") private ZSetOperations<String, String> zSetOps;

    private InventoryReservationService service;

    @BeforeEach
    void setUp() {
        service = new InventoryReservationService(
                redisTemplate, productRepository, new AfterCommitExecutor(), stockLedgerService);
        ReflectionTestUtils.setField(service, "reservationTtl", Duration.ofMinutes(10));
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    private void reservation(String id, long productId, int qty) {
        when(hashOps.entries("reservation:" + id)).thenReturn(Map.of(
                "productId", String.valueOf(productId), "quantity", String.valueOf(qty)));
    }

    private Product product(long id, String name) {
        Product p = new Product();
        p.setProductId(id);
        p.setProductName(name);
        return p;
    }

    /** The ledger takes the stock and hands back the movement it recorded. */
    private void stockTaken(long productId, int qty) {
        when(stockLedgerService.tryApplyAndRecord(eq(productId), eq(-qty),
                eq(StockMovementReason.SALE), eq("CART"), anyLong(), any()))
                .thenReturn(Optional.of(StockMovement.of(productId, -qty, StockMovementReason.SALE,
                        "CART", 1L, 0, null, "guest")));
    }

    /** The conditional update found nothing to take. */
    private void stockUnavailable(long productId, int qty) {
        when(stockLedgerService.tryApplyAndRecord(eq(productId), eq(-qty),
                eq(StockMovementReason.SALE), eq("CART"), anyLong(), any()))
                .thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("DB stock is decremented in-line, but Redis keys are not freed until commit")
    void purgeRunsOnlyAfterCommit() {
        when(zSetOps.range(CART_KEY, 0, -1)).thenReturn(Set.of("res-1"));
        reservation("res-1", 1L, 2);
        stockTaken(1L, 2);

        service.consumeReservationsForCart(1L);

        // DB work happened; Redis cleanup is still pending.
        verify(stockLedgerService).tryApplyAndRecord(1L, -2, StockMovementReason.SALE, "CART", 1L, null);
        verify(redisTemplate, never()).delete(anyString());
        verify(zSetOps, never()).remove(anyString(), anyString());

        // Commit fires the deferred purge.
        fireAfterCommit();

        verify(redisTemplate).delete("reservation:res-1");
        verify(zSetOps).remove(PRODUCT_KEY_A, "res-1:2");
        verify(redisTemplate).delete(CART_KEY);
    }

    @Test
    @DisplayName("on rollback the reservations survive — nothing is deleted")
    void rollbackKeepsReservations() {
        when(zSetOps.range(CART_KEY, 0, -1)).thenReturn(Set.of("res-1"));
        reservation("res-1", 1L, 2);
        stockTaken(1L, 2);

        service.consumeReservationsForCart(1L);
        fireAfterCompletionRollback();

        verify(redisTemplate, never()).delete(anyString());
        verify(zSetOps, never()).remove(anyString(), anyString());
    }

    @Test
    @DisplayName("when a later item cannot be fulfilled, no earlier reservation is freed")
    void failingItemLeavesEveryReservationIntact() {
        Set<String> ids = new LinkedHashSet<>(List.of("res-1", "res-2"));
        when(zSetOps.range(CART_KEY, 0, -1)).thenReturn(ids);
        reservation("res-1", 1L, 2);
        reservation("res-2", 2L, 1);
        stockTaken(1L, 2);          // first succeeds
        stockUnavailable(2L, 1);    // second lost the race
        when(productRepository.findById(2L)).thenReturn(Optional.of(product(2L, "Widget")));

        assertThatThrownBy(() -> service.consumeReservationsForCart(1L))
                .isInstanceOf(APIException.class)
                .hasMessageContaining("Widget");

        // The method threw before registering any purge; res-1 keeps its reservation
        // even though its decrement "succeeded" (that decrement rolls back with the tx).
        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
        verify(redisTemplate, never()).delete(anyString());
        verify(zSetOps, never()).remove(anyString(), anyString());
    }

    private void fireAfterCommit() {
        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCommit();
        }
    }

    private void fireAfterCompletionRollback() {
        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        }
    }
}
