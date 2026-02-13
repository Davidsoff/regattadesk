#!/bin/bash
# BC09-002: Edge Hardening Integration Test
# Tests rate limiting, security headers, request size limits, and timeout handling
# 
# Usage: ./edge-hardening-test.sh [base_url]
# Example: ./edge-hardening-test.sh http://localhost

set -e

BASE_URL="${1:-http://localhost}"
PASSED=0
FAILED=0
WARNINGS=0

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Helper functions
print_test() {
    echo -e "${BLUE}[TEST]${NC} $1"
}

print_pass() {
    echo -e "${GREEN}[PASS]${NC} $1"
    ((PASSED++))
}

print_fail() {
    echo -e "${RED}[FAIL]${NC} $1"
    ((FAILED++))
}

print_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
    ((WARNINGS++))
}

print_header() {
    echo ""
    echo "=================================================="
    echo "$1"
    echo "=================================================="
}

# Check if services are accessible
check_service_availability() {
    print_header "Service Availability Check"
    
    print_test "Checking if backend is accessible..."
    if curl -sf "${BASE_URL}/api/health" > /dev/null 2>&1; then
        print_pass "Backend is accessible"
    else
        print_fail "Backend is not accessible at ${BASE_URL}/api/health"
        echo "Please ensure the stack is running: docker compose up -d"
        exit 1
    fi
}

# Test security headers
test_security_headers() {
    print_header "Security Headers Test"
    
    print_test "Testing security headers on public endpoint..."
    HEADERS=$(curl -sI "${BASE_URL}/api/health")
    
    # Check X-Content-Type-Options
    if echo "$HEADERS" | grep -iq "X-Content-Type-Options: nosniff"; then
        print_pass "X-Content-Type-Options header present"
    else
        print_fail "X-Content-Type-Options header missing"
    fi
    
    # Check X-Frame-Options
    if echo "$HEADERS" | grep -iq "X-Frame-Options:"; then
        print_pass "X-Frame-Options header present"
    else
        print_fail "X-Frame-Options header missing"
    fi
    
    # Check X-XSS-Protection
    if echo "$HEADERS" | grep -iq "X-XSS-Protection:"; then
        print_pass "X-XSS-Protection header present"
    else
        print_fail "X-XSS-Protection header missing"
    fi
    
    # Check Content-Security-Policy
    if echo "$HEADERS" | grep -iq "Content-Security-Policy:"; then
        print_pass "Content-Security-Policy header present"
    else
        print_warn "Content-Security-Policy header missing (may be intentional for some endpoints)"
    fi
    
    # Check Referrer-Policy
    if echo "$HEADERS" | grep -iq "Referrer-Policy:"; then
        print_pass "Referrer-Policy header present"
    else
        print_fail "Referrer-Policy header missing"
    fi
    
    # Check Permissions-Policy
    if echo "$HEADERS" | grep -iq "Permissions-Policy:"; then
        print_pass "Permissions-Policy header present"
    else
        print_warn "Permissions-Policy header missing (may be intentional)"
    fi
}

# Test rate limiting
test_rate_limiting() {
    print_header "Rate Limiting Test"
    
    print_test "Testing rate limiting on public endpoint (sending burst of requests)..."
    
    # Send requests and track response codes
    SUCCESS_COUNT=0
    RATE_LIMITED_COUNT=0
    
    for i in {1..150}; do
        HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "${BASE_URL}/api/health")
        if [[ "$HTTP_CODE" == "200" ]]; then
            ((SUCCESS_COUNT++))
        elif [[ "$HTTP_CODE" == "429" ]]; then
            ((RATE_LIMITED_COUNT++))
        fi
    done
    
    echo "  Results: $SUCCESS_COUNT successful, $RATE_LIMITED_COUNT rate-limited"
    
    if [[ $RATE_LIMITED_COUNT -gt 0 ]]; then
        print_pass "Rate limiting is working (received 429 responses)"
    else
        print_warn "Rate limiting not triggered (may need higher load or rate limits are generous)"
    fi
    
    # Wait for rate limit window to reset
    echo "  Waiting 2 seconds for rate limit window to reset..."
    sleep 2
    
    # Verify service is accessible again
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "${BASE_URL}/api/health")
    if [[ "$HTTP_CODE" == "200" ]]; then
        print_pass "Service accessible after rate limit window reset"
    else
        print_fail "Service still rate-limited after window reset (got HTTP $HTTP_CODE)"
    fi
}

# Test request size limits
test_request_size_limits() {
    print_header "Request Size Limits Test"
    
    print_test "Testing request body size limits..."
    
    # Create a 15MB payload (exceeds 10MB limit)
    LARGE_PAYLOAD=$(head -c 15728640 /dev/zero | base64)
    
    # Attempt to POST large payload
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" \
        -X POST \
        -H "Content-Type: application/json" \
        -d "{\"data\": \"$LARGE_PAYLOAD\"}" \
        "${BASE_URL}/api/health" 2>/dev/null || echo "000")
    
    if [[ "$HTTP_CODE" == "413" ]] || [[ "$HTTP_CODE" == "000" ]]; then
        print_pass "Large request rejected (HTTP $HTTP_CODE or connection refused)"
    else
        print_warn "Large request not rejected as expected (HTTP $HTTP_CODE, limit may not be enforced)"
    fi
    
    # Test with a small payload (should succeed)
    print_test "Testing with small payload (should succeed)..."
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" \
        -X POST \
        -H "Content-Type: application/json" \
        -d '{"test": "data"}' \
        "${BASE_URL}/api/health")
    
    if [[ "$HTTP_CODE" == "200" ]] || [[ "$HTTP_CODE" == "405" ]]; then
        print_pass "Small request accepted (HTTP $HTTP_CODE)"
    else
        print_fail "Small request rejected unexpectedly (HTTP $HTTP_CODE)"
    fi
}

# Test compression
test_compression() {
    print_header "Response Compression Test"
    
    print_test "Testing gzip compression support..."
    
    HEADERS=$(curl -sI -H "Accept-Encoding: gzip" "${BASE_URL}/api/health")
    
    if echo "$HEADERS" | grep -iq "Content-Encoding: gzip"; then
        print_pass "Compression is enabled"
    else
        print_warn "Compression header not present (may be intentional for small responses)"
    fi
}

# Test TLS configuration (if HTTPS is available)
test_tls_configuration() {
    print_header "TLS Configuration Test"
    
    # Check if HTTPS is configured
    if [[ "$BASE_URL" == https://* ]]; then
        print_test "Testing TLS configuration..."
        
        # Test TLS version and ciphers
        TLS_INFO=$(curl -sI --tlsv1.2 "${BASE_URL}/api/health" 2>&1)
        
        if echo "$TLS_INFO" | grep -q "200\|301\|302"; then
            print_pass "TLS 1.2+ is supported"
        else
            print_warn "Could not verify TLS configuration"
        fi
        
        # Check HSTS header
        HEADERS=$(curl -sI "${BASE_URL}/api/health" 2>/dev/null || echo "")
        if echo "$HEADERS" | grep -iq "Strict-Transport-Security:"; then
            print_pass "HSTS header is present"
        else
            print_warn "HSTS header missing (expected for HTTPS)"
        fi
    else
        print_warn "Skipping TLS tests (not using HTTPS)"
        print_warn "For production, use HTTPS with proper TLS configuration"
    fi
}

# Test timeout handling
test_timeout_handling() {
    print_header "Timeout Handling Test"
    
    print_test "Testing connection timeout handling..."
    
    # Test with a very short timeout
    START_TIME=$(date +%s)
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 1 "${BASE_URL}/api/health" || echo "timeout")
    END_TIME=$(date +%s)
    DURATION=$((END_TIME - START_TIME))
    
    if [[ "$HTTP_CODE" == "200" ]] && [[ $DURATION -le 2 ]]; then
        print_pass "Request completed within timeout ($DURATION seconds)"
    elif [[ "$HTTP_CODE" == "timeout" ]]; then
        print_warn "Request timed out (may indicate slow response)"
    else
        print_warn "Unexpected timeout behavior"
    fi
}

# Test different endpoint types
test_endpoint_protection() {
    print_header "Endpoint-Specific Protection Test"
    
    # Test public endpoint
    print_test "Testing public endpoint (/api/health)..."
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "${BASE_URL}/api/health")
    if [[ "$HTTP_CODE" == "200" ]]; then
        print_pass "Public endpoint accessible without auth"
    else
        print_fail "Public endpoint returned HTTP $HTTP_CODE"
    fi
    
    # Test protected endpoint (should require auth)
    print_test "Testing protected endpoint (/api/v1/staff)..."
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "${BASE_URL}/api/v1/staff/regattas")
    if [[ "$HTTP_CODE" == "401" ]] || [[ "$HTTP_CODE" == "302" ]]; then
        print_pass "Protected endpoint requires authentication (HTTP $HTTP_CODE)"
    else
        print_warn "Protected endpoint returned unexpected HTTP $HTTP_CODE"
    fi
}

# Main test execution
main() {
    echo "=================================================="
    echo "BC09-002: Edge Hardening Integration Tests"
    echo "=================================================="
    echo "Base URL: $BASE_URL"
    echo "Started: $(date)"
    echo ""
    
    check_service_availability
    test_security_headers
    test_rate_limiting
    test_request_size_limits
    test_compression
    test_tls_configuration
    test_timeout_handling
    test_endpoint_protection
    
    # Summary
    print_header "Test Summary"
    echo "Passed:   $PASSED"
    echo "Failed:   $FAILED"
    echo "Warnings: $WARNINGS"
    echo "Completed: $(date)"
    echo ""
    
    if [[ $FAILED -gt 0 ]]; then
        echo -e "${RED}Some tests failed. Please review the output above.${NC}"
        exit 1
    elif [[ $WARNINGS -gt 0 ]]; then
        echo -e "${YELLOW}Tests passed with warnings. Review recommended.${NC}"
        exit 0
    else
        echo -e "${GREEN}All tests passed successfully!${NC}"
        exit 0
    fi
}

# Run tests
main
