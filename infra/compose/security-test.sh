#!/bin/bash
#
# Security Regression Tests
# Tests security configurations and verifies that sensitive endpoints are not exposed
#
# Prerequisites:
# - Docker Compose stack running
#
# Usage: ./security-test.sh
#

set -euo pipefail

# Configuration
SEPARATOR="======================================"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Test counters
TESTS_RUN=0
TESTS_PASSED=0
TESTS_FAILED=0

# Helper functions
log_info() {
    local message="$1"
    echo -e "${GREEN}[INFO]${NC} ${message}"
    return 0
}

log_error() {
    local message="$1"
    echo -e "${RED}[ERROR]${NC} ${message}" >&2
    return 0
}

log_warn() {
    local message="$1"
    echo -e "${YELLOW}[WARN]${NC} ${message}"
    return 0
}

test_start() {
    local test_name="$1"
    TESTS_RUN=$((TESTS_RUN + 1))
    log_info "Test ${TESTS_RUN}: ${test_name}"
    return 0
}

test_pass() {
    TESTS_PASSED=$((TESTS_PASSED + 1))
    echo -e "${GREEN}✓ PASS${NC}"
    echo
    return 0
}

test_fail() {
    local reason="$1"
    TESTS_FAILED=$((TESTS_FAILED + 1))
    log_error "✗ FAIL: ${reason}"
    echo
    return 0
}

# Check if stack is running
check_stack() {
    log_info "Checking if Docker Compose stack is running..."
    if ! docker compose ps | grep -q "regattadesk-traefik"; then
        log_error "Traefik service is not running. Start the stack with: docker compose up -d"
        exit 1
    fi
    log_info "Stack is running"
    echo
    return 0
}

# Test: Traefik dashboard should NOT be accessible on port 8080
test_traefik_dashboard_blocked() {
    test_start "Traefik dashboard/API NOT accessible on port 8080"
    
    # Try to access dashboard
    local status_code
    status_code=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:8080" 2>/dev/null || echo "000")
    
    if [[ "$status_code" == "000" ]]; then
        log_info "Port 8080 is not accessible (correct - port not published)"
        test_pass
    else
        test_fail "Port 8080 is accessible with status ${status_code} (security risk)"
    fi
    return 0
}

# Test: Traefik API endpoint should NOT be accessible
test_traefik_api_blocked() {
    test_start "Traefik API endpoint NOT accessible"
    
    local status_code
    status_code=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:8080/api/overview" 2>/dev/null || echo "000")
    
    if [[ "$status_code" == "000" ]]; then
        log_info "Traefik API not accessible (correct - secure configuration)"
        test_pass
    else
        test_fail "Traefik API is accessible with status ${status_code} (security risk)"
    fi
    return 0
}

# Test: Verify insecure API flag is not set
test_insecure_api_flag() {
    test_start "Traefik insecure API flag NOT present in configuration"
    
    if docker compose config | grep -q "api.insecure=true"; then
        test_fail "Found '--api.insecure=true' in Traefik configuration (security risk)"
    else
        log_info "Insecure API flag not present (correct)"
        test_pass
    fi
    return 0
}

# Test: Verify dashboard port is not published
test_dashboard_port_not_published() {
    test_start "Traefik dashboard port (8080) NOT published"
    
    if docker compose config | grep -q "8080:8080"; then
        test_fail "Port 8080 is published in docker-compose.yml (security risk)"
    else
        log_info "Dashboard port not published (correct)"
        test_pass
    fi
    return 0
}

# Test: Regular routing still works
test_routing_still_works() {
    test_start "Regular routing through Traefik still works"
    
    local status_code
    status_code=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost/" 2>/dev/null || echo "000")
    
    if [[ "$status_code" == "200" ]] || [[ "$status_code" == "404" ]]; then
        log_info "Traefik routing works correctly"
        test_pass
    else
        test_fail "Traefik routing may be broken, got status ${status_code}"
    fi
    return 0
}

# Main test execution
main() {
    echo "$SEPARATOR"
    echo "  Security Regression Tests"
    echo "$SEPARATOR"
    echo
    
    # Change to compose directory
    cd "$(dirname "$0")" || exit 1
    
    # Check if stack is running
    check_stack
    
    # Run security tests
    test_traefik_dashboard_blocked
    test_traefik_api_blocked
    test_insecure_api_flag
    test_dashboard_port_not_published
    test_routing_still_works
    
    # Summary
    echo "$SEPARATOR"
    echo "  Test Summary"
    echo "$SEPARATOR"
    echo "Tests Run:    ${TESTS_RUN}"
    echo -e "Tests Passed: ${GREEN}${TESTS_PASSED}${NC}"
    echo -e "Tests Failed: ${RED}${TESTS_FAILED}${NC}"
    echo
    
    if [[ $TESTS_FAILED -eq 0 ]]; then
        echo -e "${GREEN}✓ All security tests passed!${NC}"
        echo "Traefik dashboard/API is properly secured."
        return 0
    else
        echo -e "${RED}✗ Some security tests failed.${NC}"
        echo "Review the failures above and fix security issues."
        return 1
    fi
}

# Run main
main
exit $?
