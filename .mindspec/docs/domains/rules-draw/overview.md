# Rules-Draw Domain — Overview

## What This Domain Owns

- Ruleset lifecycle, including creation, duplication, update, and promotion.
- Block scheduling, bib-pool management, draw generation, and draw publication state.
- The deterministic scheduling layer that sits between raw regatta setup and public/operator consumption.

## Boundaries

- It does not own the base regatta, athlete, crew, or entry entities.
- It does not own public result delivery, operator capture evidence, or adjudication state.
- It supplies revisions and scheduling facts to other domains, but not the pages or workflows that consume them.

## Key Files

| File | Purpose |
|:-----|:--------|
| `apps/backend/src/main/java/com/regattadesk/ruleset/RulesetAggregate.java` | Ruleset lifecycle and event application. |
| `apps/backend/src/main/java/com/regattadesk/block/api/BlockResource.java` | CRUD and ordering APIs for blocks. |
| `apps/backend/src/main/java/com/regattadesk/bibpool/api/BibPoolResource.java` | Bib-pool management APIs. |
| `apps/backend/src/main/java/com/regattadesk/draw/DrawGenerator.java` | Deterministic draw generation logic. |
| `apps/backend/src/main/java/com/regattadesk/draw/api/DrawResource.java` | Generate, publish, and unpublish draw endpoints. |
| `apps/frontend/src/api/draw.js` | Frontend draw workflow client. |
| `apps/frontend/src/composables/useBibPoolValidation.js` | Client-side validation aligned with draw constraints. |

## Current State

- Backend packages are already separated by block, bib pool, draw, and ruleset concerns.
- Frontend views and composables exist for staff setup and draw immutability behavior.
- The implementation aligns closely with the PDD bounded context, so this domain maps cleanly onto the existing codebase.
