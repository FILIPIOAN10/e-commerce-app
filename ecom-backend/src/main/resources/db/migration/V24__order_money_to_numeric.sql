-- Order money becomes exact.
--
-- orders.total_amount and its siblings were DOUBLE PRECISION — a binary float
-- standing in for a count of cents. It cannot hold 84.99 exactly, so a stored
-- total could disagree with the sum of its own lines, and the figure sent to
-- Stripe was derived from a number that had already drifted. The application
-- computed in exact decimal (Money) and then widened the result on the way into
-- these columns, which threw the exactness away at the last step.
--
-- NUMERIC(12,2) is what the money always was: ten digits before the point, two
-- after. No order this store will take needs more.

-- Defaults are dropped first: a DEFAULT written for a float column would have to
-- be re-cast along with the type, and doing it explicitly beats relying on
-- Postgres to find the cast.
ALTER TABLE orders
    ALTER COLUMN discount_amount DROP DEFAULT,
    ALTER COLUMN shipping_cost   DROP DEFAULT;

-- ROUND in the USING clause: every value the application wrote was already
-- rounded to the cent, so this is a no-op for real data. It is here for rows
-- that predate that discipline — a float carrying 24.989999999999998 lands on
-- 24.99 instead of failing the migration.
ALTER TABLE orders
    ALTER COLUMN total_amount    TYPE NUMERIC(12,2) USING ROUND(total_amount::numeric, 2),
    ALTER COLUMN discount_amount TYPE NUMERIC(12,2) USING ROUND(discount_amount::numeric, 2),
    ALTER COLUMN shipping_cost   TYPE NUMERIC(12,2) USING ROUND(shipping_cost::numeric, 2);

ALTER TABLE orders
    ALTER COLUMN discount_amount SET DEFAULT 0.00,
    ALTER COLUMN shipping_cost   SET DEFAULT 0.00;

-- order_items.discount is a percentage rather than an amount, but it is read
-- back beside the price and printed on the invoice, so it travels with it.
ALTER TABLE order_items
    ALTER COLUMN discount              TYPE NUMERIC(12,2) USING ROUND(discount::numeric, 2),
    ALTER COLUMN ordered_product_price TYPE NUMERIC(12,2) USING ROUND(ordered_product_price::numeric, 2);
