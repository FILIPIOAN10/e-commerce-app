-- =========================
-- INSERT ROLES
-- =========================
INSERT IGNORE INTO roles (role_id, role_name) VALUES (1, 'ROLE_USER');
INSERT IGNORE INTO roles (role_id, role_name) VALUES (2, 'ROLE_SELLER');
INSERT IGNORE INTO roles (role_id, role_name) VALUES (3, 'ROLE_ADMIN');

-- =========================
-- INSERT USERS
-- =========================

-- admin (password: adminPass)
INSERT IGNORE INTO users (user_id, username, email, password,password_hint)
VALUES (1, 'admin', 'admin@example.com',
'$2a$12$A.Gu/hG5ZPM8qSDl1/Q9aeSikYy.bHg/E.KMa8X91ubaJOPVBBGuK','adminXXXX');

-- user1 (password: password1)
INSERT IGNORE INTO users (user_id, username, email, password,password_hint)
VALUES (2, 'user1', 'user1@example.com',
'$2a$12$Smyyd9c9bDpI69K27xh7Rutyes.ki7jxjcUN2Ok/2xd.AQ9E9IIcC','passwordx');

-- seller1 (password: password2)
INSERT IGNORE INTO users (user_id, username, email, password,password_hint)
VALUES (3, 'seller1', 'seller1@example.com',
'$2a$12$bPLjTZ75BrBKJQz0gcDUZuO8czeDM21JIJYZYRtUK99xBkBX7WyUG','passwordx');

-- =========================
-- USER ROLES
-- =========================

-- admin → toate rolurile
INSERT IGNORE INTO user_role (user_id, role_id) VALUES (1, 1);
INSERT IGNORE INTO user_role (user_id, role_id) VALUES (1, 2);
INSERT IGNORE INTO user_role (user_id, role_id) VALUES (1, 3);

-- user1 → USER
INSERT IGNORE INTO user_role (user_id, role_id) VALUES (2, 1);

-- seller1 → SELLER
INSERT IGNORE INTO user_role (user_id, role_id) VALUES (3, 2);