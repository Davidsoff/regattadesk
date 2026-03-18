# Adjudication Domain — Overview

## What This Domain Owns

- Investigation workflows and the service layer that applies penalties, exclusions, DSQs, and reverts.
- Result-affecting disciplinary state transitions and approval-adjacent adjudication logic.
- Staff-facing adjudication UI and API surfaces used by jury and admin roles.

## Boundaries

- It does not own the initial timing evidence capture or the base entry identity model.
- It does not own public route delivery or cache semantics, even though it drives `results_revision` changes.
- It does not own finance or platform hardening concerns beyond its own auditability requirements.

## Key Files

| File | Purpose |
|:-----|:--------|
| `apps/backend/src/main/java/com/regattadesk/adjudication/AdjudicationService.java` | Service layer for investigation outcomes and disciplinary actions. |
| `apps/backend/src/main/java/com/regattadesk/adjudication/api/AdjudicationResource.java` | HTTP interface for adjudication workflows. |
| `apps/backend/src/main/java/com/regattadesk/investigation/InvestigationAggregate.java` | Event-sourced investigation state. |
| `apps/backend/src/main/java/com/regattadesk/investigation/InvestigationOutcome.java` | Outcome model for investigations. |
| `apps/frontend/src/views/staff/AdjudicationView.vue` | Staff adjudication UI. |
| `.mindspec/docs/specs/pdd-v0.1/implementation/bc07-results-and-adjudication.md` | Domain scope and plan coverage. |

## Current State

- The backend already separates investigation state from the HTTP adjudication workflow service and resource layer.
- The frontend has a dedicated staff adjudication view rather than treating this as a generic entry edit flow.
- This domain is materially implemented and directly connected to the public revision model described in BC07 and BC05.