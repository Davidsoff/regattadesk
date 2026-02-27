# Load Testing Suite (BC09-003)

This directory contains version-controlled load profiles, objective gate thresholds, and a repeatable k6 execution harness.

## Scenarios
- `public-read`: public schedule/results read traffic.
- `public-sse`: long-lived SSE client pressure.
- `operator-ingest-burst`: operator ingest burst behavior.
- `staff-api`: staff API usage under concurrent reads.

Scenario definitions are stored in `load-scenarios.json`.

## Thresholds
Release-readiness thresholds are stored in `performance-thresholds.json`.

Gate failure conditions:
- aggregate p95 latency exceeds `maxP95LatencyMs`
- aggregate error rate exceeds `maxErrorRatePct`
- CPU exceeds `maxCpuUtilizationPct`
- memory exceeds `maxMemoryUtilizationPct`

## Run
```bash
# smoke profile (CI-adjacent)
PROFILE=smoke BASE_URL=http://localhost:8080 OUTPUT_DIR=apps/backend/performance/reports \
  k6 run apps/backend/performance/load_harness.js

# deep profile (scheduled)
PROFILE=deep BASE_URL=http://localhost:8080 OUTPUT_DIR=apps/backend/performance/reports \
  CPU_UTILIZATION_PCT=70 MEMORY_UTILIZATION_PCT=68 \
  k6 run apps/backend/performance/load_harness.js
```

## Reports
Each run writes:
- `<profile>-summary.json`
- `<profile>-report.md`

Reports enumerate bottleneck scenarios and whether regression-risk thresholds were breached.
