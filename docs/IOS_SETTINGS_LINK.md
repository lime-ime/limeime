# iOS Settings Link Investigation

## Goal

The iOS setup tab should help users enable the LimeIME keyboard and Full Access with
as little friction as possible.

Ideal destination:

```text
Settings > Apps > 萊姆輸入法 > Keyboards
```

Accepted destination (current target):

```text
Settings > Apps > 萊姆輸入法
```

Research (2026-05) confirmed that no public, semi-public, or App-Store-safe API can
land users on the `Keyboards` sub-row in one tap. The realistic best supported
destination is the app's own page, which is exactly one tap away from `Keyboards`
and from `Allow Full Access`. This is acceptable.

## Current Decision

Keep the App Store-safe implementation, with the destination accepted as
`Settings > Apps > 萊姆輸入法`:

- The setup tab button is labeled `前往設定`.
- The button uses Apple's documented `UIApplication.openSettingsURLString`.
- The setup tab shows the manual path directly under the button.
- LimeIME ships a `Settings.bundle` so the app has a system Settings page.
- Private Settings URL schemes are not used in app code.

## Research Update (2026-05)

Parallel research across five facets (current `openSettingsURLString` behavior,
`Settings.bundle` prerequisites, new iOS 17/18/26 APIs, competitor onboarding,
private-scheme review reality) produced these load-bearing findings:

1. **No public API exists for the `Keyboards` sub-row.** `openSettingsURLString`
   opens `Settings > Apps > MyApp`; `openNotificationSettingsURLString` (iOS 15.4+)
   opens the Notifications sub-row; `openDefaultApplicationsSettingsURLString`
   (iOS 18.2+) opens Default Apps. There is no `openKeyboardSettingsURLString` or
   equivalent constant in iOS 17, 18, or 26. SwiftUI `SettingsLink` is macOS-only.
   App Intents cannot navigate Settings sub-panes.
2. **iOS 18 `Settings.bundle` regression (FB15267454) — broader than first thought.**
   Re-reading Apple Developer Forums thread #764519 carefully: the regression
   suppresses any bundle whose `PreferenceSpecifiers` array does not contain at
   least one specifier with a **`Key`** (a real persistable preference such as
   `PSToggleSwitchSpecifier`, `PSTextFieldSpecifier`, or `PSSliderSpecifier`).
   A bundle containing only `PSGroupSpecifier`, only `PSTitleValueSpecifier`,
   only `PSMultiValueSpecifier`, or only `PSRadioGroupSpecifier` is treated as
   empty and silently dropped. The app does not appear in `Settings > Apps` at
   all, and `openSettingsURLString` falls back to the root page.
   Both the original `Root.plist` (group + `PSTitleValueSpecifier`) and the
   first-fix `Root.plist` (group only) hit this suppression.
3. **Settings-database registration prerequisite.** Even with a valid bundle, an
   app may not appear in Settings until it has triggered at least one runtime
   permission prompt. Until then, `openSettingsURLString` opens the root page.
4. **Private schemes are still rejected and now also broken.** iOS 18 broke
   `App-Prefs:`/`prefs:` sub-path navigation. Apple App Review Guideline 2.5.1
   continues to reject these schemes. The `error 115` results observed earlier
   are consistent with these schemes failing.
5. **Competitor "deep-link to Keyboards" claims are unverified.** Common claims
   that Gboard/SwiftKey/Grammarly deep-link past `Settings > Apps > <app>` rely
   on YouTube walkthroughs and pre-iOS-18 blog posts. The actual support docs
   only describe the manual path.

References:

- [openSettingsURLString — Apple Developer Documentation](https://developer.apple.com/documentation/uikit/uiapplication/opensettingsurlstring)
- [openNotificationSettingsURLString (iOS 15.4+)](https://developer.apple.com/documentation/uikit/uiapplication/opennotificationsettingsurlstring)
- [openDefaultApplicationsSettingsURLString (iOS 18.2+)](https://developer.apple.com/documentation/uikit/uiapplication/opendefaultapplicationssettingsurlstring)
- [Building a Settings bundle for your app](https://developer.apple.com/documentation/foundation/building-a-settings-bundle-for-your-app)
- [iOS 18 Settings.bundle regression — Apple Developer Forums #764519](https://developer.apple.com/forums/thread/764519)
- [openSettingsURLString lands on root — Apple Developer Forums #111030](https://developer.apple.com/forums/thread/111030)
- [iOS 18 broke App-Prefs/prefs schemes — Apple Developer Forums #759900](https://developer.apple.com/forums/thread/759900)
- [Guideline 2.5.1 rejection — Apple Developer Forums #100471](https://developer.apple.com/forums/thread/100471)
- [QA1924 (archived/retired) — Opening Keyboard Settings](https://developer.apple.com/library/archive/qa/qa1924/_index.html)

## Apple Documentation Checked

Apple documents `UIApplication.openSettingsURLString` as the supported way to open the
calling app's Settings screen. Apple also documents `Settings.bundle` as the way for an
app to provide custom settings in the Settings app.

Important limitation: Apple does not document a public URL for opening a third-party
keyboard extension's `Keyboards` / Full Access page directly.

## What Was Tried

Test device used for the valid iOS checks:

- Simulator: iPhone 17 Pro Max
- UDID: `58BCFA46-0EE8-4631-B2A0-6331EF124A75`
- Runtime observed during the investigation: iOS 26.5

### Public API

Tried:

```swift
UIApplication.openSettingsURLString
```

Result:

- Build passed.
- Tapping `前往設定` opened the Settings app.
- On this simulator, it landed on the Settings top page, not the LimeIME app settings
  page and not the keyboard settings page.

### Settings Bundle

Added:

- `LimeIME-iOS/LimeSettings/Settings.bundle/Root.plist`
- Included `Settings.bundle` in the LimeIME app resources.
- Originally added a `PSTitleValueSpecifier` row so the bundle was not just a
  group/footer.

Verified:

- The built app contains `Settings.bundle/Root.plist`.
- The installed app container contains `Settings.bundle/Root.plist`.
- `Root.plist` passes `plutil -lint`.

Result (before fix):

- Fresh uninstall/install did not change the `UIApplication.openSettingsURLString`
  behavior.
- Simulator reboot after install did not change the behavior.
- The public API still landed at Settings top page.

Root cause hypothesis (revised 2026-05):

- The iOS 18+ `Settings.bundle` suppression regression (FB15267454,
  Apple Developer Forums #764519) drops any bundle whose `PreferenceSpecifiers`
  array does not contain at least one specifier with a **`Key`** (i.e., a real
  persistable preference such as `PSToggleSwitchSpecifier`).
- Both the original `Root.plist` (group + `PSTitleValueSpecifier`) and the
  first-attempt fix (group only) hit this suppression. The first fix only
  removed one trigger; it did not add the required real-preference row.
- When Settings.app suppresses the bundle, the app does not appear in
  `Settings > Apps`, and `openSettingsURLString` falls back to the root page.

Fix attempt 1 (2026-05, did NOT work):

- Removed the `PSTitleValueSpecifier` row from `Root.plist`, leaving only the
  `PSGroupSpecifier` with `FooterText`.
- `plutil -lint` passed.
- Runtime re-test: `前往設定` still landed on the Settings top page.
- Conclusion: group-only is also suppressed by the same regression.

Fix attempt 2 (2026-05, pending verification):

- Added a `PSToggleSwitchSpecifier` with `Key=lime_show_setup_hint`,
  `Title=顯示安裝指引`, `DefaultValue=true` after the `PSGroupSpecifier`.
- This satisfies the "must contain at least one specifier with a Key"
  requirement reported in FB15267454.
- `plutil -lint` passes.
- Runtime verification pending: rebuild → uninstall on simulator → reinstall →
  reboot simulator → tap `前往設定` → check destination.
- The toggle itself is harmless if the app does not read
  `lime_show_setup_hint` — the in-app setup hint can remain unconditional.
  Reading and honoring the preference is a separate optional follow-up.

### Private URL Schemes

These were tested as probes only and should not be treated as approved app behavior:

```text
App-Prefs:root=General&path=Keyboard/KEYBOARDS
prefs:root=General&path=Keyboard/KEYBOARDS
App-Prefs:org.limeime
App-prefs:org.limeime
App-Prefs:org.limeime.keyboard
app-settings:org.limeime
app-settings:/org.limeime
app-settings://org.limeime
settings-navigation://com.apple.Settings.Apps/org.limeime
settings-navigation://com.apple.Settings.General/Keyboard/KEYBOARDS
```

Observed results:

- `prefs:` URLs failed with `LSApplicationWorkspaceErrorDomain error 115`.
- `app-settings:` URL variants failed with `LSApplicationWorkspaceErrorDomain error 115`.
- `settings-navigation://...` URL variants failed with
  `LSApplicationWorkspaceErrorDomain error 115`.
- `App-Prefs:` variants were accepted by `simctl openurl` in some cases, but they either
  landed at Settings top page or did not reach the required keyboard settings page.

Conclusion:

- No private URL tested produced a reliable one-tap route to LimeIME's keyboard settings.
- Private Settings schemes remain unsuitable for app code because they are undocumented,
  unstable, and carry App Store review risk.

### Settings Search Manifest

The iOS 26.5 simulator runtime contains a Settings search manifest for keyboard
settings:

```text
System/Library/PreferenceManifestsInternal/PreferencesManifests.bundle/
SettingsSearchManifest-com.apple.keyboard.settings.plist
```

It lists the keyboard settings search URL as:

```text
prefs:root=General&path=Keyboard/KEYBOARDS
```

However, opening that URL externally failed or did not navigate to the expected page.
This suggests the Settings app may use internal routes that are not available to
third-party apps or external URL opening.

## Current Implementation State

Files involved:

- `LimeIME-iOS/LimeSettings/Views/SetupTabView.swift`
  - Button label: `前往設定`
  - Uses `UIApplication.openSettingsURLString`
  - Shows the manual path under the button
- `LimeIME-iOS/LimeSettings/Settings.bundle/Root.plist`
  - Minimal Settings bundle so iOS has an app settings page for LimeIME
  - Contains only `PSGroupSpecifier` + `FooterText` (iOS 18-safe; avoids the
    `PSTitleValueSpecifier` suppression regression)
- `LimeIME-iOS/LimeSettings/Info.plist`
  - Removed stale `App-Prefs` query scheme
- `LimeIME-iOS/LimeIME.xcodeproj/project.pbxproj`
  - Includes `Settings.bundle` in app resources
- `LimeIME-iOS/LimeTests/KeyboardViewControllerTest.swift`
  - Regression checks for the supported setup-link contract

## Verification Already Done

Build and static checks:

- `xcodebuild` for LimeIME on the iPhone 17 Pro Max simulator destination passed.
- `plutil -lint` passed for `Info.plist` and `Settings.bundle/Root.plist`.
- `git diff --check` passed for the touched iOS setup/settings files.

Runtime checks:

- Fresh uninstall/install on the iPhone 17 Pro Max simulator.
- Simulator reboot after install.
- Confirmed installed app container includes `Settings.bundle/Root.plist`.
- Confirmed the setup screen shows `前往設定` and the visible fallback path.
- After `PSTitleValueSpecifier` removal: re-tested on the iPhone 17 Pro Max
  simulator. `前往設定` still lands on the Settings top page, not on
  `Settings > Apps > 萊姆輸入法`. The single-specifier-removal hypothesis is
  insufficient on its own. See `## Next Hypotheses` below.

Known caveat:

- During one manual Settings-search attempt, the automation focus briefly switched to a
  different Simulator window. After that, checks were restricted back to the exact
  iPhone 17 Pro Max UDID with `simctl`.

## Next Hypotheses

Ordered by likelihood, based on the deep-dive research (2026-05). Apply in
order; stop when `前往設定` lands on `Settings > Apps > 萊姆輸入法`.

1. **Add a real persistable preference to `Root.plist`.** *(applied — fix
   attempt 2 above)* Required by iOS 18+ to register the bundle. At least one
   `PSToggleSwitchSpecifier` / `PSTextFieldSpecifier` / `PSSliderSpecifier`
   with a `Key` must be present.
2. **Clear the Simulator Settings cache.** Plain uninstall/reinstall sometimes
   does not force Settings.app to re-scan; the Settings.bundle registration is
   cached. Two options, least to most destructive:
   - Kill the Settings.app process (`xcrun simctl spawn <udid> killall -9 Preferences`)
     and reopen.
   - Boot-erase the LimeIME app data only — uninstall the app, then run
     `xcrun simctl shutdown <udid>`, `xcrun simctl boot <udid>`, then reinstall.
   - Full erase (`xcrun simctl erase <udid>`) — wipes the entire simulator
     including other apps; only use if the above two fail.
3. **Add `en.lproj/Root.strings`** (can be empty). Belt-and-suspenders. The
   bundle currently references `StringsTable=Root` but no strings file exists.
   Apple docs say this falls back gracefully, but matching the Xcode template
   removes one variable.
4. **Try on a real device.** Simulator routing for Settings deep links is
   documented as unreliable independently of the bundle contents. If steps 1–3
   work on a real iPhone/iPad but not the iPhone 17 Pro Max simulator on
   iOS 26.5, the residual failure is a simulator bug — log it and move on.
5. **Request one runtime permission** (e.g. notifications) on first launch
   as a separate registration path. Independent of `Settings.bundle`. Only do
   this if steps 1–4 are insufficient, since it adds a user-visible prompt the
   app does not otherwise need.
6. **File a Feedback Assistant report** referencing FB15267454 if the iOS 26
   behavior diverges from the iOS 18 thread's confirmed fix.

## Forward Plan

Move forward with the supported UX targeted at `Settings > Apps > 萊姆輸入法`
(verification pending after fix attempt 2 — see `## Next Hypotheses` above):

1. Keep the current App Store-safe implementation.
2. Keep `Settings.bundle` in the app, with the iOS 18-safe shape:
   `PSGroupSpecifier` + `FooterText` **plus at least one specifier with a
   `Key`** (currently a `PSToggleSwitchSpecifier`). Avoid the suppression
   triggers: `PSTitleValueSpecifier`, `PSMultiValueSpecifier`,
   `PSRadioGroupSpecifier`.
3. Keep the visible manual path under the setup button.
4. Do not add private Settings URL schemes to production code.
5. Walk through `## Next Hypotheses` until the runtime destination matches.

## Later Investigation

Revisit only if one of these becomes true:

- Apple documents a public API for opening the `Keyboards` sub-row of an app's
  Settings page (file Feedback Assistant request; precedent: `openNotificationSettingsURLString`).
- A newer iOS release ships an `openKeyboardSettingsURLString` or equivalent.
- A reproducible, App Store-safe one-tap route to `Keyboards` is found on real
  devices, not only simulator.
- A maintainer explicitly accepts private URL scheme risk for a non-App-Store build.

Future verification should include:

- A real iPhone/iPad device, not only simulator (Simulator routing is documented
  as unreliable for Settings deep links).
- Current public iOS release.
- Fresh install, upgrade install, and already-enabled-keyboard states.
- English and Traditional Chinese Settings language if the route depends on Settings
  indexing/localization.
