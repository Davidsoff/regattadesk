# Test Quality Rubric

Use this rubric to score findings during adversarial review.

## 1) Correctness Signal
- Verify test fails for the intended defect.
- Verify test passes when defect is fixed.
- Reject tests that can pass with broken behavior.

## 2) Determinism
- Remove sleeps/time-race dependencies.
- Control clocks and random seeds.
- Replace flaky ordering assumptions with explicit ordering.

## 3) Isolation
- Avoid shared mutable global state between tests.
- Reset fixtures and mocks per test.
- Avoid hidden dependency on test execution order.

## 4) Assertion Strength
- Assert behavior, not only non-null/truthy values.
- Assert key fields and invariants.
- Add negative-path assertions for error handling.

## 5) Coverage of Risk
- Cover boundary values and malformed input.
- Cover state transitions and idempotency.
- Cover regression paths for previously reported bugs.

## 6) Maintainability
- Keep test setup small and local.
- Prefer explicit helper names over magic constants.
- Remove duplicated fixtures when they hide intent.
