# Observability Setup for RegattaDesk

This document describes the observability infrastructure implemented for RegattaDesk v0.1, including health monitoring, metrics collection, distributed tracing, and operational dashboards.

## Overview

RegattaDesk implements comprehensive observability through:
- **Health Endpoints**: Kubernetes-style liveness, readiness, and startup probes
- **Metrics**: Prometheus-compatible metrics for performance monitoring
- **Distributed Tracing**: OpenTelemetry-based tracing via Jaeger
- **Dashboards**: Grafana dashboards for operational visibility

## Architecture

```
┌─────────────────┐
│  RegattaDesk    │
│    Backend      │
│                 │
│  - Health Probes│──────┐
│  - Metrics      │──┐   │
│  - Traces       │  │   │
└─────────────────┘  │   │
                     │   │
         ┌───────────┘   └──────────┐
         │                          │
         ▼                          ▼
  ┌─────────────┐          ┌──────────────┐
  │ Prometheus  │          │    Jaeger    │
  │   (Metrics) │          │   (Traces)   │
  └──────┬──────┘          └──────────────┘
         │
         │
         ▼
  ┌─────────────┐
  │   Grafana   │
  │ (Dashboards)│
  └─────────────┘
```

## Health Endpoints

### Available Endpoints

1. **Application Health** (`/api/health`)
   - Custom health endpoint
   - Returns: `{"status": "UP", "version": "0.1.0-SNAPSHOT"}`

2. **Kubernetes Readiness** (`/q/health/ready`)
   - Indicates if the service is ready to accept traffic
   - Used by load balancers and orchestrators

3. **Kubernetes Liveness** (`/q/health/live`)
   - Indicates if the service is alive
   - Used to detect and restart unhealthy containers

4. **Kubernetes Startup** (`/q/health/started`)
   - Indicates if the application has completed startup
   - Used during initial deployment

### Health Check Implementation

Custom health checks are implemented using MicroProfile Health:

```java
@Readiness
@ApplicationScoped
public class ReadinessCheck implements HealthCheck {
    @Override
    public HealthCheckResponse call() {
        return HealthCheckResponse.up("regattadesk-backend-ready");
    }
}
```

Future enhancements will add database connectivity and external dependency checks.

## Metrics

### Prometheus Metrics

Metrics are exposed at `/q/metrics` in Prometheus format.

#### Available Metrics Categories

1. **HTTP Request Metrics**
   - `http_server_requests_seconds_count`: Total request count
   - `http_server_requests_seconds_sum`: Total request duration
   - `http_server_requests_seconds_bucket`: Request duration histogram
   - Labels: `method`, `uri`, `status`

2. **JVM Metrics**
   - `jvm_memory_used_bytes`: JVM memory usage by area (heap/non-heap)
   - `jvm_memory_max_bytes`: Maximum JVM memory
   - `jvm_gc_*`: Garbage collection metrics
   - `jvm_threads_*`: Thread pool metrics

3. **System Metrics**
   - `system_cpu_usage`: System CPU usage
   - `process_cpu_usage`: Process CPU usage
   - `system_load_average_1m`: System load average

### Scraping Configuration

Prometheus scrapes metrics every 15 seconds from the backend service. Configuration is in `infra/compose/prometheus/prometheus.yml`.

## Distributed Tracing

### OpenTelemetry Integration

RegattaDesk uses OpenTelemetry for distributed tracing:

- **Protocol**: OTLP (OpenTelemetry Protocol) over gRPC
- **Backend**: Jaeger
- **Sampling**: Always-on for v0.1 (100% of traces)

### Instrumented Components

1. **REST API Endpoints**: All HTTP requests/responses
2. **Reactive Streams**: RESTEasy Reactive instrumentation
3. **Future**: Database queries, external HTTP calls, messaging

### Trace Attributes

Standard attributes included in traces:
- `service.name`: `regattadesk-backend`
- `service.version`: `0.1.0-SNAPSHOT`
- `http.method`, `http.url`, `http.status_code`
- `http.route`: Request path template

## Dashboards

### Grafana Setup

Grafana is accessible at `http://localhost/grafana` (or your configured domain).

**Default Credentials:**
- Username: `admin`
- Password: `admin` (change on first login)

### Available Dashboards

#### 1. RegattaDesk Backend API Health

**Panels:**
- Service Status: Real-time UP/DOWN indicator
- Request Rate by Endpoint: Requests per second by URI and method
- Total Request Rate: Aggregate requests per second
- Request Latency (p50, p95, p99): Response time percentiles
- Response Status Distribution: 2xx/4xx/5xx breakdown
- JVM Memory Usage: Heap and non-heap memory trends
- CPU Usage: System and process CPU utilization

**Refresh Rate:** 10 seconds

### Creating Custom Dashboards

1. Access Grafana at `/grafana`
2. Navigate to Dashboards → New Dashboard
3. Add panels using Prometheus datasource
4. Use PromQL to query metrics

Example PromQL queries:
```promql
# Request rate
rate(http_server_requests_seconds_count[5m])

# 95th percentile latency
histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m]))

# Error rate
sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) / sum(rate(http_server_requests_seconds_count[5m]))
```

## Alert Rules

Alert rules are defined in `infra/compose/prometheus/alerts.yml`.

### Critical Alerts

1. **BackendDown**
   - Trigger: Service unavailable for >1 minute
   - Severity: Critical

2. **HealthCheckFailing**
   - Trigger: Health endpoint unreachable for >2 minutes
   - Severity: Critical

### Warning Alerts

1. **HighErrorRate**
   - Trigger: Error rate >5% for 5 minutes
   - Severity: Warning

2. **HighLatency**
   - Trigger: p95 latency >2 seconds for 5 minutes
   - Severity: Warning

3. **HighMemoryUsage**
   - Trigger: Heap usage >90% for 5 minutes
   - Severity: Warning

4. **HighCPUUsage**
   - Trigger: CPU usage >80% for 5 minutes
   - Severity: Warning

### Alert Thresholds

These are initial conservative thresholds. Adjust based on observed baseline:

| Metric | Threshold | Rationale |
|--------|-----------|-----------|
| Error rate | 5% | Tolerate occasional errors |
| p95 latency | 2s | Acceptable for v0.1; optimize in future |
| Memory usage | 90% | Leave headroom before OOM |
| CPU usage | 80% | Indicate saturation before degradation |

## Deployment

### Starting with Observability

To start RegattaDesk with full observability stack:

```bash
cd infra/compose
docker-compose -f docker-compose.yml -f docker-compose.observability.yml up -d
```

### Accessing Services

- **Backend API**: http://localhost/api/health
- **Prometheus**: http://localhost:9090
- **Jaeger UI**: http://localhost:16686
- **Grafana**: http://localhost/grafana

### Environment Variables

Configure observability via environment variables in `.env`:

```bash
# Prometheus
PROMETHEUS_PORT=9090

# Jaeger
JAEGER_UI_PORT=16686
JAEGER_OTLP_GRPC_PORT=4317
JAEGER_OTLP_HTTP_PORT=4318

# Grafana
GRAFANA_ADMIN_USER=admin
GRAFANA_ADMIN_PASSWORD=changeme

# OpenTelemetry (backend)
OTEL_EXPORTER_OTLP_ENDPOINT=http://jaeger:4317
```

## Testing

### Smoke Tests

Observability smoke tests verify that telemetry is being emitted correctly:

```bash
cd apps/backend
./mvnw test -Dtest=ObservabilityEndpointsTest
./mvnw test -Dtest=HealthEndpointsTest
```

### Manual Verification

1. **Health Endpoints:**
   ```bash
   curl http://localhost/q/health/live
   curl http://localhost/q/health/ready
   curl http://localhost/q/health/started
   ```

2. **Metrics:**
   ```bash
   curl http://localhost/q/metrics
   ```

3. **Traces:** Make API requests and view in Jaeger UI

## Production Considerations

### Sampling Strategy

**Current:** Always-on sampling (100%)
**Production Recommendation:** Tail-based sampling or probabilistic sampling (e.g., 10%)

Update in `application.properties`:
```properties
quarkus.otel.traces.sampler=traceidratio
quarkus.otel.traces.sampler.arg=0.1  # 10% sampling
```

### Data Retention

**Prometheus:**
- Default: 15 days
- Configure: `--storage.tsdb.retention.time=30d`

**Jaeger:**
- Default: In-memory (ephemeral)
- Production: Use persistent storage backend (Elasticsearch, Cassandra)

### Security

1. **Metrics Endpoint:** Currently public for Prometheus scraping
   - Production: Use network segmentation or authentication
   - Alternative: Push metrics to secure gateway

2. **Grafana:** Change default admin password
   - Use OAuth/SSO for authentication
   - Configure role-based access control

3. **Jaeger:** Not exposed publicly
   - Access via VPN or internal network only

### Alerting Integration

Current: Prometheus alert rules (no notification)

**Future:**
- Integrate Alertmanager for notifications
- Configure receivers: Email, Slack, PagerDuty
- Define on-call rotation and escalation policies

## Troubleshooting

### No Metrics Available

1. Verify backend is running: `docker ps`
2. Check metrics endpoint: `curl http://backend:8080/q/metrics`
3. Check Prometheus targets: http://localhost:9090/targets
4. Verify Prometheus configuration: `docker logs regattadesk-prometheus`

### No Traces in Jaeger

1. Verify Jaeger is running: `docker ps`
2. Check OTLP endpoint: `docker logs regattadesk-jaeger`
3. Verify backend configuration:
   ```bash
   docker exec regattadesk-backend env | grep OTEL
   ```
4. Check backend logs for OTEL errors

### Grafana Dashboard Shows No Data

1. Verify datasource: Grafana → Configuration → Data Sources → Prometheus
2. Test datasource connection
3. Check query syntax in panel
4. Verify time range selector

## Future Enhancements

### Step 24 (Hardening)
- Enhanced database connectivity health checks
- MinIO/S3 connectivity health checks
- Custom business metrics (regattas created, entries processed)
- Load testing observability

### Step 25 (Testing Consolidation)
- Integration tests with Testcontainers
- Metrics assertion tests
- Trace assertion tests
- Contract testing for observability endpoints

### Post v0.1
- Log aggregation (Loki or ELK stack)
- Distributed tracing for database queries
- Custom business dashboards
- SLO/SLI tracking and reporting
- Anomaly detection and forecasting

## References

- [Quarkus OpenTelemetry Guide](https://quarkus.io/guides/opentelemetry)
- [Quarkus Micrometer Guide](https://quarkus.io/guides/micrometer)
- [Quarkus SmallRye Health Guide](https://quarkus.io/guides/smallrye-health)
- [Prometheus Documentation](https://prometheus.io/docs/)
- [Jaeger Documentation](https://www.jaegertracing.io/docs/)
- [Grafana Documentation](https://grafana.com/docs/)
- [OpenTelemetry Specification](https://opentelemetry.io/docs/)
