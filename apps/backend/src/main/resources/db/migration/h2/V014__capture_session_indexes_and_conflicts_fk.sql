-- Add missing capture_sessions indexes (BC06 V01GAP-003) (H2 compatible)

-- Missing index on device_id for fast session lookup by device.
CREATE INDEX idx_capture_sessions_device ON capture_sessions(device_id);

-- Missing index on closed_at for retention/pruning queries and time-range filters.
CREATE INDEX idx_capture_sessions_closed_at ON capture_sessions(closed_at);
