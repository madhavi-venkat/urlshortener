package com.urlshortener.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request to create a short URL.
 *
 * <p>Bean-validation runs at the controller boundary (fail fast, before any work):
 *  <ul>
 *    <li>longUrl required; deeper URL-safety checks happen in UrlSafetyValidator.</li>
 *    <li>customAlias optional; if present, constrained to a safe charset/length so a
 *        user can't inject path-breaking or oversized codes.</li>
 *    <li>expiresInSeconds optional; null = never expires.</li>
 *  </ul>
 */
public record CreateShortUrlRequest(

        @NotBlank(message = "longUrl is required")
        @Schema(description = "Must be http/https, not point at an internal/loopback address, "
                + "and stay under the configured max length.",
                example = "https://example.com/a/very/long/path")
        String longUrl,

        @Pattern(regexp = "^[A-Za-z0-9_-]{3,16}$",
                message = "customAlias must be 3-16 chars of letters, digits, _ or -")
        @Schema(description = "Optional user-chosen code. 3-16 chars of letters, digits, _ or -.",
                example = "launch-2026", nullable = true)
        String customAlias,

        @Schema(description = "Optional. Omit/null for a link that never expires.",
                example = "86400", nullable = true)
        Long expiresInSeconds
) {
}
