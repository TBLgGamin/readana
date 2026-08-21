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
- [x] Controls: tap zones & swipe, paginated/scrolled, font family/size, margins, line height, themes (light/sepia/dark/black), brightness gesture, keep-screen-on, volume-key paging
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

## M5 — More sources

- [ ] OPDS 1.2/2.0 catalogs via `readium-opds` (Standard Ebooks, Calibre-Web, DOAB)
- [ ] Open Library / Internet Archive, arXiv
- [ ] Per-source enable/disable, language and format filters in Discover

## M6 — Polish

- [ ] Roborazzi screenshot tests, baseline profiles, release signing + GitHub Releases
- [ ] Stylus: highlight with pen, palm rejection in reader
