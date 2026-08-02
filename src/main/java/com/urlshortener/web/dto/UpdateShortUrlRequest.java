package com.urlshortener.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Request to edit an existing short URL. The code/alias itself is immutable (it's the
 * path segment shared publicly); only where it points, and when it expires, can change.
 * {@code longUrl} is re-validated through
 * {@link com.urlshortener.service.security.UrlSafetyValidator} just like creation — an
 * edit is not a trusted shortcut around the safety gate.
 *
 * <p>Expiry is tri-state, matching what a form checkbox/dropdown naturally expresses:
 * {@code changeExpiry=false} means "leave it as it is" ({@code expiresInSeconds} is then
 * ignored); {@code changeExpiry=true} replaces the expiry with one computed from
 * {@code expiresInSeconds} seconds from now, or clears it (never expires) when that is
 * null. A plain nullable field alone can't distinguish "don't touch" from "clear it".
 */
public record UpdateShortUrlRequest(

        @NotBlank(message = "longUrl is required")
        @Schema(example = "https://example.com/updated-destination")
        String longUrl,

        @Schema(description = "false = leave the current expiry untouched (expiresInSeconds is "
                + "ignored). true = replace it.", defaultValue = "false")
        boolean changeExpiry,

        @Schema(description = "Only used when changeExpiry is true. Null clears the expiry "
                + "(never expires).", nullable = true)
        Long expiresInSeconds
) {
}
