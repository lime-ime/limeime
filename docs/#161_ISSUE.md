# Issue #161: Related-record management search and iOS runtime parity

## Status

- GitHub issue: https://github.com/lime-ime/limeime/issues/161
- Classification: `bug` + `Usability`
- Platforms: Android and iOS
- State: implementation in PR #165; keep open until review, merge, release, and reporter retest

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

PR #165 aligns these iOS paths with Android while leaving automatic-learning filters unchanged.

## Management search contract

Management search is separate from runtime candidate lookup. A non-empty query performs only an escaped `pword LIKE 'query%'` prefix match. It does not search `cword` or the displayed `pword + cword` combination. Filtered counts use exactly the same predicate as paged results. SQL wildcard characters in user text are treated literally.

## TDD evidence

- Existing code reproduced RED for iOS item-0 parity: the selection path contained `wasComposingCodeCommit` and bypassed `updateRelatedPhrase`; the lookup guard rejected composing-code records.
- Added iOS tests for item-0 routing and full-word/final-character query behavior.
- Existing Android/iOS management-search tests cover multi-character parent and combined-phrase lookup.

## Acceptance criteria

- [x] No Han-only or one-character validation is introduced
- [x] Android management search uses `pword` prefix matching only
- [x] iOS management search uses the same `pword` prefix semantics and filtered count
- [x] iOS item-0 raw-code commit requests related candidates like Android
- [x] iOS multi-character runtime lookup uses full word plus final-character fallback
- [x] Android regression tests pass on the final branch
- [ ] iOS XCTest/Xcode Cloud passes on the final branch
- [ ] Maintainer review/merge
- [ ] Reporter verifies search/deletion in a released build
