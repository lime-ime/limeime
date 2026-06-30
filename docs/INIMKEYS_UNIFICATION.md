# `isKeyInImkeys` Unification — Design

> Status: **design / not yet implemented. Prerequisite for #140.** Per decision 2026-06-30, #140
> will **not** ship before this lands; cj4's `;` is delivered *through* this unification (cj4
> keymap = `CJ_KEY` + the cj4 table's symbol roots), so #140 ships with **no** interim `setSymbolMapping` /
> forced-`hasSymbolMapping` / `refreshImKeys`-append — the two land together. Captured 2026-06-30.

## 1. Problem

Composing-acceptance ("does this keypress go into the composing buffer as a *root*, or get
output directly as text?") is decided three different ways today:

| Surface | Rule | Where |
|---|---|---|
| iOS phone | `hasSymbol` / `hasNumber` heuristic (broad: accept *all* symbol/digit codes when the flag is on) | [KeyboardViewController.swift:1391-1409](../LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift#L1391-L1409) |
| iOS iPad | `imkeys` membership (`currentImKeys.contains(char)`) | [KeyboardViewController.swift:1385-1390](../LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift#L1385) |
| Android | **same `hasSymbol`/`hasNumber` heuristic as iOS phone** (line-for-line mirror) — `isKeyInImkeys` exists but is used **only** for the endkey-commit path, *not* acceptance | accept: [LIMEService.java:5517-5549](../LimeStudio/app/src/main/java/net/toload/main/hd/LIMEService.java#L5517); endkey helper: [:2314 / :2416](../LimeStudio/app/src/main/java/net/toload/main/hd/LIMEService.java#L2416) |

`#140` exposed the cost of this split: cj4's `;` is a genuine root, but it lives in neither the
hardcoded iOS `CJ_KEY` nor cj4's table `imkeys`, so the iPad path rejected it while the phone
heuristic accepted it (forced `hasSymbolMapping`). We patched it per-platform; this doc removes
the need for such patches.

`hasSymbol` / `hasNumber` are also load-bearing for two *other* behaviours, which is why they
can't just be deleted:
- **Full-width `，`/`。` punctuation** — gated on `!hasSymbol` ([:1391](../LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift#L1391)).
- **Selkey (selection-key) validation** — `LIMEService` passes both flags to SearchServer
  "for selkey validation" ([:5398](../LimeStudio/app/src/main/java/net/toload/main/hd/LIMEService.java#L5398)); number-root IMs need digits treated as roots, not selectors.

## 2. Key insight

All three behaviours reduce to **one** question: *is this character a root for the active IM?*

- Composing acceptance → "it's a root."
- Full-width `，`/`。` → "`,`/`.` is **not** a root (so fall to punctuation)." Dayi *does* have
  `,./` as roots → composes; Cangjie does not → full-width. That's membership, **not** `!hasSymbol`.
- Selkey → "a digit/symbol is a selection key iff it's **not** a root."

So a single predicate `isKeyInImkeys(code) -> Bool` subsumes all of it. Android already has the
**named helper** `isKeyInImkeys` (today wired only to the endkey-commit path); iOS has the
building block (`imKeysForTable` + `currentImKeys`, used inline on iPad). Neither phone surface
uses it for **acceptance** yet — both phones run the `hasSymbol`/`hasNumber` heuristic; only iOS
iPad does membership. That's the work: route acceptance through `isKeyInImkeys` on all three.

## 3. Proposed design

Formalize `isKeyInImkeys(code)` as **the** authoritative root test on both platforms, backed by the
IM's root set, and route acceptance / punctuation / selkey through it:

```
// isKeyInImkeys(code) is true for the IM's roots AND always for ',' / '.'
root = isKeyInImkeys(code) || (isLetter && lettersAreRootsForThisIM)
if root:   accept into composing   // ',' / '.' compose → the lookup emits 「，」「。」 (ungated) + any table candidates
else:      direct output / selection-key handling
```

- **`,` / `.` are always roots.** `isKeyInImkeys(',')` / `isKeyInImkeys('.')` return `true` for
  **every** IM — membership for tables that map them (e.g. Dayi: `DAYI_KEY` has `,./`), a `||
  code==',' || code=='.'` special-case for the rest. This matches today exactly: `,`/`.` are
  *already* accepted by every IM (the `hasSymbol` flag only chose *which branch* accepted them).
  Once composing, the lookup ([LimeDB.swift:702-709](../LimeIME-iOS/Shared/Database/LimeDB.swift#L702))
  emits full-width 「，」「。」 **ungated** plus any table candidates. So there is **no** separate
  comma/period fallback branch — and the iPad half-width bug disappears (iPad now accepts `,`/`.`).
- **selkey:** digit/symbol is a selection key iff `!isKeyInImkeys(code)`.
- **cj4 + pref (#140):** `isKeyInImkeys` for cj4 returns `CJ_KEY` + the cj4 table's symbol roots (at least
  `;`). Because #140 ships **only after** this, cj4's `;` is delivered **entirely** through
  `isKeyInImkeys` — #140 carries **no** `SearchServer.setSymbolMapping`, no forced `hasSymbolMapping`,
  and no iOS `refreshImKeys` `;` append. The current #140 branch's interim versions of those are
  reworked out when the two land together.
- **`custom` IM — `imkeys` when present, prefs only as fallback.** If the imported custom table
  (`.cin` / `.lime` / `.limedb`) carries `imkeys` **and** `imkeynames`, custom uses `isKeyInImkeys`
  like every other IM, and the IM-details page **hides** `accept_number_index` /
  `accept_symbol_index` (the table's roots are authoritative). Only when `imkeys`/`imkeynames`
  are **missing** (legacy / hand-built tables) does custom fall back to those two prefs driving
  `hasNumber` / `hasSymbol` — and the page **shows** them. So `hasSymbol`/`hasNumber` survive
  *only* as the custom-without-`imkeys` fallback.
- Retire `hasSymbol` / `hasNumber` as the acceptance driver for built-in IMs (they may remain as
  cheap prefetch hints / capability metadata, but not as the acceptance gate).

**Bonus fix:** adding the `，`/`。` fallback to the unified path also fixes the iPad's *current*
bug, where the imkeys-only path emits half-width `,`/`.` instead of full-width on Cangjie/etc.

## 4. Root-data source of truth (the hard part)

**Table-primary, hardcoded fallback — and iOS mirrors Android.** A table's root set comes from its
imported `imkeys` (+ `imkeynames`) — the same data on both platforms. `imKeysForTable` returns that
stored value whenever both are present; the hardcoded keymaps (`CJ_KEY`, `DAYI_KEY`, `ARRAY_KEY`,
`ARRAY10_KEY` — [LimeDB.swift:113-124](../LimeIME-iOS/Shared/Database/LimeDB.swift#L113)) are **only
the fallback** for legacy tables that lack it. The **phonetic family is the one exception** — its
roots depend on the runtime `phoneticKeyboardType` (BPMF / ETEN26 / HSU), not a fixed table value,
so it stays keyboard-type-driven and is never table-overridden.

**Hard rule:** post-unification the iOS acceptance + imkeys code is **identical to Android** — same
`isKeyInImkeys`, same table-sourced imkeys (`getImConfig`), same `currentImKeys.isEmpty` heuristic
fallback. The *only* iOS-specific code is iPad layouts and the iPad-slide / iPhone-popup gestures.
No iOS-only `refreshImKeys` append, no iOS-only hardcoded-primary keymaps.

So cj4 with a `;`-mapping table accepts `' , . ; ? [ ]` straight from that table's `imkeys` — no
hardcoded cj4 append. array10 uses its table's `imkeys=1234567890`; `ARRAY10_KEY` is just its
fallback. The §5 catalog test guarantees every shipped table carries correct `imkeys`/`imkeynames`,
so the fallback keymaps are rarely hit.

**Status (iOS — done, 345/0 green):** `imKeysForTable` is table-primary (hardcoded fallback,
phonetic special); the iOS `refreshImKeys` `;`/`'` append is removed; 4 `imKeysForTable` unit tests
added. **Android** already sources imkeys from `getImConfig` — its remaining gap is routing *main
acceptance* through `isKeyInImkeys` (Android Phase 3) so the two are byte-for-byte aligned.

### Completeness gaps found

- **array10 (行列10) — done.** Its table carries `imkeys=1234567890`, so table-primary uses that
  directly. `ARRAY10_KEY="1234567890"` was added as the fallback and the `array`/`array10` case was
  split (array10 had wrongly shared `ARRAY_KEY`'s letters — a latent bug masked only because phone
  acceptance used the `hasNumber` heuristic, never `imKeysForTable`).
- **pinyin** — falls to the `default:` (stored-`imkeys`) branch; verify its imported `imkeys`
  carries its roots.
- **Symbol-heavy IMs (eten / et_41) need full coverage** — `lime_et_41_shift.xml` exposes the
  number row + symbol roots; `ETEN_KEY` already lists them, but the audit must confirm every key a
  *shift* layout exposes as a root is in the keymap. (Layouts don't change the root *set*, but they
  do change which roots are reachable, so a missing keymap entry surfaces only via the shift layout.)

## 5. Cloud-catalog `imkeys` integrity — integration test

The cloud IMs are served from the repo's `Database/` folder (base URL
`https://github.com/lime-ime/limeime/raw/master/Database/`) and enumerated in
[`IMCatalog.swift`](../LimeIME-iOS/LimeSettings/IMCatalog.swift) (iOS) /
`ImInstallFragment.java` (Android). Each entry carries `filename` (`.zip` or `.limedb` in
`Database/`), `tableName`, and `recordCount`.

Because acceptance now depends on the imported `imkeys`, **every** catalog file's `imkeys` /
`imkeynames` must be correct, or input silently breaks for that IM after download. Add an
integration test that iterates the catalog against the **local `Database/` folder** (no network):

For each `IMCatalog` entry:
1. Resolve `Database/<filename>` (decompress `.zip` → `.limedb`, or import `.cin`/`.lime`) into a
   fresh temp DB.
2. **Records:** imported count `> 0` and matches the catalog `recordCount` (exact, or within a
   small tolerance if compression/dedup applies).
3. **Meta:** `name` / `version` / `source` present and consistent with the catalog entry.
4. **imkeys / imkeynames present:** both non-empty.
5. **Invariant:** `count(imkeys chars) == count(imkeynames split on "|")` — the keyname-block
   integrity check (each `%keyname` line contributes one key char + one radical name).
6. **(cross-source, §4):** for IMs with a hardcoded iOS keymap, assert the imported `imkeys`
   equals that keymap — catches iOS/Android divergence.

Fail loudly per-IM so a malformed `Database/` file is caught in CI before it ships.

## 6. Test-coverage guard — characterization-first (required)

Hard requirement: **every code path that reads `hasSymbolMapping` / `hasNumberMapping` must be
converted to check `imkeys` (`isKeyInImkeys`) instead, and each conversion must have a test.** The
**only** exception is the **custom-IM fallback** for an old `.lime` / `.limedb` imported without
`imkeys`/`imkeynames` — that single path keeps `hasSymbol`/`hasNumber` (and is itself tested).
Method (characterization-first): write the test against today's behaviour (it passes now) → swap
the path to `isKeyInImkeys` → it must still pass. No path converts until its row below is green on the
*current* code. The table is the **exhaustive** list of `hasSymbol`/`hasNumber` reads; the *set*
sites that compute the flags (`detectIMCapabilities` → `setTableName` params) go vestigial once
all readers convert, kept only to feed the custom fallback.

| `hasSymbol`/`hasNumber` path | Behaviour it drives | Existing test | Action |
|---|---|---|---|
| iOS `handleCharacter` accept branches — [KeyboardViewController.swift:1362-1409](../LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift#L1362) | per-IM composing acceptance (letters / digits / symbols) | **none** | **ADD** characterization tests: Cangjie (letters compose, symbols don't), Dayi (`,./;` compose as roots), Array10 (digits compose), Phonetic |
| iOS comma/period — [:1391](../LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift#L1391) `!hasSymbol && (,\|.)` | full-width 「，」「。」 vs root | indirect only (emoji `test_3_6_4_6/7`) | **ADD** `,`/`.`→full-width (Cangjie) vs root (Dayi) |
| iOS `SearchServer.getSelkey` (number/symbol → selkey) | selection-key string | ✓ `SearchServerTest.test_3_5_5_2_getSelkey_number_symbol_combos` (+ `_1`/`_3`/`_4`, `test_getSelkey_after_setTableName`) | KEEP — assert unchanged after refactor |
| iOS prefetch — [SearchServer.swift:969-970](../LimeIME-iOS/Shared/Search/SearchServer.swift#L969) | cache warm-up keys | ✓ `SearchServerTest.test_3_3_2_2_prefetchCache_symbols` | KEEP (or re-point at `isKeyInImkeys`) |
| Android `handleCharacter` accept branches — [LIMEService.java:5517-5549](../LimeStudio/app/src/main/java/net/toload/main/hd/LIMEService.java#L5517) `!hasSymbolMapping && (,\|.)` etc. (mirror of iOS phone) | per-IM composing acceptance **+ `，。` full-width** | **none** | **ADD** per-IM acceptance + comma/period tests; then convert to `isKeyInImkeys` (helper already exists, used for endkey) |
| Android `getSelkey` — [SearchServer.java:1931](../LimeStudio/app/src/main/java/net/toload/main/hd/SearchServer.java#L1931) | selkey from flags | **verify/ADD** | **ADD** parity test (mirror iOS selkey) |
| Android mixed-mode selkey — [LIMEService.java:4340](../LimeStudio/app/src/main/java/net/toload/main/hd/LIMEService.java#L4340) `hasSymbolMapping && !dayi…` | mixed-mode selkey char (` ` vs `` ` ``) | **none** | **ADD** mixed-mode selkey test |
| **custom-IM fallback** (old `.lime`/`.limedb`, no `imkeys`/`imkeynames`) — `accept_number_index` / `accept_symbol_index` → `hasNumber` / `hasSymbol` | the **only** retained `hasSymbol`/`hasNumber` path | **none** | **KEEP — the one exception** + **ADD** test: custom *with* `imkeys`+`imkeynames` → `isKeyInImkeys`, prefs hidden; custom *without* → prefs shown + this fallback |

Rows marked **ADD** are prerequisite work — they must exist and pass on the *current* code before
any `hasSymbol`/`hasNumber` removal. (The data side — array10 keymap §4, catalog `imkeys` §5 — is
guarded separately.)

## 7. Migration / risk / scope

- **Touches:** iOS acceptance + `imKeysForTable`; Android acceptance + selkey; the keymap
  constants; the new integration test.
- **Risks:** keymap completeness (array10 confirmed; audit the rest), selkey rewire, physical-
  keyboard input validation, and regression surface on *working* phone behaviour — so per-IM
  acceptance tests on both platforms are required (Cangjie, Dayi, Array, Array10, Pinyin, Phonetic
  variants incl. ETEN/HSU shift, Custom, and #140 cj4 `;`).
- **Preserved:** per-IM root composing, full-width `，`/`。`, selkey.
- **Fixed:** iPad half-width `,`/`.` gap; iPhone/iPad/Android consistency; cj4 `;` as a clean
  special case.
- **#140 depends on this** — #140 does not ship until this lands and delivers cj4's `;` through
  `isKeyInImkeys`, not the interim `setSymbolMapping` path; the two ship together.

## 7. Task checklist

- [ ] Hardcode `ARRAY10_KEY` (digit roots) + split `array`/`array10` in `imKeysForTable` (iOS);
      mirror on Android.
- [ ] Audit `pinyin` and all `default:`-case IMs' imported `imkeys`; confirm ETEN/HSU/et_41 shift
      coverage.
- [ ] Introduce `isKeyInImkeys(code)` on iOS (mirror Android `isKeyInImkeys`); route acceptance through it.
- [ ] Add the `，`/`。` non-root → full-width fallback to the unified path (fixes iPad half-width).
- [ ] Rewire selkey validation to `!isKeyInImkeys`.
- [ ] Assert hardcoded keymap == imported `imkeys` per built-in IM (cross-source consistency).
- [ ] Integration test: iterate `IMCatalog` over `Database/`, import each, assert records / meta /
      `imkeys` / `imkeynames` present + `count(imkeys) == count(imkeynames)`.
- [ ] `custom` IM: use `isKeyInImkeys` when the imported table has `imkeys`+`imkeynames` and **hide**
      `accept_number_index` / `accept_symbol_index` in IM-details (iOS `IMDetailView` + Android
      `ImDetailFragment`); fall back to those two prefs (and show them) only when `imkeys`/`imkeynames`
      are missing.
- [ ] Rework the #140 branch onto `isKeyInImkeys` (land it **with** #140, not after): drop
      `SearchServer.setSymbolMapping` (iOS + Android) + call sites, the forced `hasSymbolMapping`
      for cj4, and the iOS `refreshImKeys` `;` append — cj4's `;` comes from its keymap instead.
- [ ] Retire `hasSymbol` / `hasNumber` as the acceptance gate everywhere **except** the
      custom-without-`imkeys` fallback, once the above pass.
