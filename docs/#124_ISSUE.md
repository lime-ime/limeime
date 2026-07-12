# Issue #124: Android root/reverse-lookup popups overlap bottom message input fields

## Current status

- Live issue: https://github.com/lime-ime/limeime/issues/124
- Status: **CLOSED / maintainer/project-account closed on 2026-07-01 after v6.1.27 shipped the popup/readability and related usability follow-ups.** The retained closing comment is https://github.com/lime-ime/limeime/issues/124#issuecomment-4855233790. It records that v6.1.27 has addressed the candidate-strip X tap target, bottom-input composing/reverse-lookup hint placement, and the English-keyboard long-press `123` shortcut, and that broader visual/layout feedback will be considered later rather than keeping this issue open.
- Classification: `bug` + `Usability`
- Assignee: `jrywu`
- Source: maintainer-created issue from a private email report. The public issue intentionally withholds the reporter email address.
- Public follow-up: `limeimetw` edited the retained v6.1.23 update comment at https://github.com/lime-ime/limeime/issues/124#issuecomment-4761898236 to use Google Play closed-test wording, then reopened the issue with https://github.com/lime-ime/limeime/issues/124#issuecomment-4761963945 for reporter confirmation. Later v6.1.27 follow-ups were posted at https://github.com/lime-ime/limeime/issues/124#issuecomment-4846962493 and https://github.com/lime-ime/limeime/issues/124#issuecomment-4849071167.
- Implementation status: **Released in v6.1.23, with additional Android popup/readability usability refinement in v6.1.27.** The initial Android popup lifetime/alignment and placement follow-up fixes are on `master` via commits `61cf87b65f03f69486e112bf1dc1383c9974a125` and `9fc84f97eaddfea5f550268e950695dadbb3fea5`. Follow-up commit `431762fb2e530ee75600e50fc0ecbb417822d7db` is included before the v6.1.27 APK build and anchors composing/reverse-lookup hints at the left screen edge, forces stale composing popup dismissal before reverse lookup, makes the reverse-lookup toast visually match the composing display, and extends the timed lime-toast timeout to `3000` ms.
- Verification / closure state: the original private reporter provided post-v6.1.23 recordings and later UX suggestions rather than an explicit final closure confirmation. Community tester `01disney` confirmed on Google Play v6.1.27 in https://github.com/lime-ime/limeime/issues/124#issuecomment-4851877127 that the candidate-strip X problem improved and later edited the same comment to say bottom-input composing/reverse-lookup hints are less likely to cover typed output. `limeimetw` then closed the issue with https://github.com/lime-ime/limeime/issues/124#issuecomment-4855233790. Treat any future popup overlap regression or language-switch/key-hide sizing request as a new issue or separate product/backlog decision, not as an active #124 retest watch.

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

- `LimeStudio/app/src/main/java/org/limeime/LIMEService.java`
  - `commitTyped(...)` calls `SearchSrv.getCodeListStringFromWord(committedCandidate.getWord())` after a candidate commit when reverse lookup is enabled.
  - Before the first #124 fix, `showReverseLookup(CharSequence)` forwarded the result through `showPersistentLimeToast(...)`, which called `CandidateView.showLimeToastUntilNextKey(...)`. The popup stayed visible until the next LIME key event.
  - Commit `61cf87b65f03f69486e112bf1dc1383c9974a125` routes reverse lookup through `showLimeToast(...)` again, restoring the existing short `LIME_TOAST_TIMEOUT_MS` timeout while still allowing the next key event to hide the popup early if it is still visible.
- `LimeStudio/app/src/main/java/org/limeime/candidate/CandidateView.java`
  - `doShowLimeToast(...)` creates a custom `PopupWindow` (`mLimeToastPopup`) with `setClippingEnabled(false)` and positions it with `showAtLocation(this, Gravity.NO_GRAVITY, x, y)`.
  - Before the first #124 fix, the reverse-lookup popup used `offsetInWindow[1] - toastHeight`, where `toastHeight` included lime-toast padding. The separate key-name / composing popup uses the composing text height. That made the reverse-lookup box sit higher than the key-name display.
  - `doSetComposing(...)` / `doUpdateComposing(...)` can also create `mComposingTextPopup` when no embedded composing view is available, using `mPopupComposingY = offsetInWindow[1] - popupHeight` and `showAtLocation(this, Gravity.NO_GRAVITY, mPopupComposingX, mPopupComposingY)`.
  - Commit `61cf87b65f03f69486e112bf1dc1383c9974a125` first narrowed the reverse-lookup timeout/alignment regression. Follow-up commit `9fc84f97eaddfea5f550268e950695dadbb3fea5` superseded the unmerged PR #126 path and adjusted the composing/key-name and reverse-lookup popup placement together, with additional test fixes.
  - Existing embedded-composing alignment still wins when that view is visible; the expanded candidate popup uses a separate path.
- `LimeStudio/app/src/main/java/org/limeime/candidate/CandidateInInputViewContainer.java`
  - The normal in-keyboard candidate row initializes `R.id.candidatesView` but does not wire an `embeddedComposing` view, so composing/root-key display can fall back to `mComposingTextPopup` in the active input-view path.
- `LimeStudio/app/src/main/java/org/limeime/candidate/CandidateViewContainer.java` and `LimeStudio/app/src/main/res/layout/candidates.xml`
  - The expanded/floating candidate container has `R.id.embeddedComposing` and calls `setEmbeddedComposingView(...)`, so popup behavior can differ between the normal in-keyboard row and expanded/floating candidate container.
- `LimeStudio/app/src/androidTest/java/org/limeime/LIMEServiceTest.java`
  - `reverseLookupUsesTimedLimeToast()` verifies reverse lookup uses the timed lime-toast path and does not reuse composing text or the persistent-until-next-key path.
  - `nextKeyClearsLimeToastIfStillVisible()` keeps the early-hide-on-next-key behavior for a toast that has not timed out yet.
- `LimeStudio/app/src/androidTest/java/org/limeime/candidate/CandidateViewTest.java`
  - Adds coverage for the lime-toast Y helper aligning to composing/key-name popup height.
  - Older tests covered the short `1400` ms lime-toast timeout; v6.1.27 follow-up commit `431762fb2e530ee75600e50fc0ecbb417822d7db` updates the reverse-lookup readability path and tests to `3000` ms.

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

## Implemented changes in v6.1.23 and v6.1.27

The released #124 work started in v6.1.23 across two GitHub-visible commits and should be read as a combined Android popup maintenance fix rather than as a single one-line geometry change. v6.1.27 adds a later readability/anchoring refinement:

1. **Reverse-lookup lifetime fix** (`61cf87b65f03f69486e112bf1dc1383c9974a125`):
   - Routed reverse lookup back through the timed lime-toast path instead of leaving the popup visible until the next key.
   - Restored the short `LIME_TOAST_TIMEOUT_MS` behavior while preserving early hide on the next LIME key if the toast is still visible.

2. **Reverse-lookup alignment fix** (`61cf87b65f03f69486e112bf1dc1383c9974a125`):
   - Reduced vertical drift between reverse-lookup toast and key-name/composing popup by aligning the reverse-lookup Y calculation with the composing/key-name popup height rather than relying only on the padded toast height.

3. **Popup placement / test follow-up** (`9fc84f97eaddfea5f550268e950695dadbb3fea5`):
   - Superseded the unmerged PR #126 path and adjusted the composing/key-name and reverse-lookup popup placement work together.
   - Included related Android test fixes and reported `connectedAndroidTest` as passing in the commit message.

4. **v6.1.27 readability / anchoring refinement** (`431762fb2e530ee75600e50fc0ecbb417822d7db`):
   - Anchors both composing and reverse-lookup hints at the left screen edge instead of offsetting by stale composing width.
   - Force-dismisses composing synchronously before showing the reverse-lookup toast, making the post-commit hint position more predictable.
   - Makes the reverse-lookup toast visual style closer to the composing display and extends the timed lime-toast timeout from `1400` ms to `3000` ms for readability.

5. **Remaining verification scope**:
   - Build and test checks support the source changes, but bottom-composer behavior still needs real-device/user verification in LINE, WeChat, Instagram, or similar apps because the original symptom depends on host-app window geometry and device/display settings.
   - The v6.1.23/v6.1.27 public comments therefore ask reporters to confirm whether the popup no longer covers the input field, or at least whether it improved compared with earlier builds.

## Follow-up / future handling

The reporter provided Android version, device model, LINE version, LIME IME version, confirmed in https://github.com/lime-ime/limeime/issues/124#issuecomment-4757262176 that similar bottom-composer apps such as WeChat and Instagram are affected, and answered the maintainer's composing/root-key display question in https://github.com/lime-ime/limeime/issues/124#issuecomment-4757356156 with screenshots showing the composing/root-key popup also near the bottom composer. The issue is now closed by `limeimetw` after the v6.1.27 follow-up and closing comment https://github.com/lime-ime/limeime/issues/124#issuecomment-4855233790.

No active public retest request remains under #124. If the original private reporter or a future community reporter shows that v6.1.27 or later still covers a bottom input field, open or triage a new focused issue with tested version/build, app, key sequence, screenshots/video, and display/keyboard/font-size settings. Keep the prior design feedback as context:

- The original reporter proposed in https://github.com/lime-ime/limeime/issues/124#issuecomment-4779129986 placing the reverse-lookup hint above the root display, showing only lookup roots without repeating the committed character, and limiting displayed lookup choices to the first or second option.
- `Limeroshenko` added UX feedback in https://github.com/lime-ime/limeime/issues/124#issuecomment-4788766570 that the reverse-lookup text may disappear too quickly for learning/lookup use, notifications are a current workaround for reading it, removing the committed character raises a question about notification content, and limiting to only one or two root-code solutions would remove information they want.
- `01disney`'s later Android 16 / POCO F6 Pro / Boshiamy first-input IME-dismiss report in https://github.com/lime-ime/limeime/issues/124#issuecomment-4786153001 and narrowed follow-up in https://github.com/lime-ime/limeime/issues/124#issuecomment-4808918464 are adjacent likely-separate language-switch / keyboard-hide sizing feedback unless maintainer evidence connects them to popup placement.
- `01disney` and `Limeroshenko` also discussed auto Chinese punctuation accessibility in the same thread after testing v6.1.24. Android APK v6.1.26 contains the auto Chinese punctuation strip fixes from commit `43336dd3c84d2af13e61d9e4ff51fed339f4b03c`; this scope is not an active #124 watch after closure.
- The candidate-strip left `X` tap target feedback from https://github.com/lime-ime/limeime/issues/124#issuecomment-4826137217 was implemented in v6.1.27 as `feat#N01`; `01disney` confirmed the improvement in https://github.com/lime-ime/limeime/issues/124#issuecomment-4851877127.
- The English-layout long-press `123` shortcut shipped in v6.1.27 as `feat#124`. The follow-up request to extend that shortcut to table/IM keyboards is tracked separately as `feat#N03` in `docs/BACKLOG.md`; do not keep #124 open solely for that product work.
- The language-switch / keyboard-hide comparison screenshot in https://github.com/lime-ime/limeime/issues/124#issuecomment-4852351599 remains adjacent layout/usability product feedback. The closing comment says broader visual/layout adjustments are not the current highest priority but will be considered later.

Because this was originally reported by email and the issue was created by `limeimetw`, avoid posting duplicate public GitHub questions. A private email follow-up or a new narrowly scoped public issue may be better if new evidence appears.

## Verification plan / release verification

✓ **Build verification**:
  - `./gradlew assembleDebug` — **PASSED**, no compilation errors.
  - The affected composing/key-name and reverse-lookup popup code paths compile after the #124 fixes.

✓ **Code review**:
  - Reverse-lookup toast path: timed toast behavior is restored and placement/alignment is adjusted relative to the composing/key-name popup path.
  - Composing/key-name and reverse-lookup popup paths were reviewed together after the follow-up placement commit.
  - Embedded composing path remains a separate path.

✓ **Manual/community verification and closure**:
  - v6.1.27 shipped the bottom-input hint placement/readability refinement and related #124 usability work.
  - `01disney` confirmed on Google Play v6.1.27 that the wider candidate-strip X improved usability and later edited the same comment to say bottom-input composing/reverse-lookup hints are less likely to cover typed output.
  - `limeimetw` closed #124 with https://github.com/lime-ime/limeime/issues/124#issuecomment-4855233790.
  - Future bottom-composer overlap regressions should be tracked as a new focused issue rather than an active #124 watch.

## Backlog / release follow-up

- Android v6.1.23 contains the targeted #124 popup-position/alignment fix. The reporter was on the Google Play closed-test channel, so the public retest request correctly asked them to update from Google Play. The retained GitHub Release/sideload APK was later replaced as `LIMEHD2026-6.1.23.apk` for the old GitHub package family (`net.toload.main.hd2026`); verified GitHub Contents blob SHA `7315b2d88bf13327d2f16343ddd2c8d1f843be84`, size `7406598` bytes, downloaded SHA-256 `644e9744af24a97d4f0ae67a5537992808ae2fbc6c4dcdb70fc1c44736225eca`.
- Android v6.1.27 contains the follow-up popup/readability adjustment from commit `431762fb2e530ee75600e50fc0ecbb417822d7db`, the wider candidate-strip X tap target (`feat#N01`), and the English-layout long-press `123` shortcut (`feat#124`). GitHub Release/APK v6.1.27 uses `LIMEHD2026-6.1.27.apk`, Contents blob SHA `297a2ffe40e5ab3a6361f9cae8cf301d40bd8292`, size `7410887` bytes, downloaded SHA-256 `299d579df4dc2ffdceabdb038f708b46098dd721bbcd271f522ebd239d4ae653`.
- `limeimetw` posted the scoped v6.1.27 GitHub APK follow-up at https://github.com/lime-ime/limeime/issues/124#issuecomment-4846962493 and the Google Play closed-test follow-up at https://github.com/lime-ime/limeime/issues/124#issuecomment-4849071167.
- `01disney` confirmed in https://github.com/lime-ime/limeime/issues/124#issuecomment-4851877127 that the X tap-target problem improved and later edited the same comment to add that v6.1.27 is less likely to cover typed output near the bottom input field. `limeimetw` then closed #124 with https://github.com/lime-ime/limeime/issues/124#issuecomment-4855233790.
- `docs/BACKLOG.md` should no longer list #124 under active issue follow-up. Keep separate product work under New features / product work: `feat#N03` for extending long-press `123` to eligible IM keyboards, and any future language-switch / keyboard-hide sizing or visual-border work only if Jeremy/maintainer confirms it as a separate backlog item.
- iOS/TestFlight retest is not required for the Android `PopupWindow` overlap path unless separate iOS reverse-lookup layout evidence appears. The auto Chinese punctuation and related feature source changes that touch iOS remain normal iOS release QA, not an active #124 public watch.
