#!/usr/bin/env python3
"""Event JSON-Schema backward-compatibility gate (S2, docs/architecture.md section 7.3).

For every ``contracts/events/*.schema.json`` in the working tree this script
fetches the previously committed version with ``git show <BASE>:<path>``.

The BASE ref is configurable and MUST point at the change's *base*, not at the
current checkout:

  * CLI:  ``--base <ref>``
  * env:  ``EVENT_COMPAT_BASE``
  * default: ``HEAD``

Using ``HEAD`` is only correct for a local, uncommitted working tree. In CI the
change is already committed on ``HEAD``, so passing the pull request base sha
(or ``github.event.before`` on push, falling back to ``origin/main``) is what
makes the gate able to fail. See ``scripts/check-event-compat-test.py`` for a
negative self-test that proves the gate detects an incompatible change.

A missing version at BASE means the schema is brand new and is treated as
compatible.

It FAILS (non-zero exit) on these incompatible changes:

  * a property that was ``required`` is removed;
  * a ``type`` is changed;
  * an ``enum`` value is removed;
  * a newly added property is listed in ``required``;
  * a ``const`` value is changed.

Backward-compatible additions are allowed: new optional properties, new enum
values, and added optional constraints.

stdlib only (argparse / json / os / subprocess / pathlib).
"""
from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
from pathlib import Path

SCHEMA_GLOB = "*.schema.json"
SCHEMA_DIR = Path("contracts/events")
ENV_BASE = "EVENT_COMPAT_BASE"
DEFAULT_BASE = "HEAD"


def resolve_base(cli_base: str | None) -> str:
    """Precedence: CLI ``--base`` > env ``EVENT_COMPAT_BASE`` > ``HEAD``."""
    if cli_base:
        return cli_base
    env_base = os.environ.get(ENV_BASE, "").strip()
    if env_base:
        return env_base
    return DEFAULT_BASE


def load_base_schema(rel_path: str, base: str) -> tuple[dict | None, str | None]:
    """Return (schema, None) from ``git show <base>:<path>`` or (None, reason)."""
    try:
        proc = subprocess.run(
            ["git", "show", f"{base}:{rel_path}"],
            capture_output=True,
            text=True,
        )
    except FileNotFoundError:
        return None, "git not found on PATH"
    if proc.returncode != 0:
        return None, proc.stderr.strip() or f"not present at {base}"
    try:
        return json.loads(proc.stdout), None
    except json.JSONDecodeError as exc:
        return None, f"{base} version is not valid JSON: {exc}"


def as_type_set(value: object) -> set[str]:
    if value is None:
        return set()
    if isinstance(value, list):
        return {str(v) for v in value}
    return {str(value)}


def compare(old: dict, new: dict, path: str, issues: list[str]) -> None:
    """Collect incompatible changes between two schema fragments at ``path``."""
    # const
    if "const" in old and old.get("const") != new.get("const"):
        issues.append(f"{path}: const changed {old.get('const')!r} -> {new.get('const')!r}")

    # type (a removed type is a relaxation; only a changed/added incompatible type fails)
    old_types = as_type_set(old.get("type"))
    new_types = as_type_set(new.get("type"))
    if old_types and new_types and old_types != new_types:
        issues.append(f"{path}: type changed {sorted(old_types)} -> {sorted(new_types)}")

    # enum
    old_enum = old.get("enum")
    new_enum = new.get("enum")
    if isinstance(old_enum, list) and isinstance(new_enum, list):
        removed = [v for v in old_enum if v not in new_enum]
        if removed:
            issues.append(f"{path}: enum value(s) removed: {removed!r}")

    # required / properties
    old_props = old.get("properties") or {}
    new_props = new.get("properties") or {}
    old_required = old.get("required") or []
    new_required = new.get("required") or []

    for name in old_required:
        if name not in new_props:
            issues.append(f"{path}: required property '{name}' removed")

    for name in new_required:
        if name not in old_required:
            issues.append(f"{path}: newly required property '{name}'")

    # recurse into nested object properties
    if isinstance(old_props, dict) and isinstance(new_props, dict):
        for name, old_sub in old_props.items():
            new_sub = new_props.get(name)
            if isinstance(old_sub, dict) and isinstance(new_sub, dict):
                compare(old_sub, new_sub, f"{path}.properties.{name}", issues)

    # recurse into array items
    old_items = old.get("items")
    new_items = new.get("items")
    if isinstance(old_items, dict) and isinstance(new_items, dict):
        compare(old_items, new_items, f"{path}.items", issues)


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--base",
        default=None,
        help=(
            "Git ref/sha to compare against (default: $"
            + ENV_BASE
            + f", else {DEFAULT_BASE!r})."
        ),
    )
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    base = resolve_base(args.base)

    if not SCHEMA_DIR.is_dir():
        print(f"No {SCHEMA_DIR}/ directory; event-contract check is a no-op.")
        return 0

    files = sorted(SCHEMA_DIR.glob(SCHEMA_GLOB))
    if not files:
        print(f"No {SCHEMA_DIR}/{SCHEMA_GLOB} schemas; event-contract check is a no-op.")
        return 0

    print(f"Comparing {SCHEMA_DIR.as_posix()}/ against base '{base}'.")
    failures = 0
    for schema_file in files:
        rel = schema_file.as_posix()
        try:
            new_schema = json.loads(schema_file.read_text(encoding="utf-8"))
        except json.JSONDecodeError as exc:
            print(f"FAIL    {rel}")
            print(f"          - working-tree schema is not valid JSON: {exc}")
            failures += 1
            continue

        old_schema, reason = load_base_schema(rel, base)
        if old_schema is None:
            print(f"NEW     {rel} (no '{base}' version{': ' + reason if reason else ''} -> compatible)")
            continue

        issues: list[str] = []
        compare(old_schema, new_schema, rel, issues)
        if issues:
            failures += 1
            print(f"FAIL    {rel}")
            for issue in issues:
                print(f"          - {issue}")
        else:
            print(f"OK      {rel}")

    print()
    if failures:
        print(f"Event-contract check FAILED: {failures} schema(s) with incompatible changes.")
        return 1
    print("Event-contract check passed: all changes are backward-compatible.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
