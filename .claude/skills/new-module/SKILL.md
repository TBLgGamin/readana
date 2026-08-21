---
name: new-module
description: Create a new Gradle module (core/engine/library) in Readana using the convention plugins. Use when code needs a new home that is not a feature screen or a book source.
---

# Add a module

1. Pick the thinnest convention plugin that works:

   | Plugin | When |
   |---|---|
   | `readana.jvm.library` | **Default.** No Android API needed (models, algorithms, parsers) |
   | `readana.android.library` | Needs Context/Android APIs, no UI |
   | `readana.android.library.compose` | Shared UI components |
   | `readana.android.feature` | User-facing screen → use /new-feature instead |

2. `.<group>/<name>/build.gradle.kts` is just the plugin block + dependencies. Android modules also
   need `android { namespace = "dev.readana.<group>.<name>" }` and get an enforced resource prefix
   `<group>_<name>_`.

3. `include(":<group>:<name>")` in `settings.gradle.kts` (keep groups sorted).

4. Package root `dev.readana.<group>.<name>`. Respect layering (Konsist-enforced):
   - pure modules never import android/androidx,
   - `engine/*` and `source/*` never import `dev.readana.core.data`,
   - only `app` sees everything.

5. If the module is pure-JVM, add its path to `PURE_MODULES` in `scripts/hooks/check_kotlin_file.py`
   and `JVM_MODULES` in `app/src/test/kotlin/dev/readana/ArchitectureTest.kt`.

6. Add the module to the tables in `docs/ARCHITECTURE.md` and the README layout block, write at least
   one unit test, then `/ship`.
