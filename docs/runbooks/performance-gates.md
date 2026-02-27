# Performance Gates Runbook

## Purpose
Define release-readiness performance checks for BC09-003 and provide a repeatable execution path.

## Profiles
- Smoke (CI-adjacent): quick PR signal using the smoke subset of scenarios.
- Deep (scheduled): full profile run with all scenarios and archived reports.

## Execution
1. Ensure a target environment is reachable.
2. Run the harness:
   ```bash
   python3 apps/backend/performance/load_harness.py --base-url <TARGET> --profile smoke
   ```
3. Review `apps/backend/performance/reports/smoke-summary.json` and `smoke-report.md`.
4. For deeper validation:
   ```bash
   python3 apps/backend/performance/load_harness.py --base-url <TARGET> --profile deep
   ```

## Gate Decision
Fail release readiness if any threshold breach is present in `thresholdBreaches`.

## Tuning Recommendations Template
Capture after each deep run:
- Bottleneck endpoint/scenario
- Suspected bottleneck layer (DB, JVM, reverse proxy, client)
- Mitigation candidate
- Follow-up ticket
