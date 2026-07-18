# Issue #161: Related-record management search and iOS runtime parity

## Status

- GitHub issue: https://github.com/lime-ime/limeime/issues/161
- Classification: `bug` + `Usability`
- Platforms: Android and iOS
- State: closed after merged PR #167, but post-merge review found an Android related-candidate cache invalidation regression that must be corrected before the next reporter-testable build

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
- The current GitHub Android APK remains v6.1.32, whose tag targets `0a7b8158536d6d55c6c3684952447ea9b915cb42`; that commit is an ancestor of the PR merge and therefore does not contain this fix.
- The final synchronize commit added an Android `relatedcache`, keyed separately for initial/full runtime results. Automatic learning invalidates both keys, but manual `關聯字管理` add, update, and delete operations still call the generic `SearchServer` mutation wrappers without invalidating this cache. A relation already queried by the keyboard can therefore remain stale after management changes until a broader cache reset.
- FIXED (follow-up): the generic `SearchServer.addRecord/updateRecord/deleteRecord` wrappers now flush the whole `relatedcache` whenever the mutated table is `related` (whole-cache clear on purpose — delete-by-id does not know the pword at that level; the cache repopulates on next lookup). Regression test `SearchServerTest.test_3_4_2_8_relatedCacheFlushedByRelatedTableMutations` pins caching, non-related-table no-flush, and flush on all three mutations.
- iOS audit found the same gap in mirror form: `ManageRelatedController` mutations never set the `needsKeyboardCacheReset` App-Group flag that `ManageImController` uses, so a warm keyboard process whose table sync was applied by another process (settings probe / other host app) could keep serving its stale `relatedCache`. FIXED: all seven related mutation sites now call the shared (`nonisolated`) `ManageImController.markKeyboardCacheDirty()` after a successful write, matching the mapping-editor pattern; the keyboard clears all caches on next activation.
- Do not request reporter retest from v6.1.32 or from a future build that contains PR #167 without this follow-up.
- iOS delivery still requires corrected-source XCTest/Xcode Cloud validation and a newer TestFlight/App Store build.

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
- [ ] Reporter verifies search/deletion in a released build
