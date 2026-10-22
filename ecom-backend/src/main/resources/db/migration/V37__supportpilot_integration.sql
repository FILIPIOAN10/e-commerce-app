-- SupportPilot (the AI support agent) integration.

-- 1. When an order was delivered. The shop's return window (30 days) is counted
--    from delivery, and until now the order only kept the day it was placed.
--    Set once by OrderServiceImpl.updateOrder on the move to Delivered.
ALTER TABLE orders ADD COLUMN delivered_at TIMESTAMP(6);

-- Orders already past delivery get their order date: the earliest the delivery
-- can have happened, so their window can only come out shorter, never longer.
UPDATE orders
   SET delivered_at = order_date::timestamp
 WHERE delivered_at IS NULL
   AND order_date IS NOT NULL
   AND order_status IN ('Delivered', 'Return Requested', 'Returned', 'Refunded');

-- 2. Where a return came from. A return the agent filed carries its conversation
--    (so an admin can read why) and the agent's idempotency key, so a retried
--    tool call finds the return it already created instead of failing on
--    "a return already exists".
ALTER TABLE return_requests ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER';
ALTER TABLE return_requests ADD COLUMN support_conversation_id VARCHAR(64);
ALTER TABLE return_requests ADD COLUMN support_idempotency_key VARCHAR(100);
ALTER TABLE return_requests
    ADD CONSTRAINT ck_return_requests_source CHECK (source IN ('CUSTOMER', 'SUPPORTPILOT'));
CREATE UNIQUE INDEX uk_return_requests_support_key
    ON return_requests (support_idempotency_key)
    WHERE support_idempotency_key IS NOT NULL;

-- 3. Which support conversations belong to which account. The transcript itself
--    stays in SupportPilot: the LangGraph checkpoint IS the history, and it has
--    to live where the paused agent resumes from. This row is what lets the shop
--    check ownership before proxying, resume the latest conversation on another
--    device, and find everything to erase under GDPR.
CREATE TABLE support_conversations (
    conversation_id VARCHAR(64)  PRIMARY KEY,
    user_id         BIGINT       NOT NULL REFERENCES users (user_id),
    status          VARCHAR(20)  NOT NULL,
    created_at      TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_support_conversations_status CHECK (status IN ('open', 'awaiting_approval'))
);
CREATE INDEX idx_support_conversations_user ON support_conversations (user_id, updated_at DESC);
