# Load Testing Suite (BC09-003)

This directory contains version-controlled load profiles, objective gate thresholds, and a repeatable execution harness.

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
python3 apps/backend/performance/load_harness.py \
  --base-url http://localhost:8080 \
  --profile smoke \
  --output-dir apps/backend/performance/reports

# deep profile (scheduled)
python3 apps/backend/performance/load_harness.py \
  --base-url http://localhost:8080 \
  --profile deep \
  --output-dir apps/backend/performance/reports \
  --cpu-utilization-pct 70 \
  --memory-utilization-pct 68
```

## Reports
Each run writes:
- `<profile>-summary.json`
- `<profile>-report.md`

Reports enumerate bottleneck scenarios and whether regression-risk thresholds were breached.
