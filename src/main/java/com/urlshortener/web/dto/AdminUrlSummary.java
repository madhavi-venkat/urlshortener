package com.urlshortener.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * One row in the admin listing of all short URLs — the "how are we doing" view,
 * as opposed to {@link StatsResponse} which is the per-code drill-down.
 */
public record AdminUrlSummary(
        String code,
        String longUrl,
        @Schema(description = "True if the code was user-chosen rather than randomly generated.")
        boolean customAlias,
        boolean active,
        Instant createdAt,
        @Schema(description = "Bumped on any edit; equals createdAt for a never-edited link.")
        Instant updatedAt,
        @Schema(nullable = true)
        Instant expiresAt,
        long totalClicks
) {
}
