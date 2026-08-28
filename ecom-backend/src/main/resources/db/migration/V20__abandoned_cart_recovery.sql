-- Abandoned-cart recovery.
--
-- carts.last_activity_at is stamped on every user cart action (add / remove /
-- change quantity / save-for-later). Without it "abandoned" cannot be defined.
-- A scheduled sweep finds non-empty carts whose owner opted in, whose activity
-- is older than a stage window, and that have no order since — and emits one
-- reminder per (cart, stage) through the transactional outbox.

ALTER TABLE carts ADD COLUMN IF NOT EXISTS last_activity_at TIMESTAMP;
UPDATE carts SET last_activity_at = CURRENT_TIMESTAMP WHERE last_activity_at IS NULL;
ALTER TABLE carts ALTER COLUMN last_activity_at SET NOT NULL;
ALTER TABLE carts ALTER COLUMN last_activity_at SET DEFAULT CURRENT_TIMESTAMP;
CREATE INDEX IF NOT EXISTS idx_carts_last_activity ON carts (last_activity_at);

-- Explicit marketing consent. Default false: nobody is emailed a reminder until
-- they opt in (surfacing the toggle in the profile UI is a follow-up).
ALTER TABLE users ADD COLUMN IF NOT EXISTS marketing_opt_in BOOLEAN NOT NULL DEFAULT FALSE;

-- One row per reminder actually sent. The (cart_id, stage) unique constraint is
-- the idempotency key — two sweeps (or two instances) cannot double-send a stage.
CREATE TABLE IF NOT EXISTS cart_reminder (
    id           BIGSERIAL   PRIMARY KEY,
    cart_id      BIGINT      NOT NULL REFERENCES carts(cart_id),
    stage        VARCHAR(20) NOT NULL,          -- FIRST | SECOND | FINAL
    sent_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    recovered_at TIMESTAMP,
    CONSTRAINT uk_cart_reminder_cart_stage UNIQUE (cart_id, stage)
);
CREATE INDEX IF NOT EXISTS idx_cart_reminder_cart ON cart_reminder (cart_id);
