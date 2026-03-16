ALTER TABLE entries
    ADD COLUMN result_label VARCHAR(30) NOT NULL DEFAULT 'provisional',
    ADD COLUMN penalty_seconds INTEGER,
    ADD CONSTRAINT chk_entries_result_label CHECK (result_label IN ('provisional', 'edited', 'official'));

CREATE TABLE adjudication_investigations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    regatta_id UUID NOT NULL REFERENCES regattas(id) ON DELETE CASCADE,
    entry_id UUID NOT NULL REFERENCES entries(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL,
    description TEXT NOT NULL,
    outcome VARCHAR(30),
    penalty_seconds INTEGER,
    opened_by VARCHAR(255) NOT NULL,
    closed_by VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    closed_at TIMESTAMPTZ,
    CHECK (status IN ('open', 'closed'))
);

CREATE INDEX idx_adjudication_investigations_regatta_entry
    ON adjudication_investigations(regatta_id, entry_id, created_at DESC);

CREATE TABLE adjudication_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    regatta_id UUID NOT NULL REFERENCES regattas(id) ON DELETE CASCADE,
    entry_id UUID NOT NULL REFERENCES entries(id) ON DELETE CASCADE,
    action VARCHAR(30) NOT NULL,
    reason TEXT NOT NULL,
    note TEXT,
    actor VARCHAR(255) NOT NULL,
    previous_status VARCHAR(50),
    current_status VARCHAR(50) NOT NULL,
    previous_result_label VARCHAR(30),
    current_result_label VARCHAR(30) NOT NULL,
    penalty_seconds INTEGER,
    results_revision INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_adjudication_history_regatta_entry
    ON adjudication_history(regatta_id, entry_id, created_at DESC);
