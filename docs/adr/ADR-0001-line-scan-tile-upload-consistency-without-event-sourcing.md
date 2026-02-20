# ADR-0001: Line-scan Tile Upload Consistency Without Event Sourcing

- Status: Accepted
- Date: 2026-02-20
- Owners: Backend
- Related: BC06, PR #65 discussion `r2832026975`

## Context

BC06 line-scan tile/manifest storage is intentionally non-event-sourced in v0.1.

Current risk: tile upload writes binary data to MinIO and metadata updates can fail separately, which can create consistency gaps and (in some orderings) orphaned objects.

We need a deterministic approach that:
- keeps BC06 non-event-sourced,
- works with unreliable networks and offline-first operator clients,
- keeps the operator UI non-blocking,
- avoids introducing distributed transactions.

## Decision

Use a DB-first upload state machine with idempotent retries.

For tile upload:
1. Persist upload intent in `line_scan_tiles` first (`upload_state=pending`, attempt metadata).
2. Upload to MinIO.
3. Mark metadata as `ready` on success.
4. Mark metadata as `failed` on upload error (with last error and attempt metadata).
5. Permit retry by re-running `PUT` for the same tile id; retries are idempotent and converge to `ready`.

This is explicitly not event sourcing. It is an API-managed persistence workflow.

## Consequences

### Gains
- No event-store/projection complexity for BC06.
- Deterministic DB record of tile upload lifecycle (`pending`/`failed`/`ready`).
- Better behavior under unreliable networks through safe retries.
- Operator frontend can treat tile upload as asynchronous work and retry independently.

### Losses / Tradeoffs
- No immutable event log for tile upload state transitions in v0.1.
- Temporary `pending`/`failed` states must be handled operationally.
- Requires schema additions and service logic for lifecycle metadata.

## Implementation Notes

- Add `upload_state`, `upload_attempts`, `last_upload_error`, `last_upload_attempt_at` to `line_scan_tiles`.
- Keep read behavior strict: tile download is allowed only for `upload_state=ready`.
- Return failure for unavailable data so clients continue retry logic.

## Non-Goals (v0.1)

- Introducing an event-sourced tile aggregate.
- Cross-system transactional guarantees (DB + object store 2PC).
- Background upload queue with payload staging in DB.
