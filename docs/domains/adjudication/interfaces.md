# Adjudication Domain — Interfaces

## Contracts

- `GET|POST /api/v1/regattas/{regatta_id}/adjudication/investigations` exposes investigation listing and creation.
- `GET /api/v1/regattas/{regatta_id}/adjudication/entries/{entry_id}` exposes adjudication detail per entry.
- `POST /api/v1/regattas/{regatta_id}/adjudication/entries/{entry_id}/penalty`, `/dsq`, `/exclude`, and `/revert_dsq` expose disciplinary actions.

## Integration Points

- `core-regatta` supplies entry identity and receives adjudication-driven status transitions.
- `operator-capture` supplies marker evidence that informs investigations and approvals.
- `public-delivery` depends on adjudication as an upstream producer of results revisions.
- `identity-access` protects all adjudication endpoints with role-based access.