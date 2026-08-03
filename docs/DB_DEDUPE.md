# LIME DB Duplicate Cleanup — Deferred, Optional

**Status: deferred indefinitely. Not scheduled, not blocking anything.** This content was moved
out of [IOS_DB_COLD_HOT_REARCH2.md](IOS_DB_COLD_HOT_REARCH2.md) because duplicate cleanup is
irrelevant to the cold/hot editor-sync rework: that campaign only requires sync SQL that does not
*assume* uniqueness (keyed `WHERE` updates/deletes, `NOT EXISTS` inserts), which costs nothing and
is specified there. Everything below is the record of what exists and the spec for a cleanup, if
one is ever wanted.

## Why it is deferred

- The seeded databases and user backups are **shared between iOS and Android**, so the
  `user_version` bump (105 → 106) that makes uniqueness a schema invariant is only safe as a
  **lock-step release on both platforms**. An iOS-only 106 backup restored into an Android app at
  105 hits `SQLiteOpenHelper`'s downgrade path, which throws by default — cross-platform restore
  breaks outright.
- Stamping seeded assets at 106 would break current Android builds that seed from them.
- The measured impact does not justify urgency (next section).

## Impact assessment — why there is no urgency (verified 2026-08-01)

- **Mapping candidate display already dedupes.** `getMappingByCode` filters by word through a
  `duplicateCheck` set ([LimeDB.swift:704](../LimeIME-iOS/Shared/Database/LimeDB.swift#L704),
  [751](../LimeIME-iOS/Shared/Database/LimeDB.swift#L751), mirroring Android `buildQueryResult`);
  emoji search dedupes likewise. Doubled mapping rows never display twice.
- **Related-phrase display does NOT dedupe**
  ([getRelatedPhraseList, LimeDB.swift:941](../LimeIME-iOS/Shared/Database/LimeDB.swift#L941)) —
  and the 12 seeded `related` duplicates are the only user-visible artifact: e.g. `回`→來 at
  basescore 97 and 27 shows 來 twice in the related bar. Present for years; zero bug reports.
- **No learned data is at stake.** Verified across all 32 payloads: **zero** duplicate rows carry
  `score != 0`.
- Minor hidden cost: a duplicate occupies one slot of the SQL `LIMIT` and increments `rsize`
  before the dedup check, so it can push one real candidate off the initial page.
- The cold/hot sync campaign's keyed operations **converge duplicates on touch** (same score
  written to all copies; keyed delete removes all copies), so the eventual physical dedupe is a
  pure row-count reduction with no ranking change for any key the sync ever touched.

## Audit — duplicates in `Database/` (read-only, 2026-08-01)

All 32 SQLite payloads (root `lime.db`, 25 `.zip`, 6 `.limedb`) opened read-only:
**785 extra rows across 21 payloads**. Row-level dump with `_id`s:
[.claude/txt/db_duplicates_full_list.txt](../.claude/txt/db_duplicates_full_list.txt).

| Payload | Table | Extra rows |
| --- | --- | ---: |
| `lime.db` | `related` | 12 |
| `lime.zip` | `related` | 12 |
| `array.zip` | `array` | 8 |
| `cj.zip` | `cj` | 25 |
| `cjbig5.zip` | `cj` | 25 |
| `cjhk.zip` | `cj` | 24 |
| `ecj.zip` | `ecj` | 24 |
| `ecjhk.zip` | `ecj` | 24 |
| `ez.zip` | `ez` | 1 |
| `hs.zip` | `hs` | 405 |
| `hs1.zip` | `hs` | 15 |
| `hs2.zip` | `hs` | 8 |
| `hs3.zip` | `hs` | 2 |
| `phonetic.zip` | `phonetic` | 36 |
| `phoneticbig5.zip` | `phonetic` | 12 |
| `phoneticcomplete.zip` | `phonetic` | 40 |
| `phoneticcompletebig5.zip` | `phonetic` | 16 |
| `pinyin.zip` | `pinyin` | 36 |
| `pinyingb.zip` | `pinyin` | 36 |
| `wb.zip` | `wb` | 23 |
| `ez.limedb` | `ez` | 1 |

Zero-duplicate payloads: `array10.zip`, `cj5.zip`, `dayi.zip`, `dayiuni.zip`, `dayiunip.zip`,
`scj.zip`, `array.limedb`, `array10.limedb`, `hahacj.limedb`, `scj.limedb`, `tricode.limedb`.

### Duplication patterns (root causes to fix in the asset generator, not just the data)

- **Cangjie radical re-insert** — `cj`/`cjbig5`/`cjhk`/`ecj`/`ecjhk` all duplicate the 24–25
  radical roots (`a`→日 … `z`→重); the original row carries the `related` payload, the re-inserted
  copy has `related = NULL`. One upstream bug inherited five times.
- **Phonetic/pinyin shared doubles** — the same ~36 characters (体, 稱, 濕, 它, 纯…) duplicated
  with adjacent `_id`s across all four phonetic variants and both pinyin variants: doubled rows in
  the shared source data.
- **`hs.zip` doubled import block** — ~320 phrase pairs at basescore 1 with adjacent `_id`s, plus
  77 single-character pairs at real basescores, plus 4 triples (`9210`→孢, `k1lm`→第一度空間,
  `n-.k`→幾天之前, `n-.u`→幾天之內). At least two distinct causes.
- **`phoneticcomplete` far-apart appends** — user-dictionary-style entries added twice in separate
  passes (`joru`/`jr`→危及, `u.y9`/`uy`→又在).
- **`wb.zip` merged-source artifacts** — far-apart `_id` pairs (`m,nn,`→成 at `_id` 131 and 6925).
- **`hs1`/`hs2` empty-word rows** (`gltr`→``, `tzb'`→``) — would be rejected by the sync
  campaign's empty-key validation if imported today.

### Data-provenance flag (review separately from dedupe)

`hs`-family payloads contain entries that look like leaked personal data, shipped in the
distributed asset: `;fhh` → `40341台中市民權路234號12樓`, `x6th` →
`32042桃園市中壢區中正路487巷18號` (full street addresses with postal codes), and a name-like
entry `ylb` → `林庭暘`. Whether these should be scrubbed from the seeds is a privacy/provenance
question independent of duplication.

## Future campaign spec (if/when both platforms can release together)

### Prerequisite

The Android release must define an `onDowngrade` story (or a version-tolerant open) **before**
iOS ships 106, so an old Android build receiving a new backup fails gracefully instead of
throwing.

### Migration 106 — deterministic dedupe, then unique indexes

In one transaction per database: group mapping rows with non-NULL `code` and `word` by logical
key (two NULL related children are the same related key); keep the lowest-`_id` row as canonical;
set canonical `score` and `basescore` to the group maxima; preserve the canonical row's other
columns (`code3r`, `related` — the cj radical pattern shows the canonical lowest-`_id` row is the
data-bearing one); delete the rest; create the indexes; stamp `user_version = 106`. Maximum
rather than sum avoids inflating rank because duplicates happened to exist.

```sql
CREATE UNIQUE INDEX IF NOT EXISTS related_uq_pair
ON related(pword, cword)
WHERE pword IS NOT NULL AND cword IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS related_uq_null_child
ON related(pword)
WHERE pword IS NOT NULL AND cword IS NULL;

-- per mapping table, e.g.:
CREATE UNIQUE INDEX IF NOT EXISTS custom_uq_code_word
ON custom(code, word);
```

Interpolate only whitelisted / schema-verified table names, quote identifiers by doubling
embedded double quotes, keep values as bound parameters. Legacy `word IS NULL` rows are code-only
sentinels used by several bundled databases — not logical mappings; the partial indexes permit
them untouched.

### Importer dedupe

All `.limedb` / `.zip` / `.db` / `.lime` / `.cin` / related import paths and restore/backup copy
helpers: process rows deterministically (`_id ASC` for SQLite sources, file order for text),
insert-first, then `score = max(existing, incoming)` / `basescore = max(...)` for later
duplicates; reject empty required fields before any write. On Android without modern UPSERT:
`INSERT OR IGNORE` followed by a bound max-update. (The current `INSERT OR IGNORE`-only importer
behavior is ineffective without a unique index — it never ignores anything.)

### Asset normalization

A stdlib-only `scripts/dedupe_database_assets.py` (`sqlite3`, `zipfile`, `tempfile`): `--write`
applies the migration policy to every payload while preserving each archive's inner path and the
code-only NULL sentinels; `--check` opens every payload and fails on any remaining logical-key
duplicate. Add `--check` to `database-integrity.yml` so generated/downloadable assets cannot
regress. Update affected catalog/order record counts.
