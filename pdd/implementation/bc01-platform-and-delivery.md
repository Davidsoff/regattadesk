# BC01 Platform and Delivery

## Scope
Foundational platform setup, runtime composition, dependency baseline, and delivery pipeline for v0.1.

## Functional Features to Implement
- Bootstrap repository structure with Quarkus backend and Vue frontend skeletons.
- Provide canonical Docker Compose stack with backend, frontend, PostgreSQL, Traefik, Authelia, and MinIO.
- Build CI/CD pipeline for build, test, and deployment flow.
- Establish and maintain third-party dependency inventory with minimum versions and update policy.
- Provide baseline environment configuration to run the full stack in local and production-like environments.

## Non-Functional Features to Implement
- Use Docker Compose as canonical runtime for both local development and production deployment in v0.1.
- Ensure no externally assumed core services in v0.1 stack composition.
- Keep Authelia in DB-only mode for v0.1 and explicitly exclude Redis from stack requirements.
- Pin dependency versions in lockfiles/build manifests.
- Enforce weekly automated dependency vulnerability scanning.
- Allow expedited dependency updates for security/critical bug fixes.
- Keep builds reproducible across environments.

## Plan Coverage
- Step 1
- Third-party dependency inventory (table)
- Dependency governance (shared ownership with BC09)
- Docker Compose requirement
