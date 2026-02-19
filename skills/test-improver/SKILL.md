---
name: test-improver
description: Improve test suites through adversarial review plus patching loops. Use when asked to improve tests, harden flaky or weak tests, increase deterministic PR-gating quality, run repeated review-and-fix iterations (for example 3 or 10 loops), or create failing tests before implementation patches.
---

# Test Improver

## Overview
Run repeatable adversarial test-quality reviews, convert findings into failing tests first, patch production code and tests, and re-run deterministic suites until findings are closed.

Load `references/test-quality-rubric.md` before writing findings.

## Workflow
1. Establish baseline:
   - Run the relevant deterministic test command(s).
   - Capture failures and current risk areas.
2. Perform adversarial review:
   - Rank findings by severity: correctness, determinism, isolation, assertion quality, and coverage gaps.
   - Reject style-only feedback unless it changes test signal quality.
3. Convert findings to failing tests first:
   - Add or tighten tests so each accepted finding fails before code patches.
   - Prefer small, explicit fixtures and deterministic data.
4. Patch to make tests pass:
   - Apply minimal production/test changes required by the failing tests.
   - Keep behavior changes explicit.
5. Re-run deterministic suites:
   - Re-run targeted tests, then broader required gates.
   - Report remaining failures with file paths and root cause.

## Iteration Loop
When asked to run multiple loops (for example `3` or `10`):
1. Repeat the workflow for the requested loop count.
2. In each loop, only carry forward unresolved findings.
3. Stop early only if no actionable findings remain.
4. After the final loop, provide:
   - Closed findings
   - Remaining risks
   - Commands run
   - Files changed

## Determinism Rules
- Treat PR-gating tests as deterministic only.
- Avoid timing-sensitive assertions unless time is controlled.
- Avoid performance benchmarks as hard pass/fail gates in CI.
- Remove randomness or seed it explicitly.
- Avoid network and external-service dependency in unit/PR-gating tests.

## Execution Rules
- Prefer `rg`/`rg --files` for search.
- Keep tests readable and minimal.
- Add regression tests for every fixed bug.
- Do not disable tests to get green CI.

## Deliverables
Return:
1. Findings fixed in this pass (with file references).
2. Newly added/updated failing tests.
3. Patches applied to make tests pass.
4. Test commands and outcomes.
5. Residual risks or next steps.
