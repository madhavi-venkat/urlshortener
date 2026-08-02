package com.urlshortener.web.dto;

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
        String longUrl,

        @Pattern(regexp = "^[A-Za-z0-9_-]{3,16}$",
                message = "customAlias must be 3-16 chars of letters, digits, _ or -")
        String customAlias,

        Long expiresInSeconds
) {
}
