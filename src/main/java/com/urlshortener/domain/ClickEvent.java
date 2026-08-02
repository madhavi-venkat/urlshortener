package com.urlshortener.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A single recorded click on a short URL.
 *
 * <p>Deliberately stores derived geo only — never a raw IP address (see V2 migration
 * and GeoResolver). References the short_url by its surrogate id.
 */
@Entity
@Table(name = "click_event")
public class ClickEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "short_url_id", nullable = false)
    private Long shortUrlId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column
    private String referrer;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "geo_country", length = 2)
    private String geoCountry;

    @Column(name = "geo_region", length = 64)
    private String geoRegion;

    protected ClickEvent() {
        // JPA
    }

    public ClickEvent(Long shortUrlId, String referrer, String userAgent,
                      String geoCountry, String geoRegion) {
        this.shortUrlId = shortUrlId;
        this.occurredAt = Instant.now();
        this.referrer = referrer;
        this.userAgent = userAgent;
        this.geoCountry = geoCountry;
        this.geoRegion = geoRegion;
    }

    public Long getId() {
        return id;
    }

    public Long getShortUrlId() {
        return shortUrlId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
