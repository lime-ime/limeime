# Issue #176: Related-phrase design and iOS custom-table smart composition

## Status

- Issue: https://github.com/lime-ime/limeime/issues/176
- Classification: iOS defect plus separate Android/iOS product requests
- State: closed as completed by maintainer `jrywu` on 2026-08-02. The accepted iOS source correction is included in public App Store v6.1.37. The reporter has not confirmed the corrected behavior on that version, so this records maintainer closeout rather than reporter validation. Keep the approved bundled-database work under `feat#176`, while the longer cross-commit context proposal remains separate unapproved product evaluation.
- Reporter environment: LIME 6.1.33. The original related-phrase examples use 行列 10.

## Separate product scope

The reporter commits `命` and `運` separately. Related lookup uses the text from each individual commit as `pword`; it does not concatenate earlier commits into a longer lookup key. Therefore the second lookup uses `pword = 運`, not `pword = 命運`.

A multi-character `pword = 命運` lookup occurs only when `命運` is committed in one action, such as through LD（連打詞輸入）. The original `命運交響曲` observation therefore matches the current exact-commit lookup model and is not a related-phrase lookup defect.

The reporter is requesting product improvements to preserve longer text context across commits and refresh the bundled related-phrase database. The database-refresh direction is tracked under `feat#176`. The longer-context proposal remains product evaluation and is not yet approved backlog work. Neither scope should be folded into the iOS defect fix.

## Confirmed iOS defect

The project account confirmed the platform comparison in comment `5029971309`:

- iOS: entering the code sequence for `我也是` with the imported `自建` table does not produce the expected smart-composition candidate.
- Android: the same imported table produces the expected candidate.

Runtime evidence therefore establishes an iOS-only defect. Earlier Android screenshots showed a result difference but did not establish an Android custom-table defect. Android is the behavioral oracle for this scope.

## Source correction and delivery

- Commit `34584daf66c520ac8d0e3823f105b31a55390e4c` bundles `hanconvertv2.db` for the iOS import path, reads Android-parity `TCSC.score` values for scoreless imports, and adds focused import/base-score regressions.
- Follow-up commit `a15e40ac4fa2393ba5c2d57bdd5134d37788827b` makes field presence authoritative: an omitted `basescore` receives the Han frequency fallback, while an explicit `0` remains `0` for lossless `.lime` round trips.
- Both commits are ancestors of release target `d4e8840e9a7080a7980036284952f50ef6ccf3d7`. App Store v6.1.37 became public on 2026-07-26 and its release notes include the custom-table import candidate-order improvement.
- The commits record focused iOS tests and a full LimeTests run. Reporter-visible confirmation for `我也是` on v6.1.37 is absent. No public retest request was added after the maintainer closure.

## Platform impact

### iOS

Confirmed affected in the reported v6.1.33 path. Source tracing identified one deterministic Android-parity failure and one separate cache-path risk in the iOS imported-table/search path:

1. Scoreless text imports remain scoreless on iOS. `LimeDB.importTxtFile()` calls `getBaseScore(word)` when an imported `.cin`/`.lime` row omits `basescore` or supplies `0`, but the iOS `getBaseScore()` implementation always returns `0`. `SearchServer.makeRunTimeSuggestion()` then skips an exact seed whose `baseScore` is `0` and rejects a remaining mapping whose `baseScore` is below `2`. A normal scoreless custom text table therefore cannot seed or extend a smart-composition phrase on iOS.
2. Mapping-cache hits bypass runtime state updates. Both the exact cache hit and the stage-1-to-stage-2 prefetch fallback return immediately from `SearchServer.getMappingByCode()` before `makeRunTimeSuggestion()` runs. `triggerPrefetch()` warms first-stroke results with `isPrefetch = true`; when the user later enters that stroke, the cached return can prevent `suggestionLoL` from being seeded even when the mappings carry valid positive base scores. The cache currently stores an already assembled candidate list, so it can also retain candidate output derived from stale runtime state.

Neither path is a `custom`-specific branch. Imported text commonly lacks explicit base scores, which made the first failure deterministic before the accepted correction. Custom-table activation can also prefetch root keys, but the issue evidence did not independently prove that the cache risk caused the reporter's run, and the maintainer closeout did not require that broader cache refactor.

### Android

Confirmed unaffected for the same imported table. Android's `SearchServer.makeRunTimeSuggestion()` produces the expected phrase and has no intentional exclusion for `custom`. No Android product-code change belongs in `fix#176`; Android should instead supply parity fixtures and expected behavior for iOS tests.

## Root cause

### Primary: missing iOS base-frequency scores

Android and iOS implement different fallback policies for imported rows without an explicit `basescore`:

- Android ships `hanconvertv2.db`; its importer calls `getBaseScore(word)` and stores the frequency score from `TCSC.score`. The current shared source returns `123003` for `我`, `33788` for `也`, and `152789` for `是`.
- iOS uses `CFStringTransform` for Han conversion and does not bundle the Android score database. Its `getBaseScore()` is deliberately implemented as unconditional `0`, so the importer's fallback does not populate anything.

This conflicts directly with the runtime algorithm's score contract. iOS drops a zero-score exact seed before applying the minimum-score clamp, while both platforms require a remaining mapping score of at least `2`. Consequently, a scoreless `.cin`/`.lime` import is structurally unable to produce `我也是` through the iOS smart-composition path. Android populates the missing scores during import and can produce it.

The public issue does not include the reporter's source file or its score fields, so the exact imported rows should still be captured in the regression fixture. That does not change the proven iOS failure for all scoreless text imports.

### Secondary: cached results skip composition state

Android retrieves raw mappings from its cache and then invokes `makeRunTimeSuggestion(code, resultList)` for every non-prefetch user query. iOS instead returns cached assembled candidates before reaching its runtime-suggestion call. This violates the stateful algorithm's requirement to process each progressive input code and can independently suppress phrase construction after root-key prefetch.

The base-score failure was sufficient to explain scoreless imports and is the accepted fix boundary. The cache-path risk remains a separate hardening hypothesis unless a focused regression or runtime evidence proves it affects the reporter-visible path.

## Accepted solution and remaining hardening

1. Completed: iOS now bundles the Android Han frequency database and seeds omitted import base scores from `TCSC.score`.
2. Completed: focused tests cover known frequencies and scoreless custom-table imports.
3. Completed in the follow-up: an explicitly present `basescore`, including `0`, is authoritative; only an omitted field receives the frequency fallback.
4. Remaining optional hardening: test the warm-prefetch/cache path through the full `SearchServer` progression before deciding whether raw-mapping cache refactoring is needed. This was not established as the reporter-visible cause and is not part of the completed `fix#176` scope.
5. Keep this iOS-only correction separate from cross-commit related-phrase context and bundled-related-database content. No Android product-code change is required.

## Remaining evidence

- Reporter-visible confirmation for the original imported table on App Store v6.1.37 is not available.
- The broader warm-prefetch/cache hypothesis remains unproven for this report and should not be treated as part of the completed fix without a focused regression.

## Verification status and follow-up

1. Historical RED contract: before `34584daf`, iOS `getBaseScore()` returned `0`, so a scoreless text import could not satisfy the smart-composition score gates.
2. GREEN source coverage: focused tests now verify known Han frequencies and nonzero score seeding for a scoreless custom-table import.
3. Follow-up source coverage verifies that omitted and explicitly present `basescore` fields remain distinct, including preservation of an explicit `0`.
4. The full iOS test suite recorded by the follow-up commit passed. No equivalent reporter-visible `我也是` retest on App Store v6.1.37 is recorded.
5. If the behavior is reported again, reproduce through the full `SearchServer` cold- and warm-cache paths before changing cache design, and verify `smart_chinese_input = false`, backspace/restart state, LD, related lookup after commit, learning invalidation, and mixed English input.
6. Keep the `feat#176` database work and the unapproved longer-context evaluation separate from defect completion and any future hardening.

## Backlog decision

Remove completed `fix#176 iOS` from the unresolved backlog after the accepted source correction shipped in App Store v6.1.37 and maintainer `jrywu` closed the issue. Retain `feat#176 Android+iOS` only for the separately approved related-phrase database refresh. Keep the longer cross-commit context proposal out of the backlog until a maintainer confirms its product direction.
