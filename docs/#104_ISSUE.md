# Issue #104 — Android Enter key commits related candidate after 6.1.16

## Problem statement

Community reporter `Limeroshenko` reports a regression in Android APK `6.1.16`: after committing a word, when the candidate strip still shows related/association candidates, pressing Enter commits the first/highlighted candidate instead of passing Enter through to the target app.

Expected behavior from the reporter's pre-6.1.16 usage: Enter should perform the editor action such as newline or search after the main word has already been committed.

Issue: https://github.com/lime-ime/limeime/issues/104
Evidence: reporter attached a video at https://github.com/user-attachments/assets/6173a1f7-c44e-4cf6-9ecf-aa6f4e49d48a

## Reproduction details from the report

1. Install/use Android LIME IME `6.1.16`.
2. Type and commit a word.
3. Leave the related/association candidate strip visible after the word.
4. Press Enter in an editor/search field.
5. Observed: LIME sends the first candidate from the candidate strip.
6. Expected: Enter should reach the editor and perform newline/search/action without forcing the user to delete the previous word just to clear the related candidate strip.

The report specifically compares against `6.1.15` and earlier behavior.

## Current code observations

Relevant Android code inspected in `LimeStudio/app/src/main/java/net/toload/main/hd/LIMEService.java` and `LimeStudio/app/src/main/java/net/toload/main/hd/candidate/CandidateView.java`:

- Soft-keyboard `onKey(...)` handles `MY_KEYCODE_ENTER` in the same candidate-selection branch as Space:
  - if `hasCandidatesShown` is true, it calls `pickHighlightedCandidate()`.
  - only when no candidate is picked does it send the raw Enter character.
- Physical-keyboard `onKeyDown(KEYCODE_ENTER, ...)` also checks `hasCandidatesShown` and calls `pickHighlightedCandidate()`, then blocks the key-up path with `hasEnterProcessed`.
- `commitTyped(...)` intentionally allows non-composing candidates such as related phrase or English suggestion records to commit when the user explicitly picks them, even when `mComposing.length() == 0`.
- `updateRelatedPhrase(...)` builds the post-commit related-candidate strip by calling `SearchSrv.getRelatedByWord(...)`; those records are created with `setRelatedPhraseRecord()` and `code = ""`, then passed through `setSuggestions(...)`.
- `CandidateView.takeSelectedSuggestion()` only returns true if `mSelectedIndex >= 0`; before the regression, related-candidate-only strips deliberately used `mSelectedIndex = -1`, so Enter fell through to the editor action.

This makes the reported symptom plausible when related candidates remain visible after a normal commit: if the related strip now has a selected/highlighted item, the existing Enter branch treats it as a normal candidate selection even though there is no active composing code.

## Existing coverage / gap

Relevant tests exist for Enter key constants, broad `onKey`/`onKeyDown` branches, `pickHighlightedCandidate()`, candidate lists, and related-phrase/search-server behavior, but the inspected tests do not appear to assert the user-facing path:

- after a committed word,
- with only related/association candidates visible,
- pressing Enter should pass through to the target editor action rather than commit a related candidate.

This missing regression case likely allowed the 6.1.16 behavior to ship without a guard.

## Root cause and causal commit

Root cause: related/association candidate lists should be browse-only after a word has already been committed, so they should not have a default highlighted/selected item. Android Enter handling has long selected `CandidateView.mSelectedIndex` when candidates are visible; that was safe for related-only strips only while their selected index stayed `-1`.

Causal commit identified by `git blame` / `git show`: `35abf08da89ddec0b221fab5612a44cbd2ea03d4` (`fix(android #93 #96): align lime metadata and limeendkey`, 2026-06-04). This commit refactored default candidate selection into `LIMEService.defaultSelectedCandidateIndex(...)` and changed both service-side `selectedCandidate` and UI-side `CandidateView.mSelectedIndex` to use the same helper.

The regression-inducing detail is the helper fallback:

```java
for (int i = 0; i < suggestions.size(); i++) {
    if (isDefaultCommitCandidate(suggestions.get(i))) {
        return i;
    }
}
return 0;
```

For a related-only suggestion list, no item is an exact/partial/code/punctuation default-commit candidate, so the helper returns `0`. That gives the first related candidate a highlight/selected index. Pressing Enter then calls `pickHighlightedCandidate()`, `CandidateView.takeSelectedSuggestion()` returns true, and `pickCandidateManually(0)` commits the related candidate.

Before `35abf08d`, `CandidateView.setSuggestions(...)` explicitly kept related phrases, Chinese punctuation-symbol group candidates, and English suggestions unhighlighted:

```java
} else {
    // no default selection for related phrase, chinese punctuation symbols1 and English suggestions  Jeremy '15,6,4
    mSelectedIndex = -1;
}
```

So the fix should restore the old no-default-highlight rule for post-commit related/association candidates while preserving the intended #96 behavior for active composition and Lime end-key resolution.

## Fix approach

The source fix restores the pre-`35abf08d` default-highlight rules used by `CandidateView` / visible candidate strips:

1. If the second candidate is an exact/partial match to the current code, default-select index `1`.
2. Else if the first candidate is the composing-code or runtime-built-phrase record, default-select index `0`.
3. Else leave no default selected item (`-1`).

That means related/association-only strips, Chinese punctuation-symbol-only strips, and English suggestions do not get an automatic highlighted/default item. Enter/Search/Return can therefore pass through after composition has already ended.

`%limeendkey` / `@limeendkey@` is handled separately: end-key resolution now uses a private end-key candidate resolver that can still choose exact/partial/chinese-punctuation candidates for explicit end-key commits without changing the visible/default candidate selection rules. This keeps #96 behavior decoupled from the candidate-strip default item.

## Follow-up questions

Current report is sufficient to classify this as a plausible Android regression. If reproduction is inconsistent, ask the reporter for:

- input table/IM used,
- whether related-phrase/association candidate settings are enabled,
- the exact app/input field used in the attached video.

Do not ask for retest until a newer APK/build contains a targeted fix.

## Platform impact analysis

### Android

Confirmed reporter platform. The inspected Android `LIMEService` candidate/Enter handling plausibly matches the symptom in `6.1.16`. Android needs a source fix plus an APK retest.

### iOS

Not reported. iOS has a separate Swift keyboard implementation, so Android `LIMEService.java` state handling does not directly apply. Still, iOS should receive a light parity audit for return-key behavior when related candidates remain visible after commit, especially if the intended product rule is that Enter/Search/Return should pass through once composition has ended.

## Verification plan

- Android unit/instrumentation coverage: added coverage for legacy default-selection rules and related-only candidate lists returning `-1` / no default selected candidate. Existing end-key coverage now uses a Chinese-punctuation record (not just an exact-match record) for the fresh trigger candidate.
- Android manual: in a normal multiline text field, press Enter after committing a word with related candidates visible and confirm a newline occurs.
- Android manual: in a browser/search field, press Enter/Search after committing a word with related candidates visible and confirm search/action runs.
- Regression: active composing candidate selection with Space and valid selection keys still works; `%limeendkey`/`@limeendkey@` behavior from #96 remains unchanged.
- iOS audit: confirm return/search key behavior with visible related candidates after commit, or document that iOS behavior is already independent.

## Current follow-up status

- Classification: Android bug / regression.
- Public issue: open, source fix prepared in branch `fix/issue-104-restore-default-candidate`.
- Root-cause attribution: `35abf08da89ddec0b221fab5612a44cbd2ea03d4` introduced default-selection fallback `return 0`, which accidentally highlights related-only candidates.
- Validation in WSL: Java/app and androidTest compilation pass; connected instrumentation execution is blocked by no attached Android device/emulator.
- Retest condition: wait for a newer Android APK than `6.1.16` containing the targeted Enter/related-candidate fix before asking the reporter to retest.
