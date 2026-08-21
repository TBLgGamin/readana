---
name: new-feature
description: Scaffold a new feature (screen) module for Readana with Navigation 3 wiring, strings, ViewModel and tests. Use when adding a new user-facing screen such as "add a downloads screen".
---

# Add a feature module

Given a feature name `<name>` (lowercase, e.g. `downloads`):

1. **Module**: create `feature/<name>/build.gradle.kts`:

   ```kotlin
   plugins {
       alias(libs.plugins.readana.android.feature)
   }

   android {
       namespace = "dev.readana.feature.<name>"
   }
   ```

   Add `include(":feature:<name>")` to `settings.gradle.kts` (keep the feature block sorted).

2. **Files** under `feature/<name>/src/main/kotlin/dev/readana/feature/<name>/` — copy the shape of
   `feature/library` (the reference implementation):
   - `<Name>Navigation.kt`: `@Serializable data object <Name>Key : NavKey` + `EntryProviderScope<NavKey>.<name>Entry(...)`.
   - `<Name>ViewModel.kt`: `@HiltViewModel`, single `StateFlow<UiState>` (sealed interface, `SharingStarted.WhileSubscribed(5_000)`).
   - `<Name>Screen.kt`: `internal <Name>Route` (owns the ViewModel via `hiltViewModel()` from
     `androidx.hilt.lifecycle.viewmodel.compose`) + stateless `internal <Name>Screen` + `private` previews.
   - `src/main/res/values/strings.xml`: every UI string, prefixed `feature_<name>_` (lint enforces the prefix).

3. **Wire into the app** (both steps, or state restoration breaks):
   - `app/src/main/kotlin/dev/readana/navigation/ReadanaNavigation.kt`: `subclass(<Name>Key::class)` in the polymorphic module.
   - `app/src/main/kotlin/dev/readana/ui/ReadanaApp.kt`: call `<name>Entry(...)` in the entryProvider.
   - Top-level destination? Also add it to `TopLevelDestination` and `app/src/main/res/values/strings.xml`.
   - `app/build.gradle.kts`: `implementation(projects.feature.<name>)`.

4. **Tests**: ViewModel test in `src/test/...` using `MainDispatcherRule` + fakes from `core/testing` + Turbine
   (see `LibraryViewModelTest`).

5. Finish with the `/ship` checklist (gates, CHANGELOG, docs/ARCHITECTURE.md module table).

Rules: features never import other features; data access only through `core/data` repositories;
errors as `AppResult`/`AppError`.
