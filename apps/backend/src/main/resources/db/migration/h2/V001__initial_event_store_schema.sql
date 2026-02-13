-- V001: Initial Event Store Schema (H2-compatible version for testing)
-- 
-- This is an H2-compatible version of the event store schema for testing purposes.
-- Production should use PostgreSQL with the main V001 migration.

-- ========================================
-- AGGREGATES TABLE
-- ========================================
CREATE TABLE aggregates (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_aggregates_type ON aggregates(aggregate_type);
CREATE INDEX idx_aggregates_version ON aggregates(version);

-- ========================================
-- EVENT_STORE TABLE
-- ========================================
CREATE TABLE event_store (
    id UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    sequence_number BIGINT NOT NULL,
    payload VARCHAR(10000) NOT NULL,
    metadata VARCHAR(10000) DEFAULT '{}',
    correlation_id UUID,
    causation_id UUID,
    created_at TIMESTAMP NOT NULL,
    UNIQUE(aggregate_id, sequence_number),
    FOREIGN KEY(aggregate_id) REFERENCES aggregates(id)
);

-- Indexes for efficient event stream reads and queries
CREATE INDEX idx_event_store_aggregate ON event_store(aggregate_id);
CREATE INDEX idx_event_store_type ON event_store(event_type);
CREATE INDEX idx_event_store_created ON event_store(created_at);
CREATE INDEX idx_event_store_correlation ON event_store(correlation_id);
CREATE INDEX idx_event_store_aggregate_sequence ON event_store(aggregate_id, sequence_number);

-- ========================================
-- IMMUTABILITY CONSTRAINTS
-- ========================================
-- Prevent updates to event_store rows (append-only)
CREATE TRIGGER enforce_event_store_immutability
    BEFORE UPDATE ON event_store
    FOR EACH ROW
    CALL 'com.regattadesk.eventstore.PreventUpdatesTrigger';

-- Prevent deletes from event_store (immutable audit trail)
CREATE TRIGGER enforce_event_store_no_deletes
    BEFORE DELETE ON event_store
    FOR EACH ROW
    CALL 'com.regattadesk.eventstore.PreventDeletesTrigger';
