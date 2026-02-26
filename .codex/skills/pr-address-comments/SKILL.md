---
name: pr-address-comments
description: Address all open review thread comments for a GitHub PR, commit and push the fixes, wait for all CI checks and CodeRabbit to finish, fix any new findings, then resolve the threads. Also handles SonarQube findings and rebases onto the base branch.
license: MIT
metadata:
  author: regattadesk
  version: "1.1"
---

# pr-address-comments

Address all open review comments on a GitHub PR, then commit, push, wait for all CI checks and CodeRabbit to pass, fix any new findings, and finally resolve the threads.

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
cursor=null
while :; do
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
  }' -f owner="$OWNER" -f repo="$REPO" -F pr=<PR> -f cursor="$cursor")

  # Process this page's threads here.

  has_next=$(printf '%s' "$result" | jq -r '.data.repository.pullRequest.reviewThreads.pageInfo.hasNextPage')
  cursor=$(printf '%s' "$result" | jq -r '.data.repository.pullRequest.reviewThreads.pageInfo.endCursor')
  [[ "$has_next" == "true" ]] || break
done
```

Collect all threads where `isResolved: false` and `isOutdated: false`. If there are none, report "No open review threads." and stop.

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

Work through each open thread comment systematically:

- **Read the comment body carefully** to understand what change is required.
- **Apply the minimal correct fix**; do not refactor unrelated code.
- If a comment includes a `suggestion` code block, use that as the implementation unless there is a clear reason not to.
- If two comments require changes to the same file, batch them into a single edit pass on that file.
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
SONAR_COMPONENT_KEY="${OWNER}_${REPO}"
curl -s "https://sonarcloud.io/api/issues/search?componentKeys=${SONAR_COMPONENT_KEY}&pullRequest=<PR>&statuses=OPEN,CONFIRMED&sinceLeakPeriod=true" \
  | python3 -c "
import json,sys
data=json.load(sys.stdin)
for i in data.get('issues',[]):
    print(i['rule'], i['severity'], i.get('message',''), i['component'], i.get('line',''))
"
```

Fix each finding, add a new commit, push, and return to step 8 to wait for checks again. Repeat until Sonar reports 0 new issues, up to a maximum of 3 iterations. If issues persist after 3 attempts, stop and report remaining findings.

If Sonar is not available or did not post a comment, **skip this step** and note it for the user.

### 10. Handle CodeRabbit review

Check whether CodeRabbit has posted or updated a review comment on the PR's latest commit:

```bash
gh api repos/$OWNER/$REPO/issues/<PR>/comments \
  --jq '[.[] | select(.user.login == "coderabbitai[bot]")] | last | .body'
```

Also check for any new CodeRabbit review threads opened since the last push:

```bash
cursor=null
while :; do
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
              nodes { id body path line author { login } createdAt url }
            }
          }
        }
      }
    }
  }' -f owner="$OWNER" -f repo="$REPO" -F pr=<PR> -f cursor="$cursor")

  # Process this page's threads here.

  has_next=$(printf '%s' "$result" | jq -r '.data.repository.pullRequest.reviewThreads.pageInfo.hasNextPage')
  cursor=$(printf '%s' "$result" | jq -r '.data.repository.pullRequest.reviewThreads.pageInfo.endCursor')
  [[ "$has_next" == "true" ]] || break
done
```

Filter for threads where `isResolved: false`, `isOutdated: false`, and the author login is `coderabbitai[bot]`.

**If CodeRabbit opened new review threads:**
- Add these to the working list of open threads.
- Implement the fixes the same way as in step 6.
- Commit, push, and return to step 8 to wait for all checks again.
- Repeat until CodeRabbit raises no new unresolved threads on the latest push, up to a maximum of 3 iterations. If new threads persist after 3 attempts, stop and report them.

**If CodeRabbit's summary comment says "Review skipped" (bot user detected):**
- This is normal for automated pushes; no action required.

**If CodeRabbit's summary contains actionable issues but no thread:**
- Treat each bullet point as a comment to address, implement the fixes, and re-push.

### 11. Confirm all checks pass

Once both Sonar and CodeRabbit are clean, confirm every required check is green:

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

### 12. Rebase onto the base branch (if needed)

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

### 13. Resolve all review threads

For **every** thread ID collected across steps 3 and 10 (original threads + any new CodeRabbit threads), run:

```bash
gh api graphql -f query='
mutation ResolveThread($threadId: ID!) {
  resolveReviewThread(input: {threadId: $threadId}) {
    thread { id isResolved }
  }
}' -f threadId="<threadId>"
```

Confirm each returns `"isResolved": true`.

### 14. Clean up

Remove the temporary worktree:

```bash
git worktree remove /tmp/pr-<PR>-fix
```

### 15. Report

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

### CodeRabbit
- No new threads raised  (or "<N> new threads addressed and resolved")

### Rebase
- Rebased onto <baseRefName>  (or "Already up-to-date with <baseRefName>")

All review threads resolved. ✓
```

---

## Guardrails

- Ensure your GitHub token/app has Repository `Contents: Read and Write` permission before using `resolveReviewThread` / `unresolveReviewThread`; pull-request-only scopes can fail with "Resource not accessible by integration".
- **Never** edit files in the main worktree; always work inside `/tmp/pr-<PR>-fix`.
- **Never** skip a comment because it looks minor; address every open, non-outdated thread.
- **Prefer the reviewer's suggested code** over a different implementation when one is provided.
- **Do not** add unrelated refactoring or cleanup beyond what the comments ask for.
- **Do not** force-push unless a rebase actually happened; use regular push otherwise.
- If a comment is ambiguous, implement the most conservative interpretation and note it in the commit message.
- If a fix would break existing passing tests, flag it to the user before committing.
- Resolve threads only **after** all CI checks pass and CodeRabbit raises no new issues.
- Wait for CodeRabbit to finish before resolving; it may open new threads on the latest commit.
- If the workflow exits early, still run `git worktree remove --force /tmp/pr-<PR>-fix` so stale worktrees are not left behind.
