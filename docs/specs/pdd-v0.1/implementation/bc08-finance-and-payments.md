# BC08 Finance and Payments

## Scope
Payment-status operations for entries and clubs, and invoice lifecycle management.

## Functional Features to Implement
- Implement payment status management per entry.
- Implement payment status management per club.
- Implement bulk mark paid/unpaid operations for staff workflows.
- Implement invoice generation (async), listing, detail retrieval, and mark-paid (V01GAP-004).

## API Surface (v0.1)
- `GET /api/v1/regattas/{regatta_id}/entries/{entry_id}/payment_status`
- `PUT /api/v1/regattas/{regatta_id}/entries/{entry_id}/payment_status`
- `GET /api/v1/regattas/{regatta_id}/clubs/{club_id}/payment_status`
- `PUT /api/v1/regattas/{regatta_id}/clubs/{club_id}/payment_status`
- `GET /api/v1/regattas/{regatta_id}/finance/entries`
- `GET /api/v1/regattas/{regatta_id}/finance/clubs`
- `POST /api/v1/regattas/{regatta_id}/payments/mark_bulk`
- `GET /api/v1/regattas/{regatta_id}/invoices`
- `POST /api/v1/regattas/{regatta_id}/invoices/generate`
- `GET /api/v1/regattas/{regatta_id}/invoices/jobs/{job_id}`
- `GET /api/v1/regattas/{regatta_id}/invoices/{invoice_id}`
- `POST /api/v1/regattas/{regatta_id}/invoices/{invoice_id}/mark_paid`

## Non-Functional Features to Implement
- Enforce role-based access for financial operations.
- Preserve auditable history for payment-status changes.
- Ensure bulk update actions are safe and repeatable.
- Ensure invoice-state transitions are auditable and machine-readable.
- Expose stable async generation semantics for invoice creation.

## Plan Coverage
- Step 15
