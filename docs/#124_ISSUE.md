# Issue #124: Android reverse-lookup toast overlaps LINE message input field

## Current status

- Live issue: https://github.com/lime-ime/limeime/issues/124
- Status: open
- Classification: `bug` + `Usability`
- Assignee: `jrywu`
- Source: maintainer-created issue from a private email report. The public issue intentionally withholds the reporter email address.
- Public acknowledgement: none needed for now because this tracking issue was created by the project account on behalf of the email reporter.

## Problem statement

The reporter says that when using LIME IME Array input inside LINE on Android, committing a candidate can trigger the reverse-lookup display and the grey floating reverse-lookup window appears near the LINE message input field. In the attached email screenshot described by the issue, that floating window covers the active message input area, interfering with reading and continuing to type.

Known public reproduction context:

- Host app: LINE 26.8.0
- Platform: Android 16
- Device: Asus Zenfone 12 Ultra
- LIME IME version: 6.1.22-2026
- IM/table: LIME IME Array
- Feature: reverse lookup notification after candidate commit
- Still missing details: whether other apps reproduce it, the exact key sequence / reverse-lookup source setting, and whether any LINE display/fullscreen or keyboard-height setting affects the overlap.

## Source evidence inspected

Android:

- `LimeStudio/app/src/main/java/net/toload/main/hd/LIMEService.java`
  - `commitTyped(...)` calls `SearchSrv.getCodeListStringFromWord(committedCandidate.getWord())` after a candidate commit when reverse lookup is enabled.
  - `showReverseLookup(CharSequence)` forwards the result to `showPersistentLimeToast(...)`.
  - `showPersistentLimeToast(...)` picks `mCandidateViewInInputView` when it has a window token, then calls `CandidateView.showLimeToastUntilNextKey(...)`.
  - `onKey(...)` hides the lime toast at the start of the next key event, so reverse lookup is intentionally persistent until the next LIME key press rather than a short system toast.
- `LimeStudio/app/src/main/java/net/toload/main/hd/candidate/CandidateView.java`
  - `doShowLimeToast(...)` creates a custom `PopupWindow` (`mLimeToastPopup`) with `setClippingEnabled(false)` and positions it with `showAtLocation(this, Gravity.NO_GRAVITY, x, y)`.
  - The current Y position is `offsetInWindow[1] - toastHeight`, with optional alignment to `embeddedComposing`/`mComposingTextView`. There is no visible-frame / host-editor overlap check before showing the popup.
  - X is clamped to the candidate row right edge, but Y is not clamped or flipped relative to the visible app/edit field area.
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

The Android reverse-lookup notification was moved to a custom persistent `PopupWindow` anchored around the candidate row. In LINE and possibly other chat apps, the candidate row can sit immediately below or near the host app's message input field. Because `CandidateView.doShowLimeToast(...)` positions the popup above the candidate view without checking the host window's visible input/editor area, the reverse-lookup popup can extend into and cover the app's message input field.

This is a geometry/placement bug in the Android reverse-lookup lime-toast path, not a LINE-specific text-processing bug based on current evidence.

## Proposed fix / investigation direction

1. Reproduce on Android with LINE and Array IM, reverse lookup enabled, and a committed candidate that produces reverse-lookup text.
2. Inspect the runtime values for:
   - candidate view `getLocationInWindow(...)`
   - toast measured height
   - root-window visible display frame / IME visible area
   - whether LINE uses fullscreen/extract mode, insets, or adjusted resize/pan behavior on the affected device.
3. Change the Android reverse-lookup display so it avoids covering the host input field. Candidate approaches:
   - Prefer rendering reverse-lookup text inside the existing candidate/input view area when possible, similar to the iOS candidate-bar path.
   - If a `PopupWindow` remains necessary, clamp or flip its Y position to stay within the IME-owned/candidate area instead of drawing into the host editor area.
   - Add a small fallback for constrained layouts: use candidate-bar inline text or a short in-keyboard toast when there is not enough safe space above the candidate row.
4. Add focused tests for the geometry helper(s), especially when the candidate row is adjacent to the visible host editor area and the popup would otherwise overlap it.

## Follow-up questions for the reporter

The reporter has now provided Android version, device model, LINE version, and LIME IME version in https://github.com/lime-ime/limeime/issues/124#issuecomment-4757147733.

Remaining useful follow-up, if the maintainer needs it before reproducing/fixing:

- Reverse-lookup source setting
- Exact operation sequence from typing Array roots to showing the floating reverse-lookup window
- Whether the same issue occurs in other apps with bottom message/input fields
- Whether LINE display/fullscreen mode, keyboard height, or font/display-size settings change the overlap

Because this was reported by email and the issue was created by `limeimetw`, do not post these as a public GitHub question unless the maintainer wants public follow-up. A private email follow-up may be better if the reporter is not using GitHub.

## Verification plan

- Manual Android verification:
  - LINE chat input with Array IM and reverse lookup enabled.
  - A non-LINE app with a bottom-aligned text field, to determine whether this is general geometry or LINE-specific.
  - At least one normal text editor / notes app to confirm the reverse-lookup display remains visible and readable.
- Regression checks:
  - Reverse lookup still appears after candidate commit when enabled.
  - Reverse lookup still hides on the next LIME key press.
  - Candidate bar, expanded candidate popup, and clear-code/dismiss controls remain usable.
- Automated checks:
  - Add unit/helper coverage for toast Y-position selection and no-overlap fallback.
  - Keep or update existing `reverseLookupUsesPersistentLimeToast()` / `nextKeyClearsPersistentLimeToast()` expectations according to the chosen UI path.

## Backlog / release follow-up

- Track as an active Android usability bug until a source fix lands.
- No Android APK retest request applies yet because the observed `6.1.22-2026` report is on the current `LIMEHD2026-6.1.22.apk` line and no targeted reverse-lookup placement fix has landed after it.
- No public reporter retest request should be posted until a newer Android APK contains the relevant reverse-lookup placement fix.
- iOS/TestFlight retest is not required for the Android `PopupWindow` overlap path unless separate iOS reverse-lookup layout evidence appears.
