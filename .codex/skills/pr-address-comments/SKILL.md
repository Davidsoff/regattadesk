---
name: pr-address-comments
description: Address all open review thread comments for a GitHub PR, commit and push the fixes, then resolve the threads. Also fixes any SonarQube findings introduced by those changes and rebases onto master.
license: MIT
metadata:
  author: regattadesk
  version: "1.0"
---

Address all open review comments on a GitHub PR, then commit, push, resolve the threads, and fix any Sonar findings.

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

Note the `headRefName` (the PR branch).

### 3. Fetch all open review threads

```bash
gh api graphql -f query='
query($owner:String!, $repo:String!, $pr:Int!) {
  repository(owner:$owner, name:$repo) {
    pullRequest(number:$pr) {
      reviewThreads(first:50) {
        nodes {
          id
          isResolved
          isOutdated
          comments(first:10) {
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
}' -f owner=Davidsoff -f repo=regattadesk -F pr=<PR>
```

Collect all threads where `isResolved: false` and `isOutdated: false`. If there are none, report "No open review threads." and stop.

Print a summary:
```
Found N open review threads across these files:
  • <file> (line X) — <short description of issue>
  ...
```

### 4. Check out the PR branch

Create a temporary git worktree so the main working tree is unaffected:

```bash
git fetch origin <headRefName>
git worktree add /tmp/pr-<PR>-fix <headRefName>
```

If the worktree already exists, remove and recreate it:
```bash
git worktree remove --force /tmp/pr-<PR>-fix
git worktree add /tmp/pr-<PR>-fix <headRefName>
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
- **Apply the minimal correct fix** — do not refactor unrelated code.
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

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>"
```

Use a bullet for each distinct issue addressed.

### 8. Check for SonarQube findings

Push the branch and wait for the SonarCloud check to complete:

```bash
cd /tmp/pr-<PR>-fix
git push origin <headRefName>
```

Wait up to 2 minutes for SonarCloud to post its report, checking every 30 seconds:
```bash
gh pr checks <PR> 2>&1
```

Once the SonarCloud check has run, inspect the latest SonarCloud comment on the PR:
```bash
gh api repos/Davidsoff/regattadesk/issues/<PR>/comments \
  --jq '[.[] | select(.user.login == "sonarqubecloud[bot]")] | last | .body'
```

If the comment contains "New issue" (e.g., "1 New issue"), fetch the issue details:
```bash
curl -s "https://sonarcloud.io/api/issues/search?componentKeys=Davidsoff_regattadesk&pullRequest=<PR>&statuses=OPEN,CONFIRMED&sinceLeakPeriod=true" \
  | python3 -c "
import json,sys
data=json.load(sys.stdin)
for i in data.get('issues',[]):
    print(i['rule'], i['severity'], i.get('message',''), i['component'], i.get('line',''))
"
```

Fix each finding, amend the existing commit or add a new fixup commit, then push again. Repeat until Sonar reports 0 new issues.

If Sonar is not available or the check hasn't posted yet after waiting, **skip this step** and note it for the user.

### 9. Rebase onto master (if needed)

Check whether the PR branch is already up-to-date with master:
```bash
git fetch origin master
git log --oneline origin/master ^HEAD | head -5
```

If master has new commits not in the PR branch, rebase:
```bash
git rebase origin/master
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

If rebase was not needed, just push normally (already done in step 8).

### 10. Resolve all review threads

For each thread ID collected in step 3, run:
```bash
gh api graphql -f query='
mutation ResolveThread($threadId: ID!) {
  resolveReviewThread(input: {threadId: $threadId}) {
    thread { id isResolved }
  }
}' -f threadId="<threadId>"
```

Confirm each returns `"isResolved": true`.

### 11. Clean up

Remove the temporary worktree:
```bash
git worktree remove /tmp/pr-<PR>-fix
```

### 12. Report

Print a summary:

```
## PR <N> — Review Comments Addressed

**Branch:** <headRefName>
**Threads resolved:** N/N

### Fixes applied
- <file>:<line> — <what was changed>
- ...

### Sonar
- <N> new issues fixed  (or "Quality gate already passing — no new issues")

### Rebase
- Rebased onto master  (or "Already up-to-date with master")

All review threads resolved. ✓
```

---

## Guardrails

- **Never** edit files in the main worktree — always work inside `/tmp/pr-<PR>-fix`.
- **Never** skip a comment because it looks minor; address every open, non-outdated thread.
- **Prefer the reviewer's suggested code** over a different implementation when one is provided.
- **Do not** add unrelated refactoring or cleanup beyond what the comments ask for.
- **Do not** force-push unless a rebase actually happened; use regular push otherwise.
- If a comment is ambiguous, implement the most conservative interpretation and note it in the commit message.
- If a fix would break existing passing tests, flag it to the user before committing.
- Resolve threads only **after** the fixes are committed and pushed.
