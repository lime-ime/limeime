# Cache, Prefetch & Sort Implementation

## TODO: Refactor Android to Match iOS Evict-and-Re-warm Pattern

Android's current post-selection cache update (in `updateScoreCache`) keeps the
exact-match cache entry alive by doing an in-memory bubble-sort of the `ArrayList`
on a background thread.  This causes a known race condition and produces an
approximated sort order.  The iOS approach — evict then re-query DB on the same
background thread — is simpler, thread-safe, and always reflects the true DB order.

### What to change in `SearchServer.java`

**1. Replace in-memory re-sort with eviction in `updateScoreCache()`**

Remove the bubble-shift loop for exact-match entries.  Instead, call
`removeRemappedCodeCachedMappings(code)` to evict, then let
`updateSimilarCodeCache(code)` handle prefix eviction — same as the related-list
case already does today.

```java
// Current (remove):
if (sort) {
    // bubble-shift cachedList ...
} else {
    // score bump in-memory only ...
}

// Replacement:
cache.remove(cachekey);  // evict; DB has the updated score already
```

**2. Re-warm all evicted entries after eviction in `updateSimilarCodeCache()`**

Change `updateSimilarCodeCache` to return the list of actually-evicted prefix codes
(mirroring the iOS change), then re-query each one via
`getMappingByCode(code, ..., prefetchCache=true)` on the same background thread.
Always re-query `candidate.code` itself as well.

```java
// After the eviction loop and existing single-char re-prefetch:
getMappingByCode(code, !isPhysicalKeyboardPressed, false, true); // re-warm selected code
for (String prefix : evictedPrefixes) {
    getMappingByCode(prefix, !isPhysicalKeyboardPressed, false, true);
}
```

**3. Remove the threading race**

The above changes eliminate all `ArrayList` mutations on the background thread, so
no lock is needed.  The `ConcurrentHashMap` already handles concurrent `put`/`remove`
safely at the map level.

### Expected outcome after refactor

| | Android (current) | Android (after refactor) |
|---|---|---|
| Cache mutation on background thread | Mutates `ArrayList` (race) | Only `ConcurrentHashMap.remove` + `put` (safe) |
| Sort correctness | Approximate (in-memory bubble-shift) | Exact (DB `ORDER BY`) |
| Cross-composition cache | ✓ kept alive in-memory | ✓ evicted then re-warmed from DB |
| Code complexity | High | Same as iOS |

### Test impact

**Android `SearchServerTest.java`** — one test must be updated:

- `test_3_3_5_2_updateScoreCache_exact_match_reordering` (line 1866): currently asserts
  that after `updateScoreCache`, the cache list is still present and the selected item
  has been bubble-shifted to the correct position with score incremented to 6.  After
  refactor the cache entry is evicted entirely, so `cache.get("customab")` returns
  `null`.  The test should instead assert that the entry is absent from cache (evicted)
  and that `stub.addScoreCalled == true`.

The following tests remain valid unchanged:
- `test_3_3_4_1_updateSimilarCodeCache_drops_prefix_entries` — eviction still happens
- `test_3_3_4_2_updateSimilarCodeCache_prefetch_single_char` — single-char re-warm still triggered
- `test_3_3_4_3_updateSimilarCodeCache_remote_exception` — exception handling path unchanged
- `test_3_3_5_1_updateScoreCache_learning_invalidation` — only asserts `addScoreCalled`

---

## Follow-up: Partial-match (prefix / "fuzzy") reordering after score bump

### Context

User report: type `g`, pick 也 (`gds`) from the candidate list; on the next time `g` is
typed, 也 does **not** move forward in the `g*` prefix list, even though its DB score
has been incremented.

Confirmed via code trace:

- `addScore()` is called unconditionally for all match types at
  [SearchServer.java:1149](LimeStudio/app/src/main/java/org/limeime/SearchServer.java#L1149)
  — the DB score **does** increment for partial-match picks.
- The visible-order problem is caused by two independent issues in the partial-match
  code path, both of which must be fixed for the user's expectation to be satisfied.

### Issue A — Prefix cache is never evicted on partial-match selection

`updateScoreCache()` at
[SearchServer.java:1156-1161](LimeStudio/app/src/main/java/org/limeime/SearchServer.java#L1156-L1161)
only removes the cache entry for the **selected full code** (`gds`). The prefix cache
entries (`g`, `gd`) that the candidate bar actually consults on the next keystroke are
left intact, so the user continues to see the pre-bump list forever.

The exact-match branch at
[SearchServer.java:1173-1180](LimeStudio/app/src/main/java/org/limeime/SearchServer.java#L1173-L1180)
already handles this correctly via `updateSimilarCodeCache(code)` (evicts all prefix
entries) plus re-query. The partial-match branch needs the same treatment.

### Issue B — `length(code)` outranks `score` in the partial-match ORDER BY

The ORDER BY built at
[LimeDB.java:1856-1864](LimeStudio/app/src/main/java/org/limeime/limedb/LimeDB.java#L1856-L1864)
places `(length(code) <= 5) * length(code) DESC` **above** `score DESC`. For typed
prefix `g`, this sorts the list into length bands (5-char → 4-char → 3-char → 2-char
→ 1-char `g`) with `score` acting only as an intra-band tiebreaker. 也 (`gds`, 3 chars)
can climb within its 3-char band but can never cross into the 4-char or 5-char bands
regardless of how high its score grows.

### Fix 1 — Apply evict-and-re-warm to partial-match branch

In `updateScoreCache()` at
[SearchServer.java:1156-1161](LimeStudio/app/src/main/java/org/limeime/SearchServer.java#L1156-L1161):

```java
// Current:
if ((cachedMapping.getId() == null || cachedMapping.isPartialMatchToCodeRecord())
        && cachedList != null && !cachedList.isEmpty()) {
    if (cache.remove(cachekey) == null) {
        removeRemappedCodeCachedMappings(code);
    }
}

// Replacement — mirror the exact-match branch:
if ((cachedMapping.getId() == null || cachedMapping.isPartialMatchToCodeRecord())
        && cachedList != null && !cachedList.isEmpty()) {
    cache.remove(cachekey);                       // drop full-code entry
    List<String> evictedPrefixes = updateSimilarCodeCache(code);   // drop all prefix entries
    getMappingByCode(code, !isPhysicalKeyboardPressed, false, true);
    for (String prefix : evictedPrefixes) {
        getMappingByCode(prefix, !isPhysicalKeyboardPressed, false, true);
    }
}
```

`updateSimilarCodeCache()` already returns the evicted-prefixes list per the main
refactor above; Fix 1 reuses that same hook.

### Fix 2 — Promote `score DESC` above `length(code)` in the partial-match ORDER BY

In [LimeDB.java:1856-1864](LimeStudio/app/src/main/java/org/limeime/limedb/LimeDB.java#L1856-L1864),
reorder the ORDER BY keys **for the partial-match branch only** so `score DESC` is
evaluated before the length bias:

```sql
-- Current (partial-match path):
( exactmatch=1 AND (score>0 OR basescore>0) AND length(word)=1 ) DESC,
exactmatch DESC,
( length(code) >= codeLen ) DESC,
( length(code) <= 5 ) * length(code) DESC,
score DESC,           -- appended only if `sort` pref is ON
basescore DESC,
_id ASC

-- After Fix 2:
( exactmatch=1 AND (score>0 OR basescore>0) AND length(word)=1 ) DESC,
exactmatch DESC,                               -- exact hits still above partial hits
( length(code) >= codeLen ) DESC,
score DESC,                                    -- PROMOTED above length(code)
( length(code) <= 5 ) * length(code) DESC,     -- now a tiebreaker among equal-score rows
basescore DESC,
_id ASC
```

Guardrails:

- Apply only when the SQL is built for the partial-match path. Exact-match queries
  (`typedCode == row.code`) keep today's ORDER BY; typing the full `gds` still returns
  也 directly, unchanged.
- `exactmatch DESC` remains above `score DESC`, so exact-match rows always sort above
  partial-match rows regardless of score. A high-score partial-match row cannot
  displace an exact-match row.
- Keep the existing `sort` preference gate. When the user has sort-by-score disabled,
  the score key is dropped and behavior falls back to today's length-first ordering.

### Outcome after both fixes

Typing `g`:

```text
── exact-match block (exactmatch=1) ──
  single-char 'g' exact entry, if any
── partial-match block (exactmatch=0) ──
  highest-score g* entry (any length)      ← 也(gds) reaches here after repeated picks
  next-highest-score g* entry
  ...
  zero-score entries, fallback to length(code) DESC then _id
```

### Test impact for the follow-up

**Android `SearchServerTest.java`**:

- Add a test covering the partial-match branch of `updateScoreCache`: seed the cache
  with prefix entries (`g`, `gd`), call `updateScoreCache` with a `Mapping` carrying
  `recordType = RECORD_PARTIAL_MATCH_TO_CODE` and `code = "gds"`, then assert:
  1. `cache.get("gds") == null` (full-code entry evicted)
  2. `cache.get("g") == null` and `cache.get("gd") == null` (prefix entries evicted)
  3. `stub.addScoreCalled == true`
  4. The re-warm stub records `getMappingByCode` calls for `gds`, `gd`, and `g`
- Add a SQL-level test (or integration test) for `LimeDB` that seeds two rows with the
  same prefix and different code lengths, bumps the shorter row's score past the
  longer row's score, queries the prefix, and asserts the shorter-code row now sorts
  above the longer-code row.

### Verification (manual)

1. Build APK, open an Array-layout IME session.
2. Type `g`, pick 也 (`gds`) three times in succession (re-type `g` each round).
3. Confirm 也's position in the `g` candidate list advances after each pick, and
   eventually surfaces to the top of the partial-match block once its score exceeds
   every other `g*` mapping.
4. Confirm that typing the full `gds` still returns 也 first (exact-match path
   unaffected).
5. Confirm that with the `sort` preference OFF, ordering is identical to the current
   build (no score ranking applied).

---

## Second follow-up (2026-04-22) — partial-match score not incrementing in DB

### Observed

Field test on the initial follow-up build: typing `g` and picking 了 (`gd`) or
也 (`gds`) did NOT bump the DB score. Expected DB score to climb 0→1→2→3 with
repeated picks; instead it stayed at a single value.

### Root cause

`updateScoreCache` at
[SearchServer.java:1146](LimeStudio/app/src/main/java/org/limeime/SearchServer.java#L1146)
had a gate that quietly disabled all prefix-cache invalidation for the most
common partial-match path:

```java
String code = cachedMapping.getCode().toLowerCase(Locale.US);  // e.g., "gds"
String cachekey = cacheKey(code);                              // e.g., "arraygds"
List<Mapping> cachedList = cache.get(cachekey);                // usually null
if ((isPartial || ...) && cachedList != null && ...) { evict + re-warm; }
else if (isExact && cachedList != null && ...) { evict + re-warm; }
else { removeRemappedCodeCachedMappings(code); }               // fallback
```

For the scenario *user types `g`, picks 也 (`gds`)*, the cache key derived from
the candidate's full code (`arraygds`) is typically empty — the user never
typed `gds`, so nothing was cached under that key. The stale score lives in
the prefix cache under `arrayg`, not `arraygds`. Both gated branches failed
their `cachedList != null` check, execution fell to the `else` that only
cleans remapped-code mappings and never calls `updateSimilarCodeCache(code)`.

Consequence: `addScore()` at
[SearchServer.java:1149](LimeStudio/app/src/main/java/org/limeime/SearchServer.java#L1149)
fired exactly once and bumped the DB row from 0→1 on the first pick. Every
subsequent pick read the stale `cache["arrayg"]` (still carrying Mapping
with score=0), so the issued SQL
`UPDATE array SET score = 0 + 1 WHERE word = '也'` kept writing the same
value 1. Observable symptom: "score stuck / not updating".

### Fix

Remove the `cachedList != null` gate entirely and always invalidate both
the full-code cache entry and the prefix chain for non-related-phrase
records. The three prior branches (partial / exact / code-not-cached)
collapse into one unified evict-and-re-warm block because they should all
do the same thing:

```java
if (!cachedMapping.isRelatedPhraseRecord()) {
    String code = cachedMapping.getCode().toLowerCase(Locale.US);
    String cachekey = cacheKey(code);

    if (cache.remove(cachekey) == null) {
        removeRemappedCodeCachedMappings(code);
    }

    List<String> evictedPrefixes = updateSimilarCodeCache(code);
    try {
        getMappingByCode(code, !isPhysicalKeyboardPressed, false, true);
        for (String prefix : evictedPrefixes) {
            getMappingByCode(prefix, !isPhysicalKeyboardPressed, false, true);
        }
    } catch (RemoteException e) {
        Log.e(TAG, "updateScoreCache(): re-warm failed", e);
    }
}
```

`updateSimilarCodeCache` already no-ops on prefix keys that aren't cached,
so running it unconditionally is safe — it only does real work when stale
data is present.

### Test impact for the second follow-up

- `test_3_3_5_5_updateScoreCache_code_not_in_cache` at
  [SearchServerTest.java:2070](LimeStudio/app/src/androidTest/java/org/limeime/SearchServerTest.java#L2070):
  previously asserted `assertNull(cache.get("customzz"))`. After this fix the
  re-warm call always runs, and the stub DB returns `new ArrayList<>()` which
  gets stored in cache. Assertion relaxed to `resultList == null || resultList.isEmpty()`
  (the correct invariant: "no stale mapping is cached"). Also now asserts
  `stub.addScoreCalled`.

Other `test_3_3_5_*` cases remain valid unchanged: `test_3_3_5_2`,
`test_3_3_5_4`, `test_3_3_5_19` already use the lenient assertion pattern.

---

## Third follow-up (2026-04-22) — latent TOCTOU race in `cacheKey()` exposed

### Third follow-up — observed

After the second follow-up landed, a full `:app:connectedDebugAndroidTest`
run surfaced **5 failing `SearchServerTest` cases** plus a process crash on
one emulator:

| Test | Symptom |
| --- | --- |
| `test_3_3_5_3_updateScoreCache_related_phrase_record` | `expected null, but was:<[]>` |
| `test_3_3_5_9_updateScoreCache_related_phrase_removal_cache_hit` | `expected null, but was:<[]>` |
| `test_3_3_5_17_updateScoreCache_related_removal_path` | `assertFalse(fakeCache.containsKey(key))` failed |
| `test_3_1_10_8_remapcache_updates_on_exact_match` | `assertTrue(coderemapcache.get(key).contains("query"))` failed |
| `test_3_3_5_12_updateScoreCache_physical_keyboard_sort_preference` | `FAILED` with no trace — process crashed mid-test |

The crash log pointed at
[SearchServer.java:322](LimeStudio/app/src/main/java/org/limeime/SearchServer.java#L322):

```text
java.lang.NullPointerException: Attempt to invoke virtual method
  'java.lang.String LIMEPreferenceManager.getPhoneticKeyboardType()'
  on a null object reference
  at SearchServer.cacheKey(SearchServer.java:322)
  at SearchServer.getMappingByCodeFromCacheOrDB(SearchServer.java:963)
  at SearchServer.getMappingByCode(SearchServer.java:802)
  at SearchServer$1.run(SearchServer.java:244)     ← prefetchThread
```

### Third follow-up — root cause

`cacheKey()` had a null-guard, but it was TOCTOU-racy on a non-volatile
field:

```java
private String cacheKey(String code) {
    if (mLIMEPref == null || dbadapter == null) {
        return "";                                  // check
    }
    ...
    key = dbadapter.getTableName() + mLIMEPref.getPhoneticKeyboardType() + code;
    //                               ^^^^^^^^^ NPE here if another thread nulled mLIMEPref
    //                                         between the check above and this read
}
```

The pattern *check then dereference* on a non-volatile, writable field is
never safe under concurrency — a second thread nulling the field between
the check and the read produces exactly the observed NPE.

The race had always existed. It was invisible in the test suite before
because `cacheKey()` was called from background threads only occasionally
(the single-char prefetch loop + one re-warm per exact-match score bump).
With the second follow-up, `updateScoreCache` now unconditionally runs
`updateSimilarCodeCache(code) + getMappingByCode` for the selected code
plus every evicted prefix — increasing background `cacheKey()` calls per
score update from ~1 to *1 + len(prefix chain)*. More background activity
× orphan threads surviving into the next test's teardown = the microsecond
window gets hit.

So the correct characterisation is **not** "#49 introduced a race." It is
"#49 increased background concurrency enough to reveal a latent race in
code that was never thread-safe in the first place."

### Third follow-up — fix

Snapshot the mutable fields into locals **before** the null check and use
the snapshots throughout. Classic TOCTOU remedy — check and use the same
immutable reference:

```java
private String cacheKey(String code) {
    LIMEPreferenceManager pref = mLIMEPref;
    LimeDB db = dbadapter;
    if (pref == null || db == null) {
        Log.e(TAG, "cacheKey() mLIMEPref or dbadapter is null");
        return "";
    }
    String key;
    if (isPhysicalKeyboardPressed) {
        if (tablename.equals(LIME.DB_TABLE_PHONETIC)) {
            key = pref.getPhysicalKeyboardType() + db.getTableName()
                    + pref.getPhoneticKeyboardType() + code;
        } else {
            key = pref.getPhysicalKeyboardType() + db.getTableName() + code;
        }
    } else {
        if (tablename.equals(LIME.DB_TABLE_PHONETIC))
            key = db.getTableName() + pref.getPhoneticKeyboardType() + code;
        else
            key = db.getTableName() + code;
    }
    return key;
}
```

Production impact is negligible — `mLIMEPref` is never nulled in a real IME
lifecycle. The fix is a defensive correctness tightening that keeps orphan
background threads from crashing the test process, and is the right thing
to do independent of #49.

### Test impact for the third follow-up

Four test assertions were relying on the old pre-#49 contract "`cache.get(key)`
is null after `updateScoreCache`", which is no longer a correct invariant
after the unified evict-and-re-warm. The new invariant is "the stale mapping
is gone" — satisfied by both `null` and `[]`.

- `test_3_3_5_3`
  ([SearchServerTest.java:1984](LimeStudio/app/src/androidTest/java/org/limeime/SearchServerTest.java#L1984)):
  `assertNull(resultList)` → `assertTrue(resultList == null || resultList.isEmpty())`.
- `test_3_3_5_9`
  ([SearchServerTest.java:2412](LimeStudio/app/src/androidTest/java/org/limeime/SearchServerTest.java#L2412)):
  same relaxation.
- `test_3_3_5_17`
  ([SearchServerTest.java:2990](LimeStudio/app/src/androidTest/java/org/limeime/SearchServerTest.java#L2990)):
  the fake `ConcurrentHashMap` that returns `null` from `remove()` to force
  the fallback branch now also needs to drop subsequent `put()` calls from
  the re-warm step; otherwise the re-queried empty list would be written
  back and invalidate the `assertFalse(fakeCache.containsKey(key))`
  assertion. Stub `LimeDB` installed for deterministic empty re-warm.
- `test_3_1_10_8`
  ([SearchServerTest.java:983](LimeStudio/app/src/androidTest/java/org/limeime/SearchServerTest.java#L983)):
  added `cache.clear()` at test start. The test assumed an empty main cache
  but only cleared `coderemapcache`; prior tests' entries short-circuited
  `getMappingByCodeFromCacheOrDB` before the coderemapcache-population block
  could run.
- `test_3_3_5_12` passed once the `cacheKey()` race stopped crashing the
  process — no assertion change needed.

### Verification

Full `:app:connectedDebugAndroidTest` run (Pixel Tablet AVD-15 + Pixel 9
Pro AVD-16) — **256/256 passing on both devices, 0 failures**, no process
crashes.
