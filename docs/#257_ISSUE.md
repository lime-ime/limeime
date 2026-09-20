# Issue #257: iOS HSU phonetic multi-key remapping uses the wrong positional exception

## Current status

- Issue: https://github.com/lime-ime/limeime/issues/257
- State: **confirmed iOS defect — draft PR #258 under validation**
- Classification: community-created iOS phonetic-input defect
- Reported environment: LIME 6.1.38 on iOS 27, built-in Phonetic IM, HSU (許氏) phonetic keyboard
- Android: reporter-confirmed working and used as the behavioral oracle
- Implementation: draft PR #258, https://github.com/lime-ime/limeime/pull/258
- Last reconciled: 2026-09-20

## Problem statement

With the built-in Phonetic input method configured for the HSU keyboard on iOS, valid multi-key HSU sequences do not produce their expected candidates. The reporter supplied screenshots and two concrete comparisons:

- `xmf` should produce `晚`, as it does on Android, but iOS leaves the raw input without that candidate.
- `hnf` should produce `很`, but the same iOS failure occurs.

The active input method and HSU keyboard configuration are visible in the supplied Settings screenshots. No Android defect is established.

## Architecture preflight

The affected subsystem is the Phonetic query pipeline from `SearchServer` to `LimeDB`, specifically HSU positional remapping before the phonetic-table lookup and dual-code expansion.

Authoritative design and implementation records reviewed in full:

- `docs/IM_SERVICE.md`, especially §5 **Phonetic Keyboard Code Remapping** and §6 **Query Pipeline**. It defines Android as the porting behavior and limits the HSU `a/e/s/d/f/j` forced-initial exception to a **single-character** code.
- `docs/PHONETIC_KEYBOARD.md`, especially §5 **Code Remapping** and §11 **Testing**. It requires iOS HSU remapping and candidate results to match Android.
- `docs/TODO_IOS_GAPS.md`, **Phonetic Code Remapping (spec §5)**. It records HSU dual remapping with position detection as required iOS behavior.
- `docs/LIMEIME_ARCHITECTURE.md`, **Architecture Principles**, **Query Path**, and **Access Patterns**. Candidate queries flow through `SearchServer`, while remapping and dual-code expansion remain in `LimeDB`.

Current production flow reviewed:

1. `KeyboardViewController` refreshes `phonetic_keyboard_type` and passes it to `SearchServer`.
2. `SearchServer.getMappingByCode` delegates the active phonetic query to `LimeDB`.
3. `LimeDB.preProcessingRemappingCode` selects HSU dual remapping.
4. `LimeDB.applyDualRemap` currently tests `HSU_ALWAYS_INITIAL_CHARS` for every character position.
5. The remapped code and its dual alternatives are queried in the bundled phonetic table.

### Constraint ledger

| Dimension | Required invariant |
|---|---|
| Required behavior | Valid HSU key sequences must resolve to the same canonical phonetic codes and candidates as Android. |
| Governing invariant | HSU `a/e/s/d/f/j` uses the forced-initial exception only when the entire composing code has one character. In longer input, ordinary position and syllable-boundary rules apply. |
| Platform limit | iOS keyboard extensions do not provide Android's composing API, but that does not constrain deterministic code remapping or SQLite lookup. |
| Removable behavior | The unconditional per-character application of the forced-initial set in Swift is not required by the design and diverges from Android. |
| Proposed-change consequence | Scope the forced-initial exception to one-character input. Preserve all remap tables, syllable-boundary detection, dual-code expansion, cache ownership, and standard/ETEN behavior. |

**Architecture-conflict verdict:** The expected behavior and narrow correction do not conflict with the accepted architecture. They restore the explicit Android-parity contract at the existing `LimeDB` remapping layer.

## Root cause

The iOS port widened Android's single-character HSU exception into an every-position exception.

Android chooses the forced-initial HSU map only when `code.length() == 1` and the sole key is in `a/e/s/d/f/j`. For multi-character input, it chooses initial or final remapping from the character position and the preceding syllable boundary.

Swift's `applyDualRemap` instead evaluates `alwaysInitial.contains(ch)` inside the loop without checking the total input length. Consequently, the final `f` in both reported three-key sequences is incorrectly treated as an initial:

- Correct Android-equivalent path: `xmf` remaps to `ja3`, whose HSU dual expansion includes `j03`. The bundled phonetic table contains `j03 → 晚`.
- Current iOS path: `xmf` remaps the trailing `f` with the initial table, producing a `z` ending and missing `晚`.
- Correct Android-equivalent path: `hnf` remaps to `cs3`, whose dual expansion includes `cp3`. The bundled phonetic table contains `cp3 → 很`.
- Current iOS path again remaps the trailing `f` as initial and misses `很`.

This is an iOS source defect rather than a missing table record, user configuration issue, or iOS 27 platform limitation.

## Proposed solution

1. Add focused iOS regression tests for HSU multi-key positional remapping using `xmf → 晚` and `hnf → 很`.
2. Restrict the `alwaysInitial` exception in `applyDualRemap` to single-character input, matching Android.
3. Preserve the existing one-character exception tests and add multi-key cases where `f` and the other exception keys occur in final positions.
4. Make the single-character branch total so ETEN26/HSU keys outside the forced-initial set explicitly use the final map, matching Android.
5. Verify that standard Phonetic, ETEN 41-key, and ETEN 26-key remapping remain unchanged.

Draft PR #258 implements the narrow exception-scope correction, aligns both the query
remapper and `buildKeyNameDual` composing-display syllable-boundary guards with Android,
and makes the single-character dual-map branch explicitly choose the final map for keys
outside the forced-initial set. Review found that the first candidate-lookup regression was
not executable: a fresh `LimeDB` creates no `phonetic` table, and the non-throwing seed
overload silently discarded both missing-table errors. Commit `3d767bc8` corrects the fixture
by creating a real `phonetic` table and using the throwing seed path. The corrected focused
XCTest passed on an iPhone 17 Pro iOS 26.5 simulator, and the updated Linux contract passes
all four checks. On exact head `684a45b40067d4dd2ee38d116d79279aba5297db`, the full
XCTest suite subsequently passed 1,194 tests with 5 skips and 0 failures, and an independent
review found no blocker. A pre-fix native RED replay did not complete before the simulator
runs timed out, so that RED evidence and iPhone/iPad keyboard-extension runtime validation
remain outstanding. The PR remains draft and is not candidate-ready.

## Follow-up questions

No reporter clarification is required to establish the defect. Runtime validation still needs to confirm the corrected path on an iPhone/iPad keyboard extension using the built-in Phonetic table and HSU layout.

## Verification plan

### Completed source, data, and iOS checks

- [x] The corrected focused XCTest GREEN result passed at fixture commit `3d767bc8`; its production and test files are byte-identical at exact head `684a45b40067d4dd2ee38d116d79279aba5297db`.
- [x] One-character assertions cover every forced-initial HSU key, and the bundled phonetic database contains `j03 → 晚` and `cp3 → 很`.
- [x] The focused composing-display regression covers the HSU and ETEN26 syllable-boundary guard at and after input position 1.
- [x] The full applicable iOS XCTest suite passed 1,194 tests with 5 skips and 0 failures on exact head `684a45b40067d4dd2ee38d116d79279aba5297db`.
- [x] Source-boundary/structural tests and `git diff --check` passed on that exact head.
- [x] Exact-head independent review found no blocker in remapping parity or the adjacent ETEN and shifted-symbol paths.

### Remaining validation

- [ ] Capture a completed pre-fix native RED proving iOS HSU remapping omits `晚` for `xmf` and `很` for `hnf`.
- [ ] Repeat the full XCTest and independent-review gates if the PR source or tests change after `684a45b40067d4dd2ee38d116d79279aba5297db`.

### Reporter-visible runtime checks

On an iPhone or iPad with the built-in Phonetic IM and HSU keyboard selected:

1. Type `xmf` and verify `晚` appears and can be committed.
2. Type `hnf` and verify `很` appears and can be committed.
3. Verify representative one-key HSU exceptions still use their initial mapping.
4. Switch to standard, ETEN 41-key, and ETEN 26-key layouts and verify representative input still resolves normally.

## Platform impact

### iOS

Confirmed affected. The current Swift HSU remapper applies a single-character exception at every character position. The correction belongs in the iOS `LimeDB` remapping implementation and its tests.

### Android

Not affected by the reported defect. The reporter confirms both examples work on Android, and current Android source limits the forced-initial exception to one-character input. Android remains the behavioral oracle and requires no production change.
