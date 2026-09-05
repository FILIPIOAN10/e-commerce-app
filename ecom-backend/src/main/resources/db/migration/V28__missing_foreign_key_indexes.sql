-- ==========================================================
-- The foreign keys that were still unindexed
-- ==========================================================
-- JPA does not create an index for a @JoinColumn, and Postgres does not create
-- one for a REFERENCES clause either — only the referenced side gets one, from
-- its primary key. So every one of these was a sequential scan on the child
-- table, and the places that hit them hardest are the ones that walk a whole
-- account: GDPR export and erasure, and the cascade behind deleting a product,
-- an address or a subscription plan.
--
-- carts.user_id was the sixth; V26 covered it with uk_carts_user.
--
-- reviews.user_id is deliberately absent: uk_reviews_user_product is
-- UNIQUE (user_id, product_id) and user_id is its leading column, so lookups by
-- user already use it. wishlists is the same shape but the other way round —
-- its unique starts with user_id, which leaves product_id uncovered.

CREATE INDEX IF NOT EXISTS idx_orders_address           ON orders (address_id);
CREATE INDEX IF NOT EXISTS idx_wishlists_product        ON wishlists (product_id);
CREATE INDEX IF NOT EXISTS idx_product_questions_user   ON product_questions (user_id);
CREATE INDEX IF NOT EXISTS idx_user_subscriptions_plan  ON user_subscriptions (plan_id);
CREATE INDEX IF NOT EXISTS idx_subscription_plans_product ON subscription_plans (product_id);

-- Both join tables have a composite primary key, which covers only its leading
-- column. The trailing one is the side you search from when deleting the other
-- parent: bundle_products by product (products do get deleted, and the FK is
-- ON DELETE CASCADE, so the scan happens inside the delete), user_role by role.
-- The roles table is tiny and static, so that one is for the invariant rather
-- than for the plan — ForeignKeyIndexTest asserts no FK is left uncovered, and
-- an exception carried in a list is an exception nobody revisits.
CREATE INDEX IF NOT EXISTS idx_bundle_products_product  ON bundle_products (product_id);
CREATE INDEX IF NOT EXISTS idx_user_role_role           ON user_role (role_id);
