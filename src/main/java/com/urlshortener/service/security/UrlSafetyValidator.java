package com.urlshortener.service.security;

import com.urlshortener.web.error.InvalidUrlException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.IDN;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Set;

/**
 * Validates a submitted URL before it is ever shortened. Security is built into the
 * create path from the first endpoint — not added later.
 *
 * <p>A URL shortener is a natural abuse vector, so we guard against:
 *  <ul>
 *    <li><b>Bad schemes</b> — only http/https; reject javascript:, data:, file:, etc.</li>
 *    <li><b>SSRF / internal targets</b> — reject loopback, private, link-local and the
 *        cloud metadata address, so the service can't be used to reach internal infra
 *        or leak internal endpoints.</li>
 *    <li><b>Oversized input</b> — bounded length.</li>
 *  </ul>
 *
 * <p>Note: we intentionally do NOT block arbitrary <i>external</i> destinations — a
 * shortener legitimately redirects anywhere on the public internet. The guard is about
 * scheme safety and internal-target protection, not general destination filtering.
 */
@Component
public class UrlSafetyValidator {

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");
    private final int maxUrlLength;

    public UrlSafetyValidator(@Value("${app.max-url-length:2048}") int maxUrlLength) {
        this.maxUrlLength = maxUrlLength;
    }

    public void validate(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new InvalidUrlException("URL must not be empty.");
        }
        if (rawUrl.length() > maxUrlLength) {
            throw new InvalidUrlException("URL exceeds maximum length of " + maxUrlLength + ".");
        }

        final URI uri;
        try {
            uri = URI.create(rawUrl.trim());
        } catch (IllegalArgumentException e) {
            throw new InvalidUrlException("URL is not well-formed.");
        }

        String scheme = uri.getScheme();
        if (scheme == null || !ALLOWED_SCHEMES.contains(scheme.toLowerCase())) {
            throw new InvalidUrlException("Only http and https URLs are allowed.");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new InvalidUrlException("URL must include a host.");
        }

        rejectInternalTargets(host);
    }

    /**
     * Rejects hosts that resolve to loopback, private, link-local, or wildcard
     * addresses — the SSRF guard. Note: this is a best-effort check at create time;
     * DNS rebinding at redirect time is a known residual risk (documented in the
     * risk register), out of scope for the prototype.
     */
    private void rejectInternalTargets(String host) {
        String normalizedHost = IDN.toASCII(host).toLowerCase();
        try {
            for (InetAddress addr : InetAddress.getAllByName(normalizedHost)) {
                if (addr.isLoopbackAddress()
                        || addr.isAnyLocalAddress()
                        || addr.isLinkLocalAddress()
                        || addr.isSiteLocalAddress()) {
                    throw new InvalidUrlException("URLs pointing to internal addresses are not allowed.");
                }
            }
        } catch (UnknownHostException e) {
            throw new InvalidUrlException("URL host could not be resolved.");
        }
    }
}
