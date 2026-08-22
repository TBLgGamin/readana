# 0016 — Read-aloud through Readium, in the foreground only

Date: 2026-08-22 · Status: accepted

## Context

An e-reader on a tablet should be able to read to you. Two ways were available: drive Android's
`TextToSpeech` over text pulled out of the chapter, or use Readium's own `TtsNavigator`, which was
already sitting in the version catalog (`readium-navigator-media-tts`) unused.

## Decision

- **Readium's `TtsNavigator`.** It splits the publication into utterances the same way the rest of
  the toolkit understands positions, so the spoken sentence has a `Locator` — which is what lets the
  page turn itself and mark where the voice is. Feeding raw text to `TextToSpeech` would have meant
  keeping a second, disagreeing idea of "where we are" in the book.
- The navigator is built by a closure on `ReaderSession`, matching `search` and `textWalker`: the
  session hands out what the publication can do without ever handing out the publication.
- A third reader ViewModel, `ReaderTtsViewModel`, for the same reason the assistant has its own —
  `ReaderViewModel` is at its limit, and the two share only the session store and the book id.
- The spoken sentence is drawn in its own Readium decoration group (`"tts"`), so it never disturbs
  the reader's highlights.
- **Foreground only.** Playback stops when the reader leaves the screen.

## Consequences

- Reading aloud does not continue with the screen off or the app in the background. That needs a
  `MediaSessionService` and a media3 dependency, neither of which this app has; adding them means a
  notification, audio focus handling across process death, and HyperOS's background-work rules
  (docs/DEVICE.md) — a piece of work in its own right. `keepScreenOn` already defaults to true, so
  the common case of listening while the tablet is propped up works.
- The `TtsNavigator` holds a synthesis engine and an audio focus request, so it is closed when the
  reader stops rather than when the book closes.
- PDFs opened through pdfium have no text service and cannot be spoken; the Listen button is absent
  for them rather than present and dead.
