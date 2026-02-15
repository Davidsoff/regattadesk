#!/bin/bash
# Security Test: Verify metrics endpoint is not publicly accessible
# This script tests that:
# 1. /q/metrics is NOT accessible via Traefik (public route)
# 2. /q/metrics IS accessible from internal network (Prometheus scraping)

set -e

echo "=== RegattaDesk Metrics Security Test ==="
echo ""

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

BACKEND_URL="${BACKEND_URL:-http://localhost}"

echo "Testing that /q/metrics is NOT publicly accessible..."
echo ""

# Test 1: Public access should be denied (404)
echo -n "Test 1: Public access via Traefik should return 404... "
status=$(curl -s -o /dev/null -w "%{http_code}" "$BACKEND_URL/q/metrics" || echo "000")

if [[ "$status" = "404" ]]; then
    echo -e "${GREEN}✓ PASS${NC} (HTTP $status - correctly denied)"
else
    echo -e "${RED}✗ FAIL${NC} (HTTP $status - should be 404)"
    echo "ERROR: Metrics endpoint is publicly accessible!" >&2
    exit 1
fi

# Test 2: Health endpoints should still be publicly accessible
echo -n "Test 2: Health endpoint should still be public (200)... "
status=$(curl -s -o /dev/null -w "%{http_code}" "$BACKEND_URL/q/health/ready" || echo "000")

if [[ "$status" = "200" ]]; then
    echo -e "${GREEN}✓ PASS${NC} (HTTP $status)"
else
    echo -e "${RED}✗ FAIL${NC} (HTTP $status - should be 200)"
    exit 1
fi

# Test 3: Metrics should be accessible from internal network
echo -n "Test 3: Internal network access should work... "
if docker exec regattadesk-backend curl -s http://localhost:8080/q/metrics | grep -q "jvm_memory"; then
    echo -e "${GREEN}✓ PASS${NC} (accessible from backend container)"
else
    echo -e "${RED}✗ FAIL${NC} (not accessible from internal network)"
    exit 1
fi

# Test 4: Prometheus should be able to scrape metrics
echo -n "Test 4: Prometheus scraping should work... "
if docker exec regattadesk-prometheus curl -s http://backend:8080/q/metrics | grep -q "jvm_memory"; then
    echo -e "${GREEN}✓ PASS${NC} (Prometheus can scrape)"
else
    echo -e "${RED}✗ FAIL${NC} (Prometheus cannot scrape)"
    exit 1
fi

echo ""
echo "==================================="
echo -e "${GREEN}All security tests passed!${NC}"
echo ""
echo "Summary:"
echo "  ✓ Metrics endpoint is NOT publicly accessible"
echo "  ✓ Health endpoints remain publicly accessible"
echo "  ✓ Metrics are accessible from internal network"
echo "  ✓ Prometheus can successfully scrape metrics"
