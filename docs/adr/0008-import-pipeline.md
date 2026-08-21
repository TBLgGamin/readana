# 0008 — Book import pipeline

Date: 2026-08-21 · Status: accepted

## Context

M1 brings files into the library from the system picker and from "Open with Readana". Doing that
needs the same publication parsing the reader already uses (for title, authors, language and the
cover), a place for the copied file, and a rule for files the user imports twice. The Readium
opener lived in `feature/reader`, which features may not import from each other.

## Decision

- `ReadiumPublicationOpener` moves to `core/data` (`dev.readana.core.data.readium`). The importer
  needs it alongside `LibraryRepository` and `SettingsRepository`, so a separate module would put
  Readium on `core/data`'s classpath anyway. `feature/reader` keeps the navigator dependencies.
- Metadata is read from the **original** file before anything is copied, so unreadable files cost
  no disk writes.
- Duplicates are detected by extracted title plus byte size, checked before the copy. It is cheap,
  needs no extra full read, and catches the practical case of re-importing the same file. Files
  whose size the provider does not report are always imported.
- Covers are always app-internal (`filesDir/covers/<id>.png`) even when books live in a SAF folder:
  a cover is derived data that can be regenerated from the file.
- `BookImportManager` runs imports on the application scope, so an import started by an incoming
  intent finishes even if the user leaves the library. Its events are best effort — the grid itself
  always catches up through the database flow.
- Coil 3 renders covers, wired into `core/designsystem` only. Covers are local files, so the
  network fetcher is left out and no `ImageLoader` is configured.

## Consequences

- `core/data` depends on Readium. The module graph in `docs/ARCHITECTURE.md` records this.
- A book whose title Readium reports differently from a previous import (edited metadata, another
  edition) imports again rather than being flagged as a duplicate.
- Changing the library folder does not move books already imported; new books go to the new folder.
