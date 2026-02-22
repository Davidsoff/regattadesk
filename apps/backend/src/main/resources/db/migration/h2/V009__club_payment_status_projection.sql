-- Finance read model for BC08-001 (H2 compatible)
-- Derived club payment status projection table

CREATE TABLE club_payment_statuses (
    regatta_id UUID NOT NULL REFERENCES regattas(id) ON DELETE CASCADE,
    club_id UUID NOT NULL REFERENCES clubs(id) ON DELETE CASCADE,
    payment_status CHARACTER VARYING(50) NOT NULL DEFAULT 'unpaid',
    billable_entry_count INTEGER NOT NULL DEFAULT 0,
    paid_entry_count INTEGER NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    PRIMARY KEY (regatta_id, club_id),
    CONSTRAINT chk_club_payment_status_value CHECK (payment_status IN ('unpaid', 'paid')),
    CONSTRAINT chk_club_payment_billable_count CHECK (billable_entry_count >= 0),
    CONSTRAINT chk_club_payment_paid_count CHECK (paid_entry_count >= 0),
    CONSTRAINT chk_club_payment_paid_lte_billable CHECK (paid_entry_count <= billable_entry_count)
);

CREATE INDEX IF NOT EXISTS idx_club_payment_statuses_status
    ON club_payment_statuses(payment_status);
