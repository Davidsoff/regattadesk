package com.regattadesk.operator;

import com.regattadesk.operator.events.OperatorTokenIssuedEvent;
import com.regattadesk.operator.events.OperatorTokenValidatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

        OperatorToken token = service.issueToken(
            UUID.randomUUID(),
            null,
            "start-line",
            Instant.now(),
            Instant.now().plus(1, ChronoUnit.HOURS)
        );

        assertNotNull(token);
        assertNotNull(token.getToken());
        assertEquals(6, token.getPin().length());
        verify(repository).appendEvent(any(OperatorTokenIssuedEvent.class));
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
        verify(repository).appendEvent(any(OperatorTokenValidatedEvent.class));
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
        verify(repository, atLeastOnce()).appendEvent(any());
    }
}
