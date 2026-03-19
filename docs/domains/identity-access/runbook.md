# Identity-Access Domain — Runbook

## Development Workflows

- Use `infra/compose/generate-users-database.sh` to build an Authelia users database for local testing.
- Start the secure stack from `infra/compose` so Traefik and Authelia sit in front of backend routes.
- Validate public-session behavior by hitting `/public/regattas/{id}/versions` and then `/public/session` when a `401` is expected.

## Debugging

- Run `infra/compose/edge-auth-test.sh` and `infra/compose/security-test.sh` to verify edge policy and forwarded identity behavior.
- Inspect `docs/core/IDENTITY_FORWARDING.md` first when route trust assumptions and implementation diverge.
- If headers appear unexpectedly on untrusted routes, review `IdentityHeaderSanitizer` before changing resource code.

## Common Tasks

- To add a new protected endpoint, align Traefik routing, Authelia policy, and backend role annotations in the same change.
- To rotate public-session keys, update JWT configuration and follow the overlap model described in the docs and runbooks.
- To validate edge hardening, run `infra/compose/edge-hardening-test.sh` and review security headers through Traefik.
