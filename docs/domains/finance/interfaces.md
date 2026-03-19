# Finance Domain — Interfaces

## Contracts

- `GET /api/v1/regattas/{regatta_id}/finance/entries` and `/finance/clubs` expose finance summary lists.
- `GET|PUT /api/v1/regattas/{regatta_id}/entries/{entry_id}/payment_status` and `/clubs/{club_id}/payment_status` expose direct payment status operations.
- `POST /api/v1/regattas/{regatta_id}/payments/mark_bulk` exposes bulk payment updates.
- `GET|POST /api/v1/regattas/{regatta_id}/invoices` plus `/generate`, `/jobs/{job_id}`, and `/{invoice_id}/mark_paid` expose invoice lifecycle workflows.

## Integration Points

- `core-regatta` supplies the entries, clubs, and billing context that finance references.
- `identity-access` protects financial-manager and admin routes.
- `operability` supplies testing and audit expectations for finance changes.