# Android And iPhone Keyboard Layout

This document describes the keyboard layout formats and special-key behavior for both LIME Android and LIME iPhone/iPad. Android layouts are XML resources. iPhone layouts are generated JSON resources that mirror the Android model while adding iOS-only behavior for globe, iPad secondary glyphs, and UIKit gestures.

## Source Map

Android keyboard XML files live under `LimeStudio/app/src/main/res/xml/`.

Android parser and runtime handlers:

- `LimeStudio/app/src/main/res/values/attrs.xml`: custom `limehd:` XML attributes.
- `LimeStudio/app/src/main/java/org/limeime/keyboard/LIMEBaseKeyboard.java`: XML parser, key geometry, key code constants.
- `LimeStudio/app/src/main/java/org/limeime/keyboard/LIMEKeyboard.java`: LIME-specific key drawing and hit-area adjustment.
- `LimeStudio/app/src/main/java/org/limeime/keyboard/LIMEKeyboardView.java`: Android keyboard view overrides, space slide, special long-press handling.
- `LimeStudio/app/src/main/java/org/limeime/keyboard/LIMEKeyboardBaseView.java`: key preview, popup mini-keyboard, gesture detector.
- `LimeStudio/app/src/main/java/org/limeime/keyboard/PointerTracker.java`: touch tracking, repeat, multi-tap, long-press timer.
- `LimeStudio/app/src/main/java/org/limeime/LIMEService.java`: final action dispatch for key codes.

iPhone/iPad keyboard JSON files live under `LimeIME-iOS/LimeKeyboard/Layouts/`.

iOS parser and runtime handlers:

- `LimeIME-iOS/Shared/Models/KeyLayout.swift`: `LimeKeyCode`, `KeyDef`, `KeyRow`, `LimeKeyLayout`.
- `LimeIME-iOS/LimeKeyboard/LayoutLoader.swift`: loads JSON, resolves Android-style labels and popup references, chooses `_ipad` variants.
- `LimeIME-iOS/LimeKeyboard/KeyboardView.swift`: renders keys, handles touch, repeat, popup long press, space key, iPad slide-down secondary keys.
- `LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift`: final key dispatch, IM switching, options menu, popup keyboard, emoji panel.
- `LimeIME-iOS/LimeKeyboard/PopupKeyboardView.swift`: floating long-press mini-keyboard.
- `LimeIME-iOS/LimeKeyboard/LayoutMetrics.swift`: gesture thresholds and geometry constants.

Canonical iPad references:

- [IPAD_KEYBOARD.md](IPAD_KEYBOARD.md): iPad-only layout scaffolding, row key-count invariants, `_ipad.json` generation rules, globe/menu behavior, dual-row key rules, and current implementation log.
- [IPAD_KB_SIZE_TIERS.md](IPAD_KB_SIZE_TIERS.md): planned iPad size tiers for 13", 11", and mini, including square-cell row-height invariants and `_ipad_narrow.json` strategy.

## Layout Format

### Android XML

An Android layout is a `Keyboard` resource with `Row` children and `Key` children.

```xml
<Keyboard xmlns:limehd="http://schemas.android.com/apk/res-auto"
    limehd:keyWidth="10%p"
    limehd:keyHeight="@dimen/key_height"
    limehd:horizontalGap="0px"
    limehd:verticalGap="0px">

    <Row>
        <Key limehd:codes="113" limehd:keyLabel="q" limehd:keyEdgeFlags="left" />
        <Key limehd:codes="-5" limehd:keyIcon="@drawable/sym_keyboard_delete_light"
            limehd:isModifier="true"
            limehd:isRepeatable="true"
            limehd:keyEdgeFlags="right" />
    </Row>
</Keyboard>
```

Common Android attributes:

- `limehd:keyWidth`, `limehd:keyHeight`, `limehd:horizontalGap`, `limehd:verticalGap`: defaults on `Keyboard`, overridable on `Row` or `Key`. Percent values such as `10%p` are relative to the parent keyboard width.
- `limehd:codes`: integer key code. Printable keys usually use Unicode code points, for example `32` for space or `113` for `q`. Comma-separated values enable multi-tap behavior.
- `limehd:keyLabel`: visible text label. If `codes` is omitted, the parser uses the first character of this label as the key code.
- `limehd:keyOutputText`: committed text string. Use this for multi-character output such as domains or smileys.
- `limehd:keyIcon`, `limehd:iconPreview`: drawable for the key or preview.
- `limehd:isModifier`: marks function keys such as shift, delete, mode, language, return, and keyboard/dismiss.
- `limehd:isSticky`: toggle key behavior, mainly shift.
- `limehd:isRepeatable`: repeats while held. Delete uses this. Space may be marked repeatable in XML, but `PointerTracker` suppresses space repeat.
- `limehd:keyEdgeFlags`: `left` or `right` key anchoring; row edge flags are ORed into key edge flags.
- `limehd:rowEdgeFlags`: `top` or `bottom` row anchoring.
- `limehd:keyboardMode`: selects rows/keys for input-context modes such as normal, URL, email, or IM mode.

### iPhone JSON

iPhone/iPad layouts are JSON files copied into the keyboard extension bundle. The JSON shape mirrors Android fields, with UIKit-friendly names and a few iOS extensions.

```json
{
  "id": "lime_english",
  "defaultWidthPercent": 10.0,
  "rows": [
    {
      "isBottomRow": false,
      "keys": [
        {
          "code": 113,
          "label": "q",
          "sublabel": "",
          "widthPercent": 10.0,
          "icon": "",
          "isModifier": false,
          "isRepeatable": false,
          "isSticky": false,
          "popupKeyboard": "",
          "popupCharacters": ""
        }
      ]
    }
  ]
}
```

Common iOS fields:

- `id`: layout id, usually matching the Android XML basename.
- `defaultWidthPercent`: fallback key width.
- `rows[].isBottomRow`: marks bottom-row styling and behavior.
- `code`: primary key code. Positive codes are Unicode scalar values; negative codes are special actions from `LimeKeyCode`.
- `codes`: optional multi-code array. If absent, `LayoutLoader` uses `[code]`.
- `label`, `sublabel`: primary and secondary text. `LayoutLoader` splits Android literal `\n` labels into these fields and collapses Android literal `\t` for dual-mapped phonetic keys.
- `widthPercent`: proportional row width.
- `icon`: SF Symbol name, such as `space.bar`, `return`, `delete.backward`, or `keyboard.chevron.compact.down`.
- `isModifier`, `isRepeatable`, `isSticky`: same intent as Android.
- `longPressCode`: iOS extension. `0` means none. Positive values are iPad slide-down secondary glyphs. `-100` means keyboard/options menu.
- `popupKeyboard`, `popupCharacters`: Android-style popup data. `LayoutLoader` strips `@xml/`, validates real popup layouts, and keeps `popup_template` only when `popupCharacters` is non-empty.

## Layout Families

Android main layout files include:

- English layouts: `lime_english.xml`, `lime_english_shift.xml`, `lime_english_number.xml`, `lime_english_number_shift.xml`.
- ABC-grid layouts: `lime_abc.xml`, `lime_abc_shift.xml`.
- Chinese/IM layouts: `lime.xml`, `lime_shift.xml`, `lime_phonetic.xml`, `lime_cj.xml`, `lime_array.xml`, `lime_dayi.xml`, `lime_hsu.xml`, and related shifted/number variants.
- Context layouts: `lime_url.xml`, `lime_email.xml`, phone layouts.
- Symbol pages: `symbols1.xml`, `symbols2.xml`, `symbols3.xml`.
- Popup mini-keyboards: `popup_*.xml`.

iOS has corresponding JSON files, usually with the same basename:

- Phone layouts: `lime_english.json`, `lime_english_shift.json`, `lime_english_number.json`, `lime_english_number_shift.json`, `lime_abc.json`, `lime_phonetic.json`, and the other IM/symbol/popup JSON files.
- iPad layouts: `_ipad` suffix variants such as `lime_english_ipad.json`, `lime_abc_ipad.json`, `lime_phonetic_ipad.json`, and shifted/number siblings.
- `LayoutLoader.load(_:)` tries the `_ipad` variant first when the host app trait collection is iPad, then falls back to the phone layout.

Rows should normally total to 100% parent width once key widths and gaps are considered. English bottom rows often have multiple context variants on Android and multiple sibling JSON files on iOS, so changes need to be applied to every relevant phone/iPad, shifted, number, URL, and email layout.

For iPad-specific row structure and generation invariants, defer to [IPAD_KEYBOARD.md](IPAD_KEYBOARD.md). That file owns the current rules for 5-row iPad layouts, row key counts, bottom-row template, shift mirroring, transparent spacers, globe visibility, and dual-sliding key label convention.

## Special Key Codes

Shared Android/iOS codes:

| Code | Android constant | iOS constant | Behavior |
| --- | --- | --- | --- |
| `-1` | `KEYCODE_SHIFT` | `LimeKeyCode.shift` | Toggle/handle shift. Usually modifier and sticky. |
| `-2` | `KEYCODE_MODE_CHANGE` | `LimeKeyCode.switchToSymbol` | Switch into symbol mode, or back out depending on current keyboard state. |
| `-3` | `KEYCODE_DONE` | `LimeKeyCode.done` | Short press closes keyboard; long press opens the LIME options menu. |
| `-5` | `KEYCODE_DELETE` | `LimeKeyCode.delete` | Backspace. Usually repeatable. |
| `-9` | `KEYCODE_SWITCH_TO_ENGLISH_MODE` | `LimeKeyCode.switchToEnglish` | Switch Chinese/IM keyboard to English mode. Often labeled `EN` or `ABC`. |
| `-10` | `KEYCODE_SWITCH_TO_IM_MODE` | `LimeKeyCode.switchToIM` | Switch English mode back to the active Chinese IM. Usually labeled `中` or `中文`. |
| `-15` | `KEYCODE_SWITCH_SYMBOL_KEYBOARD` | `LimeKeyCode.switchSymbolKeyboard` | Cycle symbol pages, for example `1/3`, `2/3`, `3/3`. |
| `10` | `KEYCODE_ENTER` | `LimeKeyCode.enter` | Enter/return. |
| `32` | `KEYCODE_SPACE` | `LimeKeyCode.space` | Space. Also supports slide and long-press behavior. |
| `-201` | `LIME.KEYCODE_EMOJI_PANEL` | `LimeKeyCode.emojiPanel` | Opens emoji panel. |
| `-202` | `LIME.KEYCODE_EMOJI_ABC` | `LimeKeyCode.emojiABC` | Hides emoji panel and returns to keyboard. |
| `-203` to `-212` | Emoji category codes | Emoji category codes | Jump to emoji categories. |

## Shift Key Behavior

Android and iOS both expose shift as `code = -1`, but the runtime touch model differs. The desired user-visible state machine is the same:

- Tap and release shift once from unshifted: enter one-shot shift. The shift key shows active/blue, the shifted keyboard is visible, the next character is shifted, then shift returns to off.
- Tap and release shift once from shifted: return to unshifted. Repeated single taps must only toggle shifted/unshifted and must not promote into caps lock.
- Double-tap shift: enter caps lock. Shifted output continues until shift is tapped again.
- Tap and release shift once from caps lock: exit caps lock and return to unshifted.
- Press and hold shift: show shifted keys while the physical shift touch is down. Every tapped key while shift is held outputs shifted content. Releasing shift returns the key state and keyboard view to unshifted.

iOS implementation notes:

- `KeyboardViewController` owns `isShiftOn`, `mCapsLock`, `isShiftKeyHeld`, and `shiftHoldModifiedCharacter`.
- Do not rebuild/remove the pressed shift button while the shift touch is still down. `KeyboardView.previewLayout(_:)` restyles the existing buttons with the shifted layout so UIKit can still deliver the original shift release event.
- A held shift reset happens on release after shift modified at least one character. A one-shot shift reset happens after the first character when shift is no longer physically held.
- iPad dual-label keys use `longPressCode` as their shifted output while a physical shift hold is active.
- Repeated shift press events during one physical hold are ignored so they do not promote the state into caps lock. Caps lock requires an actual double-tap gesture, not the old three-state repeated-single-tap cycle.

Android-only or Android-specific codes:

| Code | Constant | Behavior |
| --- | --- | --- |
| `-6` | `KEYCODE_ALT` | Alt modifier support. |
| `-11` | `KEYCODE_UP` | Sends DPAD up. |
| `-12` | `KEYCODE_DOWN` | Sends DPAD down. |
| `-13` | `KEYCODE_LEFT` | Sends DPAD left. |
| `-14` | `KEYCODE_RIGHT` | Sends DPAD right. |
| `-100` | `LIMEKeyboardView.KEYCODE_OPTIONS` | Internal synthetic code from long-pressing `KEYCODE_DONE`; opens options. |
| `-102` | `LIMEKeyboardView.KEYCODE_SPACE_LONGPRESS` | Internal synthetic code from long-pressing space; opens the active IM picker. |
| `-104` | `LIMEKeyboardView.KEYCODE_NEXT_IM` | Switch to next active IM. |
| `-105` | `LIMEKeyboardView.KEYCODE_PREV_IM` | Switch to previous active IM. |

iOS-only or iOS-specific codes:

| Code | Constant | Behavior |
| --- | --- | --- |
| `-200` | `LimeKeyCode.globe` | iOS system input-mode key. Uses `handleInputModeList(from:with:)` when the input-mode controller is available; otherwise long press enters the LIME/globe menu. |
| `-220` | `LimeKeyCode.voiceInput` | Deprecated iOS mic key. Shipped iPhone/iPad layouts must not expose it because custom keyboard extensions cannot record audio or launch system dictation. |
| `-20` | `LimeKeyCode.nextIM` | Switch to next active LIME IM. |
| `-21` | `LimeKeyCode.prevIM` | Switch to previous active LIME IM. |
| `-30` | `LimeKeyCode.arrowLeft` | Move candidate selection left, or document caret left. |
| `-31` | `LimeKeyCode.arrowRight` | Move candidate selection right, or document caret right. |
| `-32` | `LimeKeyCode.arrowUp` | Move by line upward. |
| `-33` | `LimeKeyCode.arrowDown` | Move by line downward. |
| `-100` | `LimeKeyCode.keyboardOptionsMenu` | Stored in JSON `longPressCode` for keyboard/options long press. |

## Popup And Long-Press Keys

### Android

Long press timeout is `400 ms` from `config_long_press_key_timeout`.

A key gets a long-press popup when it has `limehd:popupKeyboard`.

```xml
<Key limehd:codes="101"
    limehd:keyLabel="e"
    limehd:popupKeyboard="@xml/popup_template"
    limehd:popupCharacters="@string/alternates_for_e" />
```

There are two popup styles:

- `popupKeyboard` only: inflate the named popup XML directly.
- `popupKeyboard` plus `popupCharacters`: build a mini-keyboard from the characters using the popup template.

When the mini-keyboard is shown, selecting a mini key calls the same `onKey()`/`onText()` path as a normal key, then dismisses the popup. Popup keys can also use `keyOutputText` for multi-character commits.

### iPhone

Popup keyboard hold duration is `0.4 s` from `LayoutMetrics.Gesture.popupKeyboardHoldDuration`.

`KeyboardView` attaches a `UILongPressGestureRecognizer` to keys whose resolved `popupKeyboard` is non-empty. The controller resolves the popup this way:

- `popup_template`: build a one-row popup from `popupCharacters`.
- Other names such as `popup_punctuation` or `popup_domains`: load the corresponding JSON layout.
- Single-key popup: commit the popup key directly without showing a floating panel.
- Multi-key popup: show `PopupKeyboardView` above the source key, clamped inside the keyboard view.

`PopupKeyboardView` is a floating UIKit view with one button per popup key. Selecting a key dismisses the popup and routes the selected `KeyDef` through normal key dispatch. Positive popup codes commit text directly; negative popup codes route through `onKey(primaryCode:)`.

## Long-Press Keyboard Key Menu

### Android

The lower-left keyboard/dismiss key is usually `codes="-3"` (`KEYCODE_DONE`) with a keyboard/dismiss icon.

- Short press: `LIMEService.onKey()` handles `KEYCODE_DONE` by closing the keyboard.
- Long press: `LIMEKeyboardView.onLongPress()` converts it to `KEYCODE_OPTIONS`, then `LIMEService.handleOptions()` opens the LIME options dialog.

The Android options dialog contains:

- Preference.
- Reverse lookup source for the current active IM.
- Han conversion.
- Keyboard list, which opens the active IM picker.
- System input method picker.
- Split/merge keyboard, when available for the current orientation.
- Voice input.

Reverse lookup should be handled as an options-menu submenu, not by routing the user into the full settings UI. The row should show the current value, for example `字根反查：無`, and selecting it should open a single-choice list using the same labels and values as the preference screen:

- Labels: `無`, followed by the enabled IM display names from the same active IM list used by the IM picker / IM list UI.
- Values: `none`, followed by the matching IM table codes (`tableNick` / Android IM code). The displayed label changes, but the stored value stays the table code.

When the user selects a value, write it through the preference API for the current active IM. Android's phonetic IM uses the legacy key `bpmf_im_reverselookup`; other IMs use `<table>_im_reverselookup`. `LIMEPreferenceManager` is the right place for the shared getter/setter mapping so the keyboard menu and preference screen stay aligned.

### iPhone

The iPhone/iPad keyboard/dismiss key is also `code = -3` (`LimeKeyCode.done`) with icon `keyboard.chevron.compact.down`. JSON may store `longPressCode = -100` for parity and documentation, but the runtime special-cases the key by code.

- Short press: `KeyboardViewController.onKey()` handles `LimeKeyCode.done` by closing the keyboard.
- Long press: `KeyboardView.specialLongPressed(_:)` calls `keyboardView(_:didLongPress:)`; the controller shows a globe/key preview and then `showGlobeMenu(from:)`.

The iOS inline options menu mirrors Android where iOS allows it, but intentionally omits Android's Preference item because a keyboard extension cannot reliably route into the containing LimeSettings app:

- Reverse lookup source for the current active IM.
- Han conversion sub-picker.
- LIME IM picker.
- System input-mode switch, only when a visible globe key is not already handling it.
- Cancel.

iOS cannot show Android's system `InputMethodManager` picker. It uses `advanceToNextInputMode()` and UIKit's globe handling instead.

Reverse lookup should also be handled inside the keyboard extension as an inline submenu. Do not try to open the containing app's reverse-lookup settings screen from the keyboard. The settings screen stores each value in App Group `UserDefaults`, and the keyboard already reads the active value with the current IM key.

The inline submenu should show the same source choices as the Settings app: `無`, followed by the enabled IM display names from the active IM list. Selection still writes the backing table code (`none`, `cj`, `phonetic`, etc.) to the current active IM's reverse-lookup preference. If the active IM list cannot be loaded yet, the implementation may fall back to the built-in IM code list so the menu is never empty.

Selecting a value should write through the shared preference API to the current active IM's reverse lookup key, matching `ReverseLookupSettingsView`. This keeps iPhone behavior aligned with Android while avoiding Settings-app routing from inside the keyboard extension.

## Sliding Space Key

### Android

Horizontal sliding on the space key moves the text cursor.

Implementation details:

- `LIMEKeyboardView` starts tracking when the touch begins on the space key.
- Movement below `@dimen/space_caret_dead_zone` is ignored. The default is `12dp`.
- Once movement crosses the dead zone, the normal key touch is cancelled so the gesture does not insert a space.
- Steps are based on `@dimen/space_caret_step`, default `7dp`.
- Larger drags accelerate: after about `60dp` travel the step size doubles, and after about `140dp` it accelerates again.
- Negative steps send DPAD left; positive steps send DPAD right.
- `LIMEService.moveCaretBy()` ignores the gesture while composing text is active, so it only moves the editor caret when there is no composing buffer.

### iPhone

The iPhone/iPad space key uses `SpaceKeyButton`, a custom `KeyButton` subclass that handles tap, horizontal slide, and long press with raw touch methods instead of normal `UIButton` touch events.

Implementation details:

- Movement below `LayoutMetrics.Gesture.spaceSwipeThreshold` is ignored. The default is `12 pt`.
- Once movement crosses the dead zone, the long-press timer is cancelled and tap insertion is suppressed.
- Steps are based on `LayoutMetrics.Gesture.spaceCaretStepPx`, default `7 pt`.
- Larger drags accelerate with the same tiers as Android: after about `60 pt` and `140 pt` beyond the dead zone.
- Negative steps call `textDocumentProxy.adjustTextPosition(byCharacterOffset:)` left; positive steps move right.
- `KeyboardViewController.keyboardView(_:didMoveCaretBy:)` ignores the gesture while `mComposing` is non-empty.

## Long-Press Space Key Menu

### Android

Long-pressing space opens the active LIME IM picker.

`LIMEKeyboardView.onLongPress()` only does this when the finger has not moved far enough to count as a space slide. It emits `KEYCODE_SPACE_LONGPRESS`; `LIMEService.onKey()` handles that by calling `showIMPicker()`.

The picker lists active, non-disabled IMs. Selecting one updates the active IM, clears composing state when needed, leaves English-only mode, initializes the selected IM keyboard, and refreshes keyboard configuration.

### iPhone

Long-pressing space opens the LIME-internal IM picker, not the iOS system keyboard picker.

`SpaceKeyButton` starts a `0.5 s` timer from `LayoutMetrics.Gesture.spaceLongPressDuration`. If the finger has not slid past the caret threshold, it calls `KeyboardView.keyboardView(_:didLongPress:)`. The controller handles a space long press by calling `showLimeIMPicker()`.

The picker lists active LIME IMs, marks the current IM with a checkmark, and calls `switchIM(toIndex:)` for the selected item.

## iPad Secondary Glyphs

iPad JSON layouts may use `longPressCode` on ordinary text keys for Apple-style secondary glyphs. This is separate from popup keyboards. The complete iPad layout and gesture spec lives in [IPAD_KEYBOARD.md](IPAD_KEYBOARD.md); this section is only the cross-platform summary.

Runtime behavior:

- Only active for iPad layouts where the layout id ends with `_ipad`.
- Downward pan past `LayoutMetrics.Gesture.dualRowSwipeThreshold(landscape:)` commits the secondary glyph (`longPressCode`) instead of the primary `code`.
- Long press previews the secondary glyph and commits it on release.
- Threshold is `24 pt` in portrait and `16 pt` in landscape.

Do not confuse this with Android `popupCharacters`. iPad `longPressCode` is for one secondary glyph directly on the key. `popupKeyboard` is for a floating multi-key popup.

For iPad 13" / 11" / mini sizing, do not infer sizes from this file. Use [IPAD_KB_SIZE_TIERS.md](IPAD_KB_SIZE_TIERS.md), which owns the square-cell invariant, planned `.large` / `.medium` / `.small` tiers, and `_ipad_narrow.json` fallback strategy.

## General Swipe Gestures

Android base keyboard swipes:

- Swipe right: pick highlighted candidate.
- Swipe left: backspace.
- Swipe down: close keyboard.
- Swipe up: open options.

These are separate from the space-key horizontal slide. Space slide consumes the gesture once it crosses the dead zone.

iPhone does not use these global Android-style keyboard swipes. It uses specific UIKit gestures:

- Space horizontal slide: caret movement.
- Keyboard/dismiss and globe long press: options/globe menu.
- Popup-key long press: floating mini keyboard.
- iPad dual-row pan/long press: secondary glyph.

## Touch Handling — Gaps And Row Margins (iPhone)

On iPhone the whole keyboard is driven by a single `KeyTouchLayer` per row, not by
N per-key `UIButton`s (see [IOS_TOUCH_REWRITE.md](IOS_TOUCH_REWRITE.md)). Two
consequences matter to layout authors:

- **The per-row touch layer spans the full row width; keys are centered inside it
  via a layout guide.** A tap in an indented row's side margin, or in a `gap` /
  transparent spacer between keys, resolves to the **nearest key by proximity**
  (`KeyDetector`) instead of being dead. A layout may leave gaps/margins for visual
  spacing without creating dead zones — taps beyond the proximity threshold of any
  real key are still ignored.
- **The hit surface must not be fully transparent.** iOS custom-keyboard extensions
  drop touches that land on fully transparent pixels *before* hit-testing
  ([IOS_CANDI_TOUCH.md §Resolution](IOS_CANDI_TOUCH.md)). The `KeyTouchLayer` (and the
  expanded candidate panel's scroll content + cells) therefore use a near-invisible
  `LayoutMetrics.TouchTrap.fill` (`white 0.5, alpha 0.01`). Key frames already have
  solid backgrounds, so on-key taps were never affected — only the between-key gaps
  were, until they were given the trap fill (commit `7188a4ae`).

Android has no equivalent workaround: one custom view owns the whole `MotionEvent`
stream and there is no transparent-pixel gate, so gaps resolve through
`ProximityKeyDetector` directly. For iPad spacer/gap specifics, see
[IPAD_KEYBOARD.md §4.2.7](IPAD_KEYBOARD.md).

## Editing Notes

- For Android English layouts, keep the `中` key immediately left of the space key across all English variants and modes.
- For iPhone English layouts, keep the IM switch key (`中`/`中文`) in the corresponding left-of-space slot across phone and iPad JSON variants unless the visual spec changes.
- Emoji panel entry on Android and iPhone is owned by the candidate bar launcher. Do not insert a dedicated emoji key back into English layout files unless the product decision changes.
- Android English bottom-row changes should check `lime_english.xml`, `lime_english_shift.xml`, `lime_english_number.xml`, and `lime_english_number_shift.xml`.
- iPhone English bottom-row changes should check the matching JSON files, including `_ipad` variants.
- iPad layout, key-count, and sizing changes should start from [IPAD_KEYBOARD.md](IPAD_KEYBOARD.md) and [IPAD_KB_SIZE_TIERS.md](IPAD_KB_SIZE_TIERS.md); this file intentionally stays at the Android/iPhone correspondence level.
- Android URL/email context changes should check `lime_url.xml` and `lime_email.xml`.
- iPhone URL/email context changes should check `lime_url.json`, `lime_email.json`, and their `_ipad` variants.
- Prefer Android `keyOutputText` and iOS popup/direct text handling for strings; prefer numeric `codes`/`code` for single key codes and special actions.
- Avoid relying on visible labels for special behavior. Runtime behavior is driven by numeric key codes.
