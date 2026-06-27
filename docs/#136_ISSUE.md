# Issue #136: Private email report about Space candidate switching and emoji search regressions

## Status

- Live issue: https://github.com/lime-ime/limeime/issues/136
- Source: private project email report summarized by `limeimetw`; reporter identity and email address are intentionally not public.
- Classification: GitHub labels `bug` + `Usability`
- Current state: open, needs maintainer investigation.
- Public acknowledgement: none needed yet because the GitHub issue was created by the project account from a private email report.

## Problem statement

The private reporter says the newer LIME HD build appears to differ from the older app in two user-visible areas:

1. After pressing Space, the older candidate/character left-right switching behavior may no longer be available or may have changed.
2. Emoji lookup no longer finds the expected emoji when typing or selecting the character/key path described by the reporter as `人(O)`.

The report does not yet include device model, Android version, exact LIME version, IM table, screenshots/video, or a step-by-step reproduction. Treat both symptoms as plausible regressions, but keep the exact root cause open until the reporter path is reproduced or clarified.

## Current evidence from source inspection

### Android Space / candidate switching path

- `LIMEService.onKey(...)` handles left/right soft-key events by sending DPAD left/right with `hasCandidatesShown` context, so candidate navigation still has a code path when the relevant arrow keys are pressed.
- The Space branch in `LIMEService.onKey(...)` commits or picks the highlighted candidate when candidates are shown for non-English, non-phonetic input. If no candidate is picked, it sends a literal Space. This makes the Space behavior sensitive to whether the candidate strip already has a highlighted/default item.
- `CandidateView` still contains the old swipe behavior split: `candidate_switch=false` would use horizontal swipes as previous/next candidate selection, while `candidate_switch=true` uses free scrolling.
- `LIMEPreferenceManager.getSelectDefaultOnSliding()` now always returns `true`, with comments saying the old `candidate_switch` UI was removed and the stored value is ignored. This is a plausible explanation for a user remembering older left/right candidate-switch behavior and seeing only free-scroll behavior in newer builds.

Relevant files inspected:

- `LimeStudio/app/src/main/java/net/toload/main/hd/LIMEService.java`
  - `onKey(...)` Space/Enter candidate handling and left/right key handling.
  - `pickHighlightedCandidate()` / `pickCandidateManually(...)` candidate commit paths.
- `LimeStudio/app/src/main/java/net/toload/main/hd/candidate/CandidateView.java`
  - `onScroll(...)` switches between free-scroll and previous/next behavior based on `getSelectDefaultOnSliding()`.
- `LimeStudio/app/src/main/java/net/toload/main/hd/global/LIMEPreferenceManager.java`
  - `getSelectDefaultOnSliding()` currently always returns `true`.
- `docs/IM_SERVICE.md`
  - Existing documentation still describes `candidate_switch` as free-scroll versus left/right previous/next candidate behavior.

### Android emoji search path

- Emoji search is implemented in `LIMEService` as a custom search field plus LIME keyboard/candidate handling.
- `enterEmojiSearchMode()` chooses the search keyboard mode from the source language mode.
- `handleEmojiSearchKey(...)` directly appends printable ASCII keys only when `shouldEmojiSearchConsumePrintableKey(primaryCode, mEnglishOnly)` is true.
- `shouldEmojiSearchConsumePrintableKey(...)` currently returns true only for English-mode printable ASCII keys.
- In Chinese IM mode, raw composing-code keys should go through normal IM composition and a picked non-emoji, non-composing-code candidate can be appended to the emoji search query through `appendPickedCandidateToEmojiSearch(...)`.
- Existing Android tests cover the helper policy: English printable keys bypass composing, Chinese printable keys do not, selected Chinese candidates can become emoji search text, and raw composing-code records should not become search text.

Relevant files inspected:

- `LimeStudio/app/src/main/java/net/toload/main/hd/LIMEService.java`
  - `enterEmojiSearchMode()`
  - `handleEmojiSearchKey(...)`
  - `shouldEmojiSearchConsumePrintableKey(...)`
  - `appendPickedCandidateToEmojiSearch(...)`
  - `shouldAppendPickedCandidateToEmojiSearch(...)`
  - `pickCandidateManually(...)`
- `LimeStudio/app/src/androidTest/java/net/toload/main/hd/LIMEServiceTest.java`
  - `emojiSearchPrintableKeysOnlyBypassComposerInEnglishMode()`
  - `emojiSearchCandidatePickPolicySeparatesEmojiFromComposedText()`

## Likely root cause / investigation direction

This issue likely has two related but separate investigation tracks:

1. **Candidate switching / Space behavior:** the removed `candidate_switch` UI and `getSelectDefaultOnSliding()` forcing free-scroll may have removed an older swipe-left/right candidate-selection behavior that the reporter still relies on. Separately, the Space branch may now commit the highlighted/default candidate earlier than the reporter expects, depending on candidate state.
2. **Emoji search:** the current emoji search design depends on the exact mode and candidate-pick path. English printable keys write directly to the search query, while Chinese IM keys must first compose/select a candidate before that candidate text becomes the emoji search query. The reporter's `人(O)` path needs to be reproduced to determine whether the intended Chinese candidate-pick search path fails, whether emoji data/keyword matching changed, or whether the reporter expects raw table-code input to search emoji directly.

Do not treat either track as fixed until a newer build with a targeted change is available and the private reporter can verify by email.

## Existing test coverage / gaps

- Android has helper-level tests for emoji search mode-key handling, printable-key consumption, and candidate-pick policy.
- Android has broad `onKey(...)` coverage, but the existing tests around Space appear mostly branch/constant oriented rather than reproducing a real candidate-strip state where Space plus left/right switching can be validated end-to-end.
- No focused regression test currently verifies the old `candidate_switch=false` swipe-left/right previous/next behavior because the getter now forces `true`.
- No focused test currently reproduces the exact `人(O)` emoji lookup path described by the reporter.

## Platform impact

### Android

Confirmed scope for investigation. The public issue title says LIME HD, and the inspected Android code contains both the `candidate_switch`/Space candidate path and the emoji search implementation that match the report.

### iOS

Possible parity risk only, not confirmed by the report. iOS also has candidate-bar and emoji/search-related code and a `candidate_switch` preference mirror, but this report does not identify iOS and the Android implementation has distinct `LIMEService` paths. If the product decision is to restore or change candidate switching semantics, audit iOS candidate-bar parity separately before claiming cross-platform coverage.

## Proposed next steps

1. Ask or infer from the private email thread the reporter's exact LIME version, Android version, device model, IM table, and whether the Space/left-right behavior refers to candidate-strip swiping, arrow keys, or hardware-key navigation.
2. Reproduce the Space path with the reporter's IM table and candidate state. Specifically check whether the removed `candidate_switch` previous/next mode is the missing behavior, or whether Space is committing a highlighted candidate too early.
3. Reproduce the emoji lookup path with the same IM table and source/search mode: type/compose `人`, pick it into emoji search, and verify whether the expected emoji appears.
4. If regression is confirmed, implement targeted Android tests before changing behavior:
   - candidate switching/Space candidate-state regression test;
   - emoji search test for selected Chinese candidate text such as `人` producing emoji-search results.
5. If a fix lands, include it in a newer Android APK/Google Play build and route verification through the private email reporter.

## Retest condition

Do not ask for retest on the current build. Request private reporter verification only after a newer Android build contains a targeted candidate-switching and/or emoji-search fix, and specify which symptom each build is expected to address.
