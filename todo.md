# PDD Documentation Review - Action Items

Generated: 2026-02-02

## Critical Issues (Resolve Before Implementation)

- [x] 1. Resolve SSE Event Types Conflict
  - **What**: Decide which document is authoritative for SSE event types
  - **Where**: 
    - [`detailed-design.md`](pdd/design/detailed-design.md:260-266) includes `investigation_created` and `penalty_assigned`
    - [`idea-honing.md`](pdd/idea-honing.md:241) defers these to post-v0.1
  - **Expected Outcome**: Either remove events from detailed-design OR update idea-honing to include them, with clear v0.1 scope boundary
  - **Summary**: Removed `investigation_created` and `penalty_assigned` from detailed-design.md and added note that these are deferred to post-v0.1

- [x] 2. Align Public Results Tie-breaker Order
  - **What**: Standardize the sorting criteria for public results
  - **Where**:
    - [`detailed-design.md`](pdd/design/detailed-design.md:84-85): "start time, then bib"
    - [`idea-honing.md`](pdd/idea-honing.md:108-109): 4-level tie-breaker (elapsed time, start time, bib, crew name)
  - **Expected Outcome**: Single authoritative tie-breaker specification with documented rationale
  - **Summary**: Updated detailed-design.md to match the 4-level tie-breaker from idea-honing.md

- [x] 3. Resolve Mobile Event Selection Matrix Decision
  - **What**: Choose between Option A and Option B for mobile behavior
  - **Where**: [`style-guide.md`](pdd/design/style-guide.md:171-172)
  - **Expected Outcome**: Documented decision with rationale and updated style guide
  - **Summary**: Selected Option B (boat-type tabs + category list) with documented rationale in style-guide.md

---

## Gaps to Address

- [x] 4. Add User Flow Diagrams
  - **What**: Create documented workflows for key processes
  - **Where**: New section in `detailed-design.md` or separate flowchart documents
  - **Required Flows**:
    - Draw generation flow
    - Result publishing flow
    - Investigation workflow (all actors)
    - Operator handoff PIN flow
    - Offline sync conflict resolution UI flow
  - **Expected Outcome**: Visual or text-based workflow documentation for each process
  - **Summary**: Added Mermaid flowcharts for all 5 required user flows in detailed-design.md

- [x] 5. Complete Data Model Schemas
  - **What**: Add detailed table schemas for incomplete models
  - **Where**: [`detailed-design.md`](pdd/design/detailed-design.md) data models section
  - **Required Schemas**:
    - `athletes` table (full structure with constraints)
    - `crews` table (full structure with constraints)
    - `clubs` billing details schema
    - Projection table structures
    - Event store schema (detailed)
  - **Expected Outcome**: Complete SQL schema definitions for all entities
  - **Summary**: Added complete SQL schemas for athletes, crews, clubs (with billing), event_store, and projection tables

- [x] 6. Standardize API Documentation Detail Level
  - **What**: Add missing request/response schemas to all endpoints
  - **Where**: [`detailed-design.md`](pdd/design/detailed-design.md:285-461) API section
  - **Required**:
    - Request/response schemas for endpoints lacking them
    - Pagination specification for all paginated endpoints
    - Filtering query parameter specifications
  - **Expected Outcome**: Consistent documentation depth across all API endpoints
  - **Summary**: Added API conventions section with pagination, filtering, and request/response schema standards

- [x] 7. Document Payment Workflow
  - **What**: Add comprehensive payment/invoicing documentation
  - **Where**: New section in `detailed-design.md`
  - **Required**:
    - Invoice generation flow
    - Payment reconciliation process
    - Refund handling (explicit "not supported" if applicable)
  - **Expected Outcome**: Complete payment workflow documentation
  - **Summary**: Added payment workflow section with invoice generation flow, payment reconciliation process, and explicit refund not supported message

---

## Missing Information (High Priority)

- [x] 8. Add Authentication/Authorization Details
  - **What**: Expand Auth0 implementation details
  - **Where**: [`detailed-design.md`](pdd/design/detailed-design.md:207-213) authentication section
  - **Required**:
    - Token refresh implementation details
    - Role claim format examples
    - Permission inheritance rules
  - **Expected Outcome**: Complete auth implementation guide
  - **Summary**: Expanded authentication section with token refresh, role claim format, permission inheritance rules, and permission matrix

- [x] 9. Document Offline Sync Protocol Details
  - **What**: Add offline sync technical specifications
  - **Where**: [`detailed-design.md`](pdd/design/detailed-design.md:130-134) offline section
  - **Required**:
    - Queue size limits
    - Retry strategy for sync failures
    - Offline duration tracking
  - **Expected Outcome**: Complete offline sync protocol specification
  - **Summary**: Added queue size limits, retry strategy with exponential backoff, and offline duration tracking specifications

- [x] 10. Define Performance Requirements
  - **What**: Add performance SLAs and limits
  - **Where**: New performance section in `detailed-design.md`
  - **Required**:
    - API response time SLAs
    - Concurrent user limits
    - SSE connection scaling expectations
    - CDN caching TTL recommendations
  - **Expected Outcome**: Documented performance requirements
  - **Summary**: Added comprehensive performance section with API SLAs, user limits, SSE scaling, and CDN TTL recommendations

- [x] 11. Complete Error Handling Specification
  - **What**: Create comprehensive error catalog
  - **Where**: [`detailed-design.md`](pdd/design/detailed-design.md:462-477) error handling section
  - **Required**:
    - Complete error code catalog
    - Retry guidance for clients
    - Rate limiting details
  - **Expected Outcome**: Full error handling guide
  - **Summary**: Added complete error code catalog, retry guidance, rate limiting details, and optimistic concurrency control

- [x] 12. Document Backup/Disaster Recovery
  - **What**: Add backup and recovery procedures
  - **Where**: New section in `detailed-design.md` or operations.md
  - **Required**:
    - Backup strategy for event store
    - Recovery procedures
    - Data retention policy
  - **Expected Outcome**: Complete backup/recovery documentation

---

## Missing Information (Medium Priority)

- [x] 13. Document Print Generation Details
  - **What**: Add print/PDF generation specifications
  - **Where**: [`detailed-design.md`](pdd/design/detailed-design.md:195-198) print concepts section
  - **Required**:
    - PDF library selection
    - Async job status polling details
    - Print template specifications
  - **Expected Outcome**: Complete print generation guide
  - **Summary**: Added PDF library selection (OpenPDF), async job status polling details, and print template specifications

- [x] 14. Add Monitoring/Alerting Specifications
  - **What**: Define observability requirements
  - **Where**: [`detailed-design.md`](pdd/design/detailed-design.md:487-490) observability section
  - **Required**:
    - Key metrics definitions
    - Alert thresholds
    - Dashboard requirements
  - **Expected Outcome**: Complete monitoring specification
  - **Summary**: Added comprehensive monitoring section with key metrics, alert thresholds, and dashboard requirements

- [x] 15. Define Testing Specifications
  - **What**: Add testing requirements and targets
  - **Where**: [`detailed-design.md`](pdd/design/detailed-design.md:492-495) testing section
  - **Required**:
    - Test coverage targets
    - E2E test scenarios
    - Load test specifications
  - **Expected Outcome**: Complete testing strategy document
  - **Summary**: Added testing strategy with coverage targets, E2E scenarios, load tests, and testing pyramid

- [x] 16. Document Edge Cases
  - **What**: Add edge case handling documentation
  - **Where**: New section in `detailed-design.md`
  - **Required**:
    - Bib pool exhaustion handling
    - Draw publish failure recovery
    - DSQ revert for multiple penalties
    - Race condition handling for concurrent marker linking
  - **Expected Outcome**: Documented edge case scenarios with handling procedures
  - **Summary**: Added edge cases section with handling procedures and recovery actions for various scenarios

---

## Documentation Maintenance

- [x] 17. Add State Diagrams
  - **What**: Create visual state diagrams for key lifecycles
  - **Where**: New diagrams in `detailed-design.md`
  - **Required Diagrams**:
    - Entry lifecycle state machine
    - Regatta state transitions
    - Investigation flow state machine
  - **Expected Outcome**: Visual state diagrams (Mermaid or similar) for key workflows
  - **Summary**: Added Mermaid state diagrams for entry lifecycle, regatta transitions, investigation flow, marker lifecycle, and operator session

- [x] 18. Create Staff Accessibility Pattern Guide
  - **What**: Document accessibility patterns for staff interfaces
  - **Where**: [`style-guide.md`](pdd/design/style-guide.md) or new accessibility guide
  - **Required**: Staff-specific accessibility patterns (keyboard navigation, screen reader support)
  - **Expected Outcome**: Complete accessibility guide for staff interfaces
  - **Summary**: Added comprehensive accessibility guide with keyboard navigation, screen reader support, high contrast mode, and staff-specific patterns
