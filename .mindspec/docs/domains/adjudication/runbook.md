# Adjudication Domain — Runbook

## Development Workflows

- Run backend tests after changing investigation events or adjudication actions with `cd apps/backend && ./mvnw test`.
- Exercise the staff adjudication flow in the frontend while the backend runs in dev mode.
- Re-run any public-delivery checks when adjudication changes can alter `results_revision` behavior.

## Debugging

- Start with the investigation aggregate history and compare it to the adjudication service output before changing frontend code.
- If a result label or approval transition looks wrong, follow the state transition into the regatta or entry projection path.
- Verify role enforcement before diagnosing business-logic failures on protected routes.

## Common Tasks

- To add a new adjudication action, update the request model, service behavior, audit event handling, and staff UI in one change.
- To debug missing public updates, confirm that the adjudication action results in the expected `results_revision` side effect.
- To investigate incorrect reversions, inspect the stored prior-state metadata before altering business logic.