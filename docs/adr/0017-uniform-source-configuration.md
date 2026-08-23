# 17. Sources configure themselves, and catalogs are data

Date: 2026-08-23

## Status

Accepted. Extends [ADR 0004](0004-pluggable-book-sources.md), which still holds for sources that
are compiled in.

## Context

ADR 0004 said adding a source is "a new module + one `@IntoSet` provider". That was true of the
code, but not of everything around it. Anna's Archive needs a mirror list and a membership key, and
those ended up:

- as five named fields on `SourceSettings` in `core/model` (`annasMirrors`, `annasSecretKey`,
  `annasPreferredMirror`, `annasDownloadsLeft`, `annasDownloadsPerDay`),
- as five DataStore keys,
- as hand-written text fields inside `SettingsScreen.SourcesSection`,
- and as the destination the download pipeline wrote a reported quota back to.

So a second source needing a key had nowhere to put it, a second *metered* source would have
overwritten Anna's allowance, and every new source meant new settings UI. The M5 sources —
OPDS catalogs with optional credentials, arXiv with a category filter — would have made the pattern
four times worse.

Catalogs are also different in kind from the other sources. There is no single "OPDS source": there
are as many as the user cares to add, they appear and disappear while the app is running, and two
of them share all their code and differ only by a URL. `BookSourceRegistry` took a fixed
`Set<BookSource>` from Hilt, which cannot express that.

## Decision

**A source declares what it needs configured.** `BookSource` gains `configFields:
List<SourceConfigField>` — a key, a label, a kind (`Text`, `Secret`, `UrlList`), and optional help
and placeholder text — plus a one-line `summary`. `SourceSettings` holds the answers generically:

```kotlin
val config: Map<String, Map<String, String>>   // sourceId -> fieldKey -> value
val quotas: Map<String, StoredQuota>           // sourceId -> what it said it had left
val catalogs: List<CatalogRef>                 // catalogs the user added
```

The Sources settings page renders one row per source and one field per declared entry. It contains
no source's name. Adding a source is once again just a module and a provider.

**Catalogs are data, not modules.** A `CatalogRef` (id, name, feed URL, optional preset id) is
stored in settings; `BookSourceRegistry` builds one `OpdsSource` per stored catalog through a
`CatalogSourceFactory`, and exposes `Flow<List<BookSource>>` rather than a fixed list. The
ready-made catalogs (Standard Ebooks, DOAB, Feedbooks) are entries in a list, not modules.

**Browsing is a capability, not the default.** `CatalogSource : BookSource` adds
`browse(feedUrl): AppResult<CatalogPage>`, whose entries are either a `Section` to open or a
`Publication` that is an ordinary `SearchResult`. Sources that only answer queries implement
nothing new.

The three nested maps are stored as one JSON blob per concern rather than a DataStore key per
field, because a preference key per field is exactly the thing this ADR is removing.

## Consequences

- A new source needs no settings UI, no model field and no DataStore key. `source/arxiv` shipped
  its category filter without touching `feature/settings`.
- A second metered source can report its allowance without fighting the first for one slot.
- Settings written by earlier builds are folded into the generic map on first read and the old keys
  dropped on first write, so a mirror list and a membership key survive the upgrade. That migration
  is the one place in `core/data` that still names Anna's, and it is covered by
  `SourceSettingsMigrationTest`.
- `BookSourceRegistry` lookups became suspending. Every call site was already inside a coroutine.
- `readium-opds` stays unused. It is an Android artifact, and every module under `source/` is
  Android-free by a Konsist rule that also buys the source layer its millisecond unit tests. OPDS
  1.2 is parsed with jsoup in XML mode — already a dependency of `source/annas` — and OPDS 2.0 with
  kotlinx.serialization. See [ADR 0004](0004-pluggable-book-sources.md) for why that rule exists.
- Sources still cannot express a format outside `BookFormat` (EPUB, PDF). Catalog entries offering
  mobi, cbz or an audiobook are dropped rather than listed as a download that would fail.
