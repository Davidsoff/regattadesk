-- Projection checkpoints table
-- Tracks the last processed event for each projection to enable idempotent replay
CREATE TABLE projection_checkpoints (
    projection_name CHARACTER VARYING(255) PRIMARY KEY,
    last_processed_event_id UUID NOT NULL,
    last_processed_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Index for efficient queries
CREATE INDEX idx_projection_checkpoints_updated_at ON projection_checkpoints(updated_at);

-- Add comment for documentation
COMMENT ON TABLE projection_checkpoints IS 'Tracks projection progress for idempotent event replay';
COMMENT ON COLUMN projection_checkpoints.projection_name IS 'Unique name of the projection';
COMMENT ON COLUMN projection_checkpoints.last_processed_event_id IS 'ID of the last successfully processed event';
COMMENT ON COLUMN projection_checkpoints.last_processed_at IS 'Timestamp when the last event was created';
COMMENT ON COLUMN projection_checkpoints.updated_at IS 'Timestamp when this checkpoint was last updated';
