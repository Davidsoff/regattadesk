package com.regattadesk.operator;

import com.regattadesk.operator.api.CaptureSessionResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaptureSessionResponseTest {

    @Test
    void responseExposesCapabilitiesAndLiveStatusForLineScanSessions() {
        Instant startedAt = Instant.parse("2026-03-19T10:00:00Z");
        Instant closedAt = Instant.parse("2026-03-19T10:00:05Z");
        CaptureSession session = new CaptureSession(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "line-scan",
            "device-1",
            CaptureSession.SessionType.finish,
            CaptureSession.SessionState.closed,
            startedAt,
            12L,
            25,
            false,
            true,
            "ntp drift",
            closedAt,
            "done",
            startedAt,
            closedAt,
            512,
            60
        );

        CaptureSessionResponse response = new CaptureSessionResponse(session);

        assertTrue(response.getCapabilities().persistedEvidenceWorkspaceSupported());
        assertEquals(false, response.getCapabilities().livePreviewSupported());
        assertEquals("read_only", response.getCapabilities().deviceControlMode());
        assertEquals("closed", response.getLiveStatus().previewState());
        assertEquals("drift_exceeded", response.getLiveStatus().driftState());
        assertEquals(5_000L, response.getLiveStatus().elapsedCaptureMs());
        assertEquals(closedAt, response.getLiveStatus().statusObservedAt());
    }

    @Test
    void responseExposesDeviceControlsForLineScanSessions() {
        Instant now = Instant.parse("2026-03-19T10:00:00Z");
        CaptureSession openSession = new CaptureSession(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "line-scan",
            "device-1",
            CaptureSession.SessionType.finish,
            CaptureSession.SessionState.open,
            now,
            null,
            25,
            true,
            false,
            null,
            null,
            null,
            now,
            now,
            512,
            60
        );

        CaptureSessionResponse response = new CaptureSessionResponse(openSession);

        assertTrue(response.getDeviceControls().scanLinePositionSupported());
        assertTrue(response.getDeviceControls().scanLinePositionWritable());
        assertEquals(512, response.getDeviceControls().scanLinePosition());
        assertTrue(response.getDeviceControls().captureRateSupported());
        assertTrue(response.getDeviceControls().captureRateWritable());
        assertEquals(60, response.getDeviceControls().captureRate());
    }

    @Test
    void responseHidesDeviceControlsForNonLineScanStations() {
        Instant now = Instant.parse("2026-03-19T10:00:00Z");
        CaptureSession session = new CaptureSession(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "finish-line",
            "device-1",
            CaptureSession.SessionType.finish,
            CaptureSession.SessionState.open,
            now,
            null,
            25,
            true,
            false,
            null,
            null,
            null,
            now,
            now,
            512,
            60
        );

        CaptureSessionResponse response = new CaptureSessionResponse(session);

        // Device controls should not be supported for non-line-scan stations
        assertEquals(false, response.getDeviceControls().scanLinePositionSupported());
        assertEquals(false, response.getDeviceControls().scanLinePositionWritable());
        assertEquals(false, response.getDeviceControls().captureRateSupported());
        assertEquals(false, response.getDeviceControls().captureRateWritable());
    }

    @Test
    void responseDisablesDeviceControlWritesForClosedSessions() {
        Instant now = Instant.parse("2026-03-19T10:00:00Z");
        CaptureSession closedSession = new CaptureSession(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "line-scan",
            "device-1",
            CaptureSession.SessionType.finish,
            CaptureSession.SessionState.closed,
            now,
            null,
            25,
            true,
            false,
            null,
            now,
            "done",
            now,
            now,
            512,
            60
        );

        CaptureSessionResponse response = new CaptureSessionResponse(closedSession);

        // Device controls should be supported but not writable for closed sessions
        assertTrue(response.getDeviceControls().scanLinePositionSupported());
        assertEquals(false, response.getDeviceControls().scanLinePositionWritable());
        assertTrue(response.getDeviceControls().captureRateSupported());
        assertEquals(false, response.getDeviceControls().captureRateWritable());
    }
}
