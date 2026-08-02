package com.urlshortener.web;

import com.urlshortener.domain.ShortUrl;
import com.urlshortener.service.ShortUrlService;
import com.urlshortener.web.dto.CreateShortUrlRequest;
import com.urlshortener.web.dto.CreateShortUrlResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public link-creation API. Analytics (per-code stats and the all-links admin view)
 * moved to {@link com.urlshortener.web.admin.AdminController} — it's an operational
 * concern, not something a link creator needs from this endpoint.
 */
@RestController
@RequestMapping("/api/v1/urls")
@Tag(name = "Links", description = "Public link creation and redirection.")
public class UrlController {

    private final ShortUrlService shortUrlService;
    private final String baseUrl;

    public UrlController(ShortUrlService shortUrlService,
                         @Value("${app.base-url}") String baseUrl) {
        this.shortUrlService = shortUrlService;
        this.baseUrl = baseUrl;
    }

    @PostMapping
    @Operation(
            summary = "Create a short URL",
            description = "Validates the destination (http/https only, SSRF-guarded against "
                    + "internal/loopback targets, bounded length) before shortening it. Provide "
                    + "customAlias to pick your own code; omit it for a randomly generated one.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Short URL created"),
            @ApiResponse(responseCode = "400",
                    description = "Missing/invalid/unsafe longUrl, or a malformed customAlias",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "customAlias is already taken",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "503",
                    description = "Could not generate a unique random code after retrying "
                            + "(code space exhausted)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
    })
    public ResponseEntity<CreateShortUrlResponse> create(
            @Valid @RequestBody CreateShortUrlRequest request) {

        ShortUrl created = shortUrlService.create(request);

        CreateShortUrlResponse body = new CreateShortUrlResponse(
                created.getCode(),
                baseUrl + "/" + created.getCode(),
                created.getLongUrl(),
                created.getExpiresAt());

        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }
}
