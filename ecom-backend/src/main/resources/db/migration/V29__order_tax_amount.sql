-- Orders carry a VAT line.
--
-- The pricing pipeline was built with a TAX slot from the start
-- (PriceLineType.TAX, PriceBreakdown.taxTotal()) but no rule filled it, so every
-- order so far stored no tax and its total_amount is a pre-tax figure. VatRule
-- now adds the line; this column is where the amount lands.
--
-- NUMERIC(12,2) and NOT NULL DEFAULT 0.00 to match total_amount / discount_amount
-- / shipping_cost from V24. The default backfills existing rows with 0.00, which
-- is correct rather than merely convenient: those orders were genuinely priced
-- and charged without tax, and their total_amount already reflects that.

ALTER TABLE orders
    ADD COLUMN tax_amount NUMERIC(12,2) NOT NULL DEFAULT 0.00;
