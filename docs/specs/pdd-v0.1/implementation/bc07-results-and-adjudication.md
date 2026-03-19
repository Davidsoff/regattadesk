# BC07 Results and Adjudication

## Scope
Result-approval and disciplinary workflows that mutate official outcome state.

## Functional Features to Implement
- Implement investigation workflow for disputed or review-required entries.
- Implement configurable per-regatta penalty seconds.
- Implement DSQ/exclusion actions.
- Implement DSQ revert behavior that restores the prior state.
- Implement result labeling model for adjudicated outcomes.
- Implement `results_revision` progression when adjudication changes published outcomes.

## Non-Functional Features to Implement
- Preserve full reversibility and auditability of adjudication actions.
- Keep adjudication state transitions consistent with event log semantics.
- Enforce role-scoped authority boundaries for result-affecting actions.
- Ensure compatibility with downstream public results publication.

## Plan Coverage
- Step 20
- Shared dependency for Step 22 result revision display
