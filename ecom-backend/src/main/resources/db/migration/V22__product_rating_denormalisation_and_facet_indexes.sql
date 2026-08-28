-- Faceted search.
--
-- Filtering on rating meant averaging the reviews table per product on every
-- query — impossible to index and impossible to bucket cheaply. The average and
-- the count now live on products, recomputed from the reviews table whenever a
-- review changes (see ProductRepository.refreshRatingAggregate). Recomputed,
-- not incremented: a derived value that is only ever adjusted will eventually
-- drift, one that is re-derived cannot.
--
-- Note that SortWhitelist.PRODUCT already advertised "averageRating" as a
-- sortable property, which until now resolved to nothing. It works from here.

ALTER TABLE products
    ADD COLUMN IF NOT EXISTS average_rating DOUBLE PRECISION NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS review_count   INTEGER          NOT NULL DEFAULT 0;

-- Backfill from what the reviews table already holds.
UPDATE products p
SET average_rating = COALESCE(r.avg_rating, 0),
    review_count   = COALESCE(r.review_count, 0)
FROM (
    SELECT product_id, AVG(rating) AS avg_rating, COUNT(*) AS review_count
    FROM reviews
    GROUP BY product_id
) r
WHERE p.product_id = r.product_id;

-- The facet aggregates group and filter on these four columns. special_price is
-- the one the customer actually pays, so it is the one the price facet filters
-- on; price would band products by a number nobody is charged.
CREATE INDEX IF NOT EXISTS idx_products_special_price   ON products (special_price);
CREATE INDEX IF NOT EXISTS idx_products_average_rating  ON products (average_rating);
CREATE INDEX IF NOT EXISTS idx_products_quantity        ON products (quantity);

-- Category is the facet most often combined with a price range; the composite
-- serves "this category, in this band" without a second lookup.
CREATE INDEX IF NOT EXISTS idx_products_category_price  ON products (category_id, special_price);
