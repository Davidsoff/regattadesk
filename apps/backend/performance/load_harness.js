import exec from "k6/execution";
import http from "k6/http";
import { Counter, Trend } from "k6/metrics";

const profile = __ENV.PROFILE || "smoke";
const outputDir = __ENV.OUTPUT_DIR || "apps/backend/performance/reports";
const baseUrl = (__ENV.BASE_URL || "").replace(/\/$/, "");
const cpuUtilizationPct = Number(__ENV.CPU_UTILIZATION_PCT || "0");
const memoryUtilizationPct = Number(__ENV.MEMORY_UTILIZATION_PCT || "0");

if (!baseUrl) {
  throw new Error("BASE_URL is required");
}

const scenarioCatalog = JSON.parse(open("./load-scenarios.json"));
const thresholdConfig = JSON.parse(open("./performance-thresholds.json"));
const selectedScenarios = scenarioCatalog.scenarios.filter(
  (scenario) => profile === "deep" || scenario.trafficClass === "smoke",
);

if (selectedScenarios.length === 0) {
  throw new Error(`No scenarios selected for profile '${profile}'`);
}

const scenarioById = Object.fromEntries(selectedScenarios.map((scenario) => [scenario.id, scenario]));
const metricKeyByScenario = Object.fromEntries(
  selectedScenarios.map((scenario) => [scenario.id, scenario.id.replace(/[^a-zA-Z0-9_]/g, "_")]),
);

const latencyMetrics = {};
const requestMetrics = {};
const successMetrics = {};
const failureMetrics = {};

for (const scenario of selectedScenarios) {
  const metricKey = metricKeyByScenario[scenario.id];
  latencyMetrics[scenario.id] = new Trend(`scenario_latency_ms_${metricKey}`);
  requestMetrics[scenario.id] = new Counter(`scenario_requests_${metricKey}`);
  successMetrics[scenario.id] = new Counter(`scenario_success_${metricKey}`);
  failureMetrics[scenario.id] = new Counter(`scenario_failures_${metricKey}`);
}

const scenarioOptions = {};
for (const scenario of selectedScenarios) {
  scenarioOptions[scenario.id] = {
    executor: "per-vu-iterations",
    exec: "runScenario",
    vus: scenario.users,
    iterations: scenario.iterations,
    maxDuration: "15m",
    tags: {
      scenario_id: scenario.id,
    },
  };
}

export const options = {
  scenarios: scenarioOptions,
  thresholds: {
    http_req_duration: [`p(95)<=${thresholdConfig.maxP95LatencyMs}`],
    http_req_failed: [`rate<=${thresholdConfig.maxErrorRatePct / 100}`],
  },
};

export function runScenario() {
  const scenarioId = exec.scenario.tags.scenario_id || exec.scenario.name;
  const scenario = scenarioById[scenarioId];
  if (!scenario) {
    throw new Error(`Unknown scenario '${scenarioId}'`);
  }

  const url = `${baseUrl}${scenario.path}`;
  const params = { timeout: "5s" };
  let response;

  if (scenario.method === "POST") {
    response = http.post(url, JSON.stringify({ marker: "load-test" }), {
      ...params,
      headers: { "Content-Type": "application/json" },
    });
  } else {
    response = http.get(url, params);
  }

  const isSuccess = response.status >= 200 && response.status < 500;
  const durationMs = response.timings.duration;

  requestMetrics[scenarioId].add(1);
  latencyMetrics[scenarioId].add(durationMs);
  if (isSuccess) {
    successMetrics[scenarioId].add(1);
  } else {
    failureMetrics[scenarioId].add(1);
  }
}

function readMetricNumber(data, metricName, metricField) {
  const metric = data.metrics[metricName];
  if (!metric || !metric.values || metric.values[metricField] === undefined) {
    return 0;
  }
  return Number(metric.values[metricField]) || 0;
}

function buildMarkdown(summary, breaches, thresholds) {
  const lines = [
    "# Performance Gate Report",
    "",
    `Profile: \`${summary.profile}\``,
    `Status: \`${summary.status}\``,
    "",
    "## Aggregate",
    `- p95 latency ms: ${summary.aggregateP95LatencyMs}`,
    `- error rate pct: ${summary.aggregateErrorRatePct}`,
    `- cpu utilization pct: ${summary.cpuUtilizationPct}`,
    `- memory utilization pct: ${summary.memoryUtilizationPct}`,
    "",
    "## Thresholds",
    `- max p95 latency ms: ${thresholds.maxP95LatencyMs}`,
    `- max error rate pct: ${thresholds.maxErrorRatePct}`,
    `- max cpu utilization pct: ${thresholds.maxCpuUtilizationPct}`,
    `- max memory utilization pct: ${thresholds.maxMemoryUtilizationPct}`,
    "",
    "## Bottlenecks",
  ];

  for (const scenario of summary.scenarios) {
    lines.push(
      `- ${scenario.scenario}: p95=${scenario.p95LatencyMs}ms, errors=${scenario.errorRatePct}%`,
    );
  }

  lines.push("");
  lines.push("## Regression Risk");
  if (breaches.length > 0) {
    lines.push(`- Threshold breaches: ${breaches.join(", ")}`);
  } else {
    lines.push("- No threshold breach detected");
  }

  lines.push("");
  return `${lines.join("\n")}`;
}

export function handleSummary(data) {
  const scenarioResults = selectedScenarios.map((scenario) => {
    const metricKey = metricKeyByScenario[scenario.id];
    const requests = readMetricNumber(data, `scenario_requests_${metricKey}`, "count");
    const successes = readMetricNumber(data, `scenario_success_${metricKey}`, "count");
    const failures = readMetricNumber(data, `scenario_failures_${metricKey}`, "count");
    const p95 = readMetricNumber(data, `scenario_latency_ms_${metricKey}`, "p(95)");
    const errorRatePct = requests > 0 ? (failures / requests) * 100 : 0;
    return {
      scenario: scenario.id,
      requests: Math.round(requests),
      success: Math.round(successes),
      failures: Math.round(failures),
      errorRatePct: Number(errorRatePct.toFixed(3)),
      avgLatencyMs: Number(readMetricNumber(data, `scenario_latency_ms_${metricKey}`, "avg").toFixed(2)),
      p95LatencyMs: Number(p95.toFixed(2)),
    };
  });

  const scenarioP95LatencyMs = Object.fromEntries(
    scenarioResults.map((result) => [result.scenario, result.p95LatencyMs]),
  );

  const aggregateP95LatencyMs = Number(readMetricNumber(data, "http_req_duration", "p(95)").toFixed(2));
  const aggregateErrorRatePct = Number(
    (readMetricNumber(data, "http_req_failed", "rate") * 100).toFixed(3),
  );

  const breaches = [];
  if (aggregateP95LatencyMs > thresholdConfig.maxP95LatencyMs) {
    breaches.push("p95 latency");
  }
  if (aggregateErrorRatePct > thresholdConfig.maxErrorRatePct) {
    breaches.push("error rate");
  }
  if (cpuUtilizationPct > thresholdConfig.maxCpuUtilizationPct) {
    breaches.push("cpu utilization");
  }
  if (memoryUtilizationPct > thresholdConfig.maxMemoryUtilizationPct) {
    breaches.push("memory utilization");
  }

  const summary = {
    profile,
    status: breaches.length > 0 ? "FAIL" : "PASS",
    aggregateP95LatencyMs,
    aggregateErrorRatePct,
    cpuUtilizationPct,
    memoryUtilizationPct,
    scenarioP95LatencyMs,
    thresholdBreaches: breaches,
    scenarios: scenarioResults,
  };

  return {
    [`${outputDir}/${profile}-summary.json`]: `${JSON.stringify(summary, null, 2)}\n`,
    [`${outputDir}/${profile}-report.md`]: `${buildMarkdown(summary, breaches, thresholdConfig)}\n`,
  };
}
