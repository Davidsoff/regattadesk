# Agent Instructions: Applications Directory

## Context
This directory contains the main application components of RegattaDesk.

## Structure
```
apps/
├── backend/     # Quarkus backend service (Java 25)
└── frontend/    # Vue.js frontend application (Node 22)
```

## Overview

### Backend (`apps/backend/`)
- **Technology**: Quarkus 3.8+ with Java 25
- **Purpose**: REST API, business logic, event sourcing, database access, and line-scan tile storage workflows (non-event-sourced in v0.1)
- **Development**: `cd apps/backend && ./mvnw quarkus:dev`
- **Port**: 8080 (default)

See [apps/backend/AGENTS.md](backend/AGENTS.md) for detailed instructions.

### Frontend (`apps/frontend/`)
- **Technology**: Vue 3 + Vite 7
- **Purpose**: User interface, staff workflows, public pages
- **Development**: `cd apps/frontend && npm run dev`
- **Port**: 5173 (default)

See [apps/frontend/AGENTS.md](frontend/AGENTS.md) for detailed instructions.

## Development Workflow

### Starting Both Applications
```bash
# Terminal 1 - Backend
cd apps/backend
./mvnw quarkus:dev

# Terminal 2 - Frontend  
cd apps/frontend
npm run dev
```

Or from the root directory:
```bash
make backend-dev  # Terminal 1
make frontend-dev # Terminal 2
```

### Building Both Applications
```bash
# From root directory
make build

# Or individually
cd apps/backend && ./mvnw clean package
cd apps/frontend && npm run build

# Backend container image (Quarkus Jib)
cd apps/backend && ./mvnw package -Dquarkus.container-image.build=true
```

## Integration Points

### API Communication
- Backend exposes REST API at `http://localhost:8080/api/`
- Frontend will proxy API requests through Vite during development
- Production: Traefik will route requests based on path

### Health Checks
- Backend: `http://localhost:8080/api/health`
- Backend (Quarkus): `http://localhost:8080/q/health`

## Important Notes for Agents

1. **Each app is self-contained**: Backend and frontend can be developed independently
2. **Consistent Versioning**: Both use version 0.1.0-SNAPSHOT
3. **Toolchain Requirements**: Java 25 for backend, Node 22+ for frontend
4. **Build Isolation**: Each app has its own build system (Maven vs npm)
5. **Future Integration**: Docker Compose will orchestrate both apps together
6. **Pre-production policy**: For v0.1, prefer clean breaking changes over deprecation/migration shims that are not used yet
7. **Docs sync is mandatory**: When behavior or contracts change, update relevant `pdd/` docs in the same PR
8. **PR gate determinism**: Default PR checks must use deterministic tests only (no timing-threshold or sleep-based gating tests)
9. **Performance testing policy**: Performance tests are non-gating and must run outside default PR gate jobs

## Future Enhancements
- Shared TypeScript types between backend and frontend
- API contract testing (Pact)
- Integrated E2E testing
- Shared CI/CD pipeline
- Docker images for each application
