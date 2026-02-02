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
- Terminology (glossary; use consistently in this doc):
  - Category: demographic grouping (age/gender/skill level).
  - Boat type: shell/rigging type (e.g., 1x, 2x, 2-, 4+, 8+).
  - Event: a Category × Boat type pairing; primary unit for entries, scheduling, and results.
  - Event group (optional): named grouping of events for awards or schedule organization (avoid using “class” as a standalone term).
  - Block: operational scheduling unit with an ordered list of events.
- Regatta setup: events (with optional event grouping), blocks, bib pools (multiple per block), overflow pool, display prefs (per-entry vs block-only start time), penalties (seconds configurable per regatta), ruleset selection.
- Regatta state lifecycle: draft → published (draw published) → archived → deleted.
- Blocks: schedule start time plus interval between crews and interval between events (block-level config).
- Regatta end definition (for retention windows): use explicit regatta_end_at timestamp when set; otherwise compute from the latest block’s scheduled end time.
- Scheduled start time (deterministic):
  - per-entry scheduled_start = block_start + (event_index * event_interval) + (crew_index_within_event * crew_interval)
  - indices are zero-based in draw order; event order is the block’s event sequence, crew order is the event draw order
- Block scheduled end time (best practice): block_end = last_scheduled_start + crew_interval.
  - Equivalent formula for final crew: block_end = block_start + (event_index * event_interval) + (crew_index_within_event * crew_interval) + crew_interval
- Entries/crews/athletes: CRUD via API; crew reusable across regattas; entry is regatta-scoped participation.
  - Club fields: id, name, short_name.
  - Athlete fields: full name, DOB, gender, club, optional federation id.
    - Federation ID format: alphanumeric, max 20 chars; optional field for national federation integration.
    - API for searching athletes by federation ID: GET /api/v1/athletes?federationId={id}.
  - Crew fields: display name; explicit club assignment when provided, otherwise derived from athletes; list of athletes + seat order.
    - Derived club rule: if all athletes share the same club, use that club; otherwise mark crew as composite/multi-club.
  - Entry fields: bib, start position, status.
- Events: category × boat type; may be grouped into event groups per regatta.
- Rulesets: versioned; can be duplicated and updated; global rulesets immutable once linked to a regatta with a published draw; regatta cannot change ruleset after draw; age calculation config (actual age at start vs age as of Jan 1).
  - Only super_admin can promote regatta-owned rulesets to global selection.
  - Validation checks: gender compatibility and min/max age constraints.
- Finance: payment_status enum (v0.1) is `unpaid` or `paid` only (no partial/refund support); default is `unpaid`.
  - Payment amount configuration: entry fee is configurable per regatta (default €0.00); stored in regatta configuration.
  - Club-level billing: configurable billing contact and invoicing details per club.
  - Entry-level status is the source of truth.
  - Club “paid/unpaid” is a bulk action that updates entries with audit events.
  - Club status is derived from current entry statuses (paid only if all current billable entries are `paid`; otherwise `unpaid`).
  - Optional payment metadata fields: paid_at, paid_by, payment_reference.
  - Billing club source of truth: entry.billing_club_id when set; otherwise crew’s club (single-club crews only).
  - Composite/multi-club crews require explicit billing_club_id (and remain labeled as composite for reporting).
- Draw: random v0.1, stored seed for reproducibility, publish increments draw_revision; no insertion after draw; events start sequentially but finishes can interleave.
  - Bib pools are immutable after draw publish. To change pools, unpublish draw first.
  - Draw generation uses the bib pool allocation algorithm. If a pool is exhausted, continue to the next pool. If overflow is exhausted, throw error.
- Bibs: regatta-wide unique; collision resolution assigns next available bib to changing entry; missing bib replacement affects only that entry; default assignment direction (smallest/largest) configurable per regatta (no per-pool override in v0.1); blocks can have multiple bib pools; overflow pool usage bounded by physical inventory.
  - Pools/overflow must be defined as non-overlapping inclusive numeric ranges (or explicit lists); validate uniqueness across all pools at setup.
  - Allocation priority: use the entry’s block primary pool first, then additional block pools in configured order, then overflow.
  - Next available bib = first unused number in the current pool when scanning in the configured direction; if exhausted, continue to the next pool in priority order.
  - Overflow pool is regatta-level and shared across all blocks; any block may borrow from it.
- Timing: line-scan markers create/move/delete; link/unlink to bib; immutability after approval.
  - UI: overview strip plus draggable detail window for fine alignment at start/finish.
  - Selecting an unlinked marker recenters the detail view on that marker.
  - Capture metadata: recording start time + fps; compute time from frame offset.
  - Recording start time sourced via device time sync at session start (server handshake/NTP); store server_time_at_start plus device monotonic offset.
  - Drift handling: periodic resync; apply offset correction; if drift exceeds threshold, flag the capture session for review.
  - Fallback: if device time is invalid or sync fails, mark the capture session as unsynced and require manual timestamp correction before approvals (server-received timestamps are provisional only).
  - Time storage (best practice): regatta.time_zone stored as IANA TZ name (e.g., `Europe/Amsterdam`); store timestamps in UTC (Instant) and display in regatta-local time zone.
  - Marker metadata: timestamp, capture device id, image tile reference (tile coords/ids).
  - Tile defaults (best practice): 512x512 WebP lossless tiles with PNG fallback; manifest includes tile size, origin, and x-coordinate -> timestamp mapping.
  - Unlinked marker create/delete is not audited.
  - Link/unlink actions and edits to linked markers are always audited.
  - Pre-approval deletes of linked markers emit audit events.
  - Linked markers become immutable after approval.
  - Retention: keep full line scan during regatta; after configured delay (default 14 days after regatta end, configurable per regatta) prune to ±2s around approved markers. (Regatta end defined above.)
    - Do not prune until the regatta is archived or all entries are approved.
    - If the retention delay elapses first, keep the full scan and raise an admin alert.
  - Result calculation: use actual start + finish markers only (no scheduled-time fallback).
  - Public results ordering (best practice):
    - Rank by elapsed time including penalties.
    - Ties share rank.
    - Secondary sort for display: start time, then bib.
    - Non-finish statuses (dns/dnf/dsq/excluded/withdrawn) appear after ranked entries.
  - If a start/finish marker is missing, approval is blocked until the marker is added or the entry is set to DNS/DNF/DSQ/Excluded.
  - Timing precision: store milliseconds.
  - Default display precision: milliseconds (`.mmm`).
  - Rounding rule: round half-up to the configured precision for display; ranking uses actual (unrounded) time.
  - Elapsed-time format: `M:SS.mmm` (or `H:MM:SS.mmm` when ≥1h).
  - Scheduled-time format: `HH:mm` (24h).
  - Public results Delta: time behind leader, computed from unrounded times then rounded for display; format `+M:SS.mmm` (or `+H:MM:SS.mmm` when ≥1h).
  - Leader delta displays as `+0:00.000` (rounded) so the UI never shows a blank delta.
- Operator workflow: global queue across blocks (not necessarily draw order); marker→bib linking with quick correction; DNS batch warnings before bulk changes.
- Jury: investigations per entry; outcomes include no action, penalty seconds (configurable per regatta via penalty configuration), excluded, DSQ (entry); approvals gate.
  - Multiple investigations per entry allowed; closure is per investigation.
  - Penalty seconds are added to computed elapsed time for ranking and delta; raw timing data is retained for audit.
  - Penalty configuration (`defaultPenaltySeconds`, `allowCustomSeconds`) is set at the regatta level and used when assigning penalties via investigations.
  - "No action" closes the investigation; workflow: 1) Close investigation with outcome=no_action, 2) System checks if timing is complete, 3) If yes and no other open investigations, auto-approve entry, 4) If no, entry returns to pending_approval.
  - Tribunal escalation is modeled by re-opening an investigation.
  - Not all entries in a single investigation must receive penalties.
  - Best practice: regatta-wide DSQ is modeled as a bulk action applying DSQ (entry) to all affected entries, with per-entry audit events.
- Approvals/results: entry completion criteria, event approval gating (withdrawn entries excluded), DSQ as a canonical status with explicit revert event storing prior status in metadata; result labels provisional/edited/official.
  - Entry completion: finish time set OR status dns/dnf/dsq/excluded and not under investigation.
  - Entry approval is explicit (head_of_jury or regatta_admin):
    - When timing is complete and there are no open investigations, entry becomes “pending approval”.
    - Approving an entry marks it `approved/immutable` and locks linked markers.
  - Event cannot be approved unless every non-withdrawn entry is in approved/dns/dnf/dsq/excluded state.
  - withdrawn_before_draw entries are excluded from public schedule/results entirely; show only in staff views with filters/audit history.
  - withdrawn_after_draw entries remain visible on staff/public schedules and results with a withdrawn status label; they are excluded from rankings and approvals.
  - If a required start/finish marker is missing, approval is blocked unless the entry is set to DNS/DNF/DSQ/Excluded.
  - Result labels:
    - provisional: computed but not event-approved
    - edited: manual adjustment or penalty applied (still provisional until approval)
    - official: event approved
- Public: cacheable versioned paths with draw/results revision keys; /versions no-store; SSE ticks and snapshot.
  - Schedule/start order content depends only on draw_revision, even though cache keys include both revisions.
  - Revision bump rules (best practice):
    - draw_revision: draw publish + any schedule/start-order/bib display change
    - results_revision: marker/time edits, penalties, approvals, DNS/DNF/DSQ/excluded changes
    - if a single operation affects both schedule/start-order/bib display and result-affecting data, increment both together

### Status taxonomy
- Domain status values: active, withdrawn_before_draw, withdrawn_after_draw, dns, dnf, excluded, dsq.
  - `excluded` - entry was disqualified from the race (not the same as DSQ which is entry-level ban).
- Derived workflow/UI states: under_investigation, approved/immutable, offline_queued, provisional/edited/official.

### Non-functional
- Operator offline: queued actions; sync with explicit conflict policy.
  - Queue data structure: array of {action, timestamp, deviceId, metadata} objects.
  - Sync endpoint: POST /api/v1/regattas/{id}/operator/sync (accepts queued actions, returns conflicts).
  - Conflict resolution: last-write-wins for marker position/time adjustments and unlinking when entry is not approved; auto-accept link if entry has no linked marker at that station and marker is not linked elsewhere.
  - Manual resolution required: duplicate links (entry already linked to a different marker), marker linked to a different entry, or any edits against approved/immutable entries (reject and surface conflict).
  - Conflict resolution API: GET /api/v1/regattas/{id}/operator/conflicts returns pending conflicts with resolution options.
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
  - Print generation: async processing via background queue (for large regattas); PDF library: iText or similar.
  - Each page header includes regatta name, generated timestamp, draw/results revisions, and page number.
- Operator UX constraints:
  - Must remain usable on iPhone SE class devices.
  - Must support outdoor readability via high contrast and larger touch targets.
  - Default to high-contrast mode with a toggle back to standard; persist preference per device (LocalStorage).
  - Density toggle: persist per device via LocalStorage (compact/dense modes).
  - PIN/token flows must not interrupt active capture UI.

## Components and Interfaces
- Staff API: Auth0 JWT, regatta-scoped roles (+ super_admin).
  - Auth0 configuration:
    - Tenant: configured via environment variable
    - Audience: `https://api.regattadesk.com`
    - Role claim format: `https://regattadesk.com/roles` array in JWT token
    - Token refresh: automatic via Auth0 SDK; refresh token rotation enabled
  - Regatta roles: regatta_admin, head_of_jury, info_desk, financial_manager; super_admin is global.
  - Permissions matrix (best-practice defaults):
  
  | Action | regatta_admin | head_of_jury | info_desk | financial_manager | operator | super_admin |
  | --- | --- | --- | --- | --- | --- | --- |
  | Create/update regatta | Yes | No | No | No | No | Yes |
  | Create/update events | Yes | No | No | No | No | Yes |
  | Create/update blocks | Yes | No | No | No | No | Yes |
  | Manage bib pools | Yes | No | No | No | No | Yes |
  | Create/update entries | Yes | No | Yes | No | No | Yes |
  | Create/update crews/athletes | Yes | No | Yes | No | No | Yes |
  | Publish draw | Yes | No | No | No | No | Yes |
  | Approve entry | Yes | Yes | No | No | No | Yes |
  | Approve event | Yes | Yes | No | No | No | Yes |
  | Mark DNS | Yes | Yes | No | No | Yes (within scoped block) | Yes |
  | Mark DNF | Yes | Yes | No | No | No | Yes |
  | Mark withdrawn_before_draw | Yes | No | Yes | No | No | Yes |
  | Mark withdrawn_after_draw | Yes | Yes | Yes | No | No | Yes |
  | Mark paid/unpaid | Yes | No | No | Yes | No | Yes |
  | Create investigations | Yes | Yes | No | No | No | Yes |
  | Assign penalties | Yes | Yes | No | No | No | Yes |
  | Close investigations | Yes | Yes | No | No | No | Yes |
  | View audit logs | Yes | No | No | No | No | Yes |
  | Export printables/PDFs | Yes | No | No | No | No | Yes |
  | Manage operator tokens | Yes | No | No | No | No | Yes |
  | Revoke operator access | Yes | No | No | No | No | Yes |
- Operator API: QR token scoped to block(s), station, validity window, revocable; operators are accountless (QR/token only).
  - Station model: single active station per token; second device can request access without interrupting active station.
  - Handoff: new device shows a PIN; active station can reveal the matching PIN to complete handover.
  - After PIN handover, the previous device is demoted to read-only and must re-auth to regain control.
  - Admin can view PIN remotely only if the active station cannot access the PIN flow.
  - Token display must not interrupt capture UI (hidden unless opened intentionally).
  - QR tokens exportable to PDF with fallback instructions (short URL + token/PIN) if QR scan fails.
- Public:
  - POST /public/session (204) mints/refreshes anon HttpOnly JWT cookie (HS256; iss/aud; kid rotation).
    - If missing/invalid anon cookie: mint a new one.
    - If valid and within refresh window: refresh (new Set-Cookie).
    - If valid and outside refresh window: 204 with no Set-Cookie.
    - Sliding TTL 5 days; refresh window 20% of TTL.
    - Key rotation: two active keys; overlap ≥6 days.
    - Cookie attributes: HttpOnly; Secure; SameSite=Lax; Path=/; Max-Age=5d.
    - Idempotent; Cache-Control: private, no-store; mild abuse protection (no Origin/Referer checks).
    - CSRF: no token required (anonymous + idempotent); rely on SameSite=Lax.
    - JWT includes a stable client-id claim used for per-client SSE caps.
  - Bootstrap: call /versions first; if 401 missing/invalid then call /public/session and retry /versions once; then open SSE.
  - GET /public/regattas/{id}/versions returns {draw_revision, results_revision}, requires anon session cookie; 401 if missing/invalid; Cache-Control: no-store; rate-limited per client-id.
  - GET /public/regattas/{id}/events SSE: snapshot on connect + revision ticks; requires anon session cookie; 401 if missing/invalid.
  - SSE event types:
    - `snapshot` - initial state on connect (contains full regatta data)
    - `draw_revision` - draw-related changes (schedule, start order, bibs)
    - `results_revision` - results-related changes (times, penalties, approvals, status changes)
    - `investigation_created` - new investigation opened
    - `penalty_assigned` - penalty applied to entry
  - Multiplexed by event type (event: snapshot, draw_revision, results_revision).
    - Deterministic SSE id includes draw_revision + results_revision.
    - Per-client cap: 20 concurrent connections per client-id per regatta; reject excess with 429.
    - Reconnect: exponential backoff with full jitter; min 100ms, base 500ms, cap 20s; retry forever.
    - No per-IP concurrent cap or per-IP rate limiting until measured.
    - UI shows a minimal Live/Offline indicator based on SSE connection state only (no freshness claim).
  - GET /public/v{d}-{r}/... versioned pages/data cacheable.
    - Fully anonymous: no anon session cookie required; caches should ignore cookies for these endpoints.
    - Cache keys include draw_revision + results_revision; client soft-updates and replaces URL to latest version.
    - Schedule/start order content still only changes with draw_revision.
  - withdrawn_after_draw status changes bump both draw_revision and results_revision.

## Data Models (high level)
- event_store: aggregate streams with sequence numbers; payload + metadata.
- projections: public tables keyed by (regatta_id, draw_revision, results_revision) where appropriate.
- payments: per-entry and per-club payment status in projections; events for mark_paid/mark_unpaid.
- results_state: derived labels (provisional/edited/official) based on approvals and manual edits/penalties.
- line-scan storage: capture session + tile manifest + tiles in object storage; markers reference tile coords and computed time, and store timestamp + capture device id.

## Staff API Endpoints

### API Naming Convention
All API endpoints use plural for collection resources:
- Root-level collections: `/api/v1/categories`, `/api/v1/boat-types`, `/api/v1/athletes`, `/api/v1/clubs`
- Regatta-scoped: `/api/v1/regattas/{id}/events`, `/api/v1/regattas/{id}/event-groups`, `/api/v1/regattas/{id}/investigations`

All collection resources use plural naming.

### Authentication
All endpoints require Auth0 JWT in `Authorization: Bearer <token>` header with regatta-scoped role claims.

### Regatta Management
| Method | Path | Description | Auth |
|--------|------|-------------|------|
| GET | /api/v1/regattas | List regattas (paginated) | super_admin |
| POST | /api/v1/regattas | Create regatta | super_admin |
| GET | /api/v1/regattas/{id} | Get regatta details | regatta-scoped roles |
| PUT | /api/v1/regattas/{id} | Update regatta | regatta_admin, super_admin |
| DELETE | /api/v1/regattas/{id} | Delete regatta (if not published) | regatta_admin, super_admin |
| POST | /api/v1/regattas/{id}/archive | Archive regatta | regatta_admin, super_admin |
| POST | /api/v1/regattas/{id}/publish-draw | Publish draw | regatta_admin, super_admin |
| GET/PUT | /api/v1/regattas/{id}/config/entry-fee | Get/Update entry fee configuration | regatta_admin, super_admin |
Response/request schema: `{amount: number, currency: string}`

### Event Management
| Method | Path | Description | Auth |
|--------|------|-------------|------|
| GET | /api/v1/regattas/{id}/events | List events | regatta-scoped roles |
| POST | /api/v1/regattas/{id}/events | Create event | regatta_admin, super_admin |
| GET | /api/v1/regattas/{id}/events/{eventId} | Get event details | regatta-scoped roles |
| PUT | /api/v1/regattas/{id}/events/{eventId} | Update event | regatta_admin, super_admin |
| DELETE | /api/v1/regattas/{id}/events/{eventId} | Delete event (if draw not published) | regatta_admin, super_admin |
| GET | /api/v1/regattas/{id}/event-groups | List event groups | regatta-scoped roles |
| POST | /api/v1/regattas/{id}/event-groups | Create event group | regatta_admin, super_admin |
| GET | /api/v1/regattas/{id}/event-groups/{groupId} | Get event group details | regatta-scoped roles |
| PUT | /api/v1/regattas/{id}/event-groups/{groupId} | Update event group | regatta_admin, super_admin |
| DELETE | /api/v1/regattas/{id}/event-groups/{groupId} | Delete event group | regatta_admin, super_admin |

### Category and Boat Type Management
| Method | Path | Description | Auth |
|--------|------|-------------|------|
| GET | /api/v1/categories | List categories | regatta-scoped roles |
| POST | /api/v1/categories | Create category | super_admin |
| GET | /api/v1/boat-types | List boat types | regatta-scoped roles |
| POST | /api/v1/boat-types | Create boat type | super_admin |

### Ruleset Management
| Method | Path | Description | Auth |
|--------|------|-------------|------|
| GET | /api/v1/rulesets | List global rulesets | regatta-scoped roles |
| POST | /api/v1/rulesets | Create ruleset | super_admin |
| PUT | /api/v1/rulesets/{id} | Update ruleset | super_admin |
| POST | /api/v1/regattas/{id}/rulesets | Create regatta-specific ruleset | regatta_admin, super_admin |
| POST | /api/v1/regattas/{id}/rulesets/{rulesetId}/promote | Promote ruleset to global | super_admin |

### Block Management
| Method | Path | Description | Auth |
|--------|------|-------------|------|
| GET | /api/v1/regattas/{id}/blocks | List blocks | regatta-scoped roles |
| POST | /api/v1/regattas/{id}/blocks | Create block | regatta_admin, super_admin |
| GET | /api/v1/regattas/{id}/blocks/{blockId} | Get block details | regatta-scoped roles |
| PUT | /api/v1/regattas/{id}/blocks/{blockId} | Update block | regatta_admin, super_admin |
| DELETE | /api/v1/regattas/{id}/blocks/{blockId} | Delete block (if draw not published) | regatta_admin, super_admin |
| GET | /api/v1/regattas/{id}/blocks/{blockId}/bib-pools | List bib pools for block | regatta-scoped roles |
| POST | /api/v1/regattas/{id}/blocks/{blockId}/bib-pools | Create bib pool | regatta_admin, super_admin |
| PUT | /api/v1/regattas/{id}/blocks/{blockId}/bib-pools/{poolId} | Update bib pool | regatta_admin, super_admin |
| DELETE | /api/v1/regattas/{id}/blocks/{blockId}/bib-pools/{poolId} | Delete bib pool (only if draw not published) | regatta_admin, super_admin |

### Entry/Crew/Athlete Management
| Method | Path | Description | Auth |
|--------|------|-------------|------|
| GET | /api/v1/regattas/{id}/entries | List entries (paginated, filterable) | regatta-scoped roles |
| POST | /api/v1/regattas/{id}/entries | Create entry | regatta_admin, info_desk, super_admin |
| GET | /api/v1/regattas/{id}/entries/{entryId} | Get entry details | regatta-scoped roles |
| PUT | /api/v1/regattas/{id}/entries/{entryId} | Update entry | regatta_admin, info_desk, super_admin |
| DELETE | /api/v1/regattas/{id}/entries/{entryId} | Delete entry (if not approved) | regatta_admin, info_desk, super_admin |
| POST | /api/v1/regattas/{id}/entries/{entryId}/withdraw-before-draw | Mark withdrawn_before_draw | regatta_admin, info_desk, super_admin |
| POST | /api/v1/regattas/{id}/entries/{entryId}/withdraw-after-draw | Mark withdrawn_after_draw | regatta_admin, head_of_jury, info_desk, super_admin |
| POST | /api/v1/regattas/{id}/entries/{entryId}/status | Update status (DNS/DNF/DSQ/Excluded) | regatta_admin, head_of_jury, operator (DNS only) |
| POST | /api/v1/regattas/{id}/entries/{entryId}/approve | Approve entry | regatta_admin, head_of_jury |
| GET | /api/v1/crews | Search crews (reusable across regattas) | regatta-scoped roles |
| POST | /api/v1/crews | Create crew | regatta_admin, info_desk, super_admin |
| GET | /api/v1/crews/{crewId} | Get crew details | regatta-scoped roles |
| PUT | /api/v1/crews/{crewId} | Update crew | regatta_admin, info_desk, super_admin |
| GET | /api/v1/athletes | Search athletes | regatta-scoped roles |
| POST | /api/v1/athletes | Create athlete | regatta_admin, info_desk, super_admin |
| GET | /api/v1/athletes/{athleteId} | Get athlete details | regatta-scoped roles |
| PUT | /api/v1/athletes/{athleteId} | Update athlete | regatta_admin, info_desk, super_admin |

### Draw Management
| Method | Path | Description | Auth |
|--------|------|-------------|------|
| GET | /api/v1/regattas/{id}/draw | Get current draw state | regatta-scoped roles |
| POST | /api/v1/regattas/{id}/draw/generate | Generate random draw | regatta_admin, super_admin |
| POST | /api/v1/regattas/{id}/draw/publish | Publish draw (bumps draw_revision) | regatta_admin, super_admin |
| GET | /api/v1/regattas/{id}/draw/seed | Get draw seed (for reproducibility) | regatta-scoped roles |

### Timing/Markers
| Method | Path | Description | Auth |
|--------|------|-------------|------|
| GET | /api/v1/regattas/{id}/markers | List markers (filterable by blockId, entryId, status, captureSessionId) | regatta-scoped roles |
| POST | /api/v1/regattas/{id}/markers | Create marker | operator, regatta_admin, super_admin |
| GET | /api/v1/regattas/{id}/markers/{markerId} | Get marker details | regatta-scoped roles |
| PUT | /api/v1/regattas/{id}/markers/{markerId} | Update marker position/time | operator, regatta_admin, super_admin |
| DELETE | /api/v1/regattas/{id}/markers/{markerId} | Delete marker (pre-approval only) | operator, regatta_admin, super_admin |
| POST | /api/v1/regattas/{id}/markers/{markerId}/link | Link marker to entry | operator, regatta_admin, super_admin |
| POST | /api/v1/regattas/{id}/markers/{markerId}/unlink | Unlink marker from entry | operator, regatta_admin, super_admin |
| GET | /api/v1/regattas/{id}/tiles/manifest | Get tile manifest for capture session | regatta-scoped roles |
| GET | /api/v1/regattas/{id}/tiles/{tileId} | Retrieve tile image | regatta-scoped roles |
| POST | /api/v1/regattas/{id}/capture-sessions/{sessionId}/tiles | Upload tile image | operator, regatta_admin, super_admin |
| POST | /api/v1/regattas/{id}/capture-sessions/{sessionId}/manifest | Upload tile manifest | operator, regatta_admin, super_admin |

### Investigations
| Method | Path | Description | Auth |
|--------|------|-------------|------|
| GET | /api/v1/regattas/{id}/investigations | List investigations | regatta-scoped roles |
Response schema: `{investigations: [{id, entryId, status, createdAt, createdBy, ...}]}`
| POST | /api/v1/regattas/{id}/investigations | Create investigation | regatta_admin, head_of_jury, super_admin |
| GET | /api/v1/regattas/{id}/investigations/{invId} | Get investigation details | regatta-scoped roles |
| GET | /api/v1/regattas/{id}/investigations/{invId}/penalties | List penalties for investigation | regatta-scoped roles |
| POST | /api/v1/regattas/{id}/investigations/{invId}/close | Close investigation | regatta_admin, head_of_jury, super_admin |
| POST | /api/v1/regattas/{id}/investigations/{invId}/reopen | Reopen investigation (tribunal escalation) | regatta_admin, head_of_jury, super_admin |
| POST | /api/v1/regattas/{id}/investigations/{invId}/penalties | Assign penalty to entry | regatta_admin, head_of_jury, super_admin |
| DELETE | /api/v1/regattas/{id}/investigations/{invId}/penalties/{entryId} | Remove penalty | regatta_admin, head_of_jury, super_admin |

### Finance
| Method | Path | Description | Auth |
|--------|------|-------------|------|
| GET | /api/v1/regattas/{id}/payments | List payment statuses | regatta-scoped roles |
| POST | /api/v1/regattas/{id}/entries/{entryId}/mark-paid | Mark entry paid | regatta_admin, financial_manager, super_admin |
| POST | /api/v1/regattas/{id}/entries/{entryId}/mark-unpaid | Mark entry unpaid | regatta_admin, financial_manager, super_admin |
| POST | /api/v1/regattas/{id}/clubs/{clubId}/mark-paid | Mark all club entries paid | regatta_admin, financial_manager, super_admin |
| POST | /api/v1/regattas/{id}/clubs/{clubId}/mark-unpaid | Mark all club entries unpaid | regatta_admin, financial_manager, super_admin |

### Club Management
| Method | Path | Description | Auth |
|--------|------|-------------|------|
| GET | /api/v1/clubs | List clubs (paginated) | regatta-scoped roles |
| POST | /api/v1/clubs | Create club | super_admin |
| GET | /api/v1/clubs/{clubId} | Get club details | regatta-scoped roles |
| PUT | /api/v1/clubs/{clubId} | Update club | super_admin |
| DELETE | /api/v1/clubs/{clubId} | Delete club (only if no entries) | super_admin |

### Event Approvals
| Method | Path | Description | Auth |
|--------|------|-------------|------|
| POST | /api/v1/regattas/{id}/events/{eventId}/approve | Approve event (all entries approved) | regatta_admin, head_of_jury |
| GET | /api/v1/regattas/{id}/events/{eventId}/approval-status | Get event approval status | regatta-scoped roles |

### Penalty Configuration
| Method | Path | Description | Auth |
|--------|------|-------------|------|
| GET | /api/v1/regattas/{id}/penalty-config | Get penalty configuration | regatta-scoped roles |
| PUT | /api/v1/regattas/{id}/penalty-config | Update penalty configuration | regatta_admin, super_admin |
Response schema: `{defaultPenaltySeconds: number, allowCustomSeconds: boolean}`

### Operator Tokens
| Method | Path | Description | Auth |
|--------|------|-------------|------|
| GET | /api/v1/regattas/{id}/operator-tokens | List operator tokens | regatta_admin, super_admin |
| POST | /api/v1/regattas/{id}/operator-tokens | Create operator token | regatta_admin, super_admin |
Request body: `{blockIds: [], stationId: string, validityHours: number}`
| GET | /api/v1/regattas/{id}/operator-tokens/{tokenId}/config | Get token config (validity window) | regatta_admin, super_admin |
| PUT | /api/v1/regattas/{id}/operator-tokens/{tokenId}/config | Update token config | regatta_admin, super_admin |
| DELETE | /api/v1/regattas/{id}/operator-tokens/{tokenId} | Revoke operator token | regatta_admin, super_admin |
| POST | /api/v1/regattas/{id}/operator-tokens/{tokenId}/export-pdf | Export token to PDF | regatta_admin, super_admin |

### Audit/Export
| Method | Path | Description | Auth |
|--------|------|-------------|------|
| GET | /api/v1/regattas/{id}/audit-log | Query audit events | regatta_admin, super_admin |
Query parameters: `entityType`, `entityId`, `actorId`, `action`, `fromDate`, `toDate`, `limit`, `offset`
| GET | /api/v1/regattas/{id}/export/results | Export results (CSV/JSON: delimiter `,`, LF line endings, UTF-8 encoding; JSON schema includes all entry fields with penalties applied) | regatta-scoped roles |
| GET | /api/v1/regattas/{id}/export/printables | Generate printable PDFs (async for large regattas; monochrome-friendly A4, header with regatta name, revision, timestamp, page number) | regatta_admin, super_admin |
Async job endpoint: `POST /api/v1/regattas/{id}/export/printables` returns `{jobId: string}`, status polling via `GET /api/v1/jobs/{jobId}`

## Error Handling
- Structured error responses `{code, message, details}`.
- Standard error codes:
  - `ANON_SESSION_MISSING` (401) - Anonymous session cookie missing
  - `ANON_SESSION_INVALID` (401) - Anonymous session cookie invalid/expired
  - `CONFLICT` (409) - Stale version or conflicting state
  - `PERMISSION_DENIED` (403) - Insufficient permissions
  - `VALIDATION_ERROR` (400) - Request validation failed
  - `NOT_FOUND` (404) - Resource not found
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
