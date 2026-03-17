-- Regatta-scoped crew associations for setup workflows
-- Reference: pdd/design/database-schema.md

CREATE TABLE regatta_crews (
    regatta_id UUID NOT NULL REFERENCES regattas(id) ON DELETE CASCADE,
    crew_id UUID NOT NULL REFERENCES crews(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (regatta_id, crew_id)
);

CREATE INDEX idx_regatta_crews_crew ON regatta_crews(crew_id);