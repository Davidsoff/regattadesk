-- Line-scan manifest and tile metadata tables for BC06-003 (H2 compatible)

-- Line-scan manifests table
-- Stores metadata about capture sessions and tile grids
CREATE TABLE line_scan_manifests (
    id UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    regatta_id UUID NOT NULL,
    capture_session_id UUID NOT NULL,
    tile_size_px INTEGER NOT NULL CHECK (tile_size_px IN (512, 1024)),
    primary_format VARCHAR(50) NOT NULL CHECK (primary_format IN ('webp_lossless', 'png')),
    fallback_format VARCHAR(50) CHECK (fallback_format IN ('png')),
    x_origin_timestamp_ms BIGINT NOT NULL,
    ms_per_pixel DOUBLE PRECISION NOT NULL,
    retention_days INTEGER NOT NULL DEFAULT 14 CHECK (retention_days >= 1),
    prune_window_seconds INTEGER NOT NULL DEFAULT 2 CHECK (prune_window_seconds >= 1),
    retention_state VARCHAR(50) NOT NULL DEFAULT 'full_retained' 
        CHECK (retention_state IN ('full_retained', 'pending_delay', 'eligible_waiting_archive_or_approvals', 'pruned')),
    prune_eligible_at TIMESTAMP WITH TIME ZONE,
    pruned_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for line_scan_manifests
CREATE INDEX idx_line_scan_manifests_regatta ON line_scan_manifests(regatta_id);
CREATE INDEX idx_line_scan_manifests_session ON line_scan_manifests(capture_session_id);
CREATE INDEX idx_line_scan_manifests_retention_state ON line_scan_manifests(retention_state);
CREATE UNIQUE INDEX idx_line_scan_manifests_session_unique ON line_scan_manifests(capture_session_id);

-- Line-scan tile metadata table
-- Tracks individual tiles within a manifest (actual image data stored in MinIO)
CREATE TABLE line_scan_tiles (
    id UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    manifest_id UUID NOT NULL REFERENCES line_scan_manifests(id) ON DELETE CASCADE,
    tile_id VARCHAR(255) NOT NULL,
    tile_x INTEGER NOT NULL,
    tile_y INTEGER NOT NULL,
    content_type VARCHAR(50) NOT NULL CHECK (content_type IN ('image/webp', 'image/png')),
    byte_size INTEGER CHECK (byte_size >= 0),
    minio_bucket VARCHAR(255) NOT NULL,
    minio_object_key VARCHAR(512) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for line_scan_tiles
CREATE INDEX idx_line_scan_tiles_manifest ON line_scan_tiles(manifest_id);
CREATE INDEX idx_line_scan_tiles_tile_id ON line_scan_tiles(tile_id);
CREATE UNIQUE INDEX idx_line_scan_tiles_manifest_tile_unique ON line_scan_tiles(manifest_id, tile_id);

-- Add updated_at trigger for line_scan_manifests
CREATE TRIGGER set_updated_at_line_scan_manifests
    BEFORE UPDATE ON line_scan_manifests
    FOR EACH ROW CALL 'com.regattadesk.eventstore.UpdateTimestampTrigger';

-- Add updated_at trigger for line_scan_tiles
CREATE TRIGGER set_updated_at_line_scan_tiles
    BEFORE UPDATE ON line_scan_tiles
    FOR EACH ROW CALL 'com.regattadesk.eventstore.UpdateTimestampTrigger';
