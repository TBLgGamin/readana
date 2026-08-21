#!/usr/bin/env python3
"""Stop hook: auto-format the repository whenever a turn changed formatable files.

Runs `gradlew spotlessApply` only when git reports modified/untracked .kt/.kts/.toml/
.yml/.yaml/.md/.xml files, so quiet turns cost nothing. Prints a systemMessage summary.
"""

import json
import os
import subprocess
import sys

EXTENSIONS = (".kt", ".kts", ".toml", ".yml", ".yaml", ".md", ".xml")


def porcelain() -> list[str]:
    out = subprocess.run(
        ["git", "status", "--porcelain"],
        capture_output=True, text=True, timeout=30, check=False,
    ).stdout
    return [line for line in out.splitlines() if line.strip()]


def main() -> None:
    try:
        sys.stdin.read()  # payload unused; drain it
    except Exception:
        pass
    try:
        dirty = [line for line in porcelain() if line[3:].strip().strip('"').endswith(EXTENSIONS)]
    except Exception:
        return
    if not dirty:
        return

    wrapper = os.path.abspath("gradlew.bat" if os.name == "nt" else "gradlew")
    result = subprocess.run(
        [wrapper, "-q", "spotlessApply"],
        capture_output=True, text=True, timeout=560, check=False,
    )
    if result.returncode == 0:
        print(json.dumps({
            "systemMessage": f"spotlessApply ran over {len(dirty)} changed file(s) — repo formatted.",
            "suppressOutput": True,
        }))
    else:
        tail = (result.stderr or result.stdout or "").strip()[-700:]
        print(json.dumps({
            "decision": "block",
            "reason": "spotlessApply FAILED after this turn's changes — fix before finishing:\n" + tail,
        }))


if __name__ == "__main__":
    main()
