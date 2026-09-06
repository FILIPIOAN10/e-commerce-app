-- Chargebacks / disputes.
--
-- When a cardholder or their bank disputes a charge, Stripe sends
-- charge.dispute.created and we have a deadline (evidence_due_by) to respond
-- with evidence. This table is our copy of that dispute: opened from the
-- webhook, walked through an explicit status machine (NEEDS_RESPONSE ->
-- UNDER_REVIEW -> WON / LOST / CLOSED) by later charge.dispute.updated /
-- .closed events, and shown to an admin who attaches evidence files.
--
-- stripe_status keeps Stripe's own raw status string alongside our mapped
-- enum, because Stripe has ~10 statuses we fold into 5 and the raw value is
-- worth having when reconciling by hand.

CREATE TABLE disputes (
    id                    BIGSERIAL     PRIMARY KEY,
    stripe_dispute_id     VARCHAR(255)  NOT NULL,
    payment_intent_id     VARCHAR(255)  NOT NULL,
    charge_id             VARCHAR(255),
    order_id              BIGINT,
    amount                NUMERIC(12,2) NOT NULL,
    currency              VARCHAR(3)    NOT NULL DEFAULT 'USD',
    reason                VARCHAR(64),
    status                VARCHAR(20)   NOT NULL,
    stripe_status         VARCHAR(40),
    evidence_due_by       TIMESTAMP(6),
    evidence_submitted_at TIMESTAMP(6),
    outcome_note          TEXT,
    created_at            TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_disputes_order FOREIGN KEY (order_id) REFERENCES orders (id)
);

-- One row per Stripe dispute. The webhook is at-least-once, so charge.dispute.created
-- can arrive twice — the second insert collides here and the handler treats it
-- as an update instead.
CREATE UNIQUE INDEX uk_disputes_stripe_id ON disputes (stripe_dispute_id);

-- FK column (parent-delete scan) + the columns the admin views and the webhook
-- looks up on.
CREATE INDEX idx_disputes_order             ON disputes (order_id);
CREATE INDEX idx_disputes_payment_intent    ON disputes (payment_intent_id);
CREATE INDEX idx_disputes_status            ON disputes (status);

-- Evidence an admin uploads for a dispute. The bytes live wherever FileService
-- puts them (local dir by default, S3 when configured); this is just the
-- metadata and the stored name to fetch them back by.
CREATE TABLE dispute_evidence_files (
    id             BIGSERIAL     PRIMARY KEY,
    dispute_id     BIGINT        NOT NULL,
    stored_name    VARCHAR(255)  NOT NULL,
    original_name  VARCHAR(255)  NOT NULL,
    content_type   VARCHAR(128),
    size_bytes     BIGINT        NOT NULL,
    uploaded_by    VARCHAR(255),
    uploaded_at    TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_evidence_dispute FOREIGN KEY (dispute_id) REFERENCES disputes (id)
);

CREATE INDEX idx_evidence_dispute ON dispute_evidence_files (dispute_id);
