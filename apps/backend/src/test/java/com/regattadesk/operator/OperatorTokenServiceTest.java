package com.regattadesk.operator;

import com.regattadesk.operator.events.OperatorTokenIssuedEvent;
import com.regattadesk.operator.events.OperatorTokenRevokedEvent;
import com.regattadesk.operator.events.OperatorTokenValidatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperatorTokenServiceTest {

    @Mock
    OperatorTokenRepository repository;

    private OperatorTokenService service;

    @BeforeEach
    void setUp() {
        service = new OperatorTokenService(repository, new OperatorTokenValidator());
    }

    @Test
    void issueToken_shouldPersistAndEmitIssuedEvent() {
        when(repository.existsByToken(any())).thenReturn(false);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        Instant validFrom = Instant.parse("2026-02-20T10:00:00Z");
        Instant validUntil = validFrom.plus(1, ChronoUnit.HOURS);

        OperatorToken token = service.issueToken(
            UUID.randomUUID(),
            null,
            "start-line",
            validFrom,
            validUntil
        );

        assertNotNull(token);
        assertNotNull(token.getToken());
        assertEquals(6, token.getPin().length());
        assertEquals("start-line", token.getStation());
        assertEquals(validFrom, token.getValidFrom());
        assertEquals(validUntil, token.getValidUntil());

        ArgumentCaptor<OperatorTokenIssuedEvent> issuedEventCaptor = ArgumentCaptor.forClass(OperatorTokenIssuedEvent.class);
        verify(repository).appendEvent(issuedEventCaptor.capture());
        OperatorTokenIssuedEvent event = issuedEventCaptor.getValue();
        assertEquals(token.getId(), event.getTokenId());
        assertEquals(token.getRegattaId(), event.getRegattaId());
        assertEquals("start-line", event.getStation());
        assertEquals(validFrom, event.getValidFrom());
        assertEquals(validUntil, event.getValidUntil());
        assertEquals("system", event.getPerformedBy());
    }

    @Test
    void validateToken_shouldEmitValidationEventForKnownToken() {
        UUID regattaId = UUID.randomUUID();
        OperatorToken token = new OperatorToken(
            UUID.randomUUID(),
            regattaId,
            null,
            "finish-line",
            "known-token",
            "123456",
            Instant.now().minus(1, ChronoUnit.HOURS),
            Instant.now().plus(1, ChronoUnit.HOURS),
            true,
            Instant.now(),
            Instant.now()
        );
        when(repository.findByToken("known-token")).thenReturn(Optional.of(token));

        OperatorTokenService.TokenValidationResult result = service.validateToken(
            "known-token",
            regattaId,
            "finish-line",
            null
        );

        assertTrue(result.getResult().isValid());
        ArgumentCaptor<OperatorTokenValidatedEvent> validatedEventCaptor = ArgumentCaptor.forClass(OperatorTokenValidatedEvent.class);
        verify(repository).appendEvent(validatedEventCaptor.capture());
        OperatorTokenValidatedEvent event = validatedEventCaptor.getValue();
        assertEquals(token.getId(), event.getTokenId());
        assertEquals(regattaId, event.getRegattaId());
        assertEquals("finish-line", event.getStation());
        assertTrue(event.isValidationSucceeded());
        assertEquals(OperatorTokenValidator.ValidationResult.VALID.getMessage(), event.getValidationReason());
    }

    @Test
    void revokeTokenForRegatta_shouldBeAtomicAndEmitEvent() {
        UUID regattaId = UUID.randomUUID();
        UUID tokenId = UUID.randomUUID();
        OperatorToken token = new OperatorToken(
            tokenId,
            regattaId,
            null,
            "results-desk",
            "token",
            "654321",
            Instant.now().minus(1, ChronoUnit.HOURS),
            Instant.now().plus(1, ChronoUnit.HOURS),
            true,
            Instant.now(),
            Instant.now()
        );

        when(repository.findById(tokenId)).thenReturn(Optional.of(token));
        when(repository.revoke(tokenId)).thenReturn(true);

        OperatorTokenService.RevokeResult result = service.revokeTokenForRegatta(tokenId, regattaId);

        assertEquals(OperatorTokenService.RevokeResult.REVOKED, result);
        ArgumentCaptor<OperatorTokenRevokedEvent> revokedEventCaptor = ArgumentCaptor.forClass(OperatorTokenRevokedEvent.class);
        verify(repository, atLeastOnce()).appendEvent(revokedEventCaptor.capture());
        OperatorTokenRevokedEvent event = revokedEventCaptor.getValue();
        assertEquals(tokenId, event.getTokenId());
        assertEquals(regattaId, event.getRegattaId());
        assertEquals("results-desk", event.getStation());
        assertEquals("revoked", event.getReason());
        assertEquals("system", event.getPerformedBy());
    }

    @Test
    void validateToken_shouldReturnInvalidTokenAndSkipAuditEvent_whenRegattaIdMissing() {
        OperatorTokenService.TokenValidationResult result = service.validateToken(
            "known-token",
            null,
            "finish-line",
            null
        );

        assertEquals(OperatorTokenValidator.ValidationResult.INVALID_TOKEN, result.getResult());
        assertEquals("Regatta ID is required", result.getMessage());
        verify(repository, never()).appendEvent(any(OperatorTokenValidatedEvent.class));
    }

    @Test
    void revokeTokenForRegatta_shouldNotRevokeOrEmitEvent_whenRegattaDoesNotMatch() {
        UUID tokenId = UUID.randomUUID();
        UUID actualRegattaId = UUID.randomUUID();
        UUID requestedRegattaId = UUID.randomUUID();
        OperatorToken token = new OperatorToken(
            tokenId,
            actualRegattaId,
            null,
            "results-desk",
            "token",
            "654321",
            Instant.parse("2026-02-20T09:00:00Z"),
            Instant.parse("2026-02-20T11:00:00Z"),
            true,
            Instant.parse("2026-02-20T09:00:00Z"),
            Instant.parse("2026-02-20T09:00:00Z")
        );
        when(repository.findById(tokenId)).thenReturn(Optional.of(token));

        OperatorTokenService.RevokeResult result = service.revokeTokenForRegatta(tokenId, requestedRegattaId);

        assertEquals(OperatorTokenService.RevokeResult.NOT_FOUND, result);
        verify(repository, never()).revoke(tokenId);
        verify(repository, never()).appendEvent(any());
    }
}
