package com.urlshortener.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Generates random short codes from a Base62 alphabet.
 *
 * <p>Design choice: random generation (not a sequential counter) so codes are not
 * enumerable. Collisions are theoretically possible but astronomically rare in a
 * 62^7 space; correctness does NOT rely on that rarity — the database's UNIQUE
 * constraint on {@code short_url.code} is the authoritative guard, and the service
 * layer retries on the rare constraint violation. See ShortUrlService#create.
 */
@Component
public class CodeGenerator {

    private static final String ALPHABET =
            "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

    // SecureRandom: codes shouldn't be predictable. Thread-safe for concurrent use.
    private final SecureRandom random = new SecureRandom();
    private final int codeLength;

    public CodeGenerator(@Value("${app.code-length:7}") int codeLength) {
        this.codeLength = codeLength;
    }

    public String generate() {
        StringBuilder sb = new StringBuilder(codeLength);
        for (int i = 0; i < codeLength; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
