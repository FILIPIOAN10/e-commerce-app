-- Product and cart money becomes exact — slice 3, after V24 did the same for
-- orders.
--
-- These are the columns every price in the catalogue starts from: a cart total
-- is a sum of product_price, an order total is a sum of the cart, and the amount
-- confirmed with Stripe is derived from that. Leaving the source of all of it as
-- DOUBLE PRECISION while the order downstream was NUMERIC would have meant an
-- exact total computed from inexact inputs.
--
-- products.price / discount / special_price are NOT NULL and stay that way. The
-- discount columns hold a percentage rather than an amount, at the same scale, so
-- a 12.5% promotion is expressible.

ALTER TABLE products
    ALTER COLUMN price         TYPE NUMERIC(12,2) USING ROUND(price::numeric, 2),
    ALTER COLUMN discount      TYPE NUMERIC(12,2) USING ROUND(discount::numeric, 2),
    ALTER COLUMN special_price TYPE NUMERIC(12,2) USING ROUND(special_price::numeric, 2);

-- Postgres rebuilds the indexes on an altered column by itself, so the two facet
-- indexes V22 put on special_price (idx_products_special_price and
-- idx_products_category_price) survive this and keep serving the price bands.

ALTER TABLE carts
    ALTER COLUMN total_price TYPE NUMERIC(12,2) USING ROUND(total_price::numeric, 2);

ALTER TABLE cart_items
    ALTER COLUMN discount      TYPE NUMERIC(12,2) USING ROUND(discount::numeric, 2),
    ALTER COLUMN product_price TYPE NUMERIC(12,2) USING ROUND(product_price::numeric, 2);

-- ROUND in the USING clauses, as in V24: a no-op for every value the application
-- wrote, and a safety net for legacy rows carrying float noise — 63.742499999999
-- from an old special-price calculation lands on 63.74 rather than failing the
-- migration.
--
-- special_price was computed as price - (discount * 0.01 * price) in double, so
-- it is the column most likely to hold such a value. Rounding it here is the
-- same figure the application would have displayed, made permanent.
