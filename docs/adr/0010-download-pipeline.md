# 0010 — Download pipeline

Date: 2026-08-21 · Status: accepted

## Context

M3 turns a Discover search result into a book in the library. On HyperOS that transfer has to
survive the app going to the background (see `docs/DEVICE.md`), it has to resume when the tablet
loses Wi-Fi, and it has to cope with Anna's Archive: a paid membership serves a direct link through
`/dyn/api/fast_download.json`, but the daily allowance runs out and some downloads are only
reachable through a wait-list page a script cannot drive.

## Decision

- **One `CoroutineWorker` per job**, enqueued as unique work named `download:<sourceId>:<recordId>`
  with `ExistingWorkPolicy.KEEP`. WorkManager already provides persistence, exponential backoff and
  cancellation per job, and the unique name is the duplicate rule: a record that is enqueued or
  running cannot be queued twice, while a finished one can be retried.
- **The `downloads` table is the source of truth for the UI**, not `WorkInfo`. The worker writes
  progress rows; Library observes the DAO flow. This survives process death and keeps WorkManager
  types out of feature modules.
- **`DownloadStatus` keeps its original states**; failures carry a `DownloadErrorKind`
  (`QuotaExhausted`, `RequiresBrowser`, `Unauthorized`, …). A spent quota is a *reason* a job
  failed, not a state it can leave on its own — the rolling 24-hour window is not something we can
  schedule against — so the queue renders a different action instead of inventing a state.
- **Resolve at enqueue, re-resolve on retry.** Resolving immediately gives the user instant feedback
  and the remaining quota, and the worker starts seconds later while the link is fresh. Retries and
  expired links (403/404/410) resolve again; that is free, because Anna's does not charge quota for
  an md5 in `recently_downloaded_md5s`.
- **The worker streams into `filesDir/downloads/<jobId>.part`, then calls the existing
  `BookImporter`.** Resuming is a `Range` header against that file. Reusing the importer means
  metadata extraction, cover writing, SAF handling and duplicate detection stay in one place; the
  cost is one extra copy, which is nothing at ebook sizes. Writing straight into the user's SAF
  folder was rejected: a crash would leave half a book sitting in it.
- **Browser downloads re-enter the same pipeline.** When the API cannot serve — no key, spent quota,
  or a source that says `requiresBrowser` — the in-app browser opens the record page and its
  `DownloadListener` hands the captured URL plus cookies back as a job whose headers are already
  set. Such a job is never re-resolved, because its session cannot be reconstructed outside the
  WebView. Anna's Archive has no password, so the membership key is posted to `/account/` to sign
  the WebView in first.
- **`BookDownloader` holds the logic, `DownloadWorker` is a shell.** Resuming, re-resolution and the
  retry/terminal decision are testable with a Ktor `MockEngine` and a temp directory; the worker
  only maps outcomes onto `Result` and owns the notification.

## Consequences

- `core/data` depends on WorkManager and declares the `dataSync` foreground service and the
  notification permissions in its own manifest, since it owns the worker.
- The app must initialise WorkManager on demand (`Configuration.Provider` plus removing the default
  initialiser) so Hilt can construct workers.
- Android 15 caps `dataSync` foreground services at six hours per day. Requests have no overall
  timeout but do keep a 60-second socket timeout, so a dead connection fails into a retry instead of
  holding that budget open.
- A retry started while the app is in the background may be refused foreground promotion on
  Android 12+. The transfer still runs unpromoted; HyperOS may then kill it, which is what the
  battery-restriction guidance in Settings is for.
- Declining the notification permission costs only visibility: progress remains in the Library
  queue, and enqueueing never waits on the grant.
