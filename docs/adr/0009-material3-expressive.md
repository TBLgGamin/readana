# 0009 — Material 3 Expressive for the whole UI

Date: 2026-08-21 · Status: accepted

## Context

The app should use Google's newest Material Design throughout. Material 3 Expressive is the current
evolution of Material 3: `MaterialExpressiveTheme`, an expressive motion scheme, and components
such as `LoadingIndicator`. In material3 1.4.0 — the version the pinned Compose BOM resolves — those
APIs exist but are `internal`, so they cannot be used from application code.

## Decision

- `ReadanaTheme` builds on `MaterialExpressiveTheme` with `MotionScheme.expressive()`.
- `androidx.compose.material3:material3` and its `material3-adaptive-navigation-suite` sibling are
  pinned ahead of the Compose BOM (`androidxMaterial3` in the version catalog) to the newest release
  that exposes the expressive APIs. Everything else still comes from the BOM.
- The experimental opt-in lives in the Compose convention plugin, so screens carry no annotations.
- Screens use stock Material 3 components only — no custom-drawn widgets — so the design stays
  consistent as the library evolves.

## Consequences

- material3 tracks a pre-release version, so it must be upgraded deliberately and its changelog read
  before bumping; Dependabot's Compose group will propose the bumps.
- Expressive APIs may change shape between releases. They are confined to the theme and to stock
  component usage, which keeps the blast radius small.
- The two material3 artifacts move together; upgrading one without the other risks a runtime
  mismatch with the navigation suite.
