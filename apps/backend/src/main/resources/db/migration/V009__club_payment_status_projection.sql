-- Finance read model for BC08-001
-- Derived club payment status projection table

CREATE TABLE club_payment_statuses (
    regatta_id UUID NOT NULL REFERENCES regattas(id) ON DELETE CASCADE,
    club_id UUID NOT NULL REFERENCES clubs(id) ON DELETE CASCADE,
    payment_status VARCHAR(50) NOT NULL DEFAULT 'unpaid',
    billable_entry_count INTEGER NOT NULL DEFAULT 0,
    paid_entry_count INTEGER NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (regatta_id, club_id),
    CHECK (payment_status IN ('unpaid', 'paid')),
    CHECK (billable_entry_count >= 0),
    CHECK (paid_entry_count >= 0),
    CHECK (paid_entry_count <= billable_entry_count)
);

CREATE INDEX idx_club_payment_statuses_status
    ON club_payment_statuses(payment_status);
