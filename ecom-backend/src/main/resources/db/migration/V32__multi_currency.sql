-- Multi-currency: a presentation currency on top of the USD base.
--
-- Every price in the store is still held in USD (products, carts, order totals) —
-- that stays the settlement currency. What this adds is the currency the customer
-- *chose to see and check out in*: the list of currencies we offer, and, on each
-- order, which one the customer saw and the USD->currency rate that was quoted at
-- the time. The rate is frozen onto the order so an invoice reprinted a year
-- later still shows the figures the customer agreed to.

CREATE TABLE supported_currencies (
    code           VARCHAR(3)   PRIMARY KEY,          -- ISO 4217, e.g. USD, EUR
    symbol         VARCHAR(8)   NOT NULL,             -- what the UI puts in front of the amount
    decimal_digits SMALLINT     NOT NULL DEFAULT 2,   -- 2 for most, 0 for JPY
    active         BOOLEAN      NOT NULL DEFAULT TRUE, -- offered at checkout right now
    sort_order     INT          NOT NULL DEFAULT 0,   -- display order in the picker
    CONSTRAINT ck_supported_currencies_code_upper CHECK (code = UPPER(code)),
    CONSTRAINT ck_supported_currencies_digits     CHECK (decimal_digits BETWEEN 0 AND 4)
);

-- Seed. Idempotent so a re-run or a half-applied environment does not error.
-- USD first and always active: it is the base and the fallback.
INSERT INTO supported_currencies (code, symbol, decimal_digits, active, sort_order) VALUES
    ('USD', '$',   2, TRUE, 0),
    ('EUR', '€',   2, TRUE, 1),
    ('GBP', '£',   2, TRUE, 2),
    ('CAD', 'CA$', 2, TRUE, 3),
    ('AUD', 'A$',  2, TRUE, 4),
    ('RON', 'lei', 2, TRUE, 5),
    ('JPY', '¥',   0, TRUE, 6)
ON CONFLICT (code) DO NOTHING;

-- The currency the customer checked out in, and the USD->currency rate quoted
-- then. Constant defaults, so this is a fast metadata-only change on a populated
-- orders table — every existing order was USD at rate 1.
ALTER TABLE orders ADD COLUMN currency_code  VARCHAR(3)     NOT NULL DEFAULT 'USD';
ALTER TABLE orders ADD COLUMN exchange_rate  NUMERIC(18,8)  NOT NULL DEFAULT 1;

ALTER TABLE orders ADD CONSTRAINT fk_orders_currency
    FOREIGN KEY (currency_code) REFERENCES supported_currencies (code);

ALTER TABLE orders ADD CONSTRAINT ck_orders_exchange_rate_positive
    CHECK (exchange_rate > 0);

-- The child side of fk_orders_currency. Low cardinality, but the project's
-- invariant is that every FK column starts an index so a parent delete does not
-- sequentially scan orders (see V28 and ForeignKeyIndexTest).
CREATE INDEX idx_orders_currency_code ON orders (currency_code);
