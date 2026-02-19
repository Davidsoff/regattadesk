-- Read model tables for regatta setup entities
-- These tables are populated by projections from events

-- Regattas read model
CREATE TABLE regattas (
    id UUID PRIMARY KEY,
    name CHARACTER VARYING(255) NOT NULL,
    description TEXT,
    time_zone CHARACTER VARYING(50) NOT NULL DEFAULT 'Europe/Amsterdam',
    status CHARACTER VARYING(50) NOT NULL DEFAULT 'draft',
    entry_fee DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    currency CHARACTER VARYING(3) NOT NULL DEFAULT 'EUR',
    draw_revision INTEGER NOT NULL DEFAULT 0,
    results_revision INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (status IN ('draft', 'published', 'archived', 'deleted'))
);

CREATE INDEX idx_regattas_status ON regattas(status);
CREATE INDEX idx_regattas_revisions ON regattas(draw_revision, results_revision);

COMMENT ON TABLE regattas IS 'Regatta read model populated by projections';
