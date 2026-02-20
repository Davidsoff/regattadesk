# RegattaDesk Operational Runbooks

**BC09-002 Implementation**  
**Target Audience:** On-call engineers, DevOps, SREs  
**Last Updated:** 2026-02-13

## Overview

This directory contains operational runbooks for RegattaDesk v0.1 incident response, recovery, and routine operational procedures. These runbooks are designed to be executable by on-call engineers without requiring deep tribal knowledge of the system.

## Runbook Index

### Incident Response

1. **[Service Unavailability](./incident-service-unavailability.md)**
   - Backend service down
   - Database connectivity issues
   - Authelia authentication service failures
   - Complete stack failures

2. **[Authentication and Session Issues](./incident-authentication-failures.md)**
   - Login failures
   - Session timeout problems
   - ForwardAuth middleware issues
   - Authelia misconfigurations

3. **[Public Endpoint Performance Issues](./incident-public-performance.md)**
   - Slow response times
   - High error rates
   - SSE connection failures
   - Rate limiting triggers

4. **Database Issues (RB-004, planned)**
   - Connection pool exhaustion
   - Query performance degradation
   - PostgreSQL service failures
   - Data integrity concerns

### Operational Procedures

5. **[Deployment and Rollback](./procedure-deployment-rollback.md)**
   - Standard deployment process
   - Emergency rollback procedures
   - Configuration updates
   - Zero-downtime deployment strategies

6. **Log Analysis and Troubleshooting (RB-006, planned)**
   - Accessing logs
   - Common log patterns
   - Correlation techniques
   - Using Grafana and Prometheus

7. **Health Check Interpretation (RB-007, planned)**
   - Understanding health endpoint responses
   - Readiness vs liveness vs startup probes
   - When to escalate health check failures

8. **Configuration Management (RB-008, planned)**
   - Environment variable management
   - Secret rotation procedures
   - Traefik configuration updates
   - Authelia configuration changes

## Quick Reference

### Emergency Contacts

| Role | Contact | Escalation |
|------|---------|------------|
| Primary On-Call | See PagerDuty | Backend issues |
| Database Admin | See PagerDuty | Database failures |
| Security Team | See PagerDuty | Security incidents |
| Product Manager | See team roster | Business impact decisions |

### Service URLs (Production)

| Service | URL | Purpose |
|---------|-----|---------|
| Backend API | https://api.regattadesk.example | Main API |
| Frontend | https://regattadesk.example | User interface |
| Authelia | https://regattadesk.example/auth | Authentication |
| Grafana | https://grafana.regattadesk.example | Monitoring |
| Prometheus | https://prometheus.regattadesk.example | Metrics |
| Jaeger | https://jaeger.regattadesk.example | Tracing |

### Common Commands

```bash
# Check service status
docker compose ps

# View logs
docker compose logs -f backend
docker compose logs -f authelia
docker compose logs -f traefik

# Restart a service
docker compose restart backend

# Full stack restart
docker compose down && docker compose up -d

# Check health endpoints
curl https://api.regattadesk.example/q/health/ready
curl https://api.regattadesk.example/api/health

# View Traefik configuration
docker compose exec traefik cat /etc/traefik/dynamic/dynamic.yml
```

### Critical Metrics to Monitor

| Metric | Normal Range | Warning Threshold | Critical Threshold |
|--------|--------------|-------------------|-------------------|
| Response Time (p95) | < 500ms | > 1000ms | > 2000ms |
| Error Rate | < 1% | > 5% | > 10% |
| Memory Usage | < 70% | > 80% | > 90% |
| CPU Usage | < 60% | > 75% | > 85% |
| Database Connections | < 80% pool | > 85% pool | > 95% pool |

## Runbook Template

Each runbook follows a standard structure:

1. **Symptoms** - Observable indicators of the issue
2. **Impact** - Business and technical impact
3. **Initial Triage** - First steps for diagnosis
4. **Resolution Steps** - Detailed fix procedures
5. **Validation** - How to confirm the fix
6. **Prevention** - Long-term mitigation strategies
7. **Escalation Criteria** - When to escalate

## Using These Runbooks

### During an Incident

1. **Identify symptoms** - Match observed behavior to runbook symptoms
2. **Follow triage steps** - Execute diagnostic commands
3. **Apply resolution** - Follow step-by-step resolution procedures
4. **Validate fix** - Confirm issue is resolved
5. **Document** - Update incident log with actions taken
6. **Postmortem** - Schedule postmortem if needed

### Best Practices

- ✅ **Do**: Follow runbooks sequentially
- ✅ **Do**: Document all actions taken
- ✅ **Do**: Communicate status to stakeholders
- ✅ **Do**: Escalate when stuck for > 15 minutes
- ❌ **Don't**: Skip validation steps
- ❌ **Don't**: Make undocumented changes
- ❌ **Don't**: Restart services without checking logs first

## Testing Runbooks

Runbooks should be validated regularly through:

1. **Tabletop exercises** - Walk through runbooks in team meetings
2. **Chaos engineering** - Intentionally trigger failure scenarios
3. **Rotation onboarding** - New on-call engineers execute runbooks
4. **Quarterly reviews** - Update runbooks based on incidents

### Tabletop Exercise Schedule

| Exercise | Frequency | Last Executed | Next Due |
|----------|-----------|---------------|----------|
| Service Unavailability | Quarterly | TBD | TBD |
| Authentication Failure | Quarterly | TBD | TBD |
| Database Failure | Semi-annually | TBD | TBD |
| Full Stack Disaster | Annually | TBD | TBD |

## Contributing

When updating runbooks:

1. Test procedures in staging environment first
2. Update "Last Updated" date in runbook header
3. Include ticket/incident references for context
4. Review changes with team before merging
5. Update this index if adding new runbooks

## Related Documentation

- [OBSERVABILITY.md](../../infra/compose/OBSERVABILITY.md) - Monitoring and telemetry
- [BC09-001 Implementation](../../infra/compose/BC09-001-IMPLEMENTATION-SUMMARY.md) - Observability setup
- [BC09-002 Implementation](../../infra/compose/BC09-002-IMPLEMENTATION-SUMMARY.md) - Edge hardening
- [Developer Setup](../DEVELOPER_SETUP.md) - Local development environment
- [Implementation Plan](../../pdd/implementation/plan.md) - Overall implementation strategy
