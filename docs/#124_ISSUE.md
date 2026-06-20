# Issue #124: Android root/reverse-lookup popups overlap bottom message input fields

## Current status

- Live issue: https://github.com/lime-ime/limeime/issues/124
- Status: open
- Classification: `bug` + `Usability`
- Assignee: `jrywu`
- Source: maintainer-created issue from a private email report. The public issue intentionally withholds the reporter email address.
- Public acknowledgement: none needed for now because this tracking issue was created by the project account on behalf of the email reporter.
- Implementation status: branch `fix/124-android-popup-placement` constrains both Android popup paths to the IME-owned candidate area and is awaiting PR review/merge.

## Problem statement

The reporter says that when using LIME IME Array input inside LINE on Android, committing a candidate can trigger the reverse-lookup display and the grey floating reverse-lookup window appears near the LINE message input field. In the attached email screenshot described by the issue, that floating window covers the active message input area, interfering with reading and continuing to type. Reporter follow-up screenshots now also show the composing/root-key display during typing overlapping bottom-composer input regions, so the issue likely covers both Android floating popup paths rather than only the post-commit reverse-lookup toast.

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
  - `showReverseLookup(CharSequence)` forwards the result to `showPersistentLimeToast(...)`.
  - `showPersistentLimeToast(...)` picks `mCandidateViewInInputView` when it has a window token, then calls `CandidateView.showLimeToastUntilNextKey(...)`.
  - `onKey(...)` hides the lime toast at the start of the next key event, so reverse lookup is intentionally persistent until the next LIME key press rather than a short system toast.
- `LimeStudio/app/src/main/java/net/toload/main/hd/candidate/CandidateView.java`
  - `doShowLimeToast(...)` creates a custom `PopupWindow` (`mLimeToastPopup`) with `setClippingEnabled(false)` and positions it with `showAtLocation(this, Gravity.NO_GRAVITY, x, y)`.
  - The reverse-lookup Y position is `offsetInWindow[1] - toastHeight`, with optional alignment to `embeddedComposing`/`mComposingTextView`. There is no visible-frame / host-editor overlap check before showing the popup.
  - `doSetComposing(...)` / `doUpdateComposing(...)` can also create `mComposingTextPopup` when no embedded composing view is available, using `mPopupComposingY = offsetInWindow[1] - popupHeight` and `showAtLocation(this, Gravity.NO_GRAVITY, mPopupComposingX, mPopupComposingY)` without visible-frame / host-editor overlap checks.
  - X is clamped for the reverse-lookup popup to the candidate row right edge, but Y is not clamped or flipped relative to the visible app/edit field area. The composing popup path similarly anchors above the candidate view.
- `LimeStudio/app/src/main/java/net/toload/main/hd/candidate/CandidateInInputViewContainer.java`
  - The normal in-keyboard candidate row initializes `R.id.candidatesView` but does not wire an `embeddedComposing` view, so composing/root-key display can fall back to `mComposingTextPopup` in the active input-view path.
- `LimeStudio/app/src/main/java/net/toload/main/hd/candidate/CandidateViewContainer.java` and `LimeStudio/app/src/main/res/layout/candidates.xml`
  - The expanded/floating candidate container has `R.id.embeddedComposing` and calls `setEmbeddedComposingView(...)`, so popup behavior can differ between the normal in-keyboard row and expanded/floating candidate container.
- `LimeStudio/app/src/androidTest/java/net/toload/main/hd/LIMEServiceTest.java`
  - `reverseLookupUsesPersistentLimeToast()` verifies reverse lookup uses `showLimeToastUntilNextKey(...)` and does not reuse composing text.
  - `nextKeyClearsPersistentLimeToast()` verifies the next key hides the persistent toast.
- `LimeStudio/app/src/androidTest/java/net/toload/main/hd/candidate/CandidateViewTest.java`
  - Existing tests cover whether a toast can show with an attached anchor, but not vertical placement, visible-frame overlap, LINE/editor geometry, or fallback placement.

Analogous iOS path checked:

- `LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift`
  - iOS also performs reverse lookup after a candidate commit, but `showLimeToast(_:)` writes the lookup text into `candidateBar.composingText` and `expandedComposingLabel` inside the keyboard extension UI instead of creating a separate floating `PopupWindow` over the host app.
  - This makes the reported floating-window-over-LINE-input symptom Android-specific by implementation path. iOS reverse lookup can still have candidate-bar layout concerns, but it does not use Android's host-window `PopupWindow` placement path.

## Likely root cause

Android has two relevant floating-popup paths anchored around the candidate row: the persistent reverse-lookup lime toast (`mLimeToastPopup`) and the composing/root-key popup (`mComposingTextPopup`) used when the active input-view candidate row lacks an embedded composing view. In LINE, WeChat, Instagram, and similar bottom-composer apps, the candidate row can sit immediately below or near the host app's message input field. Because both popup paths position themselves above the candidate view without checking the host window's visible input/editor area, either popup can extend into and cover the app's message input field.

This is a geometry/placement bug in Android candidate-view popup handling, not a LINE-specific text-processing bug based on current evidence.

## Implemented fix / investigation direction

1. Source fix in branch `fix/124-android-popup-placement` adds a shared Android `CandidateView.clampPopupYToImeArea(...)` helper.
2. The composing/root-key popup path now clamps `mPopupComposingY` so it cannot rise above the candidate row and cover a host bottom-composer input field.
3. The non-embedded reverse-lookup lime toast path now applies the same clamp, while the embedded composing path keeps its existing in-IME placement.
4. Expanded candidate popup behavior is intentionally unchanged because it uses a separate bottom-anchored popup path and already hides the composing popup while expanded.
5. Follow-up manual reproduction should still inspect runtime values for:
   - candidate view `getLocationInWindow(...)`
   - composing-popup and reverse-lookup toast measured heights
   - root-window visible display frame / IME visible area
   - whether LINE uses fullscreen/extract mode, insets, or adjusted resize/pan behavior on the affected device.
6. If manual device testing shows the candidate-row overlay is still too intrusive, the next UI refinement should move these short hints fully inline inside the candidate/input view area, similar to the iOS candidate-bar path.

## Follow-up questions for the reporter

The reporter provided Android version, device model, LINE version, LIME IME version, confirmed in https://github.com/lime-ime/limeime/issues/124#issuecomment-4757262176 that similar bottom-composer apps such as WeChat and Instagram are affected, and answered the maintainer's composing/root-key display question in https://github.com/lime-ime/limeime/issues/124#issuecomment-4757356156 with screenshots showing the composing/root-key popup also near the bottom composer.

Remaining useful follow-up, if the maintainer needs it before reproducing/fixing:

- Reverse-lookup source setting
- Exact operation sequence from typing Array roots to showing the floating reverse-lookup window
- Whether display/fullscreen mode, keyboard height, or font/display-size settings change the overlap

Because this was reported by email and the issue was created by `limeimetw`, avoid posting duplicate public GitHub questions. A private email follow-up may be better if the maintainer needs more device/settings details and the reporter is not using GitHub.

## Verification plan

- Manual Android verification:
  - LINE, WeChat, Instagram, or another chat-style input with a bottom-aligned composer, using Array IM, composing/root-key display, and reverse lookup enabled.
  - At least one non-chat app with a bottom-aligned text field, to determine whether this is general bottom-composer geometry or chat-app-specific.
  - At least one normal text editor / notes app to confirm the reverse-lookup display remains visible and readable.
- Regression checks:
  - Composing/root-key hints remain visible and readable while typing.
  - Reverse lookup still appears after candidate commit when enabled.
  - Reverse lookup still hides on the next LIME key press.
  - Candidate bar, expanded candidate popup, and clear-code/dismiss controls remain usable.
- Automated checks:
  - `./gradlew :app:compileDebugJavaWithJavac`
  - `./gradlew :app:compileDebugAndroidTestJavaWithJavac`
  - Add helper coverage for popup Y-position clamping when a popup would otherwise rise above the candidate row.
  - Keep or update existing composing-popup and `reverseLookupUsesPersistentLimeToast()` / `nextKeyClearsPersistentLimeToast()` expectations according to the chosen UI path.

## Backlog / release follow-up

- Track as an active Android usability bug until the branch/PR source fix lands for bottom-composer placement of both composing/root-key and reverse-lookup floating popups.
- No Android APK retest request applies yet because the observed `6.1.22-2026` report is on the current `LIMEHD2026-6.1.22.apk` line and no targeted popup-placement fix has landed after it.
- No public reporter retest request should be posted until a newer Android APK contains the relevant popup-placement fix.
- iOS/TestFlight retest is not required for the Android `PopupWindow` overlap path unless separate iOS reverse-lookup layout evidence appears.
