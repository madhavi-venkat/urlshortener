-- V2: click_event table.
--
-- Analytics is a first-class, designed-in concern, not a later bolt-on: the table
-- exists as soon as the redirect path does, and the redirect emits events from its
-- first line.
--
-- PRIVACY DECISION (defensible, regulated-firm appropriate):
--  * We store DERIVED geo (country/region), never the raw IP address. The IP is used
--    transiently at ingestion to derive geo and is then discarded. This keeps click
--    analytics useful without retaining PII (raw IPs are personal data under GDPR and
--    similar frameworks). "We deliberately do not store IP addresses" is a stance we
--    can defend in a privacy review.
--  * short_url_id FK (not the varchar code) — joins on the small surrogate key.

CREATE TABLE click_event (
    id            BIGSERIAL   PRIMARY KEY,
    short_url_id  BIGINT      NOT NULL REFERENCES short_url (id),
    occurred_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    referrer      TEXT,
    user_agent    TEXT,
    geo_country   VARCHAR(2),   -- ISO country code, derived from IP then IP discarded
    geo_region    VARCHAR(64)
);

-- Analytics queries are "clicks for a given short_url over time" — index the FK.
CREATE INDEX idx_click_event_short_url_id ON click_event (short_url_id);
CREATE INDEX idx_click_event_occurred_at  ON click_event (occurred_at);
