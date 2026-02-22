-- Marker workflow and entry completion fields for BC06-004

-- Capture sessions are required for marker ownership and regatta scoping.
CREATE TABLE capture_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    regatta_id UUID NOT NULL REFERENCES regattas(id) ON DELETE CASCADE,
    block_id UUID REFERENCES blocks(id) ON DELETE CASCADE,
    station VARCHAR(100) NOT NULL,
    device_id VARCHAR(255) NOT NULL,
    session_type VARCHAR(50) NOT NULL,
    state VARCHAR(20) NOT NULL DEFAULT 'open',
    server_time_at_start TIMESTAMPTZ NOT NULL,
    device_monotonic_offset_ms BIGINT,
    fps INTEGER NOT NULL,
    is_synced BOOLEAN NOT NULL DEFAULT TRUE,
    drift_exceeded_threshold BOOLEAN NOT NULL DEFAULT FALSE,
    unsynced_reason TEXT,
    closed_at TIMESTAMPTZ,
    close_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (session_type IN ('start', 'finish')),
    CHECK (state IN ('open', 'closed'))
);

CREATE INDEX idx_capture_sessions_regatta ON capture_sessions(regatta_id);
CREATE INDEX idx_capture_sessions_block ON capture_sessions(block_id);
CREATE INDEX idx_capture_sessions_type ON capture_sessions(session_type);
CREATE INDEX idx_capture_sessions_state ON capture_sessions(state);

-- Marker storage. Markers are API-managed persistence in BC06 v0.1.
CREATE TABLE timing_markers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    capture_session_id UUID NOT NULL REFERENCES capture_sessions(id) ON DELETE CASCADE,
    entry_id UUID REFERENCES entries(id) ON DELETE SET NULL,
    frame_offset BIGINT NOT NULL,
    timestamp_ms BIGINT NOT NULL,
    is_linked BOOLEAN NOT NULL DEFAULT FALSE,
    is_approved BOOLEAN NOT NULL DEFAULT FALSE,
    tile_x INTEGER,
    tile_y INTEGER,
    tile_id VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_timing_markers_session ON timing_markers(capture_session_id);
CREATE INDEX idx_timing_markers_entry ON timing_markers(entry_id);
CREATE INDEX idx_timing_markers_linked ON timing_markers(is_linked);
CREATE INDEX idx_timing_markers_approved ON timing_markers(is_approved);
CREATE INDEX idx_timing_markers_timestamp ON timing_markers(timestamp_ms);

-- Marker-derived entry outcome model.
ALTER TABLE entries
    ADD COLUMN marker_start_time_ms BIGINT,
    ADD COLUMN marker_finish_time_ms BIGINT,
    ADD COLUMN completion_status VARCHAR(30) NOT NULL DEFAULT 'incomplete',
    ADD CHECK (completion_status IN ('incomplete', 'pending_approval', 'completed'));

-- Keep updated_at in sync.
CREATE TRIGGER set_updated_at_capture_sessions
    BEFORE UPDATE ON capture_sessions
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER set_updated_at_timing_markers
    BEFORE UPDATE ON timing_markers
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
