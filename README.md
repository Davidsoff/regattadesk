# RegattaDesk

A modern regatta management system with a Quarkus backend and Vue.js frontend.

## Prerequisites

Before you begin, ensure you have the following installed:

- **Java 25** - [Download OpenJDK 25](https://ado
- ptium.net/)
- **Node.js 22+** (LTS) - [Download Node.js](https://nodejs.org/)
- **Docker & Docker Compose 2.24+** - [Download Docker](https://www.docker.com/products/docker-desktop)
- **Make** (optional, for convenience commands)

### Verify Prerequisites

```bash
java -version   # Should show Java 25
node --version  # Should show v22.x or higher
docker --version
docker compose version
```

## Quick Start

### 1. Clone the Repository

```bash
git clone https://github.com/Davidsoff/regattadesk.git
cd regattadesk
```

### 2. Install Dependencies

```bash
make install
```

Or manually:
```bash
# Backend
cd apps/backend && ./mvnw clean install -DskipTests

# Frontend
cd apps/frontend && npm ci
```

### 3. Build Applications

```bash
make build
```

This will build both the backend and frontend applications.

### 4. Run Applications

#### Option A: Using separate terminals (development mode)

Terminal 1 - Backend:
```bash
make backend-dev
# Or: cd apps/backend && ./mvnw quarkus:dev
```

Terminal 2 - Frontend:
```bash
make frontend-dev
# Or: cd apps/frontend && npm run dev
```

The backend will be available at `http://localhost:8080` and the frontend at `http://localhost:5173`.

#### Option B: Using Docker Compose (future)

> Note: Full Docker Compose stack with all services (PostgreSQL, Traefik, Authelia, MinIO) is planned for BC01-002.

## Repository Structure

```
regattadesk/
├── apps/
│   ├── backend/          # Quarkus backend service
│   └── frontend/         # Vue.js frontend application
├── infra/
│   └── compose/          # Docker Compose configurations (planned)
├── docs/                 # Documentation
├── tests/                # Integration and E2E tests (future)
├── pdd/                  # Product Definition Documents
├── Makefile             # Build automation
└── README.md            # This file
```

## Common Commands

| Command | Description |
|---------|-------------|
| `make help` | Show all available commands |
| `make install` | Install all dependencies |
| `make build` | Build backend and frontend |
| `make test` | Run all tests |
| `make lint` | Run linters |
| `make clean` | Clean build artifacts |
| `make backend-dev` | Start backend in dev mode |
| `make frontend-dev` | Start frontend in dev mode |
| `make backend-image` | Build backend container image via Quarkus Jib |

## Backend (Quarkus)

The backend is built with Quarkus 3.8+ and Java 25.
Container images are built with Quarkus container-image Jib extension.

### Backend Commands

```bash
cd apps/backend

# Build
./mvnw clean package

# Run in dev mode (with live reload)
./mvnw quarkus:dev

# Run tests
./mvnw test

# Package for production
./mvnw clean package -Dquarkus.package.type=uber-jar

# Build container image with Quarkus Jib
./mvnw package -Dquarkus.container-image.build=true
```

### Backend Endpoints

- Health Check: `http://localhost:8080/q/health`
- Custom Health: `http://localhost:8080/api/health`
- Swagger UI: `http://localhost:8080/q/swagger-ui` (in dev mode)

## Frontend (Vue.js)

The frontend is built with Vue 3, Vite 5+, and Node.js 22.

### Frontend Commands

```bash
cd apps/frontend

# Install dependencies
npm ci

# Run dev server
npm run dev

# Build for production
npm run build

# Preview production build
npm run preview
```

### Frontend URLs

- Development: `http://localhost:5173`
- Production preview: `http://localhost:4173`

## Development Workflow

1. **Create a feature branch** from `main`
2. **Make your changes** in `apps/backend` or `apps/frontend`
3. **Test locally** using `make test`
4. **Build** to ensure no errors: `make build`
5. **Commit and push** your changes
6. **Create a pull request** for review

## Toolchain Versions

The project uses the following minimum versions (as defined in `pdd/implementation/plan.md`):

| Tool | Minimum Version | Update Policy |
|------|----------------|---------------|
| Java | 25 | Track latest Java 25 patches monthly |
| Node.js | 22+ (LTS) | Stay on active LTS |
| Quarkus | 3.8+ | Latest stable minor in current major |
| Vue | 3.4+ | Latest stable minor in major 3 |
| Vite | 5.0+ | Latest stable minor |
| PostgreSQL | 16+ | Stay within supported major versions |
| Docker Compose | 2.24+ | Latest stable minor |

## Testing

RegattaDesk follows a comprehensive testing strategy with multiple test categories and CI quality gates. See [Testing Strategy](docs/TESTING_STRATEGY.md) for complete details.

### Test Categories

1. **Unit Tests** - Fast, isolated tests for business logic
2. **Integration Tests** - Tests with real PostgreSQL (via Testcontainers)
3. **Contract Tests** - API contract verification (Pact)
4. **Accessibility Tests** - WCAG 2.2 AA compliance (axe-core)
5. **UI Component Tests** - Vue component behavior

### Running Tests

```bash
# All tests
make test

# Backend unit tests
cd apps/backend && ./mvnw test

# Backend integration tests (when implemented)
cd apps/backend && ./mvnw verify -Pintegration

# Backend contract tests (when implemented)
cd apps/backend && ./mvnw verify -Pcontract

# Frontend tests (when configured)
cd apps/frontend && npm test

# Accessibility tests (when configured)
cd apps/frontend && npm run test:a11y
```

### CI Quality Gates

All pull requests must pass these required checks:
- Backend lint, build, and unit tests
- Frontend lint and build
- Dependency pinning validation

Optional checks (informational until implemented):
- Backend integration and contract tests
- Frontend unit (`frontend-test`) and accessibility tests

See [CI Quality Gates](docs/CI_QUALITY_GATES.md) for validation scenarios and troubleshooting.

### Test Coverage Requirements

- **Unit Tests:** 80% minimum for new code
- **Critical Paths:** 100% for command handlers and domain rules
- **API Coverage:** All public/staff/operator endpoints must have tests

For detailed test requirements by change type, see [Testing Strategy](docs/TESTING_STRATEGY.md).

## Documentation

- [Developer Setup Guide](docs/DEVELOPER_SETUP.md) - First-time setup instructions
- [Implementation Plan](pdd/implementation/plan.md) - Full implementation roadmap
- [BC01 Platform Spec](pdd/implementation/bc01-platform-and-delivery.md) - Platform context

## Contributing

This project follows the implementation plan defined in `pdd/implementation/plan.md`. Each bounded context has specific feature requirements and acceptance criteria.

## License

(License to be determined)

## Support

For questions or issues, please refer to the project's GitHub issue tracker.
