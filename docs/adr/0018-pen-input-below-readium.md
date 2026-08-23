# 18. Pen input is handled below Readium, and ink is honest about reflow

Date: 2026-08-23

## Status

Accepted.

## Context

The Xiaomi Pad 8 ships with a Focus Pen Pro — hover, 16 384 pressure levels, and no vendor SDK
worth using ([docs/DEVICE.md](../DEVICE.md)). Two things were wanted from it: a hand resting on the
page should not turn it, and a stroke should be able to highlight text or leave a line.

The obvious place to put that is Readium's input layer. It does not work:

- `InputListener` carries `TapEvent(PointF)` and `DragEvent(Type, PointF, PointF)`. There is no
  tool type in either, verified against the 3.3.0 AAR.
- For EPUB the events do not come from Android at all. `readium-reflowable.js` binds `touchstart`
  inside the WebView and reports back over the JS bridge; it never reads `pointerType`. By the time
  Readium says "a tap happened", whether it was a nib or a palm is gone.
- `EpubNavigatorFragment.Configuration` offers `servedAssets`, `javascriptInterfaces`,
  `decorationTemplates` and `selectionActionModeCallback`. None of them run a script on demand.

Ink raises a second problem that has nothing to do with Readium. A highlight is a range of text, so
it survives any change of layout. A stroke is a shape over a place — and on a reflowable EPUB, that
place moves the moment the font size, the margins or the column count change.

## Decision

**Touches are sorted before the WebView sees them.** `ReaderNavigatorHostFragment` now returns a
`ReaderSurfaceView` wrapping the navigator's container. It sees the raw `MotionEvent`, where
`getToolType()` is still available, and:

- drops finger events while the pen is down or hovering, and for 300 ms after it lifts — which is
  when the palm actually lands;
- takes pen drags for itself when a pen mode is on, so they never reach the page as taps.

The rule itself lives in `StylusTouchRouter`, free of Android views, so it is unit-tested rather
than only tried on a device.

**A pen highlight is made by the page, not reimplemented.** Rather than work out which words a
stroke crossed, `PenSelection` asks the WebView: `caretRangeFromPoint` at each end of the stroke,
joined into a range, set as the document's own selection. From there
`SelectableNavigator.currentSelection()` reads it exactly as it does after a long press, and the
existing `addHighlight` path — Room, decorations, the notes list, the Markdown export — carries on
untouched. The script is run with `evaluateJavascript` on the WebView found in the navigator's view
tree, because Readium offers no hook for it.

**Ink says when it might be wrong.** A stroke stores the locator of the element it started on, its
points normalised to the surface it was drawn on, *and* a fingerprint of the reading settings in
force — font scale, family, line height, paragraph spacing, margins, page-turn mode, columns.
When the current fingerprint differs, the ink is still drawn, but faintly, because the text under
it has moved. On a PDF or a fixed-layout EPUB the page never reflows and the fingerprint never
changes, so ink there is simply exact.

## Consequences

- Palm rejection and pen highlighting work on EPUB, which was the whole point, and they cost
  Readium no patch and no fork.
- `PenSelection` depends on `caretRangeFromPoint` and on finding the WebView by walking the view
  tree. Both are undocumented territory as far as Readium is concerned, and a future version that
  restructures the navigator's views would break it. It fails closed: no WebView, or no selectable
  text under the stroke, leaves the page exactly as it was.
- Ink on a reflowable book can be misplaced, and the app admits it rather than drawing a confident
  line through the wrong sentence. Re-anchoring a faded stroke is not implemented; it can be
  erased and redrawn.
- Nothing changes when no pen is present. Palm rejection ignores nothing until a stylus appears,
  and `PenMode.Off` makes the pen behave like a finger for anyone who prefers that.
- The database gained an additive `ink_strokes` table, so `AutoMigration(11, 12)` covers it, in
  keeping with the rule that every version only adds.
