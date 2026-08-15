-- ==========================================================
--  V4 - Reviews social & Q&A
-- ==========================================================
--  Adds review helpfulness / verified purchase columns and
--  the product questions table.
-- ==========================================================

ALTER TABLE reviews
    ADD COLUMN IF NOT EXISTS verified_purchase BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS helpful_count    INTEGER      NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS unhelpful_count  INTEGER      NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS product_questions (
    question_id  BIGSERIAL PRIMARY KEY,
    product_id   BIGINT       NOT NULL,
    user_id      BIGINT       NOT NULL,
    question     VARCHAR(1000) NOT NULL,
    answer       VARCHAR(1000),
    created_at   TIMESTAMP(6) NOT NULL,
    answered_at  TIMESTAMP(6),
    CONSTRAINT fk_product_questions_product FOREIGN KEY (product_id) REFERENCES products (product_id),
    CONSTRAINT fk_product_questions_user    FOREIGN KEY (user_id)    REFERENCES users (user_id)
);

CREATE INDEX IF NOT EXISTS idx_product_questions_product ON product_questions (product_id);
