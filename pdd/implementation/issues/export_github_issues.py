#!/usr/bin/env python3
"""Export bounded-context issue YAML files to GitHub issues via gh CLI."""

from __future__ import annotations

import argparse
import glob
import json
import os
import shlex
import subprocess
import sys
import tempfile
from typing import Any

try:
    import yaml
except ModuleNotFoundError as exc:
    raise SystemExit(
        "PyYAML is required. Install with: pip install pyyaml"
    ) from exc


def load_issue_files(issues_dir: str) -> list[dict[str, Any]]:
    files = sorted(glob.glob(os.path.join(issues_dir, "*.issues.yaml")))
    if not files:
        raise SystemExit(f"No '*.issues.yaml' files found in {issues_dir}")

    all_entries: list[dict[str, Any]] = []
    seen_ids: set[str] = set()

    for path in files:
        with open(path, "r", encoding="utf-8") as handle:
            data = yaml.safe_load(handle)

        if not isinstance(data, dict) or "tickets" not in data:
            raise SystemExit(f"Invalid file format in {path}: missing 'tickets'")

        bc = data.get("bounded_context", {})
        bc_id = bc.get("id", "UNKNOWN")
        bc_name = bc.get("name", "Unknown")

        for ticket in data["tickets"]:
            ticket_id = ticket.get("id")
            title = ticket.get("title")
            body = ticket.get("body")
            labels = ticket.get("labels", [])

            if not ticket_id or not title or not body:
                raise SystemExit(
                    f"Invalid ticket in {path}: 'id', 'title', and 'body' are required"
                )
            if ticket_id in seen_ids:
                raise SystemExit(f"Duplicate ticket id detected: {ticket_id}")
            seen_ids.add(ticket_id)

            all_entries.append(
                {
                    "file": path,
                    "bounded_context_id": bc_id,
                    "bounded_context_name": bc_name,
                    "ticket": ticket,
                    "labels": labels,
                }
            )

    all_entries.sort(key=lambda x: (x["file"], x["ticket"]["id"]))
    return all_entries


def _run_checked(cmd: list[str], *, verbose: bool) -> subprocess.CompletedProcess[str]:
    if verbose:
        print("RUN:", shlex.join(cmd))
    return subprocess.run(cmd, check=True, text=True, capture_output=True)


def _label_color(label: str) -> str:
    # Keep deterministic colors by label namespace to make scanning easier in GitHub UI.
    if label.startswith("type:"):
        return "0E8A16"
    if label.startswith("area:"):
        return "1D76DB"
    if label.startswith("priority:"):
        return "D93F0B"
    if label.startswith("milestone:"):
        return "5319E7"
    return "BFD4F2"


def ensure_labels_exist(
    *,
    labels_needed: list[str],
    repo: str | None,
    dry_run: bool,
    verbose: bool,
) -> None:
    if not labels_needed:
        return

    if dry_run:
        print(f"DRY RUN: would ensure {len(labels_needed)} labels exist")
        for label in labels_needed:
            color = _label_color(label)
            cmd = ["gh", "label", "create", label, "--color", color]
            if repo:
                cmd.extend(["--repo", repo])
            cmd.extend(["--description", "Auto-created by issue exporter"])
            print("DRY RUN:", shlex.join(cmd))
        return

    list_cmd = ["gh", "label", "list", "--limit", "1000", "--json", "name"]
    if repo:
        list_cmd.extend(["--repo", repo])
    result = _run_checked(list_cmd, verbose=verbose)
    existing = {item["name"] for item in json.loads(result.stdout)}

    missing = [label for label in labels_needed if label not in existing]
    if not missing:
        return

    for label in missing:
        color = _label_color(label)
        create_cmd = ["gh", "label", "create", label, "--color", color]
        if repo:
            create_cmd.extend(["--repo", repo])
        create_cmd.extend(["--description", "Auto-created by issue exporter"])
        _run_checked(create_cmd, verbose=verbose)


def render_issue_body(entry: dict[str, Any]) -> str:
    t = entry["ticket"]
    depends_on = t.get("depends_on", []) or []
    plan_coverage = t.get("plan_coverage", []) or []
    nfr_coverage = t.get("nfr_coverage", []) or []

    def _as_text(value: Any) -> str:
        if isinstance(value, dict):
            if len(value) == 1:
                key, val = next(iter(value.items()))
                return f"{key}: {val}"
            return ", ".join(f"{k}: {v}" for k, v in value.items())
        if isinstance(value, list):
            return ", ".join(_as_text(x) for x in value)
        return str(value)

    lines: list[str] = []
    lines.append("## Metadata")
    lines.append(f"- Ticket ID: `{t['id']}`")
    lines.append(
        f"- Bounded Context: `{entry['bounded_context_id']}` {entry['bounded_context_name']}"
    )
    lines.append(
        "- Dependencies: "
        + (", ".join(f"`{d}`" for d in depends_on) if depends_on else "None")
    )
    lines.append(
        "- Plan Coverage: "
        + (", ".join(_as_text(x) for x in plan_coverage) if plan_coverage else "Not specified")
    )
    lines.append(
        "- Non-Functional Coverage: "
        + (
            ", ".join(_as_text(x) for x in nfr_coverage)
            if nfr_coverage
            else "Not specified"
        )
    )
    lines.append("")
    lines.append(t["body"].rstrip())
    lines.append("")
    return "\n".join(lines)


def run_gh_issue_create(
    *,
    title: str,
    body: str,
    labels: list[str],
    repo: str | None,
    dry_run: bool,
    verbose: bool,
) -> None:
    cmd = ["gh", "issue", "create", "--title", title]
    if repo:
        cmd.extend(["--repo", repo])

    for label in labels:
        cmd.extend(["--label", label])

    if dry_run:
        print("DRY RUN:", " ".join(cmd), "--body-file <tmp>")
        preview = "\n".join(body.splitlines()[:20])
        print(preview)
        if len(body.splitlines()) > 20:
            print("... (truncated preview)\n")
        else:
            print()
        return

    with tempfile.NamedTemporaryFile("w", encoding="utf-8", delete=False) as tmp:
        tmp.write(body)
        tmp_path = tmp.name

    try:
        create_cmd = cmd + ["--body-file", tmp_path]
        if verbose:
            print("RUN:", shlex.join(create_cmd))
        subprocess.run(create_cmd, check=True)
    finally:
        os.unlink(tmp_path)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--issues-dir",
        default=os.path.dirname(os.path.abspath(__file__)),
        help="Directory containing *.issues.yaml files",
    )
    parser.add_argument(
        "--repo",
        default=None,
        help="Optional GitHub repo in owner/name format. Defaults to current gh repo.",
    )
    parser.add_argument(
        "--apply",
        action="store_true",
        help="Create issues via gh CLI. Without this flag, runs in dry-run mode.",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Explicitly force dry-run mode (default when --apply is not set).",
    )
    parser.add_argument(
        "--label-prefix",
        default="",
        help="Optional prefix added to every label, e.g. 'regattadesk/'",
    )
    parser.add_argument(
        "--verbose",
        action="store_true",
        help="Print the gh command before each create call.",
    )
    args = parser.parse_args()

    entries = load_issue_files(args.issues_dir)
    all_labels = sorted(
        {
            f"{args.label_prefix}{label}"
            for entry in entries
            for label in entry.get("labels", [])
        }
    )

    dry_run = args.dry_run or not args.apply
    mode = "DRY RUN" if dry_run else "APPLY"
    print(f"Mode: {mode}")
    print(f"Found {len(entries)} tickets")
    ensure_labels_exist(
        labels_needed=all_labels,
        repo=args.repo,
        dry_run=dry_run,
        verbose=args.verbose,
    )

    for entry in entries:
        t = entry["ticket"]
        labels = [f"{args.label_prefix}{x}" for x in entry.get("labels", [])]
        body = render_issue_body(entry)
        run_gh_issue_create(
            title=t["title"],
            body=body,
            labels=labels,
            repo=args.repo,
            dry_run=dry_run,
            verbose=args.verbose,
        )

    print("Done")
    return 0


if __name__ == "__main__":
    sys.exit(main())
