# Infrastructure - Docker Compose

This directory will contain Docker Compose configurations for the RegattaDesk stack.

## Planned Services (BC01-002)

The full Docker Compose stack will include:

- **Backend**: Quarkus application
- **Frontend**: Vue.js application (served via Nginx)
- **PostgreSQL**: Database (version 16+)
- **Traefik**: Reverse proxy and load balancer (version 3.0+)
- **Authelia**: SSO/Authentication service (version 4.38+, DB-only mode)
- **MinIO**: S3-compatible object storage for line-scan tiles/manifests

## Current Status

This directory structure is a placeholder. Full Docker Compose implementation will be completed in BC01-002 (Docker Compose runtime composition).

## References

- [BC01 Platform Spec](../../pdd/implementation/bc01-platform-and-delivery.md)
- [Implementation Plan](../../pdd/implementation/plan.md) - Step 1
