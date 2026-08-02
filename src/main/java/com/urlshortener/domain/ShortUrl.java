package com.urlshortener.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A single short-code -> long-URL mapping.
 *
 * <p>The {@code code} column is unique at the database level; that constraint is the
 * authoritative guard against code collisions (see V1 migration). Application code
 * generates candidate codes, but the database is what guarantees uniqueness.
 */
@Entity
@Table(name = "short_url")
public class ShortUrl {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 16)
    private String code;

    @Column(name = "long_url", nullable = false, columnDefinition = "text")
    private String longUrl;

    @Column(name = "custom_alias", nullable = false)
    private boolean customAlias;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean active;

    protected ShortUrl() {
        // JPA
    }

    public ShortUrl(String code, String longUrl, boolean customAlias, Instant expiresAt) {
        this.code = code;
        this.longUrl = longUrl;
        this.customAlias = customAlias;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
        this.expiresAt = expiresAt;
        this.active = true;
    }

    /** A code is resolvable only if it is active and not past its expiry. */
    public boolean isResolvable() {
        if (!active) {
            return false;
        }
        return expiresAt == null || expiresAt.isAfter(Instant.now());
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getLongUrl() {
        return longUrl;
    }

    public boolean isCustomAlias() {
        return customAlias;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isActive() {
        return active;
    }

    public void deactivate() {
        this.active = false;
    }

    /**
     * Repoints an existing code at a new destination and, optionally, a new expiry.
     * The code itself never changes. {@code changeExpiry=false} leaves the current
     * expiry untouched (the caller didn't ask to change it); {@code true} replaces it
     * with {@code newExpiresAt}, which may itself be null (never expires).
     */
    public void applyEdit(String longUrl, boolean changeExpiry, Instant newExpiresAt) {
        this.longUrl = longUrl;
        if (changeExpiry) {
            this.expiresAt = newExpiresAt;
        }
        this.updatedAt = Instant.now();
    }
}
