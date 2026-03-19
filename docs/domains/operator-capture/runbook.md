# Operator-Capture Domain — Runbook

## Development Workflows

- Start the full stack with MinIO available before changing line-scan ingest or operator workflows.
- Run backend tests plus targeted frontend unit or accessibility tests when changing capture views or offline composables.
- Use `cd apps/frontend && npm run dev` to exercise the operator views while the backend runs in Quarkus dev mode.

## Debugging

- Verify object storage and manifest metadata before debugging marker behavior.
- Check capture-session state and sync-state endpoints when queued actions do not clear.
- Use the evidence workspace response to inspect whether upload state is `pending`, `syncing`, `partial_failure`, `failed`, or `completed` before deciding whether a tile retry is needed.
- If marker link behavior is inconsistent, compare operator API responses with the downstream entry or adjudication state before changing UI code.

## Common Tasks

- To add a new operator action, update the backend resource, sync semantics, and matching frontend composable in one change.
- To debug upload issues, inspect MinIO accessibility through the compose stack and verify content types on tile upload.
- Failed evidence uploads are retryable by reissuing the same tile upload; do not recapture unless the source artifact is lost.
- To validate token handoff flows, exercise reveal, complete, and cancellation endpoints against a live stack.
