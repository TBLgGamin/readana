# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and versions follow
[Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added

- Reader: EPUB and PDF are rendered by the Readium navigator hosted in Compose, with tap zones and
  swipes for page turns, live reading settings, immersive full-screen reading, keep-screen-on, and
  the reading position restored and saved as a Readium locator.
- Bookmarks: a `bookmarks` table added by a Room auto-migration to schema version 2.
- Reader controls: progress slider, chapter list, a reading-settings sheet (typeface, size, line
  spacing, margins, theme, paginated or scrolled, columns), a left-edge brightness drag and
  volume-key page turns.
- New `columns` reading setting: a two-page spread in landscape by default, or forced to one or two.
- Library screen: import books from the system file picker, sort by recently read, title or author,
  search by title and author, a book details sheet, and delete with confirmation.
- Books opened with "Open with Readana" from another app are imported automatically.
- Covers extracted at import are rendered with Coil; books without one keep the title card.
- Settings: optional SAF library folder, so books can live outside app storage.
- Book import pipeline: copies EPUB/PDF files into app storage or the chosen SAF library folder,
  extracts title, authors, language and cover through Readium, skips files already in the library
  and deletes files together with their entry.
- Material 3 Expressive theming app-wide: `ReadanaTheme` builds on `MaterialExpressiveTheme` with
  the expressive motion scheme, and loading states use the expressive `LoadingIndicator`.
- Project scaffold (M0): Gradle 9.7 / AGP 9.3 / Kotlin 2.4 multi-module build with convention
  plugins, Spotless + ktlint, detekt 2, Android Lint, Konsist, GitHub Actions CI, Dependabot,
  pre-commit hooks.
- Data layer: Room library database, DataStore settings, repositories, Hilt wiring.
- `BookSource` abstraction with Project Gutenberg (Gutendex) and Anna's Archive
  (HTML search parser, member fast-download API, mirror failover) implementations.
- EPUB 3 writer validated by epubcheck; PDF reflow heuristics; PDFBox-Android text extractor.
- Screens: Library, Discover, Settings, and a Reader placeholder that opens publications through Readium.
- Documentation: README, architecture, device notes, research summary, roadmap, ADRs.
- AI-session automation in `.claude/`: hooks that auto-format the repo after every turn, check
  convention violations on each Kotlin edit, and block commits that break Conventional Commits or
  skip the changelog; skills `/ship`, `/new-feature`, `/new-source`, `/new-module`, `/deploy-tablet`.
