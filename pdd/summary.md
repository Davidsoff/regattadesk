Version: v2
Last Updated: 2026-02-06
Author: RegattaDesk Team

# Artifacts produced
- rough-idea.md
- idea-honing.md
- research/image-format-and-storage-notes.md
- design/detailed-design.md
- design/style-guide.md
- implementation/plan.md
- summary.md

# Browser support matrix
- Desktop:
  - Chrome: current stable and current-1
  - Firefox: current stable and current-1
  - Safari: current stable major version
  - Edge: current stable major version
- Mobile:
  - iOS Safari: current iOS major version
  - Chrome for Android: current stable major version
  - Samsung Internet: current stable major version
- Support duration policy:
  - Browser matrix is reviewed quarterly.
  - Current and current-1 support windows are maintained throughout each quarter; changes are announced in release notes.

# Demo mode specification (Out of scope for v0.1)
- Status:
  - Deferred to post-v0.1.
  - `/demo` and `POST /demo/reset` are not part of the v0.1 implementation scope.
- Access method:
  - Dedicated environment URL: `/demo`
  - Login uses a pre-provisioned demo staff account and a demo public session endpoint.
- Sample data set:
  - 1 sample regatta with 3 blocks, 12 events, and ~120 entries.
  - Includes representative domain statuses: entered, dns, dnf, withdrawn_after_draw, dsq, excluded.
  - Includes derived workflow/result states: pending approval, under_investigation, provisional, official.
  - Includes sample invoices, penalties, and audit log entries.
- Reset behavior:
  - Automatic full reset every 24 hours at 00:00 UTC.
  - Manual reset endpoint for admins in demo only: `POST /demo/reset`.
  - Reset clears all user edits and restores baseline sample data snapshot.
- Feature limitations:
  - Outbound email/webhook delivery is disabled (logged as simulated sends).
  - Payment marking is simulated; no real payment provider integration.
  - Data export is watermarked as demo and expires after 24 hours.
  - Operator upload storage is capped and purged on each reset.

# Next steps
- Implement event store + core aggregates + projections scaffold.
- Implement public session + versions + SSE to validate public delivery early.
- Implement draw + bib pools + publish revisions.
- Implement operator token + offline PWA + marker linking.
- Implement investigations and approvals with results revisioning.
- Implement design tokens + table primitives per design/style-guide.md.
- Add observability + hardening + runbooks.
