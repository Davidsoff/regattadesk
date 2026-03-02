---
name: gh-fanout-issue-to-pr
description: Find all open GitHub issues that are unassigned and not blocked, then process them in parallel by spawning one subagent per issue that runs the gh-issue-to-pr-tdd skill. Use when a user wants bulk issue-to-PR automation with bounded parallelism and per-issue isolation.
---

# GitHub Fanout Issue To PR

## Overview

Queue issue implementation at scale while preserving one-issue-per-worker isolation.
Filter to issues that are actionable now: open, unassigned, and not blocked.

## Parallelism

Default max parallelism: `5`.

## Workflow

### 1) Verify GitHub Auth

```bash
gh auth status
```

If auth fails, stop and tell the user to run `gh auth login`.

### 2) Get Repo Coordinates

```bash
OWNER=$(gh repo view --json owner --jq '.owner.login')
REPO=$(gh repo view --json name --jq '.name')
```

### 3) List Open Unassigned Issues

```bash
gh issue list --state open --assignee "" --limit 200 --json number,title,url
```

### 4) Check Whether an Issue Is Blocked (Required API Call)

Use GitHub GraphQL `Issue.blockedBy` and keep only issues with zero open blockers:

```bash
gh api graphql -f query='
query($owner:String!, $repo:String!, $number:Int!) {
  repository(owner:$owner, name:$repo) {
    issue(number:$number) {
      blockedBy(first:50) {
        nodes { number state title url }
      }
    }
  }
}' -f owner="$OWNER" -f repo="$REPO" -F number=<ISSUE_NUMBER>
```

Filter rule:
- `blocked` if any `blockedBy.nodes[].state == "OPEN"`
- `not blocked` otherwise

### 5) Build Execution Queue

Queue each issue that is:
- open
- unassigned
- not blocked (per API above)

If queue is empty, report: `No open unassigned unblocked issues.`

### 6) Prepare Worktree Per Queued Issue

For each issue `<N>`:

```bash
BASE=$(gh repo view --json defaultBranchRef --jq '.defaultBranchRef.name')
git fetch origin "$BASE"
WT="/tmp/issue-${N}-pr"
if git worktree list --porcelain | grep -q "worktree $WT$"; then
  git worktree remove --force "$WT"
fi
git worktree add "$WT" "origin/$BASE"
```

The per-issue worker will create its own branch from this fresh default-branch worktree.

### 7) Spawn Workers (Max 5 Parallel)

Spawn one subagent per issue with bounded concurrency (`5` at a time).

Worker instruction template:

```text
Use `$gh-issue-to-pr-tdd` for issue #<N>.

Run inside this dedicated worktree:
- /tmp/issue-<N>-pr

Scope:
- Work only on issue #<N>.
- Follow the full TDD Red/Green/Refactor flow.
- Assign issue to yourself, implement, review, run CI-equivalent tests, and create a PR.
- Use a worktree-local npm cache for frontend dependency install (`npm_config_cache="$PWD/apps/frontend/.npm-cache"`) to avoid `~/.npm` permission/cache issues.
- If frontend test commands fail with `vitest: not found` or other `exit 127` errors, run a clean frontend install before retrying tests.
- Do not edit files outside /tmp/issue-<N>-pr.
- Explicitly mention blockers immediately.

Return:
- issue number
- status (completed | blocked | no-op)
- branch and PR URL
- commit SHA(s)
- test/CI-equivalent summary
```

### 8) Collect Results and Cleanup

As workers complete:
- collect outcome per issue
- remove `/tmp/issue-<N>-pr` worktree

```bash
git worktree remove --force /tmp/issue-<N>-pr
```

### 9) Final Aggregate Report

Report one line per issue:
- `issue #<N>`
- `status: completed | blocked | no-op`
- `pr: <url or none>`
- `commits: <sha list or none>`
- `notes: <summary or blocker>`

## Guardrails

- Never process blocked issues.
- Never let a worker handle multiple issues.
- Keep parallelism at `5` unless user explicitly changes it.
- If GitHub rate limiting starts, reduce parallelism to `1` and retry.
