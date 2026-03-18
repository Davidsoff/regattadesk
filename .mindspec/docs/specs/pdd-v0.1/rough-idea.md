Version: v2 (2026-02-02)

# Rough idea

> NOTE: Superseded by `idea-honing.md` v2 and `design/detailed-design.md`. This document retained for historical reference only.
> **Authentication Note:** This early document mentions Auth0; the final architecture uses Authelia SSO (see detailed-design.md).

Build a regatta management system for rowing head races (single distance) with:

- Backend: Quarkus (Java) + Postgres
- Frontend: Vue
- Containerized deployment + automated pipeline

Primary users:
- Regatta organizers: import/manage entries, create draw, bibs, publish schedule
- Jury: handle protests/investigations, assign penalties, approve results
- Info desk: submit crew mutations, withdrawals, bib issues
- Finance: mark paid (per entry or per club)
- Start/finish operators: register start/finish times using line-scan camera UI; offline-capable
- Competitors/supporters/public: view schedule and live-updating results (high read load, cacheable)

Key capabilities (v0.1):
- Head racing only; sequential class starts; interleaved finishes
- Random draw, designed for future algorithms
- API-first for all operations (imports can be external tools later)
- Photo-finish line camera UI for start and finish (markers create/move/delete; overview + draggable detail window)
- Strong auditability (adopt event sourcing; later v0.1 exception added for BC06 line-scan tile/manifest storage path, which is API-managed and non-event-sourced)
- Public pages cacheable; results auto-update via server push
- Staff auth via Authelia SSO; per-regatta roles; operators use QR token links (no personal accounts)
- Operators pages work without stable internet; offline conflict policy uses LWW for marker adjustments/unlinks when entry is not approved, and requires manual resolution for duplicate links or edits against approved/immutable entries
- Health endpoint + OpenTelemetry endpoint
