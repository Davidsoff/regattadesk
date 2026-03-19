# Core-Regatta Domain — Architecture

## Key Patterns

- Event sourcing is the primary write model, backed by `AggregateRoot`, `Command`, and `DomainEvent` abstractions.
- Read models are derived through projection handlers and checkpointed projection workers.
- Staff workflows are API-first: backend resources expose the domain, and frontend setup code calls those endpoints directly.

## Invariants

- Core business history is append-only and must never be rewritten in place.
- Projection state must be reproducible from the event log and safe to rebuild.
- Regatta-scoped entities must stay within a single regatta boundary unless explicitly modeled otherwise.
- Withdrawal and reinstatement transitions must preserve auditability.

## Design Decisions

- The event store is implemented directly on PostgreSQL so auditability and replay remain first-class concerns.
- Projection checkpoints are stored in the database so long-running read models can recover deterministically.
- Frontend consumers are kept behind the same REST resources used by external clients to preserve contract-driven development.
