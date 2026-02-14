#!/bin/bash
# Security validation test for Grafana protection
# Tests that Grafana properly requires credentials and Authelia protection

set -e

echo "=========================================="
echo "Grafana Security Validation Test"
echo "=========================================="
echo ""

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_DIR="$SCRIPT_DIR"

# Test 1: Verify Grafana fails without credentials
echo "Test 1: Verify Grafana requires credentials in environment"
echo "-----------------------------------------------------------"

# Create test env file without Grafana credentials
TEST_ENV_FILE="/tmp/test-grafana-security-$$.env"
cat > "$TEST_ENV_FILE" << 'EOF'
DOMAIN=localhost.local
POSTGRES_DB=regattadesk
POSTGRES_USER=regattadesk
POSTGRES_PASSWORD=test_password
POSTGRES_AUTHELIA_DB=authelia
POSTGRES_PORT=5432
MINIO_ROOT_USER=regattadesk
MINIO_ROOT_PASSWORD=test_password
MINIO_API_PORT=9000
MINIO_CONSOLE_PORT=9001
AUTHELIA_JWT_SECRET=changeme_jwt_secret_min_32_chars_test
AUTHELIA_SESSION_SECRET=changeme_session_secret_min_32_chars
AUTHELIA_STORAGE_ENCRYPTION_KEY=changeme_encryption_key_min_32_chars
AUTHELIA_STORAGE_POSTGRES_DATABASE=authelia
AUTHELIA_STORAGE_POSTGRES_USERNAME=authelia
AUTHELIA_STORAGE_POSTGRES_PASSWORD=test_password
TRAEFIK_HTTP_PORT=80
TRAEFIK_HTTPS_PORT=443
TRAEFIK_DASHBOARD_PORT=8080
TRAEFIK_LOG_LEVEL=INFO
EOF

# Try to validate config without Grafana credentials - should fail
if docker compose --env-file "$TEST_ENV_FILE" \
    -f "$COMPOSE_DIR/docker-compose.yml" \
    -f "$COMPOSE_DIR/docker-compose.observability.yml" \
    config >/dev/null 2>&1; then
    echo "❌ FAILED: Docker Compose should fail without Grafana credentials"
    rm -f "$TEST_ENV_FILE"
    exit 1
else
    echo "✅ PASSED: Docker Compose correctly requires GRAFANA_ADMIN_USER"
fi

# Test 2: Verify Grafana works with credentials
echo ""
echo "Test 2: Verify Grafana accepts valid credentials"
echo "---------------------------------------------------"

# Add Grafana credentials to test env
cat >> "$TEST_ENV_FILE" << 'EOF'
GRAFANA_ADMIN_USER=test_admin
GRAFANA_ADMIN_PASSWORD=test_secure_password_123
EOF

# Try to validate config with credentials - should succeed
if docker compose --env-file "$TEST_ENV_FILE" \
    -f "$COMPOSE_DIR/docker-compose.yml" \
    -f "$COMPOSE_DIR/docker-compose.observability.yml" \
    config --quiet; then
    echo "✅ PASSED: Docker Compose validates successfully with Grafana credentials"
else
    echo "❌ FAILED: Docker Compose should succeed with valid credentials"
    rm -f "$TEST_ENV_FILE"
    exit 1
fi

# Test 3: Verify Authelia middleware is configured
echo ""
echo "Test 3: Verify Authelia ForwardAuth middleware is configured"
echo "--------------------------------------------------------------"

CONFIG_OUTPUT=$(docker compose --env-file "$TEST_ENV_FILE" \
    -f "$COMPOSE_DIR/docker-compose.yml" \
    -f "$COMPOSE_DIR/docker-compose.observability.yml" \
    config)

if echo "$CONFIG_OUTPUT" | grep -q "traefik.http.routers.grafana.middlewares.*authelia"; then
    echo "✅ PASSED: Authelia middleware is configured for Grafana route"
else
    echo "❌ FAILED: Authelia middleware not found in Grafana configuration"
    rm -f "$TEST_ENV_FILE"
    exit 1
fi

# Test 4: Verify no default password fallback in config
echo ""
echo "Test 4: Verify no default password fallback in source files"
echo "-------------------------------------------------------------"

if grep -q "GRAFANA_ADMIN_PASSWORD:-admin" "$COMPOSE_DIR/docker-compose.observability.yml"; then
    echo "❌ FAILED: Default password fallback still exists in docker-compose.observability.yml"
    rm -f "$TEST_ENV_FILE"
    exit 1
else
    echo "✅ PASSED: No default password fallback in docker-compose.observability.yml"
fi

# Test 5: Verify documentation exists
echo ""
echo "Test 5: Verify security documentation exists"
echo "----------------------------------------------"

if [[ -f "$COMPOSE_DIR/SECURITY-GRAFANA.md" ]]; then
    echo "✅ PASSED: SECURITY-GRAFANA.md documentation exists"
else
    echo "❌ FAILED: SECURITY-GRAFANA.md documentation not found"
    rm -f "$TEST_ENV_FILE"
    exit 1
fi

if grep -q "GRAFANA_ADMIN_USER" "$COMPOSE_DIR/.env.example"; then
    echo "✅ PASSED: .env.example includes Grafana credentials"
else
    echo "❌ FAILED: .env.example missing Grafana credentials"
    rm -f "$TEST_ENV_FILE"
    exit 1
fi

# Cleanup
rm -f "$TEST_ENV_FILE"

echo ""
echo "=========================================="
echo "✅ All security validation tests passed!"
echo "=========================================="
echo ""
echo "Summary:"
echo "  • Grafana requires explicit credential configuration"
echo "  • No default password fallback exists"
echo "  • Authelia ForwardAuth middleware is configured"
echo "  • Security documentation is in place"
echo ""
echo "Manual verification still required:"
echo "  1. Start the stack with observability enabled"
echo "  2. Attempt to access http://localhost/grafana without authentication"
echo "  3. Verify redirect to Authelia login page"
echo "  4. After Authelia authentication, verify Grafana login prompt"
echo ""
