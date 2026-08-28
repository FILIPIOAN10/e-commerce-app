-- Makes order-creating POST requests idempotent: the client sends an
-- Idempotency-Key header, the server records the key + a hash of the request and
-- the response it produced, and replays that response for any repeat.
CREATE TABLE IF NOT EXISTS idempotency_keys (
    id               BIGSERIAL PRIMARY KEY,
    idempotency_key  VARCHAR(255) NOT NULL,
    scope            VARCHAR(100) NOT NULL,
    request_hash     VARCHAR(64)  NOT NULL,
    status           VARCHAR(20)  NOT NULL,
    response_status  INT,
    response_body    TEXT,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at     TIMESTAMP,
    CONSTRAINT uk_idempotency_key_scope UNIQUE (idempotency_key, scope)
);
