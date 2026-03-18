# Rules-Draw Domain — Runbook

## Development Workflows

- Exercise backend scheduling tests with `cd apps/backend && ./mvnw test` and rerun integration tests when migrations or projections change.
- Regenerate the frontend API client after ruleset or draw contract changes with `cd apps/frontend && npm run api:generate`.
- Validate staff draw flows from the frontend against the backend running in dev mode before changing public consumers.

## Debugging

- Start with block and bib-pool data before debugging draw output; bad setup usually looks like a bad generator.
- Compare frontend immutability guards with backend resource behavior if a published draw appears editable.
- Check draw-related migrations and regatta revision events when publish or unpublish behavior looks wrong.

## Common Tasks

- To add a scheduling rule, update the relevant backend aggregate or service, the resource, and any matching frontend validation composables.
- To troubleshoot bib allocation, inspect pool ordering and overlap constraints before modifying draw logic.
- To verify a published change, confirm both the draw API response and the downstream public version state.
