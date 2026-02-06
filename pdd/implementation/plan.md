Version: v2
Last Updated: 2026-02-06
Author: RegattaDesk Team

# Implementation Plan (v0.1)
(Each step yields a demoable increment.)

## Checklist
- [ ] Step 1: Repo + baseline Quarkus/Vue skeleton + Docker Compose stack (must include all runtime dependencies: backend, frontend, PostgreSQL, Traefik, Authelia, and any required Authelia backing services such as Redis or DB backend) + CI/CD pipeline
- [ ] Step 2: Authelia SSO integration at Traefik edge + forwarded identity/role mapping model (regatta_admin, head_of_jury, info_desk, financial_manager; super_admin global)
- [ ] Step 3: Event store schema + append/read primitives + audit log retention (retain indefinitely in v0.1)
- [ ] Step 4: Core aggregates + projections scaffold
- [ ] Step 5: Core domain CRUD + staff workflows (API-first; staff UI consumes same API; regatta setup: events/event group grouping, athlete/crew/entry CRUD, crew mutations/withdrawals)
- [ ] Step 6: Public session endpoint + anon JWT cookie (POST /public/session returns 204; idempotent refresh window at 20% of 5d TTL; HttpOnly/Secure/SameSite=Lax/Path=/; Max-Age=5d; HS256 JWT with `kid` rotation using two active keys with ≥6-day overlap) + rotation config (used by bootstrap on /versions 401)
- [ ] Step 7: Public versions endpoint + revision tracking + bootstrap flow (call /versions first; on 401 call /public/session then retry /versions once)
- [ ] Step 8: Public site versioned delivery (all pages use /public/v{draw}-{results}) + CDN headers
- [ ] Step 9: Design tokens + table primitives per design/style-guide.md + public accessibility targets (WCAG 2.2 AA min, aim AAA) + operator default high-contrast mode toggle with per-device persistence
- [ ] Step 10: i18n/formatting (nl/en, 24h, ISO 8601 `YYYY-MM-DD` in technical docs/APIs, locale-dependent UI display, regatta-local timezone) + printables (A4 PDFs, monochrome-friendly, header with regatta name + generated timestamp + draw/results revisions + page number)
- [ ] Step 11: SSE per regatta (snapshot + multiplexed event types + ticks; events: `snapshot`/`draw_revision`/`results_revision` with data `{draw_revision, results_revision, reason?}`) + deterministic id + reconnect/backoff (min 100ms, base 500ms, cap 20s, full jitter, retry forever) + per-client cap (open after /versions bootstrap)
- [ ] Step 12: Rulesets: versioning, duplication/update, immutability after draw, age calculation config, validation (gender + min/max age), super_admin-only promotion of regatta-owned rulesets to global selection
- [ ] Step 13: Blocks (start time + intervals between crews/event groups), multiple bib pools per block, regatta-level shared overflow pool + bib assignment rules + start-time display config
- [ ] Step 14: Draw (random with stored seed) + publish draw + draw_revision; no insertion after draw in v0.1
- [ ] Step 15: Finance: payment status per entry/club + bulk mark paid/unpaid
- [ ] Step 16: Operator QR token model + PDF export (with fallback instructions) + validity logic + station handoff/PIN flow
- [ ] Step 17: Operator PWA offline shell + local queue + sync protocol + conflict policy (LWW vs manual resolution)
- [ ] Step 18: Line-scan storage: manifests + tiles API + marker CRUD + retention pruning (full scan during regatta; default 14-day delay after regatta end; do not prune until regatta archived or all entries approved; if delay elapses first, keep full scan and raise admin alert; then prune to +/-2s around approved markers)
- [ ] Step 19: Link markers to entries + start/finish times + entry completion rule + approval gates
- [ ] Step 20: Investigations + penalties (seconds configurable per regatta) + DSQ/exclusion + DSQ revert behavior (restore prior state) + result labels + results_revision
- [ ] Step 21: Public schedule pages (read model + UI; content depends only on draw_revision)
- [ ] Step 22: Public results pages + live updates via SSE ticks (implement /versions → /public/session retry → SSE bootstrap) + Live/Offline indicator (SSE state only)
- [ ] Step 23: Observability: health + OTEL + metrics + dashboards
- [ ] Step 24: Hardening: edge protections, load tests, runbooks
- [ ] Step 25: Testing strategy: unit tests for command validation/rules, Postgres integration tests via Testcontainers, Pact contract tests for public/staff APIs

## Third-party dependency inventory (v0.1 baseline)
| Component | Dependency / Service | Minimum version | Update policy |
| --- | --- | --- | --- |
| Backend runtime | Java (LTS) | 21 | Track latest Java 21 patch monthly; evaluate next LTS in quarterly review |
| Backend framework | Quarkus | 3.8+ | Stay on latest stable minor in current major; patch updates monthly |
| Database | PostgreSQL | 16+ | Stay within supported major versions; apply minor patches monthly |
| Frontend runtime | Node.js (LTS) | 22+ | Stay on active LTS; patch updates monthly |
| Frontend framework | Vue | 3.4+ | Stay on latest stable minor in major 3; update quarterly |
| Build tooling | Vite | 5.0+ | Stay on latest stable minor; patch updates monthly |
| Container orchestration | Docker Compose | 2.24+ | Stay on latest stable minor; patch updates monthly |
| Reverse proxy | Traefik | 3.0+ | Track latest stable minor; patch updates monthly |
| SSO/AuthN | Authelia | 4.38+ | Track latest stable minor; patch updates monthly |
| PDF generation | OpenPDF | 1.3+ | Patch updates quarterly; security advisories are expedited |
| Testing (integration) | Testcontainers | 1.20+ | Keep within latest stable minor; update quarterly |
| API contract testing | Pact | 4.6+ | Keep within latest stable minor; update quarterly |
| Accessibility testing | axe-core | 4.9+ | Update quarterly; pin in CI for repeatable results |
| Telemetry | OpenTelemetry SDK | 1.35+ | Keep current stable minor; patch updates monthly |

Dependency governance:
- Use latest stable releases at implementation time, with the table values as minimum supported versions.
- Pin all dependency versions in lockfiles/build manifests.
- Run automated dependency vulnerability scan weekly.
- Security or critical bug fixes can bypass normal cadence.

Docker Compose requirement:
- For v0.1, Docker Compose is the canonical runtime for both local development and production deployment.
- The `docker-compose` stack must include every required runtime dependency from this plan (no externally assumed core services).

Step format:
Step N: <objective>
Objective:
Implementation guidance:
Test requirements:
Integration with previous steps:
Demo:
