package com.urlshortener.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Redis cache-aside helper for the redirect hot path.
 *
 * <p><b>Graceful degradation is the whole point:</b> every Redis operation is wrapped so
 * that if Redis is slow or down, a read returns "miss" (the caller falls through to
 * Postgres) and a write silently no-ops. A cache outage degrades latency, never
 * correctness or availability of the redirect. We never fail a redirect because the
 * cache is unavailable.
 */
@Component
public class ShortUrlCache {

    private static final Logger log = LoggerFactory.getLogger(ShortUrlCache.class);
    private static final String KEY_PREFIX = "u:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public ShortUrlCache(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public Optional<ResolvedTarget> get(String code) {
        try {
            String json = redis.opsForValue().get(KEY_PREFIX + code);
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, ResolvedTarget.class));
        } catch (Exception e) {
            // Redis down / unreachable / bad payload -> treat as a miss, fall through to DB.
            log.warn("Cache read failed for code={}, falling through to DB: {}", code, e.getMessage());
            return Optional.empty();
        }
    }

    public void put(String code, ResolvedTarget target, Duration ttl) {
        if (ttl.isZero() || ttl.isNegative()) {
            return; // nothing to cache (already expired)
        }
        try {
            String json = objectMapper.writeValueAsString(target);
            redis.opsForValue().set(KEY_PREFIX + code, json, ttl);
        } catch (Exception e) {
            // Cache population is best-effort; a failure here must not affect the request.
            log.warn("Cache write failed for code={}: {}", code, e.getMessage());
        }
    }

    public void evict(String code) {
        try {
            redis.delete(KEY_PREFIX + code);
        } catch (Exception e) {
            log.warn("Cache evict failed for code={}: {}", code, e.getMessage());
        }
    }
}
