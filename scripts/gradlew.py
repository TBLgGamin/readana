#!/usr/bin/env python3
"""Cross-platform shim so pre-commit hooks can invoke the Gradle wrapper on Windows and POSIX."""
import os
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
WRAPPER = os.path.join(ROOT, "gradlew.bat" if os.name == "nt" else "gradlew")

if __name__ == "__main__":
    sys.exit(subprocess.call([WRAPPER, *sys.argv[1:]], cwd=ROOT))
