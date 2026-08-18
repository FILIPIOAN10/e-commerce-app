CREATE TABLE IF NOT EXISTS bundles (
    bundle_id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    discount_percentage NUMERIC(5,2) NOT NULL DEFAULT 0.0,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS bundle_products (
    bundle_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    PRIMARY KEY (bundle_id, product_id),
    CONSTRAINT fk_bundle_products_bundle FOREIGN KEY (bundle_id) REFERENCES bundles(bundle_id) ON DELETE CASCADE,
    CONSTRAINT fk_bundle_products_product FOREIGN KEY (product_id) REFERENCES products(product_id) ON DELETE CASCADE
);
