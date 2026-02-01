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
- Finance: payment status per entry or per club; bulk mark paid/unpaid
- API-first for all operations; staff/operator/public UIs consume the same API surface (imports can be external tools later)
- Event sourcing for auditability and reversibility

### Out of scope (v0.1)
- Side-by-side racing, multi-distance, handicap/conversion factors (beyond “winner time” ranking)
- OCR bib recognition (future, keep extension points)
- Built-in CSV import UI (API-only; import tools later)

## 2) Domain concepts

- Club: id, name, short_name (used for display and billing rollups).
- Athlete: full name, DOB, gender, club, optional federation id
- Crew: display name; explicit club assignment when provided, otherwise derived from athletes; list of athletes + seat order; reusable across regattas.
  - Derived club rule: if all athletes share the same club, use that club; otherwise mark crew as composite/multi-club.
- Entry: participation of a crew in a regatta+event; includes bib, start position, status
- Event: class/category/boat type; may be grouped into overarching classes per regatta
- Block: operational unit with multiple classes/events; has schedule start and start intervals:
  - interval between crews
  - interval between classes
- Bib pools:
  - blocks can have multiple pools
  - regatta has a single overflow pool; all blocks may borrow from it
- Payments (best practice):
  - entry-level status is the source of truth
  - club “paid/unpaid” is a bulk action that updates entries and emits audit events
  - club status is derived from current entry statuses
  - billing club source of truth: entry.billing_club_id when set; otherwise crew’s club (single-club crews only)
  - composite/multi-club crews require explicit billing_club_id (and remain labeled as composite for reporting)
- Status values: active, withdrawn_before_draw, withdrawn_after_draw, dns, dnf, excluded, dsq
- Derived workflow/UI states (not primary status values): under_investigation, approved/immutable, offline_queued

## 3) Draw + schedule
- Draw order random for v0.1; architecture allows future algorithms
- Store the random draw seed for reproducibility
- Classes start sequentially, but finishes can interleave
- No insertion after draw in v0.1
- Start time display config:
  - per-entry start display is supported
  - display can be configured per regatta (some show block times only)
- Regatta end definition (for retention windows): use explicit regatta_end_at timestamp when set; otherwise compute from the latest block’s scheduled end time (see schedule formula below).
- Scheduled start time (deterministic):
  - per-entry scheduled_start = block_start + (class_index * class_interval) + (crew_index_within_class * crew_interval)
  - indices are zero-based in draw order; class order is the block’s class sequence, crew order is the class draw order
- Block scheduled end time (best practice): block_end = last_scheduled_start + crew_interval.
  - Equivalent formula for final crew: block_end = block_start + (class_index * class_interval) + (crew_index_within_class * crew_interval) + crew_interval

## 4) Bib assignment + collisions
- Bibs are regatta-wide unique (a bib number can be assigned to only one entry across all blocks).
- Default: assign from smallest or largest bib; configurable
- Missing bib replacement: overwrite only that entry’s bib, no ripple effects
- Pool priority order (allocation): use the entry’s block primary pool first, then additional block pools in configured order, then overflow pool.
- Collision resolution rule:
  - when an entry’s bib is changed and collides, the changing entry gets next available bib
  - next available bib = first unused number in the current pool when scanning in the configured direction (smallest-to-largest or largest-to-smallest)
  - if the current pool is exhausted, continue to the next pool in priority order (ending with overflow)
  - even if bib sequence becomes non-consecutive
  - allow creation/usage of overflow pool (bounded by physical inventory)

## 5) Timing + camera UI
- Start and finish both use line-scan camera interface:
  - overview image + draggable detail window for fine adjustment
  - markers can be created/moved/deleted in both views
  - selecting an unlinked marker moves detail view to that marker
- Camera metadata:
  - know when recording started and fps; compute time from frame offset
  - recording start time sourced via device time sync at session start (server handshake/NTP); store server_time_at_start plus device monotonic offset
  - drift handling: periodic resync; apply offset correction; if drift exceeds threshold, flag session for review
  - fallback: if device time is invalid or sync fails, mark the capture session as unsynced and require manual timestamp correction before approvals (server-received timestamps are provisional only)
- store full line scan during regatta; after configured delay (default 14 days after regatta end) keep only ±2s around approved markers
- Markers:
  - contain timestamp, capture device, image tile reference
  - unassigned markers are not audited
  - assigned markers become immutable once the associated entry timing is approved
- Result calculation:
  - use actual start + finish markers only (no scheduled-time fallback)
- if a start/finish marker is missing, block approval until the marker is added or the entry is set to DNS/DNF/DSQ/Excluded
- Timing precision:
  - store milliseconds
  - display rounds to configured precision; ranking uses actual (unrounded) time
  - Public results Delta: time behind leader, computed from unrounded times then rounded for display; format `+M:SS.mmm` (or `+H:MM:SS.mmm` when >=1h)
- Timing storage (best practice):
  - store timestamps in UTC (Instant)
  - display in regatta-local time zone

## 6) Operations: start/finish flow
- Operators work in blocks, on a global queue (not necessarily draw order)
- After marker creation, operator can link marker to bib (quickly correctable)
- Operator station model:
  - single active station per token
  - second device can request access without interrupting active station
  - new device shows a PIN; active station can reveal the matching PIN to complete handover
  - admin can view PIN remotely only if the active station cannot access the PIN flow
  - token display must not interrupt the active station UI (hidden unless opened intentionally)
- Offline conflict policy (operator queue sync):
  - Last-write-wins: marker position/time adjustments and unlinking when entry is not approved.
  - Auto-accept link if entry has no linked marker at that station and marker is not linked elsewhere.
  - Manual resolution required: duplicate links (entry already linked to a different marker), marker linked to a different entry, or any edits against approved/immutable entries (reject and surface conflict).

## 7) Protests / investigations / penalties
- Investigation is per result/entry
- Outcomes: no action, penalty (seconds configurable per regatta), exclusion (race), DSQ (regatta)
- One penalty per investigation; multiple investigations allowed; not all entries in an investigation get penalties
- Penalty seconds are added to computed elapsed time for ranking and delta; raw timing data is retained for audit.
- Investigation closure is per investigation (not bulk)
- Closing an investigation with “no action” by an authorized role auto-approves the entry if timing is complete and no other investigations are open; otherwise it returns to “pending approval”.
- Rare tribunal escalation represented by re-opening

## 8) Approvals + state transitions
- Entry timing can be “complete” if finish time set OR marked dns/dnf/dsq/excluded and not under investigation
- Entry approval is explicit (head_of_jury or regatta_admin):
  - When timing is complete and there are no open investigations, entry becomes “pending approval”.
  - Approving an entry marks it `approved/immutable` and locks linked markers.
- Withdrawn entries are excluded from approval gating.
- withdrawn_before_draw entries are excluded from public schedule/results entirely; show only in staff views with filters/audit history.
- withdrawn_after_draw entries remain visible on staff/public schedules and results with a withdrawn status label; they are excluded from rankings and approvals.
- Event cannot be approved if any non-withdrawn entry is not in approved/dns/dnf/dsq/excluded state
- If a required start/finish marker is missing, approval is blocked unless the entry is set to DNS/DNF/DSQ/Excluded.
- Operators can mark/unmark DNS; warning lists impacted entries before batch operations
- DSQ is a canonical entry status (no separate flag)
- Reverting DSQ is an explicit status-change event that restores the prior status; store prior status in event metadata for easy revert/audit
- Result labels for UI:
  - provisional: computed but not event-approved
  - edited: manual adjustment or penalty applied (still provisional until approval)
  - official: event approved

## 9) Authn/Authz and roles
- Staff auth: Auth0; one account per person
- Per-regatta roles:
  - regatta_admin: full access within regatta
  - head_of_jury: approve, close investigations, assign penalties
  - info_desk: crew mutations, missing/changed bibs, withdrawals
  - financial_manager: mark paid per entry or per club
  - super_admin: all regattas; manage global defaults (e.g., rulesets)
- Competitors do not need accounts
- Operators: QR token links, scoped to block(s) + station; configurable validity window; revocable; export to PDF with fallback instructions
- Operators are accountless (no personal accounts); access is strictly via QR/token links
- Permissions matrix (best-practice defaults):

| Action | regatta_admin | head_of_jury | info_desk | operator |
| --- | --- | --- | --- | --- |
| Publish draw | Yes | No | No | No |
| Approve entry | Yes | Yes | No | No |
| Approve event | Yes | Yes | No | No |
| Mark DNS | Yes | Yes | No | Yes (within scoped block) |
| Mark DNF | Yes | Yes | No | No |
| Mark withdrawn_before_draw | Yes | No | Yes | No |
| Mark withdrawn_after_draw | Yes | Yes | Yes | No |

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
- Versioned public endpoints (`/public/v{draw_revision}-{results_revision}/...`) are fully anonymous and cacheable (no anon session cookie required; caches should ignore cookies).
- Revision model:
  - schedule/start order content depends on draw_revision
  - results_revision tracks results changes
  - cache keys for all public pages include draw_revision + results_revision
  - withdrawn_after_draw status changes bump both draw_revision and results_revision
- Revision bump rules (best practice):
  - draw_revision: draw publish + any schedule/start-order/bib display change
  - results_revision: marker/time edits, penalties, approvals, DNS/DNF/DSQ/exclusion changes
  - if a single operation affects both schedule/start-order/bib display and result-affecting data, increment both together
- Cache busting:
  - path versioning: /public/v{draw_revision}-{results_revision}/...
  - client soft-updates and history.replaceState to latest versioned path
- Versions endpoint:
  - requires anon session cookie; 401 if missing/invalid
  - rate-limited (per client-id); Cache-Control: no-store
  - GET /public/regattas/{id}/versions -> {draw_revision, results_revision}
- Live updates:
  - SSE per regatta, multiplexed event types
  - snapshot event emitted on connect
  - requires anon session cookie; 401 if missing/invalid
  - deterministic SSE id includes both revisions
  - reconnect: exp backoff + full jitter; min 100ms, base 500ms, cap 20s; retry forever
  - UI: minimal Live/Offline indicator (SSE state only)

## 12) Public anonymous session (for per-client limits)
- POST /public/session mints/refreshes anon session cookie:
  - if missing/invalid anon cookie, mint a new one
  - if valid and within refresh window, refresh (new Set-Cookie)
  - otherwise 204 with no Set-Cookie
- Cookie is signed JWT (HS256) with iss/aud; key rotation with kid; two active keys; overlap ≥6 days
- Cookie attributes: HttpOnly; Secure; SameSite=Lax; Path=/; Max-Age=5d
- Sliding TTL 5 days; refresh window 20% of TTL; no Origin/Referer checks
- /public/session:
  - 204 No Content
  - idempotent; refresh only when needed
  - mild Cloudflare abuse protection; no Origin/Referer checks
  - used only for /public/regattas/{id}/versions and SSE (versioned public pages remain anonymous)
  - CSRF: no token required (anonymous + idempotent); rely on SameSite=Lax
  - Cache-Control: private, no-store
- JWT includes a stable client-id claim used for per-client SSE caps
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
- Containerized deployment + automated pipeline (CI/CD)

## 14) UX, accessibility, i18n, and printing
- Three UX surfaces:
  - Staff Web (authenticated, desktop-first)
  - Operator PWA (token/QR, offline-capable; outdoor readable; minimum phone size iPhone SE class)
  - Public Web (anonymous, high-traffic, cacheable, live-updating)
- Accessibility targets:
  - Public: WCAG 2.2 AA minimum for all flows; aim for WCAG 2.2 AAA where feasible (especially schedule/results).
  - Staff: no hard requirement, but avoid obviously inaccessible patterns (focus visibility, contrast, touch targets).
  - Operator: prioritize outdoor readability and large touch targets; default to high-contrast mode with a toggle back to standard; persist per-device.
- Internationalization and formatting:
  - Initial locales: Dutch (`nl`) and English (`en`).
  - Time: 24h.
  - Dates: `dd-MM-yyyy`.
  - Time zone: regatta-local (future may add viewer-local toggle).
- Density:
  - Default comfortable density; provide compact/dense toggle (especially for staff tables).
- Printing:
  - Admin generates printables (A4); assume mostly monochrome printers.
  - Each printed page must include regatta name, generated timestamp, draw/results revisions, and page number.
- Style guide:
  - Visual direction for v0.1 is “Calm Instrument”.
  - Canonical design tokens and component/page patterns are defined in `design/style-guide.md`.
