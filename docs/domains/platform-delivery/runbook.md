# Platform-Delivery Domain — Runbook

## Development Workflows

- Install all dependencies with `make install`.
- Build the application pair with `make build`.
- Start local app-only development with `make backend-dev` and `make frontend-dev` in separate terminals.
- Start the full compose stack from `infra/compose` using `docker compose up -d`, or add `-f docker-compose.dev.yml` for direct database and MinIO access.

## Debugging

- Use `docker compose ps` and `docker compose logs -f` in `infra/compose` to verify stack health.
- Check `infra/compose/.env.example` against the local `.env` if containers fail to start or Authelia secrets are missing.
- For backend image issues, rebuild through `make backend-image` or `./mvnw package -Dquarkus.container-image.build=true`.

## Common Tasks

- To enable dev-only host access, start with `docker-compose.dev.yml` rather than editing the base compose file.
- To attach observability services, include `docker-compose.observability.yml` and optionally `docker-compose.observability.dev.yml`.
- To rotate dependency baselines, update `apps/backend/pom.xml`, `apps/frontend/package.json`, and any related docs in the same change.
