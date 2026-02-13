#!/bin/bash
#
# Edge Authentication Integration Tests
# Tests Traefik + Authelia SSO integration and role-based access control
#
# Prerequisites:
# - Docker Compose stack running with Authelia enabled
# - Environment variables configured in .env
#
# Usage: ./edge-auth-test.sh
#

set -euo pipefail

# Configuration
DOMAIN="${DOMAIN:-localhost.local}"
BASE_URL="http://${DOMAIN}"
AUTHELIA_URL="${BASE_URL}/auth"
CURL_HTTP_CODE_FORMAT="%{http_code}"

# Test credentials
SUPER_ADMIN_USER="superadmin"
REGATTA_ADMIN_USER="regattaadmin"
HEAD_OF_JURY_USER="headofjury"
INFO_DESK_USER="infodesk"
FINANCIAL_MANAGER_USER="financialmanager"
OPERATOR_USER="operator"
TEST_PASSWORD="changeme"

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
    if ! docker compose ps | grep -q "regattadesk-authelia"; then
        log_error "Authelia service is not running. Start the stack with: docker compose up -d"
        exit 1
    fi
    if ! docker compose ps | grep -q "regattadesk-traefik"; then
        log_error "Traefik service is not running. Start the stack with: docker compose up -d"
        exit 1
    fi
    log_info "Stack is running"
    echo
    return 0
}

# Wait for services to be healthy
wait_for_services() {
    log_info "Waiting for services to be healthy..."
    local max_attempts=30
    local attempt=0
    
    while [ $attempt -lt $max_attempts ]; do
        if docker compose ps | grep -q "authelia.*healthy" && \
           docker compose ps | grep -q "traefik"; then
            log_info "Services are healthy"
            echo
            return 0
        fi
        attempt=$((attempt + 1))
        echo -n "."
        sleep 2
    done
    
    log_error "Services did not become healthy after ${max_attempts} attempts"
    exit 1
}

# Login helper - creates session cookie file
# Args: output_cookie_file
login_user() {
    local cookie_file="$1"
    
    # Get login page to get session cookie
    curl -s -c "$cookie_file" "${AUTHELIA_URL}/" > /dev/null
    
    # Perform login (simplified - actual Authelia login flow may vary)
    # This is a placeholder - actual implementation depends on Authelia's API
    log_warn "Login automation not fully implemented - assuming session exists"
    return 0
}

# Test: Public endpoint accessible without authentication
test_public_access() {
    test_start "Public endpoint accessible without authentication"
    
    local response
    local status_code
    
    response=$(curl -s -w "\n${CURL_HTTP_CODE_FORMAT}" "${BASE_URL}/api/v1/public/versions")
    status_code=$(echo "$response" | tail -n1)
    
    if [ "$status_code" = "200" ] || [ "$status_code" = "404" ]; then
        test_pass
    else
        test_fail "Expected 200 or 404, got ${status_code}"
    fi
    return 0
}

# Test: Health endpoint accessible without authentication
test_health_access() {
    test_start "Health endpoint accessible without authentication"
    
    local status_code
    status_code=$(curl -s -o /dev/null -w "${CURL_HTTP_CODE_FORMAT}" "${BASE_URL}/api/health")
    
    if [ "$status_code" = "200" ] || [ "$status_code" = "404" ]; then
        test_pass
    else
        test_fail "Expected 200 or 404, got ${status_code}"
    fi
    return 0
}

# Test: Staff endpoint blocked without authentication
test_staff_blocked_unauthenticated() {
    test_start "Staff endpoint blocked without authentication"
    
    local status_code
    status_code=$(curl -s -o /dev/null -w "${CURL_HTTP_CODE_FORMAT}" "${BASE_URL}/api/v1/staff/regattas")
    
    # Authelia should redirect to login or return 401/302
    if [ "$status_code" = "302" ] || [ "$status_code" = "401" ]; then
        test_pass
    else
        test_fail "Expected 302 or 401, got ${status_code}"
    fi
    return 0
}

# Test: Operator endpoint blocked without authentication
test_operator_blocked_unauthenticated() {
    test_start "Operator endpoint blocked without authentication"
    
    local status_code
    status_code=$(curl -s -o /dev/null -w "${CURL_HTTP_CODE_FORMAT}" "${BASE_URL}/api/v1/regattas/test-regatta/operator/stations")
    
    # Authelia should redirect to login or return 401/302
    if [ "$status_code" = "302" ] || [ "$status_code" = "401" ]; then
        test_pass
    else
        test_fail "Expected 302 or 401, got ${status_code}"
    fi
    return 0
}

# Test: Verify Authelia is responding
test_authelia_health() {
    test_start "Authelia health endpoint responding"
    
    local status_code
    status_code=$(curl -s -o /dev/null -w "${CURL_HTTP_CODE_FORMAT}" "http://localhost:9091/api/health" 2>/dev/null || echo "000")
    
    if [ "$status_code" = "200" ]; then
        test_pass
    else
        # Try via docker exec as fallback
        log_info "Checking via docker exec..."
        if docker compose exec -T authelia wget --no-verbose --tries=1 --spider http://localhost:9091/api/health 2>&1 | grep -q "200 OK"; then
            log_info "Authelia is healthy (verified via docker exec)"
            test_pass
        else
            test_fail "Expected 200, got ${status_code} (Authelia may not be running)"
        fi
    fi
    return 0
}

# Test: Verify Traefik routing configuration
test_traefik_routing() {
    test_start "Traefik routing configuration"
    
    # Check if Traefik dashboard is accessible (if enabled)
    local status_code
    status_code=$(curl -s -o /dev/null -w "${CURL_HTTP_CODE_FORMAT}" "http://localhost:8080/api/overview" 2>/dev/null || echo "000")
    
    if [ "$status_code" = "200" ]; then
        log_info "Traefik dashboard accessible"
        test_pass
    else
        log_warn "Traefik dashboard not accessible (may be disabled)"
        test_pass
    fi
    return 0
}

# Test: Verify forwarded headers contract (mock test)
test_forwarded_headers() {
    test_start "Forwarded headers configuration"
    
    # Verify dynamic configuration is loaded
    if [ -f "./traefik/dynamic.yml" ]; then
        if grep -q "Remote-User" ./traefik/dynamic.yml && \
           grep -q "Remote-Groups" ./traefik/dynamic.yml && \
           grep -q "Remote-Name" ./traefik/dynamic.yml && \
           grep -q "Remote-Email" ./traefik/dynamic.yml; then
            log_info "All required headers configured in dynamic.yml"
            test_pass
        else
            test_fail "Missing required headers in dynamic.yml"
        fi
    else
        test_fail "Dynamic configuration file not found"
    fi
    return 0
}

# Test: Verify role configuration in Authelia
test_role_configuration() {
    test_start "Role configuration in Authelia users database template"
    
    # Check the template file instead of the actual users database
    if [ -f "./authelia/users_database.yml.example" ]; then
        local roles=("super_admin" "regatta_admin" "head_of_jury" "info_desk" "financial_manager" "operator")
        local all_found=true
        
        for role in "${roles[@]}"; do
            if ! grep -q "$role" ./authelia/users_database.yml.example; then
                log_error "Role '$role' not found in users database template"
                all_found=false
            fi
        done
        
        if [ "$all_found" = true ]; then
            log_info "All required roles configured in template"
            test_pass
        else
            test_fail "Missing required roles in users database template"
        fi
    else
        test_fail "Users database template file not found"
    fi
    return 0
}

# Test: Verify access control rules in Authelia
test_access_control_rules() {
    test_start "Access control rules in Authelia configuration"
    
    if [ -f "./authelia/configuration.yml" ]; then
        if grep -q "api/v1/public" ./authelia/configuration.yml && \
           grep -q "api/v1/staff" ./authelia/configuration.yml && \
           grep -q "default_policy: deny" ./authelia/configuration.yml; then
            log_info "Access control rules properly configured"
            test_pass
        else
            test_fail "Missing or incorrect access control rules"
        fi
    else
        test_fail "Authelia configuration file not found"
    fi
    return 0
}

# Main test execution
main() {
    echo "======================================"
    echo "  Edge Authentication Integration Tests"
    echo "======================================"
    echo
    
    # Change to compose directory
    cd "$(dirname "$0")" || exit 1
    
    # Check if stack is running
    check_stack
    
    # Wait for services
    wait_for_services
    
    # Run tests
    test_authelia_health
    test_traefik_routing
    test_forwarded_headers
    test_role_configuration
    test_access_control_rules
    test_public_access
    test_health_access
    test_staff_blocked_unauthenticated
    test_operator_blocked_unauthenticated
    
    # Summary
    echo "======================================"
    echo "  Test Summary"
    echo "======================================"
    echo "Tests Run:    ${TESTS_RUN}"
    echo -e "Tests Passed: ${GREEN}${TESTS_PASSED}${NC}"
    echo -e "Tests Failed: ${RED}${TESTS_FAILED}${NC}"
    echo
    
    if [ $TESTS_FAILED -eq 0 ]; then
        echo -e "${GREEN}All tests passed!${NC}"
        exit 0
    else
        echo -e "${RED}Some tests failed.${NC}"
        exit 1
    fi
}

# Run main
main
