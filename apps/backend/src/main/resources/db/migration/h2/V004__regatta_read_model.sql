-- Read model tables for regatta setup entities (H2 compatible)

-- Regattas read model
CREATE TABLE regattas (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    time_zone VARCHAR(50) NOT NULL DEFAULT 'Europe/Amsterdam',
    status VARCHAR(50) NOT NULL DEFAULT 'draft',
    entry_fee DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    currency VARCHAR(3) NOT NULL DEFAULT 'EUR',
    draw_revision INTEGER NOT NULL DEFAULT 0,
    results_revision INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    CHECK (status IN ('draft', 'published', 'archived', 'deleted'))
);

CREATE INDEX idx_regattas_status ON regattas(status);
CREATE INDEX idx_regattas_revisions ON regattas(draw_revision, results_revision);
