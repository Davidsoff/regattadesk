package com.regattadesk.linescan.model;

import java.util.UUID;

/**
 * Immutable timing marker model for BC06 marker workflows.
 */
public record TimingMarker(
    UUID id,
    UUID captureSessionId,
    UUID entryId,
    long frameOffset,
    long timestampMs,
    boolean isLinked,
    boolean isApproved,
    String tileId,
    Integer tileX,
    Integer tileY
) {
}
