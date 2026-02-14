#!/bin/bash
# Observability Stack Smoke Test
# Tests that health, metrics, and tracing endpoints are functional
#
# Note: By default, Prometheus and Jaeger are only accessible internally.
# To test direct host access, run with docker-compose.observability.dev.yml:
# docker compose -f docker-compose.yml -f docker-compose.observability.yml -f docker-compose.observability.dev.yml up -d

set -e

echo "=== RegattaDesk Observability Stack Smoke Test ==="
echo ""

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

BACKEND_URL="${BACKEND_URL:-http://localhost}"
PROMETHEUS_URL="${PROMETHEUS_URL:-http://localhost:9090}"
JAEGER_URL="${JAEGER_URL:-http://localhost:16686}"
GRAFANA_URL="${GRAFANA_URL:-http://localhost/grafana}"

# Check if we're running in dev mode (with host-exposed ports)
# Dev mode is detected by checking if Prometheus has port 9090 published to host
# jq: Check array not empty, then check if port 9090 is published
DEV_MODE=false
if docker compose ps prometheus --format json 2>/dev/null | jq -e 'if length > 0 then .[0].Publishers[]? | select(.PublishedPort == 9090) else false end' > /dev/null 2>&1; then
    DEV_MODE=true
fi

test_endpoint() {
    local name="$1"
    local url="$2"
    local expected_status="${3:-200}"
    
    echo -n "Testing $name... "
    
    status=$(curl -s -o /dev/null -w "%{http_code}" "$url" || echo "000")
    
    if [[ "$status" = "$expected_status" ]]; then
        echo -e "${GREEN}✓ OK${NC} (HTTP $status)"
        return 0
    else
        echo -e "${RED}✗ FAILED${NC} (HTTP $status, expected $expected_status)"
        return 1
    fi
}

test_content() {
    local name="$1"
    local url="$2"
    local expected_content="$3"
    
    echo -n "Testing $name content... "
    
    content=$(curl -s "$url" || echo "")
    
    if echo "$content" | grep -q "$expected_content"; then
        echo -e "${GREEN}✓ OK${NC}"
        return 0
    else
        echo -e "${RED}✗ FAILED${NC} (expected content not found)"
        return 1
    fi
}

failures=0

echo "=== Backend Health Endpoints ==="
test_endpoint "Custom Health" "$BACKEND_URL/api/health" || ((failures++))
test_endpoint "Readiness Probe" "$BACKEND_URL/q/health/ready" || ((failures++))
test_endpoint "Liveness Probe" "$BACKEND_URL/q/health/live" || ((failures++))
test_endpoint "Startup Probe" "$BACKEND_URL/q/health/started" || ((failures++))
echo ""

echo "=== Metrics Endpoints ==="
test_endpoint "Prometheus Metrics" "$BACKEND_URL/q/metrics" || ((failures++))
test_content "JVM Metrics" "$BACKEND_URL/q/metrics" "jvm_memory" || ((failures++))
test_content "HTTP Metrics" "$BACKEND_URL/q/metrics" "http_server_requests" || ((failures++))
echo ""

echo "=== Observability Services ==="
if [[ "$DEV_MODE" == "true" ]]; then
    echo "  (Running in dev mode - testing direct host access)"
    test_endpoint "Prometheus" "$PROMETHEUS_URL/-/ready" || ((failures++))
    test_endpoint "Jaeger UI" "$JAEGER_URL/" || ((failures++))
else
    echo -e "  ${YELLOW}Prometheus and Jaeger not exposed to host (secure default)${NC}"
    echo -e "  ${YELLOW}Use docker-compose.observability.dev.yml for direct access${NC}"
fi
test_endpoint "Grafana" "$GRAFANA_URL/api/health" || ((failures++))
echo ""

if [[ "$DEV_MODE" == "true" ]]; then
    echo "=== Prometheus Targets ==="
    test_content "Backend Target" "$PROMETHEUS_URL/api/v1/targets" "regattadesk-backend" || ((failures++))
    echo ""
fi

echo "==================================="
if [[ $failures -eq 0 ]]; then
    echo -e "${GREEN}All tests passed!${NC}"
    exit 0
else
    echo -e "${RED}$failures test(s) failed${NC}"
    exit 1
fi
