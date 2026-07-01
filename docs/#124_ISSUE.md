# Issue #124: Android root/reverse-lookup popups overlap bottom message input fields

## Current status

- Live issue: https://github.com/lime-ime/limeime/issues/124
- Status: **OPEN / v6.1.23 popup fix delivered; v6.1.26 auto Chinese punctuation fix delivered; v6.1.27 delivered the wider candidate-strip X tap target plus related #124 Android usability follow-ups. `01disney` confirmed the X tap target improved on Google Play v6.1.27 and added that the bottom-input composing/reverse-lookup hints are less likely to cover typed output; remaining original-reporter popup/readability confirmation and expanded `123` long-press scope need maintainer/product decision.** (reopened 2026-06-21)
- Classification: `bug` + `Usability`
- Assignee: `jrywu`
- Source: maintainer-created issue from a private email report. The public issue intentionally withholds the reporter email address.
- Public follow-up: `limeimetw` edited the retained v6.1.23 update comment at https://github.com/lime-ime/limeime/issues/124#issuecomment-4761898236 to use Google Play closed-test wording, then reopened the issue with https://github.com/lime-ime/limeime/issues/124#issuecomment-4761963945 so the reporter can confirm the result after updating.
- Implementation status: **Released in v6.1.23, with additional Android popup/readability usability refinement in v6.1.27.** The initial Android popup lifetime/alignment and placement follow-up fixes are on `master` via commits `61cf87b65f03f69486e112bf1dc1383c9974a125` and `9fc84f97eaddfea5f550268e950695dadbb3fea5`. Follow-up commit `431762fb2e530ee75600e50fc0ecbb417822d7db` is included before the v6.1.27 APK build and anchors composing/reverse-lookup hints at the left screen edge, forces stale composing popup dismissal before reverse lookup, makes the reverse-lookup toast visually match the composing display, and extends the timed lime-toast timeout to `3000` ms.
- Reporter verification: pending. After the v6.1.23 Google Play retest request, the reporter uploaded two screen recordings in https://github.com/lime-ime/limeime/issues/124#issuecomment-4765529644 and https://github.com/lime-ime/limeime/issues/124#issuecomment-4765655167 without explicitly stating the tested app version in the comments or sampled frames. `limeimetw` followed up in https://github.com/lime-ime/limeime/issues/124#issuecomment-4766516641 to ask whether the current temporary reverse-lookup display duration and placement are acceptable, using public wording that describes the expected disappearance as roughly five seconds, or whether the reporter prefers a shorter duration or further inward keyboard placement. v6.1.27 source/tests now pin the timed reverse-lookup lime-toast timeout to `3000` ms after commit `431762fb2e530ee75600e50fc0ecbb417822d7db`; older v6.1.23/v6.1.26 notes that mentioned `1400` ms are historical and should be clarified against the exact tested build/path. The original reporter then proposed UX adjustments in https://github.com/lime-ime/limeime/issues/124#issuecomment-4779129986: put the reverse-lookup hint above the root display, show only the lookup roots without repeating the committed character, and limit the list to the first or second lookup option. Later commenter `Limeroshenko` edited https://github.com/lime-ime/limeime/issues/124#issuecomment-4788766570 to report the reverse-lookup display disappears too quickly to read, so notifications are a current workaround, to ask whether removing the committed character would leave only root codes in the notification, and to disagree with limiting the lookup list because they want all possible root-code solutions available. A later commenter `01disney` reported a likely separate Android 16 / POCO F6 Pro / Boshiamy first-input IME-dismiss symptom in https://github.com/lime-ime/limeime/issues/124#issuecomment-4786153001, then narrowed it in https://github.com/lime-ime/limeime/issues/124#issuecomment-4808918464: after more observation, it seems to happen when pressing the `中` / `EN` language-switch key, with the keyboard sliding down as if the hide-key path was triggered. Keep that separate from the popup-placement scope unless maintainer evidence connects them.

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

## Follow-up questions for the reporter

The reporter provided Android version, device model, LINE version, LIME IME version, confirmed in https://github.com/lime-ime/limeime/issues/124#issuecomment-4757262176 that similar bottom-composer apps such as WeChat and Instagram are affected, and answered the maintainer's composing/root-key display question in https://github.com/lime-ime/limeime/issues/124#issuecomment-4757356156 with screenshots showing the composing/root-key popup also near the bottom composer.

Current unresolved follow-up is narrowed to original private-reporter confirmation and remaining product/usability decisions after the v6.1.23–v6.1.27 Google Play closed-test follow-ups:

- Whether the current temporary reverse-lookup display time and placement are acceptable. The public follow-up describes the expected disappearance as roughly five seconds; v6.1.27 source/tests now pin the timed reverse-lookup lime-toast timeout to `3000` ms after commit `431762fb2e530ee75600e50fc0ecbb417822d7db`, while older `1400` ms notes are historical and should be interpreted against the exact tested build/path.
- If not acceptable, whether the preferred direction is a shorter display duration or moving the hint farther inside the keyboard area.
- The original reporter answered the acceptability/design follow-up in https://github.com/lime-ime/limeime/issues/124#issuecomment-4779129986 by proposing three changes: place the reverse-lookup hint above the root display, show only the reverse-lookup roots without repeating the committed character, and limit the displayed lookup choices to the first or second option.
- Later commenter `Limeroshenko` added UX feedback in https://github.com/lime-ime/limeime/issues/124#issuecomment-4788766570: the reverse-lookup text may disappear too quickly for learning/lookup use, notifications are a current workaround for reading it, removing the committed character raises a question about whether notifications would show only root codes, and limiting to only one or two root-code solutions would remove information they want to choose from. Treat this as product/design input on reverse-lookup readability and content volume, not as a new Android placement failure by itself.
- If the reporter reports continued overlap, ask them to state the tested LIME version/build and keep any additional evidence scoped to the exact app, key sequence, and display/keyboard/font-size settings.
- Treat `01disney`'s later Android 16 / POCO F6 Pro / Boshiamy first-input IME-dismiss report in https://github.com/lime-ime/limeime/issues/124#issuecomment-4786153001 and the narrowed follow-up in https://github.com/lime-ime/limeime/issues/124#issuecomment-4808918464 as an adjacent likely-separate language-switch / keyboard-dismiss symptom unless maintainer evidence connects it to the popup-placement work. The current reporter observation is that pressing `中` / `EN` can make the IME slide down as if the hide-key path was triggered, rather than a fully automatic close. Prefer a separate issue or targeted follow-up rather than broadening #124 silently.
- `01disney` and `Limeroshenko` also discussed auto Chinese punctuation accessibility in the same thread after testing v6.1.24. Android APK v6.1.26 contains the auto Chinese punctuation strip fixes from commit `43336dd3c84d2af13e61d9e4ff51fed339f4b03c`; `limeimetw` posted a scoped v6.1.26 GitHub-APK retest request to `01disney` at https://github.com/lime-ime/limeime/issues/124#issuecomment-4825931659, with explicit Google Play channel wording for Play users.
- `01disney` edited https://github.com/lime-ime/limeime/issues/124#issuecomment-4826137217 after that retest request to include a working screen recording. The current comment says the candidate-strip left `X` is too small and hard to press, can require several taps, affects typing flow, may accidentally hit smart candidates, asks whether the `X` can be made the same size as the smiley icon, and adds that thumb typing is less precise than index-finger tapping so a larger `X` may be more intuitive. Sampled video frames show LINE with LIME active, the input-method picker, the left candidate-strip `X`, and finger interaction around the candidate strip / keyboard while candidate, punctuation, and emoji-like options appear. The frames do not visibly confirm the tested LIME version/build, so treat this as reinforced clear-code / dismiss-button usability feedback rather than a confirmed v6.1.26 punctuation-fix failure. Jeremy confirmed this issue should stay open: v6.1.26 fixes the auto Chinese punctuation behavior, but the next Android release should widen the candidate-strip dismiss button. `limeimetw` posted https://github.com/lime-ime/limeime/issues/124#issuecomment-4826174943 to state that v6.1.26 fixed the punctuation scope, the X tap-target issue is not solved yet, #124 will remain open, and the wider candidate dismiss button is queued for a later Android release.
- After `limeimetw` posted the Google Play closed-test v6.1.27 follow-up at https://github.com/lime-ime/limeime/issues/124#issuecomment-4849071167, `01disney` replied in https://github.com/lime-ime/limeime/issues/124#issuecomment-4851877127 that the X problem improved and is convenient now, and later edited the same comment to add that bottom-input composing/reverse-lookup hints are less likely to cover typed output in the new version. Treat this as a positive community datapoint for v6.1.27, not full original-reporter closure. The same reply asks whether the English-keyboard `123` long-press shortcut can also apply inside table/input-method keyboards such as Boshiamy, because that keyboard is visually similar to the English keyboard; this is a new expansion request beyond the confirmed English-layout-only `feat#124` scope and should wait for maintainer/Jeremy product decision before adding new backlog implementation scope.

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
- The issue is reopened and should remain open pending original-reporter confirmation and current Google Play v6.1.27 follow-up decisions.
- After two reporter recordings, `limeimetw` posted the current narrowed follow-up at https://github.com/lime-ime/limeime/issues/124#issuecomment-4766516641 asking whether the temporary reverse-lookup hint duration and placement are acceptable, using public wording that describes disappearance as roughly five seconds, or whether the reporter prefers a shorter duration or further inward keyboard placement; v6.1.27 source/tests now pin the timed reverse-lookup lime-toast timeout to `3000` ms after commit `431762fb2e530ee75600e50fc0ecbb417822d7db`, while older `1400` ms notes are historical and should be interpreted against the exact tested build/path.
- If the reporter says v6.1.23 still overlaps the bottom input field or remains unacceptable, continue the focused follow-up with tested version/build, screenshots/video, exact app/key sequence, and display/keyboard/font-size settings.
- Android v6.1.26 contains the auto Chinese punctuation strip fixes from commit `43336dd3c84d2af13e61d9e4ff51fed339f4b03c`; `limeimetw` posted scoped GitHub-APK retest request https://github.com/lime-ime/limeime/issues/124#issuecomment-4825931659 for `01disney`'s v6.1.24 punctuation feedback, while telling Google Play users to update through Google Play instead of installing the GitHub APK. Follow-up https://github.com/lime-ime/limeime/issues/124#issuecomment-4826174943 clarifies that v6.1.26 fixes the auto Chinese punctuation scope, but the issue should stay open because the left candidate-strip X is still too small/hard to press. The wider candidate dismiss / clear-code button tap target is tracked as `feat#N01 Android` in `docs/BACKLOG.md` and is included in Android GitHub APK v6.1.27. `limeimetw` posted the scoped v6.1.27 GitHub APK update/retest note at https://github.com/lime-ime/limeime/issues/124#issuecomment-4846962493, covering the wider X tap target, English `123` long-press shortcut, and bottom-input hint placement adjustment. After Google Play closed-test v6.1.27 was published, `limeimetw` posted the Play-channel follow-up at https://github.com/lime-ime/limeime/issues/124#issuecomment-4849071167 asking Google Play users to update from the store page and confirm the X tap target plus bottom-input composing/reverse-lookup hint placement. `01disney` confirmed in https://github.com/lime-ime/limeime/issues/124#issuecomment-4851877127 that the X problem improved and later edited the same comment to add that the new version is less likely to cover typed output near the bottom input field, but asked whether the `123` long-press shortcut can also apply inside Boshiamy/table IM keyboards. Keep #124 open pending original-reporter popup/readability confirmation and maintainer/product decision on whether to expand the `123` shortcut beyond the current English-layout-only scope.
- iOS/TestFlight retest is not required for the Android `PopupWindow` overlap path unless separate iOS reverse-lookup layout evidence appears. The auto Chinese punctuation source change also touches iOS, but iOS user delivery remains separate from this Android GitHub APK follow-up.
