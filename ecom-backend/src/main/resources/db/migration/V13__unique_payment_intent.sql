-- Enforce that a Stripe / PayPal payment id can only be associated with one order.
-- NULL or empty values (e.g. COD orders) are excluded so they do not fail the check.
CREATE UNIQUE INDEX IF NOT EXISTS uk_payments_pg_payment_id
    ON payments (pg_payment_id)
    WHERE pg_payment_id IS NOT NULL AND pg_payment_id <> '';
