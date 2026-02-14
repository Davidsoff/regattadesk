#!/bin/bash
# Security validation test for reduced port exposure
# This script validates that internal services are NOT exposed by default

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "=========================================="
echo "Security: Port Exposure Validation"
echo "=========================================="
echo

# Check if .env exists
if [[ ! -f .env ]]; then
    echo -e "${YELLOW}Creating .env from .env.example...${NC}"
    cp .env.example .env
fi

echo "Test 1: Default configuration (secure mode)"
echo "-------------------------------------------"

echo -n "  Validating base compose configuration... "
if docker compose config --quiet 2>&1; then
    echo -e "${GREEN}✓${NC}"
else
    echo -e "${RED}✗${NC}"
    exit 1
fi

echo -n "  Checking PostgreSQL has NO host ports... "
POSTGRES_PORTS=$(docker compose config 2>/dev/null | yq '.services.postgres.ports // "none"')
if [[ "$POSTGRES_PORTS" == "none" ]]; then
    echo -e "${GREEN}✓${NC}"
else
    echo -e "${RED}✗ Found ports: $POSTGRES_PORTS${NC}"
    exit 1
fi

echo -n "  Checking PostgreSQL has internal expose... "
POSTGRES_EXPOSE=$(docker compose config 2>/dev/null | yq '.services.postgres.expose | contains(["5432"])')
if [[ "$POSTGRES_EXPOSE" == "true" ]]; then
    echo -e "${GREEN}✓${NC}"
else
    echo -e "${RED}✗${NC}"
    exit 1
fi

echo -n "  Checking MinIO has NO host ports... "
MINIO_PORTS=$(docker compose config 2>/dev/null | yq '.services.minio.ports // "none"')
if [[ "$MINIO_PORTS" == "none" ]]; then
    echo -e "${GREEN}✓${NC}"
else
    echo -e "${RED}✗ Found ports: $MINIO_PORTS${NC}"
    exit 1
fi

echo -n "  Checking MinIO has internal expose... "
MINIO_EXPOSE=$(docker compose config 2>/dev/null | yq '.services.minio.expose | contains(["9000", "9001"])')
if [[ "$MINIO_EXPOSE" == "true" ]]; then
    echo -e "${GREEN}✓${NC}"
else
    echo -e "${RED}✗${NC}"
    exit 1
fi

echo
echo "Test 2: Development mode configuration"
echo "---------------------------------------"

echo -n "  Validating dev overlay configuration... "
if docker compose -f docker-compose.yml -f docker-compose.dev.yml config --quiet 2>&1; then
    echo -e "${GREEN}✓${NC}"
else
    echo -e "${RED}✗${NC}"
    exit 1
fi

echo -n "  Checking PostgreSQL HAS host port in dev mode... "
POSTGRES_DEV_PORTS=$(docker compose -f docker-compose.yml -f docker-compose.dev.yml config 2>/dev/null | yq '.services.postgres.ports[0].published')
if [[ "$POSTGRES_DEV_PORTS" == "5432" ]]; then
    echo -e "${GREEN}✓${NC}"
else
    echo -e "${RED}✗ Expected 5432, got: $POSTGRES_DEV_PORTS${NC}"
    exit 1
fi

echo -n "  Checking MinIO HAS host ports in dev mode... "
MINIO_DEV_PORT1=$(docker compose -f docker-compose.yml -f docker-compose.dev.yml config 2>/dev/null | yq '.services.minio.ports[0].published')
MINIO_DEV_PORT2=$(docker compose -f docker-compose.yml -f docker-compose.dev.yml config 2>/dev/null | yq '.services.minio.ports[1].published')
if [[ "$MINIO_DEV_PORT1" == "9000" ]] && [[ "$MINIO_DEV_PORT2" == "9001" ]]; then
    echo -e "${GREEN}✓${NC}"
else
    echo -e "${RED}✗ Expected 9000,9001, got: $MINIO_DEV_PORT1,$MINIO_DEV_PORT2${NC}"
    exit 1
fi

echo
echo "Test 3: Observability configuration (secure mode)"
echo "--------------------------------------------------"

echo -n "  Validating observability configuration... "
if docker compose -f docker-compose.yml -f docker-compose.observability.yml config --quiet 2>&1; then
    echo -e "${GREEN}✓${NC}"
else
    echo -e "${RED}✗${NC}"
    exit 1
fi

echo -n "  Checking Jaeger has NO host ports... "
JAEGER_PORTS=$(docker compose -f docker-compose.yml -f docker-compose.observability.yml config 2>/dev/null | yq '.services.jaeger.ports // "none"')
if [[ "$JAEGER_PORTS" == "none" ]]; then
    echo -e "${GREEN}✓${NC}"
else
    echo -e "${RED}✗ Found ports: $JAEGER_PORTS${NC}"
    exit 1
fi

echo -n "  Checking Prometheus has NO host ports... "
PROMETHEUS_PORTS=$(docker compose -f docker-compose.yml -f docker-compose.observability.yml config 2>/dev/null | yq '.services.prometheus.ports // "none"')
if [[ "$PROMETHEUS_PORTS" == "none" ]]; then
    echo -e "${GREEN}✓${NC}"
else
    echo -e "${RED}✗ Found ports: $PROMETHEUS_PORTS${NC}"
    exit 1
fi

echo
echo "Test 4: Observability development mode"
echo "---------------------------------------"

echo -n "  Validating observability dev overlay... "
if docker compose -f docker-compose.yml -f docker-compose.observability.yml -f docker-compose.observability.dev.yml config --quiet 2>&1; then
    echo -e "${GREEN}✓${NC}"
else
    echo -e "${RED}✗${NC}"
    exit 1
fi

echo -n "  Checking Jaeger HAS host ports in dev mode... "
JAEGER_DEV_PORT=$(docker compose -f docker-compose.yml -f docker-compose.observability.yml -f docker-compose.observability.dev.yml config 2>/dev/null | yq '.services.jaeger.ports[0].published')
if [[ "$JAEGER_DEV_PORT" == "16686" ]]; then
    echo -e "${GREEN}✓${NC}"
else
    echo -e "${RED}✗ Expected 16686, got: $JAEGER_DEV_PORT${NC}"
    exit 1
fi

echo -n "  Checking Prometheus HAS host port in dev mode... "
PROMETHEUS_DEV_PORT=$(docker compose -f docker-compose.yml -f docker-compose.observability.yml -f docker-compose.observability.dev.yml config 2>/dev/null | yq '.services.prometheus.ports[0].published')
if [[ "$PROMETHEUS_DEV_PORT" == "9090" ]]; then
    echo -e "${GREEN}✓${NC}"
else
    echo -e "${RED}✗ Expected 9090, got: $PROMETHEUS_DEV_PORT${NC}"
    exit 1
fi

echo
echo "=========================================="
echo -e "${GREEN}✓ All security validation tests passed!${NC}"
echo "=========================================="
echo
echo "Summary:"
echo "  • Internal services (PostgreSQL, MinIO) are NOT exposed by default"
echo "  • Observability services (Jaeger, Prometheus) are NOT exposed by default"
echo "  • Dev overlays correctly expose services when explicitly requested"
echo "  • Configuration is valid for all deployment modes"
