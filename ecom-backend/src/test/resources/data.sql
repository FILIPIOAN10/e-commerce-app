-- Seed data for integration tests
-- Hibernate creates tables first (ddl-auto=create-drop), then this runs

INSERT INTO roles (role_id, role_name) VALUES
    (1, 'ROLE_USER'),
    (2, 'ROLE_SELLER'),
    (3, 'ROLE_ADMIN')
ON CONFLICT DO NOTHING;

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

INSERT INTO user_role (user_id, role_id) VALUES
    (1, 1), (1, 2), (1, 3),
    (2, 1),
    (3, 2)
ON CONFLICT DO NOTHING;
