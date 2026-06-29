# feat#N01: Wider Candidate Dismiss Button — 1.5× Width (Android + iOS)

## Source request

Requested directly by Jeremy on 2026-06-28 after #124 commenter `01disney`
(https://github.com/lime-ime/limeime/issues/124#issuecomment-4826137217) reinforced that
the candidate-strip left `X` (dismiss / clear-code button) is too small/hard to press during
thumb typing and asked whether it can be made the same size as the smiley icon.

Jeremy's locked decision (2026-06-29): instead of matching the smiley/emoji button exactly,
make the dismiss button **1.5× its current width** on both platforms, keeping **height, color,
corner style, glyph, and tap semantics intact**. This is a pure tap-target width change.

## Goal

Make the candidate-strip dismiss / clear-code button (the left `X`) 1.5× wider on Android and
iOS so it is easier to hit during normal thumb typing, with no other visual or behavioral change.

## Locked scope / constraints

- **Width only, exactly 1.5×.** Nothing else changes.
- Preserve height, background color, corner radius / style, and the `X` glyph rendering.
- Preserve the existing dismiss / clear-composition tap semantics.
- Do NOT touch the keyboard delete/backspace key.
- Do NOT change the emoji/smiley, chevron/expand, options, or any other candidate-strip control.
- Android and iOS get the same intent; per-platform mechanics differ.

## Current behavior (exact, before)

### Android

The dismiss button width is a single dimension resource that cascades everywhere:

- `LimeStudio/app/src/main/res/values/dimens.xml:41` → `<dimen name="candidate_dismiss_button_width">21sp</dimen>`
  - For reference, the emoji/expand button is `candidate_expand_button_width = 42sp` (dimens.xml:40).
- Referenced by:
  - `res/layout/inputcandidate.xml:55` — dismiss button `layout_width` (height is `fill_parent` at :56).
  - `res/layout/candidatepopup.xml:50` — dismiss button `layout_width`.
  - `res/layout/candidates.xml:42` — `layout_marginStart` (reserves the dismiss column for candidates).
  - `res/layout/candidates.xml:53` — dismiss button `layout_width`.
  - `candidate/CandidateInInputViewContainer.java:342` — `dismissWidth` used in the available-width calc.
  - `candidate/CandidateExpandedView.java:341-342` (`rowStartX`) reserves the dismiss width for the
    first expanded row; it reads the live button width via `CandidateView.popupDismissButtonWidth()`.

The `X` glyph is drawn centered by `DismissGlyphDrawable` (size driven by height/glyph metrics, not by
button width), so a wider button keeps the same glyph with more tappable padding.

### iOS

The dismiss button width is `Chevron.buttonWidth(isPad) / 2` ("half chevron width") and appears in 3 places:

- `LimeKeyboard/CandidateBarView.swift:423` — standalone candidate-bar dismiss button width.
- `LimeKeyboard/KeyboardViewController.swift:1059` — expanded-panel dismiss button width.
- `LimeKeyboard/KeyboardViewController.swift:2080` — `dismissZone`, the first-row start offset that
  reserves space for the dismiss button in the expanded panel layout (`expandedRowStartX(row 0) = dismissZone + hPad`).

Base widths (`LayoutMetrics.swift:83-101`): Phone `buttonWidth = 40` → dismiss `20pt`; iPad `buttonWidth = 52` → dismiss `26pt`.

Style/layout already isolates width from everything else:
- Height: `dismissHeight` constraint (`= heightAnchor − composingStripHeight`); centered on the glyph axis.
- Color/style: `dismissButton.backgroundColor = palette.normalKey.withAlphaComponent(0.15)`, `cornerRadius = 6`,
  `xmark` SF Symbol with a fixed `SymbolConfiguration` — all independent of width.
- `scrollView.leadingAnchor` and `composingLabel.leadingAnchor` anchor to `dismissButton.trailingAnchor`
  (CandidateBarView.swift:481, :474), so widening the dismiss button shifts candidates/composing right
  automatically — no overlap, no other edit needed.

## Intended change (exact, after)

### Android — one dimen

`res/values/dimens.xml:41`:

```xml
<!-- feat#N01: 1.5x the former 21sp tap target; height/style unchanged. -->
<dimen name="candidate_dismiss_button_width">31.5sp</dimen>
```

`21 × 1.5 = 31.5sp`. The single change cascades to the button width (3 layouts), the candidate
`marginStart`, the width calc, and the expanded first-row offset — all stay consistent. Height
(`fill_parent`), glyph, and color are untouched.

### iOS — one helper, three call sites

Add a dismiss-specific selector next to `buttonWidth` in `LayoutMetrics.swift` (Chevron enum, after :100):

```swift
/// feat#N01: dismiss (X) button is 1.5x the former half-chevron tap target.
static func dismissButtonWidth(isPad: Bool) -> CGFloat {
    buttonWidth(isPad: isPad) / 2 * 1.5
}
```

Result: Phone `40/2*1.5 = 30pt`, iPad `52/2*1.5 = 39pt`. Replace the three `buttonWidth(isPad:)/2`
dismiss usages with `dismissButtonWidth(isPad:)`:

- `CandidateBarView.swift:423` → `... equalToConstant: LayoutMetrics.CandidateBar.Chevron.dismissButtonWidth(isPad: isPad))`
- `KeyboardViewController.swift:1059` → `... equalToConstant: LayoutMetrics.CandidateBar.Chevron.dismissButtonWidth(isPad: isOnPad))`
- `KeyboardViewController.swift:2080` → `let dismissZone = LayoutMetrics.CandidateBar.Chevron.dismissButtonWidth(isPad: isOnPad)`

The chevron/collapse (`buttonWidth`, full) and the emoji/options columns are NOT changed.

## TDD tests

### Android (instrumented) — `app/src/androidTest/.../candidate/CandidatePopupAnchorTest.java`

Pin the width as 1.5× the former 21sp, density-independently via `scaledDensity`:

```java
@Test
public void candidateDismissButtonWidthIs1_5xFormerWidth() {
    // feat#N01: 31.5sp == 1.5 × the former 21sp tap target. height/style unchanged.
    android.content.res.Resources r =
        InstrumentationRegistry.getInstrumentation().getTargetContext().getResources();
    float sp = r.getDisplayMetrics().scaledDensity;
    assertEquals(31.5f * sp, r.getDimension(R.dimen.candidate_dismiss_button_width), 1.0f);
}
```

Existing `popupBaseX(…,21)` / `rowStartX(…,21)` assertions pass an arbitrary width arg to pure
math helpers and are unaffected by the dimen change (leave them as-is).

### iOS — `LimeTests/KeyboardViewControllerTest.swift`

```swift
func testDismissButtonWidthIs1_5xHalfChevron() {
    // feat#N01: dismiss (X) is 1.5× the former half-chevron width; chevron unchanged.
    XCTAssertEqual(LayoutMetrics.CandidateBar.Chevron.dismissButtonWidth(isPad: false), 30, accuracy: 0.001)
    XCTAssertEqual(LayoutMetrics.CandidateBar.Chevron.dismissButtonWidth(isPad: true), 39, accuracy: 0.001)
    XCTAssertEqual(LayoutMetrics.CandidateBar.Chevron.dismissButtonWidth(isPad: false),
                   LayoutMetrics.CandidateBar.Chevron.buttonWidth(isPad: false) / 2 * 1.5, accuracy: 0.001)
}
```

## Implementation steps (TDD order)

### iOS

1. Add `testDismissButtonWidthIs1_5xHalfChevron` (above); run it → FAILS (no `dismissButtonWidth`).
2. Add `dismissButtonWidth(isPad:)` to `LayoutMetrics.swift` Chevron enum; run test → PASSES.
3. Replace the 3 call sites (CandidateBarView:423, KeyboardViewController:1059, :2080) with the helper.
4. `xcodebuild -project LimeIME-iOS/LimeIME.xcodeproj -scheme LimeIME -destination 'generic/platform=iOS Simulator' build`
5. Run the iOS test target; visually verify the `X` is wider, same height/color/corners, candidates shifted right.
6. Commit.

### Android

1. Add `candidateDismissButtonWidthIs1_5xFormerWidth` (above); run instrumented → FAILS (still 21sp).
2. Change `candidate_dismiss_button_width` 21sp → 31.5sp in `dimens.xml`; run instrumented → PASSES.
3. `cd LimeStudio && ./gradlew :app:compileDebugJavaWithJavac :app:compileDebugAndroidTestJavaWithJavac`
4. `git diff --check`.
5. Visually verify (android-visual-verify): wider `X`, same height/color/glyph, candidates start further right,
   backspace key unchanged.
6. Commit.

## Verification plan

- iOS: build + test target green; visual check on iPhone (phone metrics) and, if convenient, iPad.
- Android: `compileDebugJavaWithJavac` + instrumented test green; visual check on emulator/device.
  (Note: instrumented runs are currently blocked until the stuck `net.toload.main.hd2026` emulator package
  is cleared; compile + the dimen assertion can run once that is resolved.)

## Public communication

No public GitHub comment needed unless this is later tied to a public issue/decision.
