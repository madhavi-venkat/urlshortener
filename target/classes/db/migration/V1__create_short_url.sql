-- V1: core short_url table.
--
-- Design notes (defensible decisions):
--  * Surrogate BIGSERIAL id as PK: a small, stable key for future FK references
--    (e.g. an analytics/click table joins on id, not on the varchar code).
--  * code is UNIQUE NOT NULL: this UNIQUE constraint is the CORRECTNESS BACKSTOP
--    for code generation. Even if two concurrent requests generate the same random
--    code between an app-level check and the insert, the database rejects the second
--    insert. Uniqueness is guaranteed by the DB, not by hopeful application logic.
--  * custom_alias flags user-chosen codes (which share the same uniqueness path).
--  * expires_at NULL = never expires; a partial index keeps expiry scans cheap.
--  * active supports soft-delete/deactivation without losing the audit record.

CREATE TABLE short_url (
    id           BIGSERIAL   PRIMARY KEY,
    code         VARCHAR(16) NOT NULL,
    long_url     TEXT        NOT NULL,
    custom_alias BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at   TIMESTAMPTZ,
    active       BOOLEAN     NOT NULL DEFAULT TRUE,

    CONSTRAINT uq_short_url_code UNIQUE (code)
);

-- Partial index: only index rows that actually expire, keeping the index small.
CREATE INDEX idx_short_url_expires_at
    ON short_url (expires_at)
    WHERE expires_at IS NOT NULL;
