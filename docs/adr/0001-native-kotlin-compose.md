# 0001 — Native Android app in Kotlin + Jetpack Compose

Date: 2026-08-20 · Status: accepted

## Context

The app targets one device family (Xiaomi Pad 8, Android 16). It needs a high-quality EPUB
renderer, robust background downloads under HyperOS's aggressive process management, on-device
PDF processing, and large-screen/stylus support. Candidates: native Kotlin/Compose, Flutter,
React Native, Kotlin/Compose Multiplatform, Tauri v2, PWA.

## Decision

Native Android in Kotlin with Jetpack Compose and Material 3 adaptive components.

## Consequences

- Direct access to WorkManager, foreground services, SAF, window size classes and the Readium toolkit.
- No second platform; a KMP migration later stays possible because domain/engine/source modules are pure Kotlin.
- Compose conventions are enforced by compose-rules (ktlint + detekt).
