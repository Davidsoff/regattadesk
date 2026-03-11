ALTER TABLE entries ADD COLUMN result_label CHARACTER VARYING(30) DEFAULT 'provisional' NOT NULL;
ALTER TABLE entries ADD COLUMN penalty_seconds INTEGER;
ALTER TABLE entries ADD CONSTRAINT chk_entries_result_label CHECK (result_label IN ('provisional', 'edited', 'official'));

CREATE TABLE adjudication_investigations (
    id UUID PRIMARY KEY,
    regatta_id UUID NOT NULL,
    entry_id UUID NOT NULL,
    status CHARACTER VARYING(20) NOT NULL,
    description CLOB NOT NULL,
    outcome CHARACTER VARYING(30),
    penalty_seconds INTEGER,
    opened_by CHARACTER VARYING(255) NOT NULL,
    closed_by CHARACTER VARYING(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    closed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_adj_inv_regatta FOREIGN KEY (regatta_id) REFERENCES regattas(id) ON DELETE CASCADE,
    CONSTRAINT fk_adj_inv_entry FOREIGN KEY (entry_id) REFERENCES entries(id) ON DELETE CASCADE,
    CONSTRAINT chk_adj_inv_status CHECK (status IN ('open', 'closed'))
);

CREATE INDEX idx_adjudication_investigations_regatta_entry
    ON adjudication_investigations(regatta_id, entry_id, created_at DESC);

CREATE TABLE adjudication_history (
    id UUID PRIMARY KEY,
    regatta_id UUID NOT NULL,
    entry_id UUID NOT NULL,
    action CHARACTER VARYING(30) NOT NULL,
    reason CLOB NOT NULL,
    note CLOB,
    actor CHARACTER VARYING(255) NOT NULL,
    previous_status CHARACTER VARYING(50),
    current_status CHARACTER VARYING(50) NOT NULL,
    previous_result_label CHARACTER VARYING(30),
    current_result_label CHARACTER VARYING(30) NOT NULL,
    penalty_seconds INTEGER,
    results_revision INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_adj_history_regatta FOREIGN KEY (regatta_id) REFERENCES regattas(id) ON DELETE CASCADE,
    CONSTRAINT fk_adj_history_entry FOREIGN KEY (entry_id) REFERENCES entries(id) ON DELETE CASCADE
);

CREATE INDEX idx_adjudication_history_regatta_entry
    ON adjudication_history(regatta_id, entry_id, created_at DESC);
