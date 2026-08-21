# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and versions follow
[Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added

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
