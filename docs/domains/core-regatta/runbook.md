# Core-Regatta Domain — Runbook

## Development Workflows

- Run backend unit tests with `cd apps/backend && ./mvnw test`.
- Run PostgreSQL-backed integration checks with `cd apps/backend && ./mvnw verify -Pintegration` when changing migrations, repositories, or projections.
- Regenerate frontend API artifacts after contract changes with `cd apps/frontend && npm run api:generate`.

## Debugging

- Start with the relevant aggregate, then follow its projection handler and read model migration before blaming a frontend view.
- Inspect `apps/backend/src/main/resources/db/migration/` when API behavior and read model shape diverge.
- Projection lag or rebuild issues usually route through checkpoint state in `com.regattadesk.projection`.

## Common Tasks

- To add a new event-sourced workflow, create the event type, aggregate logic, projector update, migration or read model change, and API resource in one increment.
- To debug missing state in the UI, compare the aggregate events with the relevant projection output before changing frontend code.
- To change request or response shapes, update the OpenAPI source of truth and regenerate the frontend client.
