# 14. A provider-agnostic reading assistant that cannot spoil the book

Date: 2026-08-21

## Status

Accepted.

## Context

Readana's reader could mark a passage and nothing else. Readers want to ask what a passage means,
who a character is, and — coming back after a fortnight — what has happened so far. Every one of
those is easy to build badly: the obvious implementation hands the whole book to a model, which
then cheerfully explains a passage in the light of an ending the reader has not reached.

The other constraint is that this is a personal reader with no backend. Readana has no API key of
its own to spend, so the assistant must work with whatever key its user already has, from whichever
vendor, including a model running on the same LAN.

## Decision

**The context is cut at the reading position, mechanically.**
`ReadingContextBuilder` (in `ai/client`, pure Kotlin) walks the publication's own text through a
`TextWalker`. It walks *forward* only until the selected passage has been included, and stops
there; everything else it gathers by walking *backward* from the anchor until the character budget
is spent. A recap passes no selection and so never walks forward at all. The anchor is
`VisualNavigator.firstVisibleElementLocator()` — the first element on the page — not a numeric
progression, because the navigator's progression and the content iterator's are different scales.

The prompt repeats the rule in words ("everything you know about this book is the text quoted in
`<book_so_far>`"), but the guarantee is the walk: text the reader has not reached is never read,
so it cannot be sent.

**The rendered context is stored with the conversation.** `ai_threads.context_text` holds the exact
bytes that were sent. Follow-up questions resend them unchanged, which (a) means a conversation can
never quietly widen to cover text read since, and (b) is what makes provider prompt caches hit.

**Two wire dialects, not six.** Anthropic's Messages API is spoken natively, because only it lets
the book block be marked cacheable (`cache_control`). Everything else — OpenAI, Gemini (through its
OpenAI-compatible endpoint), OpenRouter, DeepSeek, Ollama, LM Studio, any gateway — is one
`OpenAiCompatibleClient` differing only in base URL, a header or two and the model name. Users can
add their own OpenAI-compatible endpoint from Settings, which is why "provider agnostic" is real
rather than a list of five.

**A dedicated HTTP client** (`@AiHttp`). The shared one carries the WebView's cookies and a browser
user agent — neither belongs in a request to an AI vendor — and its ten-second read timeout would
cut a streamed answer whenever a model paused to think. The assistant's client has an infinite
request timeout and a two-minute socket timeout.

**Keys live in Preferences DataStore**, in app-private storage, exactly as the Anna's Archive
member key does. A Keystore-wrapped store would be better and is a candidate for later; it is not
worth blocking the feature on.

**Pictures are fetched by the app, never by the model.** Illustrations from the walked pages are
attached to the request as vision input, and an answer can ask for a picture of a subject by
writing `[image: …]`, which Readana resolves against Wikipedia (no key, free licensing, a page to
credit) and renders. The providers' own web-search tools were rejected for this: they return text
and citations rather than pictures, DeepSeek and local servers have none, OpenRouter bills per
search — and decisively, whatever they read re-enters the conversation, which is precisely how a
search about a novel tells the reader how it ends. A one-way lookup keeps the guarantee: the model
writes a subject, the reader sees a picture, and nothing comes back.

## Consequences

- The assistant is EPUB-only. pdfium PDFs expose no content service and no text selection, so the
  menu items and the recap simply never appear for them. PDFs are converted to EPUB on import, so
  in practice this is rarely visible.
- Answers about the early chapters of a very long book need the context budget raised (Settings →
  Reading assistant), because the budget is spent from the reading position backwards.
- Gathering the context costs a walk proportional to the budget, not to the book: nothing is
  extracted up front and nothing is cached on disk, so there is no invalidation to get wrong.
- The model is asked for plain prose, so the sheet renders plain text. No markdown dependency, and
  no half-parsed asterisks.
