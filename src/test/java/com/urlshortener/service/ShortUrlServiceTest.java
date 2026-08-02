package com.urlshortener.service;

import com.urlshortener.domain.ShortUrl;
import com.urlshortener.repository.ShortUrlRepository;
import com.urlshortener.service.security.UrlSafetyValidator;
import com.urlshortener.web.dto.CreateShortUrlRequest;
import com.urlshortener.web.error.AliasAlreadyExistsException;
import com.urlshortener.web.error.CodeGenerationException;
import com.urlshortener.web.error.InvalidUrlException;
import com.urlshortener.web.error.ShortUrlNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Deterministic tests of the service logic — especially the collision-retry path,
 * which is hard to trigger with real random codes but is the core correctness claim.
 * We simulate the database's UNIQUE-constraint rejection to prove the retry behaves.
 */
@ExtendWith(MockitoExtension.class)
class ShortUrlServiceTest {

    @Mock ShortUrlRepository repository;
    @Mock CodeGenerator codeGenerator;
    @Mock UrlSafetyValidator urlSafetyValidator;
    @Mock ShortUrlCache cache;

    @InjectMocks ShortUrlService service;

    private CreateShortUrlRequest generated(String url) {
        return new CreateShortUrlRequest(url, null, null);
    }

    @Test
    void retriesOnCollisionThenSucceeds() {
        when(codeGenerator.generate()).thenReturn("AAAAAAA", "BBBBBBB");
        // First insert collides (DB rejects), second succeeds.
        when(repository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate key"))
                .thenAnswer(inv -> inv.getArgument(0));

        ShortUrl result = service.create(generated("http://93.184.216.34/"));

        assertThat(result.getCode()).isEqualTo("BBBBBBB");
        verify(codeGenerator, times(2)).generate();      // it retried
        verify(repository, times(2)).saveAndFlush(any());
    }

    @Test
    void failsAfterExhaustingRetryBudget() {
        when(codeGenerator.generate()).thenReturn("AAAAAAA");
        when(repository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> service.create(generated("http://93.184.216.34/")))
                .isInstanceOf(CodeGenerationException.class);

        verify(repository, times(5)).saveAndFlush(any()); // bounded budget
    }

    @Test
    void customAliasCollision_isConflict_notRetried() {
        var request = new CreateShortUrlRequest("http://93.184.216.34/", "my-alias", null);
        when(repository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(AliasAlreadyExistsException.class);

        // A taken alias is deterministic — retrying can't help, so exactly one attempt.
        verify(repository, times(1)).saveAndFlush(any());
        verify(codeGenerator, never()).generate();
    }

    @Test
    void securityGateRunsBeforeAnyPersistence() {
        doThrow(new InvalidUrlException("blocked"))
                .when(urlSafetyValidator).validate(any());

        assertThatThrownBy(() -> service.create(generated("http://127.0.0.1/")))
                .isInstanceOf(InvalidUrlException.class);

        verify(repository, never()).saveAndFlush(any()); // never reached the DB
    }

    @Test
    void resolveUnknownCode_throwsNotFound() {
        when(repository.findByCode("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolve("nope"))
                .isInstanceOf(ShortUrlNotFoundException.class);
    }

    @Test
    void resolveExpiredCode_throwsNotFound() {
        ShortUrl expired = new ShortUrl("expird1", "http://93.184.216.34/", false,
                Instant.now().minusSeconds(60));
        when(repository.findByCode("expird1")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.resolve("expird1"))
                .isInstanceOf(ShortUrlNotFoundException.class);
    }

    @Test
    void update_revalidatesAndEvictsCache() {
        ShortUrl existing = new ShortUrl("abc1234", "http://93.184.216.34/", false, null);
        when(repository.findByCode("abc1234")).thenReturn(Optional.of(existing));

        ShortUrl result = service.update("abc1234", "http://example.org/new-target", false, null);

        assertThat(result.getLongUrl()).isEqualTo("http://example.org/new-target");
        verify(urlSafetyValidator).validate("http://example.org/new-target");
        verify(repository).save(existing);
        verify(cache).evict("abc1234");
    }

    @Test
    void update_changeExpiryFalse_leavesExpiryUntouched() {
        Instant originalExpiry = Instant.now().plusSeconds(999);
        ShortUrl existing = new ShortUrl("abc1234", "http://93.184.216.34/", false, originalExpiry);
        when(repository.findByCode("abc1234")).thenReturn(Optional.of(existing));

        ShortUrl result = service.update("abc1234", "http://example.org/new-target", false, 3600L);

        // expiresInSeconds is ignored when changeExpiry is false.
        assertThat(result.getExpiresAt()).isEqualTo(originalExpiry);
    }

    @Test
    void update_changeExpiryTrue_recomputesExpiry() {
        ShortUrl existing = new ShortUrl("abc1234", "http://93.184.216.34/", false, null);
        when(repository.findByCode("abc1234")).thenReturn(Optional.of(existing));

        Instant before = Instant.now();
        ShortUrl result = service.update("abc1234", "http://example.org/new-target", true, 3600L);

        assertThat(result.getExpiresAt()).isAfter(before.plusSeconds(3599));
    }

    @Test
    void update_changeExpiryTrueWithNullSeconds_clearsExpiry() {
        ShortUrl existing = new ShortUrl("abc1234", "http://93.184.216.34/", false,
                Instant.now().plusSeconds(999));
        when(repository.findByCode("abc1234")).thenReturn(Optional.of(existing));

        ShortUrl result = service.update("abc1234", "http://example.org/new-target", true, null);

        assertThat(result.getExpiresAt()).isNull();
    }

    @Test
    void update_unknownCode_throwsNotFound() {
        when(repository.findByCode("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update("nope", "http://93.184.216.34/", false, null))
                .isInstanceOf(ShortUrlNotFoundException.class);

        verify(cache, never()).evict(any());
    }

    @Test
    void update_unsafeUrl_isRejectedBeforeLookup() {
        doThrow(new InvalidUrlException("blocked"))
                .when(urlSafetyValidator).validate("http://127.0.0.1/");

        assertThatThrownBy(() -> service.update("abc1234", "http://127.0.0.1/", false, null))
                .isInstanceOf(InvalidUrlException.class);

        verify(repository, never()).findByCode(any());
    }
}
