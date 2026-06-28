# iOS Full Access — Impact on LimeIME

Scope: LimeKeyboard extension (`LimeIME-iOS/LimeKeyboard/`).

The user-facing **Settings → General → Keyboard → LimeIME → Allow Full Access** switch gates a specific set of hardware and system capabilities inside keyboard extensions. For LimeIME, only two features depend on it.

---

## Features that require Full Access

### 1. Haptic feedback (current, silently broken without FA)

Code: [`KeyboardView.swift:875`](../LimeIME-iOS/LimeKeyboard/KeyboardView.swift), [`KeyboardViewController.swift:1727`](../LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift)

`UIImpactFeedbackGenerator.impactOccurred()` is called on every key press and candidate-bar interaction. Apple's documentation states:

> *"If the user doesn't grant open access to your keyboard, calls to the feedback generators are silently ignored."*

**Effect without Full Access:** haptic feedback silently stops. No crash, no error — the Taptic Engine is simply never engaged. There is currently no `hasFullAccess` guard before these calls.

Sound feedback (`UIDevice.current.playInputClick()` via the `UIInputViewAudioFeedback` protocol, [`KeyboardView.swift:141`](../LimeIME-iOS/LimeKeyboard/KeyboardView.swift)) is **not** gated by Full Access and works correctly either way.

### 2. Voice input — mic key (planned, not yet shipped)

All four sub-capabilities needed for the mic key require Full Access:

| Sub-capability | Gated? |
| --- | --- |
| Microphone hardware access from an extension | ✅ |
| `NSMicrophoneUsageDescription` prompt in an extension | ✅ |
| `SFSpeechRecognizer` instantiation in an extension | ✅ |
| `NSSpeechRecognitionUsageDescription` prompt | ✅ |

Implementation must use **on-device recognition only** (`requiresOnDeviceRecognition = true`). Requires A12 Bionic or newer. Pre-A12 devices show the mic key as disabled. See roll-out notes in [IPAD_KEYBOARD.md](IPAD_KEYBOARD.md) for placement.

**Note — system mic bar:** All Face ID iPhones (iPhone X and later, iOS 11+) show a system action bar below the keyboard with a microphone on the right and globe on the left. This predates Dynamic Island and iOS 16 — the trigger is simply the absence of a physical home button. iPhone SE (all generations, home button) does not have this bar; its globe and mic appear inline in the keyboard bottom row. Tapping the system mic invokes Apple's own dictation in a separate OS process — LimeIME is not involved and its Full Access state is irrelevant. LimeIME's own mic key (this section) exists because Apple's system dictation disappears when a 3rd-party keyboard is active on devices without Dynamic Island's dedicated mic hardware path.

Required `Info.plist` additions when shipping (extension target):

```xml
<key>NSMicrophoneUsageDescription</key>
<string>萊姆輸入法需要麥克風以提供離線語音輸入；錄音不會離開您的裝置。</string>
<key>NSSpeechRecognitionUsageDescription</key>
<string>萊姆輸入法以裝置上的語音辨識將語音轉為文字；不會傳送音訊或文字至雲端。</string>
```

---

## Everything else works without Full Access

The common misconception is that the App Group requires Full Access — it does not. App Group access (`FileManager.containerURL(forSecurityApplicationGroupIdentifier:)`) and shared `UserDefaults(suiteName:)` are gated only by the App Group entitlement (`group.org.limeime`), which both LimeKeyboard and LimeSettings already declare.

Empirically verified with Full Access **off**:

- Chinese IM input (all IMs: phonetic, array, CJ, dayi, ET, Hsu, EZ, WB) — works
- `lime.db` opens, candidates produced normally
- IM selection, theme, key/font size, number row pref — all read from shared `UserDefaults`
- Phrase learning and score writebacks to `lime.db` — work
- Heartbeat to LimeSettings (`keyboard_has_full_access`, `keyboard_last_seen_at`) — works

---

## LimeSettings orange banner

The Setup tab shows an orange ⚠ banner when `keyboard_has_full_access == false`. This is now justified by two reasons:

1. **Today:** haptic feedback silently stops without Full Access.
2. **When mic key ships:** voice input requires Full Access.

Banner copy should reflect the haptic + voice dependency, not imply the keyboard is broken for IM input.
