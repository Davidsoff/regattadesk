---
name: gh-issue-to-pr-tdd
description: Implement one GitHub issue end-to-end and open a pull request using a strict TDD flow (Red, Green, Refactor). Use when a user wants a single issue picked up, assigned, implemented on a fresh branch from the latest default branch in an isolated worktree, reviewed for requirement coverage, fully validated against CI-equivalent tests, and submitted as a PR.
---

# GitHub Issue To PR (TDD)

## Overview

Take one issue number and deliver a PR that satisfies the issue with test-first implementation.
Always run in a dedicated worktree, use subagents for Red/Green/Refactor, then run a reviewer subagent before full CI-equivalent validation and PR creation.

## Inputs

- Required: issue number
- Optional: base branch (default repository default branch)

If issue number is missing, ask:
`Which issue number should I implement?`

## Required Agent Files

Before spawning subagents, ensure these files exist:
- `agents/red-agent.md`
- `agents/green-agent.md`
- `agents/refactor-agent.md`
- `agents/reviewer-agent.md`

If any file is missing, create it before continuing.

## Workflow

### 1) Assign the Issue to Yourself

```bash
gh issue edit <ISSUE_NUMBER> --add-assignee "@me"
```

### 2) Read Issue and Acceptance Signals

```bash
gh issue view <ISSUE_NUMBER> --json number,title,body,labels,assignees,url
```

Extract:
- explicit acceptance criteria
- implied constraints
- out-of-scope boundaries

### 3) Create Fresh Worktree From Latest Default Branch

Use branch prefix `codex/`.

```bash
set -euo pipefail
ISSUE=<ISSUE_NUMBER>
BASE=$(gh repo view --json defaultBranchRef --jq '.defaultBranchRef.name')
SLUG=$(gh issue view "$ISSUE" --json title --jq '.title' | tr '[:upper:]' '[:lower:]' | sed -E 's/[^a-z0-9]+/-/g; s/^-+|-+$//g' | cut -c1-40)
BRANCH="codex/issue-${ISSUE}-${SLUG}"
WT="/tmp/issue-${ISSUE}-pr"

git fetch origin "$BASE"
if git worktree list --porcelain | grep -q "worktree $WT$"; then
  git worktree remove --force "$WT"
fi
git worktree add -b "$BRANCH" "$WT" "origin/$BASE"
cd "$WT"
```

### 4) Build the Plan and Start With Tests (TDD)

Create a short plan in this order:
1. Red: failing tests that encode issue requirements
2. Green: minimal code to pass tests
3. Refactor: cleanup while preserving behavior
4. CI-equivalent validation
5. PR creation

### 5) Red/Green/Refactor via Subagents

Spawn one subagent per phase using the prompt files under `agents/`:
- Red phase prompt: `agents/red-agent.md`
- Green phase prompt: `agents/green-agent.md`
- Refactor phase prompt: `agents/refactor-agent.md`

Rules:
- Execute phases strictly in sequence.
- Stop if Red phase does not produce failing tests.
- Stop if Green phase changes tests beyond what is needed to pass.
- Ensure each phase reports changed files and exact test commands run.

### 6) Requirement Coverage Review (Reviewer Agent)

Spawn a reviewer subagent using `agents/reviewer-agent.md`.

Reviewer must:
- compare implementation against issue body and acceptance criteria
- flag missing behavior and regressions
- require fixes before merge if anything is missing

If reviewer finds gaps, loop back through Red/Green/Refactor as needed.

### 7) Run CI-Equivalent Test Suite Locally

Run the same commands used by CI (or closest local equivalent), from repo root:

```bash
set -euo pipefail

# Backend
(cd apps/backend && ./mvnw verify -Dformat.validate)
(cd apps/backend && ./mvnw clean package -DskipTests)
(cd apps/backend && ./mvnw test)
(cd apps/backend && ./mvnw verify -Pintegration || true)
(cd apps/backend && ./mvnw verify -Pcontract || true)

# Frontend
(cd apps/frontend && npm_config_cache="$PWD/.npm-cache" npm ci)
(cd apps/frontend && npm run lint)
(cd apps/frontend && npm run build)
(cd apps/frontend && npm run test || true)
(cd apps/frontend && npm run test:a11y || true)

# Dependency pinning check
test -f apps/frontend/package-lock.json
! grep -E '"\^|"~' apps/frontend/package.json
! grep -qE 'version>\\s*(LATEST|RELEASE)' apps/backend/pom.xml
```

Notes:
- Optional CI jobs (`integration`, `contract`, `frontend test`, `a11y`) are allowed to fail in local validation exactly as CI marks them non-gating.
- Required checks must pass.
- If frontend commands fail with `exit 127` (`vitest: not found`) or `npm ci` cache permission errors (`EPERM` under `~/.npm/_cacache`), clean `apps/frontend/node_modules` and rerun `npm ci` with a repo-local cache:

```bash
(cd apps/frontend && rm -rf node_modules && npm_config_cache="$PWD/.npm-cache" npm ci)
```

### 8) Commit and Push

```bash
cd /tmp/issue-<ISSUE_NUMBER>-pr
git add -A
git commit -m "feat: implement issue #<ISSUE_NUMBER>\n\n- add failing tests first (TDD red)\n- implement minimal behavior to pass (green)\n- refactor and keep tests green"
git push -u origin "$(git rev-parse --abbrev-ref HEAD)"
```

### 9) Open Pull Request

```bash
BASE=${BASE:-$(gh repo view --json defaultBranchRef --jq '.defaultBranchRef.name')}
gh pr create \
  --base "$BASE" \
  --head "$(git rev-parse --abbrev-ref HEAD)" \
  --title "Fix #<ISSUE_NUMBER>: <issue title>" \
  --body-file - <<'EOF'
## Summary
- <what changed>

## Issue
- Closes #<ISSUE_NUMBER>

## TDD Evidence
- Red: <failing tests added>
- Green: <implementation files>
- Refactor: <cleanup>

## Validation
- <required checks passed>
- <optional checks status>
EOF
```

## Output Contract

Always return:
- issue number and URL
- branch name and worktree path
- files changed
- commit SHA(s)
- PR URL
- CI-equivalent command results
- reviewer findings and how they were addressed
