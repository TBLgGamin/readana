# CLAUDE.md — working notes for AI-assisted sessions

Readana is a Kotlin/Jetpack Compose Android e-reader targeting a Xiaomi Pad 8 (Android 16, API 36,
11.2" 3:2 tablet). Read `docs/ARCHITECTURE.md` and `docs/ROADMAP.md` before starting feature work.

## Commands

```bash
./gradlew assembleDebug                 # build APK (app/build/outputs/apk/debug/)
./gradlew spotlessApply                 # format (run before committing; CI runs spotlessCheck)
./gradlew detekt                        # static analysis, config in config/detekt/detekt.yml
./gradlew :app:lintDebug                # Android Lint for all modules; warnings fail the build
./gradlew testDebugUnitTest test        # unit tests (Android modules + pure-JVM modules)
./gradlew :engine:reflow:test           # single module
./gradlew check                         # all of the above
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Local machine: Android SDK at `%LOCALAPPDATA%\Android\Sdk` (set in git-ignored `local.properties`);
JDK 24 runs Gradle fine (bytecode target is 17).

## Conventions that are easy to get wrong

- **AGP 9 built-in Kotlin**: never apply `org.jetbrains.kotlin.android`; Kotlin options go through
  `kotlin { compilerOptions {} }` (see `build-logic/.../KotlinAndroid.kt`). AGP 9's DSL wants
  property style (`compileOptions.apply {}`), not `compileOptions {}`.
- **New module** = one `build.gradle.kts` applying a convention plugin
  (`readana.jvm.library`, `readana.android.library[.compose]`, `readana.android.feature`) +
  `include(...)` in `settings.gradle.kts`. Pure-Kotlin modules are preferred whenever no Android API is needed.
- **Layering (Konsist-enforced, `app/src/test/.../ArchitectureTest.kt`)**: features never import
  other features; `core/model`, `core/common`, `core/testing`, `engine/*`, `source/*` never import
  `android.*`/`androidx.*`; sources/engines never import `dev.readana.core.data`.
- **Errors**: return `AppResult<T>` / `AppError` across module boundaries; use `suspendRunCatching`
  (never `runCatching`) in suspend code so cancellation propagates.
- **Navigation 3**: each feature exposes a `@Serializable … : NavKey` and an
  `EntryProviderScope<NavKey>.xxxEntry()`; register new keys in `app/.../ReadanaNavigation.kt`
  (polymorphic serializers module) or state restoration breaks.
- **Resources**: Android library modules must prefix resources (`feature_library_*`,
  `core_designsystem_*`); lint enforces it. No hard-coded UI strings.
- **Compose**: `modifier` is the first optional parameter; public composables take a `Modifier`;
  previews are `private`. compose-rules run via ktlint and detekt.
- **detekt** runs once from the root over all modules (no per-module plugin). Suppress with a
  reason or fix; don't grow the baseline.
- **Kotlin comments**: a `/*` inside KDoc (e.g. `core/*`) opens a nested comment and breaks compilation.
- **Dependencies** only via `gradle/libs.versions.toml`; Dependabot groups updates weekly.

## Automation in this repo

Hooks (`.claude/settings.json` + `scripts/hooks/*.py`) run automatically: repo-wide `spotlessApply`
fires at the end of every turn that touched formatable files; each Kotlin Write/Edit is checked for
layering violations (blocking) and convention slips (advisory); `git commit` is denied unless the
message is a Conventional Commit and CHANGELOG.md is staged alongside code changes (append
`[skip-changelog]` to the body for pure refactors). Skills: `/ship` (finishing pass),
`/new-feature`, `/new-source`, `/new-module`, `/deploy-tablet`. Prefer them over ad-hoc flows.

## Adding a book source

1. Implement `BookSource` in a new pure-Kotlin `source/<name>` module (depend on `projects.source.api`).
2. Unit-test it with Ktor `MockEngine` and fixtures under `src/test/resources`.
3. Bind it with `@Provides @IntoSet` in `core/data/.../di/SourcesModule.kt`.

## Device gotchas (see docs/DEVICE.md)

HyperOS kills background work aggressively → downloads must be WorkManager + foreground service,
and the user must set "No restrictions" for the app. `adb install` needs "Install via USB" which
requires a Mi account login.
