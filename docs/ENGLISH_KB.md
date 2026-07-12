# English Keyboard — Behavior Reference & Improvement Plan

Comparison of LimeIME's English-mode behavior against AOSP **LatinIME** (the
open-source ancestor of Google's gboard; gboard itself is closed-source but
shares this core IME logic). Goal: identify high-value, low-risk improvements
for LimeIME §8.7 「英文鍵盤」.

Spec touchpoints: §8.7 (English Keyboard), §8.4 (IM Behaviour).

---

## 1. Auto-Capitalization

### LatinIME

- `RichInputConnection.getCursorCapsMode(inputType, SpacingAndPunctuations,
  hasSpaceBefore)` **does not** delegate to the host `InputConnection`. It
  walks its own committed-text cache through `CapsModeUtils.getCapsMode()`.
- Look-back is **unbounded** — it skips trailing whitespace and closing
  punctuation (quotes, parens) to find the last sentence terminator or
  paragraph boundary.
- Abbreviation FSM matches `(\w\.){2,}` so `Mr.`, `U.S.`, `e.g.` do **not**
  re-trigger sentence-cap.
- Locale flags in `SpacingAndPunctuations`:
  - `mUsesAmericanTypography` — skip trailing `"`, `'`, `)` before terminator
    check, so `She said "Hello." |` capitalizes correctly.
  - `mUsesGermanRules` — `digit.` is not a sentence terminator.

### LimeIME today

- **iOS** (`KeyboardViewController.updateShiftForAutoCap`, ~line 1395)
  - Gated on `mEnglishOnly`, `!isShiftOn`, `!mCapsLock`, pref `autoCap`,
    and host `textDocumentProxy.autocapitalizationType`.
  - Heuristic: `before.isEmpty || hasSuffix(". " / "! " / "? ")`.
  - **No** abbreviation guard — `Mr. |` will mis-capitalize.
  - **No** quote/paren skip — `"Hello." |` will not capitalize.
  - **No** paragraph/newline trigger — only `". "` family.
- **Android** (`LIMEService.java:1851`)
  - Delegates entirely to `InputConnection.getCursorCapsMode(inputType)`.
  - Behavior matches whatever the host editor reports — usually fine for
    `TYPE_TEXT_FLAG_CAP_SENTENCES`, but loses control over abbreviations,
    quotes, and the LimeIME-specific timing.

### Gap

| Behavior                        | LatinIME | LimeIME-iOS | LimeIME-Android |
| ------------------------------- | :------: | :---------: | :-------------: |
| Sentence end (`. ` / `! ` / `?`)|   ✓      |   ✓         |   ✓ (host)      |
| Start-of-document               |   ✓      |   ✓         |   ✓ (host)      |
| After newline / paragraph       |   ✓      |   ✗         |   depends       |
| Skip closing quotes/parens      |   ✓      |   ✗         |   ✗             |
| Abbreviation guard (`Mr.`)      |   ✓      |   ✗         |   ✗             |
| After `?!` not just `.`         |   ✓      |   ✓         |   ✓ (host)      |

### Recommendation

1. **iOS — extend the suffix check** (`KeyboardViewController.swift`):
   - Trigger when `before` ends with **`. ` / `! ` / `? ` / `\n` / `\n\n`**,
     or ends with one of those followed by `"`, `'`, `)`, `]`, `}`.
   - Add an abbreviation guard: if the char before `". "` is a single
     letter (e.g. `r. `) **and** the run before is `\w\.`-pattern repeating,
     suppress. Cheap version: skip auto-cap when `documentContextBeforeInput`
     ends with `[A-Za-z]\. ` and the preceding char (if any) is alpha (covers
     `Mr.`, `Dr.`, `e.g.`, `U.S.`).
2. **Android** — keep host `getCursorCapsMode()` as the baseline, but when
   `mAutoCap && caps == 0` and `mEnglishOnly`, run the same suffix/abbrev
   heuristic on `ic.getTextBeforeCursor(3, 0)` as a fallback. Many WebView
   editors return `0` for caps mode even on sentence-cap fields.

---

## 2. Smart Space / Smart Period

### LatinIME

`SpaceState` enum drives this:

| State              | Meaning                                                    |
| ------------------ | ---------------------------------------------------------- |
| `NONE`             | Normal                                                     |
| `DOUBLE`           | Just performed double-space → `". "`                       |
| `SWAP_PUNCTUATION` | Just swapped weak space with picked punctuation            |
| `WEAK`             | Auto-inserted space after a suggestion pick (swappable)    |
| `PHANTOM`          | Deferred space — emitted before next non-separator char    |

- **Double-space → `". "`** (`InputLogic.tryPerformDoubleSpacePeriod`):
  requires `<char><space>` before cursor; rewrites to `<char>. `.
- **Swap with punctuation** (`trySwapSwapperAndSpace`): if you pick a
  suggestion (weak space auto-inserted), then type `,`, the result is
  `word, ` not `word ,`.
- **Phantom space**: picking a suggestion does not literally insert a space;
  the IME marks `PHANTOM` so the next letter is preceded by a space, but
  a separator like `.` overrides it (no `word .`).

### LimeIME today

- **Neither platform** implements any of the four states. There is no
  double-space-to-period and no swap-with-punctuation.
- iOS does have an `isSelfUpdate` recursion guard but that is unrelated.

### Recommendation

Two low-risk wins on both platforms (English-mode only, behind `auto_cap`
or a new pref):

1. **Double-space → `". "`**
   - On `space` keypress in English mode, peek `documentContextBeforeInput`
     (iOS) / `getTextBeforeCursor(2, 0)` (Android). If it is
     `[A-Za-z0-9)\]"' ]<space>`, delete one char and insert `". "`.
   - Skip when the previous non-space char is already terminal punctuation
     (`.!?,:;`).
   - Compose well with the new auto-cap path above — `". "` then triggers
     sentence-cap on the next letter.
2. **Swap auto-space with punctuation** — see §2a below. This was previously
   deferred, but LimeIME now auto-appends a space on English suggestion pick,
   so the swap is the missing companion behavior.

---

## 2a. Auto-Space-On-Pick → Punctuation Swap (the missing piece)

> **Status: implemented** (replicates LatinIME, both platforms, always-on in
> English mode). See [design spec](superpowers/specs/2026-06-06-english-pick-space-punctuation-swap-design.md).
> - Android: `LIMEService.commitEnglishPunctuationWithSwap(...)` + `mPickedAutoSpace`
>   flag set at the suggestion-pick commit; swap invoked from the English-mode
>   character handler.
> - iOS: `KeyboardViewController.commitEnglishPunctuationWithSwap(...)` +
>   `pickedAutoSpace` flag set in `commitEnglishSuggestion`; swap invoked from
>   `handleEnglishCharacter`.

### Current LimeIME behavior

Both platforms commit a **literal trailing space** when the user picks an
English suggestion:

- iOS — `KeyboardViewController.swift:2454`: `textDocumentProxy.insertText(suffix + " ")`
- Android — `LIMEService.java:5431` / `5438`: `ic.commitText(word + " ", 1)`
  (emoji-pick path and normal-suffix path).

So after a pick the buffer is `word␣` with the cursor after the space. If the
user then types `,` `.` `?` etc., the result today is the wrong **`word ,`**
instead of **`word,`**.

### How LatinIME does it

LatinIME does **not** insert a literal space on pick — it sets a deferred
`PHANTOM` space flag (`InputLogic.onPickSuggestionManually` →
`mSpaceState = PHANTOM`). The space materializes only before the *next*
non-separator char. When the next char is sentence/closing punctuation
(`isUsuallyFollowedBySpace`), the phantom is **suppressed** (no space before the
punctuation) and stays pending so the space reappears before the following word.
The literal **swap** (`trySwapSwapperAndSpace`: delete the trailing space, then
commit `punct + " "`) is used only when a *real* space is already in the buffer
(the `WEAK` state, after the user pressed the physical spacebar to commit).

Character sets (AOSP `donottranslate-config-spacing-and-punctuations.xml`):

| Set | Chars (en_US) | Space rule |
| --- | --- | --- |
| `symbols_followed_by_space` | `. , ; : ! ? ) ] }` | space goes **after** → swap to `punct␣` |
| `symbols_preceded_by_space` | `( [ {` | space goes **before** → keep `word␣(` |
| other strip punct | `- / @ _ '` | strip the space, no space added → `word-` |

### Recommendation for LimeIME

Because LimeIME already commits a **literal** space on pick, the simplest
faithful port is LatinIME's literal **swap**, not the phantom-flag rewrite.

1. **Set a flag on pick.** Right after the `word + " "` commits (both pick sites
   above), set `mPickedAutoSpace = true` (Android) /
   `pickedAutoSpace = true` (iOS). Clear it on any input that is not the
   immediately-following punctuation keypress (next letter, cursor move,
   backspace, mode switch, new compose).

2. **On the next punctuation keypress, in English mode, while the flag is set,**
   look at the char before the cursor:
   - char before cursor is a space, **and** the typed punct ∈
     `{ . , ; : ! ? ) ] } }` → **swap**: delete one space, commit `punct + " "`.
     Result `word, ` (space now *after* the punctuation, ready for the next word).
   - typed punct ∈ `{ ( [ { }` → **leave** the existing `word␣`, commit the
     bracket → `word (`. (Opening brackets want a leading space.)
   - typed punct ∈ `{ - / @ _ ' }` → delete the trailing space, commit the punct
     bare, add no space → `word-`.
   - Then clear the flag.

3. **Compose with §2 double-space-period.** The swap only fires on punctuation,
   never on `space`, so it cannot collide with double-space→`". "`. After a swap
   to `". "`, the §1 auto-cap path then capitalizes the next letter — same chain
   LatinIME gets.

4. **Platform notes:**
   - iOS: "delete one space" = `textDocumentProxy.deleteBackward()` once;
     guard with `documentContextBeforeInput?.hasSuffix(" ") == true` so we never
     delete a non-space. Re-insert with `insertText(String(punct) + " ")`.
     Respect the existing `isSelfUpdate` recursion guard.
   - Android: `ic.getTextBeforeCursor(1,0)` to confirm the space, then
     `ic.deleteSurroundingText(1,0)` + `ic.commitText(punct + " ", 1)`. Wrap in
     `beginBatchEdit()` / `endBatchEdit()`. This lives in the punctuation branch
     of `onKey` / `pickSuggestionManually`'s sibling key path, English-mode only.

5. **Scope guard.** English mode only (`mEnglishOnly` / `mEnglishOnly` iOS).
   Do **not** touch Chinese/table-IM candidate commit or its end-key behavior.
   No swap when the previous non-space char is already terminal punctuation
   (avoid `word,,`).

### Edge cases to verify

- `word␣` + `,` → `word,␣` then type `Hi` → `word, Hi`.
- `word␣` + `.` → `word.␣` then auto-cap makes next letter uppercase.
- `word␣` + `(` → `word (` (no swap; bracket keeps its leading space).
- `word␣` + `-` → `word-` (space removed, none added).
- pick, then move cursor, then `,` → flag cleared, no swap (normal `,`).
- pick, then backspace (removes the auto-space), then `,` → no stray delete.
- non-English IM candidate pick → flag never set, behavior unchanged.

---

## 3. Shift State Machine

### LatinIME

`KeyboardState` has states `UNSHIFT`, `MANUAL_SHIFT`, `AUTOMATIC_SHIFT`,
`SHIFT_LOCK_SHIFTED`. Auto-shift is applied at **release**, not press.
Double-tap timeout `isInDoubleTapShiftKeyTimeout()` toggles caps-lock.
Chording (hold shift + type letter) keeps shift through the letter, releases
after.

### LimeIME today

iOS uses `ShiftResetPolicy.shouldResetAfterCharacter` /
`shouldResetAfterShiftRelease` — already implements chording vs. tap.
`KeyboardViewController.swift:1312-1325` covers single-tap, double-tap-lock,
and tap-to-lock-off. **This is on par with LatinIME.** No change needed.

---

## 4. English Prediction Pipeline

### LatinIME

- `DictionaryFacilitatorImpl` loads `main` (binary LM), `user_history`,
  `user` (system Personal Dictionary), `contacts`.
- `Suggest.getSuggestedWords()` ranks via `NgramContext` (bigram/trigram).
- Auto-commit on separator gated by `mAutoCorrectionEnabledPerUserSettings`
  AND `InputAttributes.mInputTypeNoAutoCorrect`.
- Capitalized-word learning capped at
  `CAPITALIZED_FORM_MAX_PROBABILITY_FOR_INSERT = 140` so accidental
  `Hello`-after-`. ` doesn't displace `hello` in user history.

### LimeIME today

- iOS surfaces English candidates via `updateEnglishPrediction()` from a
  shipped English dictionary — no n-gram, no user-history learning, no
  contacts integration.
- Android surfaces English candidates through the existing IM candidate
  pipeline when `english_dictionary_enable` is on; same limitations.
- Neither platform auto-commits on space — the user must tap a candidate.

### Recommendation

Out of scope for a §8.7 polish pass — these are multi-week changes
(binary LM, history DB, ngram lookups). Park for a future "English IME v2"
proposal. The immediate auto-cap + double-space-period improvements deliver
80% of the perceived gboard parity at <1% of the cost.

### #6 vs. the scored dictionary — scope boundary

`wordfreq` (a candidate data source for the Android scored dictionary — see
[ENG_AUTO_COMPLETION.md](ENG_AUTO_COMPLETION.md) "Scored Dictionary (Android)")
is **not** an alternative to #6 — it is one ingredient of it. wordfreq is a
static `word → frequency` list (the `basescore` column); #6 is the full
prediction *system*. The four parts of #6 and where each comes from:

| #6 component | Provided by | Status |
| --- | --- | --- |
| 6.1 Main-dictionary frequency (base ranking) | **`basescore` from bundled payload** | Android scored dictionary — planned |
| 6.2 User-history learning (personal, decaying) | **`score` column + learning logic** | Android scored dictionary — planned |
| 6.3 N-gram context (bigram/trigram, "Happy→Birthday") | bigram table + prev-word lookup | **future — not planned** |
| 6.4 Auto-commit on separator (auto-correct top word on space/`.`) | input-logic change (LatinIME `mAutoCorrection` path) | **future — not planned** |

So the Android scored-dictionary work (Android-only, **no `lime.db` version
bump** — the dictionary is self-versioned via `DICTIONARY_DATA_VERSION`) already
delivers **6.1 and 6.2** (static frequency plus user-learned score, manual-tap
prefix completion). What remains genuinely "English IME v2 / weeks of work" is
**6.3 n-gram context** and **6.4 auto-commit-on-separator**. wordfreq feeds 6.1;
it does not replace #6.

Note: iOS is excluded — it keeps `UITextChecker` (see
[ENG_AUTO_COMPLETION.md](ENG_AUTO_COMPLETION.md) scope decision), so 6.1/6.2 land
on Android only.

One-line summary: **wordfreq = the frequency *data* (one column); #6 = the full
prediction *system* (frequency + personal learning + next-word context +
auto-correct).**

---

## 5. Input-Type Awareness

### LatinIME (`InputAttributes.java`)

- **Suggestions off** for: passwords, `isEmailVariation()`,
  `TYPE_TEXT_VARIATION_URI`, `TYPE_TEXT_VARIATION_FILTER`,
  `TYPE_TEXT_FLAG_NO_SUGGESTIONS`, `TYPE_TEXT_FLAG_AUTO_COMPLETE`.
- **Auto-correct off** for: `TYPE_TEXT_VARIATION_WEB_EDIT_TEXT` (unless
  explicit auto-correct flag), `TYPE_TEXT_FLAG_NO_SUGGESTIONS`, any
  single-line field without `TYPE_TEXT_FLAG_AUTO_CORRECT`.

### LimeIME today

- Android (`LIMEService.java:895-945`) already detects
  `EMAIL_ADDRESS`, `URI`, `WEB_EMAIL_ADDRESS` and forces the English
  layout for those fields (see project memory: "URL fields stay
  English-only #74").
- iOS gates `lime_url_*` / `lime_email_*` layouts via `keyboardType` —
  also force-English.
- Neither platform suppresses suggestions in password fields beyond the
  layout switch. That is a defect for any future English-prediction work
  but **not** for the current candidate-list behavior (no auto-correct
  exists today, so nothing to suppress).

### Recommendation

When (and only when) English prediction grows beyond the current
read-only dictionary, mirror LatinIME's `InputAttributes` gate to
disable the English suggestion strip for password / NO_SUGGESTIONS
fields. Until then, no action.

---

## 6. English Layout Source Of Truth

### Finding

`lime.db` keeps legacy `keyboard.engkb` and `keyboard.engshiftkb` columns, but
LimeIME does not expose a user-facing setting for choosing a separate English
keyboard layout. Runtime English-mode layout selection should therefore stay
preference-driven, not DB-row-driven:

- `number_row_in_english = true` → `lime_english_number` /
  `lime_english_number_shift`.
- `number_row_in_english = false` → `lime_english` /
  `lime_english_shift`.

iOS already follows this rule in practice. `KeyboardConfig` reads `engkb` /
`engshiftkb`, but `KeyboardViewController` chooses the English layout from
`numberRowInEnglish` when entering English mode. Android follows the same rule
through `LIMEKeyboardSwitcher.resolveEnglishLayoutId(...)`, with no IM-specific
English layout exceptions.

### Decision

Do **not** add iOS runtime support for resolving English mode through
`keyboard.engkb` / `keyboard.engshiftkb`. Without a settings UI, honoring those
columns would create hidden behavior that users cannot inspect or change.

Use the `keyboard` table this way:

- `imkb` / `imshiftkb`: authoritative for Chinese IM layout selection.
- `symbolkb` / `symbolshiftkb`: optional source for IM-specific symbol pages.
- `engkb` / `engshiftkb`: legacy compatibility fields only; do not treat as
  the English layout source of truth.

### Plan TODO

- [x] Add code comments on iOS English layout selection explaining why it
      intentionally ignores `KeyboardConfig.engkb` / `engshiftkb`.
- [x] Add equivalent Android comments around `Keyboard.getEngkb(showNumberRow)`
      and the English switcher path.
- [x] Remove the Android `wb` English-layout special case so `wb` uses the same
      `number_row_in_english`-controlled English layout family as every other IM.
- [ ] During DB 104 `cj4` keyboard-row work, keep legacy `engkb` /
      `engshiftkb` values populated because those columns already exist, but
      do not add schema changes for English layout behavior and do not rely on
      those fields at runtime.

---

## 7. Prioritized Backlog

Ordered by user-visible impact ÷ engineering cost:

| # | Item                                                            | Platform | Est. effort | Spec note |
| - | --------------------------------------------------------------- | -------- | ----------- | --------- |
| 0 | ✅ Swap auto-space w/ punct after pick (no `word ,`, see §2a)    | both     | done        | §2a       |
| 1 | Auto-cap after `\n` / paragraph boundary                        | iOS      | ~5 LOC      | §8.7      |
| 2 | Auto-cap skip-trailing-quote/paren (`"Hello." `)                | iOS      | ~10 LOC     | §8.7      |
| 3 | Abbreviation guard (`Mr.`, `e.g.`)                              | both     | ~20 LOC     | §8.7      |
| 4 | Double-space → `". "` (English-only, behind `auto_cap` pref)    | both     | ~30 LOC ea  | §8.7      |
| 5 | Android: fallback heuristic when host returns `caps == 0`       | Android  | ~15 LOC     | §8.7      |
| 6 | English n-gram / user-history dictionary                        | both     | weeks       | future    |
| 7 | Disable English suggestions in password / NO_SUGGESTIONS fields | both     | small       | after #6  |

Item 0 was a **correctness fix** (the old output `word ,` was wrong) and is now
**implemented** on both platforms (see §2a). Items 1–5 remain.

Items 1–5 are independently shippable and share the existing `auto_cap`
preference — no new UI required. Recommend bundling 1–3 in one PR for iOS,
5 alone for Android, then 4 as a second PR per platform.

---

## 8. Source References

AOSP LatinIME (mirrors: `aosp-mirror/platform_packages_inputmethods_LatinIME`):

- `java/src/com/android/inputmethod/latin/RichInputConnection.java`
- `java/src/com/android/inputmethod/latin/utils/CapsModeUtils.java`
- `java/src/com/android/inputmethod/latin/settings/SpacingAndPunctuations.java`
- `java/src/com/android/inputmethod/latin/inputlogic/InputLogic.java`
- `java/src/com/android/inputmethod/latin/inputlogic/SpaceState.java`
- `java/src/com/android/inputmethod/keyboard/internal/KeyboardState.java`
- `java/src/com/android/inputmethod/latin/DictionaryFacilitatorImpl.java`
- `java/src/com/android/inputmethod/latin/InputAttributes.java`

LimeIME:

- `LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift` —
  `updateShiftForAutoCap` (~1395), `handleEnglishCharacter` (~1231),
  `handleEnterOrSpace` (~1091).
- `LimeStudio/app/src/main/java/org/limeime/LIMEService.java` —
  `mAutoCap` (156), `loadSettings()` (~999), runtime gate (~1851),
  input-type detection (~895-945).
- `LimeStudio/app/src/main/res/xml/preference.xml` — `auto_cap` toggle.
- `LimeIME-iOS/LimeSettings/Views/PreferencesTabView.swift` — §8.7 UI.
