# Auto Chinese Punctuation Candidate — Specification & Tests

## 1. Overview

When the **auto Chinese punctuation** preference (`auto_chinese_symbol`) is ON, the
keyboard shows a strip of Chinese punctuation candidates (`，。、？！…`) in the
candidate bar **whenever the bar would otherwise be empty** in Chinese-input mode —
i.e. there is no active composition and no real candidate / related-phrase list to
show. The user can put the strip away with the candidate-bar **dismiss (✕)** button.

This lets the user insert full-width Chinese punctuation with one tap right after
committing a word, without switching to a symbol keyboard.

> This is a **different feature** from `docs/CHI_PERIOD_COMMA_INS.md`, which is about
> ordering an inline `，`/`。` candidate **during composition** relative to emoji. This
> document is only about the post-commit / empty-bar punctuation **strip**.

Primary source files:

| Platform | Files |
|---|---|
| iOS | `LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift`, `LimeIME-iOS/Shared/Preferences/LIMEPreferenceManager.swift`, `LimeIME-iOS/LimeKeyboard/CandidateBarView.swift` |
| Android | `LimeStudio/app/src/main/java/net/toload/main/hd/LIMEService.java`, `…/global/LIMEPreferenceManager.java`, `…/data/ChineseSymbol.java`, `…/candidate/CandidateInInputViewContainer.java` |

---

## 2. Preference

| | Key | Type | Default | Read by |
|---|---|---|---|---|
| iOS | `auto_chinese_symbol` | Bool | **OFF** | `LIMEPreferenceManager.autoChineseSymbol`; `KeyboardViewController.autoChineseSymbol` (`= d?.bool(forKey:) ?? false`) |
| Android | `auto_chinese_symbol` | Bool | **OFF** | `LIMEPreferenceManager.getAutoChineseSymbol()`; read on demand in `LIMEService.clearSuggestions()` |

**Default-value parity action item.** iOS effectively defaults OFF (`bool(forKey:)`
returns `false` for a missing key). Android's `preference.xml` / `xml-v17/preference.xml`
declare `android:defaultValue="false"`, **but** `getAutoChineseSymbol()` uses a fallback
of `true` (`sp.getBoolean("auto_chinese_symbol", true)`). After `setDefaultValues` runs
the stored value is `false`, so in practice both default OFF — but the Android getter
fallback should be changed to `false` so the two platforms agree even before defaults
are materialised.

The feature is **Chinese-mode only**: every show path is gated on `!mEnglishOnly` on
both platforms. In English-prediction mode the strip never appears.

---

## 3. Punctuation symbol set

The strip content is a fixed list (no DB lookup).

**Canonical set (37 symbols, this exact order):**

```text
，。、？！：；（）「」『』【】／＼－＿＊＆︿％＄＃＠～｛｝［］＜＞＋｜‵＂
```

- **Android** — `ChineseSymbol.chineseSymbols` → `getChineseSymoblList()` is the **source of
  truth** for this set.
- **iOS** — `KeyboardViewController.chinesePunctuationMappings()` mirrors it byte-for-byte
  (same symbols, same order).

> **Resolved:** the Android set was chosen as canonical; iOS was changed to match (it
> previously showed a different 30-symbol punctuation/quote set — `〔〕《》〈〉…——·※ “”‘’`
> — which has been replaced). Locked on both sides by the **T-SET** parity tests (§10.4):
> the Android test asserts `getChineseSymoblList()` returns this exact ordered list, and
> the iOS test asserts `chinesePunctuationMappings()` emits the same symbols in order and
> no longer contains the old iOS-only symbols.

---

## 4. Behaviour specification

All rows below assume **Chinese input mode** (`!mEnglishOnly`).

### 4.1 When the strip SHOWS (pref ON)

The strip appears whenever the bar transitions to "no active composition and no real
candidate / related list", through any of:

| Case | Trigger |
|---|---|
| **(a)** | Backspace deletes the **last** composing character → composition becomes empty. |
| **(b)** | A word is committed (by selecting a candidate) and **no related phrases follow** — either related is OFF or the committed word has none. |
| **(c)** | A related-phrase strip is dismissed with the candidate-bar **dismiss (✕)** button. |
| **(g)** | The **dismiss (✕)** button is pressed during active composition — the composing text is deleted first, then the strip surfaces (iOS aligned to Android). |

### 4.2 When the strip is DISMISSED / not shown

| Case | Trigger | Result |
|---|---|---|
| **(d)** | Dismiss (✕) button pressed **while the punctuation strip is showing**. | Strip hidden and **stays hidden** until the next candidate cycle. |
| **(e)** | Backspace pressed **while the punctuation strip is showing**. | Strip hidden, **no character deleted** (intentional cancel gesture). |
| **(f)** | Backspace pressed **while a related-phrase strip is showing** (#78). | Related strip dismissed **and one character deleted** in a single tap — strip does **not** reappear as punctuation. |

### 4.3 When the pref is OFF

The strip never appears. Every case (a)–(f) behaves as a plain clear/delete: the bar
empties (or hides) and Backspace deletes normally. No punctuation strip in any path.

### 4.4 Mode / gating invariants

- Strip is shown only when `autoChineseSymbol/getAutoChineseSymbol()` is ON **and**
  `!mEnglishOnly`.
- The strip is a **browse-only** list: Space/Enter insert a literal space/newline
  instead of committing the first entry, and there is no default-highlighted entry.
- Selecting a strip entry commits that punctuation (and does not re-open the strip on
  the same tap).

---

## 5. State model

Both platforms drive the strip from the same flags.

| Flag | Meaning |
|---|---|
| `autoChineseSymbol` / `getAutoChineseSymbol()` | The preference. |
| `hasCandidatesShown` | The bar currently has (or just had) candidates — the "we are transitioning from a populated bar" signal that gates the strip. |
| `hasChineseSymbolCandidatesShown` | The **punctuation strip itself** is the current bar content. |
| `isShowingRelatedPhrases` (iOS) | A related-phrase browse list is the current content. (Android infers this from `hasCandidatesShown && !hasChineseSymbolCandidatesShown` with empty composing.) |
| `mComposing` | Active composing buffer; empty ⇒ no live composition. |

**The strip is built in exactly one function per platform**, and every show path must
route through it:

- iOS — `clearSuggestions()`, branch gated by
  `autoChineseSymbol && !mEnglishOnly && hasCandidatesShown && !hasChineseSymbolCandidatesShown`.
- Android — `clearSuggestions()` → `if (hasCandidatesShown) updateChineseSymbol()`,
  under `!mEnglishOnly && getAutoChineseSymbol()`.

---

## 6. Code paths

### 6.1 iOS (`KeyboardViewController.swift`)

| Path | Function | Notes |
|---|---|---|
| Strip builder | `clearSuggestions()` — `autoChineseSymbol` branch | Only place the strip is produced. |
| Case (a) | `handleBackspace()` → `mComposing.count == 1` → `clearComposing(force:true)` → `clearSuggestions()` | `hasCandidatesShown` still true ⇒ strip. |
| Case (b) | `pickCandidateManually` → `commitTyped()` → `updateRelatedPhrase()`; empty-related branch sets `hasCandidatesShown = true` then `clearSuggestions()` | **Fix:** the commit path (`finishComposing` + the `mComposing.isEmpty` block) had reset `hasCandidatesShown` to false; it is restored at the empty-related decision point so the strip can surface. |
| Case (c) + (g) | `candidateBarViewDidRequestDismiss()` → (strip not showing) `clearComposing(force: true)` → `clearSuggestions()` | **Fix:** deletes any inline composing text and keeps `hasCandidatesShown`, so the strip surfaces for both a related-phrase dismiss (c, empty composing) and a dismiss during active composing (g). Mirrors Android `clearComposing(true)`. |
| Case (d) | `candidateBarViewDidRequestDismiss()` → `if hasChineseSymbolCandidatesShown { cancelComposing() }` | **Fix:** strip showing ⇒ `cancelComposing()` empties via direct `setCandidates([])`, bypassing `clearSuggestions()` so it cannot re-surface. |
| Case (e) | `handleBackspace()` Case 4 (`hasChineseSymbolCandidatesShown`) | Hides strip, no delete. |
| Case (f) | `handleBackspace()` → `isBrowseOnlySuggestionList` → `dismissBrowseOnlySuggestionBar()` + `deleteBackward()` | Clears related bar, deletes a char, no strip. |

### 6.2 Android (`LIMEService.java`)

| Path | Function | Notes |
|---|---|---|
| Strip builder | `updateChineseSymbol()` (called from `clearSuggestions()` and from the explicit Ctrl-`/` shortcut) | `clearSuggestions()` re-show is the gated path; the Ctrl-`/` caller is an explicit user request and is not gated. |
| Case (a) | `handleBackspace()` → `length == 1` → `clearComposing(true)` → `clearSuggestions()` | `hasCandidatesShown` still true ⇒ strip. |
| Case (b) | commit → `updateRelatedPhrase()`; empty-related branch → `clearSuggestions()` | `updateRelatedPhrase()` resets only `hasChineseSymbolCandidatesShown`, **not** `hasCandidatesShown`, so the strip surfaces. (This is the reference behaviour iOS case (b) was fixed to match.) |
| Case (c) | `dismissCandidateComposing()` → `clearComposing(true)` → `clearSuggestions()` | Related strip has `hasChineseSymbolCandidatesShown == false`, so the re-show branch runs and surfaces the strip. |
| Case (d) | `dismissCandidateComposing()` → `if (hasChineseSymbolCandidatesShown) hideCandidateView()` | **Fix:** previously `clearComposing(true)` → `clearSuggestions()` rebuilt the strip on the same tap (it was impossible to dismiss). `hideCandidateView()` resets both flags so it stays gone. |
| Case (e) | `handleBackspace()` → strip showing → falls to `hideCandidateView()` branch | Hides strip, no delete. |
| Case (f) | `handleBackspace()` → `!mEnglishOnly && hasCandidatesShown && !hasChineseSymbolCandidatesShown` → pre-clears `hasCandidatesShown`, `clearComposing(false)`, `keyDownUp(DEL)` | Clears related bar, deletes a char, no strip. |

---

## 7. Cross-platform parity matrix

| # | Trigger | iOS (ON) | Android (ON) | OFF (both) | Aligned |
|---|---|---|---|---|---|
| a | Backspace clears composition → empty | strip shows | strip shows | bar clears | ✅ |
| b | Commit word, related OFF/none | strip shows | strip shows | bar clears | ✅ |
| c | Dismiss (✕) on related strip | strip shows | strip shows | bar clears | ✅ |
| d | Dismiss (✕) on punctuation strip | hidden, stays gone | hidden, stays gone | n/a | ✅ |
| e | Backspace on punctuation strip | hidden, no delete | hidden, no delete | n/a | ✅ |
| f | Backspace on related strip (#78) | dismiss + delete char, no strip | dismiss + delete char, no strip | dismiss + delete | ✅ |
| g | Dismiss (✕) during active composing | strip shows | strip shows | clears | ✅ |

### 7.1 Case (g) — resolved

The dismiss (✕) button is visible during active composition on both platforms. Pressing
it deletes the composing text and then **shows the strip** (pref ON) on both. This is the
same "composition cleared → strip" rule as cases (a)/(c); the only dismiss that
*suppresses* the strip is case (d) (the strip is already showing).

**Decision:** iOS aligned to Android. iOS `candidateBarViewDidRequestDismiss` now routes
the non-strip dismiss through `clearComposing(force: true)` → `clearSuggestions()` (deletes
any inline composing text, keeps `hasCandidatesShown`), mirroring Android's
`dismissCandidateComposing` → `clearComposing(true)`. Case (d) still routes through
`cancelComposing()` so the strip is put away without rebuilding.

---

## 8. Existing tests

### 8.1 iOS (`LimeIME-iOS/LimeTests`)

| Test | Covers | Kind |
|---|---|---|
| `LIMEPreferenceManagerTest.testDefaultAutoChineseSymbol` | Default pref is OFF | behaviour |
| `KeyboardViewControllerTest.testCandidateBarDismissSurfacesPunctuationStrip` (+ T-iOS-2/3/4, T-MODE, T-BROWSE, T-SET — see §11) | Unified dismiss (c/d/g), commit-restore, gate, backspace, browse-only, canonical set | source-pattern |
| `KeyboardViewControllerTest.testDefaultHighlightedCandidate*` | Highlight of an exact-match punctuation candidate after the composing echo | logic |
| `LimeDBTest` (`isChinesePunctuationRecord`) | The record-type flag on `，`/`。` | logic |
| `SearchServerTest.test_3_6_4_*` | Emoji-vs-punctuation **ordering during composing** (the *other* feature) | logic |

### 8.2 Android (`LimeStudio/app/src/androidTest/.../LIMEServiceTest.java`)

| Test | Covers | Kind |
|---|---|---|
| `dismissCandidateComposingCancelsInputConnectionComposingText` | Dismiss during **active composing** clears composition + finishes IC text | behaviour (Mockito) |
| `updateChineseSymbol*` coverage tests | `updateChineseSymbol()` sets the flag and calls `setSuggestions()` | coverage (reflection) |
| `handleBackspace` … `hasChineseSymbolCandidatesShown` true/false branches | Backspace branch selection | coverage (reflection) |

---

## 9. Coverage gaps

Neither platform currently has an **end-to-end behaviour test for the spec cases**
(a)–(g) with the pref ON *and* OFF. Specifically missing:

1. **(a)** backspace-to-empty shows the strip (ON) / clears (OFF).
2. **(b)** commit + related OFF shows the strip (ON) / clears (OFF) — the regression that
   was just fixed on iOS.
3. **(c)** dismiss-button on a related strip shows the strip (ON) — the iOS fix.
4. **(d)** dismiss-button on the punctuation strip hides it **and does not rebuild it** —
   the Android fix. No test currently locks this.
5. **(e)** backspace on the strip hides it without deleting.
6. **(f)** backspace on a related strip deletes a char and does **not** show the strip.
7. **(g)** the dismiss-while-composing divergence (once a decision is made).
8. **Symbol-set parity** — no test asserts iOS and Android emit the same ordered set
   (they currently do not; see §3).
9. **Default-value parity** — no test asserts the Android getter returns `false` by
   default (it currently falls back to `true`).

> **Status — addressed.** Gaps 1–6 and the symbol-set / default-value items now have the
> tests listed in §11 (Android `autoChinesePunc_T_A_*` + T-SET; iOS T-iOS-1…4, T-MODE,
> T-BROWSE, T-SET). Still open: **T-A-b** (deferred — needs a live `SearchServer`/DB
> harness; covered in spirit by T-A-a) and the §10.2 pure-policy refactor that would give
> iOS behaviour-level (not source-pattern) coverage of the full matrix.

The old iOS `testCandidateBarDismissRoutesThroughForcedComposingClear` was replaced by
`testCandidateBarDismissSurfacesPunctuationStrip` (T-iOS-1), which locks the unified
dismiss behaviour (case d → hide via `cancelComposing()`; cases c + g →
`clearComposing(force: true)` → `clearSuggestions()`).

---

## 10. Test plan for full conformance

### 10.1 Android — behaviour tests (feasible now, mirror `dismissCandidateComposing…`)

Use the existing reflection + Mockito harness: inject a mock `CandidateView`, set the
state fields, invoke the method, and verify `setSuggestions(...)` / `hideCandidateView()`
/ `updateChineseSymbol()` interactions.

| ID | Case | Setup | Assert |
|---|---|---|---|
| T-A-a | (a) ON | pref ON, `hasCandidatesShown=true`, `mComposing=""` → `clearSuggestions()` | `updateChineseSymbol()` runs ⇒ `setSuggestions(punctuationList,…)`; `hasChineseSymbolCandidatesShown=true` |
| T-A-aOff | (a) OFF | pref OFF, same | `hideCandidateView()`; **no** punctuation `setSuggestions` |
| T-A-b | (b) | pref ON, related OFF, commit a normal word → `updateRelatedPhrase()` empty branch | strip shown (`setSuggestions` punctuation) |
| T-A-c | (c) | pref ON, related strip state (`hasCandidatesShown=true`, `hasChineseSymbolCandidatesShown=false`, `mComposing=""`) → `dismissCandidateComposing()` | strip shown |
| T-A-d | (d) | pref ON, `hasChineseSymbolCandidatesShown=true` → `dismissCandidateComposing()` | `hideCandidateView()` called; **no** subsequent `updateChineseSymbol()`/`setSuggestions` (locks the dismiss fix) |
| T-A-e | (e) | `hasChineseSymbolCandidatesShown=true`, `mComposing=""` → `handleBackspace()` | `hideCandidateView()`; **no** `keyDownUp(KEYCODE_DEL)` |
| T-A-f | (f) | `hasCandidatesShown=true`, `hasChineseSymbolCandidatesShown=false`, `mComposing=""` → `handleBackspace()` | `keyDownUp(KEYCODE_DEL)` called; **no** punctuation `setSuggestions` |
| T-A-default | §2 | fresh `SharedPreferences` after `setDefaultValues` | `getAutoChineseSymbol()` == `false` |

### 10.2 iOS — recommended: extract a pure decision policy

`KeyboardViewController` is a `UIInputViewController` and is not unit-testable for this
flow, which is why the current iOS tests are source-pattern string matches. To get real
**behaviour** conformance, extract the show/dismiss decision into a pure policy struct
(the same pattern already used by `CandidateSelectionPolicy` and `LimeEndkeyPolicy`):

```
enum PunctuationStripAction { case show, hide, clear }

struct ChinesePunctuationPolicy {
    static func action(
        event: CandidateBarEvent,      // .backspace, .dismissButton, .commitNoRelated
        prefOn: Bool, englishOnly: Bool,
        hasCandidatesShown: Bool,
        showingPunctuation: Bool,
        showingRelated: Bool,
        composingEmpty: Bool
    ) -> PunctuationStripAction
}
```

Then `KeyboardViewController` calls the policy, and a `ChinesePunctuationPolicyTest`
asserts every cell of the §7 matrix (cases a–g × pref on/off). Mirror the same truth
table in an Android `ChinesePunctuationPolicy` so both platforms are provably identical.
This is the durable path to "fully conform".

### 10.3 iOS — interim: strengthen the source-pattern locks

Until the policy refactor lands, add/strengthen source-pattern tests:

| ID | Locks |
|---|---|
| T-iOS-1 | `candidateBarViewDidRequestDismiss` contains the `mComposing.isEmpty && isShowingRelatedPhrases { clearSuggestions() }` branch (case c) **and** still falls through to `cancelActiveComposingFromCandidateDismiss()` (case d). Replaces the incomplete existing test. |
| T-iOS-2 | `updateRelatedPhrase` empty-related branch sets `hasCandidatesShown = true` before `clearSuggestions()` (case b). |
| T-iOS-3 | `clearSuggestions()` contains the `autoChineseSymbol && !mEnglishOnly && hasCandidatesShown && !hasChineseSymbolCandidatesShown` gate (the single builder). |
| T-iOS-4 | `handleBackspace` Case 4 hides on `hasChineseSymbolCandidatesShown` without `deleteBackward`; browse-only path uses `dismissBrowseOnlySuggestionBar()` (cases e/f). |

### 10.4 Both — shared invariants

| ID | Asserts |
|---|---|
| T-SET | iOS `chinesePunctuationMappings()` and Android `getChineseSymoblList()` produce the **same ordered symbol set** (after the §3 canonical set is decided). Fails today by design until §3 is resolved. |
| T-MODE | Strip never appears when `mEnglishOnly` is true (English-prediction mode), pref ON. |
| T-BROWSE | Space/Enter on the strip insert a literal space/newline (browse-only), no commit. |

---

## 11. Status checklist

- [x] (a) backspace-to-empty shows strip — both platforms.
- [x] (b) commit + related OFF shows strip — Android already; **iOS fixed**.
- [x] (c) dismiss related → strip — Android already; **iOS fixed**.
- [x] (d) dismiss strip → hidden, no rebuild — iOS already; **Android fixed**.
- [x] (e) backspace on strip → hidden, no delete — both.
- [x] (f) backspace on related → delete + no strip (#78) — both.
- [x] (g) dismiss during composing → strip — Android already; **iOS fixed** (aligned to Android, §7.1).
- [x] Symbol set unified across platforms — Android set chosen as canonical; iOS
  `chinesePunctuationMappings()` changed to emit it in order; locked by **T-SET** on both
  sides (§3).
- [x] Android getter default changed to `false` (§2) — `getAutoChineseSymbol()` fallback is
  now `false`; locked by `autoChinesePunc_T_A_default_getterDefaultsFalse`.
- [x] Behaviour tests added (§10): Android `autoChinesePunc_T_A_*` (a / aOff / c / d / e /
  f / default) + `…_T_SET_…`; iOS `testCandidateBarDismissSurfacesPunctuationStrip`
  (T-iOS-1) + T-iOS-2/3/4 + T-MODE + T-BROWSE + `…MatchesCanonicalAndroidSet` (T-SET).
  - Deferred: **T-A-b** (commit + empty-related → strip) needs a live `SearchServer`/DB
    harness; its core re-show invariant is already covered by **T-A-a** (same
    `clearSuggestions()` → `updateChineseSymbol()` builder). The pure-policy refactor
    (§10.2) remains the path to true behaviour-level iOS coverage of the full matrix.
