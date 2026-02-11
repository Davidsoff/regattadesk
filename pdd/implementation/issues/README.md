# GitHub Issue Export Backlog

This folder contains implementation-ready ticket backlogs grouped by bounded context.

## Files
- `bc01-platform-and-delivery.issues.yaml`
- `bc02-identity-and-access.issues.yaml`
- `bc03-core-regatta-management.issues.yaml`
- `bc04-rules-scheduling-and-draw.issues.yaml`
- `bc05-public-experience-and-delivery.issues.yaml`
- `bc06-operator-capture-and-line-scan.issues.yaml`
- `bc07-results-and-adjudication.issues.yaml`
- `bc08-finance-and-payments.issues.yaml`
- `bc09-operability-hardening-and-quality.issues.yaml`
- `coverage-map.md` (traceability from plan requirements to ticket IDs)

Each ticket includes:
- `id`, `title`, `labels`, `depends_on`
- `plan_coverage`, `nfr_coverage`
- `body` (fully detailed implementation-ready GitHub issue markdown)

## Export to GitHub Issues
Use the provided exporter script:

```bash
python3 pdd/implementation/issues/export_github_issues.py --dry-run
python3 pdd/implementation/issues/export_github_issues.py --apply
```

Optional flags:

```bash
python3 pdd/implementation/issues/export_github_issues.py --apply --repo owner/repo
python3 pdd/implementation/issues/export_github_issues.py --apply --label-prefix regattadesk/
```

## Notes
- `--dry-run` prints issue titles, labels, dependencies, and generated body previews without creating issues.
- `--apply` requires `gh` CLI authentication.
- Export order is deterministic by file name, then ticket ID.
