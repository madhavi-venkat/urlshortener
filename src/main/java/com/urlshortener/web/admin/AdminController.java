package com.urlshortener.web.admin;

import com.urlshortener.domain.ShortUrl;
import com.urlshortener.service.ShortUrlService;
import com.urlshortener.service.analytics.StatsPeriod;
import com.urlshortener.service.analytics.StatsService;
import com.urlshortener.web.dto.AdminUrlSummary;
import com.urlshortener.web.dto.StatsResponse;
import com.urlshortener.web.dto.UpdateShortUrlRequest;
import com.urlshortener.web.dto.UpdateShortUrlResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin-facing analytics: the aggregate "all links" view plus the per-code drill-down
 * that used to live on the public {@code /api/v1/urls} API. Grouped here because both
 * are operational/reporting concerns, not something a link creator needs.
 *
 * <p><b>Limitation (documented, not fixed here):</b> this module has no access control
 * yet — anyone who can reach the API can reach {@code /api/v1/admin/**}. Acceptable for
 * this prototype; adding auth (e.g. HTTP Basic in front of this path) is the natural
 * next step before this is exposed anywhere but localhost.
 */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin", description = "Dashboard/analytics/edit surface. No access control yet — "
        + "anyone who can reach the API can reach /api/v1/admin/**. Acceptable for this "
        + "prototype; HTTP Basic (or similar) in front of this path is the natural next step "
        + "before it's exposed anywhere but localhost.")
public class AdminController {

    private final StatsService statsService;
    private final ShortUrlService shortUrlService;

    public AdminController(StatsService statsService, ShortUrlService shortUrlService) {
        this.statsService = statsService;
        this.shortUrlService = shortUrlService;
    }

    @GetMapping("/urls")
    @Operation(summary = "List every short URL",
            description = "Newest first, each with its all-time click total.")
    public List<AdminUrlSummary> listUrls() {
        return statsService.listAll();
    }

    @GetMapping("/urls/{code}")
    @Operation(summary = "Get one short URL's details",
            description = "Used to pre-fill the edit form.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "Unknown code",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
    })
    public AdminUrlSummary getUrl(@PathVariable String code) {
        return statsService.summaryFor(code);
    }

    @GetMapping("/urls/{code}/stats")
    @Operation(summary = "Analytics for one short code",
            description = "Total clicks (all-time), a geo breakdown, and a click count bucketed "
                    + "by day/week/month for the requested period.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "400",
                    description = "Invalid period value (not one of DAY, WEEK, MONTH)"),
            @ApiResponse(responseCode = "404", description = "Unknown code",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
    })
    public StatsResponse stats(@PathVariable String code,
                               @Parameter(description = "Bucket granularity for clicksByPeriod")
                               @RequestParam(defaultValue = "DAY") StatsPeriod period) {
        return statsService.statsFor(code, period);
    }

    @PatchMapping("/urls/{code}")
    @Operation(
            summary = "Edit a short URL's destination and/or expiry",
            description = "The code/alias itself is immutable. longUrl is re-validated through "
                    + "the same safety gate as creation. Expiry is tri-state: changeExpiry=false "
                    + "leaves the current expiry untouched (expiresInSeconds is then ignored); "
                    + "changeExpiry=true replaces it — expiresInSeconds=null clears it (never "
                    + "expires), otherwise it's recomputed as now + expiresInSeconds. Evicts any "
                    + "cached redirect target so the change is visible on the very next redirect.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Updated"),
            @ApiResponse(responseCode = "400", description = "Missing/invalid/unsafe longUrl",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Unknown code",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
    })
    public UpdateShortUrlResponse update(@PathVariable String code,
                                         @Valid @RequestBody UpdateShortUrlRequest request) {
        ShortUrl updated = shortUrlService.update(
                code, request.longUrl(), request.changeExpiry(), request.expiresInSeconds());
        return new UpdateShortUrlResponse(
                updated.getCode(), updated.getLongUrl(), updated.getExpiresAt(), updated.getUpdatedAt());
    }
}
