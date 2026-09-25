-- Speeds up the audit log filter by user and the "last login" of the employee list on large audit logs.
-- Portable SQL: runs unchanged on PostgreSQL and H2.

CREATE INDEX idx_audit_log_user ON audit_log (user_id, occurred_at);
CREATE INDEX idx_audit_log_action_user ON audit_log (action, user_id, occurred_at);
