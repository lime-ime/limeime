# Legacy iPhone Globe / Keyboard Key Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give home-button iPhones (SE 2/3, 8) an in-keyboard globe key that satisfies App Store guideline 4.1/4.5, by repurposing two existing buttons (bottom-row `-3` key and candidate-bar right-edge `optionsButton`) when `needsInputModeSwitchKey == true`. iPad and modern iPhones unchanged.

**Architecture:** A pure-Swift policy gate `legacyGlobeMode` (= `needsInputModeSwitchKey && !isOnPad && !currentLayout.id.contains("_ipad")`) is computed on `KeyboardViewController` and pushed into `KeyboardView` and `CandidateBarView`. The policy lives in `KeyboardGesturePolicy` (already a unit-tested enum in `KeyLayout.swift`), so the conditional matrix gets XCTest coverage. View-binding swap is **build-time**, not runtime patch-up: when the flag flips, `setLayout(currentLayout)` rebuilds the bottom row, which avoids gesture-recognizer cleanup foot-guns. The candidate bar's `optionsButton` retargets between two already-declared delegate callbacks (`candidateBarViewDidRequestDismiss` vs `candidateBarViewDidRequestOptions`).

**Tech Stack:** Swift / UIKit / iOS keyboard extension. Existing XCTest target `LimeTests` for policy unit tests. Simulator-based visual verification (iPhone SE 3rd gen, iPhone 15, iPad Air) for view-binding behavior — no UI-automation test framework in this repo.

---

## Reference design doc

[docs/IPHONE_LEGACY_KB.md](../../IPHONE_LEGACY_KB.md) — the design rationale, behavior matrix, risks, and verification checklist. This plan implements that doc; do not re-derive design choices.

## File structure

| File | Role | Modification |
| ---- | ---- | ------------ |
| [LimeIME-iOS/Shared/Models/KeyLayout.swift](../../../LimeIME-iOS/Shared/Models/KeyLayout.swift) | Pure-Swift policy enum (`KeyboardGesturePolicy`). Already houses `shouldUseLimeOptionsMenuGesture` and `shouldUseDualRowGesture`. | Extend with `legacyGlobeMode` parameter; add `shouldWireSystemPickerOnKeyboardKey(...)` and `iconForKeyboardKey(...)`. |
| [LimeIME-iOS/LimeTests/KeyboardViewControllerTest.swift](../../../LimeIME-iOS/LimeTests/KeyboardViewControllerTest.swift) | XCTest target for pure-Swift policy. | Append new tests covering the legacy-mode matrix. |
| [LimeIME-iOS/LimeKeyboard/KeyboardView.swift](../../../LimeIME-iOS/LimeKeyboard/KeyboardView.swift) | Renders keys, owns gesture recognizers, wires `inputModeViewController` for the system globe picker. | Add `legacyGlobeMode` settable property; in `makeKeyButton` consume the new policy fns; weak ref to the `-3` button for icon swap. |
| [LimeIME-iOS/LimeKeyboard/CandidateBarView.swift](../../../LimeIME-iOS/LimeKeyboard/CandidateBarView.swift) | Renders candidate strip including right-edge `optionsButton` (☰). Already has dismiss + options delegate callbacks. | Add `legacyGlobeMode` settable property; `applyLegacyOptionsBinding()` swaps icon, tap target, long-press recognizer, and visibility. |
| [LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift](../../../LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift) | Owns gate; refreshes subviews. Already has `updateGlobeKeyVisibility()` called from `viewWillLayoutSubviews`, `textWillChange`, `textDidChange`, and after `setLayout`. | Add `legacyGlobeMode` computed property; rename `updateGlobeKeyVisibility` → `updateGlobeAndDismissBindings`; push flag into both subviews. |
| [docs/IPHONE_LEGACY_KB.md](../../IPHONE_LEGACY_KB.md) | Verification checklist. | Tick items off as Phase 6 walks them on real simulators. |

No JSON layout changes. No new files outside `docs/superpowers/plans/`.

---

## Phase 0: Confirm build/test environment

### Task 0.1: Discover the test scheme

**Files:** none (read-only probe).

- [ ] **Step 0.1.1: List schemes**

```bash
xcodebuild -list -project LimeIME-iOS/LimeIME.xcodeproj
```

Expected output includes a `Schemes:` block. Note the scheme that runs the `LimeTests` target — typically `LimeIME` or `LimeTests`. Call it `<TEST_SCHEME>` in every subsequent xcodebuild invocation.

- [ ] **Step 0.1.2: List available simulators**

```bash
xcrun simctl list devices available | grep -E "iPhone SE|iPhone 15|iPad Air"
```

Pick the booted (or most recent) UDID for each device class. Record:
- `SE_UDID` — iPhone SE (3rd generation) for legacy globe mode
- `IPHONE15_UDID` — iPhone 15 (or any iPhone X+ model) for "no change" verification
- `IPAD_UDID` — iPad Air for "iPad unchanged" verification

- [ ] **Step 0.1.3: Run the existing test suite once to establish a clean baseline**

```bash
xcodebuild test \
  -project LimeIME-iOS/LimeIME.xcodeproj \
  -scheme <TEST_SCHEME> \
  -destination "platform=iOS Simulator,id=$SE_UDID" \
  -only-testing:LimeTests 2>&1 | tail -40
```

Expected: `** TEST SUCCEEDED **`. If any test fails on `origin/master`, stop and report; do not proceed until baseline is green.

- [ ] **Step 0.1.4: Commit nothing — Phase 0 is read-only**

---

## Phase 1: Policy extension (TDD)

### Task 1.1: Extend `KeyboardGesturePolicy` API surface — failing tests first

**Files:**
- Test: `LimeIME-iOS/LimeTests/KeyboardViewControllerTest.swift` (append within the existing `final class KeyboardViewControllerTest: XCTestCase`)

- [ ] **Step 1.1.1: Append the failing test block**

Open `LimeIME-iOS/LimeTests/KeyboardViewControllerTest.swift`. Locate the existing test `func testGlobeRoutesToSystemPickerWhileKeyboardKeyRoutesToLimeOptionsMenu()` near line 210. Immediately after its closing brace, insert:

```swift
    // MARK: - Legacy iPhone globe mode (spec: docs/IPHONE_LEGACY_KB.md)

    private func makeKeyboardKey() -> KeyDef {
        KeyDef(code: LimeKeyCode.done.rawValue,
               widthPercent: 14,
               icon: "keyboard.chevron.compact.down",
               isModifier: true,
               longPressCode: LimeKeyCode.keyboardOptionsMenu.rawValue)
    }

    private func makeGlobeKey() -> KeyDef {
        KeyDef(code: LimeKeyCode.globe.rawValue,
               widthPercent: 8,
               icon: "globe",
               isModifier: true,
               longPressCode: LimeKeyCode.keyboardOptionsMenu.rawValue)
    }

    func testStandardModeKeyboardKeyOwnsLimeOptionsMenu() {
        let key = makeKeyboardKey()
        XCTAssertTrue(KeyboardGesturePolicy.shouldUseLimeOptionsMenuGesture(
            keyDef: key, legacyGlobeMode: false))
    }

    func testLegacyModeKeyboardKeyReleasesLimeOptionsMenuToSystemPicker() {
        let key = makeKeyboardKey()
        XCTAssertFalse(KeyboardGesturePolicy.shouldUseLimeOptionsMenuGesture(
            keyDef: key, legacyGlobeMode: true))
    }

    func testStandardModeGlobeKeyNeverGetsLimeOptionsMenu() {
        let key = makeGlobeKey()
        XCTAssertFalse(KeyboardGesturePolicy.shouldUseLimeOptionsMenuGesture(
            keyDef: key, legacyGlobeMode: false))
    }

    func testLegacyModeGlobeKeyStillBypassesLimeOptionsMenu() {
        let key = makeGlobeKey()
        XCTAssertFalse(KeyboardGesturePolicy.shouldUseLimeOptionsMenuGesture(
            keyDef: key, legacyGlobeMode: true))
    }

    func testStandardModeKeyboardKeyDoesNotWireSystemPicker() {
        let key = makeKeyboardKey()
        XCTAssertFalse(KeyboardGesturePolicy.shouldWireSystemPickerOnKeyboardKey(
            keyDef: key, legacyGlobeMode: false, hasInputModeViewController: true))
    }

    func testLegacyModeKeyboardKeyWiresSystemPickerWhenIVCPresent() {
        let key = makeKeyboardKey()
        XCTAssertTrue(KeyboardGesturePolicy.shouldWireSystemPickerOnKeyboardKey(
            keyDef: key, legacyGlobeMode: true, hasInputModeViewController: true))
    }

    func testLegacyModeWithoutIVCDoesNotWireSystemPicker() {
        let key = makeKeyboardKey()
        XCTAssertFalse(KeyboardGesturePolicy.shouldWireSystemPickerOnKeyboardKey(
            keyDef: key, legacyGlobeMode: true, hasInputModeViewController: false))
    }

    func testLegacyModeOnlyAppliesToKeyboardKey_NotShiftOrEnter() {
        let shift = KeyDef(code: LimeKeyCode.shift.rawValue, widthPercent: 14,
                           icon: "shift", isModifier: true)
        let enter = KeyDef(code: LimeKeyCode.enter.rawValue, widthPercent: 14,
                           icon: "return", isModifier: true)
        XCTAssertFalse(KeyboardGesturePolicy.shouldWireSystemPickerOnKeyboardKey(
            keyDef: shift, legacyGlobeMode: true, hasInputModeViewController: true))
        XCTAssertFalse(KeyboardGesturePolicy.shouldWireSystemPickerOnKeyboardKey(
            keyDef: enter, legacyGlobeMode: true, hasInputModeViewController: true))
    }

    func testIconForKeyboardKey_StandardModeReturnsNilSoJSONIconWins() {
        let key = makeKeyboardKey()
        XCTAssertNil(KeyboardGesturePolicy.iconForKeyboardKey(
            keyDef: key, legacyGlobeMode: false))
    }

    func testIconForKeyboardKey_LegacyModeReturnsGlobe() {
        let key = makeKeyboardKey()
        XCTAssertEqual(
            KeyboardGesturePolicy.iconForKeyboardKey(keyDef: key, legacyGlobeMode: true),
            "globe")
    }

    func testIconForKeyboardKey_LegacyModeIgnoresNonKeyboardKey() {
        let shift = KeyDef(code: LimeKeyCode.shift.rawValue, widthPercent: 14,
                           icon: "shift", isModifier: true)
        XCTAssertNil(KeyboardGesturePolicy.iconForKeyboardKey(
            keyDef: shift, legacyGlobeMode: true))
    }
```

- [ ] **Step 1.1.2: Confirm tests fail to compile (no implementation yet)**

```bash
xcodebuild test \
  -project LimeIME-iOS/LimeIME.xcodeproj \
  -scheme <TEST_SCHEME> \
  -destination "platform=iOS Simulator,id=$SE_UDID" \
  -only-testing:LimeTests 2>&1 | tail -30
```

Expected: compile error. Errors should reference `shouldUseLimeOptionsMenuGesture(keyDef:legacyGlobeMode:)` (missing argument label), `shouldWireSystemPickerOnKeyboardKey` (not found), and `iconForKeyboardKey` (not found). The existing test at line 210 (single-arg form) will also need updating in Step 1.2.2.

### Task 1.2: Implement the policy extensions

**Files:**
- Modify: `LimeIME-iOS/Shared/Models/KeyLayout.swift` (around line 357 — the `KeyboardGesturePolicy` enum)
- Modify: `LimeIME-iOS/LimeTests/KeyboardViewControllerTest.swift` (single-arg call at existing line 222-223)

- [ ] **Step 1.2.1: Replace the policy enum body**

In `LimeIME-iOS/Shared/Models/KeyLayout.swift`, find:

```swift
    static func shouldUseLimeOptionsMenuGesture(keyDef: KeyDef) -> Bool {
        keyDef.code == LimeKeyCode.done.rawValue
            || (keyDef.longPressCode == LimeKeyCode.keyboardOptionsMenu.rawValue
                && keyDef.code != LimeKeyCode.globe.rawValue)
    }
```

Replace with:

```swift
    /// Whether `keyDef` should receive the LIME options-menu long-press recognizer.
    /// In legacy iPhone globe mode the `-3` keyboard key releases its long-press
    /// to the iOS system input-mode picker, so it must NOT get the LIME gesture.
    static func shouldUseLimeOptionsMenuGesture(keyDef: KeyDef,
                                                 legacyGlobeMode: Bool = false) -> Bool {
        if legacyGlobeMode && keyDef.code == LimeKeyCode.done.rawValue {
            return false
        }
        return keyDef.code == LimeKeyCode.done.rawValue
            || (keyDef.longPressCode == LimeKeyCode.keyboardOptionsMenu.rawValue
                && keyDef.code != LimeKeyCode.globe.rawValue)
    }

    /// Whether `keyDef`'s button should be wired to `UIInputViewController
    /// .handleInputModeList(from:with:)` (system input-mode picker).
    /// True only for the `-3` keyboard key in legacy globe mode, and only when
    /// the host extension provides an `inputModeViewController` reference.
    static func shouldWireSystemPickerOnKeyboardKey(keyDef: KeyDef,
                                                     legacyGlobeMode: Bool,
                                                     hasInputModeViewController: Bool) -> Bool {
        legacyGlobeMode
            && keyDef.code == LimeKeyCode.done.rawValue
            && hasInputModeViewController
    }

    /// SF Symbol override for `keyDef`'s rendered icon. Returns `nil` when the
    /// caller should use `keyDef.icon` from the JSON / hardcoded layout.
    /// In legacy iPhone globe mode the `-3` keyboard key paints as `"globe"`.
    static func iconForKeyboardKey(keyDef: KeyDef,
                                    legacyGlobeMode: Bool) -> String? {
        if legacyGlobeMode && keyDef.code == LimeKeyCode.done.rawValue {
            return "globe"
        }
        return nil
    }
```

- [ ] **Step 1.2.2: Update the pre-existing test to use the new signature**

In `LimeIME-iOS/LimeTests/KeyboardViewControllerTest.swift` around line 222-223, the existing call:

```swift
        XCTAssertTrue(KeyboardGesturePolicy.shouldUseLimeOptionsMenuGesture(keyDef: keyboardKey))
        XCTAssertFalse(KeyboardGesturePolicy.shouldUseLimeOptionsMenuGesture(keyDef: globeKey))
```

…still compiles because the new `legacyGlobeMode` parameter has `= false` default. No change needed here; verify both old and new tests pass.

- [ ] **Step 1.2.3: Run the test suite — expect green**

```bash
xcodebuild test \
  -project LimeIME-iOS/LimeIME.xcodeproj \
  -scheme <TEST_SCHEME> \
  -destination "platform=iOS Simulator,id=$SE_UDID" \
  -only-testing:LimeTests 2>&1 | tail -20
```

Expected: `** TEST SUCCEEDED **`. Eleven new test methods pass alongside the existing `testGlobeRoutesToSystemPickerWhileKeyboardKeyRoutesToLimeOptionsMenu`.

- [ ] **Step 1.2.4: Commit**

```bash
git add LimeIME-iOS/Shared/Models/KeyLayout.swift \
        LimeIME-iOS/LimeTests/KeyboardViewControllerTest.swift
git commit -m "Add legacy-globe-mode parameters to KeyboardGesturePolicy"
```

---

## Phase 2: Gate plumbing (no behavior change)

### Task 2.1: Add `legacyGlobeMode` properties to subviews

**Files:**
- Modify: `LimeIME-iOS/LimeKeyboard/KeyboardView.swift` (near the top of `class KeyboardView`, beside other public settable state)
- Modify: `LimeIME-iOS/LimeKeyboard/CandidateBarView.swift` (near top of `final class CandidateBarView`)

- [ ] **Step 2.1.1: Add property to `KeyboardView`**

Open `LimeIME-iOS/LimeKeyboard/KeyboardView.swift`. Find the section near line 163-168:

```swift
    private weak var globeButton: UIButton?

    private weak var inputModeViewController: UIInputViewController? {
        didSet { configureGlobeButtonForSystemPicker() }
    }
```

Immediately after `private weak var globeButton: UIButton?`, add:

```swift
    /// Weak ref to the bottom-row `-3` (LimeKeyCode.done) button — needed in legacy
    /// iPhone globe mode so we can swap its SF Symbol image when the flag flips.
    private weak var keyboardDoneButton: UIButton?

    /// Set by KeyboardViewController. When true, the `-3` key paints as a globe and
    /// hands tap + long-press to iOS' input-mode picker (spec: docs/IPHONE_LEGACY_KB.md).
    /// Changing this triggers a full layout rebuild because the bottom-row gesture
    /// wiring is determined at button-construction time.
    var legacyGlobeMode: Bool = false {
        didSet {
            guard oldValue != legacyGlobeMode else { return }
            setLayout(layout)
        }
    }
```

- [ ] **Step 2.1.2: Add property to `CandidateBarView`**

Open `LimeIME-iOS/LimeKeyboard/CandidateBarView.swift`. Find the property block around line 14-25 (just after `weak var delegate`). Add after `private let optionsButton = UIButton(type: .system)`:

```swift

    /// Set by KeyboardViewController. When true, `optionsButton` paints as
    /// `keyboard.chevron.compact.down`, taps dismiss the keyboard, long-press
    /// shows the LIME options menu, and it stays visible regardless of bar state
    /// (spec: docs/IPHONE_LEGACY_KB.md).
    var legacyGlobeMode: Bool = false {
        didSet {
            guard oldValue != legacyGlobeMode else { return }
            applyLegacyOptionsBinding()
        }
    }

    /// Long-press recognizer attached to `optionsButton` in legacy mode so a
    /// long press routes to the LIME options menu while a short tap dismisses.
    /// Kept as a weak property so `applyLegacyOptionsBinding()` can remove it
    /// when leaving legacy mode.
    private weak var legacyOptionsLongPress: UILongPressGestureRecognizer?
```

- [ ] **Step 2.1.3: Add a no-op stub for `applyLegacyOptionsBinding()`**

Still in `CandidateBarView.swift`, find the `optionsTapped` handler at line ~833. Just above it, add:

```swift
    /// No-op stub — full body lands in Phase 4. Declared in Phase 2 so the
    /// `legacyGlobeMode` didSet compiles.
    private func applyLegacyOptionsBinding() {
        // Intentionally empty; see Phase 4 for the real implementation.
    }
```

- [ ] **Step 2.1.4: Build only — no behavior change yet**

```bash
xcodebuild build \
  -project LimeIME-iOS/LimeIME.xcodeproj \
  -scheme <TEST_SCHEME> \
  -destination "platform=iOS Simulator,id=$SE_UDID" 2>&1 | tail -10
```

Expected: `** BUILD SUCCEEDED **`. Run the test suite too:

```bash
xcodebuild test -project LimeIME-iOS/LimeIME.xcodeproj -scheme <TEST_SCHEME> \
  -destination "platform=iOS Simulator,id=$SE_UDID" -only-testing:LimeTests 2>&1 | tail -10
```

Expected: still `** TEST SUCCEEDED **` (no regressions).

- [ ] **Step 2.1.5: Commit**

```bash
git add LimeIME-iOS/LimeKeyboard/KeyboardView.swift \
        LimeIME-iOS/LimeKeyboard/CandidateBarView.swift
git commit -m "Add dormant legacyGlobeMode plumbing on KeyboardView and CandidateBarView"
```

### Task 2.2: Add `legacyGlobeMode` gate to controller and route into subviews

**Files:**
- Modify: `LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift`

- [ ] **Step 2.2.1: Add the gate computed property**

Open `LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift`. Find the existing `private var isOnPad: Bool { ... }` near line 173. Just below it, add:

```swift
    /// True when iOS reports it cannot supply a globe key for us (legacy
    /// home-button iPhones: SE 2/3, 8). Drives the in-keyboard globe affordance
    /// (spec: docs/IPHONE_LEGACY_KB.md). Excludes iPad and any `_ipad` layout
    /// so the existing dual-key iPad story (`-200` globe + `-3` dismiss) is
    /// untouched.
    private var legacyGlobeMode: Bool {
        needsInputModeSwitchKey
            && !isOnPad
            && !currentLayout.id.contains("_ipad")
    }
```

- [ ] **Step 2.2.2: Rename `updateGlobeKeyVisibility()` → `updateGlobeAndDismissBindings()` and push the flag**

Find `updateGlobeKeyVisibility()` near line 2397. Rename it and append the two flag pushes:

```swift
    /// Single refresh point for all globe-related view state (spec §10 and
    /// docs/IPHONE_LEGACY_KB.md). Must be called whenever `needsInputModeSwitchKey`
    /// could have changed (textWillChange/textDidChange) or after any layout
    /// rebuild (setLayout, shift toggle, symbol mode, IM switch).
    private func updateGlobeAndDismissBindings() {
        let isPad = isOnPad
        // On iPad with an _ipad layout, globe is always visible (matches Apple's stock keyboard).
        let globeVisible = (isPad && currentLayout.id.contains("_ipad")) || needsInputModeSwitchKey
        keyboardView?.setGlobeKeyVisible(globeVisible)

        let legacy = legacyGlobeMode
        keyboardView?.legacyGlobeMode = legacy
        candidateBar.legacyGlobeMode = legacy
    }
```

- [ ] **Step 2.2.3: Update all call sites**

In the same file, replace every call to `updateGlobeKeyVisibility()` with `updateGlobeAndDismissBindings()`. Run this exact grep to find them all:

```bash
grep -n "updateGlobeKeyVisibility" \
  LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift
```

Expected hits before the rename: lines ~287, 305, 325 plus the definition at ~2397. After the rename, the grep must return **zero** lines:

```bash
grep -n "updateGlobeKeyVisibility" \
  LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift
# (no output)
```

Then verify the new name is reachable from each former call site:

```bash
grep -n "updateGlobeAndDismissBindings" \
  LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift
```

Expected: 4 lines (3 call sites + 1 definition).

- [ ] **Step 2.2.4: Build + test**

```bash
xcodebuild test -project LimeIME-iOS/LimeIME.xcodeproj -scheme <TEST_SCHEME> \
  -destination "platform=iOS Simulator,id=$SE_UDID" -only-testing:LimeTests 2>&1 | tail -10
```

Expected: `** TEST SUCCEEDED **`. Behavior on device should still be identical because `KeyboardView.legacyGlobeMode`'s didSet calls `setLayout(layout)` (a no-op when the value flips from `false → false`), and `CandidateBarView.applyLegacyOptionsBinding()` is the empty stub.

- [ ] **Step 2.2.5: Commit**

```bash
git add LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift
git commit -m "Wire legacyGlobeMode gate through controller refresh path"
```

---

## Phase 3: KeyboardView consumes the policy — bottom-row `-3` rebinding

### Task 3.1: Branch `makeKeyButton` on the policy

**Files:**
- Modify: `LimeIME-iOS/LimeKeyboard/KeyboardView.swift` (around lines 684-734 — the `makeKeyButton` body that wires gestures and targets)

- [ ] **Step 3.1.1: Update `isKeyboardOptionsKey` to pass `legacyGlobeMode`**

Find line 684:

```swift
        let isKeyboardOptionsKey = Self.shouldUseLimeOptionsMenuGesture(keyDef: keyDef)
```

Replace with:

```swift
        let isKeyboardOptionsKey = Self.shouldUseLimeOptionsMenuGesture(
            keyDef: keyDef, legacyGlobeMode: legacyGlobeMode)
```

Also update the static convenience wrapper at lines 152-153 to keep them aligned:

```swift
    static func shouldUseLimeOptionsMenuGesture(keyDef: KeyDef,
                                                 legacyGlobeMode: Bool = false) -> Bool {
        KeyboardGesturePolicy.shouldUseLimeOptionsMenuGesture(
            keyDef: keyDef, legacyGlobeMode: legacyGlobeMode)
    }
```

- [ ] **Step 3.1.2: Track the `-3` button + wire system picker in legacy mode**

Still in `makeKeyButton`. Find the existing globe block at lines 705-711:

```swift
        if keyDef.code == LimeKeyCode.globe.rawValue {
            globeButton = btn
            if isSystemGlobe, let ivc = inputModeViewController {
                btn.addTarget(ivc, action: #selector(UIInputViewController.handleInputModeList(from:with:)),
                              for: .allTouchEvents)
            }
        }
```

Immediately below it (before the `popupKeyboard` block at line 714), insert the `-3` block:

```swift
        // Keyboard key (code -3): in legacy iPhone globe mode it takes over the
        // role of the missing system-bar globe (spec: docs/IPHONE_LEGACY_KB.md).
        // We always track it so the icon can be repainted; we wire the system
        // picker only when the policy says so.
        if keyDef.code == LimeKeyCode.done.rawValue {
            keyboardDoneButton = btn
            let wireSystemPicker = KeyboardGesturePolicy.shouldWireSystemPickerOnKeyboardKey(
                keyDef: keyDef,
                legacyGlobeMode: legacyGlobeMode,
                hasInputModeViewController: inputModeViewController != nil)
            if wireSystemPicker, let ivc = inputModeViewController {
                btn.addTarget(ivc, action: #selector(UIInputViewController.handleInputModeList(from:with:)),
                              for: .allTouchEvents)
            }
        }
```

- [ ] **Step 3.1.3: Suppress `keyboardKeyTapped` for the legacy `-3` button**

Find the deferred-tap block at lines 729-734:

```swift
        if keyDef.code == LimeKeyCode.done.rawValue
            || isKeyboardOptionsKey
            || (keyDef.code == LimeKeyCode.globe.rawValue && !isSystemGlobe)
            || !keyDef.popupKeyboard.isEmpty || isDualRowIPadKey {
            btn.addTarget(self, action: #selector(keyboardKeyTapped(_:)), for: .touchUpInside)
        }
```

Replace with:

```swift
        // Skip our own touchUpInside target on the `-3` key when legacy globe mode
        // has handed it to iOS' system picker — UIKit must own all events there.
        let legacyOwnedByIVC = keyDef.code == LimeKeyCode.done.rawValue
            && legacyGlobeMode
            && inputModeViewController != nil
        if !legacyOwnedByIVC && (
            keyDef.code == LimeKeyCode.done.rawValue
                || isKeyboardOptionsKey
                || (keyDef.code == LimeKeyCode.globe.rawValue && !isSystemGlobe)
                || !keyDef.popupKeyboard.isEmpty || isDualRowIPadKey) {
            btn.addTarget(self, action: #selector(keyboardKeyTapped(_:)), for: .touchUpInside)
        }
```

- [ ] **Step 3.1.4: Apply the icon override after the existing icon is set**

Find the call to `applyButtonStyle(btn, keyDef: keyDef, rowHeight: rowHeight, totalPercent: totalPercent)` at line 720. Immediately after that line, insert:

```swift
        if let overrideIcon = KeyboardGesturePolicy.iconForKeyboardKey(
            keyDef: keyDef, legacyGlobeMode: legacyGlobeMode) {
            let cfg = btn.imageView?.preferredSymbolConfiguration
                ?? UIImage.SymbolConfiguration(pointSize: LayoutMetrics.Key.shiftIconSize,
                                                weight: .regular)
            if let image = UIImage(systemName: overrideIcon, withConfiguration: cfg) {
                btn.setImage(image, for: .normal)
            }
        }
```

(Reads `applyButtonStyle`'s already-applied symbol configuration so the swapped
`"globe"` icon renders at the same point size as the original keyboard-down
icon. If `applyButtonStyle` does not seed a `preferredSymbolConfiguration`,
the fallback uses `LayoutMetrics.Key.shiftIconSize` — visually similar.)

- [ ] **Step 3.1.5: Clear `keyboardDoneButton` in every globe-clear path**

Find every `globeButton = nil` line — the search already exposed them:

```bash
grep -n "globeButton = nil" LimeIME-iOS/LimeKeyboard/KeyboardView.swift
```

Expected: 7 hits (lines 281, 294, 306, 319, 332, 404, 425 — line numbers will have shifted after Phase 2 edits). For each hit, append `keyboardDoneButton = nil` on the next line:

```swift
            globeButton = nil
            keyboardDoneButton = nil
```

After the edits, this grep must return matching counts:

```bash
grep -c "globeButton = nil" LimeIME-iOS/LimeKeyboard/KeyboardView.swift
grep -c "keyboardDoneButton = nil" LimeIME-iOS/LimeKeyboard/KeyboardView.swift
```

Expected: both report the same number (≥7).

- [ ] **Step 3.1.6: Build + run unit tests**

```bash
xcodebuild test -project LimeIME-iOS/LimeIME.xcodeproj -scheme <TEST_SCHEME> \
  -destination "platform=iOS Simulator,id=$SE_UDID" -only-testing:LimeTests 2>&1 | tail -10
```

Expected: `** TEST SUCCEEDED **`.

- [ ] **Step 3.1.7: Visual verify on iPhone SE simulator**

Use the `ios-visual-verify` skill (or the manual equivalent):

1. Boot `$SE_UDID`.
2. Build & install LimeIME, enable the keyboard, grant Full Access (steps documented in the `ios-visual-verify` skill).
3. In Notes.app, focus a text field; cycle through Chinese IM → emoji panel → English.
4. Take a screenshot. Verify the bottom-left `-3` key renders as `globe` (Apple's standard globe SF Symbol), not the keyboard-chevron icon.
5. Tap it. Expected: iOS switches to the next input mode (e.g., to native English keyboard).
6. Long-press it. Expected: iOS' system input-mode picker appears (overlay listing all enabled keyboards). The LIME options menu must NOT appear.

If steps 5 or 6 fail, common causes:
- `keyboardKeyTapped` still firing → check Step 3.1.3.
- LIME long-press still attached → check Step 3.1.1 (the `isKeyboardOptionsKey` flag).
- System picker silent → confirm `inputModeViewController` is non-nil at button-build time (controller assigns it at line 774).

- [ ] **Step 3.1.8: Visual verify on iPhone 15 simulator — no regression**

Re-run on `$IPHONE15_UDID`. The `-3` key must remain `keyboard.chevron.compact.down`; tap dismisses; long-press shows the LIME options menu. This proves the gate excludes modern iPhones.

- [ ] **Step 3.1.9: Visual verify on iPad simulator — no regression**

Re-run on `$IPAD_UDID` with any `_ipad` layout (load Dayi or English). The `-200` globe and `-3` keyboard-down must both render with their existing icons and behaviors. This proves `!currentLayout.id.contains("_ipad")` is honored.

- [ ] **Step 3.1.10: Commit**

```bash
git add LimeIME-iOS/LimeKeyboard/KeyboardView.swift
git commit -m "Rebind bottom-row -3 key as system globe in legacy iPhone mode"
```

---

## Phase 4: CandidateBarView `optionsButton` rebinding

### Task 4.1: Implement `applyLegacyOptionsBinding()`

**Files:**
- Modify: `LimeIME-iOS/LimeKeyboard/CandidateBarView.swift`

- [ ] **Step 4.1.1: Replace the empty stub with the real binding swap**

Open `LimeIME-iOS/LimeKeyboard/CandidateBarView.swift`. Find the stub added in Phase 2:

```swift
    /// No-op stub — full body lands in Phase 4. Declared in Phase 2 so the
    /// `legacyGlobeMode` didSet compiles.
    private func applyLegacyOptionsBinding() {
        // Intentionally empty; see Phase 4 for the real implementation.
    }
```

Replace with:

```swift
    /// Swap the right-edge `optionsButton`'s role based on `legacyGlobeMode`.
    /// Legacy mode: keyboard-down chevron, tap dismisses, long-press → LIME menu,
    /// always visible. Standard mode: hamburger ☰, tap → LIME menu, visibility
    /// driven by composing state (existing behavior).
    private func applyLegacyOptionsBinding() {
        // Tap target swap. Remove both possible targets defensively so repeated
        // flips never accumulate handlers.
        optionsButton.removeTarget(self, action: #selector(optionsTapped),
                                    for: .touchUpInside)
        optionsButton.removeTarget(self, action: #selector(legacyDismissTapped),
                                    for: .touchUpInside)

        // Long-press recognizer: remove any prior instance before re-adding.
        if let lp = legacyOptionsLongPress {
            optionsButton.removeGestureRecognizer(lp)
            legacyOptionsLongPress = nil
        }

        let iconName: String
        let iconScale: CGFloat
        if legacyGlobeMode {
            iconName = "keyboard.chevron.compact.down"
            iconScale = 1.10
            optionsButton.addTarget(self, action: #selector(legacyDismissTapped),
                                     for: .touchUpInside)
            let lp = UILongPressGestureRecognizer(target: self,
                                                   action: #selector(legacyOptionsLongPressed(_:)))
            lp.minimumPressDuration = LayoutMetrics.Gesture.specialKeyHoldDuration
            optionsButton.addGestureRecognizer(lp)
            legacyOptionsLongPress = lp
        } else {
            iconName = "line.3.horizontal"
            iconScale = 1.10
            optionsButton.addTarget(self, action: #selector(optionsTapped),
                                     for: .touchUpInside)
        }

        let cfg = UIImage.SymbolConfiguration(
            pointSize: LayoutMetrics.CandidateBar.Chevron.iconSize(isPad: isPad) * iconScale,
            weight: .regular)
        if let image = UIImage(systemName: iconName, withConfiguration: cfg) {
            optionsButton.setImage(image, for: .normal)
            optionsButton.setTitle(nil, for: .normal)
        } else {
            // SF Symbol fallback — same string used in the existing constructor.
            optionsButton.setImage(nil, for: .normal)
            optionsButton.setTitle(legacyGlobeMode ? "⌄" : "☰", for: .normal)
        }

        // Force visibility in legacy mode so the dismiss/menu surface is
        // always reachable, even when no candidates are composing. The
        // standard-mode visibility rule (hidden when not composing) is owned
        // by other call sites and only takes effect when this function does
        // NOT override.
        if legacyGlobeMode {
            optionsButton.isHidden = false
        }
    }

    @objc private func legacyDismissTapped() {
        delegate?.candidateBarViewDidRequestDismiss(self)
    }

    @objc private func legacyOptionsLongPressed(_ gr: UILongPressGestureRecognizer) {
        guard gr.state == .began else { return }
        delegate?.candidateBarViewDidRequestOptions(self)
    }
```

- [ ] **Step 4.1.2: Force visibility in legacy mode**

In `CandidateBarView.swift`, find every assignment of `optionsButton.isHidden = ...` (use grep):

```bash
grep -n "optionsButton.isHidden" LimeIME-iOS/LimeKeyboard/CandidateBarView.swift
```

For each assignment that hides the button (`= true`), wrap with a guard:

```swift
        optionsButton.isHidden = legacyGlobeMode ? false : true
```

For the `= false` paths (when the bar is empty / composing), leave them alone — already visible.

If the hide path is conditional like `optionsButton.isHidden = hasCandidates`, change to:

```swift
        optionsButton.isHidden = legacyGlobeMode ? false : hasCandidates
```

(Use the existing variable name, not `hasCandidates` literally — adapt to whatever the surrounding code uses.)

- [ ] **Step 4.1.3: Initialize binding on first paint**

In `CandidateBarView.swift`, find the end of the existing initializer / `setUpSubviews` (whichever currently configures `optionsButton`). Append:

```swift
        applyLegacyOptionsBinding()
```

This makes the first paint correct without waiting for a `legacyGlobeMode` flip.

- [ ] **Step 4.1.4: Build + test**

```bash
xcodebuild test -project LimeIME-iOS/LimeIME.xcodeproj -scheme <TEST_SCHEME> \
  -destination "platform=iOS Simulator,id=$SE_UDID" -only-testing:LimeTests 2>&1 | tail -10
```

Expected: `** TEST SUCCEEDED **`.

- [ ] **Step 4.1.5: Visual verify on iPhone SE**

Boot `$SE_UDID`, launch Notes.app, focus a text field with composing active (start typing in Bopomofo / Dayi so candidates appear). Verify:
- Right edge of candidate bar shows `keyboard.chevron.compact.down`, not ☰.
- Short tap dismisses the keyboard.
- Reopen, long-press → LIME options menu appears.
- Empty candidate bar (no composing) → button still visible (legacy mode forces it).

- [ ] **Step 4.1.6: Visual verify on iPhone 15 — no regression**

Boot `$IPHONE15_UDID`, repeat. Right edge must be ☰, tap → LIME options menu (existing behavior), and visibility reverts to its old "shown when no composing" rule.

- [ ] **Step 4.1.7: Visual verify on iPad — no regression**

Boot `$IPAD_UDID`, confirm ☰ and old behavior.

- [ ] **Step 4.1.8: Commit**

```bash
git add LimeIME-iOS/LimeKeyboard/CandidateBarView.swift
git commit -m "Rebind candidate-bar optionsButton as dismiss+menu in legacy mode"
```

---

## Phase 5: Dynamic flip handling

### Task 5.1: Confirm refresh paths cover all flip triggers

**Files:**
- Read-only audit, possibly tiny edits in: `LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift`

- [ ] **Step 5.1.1: Confirm `textWillChange` / `textDidChange` call the refresh**

Search for these overrides:

```bash
grep -nA 3 "override func textWillChange\|override func textDidChange" \
  LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift
```

Each body must call `updateGlobeAndDismissBindings()`. If a body sets `hasFullAccess` flags (existing behavior at lines ~212, ~236) and exits without calling the refresh, append the call before the closing brace.

- [ ] **Step 5.1.2: Confirm `viewWillLayoutSubviews` calls it**

```bash
grep -nA 6 "override func viewWillLayoutSubviews" \
  LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift
```

Must call `updateGlobeAndDismissBindings()` (was already calling `updateGlobeKeyVisibility()` pre-rename — verify the rename took effect).

- [ ] **Step 5.1.3: Confirm `setLayout` / mode-switch paths call it**

```bash
grep -nB 1 -A 4 "currentLayout =\|setLayout(" \
  LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift | head -80
```

For every site that mutates `currentLayout` or calls `keyboardView?.setLayout(...)`, the refresh must run afterward. Most are already covered via `viewWillLayoutSubviews`; any direct mutation that occurs outside the layout cycle needs an explicit call.

- [ ] **Step 5.1.4: Live-flip simulator test on iPhone SE**

Boot `$SE_UDID`, launch Notes, type into a normal field — verify legacy globe is active. Now attach a hardware keyboard in the simulator menu (`I/O → Keyboard → Connect Hardware Keyboard`, ⇧⌘K). iOS flips `needsInputModeSwitchKey` to false; the `-3` key must revert to `keyboard.chevron.compact.down` within a layout cycle (a tap on any key triggers it). Detach and confirm it reverts.

If the flip doesn't propagate, the missing call site is the culprit — add an explicit `updateGlobeAndDismissBindings()` in the relevant override.

- [ ] **Step 5.1.5: Commit (only if any explicit calls were added)**

```bash
git add LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift
git commit -m "Ensure all needsInputModeSwitchKey flip points refresh bindings"
```

If no edits were needed, skip the commit and move on.

---

## Phase 6: Walk the verification checklist

### Task 6.1: Tick off docs/IPHONE_LEGACY_KB.md

**Files:**
- Modify: `docs/IPHONE_LEGACY_KB.md` (the "Verification checklist" section at the bottom)

- [ ] **Step 6.1.1: iPhone SE bottom-row globe behavior**

On `$SE_UDID`, in Notes.app:
- `-3` renders as globe → verify and mark `[x]` first checkbox.
- Short tap cycles IMs → verify and mark.
- Long-press shows iOS picker → verify and mark.
- LIME options menu NOT shown from `-3` → verify and mark.

- [ ] **Step 6.1.2: iPhone SE candidate-bar behavior**

On `$SE_UDID`:
- Right edge shows `keyboard.chevron.compact.down` → mark.
- Short tap dismisses keyboard → mark.
- Long press shows LIME options menu → mark.

- [ ] **Step 6.1.3: iPhone 15 no-change**

On `$IPHONE15_UDID`:
- `-3` unchanged → mark.
- Candidate-bar ☰ tap fires LIME menu → mark.

- [ ] **Step 6.1.4: iPad no-change**

On `$IPAD_UDID` with a `_ipad` layout:
- Globe and keyboard-down visible side by side, both behaviors unchanged → mark.

- [ ] **Step 6.1.5: iPhone SE rebuild survival**

On `$SE_UDID`: rotate landscape ↔ portrait, toggle shift, enter and exit symbol mode, switch IM (Dayi ↔ Bopomofo ↔ English), open and close the emoji panel. After each, globe must still appear on `-3`. Mark.

- [ ] **Step 6.1.6: iPhone SE hardware-keyboard flip**

On `$SE_UDID`: ⇧⌘K to attach hardware keyboard → both bindings revert live; detach → restore legacy mode. Mark.

- [ ] **Step 6.1.7: Commit verification update**

```bash
git add docs/IPHONE_LEGACY_KB.md
git commit -m "Mark verification checklist items as passed on SE / 15 / iPad"
```

- [ ] **Step 6.1.8: Final summary**

Branch is ready for code review. Open a PR with title `feat: legacy iPhone globe key (SE / 8)` and body referencing both `docs/IPHONE_LEGACY_KB.md` and this plan.

---

## Risks the executor should watch for

These come from `docs/IPHONE_LEGACY_KB.md` § Risks/pitfalls — re-read that section before starting:

- **Recycling regressions** — every `setLayout` rebuild path must funnel through `updateGlobeAndDismissBindings()`. Phase 5 audits this.
- **Long-press conflict on `-3`** — Phase 3 skips the LIME long-press recognizer entirely (policy returns false). Do not "remove after attach" — that path is fragile.
- **First-tap latency on system picker** — Phase 3.1.2 wires `handleInputModeList` at button-build time, not on first `viewWillLayoutSubviews`. Make sure the controller's `keyboardView.inputModeViewController = self` (line ~774) runs before the first `buildKeys`.
- **Candidate-bar width on iPhone SE** — Phase 4.1.2 forces `optionsButton` visible. Visual-verify on SE (4.7") that this does not chop a candidate slot; if it does, narrow `optionsColumnWidth` or revert to mirroring `moreButton`'s visibility (note in commit message).
- **App Store reviewer test rig** — verify on a *real* iPhone SE if you have one before declaring done.

## Out of scope for this plan

- Modernizing the existing iPad globe block (lines 705-711 in pre-change KeyboardView). Touch it only if Phase 3 forces a refactor; otherwise leave alone.
- JSON layout edits. Zero JSONs change.
- Android side. This is iOS-only.
- The unrelated working-tree changes (`DBServer.swift`, `IntegrationTestBackupRestore.java`, etc.) on the main repo's master branch.
