-- ==========================================================
--  V1 - Baseline schema
-- ==========================================================
--  Mirrors the JPA entities under com.ecommerce.project.model.
--
--  On an existing database created by hibernate ddl-auto=update,
--  this script is SKIPPED: spring.flyway.baseline-on-migrate=true
--  stamps the schema at version 0 and starts applying from V2.
--  On a fresh database it runs and creates the full schema.
-- ==========================================================

-- ---------- roles ----------
CREATE TABLE IF NOT EXISTS roles (
    role_id   SERIAL PRIMARY KEY,
    role_name VARCHAR(20)
);

-- ---------- users ----------
CREATE TABLE IF NOT EXISTS users (
    user_id            BIGSERIAL PRIMARY KEY,
    username           VARCHAR(20)  NOT NULL,
    email              VARCHAR(50)  NOT NULL,
    password           VARCHAR(120) NOT NULL,
    password_hint      VARCHAR(100),
    provider           VARCHAR(255),
    provider_id        VARCHAR(255),
    two_factor_secret  VARCHAR(255),
    two_factor_enabled BOOLEAN      NOT NULL DEFAULT FALSE,
    verified           BOOLEAN      NOT NULL DEFAULT FALSE,
    phone              VARCHAR(20),
    avatar_url         VARCHAR(500),
    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT uk_users_email    UNIQUE (email)
);

-- ---------- user_role (join) ----------
CREATE TABLE IF NOT EXISTS user_role (
    user_id BIGINT  NOT NULL,
    role_id INTEGER NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES roles (role_id)
);

-- ---------- categories ----------
CREATE TABLE IF NOT EXISTS categories (
    category_id   BIGSERIAL PRIMARY KEY,
    category_name VARCHAR(255)
);

-- ---------- products ----------
-- Product uses GenerationType.AUTO, which Hibernate 6 maps to a
-- sequence named products_seq with allocationSize 50.
CREATE SEQUENCE IF NOT EXISTS products_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE IF NOT EXISTS products (
    product_id           BIGINT PRIMARY KEY,
    product_name         VARCHAR(255),
    image                VARCHAR(255),
    description          VARCHAR(255),
    tags                 VARCHAR(255),
    quantity             INTEGER,
    low_stock_threshold  INTEGER,
    price                DOUBLE PRECISION NOT NULL,
    discount             DOUBLE PRECISION NOT NULL,
    special_price        DOUBLE PRECISION NOT NULL,
    category_id          BIGINT,
    seller_id            BIGINT,
    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories (category_id),
    CONSTRAINT fk_products_seller   FOREIGN KEY (seller_id)   REFERENCES users (user_id)
);

CREATE INDEX IF NOT EXISTS idx_products_category ON products (category_id);
CREATE INDEX IF NOT EXISTS idx_products_seller   ON products (seller_id);

-- ---------- product_images ----------
CREATE TABLE IF NOT EXISTS product_images (
    image_id   BIGSERIAL PRIMARY KEY,
    image_name VARCHAR(255),
    product_id BIGINT,
    CONSTRAINT fk_product_images_product FOREIGN KEY (product_id) REFERENCES products (product_id)
);

CREATE INDEX IF NOT EXISTS idx_product_images_product ON product_images (product_id);

-- ---------- addresses ----------
CREATE TABLE IF NOT EXISTS addresses (
    address_id    BIGSERIAL PRIMARY KEY,
    street        VARCHAR(255),
    building_name VARCHAR(255),
    city          VARCHAR(255),
    state         VARCHAR(255),
    country       VARCHAR(255),
    pincode       VARCHAR(255),
    user_id       BIGINT,
    CONSTRAINT fk_addresses_user FOREIGN KEY (user_id) REFERENCES users (user_id)
);

CREATE INDEX IF NOT EXISTS idx_addresses_user ON addresses (user_id);

-- ---------- carts ----------
CREATE TABLE IF NOT EXISTS carts (
    cart_id     BIGSERIAL PRIMARY KEY,
    user_id     BIGINT,
    total_price DOUBLE PRECISION,
    CONSTRAINT fk_carts_user FOREIGN KEY (user_id) REFERENCES users (user_id)
);

-- ---------- cart_items ----------
CREATE TABLE IF NOT EXISTS cart_items (
    cart_item_id  BIGSERIAL PRIMARY KEY,
    cart_id       BIGINT,
    product_id    BIGINT,
    quantity      INTEGER,
    discount      DOUBLE PRECISION,
    product_price DOUBLE PRECISION,
    CONSTRAINT fk_cart_items_cart    FOREIGN KEY (cart_id)    REFERENCES carts (cart_id),
    CONSTRAINT fk_cart_items_product FOREIGN KEY (product_id) REFERENCES products (product_id)
);

CREATE INDEX IF NOT EXISTS idx_cart_items_cart    ON cart_items (cart_id);
CREATE INDEX IF NOT EXISTS idx_cart_items_product ON cart_items (product_id);

-- ---------- payments ----------
CREATE TABLE IF NOT EXISTS payments (
    payment_id          BIGSERIAL PRIMARY KEY,
    payment_method      VARCHAR(255),
    pg_payment_id       VARCHAR(255),
    pg_status           VARCHAR(255),
    pg_response_message VARCHAR(255),
    pg_name             VARCHAR(255)
);

-- ---------- orders ----------
CREATE TABLE IF NOT EXISTS orders (
    id           BIGSERIAL PRIMARY KEY,
    email        VARCHAR(255) NOT NULL,
    order_date   DATE,
    payment_id   BIGINT,
    total_amount DOUBLE PRECISION,
    order_status VARCHAR(255),
    address_id   BIGINT,
    CONSTRAINT uk_orders_payment  UNIQUE (payment_id),
    CONSTRAINT fk_orders_payment  FOREIGN KEY (payment_id) REFERENCES payments (payment_id),
    CONSTRAINT fk_orders_address  FOREIGN KEY (address_id) REFERENCES addresses (address_id)
);

CREATE INDEX IF NOT EXISTS idx_orders_email ON orders (email);

-- ---------- order_items ----------
CREATE TABLE IF NOT EXISTS order_items (
    order_item_id          BIGSERIAL PRIMARY KEY,
    product_id             BIGINT,
    order_id               BIGINT,
    quantity               INTEGER,
    discount               DOUBLE PRECISION,
    ordered_product_price  DOUBLE PRECISION,
    CONSTRAINT fk_order_items_product FOREIGN KEY (product_id) REFERENCES products (product_id),
    CONSTRAINT fk_order_items_order   FOREIGN KEY (order_id)   REFERENCES orders (id)
);

CREATE INDEX IF NOT EXISTS idx_order_items_order   ON order_items (order_id);
CREATE INDEX IF NOT EXISTS idx_order_items_product ON order_items (product_id);

-- ---------- coupons ----------
CREATE TABLE IF NOT EXISTS coupons (
    coupon_id        BIGSERIAL PRIMARY KEY,
    code             VARCHAR(255) NOT NULL,
    discount_percent INTEGER      NOT NULL,
    expiry_date      DATE         NOT NULL,
    max_uses         INTEGER      NOT NULL,
    used_count       INTEGER      NOT NULL,
    active           BOOLEAN      NOT NULL,
    CONSTRAINT uk_coupons_code UNIQUE (code)
);

-- ---------- reviews ----------
CREATE TABLE IF NOT EXISTS reviews (
    review_id  BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    product_id BIGINT       NOT NULL,
    rating     INTEGER      NOT NULL,
    comment    VARCHAR(1000),
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_reviews_user_product UNIQUE (user_id, product_id),
    CONSTRAINT fk_reviews_user    FOREIGN KEY (user_id)    REFERENCES users (user_id),
    CONSTRAINT fk_reviews_product FOREIGN KEY (product_id) REFERENCES products (product_id)
);

CREATE INDEX IF NOT EXISTS idx_reviews_product ON reviews (product_id);

-- ---------- wishlists ----------
CREATE TABLE IF NOT EXISTS wishlists (
    wishlist_id BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    product_id  BIGINT       NOT NULL,
    created_at  TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_wishlists_user_product UNIQUE (user_id, product_id),
    CONSTRAINT fk_wishlists_user    FOREIGN KEY (user_id)    REFERENCES users (user_id),
    CONSTRAINT fk_wishlists_product FOREIGN KEY (product_id) REFERENCES products (product_id)
);

CREATE INDEX IF NOT EXISTS idx_wishlists_user ON wishlists (user_id);

-- ---------- return_requests ----------
CREATE TABLE IF NOT EXISTS return_requests (
    id           BIGSERIAL PRIMARY KEY,
    order_id     BIGINT       NOT NULL,
    user_email   VARCHAR(255) NOT NULL,
    reason       VARCHAR(500) NOT NULL,
    status       VARCHAR(255) NOT NULL,
    requested_at TIMESTAMP(6),
    processed_at TIMESTAMP(6),
    admin_note   VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_return_requests_order ON return_requests (order_id);
CREATE INDEX IF NOT EXISTS idx_return_requests_email ON return_requests (user_email);

-- ---------- notifications ----------
CREATE TABLE IF NOT EXISTS notifications (
    id              BIGSERIAL PRIMARY KEY,
    recipient_email VARCHAR(255),
    title           VARCHAR(255) NOT NULL,
    message         VARCHAR(500) NOT NULL,
    type            VARCHAR(255),
    reference_id    BIGINT,
    read            BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP(6)
);

CREATE INDEX IF NOT EXISTS idx_notifications_recipient ON notifications (recipient_email);
