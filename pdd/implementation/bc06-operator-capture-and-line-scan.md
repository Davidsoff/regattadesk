# BC06 Operator Capture and Line Scan

## Scope
Operator authentication artifacts, line-scan ingest/retrieval, offline-first operation, marker processing, and scan-retention lifecycle.

## Functional Features to Implement
- Implement operator QR token model for station access.
- Implement operator token PDF export including fallback instructions.
- Implement token validity checks and station handoff/PIN flow.
- Implement line-scan manifest and tile API endpoints:
- `POST /api/v1/regattas/{regatta_id}/line_scan/manifests`
- `GET /api/v1/regattas/{regatta_id}/line_scan/manifests/{manifest_id}`
- `PUT /api/v1/regattas/{regatta_id}/line_scan/tiles/{tile_id}`
- `GET /api/v1/regattas/{regatta_id}/line_scan/tiles/{tile_id}`
- Implement marker CRUD for captured line-scan data.
- Implement Operator PWA offline shell.
- Implement local queue and sync protocol for offline-to-online transitions.
- Implement conflict policy handling (last-write-wins or manual resolution).
- Implement marker linking to entries with start/finish times.
- Implement entry completion rule and approval gates.
- Implement retention-pruning workflow for line-scan data.

## Non-Functional Features to Implement
- Enforce auth boundaries: ingest via `OperatorTokenAuth`; retrieval via `OperatorTokenAuth` or `StaffProxyAuth`.
- Default operator UI to high-contrast mode with per-device persistence.
- Keep full line-scan data during regatta.
- Apply default 14-day delay after regatta end before pruning logic can execute.
- Block pruning until regatta is archived or all entries are approved.
- If delay elapses before prune preconditions are met, retain full scan and raise admin alert.
- After prune eligibility, reduce retained data to +/-2s around approved markers.
- Preserve offline reliability with eventual consistency after reconnection.

## Plan Coverage
- Step 16
- Step 17
- Step 18
- Step 19
- Step 9 (operator-specific high-contrast requirement)
