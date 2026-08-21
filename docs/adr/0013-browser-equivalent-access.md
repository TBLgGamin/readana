# 0013 — Browser-equivalent access for shadow-library sources

Date: 2026-08-21 · Status: accepted

## Context

Anna's Archive searches and downloads failed with an assortment of 400s and 403s, for members and
free users alike. Reading the site's own source (`allthethings/utils.py`,
`allthethings/page/views.py` on <https://software.annas-archive.gl>) explains why:

`/search`, `/md5/<md5>` and `/slow_download/...` all run `check_for_recent_ddg_cookie_or_membership()`.

| Request | What the site does |
|---|---|
| carries a valid member session cookie `aa_account_id2` and the account has an active membership | serves the page; 180 searches/hour |
| anything else | `302` to the same URL with `check=1`, which the front-end answers with a JavaScript challenge (`403`, `<title>DDoS-Guard`, `/.well-known/ddos-guard/js-challenge/*`) |

Passing the challenge mints `aa_ddg_check` (plus `__ddg5_`), good for 15 minutes on `/search` and 7
on `/slow_download`. So a cookie-less HTTP client can never get through, no matter what it sends,
and a client that does not keep cookies re-fails every single request. The earlier reading — that
these were per-IP throttling and that a membership key only unlocked downloads — was wrong.

Two more facts shape the design. There is still **no search API** (the FAQ says so outright), so the
result page has to be scraped. And most of what Anna's Archive indexes actually lives in Library
Genesis, which serves the same file by md5 over plain HTTP with no check and no wait.

## Decision

- **One HTTP client that is indistinguishable from the app's own browser.** The Ktor client runs on
  an OkHttp `CookieJar` backed by the system WebView's cookie store, and an interceptor stamps every
  request with `WebSettings.getDefaultUserAgent`. A check cleared in any WebView therefore counts
  for plain requests too, which is the whole point: these cookies are only honoured for the client
  that earned them. The user agent is set in exactly one place — a per-request one would silently
  invalidate the cookie.
- **A member is signed in rather than verified.** With a key configured, the source posts it to
  `/account/` once per mirror per session; the site then waves the session past the check entirely.
  This is also why a membership is worth having for *searching*, not only for download speed.
- **Everyone else has the check answered out of sight.** `BrowserVerifier` (declared in
  `source/api`, implemented in `core/data` as a hidden `WebView`) loads the page, lets its
  JavaScript run, and returns once the site serves something real. The request is then repeated
  exactly once. Reactive, not scheduled: a stale cookie costs one cheap `302 + 403` probe, which is
  cheaper than tracking two different expiry windows per host.
- **Detection ignores the `Server` header.** Every response from the site carries `ddos-guard`,
  challenge or not; only the challenge page's own markers and the `check=1` parameter are evidence.
- **A download walks a ladder**: a member's fast link, then Library Genesis, then the site's free
  partner servers (preferring the numbers that never queue), then the visible browser. A spent daily
  allowance drops to the free rungs instead of dead-ending at the browser.
- **No third-party "Anna's Archive API".** Every such service is someone else's scraper: it sees the
  user's searches, and it breaks or disappears without notice.

## Consequences

- Cookies are per host, so switching mirror costs one extra login or verification. The mirror that
  worked is remembered in settings to keep that rare.
- Verification needs a WebView and the main thread, and takes a second or two the first time. A
  device without one, or a check that will not settle, reports `AppError.BrowserCheck` with the URL,
  and the UI offers to open it — the check still passes, just with a person watching.
- Resolving a download can now block for seconds, so it happens in the download worker rather than
  on the tap (see ADR 0010).
- The client is shared with Coil, so cover art also carries the cookies and the user agent.
- The app must stay at human pace: no search-as-you-type and no prefetching record pages. The site's
  own limits (180 searches/hour for members, a widening wait-list past 20 free downloads a day) are
  generous for a person and easy to trip with automation.
- If the site swaps DDoS-Guard for something else, `DdosGuard.isChallenge` is the single place that
  has to learn the new markers; Cloudflare's are already there.
