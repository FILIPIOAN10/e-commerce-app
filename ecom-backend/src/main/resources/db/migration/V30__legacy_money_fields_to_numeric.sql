-- The last money fields leave `double` — slice 5, and the end of the migration.
--
-- Four columns still carried DOUBLE PRECISION after V24/V25 did orders, products
-- and carts: a bundle's percentage off, a promo campaign's percentage off, a
-- subscription plan's price, and a return's refund amount. Two are amounts and
-- two are percentages, but V25 already settled that both kinds travel as
-- NUMERIC(12,2) here — it converted products.discount, a percentage, for the
-- same reason a percentage multiplied into a price must not reintroduce the
-- float error the prices themselves just shed, and 12.5% has to be expressible.
--
-- ROUND in every USING clause, as in V24/V25: a no-op for values the application
-- wrote (all already at scale 2 or below), a safety net for any legacy float
-- noise on older rows.

-- bundles.discount_percentage has a DEFAULT, dropped and re-added around the
-- type change the same way V24 handled orders.discount_amount.
ALTER TABLE bundles
    ALTER COLUMN discount_percentage DROP DEFAULT;
ALTER TABLE bundles
    ALTER COLUMN discount_percentage TYPE NUMERIC(12,2) USING ROUND(discount_percentage::numeric, 2);
ALTER TABLE bundles
    ALTER COLUMN discount_percentage SET DEFAULT 0.00;

ALTER TABLE promo_campaigns
    ALTER COLUMN discount_percent TYPE NUMERIC(12,2) USING ROUND(discount_percent::numeric, 2);

ALTER TABLE subscription_plans
    ALTER COLUMN amount TYPE NUMERIC(12,2) USING ROUND(amount::numeric, 2);

ALTER TABLE return_requests
    ALTER COLUMN refund_amount TYPE NUMERIC(12,2) USING ROUND(refund_amount::numeric, 2);
