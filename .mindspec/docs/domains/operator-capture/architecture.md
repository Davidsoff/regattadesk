# Operator-Capture Domain — Architecture

## Key Patterns

- Operator workflows combine token-based authorization, capture-session state, and offline-first frontend behavior.
- Line-scan manifests and tiles are stored as API-managed persistence rather than domain events.
- Marker operations bridge the line-scan storage model and the event-sourced entry model by linking evidence to entries.

## Invariants

- Tile and manifest writes must be safe to retry without corrupting storage state.
- Linked markers must become effectively immutable once approvals happen downstream.
- Offline sync behavior must distinguish auto-resolvable conflicts from manual-resolution cases.
- Station handoff rules must prevent multiple active stations from silently sharing a token.

## Design Decisions

- The project accepts a non-event-sourced storage path for large binary line-scan data to keep v0.1 implementation tractable.
- Operator UI logic is isolated in dedicated views and composables because its offline and sunlight-readable requirements differ from staff and public surfaces.
- Capture sessions are modeled explicitly so sync state, drift, and session closure can be reasoned about independently of markers.