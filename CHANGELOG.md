# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and versions follow
[Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added

- The assistant can show pictures. Illustrations from the pages you have already read are attached
  to the question, so the model can see a plate and point at it, and an answer may also ask for a
  picture of something it mentions — a ship, a bird, a building — which Readana looks up on
  Wikipedia and shows with a credit. It never uses the providers' own web search: those return text
  rather than pictures, two of the five do not have one, and anything they read would come back
  into the conversation, which is exactly how a search about a book gives away its ending. Here the
  model only writes down a subject and never sees what was found, so the no-spoilers guarantee is
  untouched. Switchable off under Settings → Reading assistant.

- The reader's bottom bar has a conversations button next to contents and settings, listing every
  chat held about the book, newest first, and reopening any of them.

- Selecting text in the reader now offers **Ask** and **Explain** alongside Highlight, and a book
  reopened after a while offers **Where was I?** — a recap of the story so far. All three are built
  so they cannot spoil the book: the assistant is only ever given the text above the reader's
  current position (plus the selected passage itself), gathered by walking backwards from the page
  on screen, and a conversation keeps the exact text it started with, so a follow-up months later
  still asks about the same part of the book. Conversations are saved and listed in the contents
  sheet.
- The assistant works with whichever provider the reader already pays for. Anthropic, OpenAI,
  Google Gemini, OpenRouter and DeepSeek come preconfigured — paste a key, fetch the model list,
  test the connection — and any other OpenAI-compatible endpoint, including a model running on the
  same network, can be added by hand. Settings also control how much of the read text is sent and
  how long an absence earns a recap.

- Readana has a complete Android launcher-icon set based on the new logo: adaptive colour and
  themed monochrome layers, round and legacy density fallbacks, plus 512 px Play Store artwork.

- The reader spaces paragraphs apart. Publishers usually mark a new paragraph with an indent and
  no gap, which is hard going on a backlit screen, so Readana now adds a gap and drops the indent —
  adjustable, including back to the publisher's own styling, in the reader's settings sheet.

- Search results group the copies of a book together. Shadow libraries index the same work many
  times over, so a page of hits was mostly repeats; now each book appears once, showing the copy
  the app can actually open. Where there is more than one copy, the download button opens a picker
  listing every one of them by language, format, size and year rather than guessing, and a badge on
  the cover says how many there are.

- Holding a book in Discover opens the same details sheet the library uses — cover, title, author,
  format, size, language, year, publisher and source — with no actions on it, since the cover it
  was opened from already carries the download button. A plain tap opens it too.
- The popular shelf has the same download button as a search result. A popular book carries no file
  of its own, so tapping it searches the enabled sources and queues the best match, naming what it
  queued so a wrong edition is obvious.

### Changed

- Vertical swipes no longer change the app brightness. The reader now leaves brightness entirely
  to Android and no longer places a gesture-capturing strip over the left edge of the page.
- Fixed-layout EPUBs such as comics open one full-size page at a time by default, and the reader's
  one/two-column choice now controls their page spread. They stay in paginated mode even if the
  global preference is scrolling, and their settings explain the horizontal swipe and page-edge
  controls instead of offering a scrolling option Readium cannot apply. This previously left
  downloaded image-based editions tiny and difficult to navigate.
- Selecting text in the reader offers Highlight and the platform's own copy and share, nothing
  else. Note and Define were a menu deep enough to get in the way of reading; a highlight's colour,
  note and deletion still live in its editor, which opens by tapping the highlight.
- Discover waits with the same spinner a book opens with, instead of a grid of placeholder cards
  that promised results which may not arrive.

- Progress bars are Material 3 Expressive's. The wavy one is kept for work happening right now —
  downloading, importing, converting, searching, loading a page — while how far you have read a
  book is the flat determinate bar, whose stop indicator doubles as a marker for the end. A
  squiggle under every cover read as activity that was not there.
- Library titles stay on one line, so the progress bars across a row line up instead of one card
  sitting lower than its neighbours.

- Settings drops its Reading section. Everything in it — theme, text size, line height, margins,
  page turning, columns, keep-screen-on, volume keys — is already in the reader's own settings
  sheet, where you can see what a change does to the page in front of you.
- Discover's search icon moved to the left of the box, as in the library, and the box gained a
  clear button. Tapping the icon still runs the search.

- Tapping download queues the book straight away. Working out where a file actually comes from can
  mean answering a browser check or sitting out a wait-list, so that now happens in the download
  job — under the foreground service that survives leaving the app — instead of holding up the tap.
  A download that turns out to need the browser says so in the queue, where it already had a button
  for it.
- The Anna's Archive mirror list and membership key are saved when you leave the field rather than
  on every keystroke, and changing the key clears the remaining-allowance figure that belonged to
  the old one.

### Fixed

- A question that gets no answer at all now says so. Models that think before answering spend the
  same budget on thinking, and a long deliberation could use the lot before a single word was
  written; the empty result was drawn as an empty message, so the spinner simply vanished and left
  nothing behind. The budget is several times larger, an empty answer is reported with the reason
  the provider gave, and an answer consisting only of a picture that could not be found says that
  instead of rendering blank.

- An answer that fails part-way through says so, keeping what was written, instead of stopping
  mid-sentence with no explanation. A provider that is overloaded ends the stream with an error
  after a sentence or two, and that error was being swallowed whenever any text had arrived.

- Explain no longer sits on a spinner forever. Reading back through the book to gather the context
  ran on the main thread, so the answer could not be drawn while it worked; Ask hid the same stall
  behind the seconds spent typing a question.

- An API key typed into the reading assistant's settings is saved as it is typed rather than only
  when the field loses focus, and a provider can be picked before it is finished. Pasting a key and
  collapsing the section threw the key away, and the provider's radio button stayed disabled until
  a key had been saved — so the assistant stayed switched off with nothing on screen to say why.

- A finished download reaches the library instead of being refused as "Not an EPUB or PDF". The
  file was handed to the importer still named after the job it came from, and the importer works
  the format out from the name — so every download ran to completion and was then thrown away. It
  now takes the extension its own first bytes prove, which also catches a book the catalogue
  mislabelled, and a server that answers with an error page instead of a file is refused outright
  rather than imported as a broken book.

- Downloads work on connections whose ISP blocks Anna's Archive. The site hands out download links
  on rotating partner domains, and a blocked one simply stops resolving — the download then spent
  all five of its attempts on a name that was never going to resolve, and gave up. Readana now
  checks a link resolves before starting, and asks the site for another server if it does not.
  Asking again for the same book is free: the daily allowance is charged once per book, not per
  link. Library Genesis links are checked the same way, with one byte, since its file servers go
  down independently of the pages that link to them.
- Anna's Archive results show their language again. The tabular layout writes a bare "en" where the
  card layout writes "English [en]", and only the second was being read.
- Copies of a book whose title carries a subtitle group with the rest of them ("Circe : a novel"
  alongside "Circe").

- A download no longer sits at "Waiting to start…" forever. Work was queued under one name per book
  and kept rather than replaced, so a new attempt was silently dropped whenever an older one was
  still waiting out a retry backoff — and dismissing a failed download left that older work behind.
  The queue also distinguishes waiting to begin from trying again, and says why, and a download
  worker that falls over now marks the row failed instead of leaving it looking unstarted.
- Covers load faster: the grid no longer subcomposes a cell per cover, and the user agent every
  request needs is worked out at startup rather than by the first batch of covers.
- Discover says when a source is answering a browser check, instead of showing a progress bar that
  looks stuck, and a source that could not get past one offers to open the page so you can. Coming
  back re-runs the search.
- Settings shows how many fast downloads a membership has left today.

- Downloading without a membership works on its own now. Anna's Archive is a catalogue: most of
  what it lists is held by Library Genesis, which serves the same file by md5 with no browser check
  and no wait, so that is tried first and a free download starts immediately. If the file is not
  there, Readana asks Anna's own free partner servers — preferring the ones that never queue, and
  sitting through a short wait-list when that is all there is. The in-app browser is now the last
  resort rather than the only route.
- A membership whose daily allowance is spent no longer dead-ends at the browser: the download
  falls through to the free routes above, and Settings still shows what is left.

- Anna's Archive searches get past the site's browser check instead of failing on it. Without a
  recent check the site bounces every request to a JavaScript challenge, which no plain HTTP client
  can answer — that was behind the assorted 400s and 403s. Readana now answers it in a WebView you
  never see, usually in a second or two, and repeats the request with the cookie it earns. If the
  device has no WebView, or the check will not settle, the error offers to open it in a browser
  instead of failing outright.
- A membership key now helps with searching too. Posting it to the account page signs in, and the
  site waves signed-in members past the browser check entirely — so a member never waits for one.
  The key is posted once per mirror per session, and a lapsed membership quietly falls back to the
  same check everyone else gets.

- Anna's Archive mirrors are the three the site currently authorises (`.gl`, `.pk`, `.gd`); `.org`,
  `.se` and `.li` are gone because they no longer resolve or only serve a redirect page. The mirror
  that answers is remembered across restarts, and one that just failed is tried last for five
  minutes instead of costing a connect timeout on every search — it is demoted, never dropped, so a
  connection that can reach it still does.
- When every Anna's Archive mirror fails, the message is the one that explains most. A mirror that
  answered "rate-limited" used to be hidden behind a later mirror's "no such host".
- A refused fast download now says which refusal it was — key rejected, membership expired, record
  gone, or today's allowance spent — instead of "HTTP 403", and stops asking the other mirrors the
  same question, since none of those answers change with the domain.

- Network requests now speak with the same identity as the in-app browser: they share the system
  WebView's cookie store and its user agent. Shadow-library front-ends only honour the cookie that
  clears their bot check for the exact client that earned it, so search and download requests could
  never benefit from a check the browser had already passed. Cover art and file transfers use the
  same client, so nothing sends a different agent any more.

- Anna's Archive searches find results again. The site ships part of its result list inside HTML
  comments and un-comments it in the browser, so a plain parser saw only half a page — and its card
  layout is restyled often. Searches now ask for the tabular layout, whose columns are fixed, read
  the commented-out records too, and fall back to the card layout if a mirror ignores the request.
- A search now asks Anna's Archive for every format the app can open, instead of silently asking for
  none. Results in formats Readana cannot read (`.azw3`, `.djvu`, …) are dropped rather than
  downloaded under an `.epub` name and failing at import.

- Reading on into the next chapter in scrolling mode now starts at its first line. Readium carried
  the scroll offset across the boundary, so finishing a chapter dropped you the same distance down
  the next one. Jumps from the contents list, bookmarks and search hits still land where they were
  asked to.
- Converting a PDF whose pages share an image (a logo, a chapter ornament) no longer fails with
  "Can't compress a recycled bitmap" once every page has been read. The extractor was freeing the
  bitmap PDFBox caches and reuses, so the second page drawing that image got a released one back.
  A figure that still cannot be read is now skipped with a note in the conversion report rather
  than losing the whole book.
- Cover art from search results and the popular shelf actually loads. Coil was configured without a
  network fetcher back when covers were only ever local files, so every remote cover failed silently
  and fell back to its title card. Covers are now memory- and disk-cached.
- A mirror typed without `https://` no longer disappears. `annas-archive.gl` is the obvious thing to
  enter, but only entries with a scheme survived, so a filled-in field could still report "No Anna's
  Archive mirror configured". Missing schemes are filled in, and if nothing usable is left the
  built-in list is used instead of failing. The field also shows the built-in mirrors as its
  placeholder, so an empty box no longer looks unconfigured.

- Anna's Archive searches no longer stop at the first mirror that refuses them. The site throttles
  scraping per IP and answers 403 or 429; those replies now move on to the next mirror instead of
  ending the search, and when every mirror is throttling, the message says so rather than "HTTP 403".
  Mirrors that only serve a redirect page (`.li`) are skipped too, instead of quietly reading as
  "no results".
- Default mirrors reordered to ones that currently resolve: `.gl`, `.pk` and `.li` are tried before
  `.org` and `.se`, which no longer resolve from some connections.

### Changed

- Settings is now five collapsible groups, one open at a time, each showing what its settings
  currently add up to ("Sepia · 1.0× text · Auto") so most answers need no tap. Theme, page-turn and
  column choices are proper labels instead of raw enum names.
- Converted EPUBs separate paragraphs with a blank line instead of a first-line indent, which is
  much easier to follow on a backlit screen. Books converted before this need a re-convert from
  their details sheet to pick up the new styling.
- The book details sheet drops its "Read" button: tapping the cover in the grid already opens the
  book, and the sheet is reached by long-pressing that same cover.
- Discover shows results as a two-column cover grid, with the download button on the cover.
- Discover and its popular shelf show pulsing skeleton placeholders while covers load, instead of an
  empty screen.
- Project Gutenberg is no longer searched. Its public-domain-only catalogue mostly added noise; the
  `source:gutenberg` module is still in the tree if it is ever wanted back.

### Added

- Discover opens on a "Popular right now" shelf of cover art from Open Library, so the screen is
  browsable before searching; tapping a book searches the enabled sources for it. Open Library is
  used rather than Anna's Archive because Anna's throttles scraping and that budget belongs to real
  searches.

- PDF to EPUB: PDFs are converted on import and after a download so they reflow at your font size,
  keeping the original file. Figures are carried across, two-column pages are read column by column,
  and the PDF's own bookmarks become the table of contents when it has them. Progress shows in the
  Library; the book's details sheet reports what the converter made of it and can convert again.
- Conversion settings: auto-convert on or off, header/footer removal, heading sensitivity and
  whether to include figures.
- Downloads: tapping a Discover result queues a resumable transfer that runs as a WorkManager job
  with a progress notification, resumes with a `Range` request after a dropped connection, and
  hands the finished file to the existing import pipeline. The queue lives in a `downloads` table
  (schema version 4) and is shown in the Library with retry, cancel, dismiss and open actions;
  books already in the library or already queued are not fetched twice.
- Anna's Archive membership: the fast-download API now reports the remaining daily allowance, which
  Discover shows when a download starts. When the allowance is spent — or a record needs a
  wait-list page — the download moves to an in-app browser that signs in with the membership key
  and captures the resulting file back into the same queue.
- Settings: a "Downloads & background" section explaining HyperOS's battery restrictions, with
  shortcuts to the MIUI battery and autostart screens.
- Reader: EPUB and PDF are rendered by the Readium navigator hosted in Compose, with tap zones and
  swipes for page turns, live reading settings, immersive full-screen reading, keep-screen-on, and
  the reading position restored and saved as a Readium locator.
- Bookmarks: a `bookmarks` table added by a Room auto-migration to schema version 2.
- Reader controls: progress slider, chapter list, a reading-settings sheet (typeface, size, line
  spacing, margins, theme, paginated or scrolled, columns), a left-edge brightness drag and
  volume-key page turns.
- Reader bookmarks and in-book full-text search: bookmark the current page from the top bar, jump
  back from the contents sheet, and search the open book with paged results.
- Highlights and notes: mark a selection from the text menu, recolour it, attach a note and delete
  it again; highlights are drawn over the page as Readium decorations and stored in schema
  version 3. Selecting text also offers Define, which hands the words to a dictionary app.
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
