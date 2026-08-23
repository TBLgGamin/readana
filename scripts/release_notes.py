#!/usr/bin/env python3
"""Print the CHANGELOG section for one version, for use as GitHub release notes.

The changelog is the record of what changed; `gh release --generate-notes` would instead list
commit subjects, which say far less. Usage:

    python scripts/release_notes.py v0.1.0 > notes.md

Falls back to the Unreleased section when the version has no section of its own yet, so a release
cut before the changelog was rolled over still says something useful.
"""

from __future__ import annotations

import argparse
import pathlib
import re
import sys

CHANGELOG = pathlib.Path(__file__).resolve().parent.parent / "CHANGELOG.md"

# "## [1.2.3] - 2026-08-23" or "## [Unreleased]"
HEADING = re.compile(r"^## \[([^\]]+)\]")


def section(text: str, wanted: str) -> str | None:
    """Returns the body under the heading for `wanted`, without the heading itself."""
    lines = text.splitlines()
    start: int | None = None
    for index, line in enumerate(lines):
        match = HEADING.match(line)
        if match is None:
            continue
        if start is not None:
            # The next heading ends the section we were collecting.
            return "\n".join(lines[start:index]).strip() or None
        if match.group(1).lower() == wanted.lower():
            start = index + 1
    if start is None:
        return None
    return "\n".join(lines[start:]).strip() or None


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("version", help="tag or version, e.g. v0.1.0 or 0.1.0")
    parser.add_argument(
        "--changelog", type=pathlib.Path, default=CHANGELOG, help="path to CHANGELOG.md"
    )
    args = parser.parse_args()

    text = args.changelog.read_text(encoding="utf-8")
    version = args.version.removeprefix("v")

    body = section(text, version) or section(text, "Unreleased")
    if body is None:
        print(f"no changelog section for {args.version} or Unreleased", file=sys.stderr)
        return 1

    # The changelog is full of en dashes and arrows, and Windows still defaults stdout to cp1252.
    sys.stdout.reconfigure(encoding="utf-8")
    print(body)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
