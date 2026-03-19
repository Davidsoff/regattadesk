# Rules-Draw Domain — Interfaces

## Contracts

- `GET|POST|PUT|DELETE /api/v1/rulesets` plus `/duplicate` and `/promote` expose ruleset lifecycle.
- `GET|POST|PUT|DELETE /api/v1/regattas/{regatta_id}/blocks` and `/bib_pools` expose scheduling configuration.
- `POST /api/v1/regattas/{regatta_id}/draw/generate`, `/publish`, and `/unpublish` expose the draw workflow.

## Integration Points

- `core-regatta` supplies the events, crews, and entries that this domain schedules.
- `public-delivery` consumes the published draw revision and schedule outputs.
- `operator-capture` consumes bib and draw order expectations when linking markers during capture.
