# BC08 Finance and Payments

## Scope
Payment-status operations for entries and clubs.

## Functional Features to Implement
- Implement payment status management per entry.
- Implement payment status management per club.
- Implement bulk mark paid/unpaid operations for staff workflows.

## API Surface (v0.1)
- `GET /api/v1/regattas/{regatta_id}/entries/{entry_id}/payment_status`
- `PUT /api/v1/regattas/{regatta_id}/entries/{entry_id}/payment_status`
- `GET /api/v1/regattas/{regatta_id}/clubs/{club_id}/payment_status`
- `PUT /api/v1/regattas/{regatta_id}/clubs/{club_id}/payment_status`

## Non-Functional Features to Implement
- Enforce role-based access for financial operations.
- Preserve auditable history for payment-status changes.
- Ensure bulk update actions are safe and repeatable.

## Plan Coverage
- Step 15
