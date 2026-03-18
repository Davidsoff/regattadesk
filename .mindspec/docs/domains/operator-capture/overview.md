# Operator-Capture Domain — Overview

## What This Domain Owns

- Operator tokens, station handoff flows, and capture-session lifecycle.
- Line-scan manifest and tile persistence plus marker creation, linking, and sync-oriented updates.
- Offline-capable operator PWA behavior, including local queueing, conflict handling, and sync state.

## Boundaries

- It does not own regatta entity identity, draw generation, or public result publication.
- It stores line-scan binaries and immediate metadata, but it does not own event-sourced history for the rest of the domain model.
- It produces evidence and timing data, while adjudication owns penalty and approval decisions.

## Key Files

| File | Purpose |
|:-----|:--------|
| `apps/backend/src/main/java/com/regattadesk/operator/api/OperatorTokenResource.java` | Operator token management endpoints. |
| `apps/backend/src/main/java/com/regattadesk/operator/api/CaptureSessionResource.java` | Capture-session lifecycle and sync-state endpoints. |
| `apps/backend/src/main/java/com/regattadesk/operator/api/StationHandoffResource.java` | Handoff and PIN workflow endpoints. |
| `apps/backend/src/main/java/com/regattadesk/linescan/api/LineScanManifestResource.java` | Manifest ingest and retrieval. |
| `apps/backend/src/main/java/com/regattadesk/linescan/api/LineScanTileResource.java` | Tile ingest and retrieval. |
| `apps/backend/src/main/java/com/regattadesk/linescan/api/MarkerResource.java` | Marker CRUD and link workflows. |
| `apps/frontend/src/components/operator/LineScanCapture.vue` | Primary operator capture UI component. |
| `apps/frontend/src/composables/useOfflineQueue.js` | Offline queue handling. |

## Current State

- This domain spans backend packages for operator auth and line-scan storage plus dedicated operator UI views and composables.
- The repo already contains migrations for line-scan storage, markers, capture-session indexes, and station handoffs.
- ADR-0001 documents the major architectural exception: tile and manifest storage is API-managed rather than event-sourced in v0.1.