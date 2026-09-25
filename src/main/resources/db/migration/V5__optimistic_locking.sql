-- Two administrators editing the same employee or public holiday must not silently overwrite each other:
-- the version counts the changes and a save based on an older version is refused. Portable SQL: runs unchanged
-- on PostgreSQL and H2.

ALTER TABLE employee ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE public_holiday ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
