# Contributing

Readana is a personal project, but the workflow is the same one a team would use so that
tooling, AI assistants and future contributors all follow one path.

## Workflow

1. Branch from `main` (`feat/reader-tap-zones`, `fix/annas-parser-year`).
2. Keep commits focused; use [Conventional Commits](https://www.conventionalcommits.org/)
   (`feat:`, `fix:`, `refactor:`, `docs:`, `build:`, `test:`, `chore:`).
3. Before pushing: `./gradlew spotlessApply detekt testDebugUnitTest test :app:lintDebug`
   (or install the pre-commit hooks).
4. Open a PR; CI must be green. Update `CHANGELOG.md` under *Unreleased* for user-visible changes
   and add/adjust an ADR when an architectural decision changes.

When working with Claude Code, the repo enforces this automatically: `.claude/settings.json` hooks
format the repo at the end of each turn (`spotlessApply`), lint each edited Kotlin file against the
layering/naming rules, and refuse `git commit` when the message is not a Conventional Commit or a
code change is committed without a CHANGELOG entry (`[skip-changelog]` in the body opts out for
pure refactors). The `/ship` skill runs the whole finishing pass.

## Code style

- Kotlin official style via ktlint (`.editorconfig`); 120-column lines; trailing commas.
- Compose: stateless screens + `Route` wrappers that own the ViewModel; `modifier` first optional
  parameter; private previews; strings from resources with the module prefix.
- Errors cross module boundaries as `AppResult`/`AppError`; no exceptions for expected failures.
- Tests next to the code (`src/test`); fixtures under `src/test/resources`.

## Adding things

| Thing | Steps |
|-------|-------|
| Module | `include(":group:name")` in `settings.gradle.kts`; `build.gradle.kts` applying a `readana.*` convention plugin; `namespace` for Android modules |
| Feature | New `feature/<name>` with `readana.android.feature`; add `XxxKey`, `xxxEntry()`, `XxxRoute/Screen/ViewModel`; register the key in `app/.../ReadanaNavigation.kt`; add an entry in `ReadanaApp` |
| Book source | See CLAUDE.md "Adding a book source" |
| Dependency | Add to `gradle/libs.versions.toml` only |

## Reporting problems

Use the issue templates. Never attach copyrighted books; describe the file instead.
