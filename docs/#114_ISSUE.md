# Issue #114: Duolingo English candidate strip intermittently missing

## Live issue state

- Issue: https://github.com/lime-ime/limeime/issues/114
- Status: closed / reporter confirmed Android APK `LIMEHD2026-6.1.19.apk` improved the Duolingo English candidate-strip issue in comment `4701168618`; `limeimetw` posted closing acknowledgement `4715747759`
- Reporter: `SmithCCho`
- Current labels after triage: `bug`, `Usability`
- Assignee after triage: `jrywu`

## Problem statement

Reporter `SmithCCho` says that in the Duolingo Android app, LIME English candidates sometimes do not display correctly while Chinese candidates still display normally. The reporter later supplied device/version details: Samsung A16, Android 16 / One UI 8.5, LIME 6.1.18, and Duolingo 6.83.4; they noted earlier Duolingo versions had also shown the intermittent behavior. Only Duolingo is mentioned in the report so far.

The screenshots show the same Duolingo fill-in-the-blank style exercise around the text `Can I speak to you for fif____ minutes?`:

1. Abnormal English state: the LIME keyboard is in English/alphabet mode, `fif` is being composed/underlined in the exercise field, but the LIME candidate strip shows only the empty toolbar row (emoji/microphone area) and no English candidates.
2. Chinese state: switching to Chinese/table input in the same field shows table candidates normally.
3. Normal English state: another English attempt for `fif` shows the candidate strip with `fif`, `fifth`, `fifty`, `fifteen`, etc.

## Reproduction information from report

Confirmed from the report and follow-up comment `4697486430`:

- Platform/device: Android on Samsung A16.
- OS/UI: Android 16 / One UI 8.5.
- Reporter-tested baseline: LIME 6.1.18.
- Reporter-tested fix build: Android APK `LIMEHD2026-6.1.19.apk` (GitHub Contents blob SHA `b5cab1ec2cd8cb0c6cb4538a84b5562c3321feff`, size 14,053,598 bytes).
- App context: Duolingo 6.83.4 exercise input field; reporter says earlier Duolingo versions had also shown the intermittent behavior.
- Input mode: English candidates are affected; Chinese candidates are visible.
- Failure is intermittent: sometimes the English candidate strip appears normally, sometimes it stays empty.
- Reporter says recording the failure is difficult because it is infrequent: Duolingo may show a different exercise type such as voice input, so the reporter only knows whether the issue recurs when the next word-input exercise appears.

Additional details to collect if they become necessary for implementation:

- Whether the problem also happens in other Duolingo text fields or other English prediction fields.
- On the next recurrence, whether leaving/re-entering the Duolingo field, closing/reopening the exercise, or switching away from and back to LIME restores the candidate strip.
- If the team cannot reproduce locally, targeted Android logcat/debug output showing relevant `EditorInfo`, `InputConnection`, or LIME state when candidates disappear.

## Related prior context

Issue #103 covered general Android English prediction visibility and ranking. That scope was reporter-confirmed fixed in Android APK `LIMEHD2026-6.1.17.apk`, and the current mutable state says #103 should remain closed unless new English-candidate evidence appears.

#114 is not the same as #103's exact-match/ranking issue: here the candidate list can be normal for the same prefix (`fif`) but intermittently disappears only in a specific app context. Treat this as a new app-specific English candidate display/state-sync bug rather than reopening #103 directly.

## Relevant Android code paths inspected

Primary source areas:

- `LimeStudio/app/src/main/java/net/toload/main/hd/LIMEService.java`
- `LimeStudio/app/src/main/java/net/toload/main/hd/SearchServer.java`

Observed current behavior:

- `initOnStartInput(EditorInfo attribute)` disables prediction when the target text field advertises `TYPE_TEXT_FLAG_NO_SUGGESTIONS`, disables LIME prediction and uses completion behavior for `TYPE_TEXT_FLAG_AUTO_COMPLETE`, and otherwise allows English prediction in normal text fields.
- `updateEnglishPrediction()` builds candidates only when `mPredictionOn` and the English prediction preference are enabled.
- `updateEnglishPrediction()` checks the current `InputConnection` with `getTextBeforeCursor(...)` and `getTextAfterCursor(...)`; if the app returns context that does not match `tempEnglishWord` and the next character is not considered a boundary, the method can skip refreshing the candidate list.
- When English suggestions are shown, `buildEnglishPredictionCandidates(...)` always prepends the composing/self candidate, and `setEnglishPredictionSuggestions(...)` uses the no-highlight display path from the #103 fix.
- `clearSuggestions()` / empty-toolbar behavior can leave the embedded candidate area visible without candidate words, which is consistent with the abnormal screenshots.

Existing test coverage observed:

- `LIMEServiceTest.englishPredictionCandidatesKeepComposingWordWhenSuggestionsAreEmpty()` covers the #103 helper that keeps the typed word when dictionary suggestions are empty.
- `LIMEServiceTest.englishPredictionCandidatesKeepSuggestionsAfterComposingWord()` covers prepending the composing word before English suggestions.
- `CandidateViewTest.setSuggestionsWithoutHighlightLeavesNoSelectedCandidate()` covers the no-highlight English candidate display path.
- Current tests do not appear to cover app-specific `InputConnection` behavior where `getTextBeforeCursor(...)` / `getTextAfterCursor(...)` disagree with LIME's local `tempEnglishWord`, nor do they cover candidate-strip recovery/state rebuild if toggling Chinese/English mode turns out to be part of the failure path.

Fix/retest update:

- Commit `0a80a082eabf` changed `SearchServer.getMappingByCode(...)` so the English-fallback branch no longer calls `clearRunTimeSuggestion(true)` during background `prefetchCache` queries. The public retest request says this is intended to address the Duolingo English candidate state issue.
- APK `LIMEHD2026-6.1.19.apk` includes that change and was posted for reporter retest in https://github.com/lime-ime/limeime/issues/114#issuecomment-4698478642.
- Reporter `SmithCCho` confirmed in https://github.com/lime-ime/limeime/issues/114#issuecomment-4701168618 that after testing nearly ten Duolingo units on 6.1.19, English candidates appeared; they noted occasional slight candidate delay on Samsung A16 but described it as acceptable and said 99.9% of normal typing was smooth.
- `limeimetw` posted the closing acknowledgement in https://github.com/lime-ime/limeime/issues/114#issuecomment-4715747759, and the issue is closed.

## Root cause summary / remaining unknowns

Root cause is narrowed by the 6.1.19 fix and reporter confirmation, but the exact Duolingo-side trigger remains inferred rather than directly reproduced in logs.

The most likely investigation area is the interaction between Duolingo's exercise input field and LIME's English prediction state. The reporter says earlier Duolingo versions also showed the intermittent behavior, so this should not be framed as specific to Duolingo 6.83.4 yet. Because the failure is intermittent, a static `EditorInfo` classification alone may not explain the whole symptom; state carried across exercise/focus/mode transitions or inconsistent `InputConnection` context may be involved.

1. Duolingo may expose unusual `EditorInfo.inputType` flags, completion mode, or no-suggestions flags for some exercise states.
2. Duolingo's custom fill-in-the-blank field may return inconsistent cursor context through `InputConnection.getTextBeforeCursor(...)` / `getTextAfterCursor(...)` while the visible composing text still shows `fif` underlined.
3. LIME's `updateEnglishPrediction()` can then skip rebuilding English candidates or clear suggestions, leaving the empty toolbar row instead of the `fif` / `fifth` / `fifty` list.
4. The 6.1.19 fix identifies one concrete stale-state path: background English prefetch queries could reach the English-fallback branch and clear runtime phrase suggestion state, even though prefetch should only warm caches.
5. Chinese table candidates use a different lookup path, so they can still work in the same app context.

The reporter-confirmed result supports the background-prefetch/runtime-suggestion guard as the effective fix for the observed Duolingo symptom. Keep the broader `EditorInfo` / `InputConnection` mismatch notes as follow-up context only if the issue reopens with new evidence.

## Follow-up / reopened investigation direction

No routine follow-up is needed while #114 remains closed after the 6.1.19 confirmation. If the reporter reopens or provides new Duolingo evidence, restart from the following checks:

1. Reproduce in Duolingo with English prediction enabled and collect:
   - `EditorInfo.inputType`, `imeOptions`, and variation/flags when the field starts.
   - The `tempEnglishWord` value when `updateEnglishPrediction()` runs.
   - `getTextBeforeCursor(...)` / `getTextAfterCursor(...)` results when the candidate strip is empty vs normal.
2. If the editor context is inconsistent but `tempEnglishWord` is non-empty, consider making English prediction more resilient by still showing the composing/self candidate when the local composing buffer is valid, instead of silently leaving the candidate strip empty.
3. If mode-toggle recovery is implicated during reproduction, ensure toggling mode or restarting input clears/rebuilds English prediction state for the current composing text.
4. Add a focused regression test or testable helper around the `InputConnection`/`tempEnglishWord` gating logic so an app-specific context mismatch cannot hide all English candidates while a local English composition exists.

## Follow-up questions for reporter

Already collected from the reporter: Samsung A16, Android 16 / One UI 8.5, LIME 6.1.18, Duolingo 6.83.4, and the note that earlier Duolingo versions had also shown the intermittent behavior.

Only ask for additional details if they are needed for implementation/debugging:

1. Whether the failure happens only in this Duolingo exercise type, or also in other Duolingo text fields/apps.
2. If the issue recurs, whether leaving/re-entering the field, closing/reopening the exercise, or switching away from and back to LIME restores the candidate strip. The reporter already noted that recording the exact transition may be difficult because recurrence is infrequent and Duolingo alternates exercise types.
3. If local reproduction is not possible and more evidence is needed, consider asking for filtered logcat/debug output only with clear steps; do not make this the next routine reporter request by default.

The 6.1.19 retest is already reporter-confirmed in comment `4701168618`. Do not post another retest request or acknowledgement unless the reporter reopens or adds new evidence.

## Platform impact analysis

### Android

Confirmed reporter platform: Samsung A16 on Android 16 / One UI 8.5, originally on LIME 6.1.18. The reporter confirmed Android APK `LIMEHD2026-6.1.19.apk` improved the Duolingo English candidate-strip issue after nearly ten tested units, with only occasional slight candidate delay considered acceptable. The affected implementation is Android English prediction / runtime suggestion state in app-specific input fields; the pre-fix symptom is reporter-confirmed improved on 6.1.19. Chinese table input uses a separate candidate lookup path and appeared normal in the original screenshots.

### iOS

No iOS behavior is reported. iOS uses a different keyboard implementation and English completion flow, so the Android `LIMEService` / `InputConnection` fix path does not directly apply. If similar app-specific English-candidate evidence appears on iOS, triage it separately rather than treating this Android APK confirmation as iOS verification.

## Verification result

Reporter confirmation: https://github.com/lime-ime/limeime/issues/114#issuecomment-4701168618

1. Android APK `LIMEHD2026-6.1.19.apk` was installed/tested by the reporter in Duolingo.
2. After nearly ten Duolingo units, English candidates appeared instead of disappearing.
3. The reporter noted occasional slight candidate-display delay on Samsung A16 but described it as acceptable and said 99.9% of normal typing remained smooth.
4. Issue #114 was closed after the reporter confirmation and `limeimetw` closing acknowledgement.
5. If new evidence appears, regression-check #103 English exact-match/no-highlight cases and normal text fields outside Duolingo before deciding whether to reopen or split a new issue.

## Backlog status

`docs/BACKLOG.md` no longer needs an active #114 reporter-retest item because the reporter confirmed APK `LIMEHD2026-6.1.19.apk` improved the Duolingo English candidate-strip issue and the issue is closed. Keep #114 out of active follow-up unless it is reopened or new Duolingo/English-candidate evidence appears.
