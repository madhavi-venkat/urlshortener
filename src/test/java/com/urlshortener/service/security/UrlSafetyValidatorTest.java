package com.urlshortener.service.security;

import com.urlshortener.web.error.InvalidUrlException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Security tests. Uses literal IP hosts so the suite is hermetic (no DNS/network).
 */
class UrlSafetyValidatorTest {

    private final UrlSafetyValidator validator = new UrlSafetyValidator(2048);

    @ParameterizedTest
    @ValueSource(strings = {
            "http://93.184.216.34/",     // public IP, http
            "https://93.184.216.34/path?q=1"
    })
    void acceptsValidPublicUrls(String url) {
        assertThatCode(() -> validator.validate(url)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "javascript:alert(1)",       // dangerous scheme
            "data:text/html,<script>",   // dangerous scheme
            "file:///etc/passwd",        // local file scheme
            "ftp://93.184.216.34/"       // non-http scheme
    })
    void rejectsUnsafeSchemes(String url) {
        assertThatThrownBy(() -> validator.validate(url))
                .isInstanceOf(InvalidUrlException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://127.0.0.1/",         // loopback
            "http://10.0.0.1/",          // private (10/8)
            "http://192.168.1.1/",       // private (192.168/16)
            "http://169.254.169.254/",   // link-local / cloud metadata
            "http://0.0.0.0/"            // wildcard
    })
    void rejectsInternalTargets_ssrfGuard(String url) {
        assertThatThrownBy(() -> validator.validate(url))
                .isInstanceOf(InvalidUrlException.class);
    }

    @Test
    void rejectsBlankAndMalformed() {
        assertThatThrownBy(() -> validator.validate(""))
                .isInstanceOf(InvalidUrlException.class);
        assertThatThrownBy(() -> validator.validate("not a url"))
                .isInstanceOf(InvalidUrlException.class);
    }

    @Test
    void rejectsOversizedUrl() {
        String tooLong = "http://93.184.216.34/" + "a".repeat(3000);
        assertThatThrownBy(() -> validator.validate(tooLong))
                .isInstanceOf(InvalidUrlException.class);
    }
}
