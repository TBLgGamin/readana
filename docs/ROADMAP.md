# Roadmap

Milestones are sized so each one ships something usable on the tablet.

## M0 — Scaffold ✅ (2026-08-20)

- [x] Gradle 9.7 / AGP 9.3 / Kotlin 2.4 build with convention plugins and version catalog
- [x] Quality gates: Spotless + ktlint + compose-rules, detekt 2, Android Lint (warnings = errors), Konsist, CI, Dependabot, pre-commit
- [x] Module skeleton (`app`, `feature/*`, `core/*`, `engine/*`, `source/*`) with Hilt and Navigation 3
- [x] Data layer: Room `books` table, DataStore settings, repositories
- [x] `BookSource` abstraction; Gutendex source; Anna's Archive source (search parser, member API, mirror failover) — unit-tested against fixtures
- [x] EPUB 3 writer validated by epubcheck; reflow heuristics with tests; PDFBox extractor
- [x] Screens: Library grid, Discover search, Settings, Reader placeholder that opens publications through Readium
- [x] Docs: README, architecture, device notes, research, ADRs

## M1 — Library & import ✅ (2026-08-21)

- [x] Import EPUB/PDF from the system file picker and from "Open with Readana" intents
- [x] Copy/move files into app storage (optional SAF library folder), extract cover + metadata via Readium
- [x] Library sorting (recent, title, author), search, delete, book details sheet
- [x] Coil image loading for covers

## M2 — Reader ✅ (2026-08-21)

- [x] Readium `EpubNavigatorFragment` hosted in Compose (`AndroidFragment`), PDF via pdfium navigator
- [x] Controls: tap zones & swipe, paginated/scrolled, font family/size, margins, line height, themes (light/sepia/dark/black), keep-screen-on, volume-key paging
- [x] Progress slider + chapter/TOC drawer, bookmarks, in-book search, progress persisted as Readium locator
- [x] Tablet layout: two-page spread in landscape, immersive mode
- [x] Highlights/notes (Readium decorations) and dictionary lookup (stretch)

## M3 — Downloads ✅ (2026-08-21)

- [x] `DownloadWorker` (WorkManager + foreground `dataSync` service, resumable, notification)
- [x] Download queue UI in Library; retry/cancel; duplicate detection by source record id
- [x] In-app browser flow for sources that need it (Anna's slow downloads), capturing the resulting file
- [x] HyperOS battery-restriction guidance in-app

## M4 — PDF → EPUB ✅ (2026-08-21)

- [x] Auto-convert on import/download (keep original), conversion progress + report
- [x] Images/figures extraction into the EPUB; multi-column detection; table of contents from PDF outline
- [x] Conversion settings (headers/footers, heading sensitivity), per-book re-convert
- [x] Quality corpus with golden content snapshots — PDFs generated rather than checked in, see
      [ADR 0012](adr/0012-conversion-golden-corpus.md); real-world scans remain future work

## M4.5 — Reading assistant ✅ (2026-08-21)

- [x] Ask and Explain on a text selection, threaded per book and saved; "Where was I?" recap after
      time away, reusing a recent one rather than paying twice
- [x] Context cut at the reading position by walking the publication backwards from the page on
      screen, so unread text is never sent — see [ADR 0014](adr/0014-provider-agnostic-reading-assistant.md)
- [x] Provider-agnostic clients: Anthropic (native, with prompt caching) plus one OpenAI-compatible
      client covering OpenAI, Gemini, OpenRouter, DeepSeek and user-added endpoints
- [x] Settings: per-provider key/model/base URL, model fetch, connection test, context budget,
      recap threshold

## M5 — More sources ✅ (2026-08-23)

- [x] OPDS 1.2/2.0 catalogs, browsable as well as searchable, with presets (Standard Ebooks, DOAB,
      Feedbooks) and any pasted feed including Calibre-Web. Parsed in a pure-Kotlin module rather
      than with `readium-opds`, which is an Android artifact — see
      [ADR 0017](adr/0017-uniform-source-configuration.md)
- [x] Open Library search with Internet Archive downloads, limited to publicly downloadable scans
      (lending-only items are DRM-locked and are not listed); arXiv preprints, converted from PDF
- [x] Sources declare their own configuration, so a new source needs no settings UI; catalogs are
      data rather than modules, and Settings is split into five tabs

## M6 — Polish ✅ (2026-08-23)

- [x] Roborazzi screenshot tests, run inside the ordinary unit-test task through Robolectric so CI
      needs no emulator (`-PrecordScreenshots=true` to accept new goldens). Roborazzi's own Gradle
      plugin is not applied: it still uses AGP's removed `TestedExtension`
- [x] Baseline profile generated from a macrobenchmark on the tablet and committed, with a
      `benchmark` build type and a startup benchmark that measures with and without it
- [x] Release signing and GitHub Releases: a `vX.Y.Z` tag sets the version, runs the gates, builds
      a signed APK and publishes it with notes taken from the changelog — see
      [docs/RELEASING.md](RELEASING.md)
- [x] Stylus: palm rejection, pen-drag highlighting and freehand ink, all handled below Readium
      because its input events carry no tool type — see
      [ADR 0018](adr/0018-pen-input-below-readium.md)
