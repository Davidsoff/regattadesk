# PDD Review - Actionable Items

**Review Date:** 2026-02-02
**Reviewer:** Documentation Review
**Scope:** All files in `pdd/` folder
**Previous Review:** All 26 items from this review have been executed in commit 5167a85

---

## Summary

All 26 actionable items from the PDD review have been successfully executed:

### Inconsistencies (5 items)
- INC-001: Duplicate line removed from detailed-design.md
- INC-002: SSE events documentation updated (investigation_created/penalty_assigned marked as post-v0.1)
- INC-003: Penalty configuration relationship clarified (regatta-level config for investigations)
- INC-004: API path naming convention documented (always use plural)
- INC-005: Operator token scope/block parameters documented

### Missing API Endpoints (8 items)
- API-001: Club CRUD endpoints added
- API-002: Audit log query parameters documented
- API-003: Investigation penalties list endpoint added
- API-004/005: Bib pool PUT/DELETE endpoints added
- API-006: Penalty configuration endpoint path verified and schema documented
- API-007: Object storage upload endpoints added (tiles, manifest)
- API-008: Investigations list response schema documented

### Missing Documentation/Details (13 items)
- DOC-001: Tie-breaking rules documented
- DOC-002: Bib pool modification rules documented (immutable after draw)
- DOC-003: Penalty value constraints documented (max 300 seconds)
- DOC-004: Entry fee configuration endpoint added
- DOC-005: Regatta state lifecycle documented
- DOC-006: Print generation async mechanism documented
- DOC-007: Inline investigation closure workflow documented
- DOC-008: Bib collision handling documented
- DOC-009: SSE event data payload details documented
- DOC-010: Session refresh window behavior documented
- DOC-011: Operator sync protocol details documented
- DOC-012: Withdrawal status visibility rules (already documented)
- DOC-013: Event group vs block relationship (already documented)

---

## Files Modified

| File | Changes |
|------|---------|
| `pdd/design/detailed-design.md` | API endpoints, naming convention, response schemas, documentation updates |
| `pdd/idea-honing.md` | SSE events, tie-breaking, bib pools, penalties, session refresh, sync protocol |
