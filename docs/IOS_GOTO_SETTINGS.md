# iOS "前往設定" Button — Deep-Link Reliability

Scope: the Setup tab CTA that sends the user to LimeIME's page in the Settings app to enable the keyboard and (optionally) Full Access.

## Bottom line

The app already implements the best review-safe technique: `UIApplication.openSettingsURLString` plus a valid `Settings.bundle` in the app target. The residual flakiness is an acknowledged iOS bug with no public fix. The only deeper deep link (`prefs:root=General&path=Keyboard`) is an App Review violation when used from an app, so it is off the table given that passing review is a hard requirement. Remaining work is one cheap URL variant plus UX that tolerates a bad landing.

## Current implementation

- Button: `SetupTabView.swift:577-579` — opens `UIApplication.openSettingsURLString` (`app-settings:`).
- Workaround already in place: `LimeSettings/Settings.bundle` with a valid `Root.plist` (footer text + `lime_show_setup_hint` toggle), bundled in the LimeIME app target Resources. Without a Settings.bundle the deep link randomly lands on the Settings root; with one it is supposed to land on the app page consistently (KeyboardKit-documented fix).
- Manual-path fallback hint already shown while not fully enabled: `SetupTabView.swift:231` ("設定 > Apps > 萊姆輸入法 > Keyboards").
- Success feedback loop already exists: the 1-second poll + enabled-detection (see IOS_FULL_ACCESS_DETECT.md) flips the banner green when the user succeeds.

## Why it is still unreliable

1. **iOS state bug.** If the Settings app is suspended on another pane, `open(url)` may simply foreground it where it was instead of navigating. Persists through iOS 18 per Apple developer forums. No public API fixes this.
2. **Two-tap ceiling.** A perfect landing reaches Settings > Apps > 萊姆輸入法 only. The user must still tap "Keyboards" and flip the toggles. There is no public deep link to the Keyboards subpage.
3. **`App-Prefs` / `prefs` schemes are dead ends.** Undocumented `App-Prefs` paths stopped working on iOS 18. Apple QA1924 sanctions `prefs:root=General&path=Keyboard` ONLY from inside a keyboard extension; from the app it is an explicit review violation. Modern keyboard extensions cannot open URLs at all, so the QA1924 route is dead from both ends.

## Recommended changes

1. **Bundle-ID-suffixed URL first, plain URL as fallback.**
   `"\(UIApplication.openSettingsURLString)/\(Bundle.main.bundleIdentifier!)"` — reported on Apple forums to improve landing on iOS 18 for some apps. Still the documented `app-settings:` scheme, so zero review risk. Cannot make things worse.
2. **Design for the miss.** After the user taps 前往設定, elevate the manual-path hint from passive footnote to prominent guidance, phrased for both landings:
   - Landed on the 萊姆輸入法 page → tap "Keyboards" (鍵盤).
   - Landed on the Settings main list → Apps > 萊姆輸入法 > Keyboards.
   The auto-detecting banner closes the loop when they succeed; no "did it work?" prompt needed.
3. **Do NOT use `App-Prefs:` / `prefs:` from the app.** Direct rejection vector; conflicts with the hard requirement to pass review.
4. **Settings.bundle footer copy.** Users land on that page, so its footer is prime guidance real estate. Current text is correct but should soften the 完整取用 wording to match the Full-Access-optional positioning (Full Access is a feature unlock, not a requirement — see IOS_FULL_ACCESS_DETECT.md).

## Test notes

- Device, Settings app killed: tap 前往設定 → should land on the 萊姆輸入法 app page (Settings.bundle registered).
- Device, Settings app suspended on another pane (e.g. Wi-Fi): tap 前往設定 → may land on the old pane. Verify the in-app guidance covers this case; this failure is expected and unfixable.
- Fresh install: Settings may need a moment (or one relaunch) to register the Settings.bundle before the deep link behaves; do not judge reliability on the first seconds after install.
- Verify the bundle-ID-suffixed URL variant on the oldest supported iOS version before shipping (fall back to plain `openSettingsURLString` if `canOpenURL` fails or `open` reports failure).

## Sources

- Apple forums — iOS 18 open settings URLs: https://developer.apple.com/forums/thread/759900
- Apple QA1924 — Opening Keyboard Settings from a Keyboard Extension: https://developer.apple.com/library/archive/qa/qa1924/_index.html
- KeyboardKit — How to open your app's System Settings screen: https://keyboardkit.com/blog/2023/02/20/how-to-open-your-apps-system-settings-screen
- openSettingsURLString documentation: https://developer.apple.com/documentation/uikit/uiapplication/opensettingsurlstring
