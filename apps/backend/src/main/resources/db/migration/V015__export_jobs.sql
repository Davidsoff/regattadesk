-- Export jobs table for async printable PDF generation.
-- Stores job state, artifact bytes, and expiry metadata.

CREATE TABLE export_jobs (
    id UUID PRIMARY KEY,
    regatta_id UUID NOT NULL,
    type VARCHAR(50) NOT NULL DEFAULT 'printable',
    status VARCHAR(20) NOT NULL DEFAULT 'pending',
    artifact BYTEA,
    error_message TEXT,
    requested_by VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ,
    CHECK (status IN ('pending', 'processing', 'completed', 'failed'))
);

CREATE INDEX idx_export_jobs_regatta ON export_jobs(regatta_id);
CREATE INDEX idx_export_jobs_status ON export_jobs(status);
CREATE INDEX idx_export_jobs_expires ON export_jobs(expires_at);

COMMENT ON TABLE export_jobs IS 'Async export job records for printable PDF generation';
COMMENT ON COLUMN export_jobs.artifact IS 'Completed PDF bytes, stored until expires_at';
COMMENT ON COLUMN export_jobs.expires_at IS 'Artifact expiry timestamp (1 hour after completion)';
