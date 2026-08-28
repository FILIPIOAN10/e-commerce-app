-- Optimistic locking: Hibernate appends "AND version = ?" to every UPDATE of
-- these rows and bumps the value, so two concurrent edits no longer resolve to
-- last-write-wins — the loser gets an OptimisticLockException (mapped to 409).
-- Existing rows start at 0.
ALTER TABLE products ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE coupons  ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE orders   ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
