.PHONY: help install build test lint clean dev backend-dev frontend-dev

help:
	@echo "RegattaDesk Build Commands"
	@echo "=========================="
	@echo "install        - Install all dependencies (backend + frontend)"
	@echo "build          - Build all applications"
	@echo "test           - Run all tests"
	@echo "lint           - Run linters for all applications"
	@echo "clean          - Clean build artifacts"
	@echo "dev            - Run both backend and frontend in dev mode (requires separate terminals)"
	@echo "backend-dev    - Run backend in dev mode"
	@echo "frontend-dev   - Run frontend in dev mode"

install: install-backend install-frontend

install-backend:
	@echo "Installing backend dependencies..."
	cd apps/backend && ./mvnw clean install -DskipTests

install-frontend:
	@echo "Installing frontend dependencies..."
	cd apps/frontend && npm install

build: build-backend build-frontend

build-backend:
	@echo "Building backend..."
	cd apps/backend && ./mvnw clean package

build-frontend:
	@echo "Building frontend..."
	cd apps/frontend && npm run build

test: test-backend test-frontend

test-backend:
	@echo "Testing backend..."
	cd apps/backend && ./mvnw test

test-frontend:
	@echo "Testing frontend..."
	@echo "Frontend tests not yet configured"

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
