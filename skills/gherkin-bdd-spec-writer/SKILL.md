---
name: gherkin-bdd-spec-writer
description: Write Behaviour-Driven Development (BDD) acceptance/end-to-end test specifications in Gherkin (.feature) using Cucumber conventions. Use when asked to “write the bdd specs”, “create acceptance tests”, “create end-to-end specs/scenarios”, “create missing e2e scenarios”, “improve acceptance tests”, or “convert a user story to acceptance tests”. Output only Gherkin specs (one or more .feature files) and do not create or modify any non-Gherkin files.
---

# Gherkin BDD Spec Writer

## Hard rules

- Produce **only** Gherkin `.feature` specs.
- Do **not** write step definitions, test code, fixtures, page objects, screenshots, API mocks, or implementation notes.
- Do **not** edit any existing files unless they are `.feature` files and the user explicitly asks you to improve them.
- If the user did not specify a location, propose file paths under `features/` (or a user-provided folder) and output the file contents.

## Workflow

1) Clarify the goal and scope (1–3 questions max)
- Ask for: actor(s), preconditions, success outcome, platforms (web/mobile), and any constraints (SSO/MFA, rate limits, compliance).
- If requirements are missing, state assumptions explicitly inside the spec via scenario names (not prose).
- If the input is a user story, extract: persona/actor, need/goal, value, and acceptance criteria (or infer and ask for confirmation).

2) Enumerate scenarios (include edge cases)
- Include: happy path, validation errors, auth/permissions, concurrency/double-submit, network/timeouts/retries, idempotency, state recovery (refresh/back), and security-relevant negative cases (e.g., locked account).
- Prefer fewer high-signal scenarios over exhaustive permutations; use `Scenario Outline` + `Examples` for combinatorics.

3) Write Gherkin with Cucumber conventions
- Use `Feature` + short business value statement.
- Use `Background` only for true shared preconditions; otherwise keep scenarios independent.
- Use `Given/When/Then` consistently; keep steps declarative and user-observable.
- Avoid UI implementation details unless the domain requires them.
- Keep step vocabulary consistent across scenarios (same phrasing for same concept).

## Output format

For each spec file:
- Start with the proposed path as a plain line like `features/<name>.feature`
- Then provide the full `.feature` content.

## Cucumber references (vendored)

Use the vendored Cucumber documentation in `skills/gherkin-bdd-spec-writer/references/cucumber-website-docs/` for conventions and examples.
When you need guidance, search the references with ripgrep patterns like:
- `gherkin`
- `scenario outline`
- `background`
- `data table`
- `tags`
