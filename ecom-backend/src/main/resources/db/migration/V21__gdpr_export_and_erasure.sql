-- GDPR Art. 15 (access) and Art. 17 (erasure).
--
-- Erasure is *anonymisation* on anything the tax authority requires us to keep
-- — orders, order lines, payments, invoices. Those rows stay, stripped of the
-- identifiers that tie them to a person. Everything else (cart, wishlist,
-- reviews, questions, notifications, activity log) is deleted outright.
-- users.erased marks the tombstone: the row survives so the foreign keys from
-- the retained orders still resolve, but it can no longer authenticate.
-- See docs/gdpr.md.

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS erased    BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS erased_at TIMESTAMP;

-- One archive per export request. The payload is a ZIP built by the outbox
-- handler; it lives here only until expires_at, after which a scheduled purge
-- drops the bytes. Keeping it in the database (rather than object storage)
-- keeps the archive inside the same backup and retention story as the data it
-- was built from.
CREATE TABLE IF NOT EXISTS gdpr_export (
    id            BIGSERIAL   PRIMARY KEY,
    user_id       BIGINT      NOT NULL REFERENCES users(user_id),
    status        VARCHAR(20) NOT NULL DEFAULT 'PENDING',   -- PENDING | READY | EXPIRED | FAILED
    payload       BYTEA,
    byte_size     BIGINT,
    last_error    TEXT,
    created_at    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at  TIMESTAMP,
    downloaded_at TIMESTAMP,
    expires_at    TIMESTAMP   NOT NULL
);

-- "Does this user already have an export in flight or ready to download?" —
-- asked on every request, and by the purge sweep.
CREATE INDEX IF NOT EXISTS idx_gdpr_export_user_status ON gdpr_export (user_id, status);
CREATE INDEX IF NOT EXISTS idx_gdpr_export_expires_at  ON gdpr_export (expires_at);
