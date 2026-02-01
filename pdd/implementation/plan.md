Version: v2 (2026-02-01)

# Implementation Plan (v0.1)
(Each step yields a demoable increment.)

## Checklist
- [ ] Step 1: Repo + baseline Quarkus/Vue skeleton + containerized deployment + CI/CD pipeline
- [ ] Step 2: Auth0 integration + role model (regatta_admin, head_of_jury, info_desk, financial_manager; super_admin global)
- [ ] Step 3: Event store schema + append/read primitives + audit log retention (retain indefinitely in v0.1)
- [ ] Step 4: Core aggregates + projections scaffold
- [ ] Step 5: Core domain CRUD + staff workflows (API-first; staff UI consumes same API; regatta setup: events/classes grouping, athlete/crew/entry CRUD, crew mutations/withdrawals)
- [ ] Step 6: Public session endpoint + anon JWT cookie + rotation config (used by bootstrap on /versions 401)
- [ ] Step 7: Public versions endpoint + revision tracking + bootstrap flow (call /versions first; on 401 call /public/session then retry /versions once)
- [ ] Step 8: Public site versioned delivery (all pages use /public/v{draw}-{results}) + CDN headers
- [ ] Step 9: Design tokens + table primitives per design/style-guide.md + public accessibility targets (WCAG 2.2 AA min, aim AAA) + operator default high-contrast mode toggle with per-device persistence
- [ ] Step 10: i18n/formatting (nl/en, 24h, dd-MM-yyyy, regatta-local timezone) + printables (A4 PDFs, monochrome-friendly, header with regatta name + generated timestamp + draw/results revisions + page number)
- [ ] Step 11: SSE per regatta (snapshot + multiplexed event types + ticks) + deterministic id + reconnect/backoff (min 100ms, base 500ms, cap 20s, full jitter, retry forever) + per-client cap (open after /versions bootstrap)
- [ ] Step 12: Rulesets: versioning, duplication/update, immutability after draw, age calculation config, validation (gender + min/max age), super_admin-only promotion of regatta-owned rulesets to global selection
- [ ] Step 13: Blocks (start time + intervals between crews/classes), multiple bib pools per block, regatta-level shared overflow pool + bib assignment rules + start-time display config
- [ ] Step 14: Draw (random with stored seed) + publish draw + draw_revision; no insertion after draw in v0.1
- [ ] Step 15: Finance: payment status per entry/club + bulk mark paid/unpaid
- [ ] Step 16: Operator QR token model + PDF export (with fallback instructions) + validity logic + station handoff/PIN flow
- [ ] Step 17: Operator PWA offline shell + local queue + sync protocol + conflict policy (LWW vs manual resolution)
- [ ] Step 18: Line-scan storage: manifests + tiles API + marker CRUD + retention pruning (full scan during regatta, prune to +/-2s around approved markers after delay)
- [ ] Step 19: Link markers to entries + start/finish times + entry completion rule + approval gates
- [ ] Step 20: Investigations + penalties (seconds configurable per regatta) + DSQ/exclusion + DSQ revert behavior (restore prior state) + result labels + results_revision
- [ ] Step 21: Public schedule pages (read model + UI; content depends only on draw_revision)
- [ ] Step 22: Public results pages + live updates via SSE ticks (implement /versions → /public/session retry → SSE bootstrap) + Live/Offline indicator (SSE state only)
- [ ] Step 23: Observability: health + OTEL + metrics + dashboards
- [ ] Step 24: Hardening: edge protections, load tests, runbooks
- [ ] Step 25: Testing strategy: unit tests for command validation/rules, Postgres integration tests via Testcontainers, Pact contract tests for public/staff APIs

Step format:
Step N: <objective>
Objective:
Implementation guidance:
Test requirements:
Integration with previous steps:
Demo:
