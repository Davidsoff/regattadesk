.PHONY: help install build test lint clean dev backend-dev frontend-dev backend-image \
        test-backend-lint test-backend test-backend-integration test-backend-contract \
        test-frontend-lint test-frontend test-frontend-a11y \
        lint-backend lint-frontend

help:
	@echo "RegattaDesk Build Commands"
	@echo "=========================="
	@echo "install        - Install all dependencies (backend + frontend)"
	@echo "build          - Build all applications"
	@echo "test           - Run all tests and linters (mirrors CI pipeline)"
	@echo "lint           - Run linters for all applications"
	@echo "clean          - Clean build artifacts"
	@echo "dev            - Run both backend and frontend in dev mode (requires separate terminals)"
	@echo "backend-dev    - Run backend in dev mode"
	@echo "frontend-dev   - Run frontend in dev mode"
	@echo "backend-image  - Build backend container image via Quarkus Jib"

install: install-backend install-frontend

install-backend:
	@echo "Installing backend dependencies..."
	cd apps/backend && ./mvnw clean install -DskipTests

install-frontend:
	@echo "Installing frontend dependencies..."
	cd apps/frontend && npm ci

build: build-backend build-frontend

build-backend:
	@echo "Building backend..."
	cd apps/backend && ./mvnw clean package

build-frontend:
	@echo "Building frontend..."
	cd apps/frontend && npm run build

# Run all tests and linters exactly as the CI pipeline does.
# Required checks (exit non-zero on failure):
#   backend lint, backend unit tests, frontend lint
# Optional checks (continue-on-error, mirrors CI continue-on-error: true):
#   backend integration tests, backend contract tests,
#   frontend unit tests, frontend accessibility tests
test: test-backend-lint test-backend test-backend-integration test-backend-contract \
      test-frontend-lint test-frontend test-frontend-a11y

test-backend-lint:
	@echo "--- Backend Lint ---"
	cd apps/backend && ./mvnw verify -Dformat.validate

test-backend:
	@echo "--- Backend Unit Tests ---"
	cd apps/backend && ./mvnw test

test-backend-integration:
	@echo "--- Backend Integration Tests ---"
	cd apps/backend && ./mvnw verify -Pintegration || \
	  echo "⚠️  Integration tests failed or not yet implemented (non-blocking)"

test-backend-contract:
	@echo "--- Backend Contract Tests (Pact) ---"
	cd apps/backend && ./mvnw verify -Pcontract || \
	  echo "⚠️  Contract tests failed or not yet implemented (non-blocking)"

test-frontend-lint:
	@echo "--- Frontend Lint ---"
	cd apps/frontend && npm run lint

test-frontend:
	@echo "--- Frontend Unit Tests ---"
	cd apps/frontend && npm run test:run || \
	  echo "⚠️  Frontend unit tests failed or not yet configured (non-blocking)"

test-frontend-a11y:
	@echo "--- Frontend Accessibility Tests ---"
	cd apps/frontend && npm run test:a11y || \
	  echo "⚠️  Accessibility tests failed or not yet configured (non-blocking)"

lint: lint-backend lint-frontend

lint-backend:
	@echo "Linting backend..."
	cd apps/backend && ./mvnw verify -Dformat.validate

lint-frontend:
	@echo "Linting frontend..."
	cd apps/frontend && npm run lint

clean: clean-backend clean-frontend

clean-backend:
	@echo "Cleaning backend..."
	cd apps/backend && ./mvnw clean

clean-frontend:
	@echo "Cleaning frontend..."
	cd apps/frontend && rm -rf dist node_modules

backend-dev:
	@echo "Starting backend in dev mode..."
	cd apps/backend && ./mvnw quarkus:dev

frontend-dev:
	@echo "Starting frontend in dev mode..."
	cd apps/frontend && npm run dev

backend-image:
	@echo "Building backend container image with Quarkus Jib..."
	cd apps/backend && ./mvnw package -Dquarkus.container-image.build=true
