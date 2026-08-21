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

## M1 — Library & import

- [ ] Import EPUB/PDF from the system file picker and from "Open with Readana" intents
- [ ] Copy/move files into app storage (optional SAF library folder), extract cover + metadata via Readium
- [ ] Library sorting (recent, title, author), search, delete, book details sheet
- [ ] Coil image loading for covers

## M2 — Reader

- [ ] Readium `EpubNavigatorFragment` hosted in Compose (`AndroidFragment`), PDF via pdfium navigator
- [ ] Controls: tap zones & swipe, paginated/scrolled, font family/size, margins, line height, themes (light/sepia/dark/black), brightness gesture, keep-screen-on, volume-key paging
- [ ] Progress slider + chapter/TOC drawer, bookmarks, in-book search, progress persisted as Readium locator
- [ ] Tablet layout: two-page spread in landscape, immersive mode
- [ ] Highlights/notes (Readium decorations) and dictionary lookup (stretch)

## M3 — Downloads

- [ ] `DownloadWorker` (WorkManager + foreground `dataSync` service, resumable, notification)
- [ ] Download queue UI in Library; retry/cancel; duplicate detection by source record id
- [ ] In-app browser flow for sources that need it (Anna's slow downloads), capturing the resulting file
- [ ] HyperOS battery-restriction guidance in-app

## M4 — PDF → EPUB

- [ ] Auto-convert on import/download (keep original), conversion progress + report
- [ ] Images/figures extraction into the EPUB; multi-column detection; table of contents from PDF outline
- [ ] Conversion settings (headers/footers, heading sensitivity), per-book re-convert
- [ ] Quality corpus: a handful of public-domain PDFs with golden EPUB snapshots

## M5 — More sources

- [ ] OPDS 1.2/2.0 catalogs via `readium-opds` (Standard Ebooks, Calibre-Web, DOAB)
- [ ] Open Library / Internet Archive, arXiv
- [ ] Per-source enable/disable, language and format filters in Discover

## M6 — Polish

- [ ] Text-to-speech (Readium TTS navigator), reading statistics, sync/backup of library metadata
- [ ] Roborazzi screenshot tests, baseline profiles, release signing + GitHub Releases
- [ ] Stylus: highlight with pen, palm rejection in reader
