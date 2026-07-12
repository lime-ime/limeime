# Issue #104 — Android Enter key commits related candidate after 6.1.16

## Current status

Resolved / closed. Reporter `Limeroshenko` confirmed Android APK `6.1.17` restores the expected Enter/Search/Return behavior after committed text leaves related candidates visible: Enter no longer sends the first related candidate and instead performs newline/search in the target field. Confirmation: https://github.com/lime-ime/limeime/issues/104#issuecomment-4641235114

Maintainer/automation added a `+1` reaction, posted closing acknowledgement https://github.com/lime-ime/limeime/issues/104#issuecomment-4641236316, and closed the issue as completed on 2026-06-07. The verified scope is the reporter's Android 6.1.17 retest of the original post-commit related-candidate Enter/Search/Return path; iOS parity remains source-audited/aligned but not reporter-tested through this issue.

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

Relevant Android code inspected in `LimeStudio/app/src/main/java/org/limeime/LIMEService.java` and `LimeStudio/app/src/main/java/org/limeime/candidate/CandidateView.java`:

- Soft-keyboard `onKey(...)` handles `MY_KEYCODE_ENTER` in the same candidate-selection branch as Space:
  - if `hasCandidatesShown` is true, it calls `pickHighlightedCandidate()`.
  - only when no candidate is picked does it send the raw Enter character.
- Physical-keyboard `onKeyDown(KEYCODE_ENTER, ...)` also checks `hasCandidatesShown` and calls `pickHighlightedCandidate()`, then blocks the key-up path with `hasEnterProcessed`.
- `commitTyped(...)` intentionally allows non-composing candidates such as related phrase or English suggestion records to commit when the user explicitly picks them, even when `mComposing.length() == 0`.
- `updateRelatedPhrase(...)` builds the post-commit related-candidate strip by calling `SearchSrv.getRelatedByWord(...)`; those records are created with `setRelatedPhraseRecord()` and `code = ""`, then passed through `setSuggestions(...)`.
- `CandidateView.takeSelectedSuggestion()` only returns true if `mSelectedIndex >= 0`; before the regression, related-candidate-only strips deliberately used `mSelectedIndex = -1`, so Enter fell through to the editor action.

This explains the reported symptom when related candidates remain visible after a normal commit: if the related strip has a selected/highlighted item, the existing Enter branch treats it as a normal candidate selection even though there is no active composing code.

## Pre-fix coverage gap

Before commit `1cb8daecdcb6dd5583542ec902fd3b1d0089b5b9`, tests existed for Enter key constants, broad `onKey`/`onKeyDown` branches, `pickHighlightedCandidate()`, candidate lists, and related-phrase/search-server behavior, but the inspected tests did not appear to assert the user-facing path:

- after a committed word,
- with only related/association candidates visible,
- pressing Enter should pass through to the target editor action rather than commit a related candidate.

That missing regression case likely allowed the 6.1.16 behavior to ship without a guard. Commit `1cb8dae` added focused selection-policy coverage for the source fix.

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

## Implemented source fix

Maintainer commit `1cb8daecdcb6dd5583542ec902fd3b1d0089b5b9` (`fix #104 android ios candidate highlight drift`, 2026-06-05) landed on `master` and closed the GitHub issue. It separates visible candidate-strip highlighting from `%limeendkey` commit resolution:

1. Android now uses `defaultHighlightedCandidateIndex(...)` for normal candidate-strip selection. It preserves the legacy active-composition rules but returns `-1` for related-only and English-suggestion lists, so browse-only strips are not highlighted by default.
2. Android `%limeendkey` handling now uses a dedicated `endkeyCommitCandidateForSuggestions(...)` resolver for exact/partial/punctuation commit targets instead of broadening normal strip selection.
3. Android regression coverage was added for related/English lists with no default highlight and for keeping end-key commit resolution separate from candidate-strip highlighting.
4. iOS parity was audited and aligned by splitting `CandidateSelectionPolicy.defaultHighlightedCandidateIndex(...)` from `LimeEndkeyPolicy.commitCandidateIndex(...)`, and by keeping related/English candidate lists unselected. Swift tests cover the selection-policy split and no-default behavior.

The fix is included in Android APK `LIMEHD2026-6.1.17.apk` (verified GitHub Contents blob SHA `4b0f42af2b9d97e9b9c1e87ec87bffa1271d1e2f`, size 13930960 bytes). Hermes reopened the issue and posted the scoped reporter retest request https://github.com/lime-ime/limeime/issues/104#issuecomment-4641196759; reporter `Limeroshenko` then confirmed the fixed behavior on `6.1.17` in https://github.com/lime-ime/limeime/issues/104#issuecomment-4641235114.

## Follow-up questions

The report plus root-cause attribution are sufficient to classify this as an Android regression. If a future retest on a fixed APK is inconsistent, ask the reporter for:

- input table/IM used,
- whether related-phrase/association candidate settings are enabled,
- the exact app/input field used in the attached video.

The Android `6.1.17` retest is now positive and the issue is closed. If the issue is reopened or a future retest is inconsistent, use the questions above to collect narrower reproduction details.

## Platform impact analysis

### Android

Confirmed reporter platform. Android source fix `1cb8daecdcb6dd5583542ec902fd3b1d0089b5b9` restores no-default-highlight behavior for related-only/post-commit candidate strips and keeps `%limeendkey` commit resolution separate. Android APK `6.1.17` contains the fix, and reporter `Limeroshenko` confirmed that Enter/Search/Return now passes through normally instead of committing the first related candidate.

### iOS

Not reported by the community reporter. iOS has a separate Swift keyboard implementation, so the Android regression did not directly prove user impact. Commit `1cb8daecdcb6dd5583542ec902fd3b1d0089b5b9` nevertheless completed the parity audit/alignment by separating normal highlighted-candidate selection from Lime end-key commit resolution and keeping browse-only related/English lists unselected.

## Verification plan

- Android unit/instrumentation coverage: construct a related-only candidate list and verify `defaultHighlightedCandidateIndex(...) == -1` / `CandidateView` has no highlighted item; simulate post-commit related candidates visible with no composing code, press Enter, and verify no related candidate is committed and the editor action/newline path is allowed. Commit `1cb8dae` added focused selection-policy coverage for this source fix.
- Android manual: reporter-confirmed on APK `6.1.17` that Enter/Search/Return no longer sends the first related candidate after a word is committed.
- Regression: active composing candidate selection with Space and valid selection keys still works; `%limeendkey`/`@limeendkey@` behavior from #96 remains unchanged.
- iOS audit: source parity was aligned in commit `1cb8dae`; iOS delivery/QA remains separate from this Android reporter confirmation.

## Current follow-up status

- Classification: Android bug / regression with iOS parity audit.
- Public issue: closed as completed on 2026-06-07 after reporter confirmation.
- Root-cause attribution: `35abf08da89ddec0b221fab5612a44cbd2ea03d4` introduced default-selection fallback `return 0`, which accidentally highlighted related-only candidates.
- Fix/APK status: Android APK `LIMEHD2026-6.1.17.apk` contains commit `1cb8dae`; verified APK blob SHA `4b0f42af2b9d97e9b9c1e87ec87bffa1271d1e2f`, size 13930960 bytes.
- Retest status: reporter `Limeroshenko` confirmed in https://github.com/lime-ime/limeime/issues/104#issuecomment-4641235114 that `6.1.17` restores the familiar behavior: Enter/Search/Return no longer sends the first related candidate and instead performs newline/search. Closing acknowledgement: https://github.com/lime-ime/limeime/issues/104#issuecomment-4641236316. Remove from active retest watch unless reopened or new evidence appears. iOS parity was audited/aligned in source, but this GitHub reporter retest is Android-only; iOS delivery remains normal TestFlight/App Store release-QA scope.
