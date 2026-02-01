Version: v1 (2026-01-31)

# Rough idea

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
- Strong auditability (adopt event sourcing)
- Public pages cacheable; results auto-update via server push
- Staff auth via Auth0; per-regatta roles; operators use QR token links (no personal accounts)
- Operators pages work without stable internet; last-write-wins on conflicts
- Health endpoint + OpenTelemetry endpoint
