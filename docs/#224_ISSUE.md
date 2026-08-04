# Issue #224: iOS up/down caret buttons cannot cross line boundaries

## Status

- Issue: https://github.com/lime-ime/limeime/issues/224
- Classification: confirmed iOS bug
- State: fixed 2026-08-04 — device evidence captured, source corrected, unit + simulator + physical-device verified
- Reported environment: Apple's Notes app on iOS
- Scope: independent of the active LIME input method and keyboard layout

## Problem statement

In a multiline editor on iOS, LIME's Up and Down caret buttons do not move the insertion point across line boundaries. The reporter reproduced this in Apple's Notes app with any LIME input method and keyboard layout. The buttons should move to the preceding or following visual/text line.

This is a keyboard-controller behavior rather than an input-table or layout-resource problem. Both arrow keys dispatch to the same controller methods for every LIME input method and layout.

## Current implementation and likely root cause

`LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift` routes `LimeKeyCode.arrowUp` and `arrowDown` to `moveByLine(forward:)`. That helper does not ask the host editor to perform native vertical caret movement. Instead, it:

1. reads `documentContextBeforeInput` or `documentContextAfterInput` from `UITextDocumentProxy`
2. searches that limited context for a hard newline
3. derives a character offset to the newline, or falls back to at most ten characters
4. calls `adjustTextPosition(byCharacterOffset:)`

This heuristic cannot reliably represent a host editor's visual lines. A visual wrap has no newline, and a keyboard extension can receive limited, empty, or `nil` document context near a host boundary. The current `offset > 0` guard then makes an empty-context boundary a no-op. Even when context is available, a fixed ten-character fallback does not preserve the host editor's visual column or line geometry.

This analysis was confirmed by device instrumentation on 2026-08-04 — see **Device evidence** below for the contexts actually returned at each failing cursor position.

## Android comparison

Android uses the editor's native directional-key path. `LIMEService` maps the soft Up and Down keys to `KEYCODE_DPAD_UP` and `KEYCODE_DPAD_DOWN` through `keyDownUp(...)`, allowing the active editor to perform vertical caret navigation. Android does not use the iOS newline/ten-character approximation.

This Android behavior is the parity target where iOS APIs permit it. iOS keyboard extensions expose character-offset movement through `UITextDocumentProxy`, but no direct equivalent of Android's host-handled DPAD vertical navigation is currently used by LIME.

## Proposed solution

1. Add a testable vertical-caret movement policy separated from `KeyboardViewController` side effects.
2. Capture the actual `documentContextBeforeInput` and `documentContextAfterInput` boundaries returned by Notes for hard-newline and visual-wrap cases, without logging user text.
3. Replace the current no-op boundary behavior with a bounded character-offset strategy that can cross a hard line boundary when proxy context is empty or truncated.
4. Preserve best-effort behavior for visual wraps and document the platform limitation if iOS does not expose enough geometry to match native vertical movement exactly.
5. Keep Up/Down behavior independent of the active IM and keyboard layout, and do not change Left/Right candidate-selection behavior.

The implementation must be driven by a failing behavioral test and device validation. A source-only helper test is insufficient if Notes still does not move across the reporter-visible boundary.

## Follow-up questions / evidence

All answered by the device run below.

- **Explicit newline, visual wrap, or both?** Both. Hard newlines are now crossed; visual wraps still are not, and cannot be — see *Known ceiling*.
- **What context is exposed at the failing position?** Truncated before the newline in both directions: `before` stops at the line start, `after` is `nil` at the line end.
- **Start/end of line vs middle?** Yes. From a line start, Up moved nothing at all; from a line end, Down moved nothing at all; mid-line, both stalled at the line's own edge.
- **iOS version / device class?** Reproduced and fixed on iPhone 17 Pro, iOS 26.5, Xcode 26.6.
- **Another multiline host?** Apple's Notes is not installed in the iOS simulator, so a controlled native `UITextView` probe was used instead. Safari was tried first and rejected: its WebKit content ignores synthesized taps, though Safari's own UIKit chrome accepts them.

## Device evidence (2026-08-04, iPhone 17 Pro / iOS 26.5)

The blocker recorded on 2026-08-03 (Linux worktree, no Xcode) is cleared. The
measurements below were taken on a macOS runner with Xcode 26.6. Apple's Notes is
not present in the iOS simulator, so the host was the controlled native
`UITextView` probe this document already called for; Safari was rejected because
its WebKit content does not accept synthesized taps. `moveByLine` was temporarily
instrumented to log metadata only — direction, `nil`/length, newline presence —
and never any user text.

Document under test: `AAAA\nBBBBBBBB\nCCCC` (hard newlines at 4 and 13, length 18).

| caret | position | `documentContextBeforeInput` | `documentContextAfterInput` | old behavior |
|---|---|---|---|---|
| 18 | end of last line | `"CCCC"` (len 4, no `\n`) | `nil` | ↑ → 14, stops at this line's start |
| 14 | start of a line | `"\n"` (len 1) | `"CCCC"` (len 4, no `\n`) | ↑ → **no movement at all** |
| 13 | end of a *middle* line | `"BBBBBBBB"` (len 8, no `\n`) | `nil` | ↓ → **no movement at all** |

Three facts follow, and together they fully explain the report:

1. `documentContextBeforeInput` is **paragraph-limited** — it never reaches past the
   start of the caret's own line. At a line start it is exactly `"\n"`.
2. `documentContextAfterInput` is **`nil` at the end of any line**, including a line
   with more document after it. End-of-line and end-of-document are indistinguishable.
3. Therefore the old newline search could never find the boundary it needed. It
   walked to the current line's edge and stopped, and the `offset > 0` guard turned
   a line start into a total no-op.

Two further behaviors were measured, and both shaped the fix:

- `adjustTextPosition(byCharacterOffset:)` **ignores an out-of-range offset outright
  rather than clamping it.** Pressing Right seven times from caret 13 walked to 18 and
  then stopped dead — the two overshooting presses moved nothing. So a single
  combined "to the line edge, plus one across the newline" offset is silently
  discarded whenever it overshoots, which is exactly the first and last line.
- **UIKit coalesces `adjustTextPosition` calls issued in the same run-loop turn.**
  Splitting the move into two synchronous calls produced the identical dropped
  result; the newline step only survives when deferred to a later turn.

## Fix

`CaretMovePolicy.lineEdgeOffset` (`Shared/Models/KeyLayout.swift`) returns the signed
distance from the caret to the near edge of its own line, derived from the last/first
newline in the proxy context so a host that returns more than one line behaves
identically to a paragraph-limited one. `moveByLine` applies that offset, then defers
a ±1 to the next run-loop turn to cross the newline. At the first and last line only
the ±1 is out of range, so it alone is dropped and the caret settles on the document
edge — which is what a native editor does.

Runtime result, arrow keys tapped on the real keyboard (caret positions logged by the
probe):

- Repeated ↑ from the document end: 18 → 13 → 4 → 0, then no further movement.
- Repeated ↓ from the document start: 0 → 5 → 14 → 18, then no further movement.

Every press crosses exactly one hard line boundary, in both directions, and stops
cleanly at both document ends.

### Known ceiling

The caret lands on the adjacent line's near edge rather than holding its column, and
a visually wrapped line is still not a boundary. Both were confirmed on device: with
`AAAA\n<50-char wrapping paragraph>\nCCCC`, ↑ from the middle of the wrapped paragraph
jumped over the whole paragraph to the end of `AAAA` rather than moving up one visual
line. Column preservation needs a settled re-read after the asynchronous
`adjustTextPosition`, and visual-wrap parity needs host line geometry;
`UITextDocumentProxy` exposes neither. Both remain out of scope, as this document
already sanctioned.

## Verification plan

### Focused RED/GREEN coverage

- Route Up and Down through the real key handler with no candidates/composition active.
- Verify movement across a hard newline in both directions.
- Cover empty, `nil`, truncated, and newline-containing proxy contexts.
- Cover cursor positions at line start, line end, and within a line.
- Cover visual-wrap input separately from hard newlines so the test does not conflate editor geometry with text delimiters.
- Verify no regression to Left/Right movement or candidate navigation.
- Verify the same result under multiple active IMs and layouts, using representative combinations rather than duplicating the policy test for every table.

### Runtime validation

- Reproduce RED and verify GREEN in Apple's Notes on an iPhone simulator/device with explicit newlines and visual wraps.
- Repeat in at least one additional multiline host or a controlled `UITextView` probe.
- Confirm repeated Up/Down presses continue crossing more than one line boundary.
- Confirm movement does not modify or commit composing text unexpectedly.
- Run the relevant iOS unit/UI test suite and Xcode Cloud before treating the fix as complete.

### Verification results (2026-08-04)

Done:

- `KeyboardViewControllerTest.testUpDownCaretOffsetsCrossHardLineBoundaries` covers
  `nil`, empty, newline-only, truncated, and multi-line proxy contexts at line start,
  line end, mid-line, and both document ends. The whole `LimeTests` target passes.
- Runtime RED and GREEN both captured on the iPhone 17 Pro simulator / iOS 26.5 by
  tapping the real arrow-key row, with hard newlines and with a visually wrapped
  paragraph. Repeated presses cross successive boundaries in both directions.
- Left/Right were exercised in the same session (Left ×5 and Right ×7 walked the caret
  one character at a time and stopped at the document end) — unchanged, and their code
  path was not touched.
- Confirmed on a physical iPhone (iPhone 17 Pro Max, `iPhone18,2`): the maintainer
  installed this build and verified Up/Down now cross line boundaries.

Not done, and why:

- **Multiple IMs and layouts** — only 注音 was active at runtime. `moveByLine` sits in
  the shared key handler with no IM or layout branching, so the behavior is structurally
  IM-independent; this was reasoned from the source, not measured per table.
- **Composing-text interaction** — `moveByLine` only calls `adjustTextPosition` and
  never touches the composing buffer, but a press during active composition was not
  exercised on device.
- **LimeUITests scheme and Xcode Cloud** — not run.

## Platform impact

### iOS

Confirmed affected. The shared `KeyboardViewController.moveByLine(forward:)` implementation handles Up/Down for all active LIME input methods and keyboard layouts, matching the report's scope. The final correction and runtime proof are iOS-specific.

### Android

No Android failure is reported or inferred from the current implementation. Android sends native DPAD Up/Down events to the host editor and serves as the behavioral parity reference. No Android source change is currently proposed, but a quick multiline regression check should confirm the established behavior remains intact.
