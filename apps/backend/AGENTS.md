# Agent Instructions: Backend Application

## Context
This directory contains the Quarkus backend service for RegattaDesk.

## Technology Stack
- **Framework**: Quarkus 3.8+
- **Language**: Java 25
- **Build Tool**: Maven 3.9+
- **Key Extensions**: 
  - RESTEasy Reactive (REST endpoints)
  - SmallRye Health (health checks)
  - Jackson (JSON serialization)

## Project Structure
```
apps/backend/
├── src/
│   ├── main/
│   │   ├── java/com/regattadesk/
│   │   │   └── health/           # Health check endpoints
│   │   ├── resources/
│   │   │   └── application.properties  # Configuration
│   │   └── docker/               # Optional Dockerfile templates
│   └── test/                     # Unit and integration tests
├── pom.xml                       # Maven dependencies
└── mvnw                          # Maven wrapper
```

## Development Guidelines

### Running the Backend
```bash
./mvnw quarkus:dev
```
This starts the application with live reload on `http://localhost:8080`.

### Building
```bash
./mvnw clean package
```

### Building Container Image (Quarkus Jib)
```bash
./mvnw package -Dquarkus.container-image.build=true
```

### Testing
```bash
./mvnw test
```

### Adding Dependencies
Edit `pom.xml` and add the dependency within the `<dependencies>` section. Use Quarkus extensions when possible:
```bash
./mvnw quarkus:list-extensions        # List available extensions
./mvnw quarkus:add-extension -Dextensions="extension-name"
```

### Code Conventions
- Package structure: `com.regattadesk.<feature>`
- Use Jakarta EE annotations (jakarta.ws.rs.*, jakarta.inject.*)
- REST endpoints should be under `/api/` path
- Health checks are at `/q/health` (Quarkus standard) and `/api/health` (custom)
- Use Java 25 features where appropriate

### Configuration
Application configuration is in `src/main/resources/application.properties`:
- Use `quarkus.*` prefix for Quarkus-specific config
- Use environment-specific profiles (dev, test, prod)

### Health Endpoints
- Standard Quarkus health: `GET /q/health`
- Custom health endpoint: `GET /api/health`

## Important Notes for Agents

1. **Java Version**: Must be Java 25 (set in pom.xml)
2. **Quarkus Version**: 3.8+ is the baseline
3. **Build Tool**: Use Maven wrapper (`./mvnw`) not global Maven
4. **Dev Mode**: Always test changes in Quarkus dev mode before building
5. **Testing**: Add tests for new endpoints and business logic
6. **Pre-production API policy**: In v0.1, prefer direct breaking changes over temporary deprecated overloads or migration shims
7. **Contract/doc sync**: If backend behavior changes, update the relevant `pdd/` contract/design docs in the same change
8. **Deterministic CI gates**: PR-gating backend tests must be deterministic (no wall-clock sleeps, timing thresholds, or host-performance assumptions)
9. **Performance tests**: Keep performance and query-latency tests non-gating and separate from the default `./mvnw test` PR gate path
10. **Line-scan tile storage policy**: BC06 tile binary storage (and immediate manifest/tile storage metadata persistence path) is intentionally non-event-sourced in v0.1.

## Common Commands
- `./mvnw quarkus:dev` - Start in dev mode
- `./mvnw clean package` - Build
- `./mvnw package -Dquarkus.container-image.build=true` - Build container image via Quarkus Jib
- `./mvnw test` - Run tests
- `./mvnw clean install -DskipTests` - Install without tests

## Future Enhancements
- Database integration (PostgreSQL via Hibernate ORM with Panache)
- Event sourcing support
- API documentation (OpenAPI/Swagger)
- Security (JWT, OIDC integration)
