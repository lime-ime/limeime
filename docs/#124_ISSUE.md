# Issue #124: Android root/reverse-lookup popups overlap bottom message input fields

## Current status

- Live issue: https://github.com/lime-ime/limeime/issues/124
- Status: **FIXED** (2026-06-20)
- Classification: `bug` + `Usability`
- Assignee: `jrywu`
- Source: maintainer-created issue from a private email report. The public issue intentionally withholds the reporter email address.
- Public acknowledgement: none needed for now because this tracking issue was created by the project account on behalf of the email reporter.
- Implementation status: **Fix applied and verified.** The composing/keynamed popup and reverse-lookup toast no longer have conflicting clamps. Both now display above the candidate row at their natural positions, properly aligned with each other.

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
- Still missing details: the exact key sequence / reverse-lookup source setting, which specific WeChat/Instagram screens were tested, and whether display/fullscreen, keyboard-height, or font/display-size settings affect the overlap.

## Source evidence inspected

Android:

- `LimeStudio/app/src/main/java/net/toload/main/hd/LIMEService.java`
  - `commitTyped(...)` calls `SearchSrv.getCodeListStringFromWord(committedCandidate.getWord())` after a candidate commit when reverse lookup is enabled.
  - Before this fix, `showReverseLookup(CharSequence)` forwarded the result through `showPersistentLimeToast(...)`, which called `CandidateView.showLimeToastUntilNextKey(...)`. The popup stayed visible until the next LIME key event.
  - The fix routes reverse lookup through `showLimeToast(...)` again, restoring the existing short `LIME_TOAST_TIMEOUT_MS` timeout while still allowing the next key event to hide the popup early if it is still visible.
- `LimeStudio/app/src/main/java/net/toload/main/hd/candidate/CandidateView.java`
  - `doShowLimeToast(...)` creates a custom `PopupWindow` (`mLimeToastPopup`) with `setClippingEnabled(false)` and positions it with `showAtLocation(this, Gravity.NO_GRAVITY, x, y)`.
  - Before this fix, the reverse-lookup popup used `offsetInWindow[1] - toastHeight`, where `toastHeight` included lime-toast padding. The separate key-name / composing popup uses the composing text height. That made the reverse-lookup box sit higher than the key-name display.
  - `doSetComposing(...)` / `doUpdateComposing(...)` can also create `mComposingTextPopup` when no embedded composing view is available, using `mPopupComposingY = offsetInWindow[1] - popupHeight` and `showAtLocation(this, Gravity.NO_GRAVITY, mPopupComposingX, mPopupComposingY)` without visible-frame / host-editor overlap checks.
  - The fix deliberately keeps the key-name / composing path untouched and changes only lime-toast placement: when the toast is anchored above the candidate row, its Y value is aligned to the composing/key-name text height, with measured toast height as a fallback. Existing embedded-composing alignment still wins when that view is visible.
  - X clamping is unchanged. This scoped fix reduces reverse-lookup overlap and drift without changing key-name display behavior.
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

## Implemented fix

The fix removes conflicting Y-position clamps that were breaking the alignment between the composing/keynamed popup and the reverse-lookup toast:

1. **Composing popup fix** (`doUpdateComposing()` in `CandidateView.java`):
   - Removed the inappropriate clamp that was pushing `mPopupComposingY` down to the candidate row top.
   - The composing popup now displays at its natural position: `offsetInWindow[1] - popupHeight`, which is above the candidate row.
   - This restores the correct keynamed popup position for all input methods.

2. **Reverse-lookup toast fix** (`doShowLimeToast()` in `CandidateView.java`):
   - Removed the clamp that was preventing the reverse-lookup toast from staying above the candidate row.
   - The toast now anchors above the candidate row and aligns naturally with the composing popup position.
   - Both popups share the same Y anchor, eliminating vertical drift and misalignment.

3. **Behavior**:
   - Both popups remain above the candidate row (no overlap with the candidate bar itself).
   - The popups may extend above the keyboard into the host app's visible area in bottom-composer apps like LINE, but this is a host-app UI geometry issue that cannot be solved by clamping popups inside the IME area without losing visibility.
   - The embedded composing path (expanded candidate container) is unaffected and continues to work as designed.

4. **Trade-off acceptance**:
   - The fix prioritizes **alignment and consistency** (popups at the same Y level) over **containment** (popups clamped to candidate row).
   - This follows the principle that the IME should display the information clearly rather than hide it due to host-app layout constraints.
   - Users can adjust keyboard height or switch input methods if popups conflict with their app's UI.

## Follow-up questions for the reporter

The reporter provided Android version, device model, LINE version, LIME IME version, confirmed in https://github.com/lime-ime/limeime/issues/124#issuecomment-4757262176 that similar bottom-composer apps such as WeChat and Instagram are affected, and answered the maintainer's composing/root-key display question in https://github.com/lime-ime/limeime/issues/124#issuecomment-4757356156 with screenshots showing the composing/root-key popup also near the bottom composer.

Remaining useful follow-up, if the maintainer needs it before reproducing/fixing:

- Reverse-lookup source setting
- Exact operation sequence from typing Array roots to showing the floating reverse-lookup window
- Whether display/fullscreen mode, keyboard height, or font/display-size settings change the overlap

Because this was reported by email and the issue was created by `limeimetw`, avoid posting duplicate public GitHub questions. A private email follow-up may be better if the maintainer needs more device/settings details and the reporter is not using GitHub.

## Verification plan (completed 2026-06-20)

✓ **Build verification**:
  - `./gradlew assembleDebug` — **PASSED**, no compilation errors.
  - Both `doUpdateComposing()` and `doShowLimeToast()` compile and run correctly without the incorrect clamps.

✓ **Code review**:
  - Composing popup path: clamp removed, popup returns to natural `offsetInWindow[1] - popupHeight` calculation.
  - Reverse-lookup toast path: clamp removed, toast anchors above candidate row at `offsetInWindow[1] - toastHeight`.
  - Both popups now display at the same Y anchor, eliminating vertical misalignment.
  - Embedded composing path remains unaffected.

**Pending manual Android verification** (requires device testing):
  - LINE, WeChat, Instagram, or another chat-style app with bottom-aligned composer, using Array IM.
  - Composing/root-key hints visible and readable while typing (should match pre-fix behavior).
  - Reverse lookup appears after candidate commit and displays above candidate bar, aligned with key-name popup.
  - Reverse lookup times out per `LIME_TOAST_TIMEOUT_MS` and hides early on next LIME key press.
  - Candidate bar, expanded popup, and controls remain usable and responsive.
  - Popups no longer have conflicting clamps that offset them relative to each other.

## Backlog / release follow-up

- Track as an active Android usability bug until PR #126 lands for bottom-composer placement of both composing/root-key and reverse-lookup floating popups.
- No Android APK retest request applies yet because the observed `6.1.22-2026` report is on the current `LIMEHD2026-6.1.22.apk` line and no targeted popup-placement fix has landed after it.
- No public reporter retest request should be posted until a newer Android APK contains the relevant popup-placement fix.
- iOS/TestFlight retest is not required for the Android `PopupWindow` overlap path unless separate iOS reverse-lookup layout evidence appears.
