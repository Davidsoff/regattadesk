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
            closedAt
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
}
