-- ==========================================================
--  V34 - Remove the demo catalogue seeded by V12
-- ==========================================================
--  V12 inserted a default "Electronics" category (id 1) and three sample
--  products (ids 1-3) into every database. Only the Selenium suite still needs
--  them, and it now loads that data itself from classpath:db/seed
--  (R__demo_catalog, enabled by SPRING_FLYWAY_LOCATIONS in .github/workflows/
--  selenium.yml). Everywhere else the seed is unwanted, so drop it here.
--
--  Guards:
--    * name match, so a row someone legitimately created at one of these ids
--      after a wipe is left alone;
--    * the product delete is skipped for any id that a real order references,
--      to keep order history intact (that database can be cleaned by hand).
--  The seed's own R__demo_catalog re-inserts the same rows after this runs when
--  db/seed is on the location list, so /products/1 still resolves under Selenium.

DELETE FROM cart_items             WHERE product_id IN (1, 2, 3);
DELETE FROM wishlists              WHERE product_id IN (1, 2, 3);
DELETE FROM product_images         WHERE product_id IN (1, 2, 3);
DELETE FROM product_questions      WHERE product_id IN (1, 2, 3);
DELETE FROM promo_campaign_products WHERE product_id IN (1, 2, 3);
DELETE FROM reviews                WHERE product_id IN (1, 2, 3);

DELETE FROM products p
 WHERE p.product_id IN (1, 2, 3)
   AND p.product_name IN ('Wireless Mouse', 'Mechanical Keyboard', 'USB-C Hub')
   AND NOT EXISTS (SELECT 1 FROM order_items oi WHERE oi.product_id = p.product_id);

DELETE FROM categories c
 WHERE c.category_id = 1
   AND c.category_name = 'Electronics'
   AND NOT EXISTS (SELECT 1 FROM products p WHERE p.category_id = c.category_id);
