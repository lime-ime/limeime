# iOS Keyboard Enabled / Full Access Detection

Scope: how LimeSettings detects (1) "LimeIME is enabled in system Settings" and (2) "Full Access is granted", and what is actually knowable on a real device.

## Bottom line

Enabled detection is reliable and already correctly implemented with two app-side methods that need no help from the keyboard. Full Access detection is fundamentally different: only the keyboard process knows `hasFullAccess`, and it can only report the value through a keyboard→App-Group write — which is exactly the write that Full Access OFF blocks. Therefore the app can confirm Full Access is ON, but can never confirm it is OFF. Detection must be modeled as a tri-state (confirmed-on / stale / unknown), never a boolean.

## Channel facts that bound the problem

| Actor → Target | Full Access OFF | Full Access ON |
|---|---|---|
| App → App Group | read/write | read/write |
| Keyboard → App Group | read-only | read/write |
| Keyboard → its own container | read/write | read/write |
| App → keyboard's own container | never | never |

- The keyboard's heartbeat writes (`keyboard_extension_loaded`, `keyboard_has_full_access`, `keyboard_last_seen_at` — `KeyboardViewController.swift:297-299`, `:328-330`) are keyboard→App-Group shared-defaults writes. With Full Access OFF they are silently dropped on device.
- Simulator does not enforce this, so heartbeat-based detection appears to work in Simulator and fails on device.
- Shared UserDefaults reads by the app are always fine (the app is never restricted).

## Enabled detection — current implementation (KEEP)

`SetupTabView.swift` `refreshStatus()` (`:496-526`), union of two methods:

- Method A — `UITextInputMode.activeInputModes`, identifying LimeIME via the private `identifier` KVC key (bundle-ID prefix `org.limeime`). Guarded with `responds(to:)` so removal of the private key degrades to `false` instead of crashing. Same pattern used by Gboard/SwiftKey. Public `primaryLanguage` is not usable alone ("zh-Hant" collides with Apple's built-in keyboards).
- Method B — the system `AppleKeyboards` array (list of enabled keyboard bundle IDs), read from `UserDefaults.standard` and the `.GlobalPreferences` domain. Added because Method A's private key stopped resolving on newer iOS (iOS 26).

`keyboardEnabled = viaInputModes || viaAppleKeyboards`, refreshed by a 1-second poll while the tab is visible (`:552-557`).

This requires no keyboard cooperation, works in every Full Access state, and needs no change.

## Full Access detection — current implementation (BROKEN on device)

Current logic (`SetupTabView.swift:528-544`):

- Read `keyboard_has_full_access` from the App Group suite.
- Missing key → assume granted (avoid false-orange before the extension ever runs).
- Explicit `false` → show "no Full Access" state.

Why this fails on a real device:

1. The extension's `false` write happens exactly when Full Access is OFF — so it is dropped. The explicit-false branch is unreachable on device; it only fires in Simulator or from a stale warm process.
2. Stale-true: Full Access ON writes `true`; if the user later revokes Full Access, the corrective `false` is dropped. The app shows "granted" forever based on the old `true`.
3. Net effect: the banner can never truthfully display "Full Access is off" from this signal alone.

### Probe field (KEEP, with its caveat)

`SetupTabView.swift:158-162`, `:570-575`: an invisible text field is focused so iOS loads the active keyboard; if that is LimeIME, `viewWillAppear` fires and (with Full Access ON) rewrites fresh heartbeat values, picked up by the 1-second poll.

Caveat: focusing the probe loads the *currently selected* keyboard. If the user's active keyboard is Apple's, LimeIME never runs and no fresh value arrives. Therefore "no report after probing" does NOT imply Full Access is off.

## Corrected model — tri-state with freshness

Only three states are knowable by the app:

| State | Evidence | Display |
|---|---|---|
| Confirmed ON | `keyboard_has_full_access == true` AND `keyboard_last_seen_at` is recent (fresh write since probe / within threshold) | "完整取用權限：已開啟（已學習字詞會納入備份）" |
| Stale | `true` but `keyboard_last_seen_at` old | treat as Unknown; optionally trigger probe |
| Unknown | key missing, or no fresh write | neutral copy — Full Access is optional; never claim broken |

Rules:

- Never render "Full Access is OFF" as an error. OFF and never-ran are indistinguishable on device, and the keyboard is fully functional without Full Access (App Review 4.4.1 requires this anyway).
- Use `keyboard_last_seen_at` (already written alongside the flag) as the freshness signal. A `true` without freshness is not proof of the current state.
- The probe field is a best-effort freshness trigger, not an oracle.
- Banner copy positions Full Access as a feature unlock ("include learned words in backup / sync installs immediately"), not a requirement.

## Required implementation changes

- `SetupTabView.refreshStatus()`: replace the boolean `fullAccessEnabled` with the tri-state above, keyed on value + `keyboard_last_seen_at` freshness.
- Remove the "missing → assume granted" green state; missing is Unknown with neutral copy.
- Keep enabled detection (Methods A+B) and the 1-second poll unchanged.
- Keep the probe field; treat its silence as no-information.
- Keyboard side: keep writing all three heartbeat keys on `viewDidLoad`/`viewWillAppear` (harmless when dropped, definitive when Full Access is ON). Additionally mirror them to the keyboard's own `UserDefaults.standard` so the extension can self-diagnose (`keyboard_db_last_error` has the same App-Group-write weakness — `KeyboardViewController.swift:715`).

## Test matrix

- Device, fresh install, keyboard not enabled: red/neutral "not enabled" (Methods A/B), no Full Access claim.
- Device, enabled, Full Access never granted, LimeIME active, tap probe: state stays Unknown (write dropped) — banner neutral, no "broken" claim.
- Device, enabled, Full Access ON, LimeIME active, tap probe: Confirmed ON within ~2 s (poll picks up fresh heartbeat).
- Device, Full Access ON then revoked, extension killed: old `true` present but stale → Unknown, not green.
- Device, enabled but Apple keyboard is the active one: probe loads Apple keyboard; state remains Unknown; no false conclusions.
- Simulator: explicit `false` may arrive (loose sandbox) — display it as informational only; do not rely on it existing on device.
