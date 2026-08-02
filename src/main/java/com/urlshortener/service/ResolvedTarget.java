package com.urlshortener.service;

/**
 * The minimal information the redirect hot path needs: the destination and the id
 * (for analytics). Deliberately narrower than the full ShortUrl entity — a smaller,
 * stable cache payload and a clear boundary for what the redirect actually depends on.
 */
public record ResolvedTarget(Long id, String longUrl) {
}
