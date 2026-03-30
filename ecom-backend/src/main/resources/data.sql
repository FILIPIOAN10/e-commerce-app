-- =========================
-- INSERT ROLES
-- =========================

INSERT INTO roles (role_name)
VALUES ('ROLE_USER')
ON DUPLICATE KEY UPDATE role_name=role_name;

INSERT INTO roles (role_name)
VALUES ('ROLE_SELLER')
ON DUPLICATE KEY UPDATE role_name=role_name;

INSERT INTO roles (role_name)
VALUES ('ROLE_ADMIN')
ON DUPLICATE KEY UPDATE role_name=role_name;


-- =========================
-- INSERT USERS
-- =========================

INSERT INTO users (username, email, password)
VALUES ('user1', 'user1@example.com',
'$2a$10$7QJzP2E8m3nYhRrj9KcGxO6lP7Hc8mN2WfYQ2rZ6WkUjPp8ZxH9aK')
ON DUPLICATE KEY UPDATE username=username;

INSERT INTO users (username, email, password)
VALUES ('seller1', 'seller1@example.com',
'$2a$10$E9sDf3Gh4JkLmN7OpQrStU6vWxYz8AbCdEfGhIjKlMnOpQrStUvWx')
ON DUPLICATE KEY UPDATE username=username;

INSERT INTO users (username, email, password)
VALUES ('admin', 'admin@example.com',
'$2a$10$XyZ12AbCdEfGhIjKlMnOpQrStUvWxYz1234567890abcdefghi')
ON DUPLICATE KEY UPDATE username=username;


-- =========================
-- USER_ROLE RELATIONS
-- =========================

-- user1 -> ROLE_USER
INSERT INTO user_role (user_id, role_id)
SELECT u.user_id, r.role_id
FROM users u, roles r
WHERE u.username='user1' AND r.role_name='ROLE_USER'
ON DUPLICATE KEY UPDATE user_id=user_id;

-- seller1 -> ROLE_SELLER
INSERT INTO user_role (user_id, role_id)
SELECT u.user_id, r.role_id
FROM users u, roles r
WHERE u.username='seller1' AND r.role_name='ROLE_SELLER'
ON DUPLICATE KEY UPDATE user_id=user_id;

-- admin -> ROLE_USER, ROLE_SELLER, ROLE_ADMIN
INSERT INTO user_role (user_id, role_id)
SELECT u.user_id, r.role_id
FROM users u, roles r
WHERE u.username='admin'
AND r.role_name IN ('ROLE_USER','ROLE_SELLER','ROLE_ADMIN')
ON DUPLICATE KEY UPDATE user_id=user_id;