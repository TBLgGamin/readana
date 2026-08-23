---
name: new-source
description: Add a new BookSource (book search/download provider) to Readana as a pure-Kotlin module with MockEngine tests and Hilt wiring. Use for requests like "add Standard Ebooks / Open Library / an OPDS catalog as a source".
---

# Add a book source

Given a source name `<name>` (lowercase, e.g. `openlibrary`):

1. **Module**: `source/<name>/build.gradle.kts` — copy `source/gutenberg/build.gradle.kts`
   (plugins `readana.jvm.library` + `kotlin.serialization`; deps `projects.source.api`,
   `libs.ktor.client.core`, `libs.kotlinx.serialization.json`, test `libs.ktor.client.mock`,
   `libs.kotlinx.coroutines.test`; add `libs.jsoup` only if scraping HTML).
   Add `include(":source:<name>")` to `settings.gradle.kts`.

2. **Implementation** in `src/main/kotlin/dev/readana/source/<name>/`:
   - Implement `dev.readana.source.api.BookSource`: stable `SourceId`, `displayName`,
     `search(SearchQuery): AppResult<SearchPage>`, `resolveDownload(SearchResult): AppResult<DownloadRequest>`.
   - Set `enabledByDefault = false` if the source has legal caveats or needs configuration.
   - Take the `HttpClient` and any base URL via the constructor (base URL configurable for tests).
   - Map failures to typed `AppError`s (Network/Http/Parse/Unauthorized/NotFound). Wrap transport calls so
     `CancellationException` is rethrown — never swallowed (see GutendexSource.fetch or use suspendRunCatching).
   - Stash anything `resolveDownload` needs later in `SearchResult.extras`.
   - Sources that need an interactive browser step return `DownloadRequest(requiresBrowser = true)`.

3. **Tests** (`src/test/`): Ktor `MockEngine` + a realistic fixture in `src/test/resources/`
   (real API response or representative HTML). Cover: happy path mapping, wanted-format filtering,
   HTTP error, transport error, malformed payload, resolveDownload. See `GutendexSourceTest`.

4. **Wire up**: one `@Provides @IntoSet @Singleton` in
   `core/data/src/main/kotlin/dev/readana/core/data/di/SourcesModule.kt`, plus
   `implementation(projects.source.<name>)` in `core/data/build.gradle.kts`.
   Discover and Settings pick it up automatically via `BookSourceRegistry`.

5. **Anything the user must fill in** (an API key, a host list, a filter) goes in
   `BookSource.configFields`, not in a settings screen — the Sources page renders whatever a source
   declares, and the values arrive back through `SourceSettings.configFor(id)` (ADR 0017). Add a
   `summary` too: it is the line under the name. A source that browses shelves as well as searching
   implements `CatalogSource`.

6. Pure-JVM rule: no `android.*`/`androidx.*` imports anywhere in the module (Konsist + hook enforce
   this). **Add the module path to `JVM_MODULES` in `app/src/test/.../ArchitectureTest.kt`** — the
   rule only checks modules listed there, so leaving it out silently skips the check.

7. Finish with `/ship` (gates, CHANGELOG entry under Added, ARCHITECTURE.md + README source lists).
