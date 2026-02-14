-- V003: Station Handoff Schema (H2 Compatible)
-- 
-- This migration implements the station handoff system for BC06:
-- - station_handoffs table for PIN-based device handoff
-- - Support for non-interrupting station transfer
-- - Handoff lifecycle management (pending, completed, cancelled)
-- - Audit event integration

-- ========================================
-- STATION_HANDOFFS TABLE
-- ========================================
-- Tracks station handoff requests between devices
CREATE TABLE station_handoffs (
    id UUID NOT NULL PRIMARY KEY,
    regatta_id UUID NOT NULL,
    token_id UUID NOT NULL,
    station VARCHAR(100) NOT NULL,
    requesting_device_id VARCHAR(255) NOT NULL,
    pin CHAR(6) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    CONSTRAINT chk_handoff_status CHECK (status IN ('PENDING', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT chk_expires_at_after_created CHECK (expires_at > created_at),
    CONSTRAINT fk_station_handoffs_token FOREIGN KEY (token_id) REFERENCES operator_tokens(id)
);

CREATE INDEX idx_station_handoffs_regatta ON station_handoffs(regatta_id);
CREATE INDEX idx_station_handoffs_token ON station_handoffs(token_id);
CREATE INDEX idx_station_handoffs_station ON station_handoffs(regatta_id, station);
CREATE INDEX idx_station_handoffs_status ON station_handoffs(status);
CREATE INDEX idx_station_handoffs_expires ON station_handoffs(expires_at);
