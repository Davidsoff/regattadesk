#!/usr/bin/env python3
import argparse
import json
import math
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path


def percentile(values, p):
    if not values:
        return 0.0
    ordered = sorted(values)
    k = (len(ordered) - 1) * p
    f = math.floor(k)
    c = math.ceil(k)
    if f == c:
        return float(ordered[int(k)])
    return float(ordered[f] * (c - k) + ordered[c] * (k - f))


def request_once(method, url, timeout):
    req = urllib.request.Request(url=url, method=method)
    if method == "POST":
        req.add_header("Content-Type", "application/json")
        payload = b'{"marker":"load-test"}'
    else:
        payload = None

    start = time.perf_counter()
    try:
        with urllib.request.urlopen(req, data=payload, timeout=timeout) as resp:
            status = resp.getcode()
            if method == "GET" and "events" in url:
                resp.read(256)
            else:
                resp.read(16)
            latency_ms = (time.perf_counter() - start) * 1000
            return status < 500, latency_ms, status
    except urllib.error.HTTPError as err:
        latency_ms = (time.perf_counter() - start) * 1000
        return err.code < 500, latency_ms, err.code
    except Exception:
        latency_ms = (time.perf_counter() - start) * 1000
        return False, latency_ms, 0


def run_scenario(base_url, scenario):
    method = scenario["method"]
    url = base_url.rstrip("/") + scenario["path"]
    total = scenario["users"] * scenario["iterations"]
    timeout_seconds = 5

    latencies = []
    success = 0
    failures = 0

    with ThreadPoolExecutor(max_workers=max(1, scenario["users"])) as pool:
        futures = [pool.submit(request_once, method, url, timeout_seconds) for _ in range(total)]
        for future in as_completed(futures):
            ok, latency_ms, _status = future.result()
            latencies.append(latency_ms)
            if ok:
                success += 1
            else:
                failures += 1

    p95 = percentile(latencies, 0.95)
    avg = sum(latencies) / len(latencies) if latencies else 0.0
    error_rate = (failures / total) * 100 if total else 0.0
    return {
        "scenario": scenario["id"],
        "requests": total,
        "success": success,
        "failures": failures,
        "errorRatePct": round(error_rate, 3),
        "avgLatencyMs": round(avg, 2),
        "p95LatencyMs": round(p95, 2),
    }


def summarize(results):
    all_latencies = []
    total_requests = 0
    total_failures = 0
    by_scenario = {}
    for result in results:
        total_requests += result["requests"]
        total_failures += result["failures"]
        by_scenario[result["scenario"]] = result["p95LatencyMs"]
        all_latencies.append(result["p95LatencyMs"])

    aggregate_p95 = max(all_latencies) if all_latencies else 0.0
    aggregate_error_rate = (total_failures / total_requests) * 100 if total_requests else 0.0
    return aggregate_p95, aggregate_error_rate, by_scenario


def evaluate_gate(thresholds, aggregate_p95, aggregate_error_rate, cpu, memory):
    breaches = []
    if aggregate_p95 > thresholds["maxP95LatencyMs"]:
        breaches.append("p95 latency")
    if aggregate_error_rate > thresholds["maxErrorRatePct"]:
        breaches.append("error rate")
    if cpu > thresholds["maxCpuUtilizationPct"]:
        breaches.append("cpu utilization")
    if memory > thresholds["maxMemoryUtilizationPct"]:
        breaches.append("memory utilization")
    return breaches


def write_markdown(path, profile, summary, thresholds, breaches, scenarios):
    lines = [
        "# Performance Gate Report",
        "",
        f"Profile: `{profile}`",
        f"Status: `{'FAIL' if breaches else 'PASS'}`",
        "",
        "## Aggregate",
        f"- p95 latency ms: {summary['aggregateP95LatencyMs']}",
        f"- error rate pct: {summary['aggregateErrorRatePct']}",
        f"- cpu utilization pct: {summary['cpuUtilizationPct']}",
        f"- memory utilization pct: {summary['memoryUtilizationPct']}",
        "",
        "## Thresholds",
        f"- max p95 latency ms: {thresholds['maxP95LatencyMs']}",
        f"- max error rate pct: {thresholds['maxErrorRatePct']}",
        f"- max cpu utilization pct: {thresholds['maxCpuUtilizationPct']}",
        f"- max memory utilization pct: {thresholds['maxMemoryUtilizationPct']}",
        "",
        "## Bottlenecks",
    ]
    for scenario in scenarios:
        lines.append(f"- {scenario['scenario']}: p95={scenario['p95LatencyMs']}ms, errors={scenario['errorRatePct']}%")

    lines.append("")
    lines.append("## Regression Risk")
    if breaches:
        lines.append(f"- Threshold breaches: {', '.join(breaches)}")
    else:
        lines.append("- No threshold breach detected")

    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description="RegattaDesk load testing harness")
    parser.add_argument("--base-url", required=True)
    parser.add_argument("--profile", choices=["smoke", "deep"], default="smoke")
    parser.add_argument("--output-dir", default="apps/backend/performance/reports")
    parser.add_argument("--cpu-utilization-pct", type=float, default=0.0)
    parser.add_argument("--memory-utilization-pct", type=float, default=0.0)
    args = parser.parse_args()

    base_dir = Path(__file__).resolve().parent
    scenarios = json.loads((base_dir / "load-scenarios.json").read_text(encoding="utf-8"))["scenarios"]
    thresholds = json.loads((base_dir / "performance-thresholds.json").read_text(encoding="utf-8"))

    selected = [s for s in scenarios if args.profile == "deep" or s["trafficClass"] == "smoke"]

    scenario_results = [run_scenario(args.base_url, scenario) for scenario in selected]
    aggregate_p95, aggregate_error_rate, scenario_p95 = summarize(scenario_results)
    breaches = evaluate_gate(
        thresholds,
        aggregate_p95,
        aggregate_error_rate,
        args.cpu_utilization_pct,
        args.memory_utilization_pct,
    )

    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    summary = {
        "profile": args.profile,
        "status": "FAIL" if breaches else "PASS",
        "aggregateP95LatencyMs": round(aggregate_p95, 2),
        "aggregateErrorRatePct": round(aggregate_error_rate, 3),
        "cpuUtilizationPct": args.cpu_utilization_pct,
        "memoryUtilizationPct": args.memory_utilization_pct,
        "scenarioP95LatencyMs": scenario_p95,
        "thresholdBreaches": breaches,
        "scenarios": scenario_results,
    }

    json_path = output_dir / f"{args.profile}-summary.json"
    json_path.write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")

    md_path = output_dir / f"{args.profile}-report.md"
    write_markdown(md_path, args.profile, summary, thresholds, breaches, scenario_results)

    print(f"Wrote {json_path}")
    print(f"Wrote {md_path}")

    if breaches:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
