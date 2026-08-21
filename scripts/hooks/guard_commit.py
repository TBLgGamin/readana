#!/usr/bin/env python3
"""PreToolUse hook on `git commit`: enforce Conventional Commits and CHANGELOG upkeep.

Denies the commit (so Claude can fix and retry) when:
  - the -m message's first line is not a Conventional Commit header, or
  - staged changes touch production code but neither CHANGELOG.md is staged nor the
    message carries a `[skip-changelog]` marker (for pure refactors/chores).
"""

import json
import re
import subprocess
import sys

HEADER = re.compile(r"^(feat|fix|docs|style|refactor|perf|test|build|ci|chore|revert)(\([\w\-/.]+\))?!?: .+")
CODE_PREFIXES = ("app/", "core/", "engine/", "source/", "feature/", "build-logic/")


def staged_files() -> list[str]:
    try:
        out = subprocess.run(
            ["git", "diff", "--cached", "--name-only"],
            capture_output=True, text=True, timeout=30, check=False,
        ).stdout
    except Exception:
        return []
    return [line.strip().replace("\\", "/") for line in out.splitlines() if line.strip()]


def first_message(command: str) -> str | None:
    # Matches -m "..." / -m '...' and the heredoc form -m "$(cat <<'EOF' ... EOF)".
    heredoc = re.search(r"<<\s*'?EOF'?\n(.*?)\nEOF", command, re.DOTALL)
    if heredoc:
        return heredoc.group(1).strip()
    quoted = re.search(r"-m\s+(\"([^\"]+)\"|'([^']+)')", command, re.DOTALL)
    if quoted:
        return (quoted.group(2) or quoted.group(3)).strip()
    return None


def deny(reason: str) -> None:
    print(json.dumps({
        "hookSpecificOutput": {
            "hookEventName": "PreToolUse",
            "permissionDecision": "deny",
            "permissionDecisionReason": reason,
        }
    }))


def main() -> None:
    try:
        payload = json.load(sys.stdin)
    except json.JSONDecodeError:
        return
    command = (payload.get("tool_input") or {}).get("command") or ""
    if not re.search(r"\bgit\b.*\bcommit\b", command) or "--amend" in command:
        return

    problems: list[str] = []
    message = first_message(command)
    if message is not None:
        header = message.splitlines()[0]
        if not HEADER.match(header):
            problems.append(
                f'Commit header "{header}" is not a Conventional Commit. Use '
                "type(scope): summary with type in feat|fix|docs|style|refactor|perf|test|build|ci|chore|revert "
                "(see CONTRIBUTING.md)."
            )

    staged = staged_files()
    touches_code = any(f.startswith(CODE_PREFIXES) for f in staged)
    changelog_staged = any(f == "CHANGELOG.md" for f in staged)
    skip_marker = message is not None and "[skip-changelog]" in message
    if touches_code and not changelog_staged and not skip_marker:
        problems.append(
            "Staged changes touch production code but CHANGELOG.md is not staged. Add an entry under "
            "## [Unreleased] and `git add CHANGELOG.md`, or append `[skip-changelog]` to the commit body "
            "for changes with no user-visible effect."
        )

    if problems:
        deny("Commit blocked by repo conventions:\n- " + "\n- ".join(problems))


if __name__ == "__main__":
    main()
