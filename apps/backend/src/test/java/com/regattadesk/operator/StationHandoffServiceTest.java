package com.regattadesk.operator;

import com.regattadesk.operator.events.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StationHandoffServiceTest {

    @Mock
    StationHandoffRepository repository;

    @Mock
    OperatorTokenService tokenService;

    private StationHandoffService service;

    @BeforeEach
    void setUp() {
        service = new StationHandoffService(repository, tokenService);
    }

    @Test
    void requestHandoff_shouldCreatePendingHandoffAndEmitEvent() {
        UUID regattaId = UUID.randomUUID();
        UUID tokenId = UUID.randomUUID();
        String station = "finish-line";
        String deviceId = "device-123";

        OperatorToken token = createValidToken(tokenId, regattaId, station);
        when(tokenService.getTokenById(tokenId)).thenReturn(Optional.of(token));
        when(repository.findPendingByToken(tokenId)).thenReturn(List.of());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        StationHandoff handoff = service.requestHandoff(regattaId, tokenId, station, deviceId);

        assertNotNull(handoff);
        assertEquals(regattaId, handoff.getRegattaId());
        assertEquals(tokenId, handoff.getTokenId());
        assertEquals(station, handoff.getStation());
        assertEquals(deviceId, handoff.getRequestingDeviceId());
        assertEquals(StationHandoff.HandoffStatus.PENDING, handoff.getStatus());
        assertNotNull(handoff.getPin());
        assertEquals(6, handoff.getPin().length());
        assertTrue(handoff.getExpiresAt().isAfter(handoff.getCreatedAt()));
        assertTrue(handoff.getPin().matches("[1-9]\\d{5}"));

        verify(repository).save(any(StationHandoff.class));
        verify(repository).appendEvent(any(StationHandoffRequestedEvent.class));
    }

    @Test
    void requestHandoff_shouldFailForInvalidToken() {
        UUID regattaId = UUID.randomUUID();
        UUID tokenId = UUID.randomUUID();
        String station = "finish-line";
        String deviceId = "device-123";

        when(tokenService.getTokenById(tokenId)).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, 
            () -> service.requestHandoff(regattaId, tokenId, station, deviceId));
        
        verify(repository, never()).save(any());
        verify(repository, never()).appendEvent(any());
    }

    @Test
    void requestHandoff_shouldRejectDuplicatePendingHandoff() {
        UUID regattaId = UUID.randomUUID();
        UUID tokenId = UUID.randomUUID();
        String station = "finish-line";
        String deviceId = "device-123";

        OperatorToken token = createValidToken(tokenId, regattaId, station);
        StationHandoff existing = createPendingHandoff(UUID.randomUUID(), tokenId, regattaId, station);

        when(tokenService.getTokenById(tokenId)).thenReturn(Optional.of(token));
        when(repository.findPendingByToken(tokenId)).thenReturn(List.of(existing));

        assertThrows(IllegalStateException.class,
            () -> service.requestHandoff(regattaId, tokenId, station, deviceId));

        verify(repository, never()).save(any());
        verify(repository, never()).appendEvent(any());
    }

    @Test
    void revealPin_shouldReturnPinForPendingHandoff() {
        UUID handoffId = UUID.randomUUID();
        StationHandoff pendingHandoff = createPendingHandoff(handoffId);
        
        when(repository.findById(handoffId)).thenReturn(Optional.of(pendingHandoff));

        StationHandoffService.HandoffPinRevealResult result = 
            service.revealPin(handoffId, "operator-device", false);

        assertNotNull(result);
        assertEquals(pendingHandoff.getPin(), result.pin());
        verify(repository).appendEvent(any(StationHandoffPinRevealedEvent.class));
    }

    @Test
    void revealPin_shouldFailForExpiredHandoff() {
        UUID handoffId = UUID.randomUUID();
        StationHandoff expiredHandoff = createExpiredHandoff(handoffId);
        
        when(repository.findById(handoffId)).thenReturn(Optional.of(expiredHandoff));

        assertThrows(IllegalStateException.class, 
            () -> service.revealPin(handoffId, "operator-device", false));
        
        verify(repository, never()).appendEvent(any());
    }

    @Test
    void completeHandoff_shouldSucceedWithCorrectPin() {
        UUID handoffId = UUID.randomUUID();
        StationHandoff pendingHandoff = createPendingHandoff(handoffId);
        String correctPin = pendingHandoff.getPin();
        
        when(repository.findById(handoffId)).thenReturn(Optional.of(pendingHandoff));
        when(repository.update(any())).thenAnswer(invocation -> invocation.getArgument(0));

        StationHandoffService.HandoffCompletionResult result = 
            service.completeHandoff(handoffId, correctPin);

        assertTrue(result.success());
        assertNotNull(result.handoff());
        assertEquals(StationHandoff.HandoffStatus.COMPLETED, result.handoff().getStatus());
        assertNotNull(result.handoff().getCompletedAt());
        
        ArgumentCaptor<StationHandoff> captor = ArgumentCaptor.forClass(StationHandoff.class);
        verify(repository).update(captor.capture());
        assertEquals(StationHandoff.HandoffStatus.COMPLETED, captor.getValue().getStatus());
        verify(repository).appendEvent(any(StationHandoffCompletedEvent.class));
    }

    @Test
    void completeHandoff_shouldFailWithIncorrectPin() {
        UUID handoffId = UUID.randomUUID();
        StationHandoff pendingHandoff = createPendingHandoff(handoffId);
        String wrongPin = "000000";
        
        when(repository.findById(handoffId)).thenReturn(Optional.of(pendingHandoff));

        StationHandoffService.HandoffCompletionResult result = 
            service.completeHandoff(handoffId, wrongPin);

        assertFalse(result.success());
        assertEquals("Invalid PIN", result.message());
        
        verify(repository, never()).update(any());
        verify(repository, never()).appendEvent(any());
    }

    @Test
    void completeHandoff_shouldFailForExpiredHandoff() {
        UUID handoffId = UUID.randomUUID();
        StationHandoff expired = createExpiredHandoff(handoffId);

        when(repository.findById(handoffId)).thenReturn(Optional.of(expired));

        StationHandoffService.HandoffCompletionResult result =
            service.completeHandoff(handoffId, expired.getPin());

        assertFalse(result.success());
        assertEquals("Handoff has expired", result.message());
        verify(repository, never()).update(any());
        verify(repository, never()).appendEvent(any());
    }

    @Test
    void cancelHandoff_shouldCancelPendingHandoff() {
        UUID handoffId = UUID.randomUUID();
        StationHandoff pendingHandoff = createPendingHandoff(handoffId);
        
        when(repository.findById(handoffId)).thenReturn(Optional.of(pendingHandoff));
        when(repository.update(any())).thenAnswer(invocation -> invocation.getArgument(0));

        boolean cancelled = service.cancelHandoff(handoffId, "user-cancelled");

        assertTrue(cancelled);
        
        ArgumentCaptor<StationHandoff> captor = ArgumentCaptor.forClass(StationHandoff.class);
        verify(repository).update(captor.capture());
        assertEquals(StationHandoff.HandoffStatus.CANCELLED, captor.getValue().getStatus());
        verify(repository).appendEvent(any(StationHandoffCancelledEvent.class));
    }

    @Test
    void listPendingHandoffs_shouldReturnPendingHandoffs() {
        UUID regattaId = UUID.randomUUID();
        String station = "finish-line";
        List<StationHandoff> pendingHandoffs = List.of(
            createPendingHandoff(UUID.randomUUID()),
            createPendingHandoff(UUID.randomUUID())
        );
        
        when(repository.findPendingByRegattaAndStation(regattaId, station))
            .thenReturn(pendingHandoffs);

        List<StationHandoff> result = service.listPendingHandoffs(regattaId, station);

        assertEquals(2, result.size());
        verify(repository).findPendingByRegattaAndStation(regattaId, station);
    }

    private OperatorToken createValidToken(UUID tokenId, UUID regattaId, String station) {
        Instant now = Instant.now();
        return new OperatorToken(
            tokenId,
            regattaId,
            null,
            station,
            "test-token-string",
            "123456",
            now.minus(1, ChronoUnit.HOURS),
            now.plus(1, ChronoUnit.HOURS),
            true,
            now,
            now
        );
    }

    private StationHandoff createPendingHandoff(UUID handoffId) {
        return createPendingHandoff(handoffId, UUID.randomUUID(), UUID.randomUUID(), "finish-line");
    }

    private StationHandoff createPendingHandoff(UUID handoffId, UUID tokenId, UUID regattaId, String station) {
        Instant now = Instant.now();
        return new StationHandoff(
            handoffId,
            regattaId,
            tokenId,
            station,
            "device-123",
            "123456",
            StationHandoff.HandoffStatus.PENDING,
            now,
            now.plus(10, ChronoUnit.MINUTES),
            null
        );
    }

    private StationHandoff createExpiredHandoff(UUID handoffId) {
        Instant past = Instant.now().minus(1, ChronoUnit.HOURS);
        return new StationHandoff(
            handoffId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            "finish-line",
            "device-123",
            "123456",
            StationHandoff.HandoffStatus.PENDING,
            past.minus(10, ChronoUnit.MINUTES),
            past,
            null
        );
    }
}
