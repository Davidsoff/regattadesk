Version: v1 (2026-01-31)

# Implementation Plan (v0.1)
(Each step yields a demoable increment.)

## Checklist
- [ ] Step 1: Repo + baseline Quarkus/Vue skeleton + CI
- [ ] Step 2: Auth0 integration + role model
- [ ] Step 3: Event store schema + append/read primitives
- [ ] Step 4: Core aggregates + projections scaffold
- [ ] Step 5: Public session endpoint + anon JWT cookie + rotation config
- [ ] Step 6: Public versions endpoint + revision tracking
- [ ] Step 7: Public site versioned delivery (minimal pages) + CDN headers
- [ ] Step 8: SSE per regatta (snapshot + ticks) + reconnect guidance
- [ ] Step 9: Blocks, bib pools, overflow pool + bib assignment rules
- [ ] Step 10: Draw (random with stored seed) + publish draw + draw_revision
- [ ] Step 11: Operator QR token model + PDF export + validity logic
- [ ] Step 12: Operator PWA offline shell + local queue + sync protocol
- [ ] Step 13: Line-scan storage: manifests + tiles API + marker CRUD
- [ ] Step 14: Link markers to entries + start/finish times + approval gates
- [ ] Step 15: Investigations + penalties + DSQ/exclusion + results_revision
- [ ] Step 16: Public results pages + live updates via SSE ticks
- [ ] Step 17: Observability: health + OTEL + metrics + dashboards
- [ ] Step 18: Hardening: edge protections, load tests, runbooks

Step format:
Step N: <objective>
Objective:
Implementation guidance:
Test requirements:
Integration with previous steps:
Demo:
