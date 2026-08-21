---
name: ship
description: Finish a unit of work the Readana way - run every quality gate, update CHANGELOG/docs, and land a Conventional Commit. Use when the user says "ship it", "finish up", "commit this", or work on a feature/fix is complete.
---

# Ship the current work

Run the full finishing pass for this repository. Do the steps in order; do not skip a failing gate.

## 1. Quality gates (fix failures, then re-run)

```bash
./gradlew spotlessApply
./gradlew detekt
./gradlew testDebugUnitTest test
./gradlew :app:lintDebug
```

- detekt findings: fix them. Only `@Suppress("Rule") // reason` with a real reason when a fix is wrong.
  Never grow `config/detekt/baseline.xml`.
- Test failures: fix the code or the test — never delete a test to get green.

## 2. Record the change

- Add/extend a bullet under `## [Unreleased]` in `CHANGELOG.md` (Keep a Changelog categories:
  Added/Changed/Fixed/Removed). Skip only for pure refactors/chores — then the commit body must
  contain `[skip-changelog]` or the commit hook denies it.
- Tick any completed items in `docs/ROADMAP.md`.
- If an architectural decision changed, add a new `docs/adr/NNNN-*.md` (never edit old ADRs) and
  list it in `docs/adr/README.md`.
- New module/source/feature? Update `docs/ARCHITECTURE.md` tables and `settings.gradle.kts` listing in README if relevant.

## 3. Commit

- Stage deliberately (`git add <paths>`), including `CHANGELOG.md`.
- Message: Conventional Commit — `type(scope): summary` with type in
  feat|fix|docs|style|refactor|perf|test|build|ci|chore|revert. Scope = module (`reader`, `annas`, `reflow`, ...).
- One logical change per commit; split unrelated work.
- Do NOT push unless the user asked.

## 4. Report

Tell the user: gates run + result, changelog entry added, commit hash + message.
