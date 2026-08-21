# 0004 — Pluggable `BookSource` abstraction

Date: 2026-08-20 · Status: accepted

## Context

Anna's Archive is the source the user asked for, but it has no search API, rotates domains, is
ISP-blocked in the Netherlands and raises copyright concerns. Public-domain and open-access
sources (Gutenberg, OPDS catalogs) are stable and legal.

## Decision

One interface (`source/api`): `search(SearchQuery) → SearchPage`,
`resolveDownload(SearchResult) → DownloadRequest` (with `requiresBrowser` for sources that need an
interactive step). Each source is its own pure-Kotlin module, tested with Ktor `MockEngine`, and
bound into a Hilt `Set<BookSource>`. Gutenberg is the reference implementation; Anna's Archive
scrapes HTML defensively, uses the member fast-download API when a key is configured, and fails
over between user-editable mirrors.

## Consequences

- Discover fans out to all sources concurrently and shows per-source errors.
- Adding a source is a new module + one `@IntoSet` provider.
- The app shows a legal notice for Anna's Archive and ships it unconfigured.
