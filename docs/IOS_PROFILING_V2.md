# iOS Phonetic-Keyboard CPU Profile — V2 (live, self-time)

Profiled the **real LimeKeyboard extension** while typing 注音 (phonetic) on
the iPhone 17 Pro Max simulator, driven like an ordinary user (installed IM,
real text field, on-screen key taps) and sampled with `/usr/bin/sample`. The
question: during phonetic typing, where does the keyboard's CPU actually go —
and is there a hotspot worth fixing?

Companion to [docs/IOS_PROFILING.md](IOS_PROFILING.md) (methodology) and the
responsiveness thread in [docs/IOS_TOUCH_REWRITE.md §13](IOS_TOUCH_REWRITE.md).

## 0. TL;DR

1. **The keyboard is CPU-light while typing — ~98% of samples are idle**
   (`__workq_kernreturn` + `mach_msg2_trap`, threads waiting between taps).
   There is **no hot CPU path** during phonetic typing.
2. **Of the small remainder of real work, the DB query's _execute_ is the
   single biggest slice** — `btreeInitPage` (SQLite loading table pages to read
   matched rows) is the top non-idle symbol. Its neighbours are `objc_msgSend`
   (UIKit), CoreText glyph rendering, and `pread` — all small.
3. **SQL statement _compilation_ is negligible (~0 self-time).** This corrects
   a wrong reading in earlier drafts — see §3.
4. **The query does not affect felt responsiveness.** It runs on a background
   `.userInteractive` thread (off the keypress path) and is **cached per code**
   (`SearchServer.mappingCache` + prefetch), so each distinct code queries once,
   then it's a µs dictionary hit. Per query it's µs (warm) to a few ms (cold).
5. **Shipped cleanup:** removed a redundant per-keystroke IM-config read (a
   filesystem `stat()` + lock + linear scan on the main thread). Small CPU, but
   a real per-stroke syscall eliminated — §4.2.
6. **Learning is cheap and off the tap path** (§4.3).

## 1. Method

| | |
|---|---|
| Device | iPhone 17 Pro Max simulator, iOS 26.5 (build 23F77), arm64 |
| IM | 注音輸入法 — **already installed** via the app's 輸入法 ▸ ＋ flow |
| Host app | Contacts → New Contact → First-name field (a normal text field, not a URL field) |
| Keyboard | LIME, switched Cangjie→注音 via the on-keyboard ☰ menu → **LIME 輸入法切換** → 注音輸入法 |
| Driving | `idb ui tap` in device points (`.claude/scripts/sim_phonetic_burst.sh`) — taps the real soft keys, commits candidates |
| Workload | `你好` × 12 (你=ㄋㄧˇ `su3`, 好=ㄏㄠˇ `cl3`), committing the top candidate each syllable so query + commit + learning all fire |
| Profiler | `/usr/bin/sample <LimeKeyboard pid> 22` — 1 ms statistical sampling of the live extension, all threads |

**Why `sample`, not Instruments/xctrace.** `xcrun xctrace record --attach` to a
**simulator** keyboard extension **hangs** here — it ignores `--time-limit` and
never finalizes the trace (an 8 s capture ran 8 min; SIGINT ignored), the same
class of wall as the [§10](IOS_PROFILING.md) XCUITest blocker. `sample` attaches
to the extension's host pid, never hangs, and produces a symbolicated call tree.

**Read self-time, not cumulative.** `sample` prints two views: a **cumulative**
"total-in-stack" list (how often a function was *anywhere* on the sampled stack)
and a **self-time** "top of stack" list (where the CPU actually was). Only
self-time is CPU time. Cumulative over-weights shallow wrapper frames — e.g.
`sqlite3Prepare` sits on the stack for the entire parse, so cumulatively it looks
huge even though its own work is tiny. All numbers below are **self-time**.

## 2. Confirmed working on the live keyboard

Driven end-to-end, screenshots in `.claude/txt/v2_*.png`:

- 注音 layout active (ㄅㄆㄇ…; s=ㄋ u=ㄧ c=ㄏ l=ㄠ, tone 3 = ˇ).
- Typing `su3` shows the composing bar `ㄋㄧˇ` with real DB candidates 你 妳 擬 拟.
- Committing 你 then 好 appends `你好`; the bar then shows related-phrase
  suggestions (像 處 萊塢 朋友 …) — the phrase-suggestion path is live.

## 3. Results — self-time (live `你好`×12, ~92k leaf samples @ 1 ms)

Top self-time symbols across all threads:

| Self-samples | Symbol | What it is |
|---:|---|---|
| 56,119 | `__workq_kernreturn` | **idle** — dispatch worker threads parked |
| 33,104 | `mach_msg2_trap` | **idle** — waiting for the next event |
| 454 | `kevent_id` | idle / event wait |
| **300** | **`btreeInitPage`** | **SQL execute** — load a phonetic-table btree page |
| 246 | `objc_msgSend` | UIKit / Obj-C dispatch |
| 215 | `pthread_get/setspecific` | thread-local (dispatch / GRDB) |
| 89 | `pread` | **SQL execute** — read a DB page from disk |
| 66 | `TFont::FindColorBitmapForGlyph` | CoreText — candidate glyph rendering |
| 46 | `sqlite3VdbeExec` | **SQL execute** — run the query bytecode |

**Reading it:**

- **Idle ≈ 89,700 / 91,700 ≈ 97.8%.** The extension spends almost all of its
  time parked between keystrokes. Real work is only ~2% of samples.
- **SQL execute is the biggest work category** — `btreeInitPage` (300) +
  `pread` (89) + `sqlite3VdbeExec` (46) + row compares/collation ≈ **~350–450
  self-samples**. That's the query fetching and sorting matched rows. It's the
  largest slice of the work, but the work itself is tiny.
- **SQL compile self-time ≈ 0.** Parser/prepare leaves (`RunParser`,
  `yy_reduce`, `GetToken`, `resolveExpr`) do not surface in self-time — SQL
  compilation is not a real cost. (An earlier draft reported "compile ≈ half the
  query, ~201 samples"; that was the **cumulative** count of the shallow
  `sqlite3Prepare` wrapper, not CPU. Corrected here.)
- UIKit (`objc_msgSend`), candidate glyph rendering (CoreText), and allocation
  are all in the same small band as the query — nothing dominates.

## 3.1 Real per-query wall-clock (ms)

`sample` gives CPU distribution, not durations, and live per-stroke signposts
can't be read here (the extension's logs never reach `log stream`, and `xctrace`
hangs on the sim extension). So query duration was measured directly with a
wall-clock benchmark timing the real `getMappingByCode` against seeded
`phonetic`/`dayi` tables (sim, warm OS page cache; `DispatchTime`, averaged):

| | first query of a new code | repeat (Swift-cached) |
|---|---:|---:|
| **Phonetic** (avg of 10 codes) | **3.8 ms** | ~1.2 µs |
| **Dayi** (avg of 10 codes) | **2.6 ms** | ~1.4 µs |

Range: phonetic `su3` 4.6 / `cl3` 4.3 / `g4` 3.5 / untoned `su`,`cl`,… ~3.3–3.5 ms;
dayi `ant` 3.3 … `5` 2.1 ms. Phonetic runs a touch slower (broad `code3r`
homophone scans).

**Reading it:** the first time a code is queried costs **~2.5–4.6 ms**; after
that `mappingCache` serves it in **~1.4 µs**. So per session you pay ~3 ms
**once per distinct code**, never again — and it's on a background
`.userInteractive` thread, so it delays the candidate bar by a few ms, once per
new code, without blocking the keypress. Simulator / Apple-Silicon-host numbers;
confirm on the WJIP17 before treating as absolute (§5.3).

## 4. Findings

### 4.1 The DB query is execute-bound, already-mitigated — leave it

The query (`getMappingByCode` → SQLite) is the biggest *work* item, and its cost
is **execute** (loading + scanning + sorting matched rows), not compilation. But
it needs no work, because it's already well-placed:

- **Off the tap path** — dispatched to a background `.userInteractive` queue, so
  its CPU never blocks a keypress.
- **Cached per code** — `mappingCache` + `triggerPrefetch` mean each distinct
  code queries once (then µs dictionary hits); it isn't re-run per keystroke.
- **Fast per query** — µs warm, a few ms cold ([IOS_PROFILING.md §11](IOS_PROFILING.md)).

The only lever that could cut the execute cost is **scanning fewer rows** (the
broad `code3r` between-search pulls hundreds of homophones), via a covering
index or a narrower match/sort. Both are **schema/logic changes to the frozen
Android-parity port** with real parity risk, for a background-thread/battery gain
nobody feels. **Do not pursue speculatively** — only if a *measured* device
cold-read or battery problem appears, and validate on-device first.

**Statement caching was tried and rejected.** A parameterize-+`cachedStatement`
change to `getMappingByCode` (dual-code path untouched) was implemented and run
through a golden before/after diff of the candidate lists:
candidate lists came back **byte-for-byte identical** (phonetic + dayi), so it's
result-safe — but self-time compile stayed flat because compile was only ~2% to
begin with. **Reverted**: ~2% is not worth editing `LimeDB.swift`.

### 4.2 ✓ Fixed — redundant per-keystroke IM-config read

`imkeys` / `imkeynames` / `limeendkey` were re-fetched via `DBServer.getImConfig`
→ `ImJsonLimeDB.current()` on the **main thread**, on every stroke, from both
`handleLimeEndkeyCommit`/`activeImkeysForEndkey` and `keyname()`
(`showComposingPopup`). Each call did a filesystem `stat()` on `im.json` + an
`NSLock` + a linear scan of the `im` array. The CPU self-time was small, but it's
a redundant per-keystroke syscall on the main thread for a value that only
changes on IM switch.

Fixed by caching per IM on `KeyboardViewController`
([KeyboardViewController.swift:49](../LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift#L49)):
a small `imConfigCache` read through `cachedImConfig(_:)`, cleared in `activeIM`'s
`didSet`. Re-profiled on the rebuilt keyboard: the whole
`getImConfig`/`current`/`SyncContract`/`attributesOfItem` chain no longer appears
during typing.

**Android parity:** output-equivalent. Android reads the same fields the same
way, just uncached — `LIMEService.handleEndkeyCommit` calls `getImConfig(activeIM,
IM_LIME_ENDKEY)` + `getImConfig(activeIM, IMKEYS_CONFIG)` per keystroke, and
[`LimeDB.java getImConfig`](../LimeStudio/app/src/main/java/net/toload/main/hd/limedb/LimeDB.java#L4800)
runs a raw `SELECT … FROM im …` every call. The BPMF *acceptance* split is intact
on both (`getPhoneticImKeys` ↔ iOS `imKeysForTable`/`currentImKeys`, untouched).
Android could take the same cache — follow-up on that side.

### 4.3 Learning needs no work

The per-commit score update (`learnRelatedPhraseAndUpdateScore` → `updateScore`)
is tiny and runs on a background `.utility` queue; the batch LD/RP learning
(`postFinishInput`) only runs on keyboard dismiss, never during typing. Do not
optimize the learning path for tap-to-tap.

## 5. Caveats

1. **Statistical CPU, not wall-clock.** `sample` shows where CPU goes, not the
   ms of one stroke. For per-stroke span durations, build the extension with
   `-D PROFILING` and read the `os_signpost`s via `log stream --signpost` (the
   installed build has PROFILING off).
2. **Mostly idle.** ~98% of samples are the extension waiting; conclusions are
   about the *shape* of the ~2% of work, not a claim that the keyboard is busy.
3. **Simulator ≠ device.** Apple-Silicon-host SQLite is faster than real A19
   flash on cold reads. The relative ranking holds; absolute numbers do not.
   Confirm on the WJIP17 before acting on the query cost.
4. **One session** (~22 s, one `你好`×12 burst) — directional, not a baseline.

## 6. Reproduce

```bash
# 注音 already installed; open a text field and switch LIME to phonetic
# (Contacts ▸ + ▸ First name ▸ ☰ ▸ LIME 輸入法切換 ▸ 注音輸入法), then:
PID=$(pgrep -x LimeKeyboard | head -1)
/usr/bin/sample "$PID" 22 -file sample_lime.txt &   # profile the live extension
.claude/scripts/sim_phonetic_burst.sh <UDID> 12     # type 你好 ×12 via idb
wait
# self-time (actual CPU) is the "Sort by top of stack" section; count is the LAST field:
awk '/Sort by top of stack/{a=1} a' sample_lime.txt \
  | grep -E '\(in ' | awk '{c=$NF;$NF="";print c"\t"$0}' | sort -rn | head -20
```

Helpers: `.claude/scripts/sim_tap.sh` (cliclick fallback),
`.claude/scripts/sim_phonetic_burst.sh` (idb typing driver).
