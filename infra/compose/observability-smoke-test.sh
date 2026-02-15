#!/bin/bash
# Observability Stack Smoke Test
# Tests that health, metrics, and tracing endpoints are functional

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
echo -e "${YELLOW}Note: Metrics endpoint should NOT be publicly accessible${NC}"
# Test that metrics are NOT accessible via public routing (should return 404)
echo -n "Testing Metrics Public Access Denial... "
status=$(curl -s -o /dev/null -w "%{http_code}" "$BACKEND_URL/q/metrics" || echo "000")
if [[ "$status" = "404" ]]; then
    echo -e "${GREEN}✓ OK${NC} (HTTP $status - correctly denied)"
else
    echo -e "${RED}✗ FAILED${NC} (HTTP $status - should be 404 to deny public access)"
    ((failures++))
fi

# Test that metrics ARE accessible via internal network
echo -n "Testing Metrics Internal Access... "
if docker exec regattadesk-backend curl -s http://localhost:8080/q/metrics | grep -q "jvm_memory"; then
    echo -e "${GREEN}✓ OK${NC} (accessible from internal network)"
else
    echo -e "${RED}✗ FAILED${NC} (not accessible from internal network)"
    ((failures++))
fi
echo ""

echo "=== Observability Services ==="
test_endpoint "Prometheus" "$PROMETHEUS_URL/-/ready" || ((failures++))
test_endpoint "Jaeger UI" "$JAEGER_URL/" || ((failures++))
test_endpoint "Grafana" "$GRAFANA_URL/api/health" || ((failures++))
echo ""

echo "=== Prometheus Targets ==="
test_content "Backend Target" "$PROMETHEUS_URL/api/v1/targets" "regattadesk-backend" || ((failures++))
echo ""

echo "==================================="
if [[ $failures -eq 0 ]]; then
    echo -e "${GREEN}All tests passed!${NC}"
    exit 0
else
    echo -e "${RED}$failures test(s) failed${NC}"
    exit 1
fi
