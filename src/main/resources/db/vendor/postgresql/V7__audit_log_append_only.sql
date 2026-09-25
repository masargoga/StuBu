-- The audit log is append-only. The application never changes or deletes an entry, and now the database refuses it
-- too, so a bug, a careless script or a compromised application account cannot rewrite history. (The owner of the
-- table can still drop the triggers on purpose; also withhold UPDATE and DELETE on audit_log from the application's
-- database user, see docs/operations.md.)
--
-- PostgreSQL only: this folder is read only on PostgreSQL (spring.flyway.locations, {vendor}); H2 has no equivalent.
-- A future retention job that removes old entries has to drop these triggers deliberately, in its own migration.

CREATE FUNCTION audit_log_reject_change() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'audit_log is append-only: % is not allowed', TG_OP USING ERRCODE = 'restrict_violation';
END;
$$;

CREATE TRIGGER audit_log_no_update_delete
    BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION audit_log_reject_change();

CREATE TRIGGER audit_log_no_truncate
    BEFORE TRUNCATE ON audit_log
    FOR EACH STATEMENT EXECUTE FUNCTION audit_log_reject_change();
