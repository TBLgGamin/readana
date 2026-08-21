# 0012 — Conversion corpus and golden snapshots

Date: 2026-08-21 · Status: accepted

## Context

The PDF → EPUB heuristics — header stripping, paragraph merging, heading promotion, column
detection, figure placement, outline mapping — are exactly the kind of code that regresses quietly.
A unit test over hand-built `TextLine`s proves a rule fires; it does not prove the whole pipeline
still turns a real PDF into a sensible book.

## Decision

- **The corpus is generated with PDFBox, not checked in as binaries** (`CorpusPdfs`). Four documents
  cover one axis each: chapter headings with running headers and page numbers, a two-column paper
  with a spanning title and a full-width figure, a report whose bookmarks define the chapters, and
  an illustrated page. Every real candidate carried a licence to track and a few hundred kilobytes
  of repository weight, and its goldens would churn whenever the source was revised. Generating
  them keeps the corpus free and deterministic, and conversion still runs through the same PDFBox
  extraction path as a downloaded book.
- **Goldens hold extracted content, never EPUB bytes**: a block list (`H1|p0|…`, `P|…`, `IMG|…`), an
  indented contents tree, and the report's counts. Byte-level goldens would fail on things that are
  not the conversion — image encoders differ between Robolectric and a device — and `engine:epub`
  already asserts that identical input produces byte-identical output.
- **Image bytes never appear in a golden**, only media type and pixel size, for the same reason.
- **Regeneration is one flag**: `-PupdateGoldens=true` rewrites the files and then fails the run, so
  a regenerated golden can never be mistaken for a passing test.
- **epubcheck does not run in `engine:pdf2epub`.** It and Robolectric fight over the classloader
  (`io.mola.galimatias.URL` is loaded by both). EPUB validity is asserted in `engine:epub`, on the
  pure-JVM side, including the structures M4 introduced: nested navigation and image resources. The
  corpus test instead asserts the package has the parts a reader needs.

## Consequences

- The corpus is tidier than real-world input: no scanner noise, no unusual font encodings, no
  vector-drawn figures. Robustness against messy PDFs is not covered here and remains future work —
  the natural next step is a small set of real public-domain files once their licences are recorded.
- Goldens are expected to change when a heuristic is deliberately tuned. Reviewing that diff is the
  point: it shows what the change does to real output. The two-column golden earned its keep
  immediately, exposing a full-width figure being sorted into the left column instead of after both.
- Corpus tests need Robolectric because PDFBox needs a `Context`; they add a few seconds to CI.
