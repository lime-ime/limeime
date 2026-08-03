# Issue #224: iOS up/down caret buttons cannot cross line boundaries

## Status

- Issue: https://github.com/lime-ime/limeime/issues/224
- Classification: confirmed iOS bug
- State: open, source-unfixed
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

The reporter-visible failure is confirmed. The exact Notes context returned at each failing cursor position still needs device instrumentation before choosing the final fallback. Do not treat the source-level empty-context path as the only established runtime cause until that evidence is captured.

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

- Does the failure occur across an explicit Return-created newline, a visually wrapped line, or both?
- At the failing position, does Notes expose empty/`nil` proxy context, context truncated before the newline, or context containing the newline?
- Does behavior differ at the start/end of a line versus the middle of a line?
- Which iOS version and device class were used for the confirmed reproduction?
- Does the same build reproduce in another multiline host such as Mail or a minimal `UITextView` probe app?

These questions refine implementation and verification. They do not block classification of the reported iOS behavior as a bug.

## Implementation blocker (2026-08-03)

The implementation worktree is hosted on Linux and has neither `xcodebuild` nor a
Swift toolchain, iOS Simulator/device access, or an Apple Notes runtime. Therefore
it cannot capture the privacy-preserving before/after context-length evidence
required above or execute a meaningful XCTest RED/GREEN cycle. The source proves
that empty or `nil` directional context reaches the `offset > 0` guard and becomes
a no-op, but it does not prove whether Notes returns that state at the reported
hard-newline boundary. An unconditional ±1 fallback would also be invoked at the
true beginning/end of a document, because `UITextDocumentProxy` does not expose a
way to distinguish those positions from unavailable/truncated context.

Stop before adding a regression test or production fallback until a macOS runner
with Xcode and a Notes-capable simulator/device records, without logging text:

- direction and whether the context is `nil`, empty, or non-empty;
- context length and whether a hard newline is present at the failing boundary;
- whether a direct ±1 `adjustTextPosition` probe crosses that boundary; and
- the same observations at the true beginning/end of the document.

Visual-wrap parity remains out of scope for any source-only fix because
`UITextDocumentProxy` exposes neither host line geometry nor native vertical
caret movement.

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

## Platform impact

### iOS

Confirmed affected. The shared `KeyboardViewController.moveByLine(forward:)` implementation handles Up/Down for all active LIME input methods and keyboard layouts, matching the report's scope. The final correction and runtime proof are iOS-specific.

### Android

No Android failure is reported or inferred from the current implementation. Android sends native DPAD Up/Down events to the host editor and serves as the behavioral parity reference. No Android source change is currently proposed, but a quick multiline regression check should confirm the established behavior remains intact.
