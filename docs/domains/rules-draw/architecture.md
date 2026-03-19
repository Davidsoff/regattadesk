# Rules-Draw Domain — Architecture

## Key Patterns

- Rulesets are modeled as versioned domain aggregates with explicit duplication and promotion operations.
- Blocks and bib pools expose CRUD-style APIs, while draw generation uses a deterministic service layer.
- Draw publication feeds revision changes back into regatta workflow state used by public and operator domains.

## Invariants

- Bib pools must not overlap and must remain reorderable without losing uniqueness guarantees.
- Draw generation must be reproducible from the stored seed and setup state.
- Post-publication mutations must honor draw immutability constraints.
- Schedule and bib assignment outputs must remain consistent with the published draw revision.

## Design Decisions

- Separate resources for blocks, bib pools, draw, and rulesets keep scheduling concerns isolated from the event-sourced core.
- The frontend mirrors backend immutability rules through dedicated composables rather than ad hoc view logic.
- Draw publication is treated as a state transition with downstream effects, not just a derived query.
