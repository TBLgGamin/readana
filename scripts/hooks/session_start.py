#!/usr/bin/env python3
"""SessionStart hook: inject the repo's current state so sessions start oriented."""

import json
import subprocess
import sys
from pathlib import Path


def run(args: list[str]) -> str:
    try:
        return subprocess.run(args, capture_output=True, text=True, timeout=20, check=False).stdout.strip()
    except Exception:
        return ""


def main() -> None:
    try:
        sys.stdin.read()
    except Exception:
        pass
    lines: list[str] = []

    dirty = run(["git", "status", "--porcelain"])
    if dirty:
        entries = dirty.splitlines()
        lines.append(f"Working tree has {len(entries)} uncommitted change(s):")
        lines.extend("  " + entry for entry in entries[:15])
        if len(entries) > 15:
            lines.append(f"  … and {len(entries) - 15} more")

    changelog = Path("CHANGELOG.md")
    if changelog.exists():
        text = changelog.read_text(encoding="utf-8", errors="replace")
        unreleased = text.split("## [Unreleased]", 1)[-1].split("\n## ", 1)[0]
        bullets = [line for line in unreleased.splitlines() if line.lstrip().startswith("-")]
        lines.append(f"CHANGELOG [Unreleased] currently lists {len(bullets)} item(s).")

    lines.append(
        "Reminders: gates are `./gradlew spotlessApply detekt testDebugUnitTest test :app:lintDebug`; "
        "commits must be Conventional Commits and stage CHANGELOG.md when production code changes "
        "(hooks enforce both). Skills: /ship, /new-feature, /new-source, /new-module, /deploy-tablet."
    )

    print(json.dumps({
        "hookSpecificOutput": {
            "hookEventName": "SessionStart",
            "additionalContext": "Readana repo state:\n" + "\n".join(lines),
        },
        "suppressOutput": True,
    }))


if __name__ == "__main__":
    main()
