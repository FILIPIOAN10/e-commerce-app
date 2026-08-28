package com.ecommerce.project.service.stock;

import com.ecommerce.project.exception.APIException;
import com.ecommerce.project.model.StockMovement;
import com.ecommerce.project.model.StockMovementReason;
import com.ecommerce.project.repository.StockMovementRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * The one door every change to {@code products.quantity} goes through.
 *
 * <p>Each change is a single conditional {@code UPDATE … RETURNING quantity}: the
 * new balance is decided and read in one statement, so there is no window
 * between deciding there is enough stock and taking it. The
 * {@code quantity + delta >= 0} guard in the {@code WHERE} clause is the
 * authoritative oversell gate — a race that gets past every earlier check loses
 * here, and loses in the database rather than in application logic.
 *
 * <p>{@link Propagation#MANDATORY}: a movement is only ever owed as part of some
 * business change. Recording one outside a transaction would mean a ledger entry
 * that survives a rolled-back order, so a caller with no transaction fails loudly
 * here instead.
 *
 * <p><strong>The entity cache goes stale.</strong> The update is raw SQL, so a
 * {@code Product} already loaded in the persistence context keeps its old
 * {@code quantity} and {@code version}. Callers that need the new figure should
 * read it from the returned movement's {@code balanceAfter}; callers that write
 * to the same {@code Product} must flush before calling. This is the price of an
 * atomic conditional update, and it is cheaper than the race it removes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockLedgerService {

    private static final String SYSTEM_ACTOR = "system";
    private static final String GUEST_ACTOR = "guest";

    /**
     * One statement: apply the change if it keeps stock non-negative, and hand
     * back the resulting balance. Returns no row when the guard rejects it.
     *
     * <p>{@code version} is bumped along with the quantity so that an admin
     * holding a stale copy of the product cannot save it back over a sale that
     * happened meanwhile — the optimistic lock is what stops the whole row being
     * clobbered by a form submit.
     */
    private static final String APPLY_DELTA_SQL = """
            UPDATE products
            SET quantity = quantity + :delta, version = version + 1
            WHERE product_id = :productId AND quantity + :delta >= 0
            RETURNING quantity
            """;

    private final EntityManager entityManager;
    private final StockMovementRepository stockMovementRepository;

    /**
     * Applies a stock change and records it.
     *
     * @param delta signed change; negative consumes stock
     * @return the recorded movement, or empty when there was not enough stock
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<StockMovement> tryApplyAndRecord(Long productId, int delta,
                                                     StockMovementReason reason,
                                                     String refType, Long refId, String note) {
        if (delta == 0) {
            // Not an error, but not a movement either: a ledger full of zeroes
            // would bury the entries that mean something.
            return Optional.empty();
        }

        // The update is raw SQL and will not see entity changes still pending in
        // the persistence context.
        entityManager.flush();

        List<?> updated = entityManager.createNativeQuery(APPLY_DELTA_SQL)
                .setParameter("delta", delta)
                .setParameter("productId", productId)
                .getResultList();

        if (updated.isEmpty()) {
            return Optional.empty();
        }

        int balanceAfter = ((Number) updated.get(0)).intValue();
        return Optional.of(stockMovementRepository.save(StockMovement.of(
                productId, delta, reason, refType, refId, balanceAfter, note, currentActor())));
    }

    /**
     * As {@link #tryApplyAndRecord}, for callers with no better message to give
     * than "there is not enough of it".
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public StockMovement applyAndRecord(Long productId, int delta, StockMovementReason reason,
                                        String refType, Long refId, String note) {
        return tryApplyAndRecord(productId, delta, reason, refType, refId, note)
                .orElseThrow(() -> new APIException(
                        "Not enough stock for product " + productId + " to apply a change of " + delta));
    }

    /**
     * Records the quantity a product was created with, without touching
     * {@code products.quantity}.
     *
     * <p>The insert that created the product already set it. This exists so the
     * ledger's opening entry does not double the stock it is describing, and it
     * is the only path that records a movement it did not itself apply — every
     * other reason goes through {@link #tryApplyAndRecord}.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<StockMovement> recordOpeningBalance(Long productId, Integer quantity, String refType) {
        int opening = quantity == null ? 0 : quantity;
        if (opening == 0) {
            return Optional.empty();
        }
        return Optional.of(stockMovementRepository.save(StockMovement.of(
                productId, opening, StockMovementReason.OPENING_BALANCE, refType, productId,
                opening, "Initial stock", currentActor())));
    }

    /**
     * Who to attribute the movement to. Guest checkout has no principal, and the
     * scheduled jobs have none either — both are worth telling apart from a named
     * admin when reading the ledger back.
     */
    private String currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return SYSTEM_ACTOR;
        }
        String name = authentication.getName();
        if (name == null || name.isBlank() || "anonymousUser".equals(name)) {
            return GUEST_ACTOR;
        }
        return name;
    }
}
