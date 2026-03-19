# Documentation Classification

This file records the Phase 5 classification decisions for markdown files that were outside the canonical docs structure when the migration started.

Legend:

- `canonicalized`: the canonical docs copy is retained and the original non-agent source file was removed.
- `staged-copy`: a copy was placed in the canonical docs location and the original was left in place.
- `mapped`: the file was classified and given a canonical target, but the original remains authoritative for now.
- `skip`: the file should remain in place.
- `missing`: the file was listed in the request but is not present in the current workspace.

| Source | Category | Canonical Target | Status | Notes |
|:------|:---------|:-----------------|:-------|:------|
| `.github/copilot-instructions.md` | agent | `docs/agent/copilot-instructions.md` | staged-copy | Repository-specific Copilot instructions. |
| `.github/workflows/README.md` | core | `docs/core/workflows-README.md` | staged-copy | CI and workflow process reference. |
| `.opencode/commands/handoff.md` | agent | `docs/agent/opencode/handoff.md` | staged-copy | Agent command guidance. |
| `.opencode/commands/review.md` | agent | `docs/agent/opencode/review.md` | staged-copy | Agent review guidance. |
| `AGENTS.md` | agent | `docs/agent/AGENTS.md` | staged-copy | Canonical repo-wide agent instructions. |
| `CLAUDE.md` | agent | `docs/agent/CLAUDE.md` | staged-copy | Claude-specific agent guidance. |
| `README.md` | skip | n/a | skip | Root README should stay at repository root. |
| `VISUAL-DEMO.md` | user-docs | `docs/user/VISUAL-DEMO.md` | staged-copy | User-facing demo guide. |
| `apps/AGENTS.md` | agent | `docs/agent/apps/AGENTS.md` | staged-copy | App-level agent guidance. |
| `apps/backend/AGENTS.md` | agent | `docs/agent/apps/backend-AGENTS.md` | staged-copy | Backend-specific agent guidance. |
| `apps/backend/I18N_FORMATTING.md` | core | `docs/core/backend/I18N_FORMATTING.md` | staged-copy | Shared backend formatting contract. |
| `apps/backend/PUBLIC_VERSIONED_ROUTING.md` | core | `docs/core/backend/PUBLIC_VERSIONED_ROUTING.md` | staged-copy | Versioned public routing contract. |
| `apps/backend/README.md` | user-docs | `docs/user/backend-README.md` | staged-copy | Backend developer usage guide. |
| `apps/backend/performance/README.md` | core | `docs/core/backend/performance-README.md` | staged-copy | Performance-gate implementation notes. |
| `apps/backend/src/main/resources/db/migration/README.md` | core | `docs/core/backend/db-migration-README.md` | staged-copy | Shared migration guidance. |
| `apps/frontend/AGENTS.md` | agent | `docs/agent/apps/frontend-AGENTS.md` | staged-copy | Frontend-specific agent guidance. |
| `apps/frontend/I18N_FORMATTING.md` | core | `docs/core/frontend/I18N_FORMATTING.md` | staged-copy | Shared frontend formatting guide. |
| `apps/frontend/README.md` | user-docs | `docs/user/frontend-README.md` | staged-copy | Frontend developer usage guide. |
| `apps/frontend/src/DESIGN_SYSTEM.md` | core | `docs/core/frontend/DESIGN_SYSTEM.md` | staged-copy | Shared frontend design-system reference. |
| `apps/frontend/src/api/README.md` | core | `docs/core/frontend/api-README.md` | staged-copy | Frontend API client conventions. |
| `apps/frontend/src/components/examples/README.md` | core | `docs/core/frontend/components-examples-README.md` | staged-copy | Component example guidance for shared UI. |
| `apps/frontend/src/composables/PWA_USAGE.md` | core | `docs/core/frontend/PWA_USAGE.md` | staged-copy | Shared PWA implementation guidance. |
| `apps/frontend/tests/accessibility/README.md` | core | `docs/core/frontend/accessibility-README.md` | staged-copy | Accessibility test guidance. |
| `docs/CI_QUALITY_GATES.md` | core | `docs/core/CI_QUALITY_GATES.md` | staged-copy | Project-wide CI gate policy. |
| `docs/DEVELOPER_SETUP.md` | user-docs | `docs/user/DEVELOPER_SETUP.md` | staged-copy | Onboarding and local setup guide. |
| `docs/EDGE_SECURITY.md` | core | `docs/core/EDGE_SECURITY.md` | staged-copy | Shared security architecture. |
| `docs/IDENTITY_FORWARDING.md` | core | `docs/core/IDENTITY_FORWARDING.md` | staged-copy | Shared identity contract. |
| `docs/TESTING_STRATEGY.md` | core | `docs/core/TESTING_STRATEGY.md` | staged-copy | Global test matrix and policy. |
| `docs/adr/ADR-0001-line-scan-tile-upload-consistency-without-event-sourcing.md` | adr | `docs/adr/ADR-0001-line-scan-tile-upload-consistency-without-event-sourcing.md` | staged-copy | Existing ADR. |
| `docs/dependencies.md` | core | `docs/core/dependencies.md` | staged-copy | Dependency inventory reference. |
| `docs/dependency-governance.md` | core | `docs/core/dependency-governance.md` | staged-copy | Governance policy shared across BC01 and BC09. |
| `docs/retention-policy.md` | core | `docs/core/retention-policy.md` | staged-copy | Cross-cutting retention policy. |
| `docs/runbooks/README.md` | user-docs | `docs/user/runbooks/README.md` | staged-copy | Runbook index. |
| `docs/runbooks/incident-authentication-failures.md` | user-docs | `docs/user/runbooks/incident-authentication-failures.md` | staged-copy | Operations runbook. |
| `docs/runbooks/incident-public-performance.md` | user-docs | `docs/user/runbooks/incident-public-performance.md` | staged-copy | Operations runbook. |
| `docs/runbooks/incident-service-unavailability.md` | user-docs | `docs/user/runbooks/incident-service-unavailability.md` | staged-copy | Operations runbook. |
| `docs/runbooks/jwt-key-rotation.md` | user-docs | `docs/user/runbooks/jwt-key-rotation.md` | staged-copy | Operational JWT rotation procedure. |
| `docs/runbooks/performance-gates.md` | user-docs | `docs/user/runbooks/performance-gates.md` | staged-copy | Performance gate operations guide. |
| `docs/runbooks/procedure-deployment-rollback.md` | user-docs | `docs/user/runbooks/procedure-deployment-rollback.md` | staged-copy | Deployment rollback procedure. |
| `infra/compose/OBSERVABILITY.md` | core | `docs/core/infra/OBSERVABILITY.md` | staged-copy | Shared observability configuration guide. |
| `infra/compose/README.md` | user-docs | `docs/user/compose-README.md` | staged-copy | Compose usage guide. |
| `infra/compose/SECURITY-GRAFANA.md` | core | `docs/core/infra/SECURITY-GRAFANA.md` | staged-copy | Shared observability security guidance. |
| `pdd/design/database-schema.md` | spec | `docs/specs/pdd-v0.1/design/database-schema.md` | staged-copy | Part of existing design spec set. |
| `pdd/design/detailed-design.md` | spec | `docs/specs/pdd-v0.1/design/detailed-design.md` | staged-copy | Primary v0.1 product spec. |
| `pdd/design/style-guide.md` | spec | `docs/specs/pdd-v0.1/design/style-guide.md` | staged-copy | Product design and accessibility spec. |
| `pdd/idea-honing.md` | spec | `docs/specs/pdd-v0.1/idea-honing.md` | staged-copy | Pre-spec product definition artifact. |
| `pdd/implementation/bc01-platform-and-delivery.md` | spec | `docs/specs/pdd-v0.1/implementation/bc01-platform-and-delivery.md` | staged-copy | Existing bounded-context implementation plan. |
| `pdd/implementation/bc02-identity-and-access.md` | spec | `docs/specs/pdd-v0.1/implementation/bc02-identity-and-access.md` | staged-copy | Existing bounded-context implementation plan. |
| `pdd/implementation/bc03-core-regatta-management.md` | spec | `docs/specs/pdd-v0.1/implementation/bc03-core-regatta-management.md` | staged-copy | Existing bounded-context implementation plan. |
| `pdd/implementation/bc04-rules-scheduling-and-draw.md` | spec | `docs/specs/pdd-v0.1/implementation/bc04-rules-scheduling-and-draw.md` | staged-copy | Existing bounded-context implementation plan. |
| `pdd/implementation/bc05-public-experience-and-delivery.md` | spec | `docs/specs/pdd-v0.1/implementation/bc05-public-experience-and-delivery.md` | staged-copy | Existing bounded-context implementation plan. |
| `pdd/implementation/bc06-operator-capture-and-line-scan.md` | spec | `docs/specs/pdd-v0.1/implementation/bc06-operator-capture-and-line-scan.md` | staged-copy | Existing bounded-context implementation plan. |
| `pdd/implementation/bc07-results-and-adjudication.md` | spec | `docs/specs/pdd-v0.1/implementation/bc07-results-and-adjudication.md` | staged-copy | Existing bounded-context implementation plan. |
| `pdd/implementation/bc08-finance-and-payments.md` | spec | `docs/specs/pdd-v0.1/implementation/bc08-finance-and-payments.md` | staged-copy | Existing bounded-context implementation plan. |
| `pdd/implementation/bc09-operability-hardening-and-quality.md` | spec | `docs/specs/pdd-v0.1/implementation/bc09-operability-hardening-and-quality.md` | staged-copy | Existing bounded-context implementation plan. |
| `pdd/implementation/issues/README.md` | spec | `docs/specs/pdd-v0.1/issues/README.md` | staged-copy | Existing issue export instructions. |
| `pdd/implementation/issues/coverage-map.md` | spec | `docs/specs/pdd-v0.1/issues/coverage-map.md` | staged-copy | Existing ticket coverage map. |
| `pdd/implementation/plan.md` | spec | `docs/specs/pdd-v0.1/implementation/plan.md` | staged-copy | Existing implementation plan. |
| `pdd/research/image-format-and-storage-notes.md` | spec | `docs/specs/pdd-v0.1/research/image-format-and-storage-notes.md` | staged-copy | Research context pack for BC06 storage decisions. |
| `pdd/rough-idea.md` | spec | `docs/specs/pdd-v0.1/rough-idea.md` | staged-copy | Historical concept document retained for reference. |
| `pdd/summary.md` | spec | `docs/specs/pdd-v0.1/summary.md` | staged-copy | Summary/index for the legacy PDD set. |
| `apps/frontend/BC06-005-SUMMARY.md` | spec | `docs/specs/pdd-v0.1/implementation/evidence/BC06-005-SUMMARY.md` | missing | File not present in current workspace. |

## Notes

- Root `README.md` remains in place by design.
- Agent instruction files remain in their original locations and were not removed; no canonical `docs/agent/` copy is retained.
- Non-agent files listed here were canonicalized into `docs/` and their original markdown files were removed at user request.
- Some files listed in earlier workspace summaries are not present in the current filesystem snapshot; those are recorded as `missing` instead of being copied.
