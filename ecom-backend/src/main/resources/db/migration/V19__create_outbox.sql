-- Transactional outbox (F8).
--
-- Post-commit order side effects were fire-and-forget: if SMTP was down when an
-- order committed, the confirmation email was simply lost — no record that it
-- was owed, no way to retry.
--
-- Now the order listeners write a row here inside the order's own transaction.
-- The row commits with the order or not at all. A scheduled dispatcher then
-- claims pending rows with FOR UPDATE SKIP LOCKED, performs the effect, and
-- marks the row DONE — or bumps the attempt count with exponential backoff,
-- dead-lettering after a cap. The trade is "at-least-once and visible" for
-- "at-most-once and invisible"; consumers must tolerate a repeat.

CREATE TABLE IF NOT EXISTS outbox_event (
    id              BIGSERIAL   PRIMARY KEY,
    event_type      VARCHAR(80) NOT NULL,
    payload         TEXT        NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',   -- PENDING | DONE | DEAD
    attempts        INT         NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_error      TEXT,
    created_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- The dispatcher's claim query: WHERE status = 'PENDING' AND next_attempt_at <= now() ORDER BY id.
CREATE INDEX IF NOT EXISTS idx_outbox_claimable
    ON outbox_event (next_attempt_at)
    WHERE status = 'PENDING';
