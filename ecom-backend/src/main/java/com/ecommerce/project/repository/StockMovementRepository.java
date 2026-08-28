package com.ecommerce.project.repository;

import com.ecommerce.project.model.StockMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    Page<StockMovement> findByProductIdOrderByCreatedAtDescIdDesc(Long productId, Pageable pageable);

    List<StockMovement> findByProductIdOrderByIdAsc(Long productId);

    /**
     * Products whose ledger does not add up to their stock figure.
     *
     * <p>The invariant is {@code SUM(delta) = products.quantity}. Anything
     * returned here is a stock change that bypassed the ledger — the one failure
     * mode of routing every write through a service, and the reason this query
     * exists rather than being assumed unnecessary.
     *
     * @return rows of {@code [productId, quantity, ledgerTotal]}
     */
    @Query(value = """
            SELECT p.product_id, COALESCE(p.quantity, 0) AS quantity,
                   COALESCE(SUM(m.delta), 0) AS ledger_total
            FROM products p
            LEFT JOIN stock_movement m ON m.product_id = p.product_id
            GROUP BY p.product_id, p.quantity
            HAVING COALESCE(p.quantity, 0) <> COALESCE(SUM(m.delta), 0)
            """, nativeQuery = true)
    List<Object[]> findLedgerDiscrepancies();
}
