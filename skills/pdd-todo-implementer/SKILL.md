---
name: pdd-todo-implementer
description: Execute pending documentation action items from todo.md one at a time by making scoped edits in pdd/ and updating task status after each completion. Use when asked to implement already-documented PDD fixes without discovering new issues.
---

# PDD Todo Implementer

Execute this workflow to complete documentation tasks listed in `todo.md`.

## Workflow

1. Read `todo.md` and identify incomplete tasks (`- [ ]`).
2. Process tasks one at a time, in listed order.
3. For each task:
- understand requirement, location, and expected outcome
- make a concrete step-by-step plan for that single task
- apply only the documentation edits needed for that task
- update `todo.md` task to complete (`- [x]`) and add a brief `Resolved: YYYY-MM-DD - ...` note
4. Repeat until no incomplete tasks remain.
5. After all tasks are complete:
- clean `todo.md` by removing completed task entries
- stage and commit changes with a descriptive commit message

## Scope and constraints

- Edit only files in `pdd/` and `todo.md`.
- Do not implement product features or code from the PDD.
- Do not modify files outside `pdd/` and `todo.md` unless user explicitly changes scope.
- Treat `pdd/idea-honing.md` and `pdd/rough-idea.md` as read-only references.
- Fix only issues explicitly listed in `todo.md`; do not search for or fix additional inconsistencies.

## Execution rules

- Keep edits minimal and directly tied to the active task.
- If a task references missing/incorrect locations, still resolve only the stated issue using best matching section.
- Preserve existing document structure unless the task explicitly requires restructuring.
- Use deterministic completion notes in `todo.md` so follow-up runs can audit what changed.
