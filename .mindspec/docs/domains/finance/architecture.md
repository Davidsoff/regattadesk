# Finance Domain — Architecture

## Key Patterns

- Finance uses service-layer workflows for payment and invoice operations, backed by explicit finance events and projections.
- Entry and club summaries are maintained as read models rather than recalculated only in the UI.
- Async invoice generation is modeled as job-like state rather than synchronous PDF generation in the request path.

## Invariants

- Payment status changes must remain auditable and tied to a regatta and entity scope.
- Bulk payment operations must produce deterministic, machine-readable success and failure results.
- Invoice state transitions must remain coherent and traceable from generation through paid state.
- Finance routes must remain protected by the expected staff roles.

## Design Decisions

- Finance is separated from core entry management because billing workflows, summaries, and invoice jobs have their own lifecycle and read models.
- Async invoice generation avoids coupling long-running invoice work directly to request latency.
- Dedicated frontend views keep finance-heavy tables and workflows isolated from general regatta setup screens.