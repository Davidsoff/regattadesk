# PDD Fixes (inconsistencies + gaps)

## Open
- (empty)

## Completed
- Align anon session behavior: `/public/session` now mints on missing/invalid and refreshes within window; `/versions` and SSE require anon session cookie and return 401 on missing/invalid (`pdd/idea-honing.md`, `pdd/design/detailed-design.md`).
- Add result-label chip mapping for `provisional`, `edited`, `official` (`pdd/design/style-guide.md`).
- Define explicit entry approval/pending approval and marker immutability (`pdd/idea-honing.md`, `pdd/design/detailed-design.md`).
- Standardize `draw_revision`/`results_revision` naming in projection notes (`pdd/design/detailed-design.md`).
- Define timing/result calculation rules and precision/rounding (`pdd/idea-honing.md`, `pdd/design/detailed-design.md`, `pdd/design/style-guide.md`).
