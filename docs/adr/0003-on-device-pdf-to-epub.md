# 0003 — On-device PDF → EPUB with PDFBox + own reflow engine

Date: 2026-08-20 · Status: accepted

## Context

Calibre's converter does not run on Android. We need reflowable text from fixed-layout PDFs
without GPL/AGPL contamination (rules out MuPDF, k2pdfopt) and without shipping a Python runtime.

## Decision

- Extract positioned text with PDFBox-Android (Apache-2.0) in `engine/pdf2epub`.
- Implement layout heuristics ourselves in a pure-Kotlin `engine/reflow` module (header/footer
  removal, paragraph detection, hyphenation repair, heading detection, chapter splitting).
- Emit EPUB 3 through our own small writer in `engine/epub`, validated by epubcheck in tests.
- Keep the original PDF; conversion is additive and can be re-run with different options.

## Consequences

- Good results for single-column text; figures/tables/multi-column are follow-up work; scanned PDFs are rejected (no OCR).
- All heuristics are unit-testable on the JVM with synthetic pages.
