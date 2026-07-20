# Issue #176: Related-phrase context requests and custom-table smart composition

## Status

- Issue: https://github.com/lime-ime/limeime/issues/176
- Classification: enhancement/usability plus a newly reported plausible Android custom-table defect
- State: open, awaiting maintainer product direction and a minimal custom-table reproduction
- Reporter environment: LIME 6.1.33, primarily 行列 10. The newest comparison shows smart composition producing candidates with the built-in 倉頡 and 行列 30 tables but apparently producing no equivalent result with `自建`.

## Original enhancement scope

The reporter asks LIME to preserve longer context across characters committed separately, improve related-phrase ranking for examples such as `命運交響曲`, refresh outdated bundled related-phrase content, and evaluate importing other dictionaries.

The reporter clarified that `命` and `運` were selected and committed separately. They did not use LD（連打詞輸入）, and they keep 中文智慧組詞 disabled for their normal 行列 10 use because they find it difficult to use there.

Current related-phrase lookup uses each committed candidate as its lookup key. A multi-character candidate is queried as the complete candidate plus a final-character fallback, but separate one-character commits are not concatenated into a longer lookup key. The requested cross-commit context retention and ranking changes are therefore product enhancements, not evidence that the existing exact-lookup contract failed.

## New plausible defect: `自建` smart composition

In https://github.com/lime-ime/limeime/issues/176#issuecomment-5023935912, the reporter added three Android screenshots labeled 倉頡, 行列 30, and 自建. They state that 中文智慧組詞 has no effect with the custom table while the built-in tables demonstrate the feature.

This is meaningful new evidence but is not yet a stable code-level reproduction. The comment does not provide:

- the exact code sequence entered in each comparison
- the expected composed phrase and the actual custom-table candidates in text
- whether `自建` contains the same single-character mappings as the working built-in table
- the imported file format and whether it supplied score/base-score columns or relevant metadata

## Android source assessment

Android does not intentionally exclude the `custom` table from smart composition:

- `SearchServer.getMappingByCode()` calls `makeRunTimeSuggestion()` whenever `smart_chinese_input` is enabled and runtime suggestions have not been abandoned.
- `makeRunTimeSuggestion()` seeds from exact mappings, splits the remaining code, looks up an exact mapping for that remainder, and constructs a phrase candidate.
- The same path applies after `setTableName("custom", ...)`.

The custom-table outcome can still differ because runtime construction depends on table data and metadata. Plausible investigation areas include imported mapping base scores, exact-match classification, code segmentation/max-code-length assumptions, and whether the custom table contains equivalent per-character mappings. These remain hypotheses until the reporter supplies a minimal input sequence/table sample or a local test reproduces the failure.

The importer fills a missing `basescore` through `getBaseScore(word)`, while runtime composition rejects a remaining mapping whose base score is below 2. This creates a specific source-backed hypothesis for imported characters absent from the bundled frequency dictionary, but it does not yet prove the reporter's screenshots hit that condition.

## iOS platform impact

The reporter evidence is Android-only. iOS also supports custom tables and exposes 中文智慧組詞, but its runtime implementation is separate and existing tests do not establish custom-table behavioral parity for this scenario. Do not infer that iOS reproduces the Android report. If a fix is designed, first test the same custom-table fixture on both platforms and use any working platform behavior as the oracle.

## Proposed next steps

1. Ask for one minimal comparison in text: exact code sequence, expected phrase, built-in result, custom result, and the custom table's source format.
2. If possible, obtain a minimal non-private table fixture containing only the mappings needed to reproduce the comparison.
3. Add a RED Android test that imports/uses the custom fixture and demonstrates the missing runtime-built candidate.
4. Inspect the failing test to distinguish score filtering, exact-match flags, code segmentation, cache state, and metadata handling.
5. Run the same fixture against iOS before choosing a platform-specific mechanism.
6. Keep the original cross-commit context/ranking and bundled-dictionary requests separate from this custom-table defect.

## Verification plan

- Android built-in control table produces the expected runtime-built phrase for the exact test sequence.
- Android `custom` with equivalent mappings produces the same phrase when 中文智慧組詞 is enabled.
- Disabling 中文智慧組詞 suppresses the runtime-built phrase for both tables.
- Imported records with omitted and explicit score/base-score fields are covered.
- Custom codes at the supported maximum length are covered.
- No regression to ordinary exact candidates, LD（連打詞輸入）, related-phrase lookup after commit, or English mixed input.
- Equivalent iOS fixture behavior is recorded separately.

## Backlog decision

No `docs/BACKLOG.md` entry is added yet. The original enhancement direction has not been approved, and the custom-table report is plausible but still lacks a minimal reproduction that confirms the intended fix scope.
