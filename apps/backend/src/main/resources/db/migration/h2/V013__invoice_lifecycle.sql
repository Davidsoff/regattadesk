-- Invoice lifecycle read models for V01GAP-004 (H2 compatible)

CREATE TABLE invoice_generation_jobs (
    job_id UUID PRIMARY KEY,
    regatta_id UUID NOT NULL REFERENCES regattas(id) ON DELETE CASCADE,
    status CHARACTER VARYING(50) NOT NULL DEFAULT 'pending',
    requested_by CHARACTER VARYING(255) NOT NULL,
    idempotency_key CHARACTER VARYING(128),
    request_fingerprint CHARACTER VARYING(64) NOT NULL,
    requested_club_ids_json CLOB,
    invoice_ids_json CLOB,
    error_message CLOB,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    completed_at TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT chk_invoice_generation_job_status
        CHECK (status IN ('pending', 'running', 'completed', 'failed')),
    CONSTRAINT uq_invoice_generation_job_idempotency
        UNIQUE (regatta_id, requested_by, idempotency_key, request_fingerprint)
);

CREATE INDEX idx_invoice_generation_jobs_regatta
    ON invoice_generation_jobs(regatta_id, created_at DESC);

CREATE INDEX idx_invoice_generation_jobs_status
    ON invoice_generation_jobs(status);

CREATE TABLE invoices (
    id UUID PRIMARY KEY,
    regatta_id UUID NOT NULL REFERENCES regattas(id) ON DELETE CASCADE,
    club_id UUID NOT NULL REFERENCES clubs(id) ON DELETE RESTRICT,
    invoice_number CHARACTER VARYING(100) NOT NULL UNIQUE,
    total_amount NUMERIC(10, 2) NOT NULL,
    currency CHARACTER VARYING(3) NOT NULL,
    status CHARACTER VARYING(50) NOT NULL DEFAULT 'draft',
    generated_at TIMESTAMP NOT NULL DEFAULT now(),
    sent_at TIMESTAMP,
    paid_at TIMESTAMP,
    paid_by CHARACTER VARYING(255),
    payment_reference CHARACTER VARYING(255),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT chk_invoices_status
        CHECK (status IN ('draft', 'sent', 'paid', 'cancelled')),
    CONSTRAINT chk_invoices_total_amount
        CHECK (total_amount >= 0),
    CONSTRAINT chk_invoices_currency
        CHECK (currency REGEXP '^[A-Z]{3}$')
);

CREATE INDEX idx_invoices_regatta
    ON invoices(regatta_id, generated_at DESC, id DESC);

CREATE INDEX idx_invoices_club
    ON invoices(club_id);

CREATE INDEX idx_invoices_status
    ON invoices(status);

CREATE TABLE invoice_entries (
    invoice_id UUID NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
    entry_id UUID NOT NULL REFERENCES entries(id) ON DELETE RESTRICT,
    amount NUMERIC(10, 2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    PRIMARY KEY (invoice_id, entry_id),
    CONSTRAINT chk_invoice_entries_amount CHECK (amount >= 0)
);

CREATE INDEX idx_invoice_entries_entry
    ON invoice_entries(entry_id);
