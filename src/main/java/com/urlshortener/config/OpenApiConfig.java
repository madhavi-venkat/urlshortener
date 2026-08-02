package com.urlshortener.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Metadata for the live OpenAPI doc springdoc generates from the controllers
 * (browsable at {@code /swagger-ui.html}, raw JSON at {@code /v3/api-docs}).
 * Mirrors the hand-written {@code openapi.yaml} at the repo root, which stays the
 * portable/offline reference; this is the same spec kept in sync with the code by
 * construction.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI urlShortenerOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("URL Shortener API")
                        .version("1.0.0")
                        .description("""
                                Create short links, redirect through them, and manage/analyze them \
                                from the admin dashboard. `/api/v1/**` is CORS-restricted to the \
                                configured frontend origin (app.cors.allowed-origins) and to \
                                GET/POST/PATCH. The `/{code}` redirect is a plain browser navigation, \
                                not a fetch/XHR target, so it is not CORS-gated."""))
                .tags(List.of(
                        new Tag().name("Links").description("Public link creation and redirection."),
                        new Tag().name("Admin").description(
                                "Dashboard/analytics/edit surface. No access control yet — anyone who "
                                        + "can reach the API can reach /api/v1/admin/**. Acceptable for "
                                        + "this prototype; HTTP Basic (or similar) in front of this path "
                                        + "is the natural next step before it's exposed anywhere but "
                                        + "localhost.")));
    }
}
