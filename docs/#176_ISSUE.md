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

Confirmed affected. `SearchServer.getMappingByCode()` calls `makeRunTimeSuggestion()` for both phonetic and non-phonetic tables when `smart_chinese_input` is enabled, and the Swift implementation is intended to mirror Android. The current test suite does not provide executed end-to-end coverage for smart composition through an imported `custom` table; many runtime-suggestion tests are skipped or test unrelated capability paths. The exact failing boundary remains unproven and may involve imported mapping metadata, exact-match classification, state/cache handling, or candidate assembly.

### Android

Confirmed unaffected for the same imported table. Android's `SearchServer.makeRunTimeSuggestion()` produces the expected phrase and has no intentional exclusion for `custom`. No Android product-code change belongs in `fix#176`; Android should instead supply parity fixtures and expected behavior for iOS tests.

## Likely root-cause boundary

The defect is inside the iOS imported-custom-table smart-composition path, but there is not yet enough focused runtime evidence to name a specific faulty condition. A source-only diagnosis would be premature because the Swift algorithm appears to invoke the same high-level stages as Android. The next investigation must capture the imported rows and each stage of iOS phrase construction, then compare those observations with Android using equivalent sanitized fixture data.

## Proposed solution

1. Reproduce the failure with a minimal, redistribution-safe custom table containing only the mappings needed for one multi-character smart-composition case.
2. Add a RED iOS test that imports the fixture into `custom`, enables `smart_chinese_input`, enters the complete code sequence through the real search path, and asserts that the expected phrase candidate is returned.
3. Run an equivalent Android fixture as the parity oracle.
4. Identify the first platform divergence and make the smallest iOS-only correction. Do not redesign related-phrase context or alter Android behavior in this fix.
5. Add regression coverage for built-in and imported tables, then verify the rendered candidate path on iPhone, full iPad, and narrow iPad.

## Follow-up questions

- Can the failure be reduced to a minimal custom fixture without redistributing the reporter's source table?
- Do iOS and Android import identical `code`, `word`, `score`, `basescore`, and exact-match values for those fixture rows?
- At which stage do the platforms first diverge: seed selection, remaining-code lookup, phrase scoring, cache/state update, or final candidate assembly?

## Verification plan

1. RED: reproduce the missing phrase through the iOS imported-`custom` path.
2. Confirm the same fixture is GREEN on Android before changing iOS behavior.
3. GREEN: verify the smallest iOS fix returns the expected phrase candidate.
4. Re-run focused `SearchServer`, import, and custom-table tests plus the complete iOS test suite.
5. Verify ordinary exact candidates, backspace/restart state, LD, related lookup after commit, and mixed English input are unchanged.
6. Perform runtime checks on iPhone, full iPad, and narrow iPad.
7. Keep the `feat#176` database work and the unapproved longer-context evaluation separate from defect completion and release QA.

## Backlog decision

Add `fix#176 iOS` for the confirmed custom-table smart-composition defect. Retain `feat#176 Android+iOS` only for the approved related-phrase database refresh. Keep the longer cross-commit context proposal out of the backlog until a maintainer confirms its product direction.
