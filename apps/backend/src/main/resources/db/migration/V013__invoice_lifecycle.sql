-- Invoice lifecycle read models for V01GAP-004
-- Supports invoice listing/detail, async generation jobs, and mark-paid workflow.

CREATE TABLE invoice_generation_jobs (
    job_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    regatta_id UUID NOT NULL REFERENCES regattas(id) ON DELETE CASCADE,
    status VARCHAR(50) NOT NULL DEFAULT 'pending',
    requested_by VARCHAR(255) NOT NULL,
    idempotency_key VARCHAR(128),
    requested_club_ids_json TEXT,
    invoice_ids_json TEXT,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (status IN ('pending', 'running', 'completed', 'failed')),
    UNIQUE (regatta_id, idempotency_key)
);

CREATE INDEX idx_invoice_generation_jobs_regatta
    ON invoice_generation_jobs(regatta_id, created_at DESC);

CREATE INDEX idx_invoice_generation_jobs_status
    ON invoice_generation_jobs(status);

CREATE TABLE invoices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    regatta_id UUID NOT NULL REFERENCES regattas(id) ON DELETE CASCADE,
    club_id UUID NOT NULL REFERENCES clubs(id) ON DELETE RESTRICT,
    invoice_number VARCHAR(100) NOT NULL UNIQUE,
    total_amount NUMERIC(10, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'draft',
    generated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at TIMESTAMPTZ,
    paid_at TIMESTAMPTZ,
    paid_by VARCHAR(255),
    payment_reference VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (status IN ('draft', 'sent', 'paid', 'cancelled')),
    CHECK (total_amount >= 0),
    CHECK (currency ~ '^[A-Z]{3}$')
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
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (invoice_id, entry_id),
    CHECK (amount >= 0)
);

CREATE INDEX idx_invoice_entries_entry
    ON invoice_entries(entry_id);
