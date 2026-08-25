-- Phase 2: database-level invariants for stock and coupon usage.

-- Clamp any pre-existing bad data so the new constraints can be applied.
UPDATE products SET quantity = 0 WHERE quantity < 0;
UPDATE coupons SET used_count = 0 WHERE used_count < 0;

-- Stock can never go negative, regardless of which code path writes it.
ALTER TABLE products
    ADD CONSTRAINT ck_products_quantity_non_negative CHECK (quantity >= 0);

-- Cart and order line quantities must be positive.
ALTER TABLE cart_items
    ADD CONSTRAINT ck_cart_items_quantity_positive CHECK (quantity > 0);

ALTER TABLE order_items
    ADD CONSTRAINT ck_order_items_quantity_positive CHECK (quantity > 0);

-- Coupon usage can never exceed its limit or go negative.
ALTER TABLE coupons
    ADD CONSTRAINT ck_coupons_used_count_non_negative CHECK (used_count >= 0);
