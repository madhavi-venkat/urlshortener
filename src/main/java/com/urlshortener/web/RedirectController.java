package com.urlshortener.web;

import com.urlshortener.service.ResolvedTarget;
import com.urlshortener.service.ShortUrlService;
import com.urlshortener.service.analytics.ClickRecorder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * Redirect endpoint — the read-heavy, latency-sensitive hot path.
 *
 * <p>The analytics hook is wired in from the first version: on a successful resolve we
 * fire an async, failure-isolated click record (which never blocks or breaks the
 * redirect), then return the redirect immediately.
 *
 * <p>Uses 302 (temporary) rather than 301 so every click reaches the service and
 * analytics stay accurate — a deliberate trade-off against browser-side caching.
 */
@RestController
@Tag(name = "Links")
public class RedirectController {

    private final ShortUrlService shortUrlService;
    private final ClickRecorder clickRecorder;

    public RedirectController(ShortUrlService shortUrlService, ClickRecorder clickRecorder) {
        this.shortUrlService = shortUrlService;
        this.clickRecorder = clickRecorder;
    }

    @GetMapping("/{code}")
    @Operation(
            summary = "Resolve a short code and redirect to its destination",
            description = "302 (not 301) so every click reaches the service and analytics stay "
                    + "accurate. Fires an async, failure-isolated click record (geo derived from "
                    + "IP, then the IP is discarded) before returning the redirect.")
    @ApiResponses({
            @ApiResponse(responseCode = "302", description = "Redirect to the destination URL"),
            @ApiResponse(responseCode = "404", description = "Unknown, deactivated, or expired code",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
    })
    public ResponseEntity<Void> redirect(@PathVariable String code, HttpServletRequest request) {
        ResolvedTarget target = shortUrlService.resolveForRedirect(code); // 404 if unknown/expired

        // Fire-and-forget analytics; IP is used only to derive geo, then discarded.
        clickRecorder.record(
                target.id(),
                request.getHeader("Referer"),
                request.getHeader("User-Agent"),
                clientIp(request));

        return ResponseEntity.status(HttpStatus.FOUND) // 302
                .location(URI.create(target.longUrl()))
                .build();
    }

    /** Best-effort client IP; respects a proxy's X-Forwarded-For first hop if present. */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
