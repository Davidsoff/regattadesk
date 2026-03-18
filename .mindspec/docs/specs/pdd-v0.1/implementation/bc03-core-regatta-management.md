# BC03 Core Regatta Management

## Scope
Event-sourced domain core for regatta setup, staff CRUD workflows, and projection foundations.

## Functional Features to Implement
- Implement event store schema.
- Implement append/read primitives for event storage.
- Implement core aggregates and projection scaffolding.
- Implement API-first regatta setup workflows where staff UI consumes the same API.
- Implement domain CRUD for events, event-group grouping, athletes, crews, and entries.
- Implement crew mutation and withdrawal workflows.

## Non-Functional Features to Implement
- Retain audit/event log indefinitely in v0.1.
- Preserve append-only auditability for domain changes.
- Ensure projection consistency with source events.
- Maintain transactional integrity for write workflows.
- Keep domain rules testable through command-centric boundaries.

## Plan Coverage
- Step 3
- Step 4
- Step 5
