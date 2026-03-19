# Public-Delivery Domain — Interfaces

## Contracts

- `GET /public/regattas/{regatta_id}/versions` exposes the current `draw_revision` and `results_revision` pair.
- `GET /public/v{draw}-{results}/regattas/{regatta_id}/schedule` and `/results` expose immutable public content.
- `GET /public/regattas/{regatta_id}/events` exposes live SSE revision updates.
- `POST /api/v1/regattas/{regatta_id}/export/printables` and `GET /api/v1/jobs/{job_id}` expose printable export workflows for staff-facing delivery.

## Integration Points

- `identity-access` supplies the public-session bootstrap used before public version discovery.
- `rules-draw` and `adjudication` produce the revision changes that define this domain's cache keys.
- `operability` supplies accessibility and performance expectations for public pages and SSE behavior.