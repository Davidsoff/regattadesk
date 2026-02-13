-- V001: Initial Event Store Schema
-- 
-- This migration implements the foundational event sourcing schema with:
-- - Aggregates table to track domain aggregate roots
-- - Event Store table for append-only event history
-- - Immutability constraints to prevent destructive updates/deletes
-- - Indexes optimized for stream reads and event queries
--
-- Event Sourcing Principles:
-- - All events are append-only and immutable once written
-- - Events are sequenced per aggregate (optimistic concurrency via sequence_number)
-- - Correlation and causation tracking for event traceability
-- - JSONB payload for flexible event schema evolution
--
-- Retention Policy:
-- - Events are retained indefinitely in v0.1 for full audit trail
-- - No automatic pruning or archival
-- - See docs/retention-policy.md for operational procedures

-- ========================================
-- AGGREGATES TABLE
-- ========================================
-- Tracks aggregate root entities and their current version
CREATE TABLE aggregates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(100) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_aggregates_type ON aggregates(aggregate_type);
CREATE INDEX idx_aggregates_version ON aggregates(version);

-- Trigger to automatically update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_aggregates_updated_at
    BEFORE UPDATE ON aggregates
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- ========================================
-- EVENT_STORE TABLE
-- ========================================
-- Append-only log of all domain events
CREATE TABLE event_store (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id UUID NOT NULL REFERENCES aggregates(id),
    event_type VARCHAR(100) NOT NULL,
    sequence_number BIGINT NOT NULL,
    payload JSONB NOT NULL,
    metadata JSONB DEFAULT '{}'::jsonb,
    correlation_id UUID,
    causation_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(aggregate_id, sequence_number)
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
CREATE OR REPLACE FUNCTION prevent_event_store_updates()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'event_store is append-only: updates are not allowed';
END;
$$ language 'plpgsql';

CREATE TRIGGER enforce_event_store_immutability
    BEFORE UPDATE ON event_store
    FOR EACH ROW
    EXECUTE FUNCTION prevent_event_store_updates();

-- Prevent deletes from event_store (immutable audit trail)
CREATE OR REPLACE FUNCTION prevent_event_store_deletes()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'event_store is immutable: deletes are not allowed';
END;
$$ language 'plpgsql';

CREATE TRIGGER enforce_event_store_no_deletes
    BEFORE DELETE ON event_store
    FOR EACH ROW
    EXECUTE FUNCTION prevent_event_store_deletes();

-- ========================================
-- COMMENTS (Schema Documentation)
-- ========================================
COMMENT ON TABLE aggregates IS 'Tracks aggregate root entities with versioning for optimistic concurrency';
COMMENT ON COLUMN aggregates.id IS 'Unique identifier for the aggregate root';
COMMENT ON COLUMN aggregates.aggregate_type IS 'Type discriminator (e.g., Regatta, Entry, Investigation)';
COMMENT ON COLUMN aggregates.version IS 'Current version number, incremented with each event';
COMMENT ON COLUMN aggregates.created_at IS 'Timestamp of aggregate creation';
COMMENT ON COLUMN aggregates.updated_at IS 'Timestamp of last version increment';

COMMENT ON TABLE event_store IS 'Append-only log of all domain events with full audit trail';
COMMENT ON COLUMN event_store.id IS 'Unique event identifier';
COMMENT ON COLUMN event_store.aggregate_id IS 'Reference to the aggregate root this event belongs to';
COMMENT ON COLUMN event_store.event_type IS 'Event type discriminator (e.g., RegattaCreated, EntryWithdrawn)';
COMMENT ON COLUMN event_store.sequence_number IS 'Monotonic sequence number within aggregate stream (for ordering and optimistic concurrency)';
COMMENT ON COLUMN event_store.payload IS 'Event data as JSONB for schema flexibility';
COMMENT ON COLUMN event_store.metadata IS 'Additional metadata (e.g., user_id, client_ip, request_id)';
COMMENT ON COLUMN event_store.correlation_id IS 'Correlation identifier for tracing related events across aggregates';
COMMENT ON COLUMN event_store.causation_id IS 'ID of the event that caused this event (causal chain tracking)';
COMMENT ON COLUMN event_store.created_at IS 'Immutable timestamp when event was appended';
