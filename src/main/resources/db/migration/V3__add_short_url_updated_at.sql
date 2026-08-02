-- V3: track when a short_url row was last changed (destination and/or expiry edits),
-- distinct from created_at. Backfills existing rows to their created_at, since "never
-- edited" is accurately represented as "last touched at creation".

ALTER TABLE short_url
    ADD COLUMN updated_at TIMESTAMPTZ;

UPDATE short_url SET updated_at = created_at;

ALTER TABLE short_url
    ALTER COLUMN updated_at SET NOT NULL,
    ALTER COLUMN updated_at SET DEFAULT now();
