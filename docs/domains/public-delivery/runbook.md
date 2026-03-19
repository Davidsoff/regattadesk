# Public-Delivery Domain — Runbook

## Development Workflows

- Run the frontend in dev mode with `cd apps/frontend && npm run dev` and the backend in parallel when changing public pages or SSE behavior.
- Run accessibility checks with `cd apps/frontend && npm run test:a11y` after changes to public views or shared primitives.
- Regenerate frontend API bindings with `npm run api:generate` if public contract changes originate in `docs/specs/pdd-v0.1/design/openapi-v0.1.yaml`.

## Debugging

- Verify cache headers first when public behavior looks inconsistent across refreshes or deployments.
- Check SSE event delivery through `RegattaSseResource` and `useSseReconnect.js` before changing page state logic.
- If rendered dates or times look wrong, compare backend formatter output with frontend locale utilities before touching templates.

## Common Tasks

- To add a new public page, define the route contract, expose the backend resource, and keep the payload cache-safe.
- To change revision behavior, update the producing domain and then verify bootstrap, version discovery, and SSE notifications together.
- To update print output, keep backend PDF behavior and frontend print components aligned with the style guide.