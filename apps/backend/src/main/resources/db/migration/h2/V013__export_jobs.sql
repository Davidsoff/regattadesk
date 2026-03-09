-- Export jobs table for async printable PDF generation (H2-compatible).
-- Stores job state, artifact bytes, and expiry metadata.

CREATE TABLE export_jobs (
    id UUID PRIMARY KEY,
    regatta_id UUID NOT NULL,
    type VARCHAR(50) NOT NULL DEFAULT 'printable',
    status VARCHAR(20) NOT NULL DEFAULT 'pending',
    artifact BINARY LARGE OBJECT,
    error_message CLOB,
    requested_by VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE,
    CHECK (status IN ('pending', 'processing', 'completed', 'failed'))
);

CREATE INDEX idx_export_jobs_regatta ON export_jobs(regatta_id);
CREATE INDEX idx_export_jobs_status ON export_jobs(status);
CREATE INDEX idx_export_jobs_expires ON export_jobs(expires_at);
