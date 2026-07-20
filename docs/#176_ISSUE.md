# Issue #176: Related-phrase design and custom-table smart composition

## Status

- Issue: https://github.com/lime-ime/limeime/issues/176
- Classification: by-design related-phrase behavior plus an unconfirmed Android custom-table smart-composition difference
- State: open. Related lookup behavior is confirmed by design. The reporter's `自建` table is Array-derived, but controlled runtime and import checks find no `custom`-specific behavior when the underlying Array records are identical. The reporter's exported table is still needed to compare its actual mappings and scores with the built-in Array data.
- Reporter environment: LIME 6.1.33, primarily 行列 10.

## Related-phrase behavior is by design

The reporter commits `命` and `運` separately. Related lookup uses the text from each individual commit as `pword`; it does not concatenate earlier commits into a longer lookup key. Therefore the second lookup uses `pword = 運`, not `pword = 命運`.

A multi-character `pword = 命運` lookup occurs only when `命運` is committed in one action, such as through LD（連打詞輸入）. The reporter keeps 中文智慧組詞 disabled for normal 行列 10 use and does not use LD for this sequence. Their original `命運交響曲` observation therefore matches the designed exact-commit lookup model and is not a related-phrase defect.

The separate request to refresh the bundled related-phrase database remains product work under `feat#176`.

## Newly reported custom-table comparison

The reporter later stated that 中文智慧組詞 appears ineffective for `自建` and attached three Android screenshots:

| Table | Visible code | Visible smart candidate |
| --- | --- | --- |
| Built-in 倉頡 | `hqipdamyo` | `我也是` |
| Built-in 行列 30 | `loxgdspc` | `我也是` |
| `自建` | `ixaljn` | no `我也是`; visible candidates begin `我`, `祉`, `叟`, `後`, `这` |

The screenshots compare built-in 行列 30 with an Array-derived custom table, but the visible code sequences and therefore the actual mapping records differ. They demonstrate a result difference but do not by themselves prove that selecting table `custom` disables smart composition.

## Source and data-flow assessment

Android does not intentionally exclude `custom` from smart composition:

- `SearchServer.getMappingByCode()` calls `makeRunTimeSuggestion()` whenever `smart_chinese_input` is enabled and runtime suggestions have not been abandoned.
- `SearchServer.setTableName()` applies the same query path to built-in and custom tables.
- `makeRunTimeSuggestion()` is table-agnostic. It seeds from exact mappings, searches the remaining code, and builds a phrase candidate.

The runtime result does depend on mapping data:

- each code segment must resolve to an exact mapping
- the remaining segment must fit `maxCodeLength`
- at `SearchServer.java:593`, a remaining exact mapping with `basescore < 2` is rejected
- imported CIN/LIME text fills a missing base score through `LimeDB.getBaseScore(word)`, while imported/restored database content may preserve its existing score values

## Controlled Android comparison

A temporary instrumentation fixture exercised the real `SearchServer` runtime-suggestion path with the same mappings and scores under both table names:

- seed mappings composed `我` + `也` + `是`
- table `array`, positive base scores: produced `我也是`
- table `custom`, identical mappings and positive base scores: produced `我也是`
- table `custom`, identical mappings but zero base scores for remaining segments: did not produce `我也是`

The focused instrumentation run completed two tests successfully on the Android emulator. The temporary characterization test was removed after the investigation and was not committed because one case deliberately records the current score-rejection behavior rather than an approved product contract.

This establishes that the table name alone does not cause the observed difference. Mapping segmentation and score metadata can cause it.

The real `.limedb` import path was also checked. `LimeDB.importMappingRowsFromAttachedSource()` copies `code`, `word`, `score`, and `basescore` from the archive's `custom` source table into either destination table through the same SQL path. A direct import-parity simulation using the current official `Database/array.limedb` copied all 32,850 rows into target tables named `array` and `custom`; bidirectional `EXCEPT` comparisons returned zero differences. The `我`、`也`、`是` control records and base scores remained identical. Therefore importing the same official Array database under `custom` does not itself explain the reporter's result.

## Built-in Array control data

The current official `Database/array.limedb` was inspected directly. Its 行列 30 sequence in the screenshot decomposes as:

- `lox` → `我`, `basescore = 123003`
- `gds` → `也`, `basescore = 33788`
- `pc` → `是`, `basescore = 152789`

These high positive scores satisfy the runtime-suggestion thresholds. The reporter's `自建` mappings and score fields for `ixaljn` are unavailable, so the custom result cannot yet be attributed to missing segment mappings, zero/low scores, or another metadata difference.

## Information needed from the reporter

Request either the exported Array-derived custom table or a minimal extract containing only the records needed for `ixaljn`, plus:

1. the intended code split for `我`、`也`、`是`
2. the source format: `.cin`, `.lime`, or `.limedb`
3. each record's `score` and `basescore` if the format carries them
4. confirmation that the same custom table produces each character separately

With that fixture, reproduce the exact import path and compare the resulting `custom` rows to the built-in Array control.

## Verification plan if a defect is confirmed

1. Import the reporter's minimal fixture through its real format.
2. Add a RED Android test proving the expected phrase is absent with equivalent per-character mappings.
3. Identify whether the failing boundary is import scoring, exact-match flags, segmentation/max-code-length, or cache state.
4. Test the smallest source fix, then rerun the focused and complete Android suites.
5. Run an equivalent iOS fixture before assuming cross-platform impact.
6. Verify no regression to ordinary exact candidates, LD, related lookup after commit, or mixed English input.

## Backlog decision

No custom-table bug entry is added yet because the controlled test disproves a table-name-specific branch difference and the reporter's actual mapping metadata is still missing. `feat#176` remains limited to refreshing the bundled related-phrase database while preserving exact per-commit lookup.
