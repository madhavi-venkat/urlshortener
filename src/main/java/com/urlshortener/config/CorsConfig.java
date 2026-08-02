package com.urlshortener.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS for the SPA frontend.
 *
 * <p>Deliberately scoped to the configured frontend origin(s) and the API path only —
 * not a wildcard. Allowing "*" on a service that performs redirects and mutations is an
 * unnecessary exposure; the allowed origin is configuration, not hardcoded.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final @NonNull String[] allowedOrigins;

    public CorsConfig(@Value("${app.cors.allowed-origins:http://localhost:5173}") @NonNull String[] allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void addCorsMappings(@NonNull CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PATCH")
                .allowedHeaders("*");
    }
}
