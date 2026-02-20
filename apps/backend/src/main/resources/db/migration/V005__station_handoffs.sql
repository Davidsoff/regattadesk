-- V005: Station Handoff Schema
-- 
-- This migration implements the station handoff system for BC06:
-- - station_handoffs table for PIN-based device handoff
-- - Support for non-interrupting station transfer
-- - Handoff lifecycle management (pending, completed, cancelled)
-- - Audit event integration
--
-- Handoff Lifecycle:
-- - New device requests handoff via operator auth
-- - System generates short PIN and creates pending handoff
-- - Active station can reveal PIN
-- - New device completes handoff with PIN verification
-- - Previous device is demoted to read-only after completion
-- - Handoffs expire after TTL (default 10 minutes)

-- ========================================
-- STATION_HANDOFFS TABLE
-- ========================================
-- Tracks station handoff requests between devices
CREATE TABLE station_handoffs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    regatta_id UUID NOT NULL,
    token_id UUID NOT NULL REFERENCES operator_tokens(id),
    station VARCHAR(100) NOT NULL,
    requesting_device_id VARCHAR(255) NOT NULL,
    pin CHAR(6) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    CONSTRAINT chk_handoff_status CHECK (status IN ('PENDING', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT chk_expires_at_after_created CHECK (expires_at > created_at)
);

CREATE INDEX idx_station_handoffs_regatta ON station_handoffs(regatta_id);
CREATE INDEX idx_station_handoffs_token ON station_handoffs(token_id);
CREATE INDEX idx_station_handoffs_station ON station_handoffs(regatta_id, station);
CREATE INDEX idx_station_handoffs_status ON station_handoffs(status);
CREATE INDEX idx_station_handoffs_expires ON station_handoffs(expires_at);

-- Comments for documentation
COMMENT ON TABLE station_handoffs IS 'PIN-based station handoff requests for non-interrupting device transfer';
COMMENT ON COLUMN station_handoffs.pin IS 'Short numeric PIN for handoff verification';
COMMENT ON COLUMN station_handoffs.requesting_device_id IS 'Device identifier requesting handoff';
COMMENT ON COLUMN station_handoffs.status IS 'Handoff status: PENDING, COMPLETED, or CANCELLED';
COMMENT ON COLUMN station_handoffs.expires_at IS 'Handoff expiration time (TTL from creation)';
