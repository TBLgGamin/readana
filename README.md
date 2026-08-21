# Readana

[![CI](https://github.com/TBLgGamin/readana/actions/workflows/ci.yml/badge.svg)](https://github.com/TBLgGamin/readana/actions/workflows/ci.yml)

A personal e-reader for the **Xiaomi Pad 8** (Android 16 / HyperOS 3). Search and download
EPUBs and PDFs from pluggable book sources, convert PDFs to reflowable EPUB on the device,
keep everything in a persistent on-tablet library, and read with the controls you expect from a
dedicated e-reader app.

> **Status: M0 – project scaffold.** The build, quality gates, module structure, data layer and
> source abstraction are in place; the reading view, downloads and conversion UI land in the
> next milestones. See [docs/ROADMAP.md](docs/ROADMAP.md).

## What it will do

| Area | Plan |
|------|------|
| **Discover** | One search box fanned out to every configured `BookSource` (Project Gutenberg today; Anna's Archive with member API + browser fallback; OPDS catalogs later). |
| **Download** | WorkManager-driven background downloads that survive HyperOS's aggressive process killing, with progress in the library. |
| **Convert** | On-device PDF → EPUB: PDFBox text extraction → layout heuristics (headers/footers, paragraphs, hyphenation, headings) → valid EPUB 3. Validated with epubcheck in tests. |
| **Library** | Room database + files in app storage (or a user-chosen SAF folder), covers, reading progress, sort/filter. |
| **Read** | Readium Kotlin Toolkit navigator: paginated/scrolled, tap zones & swipe, font/size/margins/line-height, themes (light/sepia/dark/black), brightness gesture, progress slider, TOC, search, highlights, TTS later. |

## Stack

| Concern | Choice | Why |
|---------|--------|-----|
| Language / UI | Kotlin 2.4, Jetpack Compose (BOM 2026.08), Material 3 + adaptive layouts | Native performance, tablet window-size classes, first-class Readium support |
| Build | Gradle 9.7 (wrapper, checksum-pinned), AGP 9.3 with built-in Kotlin, convention plugins in `build-logic/`, version catalog | Current stable toolchain; modules stay 5–10 lines each |
| Architecture | Multi-module (app / feature / core / engine / source), Hilt, Navigation 3, unidirectional state | Clear seams, fast tests, swap-able sources and engines |
| Reading engine | [Readium Kotlin Toolkit](https://github.com/readium/kotlin-toolkit) 3.3 (BSD-3) | Mature EPUB 2/3 + PDF support, used by 100+ apps |
| PDF text | [PDFBox-Android](https://github.com/TomRoush/PdfBox-Android) (Apache-2.0) + own reflow engine | Permissive licence; heuristics testable on the JVM |
| Persistence | Room 2.8, DataStore Preferences | Standard, schema-exported, migration-tested |
| Networking | Ktor 3 (OkHttp engine), kotlinx.serialization, jsoup | Pure-Kotlin sources testable with `MockEngine` |
| Quality | Spotless + ktlint (+ compose rules), detekt 2, Android Lint (warnings = errors), Konsist architecture tests, Dependabot, GitHub Actions | See [docs/adr/0005](docs/adr/0005-quality-tooling.md) |

Versions are pinned in [`gradle/libs.versions.toml`](gradle/libs.versions.toml) and kept current by Dependabot.

## Getting started

Prerequisites: **JDK 17+** (any vendor; 21 in CI), the **Android SDK** with *platform 37* (compileSdk) and
*build-tools 36.0.0* (Android Studio installs these, or use `sdkmanager`). Point Gradle at the SDK
with `local.properties` (`sdk.dir=…`) or `ANDROID_HOME`.

```bash
git clone https://github.com/TBLgGamin/readana.git && cd readana
./gradlew assembleDebug                     # first run downloads ~1 GB of dependencies
adb install app/build/outputs/apk/debug/app-debug.apk
```

Device setup for the Pad 8 (developer options, the "Install via USB" Mi-account gotcha, battery
restrictions): [docs/DEVICE.md](docs/DEVICE.md).

### Quality gates

| Command | What it does |
|---------|--------------|
| `./gradlew spotlessApply` | Format Kotlin, Gradle scripts, Markdown/YAML/XML (ktlint 1.8, Android Studio style) |
| `./gradlew spotlessCheck detekt` | Formatting + static analysis, same as CI |
| `./gradlew :app:lintDebug` | Android Lint over every module (`checkDependencies`) |
| `./gradlew testDebugUnitTest test` | JVM unit tests (Android modules + pure Kotlin modules) |
| `./gradlew check` | Everything above |

Optional local hooks: `pre-commit install --hook-type pre-commit --hook-type pre-push`
(see [`.pre-commit-config.yaml`](.pre-commit-config.yaml)).

## Repository layout

```
app/                      Android application: Hilt root, MainActivity, Navigation 3 host, adaptive nav
build-logic/convention/   Gradle convention plugins (readana.android.*, readana.jvm.library, readana.hilt, …)
config/                   detekt, lint and compose-compiler configuration shared by all modules
core/common               Result/AppError types, coroutine qualifiers            (pure Kotlin)
core/model                Domain models: Book, ReaderSettings, DownloadJob, …   (pure Kotlin)
core/data                 Room, DataStore, repositories, Hilt modules, source registry
core/designsystem         Theme, icons, shared composables
core/testing              Test rules and fakes                                  (pure Kotlin)
engine/epub               EPUB 3 writer, validated with epubcheck               (pure Kotlin)
engine/reflow             PDF layout → paragraphs/headings heuristics           (pure Kotlin)
engine/pdf2epub           PDFBox-Android text extraction + converter facade
source/api                BookSource interface, search/download models          (pure Kotlin)
source/gutenberg          Project Gutenberg via Gutendex                        (pure Kotlin)
source/annas              Anna's Archive: HTML search, member API, mirror failover (pure Kotlin)
feature/library|discover|reader|settings   Compose screens + ViewModels + nav keys
docs/                     Architecture, device notes, research, roadmap, ADRs
```

Module rules (enforced by Konsist tests in `app/src/test`): features never import each other;
`core/model`, `core/common`, `engine/*` and `source/*` never import Android; sources and engines
never depend on the data layer.

## Documentation

- [Architecture](docs/ARCHITECTURE.md) — module graph, data flow, navigation, testing strategy
- [Device notes](docs/DEVICE.md) — Xiaomi Pad 8 hardware/OS facts and developer setup
- [Research](docs/RESEARCH.md) — what was evaluated and why (engines, conversion, sources, toolchain)
- [Roadmap](docs/ROADMAP.md) — milestones M0–M6
- [Decision records](docs/adr/) — the "why" behind the stack
- [Contributing](CONTRIBUTING.md) · [Security](SECURITY.md) · [Changelog](CHANGELOG.md)

## A note on sources and copyright

Readana is a reader and a download manager; it does not host content. Project Gutenberg serves
public-domain books. Anna's Archive indexes shadow libraries, and downloading copyrighted works
without permission is illegal in most jurisdictions (its domains are also blocked by several ISPs,
including in the Netherlands). The Anna's Archive source is off until you configure it, the app
shows this notice in Settings, and what you download is your responsibility.

## License

Not yet chosen — until a `LICENSE` file is added, all rights are reserved by the author.
Third-party components keep their own licences (Readium BSD-3, PDFBox Apache-2.0, epubcheck BSD-3).
