#!/usr/bin/env python3
"""Export bounded-context issue YAML files to GitHub issues via gh CLI."""

from __future__ import annotations

import argparse
import glob
import json
import os
import re
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


def _run_graphql(
    *,
    query: str,
    variables: dict[str, str] | None,
    verbose: bool,
) -> dict[str, Any]:
    cmd = ["gh", "api", "graphql", "-f", f"query={query}"]
    for key, value in (variables or {}).items():
        cmd.extend(["-F", f"{key}={value}"])

    result = _run_checked(cmd, verbose=verbose)
    payload = json.loads(result.stdout)
    errors = payload.get("errors", [])
    if errors:
        message = "; ".join(error.get("message", "Unknown GraphQL error") for error in errors)
        raise SystemExit(f"GraphQL request failed: {message}")
    return payload.get("data", {})


TICKET_ID_REGEX = re.compile(r"^- Ticket ID:\s*`([^`]+)`", re.MULTILINE)


def _extract_ticket_id_from_body(body: str | None) -> str | None:
    if not body:
        return None
    match = TICKET_ID_REGEX.search(body)
    if not match:
        return None
    return match.group(1)


def _is_real_issue_id(issue_id: str | None) -> bool:
    return bool(issue_id) and not str(issue_id).startswith("DRYRUN:")


def _issue_preference(issue: dict[str, Any]) -> tuple[int, int]:
    state = issue.get("state", "")
    number = int(issue.get("number", 0))
    # Prefer open issues when duplicate Ticket IDs exist, then prefer newest number.
    return (1 if state == "OPEN" else 0, number)


def _gh_list_limit() -> str:
    # Allow callers to tune list size for larger repositories.
    raw_value = os.getenv("GH_LIST_LIMIT", "5000")
    if not raw_value.isdigit() or int(raw_value) <= 0:
        raise SystemExit("GH_LIST_LIMIT must be a positive integer")
    return raw_value


def build_existing_ticket_issue_index(
    *,
    repo: str | None,
    verbose: bool,
) -> dict[str, dict[str, Any]]:
    cmd = [
        "gh",
        "issue",
        "list",
        "--state",
        "all",
        "--limit",
        _gh_list_limit(),
        "--json",
        "id,number,title,body,state,url",
    ]
    if repo:
        cmd.extend(["--repo", repo])

    result = _run_checked(cmd, verbose=verbose)
    issues = json.loads(result.stdout)

    index: dict[str, dict[str, Any]] = {}
    duplicate_counts: dict[str, int] = {}
    for issue in issues:
        ticket_id = _extract_ticket_id_from_body(issue.get("body"))
        if not ticket_id:
            continue

        duplicate_counts[ticket_id] = duplicate_counts.get(ticket_id, 0) + 1
        current = index.get(ticket_id)
        if current is None or _issue_preference(issue) > _issue_preference(current):
            index[ticket_id] = {
                "id": issue["id"],
                "number": issue["number"],
                "url": issue["url"],
                "state": issue["state"],
            }

    duplicates = sorted(ticket_id for ticket_id, count in duplicate_counts.items() if count > 1)
    if duplicates:
        print(
            "WARNING: duplicate Ticket IDs found on GitHub; using preferred open/newest issue for:",
            ", ".join(duplicates),
        )

    return index


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

    list_cmd = ["gh", "label", "list", "--limit", _gh_list_limit(), "--json", "name"]
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
) -> dict[str, Any] | None:
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
        return None

    with tempfile.NamedTemporaryFile("w", encoding="utf-8", delete=False) as tmp:
        tmp.write(body)
        tmp_path = tmp.name

    try:
        create_cmd = cmd + ["--body-file", tmp_path]
        result = _run_checked(create_cmd, verbose=verbose)

        create_output = "\n".join(
            [x for x in [result.stdout.strip(), result.stderr.strip()] if x]
        )
        issue_locator: str | None = None

        url_match = re.search(r"https://github\.com/\S+/issues/\d+", create_output)
        if url_match:
            issue_locator = url_match.group(0)
        else:
            # Fallback for alternate gh output formats that print issue numbers.
            number_match = re.search(r"(?:^|\s)#?(\d+)(?:\s|$)", create_output)
            if number_match:
                issue_locator = number_match.group(1)

        if not issue_locator:
            raise SystemExit(
                "Failed to parse created issue reference from gh output. "
                "Expected either an issue URL or issue number. "
                f"Output was: {create_output!r}"
            )

        view_cmd = ["gh", "issue", "view", issue_locator, "--json", "id,number,url,state"]
        if repo:
            view_cmd.extend(["--repo", repo])
        view_result = _run_checked(view_cmd, verbose=verbose)
        issue_data = json.loads(view_result.stdout)
        return {
            "id": issue_data["id"],
            "number": issue_data["number"],
            "url": issue_data["url"],
            "state": issue_data["state"],
        }
    finally:
        os.unlink(tmp_path)


def fetch_blocked_by_ids(*, issue_id: str, verbose: bool) -> set[str]:
    query = """
query($id: ID!) {
  node(id: $id) {
    ... on Issue {
      blockedBy(first: 100) {
        nodes {
          id
        }
      }
    }
  }
}
"""
    data = _run_graphql(query=query, variables={"id": issue_id}, verbose=verbose)
    node = data.get("node") or {}
    blocked_nodes = (((node.get("blockedBy") or {}).get("nodes")) or [])
    return {item["id"] for item in blocked_nodes if item and item.get("id")}


def add_blocked_by_link(*, issue_id: str, blocking_issue_id: str, verbose: bool) -> None:
    mutation = """
mutation($issueId: ID!, $blockingIssueId: ID!) {
  addBlockedBy(input: {issueId: $issueId, blockingIssueId: $blockingIssueId}) {
    issue { number }
    blockingIssue { number }
  }
}
"""
    _run_graphql(
        query=mutation,
        variables={"issueId": issue_id, "blockingIssueId": blocking_issue_id},
        verbose=verbose,
    )


def _record_missing_dependencies(
    *,
    missing_pairs: set[tuple[str, str]],
    dependent_ticket_id: str,
    dependencies: list[str],
) -> None:
    for dependency_ticket_id in dependencies:
        missing_pairs.add((dependent_ticket_id, dependency_ticket_id))


def _ensure_blocked_cache(
    *,
    blocked_cache: dict[str, set[str]],
    dependent_issue_id: str | None,
    verbose: bool,
) -> None:
    if not _is_real_issue_id(dependent_issue_id):
        return
    if dependent_issue_id in blocked_cache:
        return
    blocked_cache[dependent_issue_id] = fetch_blocked_by_ids(
        issue_id=dependent_issue_id,
        verbose=verbose,
    )


def _is_existing_dependency_link(
    *,
    dependent_issue_id: str | None,
    blocking_issue_id: str | None,
    blocked_cache: dict[str, set[str]],
) -> bool:
    return bool(
        _is_real_issue_id(dependent_issue_id)
        and _is_real_issue_id(blocking_issue_id)
        and blocking_issue_id in blocked_cache.get(dependent_issue_id, set())
    )


def _issue_display(issue: dict[str, Any] | None) -> str:
    number = (issue or {}).get("number")
    return f"#{number}" if number is not None else "<new issue>"


def _print_dry_run_dependency_link(
    *,
    dependent_ticket_id: str,
    dependency_ticket_id: str,
    dependent_issue: dict[str, Any] | None,
    blocking_issue: dict[str, Any] | None,
) -> None:
    dep_display = _issue_display(dependent_issue)
    block_display = _issue_display(blocking_issue)
    print(
        "DRY RUN: would link dependency "
        f"{dependent_ticket_id} ({dep_display}) blocked by "
        f"{dependency_ticket_id} ({block_display})"
    )


def _apply_single_dependency(
    *,
    dependent_ticket_id: str,
    dependency_ticket_id: str,
    dependent_issue: dict[str, Any] | None,
    dependent_issue_id: str | None,
    ticket_issue_index: dict[str, dict[str, Any]],
    dry_run: bool,
    verbose: bool,
    missing_pairs: set[tuple[str, str]],
    blocked_cache: dict[str, set[str]],
) -> int:
    blocking_issue = ticket_issue_index.get(dependency_ticket_id)
    if not blocking_issue:
        missing_pairs.add((dependent_ticket_id, dependency_ticket_id))
        return 0

    blocking_issue_id = blocking_issue.get("id")
    if _is_existing_dependency_link(
        dependent_issue_id=dependent_issue_id,
        blocking_issue_id=blocking_issue_id,
        blocked_cache=blocked_cache,
    ):
        return 0

    if dry_run:
        _print_dry_run_dependency_link(
            dependent_ticket_id=dependent_ticket_id,
            dependency_ticket_id=dependency_ticket_id,
            dependent_issue=dependent_issue,
            blocking_issue=blocking_issue,
        )
        return 1

    if dependent_issue is None or dependent_issue_id is None:
        missing_pairs.add((dependent_ticket_id, dependency_ticket_id))
        return 0
    if blocking_issue_id is None:
        missing_pairs.add((dependent_ticket_id, dependency_ticket_id))
        return 0
    if dependent_issue_id == blocking_issue_id:
        print(f"WARNING: skipping self dependency for {dependent_ticket_id}")
        return 0

    add_blocked_by_link(
        issue_id=dependent_issue_id,
        blocking_issue_id=blocking_issue_id,
        verbose=verbose,
    )
    blocked_cache.setdefault(dependent_issue_id, set()).add(blocking_issue_id)
    return 1


def apply_dependency_links(
    *,
    entries: list[dict[str, Any]],
    ticket_issue_index: dict[str, dict[str, Any]],
    dry_run: bool,
    verbose: bool,
) -> None:
    missing_pairs: set[tuple[str, str]] = set()
    blocked_cache: dict[str, set[str]] = {}
    linked_count = 0

    for entry in entries:
        ticket = entry["ticket"]
        dependent_ticket_id = ticket["id"]
        dependencies = ticket.get("depends_on", []) or []
        if not dependencies:
            continue

        dependent_issue = ticket_issue_index.get(dependent_ticket_id)
        if not dependent_issue and not dry_run:
            _record_missing_dependencies(
                missing_pairs=missing_pairs,
                dependent_ticket_id=dependent_ticket_id,
                dependencies=dependencies,
            )
            continue

        dependent_issue_id = (dependent_issue or {}).get("id")
        _ensure_blocked_cache(
            blocked_cache=blocked_cache,
            dependent_issue_id=dependent_issue_id,
            verbose=verbose,
        )

        for dependency_ticket_id in dependencies:
            linked_count += _apply_single_dependency(
                dependent_ticket_id=dependent_ticket_id,
                dependency_ticket_id=dependency_ticket_id,
                dependent_issue=dependent_issue,
                dependent_issue_id=dependent_issue_id,
                ticket_issue_index=ticket_issue_index,
                dry_run=dry_run,
                verbose=verbose,
                missing_pairs=missing_pairs,
                blocked_cache=blocked_cache,
            )

    if missing_pairs:
        for dependent_ticket_id, dependency_ticket_id in sorted(missing_pairs):
            print(
                "WARNING: dependency not linked because ticket was not found on GitHub by "
                f"metadata Ticket ID: {dependent_ticket_id} -> {dependency_ticket_id}"
            )

    action = "would link" if dry_run else "linked"
    print(f"Dependencies: {action} {linked_count} relationship(s)")


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
    existing_ticket_issue_index = build_existing_ticket_issue_index(
        repo=args.repo,
        verbose=args.verbose,
    )
    print(
        "Found "
        f"{len(existing_ticket_issue_index)} existing GitHub issue(s) with Ticket ID metadata"
    )
    ensure_labels_exist(
        labels_needed=all_labels,
        repo=args.repo,
        dry_run=dry_run,
        verbose=args.verbose,
    )

    created_ticket_issue_index: dict[str, dict[str, Any]] = {}
    for entry in entries:
        t = entry["ticket"]
        ticket_id = t["id"]
        existing_issue = existing_ticket_issue_index.get(ticket_id)
        if existing_issue:
            print(
                "SKIP: ticket already exists on GitHub "
                f"({ticket_id} -> #{existing_issue['number']}, state={existing_issue['state']})"
            )
            continue

        labels = [f"{args.label_prefix}{x}" for x in entry.get("labels", [])]
        body = render_issue_body(entry)
        created_issue = run_gh_issue_create(
            title=t["title"],
            body=body,
            labels=labels,
            repo=args.repo,
            dry_run=dry_run,
            verbose=args.verbose,
        )
        if created_issue:
            created_ticket_issue_index[ticket_id] = created_issue

    ticket_issue_index = dict(existing_ticket_issue_index)
    if dry_run:
        for entry in entries:
            ticket_id = entry["ticket"]["id"]
            ticket_issue_index.setdefault(
                ticket_id,
                {"id": f"DRYRUN:{ticket_id}", "number": None, "url": "", "state": "OPEN"},
            )
    else:
        ticket_issue_index.update(created_ticket_issue_index)

    apply_dependency_links(
        entries=entries,
        ticket_issue_index=ticket_issue_index,
        dry_run=dry_run,
        verbose=args.verbose,
    )

    print("Done")
    return 0


if __name__ == "__main__":
    sys.exit(main())
