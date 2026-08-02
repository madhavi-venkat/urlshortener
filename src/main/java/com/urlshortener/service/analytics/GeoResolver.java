package com.urlshortener.service.analytics;

import org.springframework.stereotype.Component;

/**
 * Derives coarse geo (country/region) from a client IP.
 *
 * <p>This is a designed-in seam: the redirect path derives geo at ingestion and then
 * DISCARDS the raw IP, so we store location without retaining PII.
 *
 * <p>Prototype limitation (documented, not hidden): a production implementation plugs
 * in a GeoIP database (e.g. MaxMind GeoLite2). We deliberately do not bundle a ~60MB
 * GeoIP dataset into the prototype; the stub returns UNKNOWN, and swapping in a real
 * resolver is a single-bean change.
 */
public interface GeoResolver {

    Geo resolve(String ip);

    record Geo(String country, String region) {
        public static final Geo UNKNOWN = new Geo(null, null);
    }

    @Component
    class StubGeoResolver implements GeoResolver {
        @Override
        public Geo resolve(String ip) {
            // Seam only — real GeoIP lookup deferred (see class javadoc).
            return Geo.UNKNOWN;
        }
    }
}
