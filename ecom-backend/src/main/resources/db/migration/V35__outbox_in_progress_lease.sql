-- ==========================================================
--  V35 - Outbox: claim as a lease, so handlers can run outside the transaction
-- ==========================================================
--  The dispatcher used to run a whole batch of handlers inside the transaction
--  that claimed them, so the FOR UPDATE SKIP LOCKED locks and a pooled
--  connection were held across Stripe calls, SMTP sends and archive builds. One
--  slow third party could park a connection for minutes while the storefront
--  timed out on hikari.connection-timeout.
--
--  Handlers now run with no transaction open. What keeps a second dispatcher off
--  a row while its handler is running is no longer the row lock but a lease: the
--  claim marks the row IN_PROGRESS and pushes next_attempt_at out by
--  app.outbox.lease-seconds. An IN_PROGRESS row whose lease has run out is
--  claimable again, which is how an event survives the process dying mid-handler
--  without needing a reaper job.
--
--  No data migration is needed: IN_PROGRESS is a new value and no row can hold
--  it yet. Only the claim index has to widen, or the claim query would stop
--  using it the moment the first row entered the new state.

-- The claim query is now:
--   WHERE status IN ('PENDING','IN_PROGRESS') AND next_attempt_at <= now()
--   ORDER BY next_attempt_at, id
DROP INDEX IF EXISTS idx_outbox_claimable;

CREATE INDEX IF NOT EXISTS idx_outbox_claimable
    ON outbox_event (next_attempt_at, id)
    WHERE status IN ('PENDING', 'IN_PROGRESS');

COMMENT ON COLUMN outbox_event.status IS
    'PENDING | IN_PROGRESS | DONE | DEAD. IN_PROGRESS is a lease, not a lock: '
    'next_attempt_at holds its expiry, after which any dispatcher may reclaim the row.';

COMMENT ON COLUMN outbox_event.next_attempt_at IS
    'When PENDING: the earliest retry. When IN_PROGRESS: when the current '
    'dispatcher''s lease expires and the row becomes claimable again.';

COMMENT ON COLUMN outbox_event.attempts IS
    'Deliveries started, counted at claim time rather than on failure — an event '
    'that kills the process still exhausts its budget and dead-letters.';
