---
name: pdd-todo-analyzer
description: Analyze Product Design Documents (PDD) and produce actionable documentation todo items in todo.md. Use when asked to review a pdd/ folder for inconsistencies, gaps, ambiguities, or outdated information and generate execution-ready tasks for another agent.
---

# PDD Todo Analyzer

Execute this workflow to analyze PDD documentation and update `todo.md` with actionable tasks.

## Workflow

1. Check `/todo.md` for unresolved tasks.
2. If any task is incomplete (`- [ ]`), stop immediately and do not analyze `pdd/`.
3. If all tasks are complete, review all editable files in `pdd/` for:
- inconsistencies
- missing requirements or specifications
- ambiguities that can block implementation
- outdated references or versions
4. Treat `pdd/idea-honing.md` and `pdd/rough-idea.md` as read-only historical references.
5. Add only new findings to `/todo.md` as pending tasks using the template in `references/todo-item-template.md`.
6. Keep tasks specific and executable by another LLM: include what to fix, where, and expected outcome.
7. If assumptions are needed:
- create `pdd/assumptions.md` and record each assumption with rationale
- if assumptions are too risky, create `pdd/questions.md` containing a numbered list of blocking questions

## Rules

- Make no code or feature implementation changes.
- Edit only analysis/reporting artifacts (`todo.md`, `pdd/assumptions.md`) unless user explicitly expands scope.
- Do not fix documentation directly in this step; only report tasks.
- Use best-practice judgment to resolve minor ambiguity in analysis notes, but still produce explicit todo items for required edits.
