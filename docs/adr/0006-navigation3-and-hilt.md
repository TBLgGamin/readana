# 0006 — Navigation 3 and Hilt

Date: 2026-08-20 · Status: accepted

## Context

Navigation Compose 2.x is stable and familiar; Navigation 3 (1.1.x, stable) is Google's
Compose-first successor with an app-owned back stack and adaptive scenes (list-detail). DI options:
Hilt (compile-time, Google-recommended) or Koin (runtime, lighter).

## Decision

- Navigation 3: each feature exposes a `@Serializable NavKey` and an entry-provider extension; the
  app owns `NavBackStack<NavKey>` and registers keys in a polymorphic `SavedStateConfiguration`.
- Hilt with KSP; `hiltViewModel()` inside entries via `rememberViewModelStoreNavEntryDecorator()`.

## Consequences

- Tablet list-detail layouts can use `ListDetailSceneStrategy` without restructuring.
- Forgetting to register a key breaks state restoration — documented in CLAUDE.md.
