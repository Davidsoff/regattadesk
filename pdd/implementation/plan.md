Version: v2
Last Updated: 2026-02-11
Author: RegattaDesk Team

# Implementation Plan (v0.1)
(Each step yields a demoable increment.)

## Bounded Context Decomposition
This implementation plan is decomposed into bounded contexts, each with functional and non-functional feature lists:

1. [BC01 Platform and Delivery](bc01-platform-and-delivery.md)
2. [BC02 Identity and Access](bc02-identity-and-access.md)
3. [BC03 Core Regatta Management](bc03-core-regatta-management.md)
4. [BC04 Rules, Scheduling, and Draw](bc04-rules-scheduling-and-draw.md)
5. [BC05 Public Experience and Delivery](bc05-public-experience-and-delivery.md)
6. [BC06 Operator Capture and Line Scan](bc06-operator-capture-and-line-scan.md)
7. [BC07 Results and Adjudication](bc07-results-and-adjudication.md)
8. [BC08 Finance and Payments](bc08-finance-and-payments.md)
9. [BC09 Operability, Hardening, and Quality](bc09-operability-hardening-and-quality.md)

## Bounded Context Ticket Backlogs (GitHub Export)
1. [BC01 tickets](issues/bc01-platform-and-delivery.issues.yaml)
2. [BC02 tickets](issues/bc02-identity-and-access.issues.yaml)
3. [BC03 tickets](issues/bc03-core-regatta-management.issues.yaml)
4. [BC04 tickets](issues/bc04-rules-scheduling-and-draw.issues.yaml)
5. [BC05 tickets](issues/bc05-public-experience-and-delivery.issues.yaml)
6. [BC06 tickets](issues/bc06-operator-capture-and-line-scan.issues.yaml)
7. [BC07 tickets](issues/bc07-results-and-adjudication.issues.yaml)
8. [BC08 tickets](issues/bc08-finance-and-payments.issues.yaml)
9. [BC09 tickets](issues/bc09-operability-hardening-and-quality.issues.yaml)
10. [Export instructions](issues/README.md)
11. [Ticket coverage map](issues/coverage-map.md)

## Functional Coverage Map (Checklist -> Bounded Context)
| Plan requirement | Bounded context owner |
| --- | --- |
| Step 1 | BC01 |
| Step 2 | BC02 |
| Step 3 | BC03 |
| Step 4 | BC03 |
| Step 5 | BC03 |
| Step 6 | BC02 |
| Step 7 | BC05 (depends on BC02) |
| Step 8 | BC05 |
| Step 9 | BC05 and BC06 |
| Step 10 | BC05 |
| Step 11 | BC05 |
| Step 12 | BC04 |
| Step 13 | BC04 |
| Step 14 | BC04 |
| Step 15 | BC08 |
| Step 16 | BC06 |
| Step 17 | BC06 |
| Step 18 | BC06 |
| Step 19 | BC06 |
| Step 20 | BC07 |
| Step 21 | BC05 |
| Step 22 | BC05 (depends on BC02 and BC07) |
| Step 23 | BC09 |
| Step 24 | BC09 |
| Step 25 | BC09 |

## Non-Functional Coverage Map
| Non-functional requirement | Bounded context owner |
| --- | --- |
| Canonical Docker Compose runtime and complete in-stack dependencies | BC01 |
| Minimum dependency versions and update cadence | BC01 |
| Dependency pinning and weekly vulnerability scanning | BC01 and BC09 |
| Security/identity constraints (roles, secure cookies, JWT rotation) | BC02 |
| Indefinite audit-log retention and event traceability | BC03 |
| Draw reproducibility and post-draw immutability | BC04 |
| Public caching policy, immutable versioned delivery, SSE reconnect/cap behavior | BC05 |
| WCAG 2.2 AA public target, localization/time formatting, printable output constraints | BC05 |
| Operator high-contrast defaults, offline sync reliability, line-scan retention/pruning safety | BC06 |
| Reversible adjudication and result-state consistency | BC07 |
| Finance access control and auditable payment updates | BC08 |
| Observability, hardening, load validation, runbooks, and CI test-gate enforcement | BC09 |
| Step-level minimum testing policy (unit/integration/contract/UI + accessibility checks) | BC09 |

## Checklist
- [ ] Step 1: Repo + baseline Quarkus/Vue skeleton + Docker Compose stack (must include all runtime dependencies: backend, frontend, PostgreSQL, Traefik, Authelia, and MinIO object storage for line-scan tiles/manifests; DB-only Authelia backing in v0.1 and no Redis dependency; TLS strategy: Let's Encrypt ACME in production and self-signed certificates for local development) + CI/CD pipeline
- [ ] Step 2: Authelia SSO integration at Traefik edge + forwarded identity/role mapping model (regatta_admin, head_of_jury, info_desk, financial_manager; super_admin global)
- [ ] Step 3: Event store schema + append/read primitives + audit log retention (retain indefinitely in v0.1)
- [ ] Step 4: Core aggregates + projections scaffold
- [ ] Step 5: Core domain CRUD + staff workflows (API-first; staff UI consumes same API; regatta setup: events/event group grouping, athlete/crew/entry CRUD, crew mutations/withdrawals)
- [ ] Step 6: Public session endpoint + anon JWT cookie (POST /public/session returns 204; idempotent refresh window at 20% of 5d TTL; HttpOnly/Secure/SameSite=Lax/Path=/; Max-Age=5d; HS256 JWT with `kid` rotation using two active keys with ≥6-day overlap) + rotation config (used by bootstrap on /versions 401)
- [ ] Step 7: Public versions endpoint + revision tracking + bootstrap flow (call /versions first; on 401 call /public/session then retry /versions once)
- [ ] Step 8: Public site versioned delivery (all pages use /public/v{draw}-{results}) + CDN headers (`/public/session`: `Cache-Control: no-store`; `/public/regattas/{regatta_id}/versions`: `Cache-Control: no-store, must-revalidate`; `/public/v{draw}-{results}/...`: `Cache-Control: public, max-age=31536000, immutable`)
- [ ] Step 9: Design tokens + table primitives per design/style-guide.md + public accessibility targets (WCAG 2.2 AA min, aim AAA) + operator default high-contrast mode toggle with per-device persistence
- [ ] Step 10: i18n/formatting (nl/en, 24h, ISO 8601 `YYYY-MM-DD` in technical docs/APIs, locale-dependent UI display, regatta-local timezone) + printables (A4 PDFs, monochrome-friendly, header with regatta name + generated timestamp + draw/results revisions + page number)
- [ ] Step 11: SSE per regatta (snapshot + multiplexed event types + ticks; events: `snapshot`/`draw_revision`/`results_revision` with data `{draw_revision, results_revision, reason?}`) + deterministic id + reconnect/backoff (min 100ms, base 500ms, cap 20s, full jitter, retry forever) + per-client cap (open after /versions bootstrap)
- [ ] Step 12: Rulesets: versioning, duplication/update, immutability after draw, age calculation config, validation (gender + min/max age), super_admin-only promotion of regatta-owned rulesets to global selection
- [ ] Step 13: Blocks (start time + intervals between crews/event groups), multiple bib pools per block, regatta-level shared overflow pool + bib assignment rules + start-time display config
- [ ] Step 14: Draw (random with stored seed) + publish draw + draw_revision; no insertion after draw in v0.1
- [ ] Step 15: Finance: payment status per entry/club + bulk mark paid/unpaid
- [ ] Step 16: Operator QR token model + PDF export (with fallback instructions) + validity logic + station handoff/PIN flow
- [ ] Step 17: Line-scan storage: manifests + tiles API + marker CRUD + retention pruning (canonical endpoints: `POST /api/v1/regattas/{regatta_id}/line_scan/manifests`, `GET /api/v1/regattas/{regatta_id}/line_scan/manifests/{manifest_id}`, `PUT|GET /api/v1/regattas/{regatta_id}/line_scan/tiles/{tile_id}`; ingest via OperatorTokenAuth; retrieval via OperatorTokenAuth or StaffProxyAuth; tile storage path is intentionally API-managed/non-event-sourced in v0.1; full scan during regatta; default 14-day delay after regatta end; do not prune until regatta archived or all entries approved; if delay elapses first, keep full scan and raise admin alert; then prune to +/-2s around approved markers)
- [ ] Step 18: Operator PWA offline shell + local queue + sync protocol + conflict policy (LWW vs manual resolution)
- [ ] Step 19: Link markers to entries + start/finish times + entry completion rule + approval gates
- [ ] Step 20: Investigations + penalties (seconds configurable per regatta) + DSQ/exclusion + DSQ revert behavior (restore prior state) + result labels + results_revision
- [ ] Step 21: Public schedule pages (read model + UI; content depends only on draw_revision)
- [ ] Step 22: Public results pages + live updates via SSE ticks (implement /versions → /public/session retry → SSE bootstrap) + Live/Offline indicator (SSE state only)
- [ ] Step 23: Observability: health + OTEL + metrics + dashboards
- [ ] Step 24: Hardening: edge protections, load tests, runbooks
- [ ] Step 25: Testing consolidation: expand and harden suite coverage (unit tests for command validation/rules, Postgres integration tests via Testcontainers, Pact contract tests for public/staff APIs), close coverage gaps from earlier steps, and enforce CI quality gates

## v0.1 Change Policy

- RegattaDesk v0.1 is pre-production. Breaking changes are allowed when they simplify implementation and remove unused compatibility layers.
- Do not add deprecation-only API shims or migration paths unless a concrete active consumer requires them.
- Any breaking change must include synchronized updates to affected `pdd/` artifacts in the same PR.

## Third-party dependency inventory (v0.1 baseline)
| Component | Dependency / Service | Minimum version | Update policy |
| --- | --- | --- | --- |
| Backend runtime | Java | 25 | Track latest Java 25 patch monthly; evaluate next release cycle in quarterly review |
| Backend framework | Quarkus | 3.8+ | Stay on latest stable minor in current major; patch updates monthly |
| Database | PostgreSQL | 16+ | Stay within supported major versions; apply minor patches monthly |
| Frontend runtime | Node.js (LTS) | 22+ | Stay on active LTS; patch updates monthly |
| Frontend framework | Vue | 3.4+ | Stay on latest stable minor in major 3; update quarterly |
| Build tooling | Vite | 5.0+ | Stay on latest stable minor; patch updates monthly |
| Container orchestration | Docker Compose | 2.24+ | Stay on latest stable minor; patch updates monthly |
| Reverse proxy | Traefik | 3.0+ | Track latest stable minor; patch updates monthly |
| SSO/AuthN | Authelia | 4.38+ | Track latest stable minor; patch updates monthly |
| Object storage | MinIO (S3-compatible) | latest stable | Track latest stable release; patch updates monthly |
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

Step quality gates:
- Every implementation step must include minimum required tests before the step is considered complete.
- Minimum test requirement per step:
  - Domain/command logic changes: unit tests.
  - Persistence/query changes: integration tests against PostgreSQL (Testcontainers where applicable).
  - API contract changes (public or staff): contract tests (Pact) and endpoint integration checks.
  - UI/UX changes: targeted component/page tests; WCAG 2.2 AA checks for affected public flows; mandatory accessibility checks for affected staff/operator critical flows (keyboard, screen reader smoke, contrast/touch-target).
- CI must pass for the step branch/commit before moving to the next step.

Docker Compose requirement:
- For v0.1, Docker Compose is the canonical runtime for both local development and production deployment.
- The `docker-compose` stack must include every required runtime dependency from this plan (no externally assumed core services).
- Line-scan tile/manifest storage must be provided by in-stack object storage (MinIO) in v0.1; external object storage is not assumed.
- Authelia backing mode is DB-only in v0.1 (no Redis service in compose).

Step format:
Step N: <objective>
Objective:
Implementation guidance:
Test requirements:
Integration with previous steps:
Demo:
