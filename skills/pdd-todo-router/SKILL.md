---
name: pdd-todo-router
description: "Route PDD documentation requests to the correct skill: use pdd-todo-analyzer for reviewing pdd/ and generating new todo items, and use pdd-todo-implementer for executing existing pending todo.md items. Use when user intent is ambiguous or when they ask to run the full analyze/implement workflow."
---

# PDD Todo Router

Use this skill to choose the correct downstream skill before acting.

## Routing decision

1. Choose `pdd-todo-analyzer` when the user asks to:
- review/analyze `pdd/`
- find inconsistencies, gaps, ambiguities, or outdated docs
- generate or refresh `todo.md` tasks

2. Choose `pdd-todo-implementer` when the user asks to:
- execute, implement, or resolve tasks in `todo.md`
- process pending checkboxes one by one
- apply documentation changes listed in existing todo items

## Tie-break rules

- If request includes both analysis and implementation, run in this order:
1. `pdd-todo-analyzer`
2. `pdd-todo-implementer`
- If `todo.md` has pending items and request is to analyze, prioritize `pdd-todo-implementer` constraints (do not re-analyze until pending tasks are done).
- If intent is unclear, ask one clarifying question: `Do you want me to find new todo items, execute current todo items, or both in sequence?`

## Guardrails

- Do not directly perform full analysis/implementation here when one of the specialized skills applies.
- Delegate to the selected skill and follow its scope and constraints.
