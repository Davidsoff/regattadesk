# FEGAP-008 Follow-Up Issues

## Overview

Based on the FEGAP-008 implementation (production-ready API client layer), four follow-up GitHub issues have been created to complete the staff draw workflow UI.

## Created Issues

### ✅ Issue #113: FEGAP-008-A - RulesetDetail View
**URL:** https://github.com/Davidsoff/regattadesk/issues/113  
**Priority:** P1  
**Dependencies:** FEGAP-008

**Scope:**
- View and edit ruleset details (name, version, description, age_calculation_type)
- Duplicate ruleset with new name/version
- Promote ruleset to global catalog (super_admin only)
- Form validation and error handling
- I18n translations (en/nl)

**Key Deliverables:**
- `RulesetDetail.vue` component
- Edit mode with save action
- Duplicate dialog with validation
- Promote button with role guard and confirmation
- Integration with draw API client

---

### ✅ Issue #114: FEGAP-008-B - BlocksManagement View
**URL:** https://github.com/Davidsoff/regattadesk/issues/114  
**Priority:** P1  
**Dependencies:** FEGAP-008, FEGAP-008-A

**Scope:**
- Configure timing blocks (start time, intervals)
- Manage bib pools per block (range or explicit-list modes)
- Configure regatta-wide overflow pool
- Drag-and-drop reordering for blocks and pools
- Handle bib pool overlap validation errors
- I18n translations (en/nl)

**Key Deliverables:**
- `BlocksManagement.vue` component
- Block CRUD operations UI
- Bib pool CRUD operations UI
- Reordering functionality
- Validation error display for overlaps

---

### ✅ Issue #115: FEGAP-008-C - DrawWorkflow View
**URL:** https://github.com/Davidsoff/regattadesk/issues/115  
**Priority:** P0 (critical path)  
**Dependencies:** FEGAP-008, FEGAP-008-B

**Scope:**
- Generate draw with optional custom seed
- Publish draw with `draw_revision` increment warning
- Unpublish draw to enable regeneration
- Display current draw status and revisions
- Prerequisite validation (blocks, pools, entries)
- I18n translations (en/nl)

**Key Deliverables:**
- `DrawWorkflow.vue` component
- Generate draw action with seed input
- Publish confirmation dialog with revision messaging
- Unpublish action
- Draw status display

---

### ✅ Issue #116: FEGAP-008-D - Immutability Guards & Role-Based Visibility
**URL:** https://github.com/Davidsoff/regattadesk/issues/116  
**Priority:** P1 (cross-cutting)  
**Dependencies:** FEGAP-008-A, FEGAP-008-B, FEGAP-008-C

**Scope:**
- Post-draw immutability enforcement in UI
- Role-based visibility for super_admin features
- Validation error display formatting
- Visual indicators for immutable state
- Authorization error handling

**Key Deliverables:**
- Immutability guards for blocks and bib pools
- `useUserRole()` composable
- Role-based visibility logic
- Validation error display components
- Lock icons and disabled state styling

---

## Implementation Order

**Recommended sequence:**

1. **FEGAP-008-A** (RulesetDetail) - Foundation for form patterns
2. **FEGAP-008-B** (BlocksManagement) - Complex CRUD with validation
3. **FEGAP-008-C** (DrawWorkflow) - Critical path for complete workflow
4. **FEGAP-008-D** (Guards & Visibility) - Polish and security layer

**Parallel Development:**
- FEGAP-008-A and FEGAP-008-B can be developed in parallel
- FEGAP-008-D should be started after A, B, C are feature-complete

## Dependencies Graph

```
FEGAP-008 (API Client Layer) ✅ COMPLETE
    ├── FEGAP-008-A (RulesetDetail)
    ├── FEGAP-008-B (BlocksManagement)
    │       └── FEGAP-008-C (DrawWorkflow)
    └── FEGAP-008-D (Guards & Visibility)
            └── depends on: A, B, C
```

## Acceptance Criteria Summary

### When All Issues Complete:
- ✅ Staff can configure complete draw workflow via UI
- ✅ UI blocks invalid post-draw edits per v0.1 constraints
- ✅ Draw publication clearly communicates `draw_revision` increments
- ✅ Ruleset list/detail/create/update/duplicate flows work end-to-end
- ✅ Super_admin-only promotion affordance with proper guard
- ✅ Block timing and bib-pool management UI functional
- ✅ Draw generate/publish/unpublish UX complete

## API Foundation (Already Complete)

All issues build on the production-ready API client from FEGAP-008:

**Files:**
- `apps/frontend/src/api/draw.js` (33 methods, 242 lines)
- `apps/frontend/src/api/__tests__/draw.test.js` (24 unit tests)
- `apps/frontend/src/__tests__/draw-lifecycle-integration.test.js` (9 integration tests)

**Test Status:** ✅ 33/33 tests passing  
**Security:** ✅ CodeQL scan passed (0 vulnerabilities)  
**Documentation:** ✅ Comprehensive summaries available

## Estimation

| Issue | Complexity | Estimated Effort | Priority |
|-------|-----------|------------------|----------|
| FEGAP-008-A | Medium | 8-12 hours | P1 |
| FEGAP-008-B | High | 16-20 hours | P1 |
| FEGAP-008-C | Medium | 10-14 hours | P0 |
| FEGAP-008-D | Medium | 8-12 hours | P1 |
| **Total** | - | **42-58 hours** | - |

## Test Infrastructure Note

**Known Issue:** Vue component test infrastructure needs improvement for i18n plugin setup.

**Impact:** Component tests may require manual verification with screenshots until test setup is improved.

**Mitigation:** 
- Focus on API integration correctness (already tested)
- Manual verification required for UI flows
- Screenshot documentation for each view
- Consider adding E2E tests as alternative

## References

- **Original Ticket:** FEGAP-008 - Build staff rules, blocks, bib-pools, and draw publish workflows
- **Implementation Summary:** `FEGAP-008-IMPLEMENTATION-SUMMARY.md`
- **Security Summary:** `FEGAP-008-SECURITY-SUMMARY.md`
- **Final Report:** `FEGAP-008-FINAL-REPORT.md`
- **BC04 Scope:** `pdd/implementation/bc04-rules-scheduling-and-draw.md`
- **OpenAPI Spec:** `pdd/design/openapi-v0.1.yaml`
- **Style Guide:** `pdd/design/style-guide.md`

---

**Created:** 2026-02-27  
**Status:** All follow-up issues created ✅  
**GitHub Issues:** #113, #114, #115, #116
