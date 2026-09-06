# Issue #253: Android auto Chinese punctuation strip is missing after candidate-cycle completion

Issue: https://github.com/lime-ime/limeime/issues/253

## Classification

Confirmed Android defect in the automatic Chinese-punctuation candidate lifecycle. Draft PR #255 isolates and gates stale asynchronous callback mutations, but source acceptance remains pending review and exact signed-build runtime validation.

Android LIME 6.1.38 with Array 30 and `auto_chinese_symbol` enabled fails to show the Chinese-punctuation strip after either deleting the final composing code or selecting a candidate that has no related phrase. In the same environment, dismissing active composition with the candidate-row X does show the strip. The first two results violate the accepted candidate-cycle contract in `docs/AUTO_CHINESE_PUNC.md`; the working X path confirms that the preference, Chinese-mode gate, punctuation data, and basic strip renderer are available in the reported session.

The separate request for idle Space to open punctuation is not part of this defect. Current accepted behavior reserves Space for a literal space when there is no active composition or committable candidate. Changing that interaction is unconfirmed product work and is not included in `fix#253`.

## Problem statement

With automatic Chinese punctuation enabled in Android LIME 6.1.38 and Array 30 active, two supported transitions to an empty candidate bar do not surface the punctuation strip:

1. Backspace removes the last composing code.
2. A selected candidate is committed and the related-phrase query has no result.

The candidate-row X remains a working control path: using it during active composition clears the composing text and surfaces the punctuation strip. The defect therefore appears to be path-specific state or sequencing around candidate-cycle completion, not a general inability to read the preference or render punctuation.

## Architecture preflight

Authoritative current references reviewed in full:

- `docs/AUTO_CHINESE_PUNC.md`
  - **Overview** and **Behaviour specification** require the strip whenever a populated Chinese candidate cycle becomes empty with the preference enabled.
  - **When the strip SHOWS** explicitly includes case (a), Backspace deleting the last composing character, and case (b), committing a word with no related phrase.
  - **State model** requires Android show paths to preserve `hasCandidatesShown` through `clearSuggestions()` so it can call `updateChineseSymbol()`.
  - **Code paths**, **Cross-platform parity matrix**, and **Status checklist** define Android as the established behavior for cases (a), (b), and (g), while also recording that the existing Android T-A-a test exercises the builder seam and T-A-b remains deferred rather than testing the complete production commit/query flow.
- `docs/CANDI_FUNCTION_KEYS.md`
  - **Candidate display states** distinguishes active composing candidates from browse-only related phrases and punctuation.
  - **Backspace** preserves the special punctuation-strip cancel gesture and requires ordinary composing Backspace to shrink or clear composition.
  - **Bugs — FIXED / Bug 2** requires Backspace on an already-visible related-phrase strip to dismiss that strip and delete committed text without surfacing punctuation.
- `docs/#78_ISSUE.md`
  - **Root cause and resolution** and **Android** preserve the `hasCandidatesShown = false` suppression only for Backspace on an already-visible related-phrase list. That suppression must not be generalized to Backspace deleting the final active composing code.

No accepted amendment, successor, or supersession note was found that changes the automatic-punctuation cases reported in #253. `docs/AUTO_CHINESE_PUNC.md` explicitly separates this post-composition strip from inline comma/period candidate ordering.

### Constraint ledger

| Item | Constraint |
| --- | --- |
| Required behavior | With `auto_chinese_symbol` enabled in Chinese mode, deleting the final composing code and committing a candidate with no related phrase must transition from the populated candidate cycle to the punctuation strip. Candidate-row X during composition must continue to do the same. |
| Governing invariant | The strip is transition-gated, not an unconditional idle bar: `hasCandidatesShown` represents a populated candidate cycle and `hasChineseSymbolCandidatesShown` identifies the punctuation strip itself. All ordinary show paths converge on `clearSuggestions()` → `updateChineseSymbol()`. |
| Platform limitation | Android candidate lookup and related-phrase lookup are asynchronous and host editors report composition/selection through `InputConnection`. Stale callbacks or editor lifecycle changes can reorder candidate updates, so runtime evidence must identify the last state mutation that wins. |
| Removable behavior | A path-specific reset, stale callback, or sequencing step that drops/overwrites the populated-cycle signal before the punctuation builder may be corrected. A broad removal of all candidate-state clearing is not safe. |
| Consequence of change | Showing punctuation whenever the bar is idle would break initial/idle behavior, the explicit X/Backspace dismissal contract, browse-only Space/Enter behavior, and #78's rule that Backspace on an already-visible related list deletes text without replacing that list with punctuation. |

## Current production flow

### Android

The expected source routes are present in both release tag `v6.1.38` and current `origin/master`:

- `handleBackspace()` with one composing character calls `clearComposing(true)`.
- `clearComposing(...)` empties the composing buffer and candidate list, then calls `clearSuggestions()` without explicitly clearing `hasCandidatesShown`.
- `clearSuggestions()` checks Chinese mode and `getAutoChineseSymbol()`, clears the view, and calls `updateChineseSymbol()` when `hasCandidatesShown` is still true.
- `updateChineseSymbol()` marks `hasChineseSymbolCandidatesShown = true` and sends the fixed punctuation list through `setSuggestions(...)`.
- `commitTyped(...)` snapshots the committed candidate, clears composition, and starts `updateRelatedPhrase(false)`.
- The empty-related result clears `committedCandidate` and calls `clearSuggestions()`.
- `dismissCandidateComposing()` uses the same `clearComposing(true)` builder route when the punctuation strip itself is not already showing, which matches the reported working X path.

The source therefore contains the intended builder calls, but the shipped behavior proves that direct source-path inspection is incomplete. The existing T-A-a test starts from synthetic `hasCandidatesShown = true` state and invokes `clearSuggestions()` directly. It does not execute `handleBackspace()` through the real candidate/query lifecycle. T-A-b is explicitly deferred. Neither test locks the reporter-visible paths that fail.

### iOS behavioral oracle

The iOS implementation independently preserves the accepted behavior:

- `handleBackspace()` with one composing character calls `clearComposing(force: true)`.
- `clearSuggestions()` shows punctuation only when the preference is on, Chinese mode is active, a candidate cycle existed, and punctuation is not already showing.
- `updateRelatedPhrase()` explicitly restores `hasCandidatesShown = true` before `clearSuggestions()` when the related query returns empty.
- Candidate-row dismiss routes active composition through `clearComposing(force: true)` while direct punctuation dismissal bypasses the builder.

No iOS runtime failure is reported. Android should retain this accepted behavior without introducing an Android-only product exception.

## Root cause and draft implementation status

Production implementation commit `e3e784f28db76c6c626ee9aee65f3f26d0735663` in draft PR #255 establishes a stale asynchronous-callback boundary. A candidate database query can return after Backspace has cleared the final composing code and replace the punctuation strip with obsolete candidates. Deterministic regressions also show that a delayed related-phrase query can restore stale content after dismissal and that delayed work can mutate candidate state after service destruction. Java thread interruption alone does not invalidate a backend or Binder result that still returns.

The draft makes candidate-query worker ownership local to each `LIMEService` instance and applies one generation contract across candidate, related-phrase, and English-prediction terminal mutations. It adds 196 lines of instrumentation coverage for stale candidate and related callbacks, service ownership, teardown, and the empty-related commit path. That implementation commit passed all 296 `LIMEServiceTest` cases on a Pixel 9 Pro API 36 emulator, and an independent review returned `READY` with no unresolved findings.

This evidence proves the stale-callback source boundary and the focused regression behavior. It does not yet prove that both reporter-visible Array 30 paths are fixed in a signed app. The required Claude Code review was attempted but could not authenticate because its OAuth session had expired. PR #255 therefore remains a draft and must not be presented as source-fixed or merge-ready until that review and exact signed-build/device validation succeed.

## Remaining implementation and acceptance work

1. Complete the required Claude Code review against the final exact PR head and resolve any source-backed blocker without broadening the fix.
2. Build an exact signed candidate and exercise both reported Array 30 transitions on an Android device: final-code Backspace and committing a candidate with no related phrase.
3. Confirm that punctuation remains the final visible candidate state after queued work settles, including related-phrase prediction enabled and disabled where applicable.
4. Recheck candidate-row X, punctuation dismissal and Backspace, related-list Backspace, English mode, preference off, idle Space, service teardown, and another table-based input method.
5. Keep PR #255 in draft status until the review and runtime gates pass. A passing focused test or full instrumentation class alone is not merge readiness.

Do not implement idle-Space punctuation under this defect. That is a separate cross-platform product decision for a later feature workflow if approved.

## Follow-up questions and evidence needed

- Whether the two failures reproduce with another table-based Android input method on the same build.
- Whether related-phrase prediction is enabled or disabled for the no-related candidate case. Both outcomes should ultimately surface punctuation when no real related list remains.
- An ordered DEBUG trace or deterministic test identifying whether punctuation is never built or is built and then overwritten.
- The exact candidate/code used for the empty-related selection, so runtime verification can repeat the same query result.

## Verification plan

### Android

- Reproduce both reported Array 30 workflows with `auto_chinese_symbol` enabled:
  - type one composing code, then Backspace
  - commit a candidate confirmed to have no related phrase
- Confirm the punctuation strip is the final visible candidate state in each path.
- Repeat with related-phrase prediction enabled and disabled.
- Repeat with at least one other table input method to distinguish shared lifecycle behavior from table-specific data.
- Verify candidate-row X during active composition still surfaces punctuation.
- Verify X while punctuation is already showing hides it and does not immediately rebuild it.
- Verify Backspace while punctuation is showing hides it without deleting committed text.
- Verify Backspace while a related-phrase list is showing dismisses the list and deletes one committed character without surfacing punctuation.
- Verify preference OFF and English mode never show the strip.
- Verify a fresh/idle candidate bar remains idle and Space/Enter insert literal whitespace rather than opening or committing punctuation.
- Run the existing `autoChinesePunc_T_A_*` tests plus the new end-to-end state-transition regressions, then verify the exact fixing build on an Android device.

### iOS

No iOS defect is established. Current iOS source implements the accepted cases (a), (b), and (g) and remains the behavioral oracle for this parity defect. Keep iOS source unchanged unless independent runtime evidence fails the same workflows. During coordinated verification, confirm on iPhone/iPad that final-code Backspace and empty-related commit still surface punctuation, while idle Space remains a literal space.

## Product-scope separation

The v5.2.4-530 idle-Space behavior is a distinct interaction request. Accepted current documents classify punctuation as a browse-only list and no-candidate state as ordinary input, so idle Space inserts a literal space. No `feat#253` entry is added without maintainer approval of a cross-platform 6.2.x interaction change.
