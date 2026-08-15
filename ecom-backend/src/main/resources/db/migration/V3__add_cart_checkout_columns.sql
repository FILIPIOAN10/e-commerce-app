-- ==========================================================
--  V3 - Add cart & checkout columns
-- ==========================================================
--  Adds columns introduced for save-for-later and
--  enhanced order summary (discount, shipping, coupons).
-- ==========================================================

ALTER TABLE cart_items
    ADD COLUMN IF NOT EXISTS saved_for_later BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS discount_amount DOUBLE PRECISION DEFAULT 0.0,
    ADD COLUMN IF NOT EXISTS shipping_cost   DOUBLE PRECISION DEFAULT 0.0,
    ADD COLUMN IF NOT EXISTS applied_coupons VARCHAR(255);
