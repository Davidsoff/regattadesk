Version: v2 (2026-02-01)

# RegattaDesk v0.1 Detailed Design

## Overview
RegattaDesk is a regatta management system for rowing head races (single distance) that supports:
- staff workflows (entries, draw, bibs, jury, finance),
- operator workflows (start/finish timekeeping with line-scan camera UI, offline-capable),
- a high-traffic public read site with cacheable pages and live updates.

Backend: Quarkus (Java) + Postgres. Frontend: Vue.
Architecture adopts Event Sourcing + projections for read models and public delivery.
API-first for all operations; staff/operator/public clients consume the same API surface (imports can be external tools later).

## Detailed Requirements
(See idea-honing.md for consolidated requirements. This design is standalone, but references the same decisions.)

### Functional
- Regatta setup: events/classes, blocks, bib pools (multiple per block), overflow pool, display prefs (per-entry vs block-only start time), penalties (seconds configurable per regatta), ruleset selection.
- Blocks: schedule start time plus interval between crews and interval between classes (block-level config).
- Entries/crews/athletes: CRUD via API; crew reusable across regattas; entry is regatta-scoped participation.
  - Athlete fields: full name, DOB, gender, club, optional federation id.
  - Crew fields: display name; derived club; list of athletes + seat order.
  - Entry fields: bib, start position, status.
- Events: class/category/boat type; may be grouped into overarching classes per regatta.
- Rulesets: versioned; can be duplicated and updated; global rulesets immutable once linked to a regatta with a published draw; regatta cannot change ruleset after draw; age calculation config (actual age at start vs age as of Jan 1).
  - Only super_admin can promote regatta-owned rulesets to global selection.
  - Validation checks: gender compatibility and min/max age constraints.
- Finance: track payment status per entry or per club; support bulk mark paid/unpaid with audit trail.
- Draw: random v0.1, stored seed for reproducibility, publish increments draw_revision; no insertion after draw; classes start sequentially but finishes can interleave.
- Bibs: collision resolution assigns next available bib to changing entry; missing bib replacement affects only that entry; default assignment direction (smallest/largest) configurable; blocks can have multiple bib pools; overflow pool usage bounded by physical inventory.
  - Overflow pool is regatta-level and shared across all blocks; any block may borrow from it.
- Timing: line-scan markers create/move/delete; link/unlink to bib; immutability after approval.
  - UI: overview strip plus draggable detail window for fine alignment at start/finish.
  - Selecting an unlinked marker recenters the detail view on that marker.
  - Capture metadata: recording start time + fps; compute time from frame offset.
  - Marker metadata: timestamp, capture device id, image tile reference (tile coords/ids).
  - Unassigned markers are not audited; assigned markers become immutable after approval.
  - Retention: keep full line scan during regatta; after configured delay prune to ±2s around approved markers.
- Operator workflow: global queue across blocks (not necessarily draw order); marker→bib linking with quick correction; DNS batch warnings before bulk changes.
- Jury: investigations per entry; outcomes include no action, penalty seconds (value configurable per regatta), exclusion, DSQ; approvals gate.
  - Multiple investigations per entry allowed; closure is per investigation.
  - "No action" closes the investigation and leaves the entry approved as-is.
  - Tribunal escalation is modeled by re-opening an investigation.
  - Not all entries in a single investigation must receive penalties.
- Approvals/results: entry completion criteria, event approval gating (withdrawn entries excluded), DSQ reversible via is_dsq flag (reverting DSQ restores prior state, typically approved); result labels provisional/edited/official.
  - Entry completion: finish time set OR status dns/dnf/dsq/excluded and not under investigation.
  - Event cannot be approved unless every non-withdrawn entry is in approved/dns/dnf/dsq/excluded state.
  - Result labels:
    - provisional: computed but not event-approved
    - edited: manual adjustment or penalty applied (still provisional until approval)
    - official: event approved
- Public: cacheable versioned paths with draw/results revision keys; /versions no-store; SSE ticks and snapshot.
  - Schedule/start order content depends only on draw_revision, even though cache keys include both revisions.

### Status taxonomy
- Domain status values: active, withdrawn_before_draw, withdrawn_after_draw, dns, dnf, excluded, dsq.
- Derived workflow/UI states: under_investigation, approved/immutable, offline_queued, provisional/edited/official.

### Non-functional
- Operator offline: queued actions; sync with explicit conflict policy.
  - Last-write-wins: marker position/time adjustments and unlinking when entry is not approved.
  - Auto-accept link if entry has no linked marker at that station and marker is not linked elsewhere.
  - Manual resolution required: duplicate links (entry already linked to a different marker), marker linked to a different entry, or any edits against approved/immutable entries (reject and surface conflict).
- High read scalability: CDN caching + versioned paths + SSE ticks.
- Containerized deployment + automated pipeline (CI/CD).
- Observability: health + OpenTelemetry + metrics.
- Audit: event sourcing + immutable log.

### Out of scope (v0.1)
- Side-by-side racing, multi-distance, handicap/conversion factors (beyond “winner time” ranking).
- OCR bib recognition.
- Built-in CSV import UI (API-only; import tools later).

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
- Staff accessibility: no hard requirement, but avoid obviously inaccessible patterns (focus visibility, contrast, touch targets).
- Internationalization and formatting:
  - Initial locales: Dutch (`nl`) and English (`en`).
  - Time: 24h.
  - Dates: `dd-MM-yyyy`.
  - Time zone: regatta-local (future may add viewer-local toggle).
- Printing:
  - Admin generates printables as A4 PDFs; monochrome-friendly output.
  - Each page header includes regatta name, generated timestamp, draw/results revisions, and page number.
- Operator UX constraints:
  - Must remain usable on iPhone SE class devices.
  - Must support outdoor readability via high contrast and larger touch targets.
  - Default to high-contrast mode with a toggle back to standard; persist preference per device.
  - PIN/token flows must not interrupt active capture UI.

## Components and Interfaces
- Staff API: Auth0 JWT, regatta-scoped roles (+ super_admin).
  - Regatta roles: regatta_admin, head_of_jury, info_desk, financial_manager; super_admin is global.
- Operator API: QR token scoped to block(s), station, validity window, revocable; operators are accountless (QR/token only).
  - Station model: single active station per token; second device can request access without interrupting active station.
  - Handoff: new device shows a PIN; active station can reveal the matching PIN to complete handover.
  - Admin can view PIN remotely only if the active station cannot access the PIN flow.
  - Token display must not interrupt capture UI (hidden unless opened intentionally).
  - QR tokens exportable to PDF with fallback instructions (short URL + token/PIN) if QR scan fails.
- Public:
  - POST /public/session (204) mints/refreshes anon HttpOnly JWT cookie (HS256; iss/aud; kid rotation).
    - Sliding TTL 5 days; refresh window 20% of TTL; refresh only when a valid anon cookie is already present.
    - Key rotation: two active keys; overlap ≥6 days.
    - Cookie attributes: HttpOnly; Secure; SameSite=Lax; Path=/; Max-Age=5d.
    - Idempotent; Cache-Control: private, no-store; mild abuse protection (no Origin/Referer checks).
    - CSRF: no token required (anonymous + idempotent); rely on SameSite=Lax.
    - JWT includes a stable client-id claim used for per-client SSE caps.
  - Bootstrap: call /versions first; if 401 missing/invalid then call /public/session and retry /versions once; then open SSE.
  - GET /public/regattas/{id}/versions returns {draw_revision, results_revision}, Cache-Control: no-store; rate-limited.
  - GET /public/regattas/{id}/events SSE: snapshot on connect + revision ticks.
    - Multiplexed by event type (event: snapshot, draw_revision, results_revision).
    - Deterministic SSE id includes draw_revision + results_revision.
    - Per-client cap: 20 concurrent connections per client-id per regatta; reject excess with 429.
    - Reconnect: exponential backoff with full jitter; min 100ms, base 500ms, cap 20s; retry forever.
    - No per-IP concurrent cap or per-IP rate limiting until measured.
    - UI shows a minimal Live/Offline indicator based on SSE connection state only (no freshness claim).
  - GET /public/v{d}-{r}/... versioned pages/data cacheable.
    - Cache keys include draw_revision + results_revision; client soft-updates and replaces URL to latest version.
    - Schedule/start order content still only changes with draw_revision.
    - Withdrawal after draw bumps draw_revision only (no results_revision change).

## Data Models (high level)
- event_store: aggregate streams with sequence numbers; payload + metadata.
- projections: public tables keyed by (regatta_id, draw_rev, results_rev) where appropriate.
- payments: per-entry and per-club payment status in projections; events for mark_paid/mark_unpaid.
- results_state: derived labels (provisional/edited/official) based on approvals and manual edits/penalties.
- line-scan storage: capture session + tile manifest + tiles in object storage; markers reference tile coords and computed time, and store timestamp + capture device id.

## Error Handling
- Structured error responses {code, message, details}.
- Optimistic concurrency: 409 on stale expected version.
- Public session errors:
  - 401 ANON_SESSION_MISSING or ANON_SESSION_INVALID
  - client calls POST /public/session then retries once.
- SSE limits:
  - 429 TOO_MANY_REQUESTS when per-client cap exceeded.

## Security and Privacy
- Separate signing keys for anon public JWTs vs Auth0 staff JWTs.
- No client-id in metric labels (avoid privacy + cardinality).
- Event store audit retained indefinitely in v0.1.

## Performance and Scalability
- Public: CDN caching of versioned paths; SSE minimal ticks; /versions cheap.
- Media: tiles+manifest (avoids single-image dimension limits); post-regatta pruning to ±2s around approved markers.

## Observability
- Health endpoints enabled.
- OpenTelemetry export enabled.
- Metrics: SSE accept/reject counts, active connections by regatta, /versions request counts, etc.

## Testing Strategy
- Unit tests for command validation and rules.
- Integration tests with Postgres (Testcontainers).
- Contract tests (Pact) for public/staff APIs to enable parallel FE/BE work.
