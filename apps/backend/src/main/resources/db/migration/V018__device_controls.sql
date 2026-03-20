-- Add device control fields for scan-line position and capture-rate (rd-dqi.11)

ALTER TABLE capture_sessions
    ADD COLUMN scan_line_position INTEGER,
    ADD COLUMN capture_rate INTEGER;

-- Indexes for efficient device control queries
CREATE INDEX idx_capture_sessions_scan_line ON capture_sessions(scan_line_position) WHERE scan_line_position IS NOT NULL;
CREATE INDEX idx_capture_sessions_capture_rate ON capture_sessions(capture_rate) WHERE capture_rate IS NOT NULL;
