package com.urlshortener.service;

import com.urlshortener.domain.ShortUrl;
import com.urlshortener.repository.ShortUrlRepository;
import com.urlshortener.service.security.UrlSafetyValidator;
import com.urlshortener.web.dto.CreateShortUrlRequest;
import com.urlshortener.web.error.AliasAlreadyExistsException;
import com.urlshortener.web.error.CodeGenerationException;
import com.urlshortener.web.error.ShortUrlNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Core service: create a short URL, and resolve a code to its destination.
 *
 * <p>Collision handling is the notable design point. We generate a random code and
 * attempt the insert; the database's UNIQUE constraint is the authority. On the rare
 * collision we catch the integrity violation and retry with a fresh code, up to a
 * bounded budget. Custom aliases share the same uniqueness guarantee — a taken alias
 * surfaces as a clean 409 rather than a retry (retrying a user-chosen alias is
 * pointless; it won't change).
 */
@Service
public class ShortUrlService {

    private static final int MAX_GENERATION_ATTEMPTS = 5;

    /** Upper bound on how long a resolved target is cached, regardless of URL expiry. */
    private static final Duration MAX_CACHE_TTL = Duration.ofHours(1);

    private final ShortUrlRepository repository;
    private final CodeGenerator codeGenerator;
    private final UrlSafetyValidator urlSafetyValidator;
    private final ShortUrlCache cache;

    public ShortUrlService(ShortUrlRepository repository,
                           CodeGenerator codeGenerator,
                           UrlSafetyValidator urlSafetyValidator,
                           ShortUrlCache cache) {
        this.repository = repository;
        this.codeGenerator = codeGenerator;
        this.urlSafetyValidator = urlSafetyValidator;
        this.cache = cache;
    }

    @Transactional
    public ShortUrl create(CreateShortUrlRequest request) {
        // 1. Security/validation gate before any work.
        urlSafetyValidator.validate(request.longUrl());

        Instant expiresAt = resolveExpiry(request.expiresInSeconds());

        // 2. Custom alias path: user-chosen code, single uniqueness attempt.
        if (request.customAlias() != null && !request.customAlias().isBlank()) {
            return createWithAlias(request.longUrl(), request.customAlias(), expiresAt);
        }

        // 3. Generated path: random code with retry on the (rare) collision.
        return createWithGeneratedCode(request.longUrl(), expiresAt);
    }

    private ShortUrl createWithAlias(String longUrl, String alias, Instant expiresAt) {
        try {
            ShortUrl entity = new ShortUrl(alias, longUrl, true, expiresAt);
            return repository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException e) {
            // The UNIQUE constraint rejected it — alias is taken. Deterministic; no retry.
            throw new AliasAlreadyExistsException(alias);
        }
    }

    private ShortUrl createWithGeneratedCode(String longUrl, Instant expiresAt) {
        for (int attempt = 1; attempt <= MAX_GENERATION_ATTEMPTS; attempt++) {
            String code = codeGenerator.generate();
            try {
                ShortUrl entity = new ShortUrl(code, longUrl, false, expiresAt);
                return repository.saveAndFlush(entity);
            } catch (DataIntegrityViolationException e) {
                // Collision on 'code' — the DB caught it. Try a fresh code.
                // (Concurrency-safe: two threads generating the same code both hit the
                //  constraint; at most one insert wins, the other retries.)
            }
        }
        // Exhausting the budget implies either extreme load or a saturated code space —
        // a server condition, surfaced as 503, not a client error.
        throw new CodeGenerationException(
                "Could not generate a unique code after " + MAX_GENERATION_ATTEMPTS + " attempts.");
    }

    /**
     * Edits an existing code's destination and, optionally, its expiry. Re-runs the same
     * safety gate as creation, then evicts any cached redirect target so the change is
     * visible on the very next redirect rather than waiting out the cache TTL.
     */
    @Transactional
    public ShortUrl update(String code, String newLongUrl, boolean changeExpiry, Long expiresInSeconds) {
        urlSafetyValidator.validate(newLongUrl);

        ShortUrl entity = repository.findByCode(code)
                .orElseThrow(() -> new ShortUrlNotFoundException(code));
        entity.applyEdit(newLongUrl, changeExpiry, resolveExpiry(expiresInSeconds));
        repository.save(entity);

        cache.evict(code);
        return entity;
    }

    @Transactional(readOnly = true)
    public ShortUrl resolve(String code) {
        return repository.findByCode(code)
                .filter(ShortUrl::isResolvable)
                .orElseThrow(() -> new ShortUrlNotFoundException(code));
    }

    /**
     * Cached resolution for the redirect hot path (brownfield addition).
     *
     * <p>Cache-aside: check Redis; on a hit return immediately; on a miss delegate to the
     * existing {@link #resolve} (DB + expiry/active checks + 404), then populate the cache.
     * The cache TTL is capped AND clamped to the URL's own expiry, so a cached entry can
     * never outlive the URL's validity. If Redis is down, {@link ShortUrlCache} degrades to
     * misses/no-ops and this path still works against Postgres.
     */
    @Transactional(readOnly = true)
    public ResolvedTarget resolveForRedirect(String code) {
        Optional<ResolvedTarget> cached = cache.get(code);
        if (cached.isPresent()) {
            return cached.get();
        }
        ShortUrl entity = resolve(code); // reuse existing logic (404 on unknown/expired)
        ResolvedTarget target = new ResolvedTarget(entity.getId(), entity.getLongUrl());
        cache.put(code, target, cacheTtlFor(entity));
        return target;
    }

    /** TTL = min(MAX_CACHE_TTL, time until the URL expires); never caches past expiry. */
    private Duration cacheTtlFor(ShortUrl entity) {
        if (entity.getExpiresAt() == null) {
            return MAX_CACHE_TTL;
        }
        Duration untilExpiry = Duration.between(Instant.now(), entity.getExpiresAt());
        return untilExpiry.compareTo(MAX_CACHE_TTL) < 0 ? untilExpiry : MAX_CACHE_TTL;
    }

    private Instant resolveExpiry(Long expiresInSeconds) {
        if (expiresInSeconds == null) {
            return null;
        }
        return Instant.now().plusSeconds(expiresInSeconds);
    }
}
