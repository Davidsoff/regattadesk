# BC04 Rules, Scheduling, and Draw

## Scope
Competition configuration lifecycle: rulesets, block timing, bib allocation, and draw publication.

## Functional Features to Implement
- Implement ruleset versioning and duplication/update workflow.
- Enforce ruleset immutability after draw publication.
- Implement age-calculation configuration and validation for gender and min/max age bounds.
- Implement super-admin-only promotion of regatta-owned rulesets to global selection.
- Implement block scheduling with start times and intervals between crews/event groups.
- Implement multiple bib pools per block.
- Implement regatta-level shared overflow bib pool and assignment rules.
- Implement draw generation using randomization with stored seed.
- Implement draw publication and `draw_revision` progression.
- Enforce v0.1 constraint: no insertion after draw publication.

## Non-Functional Features to Implement
- Guarantee draw reproducibility through persisted random seed.
- Guarantee governance controls over ruleset promotion and post-draw immutability.
- Preserve validation correctness and explainable rejection outcomes.
- Ensure revision traceability for schedule-affecting changes.

## Plan Coverage
- Step 12
- Step 13
- Step 14
