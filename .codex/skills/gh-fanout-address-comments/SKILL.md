---
name: gh-fanout-address-comments
description: Orchestrate comment resolution across many open GitHub pull requests in one run. Use when you need to gather open PRs and delegate each PR to a subagent that runs the gh-address-comments skill for that PR.
---

# GH Fanout Address Comments

## Overview

Gather all open pull requests for the current repository, then spawn one subagent per PR to run the [gh-address-comments](/Users/david/.codex/skills/gh-address-comments/SKILL.md) workflow for that PR.

## Workflow

1. Verify GH authentication.

```bash
gh auth status
```

If auth fails, stop and ask the user to run `gh auth login`.

2. Gather open PRs.

```bash
gh pr list --state open --limit 200 --json number,title,headRefName,author,url
```

3. If no open PRs exist, report "No open PRs" and stop.

4. Summarize the PR queue for the user and begin delegation.

5. Spawn subagents with bounded parallelism (default: 3 at a time).

For each PR, give the subagent:
- The PR number
- The requirement to run the `gh-address-comments` process for that PR
- A reminder to handle only that PR

Subagent instruction template:

```text
Use [$gh-address-comments](/Users/david/.codex/skills/gh-address-comments/SKILL.md) for PR #<N>.

Scope:
- Work only on PR #<N>.
- Address the PR comments per the skill workflow.
- Commit and push fixes to the PR branch.
- Report blockers immediately.

Return:
- PR number
- What was changed
- Commit SHA(s)
- Final check status
- Remaining unresolved comments/threads (if any)
```

6. As subagents complete, collect outcomes and continue until all PRs are processed.

7. Provide a final aggregate report with one line per PR:
- `PR #<N>`
- `status: completed | blocked | no-op`
- `commits: <sha list or none>`
- `checks: pass | fail | pending`
- `notes: <blocker or summary>`

## Guardrails

- Do not skip PRs unless the user explicitly asks for filtering.
- Do not let one subagent edit multiple PRs.
- Stop delegation for a PR if auth, permissions, or branch protection blocks progress; report the blocker.
- If rate limits occur, reduce parallelism to 1 and retry.
- Keep all reporting concise and PR-specific.
