-- Automated refunds.
--
-- Until now an approved return only flipped the order status to "Refunded" — the
-- money was refunded by hand in the Stripe dashboard, and the charge.refunded
-- webhook was the only thing that ever reacted to a refund. This table is the
-- record of one refund. It is written PENDING inside the "mark refunded"
-- transaction and driven to SUCCEEDED / FAILED by the outbox handler (which
-- calls Stripe) and, as a backstop, by the charge.refunded webhook — the two
-- paths converge on the row identified by stripe_refund_id.

CREATE TABLE refunds (
    id                BIGSERIAL     PRIMARY KEY,
    return_id         BIGINT,
    order_id          BIGINT        NOT NULL,
    payment_intent_id VARCHAR(255)  NOT NULL,
    amount            NUMERIC(12,2) NOT NULL,
    status            VARCHAR(20)   NOT NULL,
    stripe_refund_id  VARCHAR(255),
    failure_reason    TEXT,
    created_at        TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_refunds_return FOREIGN KEY (return_id) REFERENCES return_requests (id),
    CONSTRAINT fk_refunds_order  FOREIGN KEY (order_id)  REFERENCES orders (id)
);

-- One refund per return request — the idempotent claim. A second markAsRefunded
-- (double-click, retried request, the tracking sweep firing twice) collides here
-- and is rejected before any Stripe call is made. Partial, because a refund made
-- straight in the Stripe dashboard has no return and must not be constrained.
CREATE UNIQUE INDEX uk_refunds_return ON refunds (return_id) WHERE return_id IS NOT NULL;

-- The real double-refund backstop: whichever path records the Stripe refund id
-- first wins; the other's write collides. Partial for the PENDING window where
-- the id is still null.
CREATE UNIQUE INDEX uk_refunds_stripe_id ON refunds (stripe_refund_id) WHERE stripe_refund_id IS NOT NULL;

-- FK columns Postgres does not index for us, plus the status filter the outbox
-- reconciliation runs.
CREATE INDEX idx_refunds_return         ON refunds (return_id);
CREATE INDEX idx_refunds_order          ON refunds (order_id);
CREATE INDEX idx_refunds_payment_intent ON refunds (payment_intent_id);
CREATE INDEX idx_refunds_status         ON refunds (status);
