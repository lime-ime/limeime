# Issue #124: Android root/reverse-lookup popups overlap bottom message input fields

## Current status

- Live issue: https://github.com/lime-ime/limeime/issues/124
- Status: **OPEN / fix delivered in v6.1.23, awaiting reporter verification** (reopened 2026-06-21)
- Classification: `bug` + `Usability`
- Assignee: `jrywu`
- Source: maintainer-created issue from a private email report. The public issue intentionally withholds the reporter email address.
- Public follow-up: `limeimetw` edited the retained v6.1.23 update comment at https://github.com/lime-ime/limeime/issues/124#issuecomment-4761898236 to use Google Play closed-test wording, then reopened the issue with https://github.com/lime-ime/limeime/issues/124#issuecomment-4761963945 so the reporter can confirm the result after updating.
- Implementation status: **Released in v6.1.23.** The Android popup lifetime/alignment and placement follow-up fixes are on `master` via commits `61cf87b65f03f69486e112bf1dc1383c9974a125` and `9fc84f97eaddfea5f550268e950695dadbb3fea5`.
- Reporter verification: pending. After the v6.1.23 Google Play retest request, the reporter uploaded two screen recordings in https://github.com/lime-ime/limeime/issues/124#issuecomment-4765529644 and https://github.com/lime-ime/limeime/issues/124#issuecomment-4765655167 without explicitly stating the tested app version in the comments or sampled frames. `limeimetw` followed up in https://github.com/lime-ime/limeime/issues/124#issuecomment-4766516641 to ask whether the current temporary reverse-lookup display duration and placement are acceptable, using public wording that describes the expected disappearance as roughly five seconds, or whether the reporter prefers a shorter duration or further inward keyboard placement. Source/tests still pin the timed reverse-lookup lime-toast timeout to `1400` ms, so any reporter-observed longer display should be clarified against the exact tested build/path.

## Problem statement

The reporter says that when using LIME IME Array input inside LINE on Android, committing a candidate can trigger the reverse-lookup display and the grey floating reverse-lookup window appears near the LINE message input field. In the attached email screenshot described by the issue, that floating window covers the active message input area, interfering with reading and continuing to type. Reporter follow-up screenshots now also show the composing/root-key display during typing overlapping bottom-composer input regions, so the broader issue covers both Android floating popup paths.

Known public reproduction context:

- Host apps: LINE 26.8.0, plus reporter follow-up says other bottom-composer apps such as WeChat and Instagram are also affected.
- Platform: Android 16
- Device: Asus Zenfone 12 Ultra
- LIME IME version: 6.1.22-2026
- IM/table: LIME IME Array
- Feature: reverse lookup notification after candidate commit, and composing/root-key display during typing.
- Public screenshots in https://github.com/lime-ime/limeime/issues/124#issuecomment-4757262176 show the grey reverse-lookup popup overlapping bottom chat input/composer regions rather than staying inside the IME/candidate area.
- Public screenshots in https://github.com/lime-ime/limeime/issues/124#issuecomment-4757356156 answer the maintainer follow-up by showing the composing/root-key display also appears in the same bottom-composer area in LINE/Instagram-style screens.
- Public recordings in https://github.com/lime-ime/limeime/issues/124#issuecomment-4765529644 and https://github.com/lime-ime/limeime/issues/124#issuecomment-4765655167 show current interaction examples after the retest request. Sampled frames show root/composing and reverse-lookup hints around the keyboard/candidate row, and the second recording's post-10-second sampled frames no longer show the grey hint; the recording comments/sampled frames do not visibly confirm the tested LIME version.

## Source evidence inspected

Android:

- `LimeStudio/app/src/main/java/net/toload/main/hd/LIMEService.java`
  - `commitTyped(...)` calls `SearchSrv.getCodeListStringFromWord(committedCandidate.getWord())` after a candidate commit when reverse lookup is enabled.
  - Before the first #124 fix, `showReverseLookup(CharSequence)` forwarded the result through `showPersistentLimeToast(...)`, which called `CandidateView.showLimeToastUntilNextKey(...)`. The popup stayed visible until the next LIME key event.
  - Commit `61cf87b65f03f69486e112bf1dc1383c9974a125` routes reverse lookup through `showLimeToast(...)` again, restoring the existing short `LIME_TOAST_TIMEOUT_MS` timeout while still allowing the next key event to hide the popup early if it is still visible.
- `LimeStudio/app/src/main/java/net/toload/main/hd/candidate/CandidateView.java`
  - `doShowLimeToast(...)` creates a custom `PopupWindow` (`mLimeToastPopup`) with `setClippingEnabled(false)` and positions it with `showAtLocation(this, Gravity.NO_GRAVITY, x, y)`.
  - Before the first #124 fix, the reverse-lookup popup used `offsetInWindow[1] - toastHeight`, where `toastHeight` included lime-toast padding. The separate key-name / composing popup uses the composing text height. That made the reverse-lookup box sit higher than the key-name display.
  - `doSetComposing(...)` / `doUpdateComposing(...)` can also create `mComposingTextPopup` when no embedded composing view is available, using `mPopupComposingY = offsetInWindow[1] - popupHeight` and `showAtLocation(this, Gravity.NO_GRAVITY, mPopupComposingX, mPopupComposingY)`.
  - Commit `61cf87b65f03f69486e112bf1dc1383c9974a125` first narrowed the reverse-lookup timeout/alignment regression. Follow-up commit `9fc84f97eaddfea5f550268e950695dadbb3fea5` superseded the unmerged PR #126 path and adjusted the composing/key-name and reverse-lookup popup placement together, with additional test fixes.
  - Existing embedded-composing alignment still wins when that view is visible; the expanded candidate popup uses a separate path.
- `LimeStudio/app/src/main/java/net/toload/main/hd/candidate/CandidateInInputViewContainer.java`
  - The normal in-keyboard candidate row initializes `R.id.candidatesView` but does not wire an `embeddedComposing` view, so composing/root-key display can fall back to `mComposingTextPopup` in the active input-view path.
- `LimeStudio/app/src/main/java/net/toload/main/hd/candidate/CandidateViewContainer.java` and `LimeStudio/app/src/main/res/layout/candidates.xml`
  - The expanded/floating candidate container has `R.id.embeddedComposing` and calls `setEmbeddedComposingView(...)`, so popup behavior can differ between the normal in-keyboard row and expanded/floating candidate container.
- `LimeStudio/app/src/androidTest/java/net/toload/main/hd/LIMEServiceTest.java`
  - `reverseLookupUsesTimedLimeToast()` verifies reverse lookup uses the timed lime-toast path and does not reuse composing text or the persistent-until-next-key path.
  - `nextKeyClearsLimeToastIfStillVisible()` keeps the early-hide-on-next-key behavior for a toast that has not timed out yet.
- `LimeStudio/app/src/androidTest/java/net/toload/main/hd/candidate/CandidateViewTest.java`
  - Adds coverage for the lime-toast Y helper aligning to composing/key-name popup height.
  - Adds coverage that the short lime-toast timeout remains `1400` ms.

Analogous iOS path checked:

- `LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift`
  - iOS also performs reverse lookup after a candidate commit, but `showLimeToast(_:)` writes the lookup text into `candidateBar.composingText` and `expandedComposingLabel` inside the keyboard extension UI instead of creating a separate floating `PopupWindow` over the host app.
  - This makes the reported floating-window-over-LINE-input symptom Android-specific by implementation path. iOS reverse lookup can still have candidate-bar layout concerns, but it does not use Android's host-window `PopupWindow` placement path.

## Root cause

Android has two relevant floating-popup paths anchored around the candidate row: the reverse-lookup lime toast (`mLimeToastPopup`) and the composing/root-key popup (`mComposingTextPopup`) used when the active input-view candidate row lacks an embedded composing view. In LINE, WeChat, Instagram, and similar bottom-composer apps, the candidate row can sit immediately below or near the host app's message input field. Because both popup paths position themselves above the candidate view without checking the host window's visible input/editor area, either popup can extend into and cover the app's message input field.

The reverse-lookup lime-toast path also had two narrower regressions relative to the key-name / composing display path:

1. Duration regression: reverse lookup was changed from the normal short lime-toast behavior to `showLimeToastUntilNextKey(...)`. In chat apps where the user pauses after committing a character, the grey popup can remain over the bottom composer indefinitely until the next LIME key press.
2. Vertical alignment drift: reverse lookup used the full measured toast height, including padding, for its above-candidate-row Y position. The key-name / composing popup does not use that padded height, so reverse lookup appeared higher than the established key-name display.

This is a geometry and lifetime bug in Android candidate-view popup handling, not a LINE-specific text-processing bug based on current evidence.

## Implemented changes in v6.1.23

The released #124 work was split across two GitHub-visible commits and should be read as a combined Android popup maintenance fix rather than as a single one-line geometry change:

1. **Reverse-lookup lifetime fix** (`61cf87b65f03f69486e112bf1dc1383c9974a125`):
   - Routed reverse lookup back through the timed lime-toast path instead of leaving the popup visible until the next key.
   - Restored the short `LIME_TOAST_TIMEOUT_MS` behavior while preserving early hide on the next LIME key if the toast is still visible.

2. **Reverse-lookup alignment fix** (`61cf87b65f03f69486e112bf1dc1383c9974a125`):
   - Reduced vertical drift between reverse-lookup toast and key-name/composing popup by aligning the reverse-lookup Y calculation with the composing/key-name popup height rather than relying only on the padded toast height.

3. **Popup placement / test follow-up** (`9fc84f97eaddfea5f550268e950695dadbb3fea5`):
   - Superseded the unmerged PR #126 path and adjusted the composing/key-name and reverse-lookup popup placement work together.
   - Included related Android test fixes and reported `connectedAndroidTest` as passing in the commit message.

4. **Remaining verification scope**:
   - Build and test checks support the source change, but bottom-composer behavior still needs real-device/user verification in LINE, WeChat, Instagram, or similar apps because the original symptom depends on host-app window geometry and device/display settings.
   - The v6.1.23 public comment therefore asks the reporter to confirm whether the popup no longer covers the input field, or at least whether it improved compared with 6.1.22.

## Follow-up questions for the reporter

The reporter provided Android version, device model, LINE version, LIME IME version, confirmed in https://github.com/lime-ime/limeime/issues/124#issuecomment-4757262176 that similar bottom-composer apps such as WeChat and Instagram are affected, and answered the maintainer's composing/root-key display question in https://github.com/lime-ime/limeime/issues/124#issuecomment-4757356156 with screenshots showing the composing/root-key popup also near the bottom composer.

Current follow-up is narrowed to reporter confirmation on the live v6.1.23 Google Play closed-test build:

- Whether the current temporary reverse-lookup display time and placement are acceptable. The public follow-up describes the expected disappearance as roughly five seconds, but source/tests still pin the timed reverse-lookup lime-toast timeout to `1400` ms, so any longer observed display should be clarified against the exact tested build/path.
- If not acceptable, whether the preferred direction is a shorter display duration or moving the hint farther inside the keyboard area.
- If the reporter reports continued overlap, ask them to state the tested LIME version/build and keep any additional evidence scoped to the exact app, key sequence, and display/keyboard/font-size settings.

Because this was reported by email and the issue was created by `limeimetw`, avoid posting duplicate public GitHub questions. A private email follow-up may be better if the maintainer needs more device/settings details and the reporter is not using GitHub.

## Verification plan / release verification

✓ **Build verification**:
  - `./gradlew assembleDebug` — **PASSED**, no compilation errors.
  - The affected composing/key-name and reverse-lookup popup code paths compile after the #124 fixes.

✓ **Code review**:
  - Reverse-lookup toast path: timed toast behavior is restored and placement/alignment is adjusted relative to the composing/key-name popup path.
  - Composing/key-name and reverse-lookup popup paths were reviewed together after the follow-up placement commit.
  - Embedded composing path remains a separate path.

**Pending manual Android verification** (requires device testing):
  - LINE, WeChat, Instagram, or another chat-style app with bottom-aligned composer, using Array IM.
  - Composing/root-key hints visible and readable while typing (should match pre-fix behavior).
  - Reverse lookup appears after candidate commit and displays above candidate bar, aligned with key-name popup.
  - Reverse lookup times out per `LIME_TOAST_TIMEOUT_MS` and hides early on next LIME key press.
  - Candidate bar, expanded popup, and controls remain usable and responsive.
  - Composing/key-name and reverse-lookup displays remain aligned consistently enough for bottom-composer testing.

## Backlog / release follow-up

- Android v6.1.23 contains the targeted #124 popup-position/alignment fix. The reporter is on the Google Play closed-test channel, so the public retest request correctly asks them to update from Google Play. The retained GitHub Release/sideload APK was later replaced as `LIMEHD2026-6.1.23.apk` for the old GitHub package family (`net.toload.main.hd2026`); verified GitHub Contents blob SHA `7315b2d88bf13327d2f16343ddd2c8d1f843be84`, size `7406598` bytes, downloaded SHA-256 `644e9744af24a97d4f0ae67a5537992808ae2fbc6c4dcdb70fc1c44736225eca`.
- `limeimetw` edited the retained v6.1.23 update comment at https://github.com/lime-ime/limeime/issues/124#issuecomment-4761898236 to tell the Google Play closed-test reporter to update from Google Play, not from a raw APK link.
- The issue is reopened and should remain open pending reporter confirmation on Google Play v6.1.23.
- After two reporter recordings, `limeimetw` posted the current narrowed follow-up at https://github.com/lime-ime/limeime/issues/124#issuecomment-4766516641 asking whether the temporary reverse-lookup hint duration and placement are acceptable, using public wording that describes disappearance as roughly five seconds, or whether the reporter prefers a shorter duration or further inward keyboard placement; source/tests still pin the timed reverse-lookup lime-toast timeout to `1400` ms, so longer observed duration should be clarified against the exact build/path.
- If the reporter says v6.1.23 still overlaps the bottom input field or remains unacceptable, continue the focused follow-up with tested version/build, screenshots/video, exact app/key sequence, and display/keyboard/font-size settings.
- iOS/TestFlight retest is not required for the Android `PopupWindow` overlap path unless separate iOS reverse-lookup layout evidence appears.
