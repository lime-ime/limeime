# `isKeyInImkeys` Unification — Autonomous Goal-Mode Completion Plan

> **Runnable in one pass by an autonomous agent.** No open decisions, no "may defer", no
> "confirm" — every choice is locked in §3, every work item has a unique ID and deterministic
> steps (§4), an explicit execution loop (§7), and binary gates + review gates (§8). Verified by
> full input-logic audit on both platforms, 2026-07-02/03 (git `1004453b` = what actually landed;
> the old "not implemented / Android Phase 3" framing was stale/phantom and is gone).
>
> **STATUS 2026-07-04 — COMPLETE & VERIFIED.** All of W-A…W-I plus both D-4 follow-ups landed;
> and a real bug the audit surfaced — **et26/hsu acceptance leaking BPMF's digits/`;`/`-`** — was
> found and fixed. The authoritative **as-built** record (per-IM DB verification, the
> acceptance↔selkey split, the array30 proof, the et26 fix, the endkey-path verification, and the
> coverage matrix) is **§11**. §2/§4/§5 are the original plan, kept for history.

---

## 0. Goal (end-state) — ACCEPTED 2026-07-03

`isKeyInImkeys(code, imkeys)` decides **root-ness** for the two decisions that ARE derivable from
the root set, **identically on iOS and Android**:

1. **Composing acceptance** — a keypress composes iff it is a root.
2. **Full-width `，`/`。`** — `,`/`.` are always roots (compose → lookup emits 「，」「。」).

**Selkey is NOT reducible to `!isKeyInImkeys`** (this was disproven — the old §0/§1 claim was
wrong). The selkey set is a per-IM policy imkeys cannot express: **array** has `hasNumber=true`
yet `ARRAY_KEY` carries no digit root (it maps `w`+digit → symbols), so its selkey must stay
`!@#$%^&*()`, never `1234567890`. Selkey therefore **keeps** the per-IM `hasSymbol`/`hasNumber`
flags (`resolveSelkey` + `mixedModeSelkeyUsesSpace`), hardcoded in `initialIMKeyboard`, optionally
overridable from table meta (missing meta → LIMEDB fallback). eten26/hsu happen to derive F/F
from their type-specific keymap, but that is a coincidence, not the mechanism.

For **acceptance only**, `hasSymbol`/`hasNumber` survive **solely** as the `imkeys.isEmpty()`
fallback for a **custom IM with no `imkeys`/`imkeynames`**; that case shows and honors the
`accept_number_index`/`accept_symbol_index` prefs. Every other IM (and custom-with-imkeys) ignores
the flags for acceptance.

---

## 1. The single rule

```text
isKeyInImkeys(code, imkeys):
    if code == ',' (44) or code == '.' (46):  return true      // always roots
    if imkeys is empty:                        return false     // → custom fallback
    return imkeys contains code (case-insensitive)

acceptsIntoComposing(code, imkeys, hasSymbol, hasNumber, isPhonetic):
    if imkeys non-empty:  return isKeyInImkeys(code, imkeys) || (isPhonetic && isSpace)
    else:                 return <hasSymbol/hasNumber heuristic>   // custom-no-imkeys ONLY

resolveSelkey(configured, hasNumber, hasSymbol, isPhonetic, isDayi, isStdPhonetic):
    // per-IM POLICY — NOT derivable from imkeys. flags hardcoded in initialIMKeyboard;
    // meta may override, missing meta → LIMEDB fallback. (array: hasNumber=true, no digit root.)
```

iOS acceptance already implements this ([KeyboardViewController.swift:1387](../LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift#L1387),
`isKeyInImkeys` [:1369](../LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift#L1369)). Android's
`isKeyInImkeys` ([LIMEService.java:2442](../LimeStudio/app/src/main/java/net/toload/main/hd/LIMEService.java#L2442))
lacks the `,`/`.` rule and Android acceptance never calls it — those are the gaps.

---

## 2. Verified current state (sites — labelled S# to avoid colliding with work IDs)

### iOS — input core **done**; only the custom-prefs tail remains
| Site | State | Evidence |
|---|---|---|
| S-i1 acceptance | ✅ imkeys-first; heuristic only when `currentImKeys` empty | KeyboardViewController.swift:1387 |
| S-i2 endkey | ✅ imkeys-driven | `LimeEndkeyPolicy.isKeyInImkeys` (:1097-1101) |
| S-i3 `，`/`。` | ✅ subsumed (`isKeyInImkeys` true for `,`/`.`) | :1369 |
| S-i4 selkey | ✅ N/A — no digit-based selkey path (tap-to-pick) | — |
| S-i5 custom toggles | ❌ orphaned — `accept_number_index`/`accept_symbol_index` written, read by no input path (fallback = `detectIMCapabilities` scan); shown for all custom IMs | IMDetailView.swift:198; LimeDB.swift:3839; hook `applyPrefsToSearchEngine` :845 |
| S-i6 prefetch | ⚠️ flag-driven, benign; tests SKIPPED | SearchServer.swift:969 |

### Android — only the endkey path converted
| Site | Decision it gates | Severity |
|---|---|---|
| S-a1 `acceptsIntoComposing` [:2135](../LimeStudio/app/src/main/java/net/toload/main/hd/LIMEService.java#L2135) / call [:5544](../LimeStudio/app/src/main/java/net/toload/main/hd/LIMEService.java#L5544) | composing-acceptance — **array10 letter-leak** | **critical** |
| S-a2 `isKeyInImkeys` [:2442](../LimeStudio/app/src/main/java/net/toload/main/hd/LIMEService.java#L2442) | missing `,`/`.`→true → punctuation regresses if routed here as-is | blocks S-a1 |
| S-a3 array30 `w`+digit [:5550](../LimeStudio/app/src/main/java/net/toload/main/hd/LIMEService.java#L5550) | separate accept branch (`hasSymbolMapping && !hasNumberMapping && IM_ARRAY && prev=='w'`) | med |
| S-a4 `getSelkey` [SearchServer.java:1948](../LimeStudio/app/src/main/java/net/toload/main/hd/SearchServer.java#L1948) | selkey validation via flag matrix | high |
| S-a5 mixedMode selkey display [:4366](../LimeStudio/app/src/main/java/net/toload/main/hd/LIMEService.java#L4366) / [:5461](../LimeStudio/app/src/main/java/net/toload/main/hd/LIMEService.java#L5461) | candidate-bar selkey prefix char (`hasSymbolMapping && !IM_DAYI`) | required¹ |
| S-a6 `ImDetailFragment` [:223](../LimeStudio/app/src/main/java/net/toload/main/hd/ui/view/ImDetailFragment.java#L223) | custom toggles shown for all custom IMs | UI |

¹ S-a5 is **required, not cosmetic-optional**: its `hasSymbolMapping &&` decision-reads must go or
the §8 retirement gate cannot pass.

Correct already: Android endkey [:2340](../LimeStudio/app/src/main/java/net/toload/main/hd/LIMEService.java#L2340)
(template); custom-fallback set-site [:5358-5359](../LimeStudio/app/src/main/java/net/toload/main/hd/LIMEService.java#L5358).
**Infra gap:** Android has no cached `currentImKeys` field (iOS caches at [:914](../LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift#L914));
the one Android `isKeyInImkeys` call fetches ad-hoc via `getImConfig(activeIM,"imkeys")` [:2334](../LimeStudio/app/src/main/java/net/toload/main/hd/LIMEService.java#L2334).

---

## 3. Locked decisions (all — nothing left to confirm)

- **D-1 Custom fallback = prefs.** custom-with-imkeys → `isKeyInImkeys`, prefs hidden.
  custom-without-imkeys → `accept_number_index`/`accept_symbol_index` drive `hasNumber`/`hasSymbol`,
  prefs shown. iOS wires the dead prefs into the fallback via `applyPrefsToSearchEngine`
  ([KeyboardViewController.swift:845](../LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift#L845));
  `detectIMCapabilities` scan becomes vestigial.
- **D-2 array30 (S-a3) = DE-FLAG the `w`+digit branch, do NOT delete** (DONE 2026-07-03).
  Corrected: `ARRAY_KEY = "qazwsxedcrfvtgbyhnujmik,ol.p;/"` — array's roots are letters + `,.;/`,
  **no digits**. So digits are correctly rejected by imkeys, and the `w`+digit symbol-input
  exception (array symbols1) must **stay**. Applied: dropped `hasSymbolMapping && !hasNumberMapping`,
  gate on `activeIM==IM_ARRAY && mComposing.matches("w[0-9]*") && isDigit && !mEnglishOnly`. This
  removes the flag decision-read (retirement gate) AND fixes the latent array30 regression the
  imkeys change introduced (old code accepted *bare* digits via `hasNumber`; new is stricter —
  digits only inside a `w`-sequence, which is more correct).
- **D-3 mixedMode selkey (S-a5) = IN SCOPE** ([:4366]/[:5461]). Convert to the selkey model; drop
  the `hasSymbolMapping &&` reads. Required for the retirement gate. **Not** deferrable.
- **D-4 One-pass scope = W-A … W-I** (§4). **Explicit follow-ups, OUT of the done-gate**
  (do not block the one pass): iOS prefetch repoint (S-i6 — benign, unit tests skipped →
  code-review only) and the cloud-catalog integrity integration test (§9) — a CI-infra deliverable
  needing a separate DB-import harness. Both are listed in §9, neither is in the §8 done-gate.

---

## 4. Work items (unique IDs W-A … W-I — deterministic, no hedges)

**Group A — Android acceptance (fixes the array10 leak)**
- **W-A** `isKeyInImkeys` [:2442]: add `if (primaryCode==',' || primaryCode=='.') return true;` at the
  top. (Also affects the endkey path [:2340] — `,`/`.` become endkey-roots; covered by the endkey test.)
- **W-B** Add a cached `String currentImKeys` field; refresh it in the `setTableName` path
  ([:5358-5428](../LimeStudio/app/src/main/java/net/toload/main/hd/LIMEService.java#L5358)) via
  `getImConfig(activeIM,"imkeys")` — mirror iOS `refreshImKeys`.
- **W-C** Change `acceptsIntoComposing` signature to `(code, imkeys, hasSymbol, hasNumber, isPhonetic)`;
  add leading branch `if (!imkeys.isEmpty()) return isKeyInImkeys(code, imkeys) || (isPhonetic && isSpace);`
  keep the `hasSymbol`/`hasNumber` branches only under `imkeys.isEmpty()`. Update the call site [:5544]
  to pass `currentImKeys`.
- **W-D** Delete the standalone `!hasSymbolMapping && (,|.)` accept branch ([:2145]) — subsumed by W-A.
- **W-E** (DONE) De-flag the array30 `w`+digit branch per **D-2** — keep the symbol-input
  exception, drop the mapping-flag reads. Gated: compile + `AcceptsIntoComposingTest` green
  (incl. new `arrayRootsFromImkeys_bareDigitsRejected`). The branch lives in `handleCharacter`
  (integration-level) — multi-digit symbols1 needs on-device confirmation.

**Group B — Android selkey**
- **W-F** `getSelkey` ([SearchServer.java:1948]) → `selkeyValid = (digit|symbol) && !isKeyInImkeys(code, imkeys)`.
  SearchServer reads imkeys via `dbadapter.getImConfig(tablename,"imkeys")` (already used [:1941](../LimeStudio/app/src/main/java/net/toload/main/hd/SearchServer.java#L1941)).
- **W-G** mixedMode selkey display ([:4366]/[:5461]) per **D-3** → derive from the selkey model;
  remove the `hasSymbolMapping &&` + hardcoded-IM reads.

**Group C — custom-IM detail (both platforms)**
- **W-H** Gate the `accept_number_index`/`accept_symbol_index` section on **imkeys-missing**:
  iOS [IMDetailView.swift:198](../LimeIME-iOS/LimeSettings/Views/IMDetailView.swift#L198),
  Android [ImDetailFragment.java:223](../LimeStudio/app/src/main/java/net/toload/main/hd/ui/view/ImDetailFragment.java#L223).
- **W-I** Make the prefs drive the custom-no-imkeys fallback on both (per **D-1**): iOS wire the dead
  prefs into `applyPrefsToSearchEngine` ([:845]) reading
  [LIMEPreferenceManager.swift:183](../LimeIME-iOS/Shared/Preferences/LIMEPreferenceManager.swift#L183);
  Android already does ([:5358-5359]).

Vestigial after the above (leave, they only feed the custom fallback): per-IM flag assignments
[:5368-5419](../LimeStudio/app/src/main/java/net/toload/main/hd/LIMEService.java#L5368) + iOS
`detectIMCapabilities`.

---

## 5. Tests — baseline + create/flip/keep

### 5.0 Baseline (verified 2026-07-03)
| Layer | Current test | Action |
|---|---|---|
| Android acceptance | `AcceptsIntoComposingTest` — 3 tests, 4-arg signature; `array10…` asserts `assertTrue('a'/'A')` = **locks the leak**; green now | **FLIP + re-sign**: add `imkeys` arg to every call; array10(`"1234567890"`)→ letters `assertFalse`; keep cangjie/dayi; ADD custom-no-imkeys (empty imkeys→heuristic) **and** an `array` case (guards D-2) |
| Android selkey | none | **CREATE** `GetSelkeyTest` mirroring the iOS selkey model |
| Android custom detail | none | **CREATE** `CustomImkeysTest` (with-imkeys hidden / without shown+effective) |
| iOS acceptance | `KeyboardViewControllerTest` "isKeyInImkeys unification characterization" (1487–1550), incl. array10 `currentImKeys="1234567890"` | **KEEP green** — reference to mirror on Android |
| iOS endkey / selkey | `KeyboardViewControllerTest` (1097–1101) / `SearchServerTest.test_3_5_5_*` | keep |
| iOS prefetch | SKIPPED (reflection) | code-review only (§9 follow-up) |

### 5.1 Method (per-item)
- **keep-green**: iOS acceptance/selkey/endkey; Android cangjie/dayi after re-sign — green now, green after.
- **flip**: Android array10 — existing assertion encodes the bug; flip `assertTrue('a'/'A')`→`assertFalse`.
- **create**: Android selkey/custom — new tests, green on post-change behavior.

### 5.2 Target coverage
| Test | Asserts | Platforms |
|---|---|---|
| Cangjie | letters compose; symbols don't | iOS + Android |
| Dayi | `,./;` compose as roots | iOS + Android |
| **Array10** | digits compose; **letters do NOT** (the fix) | iOS + Android |
| Array (24-key) | roots per imkeys (guards D-2 removal) | Android |
| Comma/period | full-width 「，」「。」 (Cangjie) vs root (Dayi) | iOS + Android |
| Selkey parity | Android `getSelkey` == `(digit\|symbol) && !isKeyInImkeys` | Android (mirror iOS) |
| Custom with / without imkeys | prefs hidden / shown+effective | iOS + Android |
| cj4 `;` (#140) | composes from table imkeys (no `setSymbolMapping`) | iOS + Android |

Keep green: iOS `SearchServerTest.test_3_5_5_2_getSelkey_number_symbol_combos` (+ `_1/_3/_4`, `test_getSelkey_after_setTableName`).

---

## 6. Data source of truth (already landed — reference)

Table-primary, hardcoded fallback (`CJ_KEY`/`DAYI_KEY`/`ARRAY_KEY`/`ARRAY10_KEY`
[LimeDB.swift:113](../LimeIME-iOS/Shared/Database/LimeDB.swift#L113)); **phonetic** roots follow
runtime `phoneticKeyboardType`. Done: iOS `imKeysForTable` table-primary; array10 split from array
(`imkeys=1234567890`); Android sources imkeys from `getImConfig`. During W-A verify `array` imkeys
carries its digit roots (guards D-2), plus `pinyin` / ETEN/HSU/et_41 shift coverage.

---

## 7. Autonomous execution loop

```text
groups = [A (W-A..W-E), B (W-F..W-G), C (W-H..W-I)]
for group in groups:
    for item in group:                       # in listed order
        1. TEST FIRST per §5 tag:
             keep-green → run per-item gate, record it's green now
             flip       → rewrite the assertion to the FIXED expectation (red now)
             create     → write the new test class (red / no-class now)
        2. IMPLEMENT the §4 step(s) for `item`.
        3. Run the item's PER-ITEM gate (§8) → must be GREEN with the new expectation.
        4. Run ALL GLOBAL gates (§8) → must stay GREEN.
        5. If any gate red after ≤3 attempts → STOP, print the failing gate output, DO NOT advance
           (systematic-debugging: research before a 4th try).
    6. Run the group's REVIEW GATE (§8). If it fails → fix within the group before the next group.
after C: run the DONE-GATE (§8). green → goal complete. red → the failing check names the residual.
```

Order is fixed: **A → B → C**. Group A must fully pass (incl. its review gate) before B; B before C.

---

## 8. Gates

### Global gates — run every iteration; must stay green
```bash
cd LimeStudio && ./gradlew :app:compileDebugJavaWithJavac --offline
cd LimeStudio && ./gradlew :app:testDebugUnitTest --offline
.claude/scripts/ios-gate.sh unit LimeTests/KeyboardViewControllerTest    # acceptance/endkey/layout
.claude/scripts/ios-gate.sh unit LimeTests/SearchServerTest              # selkey/search
```
(Verified 2026-07-03: the Android `--tests` filter runs; `ios-gate.sh`'s real interface is
`unit LimeTests/<Class>` — no `--tests` flag.)

### Per-item gates
| Item | Gate command | Tag | Green when |
|---|---|---|---|
| W-A/W-B | `cd LimeStudio && ./gradlew :app:testDebugUnitTest --offline --tests '*AcceptsIntoComposingTest'` | re-sign | `acceptsIntoComposing` takes `imkeys`; `,`/`.`→true; `currentImKeys` refreshed on `setTableName`; cangjie/dayi green |
| W-C/W-D/W-E | same command | **flip** | array10 `'a'`/`'A'` now `assertFalse`; digits/`,`/`.` true; custom-no-imkeys case green; `array` case green (D-2) |
| W-F | `./gradlew :app:testDebugUnitTest --offline --tests '*GetSelkeyTest'` (new) + `ios-gate.sh unit LimeTests/SearchServerTest` | create | android selkey == iOS model; iOS `SearchServerTest` green |
| W-G | `./gradlew … --tests '*GetSelkeyTest'` (add mixedMode cases) | create | prefix from the model; **no** `hasSymbolMapping &&` in :4366/:5461 |
| W-H/W-I | `./gradlew … --tests '*CustomImkeysTest'` (new) + `ios-gate.sh unit LimeTests/KeyboardViewControllerTest` | create | with-imkeys → hidden; without → shown **and** effective |

### Review gates (self-review at each group boundary — binary)
- **After A:** `grep -nE '\bhas(Symbol|Number)Mapping\b *(&&|\|\||\?)' LimeStudio/app/src/main/java/net/toload/main/hd/LIMEService.java` returns **only** the mixedMode lines (:4366/:5461, cleared in B) — i.e. **no acceptance** decision-read remains; `grep 'acceptsIntoComposing(' LIMEService.java` shows the `imkeys` param at every call.
- **After B:** the grep above returns **empty** for LIMEService.java; `grep -nE '\bhas(Symbol|Number)Mapping\b' SearchServer.java` shows **no** boolean-decision use in `getSelkey` (only storage/set).
- **After C:** both detail pages branch on imkeys-missing; iOS `applyPrefsToSearchEngine` reads `acceptNumberIndex`/`acceptSymbolIndex`; `CustomImkeysTest` + iOS gate green.

### Done-gate (binary — all must pass)
1. `./gradlew :app:testDebugUnitTest --offline` green **and** `AcceptsIntoComposingTest` array10 case asserts `'a'`/`'A'` **`assertFalse`**; `ios-gate.sh unit LimeTests/KeyboardViewControllerTest` + `… SearchServerTest` green.
2. **Retirement (must return EMPTY — a real binary):**
   ```bash
   # Field used as a boolean DECIDER (&& / || / ?, or a bare `if(!has…Mapping)`).
   # Verified 2026-07-03: on current code this matches EXACTLY the 3 decision-reads
   # :4366, :5461, :5550 — and does NOT flag the allowed :5544 param-pass (that keeps
   # feeding the custom fallback). W-E done (array30 de-flagged); → EMPTY once W-G (:4383/:5482)
   # and W-F (SearchServer getSelkey :1948/1956/1963) land.
   grep -rnE '\bhas(Symbol|Number)Mapping\b *(&&|\|\||\?)|\bif\s*\(\s*!?\s*has(Symbol|Number)Mapping\s*\)' \
     LimeStudio/app/src/main/java/net/toload/main/hd
   ```
   Allowed remaining `hasSymbolMapping`/`hasNumberMapping` (the grep does NOT match these): an
   assignment (`= …`), the `setTableName(tablename, hasNumberMapping, hasSymbolMapping)` arg, the
   `acceptsIntoComposing(…, hasSymbolMapping, hasNumberMapping, …)` param-pass at :5544, and
   DEBUG-log concats. Any grep match = **not done**.
3. **Parity:** Android `AcceptsIntoComposingTest` mirrors iOS `KeyboardViewControllerTest` 1487–1550
   (same IMs, same expectations).

---

## 9. Explicit follow-ups (OUT of the one-pass done-gate)

Per **D-4**, these are separate tasks and do **not** block goal completion:
- **iOS prefetch repoint** (S-i6, `SearchServer.swift:969`) — point at `imKeysForTable` instead of
  the flags. Benign (cache warm-up); unit tests are `@SKIP`ped, so verify by code review.
- **Cloud-catalog `imkeys` integrity test** — a no-network integration test iterating `IMCatalog`
  over local `Database/`: per entry import into a temp DB and assert records>0(≈`recordCount`),
  meta present, `imkeys`/`imkeynames` non-empty, `count(imkeys)==count(imkeynames split "|")`, and
  imported `imkeys`==hardcoded keymap where one exists. Fail loudly per-IM in CI.

---

## 10. Risk / preserved / fixed

- **Fixed:** array10 (and any digit-root IM) letter-leak; iOS↔Android acceptance/selkey parity;
  custom detail-page honesty (prefs only when they matter).
- **Preserved:** per-IM root composing; full-width `，`/`。`; selkey behaviour.
- **Risks + guards:** W-A touches the endkey path (endkey test guards); W-E removes array30's special
  case (the `array` acceptance test guards); physical-keyboard input inherits acceptance
  (`translateKeyDown`→`handleCharacter`, same tests); regression on working IMs → the §5 matrix.
- **#140:** cj4 `;` already sourced from table imkeys (`1004453b`); keep it so — the cj4 test guards.

---

## 11. As-built verification record — COMPLETE (2026-07-04)

Authoritative final state. Supersedes the planning framing in §2/§4/§5. Verified three ways:
**(U)** JVM unit test, **(D)** per-IM inspection of the bundled/cloud DB tables, **(C)** compile +
gate run (Android offline gates; iOS on the booted iPhone 17 Pro Max). Nothing is asserted from
memory — every per-IM claim below was read from the actual `im` meta.

### 11.1 The core finding — acceptance and selkey do NOT share a source

They answer different questions on (largely disjoint) key sets, so only one is imkeys-derivable:

| decision | code path | derivable from imkeys? | mechanism (as built) |
|---|---|---|---|
| **Acceptance** | `acceptsIntoComposing` ([LIMEService.java:5572](../LimeStudio/app/src/main/java/net/toload/main/hd/LIMEService.java#L5572)) | **YES** — acceptance ≡ "is it a root" ≡ imkeys membership | imkeys-first; flags consumed **only** in the `imkeys.isEmpty()` custom-legacy fallback |
| **Selkey** | `resolveSelkey` ([SearchServer.java:1954](../LimeStudio/app/src/main/java/net/toload/main/hd/SearchServer.java#L1954)) + `mixedModeSelkeyUsesSpace` (:4383/:5491) | **NO** — per-IM policy imkeys can't express | keeps hardcoded `hasNumber`/`hasSymbol` (meta-overridable, LIMEDB fallback) |

Retirement grep is **EMPTY** — no `has(Symbol|Number)Mapping` boolean decision-read remains outside
the allowed param-passes/assignments.

### 11.2 Per-IM verification (read from the real `im` meta, 2026-07-04)

Derivation rule for the table: `hasNumberRoot` = imkeys has a `0-9`; `hasSymbolRoot` = imkeys has a
non-alphanumeric **other than `,`/`.`**.

| IM | imkeys (root set) — source | derived N/S | hardcoded N/S | selkey derivable? | acceptance verdict | how |
|---|---|---|---|---|---|---|
| cj / cj4 / scj / cj5 / ecj | `CJ_KEY` letters | F/F | F/F | ✅ match | imkeys-first, letters only | U |
| phonetic **standard/et_41** | `BPMF`/`ETEN` (digits+symbols) | T/T | T/T | ✅ match | imkeys-first | D |
| phonetic **et26** | `ETEN26_KEY` letters+`,.` | F/F | F/F | ✅ match | **fixed** — type-resolved (§11.5) | U+D+C |
| phonetic **hsu** | `HSU_KEY` letters+`,.` | F/F | F/F | ✅ match | **fixed** — type-resolved (§11.5) | U+D+C |
| ez | `EZ_KEY` digits+symbols | T/T | T/T | ✅ match | imkeys-first | D |
| dayi | `DAYI_KEY` digits+`;/` | T/T | T/T | ✅ match | imkeys-first | D |
| array10 | `"1234567890"` | T/F | T/F | ✅ match | imkeys-first (letter-leak fixed) | U+D |
| **array (30)** | `imkeys="…rstuvwxyz./;,"`, **no digit**; `selkey="1234567890"` | F/T | **T/T** | ❌ **irreducible** (§11.3) | imkeys-first + `w`+digit exception | U+D |
| **pinyin** | **no imkeys meta** (count 0); tone digits are real codes (`a2`,`ae4`; 26 867 digit codes) | — | T/F | n/a — flag fallback | **unchanged** — `imkeys.isEmpty()` → old heuristic accepts tone digits | D |
| **wb** | **no imkeys meta** (count 0) | — | F/T | n/a — flag fallback | **unchanged** — flag heuristic | D |
| **hs** | **no imkeys meta** (count 0) | — | T/T | n/a — flag fallback | **unchanged** — flag heuristic | D |
| custom | detail-page toggles | = prefs | = prefs | n/a (not imkeys) | imkeys-first if present, else toggles (§11.6) | U+C |

### 11.3 array30 — the one proven selkey irreducible (concrete)

From `array.limedb` (unzipped): `imkeys = "abcdefghijklmnopqrstuvwxyz./;,"` (**no digits**),
`selkey = "1234567890"`. Trace `resolveSelkey("1234567890", hasNumber, hasSymbol=T, isPhonetic=F)`:

- **hardcoded `hasNumber=true`** → validity loop invalidates the all-digit selkey → fallback
  `hasNumber&&hasSymbol` → **`!@#$%^&*()`** ✅ (correct — digits are busy with `w`+digit symbol entry)
- **derived `hasNumber=false`** → digits are *not* invalidated → selkey kept as **`1234567890`** ❌

So deriving would flip array's selection keys `!@#$%^&*()` → `1234567890`. That is why the flag
stays. `mixedModeSelkeyUsesSpace` keys off `hasSymbol` (which array derives correctly as T) so it is
**not** on this mismatch — the divergence is `resolveSelkey`/`hasNumber` only.

### 11.4 pinyin / wb / hs — no imkeys meta ⇒ zero acceptance change

All three ship **no `imkeys` row** in their `im` table (`SELECT count … title='imkeys'` = 0) and have
no hardcoded keymap → `currentImKeys=""` → `acceptsIntoComposing` takes the `imkeys.isEmpty()`
fallback = the **exact pre-migration flag heuristic**. Byte-identical behaviour. Pinyin's tone
digits (`1-5`, confirmed present in its codes) keep composing via `hasNumber=true`. The imkeys-first
branch never fires for these IMs.

### 11.5 et26 / hsu acceptance — bug found by the audit, fixed

**Bug:** the migration made Android acceptance imkeys-first, and `currentImKeys` was
`getImConfig("phonetic","imkeys")` = the **type-agnostic stored BPMF** string (digits + `;/-`). For
et26/hsu (flags F/F) the old code accepted **letters + `,.` only**; the new imkeys-first path with
BPMF **also composed digits and `;/-`** — a regression. iOS never had this (`imKeysForTable` branches
on `phoneticKeyboardType`).

**Fix (Android, mirrors iOS `imKeysForTable`):**
- `LimeDB.getPhoneticImKeys(type)` → `ETEN26_KEY / HSU_KEY / ETEN_KEY`, else `BPMF_KEY`
  ([LimeDB.java](../LimeStudio/app/src/main/java/net/toload/main/hd/limedb/LimeDB.java), before `getImConfig`).
- `SearchServer.getPhoneticImKeys` proxy.
- `currentImKeys` for the phonetic IM now resolves by type ([LIMEService.java:5457](../LimeStudio/app/src/main/java/net/toload/main/hd/LIMEService.java#L5457)).
- Test: `AcceptsIntoComposingTest.imkeysPath_eten26_rejectsDigitsAndSymbols` — with `ETEN26_KEY`
  roots, `3`/`;`/`/` reject; letters + `,.` + phonetic-space compose. **(U)**

Physical-keyboard phonetic variants (MILESTONE/desireZ/chacha/xperiapro) fall through to BPMF —
**unchanged from before the fix** (they were BPMF too); not a new regression.

### 11.6 Endkey path — BPMF is correct and REQUIRED (verified, not changed)

`handleEndkeyCommit` ([:2346](../LimeStudio/app/src/main/java/net/toload/main/hd/LIMEService.java#L2346))
reads the **type-agnostic BPMF** imkeys, then `isKeyInImkeys(primaryCode, imkeys)` to decide whether
to append the endkey char before commit. Computed against the real phonetic meta:

- every endkey char (`3467'[]\=<>?:"{}|~!@#$%^&*()_+`) **is** in BPMF → all append; commit-only set empty.
- tone digits **`3,4,6,7`** are endkeys **and** in BPMF → **appended** (correct — tones are part of the code).
- non-tone digits **`1,2,5,8,9,0`** are **not** endkeys → they fall to acceptance (type-resolved, §11.5).

Using `ETEN26_KEY` here would make `isKeyInImkeys('3', ETEN26_KEY)=false` → **tones stop appending** →
et26 commit breaks. So the two paths **correctly** use different imkeys: endkey-commit needs BPMF
(universal tones), acceptance needs the type keymap (reject non-root digits). **No change needed.**

### 11.7 Group C + prefetch (S-i6)

- **iOS W-I** — `SearchServer.detectIMCapabilities` returns the `accept_number_index`/
  `accept_symbol_index` toggles for the `custom` IM (single choke point → all `setTableName` sites).
  Test `SearchServerTest.test_3_5_7_1_detectIMCapabilities_custom_readsAcceptToggles`. **(U+C)**
- **Android W-I** — already wired (`getAllowNumberMapping()` reads the same pref key). **(C)**
- **W-H both platforms** — the number/symbol section is hidden when the custom IM ships imkeys
  (iOS `IMDetailView` via `DBServer.getImConfig`, Android `ImDetailFragment` via `getImConfig`). **(C)**
- **S-i6 prefetch** — `triggerPrefetch` warms `imKeysForTable(...)` (the real roots) instead of
  `a-z`+flag guess; falls back to the flag set only for custom-no-imkeys. **(C)**

### 11.8 Coverage matrix (honest — what is verified how)

| Path | U | D | C |
|---|:--:|:--:|:--:|
| `acceptsIntoComposing` (imkeys-first + 5 fallback branches) | ✅ | | ✅ |
| `isKeyInImkeys` (`,`/`.`, empty, membership) | ✅ | | ✅ |
| `resolveSelkey` / `mixedModeSelkeyUsesSpace` (all branches) | ✅ | | ✅ |
| et26 acceptance rejects digits/`;`/`/` | ✅ | ✅ | ✅ |
| iOS `detectIMCapabilities` custom→toggles | ✅ | | ✅ |
| per-IM runtime imkeys/selkey (pinyin·wb·hs·array·phonetic) | | ✅ | |
| endkey-path BPMF append logic | | ✅ | ✅ |
| `currentImKeys` refresh incl. `getPhoneticImKeys` selector | | | ✅ |
| array30 `w`+digit branch · W-H UI · iOS prefetch · iOS non-custom `detectIMCapabilities` | | | ✅ |

**Not JVM-unit-testable** (Android class load / SwiftUI / background `Thread`): the `getPhoneticImKeys`
selector, W-H visibility, prefetch. Each is compile/gate-verified and, where behaviour-bearing, its
downstream contract is unit-tested (e.g. et26 acceptance covers what the selector feeds).

### 11.9 Files changed (this completion pass)

Android: `LIMEService.java`, `SearchServer.java`, `limedb/LimeDB.java`, `ui/view/ImDetailFragment.java`,
`test/…/AcceptsIntoComposingTest.java`, `test/…/GetSelkeyTest.java`.
iOS: `Shared/Search/SearchServer.swift`, `LimeSettings/Views/IMDetailView.swift`,
`LimeTests/SearchServerTest.swift`. Docs: this file.

### 11.10 Gate log (2026-07-04)

- `:app:compileDebugJavaWithJavac --offline` → **BUILD SUCCESSFUL**
- `:app:testDebugUnitTest --offline --rerun-tasks` → **BUILD SUCCESSFUL** (incl. new eten26 test)
- `ios-gate.sh unit LimeTests/SearchServerTest` → **TEST SUCCEEDED**
- `ios-gate.sh unit LimeTests/KeyboardViewControllerTest` → **TEST SUCCEEDED**
- `ios-gate.sh build` → **BUILD SUCCEEDED**
- retirement grep → **EMPTY**
