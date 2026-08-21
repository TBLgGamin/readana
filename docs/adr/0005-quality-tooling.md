# 0005 — Build and quality tooling

Date: 2026-08-20 · Status: accepted

## Decisions

| Topic | Choice | Rationale |
|-------|--------|-----------|
| Build | Gradle 9.7 wrapper with `distributionSha256Sum`, AGP 9.3 **built-in Kotlin**, convention plugins (`build-logic/`), version catalog, configuration cache | Current stable toolchain; modules are declarative |
| Formatting | Spotless 8 + ktlint 1.8 (Android Studio code style via `.editorconfig`) + compose-rules ktlint; also trims Markdown/YAML/XML | One command (`spotlessApply`), deterministic |
| Static analysis | detekt 2.0.0-alpha.6 from the root over all sources + compose-rules detekt | Built against our exact Kotlin/AGP/Gradle; 1.23.x predates Gradle 9 and compose-rules 0.6 needs detekt 2 |
| Android Lint | warnings as errors, `checkDependencies` from `:app`, shared `config/lint/lint.xml`, also on JVM modules | Catches API level, resource and Kotlin/coroutine misuse early |
| Architecture tests | Konsist in `app/src/test` | Layering rules fail the unit-test task |
| Tests | JUnit 4 + kotlin-test, Turbine, MockK, Robolectric (Room), Ktor MockEngine, epubcheck | Standard Android stack; pure modules stay fast |
| CI | GitHub Actions: spotlessCheck → detekt → lint → tests → assembleDebug, APK artifact; tag-triggered release workflow | Same Gradle tasks locally and in CI |
| Dependencies | Dependabot (gradle + github-actions, grouped weekly) | Version catalog support |
| Local hooks | pre-commit framework (generic hygiene + spotlessApply; detekt on push) | Optional, cross-platform via `scripts/gradlew.py` |

## Consequences

- detekt is an alpha: pin it, upgrade deliberately, keep the baseline empty.
- `android.builtInKotlin` must stay enabled; never add `org.jetbrains.kotlin.android`.
