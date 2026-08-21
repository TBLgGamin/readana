# 0011 — One library entry per converted book

Date: 2026-08-21 · Status: accepted

Amends [0007](0007-storage-strategy.md), which said a converted EPUB and its PDF are two entries
linked by `BookOrigin`.

## Context

M4 converts PDFs to EPUB so they reflow at the reader's font size, and ADR 0003 requires the
original to be kept so a conversion can be re-run with different settings. That leaves the question
of what the library shows.

ADR 0007 answered "both files, linked by `BookOrigin`". The code never went that way: `Book.fileUri`
is documented as "always EPUB once conversion has run", and `BookOrigin.sourceId` is non-null, so it
can only describe a *downloaded* book — an imported PDF has no source to link through.

## Decision

- **One row per book.** After conversion `format` becomes `EPUB` and `file_uri` points at the EPUB.
  The kept PDF is recorded in three new nullable columns — `original_file_uri`, `converted_at`,
  `conversion_report` — added in schema version 5. Two grid entries for one book would be a
  permanent annoyance in exchange for a file the reader almost never opens directly.
- **`size_bytes` keeps the original file's size.** Duplicate detection is `(title, size_bytes)`
  against the file as it arrived; recording the EPUB's size would let the same PDF be imported
  again after conversion.
- **Conversion is additive and repeatable.** Re-converting reads the kept PDF, not the previous
  EPUB, and deletes the EPUB it replaces.
- **Reading position is dropped on conversion**, with a warning in the report. A PDF locator (or one
  from a differently paginated earlier conversion) does not map onto the new text. Bookmarks and
  highlights are left alone: chapter ids are deterministic, so EPUB-to-EPUB locators usually survive.
- **A failed conversion changes nothing.** The row stays a PDF, which pdfium still renders, and the
  failure is surfaced so the user can retry from the book's details.
- **Conversion runs one book at a time**, through a channel-fed worker in `ConversionManager`.
  PDFBox plus bitmap decoding is memory-hungry enough that two at once is a real risk on the tablet.

## Consequences

- `core/data` depends on `engine:pdf2epub`, which puts PDFBox and BouncyCastle on the app's
  classpath. Lint's `TrustAllX509TrustManager` finding inside BouncyCastle is scoped-ignored in
  `config/lint/lint.xml`.
- The details sheet has to explain what happened, hence the stored report and its dialog.
- A book converted before a settings change keeps its old text until it is re-converted; settings
  are not applied retroactively.
- `BookOrigin` keeps its original meaning — where a download came from — and is not overloaded to
  express conversion.
