# Core-Regatta Domain — Overview

## What This Domain Owns

- The event-sourced heart of the application: regattas, athletes, crews, entries, and their audit trail.
- Aggregate, command, event, and projection primitives used to persist and replay core business state.
- Staff-facing regatta setup APIs that create and mutate the base entities consumed by other domains.

## Boundaries

- It does not own ruleset lifecycle, draw generation, or bib-pool allocation logic.
- It does not own public delivery, line-scan binary persistence, adjudication workflows, or invoice generation.
- It can emit state changes used by finance and adjudication, but those workflows stay in their own domains.

## Key Files

| File | Purpose |
|:-----|:--------|
| `apps/backend/src/main/java/com/regattadesk/eventstore/PostgresEventStore.java` | Append-only persistence for domain events. |
| `apps/backend/src/main/java/com/regattadesk/regatta/RegattaAggregate.java` | Core regatta aggregate and event application logic. |
| `apps/backend/src/main/java/com/regattadesk/entry/EntryAggregate.java` | Entry lifecycle and status transitions. |
| `apps/backend/src/main/java/com/regattadesk/athlete/AthleteAggregate.java` | Athlete CRUD and event sourcing. |
| `apps/backend/src/main/java/com/regattadesk/regatta/api/RegattaSetupResource.java` | Staff API for events, crews, and entries inside a regatta. |
| `apps/backend/src/main/resources/db/migration/V001__initial_event_store_schema.sql` | Initial event-store schema. |
| `apps/frontend/src/api/regatta-setup.js` | Frontend client entrypoint for setup workflows. |

## Current State

- The backend package layout is explicitly organized around aggregates, events, services, and projectors.
- Multiple migrations now extend the original event-store schema into read models, checkpoints, and regatta-scoped entities.
- Frontend staff flows already consume this API-first surface rather than bypassing it with direct state management.
