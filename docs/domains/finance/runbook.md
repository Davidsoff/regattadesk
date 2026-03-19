# Finance Domain — Runbook

## Development Workflows

- Run backend tests after changing finance events, projections, or services with `cd apps/backend && ./mvnw test`.
- Exercise staff finance views against a live backend to verify bulk operations and invoice state changes end to end.
- Regenerate the frontend API client after finance contract updates.

## Debugging

- Start with the finance service and projection handler before changing list views; stale projections often look like UI bugs.
- Compare invoice resource responses with invoice job and event state when async generation appears stuck.
- Verify role enforcement before troubleshooting `403` responses as business-logic defects.

## Common Tasks

- To add a new finance field, update the event or model layer, projection, API response, and matching staff views together.
- To debug bulk updates, inspect the per-entry failure model and response payload before modifying service code.
- To verify invoice lifecycle behavior, test generation, detail retrieval, and mark-paid flow as a single sequence.