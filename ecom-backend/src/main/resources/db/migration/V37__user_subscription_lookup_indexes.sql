-- ==========================================================
-- user_subscriptions: index the columns it is actually searched by
-- ==========================================================
-- V8 created this table with no index of its own, and V28 added only the
-- plan_id foreign key. Every other access path was a sequential scan:
--
--   findByStripeSubscriptionId       every recurring Stripe webhook
--                                    (invoice.paid, customer.subscription.*)
--   findByStripeCheckoutSessionId    every new subscription
--   findByEmailOrderByCreatedAtDesc  the account page, and the GDPR export
--
-- The table only grows — cancelled subscriptions are kept as history — so the
-- scan gets slower for the life of the install, on the one path that has a
-- deadline attached: Stripe gives a webhook 20 seconds and then retries it,
-- and a retry re-enters the same handler. V28's comment named the GDPR export
-- as its motivation, and the export reads this table on the unindexed email.

-- UNIQUE rather than plain, on both Stripe ids: the repository returns
-- Optional for each, so a second row would not be a duplicate to live with —
-- it would throw IncorrectResultSizeDataAccessException inside a webhook
-- handler. uk_refunds_stripe_id and uk_disputes_stripe_id already say the same
-- thing about the later Stripe-backed tables.
--
-- If either index fails to build, the database holds rows that disagree about
-- which subscription is which; Postgres names the duplicated value in the
-- error. Reconcile those rows against Stripe and keep one per subscription,
-- then re-run. Deliberately not clamped here: choosing which row survives is
-- a billing decision, not a migration's to make.
--
-- Partial, so the abandoned checkouts that never reached Stripe — both ids
-- still NULL — neither collide with each other nor sit in the index.
CREATE UNIQUE INDEX IF NOT EXISTS uk_user_subscriptions_stripe_id
    ON user_subscriptions (stripe_subscription_id)
    WHERE stripe_subscription_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_user_subscriptions_checkout_session
    ON user_subscriptions (stripe_checkout_session_id)
    WHERE stripe_checkout_session_id IS NOT NULL;

-- Composite rather than email alone: the query is
-- "where email = ? order by created_at desc", and carrying created_at in the
-- index lets the sort come off it instead of re-sorting the account's history.
CREATE INDEX IF NOT EXISTS idx_user_subscriptions_email_created_at
    ON user_subscriptions (email, created_at DESC);
