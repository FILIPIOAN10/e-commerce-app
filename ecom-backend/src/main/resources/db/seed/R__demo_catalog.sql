-- ==========================================================
--  Repeatable seed - demo catalogue for the Selenium suite
-- ==========================================================
--  Loaded ONLY when classpath:db/seed is on spring.flyway.locations, which is
--  set for the browser suite alone (.github/workflows/selenium.yml). It restores
--  the fixed category / products that V12 used to seed everywhere and V34 now
--  removes, so the Selenium tests that drive fixed URLs (/products/1) and expect
--  a known catalogue keep working. Products go to seller1 (user_id 3, from V2).
--
--  Idempotent: ON CONFLICT DO NOTHING, and it runs after every versioned
--  migration including V34, so re-running it is a no-op.

INSERT INTO categories (category_id, category_name) VALUES
    (1, 'Electronics')
ON CONFLICT DO NOTHING;

SELECT setval('categories_category_id_seq', COALESCE((SELECT MAX(category_id) FROM categories), 1), TRUE);

INSERT INTO products (
    product_id,
    product_name,
    image,
    description,
    tags,
    quantity,
    low_stock_threshold,
    price,
    discount,
    special_price,
    category_id,
    seller_id
) VALUES
    (1, 'Wireless Mouse', 'https://via.placeholder.com/300x200?text=Wireless+Mouse', 'Ergonomic wireless mouse with 2.4 GHz receiver.', 'mouse,wireless', 50, 10, 29.99, 0, 29.99, 1, 3),
    (2, 'Mechanical Keyboard', 'https://via.placeholder.com/300x200?text=Mechanical+Keyboard', 'RGB mechanical keyboard with blue switches.', 'keyboard,rgb', 30, 5, 89.99, 0, 89.99, 1, 3),
    (3, 'USB-C Hub', 'https://via.placeholder.com/300x200?text=USB-C+Hub', '7-in-1 USB-C hub with HDMI and card reader.', 'hub,usb-c', 0, 5, 49.99, 0, 49.99, 1, 3)
ON CONFLICT DO NOTHING;

SELECT setval('products_seq', COALESCE((SELECT MAX(product_id) FROM products), 1), TRUE);
