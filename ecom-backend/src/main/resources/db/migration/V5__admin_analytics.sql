-- ==========================================================
--  V5 - Admin & Analytics
-- ==========================================================

CREATE TABLE IF NOT EXISTS user_activity_logs (
    log_id      BIGSERIAL PRIMARY KEY,
    username    VARCHAR(120) NOT NULL,
    action      VARCHAR(100) NOT NULL,
    details     VARCHAR(1000),
    created_at  TIMESTAMP(6) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_user_activity_logs_created_at ON user_activity_logs (created_at DESC);

CREATE TABLE IF NOT EXISTS promo_campaigns (
    campaign_id       BIGSERIAL PRIMARY KEY,
    name              VARCHAR(255)   NOT NULL,
    discount_percent  DOUBLE PRECISION NOT NULL,
    start_time        TIMESTAMP(6)   NOT NULL,
    end_time          TIMESTAMP(6)   NOT NULL,
    active            BOOLEAN        NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS promo_campaign_products (
    id           BIGSERIAL PRIMARY KEY,
    campaign_id  BIGINT NOT NULL,
    product_id   BIGINT NOT NULL,
    CONSTRAINT fk_promo_campaign_products_campaign FOREIGN KEY (campaign_id) REFERENCES promo_campaigns (campaign_id) ON DELETE CASCADE,
    CONSTRAINT fk_promo_campaign_products_product  FOREIGN KEY (product_id)  REFERENCES products (product_id),
    CONSTRAINT uq_promo_campaign_product UNIQUE (campaign_id, product_id)
);

CREATE INDEX IF NOT EXISTS idx_promo_campaign_products_campaign ON promo_campaign_products (campaign_id);
CREATE INDEX IF NOT EXISTS idx_promo_campaign_products_product  ON promo_campaign_products (product_id);
