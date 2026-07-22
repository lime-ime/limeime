# Issue #176: Related-phrase design and iOS custom-table smart composition

## Status

- Issue: https://github.com/lime-ime/limeime/issues/176
- Classification: iOS defect plus separate Android/iOS product requests
- State: open. The iOS `自建` input-method path does not produce the expected 中文智慧組詞 candidate for `我也是`, while Android produces it from the same imported table. Track the iOS defect as `fix#176`. Keep the approved bundled-database work under `feat#176`, while the longer cross-commit context proposal remains unapproved product evaluation in the issue.
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

## Platform impact

### iOS

Confirmed affected. Source tracing identifies two concrete Android-parity failures in the iOS imported-table/search path:

1. Scoreless text imports remain scoreless on iOS. `LimeDB.importTxtFile()` calls `getBaseScore(word)` when an imported `.cin`/`.lime` row omits `basescore` or supplies `0`, but the iOS `getBaseScore()` implementation always returns `0`. `SearchServer.makeRunTimeSuggestion()` then skips an exact seed whose `baseScore` is `0` and rejects a remaining mapping whose `baseScore` is below `2`. A normal scoreless custom text table therefore cannot seed or extend a smart-composition phrase on iOS.
2. Mapping-cache hits bypass runtime state updates. Both the exact cache hit and the stage-1-to-stage-2 prefetch fallback return immediately from `SearchServer.getMappingByCode()` before `makeRunTimeSuggestion()` runs. `triggerPrefetch()` warms first-stroke results with `isPrefetch = true`; when the user later enters that stroke, the cached return can prevent `suggestionLoL` from being seeded even when the mappings carry valid positive base scores. The cache currently stores an already assembled candidate list, so it can also retain candidate output derived from stale runtime state.

These failures are not specific branches for table name `custom`; they become visible there because imported text commonly lacks explicit base scores and custom-table activation prefetches its root keys. Built-in tables normally ship with populated `basescore` values, which avoids the deterministic import failure, while cache timing can make the second failure intermittent.

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

The two faults must be tested and corrected separately: adding base scores does not repair a prefetched first-stroke cache hit, and changing cache flow does not make zero-score imported rows eligible for phrase construction.

## Proposed solution

1. Create a redistribution-safe scoreless `.cin` fixture with three mappings, for example `i → 我`, `xal → 也`, and `jn → 是`. Add a RED iOS import/search test that verifies the imported rows and enters `i`, `ixal`, then `ixaljn` through the real `SearchServer` path.
2. Give iOS the same base-frequency source used by Android. The narrowest parity fix is to bundle the existing approximately 269 KiB `hanconvertv2.db` resource, use `TCSC.score` for `LimeDB.getBaseScore()`, and retain `0` only when a word is absent. A generated equivalent is acceptable only if tests prove identical scores for the fixture and representative Traditional Chinese characters.
3. Keep explicit imported scores authoritative. `.lime`/`.limedb` rows with a positive `basescore` must preserve it; only missing or zero values use the frequency fallback. Do not turn user-learning `score` into `basescore`.
4. Refactor iOS mapping caching to cache raw database mappings rather than the final assembled candidate list. On every real user query, retrieve raw mappings from cache or DB, run `makeRunTimeSuggestion()`, and assemble the current echo/runtime/English candidates. Prefetch must warm only raw mappings and must not mutate runtime state.
5. Add an explicit-score control and a warm-cache control. The scoreless fixture must pass after score seeding, and the positive-score fixture must produce the same phrase before and after first-stroke prefetch. Disabling `smart_chinese_input` must suppress the runtime phrase in both cases.
6. Keep this iOS-only correction separate from cross-commit related-phrase context and bundled-related-database content. No Android product-code change is required.

## Remaining evidence to capture

- Record the reporter table's source format and the imported `code`, `word`, `score`, and `basescore` rows for `我`, `也`, and `是` if the table can be shared privately. This determines which of the two proven faults triggered the reported run; it is not required to reproduce the scoreless import defect.
- Confirm the selected iOS frequency resource is packaged in the keyboard extension, not only the Settings host app, and remains available without Full Access.
- Measure import cost and verify that score lookup is batched or prepared efficiently for large custom tables.

## Verification plan

1. RED: a scoreless `.cin` import stores zero base scores and cannot produce the runtime phrase on current iOS.
2. RED: a positive-score fixture produces the phrase on a cold query but loses it when the first stroke is served by prefetch/cache.
3. Confirm the equivalent scoreless import and progressive query are GREEN on Android and record the imported scores as the parity oracle.
4. GREEN: verify iOS imports the expected frequency scores and returns `我也是` on cold-cache and warm-cache paths.
5. Verify explicit positive `basescore` values are preserved, unknown words retain a safe fallback, and `smart_chinese_input = false` suppresses composition.
6. Verify ordinary exact candidates, ordering, two-stage expansion, repeated queries, backspace/restart state, LD, related lookup after commit, cache invalidation after learning, and mixed English input are unchanged.
7. Re-run focused `LimeDB`, `SearchServer`, import, and custom-table tests plus the complete iOS test suite.
8. Perform runtime checks on iPhone, full iPad, and narrow iPad, including a custom keyboard without Full Access.
9. Keep the `feat#176` database work and the unapproved longer-context evaluation separate from defect completion and release QA.

## Backlog decision

Add `fix#176 iOS` for the confirmed custom-table smart-composition defect. Retain `feat#176 Android+iOS` only for the approved related-phrase database refresh. Keep the longer cross-commit context proposal out of the backlog until a maintainer confirms its product direction.
