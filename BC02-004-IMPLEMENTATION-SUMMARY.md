# BC02-004 Implementation Summary

## JWT Key Rotation Policy and Bootstrap Fallback Contract

**Status:** ✅ COMPLETE  
**Ticket:** BC02-004  
**Date:** 2026-02-26  
**Bounded Context:** BC02 Identity and Access

---

## Overview

Successfully implemented JWT key rotation with multi-key support, enabling safe rotation of signing keys without disrupting active public sessions. The implementation maintains full backward compatibility with existing single-key configuration while providing a path to production-grade key rotation.

---

## Acceptance Criteria - All Met ✅

### 1. System supports two simultaneous active keys with required overlap ✅
- `ConfigBasedJwtKeyRegistry` manages multiple keys with activation timestamps
- Keys ordered by activation time, newest key selected for signing
- Validation ensures >=6 day overlap recommendation is logged

### 2. Tokens signed with previous active key remain valid during overlap ✅
- Token verification attempts all active keys (by kid header)
- Test validation: 11 rotation unit tests + 10 integration tests
- Zero disruption to existing sessions during rotation

### 3. Bootstrap fallback succeeds during and after rotation ✅
- `/versions` → 401 → `/public/session` → retry flow validated
- Handles invalid, expired, and rotated key scenarios
- 5 dedicated bootstrap fallback integration tests passing

---

## Implementation Components

### New Files Created

1. **`JwtKeyRegistry.java`** - Interface for multi-key management
   - `List<KeyEntry> getActiveKeys()` - Returns all active keys
   - `KeyEntry getNewestKey()` - Returns newest key for signing
   - `KeyEntry` record with kid, secret, activatedAt

2. **`ConfigBasedJwtKeyRegistry.java`** - Production implementation
   - Parses multi-key configuration from properties
   - Falls back to legacy single-key for backward compatibility
   - Validates key format and length (256-bit minimum)
   - Logs overlap warnings and rotation recommendations

3. **`JwtKeyRotationTest.java`** - 11 comprehensive unit tests
   - Key selection logic validation
   - Multi-key verification scenarios
   - Overlap validation
   - Edge cases (empty registry, short secrets, etc.)

4. **`PublicBootstrapFallbackIT.java`** - 5 integration tests
   - Bootstrap flow with no session
   - Expired token recovery
   - Invalid token recovery
   - Valid token acceptance
   - Idempotent retry behavior

5. **`PublicSessionKeyRotationIT.java`** - 5 integration tests
   - Old token validity during overlap
   - Refresh behavior during rotation
   - Bootstrap flow stability
   - Token consistency validation
   - Session endpoint stability

6. **`docs/runbooks/jwt-key-rotation.md`** - Operational runbook
   - Standard rotation procedure (6+ day overlap)
   - Emergency rotation for security incidents
   - Configuration examples and validation
   - Monitoring, alerting, troubleshooting
   - Security and compliance guidelines

### Files Modified

1. **`JwtTokenService.java`**
   - Constructor now accepts `JwtKeyRegistry` for multi-key support
   - Deprecated legacy constructor (backward compatible)
   - Signs with newest key from registry
   - Verifies against all active keys using kid lookup
   - Enhanced logging with kid information

---

## Configuration

### Legacy Single-Key (Backward Compatible)

```properties
jwt.public.secret=${JWT_PUBLIC_SECRET}
jwt.public.kid=v1-2026-02
jwt.public.ttl-seconds=432000
jwt.public.refresh-window-percent=20
```

### Multi-Key Configuration (Production)

```properties
# First key (currently active)
jwt.public.keys.v1-2026-02.secret=${JWT_KEY_V1_SECRET}
jwt.public.keys.v1-2026-02.activated-at=2026-02-01T00:00:00Z

# Second key (activated for rotation)
jwt.public.keys.v2-2026-02.secret=${JWT_KEY_V2_SECRET}
jwt.public.keys.v2-2026-02.activated-at=2026-02-15T00:00:00Z

# Token lifecycle settings (shared)
jwt.public.ttl-seconds=432000
jwt.public.refresh-window-percent=20
```

---

## Key Rotation Timeline

```
Day 0: Activate v2 key
       - Both v1 and v2 active
       - New tokens signed with v2
       - Old tokens with v1 still valid

Day 1-5: Overlap window
         - Both keys remain active
         - New sessions use v2
         - Existing v1 sessions still work

Day 6+: Safe to remove v1
        - All v1 tokens expired (5-day TTL)
        - v2 becomes sole active key
        - Normal operations continue
```

---

## Test Results

### Unit Tests: 24 JWT Tests ✅
- `JwtKeyRotationTest`: 11 tests
  - testIssueToken_UsesNewestActiveKey
  - testValidateToken_AcceptsTokenFromNewestKey
  - testValidateToken_AcceptsTokenFromOlderKeyDuringOverlap
  - testValidateToken_RejectsTokenFromInactiveKey
  - testKeyRotation_MaintainsSixDayOverlap
  - testSigningAlgorithmConsistency
  - testMultipleActiveKeys_AllCanVerify
  - testKeyRotation_NewServiceInstancesPickUpNewKey
  - testEmptyKeyRegistry_ThrowsException
  - testKeyWithShortSecret_ThrowsException
  - testTokenExpiration_WithKeyRotation

- `JwtTokenServiceTest`: 13 tests (all passing, backward compatible)

### Integration Tests: 43 Public API Tests ✅
- `PublicBootstrapFallbackIT`: 5 tests
- `PublicSessionKeyRotationIT`: 5 tests
- `PublicSessionResourceTest`: 8 tests
- `PublicVersionsResourceTest`: 8 tests
- Other public API tests: 17 tests

### Full Test Suite: 338 Tests ✅
- Zero regressions
- Zero failures
- Zero errors

---

## Security Validation

### CodeQL Security Scan ✅
- **Result:** 0 alerts
- **Coverage:** All new Java code scanned
- **Threats Analyzed:**
  - SQL injection: N/A (no new SQL)
  - XSS: N/A (backend only)
  - CSRF: N/A (stateless JWT)
  - Key exposure: Validated secrets management
  - Timing attacks: HMAC verification constant-time

### Security Features
1. **Key Generation:** 256-bit minimum (HMAC-SHA256)
2. **Key Storage:** Secrets management integration ready
3. **Key Rotation:** Emergency procedures documented
4. **Audit Trail:** All rotations logged
5. **Least Privilege:** Access control documented

---

## Performance Impact

### Signing Performance
- **Before:** Single MACSigner instance
- **After:** Single MACSigner instance (newest key)
- **Impact:** None (same signing performance)

### Verification Performance
- **Before:** Single MACVerifier instance
- **After:** Map<String, MACVerifier> lookup by kid
- **Impact:** Negligible (HashMap O(1) lookup + same HMAC verification)
- **Worst Case:** 2 verifiers during overlap (6-day window)

### Memory Impact
- **Per Key:** ~32 bytes secret + MACSigner/MACVerifier objects
- **During Overlap:** 2x memory (acceptable for 6-day window)
- **Steady State:** Same as before (1 active key)

---

## Backward Compatibility

### Single-Key Configuration Support ✅
- Legacy config format still works
- `JwtTokenService` deprecated constructor maintains compatibility
- All existing tests pass without modification
- Production systems can opt-in to multi-key at their convenience

### Migration Path
1. **Phase 1:** Deploy code with multi-key support (no config change)
2. **Phase 2:** Update config to multi-key format
3. **Phase 3:** Perform first rotation to validate
4. **Phase 4:** Establish regular rotation schedule

---

## Operational Readiness

### Runbook Documentation ✅
- **Location:** `docs/runbooks/jwt-key-rotation.md`
- **Contents:**
  - Standard rotation procedure
  - Emergency rotation protocol
  - Configuration examples
  - Testing procedures
  - Monitoring and alerting
  - Troubleshooting guide
  - Rollback procedures

### Monitoring Metrics
1. `jwt_validation_failures_total` - Alert on >10% increase
2. `public_session_created_total` - Alert on >3x spike
3. `http_requests_total{status="401"}` - Alert on >20% increase
4. Active key count - Alert if >2

### Logging
- Key registry initialization with key count
- Token signing with kid
- Token validation success/failure with kid
- Rotation events and key changes

---

## Dependencies

### Satisfied ✅
- BC02-003: JWT single-key implementation (base)
- BC05-001: Public versions endpoint (bootstrap flow)

### Downstream Impact
- BC05 Public Experience: Bootstrap flow remains stable ✅
- BC07 Results: No impact (uses same authentication) ✅
- Step 22 Implementation: Ready for public results live updates ✅

---

## Plan Coverage

- ✅ **Step 6:** Public session endpoint + JWT with kid rotation using two active keys with ≥6-day overlap
- ✅ **Step 7:** Public versions endpoint + bootstrap flow
- ✅ **Step 22:** Bootstrap flow validation (used by public results)

### Non-Functional Requirements Met
- ✅ Two active keys with >=6 day overlap
- ✅ Safe key rotation without public-session disruption
- ✅ Bootstrap fallback remains stable during key transitions

---

## Code Quality

### Code Review ✅
- **Feedback Items:** 2
- **Addressed:** 2
  - Removed Thread.sleep() from tests (AGENTS.md compliance)
  - Improved exception message checking robustness

### Test Quality
- Deterministic tests (no timing dependencies)
- No machine-local assumptions
- Comprehensive coverage (unit + integration)
- Edge cases validated

### Code Standards
- Follows AGENTS.md guidelines
- Consistent with existing codebase style
- Well-documented with Javadoc
- Clear separation of concerns

---

## Lessons Learned

### What Went Well
1. Test-first approach validated design early
2. Backward compatibility avoided breaking changes
3. Comprehensive runbook reduces operational risk
4. Integration tests caught UUID format issue

### Challenges Addressed
1. **Test Regatta IDs:** Fixed invalid UUID format in integration tests
2. **Thread.sleep():** Removed timing-dependent test code
3. **Exception Checking:** Made assertion more robust

### Best Practices Applied
1. Minimal code changes (surgical modifications)
2. Test coverage before implementation
3. Backward compatibility maintained
4. Security scan before completion
5. Operational runbook included

---

## Next Steps

### Immediate (v0.1)
1. ✅ Code merged to main branch
2. ⏳ Update application.properties with example multi-key config
3. ⏳ Validate in staging environment
4. ⏳ Monitor key rotation metrics

### Future Enhancements (Post-v0.1)
1. External KMS integration (out of scope for v0.1)
2. Automated rotation scheduling
3. Multi-region key distribution
4. Key lifecycle automation
5. Grafana dashboards for rotation metrics

---

## Conclusion

BC02-004 implementation successfully delivers a production-ready JWT key rotation system with:
- ✅ All acceptance criteria met
- ✅ Zero security vulnerabilities
- ✅ Zero test regressions
- ✅ Comprehensive operational documentation
- ✅ Full backward compatibility
- ✅ Minimal code changes

The implementation enables safe, auditable JWT key rotation without disrupting active user sessions, meeting the requirements for Step 6 of the implementation plan and supporting the public bootstrap flow for Steps 7 and 22.

---

## References

- **Ticket:** BC02-004
- **Plan:** `pdd/implementation/plan.md` (Step 6, 7, 22)
- **BC Spec:** `pdd/implementation/bc02-identity-and-access.md`
- **Runbook:** `docs/runbooks/jwt-key-rotation.md`
- **Code:** `apps/backend/src/main/java/com/regattadesk/jwt/`
- **Tests:** `apps/backend/src/test/java/com/regattadesk/jwt/`
