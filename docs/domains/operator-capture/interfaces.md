# Operator-Capture Domain — Interfaces

## Contracts

- `GET|POST /api/v1/regattas/{regatta_id}/operator/tokens` plus revoke and PDF export endpoints expose operator token administration.
- `GET|POST /api/v1/regattas/{regatta_id}/operator/capture_sessions` plus sync-state and close endpoints expose capture-session lifecycle.
- `GET /api/v1/regattas/{regatta_id}/operator/evidence_workspace` exposes the persisted evidence-first operator workspace keyed by capture session and optional event.
  The response is the operator review contract for the evidence stage: tile grid metadata, upload availability/degraded reasons, capture-session capability flags, and marker overlays rendered over persisted evidence.
- `GET|POST /api/v1/regattas/{regatta_id}/operator/station_handoffs` plus reveal, complete, and cancel endpoints expose station handoffs.
- `POST|GET /api/v1/regattas/{regatta_id}/line_scan/manifests`, `PUT|GET /line_scan/tiles/{tile_id}`, and marker endpoints expose line-scan ingest and evidence workflows.

## Integration Points

- `identity-access` protects operator and staff-proxied routes.
- `core-regatta` supplies the entries and regatta scope that markers attach to.
- `adjudication` consumes linked marker evidence during investigations and approvals.
- `platform-delivery` supplies MinIO and compose wiring for storage.
