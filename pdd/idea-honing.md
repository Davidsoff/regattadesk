Version: v2 (2026-02-01)

# Consolidated requirements + decisions (from Q&A)

This document consolidates the decisions made during requirements clarification into an implementable spec reference.

## 1) Scope

### In scope (v0.1)
- Head races, single distance only
- Entries, events, blocks, draw, bib assignment, start/finish timing, results publishing
- Jury investigations, penalties, exclusions, DSQ, approvals
- Public read-only site (high cacheability) with live updates
- Staff UI (Auth0) + operator UI (QR/token, offline-capable)
- Event sourcing for auditability and reversibility

### Out of scope (v0.1)
- Side-by-side racing, multi-distance, handicap/conversion factors (beyond “winner time” ranking)
- OCR bib recognition (future, keep extension points)
- Built-in CSV import UI (API-only; import tools later)

## 2) Domain concepts

- Athlete: full name, DOB, gender, club, optional federation id
- Crew: display name; derived club; list of athletes + seat order
- Entry: participation of a crew in a regatta+event; includes bib, start position, status
- Event: class/category/boat type; may be grouped into overarching classes per regatta
- Block: operational unit with multiple classes/events; has schedule start and start intervals:
  - interval between crews
  - interval between classes
- Bib pools:
  - blocks can have multiple pools
  - regatta has a single overflow pool; all blocks may borrow from it
- Status values: active, withdrawn before draw, withdrawn after draw, dns, dnf, excluded, dsq

## 3) Draw + schedule
- Draw order random for v0.1; architecture allows future algorithms
- Classes start sequentially, but finishes can interleave
- No insertion after draw in v0.1
- Start time display config:
  - per-entry start display is supported
  - display can be configured per regatta (some show block times only)

## 4) Bib assignment + collisions
- Default: assign from smallest or largest bib; configurable
- Missing bib replacement: overwrite only that entry’s bib, no ripple effects
- Collision resolution rule:
  - when an entry’s bib is changed and collides, the changing entry gets next available bib
  - even if bib sequence becomes non-consecutive
  - allow creation/usage of overflow pool (bounded by physical inventory)

## 5) Timing + camera UI
- Start and finish both use line-scan camera interface:
  - overview image + draggable detail window for fine adjustment
  - markers can be created/moved/deleted in both views
  - selecting an unlinked marker moves detail view to that marker
- Camera metadata:
  - know when recording started and fps; compute time from frame offset
  - store full line scan during regatta; after configured delay keep only ±2s around each marker
- Markers:
  - contain timestamp, capture device, image tile reference
  - unassigned markers are not audited
  - assigned markers become immutable once the associated entry timing is approved

## 6) Operations: start/finish flow
- Operators work in blocks, on a global queue (not necessarily draw order)
- After marker creation, operator can link marker to bib (quickly correctable)
- Operator station model:
  - single active station per token
  - second device can request access without interrupting active station
  - new device shows a PIN; admin can view PIN remotely to communicate to operator
  - token display must not interrupt the active station UI (hidden unless opened intentionally)

## 7) Protests / investigations / penalties
- Investigation is per result/entry
- Outcomes: no action, penalty (seconds configurable per regatta), exclusion (race), DSQ (regatta)
- One penalty per investigation; multiple investigations allowed; not all entries in an investigation get penalties
- Investigation closure is per investigation (not bulk)
- If an investigation ends with “no action”, the result is considered approved “as is”
- Rare tribunal escalation represented by re-opening

## 8) Approvals + state transitions
- Entry timing can be “complete” if finish time set OR marked dns/dnf/dsq/excl and not under investigation
- Event cannot be approved if any entry is not in approved/dns/dnf/dsq/excl state
- Operators can mark/unmark DNS; warning lists impacted entries before batch operations
- DSQ implemented via is_dsq flag for easy revert
- Reverting DSQ returns to prior state (typically approved)

## 9) Authn/Authz and roles
- Staff auth: Auth0; one account per person
- Per-regatta roles:
  - regatta_admin: full access within regatta
  - head_of_jury: approve, close investigations, assign penalties
  - info_desk: crew mutations, missing/changed bibs, withdrawals
  - financial_manager: mark paid per entry or per club
  - super_admin: all regattas; manage global defaults (e.g., rulesets)
- Competitors do not need accounts
- Operators: QR token links, scoped to block(s); configurable validity window; revocable; export to PDF with fallback instructions

## 10) Rulesets (v0.1 minimal, extensible)
- Rulesets are versioned, can be duplicated and updated
- Global rulesets become immutable once linked to a regatta with a published draw
- Regatta cannot change ruleset after draw is published
- Promotion/publishing of regatta-owned rulesets to global selection is super_admin only
- v0.1 basic rule verification:
  - gender compatibility
  - min/max age constraints
  - age calculation: configurable per regatta (actual age at start vs age as of Jan 1)

## 11) Public site caching + live updates
- Public pages cacheable; results auto-update
- Revision model:
  - draw_revision affects schedule/start order pages
  - results pages cache key includes draw_revision + results_revision
  - entry/crew list cache key includes draw_revision + results_revision
- Cache busting:
  - path versioning: /public/v{draw_revision}-{results_revision}/...
  - client soft-updates and history.replaceState to latest versioned path
- Versions endpoint:
  - unauthenticated, rate-limited, Cache-Control: no-store
  - GET /public/regattas/{id}/versions -> {draw_revision, results_revision}
- Live updates:
  - SSE per regatta, multiplexed event types
  - snapshot event emitted on connect
  - deterministic SSE id includes both revisions
  - reconnect: exp backoff + full jitter; min 100ms, base 500ms, cap 20s; retry forever
  - UI: minimal Live/Offline indicator (SSE state only)

## 12) Public anonymous session (for per-client limits)
- POST /public/session mints/refreshes anon session cookie
- Cookie is signed JWT (HS256) with iss/aud; key rotation with kid; two active keys; overlap ≥6 days
- Sliding TTL 5 days; refresh window 20% of TTL; refresh on consistent-origin calls
- /public/session:
  - 204 No Content
  - idempotent; refresh only when needed
  - mild Cloudflare abuse protection; no Origin/Referer checks
  - Cache-Control: private, no-store
- Bootstrap:
  - call /versions first
  - if 401 missing/invalid: call /public/session then retry /versions once
  - then open SSE
- SSE per-client cap:
  - 20 concurrent connections per client-id per regatta; reject new with 429
  - no per-IP concurrent cap; no per-IP rate limiting until measured
  - metrics added; avoid client-id labels

## 13) Observability + ops
- Health endpoint + OpenTelemetry endpoint
- Audit logs retained indefinitely (v0.1)
- Event sourcing chosen for auditability and easy correction of mistakes

## 14) UX, accessibility, i18n, and printing
- Three UX surfaces:
  - Staff Web (authenticated, desktop-first)
  - Operator PWA (token/QR, offline-capable; outdoor readable; minimum phone size iPhone SE class)
  - Public Web (anonymous, high-traffic, cacheable, live-updating)
- Accessibility targets:
  - Public: WCAG 2.2 AA minimum for all flows; aim for WCAG 2.2 AAA where feasible (especially schedule/results).
  - Staff: no hard requirement, but avoid obviously inaccessible patterns (focus visibility, contrast, touch targets).
  - Operator: prioritize outdoor readability and large touch targets; expose a high-contrast mode.
- Internationalization and formatting:
  - Initial locales: Dutch (`nl`) and English (`en`).
  - Time: 24h.
  - Dates: `dd-MM-yyyy`.
  - Time zone: regatta-local (future may add viewer-local toggle).
- Density:
  - Default comfortable density; provide compact/dense toggle (especially for staff tables).
- Printing:
  - Admin generates printables (A4); assume mostly monochrome printers.
  - Each printed page must include regatta name, generated timestamp, and draw/results revisions.
- Style guide:
  - Visual direction for v0.1 is “Calm Instrument”.
  - Canonical design tokens and component/page patterns are defined in `design/style-guide.md`.
