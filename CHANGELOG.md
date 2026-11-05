# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and versions follow
[Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added

- The pen does something now. Drag it across a line and the passage is highlighted — no long press,
  no handles to drag — or switch it to draw and it leaves ink on the page. While the pen is
  touching or hovering over the screen, fingers are ignored, so a hand resting on the page no
  longer turns it or starts a stray selection; put the pen down and everything works as before.
  None of this could be done through Readium, whose input events carry no tool type and, for EPUB,
  are raised by JavaScript inside the WebView: touches are sorted below it now, where a nib can
  still be told from a palm. A pen highlight goes through exactly the path a long press does, so
  notes, colours, the contents sheet and the Markdown export all work on it unchanged.

  Ink on a fixed-layout book — a PDF or a comic — is exact and stays exact. On a reflowing book it
  cannot be: change the font size and the text moves out from under the line. Rather than draw a
  confident stroke through the wrong sentence, each stroke remembers the type it was drawn under
  and is shown faintly when that no longer matches.

- Screenshot tests, so a change to how something looks is noticed in review rather than on a device
  weeks later. They run in the ordinary test task through Robolectric — no emulator, nothing new
  for CI to install — and the reference images sit next to the tests that make them.
  `./gradlew testDebugUnitTest -PrecordScreenshots=true` accepts a change as intended.

- A baseline profile, so a cold start compiles the path it actually takes instead of interpreting
  it. Generated from a macrobenchmark on the tablet and committed, with a benchmark that measures
  startup with and without it, since a profile nobody measures is a profile nobody can trust.

- Releases are a tag. Pushing `vX.Y.Z` runs the same gates CI runs, builds a signed APK, and
  publishes it with the changelog's own words as the release notes; the version in the tag becomes
  the version in the APK, and the ProGuard mapping is attached so a crash report stays readable.
  `docs/RELEASING.md` covers the keystore and the dry run.


- Four more places to find books, and a Sources page that treats them all alike. **OPDS catalogs**
  (1.2 and 2.0) can be added from a built-in list — Standard Ebooks, the Directory of Open Access
  Books, Feedbooks' public domain shelf — or by pasting any feed address, including your own
  Calibre-Web, which takes a username and password like any other setting. A catalog is browsed as
  well as searched: Discover offers the catalogs you added as shelves, and opening one walks its
  feeds, each shelf its own screen so system back comes out one level at a time. Deduplication is
  deliberately off inside a catalog, where every entry is there on purpose. **Open Library** search
  brings in Internet Archive scans, listing only what can actually be downloaded — a borrowable
  scan ends in an Adobe licence this app cannot open, so it is not offered at all — and picks the
  EPUB derivative over the scanned PDF when the item holds both. **arXiv** searches preprints,
  narrowed to the categories you name, and its PDFs go through the existing conversion so a paper
  reflows like anything else. Search results now page: scrolling to the end of the grid fetches
  more from whichever sources said they had more.

### Changed

- The app now wears the same colours as mtgfmax: shadcn/ui's neutral greys, near-black (in dark
  mode near-white) primary actions, one violet accent and its destructive red, each converted from
  that project's OKLCH tokens to sRGB. Material 3 Expressive's baseline violet scheme is gone;
  shapes, motion and components are unchanged, and dynamic colour is still off by default.
- A new logo: a lowercase r whose stem ends in a bookmark ribbon. The launcher icon is the white r
  on violet, drawn as a vector so it is sharp at every density and works as a themed icon; the
  splash screen and the window behind the first frame use the app's own background, so opening the
  app no longer flashes a different colour; Settings › About shows the full logo, switching to its
  dark version on a dark theme. The brand kit and its guidelines are in `artwork/` (`BRAND.md`);
  the old open-book PNGs and their generator script are gone.
- The README is rewritten around what the app does, how to build it and how it works.
- The repository is ready to be public: an MIT `LICENSE` and a Code of Conduct are added,
  `.github/repo-settings.sh` applies the standard GitHub settings, blank issues are off, the
  security policy states supported versions and response times, and `.gitignore` also keeps
  `.env` files, `*.p12`/`*.pem` keys and `google-services.json` out of commits.

- A source now carries its own settings, so adding one no longer means adding a settings page for
  it. Anna's mirrors and membership key used to be fields on the shared source settings, spelled
  out again in the settings screen and written back by the download queue; a second source that
  needed a key had nowhere to put it. Sources now declare what they need — a line of text, a
  secret, a list of URLs — and the Sources page renders whatever they declared. Anna's is one row
  among the others, and what you already configured moves across on first run.

- Settings is five short tabs instead of two long ones: Reading, Sources, Assistant, Files and
  About. Every tab fits on a screen, so nothing is found by scrolling past something unrelated,
  and the version number finally has somewhere to live.

### Fixed

- The release workflow could never have signed anything. Its "restore signing key" step was guarded
  by `if: ${{ secrets.KEYSTORE_BASE64 != '' }}`, and the `secrets` context is not available in a
  step-level `if` — so the guard never held and the key was never written. It also built from a
  hardcoded `versionName` regardless of the tag, and generated release notes from commit subjects
  while the changelog sat unused. No release had ever been cut, so this was found rather than
  suffered.


- Passing messages are drawn by Readana now, and turn up wherever you are. Material 3's snackbar is
  deliberately foreign to whatever is behind it — a dark inverted slab that fades in flat — so the
  app draws its own: a warm, fully rounded pill in the same palette as every other surface, with
  emphasized text and an expressive spring that rises and settles as it arrives. Messages are also
  raised centrally rather than by each screen: a screen used to own its own host, so a message only
  appeared if you happened to still be on the screen that raised it. A download queued from the
  in-app browser was announced to a browser that was already closing itself and so was rarely seen
  at all, and an import finishing while you were in Settings said nothing. One host now sits in the
  app shell, above the page and clear of the navigation bar and rail.

- Marking a book finished does something now. The details sheet was holding the book as it looked
  when the sheet was opened, so the write landed in the database and nothing on screen moved. The
  sheet now follows the book, and the button tracks the mark you set rather than the status derived
  from it, which had already called a book read once the bar was within a couple of percent of the
  end and so left the button looking stuck.

- Rows of buttons wrap instead of being crushed. Five theme names sharing one row equally left each
  one a sliver wide with its label broken or clipped, and three labelled buttons in a plain row ran
  off the edge of a phone where the last was simply unreachable. The settings choice groups, the
  reader's settings chips, the book details actions and the provider's own buttons all keep their
  natural width and wrap onto another line.

- A model that cannot read pictures no longer costs you the answer. The assistant attaches a
  book's own figures to a question, but plenty of models take text only, and which ones is not
  something a reader should have to know. A refusal over the pictures is now recognised for what it
  is and the question is asked again without them, and the prompt stops describing figures the
  model was never handed so it cannot point at one that is not there. Formats no provider decodes,
  and pictures that came back empty, are left behind before the request is built rather than
  breaking it.

### Added

- A book you have finished wears a tick on its cover in the library, in the same corner and at the
  same size as the download button on a search result, so the two read as one family of marks.

- Every queued download shows its steps. A card carries the book's own cover in the top left rather
  than an icon for whichever state it happens to be in, and a collapsible list underneath, indented
  to sit under the title, walks through waiting its turn, finding the file, downloading, and
  converting to EPUB for a PDF. The step in hand is highlighted and carries the wavy bar; the ones
  behind it are ticked off and the ones ahead wait their turn. Closed, the card shows just that
  step and its bar. Finding the file is now its own step rather than being counted as downloading,
  which is what a long pause with nothing moving actually was.

- Shelves can be made and filled from the library. A button pinned to the right of the shelf row,
  below the search box, makes a new one without going through a book first, and holding any shelf
  you made turns the grid into a picker for it: tap covers to add or remove, and the bar at the top
  counts what you have picked. Only the difference is written, so a shelf of fifty leaves
  forty-nine rows untouched.

- Downloads and conversions share one queue. Turning a PDF into an EPUB used to draw a bare strip
  over the library grid that said what was happening but not where it had got to; it is now a card
  in the download queue like everything else, with the page it is on and a bar. The queue's own
  summary lost its progress bar and became a line of text, so the only bars on the page are the
  per-item ones, each with the percentage beside it.

- Settings split into Basic and Advanced. Basic holds what gets touched while reading: how a book
  looks, how pages turn, and which model answers questions. Advanced holds the typographic fine
  tuning, book sources, PDF conversion, assistant tuning and storage. The explanatory text
  throughout was cut back to a line where a line was needed and dropped where it was not.

- Downloads have their own page, between Library and Discover. The queue used to be a strip that
  the book grid pushed around; it is now a screen that can say what is actually happening. Each
  download is a card with its title, author, format and size, a wavy bar while it moves, and the
  speed and time left worked out from watching the byte count climb. Running downloads can be
  **paused** and picked up again from the byte they stopped on rather than started over, and a
  line at the top totals the whole queue: how many books are coming, how fast, and when the last of
  them should land. Filter chips carry their own counts —
  All, In progress, Needs you, Done — and the overflow retries everything that stopped short or
  clears out the finished rows in one go. The navigation item wears a badge while anything is
  queued or waiting on you, so a download that fails while you are reading does not go unnoticed.

- Notes have somewhere to live. Selecting a passage now offers **Note** next to Highlight, which
  marks it and opens the note in one gesture, and the contents button in the reader opens a sheet
  with three tabs — Contents, Bookmarks, Notes — instead of one list with the bookmarks oddly
  stacked on top of the chapters. The Notes tab lists every marked passage in reading order and
  jumps back to any of them. The same notes appear in a book's details in the library, so a book
  can be looked back over without opening it, and either place can send the lot out as Markdown:
  the quote, the note beneath it and how far into the book it sits, in a file a notes app, a
  document or a mail draft will take.

- The reader knows how long you have left. Every settled page turn extends a reading session, and
  from those Readana works out your pace in Readium positions per minute — about a kilobyte of
  content each, so a pace learned from one book means something in the next. The bottom bar's
  second line then says "About 8 min left in this chapter" instead of a position count, and the
  library's shortcut says how much of the book is still to come. Nothing is promised until there is
  enough reading to know: short glances, books left open on a table and drags of the slider are all
  thrown out, a book uses its own pace once it has been read three times, and until any of that is
  true the old position count is what shows.

- **Define** in the selection menu. A word goes to Wiktionary — no key, no quota, and the same
  answer on every device — and comes back as senses in a small sheet. It works on the first page of
  a book, which is exactly where the assistant will not talk yet. Underneath sits "Ask the
  assistant instead", which is also the whole answer when the reader has selected a phrase rather
  than a word, so nothing has to guess in advance which of the two was wanted.

- Readana reads aloud. The bottom bar gains a Listen button for books that have text to read;
  while a voice is going a small floating toolbar appears over the page with previous, play, next,
  speed and stop, and it leaves again when the reading does. The page follows the voice and marks
  the sentence being spoken, in its own layer so it never disturbs the highlights. Reading aloud
  stops when you leave the reader: doing it in the background needs a media session and a
  foreground service, which is a piece of work of its own (ADR 0016).

- The assistant is reachable without selecting anything first. The conversations button now opens
  on **Ask about this book** and **Where was I?**, above the conversations already held. Both were
  there before but neither could be reached on purpose — a recap only ever appeared by itself after
  a few days away, and asking anything required finding a passage to select first. A question asked
  this way is given exactly the text above where the reader has got to, the same walk backwards a
  recap uses, so the no-spoilers guarantee is untouched.

- The library opens on the book you were reading. A card at the top says which one, how far in and
  roughly how long is left, and picks up where you were. It is a shortcut rather than a result, so
  it steps out of the way the moment a search or a filter is on.

- Collections, series and a reading filter. Books can go on shelves the reader names — a book can be
  on several, which is what tags were for — and Readana adds a shelf for any series it finds more
  than one book of in the publisher's own metadata. Both appear as one scrolling row of chips under
  the search field, and only once there is a shelf to show. The top bar gains a filter beside the
  sort, in the same shape, for All, Reading, Unread and Finished; a book can also be called finished
  by hand from its details, which outranks how far the progress bar happens to have got.

- Reading settings belong to the book. Change the margins in a comic and the next novel is
  unaffected: a book keeps its own copy of every setting from the moment you adjust one while it is
  open, with a line at the bottom of the sheet offering to hand it back to the defaults. Those
  defaults moved to a new **Reading** section on the Settings screen, which is also the first time
  they could be set without opening a book at all (ADR 0015).

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

- The app wears Material 3 Expressive's own colours. The hand-mixed warm-brown palette is gone in
  favour of `expressiveLightColorScheme()` — the scheme `MaterialExpressiveTheme` itself defaults
  to, the baseline violet with its on-container roles pulled down to tone 30 for the contrast
  expressive containers are drawn with — paired with the baseline `darkColorScheme()`, since
  Material ships no expressive dark counterpart. Material You dynamic colour is off by default now
  rather than on: it was quietly replacing the whole palette with the wallpaper's on every device
  since Android 12, so nothing chosen here was ever what you saw. Pass `dynamicColor = true` to
  `ReadanaTheme` to have it back.

- A PDF converting after a download no longer gets a card of its own beside the download that
  started it. It is the same book one step later, so it appears as the converting step inside that
  download's card, with the engine's own phases behind it. Only a conversion you start from a book
  already in your library still stands alone.

- The download queue keeps only what is still going. A finished download drops out the moment its
  book reaches the library, which is where you would look for it anyway, and the "Done" filter went
  with it. What is left is what still needs watching or deciding.

- The reader's contents sheet is tabbed rather than one long list — Contents, Bookmarks, Notes —
  where it used to be one scrolling column with the bookmarks stacked above the chapters.

- Settings is one page again. The five collapsible cards are gone: every setting is visible by
  scrolling, under a heading for each of sources, PDF conversion, the reading assistant and
  storage. The rows are Material 3 expressive segmented list items — a group of settings rounds its
  outer corners as one block — the either/or choices (heading detection, how much of the book to
  send) are connected button groups instead of loose chips, the title is a large flexible app bar
  that collapses as you scroll, and the whole column is capped at a readable width instead of
  stretching across the tablet. The assistant's providers are a single-choice list whose chosen
  provider shows its key, address and model directly beneath it, so nothing is behind a chevron;
  a custom provider can still be deleted straight from its row.
- The download queue on the library screen no longer grows without limit. It stops at about four
  rows and scrolls, so a long queue can no longer push the book grid off the bottom of the screen.
- Settings no longer has a "Downloads & background" group. Its shortcuts to Xiaomi's battery and
  autostart screens are gone; the HyperOS advice lives in `docs/DEVICE.md`.
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
  nothing behind. The budget is now far larger than any answer needs — unused budget costs nothing,
  since providers bill what is written — and a model too small to accept that ceiling is simply
  asked again within one it can meet. An empty answer is reported with the reason the provider gave, and an answer consisting only of a picture that could not be found says that
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
