# Blocked Issue Query

Use this GraphQL call to detect whether an issue is blocked:

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

Treat the issue as blocked if any `blockedBy.nodes[].state == "OPEN"`.
