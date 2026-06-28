# LimeIME iOS — Setup Tab Fix Plan

**Last updated:** 2026-04-20
**Scope:** Rewrite the "啟用狀態" (Setup) section in the Settings app so status detection is honest and the "go to system settings" buttons land on a page that actually exists.

---

## Context

The first tab of the iOS Settings app (`SetupTabView`) is supposed to guide the user through activating the keyboard:
1. Show a banner: *not enabled* / *enabled without Full Access* / *fully enabled*.
2. Offer two buttons: **前往系統設定** (add keyboard) and **前往鍵盤設定** (open LimeIME's keyboard page to enable Full Access).

Both the status banner and the buttons are broken today:

### Bug 1 — Status banner is a one-way latch

[LimeKeyboard/KeyboardViewController.swift:152](LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift#L152) writes `keyboard_extension_loaded = true` to the App Group on every `viewDidLoad`, but nothing ever writes `false`. Same pattern for `keyboard_has_full_access` at [line 174](LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift#L174) — it records the *current* value of `hasFullAccess`, but only when the keyboard actually loads.

Consequence: once the user has typed with LimeIME once, the banner stays green forever. If they later remove the keyboard from Settings, toggle Full Access off, or reinstall the app after a dev build, the Setup tab still reports "✅ LimeIME 鍵盤已啟用".

### Bug 2 — `App-Prefs:` deep links do not work

[LimeSettings/Views/SetupTabView.swift:82-95](LimeIME-iOS/LimeSettings/Views/SetupTabView.swift#L82-L95) uses:

```
App-Prefs:root=General&path=Keyboard
App-Prefs:root=General&path=Keyboard/org.limeime.keyboard
```

These are **private URL schemes**. Apple has progressively disabled them since iOS 11; on iOS 14+ they either no-op, open the Settings app root, or get the app rejected in App Store review. The only supported way to open Settings from an app is `UIApplication.openSettingsURLString`, which opens **the calling app's own Settings page** — not arbitrary deep links.

So "前往鍵盤設定" currently either does nothing or lands the user on the Settings root with no indication of where to go next.

---

## Fix Strategy

### Part A — Make status detection honest

We cannot query iOS for "is my keyboard enabled" or "does it have Full Access" from the container app; there is no public API. What we *can* do:

1. **Treat the App Group flags as a heartbeat, not a latch.** The Settings app clears `keyboard_extension_loaded` to `false` every time it becomes active; the keyboard re-asserts `true` the next time it loads. If the user hasn't brought the keyboard up since the Settings app was foregrounded, we fall back to "unknown."
2. **Record a timestamp with each heartbeat.** `keyboard_last_seen_at` lets us show "最後偵測：剛剛 / 5 分鐘前 / 從未" so the user understands the status is a lagging indicator.
3. **Tri-state banner** instead of a binary green/red:
   - **未偵測到** (gray, informational) — user has not opened the keyboard since this Settings session.
   - **尚未啟用** (red) — heartbeat is fresh (<5 s ago) but we haven't seen it since the user last resumed Settings. *Deprecated by #1 — keep simple two states: unknown vs. last-known.*
   - **已啟用（最後偵測：X）** (green if Full Access, orange if not).
4. **Inline "測試鍵盤" field** — a lightweight `TextField` below the banner with placeholder *"在這裡輸入以啟用鍵盤偵測"*. When the user long-presses the 🌐 globe and selects LimeIME, the keyboard loads → writes the heartbeat → banner flips to green on the next `scenePhase == .active` (or via a short polling timer while the field is focused). This converts the "unknown" state into a concrete action the user can take without leaving the app.

### Part B — Fix the navigation buttons

1. **Remove both `App-Prefs:` URLs.** They are unreliable and App-Store-risky.
2. **Single button: 「開啟 LimeIME 設定」** using `UIApplication.openSettingsURLString`. This is the one deep link Apple guarantees. It lands on the LimeIME row in the Settings app, where the user can tap **鍵盤** to reach the per-app keyboard screen (and enable Full Access there).
3. **Update the step-by-step text** to reflect the real navigation path the user will see:
   - Step 1: *設定 → 一般 → 鍵盤 → 鍵盤 → 新增鍵盤 → LimeIME*
   - Step 2: *設定 → LimeIME → 鍵盤 → 允許完整取用* (reachable via the single button above)
4. **Keep the "測試鍵盤" field from Part A** as the primary confirmation mechanism — it is more reliable than any deep link.

---

## Files to Modify

| File | Change |
|------|--------|
| [LimeIME-iOS/LimeSettings/Views/SetupTabView.swift](LimeIME-iOS/LimeSettings/Views/SetupTabView.swift) | Rewrite `statusBanner`, replace `openKeyboardSettings`/`openLimeKeyboardSettings` with a single `openAppSettings` using `UIApplication.openSettingsURLString`, add `keyboardProbeField`, change `checkStatus` to read timestamp + evaluate freshness relative to the scenePhase-active marker. |
| [LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift](LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift) | Add `keyboard_last_seen_at = Date().timeIntervalSince1970` next to the existing `keyboard_extension_loaded` write at line 152, and next to the `keyboard_has_full_access` write at line 174. Also write `keyboard_has_full_access` (with current value, can be `false`) in `viewDidLoad` — not only `viewWillAppear` — so revoked Full Access is captured on the first load. |
| [LimeIME-iOS/LimeSettings/LimeSettingsView.swift](LimeIME-iOS/LimeSettings/LimeSettingsView.swift) *(read-only check)* | Confirm the Setup tab host view forwards `scenePhase`. If not, SetupTabView's `@Environment(\.scenePhase)` already handles it — no change needed. |

No new files. No changes to App Group entitlements, `project.yml`, or the keyboard extension build settings.

---

## Reused Existing Code

- `UserDefaults(suiteName: "group.org.limeime")` — already used in both the keyboard and the Settings app; no new infrastructure.
- `@Environment(\.scenePhase)` — already observed in `SetupTabView.body` at line 54; reuse to drive the "clear on foreground" behavior.
- `Label(_:systemImage:)` styling — already established in `statusBanner`; extend rather than replace.

---

## Verification

1. **Clean install, keyboard never added**
   - Expected: banner reads *"未偵測到 LimeIME 鍵盤 — 請先依下列步驟新增"*.
   - Test field: typing in the probe field with the system keyboard does nothing.
2. **Keyboard added, Full Access off**
   - Long-press globe → LimeIME, type one character into probe field.
   - Expected: banner flips to orange *"鍵盤已啟用，但尚未允許完整取用（最後偵測：剛剛）"*.
3. **Full Access granted**
   - Expected: banner flips to green *"LimeIME 鍵盤已啟用（最後偵測：剛剛）"*.
4. **User disables keyboard in Settings**
   - Return to the app → pull to refresh / switch tabs and back → heartbeat is stale, banner returns to *"未偵測到"*.
5. **Full Access revoked**
   - Same flow as #4; after re-adding LimeIME to the probe field, banner shows the orange "尚未允許完整取用" state because `viewDidLoad` now writes the current `hasFullAccess` value (bug fix in KeyboardViewController).
6. **Button navigation**
   - Tap 「開啟 LimeIME 設定」 → lands on the LimeIME page in the system Settings app. Confirm on a real device running iOS 17 and iOS 18 (simulator behavior for `openSettingsURLString` is reliable, but the per-app Keyboards sub-page only renders on device).
7. **Build + unit tests**
   - `xcodebuild -project LimeIME-iOS/LimeIME.xcodeproj -scheme LimeIME -destination "platform=iOS Simulator,name=iPhone 16" build test` — no new unit tests required; SetupTabView logic is small enough to verify by inspection + manual run-through above.
