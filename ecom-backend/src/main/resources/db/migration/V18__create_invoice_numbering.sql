-- Fiscal invoice numbering. A fiscal invoice number must be gapless within its
-- year, which rules out a database SEQUENCE (sequences do not roll back, so an
-- aborted checkout would burn a number and leave a hole).
--
-- Instead: one counter row per fiscal year, incremented inside the same
-- transaction that inserts the invoice. If that transaction rolls back the
-- increment rolls back with it, so committed invoices form an unbroken run
-- 1..N per year. Concurrent issuers serialise on the counter row via
-- SELECT ... FOR UPDATE (see InvoiceNumberSequenceRepository).

CREATE TABLE IF NOT EXISTS invoice_number_sequences (
    fiscal_year INT    PRIMARY KEY,
    last_value  BIGINT NOT NULL DEFAULT 0
);

-- Pre-seed a generous range so the "first invoice of the year" path never has to
-- create a row under contention. Extend as needed.
INSERT INTO invoice_number_sequences (fiscal_year, last_value)
SELECT gs, 0 FROM generate_series(2024, 2035) AS gs
ON CONFLICT (fiscal_year) DO NOTHING;

CREATE TABLE IF NOT EXISTS invoices (
    id             BIGSERIAL PRIMARY KEY,
    order_id       BIGINT      NOT NULL REFERENCES orders(id),
    fiscal_year    INT         NOT NULL,
    sequence_no    BIGINT      NOT NULL,
    invoice_number VARCHAR(64) NOT NULL,
    issued_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_invoice_order      UNIQUE (order_id),
    CONSTRAINT uk_invoice_year_seq   UNIQUE (fiscal_year, sequence_no),
    CONSTRAINT uk_invoice_number     UNIQUE (invoice_number)
);

CREATE INDEX IF NOT EXISTS idx_invoice_order ON invoices (order_id);
