-- ==========================================================
--  V6 - Performance indexes for orders, cart items and products
-- ==========================================================

-- Add user/order tracking columns for future order-history queries
-- (safe no-ops if they already exist from an earlier hibernate ddl-auto run)
ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS user_id    BIGINT,
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- Composite index to speed up "orders by user, sorted by date" lookups
CREATE INDEX IF NOT EXISTS idx_orders_user_id_created_at ON orders (user_id, created_at);

-- Idempotent indexes for cart and product lookups
CREATE INDEX IF NOT EXISTS idx_cart_items_cart     ON cart_items (cart_id);
CREATE INDEX IF NOT EXISTS idx_products_category   ON products (category_id);
