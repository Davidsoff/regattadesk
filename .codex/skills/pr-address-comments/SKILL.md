---
name: pr-address-comments
description: Address all open review thread comments for a GitHub PR, commit and push the fixes, wait for all CI checks to finish, fix any new findings, then resolve the threads. Also handles SonarQube findings and rebases onto the base branch.
license: MIT
metadata:
  author: regattadesk
  version: "1.1"
---

# pr-address-comments

Address all open review comments on a GitHub PR, then commit, push, wait for all CI checks to pass, fix any new findings, and finally resolve the threads.

**Input**: A PR number. If not provided, ask for it.

---

## Steps

### 1. Resolve input

If the user did not provide a PR number, ask:
> "Which PR number should I address review comments for?"

### 2. Fetch PR metadata

```bash
gh pr view <PR> --json number,title,headRefName,baseRefName
```

Also capture repository coordinates once so later API calls are portable:

```bash
OWNER=$(gh repo view --json owner --jq '.owner.login')
REPO=$(gh repo view --json name --jq '.name')
```

Note `headRefName` (the PR branch) and `baseRefName` (the PR base branch).

### 3. Fetch all open review threads

```bash
cursor=""
while :; do
  if [[ -n "$cursor" ]]; then
    cursor_field=(-F cursor="$cursor")
  else
    cursor_field=(-F cursor=null)
  fi

  result=$(gh api graphql -f query='
  query($owner:String!, $repo:String!, $pr:Int!, $cursor:String) {
    repository(owner:$owner, name:$repo) {
      pullRequest(number:$pr) {
        reviewThreads(first:50, after:$cursor) {
          pageInfo { hasNextPage endCursor }
          nodes {
            id
            isResolved
            isOutdated
            comments(first:100) {
              nodes {
                id
                body
                path
                line
                author { login }
                url
              }
            }
          }
        }
      }
    }
  }' -f owner="$OWNER" -f repo="$REPO" -F pr=<PR> "${cursor_field[@]}")

  # Process this page's threads here.

  has_next=$(printf '%s' "$result" | jq -r '.data.repository.pullRequest.reviewThreads.pageInfo.hasNextPage')
  cursor=$(printf '%s' "$result" | jq -r '.data.repository.pullRequest.reviewThreads.pageInfo.endCursor')
  [[ "$has_next" == "true" ]] || break
done
```

Collect all threads where `isResolved: false` (including both current and outdated threads). If there are none, report "No open review threads." and stop.

For implementation work in step 6, focus on non-outdated (`isOutdated: false`) threads. Still keep outdated thread IDs in the working list so they are explicitly resolved in step 12.

Print a summary:

```text
Found N open review threads across these files:
  • <file> (line X) — <short description of issue>
  ...
```

### 4. Check out the PR branch

Create a temporary git worktree so the main working tree is unaffected, and fail fast on setup errors:

```bash
set -euo pipefail

WORKTREE_DIR="/tmp/pr-<PR>-fix"

if git worktree list --porcelain | grep -q "worktree $WORKTREE_DIR$"; then
  git worktree remove --force "$WORKTREE_DIR" || {
    echo "Failed to remove existing worktree at $WORKTREE_DIR" >&2
    exit 1
  }
fi

git fetch origin <headRefName> || {
  echo "Failed to fetch origin/<headRefName>" >&2
  exit 1
}

git worktree add "$WORKTREE_DIR" <headRefName> || {
  echo "Failed to create worktree at $WORKTREE_DIR" >&2
  exit 1
}

cd "$WORKTREE_DIR"
```

All subsequent file edits happen inside `/tmp/pr-<PR>-fix`.

### 5. Understand the affected files

Before making any changes, read every file mentioned across all the open threads. Understand the surrounding context for each comment.

Also read:
- Any config files referenced in the comments
- Related files that the comments reference by name

### 6. Implement all fixes

Work through each non-outdated open thread comment systematically:

- **Read the comment body carefully** to understand what change is required.
- **Apply the minimal correct fix**; do not refactor unrelated code.
- If a comment includes a `suggestion` code block, use that as the implementation unless there is a clear reason not to.
- If two comments require changes to the same file, batch them into a single edit pass on that file.
- Do not add code changes solely for outdated threads unless they still reflect a real issue in current code.
- After all edits, verify each changed file reads correctly.

### 7. Commit the changes

```bash
cd /tmp/pr-<PR>-fix
git add <changed files>
git commit -m "fix: address PR review comments

<one line per addressed comment, e.g.:>
- Switch authelia middleware to @docker provider
- Add Authelia access-control rule for /grafana
- Fix URL examples to use localhost.local
- Add trap EXIT for temp file cleanup in test script
- Capture and assert stderr in Test 1
- Redirect error diagnostics to stderr (Sonar S7677)
"
```

Use a bullet for each distinct issue addressed.

### 8. Push and wait for all checks

Push the branch:

```bash
cd /tmp/pr-<PR>-fix
git push origin <headRefName>
```

Then poll until every CI check has a final status (not `pending` or `in_progress`). Poll every 30 seconds, with a configurable timeout (use a conservative default such as 30 minutes):

```bash
gh pr checks <PR>
```

A check is still running if its status is `pending` or `in_progress`. Keep polling until all checks show `pass`, `fail`, or `skipped`.

Print the final check statuses once they settle.

### 9. Handle SonarQube findings

Inspect the latest SonarCloud comment on the PR:

```bash
gh api repos/$OWNER/$REPO/issues/<PR>/comments \
  --jq '[.[] | select(.user.login == "sonarqubecloud[bot]")] | last | .body'
```

If the comment contains "New issue" (e.g., "1 New issue"), fetch the details from the public SonarCloud API:

```bash
SONAR_COMPONENT_KEY_PRIMARY="${OWNER}_${REPO}"
SONAR_COMPONENT_KEY_FALLBACK="${OWNER}-${REPO}"
SONAR_RESPONSE=$(curl -s "https://sonarcloud.io/api/issues/search?componentKeys=${SONAR_COMPONENT_KEY_PRIMARY}&pullRequest=<PR>&statuses=OPEN,CONFIRMED&sinceLeakPeriod=true")
if [[ "$(printf '%s' "$SONAR_RESPONSE" | jq -r '.errors | length // 0')" != "0" ]]; then
  SONAR_RESPONSE=$(curl -s "https://sonarcloud.io/api/issues/search?componentKeys=${SONAR_COMPONENT_KEY_FALLBACK}&pullRequest=<PR>&statuses=OPEN,CONFIRMED&sinceLeakPeriod=true")
fi
printf '%s' "$SONAR_RESPONSE" \
  | jq -r '.issues[] | [.rule, .severity, (.message // ""), .component, (.line // "")] | @tsv'
```

Fix each finding, add a new commit, push, and return to step 8 to wait for checks again. Repeat until Sonar reports 0 new issues, up to a maximum of 3 iterations. If issues persist after 3 attempts, stop and report remaining findings.

If Sonar is not available or did not post a comment, **skip this step** and note it for the user.

### 10. Confirm all checks pass

Once Sonar is clean, confirm every required check is green:

```bash
gh pr checks <PR>
```

If any check has status `fail`:
- Use `gh pr checks <PR>` to identify the failing workflow/check name.
- Resolve the run id via `gh run list` (for example `gh run list --branch <headRefName> --limit 20 --json databaseId,name,status,conclusion`).
- Read failure logs with `gh run view <run-id> --log-failed`.
- Determine whether the failure is caused by our changes or is pre-existing.
- If caused by our changes: fix the issue, commit, push, and return to step 8.
- If pre-existing (existed before our first push): note it in the final report and proceed.

### 11. Rebase onto the base branch (if needed)

Check whether the PR branch is already up-to-date with `<baseRefName>`:

```bash
git fetch origin <baseRefName>
git log --oneline origin/<baseRefName> ^HEAD | head -5
```

If `<baseRefName>` has new commits not in the PR branch, rebase:

```bash
git rebase origin/<baseRefName>
```

Resolve any conflicts by:
1. Reading the conflicting file to understand both sides
2. Keeping content from both sides unless they are truly mutually exclusive
3. Staging resolved files with `git add`
4. Running `git rebase --continue`

After a successful rebase, force-push:

```bash
git push --force-with-lease origin <headRefName>
```

Then return to step 8 and wait for CI checks on the rebased branch before proceeding.

If rebase was not needed, no extra push is required (already pushed in step 8).

### 12. Resolve all review threads

For **every** thread ID collected in step 3, run:

```bash
gh api graphql -f query='
mutation ResolveThread($threadId: ID!) {
  resolveReviewThread(input: {threadId: $threadId}) {
    thread { id isResolved }
  }
}' -f threadId="<threadId>"
```

Confirm each returns `"isResolved": true`.

### 13. Clean up

Remove the temporary worktree:

```bash
git worktree remove /tmp/pr-<PR>-fix
```

### 14. Report

Print a summary:

```text
## PR <N> — Review Comments Addressed

**Branch:** <headRefName>
**Threads resolved:** N/N

### Fixes applied
- <file>:<line> — <what was changed>
- ...

### CI Checks
- All checks passed  (or list any pre-existing failures)

### Sonar
- <N> new issues fixed  (or "Quality gate passing — no new issues")

### Rebase
- Rebased onto <baseRefName>  (or "Already up-to-date with <baseRefName>")

All review threads resolved. ✓
```

---

## Guardrails

- Ensure your GitHub token/app has Repository `Contents: Read and Write` permission before using `resolveReviewThread` / `unresolveReviewThread`; pull-request-only scopes can fail with "Resource not accessible by integration".
- **Never** edit files in the main worktree; always work inside `/tmp/pr-<PR>-fix`.
- **Never** skip a thread because it looks minor; fix all open non-outdated threads and resolve all open outdated threads.
- **Prefer the reviewer's suggested code** over a different implementation when one is provided.
- **Do not** add unrelated refactoring or cleanup beyond what the comments ask for.
- **Do not** force-push unless a rebase actually happened; use regular push otherwise.
- If a comment is ambiguous, implement the most conservative interpretation and note it in the commit message.
- If a fix would break existing passing tests, flag it to the user before committing.
- Resolve threads only **after** all CI checks pass.
- If the workflow exits early, still run `git worktree remove --force /tmp/pr-<PR>-fix` so stale worktrees are not left behind.
