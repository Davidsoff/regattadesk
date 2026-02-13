# Testing Strategy

Version: v0.1  
Last Updated: 2026-02-13  
Bounded Context: BC09 - Operability, Hardening, and Quality

## Overview

This document defines the comprehensive testing strategy and CI quality gates for RegattaDesk v0.1, implementing the requirements from `pdd/implementation/plan.md` Step 25.

## Test Matrix by Change Type

Every change must include appropriate tests based on the type of modification. The CI pipeline enforces these requirements before allowing merges.

### 1. Domain/Command Logic Changes

**Required Tests:** Unit Tests

**When to Apply:**
- Business rule implementations
- Command handlers
- Domain model validations
- Event sourcing logic
- State transitions

**Framework:** JUnit 5 (backend)

**Example:**
```java
@Test
void shouldValidateCrewComposition() {
    // Test domain validation logic
}
```

### 2. Persistence/Query Changes

**Required Tests:** PostgreSQL Integration Tests

**When to Apply:**
- Database schema migrations
- Repository implementations
- Query logic
- Event store operations
- Projection updates

**Framework:** Testcontainers + PostgreSQL container

**Example:**
```java
@QuarkusTest
@TestProfile(PostgresTestProfile.class)
class EventStoreIntegrationTest {
    // Test with real PostgreSQL instance
}
```

### 3. API Contract Changes

**Required Tests:** Contract Tests (Pact) + Endpoint Integration Checks

**When to Apply:**
- REST API endpoint additions/modifications
- Request/response format changes
- Public API changes
- Staff API changes
- Operator API changes

**Framework:** Pact (Consumer-driven contracts)

**Example:**
```java
@Provider("regattadesk-backend")
@PactFolder("pacts")
class RegattaDeskPactProviderTest {
    // Verify provider honors consumer contracts
}
```

### 4. UI/UX Changes

**Required Tests:**
- Component/page tests
- Accessibility checks (WCAG 2.2 AA minimum)
- Visual regression tests (where applicable)

**When to Apply:**
- Vue component modifications
- Layout changes
- Interactive behavior changes
- Form implementations
- Public pages
- Staff interface changes
- Operator PWA interface changes

**Framework:** 
- Vitest (unit/component tests)
- axe-core (accessibility)
- Playwright (E2E, future)

**Accessibility Requirements:**
- **Public flows:** WCAG 2.2 AA minimum (aim for AAA)
- **Staff/Operator critical flows:** Mandatory accessibility checks including:
  - Keyboard navigation
  - Screen reader compatibility
  - Color contrast ratios
  - Touch target sizes

## Test Categories

### Unit Tests

**Purpose:** Fast, isolated tests for business logic

**Scope:**
- Domain model validation
- Command handlers
- Business rules
- Pure functions
- Utility classes

**Characteristics:**
- No external dependencies
- In-memory only
- Fast execution (< 1s per test)
- High coverage of edge cases

**Location:**
- Backend: `apps/backend/src/test/java/**/*Test.java`
- Frontend: `apps/frontend/src/**/*.test.ts`

### Integration Tests

**Purpose:** Test interactions with real infrastructure

**Scope:**
- Database operations
- Event store functionality
- API endpoint behavior
- Service integration

**Characteristics:**
- Use Testcontainers for PostgreSQL
- Real database migrations
- Realistic data scenarios
- Moderate execution time (< 10s per test)

**Location:**
- Backend: `apps/backend/src/test/java/**/*IntegrationTest.java`

### Contract Tests (Pact)

**Purpose:** Verify API contracts between consumers and providers

**Scope:**
- Public API contracts
- Staff API contracts
- Operator API contracts
- SSE event contracts

**Characteristics:**
- Consumer-driven contracts
- Provider verification
- Version compatibility checks
- Contract evolution tracking

**Location:**
- Backend: `apps/backend/src/test/java/**/*PactTest.java`
- Contracts: `apps/backend/src/test/resources/pacts/`

### Accessibility Tests

**Purpose:** Ensure WCAG 2.2 AA compliance (minimum)

**Scope:**
- Public pages (AA minimum, AAA target)
- Staff interfaces (AA minimum for critical flows)
- Operator PWA (AA minimum with high-contrast mode)

**Characteristics:**
- Automated axe-core scans
- Manual keyboard navigation checks
- Screen reader smoke tests
- Color contrast validation
- Touch target size verification

**Location:**
- Frontend: `apps/frontend/src/**/*.a11y.test.ts`

### UI Component Tests

**Purpose:** Test Vue component behavior and interactions

**Scope:**
- Component rendering
- Event handling
- State management
- User interactions
- Form validation

**Location:**
- Frontend: `apps/frontend/src/**/*.spec.ts`

## CI Quality Gates

### Required Checks

All PRs must pass these checks before merge:

1. **Backend Lint** - Code formatting and style
2. **Backend Build** - Compilation without errors
3. **Backend Unit Tests** - All unit tests pass
4. **Frontend Lint** - Code formatting and style
5. **Frontend Build** - Build succeeds without errors
6. **Frontend Unit Tests** - Component and utility tests pass (when applicable)
7. **Dependency Pinning** - All dependencies properly pinned

### Conditional Checks

These checks run based on changed files:

- **Integration Tests:** Run when database schema or repository code changes
- **Contract Tests:** Run when API endpoints or contracts change
- **Accessibility Tests:** Run when UI components or pages change
- **E2E Tests:** Run for full-stack feature changes (future)

### Coverage Requirements

- **Unit Test Coverage:** Minimum 80% for new code
- **Critical Path Coverage:** 100% for command handlers and domain rules
- **API Coverage:** All public/staff/operator endpoints must have tests

### Merge Blocking

The following conditions block merges:

- Any required check fails
- Security vulnerabilities in changed code
- Missing tests for change type
- Accessibility violations in affected flows
- Contract breaking changes without version bump

## Test Execution

### Local Development

```bash
# Run all tests
make test

# Backend tests only
cd apps/backend && ./mvnw test

# Backend integration tests
cd apps/backend && ./mvnw verify -Pintegration

# Frontend tests (when configured)
cd apps/frontend && npm test

# Accessibility tests (when configured)
cd apps/frontend && npm run test:a11y
```

### CI Pipeline

Tests run automatically on:
- Pull request creation
- Pull request updates
- Push to main/master branch
- Manual workflow dispatch

### Test Profiles

**Backend Profiles:**
- `default` - Unit tests only (fast)
- `integration` - Includes PostgreSQL integration tests
- `contract` - Includes Pact contract tests
- `all` - All test categories

**Frontend Profiles:**
- `unit` - Component and utility tests
- `a11y` - Accessibility tests
- `e2e` - End-to-end tests (future)

## Test Data Management

### Fixtures

- **Location:** `apps/backend/src/test/resources/fixtures/`
- **Format:** JSON for domain entities, SQL for database scenarios
- **Versioning:** Keep in sync with domain model

### Test Databases

- **Integration Tests:** Ephemeral Testcontainers PostgreSQL
- **Contract Tests:** In-memory H2 (where sufficient) or Testcontainers
- **CI:** Testcontainers with Docker-in-Docker support

## Accessibility Testing Details

### Automated Checks (axe-core)

Automated accessibility scans verify:
- Proper heading hierarchy
- Alt text on images
- Form labels
- Color contrast ratios (WCAG AA: 4.5:1 for normal text, 3:1 for large text)
- Keyboard focusable elements
- ARIA attributes validity

### Manual Checks (Required for Critical Flows)

Manual verification includes:
1. **Keyboard Navigation**
   - Tab through all interactive elements
   - Activate with Enter/Space
   - Escape to close modals/dialogs
   - Arrow keys for custom widgets

2. **Screen Reader Testing**
   - Announce page structure correctly
   - Convey state changes
   - Provide context for actions
   - Read form errors clearly

3. **High Contrast Mode** (Operator PWA)
   - Content visible in high contrast
   - Focus indicators prominent
   - Touch targets clearly defined

4. **Mobile/Touch Testing**
   - Touch targets ≥44×44 CSS pixels
   - No hover-only interactions
   - Pinch-zoom not disabled

### Accessibility Checklist Template

For each affected critical flow:

- [ ] Automated axe-core scan passes
- [ ] Keyboard navigation verified
- [ ] Screen reader announces content correctly
- [ ] Color contrast meets WCAG AA (4.5:1 normal, 3:1 large)
- [ ] Touch targets meet size requirements (44×44px)
- [ ] Focus indicators visible
- [ ] Error messages clear and accessible
- [ ] Dynamic content changes announced

## Performance Testing

### Load Testing (BC09-003)

While not part of the core test matrix, performance validation includes:
- Representative load profiles
- Latency thresholds
- Resource utilization limits
- SSE connection capacity

See BC09-003 for detailed performance testing requirements.

## Test Ownership

| Test Category | Bounded Context Owner | CI Job |
|--------------|----------------------|---------|
| Backend Unit | All backend BCs | `backend-test` |
| Backend Integration | All backend BCs | `backend-integration` |
| Contract Tests | BC05 (Public), BC02 (Staff), BC06 (Operator) | `contract-test` |
| Frontend Unit | All frontend BCs | `frontend-test` |
| Accessibility | BC05, BC06 | `accessibility-test` |
| E2E Tests | BC09 (cross-cutting) | `e2e-test` (future) |

## Enforcement

### Branch Protection Rules

Required status checks for `main`/`master`:
- All checks in "Required Checks" section above
- At least one approval from code owner
- Up-to-date with base branch

### Pre-commit Hooks (Recommended)

Developers should run locally before push:
```bash
# Backend formatting
cd apps/backend && ./mvnw spotless:check

# Frontend linting
cd apps/frontend && npm run lint

# Run affected tests
make test
```

## Continuous Improvement

### Test Metrics Tracking

Monitor and improve:
- Test execution time trends
- Flaky test rates
- Coverage trends
- Bug escape rate

### Quarterly Reviews

Review and update:
- Coverage requirements
- Test execution strategy
- Tool versions (axe-core, Pact, etc.)
- Accessibility standards compliance

## References

- [Implementation Plan](../pdd/implementation/plan.md) - Step 25, Step quality gates
- [BC09 Spec](../pdd/implementation/bc09-operability-hardening-and-quality.md)
- [WCAG 2.2 Guidelines](https://www.w3.org/WAI/WCAG22/quickref/)
- [Pact Documentation](https://docs.pact.io/)
- [Testcontainers](https://www.testcontainers.org/)
- [axe-core](https://github.com/dequelabs/axe-core)

## Version History

- v0.1 (2026-02-13) - Initial testing strategy for BC09-004
