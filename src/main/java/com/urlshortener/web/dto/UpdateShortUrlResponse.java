package com.urlshortener.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record UpdateShortUrlResponse(
        String code,
        String longUrl,
        @Schema(nullable = true) Instant expiresAt,
        Instant updatedAt
) {
}
