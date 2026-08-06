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
'$2a$12$A.Gu/hG5ZPM8qSDl1/Q9aeSikYy.bHg/E.KMa8X91ubaJOPVBBGuK', 'adminXXXX', false , null, true)
ON CONFLICT DO NOTHING;

-- user1 (password: password1)
INSERT INTO users (user_id, username, email, password, password_hint, two_factor_enabled, two_factor_secret, verified)
VALUES (2, 'user1', 'user1@example.com',
'$2a$12$Smyyd9c9bDpI69K27xh7Rutyes.ki7jxjcUN2Ok/2xd.AQ9E9IIcC', 'passwordx', false, null, true)
ON CONFLICT DO NOTHING;

-- seller1 (password: password2)
INSERT INTO users (user_id, username, email, password, password_hint, two_factor_enabled, two_factor_secret, verified)
VALUES (3, 'seller1', 'seller1@example.com',
'$2a$12$bPLjTZ75BrBKJQz0gcDUZuO8czeDM21JIJYZYRtUK99xBkBX7WyUG', 'passwordx', false, null, true)
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