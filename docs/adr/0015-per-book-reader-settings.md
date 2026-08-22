# 0015 — Reading settings belong to the book

Date: 2026-08-22 · Status: accepted

## Context

`ReaderSettings` was a single object in DataStore, shared by every book. On an 11" tablet that is
wrong often enough to be a nuisance: a comic wants two full-width pages and no margins, a novel
wants one column and room to breathe, and a badly converted PDF wants a larger typeface than
anything else in the library. Changing them for one book changed them for all.

The settings were also editable *only* from inside an open book, which meant there was no way to
say what a book should look like before opening it, and no single place a reader could go to set up
how reading works.

## Decision

- A book keeps its own settings from the moment the reader adjusts anything while it is open.
  There is no switch to throw and no mode to be in: the sheet in the reader writes to that book.
- Those settings live in a `book_reader_settings` row, one JSON blob per book, following the
  precedent set by the assistant's configuration (`AiSettingsJson`). A blob that cannot be read
  falls back to the defaults, so a bad write costs one book its adjustments and nothing else.
- The *defaults* every book starts from moved to a **Reading** section on the Settings screen,
  which is also the only place they can be changed. This fits that screen's stated rule — every
  setting on one page, nothing hidden behind a tap — and closes the hole above.
- The reader's settings sheet grows one conditional line: once a book has settings of its own, it
  says so and offers to reset it to the defaults. Until then there is nothing extra to see.

## Consequences

- Adjusting a setting mid-book no longer changes anything else, which is the point, but it does
  mean a reader who wants a change everywhere must make it in Settings. The reset line is what
  makes that discoverable from inside a book.
- One JSON column rather than a column per field: adding a reading setting stays a model change
  plus a line in the codec, with no migration. The trade is that the column is not queryable, which
  nothing needs it to be.
- `ReaderViewModel` resolves the two layers with a `combine`; the per-book value wins wholesale
  rather than field by field, because a partial override would make "reset" mean two different
  things.
