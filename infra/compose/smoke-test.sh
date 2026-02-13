#!/bin/bash
set -e

# RegattaDesk Docker Compose Smoke Test
# This script validates that all services start successfully and are healthy

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "======================================"
echo "RegattaDesk Compose Stack Smoke Test"
echo "======================================"
echo

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Escape values for safe use in sed replacement strings.
escape_sed_replacement() {
    printf '%s' "$1" | sed -e 's/[\/&]/\\&/g'
}

# Check if .env exists
if [ ! -f .env ]; then
    echo -e "${YELLOW}Warning: .env file not found. Creating from .env.example...${NC}"
    cp .env.example .env
    
    # Generate random secrets
    echo "Generating secure secrets..."
    POSTGRES_PASS=$(openssl rand -base64 24)
    MINIO_PASS=$(openssl rand -base64 24)
    JWT_SECRET=$(openssl rand -base64 32)
    SESSION_SECRET=$(openssl rand -base64 32)
    ENCRYPTION_KEY=$(openssl rand -base64 32)
    
    # Update .env with generated secrets using escaped replacements.
    POSTGRES_PASS_ESCAPED=$(escape_sed_replacement "$POSTGRES_PASS")
    MINIO_PASS_ESCAPED=$(escape_sed_replacement "$MINIO_PASS")
    JWT_SECRET_ESCAPED=$(escape_sed_replacement "$JWT_SECRET")
    SESSION_SECRET_ESCAPED=$(escape_sed_replacement "$SESSION_SECRET")
    ENCRYPTION_KEY_ESCAPED=$(escape_sed_replacement "$ENCRYPTION_KEY")

    sed -i "s/changeme_postgres_password/$POSTGRES_PASS_ESCAPED/" .env
    sed -i "s/changeme_minio_password/$MINIO_PASS_ESCAPED/" .env
    sed -i "s/changeme_jwt_secret_min_32_chars/$JWT_SECRET_ESCAPED/" .env
    sed -i "s/changeme_session_secret_min_32_chars/$SESSION_SECRET_ESCAPED/" .env
    sed -i "s/changeme_encryption_key_min_32_chars/$ENCRYPTION_KEY_ESCAPED/" .env
    
    echo -e "${GREEN}✓ .env file created with secure secrets${NC}"
fi

echo "Step 1: Validating docker-compose.yml..."
if docker compose config --quiet; then
    echo -e "${GREEN}✓ Compose configuration is valid${NC}"
else
    echo -e "${RED}✗ Compose configuration has errors${NC}"
    exit 1
fi
echo

echo "Step 2: Starting services..."
docker compose up -d

echo
echo "Step 3: Waiting for services to become healthy (max 3 minutes)..."
TIMEOUT=180
ELAPSED=0
INTERVAL=5
AUTHELIA_ENABLED=false

if docker compose config --services | grep -qx "authelia"; then
    AUTHELIA_ENABLED=true
fi

while [ $ELAPSED -lt $TIMEOUT ]; do
    sleep $INTERVAL
    ELAPSED=$((ELAPSED + INTERVAL))
    
    # Get service health status
    POSTGRES_HEALTH=$(docker compose ps postgres --format json | jq -r '.[0].Health // "starting"')
    MINIO_HEALTH=$(docker compose ps minio --format json | jq -r '.[0].Health // "starting"')
    if [ "$AUTHELIA_ENABLED" = "true" ]; then
        AUTHELIA_HEALTH=$(docker compose ps authelia --format json | jq -r '.[0].Health // "starting"')
    else
        AUTHELIA_HEALTH="disabled"
    fi
    BACKEND_HEALTH=$(docker compose ps backend --format json | jq -r '.[0].Health // "starting"')
    FRONTEND_HEALTH=$(docker compose ps frontend --format json | jq -r '.[0].Health // "starting"')
    
    echo "  [$ELAPSED/$TIMEOUT s] PostgreSQL: $POSTGRES_HEALTH, MinIO: $MINIO_HEALTH, Authelia: $AUTHELIA_HEALTH, Backend: $BACKEND_HEALTH, Frontend: $FRONTEND_HEALTH"
    
    # Check if all core services are healthy
    ALL_HEALTHY=false
    if [ "$POSTGRES_HEALTH" = "healthy" ] && \
       [ "$MINIO_HEALTH" = "healthy" ] && \
       [ "$BACKEND_HEALTH" = "healthy" ] && \
       [ "$FRONTEND_HEALTH" = "healthy" ]; then
        if [ "$AUTHELIA_ENABLED" = "true" ]; then
            [ "$AUTHELIA_HEALTH" = "healthy" ] && ALL_HEALTHY=true
        else
            ALL_HEALTHY=true
        fi
    fi

    if [ "$ALL_HEALTHY" = "true" ]; then
        echo -e "${GREEN}✓ All services are healthy${NC}"
        break
    fi
    
    # Check for unhealthy or exited services
    UNHEALTHY=$(docker compose ps --format json | jq -r '
        .[] |
        select(
            .Health == "unhealthy" or
            (
                .State == "exited" and
                (
                    .Service != "minio-init" or
                    ((.ExitCode // 1) != 0)
                )
            )
        ) |
        .Name
    ')
    if [ -n "$UNHEALTHY" ]; then
        echo -e "${RED}✗ Unhealthy or exited services detected:${NC}"
        echo "$UNHEALTHY"
        echo
        echo "Logs from unhealthy services:"
        for service in $UNHEALTHY; do
            echo "--- $service ---"
            docker logs "$service" --tail 50
        done
        exit 1
    fi
done

if [ $ELAPSED -ge $TIMEOUT ]; then
    echo -e "${RED}✗ Timeout waiting for services to become healthy${NC}"
    docker compose ps
    exit 1
fi

echo
echo "Step 4: Testing service endpoints..."

# Test frontend
echo -n "  Testing frontend (http://localhost/)... "
if curl -sf http://localhost/ > /dev/null; then
    echo -e "${GREEN}✓${NC}"
else
    echo -e "${RED}✗${NC}"
    exit 1
fi

# Test backend health
echo -n "  Testing backend health (inside backend container)... "
if docker compose exec -T backend wget --no-verbose --tries=1 --spider http://localhost:8080/q/health/ready > /dev/null 2>&1; then
    echo -e "${GREEN}✓${NC}"
else
    echo -e "${RED}✗${NC}"
    exit 1
fi

# Test Traefik dashboard
echo -n "  Testing Traefik dashboard (http://localhost:8080)... "
if curl -sf http://localhost:8080 > /dev/null; then
    echo -e "${GREEN}✓${NC}"
else
    echo -e "${RED}✗${NC}"
    exit 1
fi

# Test MinIO console
echo -n "  Testing MinIO console (http://localhost:9001)... "
if curl -sf http://localhost:9001 > /dev/null; then
    echo -e "${GREEN}✓${NC}"
else
    echo -e "${RED}✗${NC}"
    exit 1
fi

echo
echo "Step 5: Verifying MinIO buckets..."
BUCKETS=$(docker compose exec -T minio sh -c '
    mc alias set regattadesk http://localhost:9000 "${MINIO_ROOT_USER:-regattadesk}" "${MINIO_ROOT_PASSWORD}" >/dev/null 2>&1 &&
    mc ls regattadesk
' 2>/dev/null || echo "")
if echo "$BUCKETS" | grep -q "line-scan-tiles" && echo "$BUCKETS" | grep -q "line-scan-manifests"; then
    echo -e "${GREEN}✓ MinIO buckets created successfully${NC}"
else
    echo -e "${RED}✗ MinIO buckets not found${NC}"
    exit 1
fi

echo
echo "======================================"
echo -e "${GREEN}✓ All smoke tests passed!${NC}"
echo "======================================"
echo
echo "Services are running:"
docker compose ps
echo
echo "To view logs: docker compose logs -f"
echo "To stop: docker compose down"
echo "To stop and remove volumes: docker compose down -v"
