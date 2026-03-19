# Core-Regatta Domain — Interfaces

## Contracts

- `GET|POST|PUT|DELETE /api/v1/athletes` and `/api/v1/athletes/{athlete_id}` expose athlete CRUD.
- `GET|POST|PUT|DELETE /api/v1/regattas/{regatta_id}/events`, `/crews`, and `/entries` expose regatta setup workflows.
- `POST /api/v1/regattas/{regatta_id}/entries/{entry_id}/withdraw` and `/reinstate` expose staff status transitions needed by downstream domains.
- The event store and projection repositories expose the persistence contract used by other backend domains built on the same pattern.

## Integration Points

- `rules-draw` consumes events, crews, and entries before generating a draw.
- `operator-capture` links markers to entries owned here.
- `adjudication` and `finance` both mutate entry-adjacent state while relying on core entity identity and audit history.
- `public-delivery` depends on core read models and revision state as the source for public pages.
