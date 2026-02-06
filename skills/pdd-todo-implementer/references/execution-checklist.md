# Per-task Execution Checklist

For each pending todo item:

1. Confirm target files are within `pdd/` or `todo.md`.
2. Extract acceptance criteria from:
- `Issue`
- `Expected Outcome`
- `Impact` (for prioritization/context)
3. Apply edits only for the active item.
4. Update that item to complete:
- change `- [ ]` to `- [x]`
- set `status: complete`
- add `Resolved: YYYY-MM-DD - <brief summary>`
5. Validate no unrelated files were modified.

## End-of-run checklist

1. Confirm no pending checkboxes remain in `todo.md`.
2. Remove completed task entries from `todo.md`.
3. Stage all intended files.
4. Commit with a descriptive message.
