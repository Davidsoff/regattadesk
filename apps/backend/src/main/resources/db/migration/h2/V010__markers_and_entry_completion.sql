-- Marker workflow and entry completion fields for BC06-004 (H2 compatible)

CREATE TABLE capture_sessions (
    id UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    regatta_id UUID NOT NULL REFERENCES regattas(id) ON DELETE CASCADE,
    block_id UUID NOT NULL REFERENCES blocks(id) ON DELETE CASCADE,
    station CHARACTER VARYING(100) NOT NULL,
    device_id CHARACTER VARYING(255) NOT NULL,
    session_type CHARACTER VARYING(50) NOT NULL,
    state CHARACTER VARYING(20) NOT NULL DEFAULT 'open',
    server_time_at_start TIMESTAMP NOT NULL,
    device_monotonic_offset_ms BIGINT,
    fps INTEGER NOT NULL,
    is_synced BOOLEAN NOT NULL DEFAULT TRUE,
    drift_exceeded_threshold BOOLEAN NOT NULL DEFAULT FALSE,
    unsynced_reason CLOB,
    closed_at TIMESTAMP,
    close_reason CLOB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (fps > 0),
    CHECK (session_type IN ('start', 'finish')),
    CHECK (state IN ('open', 'closed'))
);

CREATE INDEX idx_capture_sessions_regatta ON capture_sessions(regatta_id);
CREATE INDEX idx_capture_sessions_block ON capture_sessions(block_id);
CREATE INDEX idx_capture_sessions_type ON capture_sessions(session_type);
CREATE INDEX idx_capture_sessions_state ON capture_sessions(state);
CREATE INDEX idx_capture_sessions_regatta_type ON capture_sessions(regatta_id, session_type);
CREATE INDEX idx_capture_sessions_regatta_state ON capture_sessions(regatta_id, state);

CREATE TABLE timing_markers (
    id UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    capture_session_id UUID NOT NULL REFERENCES capture_sessions(id) ON DELETE CASCADE,
    entry_id UUID REFERENCES entries(id) ON DELETE RESTRICT,
    frame_offset BIGINT NOT NULL,
    timestamp_ms BIGINT NOT NULL,
    is_linked BOOLEAN NOT NULL DEFAULT FALSE,
    is_approved BOOLEAN NOT NULL DEFAULT FALSE,
    tile_x INTEGER,
    tile_y INTEGER,
    tile_id CHARACTER VARYING(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_timing_markers_session ON timing_markers(capture_session_id);
CREATE INDEX idx_timing_markers_entry ON timing_markers(entry_id);
CREATE INDEX idx_timing_markers_entry_linked_approved ON timing_markers(entry_id, is_linked, is_approved);
CREATE INDEX idx_timing_markers_timestamp ON timing_markers(timestamp_ms);

ALTER TABLE entries ADD COLUMN marker_start_time_ms BIGINT;
ALTER TABLE entries ADD COLUMN marker_finish_time_ms BIGINT;
ALTER TABLE entries ADD COLUMN completion_status CHARACTER VARYING(30) NOT NULL DEFAULT 'incomplete';

ALTER TABLE entries
    ADD CONSTRAINT chk_entries_completion_status
    CHECK (completion_status IN ('incomplete', 'pending_approval', 'completed'));

CREATE TRIGGER set_updated_at_capture_sessions
    BEFORE UPDATE ON capture_sessions
    FOR EACH ROW CALL "com.regattadesk.eventstore.UpdateTimestampTrigger";

CREATE TRIGGER set_updated_at_timing_markers
    BEFORE UPDATE ON timing_markers
    FOR EACH ROW CALL "com.regattadesk.eventstore.UpdateTimestampTrigger";
