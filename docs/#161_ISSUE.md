# Issue #161: Related-record management search and iOS runtime parity

## Status

- GitHub issue: https://github.com/lime-ime/limeime/issues/161
- Classification: `bug` + `Usability`
- Platforms: Android and iOS
- State: open after the reporter confirmed the Android v6.1.33 search and immediate candidate-refresh fixes; GitHub APK v6.1.34 now contains the separate Android management-list refresh fix from PR #168, and targeted reporter verification is pending, while iOS binary delivery remains separate

## Corrected scope

The original text-shortcut question is answered: shortcut-like entries belong in each input method's `瀏覽 / 編輯資料表`, not `關聯字管理`.

The remaining confirmed defect is in related-record management. Valid records can use a multi-character `pword`, and management search must return records whose `pword` starts with the entered text so users can edit/delete them without paging through the entire table. For example, entering `萊` matches every record with `pword LIKE '萊%'`.

Do not restrict `pword` to one character or to Han-only text:

- related learning stores the full preceding committed candidate word as `pword`
- LD and ordinary mappings can commit multi-character words
- Android allows a raw item-0 code commit to query related records using that English/mixed-mode committed word
- exact/partial mappings may also have non-Han output

## Runtime lookup contract

Android is the reference behavior:

1. Pass the complete committed candidate word to related lookup.
2. For a one-character word, query that exact `pword`.
3. For a multi-character word, query both the full word and its final character.
4. Sort full-word matches before final-character fallback matches, then by score.
5. Item-0 composing-code commits participate in lookup but remain excluded from automatic related-word learning by record-type filters.

The iOS path previously diverged in two ways:

- `pickCandidateManually` cleared suggestions after an item-0 composing-code commit instead of calling `updateRelatedPhrase()`.
- `getRelatedMappings` queried only the complete word and omitted Android's final-character fallback.

Merged PR #167 aligns these iOS paths with Android while leaving automatic-learning filters unchanged.

## Management search contract

Management search is separate from runtime candidate lookup. A non-empty query performs only an escaped `pword LIKE 'query%'` prefix match. It does not search `cword` or the displayed `pword + cword` combination. Filtered counts use exactly the same predicate as paged results. SQL wildcard characters in user text are treated literally.

## TDD evidence

- Existing code reproduced RED for iOS item-0 parity: the selection path contained `wasComposingCodeCommit` and bypassed `updateRelatedPhrase`; the lookup guard rejected composing-code records.
- PR #167 adds iOS tests for item-0 routing, full-word/final-Unicode-scalar fallback, and decomposed-text behavior.
- PR #167 adds Android/iOS management tests for parent-prefix-only matching, literal wildcard escaping, filtered counts, and pagination alignment.

## Delivery boundary

- PR #167 merged to `master` as `7f799370b0e3e6cdfc7114ea7af8ffb5b71a8262` and auto-closed the community issue.
- GitHub Release v6.1.33 targets `b2fa71779d8423b92896fa1a0262706bb62ea4fa` and includes PR #167 plus the Android/iOS manual-mutation cache invalidation follow-ups. The retained GitHub testing-track asset was replaced during the Android v6.1.33 update. The currently verified `LIMEHD2026-6.1.33.apk` is 7,108,892 bytes with SHA-256 `1966310faa93aedce815639ca4c7d71027cd33c0219c8e862eb032080bd41dec` at https://github.com/lime-ime/limeime/releases/download/v6.1.33/LIMEHD2026-6.1.33.apk.
- The final synchronize commit added an Android `relatedcache`, keyed separately for initial/full runtime results. Automatic learning invalidates both keys, but manual `關聯字管理` add, update, and delete operations still call the generic `SearchServer` mutation wrappers without invalidating this cache. A relation already queried by the keyboard can therefore remain stale after management changes until a broader cache reset.
- FIXED (follow-up): the generic `SearchServer.addRecord/updateRecord/deleteRecord` wrappers now flush the whole `relatedcache` whenever the mutated table is `related` (whole-cache clear on purpose — delete-by-id does not know the pword at that level; the cache repopulates on next lookup). Regression test `SearchServerTest.test_3_4_2_8_relatedCacheFlushedByRelatedTableMutations` pins caching, non-related-table no-flush, and flush on all three mutations.
- iOS audit found the same gap in mirror form: `ManageRelatedController` mutations never set the `needsKeyboardCacheReset` App-Group flag that `ManageImController` uses, so a warm keyboard process whose table sync was applied by another process (settings probe / other host app) could keep serving its stale `relatedCache`. FIXED: all seven related mutation sites now call the shared (`nonisolated`) `ManageImController.markKeyboardCacheDirty()` after a successful write, matching the mapping-editor pattern; the keyboard clears all caches on next activation.
- Do not request reporter retest from v6.1.32 or from any build that contains PR #167 without the cache-invalidation follow-ups.
- Issue #161 was reopened for Android reporter confirmation. The retained v6.1.33 retest request is https://github.com/lime-ime/limeime/issues/161#issuecomment-5011747138 and asks the reporter to verify `pword`-prefix search/deletion plus immediate candidate refresh after manual add/update/delete.
- Reporter `coral0819` confirmed both requested Android checks in https://github.com/lime-ime/limeime/issues/161#issuecomment-5012137623: previously unfindable related records can now be found and deleted, and keyboard related candidates update immediately after add/update/delete. The comment was then edited to add a public Google Drive recording. The downloaded 21.376-second MP4 is 15,818,451 bytes with SHA-256 `0bce29c2e256c53dd7251053b77b2a98cf6955fa3ad175d38111506c95e83497`.
- The recording establishes a separate Android management-list synchronization defect. With search text `/`, the list initially shows three records with the same parent `/084V9P2` and children `/084V9P2`, `台中`, and `/`. After a delete is confirmed, the filtered list count changes but a visible row can retain the deleted record's text; tapping that stale-looking row opens a different surviving record. Clearing/re-entering the search refreshes the visible row contents. This does not invalidate the confirmed prefix-search or keyboard-candidate cache fixes, but it keeps #161 open for a focused list-refresh fix.
- Root cause: `ManageRelatedFragment.removeRelated()` removed the row directly from the same mutable list previously submitted to `ListAdapter`, violating DiffUtil's immutable-list contract. The adapter's internal item count changed without a matching RecyclerView diff/rebind, so visible holder text and the refreshed database position could diverge. The fragment also requested a second redundant refresh after `ManageImController.deleteRelatedPhrase()` had already refreshed the view.
- PR #168 merged the focused Android fix to `master` as `285b9fde57384203c074f9b16094f2bdc757a3c6` from final head `356ba547830ca0af0dd9807e308a059863fdd9fb`. The fix keeps the submitted page unchanged until the controller returns a fresh database result, removes the redundant delete refresh, and makes `ManageRelatedAdapter` snapshot each submitted list defensively. Instrumentation regression coverage first reproduced RED when caller mutation changed the adapter count from 2 to 1 without a new submission, then passed GREEN after the fix; a second RED/GREEN test verifies `removeRelated()` does not mutate the current page before controller refresh.
- GitHub Release v6.1.33 targets `b2fa71779d8423b92896fa1a0262706bb62ea4fa`, which is an ancestor of the PR #168 merge and therefore predates this deletion-list fix. Do not ask the reporter to retest this follow-up until a newer Android GitHub APK or Google Play build contains `285b9fde57384203c074f9b16094f2bdc757a3c6`.
- GitHub Release v6.1.34 targets `d45aa437b6356bfef5079ceebbfcd8d295a300b8` and contains PR #168. Its verified GitHub testing-track asset is `LIMEHD2026-6.1.34.apk` (7,112,576 bytes, SHA-256 `d16d7fde5d634d655148396c657e8ffab5f3868f434f705f9568855da4e3e84f`) at https://github.com/lime-ime/limeime/releases/download/v6.1.34/LIMEHD2026-6.1.34.apk. The targeted deletion-list retest request is https://github.com/lime-ime/limeime/issues/161#issuecomment-5016727224.
- iOS delivery still requires corrected-source XCTest/Xcode Cloud validation and a verified newer TestFlight/App Store build. The Android APK does not verify iOS behavior.

## Acceptance criteria

- [x] No Han-only or one-character validation is introduced
- [x] Android management search uses `pword` prefix matching only
- [x] iOS management search uses the same `pword` prefix semantics and filtered count
- [x] iOS item-0 raw-code commit requests related candidates like Android
- [x] iOS multi-character runtime lookup uses full word plus final Unicode-scalar fallback, matching Android code-point semantics
- [x] Android regression tests pass on the final branch
- [ ] iOS XCTest/Xcode Cloud passes on the corrected merged source
- [x] Maintainer review/merge as PR #167 / `7f799370b0e3e6cdfc7114ea7af8ffb5b71a8262`
- [x] Android manual related add/update/delete invalidates both initial and full runtime related-cache entries (whole-cache flush in the generic mutation wrappers + regression test)
- [x] iOS manual related add/update/delete signals `needsKeyboardCacheReset` so the keyboard flushes `relatedCache` on next activation
- [x] Reporter verifies Android search/deletion and immediate candidate refresh in v6.1.33
- [x] Android filtered-management-list source fix merged in PR #168 / `285b9fde57384203c074f9b16094f2bdc757a3c6`
- [x] Android GitHub APK v6.1.34 contains PR #168
- [ ] The reporter confirms that deletion refreshes visible row contents so the row and opened record remain identical
