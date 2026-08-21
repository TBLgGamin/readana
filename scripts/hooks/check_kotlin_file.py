#!/usr/bin/env python3
"""PostToolUse hook (Write|Edit): fast convention checks on Kotlin files, no Gradle involved.

Blocking (decision=block, Claude must fix immediately):
  - android/androidx imports inside pure-JVM modules (Konsist would fail later anyway)
  - applying the banned org.jetbrains.kotlin.android plugin (AGP 9 built-in Kotlin)
Advisory (additionalContext):
  - plain runCatching in files with suspend functions (use suspendRunCatching)
  - a new NavKey that is not registered in ReadanaNavigation.kt
  - hardcoded literals passed to Text(...) in feature UI files
"""

import json
import re
import sys
from pathlib import Path

PURE_MODULES = (
    "core/common",
    "core/model",
    "core/testing",
    "source/api",
    "source/gutenberg",
    "source/annas",
    "engine/epub",
    "engine/reflow",
)
NAV_REGISTRY = Path("app/src/main/kotlin/dev/readana/navigation/ReadanaNavigation.kt")


def main() -> None:
    try:
        payload = json.load(sys.stdin)
    except json.JSONDecodeError:
        return
    file_path = (payload.get("tool_input") or {}).get("file_path") or ""
    path = file_path.replace("\\", "/")
    if not path.endswith((".kt", ".kts")):
        return
    target = Path(file_path)
    if not target.exists():
        return
    text = target.read_text(encoding="utf-8", errors="replace")
    rel = path.split("readana/", 1)[-1] if "readana/" in path else path

    blockers: list[str] = []
    warnings: list[str] = []

    if any(f"/{m}/src/" in path for m in PURE_MODULES) and re.search(
        r"^import androidx?\.", text, re.MULTILINE
    ):
        blockers.append(
            f"{rel} lives in a pure-JVM module but imports android/androidx. "
            "Move the Android-dependent code to an Android module (core/data, engine/pdf2epub, feature/*) "
            "or drop the import. This is Konsist-enforced and will fail `./gradlew test`."
        )

    if "org.jetbrains.kotlin.android" in text:
        blockers.append(
            f"{rel} references the org.jetbrains.kotlin.android plugin. AGP 9's built-in Kotlin "
            "replaces it; applying it breaks the build. Remove it (see CLAUDE.md)."
        )

    if path.endswith(".kt") and "suspend " in text:
        plain = [
            i + 1
            for i, line in enumerate(text.splitlines())
            if re.search(r"(?<![A-Za-z])runCatching", line) and "suspendRunCatching" not in line
        ]
        if plain:
            warnings.append(
                f"{rel} uses plain runCatching on line(s) {plain} in a file with suspend functions. "
                "Use dev.readana.core.common.coroutines.suspendRunCatching so CancellationException propagates."
            )

    keys = re.findall(r"(?:data\s+)?(?:object|class)\s+(\w+Key)\b[^\n]*:\s*NavKey", text)
    if keys and NAV_REGISTRY.exists() and not path.endswith("ReadanaNavigation.kt"):
        registry = NAV_REGISTRY.read_text(encoding="utf-8", errors="replace")
        missing = [k for k in keys if k not in registry]
        if missing:
            warnings.append(
                f"NavKey(s) {missing} in {rel} are not registered in {NAV_REGISTRY.as_posix()} "
                "(polymorphic serializers module). Register them or back-stack state restoration breaks."
            )

    if "/feature/" in path and "/src/main/" in path:
        hardcoded = [
            i + 1
            for i, line in enumerate(text.splitlines())
            if re.search(r'\bText\(\s*(text\s*=\s*)?"[^"$]', line) and "Preview" not in line
        ]
        if hardcoded:
            warnings.append(
                f"{rel} passes a hardcoded string to Text(...) on line(s) {hardcoded}. "
                "UI strings belong in res/values/strings.xml with the module prefix (previews are exempt "
                "only inside @Preview composables)."
            )

    if blockers:
        print(json.dumps({
            "decision": "block",
            "reason": "Repo convention violation:\n- " + "\n- ".join(blockers + warnings),
        }))
    elif warnings:
        print(json.dumps({
            "hookSpecificOutput": {
                "hookEventName": "PostToolUse",
                "additionalContext": "Convention check for the file just written:\n- " + "\n- ".join(warnings),
            }
        }))


if __name__ == "__main__":
    main()
