# Operability Domain — Runbook

## Development Workflows

- Run the full project verification path with `make test`.
- Run targeted checks such as `make test-backend-integration`, `make test-backend-contract`, and `cd apps/frontend && npm run test:a11y` when changing contract-sensitive or UI-critical behavior.
- Use the compose smoke and security scripts in `infra/compose` when changing edge or observability behavior.

## Debugging

- If a failure only appears in CI, compare it to the documented quality-gate expectations before changing feature code.
- Check health endpoints, compose logs, and observability smoke tests before assuming an application bug.
- For performance-gate failures, inspect the scenario catalog and thresholds before tuning application code blindly.

## Common Tasks

- To add a new quality gate, update the relevant docs and executable check in the same change.
- To add a runbook, keep the procedure close to the affected infrastructure or incident class and reference it from this domain.
- To validate hardening changes, rerun the edge and security scripts from `infra/compose` rather than relying on static inspection.