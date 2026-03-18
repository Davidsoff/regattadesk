# Ticket Coverage Map

## Functional Coverage (Plan Steps -> Ticket IDs)
| Plan requirement | Ticket IDs |
| --- | --- |
| Step 1 | BC01-001, BC01-002, BC01-003 |
| Step 2 | BC02-001, BC02-002 |
| Step 3 | BC03-001, BC03-002 |
| Step 4 | BC03-003 |
| Step 5 | BC03-004 |
| Step 6 | BC02-003, BC02-004 |
| Step 7 | BC05-001, BC02-004 |
| Step 8 | BC05-002 |
| Step 9 | BC05-006, BC06-005 |
| Step 10 | BC05-007 |
| Step 11 | BC05-003 |
| Step 12 | BC04-001, BC04-002 |
| Step 13 | BC04-003 |
| Step 14 | BC04-003 |
| Step 15 | BC08-001, BC08-002 |
| Step 16 | BC06-001, BC06-002 |
| Step 17 | BC06-003, BC06-004, BC06-006 |
| Step 18 | BC06-005 |
| Step 19 | BC06-004 |
| Step 20 | BC07-001, BC07-002 |
| Step 21 | BC05-004 |
| Step 22 | BC05-001, BC05-003, BC05-005, BC02-004, BC07-002 |
| Step 23 | BC09-001 |
| Step 24 | BC09-002, BC09-003 |
| Step 25 | BC09-004 |

## Non-Functional Coverage
| Non-functional area | Ticket IDs |
| --- | --- |
| Canonical Docker Compose runtime and complete in-stack dependencies | BC01-002 |
| Dependency baseline, pinning, and weekly vulnerability scanning | BC01-003, BC09-004 |
| Security/identity cookie/JWT/role constraints | BC02-001, BC02-002, BC02-003, BC02-004 |
| Indefinite audit retention/event traceability | BC03-001, BC03-002 |
| Draw reproducibility and post-draw immutability | BC04-001, BC04-003 |
| Public cache policy and immutable versioned delivery | BC05-001, BC05-002 |
| SSE deterministic IDs, reconnect policy, per-client caps | BC05-003 |
| Public WCAG, design-system consistency | BC05-006 |
| i18n/timezone/date format and printable output constraints | BC05-007 |
| Operator offline reliability and high-contrast defaults | BC06-005 |
| Line-scan auth boundaries and retention-pruning safeguards | BC06-003, BC06-006 |
| Reversible adjudication and results revision consistency | BC07-001, BC07-002 |
| Finance role-scoped auditable bulk updates | BC08-001, BC08-002 |
| Observability, hardening, load validation, runbooks | BC09-001, BC09-002, BC09-003 |
| Test matrix + CI quality gate enforcement | BC09-004 |
