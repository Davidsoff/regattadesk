# Adjudication Domain — Architecture

## Key Patterns

- Investigation state is modeled event-sourcely, while adjudication actions are exposed through a dedicated service and resource layer.
- Domain actions are explicit: penalty, exclude, DSQ, and DSQ revert each map to controlled state transitions.
- Staff UI is separated into a dedicated adjudication surface because approval and disciplinary workflows are operationally distinct from setup.

## Invariants

- Adjudication actions must remain auditable and reversible where the product design requires it.
- Result-affecting actions must preserve a coherent prior state so DSQ reverts and approval behavior are deterministic.
- Only authorized staff roles may perform investigation and adjudication operations.
- Downstream `results_revision` changes must stay aligned with adjudication state transitions.

## Design Decisions

- Investigation state is separate from core entry state so review workflows can evolve without overloading the entry aggregate.
- The API surface groups adjudication under regatta-scoped endpoints because these actions are operational workflows rather than generic CRUD.
- Reversible DSQ handling is modeled explicitly to satisfy the audit and workflow requirements in the PDD.