Version: v2 (2026-02-01)

# RegattaDesk v0.1 Detailed Design

## Overview
RegattaDesk is a regatta management system for rowing head races (single distance) that supports:
- staff workflows (entries, draw, bibs, jury, finance),
- operator workflows (start/finish timekeeping with line-scan camera UI, offline-capable),
- a high-traffic public read site with cacheable pages and live updates.

Backend: Quarkus (Java) + Postgres. Frontend: Vue.
Architecture adopts Event Sourcing + projections for read models and public delivery.

## Detailed Requirements
(See idea-honing.md for consolidated requirements. This design is standalone, but references the same decisions.)

### Functional
- Regatta setup: events/classes, blocks, bib pools, overflow pool, display prefs, penalties, ruleset selection.
- Entries/crews/athletes: CRUD via API; crew reusable across regattas; entry is regatta-scoped participation.
- Draw: random v0.1, stored seed for reproducibility, publish increments draw_revision; no insertion after draw.
- Bibs: collision resolution assigns next available bib to changing entry; missing bib replacement affects only that entry.
- Timing: line-scan markers create/move/delete; link/unlink to bib; immutability after approval.
- Jury: investigations per entry; outcomes include no action, penalty seconds, exclusion, DSQ; approvals gate.
- Public: cacheable versioned paths; /versions no-store; SSE ticks and snapshot.

### Non-functional
- Operator offline: queued actions; sync; last-write-wins on allowed fields.
- High read scalability: CDN caching + versioned paths + SSE ticks.
- Observability: health + OpenTelemetry + metrics.
- Audit: event sourcing + immutable log.

## Architecture Overview

```mermaid
flowchart LR
  subgraph Clients
    Staff[Staff Web (Vue)]
    Ops[Start/Finish Operator PWA (Vue)]
    Public[Public Web (Vue)]
  end

  subgraph Edge
    CDN[CDN/Cache]
    SSE[SSE Gateway (HTTP)]
  end

  subgraph Backend[Quarkus Backend]
    API[REST API]
    CMD[Command Handler]
    ES[Event Store (Postgres)]
    PROJ[Projectors]
    RM[(Read Models / Projections)]
    OBJ[(Object Storage: tiles + manifests)]
  end

  Staff --> API
  Ops --> API
  Public --> CDN --> API
  Public --> SSE --> API

  API --> CMD --> ES
  ES --> PROJ --> RM
  API --> RM

  Ops --> OBJ
  API --> OBJ
```

## Frontend UX and design system
- v0.1 style and component specs are defined in `style-guide.md`.
- Design system approach:
  - CSS variables as the canonical token layer.
  - Headless Vue components for reuse across Staff/Operator/Public.
  - Theme/density controlled via `<html>` attributes (e.g. `data-contrast`, `data-density`).
- Public accessibility target: WCAG 2.2 AA minimum; aim AAA where feasible for key schedule/results flows.
- Operator UX constraints:
  - Must remain usable on iPhone SE class devices.
  - Must support outdoor readability via high contrast and larger touch targets.
  - PIN/token flows must not interrupt active capture UI.

## Components and Interfaces
- Staff API: Auth0 JWT, regatta-scoped roles (+ super_admin).
- Operator API: QR token scoped to block(s), station, validity window, revocable.
- Public:
  - POST /public/session (204) mints/refreshes anon HttpOnly JWT cookie (HS256; iss/aud; kid rotation).
  - GET /public/regattas/{id}/versions returns {draw_revision, results_revision}, Cache-Control: no-store.
  - GET /public/regattas/{id}/events SSE: snapshot on connect + revision ticks.
  - GET /public/v{d}-{r}/... versioned pages/data cacheable.

## Data Models (high level)
- event_store: aggregate streams with sequence numbers; payload + metadata.
- projections: public tables keyed by (regatta_id, draw_rev, results_rev) where appropriate.
- line-scan storage: capture session + tile manifest + tiles in object storage; markers reference tile coords and computed time.

## Error Handling
- Structured error responses {code, message, details}.
- Optimistic concurrency: 409 on stale expected version.
- Public session errors:
  - 401 ANON_SESSION_MISSING or ANON_SESSION_INVALID
  - client calls POST /public/session then retries once.

## Security and Privacy
- Separate signing keys for anon public JWTs vs Auth0 staff JWTs.
- No client-id in metric labels (avoid privacy + cardinality).
- Event store audit retained indefinitely in v0.1.

## Performance and Scalability
- Public: CDN caching of versioned paths; SSE minimal ticks; /versions cheap.
- Media: tiles+manifest (avoids single-image dimension limits); post-regatta pruning to ±2s around markers.

## Observability
- Health endpoints enabled.
- OpenTelemetry export enabled.
- Metrics: SSE accept/reject counts, active connections by regatta, /versions request counts, etc.

## Testing Strategy
- Unit tests for command validation and rules.
- Integration tests with Postgres (Testcontainers).
- Contract tests (Pact) for public/staff APIs to enable parallel FE/BE work.
