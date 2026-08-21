# 0002 — Readium Kotlin Toolkit as the reading engine

Date: 2026-08-20 · Status: accepted

## Context

Rendering EPUB well (pagination, CSS, fonts, fixed layout, accessibility) is years of work.
Options: Readium Kotlin Toolkit (BSD-3, native), foliate-js/epub.js in a WebView (JS bridge),
MuPDF (AGPL), writing our own.

## Decision

Use Readium Kotlin Toolkit 3.x: `streamer` for parsing, `navigator` (EPUB navigator fragment,
decorations, preferences API, TTS) for reading, `adapter-pdfium` for PDFs. Host the fragment in
Compose; evaluate the experimental Compose-native `navigator-web-reflowable` once it stabilises.

## Consequences

- `MainActivity` extends `FragmentActivity`.
- JitPack must be in the repository list for the pdfium bindings.
- Reader settings map onto Readium `EpubPreferences`; reading position is a Readium `Locator`.
