# PDD Review - Actionable Items

**Review Date:** 2026-02-02
**Reviewer:** Documentation Review
**Scope:** All files in `pdd/` folder

---

## 1. Missing API Endpoints

**Issue:** Event Groups are referenced in requirements but no API endpoints are documented for CRUD operations.

**Location:** [`pdd/design/detailed-design.md`](pdd/design/detailed-design.md:285-286) mentions event groups in requirements but the API section only lists:
- `GET /api/v1/regattas/{id}/event-groups` (list)
- `POST /api/v1/regattas/{id}/event-groups` (create)

Missing endpoints needed:
- `PUT /api/v1/regattas/{id}/event-groups/{groupId}`
- `DELETE /api/v1/regattas/{id}/event-groups/{groupId}`
- `GET /api/v1/regattas/{id}/event-groups/{groupId}`

**Expected Outcome:** Add all CRUD endpoints for Event Groups to the Staff API Endpoints section in `detailed-design.md`.

---

## 2. Tile Retrieval API Missing

**Issue:** Line-scan storage design mentions tile manifests and tile storage, but no API endpoint for retrieving individual tiles.

**Location:** [`pdd/design/detailed-design.md`](pdd/design/detailed-design.md:338) only documents:
- `GET /api/v1/regattas/{id}/tiles/manifest`

Missing endpoint needed:
- `GET /api/v1/regattas/{id}/tiles/{tileId}` or similar pattern for tile retrieval

**Expected Outcome:** Add tile retrieval endpoint to Staff API Endpoints section.

---

## 3. Marker List Endpoint Missing Filters

**Issue:** `GET /api/v1/regattas/{id}/markers` endpoint is documented without specifying filter/query parameters.

**Location:** [`pdd/design/detailed-design.md`](pdd/design/detailed-design.md:331)

**Expected Outcome:** Document supported query parameters:
- `blockId` (filter by block)
- `entryId` (filter by linked entry)
- `status` (linked/unlinked)
- `captureSessionId`

---

## 4. Status Value "Excluded" Not Defined

**Issue:** The status value `excluded` is used throughout documents but not clearly defined in the status taxonomy.

**Location:** 
- [`pdd/idea-honing.md`](pdd/idea-honing.md:52) lists `excluded` in status values
- [`pdd/design/detailed-design.md`](pdd/design/detailed-design.md:116) includes it in domain status values

**Expected Outcome:** Add clear definition: "Excluded - entry was disqualified from the race (not the same as DSQ which is entry-level ban)."

---

## 5. Penalty Configuration API Missing

**Issue:** Penalties are configurable per regatta (seconds value), but no API endpoints are documented for managing penalty configuration.

**Location:** [`pdd/design/detailed-design.md`](pdd/design/detailed-design.md:25) mentions "penalties (seconds configurable per regatta)" but no CRUD endpoints.

**Expected Outcome:** Add endpoints:
- `GET /api/v1/regattas/{id}/penalty-config`
- `PUT /api/v1/regattas/{id}/penalty-config`

---

## 6. Category and Boat Type API Missing

**Issue:** Categories and Boat Types are fundamental domain concepts but no API endpoints are documented for their management.

**Location:** 
- [`pdd/idea-honing.md`](pdd/idea-honing.md:27-28) defines Category and Boat type
- Used throughout as building blocks for Events

**Expected Outcome:** Add Category and Boat Type management endpoints:
- `GET /api/v1/categories`
- `POST /api/v1/categories`
- `GET /api/v1/boat-types`
- `POST /api/v1/boat-types`

---

## 7. Ruleset API Incomplete

**Issue:** Rulesets are documented with versioning and promotion logic, but API endpoints for ruleset management are incomplete.

**Location:** [`pdd/design/detailed-design.md`](pdd/design/detailed-design.md:40-42) describes ruleset behavior

**Expected Outcome:** Add complete ruleset API:
- `GET /api/v1/rulesets` (list global rulesets)
- `POST /api/v1/rulesets` (create ruleset)
- `PUT /api/v1/rulesets/{id}`
- `POST /api/v1/regattas/{id}/rulesets` (create regatta-specific ruleset)
- `POST /api/v1/regattas/{id}/rulesets/{rulesetId}/promote` (promote to global)

---

## 8. Audit Log Query Parameters Missing

**Issue:** `GET /api/v1/regattas/{id}/audit-log` endpoint documented without filter/query parameters.

**Location:** [`pdd/design/detailed-design.md`](pdd/design/detailed-design.md:377)

**Expected Outcome:** Document supported query parameters:
- `entityType` (entry, crew, athlete, etc.)
- `entityId`
- `actorId`
- `action`
- `fromDate`, `toDate`

---

## 9. Payment Amount Configuration Missing

**Issue:** Payment status is documented but no configuration for payment amounts per entry/per club.

**Location:** 
- [`pdd/idea-honing.md`](pdd/idea-honing.md:43-51) describes payment_status enum
- [`pdd/design/detailed-design.md`](pdd/design/detailed-design.md:43-49) details finance model

**Expected Outcome:** Document payment configuration:
- Is there a configurable fee per entry?
- Is there a club-level billing configuration?
- Add endpoints for payment configuration if applicable.

---

## 10. Missing Error Response Structures

**Issue:** Section "Error Handling" mentions structured error responses `{code, message, details}` but no examples or standard error codes.

**Location:** [`pdd/design/detailed-design.md`](pdd/design/detailed-design.md:381-388)

**Expected Outcome:** Document standard error codes:
- `ANON_SESSION_MISSING`
- `ANON_SESSION_INVALID`
- `CONFLICT` (409)
- `PERMISSION_DENIED`
- `VALIDATION_ERROR`
- `NOT_FOUND`

---

## 11. CSV/JSON Export Format Not Specified

**Issue:** Export endpoints exist but output formats are not specified.

**Location:** [`pdd/design/detailed-design.md`](pdd/design/detailed-design.md:378-379)
- `GET /api/v1/regattas/{id}/export/results`
- `GET /api/v1/regattas/{id}/export/printables`

**Expected Outcome:** Document:
- CSV field delimiter, line endings, encoding
- JSON schema for results export
- PDF template specification (if not covered by style-guide)

---

## 12. Offline Queue Sync Protocol Not Documented

**Issue:** Operator offline queue is mentioned but sync protocol details are missing.

**Location:** [`pdd/idea-honing.md`](pdd/idea-honing.md:133-136) describes conflict policy
- [`pdd/design/detailed-design.md`](pdd/design/detailed-design.md:120-123) mentions offline queue

**Expected Outcome:** Document sync protocol:
- Queue data structure
- Sync endpoint(s)
- Conflict resolution API

---

## 13. Auth0 Configuration Details Missing

**Issue:** Auth0 integration is mentioned but configuration details are not documented.

**Location:** 
- [`pdd/idea-honing.md`](pdd/idea-honing.md:167) mentions Auth0
- [`pdd/design/detailed-design.md`](pdd/design/detailed-design.md:194) references Auth0 JWT

**Expected Outcome:** Document:
- Required Auth0 configuration (tenant, audience, claims)
- Role claim format
- Token refresh behavior

---

## 14. Operator Token Validity Window Config Missing

**Issue:** Operator tokens have a "validity window" but no configuration endpoint.

**Location:** 
- [`pdd/idea-honing.md`](pdd/idea-honing.md:175) mentions validity window
- [`pdd/design/detailed-design.md`](pdd/design/detailed-design.md:221) references configurable validity window

**Expected Outcome:** Add operator token configuration endpoint:
- `GET /api/v1/regattas/{id}/operator-tokens/{tokenId}/config`
- `PUT /api/v1/regattas/{id}/operator-tokens/{tokenId}/config`

---

## 15. Version Numbers on Files

**Issue:** `rough-idea.md` is v1 while all other files are v2. This may be intentional but should be clarified.

**Location:** All files in `pdd/`

**Expected Outcome:** Either:
- Update `rough-idea.md` to v2 with note that it's superseded, or
- Add version history section to clarify why it's v1

---

## 16. SSE Event Types Not Fully Documented

**Issue:** SSE event types are partially documented but not comprehensive.

**Location:** 
- [`pdd/idea-honing.md`](pdd/idea-honing.md:235-237) mentions events: `snapshot`, `draw_revision`, `results_revision`
- [`pdd/design/detailed-design.md`](pdd/design/detailed-design.md:242) mentions same events

**Expected Outcome:** Document all SSE event types:
- `snapshot` - initial state on connect
- `draw_revision` - draw-related changes
- `results_revision` - results-related changes
- (Are there others? e.g., `investigation_created`, `penalty_assigned`?)

---

## 17. Density Toggle Persistence Not Documented

**Issue:** Density toggle is mentioned but persistence mechanism not documented.

**Location:** [`pdd/design/style-guide.md`](pdd/design/style-guide.md:91-95) shows CSS for compact density
- [`pdd/idea-honing.md`](pdd/idea-honing.md:289) mentions compact/dense toggle

**Expected Outcome:** Document how density preference is persisted:
- LocalStorage?
- User preferences in database?
- Device-specific (for operators)?

---

## 18. High-Contrast Mode Persistence Not Documented

**Issue:** Operator high-contrast mode toggle mentioned but persistence mechanism not documented.

**Location:** [`pdd/design/style-guide.md`](pdd/design/style-guide.md:79-87) shows CSS for high contrast
- [`pdd/idea-honing.md`](pdd/idea-honing.md:282) mentions persist per-device

**Expected Outcome:** Document how high-contrast preference is persisted:
- Same as density? LocalStorage?

---

## 19. Print Generation Process Unspecified

**Issue:** Printables are mentioned but generation process not specified.

**Location:** 
- [`pdd/design/detailed-design.md`](pdd/design/detailed-design.md:379) has export/printables endpoint
- [`pdd/design/style-guide.md`](pdd/design/style-guide.md:256-263) specifies print layout

**Expected Outcome:** Document print generation:
- PDF generation library/approach
- Async vs sync generation
- Queue for large regattas

---

## 20. Athlete Federation ID Usage Not Documented

**Issue:** Athletes have optional `federation_id` field but usage is not documented.

**Location:** 
- [`pdd/idea-honing.md`](pdd/idea-honing.md:33) mentions "optional federation id"
- [`pdd/design/detailed-design.md`](pdd/design/detailed-design.md:35) same

**Expected Outcome:** Document:
- Federation ID format/validation
- Use cases (national federation integration?)
- API for searching by federation ID

---

## Summary

| Priority | Category | Count |
|----------|----------|-------|
| High | Missing API endpoints | 7 |
| Medium | Missing documentation/details | 8 |
| Low | Inconsistencies | 5 |

**Total Actionable Items:** 20
