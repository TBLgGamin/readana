# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and versions follow
[Semantic Versioning](https://semver.org/).

## [Unreleased]

### Fixed

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
