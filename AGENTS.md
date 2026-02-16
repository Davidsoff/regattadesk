# AGENTS.md

This file defines best practices for human and LLM agents working in this repository.

## Mission
Build RegattaDesk v0.1 in small, testable increments while preserving correctness, operability, accessibility, and clear documentation.

## Source of truth
Always align changes with these artifacts before implementation:
- `pdd/implementation/plan.md`
- `pdd/implementation/bc*.md`
- `pdd/implementation/issues/*.issues.yaml`
- `pdd/design/openapi-v0.1.yaml`
- `pdd/design/style-guide.md`
- `pdd/design/database-schema.md`

If implementation and docs diverge, update docs in the same change.

## Working style
- Keep changes scoped and reversible.
- Prefer incremental, demoable outcomes over large batch changes.
- Use explicit assumptions; do not hide uncertainty.
- Do not introduce speculative abstractions without immediate use.
- RegattaDesk v0.1 is pre-production: breaking changes are allowed when they simplify code and remove unused migration/deprecation paths.
- When introducing a breaking change, update affected PDD docs in the same change.

## Planning and execution
- Start from a single ticket (or one tightly related ticket set).
- Identify dependencies (`depends_on`) before coding.
- Define acceptance criteria and tests up front.
- Finish one concern fully (code + tests + docs) before moving on.

## Architecture guardrails
- Follow API-first development for staff and operator surfaces.
- Treat event history as append-only; do not mutate audit/event records.
- Keep revision-driven public delivery semantics (`draw_revision`, `results_revision`) intact.
- Keep auth boundaries explicit: edge identity via Traefik/Authelia, API role enforcement in backend, and operator token rules for line-scan ingest/retrieval.
- Respect bounded-context ownership; avoid leaking domain logic across contexts.

## Coding best practices
- Prefer clear, boring code over clever code.
- Keep functions small and side effects explicit.
- Validate all external input at boundaries.
- Fail fast with actionable errors.
- Use deterministic behavior for workflows requiring replay/recovery.
- Prefer idempotent command handling for retry-prone paths.
- Keep configuration centralized and environment-driven.

### Bash scripting
- Use `[[` instead of `[` for conditional tests (safer and more feature-rich).
- Quote variables to prevent word splitting and glob expansion.
- Use `set -e` for scripts that should fail on first error.
- Prefer explicit error handling over silent failures.

## Data and persistence
- Use migrations for all schema changes.
- Define indexes with observed query patterns in mind.
- Encode invariants in both domain logic and DB constraints where practical.
- Never silently drop or rewrite historical business events.

## API and contracts
- Treat OpenAPI and Pact contracts as enforceable interfaces.
- Add contract tests when request/response behavior changes.
- Version behavior intentionally; avoid accidental contract drift.
- Return stable, machine-readable error shapes.

## Frontend and UX
- Reuse shared design tokens and primitives.
- Meet WCAG 2.2 AA minimum for impacted flows.
- Ensure keyboard navigation and focus states are usable.
- Keep locale/timezone/date formatting consistent with PDD rules.
- Validate mobile and desktop behavior for user-facing flows.

## Security and privacy
- Enforce least privilege by role.
- Apply secure defaults for cookies, headers, and token handling.
- Never commit secrets, tokens, or credentials.
- Log security-relevant actions with actor/context metadata.

## Testing standards
Apply the test matrix from `pdd/implementation/plan.md`:
- Domain/command logic: unit tests.
- Persistence/query changes: PostgreSQL integration tests.
- API contract changes: Pact + endpoint integration checks.
- UI/UX changes: targeted UI tests + accessibility checks.

Also:
- Add regression tests for bug fixes.
- Prefer deterministic tests over timing-sensitive tests.
- Keep test fixtures readable and minimal.

## Observability and operations
- Add metrics/tracing for new critical paths.
- Keep health/readiness signals meaningful.
- Document operational impacts and runbook updates in the same PR when relevant.
- Treat performance regressions as functional defects.

## Documentation and handoff
For each meaningful change:
- Update relevant PDD docs and ticket status/context.
- Document migration steps and rollback notes when relevant to an actively used environment.
- Include concise "what changed" and "how to verify" notes.

## Git and commit hygiene
- Make atomic commits with one primary concern per commit.
- Use descriptive commit messages with clear intent.
- Do not mix refactors with behavior changes unless necessary.
- Keep the working tree clean before handoff.

## Pull request checklist
- Scope matches ticket(s).
- Acceptance criteria satisfied.
- Required tests added and passing.
- Docs/contracts updated.
- Security, accessibility, and operability impacts considered.
- Risks and follow-ups explicitly listed.

## Anti-patterns to avoid
- Hidden behavior changes without tests.
- Silent schema changes without migrations.
- Cross-context coupling for short-term convenience.
- TODO-driven shipping without linked tickets.
- Disabling tests or checks to make CI pass.
