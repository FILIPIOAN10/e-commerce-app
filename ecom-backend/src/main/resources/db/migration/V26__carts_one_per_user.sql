-- ==========================================================
-- One cart per user, enforced by the database
-- ==========================================================
-- User.cart is mapped @OneToOne and CartRepository.findCartByEmail returns a
-- single Cart, but carts.user_id carried only a FK — no unique constraint and
-- no index. createCart() reads, finds nothing, then inserts, so two concurrent
-- first-touch requests (a double-clicked "Add to cart", or the SPA loading the
-- cart and adding an item at the same moment) both saw no cart and both
-- inserted one. From that point findCartByEmail matched two rows and every
-- cart request for that user failed with IncorrectResultSizeDataAccessException
-- — unrecoverable without a manual DELETE in the database.
--
-- The unique index makes the loser of that race fail its INSERT instead, which
-- CartServiceImpl now catches and resolves by re-reading the winner's cart.
-- It doubles as the FK index carts.user_id never had.
--
-- Guest carts keep user_id NULL. Postgres treats NULLs as distinct in a unique
-- index, so any number of them coexist.

-- ---------- 1. Collapse duplicates that already exist ----------
-- Oldest cart per user wins; its siblings' contents are merged into it rather
-- than dropped, so nothing a customer put in a cart disappears.

CREATE TEMP TABLE cart_merge_map ON COMMIT DROP AS
SELECT c.cart_id AS loser_id,
       k.cart_id AS keeper_id
  FROM carts c
  JOIN (SELECT user_id, MIN(cart_id) AS cart_id
          FROM carts
         WHERE user_id IS NOT NULL
         GROUP BY user_id) k
    ON k.user_id = c.user_id
 WHERE c.cart_id <> k.cart_id;

-- A product the keeper already holds must not become a second line: cart_items
-- has no unique on (cart_id, product_id), but addProductToCart rejects a
-- duplicate product outright, so two lines for one product would be a state the
-- application can neither produce nor edit. Fold the quantities together.
UPDATE cart_items keep
   SET quantity = keep.quantity + dup.quantity
  FROM cart_items dup
  JOIN cart_merge_map m ON m.loser_id = dup.cart_id
 WHERE keep.cart_id = m.keeper_id
   AND keep.product_id = dup.product_id
   AND keep.saved_for_later = dup.saved_for_later;

DELETE FROM cart_items dup
 USING cart_merge_map m, cart_items keep
 WHERE dup.cart_id = m.loser_id
   AND keep.cart_id = m.keeper_id
   AND keep.product_id = dup.product_id
   AND keep.saved_for_later = dup.saved_for_later;

-- Everything left on a loser cart is a product the keeper does not have.
UPDATE cart_items ci
   SET cart_id = m.keeper_id
  FROM cart_merge_map m
 WHERE ci.cart_id = m.loser_id;

-- cart_reminder is unique on (cart_id, stage), so reminders cannot simply be
-- repointed. They are a record of mail already sent about a cart that is about
-- to stop existing; drop them and let the sweep re-evaluate the surviving cart.
DELETE FROM cart_reminder r
 USING cart_merge_map m
 WHERE r.cart_id = m.loser_id;

DELETE FROM carts c
 USING cart_merge_map m
 WHERE c.cart_id = m.loser_id;

-- ---------- 2. Re-total the carts that absorbed items ----------
-- Mirrors CartServiceImpl.recalculateCartTotal: saved-for-later lines are
-- excluded, so the stored total matches what checkout will charge.
UPDATE carts c
   SET total_price = COALESCE((
           SELECT ROUND(SUM(ci.product_price * ci.quantity), 2)
             FROM cart_items ci
            WHERE ci.cart_id = c.cart_id
              AND ci.saved_for_later = FALSE
       ), 0.00)
 WHERE c.cart_id IN (SELECT keeper_id FROM cart_merge_map);

-- ---------- 3. Make the race impossible from here on ----------
CREATE UNIQUE INDEX IF NOT EXISTS uk_carts_user ON carts (user_id);
