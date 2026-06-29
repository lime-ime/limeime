# feat#124 — English `123` Key: `…` Hint + Long-Press → phone_simple (Android & iOS) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** On the English keyboard layout only, the symbol key keeps its **"123" face** and shows a small **"…" long-press hint** at the bottom (reusing the existing minikeyboard-hint render); short-press keeps switching to the first symbol keyboard (`symbols1`), and long-press switches to the simple numeric keypad (`phone_simple`). Android and iOS.

**Status (2026-06-28):**
- **Android — implemented and VERIFIED.** PR#135 (`feat/124-android-123-longpress`, OPEN) added the feature but with two defects (label overwritten; broken return path). Both are fixed on the branch (uncommitted) and verified on a `6.1.27` **release/GitHub** build on the emulator. See Phase A.
- **iOS — to implement.** Phase B mirrors the **corrected** Android approach.

**Architecture:** Reuse the existing `-2` (symbol-mode) key. The English `-2` key keeps its `123` label and gains a popup marker so the platform's existing popup hint ("…"/"...") draws at the bottom. Short-press keeps the unchanged `-2` behavior (→ `symbols1`). Long-press fires a dedicated code (`-106`) that loads `phone_simple` as a direct keyboard swap (no persistent mode change). Return to English uses `phone_simple`'s ABC key (`-9`), made idempotent on Android (iOS was already idempotent).

**Tech Stack:** Android (Java, `LimeStudio/app`, instrumented `androidTest` / AndroidJUnit4); iOS (Swift, `LimeIME-iOS`, XCTest target `LimeTests`, JSON keyboard layouts).

## Global Constraints

- **Cross-platform long-press code = `-106`.** Android `LIMEKeyboardView.KEYCODE_PHONE_SIMPLE_LONGPRESS = -106`; iOS `LimeKeyCode.switchToPhoneSimple = -106`.
- **Affordance = keep the "123" face + a bottom-corner hint, do NOT overwrite the label.** Reuse the existing popup/minikeyboard hint render (the dots already shown on keys like `.` and accented letters). Android draws the `mPopupHint` drawable when `popupResId != 0`; iOS draws its "…" indicator (`LayoutMetrics.Key.popupIndicator…`). The visible glyph is each platform's existing hint, NOT a relabel to `"..."`.
- Behavior change is **English layout only**. Symbol, Chinese IM, `phone_simple`, and emoji keyboards must be unaffected. Detection: a `-2` key that carries a popup marker only exists on English layouts.
- **Short-press of the symbol key stays unchanged** → `symbols1`. The key code remains `-2`; only the popup marker + a new long-press are added.
- **Entering phone_simple must not persist a keyboard mode.** Use a direct keyboard load, not a `MODE_*` that survives later toggles (the PR#135 `MODE_PHONE_SIMPLE` bug made symbols/ABC bounce back to phone_simple).
- **iOS scope = iPhone only.** Skip iPad English variants (`lime_english_ipad*`): the iPad symbol key uses dual-row gestures and the iPad layout already exposes numerics. Do not wire iPad (leave its `-2` key without `longPressCode`).
- **Encoding (user global rule):** the 4 Android English XMLs are UTF-8 **with BOM** (they contain `中`) — preserve BOM. `.json` → UTF-8 **without** BOM. `.swift` → UTF-8 **with** BOM. `.java` → UTF-8 without BOM.
- **Never** restructure unrelated code, blank/rewrite a source file, or revert via git. Targeted edits only.

---

## Reference Map (verified)

**Android (final state after PR#135 + the Phase-A fixes)**
- `LIMEKeyboardView.java`: `KEYCODE_PHONE_SIMPLE_LONGPRESS = -106`; `static boolean isEnglishPhoneSimpleShortcutKey(int primaryCode, int popupResId)` → `primaryCode == KEYCODE_MODE_CHANGE (-2) && popupResId != 0`; `onLongPress` fires `KEYCODE_PHONE_SIMPLE_LONGPRESS` for that key.
- Hint render: `LIMEKeyboardBaseView.onBufferDraw` already draws `mPopupHint` (full key bounds, dots at bottom) when `shouldDrawLabelAndIcon`/`shouldDrawIconFully` are true, i.e. `key.popupResId != 0`. No draw-code change needed — giving the key a `popupKeyboard` triggers it.
- `LIMEKeyboardSwitcher.java`: `switchToPhoneSimple()` (direct load of `phone_simple`, leaves `mMode` unchanged) and idempotent `switchToEnglish()` (resets to `MODE_TEXT` English). `MODE_PHONE_SIMPLE` removed.
- `LIMEService.java`: `switchKeyboard` handles `KEYCODE_PHONE_SIMPLE_LONGPRESS` via `mKeyboardSwitcher.switchToPhoneSimple()`; the `-9` (`KEYCODE_SWITCH_TO_ENGLISH_MODE`) handler calls `mKeyboardSwitcher.switchToEnglish()` (was `toggleChinese()`).
- 4 English XMLs (`lime_english*.xml`): every `-2` key = `keyLabel="@string/label_symbol_key"` + `popupKeyboard="@xml/phone_simple"`.
- Test: `androidTest/.../KeyboardLayoutResourceTest.java` → `englishSymbolKeyShowsPhoneShortcutHintAndLongPressPolicyKeepsNormalSymbolTap()`.

**iOS (to implement)**
- Key-code enum: `LimeIME-iOS/Shared/Models/KeyLayout.swift:8-39` (`LimeKeyCode`); `KeyDef` with `longPressCode` field at 233-283.
- English `-2` key in JSON (label `@string/label_symbol_key` → resolves to "123" via `LayoutLoader.resolveAndroidStringRef`): `lime_english.json:419-433` (`-2` at 420), `lime_english_shift.json:420`, `lime_english_number.json:545`, `lime_english_number_shift.json:545`.
- `phone_simple.json`: return keys `-2` (123 → symbols) and `-9` (ABC → English).
- Dispatch: `KeyboardViewController.swift:1125` (`onKey`); symbol case 1154; `-9` case 1142 → `switchChiEng(toEnglish: true)` (**idempotent** — iOS return path already correct, no `-9` change). Symbol helpers `switchToSymbol`/`loadSymbolLayout` 2670-2747.
- Existing "…" popup indicator: `KeyboardView.swift:1034-1050` (drawn when `!keyDef.popupKeyboard.isEmpty`). Extend its condition to also fire for the generic long-press key.
- Gestures: `makeKeyButton` 762-870; static policies `shouldUseLimeOptionsMenuGesture` (~779) / `shouldUseDualRowGesture` (~838); delegate protocol `KeyboardViewDelegate` 127-143; long-press handlers 872-878 / 1297-1328.
- Delegate impls: `KeyboardViewController.swift:3091` (`didLongPress`), `3107` (`didLongPressPopupKey`).
- Test patterns: `LimeTests/KeyboardViewControllerTest.swift` (`@testable import LimeIME`; layout fixtures decoding `longPressCode`; `LimeKeyCode` rawValue checks; `projectFileURL(_:)` source-wiring asserts). Run: `xcodebuild test -project LimeIME-iOS/LimeIME.xcodeproj -scheme LimeIME -destination 'platform=iOS Simulator,name=iPhone 16' -only-testing:LimeTests/<ClassName>` (adjust scheme/simulator to local).

---

# PHASE A — Android (DONE + VERIFIED — reference for iOS parity)

PR#135 shipped the feature with two defects; both are fixed on the branch and verified. Do not re-implement; this section is the source of truth for what the corrected Android behavior is, so iOS can mirror it.

### PR#135 defects (found during verification)
1. **Label overwritten.** PR#135 set `keyLabel="..."` on the `-2` key, so the "123" face disappeared and the dots drew in the key center.
2. **Broken return path.** PR#135 entered phone_simple via `setKeyboardMode(MODE_PHONE_SIMPLE=7)`, persisting `mMode=7`, so later toggles (symbols, ABC) bounced back to phone_simple; and `-9` (ABC) called `toggleChinese()` (a toggle) which from English flips to Chinese.

### Fixes applied (on `feat/124-android-123-longpress`, uncommitted)
- **Rendering:** reverted all 16 English `-2` keys to `keyLabel="@string/label_symbol_key"` and added `popupKeyboard="@xml/phone_simple"` (triggers the existing `mPopupHint` "…" at the bottom). Detection changed to `isEnglishPhoneSimpleShortcutKey(code, popupResId)` = `code==-2 && popupResId!=0` (only English `-2` keys carry a popup). Normalized via `.claude/scripts/feat124_normalize_english_symbol_key.py`.
- **Long-press dispatch unchanged conceptually:** `LIMEKeyboardView.onLongPress` still fires `KEYCODE_PHONE_SIMPLE_LONGPRESS`; the base view never opens a popup because the override returns `true` before `super.onLongPress`.
- **Return path:** removed `MODE_PHONE_SIMPLE`; added `LIMEKeyboardSwitcher.switchToPhoneSimple()` (direct load, no `mMode` change) and `switchToEnglish()` (idempotent → `MODE_TEXT` English); `LIMEService` enters via `switchToPhoneSimple()` and the `-9` handler calls `switchToEnglish()`.

Reference snippets (final state):

```java
// LIMEKeyboardView.java
public static boolean isEnglishPhoneSimpleShortcutKey(int primaryCode, int popupResId) {
    return primaryCode == LIMEBaseKeyboard.KEYCODE_MODE_CHANGE && popupResId != 0;
}
// onLongPress: } else if (isEnglishPhoneSimpleShortcutKey(key.codes[0], key.popupResId)) {
//                  getOnKeyboardActionListener().onKey(KEYCODE_PHONE_SIMPLE_LONGPRESS, null,0,0); return true; }
```

```java
// LIMEKeyboardSwitcher.java
public void switchToPhoneSimple() {
    if (mInputView == null) return;
    mIsSymbols = false; mIsChinese = false;
    KeyboardId kid = new KeyboardId(getKeyboardXMLID("phone_simple"));
    LIMEKeyboard keyboard = getKeyboard(kid);
    if (keyboard == null) return;
    mInputView.setKeyboard(keyboard);
    keyboard.setShifted(false);
    mInputView.setKeyboard(mInputView.getKeyboard());
    keyboard.setImeOptions(mThemedContext.getResources(), mMode, mImeOptions);
}
public void switchToEnglish() {
    mIsChinese = false; mIsSymbols = false;
    this.setKeyboardMode(ImCode, MODE_TEXT, mImeOptions, false, false, mIsShifted);
}
```

```xml
<!-- each -2 key in the 4 lime_english*.xml (BOM preserved) -->
<Key limehd:codes="-2" limehd:keyLabel="@string/label_symbol_key" limehd:isModifier="true" limehd:popupKeyboard="@xml/phone_simple" limehd:keyWidth="10%p" />
```

### Verification (6.1.27 release/GitHub APK, emulator-5554, `net.toload.main.hd2026`)
Built `:app:assembleRelease` (R8/shrink), debug-signed, installed; `run-as` fails (genuine release build); no crashes in logcat. All passed:
- [x] English `123` key shows "123" face + "…" hint at the bottom (like the `.` key).
- [x] Short-press `123` → `symbols1`.
- [x] Long-press `123` → `phone_simple`.
- [x] `phone_simple` **ABC → English** (return path fixed).
- [x] No mode contamination: after phone_simple, `123`→symbols→`EN`→English (not phone_simple).

### Remaining Android task
- [ ] **A1: Tests + commit.** Run `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=net.toload.main.hd.KeyboardLayoutResourceTest` (the updated assertion checks `keyLabel` resolves to "123" + `popupKeyboard` present + `isEnglishPhoneSimpleShortcutKey(-2, popupResId)`), then commit the fixes to the PR branch (exclude local build.gradle version/applicationId tweaks).

---

# PHASE B — iOS (iPhone) — to implement (mirrors corrected Android)

The English `-2` key keeps its `123` label and gains `longPressCode: -106`; the existing "…" indicator is extended to render for that key; long-press loads `phone_simple`. Return to English uses `phone_simple`'s `-9` (already idempotent on iOS).

### Task B1: New key code

**Files:** Modify `LimeIME-iOS/Shared/Models/KeyLayout.swift:33`; Test `LimeIME-iOS/LimeTests/PhoneSimpleLongPressTest.swift`.

**Interfaces:** Produces `LimeKeyCode.switchToPhoneSimple`, `rawValue == -106`.

- [ ] **Step 1: Write the failing test** — create `PhoneSimpleLongPressTest.swift`:

```swift
import XCTest
@testable import LimeIME

final class PhoneSimpleLongPressTest: XCTestCase {
    func testPhoneSimpleKeyCodeMirrorsAndroid() {
        XCTAssertEqual(LimeKeyCode.switchToPhoneSimple.rawValue, -106)
    }
}
```

- [ ] **Step 2: Run → FAIL** (`switchToPhoneSimple` not a member).
Run: `xcodebuild test -project LimeIME-iOS/LimeIME.xcodeproj -scheme LimeIME -destination 'platform=iOS Simulator,name=iPhone 16' -only-testing:LimeTests/PhoneSimpleLongPressTest`

- [ ] **Step 3: Implement** — in `KeyLayout.swift` after line 33:

```swift
    case switchToPhoneSimple  = -106 // feat#124: English 123 long-press → phone_simple (Android KEYCODE_PHONE_SIMPLE_LONGPRESS)
```

- [ ] **Step 4: Run → PASS.**
- [ ] **Step 5: Commit** — `git commit -m "feat#124 iOS: add switchToPhoneSimple key code (-106)"`

---

### Task B2: English `-2` key keeps "123" + gains `longPressCode -106` (JSON)

**Files:** Modify `lime_english.json` (`-2` block 419-433), `lime_english_shift.json` (420), `lime_english_number.json` (545), `lime_english_number_shift.json` (545); Test `PhoneSimpleLongPressTest.swift`.

**Interfaces:** the `-2` key in the 4 iPhone English JSONs keeps `"label": "@string/label_symbol_key"` and adds `"longPressCode": -106`. `phone_simple.json` and iPad layouts keep `-2` without that `longPressCode`.

- [ ] **Step 1: Write the failing test** — append:

```swift
    private struct KeyFixture: Decodable { let code: Int; let label: String; let longPressCode: Int? }
    private struct RowFixture: Decodable { let keys: [KeyFixture] }
    private struct LayoutFixture: Decodable { let rows: [RowFixture] }

    private func loadLayout(_ id: String) throws -> LayoutFixture {
        let url = try XCTUnwrap(
            Bundle(for: type(of: self)).url(forResource: id, withExtension: "json")
            ?? Bundle(for: KeyboardView.self).url(forResource: id, withExtension: "json"))
        return try JSONDecoder().decode(LayoutFixture.self, from: Data(contentsOf: url))
    }

    func testIPhoneEnglishSymbolKeyKeeps123AndLongPressesToPhoneSimple() throws {
        for id in ["lime_english", "lime_english_shift",
                   "lime_english_number", "lime_english_number_shift"] {
            let key = try XCTUnwrap(try loadLayout(id).rows.flatMap { $0.keys }.first { $0.code == -2 })
            XCTAssertEqual(key.label, "@string/label_symbol_key", "\(id): keep the 123 face")
            XCTAssertEqual(key.longPressCode, -106, "\(id): -2 must long-press to phone_simple")
        }
    }

    func testPhoneSimpleOwnSymbolKeyHasNoPhoneSimpleLongPress() throws {
        let key = try XCTUnwrap(try loadLayout("phone_simple").rows.flatMap { $0.keys }.first { $0.code == -2 })
        XCTAssertNotEqual(key.longPressCode, -106)
    }
```

> If `Bundle(for:)` can't resolve the JSON, reuse `KeyboardViewControllerTest`'s `loadKeyboardLayoutFixture(_:)`.

- [ ] **Step 2: Run → FAIL** (`longPressCode` is nil).
- [ ] **Step 3: Implement** — add `"longPressCode": -106,` to the `-2` key in each of the 4 files, leaving `"label": "@string/label_symbol_key"` unchanged. Example (`lime_english.json`):

```json
        {
          "code": -2,
          "label": "@string/label_symbol_key",
          "sublabel": "",
          "widthPercent": 10.0,
          "icon": "",
          "isModifier": true,
          "isRepeatable": false,
          "isSticky": false,
          "longPressCode": -106,
          "popupKeyboard": "",
          "popupCharacters": ""
        },
```

Save each `.json` as UTF-8 **without** BOM.

- [ ] **Step 4: Run → PASS.**
- [ ] **Step 5: Commit** — `git commit -m "feat#124 iOS: English -2 key keeps 123 and long-presses to phone_simple"`

---

### Task B3: Generic long-press gesture + "…" hint (policy + wiring + indicator)

**Files:** Modify `LimeIME-iOS/LimeKeyboard/KeyboardView.swift` — static `shouldUseGenericLongPress(...)`; protocol `didLongPressKey`; gesture wiring in `makeKeyButton` (~830); `genericLongPressed` handler (~878); extend the "…" indicator block (1034-1050). Test `PhoneSimpleLongPressTest.swift`.

**Interfaces:** Produces `KeyboardView.shouldUseGenericLongPress(keyDef:isPad:layoutId:legacyGlobeMode:) -> Bool` (true iff `longPressCode != 0`, not an options key, not an iPad dual-row key) and `KeyboardViewDelegate.keyboardView(_:didLongPressKey:)`.

- [ ] **Step 1: Write the failing test** — append:

```swift
    private func symKey(_ lp: Int) -> KeyDef { KeyDef(code: -2, label: "123", longPressCode: lp) }

    func testGenericLongPressFiresForEnglishSymbolKeyOnIPhone() {
        XCTAssertTrue(KeyboardView.shouldUseGenericLongPress(
            keyDef: symKey(-106), isPad: false, layoutId: "lime_english", legacyGlobeMode: false))
    }
    func testGenericLongPressIgnoresKeysWithoutLongPressCode() {
        XCTAssertFalse(KeyboardView.shouldUseGenericLongPress(
            keyDef: symKey(0), isPad: false, layoutId: "lime_english", legacyGlobeMode: false))
    }
    func testGenericLongPressYieldsToIPadDualRow() {
        XCTAssertFalse(KeyboardView.shouldUseGenericLongPress(
            keyDef: symKey(-106), isPad: true, layoutId: "lime_english_ipad", legacyGlobeMode: false))
    }
    func testKeyboardViewWiresGenericLongPressAndHint() throws {
        let src = try String(contentsOf: projectFileURL("LimeKeyboard/KeyboardView.swift"), encoding: .utf8)
        XCTAssertTrue(src.contains("func shouldUseGenericLongPress"))
        XCTAssertTrue(src.contains("genericLongPressed"))
        XCTAssertTrue(src.contains("didLongPressKey"))
    }
```

> Reuse `KeyboardViewControllerTest`'s `projectFileURL(_:)`. iPad never reaches generic long-press: Task B2 leaves iPad JSONs without `longPressCode`.

- [ ] **Step 2: Run → FAIL** (`shouldUseGenericLongPress` missing).
- [ ] **Step 3: Implement**

**(a)** Static policy (next to the other gesture policies):

```swift
    /// feat#124: keys with a generic secondary action (longPressCode) get a long-press gesture —
    /// except options keys and iPad dual-row keys, which own their own gestures.
    static func shouldUseGenericLongPress(keyDef: KeyDef, isPad: Bool,
                                          layoutId: String, legacyGlobeMode: Bool) -> Bool {
        guard keyDef.longPressCode != 0 else { return false }
        if shouldUseLimeOptionsMenuGesture(keyDef: keyDef, legacyGlobeMode: legacyGlobeMode) { return false }
        if shouldUseDualRowGesture(isPad: isPad, layoutId: layoutId, keyDef: keyDef) { return false }
        return true
    }
```

**(b)** Protocol method (after `didLongPress`, line 131): `func keyboardView(_ view: KeyboardView, didLongPressKey keyDef: KeyDef)`

**(c)** Gesture in `makeKeyButton` (after the popup-keyboard gesture block, ~830):

```swift
        // feat#124: generic long-press → secondary action (English 123 → phone_simple).
        if Self.shouldUseGenericLongPress(keyDef: keyDef, isPad: isPad,
                                          layoutId: layout.id, legacyGlobeMode: legacyGlobeMode) {
            let lp = UILongPressGestureRecognizer(target: self, action: #selector(genericLongPressed(_:)))
            lp.minimumPressDuration = LayoutMetrics.Gesture.specialKeyHoldDuration
            btn.addGestureRecognizer(lp)
        }
```

**(d)** Handler (near `popupKeyLongPressed`, ~878):

```swift
    @objc private func genericLongPressed(_ gr: UILongPressGestureRecognizer) {
        guard gr.state == .began, let keyBtn = gr.view as? KeyButton else { return }
        keyBtn.wasLongPressed = true   // suppress any deferred primary tap
        fireHaptic()
        delegate?.keyboardView(self, didLongPressKey: keyBtn.keyDef)
    }
```

**(e)** Extend the "…" indicator condition (line 1035) so the long-press key shows the hint while keeping its "123" label — mirrors Android's `mPopupHint`:

```swift
        // Popup / generic-long-press hint: small "…" pinned to bottom-right
        if !keyDef.popupKeyboard.isEmpty
            || Self.shouldUseGenericLongPress(keyDef: keyDef, isPad: isPad,
                                              layoutId: layout.id, legacyGlobeMode: legacyGlobeMode) {
```

Save `KeyboardView.swift` as UTF-8 **with** BOM.

- [ ] **Step 4: Run → PASS** (delegate-conformance error resolves once B4 adds the controller impl; compile B3+B4 together).
- [ ] **Step 5: Commit** (combine with B4 if needed) — `git commit -m "feat#124 iOS: generic long-press gesture + … hint for longPressCode keys"`

---

### Task B4: Controller — route long-press + load phone_simple

**Files:** Modify `LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift` — `onKey` case `-106` (~1155); `switchToPhoneSimple()` (after `exitSymbolMode`, ~2747); `didLongPressKey` impl (after line 3105). Test `PhoneSimpleLongPressTest.swift`.

**Interfaces:** Consumes `LimeKeyCode.switchToPhoneSimple` (B1), `didLongPressKey` (B3), `LayoutLoader.load("phone_simple")`. Produces `KeyboardViewController.switchToPhoneSimple()`.

- [ ] **Step 1: Write the failing test** — append:

```swift
    func testControllerLoadsPhoneSimpleAndRoutesLongPress() throws {
        let src = try String(contentsOf: projectFileURL("LimeKeyboard/KeyboardViewController.swift"), encoding: .utf8)
        XCTAssertTrue(src.contains("case LimeKeyCode.switchToPhoneSimple.rawValue"))
        XCTAssertTrue(src.contains("func switchToPhoneSimple"))
        XCTAssertTrue(src.contains("LayoutLoader.load(\"phone_simple\")"))
        XCTAssertTrue(src.contains("didLongPressKey keyDef"))
    }
    func testPhoneSimpleLayoutReturnsToEnglishViaAbc() throws {
        let phone = try XCTUnwrap(LayoutLoader.load("phone_simple"))
        let codes = phone.rows.flatMap { $0.keys.map(\.code) }
        XCTAssertTrue(codes.contains(LimeKeyCode.switchToEnglish.rawValue), "phone_simple needs ABC (-9)")
        XCTAssertTrue(codes.contains(LimeKeyCode.switchToSymbol.rawValue), "phone_simple needs 123 (-2)")
    }
```

- [ ] **Step 2: Run → FAIL** (source markers absent / delegate conformance).
- [ ] **Step 3: Implement**

**(a)** `onKey` after the `switchSymbolKeyboard` case (line 1155):

```swift
        case LimeKeyCode.switchToPhoneSimple.rawValue: switchToPhoneSimple()
```

**(b)** `switchToPhoneSimple()` after `exitSymbolMode()` (line 2747):

```swift
    /// feat#124: load the phone_simple numeric keypad (English 123 long-press).
    /// Return to English uses phone_simple's existing ABC (-9 → switchChiEng(toEnglish:true)).
    private func switchToPhoneSimple() {
        dismissPopupKeyboard()
        clearShiftState()
        mEnglishOnly = true
        let layout = LayoutLoader.load("phone_simple") ?? currentLayout
        currentLayout = layout
        keyboardView?.setLayout(layout)
        applyHeight()
    }
```

**(c)** Delegate impl next to `didLongPress` (after line 3105):

```swift
    func keyboardView(_ view: KeyboardView, didLongPressKey keyDef: KeyDef) {
        onKey(primaryCode: keyDef.longPressCode)
    }
```

- [ ] **Step 4: Run → PASS**, then regression-run:
Run: `xcodebuild test ... -only-testing:LimeTests/PhoneSimpleLongPressTest -only-testing:LimeTests/KeyboardViewControllerTest`

- [ ] **Step 5: Commit** — `git commit -m "feat#124 iOS: route 123 long-press to phone_simple; ABC returns to English"`

---

### Task B5: iOS visual verification

**Files:** none (manual/visual gate). Use the `ios-visual-verify` skill in the Simulator:
- [ ] English keyboard: the `123` key keeps its "123" face with a small "…" hint at the bottom (normal, shift, number, number-shift).
- [ ] **Short-press `123`** → first symbol keyboard (unchanged). A quick tap commits `switchToSymbol`, not the long-press.
- [ ] **Long-press `123`** → `phone_simple` numeric keypad.
- [ ] On `phone_simple`: `ABC` → English; `123` → symbols.
- [ ] Chinese IM, symbols, emoji panel unaffected; **iPad** English shows no "…" and behaves as before.

---

## Self-Review

**Spec coverage** (backlog feat#124 Android + iOS):
- "Symbol key shows the `…`/`...` affordance, English only" → Android: "123" face + `mPopupHint` via `popupKeyboard` (verified); iOS B2/B3 (keep label + extend "…" indicator). ✓
- "Normal press still → first symbol keyboard" → `-2` short-press unchanged both platforms (Android verified; iOS B5). ✓
- "Long-press → phone_simple" → Android (verified); iOS B1/B3/B4. ✓
- iOS parity → Phase B (iPad deferred per Global Constraints). ✓

**Type/name consistency:** `-106` used identically (`KEYCODE_PHONE_SIMPLE_LONGPRESS` / `switchToPhoneSimple`). iOS `shouldUseGenericLongPress(keyDef:isPad:layoutId:legacyGlobeMode:)` identical across wiring, hint condition (B3e), and tests. `didLongPressKey` declared (B3) + implemented (B4). `LayoutLoader.load("phone_simple")` in B4 impl + test. iOS `-2` key keeps `label "@string/label_symbol_key"` + adds `longPressCode -106` (B2).

**Key decisions / notes:**
1. **Affordance reuses the existing popup hint**, not a relabel — keeps the "123" face. Android: `popupKeyboard="@xml/phone_simple"` (never inflated as a popup because `onLongPress` intercepts first). iOS: extend the "…" indicator to `longPressCode` keys.
2. **No persistent mode for phone_simple** — direct load on both platforms (Android `switchToPhoneSimple()` leaves `mMode`; iOS loads the layout). Prevents the symbols/ABC bounce-back.
3. **Return path:** Android `-9` made idempotent (`switchToEnglish()`); iOS `-9` (`switchChiEng(toEnglish:true)`) was already idempotent — no iOS change.
4. **iOS short-press vs long-press on `-2`:** the `-2` key uses standard `keyDown`/`keyUp`; a recognized long-press cancels the control touch (→ `keyCancel`, no `didPress`); `wasLongPressed` set defensively. Verify short-press still reaches `switchToSymbol` in B5.

## Execution Handoff

Android is implemented + verified on `feat/124-android-123-longpress` (fixes uncommitted). Remaining: Phase A1 (instrumented test + commit) and **Phase B (iOS)**.

For Phase B: **Subagent-Driven** (fresh subagent per task, review between) or **Inline** (checkpoints). Which approach? (Per repo rules, no source files edited until you say to proceed.)
