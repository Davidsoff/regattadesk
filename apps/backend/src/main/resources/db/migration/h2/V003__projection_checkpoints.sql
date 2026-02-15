-- Projection checkpoints table (H2 compatible)
-- Tracks the last processed event for each projection to enable idempotent replay
CREATE TABLE projection_checkpoints (
    projection_name CHARACTER VARYING(255) PRIMARY KEY,
    last_processed_event_id UUID NOT NULL,
    last_processed_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

-- Index for efficient queries
CREATE INDEX idx_projection_checkpoints_updated_at ON projection_checkpoints(updated_at);
