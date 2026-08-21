# Research notes (2026-08-20)

What was evaluated before scaffolding the repository, and what was decided. Device facts live in
[DEVICE.md](DEVICE.md); decisions are recorded as [ADRs](adr/).

## 1. App platform

The Pad 8 is a regular Android 16 tablet, so every mainstream stack runs on it: native
Kotlin/Compose, Flutter, React Native, Kotlin/Compose Multiplatform, .NET MAUI, Tauri v2, PWA.

For an e-reader the **rendering engine** dominates the choice:

| Option | Engine | Assessment |
|--------|--------|------------|
| **Native Kotlin + Jetpack Compose** | Readium Kotlin Toolkit (WebView-based EPUB, pdfium PDF) | Mature, BSD-licensed, 100+ apps; best access to WorkManager/SAF/foreground services that HyperOS demands. **Chosen.** |
| Flutter | foliate-js in a WebView (as in Anx Reader) | Works, but bridging a JS renderer through platform channels adds latency and two languages. |
| Tauri v2 / web | foliate-js + PDF.js (as in Readest) | Readest proves it ships on Android, but background downloads/conversion are awkward in a web shell. |
| KMP / Compose Multiplatform | Readium is Android-only; would need a WebView renderer | No second platform is planned, so no payoff. |

Open-source apps worth studying: [KOReader](https://github.com/koreader/koreader) (Lua, AGPL;
k2pdfopt-based PDF reflow), [Librera](https://github.com/foobnix/LibreraReader) (GPL; PDF
reflow), [Readest](https://github.com/readest/readest) (AGPL; Tauri + foliate-js),
[Anx Reader](https://github.com/Anxcye/anx-reader) (MIT; Flutter + foliate-js),
[Book's Story](https://github.com/Acclorite/book-story) (GPL; Kotlin/Compose),
[Myne](https://github.com/Pool-Of-Tears/Myne) (Apache-2.0; Kotlin/Compose Gutenberg downloader
with Room + Hilt — closest in spirit), Readium's own test app in
[kotlin-toolkit](https://github.com/readium/kotlin-toolkit).

## 2. EPUB rendering

- **Readium Kotlin Toolkit 3.3.0** (Maven Central, `org.readium.kotlin-toolkit:*`, minSdk 23,
  built with Kotlin 2.3.20 / AGP 9.0 / compileSdk 36). Modules used: `readium-shared`,
  `readium-streamer` (parsing), `readium-navigator` (EPUB navigator fragment, decorations,
  preferences, TTS via `readium-navigator-media-tts`), `readium-adapter-pdfium` (PDF; pulls
  `com.github.marain87:PdfiumAndroid` from JitPack). `readium-navigator-web-reflowable` is a new
  Compose-native navigator (depends on `compose.foundation`) but still experimental — we start with
  the fragment navigator hosted via `AndroidFragment`, and can migrate later.
- Alternatives: epub.js/foliate-js in a WebView (more JS glue, fewer native features),
  FolioReader (abandoned), MuPDF (AGPL — licence would force the whole app AGPL), epublib/epub4j
  (parsing only).

## 3. PDF → EPUB on the device

Calibre's `ebook-convert` does not run on Android, so conversion is built from parts:

| Building block | Role | Notes |
|----------------|------|-------|
| **PDFBox-Android 2.0.27.0** (`com.tom-roush`, Apache-2.0) | Text + positions + font names per glyph (`PDFTextStripper` / `TextPosition`) | Needs `PDFBoxResourceLoader.init(context)`; no layout analysis of its own |
| **Own reflow heuristics** (`engine/reflow`, pure Kotlin) | Header/footer removal by repetition, paragraph detection by gap/indent/short-line/font change, hyphen re-joining, heading detection by size/bold, chapter splitting | Mirrors what Calibre's PDF input plugin does; unit-tested on synthetic pages |
| **Own EPUB 3 writer** (`engine/epub`) | mimetype-first OCF container, package document, nav document, XHTML chapters, CSS | Validated with `org.w3c:epubcheck:5.3.0` in tests |
| Not chosen | MuPDF structured text (AGPL), pdfium text API (weaker block detection), Android `PdfRenderer`/`androidx.pdf` (API 35+, text extraction still limited), Chaquopy + Python tools (large runtime, wheels uncertain), ML models (marker/nougat; far too heavy) | |

Expected quality: good for single-column text PDFs (novels, papers without figures), mediocre for
multi-column layouts, none for scanned PDFs (no OCR). The original PDF stays in the library and can
still be read through Readium's pdfium navigator.

## 4. Book sources

### Anna's Archive

Verified 2026-08-21 against the site's own source (<https://software.annas-archive.gl>,
`allthethings/utils.py` and `allthethings/page/views.py`) and by measurement from a Dutch
connection. See ADR 0013 for what the app does about it.

- **Everything worth reaching is behind a browser check.** `/search`, `/md5/<md5>` and
  `/slow_download/...` all call `check_for_recent_ddg_cookie_or_membership()`. A request that is
  neither a signed-in member nor recently checked is redirected to the same URL plus `check=1`,
  which the front-end serves as a JavaScript challenge (`403`, `<title>DDoS-Guard`, scripts under
  `/.well-known/ddos-guard/js-challenge/`). Passing it mints `aa_ddg_check` and `__ddg5_`, treated
  as recent for **15 minutes** on search and **7** on slow downloads. A cookie-less client cannot
  get through at all — this, not per-IP throttling, was behind the 400s and 403s.
- **A membership does unlock search.** `POST /account/` with form field `key=<secret key>` sets
  `aa_account_id2` (a 90-day JWT); `check_skip_browser_checks_and_get_account_id()` then waves that
  session past the check, up to 180 searches an hour. Cookies are per host, so it is one login per
  mirror.
- **There is still no search API** ("We don't yet have a search API" — FAQ), so the result page is
  scraped. Ask for `display=table`: the site's `aarecord_list.html` macro then emits one row per
  record with fixed columns, which survives its frequent restyling of the card layout. Part of the
  list ships inside HTML comment nodes that the page un-comments in JavaScript, so a parser has to
  read those too. `MAX_DISPLAY_RESULTS = 50` per page; `ext`, `lang`, `content`, `src` and `acc` are
  all repeatable filters.
- **Members' downloads**: `GET /dyn/api/fast_download.json?md5=<md5>&key=<key>` — no browser check.
  Documented failures: `400 Invalid md5`, `401 Invalid secret key`, `403 Not a member`,
  `404 Record not found`, `429 No downloads left`, alongside
  `account_fast_download_info.downloads_left`.
- **Free downloads**: `/slow_download/<md5>/<path_index>/<domain_index>`. Domain indexes 0–3 are
  quicker but may return a wait-list countdown; 4–7 never wait and run about three times slower.
  The page either carries the signed partner URL (`https://<partner>/d3/…`, good for two hours,
  plain GET, no cookies) or `let waitSeconds = N` and reloads itself. Past 20 downloads a day per
  IP the waits double.
- **Library Genesis is the better free route.** It holds most of what Anna's Archive indexes and
  serves it by md5 with no check and no wait: `GET https://libgen.li/ads.php?md5=<md5>` yields
  `<a href="get.php?md5=…&key=…">`, which 307s to a CDN that honours `Range`. Verified working
  2026-08-21. Sibling hosts `libgen.la`, `libgen.vg`, `libgen.bz` share the same catalogue.
- **Domains rotate.** The FAQ authorises `.gl`, `.pk` and `.gd`. On 2026-08-21 `.org` and `.se` no
  longer resolved and `.li` served only a `<meta refresh>` interstitial that mentions the site — it
  reads as an empty result page unless detected. Hence a user-editable list, a remembered choice,
  and a cooldown rather than removal.
- **Do not use third-party "Anna's Archive APIs".** They are other people's scrapers: they see the
  user's searches and vanish without notice.
- **Legal**: downloading copyrighted works without permission is unlawful in most countries; the
  app shows a notice in Settings and ships the source unconfigured.

### Legitimate / open sources

- **Project Gutenberg** via [Gutendex](https://gutendex.com) (`/books/?search=…&languages=…&page=…`,
  EPUB links under `formats["application/epub+zip"]`) — implemented, but **not registered** as a
  search source: a public-domain-only catalogue mostly added noise to results.
- **Open Library trending** (`/trending/weekly.json?limit=n` → `works[].title`, `author_name`,
  `cover_i`; art at `https://covers.openlibrary.org/b/id/<cover_i>-M.jpg`) — no key, no throttling,
  ships cover art. Used for the Discover "Popular right now" shelf, deliberately *not* Anna's
  Archive, whose scraping budget belongs to real searches.
- Planned: **OPDS** catalogs (Standard Ebooks, Feedbooks, DOAB/OAPEN, Calibre-Web) through
  `readium-opds`; **Internet Archive**; **arXiv** (PDF → converted).

## 5. Toolchain versions (verified against Maven Central / Google Maven on 2026-08-20)

| Component | Version | Notes |
|-----------|---------|-------|
| Gradle | 9.7.1 | released 2026-08-19; runs on JDK 17–24 |
| Android Gradle Plugin | 9.3.1 | needs Gradle ≥ 9.5, JDK ≥ 17; max API 37; **built-in Kotlin** (no `kotlin-android` plugin) |
| Kotlin | 2.4.10 | Compose compiler plugin ships with Kotlin |
| KSP | 2.3.11 | decoupled from Kotlin versioning |
| Compose BOM | 2026.08.00 | material3 1.4.0, ui/foundation 1.12.0, adaptive 1.3.0; AARs require **compileSdk 37** |
| Navigation 3 | 1.1.6 | `rememberNavBackStack(configuration, …)` requires a polymorphic `SavedStateConfiguration` |
| Hilt | 2.60.1 | Hilt Gradle plugin requires AGP 9 since 2.59 |
| Room | 2.8.4 | Room Gradle plugin for schema export |
| Ktor | 3.5.2 | OkHttp engine, MockEngine in tests |
| Readium | 3.3.0 | see §2 |
| ktlint / Spotless / compose-rules | 1.8.0 / 8.10.0 / 0.6.4 | compose-rules 0.6.x targets ktlint 1.8 and detekt 2.0 |
| detekt | 2.0.0-alpha.6 | built against Kotlin 2.4.10, AGP 9.3.1, Gradle 9.6 — 1.23.x predates Gradle 9 |
| Konsist / Robolectric / Turbine / MockK | 0.17.3 / 4.16.1 / 1.2.1 / 1.14.11 | |
| GitHub Actions | checkout v7, setup-java v5, gradle/actions v6, upload-artifact v7 | |

## 6. Storage on Android 15/16

- App-specific storage needs no permission, survives updates, is deleted on uninstall.
- For a user-visible library folder use SAF `ACTION_OPEN_DOCUMENT_TREE` +
  `takePersistableUriPermission()`; `MediaStore.Downloads` loses attribution on reinstall;
  `MANAGE_EXTERNAL_STORAGE` works for a side-loaded personal app but is a blunt instrument.
- Decision (ADR 0007): app-specific storage by default, optional SAF tree, metadata in Room.
