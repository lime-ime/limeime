# Issue #103: English candidate disappearance and dictionary coverage around `salt`

## Live issue state

- Issue: https://github.com/lime-ime/limeime/issues/103
- Status: closed / reporter-confirmed fixed for the English candidate scope on Android APK `LIMEHD2026-6.1.17.apk`; adjacent punctuation/no-endkey follow-up confirmed on APK `LIMEHD2026-6.1.18.apk`.
- Current labels: `enhancement`, `Usability`
- Reporter: `SmithCCho`
- Initial maintainer acknowledgement: https://github.com/lime-ime/limeime/issues/103#issuecomment-4629503196

## Problem statement

Reporter `SmithCCho` raised an English candidate usability issue: while typing `salt`, the expected candidate disappears when the word becomes an exact match. The public report also notes that `salt` appears late while typing `sal`.

Detailed cross-platform design lives in [ENG_AUTO_COMPLETION.md](ENG_AUTO_COMPLETION.md).

## Confirmed evidence

The actual bundled seed used by both platforms is byte-identical between:

- `LimeStudio/app/src/main/res/raw/lime.db`
- `Database/lime.db`

The bundled `dictionary` table has only one `salt*` row:

```text
2965|salt
```

For comparison, `year*` has non-exact follow-up rows:

```text
235|year
6298|year's
7981|yearly
121|years
```

Android filters exact English dictionary matches:

```sql
WHERE word MATCH '<typed>*'
AND word <> '<typed>'
```

That filter leaves `salt` with zero suggestion rows, because the only DB match is the exact word.

Before the #103 fix, Android also sorted English dictionary suggestions alphabetically. For `sal`, previous ordering was:

```text
Salem, Salisbury, salaries, salary, sale, sales, sally, salmon, saloon, salt, ...
```

So the `salt` ordering complaint was caused by `ORDER BY word ASC`, not by user score or frequency ranking. The #103 short-term fix uses `ORDER BY rowid ASC` as the existing dictionary rank signal.

## Implemented #103 spec

1. Keep filtering exact dictionary matches.

   Android English composing already has the typed word as the composing/self candidate. Returning the exact DB row too would create duplicate candidates, for example `year` + `year`.

2. If the exact match is the only dictionary match, still show the composing/self candidate as the only candidate item.

   Expected Android behavior:

   ```text
   salt -> [salt]
   ```

   This item should be the composing/self candidate, not a dictionary suggestion. Do not remove the exact-match filter just to make `salt` appear.

3. English composing / auto-completion mode should never pre-highlight a candidate.

   This applies whether the list is `[salt]` or `[year, year's, yearly, years]`. English candidates should be visible and tappable, but no item should be selected by default.

4. English suggestions should move away from plain alphabetical sorting.

   Short-term implemented: prefer existing dictionary row/rank order.

   Long-term: use a richer dictionary schema with `basescore` and local user `score`, matching the Chinese table score model.

## iOS alignment

iOS English completion uses a different flow from Android. It uses `UITextChecker.completions(...)` and displays completions with no default highlight (`selectedIndex = -1`). Because iOS does not prepend the same Android-style composing/self candidate in this path, it does not have the same duplicate exact-match problem.

The Android fix should align the visible behavior with iOS: English completion candidates are shown without default highlight.

## Android code paths

- `LimeStudio/app/src/main/java/org/limeime/limedb/LimeDB.java`
  - Keep the exact-match filter in `getEnglishSuggestions(...)`.
  - Sort current dictionary suggestions by `rowid ASC` instead of `word ASC`.
- `LimeStudio/app/src/main/java/org/limeime/LIMEService.java`
  - In the English prediction path, when dictionary suggestions are empty, show the composing/self candidate instead of calling `clearSuggestions()`.
  - Use a no-highlight candidate display path for all English composing candidates.
- `LimeStudio/app/src/main/java/org/limeime/candidate/CandidateView.java`
  - Add an explicit way to set suggestions while leaving `mSelectedIndex = -1`, scoped to English prediction.
  - Do not change normal Chinese/table IM default highlight behavior.

## Scored dictionary upgrade implemented in 6.1.17

Android APK `LIMEHD2026-6.1.17.apk` also includes the longer-term scored dictionary work from commits `6e66723` and `6a2ca8d`: a separate bundled `dictionary.db` payload, import/query support, and local English suggestion learning. This supersedes the older FTS-only `dictionary(word)` fallback for ranked prefix suggestions.

Implemented shape:

```sql
dictionary(
  word TEXT PRIMARY KEY,
  basescore INTEGER DEFAULT 0,
  score INTEGER DEFAULT 0
)
```

- `basescore`: bundled frequency/rank score from the generated frequency payload.
- `score`: per-user local learning score; this remains private user data and should not be disclosed publicly.
- Android imports the bundled dictionary payload, queries prefix suggestions using scored ranking, and increments local score when users pick English suggestions.
- Upgrade path remains schema-driven/idempotent, including restored legacy DBs whose actual schema may not match `PRAGMA user_version`.
- The normal indexed prefix lookup avoids repeating the #88 stale FTS virtual-table failure pattern for English completion.

## Verification plan

1. Type `salt`.
   - Candidate strip shows one item: `salt`.
   - No item is highlighted.
2. Type `year`.
   - Candidate strip does not show duplicate exact `year` entries.
   - No item is highlighted.
3. Type `sal`.
   - Existing suggestions still appear.
   - No item is highlighted.
4. Verify normal Chinese/table IM candidates still keep their existing default highlight and end-key behavior.
5. Verify tapping an English candidate still commits the tapped item.
6. Verify `sal` no longer puts `salt` behind capitalized proper nouns solely because of alphabetical order.

## Public follow-up status

Android test APK `LIMEHD2026-6.1.17.apk` (GitHub Contents blob SHA `4b0f42af2b9d97e9b9c1e87ec87bffa1271d1e2f`, size 13930960 bytes) included the earlier English composing visibility/ranking fix commit `794f741e6102cdf1c0db82f5cc6ea6280d2d5029` plus the scored dictionary / frequency-ranking / learning commits `6e667232ba3d75a45fa11957882df19e45db8c8e` and `6a2ca8d187861c0109b74c4033db7a8c3767352f`. The scored dictionary path superseded the interim rowid-ranking behavior for Android 6.1.17 retest purposes.

Automation reopened #103 after the source-fix closure and posted the scoped 6.1.17 retest request: https://github.com/lime-ime/limeime/issues/103#issuecomment-4641196730.

Resolved English retest scope:

1. `salt` exact-match English composing remains visible as a tappable candidate.
2. `sal` / `salt` ranking is improved by the new scored dictionary/frequency path.

Reporter `SmithCCho` confirmed in https://github.com/lime-ime/limeime/issues/103#issuecomment-4641456931 that APK `6.1.17` keeps full English matches visible in the candidate strip and improves the English candidate ordering.

The same comment raised an adjacent #96-style punctuation/end-key concern: on `6.1.17`, `,` / `.` did not behave like `6.1.16` when no end-key was set. Maintainer `jrywu` then posted APK `6.1.18` in https://github.com/lime-ime/limeime/issues/103#issuecomment-4643262621 for that no-endkey punctuation follow-up. Reporter `SmithCCho` confirmed in https://github.com/lime-ime/limeime/issues/103#issuecomment-4643356687 that on `6.1.18`, with end-key set `,` / `.` output full-width punctuation directly, and without end-key the candidate default is on the full-width punctuation. Maintainer `jrywu` closed #103 as completed on 2026-06-07.

Future public wording should continue to describe #103 itself as English composing/candidate-display and ranking usability work, not that exact-match filtering itself was a bug. The punctuation/no-endkey notes are adjacent #96 behavior verification, not a reason to reopen #103 unless new evidence appears.
