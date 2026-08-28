-- Stock ledger.
--
-- products.quantity was a single mutable number. When it said "3", nobody could
-- say why: no record of what sold, what came back, what an admin corrected by
-- hand, or when. A discrepancy could be seen but never explained.
--
-- Every change to stock now also appends a row here, signed, with the balance it
-- produced and what caused it. products.quantity stays as the fast read and the
-- thing constraints are enforced on; this table is the audit trail, and
-- SUM(delta) per product must always equal it — asserted by a test and watched
-- by a scheduled reconciliation that logs any drift.

CREATE TABLE IF NOT EXISTS stock_movement (
    id            BIGSERIAL   PRIMARY KEY,
    product_id    BIGINT      NOT NULL REFERENCES products(product_id) ON DELETE CASCADE,
    delta         INT         NOT NULL,   -- signed: -2 a sale, +10 a restock
    reason        VARCHAR(30) NOT NULL,   -- SALE | RESTOCK | RETURN | CANCELLATION | ADJUSTMENT | OPENING_BALANCE
    ref_type      VARCHAR(30),            -- what caused it: ORDER, CART, ADMIN_EDIT, CSV_IMPORT, ...
    ref_id        BIGINT,
    balance_after INT         NOT NULL,
    note          VARCHAR(255),
    created_by    VARCHAR(255),
    created_at    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ON DELETE CASCADE above: a hard-deleted product takes its ledger with it.
-- The alternative — orphan movements for a product that no longer exists —
-- would block deletion to preserve history nobody can interpret.

-- The admin view: one product's movements, newest first.
CREATE INDEX IF NOT EXISTS idx_stock_movement_product ON stock_movement (product_id, created_at);

-- Opening balances for everything already in the catalogue, so SUM(delta)
-- equals products.quantity from the very first reconciliation. Everything that
-- happened before this table existed is folded into one row per product —
-- history we did not record cannot be invented.
INSERT INTO stock_movement (product_id, delta, reason, balance_after, note, created_by)
SELECT product_id, COALESCE(quantity, 0), 'OPENING_BALANCE', COALESCE(quantity, 0),
       'Balance carried in when the ledger was introduced', 'system'
FROM products
WHERE NOT EXISTS (SELECT 1 FROM stock_movement m WHERE m.product_id = products.product_id);
