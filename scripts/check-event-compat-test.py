#!/usr/bin/env python3
"""Negative self-test for scripts/check-event-compat.py (S2, blocker E4).

The gate is only meaningful if it can actually FAIL. Historically it compared
the working tree against ``HEAD``; in CI the change is already committed on
``HEAD``, so old == new and the gate was vacuous. This test builds a throwaway
git repo and proves that, with the correct BASE ref, an intentionally
incompatible schema change produces a non-zero exit, while a compatible change
and an unknown base produce zero.

Deterministic and cheap: stdlib + local git only, no network.

Exit 0 if every assertion holds, 1 otherwise.
"""
from __future__ import annotations

import json
import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

CHECKER = Path(__file__).resolve().with_name("check-event-compat.py")
SCHEMA_REL = Path("contracts/events/demo.schema.json")


def git_env() -> dict[str, str]:
    env = dict(os.environ)
    env.update(
        {
            "GIT_AUTHOR_NAME": "ci",
            "GIT_AUTHOR_EMAIL": "ci@example.invalid",
            "GIT_COMMITTER_NAME": "ci",
            "GIT_COMMITTER_EMAIL": "ci@example.invalid",
        }
    )
    return env


def run_git(cwd: Path, *args: str) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        ["git", "-c", "commit.gpgsign=false", *args],
        cwd=cwd,
        capture_output=True,
        text=True,
        env=git_env(),
    )


def write_schema(cwd: Path, schema: dict) -> None:
    path = cwd / SCHEMA_REL
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(schema, indent=2) + "\n", encoding="utf-8")


BASE_SCHEMA = {
    "type": "object",
    "required": ["a", "role"],
    "properties": {
        "a": {"type": "string"},
        "role": {"type": "string", "enum": ["candidate", "employer"]},
    },
}
# incompatible: required 'a' removed + a property type changed.
INCOMPATIBLE_SCHEMA = {
    "type": "object",
    "required": ["role"],
    "properties": {
        "a": {"type": "integer"},
        "role": {"type": "string", "enum": ["candidate", "employer"]},
    },
}
# compatible: only an optional property added.
COMPATIBLE_SCHEMA = {
    "type": "object",
    "required": ["a", "role"],
    "properties": {
        "a": {"type": "string"},
        "role": {"type": "string", "enum": ["candidate", "employer"]},
        "note": {"type": "string"},
    },
}


def run_checker(cwd: Path, base: str | None = None) -> subprocess.CompletedProcess[str]:
    env = dict(os.environ)
    env.pop("EVENT_COMPAT_BASE", None)
    cmd = [sys.executable, str(CHECKER)]
    if base is not None:
        cmd += ["--base", base]
    return subprocess.run(cmd, cwd=cwd, capture_output=True, text=True, env=env)


def main() -> int:
    if not CHECKER.is_file():
        print(f"FAIL: checker not found at {CHECKER}")
        return 1

    tmp = Path(tempfile.mkdtemp(prefix="event-compat-selftest-"))
    failures: list[str] = []

    def check(name: str, condition: bool, detail: str = "") -> None:
        status = "PASS" if condition else "FAIL"
        print(f"  [{status}] {name}{(' -- ' + detail) if detail else ''}")
        if not condition:
            failures.append(name)

    try:
        run_git(tmp, "init", "-q")
        write_schema(tmp, BASE_SCHEMA)
        run_git(tmp, "add", "-A")
        commit = run_git(tmp, "commit", "-q", "-m", "base")
        if commit.returncode != 0:
            print(f"FAIL: could not create base commit: {commit.stderr.strip()}")
            return 1
        base_sha = run_git(tmp, "rev-parse", "HEAD").stdout.strip()

        # --- Scenario A: incompatible change, compared against the correct BASE.
        print("Scenario A: incompatible change vs base sha")
        write_schema(tmp, INCOMPATIBLE_SCHEMA)
        run_git(tmp, "add", "-A")
        run_git(tmp, "commit", "-q", "-m", "breaking")

        res = run_checker(tmp, base=base_sha)
        check(
            "incompatible change -> exit 1 with --base",
            res.returncode == 1,
            f"rc={res.returncode}",
        )
        check("incompatible change -> FAIL reported", "FAIL" in res.stdout, res.stdout.strip())

        # Demonstrates the old, vacuous behaviour: HEAD already contains the change.
        res_head = run_checker(tmp, base="HEAD")
        check(
            "same change vs HEAD -> exit 0 (shows why BASE is required)",
            res_head.returncode == 0,
            f"rc={res_head.returncode}",
        )

        # Env-var path is equivalent to --base.
        env = dict(os.environ)
        env.pop("EVENT_COMPAT_BASE", None)
        env["EVENT_COMPAT_BASE"] = base_sha
        res_env = subprocess.run(
            [sys.executable, str(CHECKER)],
            cwd=tmp,
            capture_output=True,
            text=True,
            env=env,
        )
        check(
            "EVENT_COMPAT_BASE env -> exit 1",
            res_env.returncode == 1,
            f"rc={res_env.returncode}",
        )

        # --- Scenario B: compatible change -> exit 0 vs base.
        print("Scenario B: compatible change vs base sha")
        write_schema(tmp, COMPATIBLE_SCHEMA)
        run_git(tmp, "add", "-A")
        run_git(tmp, "commit", "-q", "-m", "compatible")
        res = run_checker(tmp, base=base_sha)
        check(
            "compatible change -> exit 0",
            res.returncode == 0,
            f"rc={res.returncode}",
        )

        # --- Scenario C: unknown base -> absent schema treated as NEW/compatible.
        print("Scenario C: unknown base -> NEW/compatible")
        res = run_checker(tmp, base="0000000000000000000000000000000000000000")
        check(
            "unknown base -> exit 0 (NEW)",
            res.returncode == 0 and "NEW" in res.stdout,
            f"rc={res.returncode}",
        )

    finally:
        shutil.rmtree(tmp, ignore_errors=True)

    print()
    if failures:
        print(f"event-compat self-test FAILED: {len(failures)} assertion(s): {failures}")
        return 1
    print("event-compat self-test passed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
