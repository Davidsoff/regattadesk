# PDD Review - Actionable Items

**Review Date:** 2026-02-02
**Reviewer:** Documentation Review
**Scope:** All files in `pdd/` folder
**Previous Review:** All 20 items from commit 2337b30 have been executed

---

## Executive Summary

This review identified **26 actionable items** across three categories:
- **Inconsistencies:** 5 items (contradictions or duplicates within documentation)
- **Missing API Endpoints:** 8 items (endpoints that should exist but aren't documented)
- **Missing Documentation/Details:** 13 items (gaps in specifications, workflows, or configurations)

---

## 1. INCONSISTENCIES

### INC-001: Duplicate line in detailed-design.md
**What:** Lines 48-49 both state "Entry-level status is the source of truth."
**Where:** `pdd/design/detailed-design.md:48-49`
**Expected Outcome:** Remove duplicate line; keep only one instance

### INC-002: SSE event types mismatch between documents
**What:** `idea-honing.md` documents only `snapshot`, `draw_revision`, `results_revision` events, while `detailed-design.md:257-260` adds `investigation_created` and `penalty_assigned`
**Where:** `pdd/idea-honing.md:236` vs `pdd/design/detailed-design.md:257-260`
**Expected Outcome:** Update `idea-honing.md` to include all SSE event types or document that `investigation_created` and `penalty_assigned` are deferred to post-v0.1

### INC-003: Penalty Configuration vs Investigation Penalties
**What:** `detailed-design.md` has separate "Penalty Configuration" section (lines 408-412) and investigation penalties (lines 390-391). The relationship is unclear—are penalty seconds configurable globally per-regatta or per-investigation?
**Where:** `pdd/design/detailed-design.md:408-412` and `pdd/design/detailed-design.md:390-391`
**Expected Outcome:** Clarify that penalty_seconds is configured at regatta level and used when assigning penalties via investigations

### INC-004: API path naming inconsistency
**What:** Endpoints use inconsistent patterns:
- `/api/v1/categories` (plural)
- `/api/v1/boat-types` (plural)
- `/api/v1/regattas/{id}/events` (regatta-scoped, plural)
- `/api/v1/regattas/{id}/event-groups` (regatta-scoped, plural)
- `/api/v1/athletes` (root-level, plural)
**Where:** `pdd/design/detailed-design.md:284-430`
**Expected Outcome:** Establish and document a consistent naming convention (recommend: always use plural for collections)

### INC-005: Token creation endpoint missing scope/block parameters
**What:** `detailed-design.md:417-422` documents operator token endpoints but doesn't specify how to define which blocks/stations a token can access
**Where:** `pdd/design/detailed-design.md:417-422`
**Expected Outcome:** Document that `POST /api/v1/regattas/{id}/operator-tokens` accepts `{blockIds: [], stationId: string, validityHours: number}` in request body

---

## 2. MISSING API ENDPOINTS

### API-001: Club CRUD endpoints
**What:** Only payment-related club endpoints exist; no create/update/delete for clubs
**Where:** `pdd/design/detailed-design.md:393-401`
**Expected Outcome:** Add endpoints:
- `POST /api/v1/clubs` - Create club
- `GET /api/v1/clubs` - List clubs (paginated)
- `GET /api/v1/clubs/{clubId}` - Get club details
- `PUT /api/v1/clubs/{clubId}` - Update club
- `DELETE /api/v1/clubs/{clubId}` - Delete club (only if no entries)

### API-002: Audit log query parameters
**What:** `GET /api/v1/regattas/{id}/audit-log` exists but no query parameters documented
**Where:** `pdd/design/detailed-design.md:424-427`
**Expected Outcome:** Document supported filters: `entityType`, `entityId`, `actorId`, `action`, `fromDate`, `toDate`, `limit`, `offset`

### API-003: Investigation penalties list
**What:** Endpoints exist to assign and remove penalties, but no endpoint to list penalties for an investigation
**Where:** `pdd/design/detailed-design.md:382-391`
**Expected Outcome:** Add `GET /api/v1/regattas/{id}/investigations/{invId}/penalties` returning list of `{entryId, penaltySeconds, assignedAt, assignedBy}`

### API-004: Bib pool deletion endpoint
**What:** `POST /api/v1/regattas/{id}/blocks/{blockId}/bib-pools` exists but no DELETE endpoint
**Where:** `pdd/design/detailed-design.md:337-338`
**Expected Outcome:** Add `DELETE /api/v1/regattas/{id}/blocks/{blockId}/bib-pools/{poolId}` (only if draw not published)

### API-005: Bib pool update endpoint
**What:** No endpoint documented to update bib pool configuration (ranges, name, etc.)
**Where:** `pdd/design/detailed-design.md:337-338`
**Expected Outcome:** Add `PUT /api/v1/regattas/{id}/blocks/{blockId}/bib-pools/{poolId}`

### API-006: Penalty configuration get endpoint path mismatch
**What:** `detailed-design.md:408-412` documents `GET /api/v1/regattas/{id}/penalty-config` but line 390 shows penalties under investigations. Ensure consistency.
**Where:** `pdd/design/detailed-design.md:408-412`
**Expected Outcome:** Verify endpoint path and document response schema: `{defaultPenaltySeconds: number, allowCustomSeconds: boolean}`

### API-007: Object storage upload endpoints
**What:** Only tile retrieval endpoints documented; no upload endpoints for tiles or manifests
**Where:** `pdd/design/detailed-design.md:379-380`
**Expected Outcome:** Add `POST /api/v1/regattas/{id}/capture-sessions/{sessionId}/tiles` and `POST /api/v1/regattas/{id}/capture-sessions/{sessionId}/manifest`

### API-008: Investigations list response schema
**What:** `GET /api/v1/regattas/{id}/investigations` documented but response schema not specified
**Where:** `pdd/design/detailed-design.md:385-386`
**Expected Outcome:** Document response: `{investigations: [{id, entryId, status, createdAt, createdBy, ...}]}`

---

## 3. MISSING DOCUMENTATION/DETAILS

### DOC-001: Tie-breaking rule for equal elapsed times
**What:** `idea-honing.md:106` says "Ties share rank" but doesn't specify secondary sort for ties beyond start time and bib
**Where:** `pdd/idea-honing.md:106`
**Expected Outcome:** Document explicit tie-breaker order: 1) elapsed time (tie), 2) start time, 3) bib number, 4) crew name alphabetically

### DOC-002: Bib pool modification rules after draw
**What:** Not specified whether bib pool ranges can be modified after draw is published
**Where:** `pdd/idea-honing.md:72-82` and `pdd/design/detailed-design.md:56-60`
**Expected Outcome:** Document: "Bib pools are immutable after draw publish. To change pools, unpublish draw first."

### DOC-003: Penalty value specification
**What:** `idea-honing.md:140` mentions "penalty (seconds configurable per regatta)" but no minimum/maximum specified
**Where:** `pdd/idea-honing.md:140`
**Expected Outcome:** Document constraints: e.g., "Penalty seconds must be a non-negative integer, max 300 (5 minutes)"

### DOC-004: Entry fee configuration endpoint
**What:** `detailed-design.md:46` mentions "entry fee is configurable per regatta" but no API endpoint documented
**Where:** `pdd/design/detailed-design.md:46`
**Expected Outcome:** Add `GET/PUT /api/v1/regattas/{id}/config/entry-fee` with schema `{amount: number, currency: string}`

### DOC-005: Regatta state lifecycle
**What:** Not documented what states a regatta can be in (draft, published, archived, deleted)
**Where:** `pdd/design/detailed-design.md:287-296`
**Expected Outcome:** Document state machine: draft → published (draw published) → archived → deleted

### DOC-006: Print generation async mechanism
**What:** `detailed-design.md:194` and `style-guide.md:256-264` mention async processing but no mechanism documented
**Where:** `pdd/design/detailed-design.md:194` and `pdd/design/style-guide.md:256-264`
**Expected Outcome:** Document async job endpoint: `POST /api/v1/regattas/{id}/export/printables` returns jobId, status polling via `GET /api/v1/jobs/{jobId}`

### DOC-007: Inline investigation closure workflow
**What:** `idea-honing.md:145` mentions auto-approval on "no action" closure but no step-by-step documented
**Where:** `pdd/idea-honing.md:145`
**Expected Outcome:** Document workflow: 1) Close investigation with outcome=no_action, 2) System timing checks if entry complete, 3) If yes and no other open investigations, auto-approve entry, 4) If no, entry returns to pending_approval

### DOC-008: Bib collision handling during draw generation
**What:** Not specified how bib collisions are handled when generating the draw
**Where:** `pdd/idea-honing.md:76-81` and `pdd/design/detailed-design.md:56-59`
**Expected Outcome:** Document: "Draw generation uses the bib pool allocation algorithm. If a pool is exhausted, continue to next pool. If overflow is exhausted, throw error."

### DOC-009: Public SSE event data payload details
**What:** `detailed-design.md:257-263` lists event types but `data` payload structure only partially documented
**Where:** `pdd/design/detailed-design.md:257-263`
**Expected Outcome:** Document all event data payloads:
- `snapshot`: `{regatta: {...}, draw: {...}, results: {...}}`
- `draw_revision`: `{draw_revision: number, reason?: string}`
- `results_revision`: `{results_revision: number, reason?: string}`

### DOC-010: Session refresh window behavior
**What:** `idea-honing.md:250` says "refresh window 20% of TTL" but not specified how this works in practice
**Where:** `pdd/idea-honing.md:250`
**Expected Outcome:** Document: "If current time > session_expiry - (TTL * 0.2), refresh the session. Otherwise return 204."

### DOC-011: Operator sync protocol details
**What:** `detailed-design.md:127-130` describes sync endpoint but queue structure and conflict response format not documented
**Where:** `pdd/design/detailed-design.md:127-130`
**Expected Outcome:** Document:
- Request: `{actions: [{type, timestamp, deviceId, metadata}]}`
- Response: `{synced: [], conflicts: [{action, reason, resolutionOptions}]}`

### DOC-012: Withdrawal status visibility rules
**What:** `idea-honing.md:154-155` describes withdrawn_before_draw vs withdrawn_after_draw visibility but public visibility not fully clear
**Where:** `pdd/idea-honing.md:154-155`
**Expected Outcome:** Document:
- withdrawn_before_draw: NOT shown in public schedule/results
- withdrawn_after_draw: SHOWN in public schedule/results with "Withdrawn" status, excluded from rankings

### DOC-013: Event group vs Block relationship
**What:** Terminology uses "event group" but blocks contain events; relationship between event groups and blocks not explained
**Where:** `pdd/idea-honing.md:30` and `pdd/design/detailed-design.md:23-24`
**Expected Outcome:** Document: "Event groups are optional named groupings for awards/scheduling. Blocks contain ordered list of events (which may belong to event groups)."

---

## 4. ASSUMPTIONS MADE (per review rules)

1. **Assumption:** The "additional SSE events" (`investigation_created`, `penalty_assigned`) in detailed-design.md are intended features but not documented in idea-honing.md. 
   - **Resolution:** These should be documented in idea-honing.md or marked as post-v0.1

2. **Assumption:** Club CRUD operations are needed but not yet implemented; the current docs only cover payment-related club operations
   - **Resolution:** Add Club CRUD endpoints to detailed-design.md

3. **Assumption:** The duplicate line in detailed-design.md (lines 48-49) is an editing oversight
   - **Resolution:** Remove one duplicate

---

## 5. FILES REVIEWED

| File | Version | Last Modified |
|------|---------|---------------|
| `pdd/summary.md` | v2 | 2026-02-01 |
| `pdd/idea-honing.md` | v2 | 2026-02-01 |
| `pdd/rough-idea.md` | v2 | 2026-02-02 |
| `pdd/design/detailed-design.md` | v2 | 2026-02-01 |
| `pdd/design/style-guide.md` | v2 | 2026-02-01 |
| `pdd/implementation/plan.md` | v2 | 2026-02-01 |
| `pdd/research/image-format-and-storage-notes.md` | v2 | 2026-02-01 |

---

## 6. PRIORITY MATRIX

| Priority | Count | Examples |
|----------|-------|----------|
| High | 8 | API-001, API-002, DOC-001, DOC-004, DOC-005, DOC-006, DOC-007, DOC-012 |
| Medium | 12 | INC-001 through INC-005, API-003 through API-008, DOC-002, DOC-003 |
| Low | 6 | DOC-008, DOC-009, DOC-010, DOC-011, DOC-013 |

---

## 7. RECOMMENDED NEXT STEPS

1. **Immediate (High Priority):** Address API-001 (Club CRUD) and DOC-004 (Entry fee config) as these affect core domain modeling
2. **Before Implementation:** Resolve INC-002 (SSE events) to ensure public API stability
3. **During Implementation:** Use DOC-007 (investigation closure) as the reference workflow for implementing investigation logic
4. **Post-v0.1:** Consider adding `investigation_created` and `penalty_assigned` SSE events if real-time investigation updates are needed
