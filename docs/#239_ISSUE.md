# Issue #239: Android Array 30 composing hint overlaps LINE message input

## Current status

- Live issue: https://github.com/lime-ime/limeime/issues/239
- Reporter: `walin333`
- Reported version: LIME IME v6.1.38
- Classification: plausible Android UI regression / usability defect, pending device and runtime confirmation
- Backlog: not added yet. The implementation direction is not confirmed, and the accepted candidate-layout document conflicts with the older #124 containment description about whether Android composing popups may extend above the candidate bar.

## Problem statement

While using Array 30 in LINE on Android, the reporter sees LIME's composing/root hint above the candidate row covering the text being entered in LINE's bottom message field. The attached screenshot marks the overlap area. The reporter asks whether LIME can add an option to disable this hint.

This is closely related to the behavior previously tracked in #124, but it is a new focused report against v6.1.38 after the v6.1.23 and v6.1.27 popup-placement changes. The #124 closeout explicitly says a later bottom-composer overlap on v6.1.27 or newer should be triaged as a new issue rather than reopening #124 automatically.

## Reproduction context

Known from the report:

1. Use LIME IME v6.1.38 on Android.
2. Select the Array 30 input method.
3. Enter text in LINE.
4. Observe the composing/root hint above the candidate row overlap the message input content.

Still needed:

- Device model and Android version.
- LINE version.
- Whether the overlap occurs on every Array 30 keystroke or only particular codes.
- Whether reverse-lookup text after committing a candidate also overlaps, or only the composing/root hint while typing.
- Display size, font size, and LIME candidate-font-size settings.
- Whether other bottom-composer apps reproduce the same geometry.

## Evidence inspected

- Live issue body, attachment metadata, labels, comments, assignees, events, and timeline on 2026-08-14. At triage time there were no comments, labels, assignees, linked commits, or timeline events.
- Reporter attachment: `https://github.com/user-attachments/assets/814f42bf-5e44-4ac4-bff4-0046095f305c`, a 600 × 600 Android screenshot. The issue text and marked region identify the visible symptom as a hint covering LINE's input content.
- Prior issue and analysis: `docs/#124_ISSUE.md` and live issue #124. #124 covered Android composing/root and reverse-lookup popups overlapping bottom message editors, shipped placement/readability changes in v6.1.23 and v6.1.27, and directs later recurrence reports to a new focused issue.
- Current v6.1.38 source on `origin/master`:
  - `LimeStudio/app/src/main/java/org/limeime/candidate/CandidateView.java`
  - `LimeStudio/app/src/main/java/org/limeime/candidate/CandidateInInputViewContainer.java`
  - `LimeStudio/app/src/main/java/org/limeime/LIMEService.java`
- Accepted candidate geometry document: `docs/CANDI_LAYOUT.md`, read in full, especially §1, §2, §6, and §8.

## Architecture preflight and constraint ledger

Affected subsystem: Android candidate-row composing/root hint and reverse-lookup popup placement.

Authoritative current document inspected:

- `docs/CANDI_LAYOUT.md`
  - §1 and §2 define the candidate bar and keyname/reverse-lookup surfaces.
  - §6 lists shared candidate-layout invariants.
  - §8 defines the Android backport target, including popup placement and the candidate-local notification surface.
- `docs/#124_ISSUE.md` records the accepted historical fix and post-release behavior for the same Android popup paths, but it is an issue analysis/closeout record rather than the general layout architecture.

Constraint ledger:

| Item | Current evidence |
|---|---|
| Required behavior | Composing/root and reverse-lookup information must remain readable without preventing the user from seeing or continuing text entry in the host editor. |
| Governing invariant | Candidate and hint state must remain synchronized with the active composing buffer, and candidate-row controls must stay usable. |
| Platform limit | Android `PopupWindow` can extend outside the IME-owned candidate area when clipping is disabled. The IME does not own LINE's editor geometry. |
| Removable behavior | A user preference could suppress the composing/root hint, but that is product work and is not yet approved. Moving or embedding the hint may be possible but must preserve candidate geometry and physical-keyboard behavior. |
| Consequence of proposed change | Suppressing the hint removes Array root feedback. Repositioning it inside the candidate row consumes candidate space. Allowing it above the row can overlap bottom-composer editors. |

Architecture conflict requiring maintainer resolution:

- `docs/#124_ISSUE.md` and comments/tests around `CandidateView.clampPopupYToImeArea(...)` describe keeping the popup inside the IME-owned area to avoid host-editor overlap.
- Current `docs/CANDI_LAYOUT.md` §8 says Android may continue showing popups above the candidate bar.
- Current production code defines `clampPopupYToImeArea(...)`, but the composing and lime-toast placement paths inspected do not call it. `doUpdateComposing()` still computes `candidateTop - popupHeight`, and `doShowLimeToast()` does the same for the toast path.

Because the accepted documents disagree, triage fails closed on the exact replacement behavior. The report remains a plausible regression, but this analysis does not yet promise either a disable switch or a particular popup placement.

## Existing test coverage and gap

`LimeStudio/app/src/androidTest/java/org/limeime/candidate/CandidateViewTest.java` includes unit-style instrumentation assertions for `clampPopupYToImeArea(...)` boundaries. Current production placement does not call that helper, so those assertions do not prove the reporter-visible path is contained.

The remaining gap is an integration/runtime check of the active in-input candidate row in a bottom-composer app on a real Android device, including the exact v6.1.38 artifact and display/font settings. A helper-only test cannot establish that the live `PopupWindow` uses the intended Y coordinate.

## Likely cause

The current leading hypothesis is that the normal in-input candidate row still uses the Android composing `PopupWindow` path because it has no embedded composing view. That path positions the hint immediately above the candidate row with clipping behavior that can draw into LINE's message editor. The containment helper added around #124 exists but is not wired into the inspected production placement path.

This is a source-backed explanation for why the reported geometry remains plausible, not yet proof of the exact device/runtime cause. Display scaling, candidate font size, and LINE window geometry may determine whether the overlap becomes visible.

## Android and iOS impact

### Android

- Confirmed reporter platform by screenshot metadata and Android build metadata.
- Plausibly affected path: `CandidateView.doSetComposing()` → `doUpdateComposing()` for the normal in-input candidate row.
- Reverse lookup may share adjacent popup geometry, but the new report explicitly identifies the composing hint and does not yet confirm reverse-lookup overlap.

### iOS

- No iOS failure is reported.
- iOS uses an in-candidate-bar composing/reverse-lookup label rather than Android's host-window `PopupWindow` placement path, so the exact Android overlap mechanism does not directly apply.
- Any proposed cross-platform “disable hint” preference would be product work and would require an explicit parity decision. This report alone does not establish an iOS defect.

## Investigation and solution options

1. Reproduce v6.1.38 Array 30 input in LINE on an Android device using the reporter's display/font settings.
2. Capture candidate-row and popup window coordinates for the active in-input path.
3. Resolve the architecture conflict with the maintainer:
   - contain the composing hint within the IME-owned candidate/keyboard area,
   - embed the hint into the candidate row,
   - or approve an optional per-user visibility setting.
4. Add a regression that exercises the production coordinate calculation, not only the currently unused helper.
5. Verify composing hints and reverse lookup separately in LINE and at least one other bottom-composer app.

## Verification plan and retest condition

- Focused automated coverage must prove the production composing path cannot place a popup over the host editor under the selected design.
- Build and run Android instrumentation tests on a device/emulator.
- Runtime-check Array 30 composing in LINE with default and enlarged display/font settings.
- Confirm candidate selection, candidate expansion, physical-keyboard candidate display, dismiss behavior, and reverse lookup still work.
- Do not ask the reporter to retest until a newer public Android build contains a relevant verified change.
