-- ==========================================================
--  V2 - Seed roles and demo users
-- ==========================================================
--  Replaces the former src/main/resources/data.sql.
--  Idempotent: safe to apply to a database that already
--  contains these rows (e.g. one previously seeded by data.sql).
--
--  Demo credentials:
--    admin   / adminPass
--    user1   / password1
--    seller1 / password2
-- ==========================================================

-- ---------- roles ----------
INSERT INTO roles (role_id, role_name) VALUES
    (1, 'ROLE_USER'),
    (2, 'ROLE_SELLER'),
    (3, 'ROLE_ADMIN')
ON CONFLICT DO NOTHING;

-- ---------- users ----------
INSERT INTO users (user_id, username, email, password, password_hint, two_factor_enabled, two_factor_secret, verified)
VALUES (1, 'admin', 'admin@example.com',
        '$2a$12$fePvyzd6rKHwehlo8xWQJeT5ZYZ7hWuwQXNz3EIUC39U3LCbAENl.', 'adminXXXX', FALSE, NULL, TRUE)
ON CONFLICT DO NOTHING;

INSERT INTO users (user_id, username, email, password, password_hint, two_factor_enabled, two_factor_secret, verified)
VALUES (2, 'user1', 'user1@example.com',
        '$2a$12$pMgTY4iPzekJmDAPbKq1P.a7uO3vw/q9s574XqyM97Hod5kbPxWHO', 'passwordx', FALSE, NULL, TRUE)
ON CONFLICT DO NOTHING;

INSERT INTO users (user_id, username, email, password, password_hint, two_factor_enabled, two_factor_secret, verified)
VALUES (3, 'seller1', 'seller1@example.com',
        '$2a$12$R8TcA9biG5QxT.pizGfskufJQMsa5h1QWscWA9i24TW9ABFj65V2i', 'passwordx', FALSE, NULL, TRUE)
ON CONFLICT DO NOTHING;

-- ---------- user_role ----------
-- admin holds all three roles; user1 is a customer; seller1 is a seller.
INSERT INTO user_role (user_id, role_id) VALUES
    (1, 1), (1, 2), (1, 3),
    (2, 1),
    (3, 2)
ON CONFLICT DO NOTHING;

-- ---------- realign identity sequences ----------
-- The explicit ids above bypass the sequences, so advance them past
-- the seeded rows. COALESCE keeps this safe on an empty table.
SELECT setval('roles_role_id_seq', COALESCE((SELECT MAX(role_id) FROM roles), 1), TRUE);
SELECT setval('users_user_id_seq', COALESCE((SELECT MAX(user_id) FROM users), 1), TRUE);
