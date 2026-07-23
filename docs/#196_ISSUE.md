# #196 — Android punctuation end-key no longer commits candidate and punctuation together

## Live issue state

- Issue: https://github.com/lime-ime/limeime/issues/196
- Status: open
- Reporter: `ejmoog`
- Reported version: Android 6.1.36
- Related completed scope: #95 / #96 introduced opt-in Lime end-key behavior and was verified on Android 6.1.16.

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

The missing case was the intersection of punctuation's global composition fallback with an opted-in punctuation end key absent from the table's declared `imkeys`. Existing tests used `;` for the absent-root path, so they did not detect the comma/period-specific helper behavior.

A focused Android instrumentation regression now reproduces the exact semantic path with `v → 好`, `limeendkey=,.`, alphabetic `imkeys`, and `, → ，`. It failed before the production change because the runtime queried the combined code instead of resolving `v` and `,` separately.

## Proposed solution

- Preserve global comma/period acceptance for normal composition.
- Add a narrow declared-root check that reads only literal `imkeys` membership.
- Use that declared-root check only in Android end-key routing.
- Keep the existing behavior for tables that explicitly declare the end key as a root.
- Keep tables without Lime end-key metadata unchanged.

## Platform impact analysis

### Android

Confirmed affected. The reporter tested Android 6.1.36, and the Android source path reproduces the routing error. The fix is Android-only and should restore the reporter-confirmed 6.1.16 semantics without removing comma/period candidate synthesis.

### iOS

Not confirmed affected. iOS separates ordinary punctuation acceptance (`KeyboardViewController.isKeyInImkeys`, which accepts comma/period globally) from end-key root detection (`LimeEndkeyPolicy.isKeyInImkeys`, which checks literal `imkeys` membership). Its end-key dispatcher already uses the literal policy helper, so the Android conflation is not present in the inspected iOS source. No iOS production change is proposed.

## Follow-up questions

No additional information is required for the source-level fix. Reporter/device retest remains required before closing the community issue.

## Verification plan

1. Run the new focused Android instrumentation test and preserve RED/GREEN output.
2. Run the adjacent Android end-key tests and full `LIMEServiceTest` class.
3. Run Android unit, lint, Android-test compile, and full connected instrumentation gates.
4. On an Android emulator/device, configure a table with alphabetic `imkeys` and `limeendkey=,.`, then verify `v` followed by `,` commits `好，` without leaving punctuation composing.
5. Verify an explicitly declared punctuation root still follows the append-to-code behavior.
6. Verify a table without `limeendkey` retains ordinary comma/period composition behavior.
7. Ask the reporter to retest only after a newer Android build containing the fix is available. Keep #196 open until reporter confirmation or explicit maintainer direction.
