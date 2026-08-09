-- =========================
-- INSERT ROLES
-- =========================
INSERT INTO roles (role_id, role_name) VALUES (1, 'ROLE_USER') ON CONFLICT DO NOTHING;
INSERT INTO roles (role_id, role_name) VALUES (2, 'ROLE_SELLER') ON CONFLICT DO NOTHING;
INSERT INTO roles (role_id, role_name) VALUES (3, 'ROLE_ADMIN') ON CONFLICT DO NOTHING;

-- =========================
-- INSERT USERS
-- =========================

-- admin (password: adminPass)
INSERT INTO users (user_id, username, email, password, password_hint, two_factor_enabled, two_factor_secret, verified)
VALUES (1, 'admin', 'admin@example.com',
'$2a$12$fePvyzd6rKHwehlo8xWQJeT5ZYZ7hWuwQXNz3EIUC39U3LCbAENl.', 'adminXXXX', false, null, true)
ON CONFLICT DO NOTHING;

-- user1 (password: password1)
INSERT INTO users (user_id, username, email, password, password_hint, two_factor_enabled, two_factor_secret, verified)
VALUES (2, 'user1', 'user1@example.com',
'$2a$12$pMgTY4iPzekJmDAPbKq1P.a7uO3vw/q9s574XqyM97Hod5kbPxWHO', 'passwordx', false, null, true)
ON CONFLICT DO NOTHING;

-- seller1 (password: password2)
INSERT INTO users (user_id, username, email, password, password_hint, two_factor_enabled, two_factor_secret, verified)
VALUES (3, 'seller1', 'seller1@example.com',
'$2a$12$R8TcA9biG5QxT.pizGfskufJQMsa5h1QWscWA9i24TW9ABFj65V2i', 'passwordx', false, null, true)
ON CONFLICT DO NOTHING;

-- =========================
-- USER ROLES
-- =========================

-- admin → toate rolurile
INSERT INTO user_role (user_id, role_id) VALUES (1, 1) ON CONFLICT DO NOTHING;
INSERT INTO user_role (user_id, role_id) VALUES (1, 2) ON CONFLICT DO NOTHING;
INSERT INTO user_role (user_id, role_id) VALUES (1, 3) ON CONFLICT DO NOTHING;

-- user1 → USER
INSERT INTO user_role (user_id, role_id) VALUES (2, 1) ON CONFLICT DO NOTHING;

-- seller1 → SELLER
INSERT INTO user_role (user_id, role_id) VALUES (3, 2) ON CONFLICT DO NOTHING;

-- =========================
-- RESET SEQUENCES
-- =========================
SELECT setval('users_user_id_seq', (SELECT MAX(user_id) FROM users));
SELECT setval('roles_role_id_seq', (SELECT MAX(role_id) FROM roles));