# 0007 — Library storage strategy

Date: 2026-08-20 · Status: accepted

## Context

Scoped storage (Android 10+) limits raw file access. Options: app-specific storage (no permission,
gone on uninstall), SAF document tree (user-visible, survives uninstall, needs a picker),
`MediaStore.Downloads` (loses attribution on reinstall), `MANAGE_EXTERNAL_STORAGE` (blunt).

## Decision

- Default: books live in app-specific external storage (`getExternalFilesDir("books")`), readable
  by Readium via `file://` URLs.
- Optional: the user picks a library folder with `ACTION_OPEN_DOCUMENT_TREE`; the persisted tree
  URI is stored in DataStore (`libraryFolderUri`) and files are written through `DocumentFile`.
- Metadata (title, authors, progress, origin) always lives in Room; files are referenced by URI.
- Converted EPUBs sit next to their original PDF; both are library entries linked by `BookOrigin`.

## Consequences

- Uninstalling loses the default library unless a SAF folder was chosen — surfaced in Settings.
- Backup rules exclude the SQLite WAL files only; the DB itself is small and backed up.
