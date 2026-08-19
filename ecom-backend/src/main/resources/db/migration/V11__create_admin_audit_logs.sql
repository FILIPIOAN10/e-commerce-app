CREATE TABLE IF NOT EXISTS admin_audit_logs (
    log_id BIGSERIAL PRIMARY KEY,
    admin_user_id BIGINT,
    admin_username VARCHAR(50),
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(50),
    entity_id VARCHAR(100),
    old_value VARCHAR(1000),
    new_value VARCHAR(1000),
    details VARCHAR(2000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_admin_audit_log_action ON admin_audit_logs (action);
CREATE INDEX IF NOT EXISTS idx_admin_audit_log_entity ON admin_audit_logs (entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_admin_audit_log_created_at ON admin_audit_logs (created_at);
