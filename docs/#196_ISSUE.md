# #196 — Android punctuation end-key no longer commits candidate and punctuation together

## Live issue state

- Issue: https://github.com/lime-ime/limeime/issues/196
- Status: closed/source-fixed by maintainer merge on 2026-07-25
- Reporter: `ejmoog`
- Reported version: Android 6.1.36
- Related completed scope: #95 / #96 introduced opt-in Lime end-key behavior and was verified on Android 6.1.16.
- Fix: PR #198, final head `74df302bfc7968450c5389f89d75ec9759d2ba6a`, merged as `a96dcef659aa20796f6cab2edefe091de24823de`.
- Distribution boundary: the current GitHub Release remains v6.1.36 at `4060a46e585c9e46321953736c60335f40f7db94`, which predates the fixing merge. Its live Android asset is `LIMEHD2026-6.1.36.apk` (7,114,460 bytes, SHA-256 `995462d0ffb61b8b4910efa9096b86bce4ecd39177ce47a5bf0b7918b666898e`). No reporter-testable release containing this fix exists yet.
- Review boundary: compact merged-tree review confirmed that the reporter's absent-root route uses the new literal `imkeys` check, but the adjacent unresolved declared-root route still consumes the key after changing `mComposing` without refreshing candidates when the combined code has no mapping. That can leave editor composition at `v,` while the candidate strip still represents `v`; the merged regression sets `mPredictionOn=false` and checks only `mComposing`, so it does not cover this stale-state path. Correct this release blocker before distributing the change.

## Problem statement

For a table that configures `,` and `.` as Lime end keys but does not declare them as table roots, pressing one of those keys during composition should commit the current candidate and then commit the corresponding full-width punctuation candidate. In Android 6.1.36, the reporter's example `v` then `,` commits `好`, but leaves the comma composition/candidates (`、`/`，`) in the candidate area instead of producing `好，` immediately.

## Reproduction steps

1. Use an Android table with `limeendkey=,.`, alphabetic `imkeys`, and the normal synthetic full-width comma/period candidates.
2. Type `v` so `好` is the selected candidate.
3. Press `,`.
4. Expected: `好，` is committed in one flow.
5. Reported: only `好` is committed and the punctuation remains in the candidate area.

## Evidence and regression boundary

| Boundary | Known-good | Failing |
|---|---|---|
| Version | Android 6.1.16, confirmed by this reporter in the #106 follow-up | Android 6.1.36, reported in #196 |
| End-key configuration | `limeendkey=,.` | Same intended configuration |
| Table roots | `,` / `.` not declared as roots for the affected table | Same reported input path |
| User action | compose a candidate, then press punctuation end key | `v`, then `,` |
| Result | current candidate and punctuation commit | current candidate commits, punctuation remains composing |

Commit `1fd48e6d` (first included in v6.1.28) unified Android/iOS `imkeys`-driven character acceptance and made Android `isKeyInImkeys()` return true for comma and period for every IM. That fallback is correct for ordinary composition because it preserves LIME's synthetic full-width punctuation candidates. The Android end-key dispatcher also reused this helper to decide whether an end key is a table-declared root. It therefore began treating comma/period as declared roots even when the table's actual `imkeys` contains only letters.

The v6.1.35 and v6.1.36 release APKs contain byte-identical bundled SQLite payloads, and the only relevant v6.1.35-to-v6.1.36 source change is unrelated phonetic keyboard metadata. The regression is therefore in the shared Android runtime logic already present before 6.1.36, not a changed punctuation database in 6.1.36.

## Root cause

Android conflates two different questions:

1. **May this key enter composition?** Comma and period are accepted globally so `，` / `。` candidates can be synthesized.
2. **Did this table explicitly declare the end key as a root?** This must inspect only the table's `imkeys` metadata.

`handleEndkeyCommit()` used the broad `isKeyInImkeys()` acceptance helper for question 2. For punctuation end keys absent from the declared roots, this chose `commitComposingWithAppendedEndkey()` rather than the existing path that commits the current candidate and then resolves the punctuation as a fresh one-key composition.

## Existing test coverage and gap

Android already tests:

- end-key opt-in and English-mode gating
- an end key explicitly present in `imkeys`
- a non-punctuation end key absent from `imkeys`, including current-candidate then fresh-trigger commit
- stale candidate resolution
- punctuation candidate selection

The missing case was the intersection of punctuation's global composition fallback with an opted-in punctuation end key absent from the table's declared `imkeys`. Existing tests used `;` for the absent-root path, so they did not detect the comma/period-specific helper behavior. Review also found an adjacent declared-root failure path: after appending a declared punctuation root, an unresolved combined code returned `false`, so `onKey()` fell through to `handleCharacter()` and could append the same punctuation a second time.

Focused Android instrumentation regressions now encode the exact semantic path with `v → 好`, `limeendkey=,.`, alphabetic `imkeys`, and `, → ，`, plus the declared-root unresolved-code and already-selected-candidate paths. Before the production changes, the absent-root case queries the combined code instead of resolving `v` and `,` separately, while the unresolved declared-root case returns `false` after one append and permits the outer key handler to append again. The production fix and tests merged in PR #198. The merged-tree production compile, Android-test compile, unit tests, and lint pass, but connected execution remains pending because no ADB target is available. Post-merge review found that the unresolved declared-root test prevents a double append but does not assert the editor composing text or refreshed candidate state.

### Follow-up correction: candidate strip left stale on an unresolved declared root

The post-merge review confirmed a real defect behind that gap. When a declared punctuation root is appended and the combined code has no committable candidate, `commitComposingWithAppendedEndkey()` consumed the key but discarded the `false` from `commitResolvedEndkeyComposing()` without refreshing the candidate view. The composing buffer and, under prediction, the editor advanced to the combined code `v,`, while `mCandidateView`, `mCandidateList`, and `selectedCandidate` kept representing the pre-append code `v`. The shared end-key test setup fixes `mPredictionOn=false` and the unresolved-root regression only asserts `mComposing`, so it never observed the editor/strip mismatch. The correction calls `updateCandidates()` whenever nothing is committed, reconciling the strip to the combined code — showing its candidates when they exist and clearing the stale strip when they do not. A new `mPredictionOn=true` regression asserts the editor moves to `v,` and the candidate view is refreshed. Verified on a Pixel 9 Pro API 37 emulator: production and Android-test sources compile, the new regression fails RED against the pre-correction code (`candidateView.clear()` never invoked) and passes GREEN with the correction, and the adjacent declared-root and punctuation end-key regressions continue to pass. Reporter/device retest of a newer build remains required before closing #196.

## Implemented source solution

- Preserve global comma/period acceptance for normal composition.
- Add a narrow declared-root check that reads only literal `imkeys` membership.
- Use that declared-root check only in Android end-key routing.
- Keep append-to-code behavior for tables that explicitly declare the end key as a root, and consume the already-appended key even when the combined code has no immediate candidate so outer routing cannot append it again.
- Keep tables without Lime end-key metadata unchanged.
- Follow up the merged unresolved declared-root branch so one append also refreshes editor/candidate state instead of leaving stale candidates.

## Platform impact analysis

### Android

Confirmed affected. The reporter tested Android 6.1.36, and the Android source path reproduces the routing error. The Android-only source fix merged as `a96dcef659aa20796f6cab2edefe091de24823de`; delivery and reporter-visible verification remain pending because v6.1.36 predates that merge, and the adjacent declared-root stale-candidate blocker must be corrected first.

### iOS

The original routing conflation is not present. iOS separates ordinary punctuation acceptance (`KeyboardViewController.isKeyInImkeys`, which accepts comma/period globally) from end-key root detection (`LimeEndkeyPolicy.isKeyInImkeys`, which checks literal `imkeys` membership). Its end-key dispatcher already uses the literal policy helper, so the reporter's alphabetic-`imkeys` configuration routes correctly and commits `好，`.

However, the follow-up hardening was missing on the declared-root path (a table that lists the punctuation literally in `imkeys` and as a Lime end key). iOS `commitComposingWithAppendedEndkey()` returned `commitResolvedEndkeyComposing()` directly, so an unresolved combined code returned `false`; `handleLimeEndkeyCommit()` then propagated that `false` and the caller (`if handleLimeEndkeyCommit(code) { … }; handleCharacter(code)`) re-processed the key, double-inserting the punctuation the append had already inserted, and never refreshed the candidate strip. This is the iOS parallel of the Android `#198` (consume-once) and `#206` (refresh strip) fixes.

Android-parity tests in `KeyboardViewControllerTest` confirmed the gap on the booted iPhone 17 Pro Max simulator: a behavioral test shows the declared root is re-accepted into composing (the double-insert mechanism), and two source-parity tests failed RED against the pre-fix source. The iOS correction mirrors Android — consume the appended root once and call `updateCandidates()` when nothing commits — after which the parity tests pass GREEN and the existing end-key routing/policy tests continue to pass. Reporter/device retest of a newer build remains required before closing #196.

## Follow-up questions

No additional reporter information is required. The maintainer closed the issue when PR #198 merged. Correct the merged declared-root stale-candidate blocker, complete connected/runtime verification, and do not post a retest request until a newer reporter-testable Android build contains the corrected source; then request confirmation of the exact `v` then `,` → `好，` behavior.

## Verification plan

1. Add a focused regression proving that an unresolved declared punctuation root updates editor composition and candidate state exactly once, then correct the stale-state path.
2. Run the new focused Android instrumentation tests, including the unresolved declared-root and already-selected-candidate paths, on an ADB target.
3. Run the adjacent Android end-key tests and full `LIMEServiceTest` class on an ADB target.
4. Run the remaining full connected instrumentation gates; merged-tree Android unit tests, lint, production compilation, and Android-test compilation passed at exact merge `a96dcef659aa20796f6cab2edefe091de24823de` during closeout.
5. On an Android emulator/device, configure a table with alphabetic `imkeys` and `limeendkey=,.`, then verify `v` followed by `,` commits `好，` without leaving punctuation composing.
6. Verify an explicitly declared punctuation root still follows the append-to-code behavior, and an unresolved combined code keeps exactly one appended punctuation root with matching editor and candidate state.
7. Verify a table without `limeendkey` retains ordinary comma/period composition behavior.
8. Ask the reporter to retest only after a newer Android build containing the corrected source is available. The issue remains closed by explicit maintainer action until that boundary exists.
