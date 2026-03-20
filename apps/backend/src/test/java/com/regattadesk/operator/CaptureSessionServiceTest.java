package com.regattadesk.operator;

import com.regattadesk.operator.events.CaptureSessionClosedEvent;
import com.regattadesk.operator.events.CaptureSessionStartedEvent;
import com.regattadesk.operator.events.CaptureSessionSyncStateUpdatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CaptureSessionServiceTest {

    @Mock
    CaptureSessionRepository repository;

    private CaptureSessionService service;

    @BeforeEach
    void setUp() {
        service = new CaptureSessionService(repository);
    }

    // ---- startSession tests -------------------------------------------------

    @Test
    void startSession_shouldPersistSessionAndEmitEvent() {
        UUID regattaId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CaptureSession session = service.startSession(
                regattaId, blockId, "finish-line", "device-42",
                CaptureSession.SessionType.finish,
                Instant.now(), -50L, 25, "operator");

        assertNotNull(session.getId());
        assertEquals(regattaId, session.getRegattaId());
        assertEquals(blockId, session.getBlockId());
        assertEquals("finish-line", session.getStation());
        assertEquals("device-42", session.getDeviceId());
        assertEquals(CaptureSession.SessionType.finish, session.getSessionType());
        assertEquals(CaptureSession.SessionState.open, session.getState());
        assertEquals(25, session.getFps());
        assertEquals(-50L, session.getDeviceMonotonicOffsetMs());
        assertTrue(session.isSynced());
        assertFalse(session.isDriftExceededThreshold());
        assertNull(session.getClosedAt());

        verify(repository).save(any(CaptureSession.class));

        ArgumentCaptor<CaptureSessionStartedEvent> cap =
                ArgumentCaptor.forClass(CaptureSessionStartedEvent.class);
        verify(repository).appendEvent(cap.capture());

        CaptureSessionStartedEvent event = cap.getValue();
        assertEquals(session.getId(), event.captureSessionId());
        assertEquals(regattaId, event.regattaId());
        assertEquals("finish", event.sessionType());
        assertEquals(25, event.fps());
        assertEquals("operator", event.actor());
    }

    @Test
    void startSession_shouldDefaultSyncedTrue() {
        UUID regattaId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CaptureSession session = service.startSession(
                regattaId, blockId, "start-line", "dev-01",
                CaptureSession.SessionType.start,
                null, null, 30, null);

        assertTrue(session.isSynced());
        assertFalse(session.isDriftExceededThreshold());
    }

    @Test
    void startSession_shouldRejectInvalidFps() {
        UUID regattaId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class, () ->
                service.startSession(regattaId, blockId, "start-line", "dev-01",
                        CaptureSession.SessionType.start, null, null, 0, null));
    }

    @Test
    void startSession_shouldRejectNullRegattaId() {
        assertThrows(IllegalArgumentException.class, () ->
                service.startSession(null, UUID.randomUUID(), "start-line", "dev-01",
                        CaptureSession.SessionType.start, null, null, 25, null));
    }

    // ---- getSession tests ---------------------------------------------------

    @Test
    void getSession_shouldReturnSessionForMatchingRegatta() {
        UUID regattaId = UUID.randomUUID();
        CaptureSession session = buildSession(regattaId, CaptureSession.SessionState.open);
        when(repository.findById(session.getId())).thenReturn(Optional.of(session));

        Optional<CaptureSession> result = service.getSession(session.getId(), regattaId);

        assertTrue(result.isPresent());
        assertEquals(session.getId(), result.get().getId());
    }

    @Test
    void getSession_shouldReturnEmptyForWrongRegatta() {
        UUID regattaId = UUID.randomUUID();
        CaptureSession session = buildSession(UUID.randomUUID(), CaptureSession.SessionState.open);
        when(repository.findById(session.getId())).thenReturn(Optional.of(session));

        Optional<CaptureSession> result = service.getSession(session.getId(), regattaId);

        assertTrue(result.isEmpty());
    }

    // ---- listSessions tests -------------------------------------------------

    @Test
    void listSessions_shouldDelegateToRepository() {
        UUID regattaId = UUID.randomUUID();
        CaptureSession s = buildSession(regattaId, CaptureSession.SessionState.open);
        when(repository.findByRegattaId(regattaId)).thenReturn(List.of(s));

        List<CaptureSession> result = service.listSessions(regattaId);

        assertEquals(1, result.size());
        verify(repository).findByRegattaId(regattaId);
    }

    @Test
    void listOpenSessions_shouldFilterByStation() {
        UUID regattaId = UUID.randomUUID();
        when(repository.findOpenByRegattaId(regattaId, "start-line")).thenReturn(List.of());

        service.listOpenSessions(regattaId, "start-line");

        verify(repository).findOpenByRegattaId(regattaId, "start-line");
    }

    // ---- updateSyncState tests ----------------------------------------------

    @Test
    void updateSyncState_shouldUpdateAndEmitEvent() {
        UUID regattaId = UUID.randomUUID();
        CaptureSession session = buildSession(regattaId, CaptureSession.SessionState.open);
        when(repository.findById(session.getId())).thenReturn(Optional.of(session));
        when(repository.update(any())).thenAnswer(inv -> inv.getArgument(0));

        CaptureSession updated = service.updateSyncState(
                session.getId(), regattaId, false, true, "NTP unreachable", "device-42");

        assertFalse(updated.isSynced());
        assertTrue(updated.isDriftExceededThreshold());
        assertEquals("NTP unreachable", updated.getUnsyncedReason());
        assertEquals(CaptureSession.SessionState.open, updated.getState());

        ArgumentCaptor<CaptureSessionSyncStateUpdatedEvent> cap =
                ArgumentCaptor.forClass(CaptureSessionSyncStateUpdatedEvent.class);
        verify(repository).appendEvent(cap.capture());

        CaptureSessionSyncStateUpdatedEvent event = cap.getValue();
        assertEquals(session.getId(), event.captureSessionId());
        assertFalse(event.isSynced());
        assertTrue(event.driftExceededThreshold());
        assertEquals("NTP unreachable", event.unsyncedReason());
    }

    @Test
    void updateSyncState_shouldThrowForClosedSession() {
        UUID regattaId = UUID.randomUUID();
        CaptureSession session = buildSession(regattaId, CaptureSession.SessionState.closed);
        when(repository.findById(session.getId())).thenReturn(Optional.of(session));

        assertThrows(IllegalStateException.class, () ->
                service.updateSyncState(session.getId(), regattaId, true, false, null, "op"));
    }

    @Test
    void updateSyncState_shouldThrowNotFoundForMissingSession() {
        UUID regattaId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        when(repository.findById(sessionId)).thenReturn(Optional.empty());

        assertThrows(CaptureSessionService.CaptureSessionNotFoundException.class, () ->
                service.updateSyncState(sessionId, regattaId, true, false, null, "op"));
    }

    // ---- closeSession tests -------------------------------------------------

    @Test
    void closeSession_shouldCloseAndEmitEvent() {
        UUID regattaId = UUID.randomUUID();
        CaptureSession session = buildSession(regattaId, CaptureSession.SessionState.open);
        when(repository.findById(session.getId())).thenReturn(Optional.of(session));
        when(repository.update(any())).thenAnswer(inv -> inv.getArgument(0));

        CaptureSession closed = service.closeSession(
                session.getId(), regattaId, "End of race", "operator");

        assertEquals(CaptureSession.SessionState.closed, closed.getState());
        assertEquals("End of race", closed.getCloseReason());
        assertNotNull(closed.getClosedAt());

        ArgumentCaptor<CaptureSessionClosedEvent> cap =
                ArgumentCaptor.forClass(CaptureSessionClosedEvent.class);
        verify(repository).appendEvent(cap.capture());

        CaptureSessionClosedEvent event = cap.getValue();
        assertEquals(session.getId(), event.captureSessionId());
        assertEquals("End of race", event.closeReason());
        assertEquals("operator", event.actor());
    }

    @Test
    void closeSession_shouldThrowForAlreadyClosedSession() {
        UUID regattaId = UUID.randomUUID();
        CaptureSession session = buildSession(regattaId, CaptureSession.SessionState.closed);
        when(repository.findById(session.getId())).thenReturn(Optional.of(session));

        assertThrows(IllegalStateException.class, () ->
                service.closeSession(session.getId(), regattaId, "again", "op"));
    }

    @Test
    void closeSession_shouldAllowNullCloseReason() {
        UUID regattaId = UUID.randomUUID();
        CaptureSession session = buildSession(regattaId, CaptureSession.SessionState.open);
        when(repository.findById(session.getId())).thenReturn(Optional.of(session));
        when(repository.update(any())).thenAnswer(inv -> inv.getArgument(0));

        CaptureSession closed = service.closeSession(session.getId(), regattaId, null, "op");

        assertEquals(CaptureSession.SessionState.closed, closed.getState());
        assertNull(closed.getCloseReason());
    }

    // ---- updateDeviceControls tests -----------------------------------------

    @Test
    void updateDeviceControls_shouldUpdateBothParameters() {
        UUID regattaId = UUID.randomUUID();
        CaptureSession session = buildSession(regattaId, CaptureSession.SessionState.open);
        when(repository.findById(session.getId())).thenReturn(Optional.of(session));
        when(repository.update(any())).thenAnswer(inv -> inv.getArgument(0));

        CaptureSession updated = service.updateDeviceControls(
                session.getId(), regattaId, 512, 60, "operator");

        assertEquals(512, updated.getScanLinePosition());
        assertEquals(60, updated.getCaptureRate());
        verify(repository).update(any(CaptureSession.class));
    }

    @Test
    void updateDeviceControls_shouldAllowPartialUpdate() {
        UUID regattaId = UUID.randomUUID();
        CaptureSession session = buildSession(regattaId, CaptureSession.SessionState.open);
        when(repository.findById(session.getId())).thenReturn(Optional.of(session));
        when(repository.update(any())).thenAnswer(inv -> inv.getArgument(0));

        CaptureSession updated = service.updateDeviceControls(
                session.getId(), regattaId, 256, null, "operator");

        assertEquals(256, updated.getScanLinePosition());
        assertNull(updated.getCaptureRate());
    }

    @Test
    void updateDeviceControls_shouldValidateScanLinePositionNonNegative() {
        UUID regattaId = UUID.randomUUID();
        CaptureSession session = buildSession(regattaId, CaptureSession.SessionState.open);
        when(repository.findById(session.getId())).thenReturn(Optional.of(session));

        assertThrows(IllegalArgumentException.class, () ->
                service.updateDeviceControls(session.getId(), regattaId, -1, null, "op"));
    }

    @Test
    void updateDeviceControls_shouldValidateCaptureRatePositive() {
        UUID regattaId = UUID.randomUUID();
        CaptureSession session = buildSession(regattaId, CaptureSession.SessionState.open);
        when(repository.findById(session.getId())).thenReturn(Optional.of(session));

        assertThrows(IllegalArgumentException.class, () ->
                service.updateDeviceControls(session.getId(), regattaId, null, 0, "op"));

        assertThrows(IllegalArgumentException.class, () ->
                service.updateDeviceControls(session.getId(), regattaId, null, -1, "op"));
    }

    @Test
    void updateDeviceControls_shouldThrowForClosedSession() {
        UUID regattaId = UUID.randomUUID();
        CaptureSession session = buildSession(regattaId, CaptureSession.SessionState.closed);
        when(repository.findById(session.getId())).thenReturn(Optional.of(session));

        assertThrows(IllegalStateException.class, () ->
                service.updateDeviceControls(session.getId(), regattaId, 512, null, "op"));
    }

    @Test
    void updateDeviceControls_shouldThrowForUnknownSession() {
        UUID regattaId = UUID.randomUUID();
        UUID unknownSessionId = UUID.randomUUID();
        when(repository.findById(unknownSessionId)).thenReturn(Optional.empty());

        assertThrows(CaptureSessionService.CaptureSessionNotFoundException.class, () ->
                service.updateDeviceControls(unknownSessionId, regattaId, 512, null, "op"));
    }

    // ---- Helpers ------------------------------------------------------------

    private CaptureSession buildSession(UUID regattaId, CaptureSession.SessionState state) {
        Instant now = Instant.now();
        Instant closedAt = state == CaptureSession.SessionState.closed ? now : null;
        return new CaptureSession(
                UUID.randomUUID(), regattaId, UUID.randomUUID(),
                "finish-line", "device-99",
                CaptureSession.SessionType.finish, state,
                now, null, 25,
                true, false, null,
                closedAt, null,
                now, now);
    }
}
