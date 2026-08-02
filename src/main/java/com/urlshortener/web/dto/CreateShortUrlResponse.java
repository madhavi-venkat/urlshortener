package com.urlshortener.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record CreateShortUrlResponse(
        @Schema(example = "aX9btQ2") String code,
        @Schema(example = "http://localhost:8080/aX9btQ2") String shortUrl,
        @Schema(example = "https://example.com/a/very/long/path") String longUrl,
        @Schema(nullable = true) Instant expiresAt
) {
}
