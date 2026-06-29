# Keyboard type / input-field handling — Android vs iOS

Context: GitHub issue [#74](https://github.com/lime-ime/limeime/issues/74) asks
LIME to respect field-specific input types: phone / number / date-time fields
should open a restricted numeric layout with no `中` / `abc` / `EN` mode-switch
keys, and persisted Chinese/English mode should not leak into those restricted
fields. URL/address-bar/search fields are treated like normal text because
address bars are often used for Chinese keyword search. With persisted
language mode on they restore the remembered Chinese/English state; with it
off they start in Chinese mode like normal text.

This doc tracks how each field type is currently handled on Android, and what
remains to be done on iOS and Android to satisfy #74.

## How each platform detects the field

- Android — `LIMEService.onStartInput(EditorInfo attribute)` switches on
  `attribute.inputType & EditorInfo.TYPE_MASK_CLASS` (number / phone / datetime
  / text) and then on `TYPE_MASK_VARIATION` for text variations
  ([LIMEService.java:862](../LimeStudio/app/src/main/java/net/toload/main/hd/LIMEService.java#L862)).
- iOS — `KeyboardViewController.initOnStartInput()` switches on
  `textDocumentProxy.keyboardType` (`UIKeyboardType`)
  ([KeyboardViewController.swift:348](../LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift#L348)).
  iOS has no equivalent of `TYPE_CLASS_DATETIME`; the system uses
  `UIDatePicker` for date/time, so the extension is rarely invoked there.

## Field-by-field status

Notation in the cells below:

- "pref `numberRow`" = Android `mLIMEPref.getShowNumberRowInEnglish()` / iOS `number_row_in_english` (default **true**). Controls whether the English layout family adds a top number row.
- **iOS pref-toggle is hidden on iPad.** The `Toggle` in [PreferencesTabView.swift:96-98](../LimeIME-iOS/LimeSettings/Views/PreferencesTabView.swift#L96-L98) is gated by `if UIDevice.current.userInterfaceIdiom != .pad`. iPad users cannot change `number_row_in_english`; it stays at its default `true`. Practical consequence: every "iPhone vs iPad" row in this table simplifies to *iPad always loads the `_number` variant* — `lime_english_ipad.json` is effectively dead on iPad unless a cross-device shared-defaults write flips the pref (edge case).
- "iPad variant" = `LayoutLoader` automatically appends `_ipad` / `_ipad_shift` when `hostIsPad` is true; falls back to the iPhone JSON if no variant exists ([LayoutLoader.swift:75-100](../LimeIME-iOS/LimeKeyboard/LayoutLoader.swift#L75-L100)).

| Field type (Android `EditorInfo` / iOS `UIKeyboardType`) | Current Android behaviour — actual XML loaded | Current iOS behaviour — actual JSON loaded | iOS gap (vs #74) | Android gap (vs #74) |
| --- | --- | --- | --- | --- |
| Phone — `TYPE_CLASS_PHONE` / `.phonePad` | `mEnglishOnly = true`, `MODE_PHONE` ([LIMEService.java:868-871](../LimeStudio/app/src/main/java/net/toload/main/hd/LIMEService.java#L868-L871)). Switcher path: `setKeyboardMode(MODE_PHONE, isSymbol=false)` → `getKeyboardXMLID("phone_number")` → **`phone_number.xml`** ([LIMEKeyboardSwitcher.java:485-488](../LimeStudio/app/src/main/java/net/toload/main/hd/LIMEKeyboardSwitcher.java#L485-L488)). Restricted T9-style layout, no mode key. | Observed iOS behaviour: `.phonePad` is handled by the system's own phone/symbol keyboard, so LIME is normally not invoked and no LIME JSON is loaded. The Swift fallback branch still sets `mEnglishOnly = true`, `mPredictionOn = false`, and names `phone_number`, but that is not the normal visible route. | None for the observed system-keyboard path. | None. Matches #74. |
| Number — `TYPE_CLASS_NUMBER` / `.numberPad`, `.decimalPad`, `.asciiCapableNumberPad` | `mEnglishOnly = true`, `MODE_PHONE` with `isSymbol = false`. Switcher path: `setKeyboardMode(MODE_PHONE, isSymbol=false)` → `getKeyboardXMLID("phone_number")` → **`phone_number.xml`**. This intentionally reuses the restricted phone-number layout for integer and decimal fields, so no `EN` / `中` mode keys are exposed. | All three `UIKeyboardType`s → `mEnglishOnly = true`, `mPredictionOn = true`. Layout = **`symbols1`** ([KeyboardViewController.swift:387-396](../LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift#L387-L396)). **iPhone loads:** `symbols1.json`. **iPad loads:** `symbols1_ipad.json` (via `LayoutLoader`'s `_ipad` substitution). `lime_number.json` / `lime_number_ipad.json` remain unused (they're the Chinese-IM family's numbers layer, with an `EN` key — see Layout assets section below). | iOS still uses `symbols1` for number/decimal. A stricter mode-key-free iOS numeric layout remains a possible future improvement, but is out of scope for the current Android-only routing change. | None for the current Android decision — `TYPE_CLASS_NUMBER` now reuses the restricted `phone_number.xml` route. |
| Date/time — `TYPE_CLASS_DATETIME` / (no iOS equivalent) | In practice, native/browser date-time controls show the Android system date/time picker, so LIME is normally not asked to display a keyboard. The fallback code path still maps `TYPE_CLASS_DATETIME` to `MODE_TEXT + isSymbol` → **`symbols1.xml`** if a host invokes the IME directly. | N/A. iOS hosts use `UIDatePicker`; no `UIKeyboardType` is delivered to the extension for date/time. | N/A. | None for the observed date/time picker path. Keep the fallback path unchanged unless a real host app/WebView is found that sends `TYPE_CLASS_DATETIME` to LIME and requires a custom restricted layout. |
| Email — `TYPE_TEXT_VARIATION_EMAIL_ADDRESS` (text class) / `.emailAddress` | `mEnglishOnly = true`, `mPredictionOn = false`, `MODE_EMAIL` ([LIMEService.java:921-927](../LimeStudio/app/src/main/java/net/toload/main/hd/LIMEService.java#L921-L927)). Switcher path ([LIMEKeyboardSwitcher.java:510-530](../LimeStudio/app/src/main/java/net/toload/main/hd/LIMEKeyboardSwitcher.java#L510-L530)): non-WB IM → **`lime_english_number(.xml)`** (or `lime_english_number_shift.xml`) when pref `numberRow` is true; **`lime_english.xml`** / `lime_english_shift.xml` when false. WB IM → **`lime_abc.xml`** / `lime_abc_shift.xml`. (`lime_email.xml` and its `R.xml.lime_email` switch-case entry have been removed in the #74 cleanup — they were never loaded by any code path.) | `.emailAddress` → `mEnglishOnly = true`, `mPredictionOn = false`, then the English layout family is selected. **iPhone loads:** `lime_english_number.json` (default) or `lime_english.json`. **iPad loads:** `lime_english_number_ipad.json` — always, since the number-row toggle is hidden on iPad. (`lime_email.json` / `lime_email_ipad.json` removed in #74 cleanup.) | **Decision (#74):** keep current behaviour — `.emailAddress` stays `mEnglishOnly = true` with `lime_english_number*` loaded on both iPhone and iPad. The number-row English layout already exposes `.` / `@` / `-` / `_`, so a dedicated email layout adds nothing. Cleanup: removed the never-loaded `lime_email.json` / `lime_email_ipad.json` assets and their `project.pbxproj` references. | **Decision (#74):** keep current behaviour — `MODE_EMAIL` already loads `lime_english_number(_shift)` / `lime_english(_shift)` (or `lime_abc*` for WB IM). Cleanup: removed the never-loaded `lime_email.xml` asset and its `R.xml.lime_email` switch-case entry in `LIMEKeyboardSwitcher.getKeyboardXMLID`. |
| URL/search — `TYPE_TEXT_VARIATION_URI` / search-like text fields / `.URL`, `.webSearch` | URL/search follows the generic text/default branch. When `persistent_language_mode` is on, LIME restores `language_mode`; when it is off, URL/search starts in Chinese mode like normal text. If the remembered mode is Chinese, `initialIMKeyboard()` loads the active IM layout. Search fields additionally depend on the host's `imeOptions` for the Enter/search key icon. | `.URL` / `.webSearch` follow the default branch. When `persistent_language_mode` is on, LIME reads `persisted_english_mode`; otherwise it starts in Chinese mode like normal text. `returnKeyType` still adapts the Enter key for search/go actions. | None — matches the #74 URL/search decision. | None — matches the #74 URL/search decision. |
| Password — `TYPE_TEXT_VARIATION_PASSWORD` / `TYPE_TEXT_VARIATION_WEB_PASSWORD` / `TYPE_TEXT_VARIATION_VISIBLE_PASSWORD` | `mEnglishOnly = true`, `mPredictionOn = false`, routed to **`MODE_EMAIL`** ([LIMEService.java:911-919](../LimeStudio/app/src/main/java/net/toload/main/hd/LIMEService.java#L911-L919)). Same layouts as the Email row: **`lime_english_number.xml`** / **`lime_english.xml`** / `lime_abc.xml` for WB IM. Candidate bar suppressed so password text is never echoed to suggestions. | iOS extensions cannot reliably read `isSecureTextEntry`; iOS swaps in the system secure keyboard, bypassing LIME entirely. No explicit handling in `initOnStartInput`. | None — platform-handled. | None. Android already disables prediction and forces English. |
| Short-message / IM body — `TYPE_TEXT_VARIATION_SHORT_MESSAGE` / (no iOS equivalent) | `mEnglishOnly = false`, `MODE_IM` ([LIMEService.java:937-939](../LimeStudio/app/src/main/java/net/toload/main/hd/LIMEService.java#L937-L939)). Switcher path: `isIm=true` branch → `kConfig.getImkb()` / `getImshiftkb()` ([LIMEKeyboardSwitcher.java:532-540](../LimeStudio/app/src/main/java/net/toload/main/hd/LIMEKeyboardSwitcher.java#L532-L540)) → the active IM's keyboard layout (e.g. **`lime.xml`** for phonetic, **`lime_cj.xml`** for Cangjie, **`lime_dayi.xml`**, **`lime_array.xml`**, …). | N/A. iOS has no SMS-body keyboard-type hint. | N/A. | None. |
| Generic text (`default`) / `.default`, `.asciiCapable`, `.twitter`, `.webSearch`, … | `default` branch ([LIMEService.java:942-956](../LimeStudio/app/src/main/java/net/toload/main/hd/LIMEService.java#L942-L956)). Persisted-mode on + English mode last selected → `MODE_TEXT` non-IM English → **`lime_english_number.xml`** / **`lime_english.xml`** (pref `numberRow`); WB IM → **`lime_abc.xml`**. Persisted-mode off or last mode = Chinese → `initialIMKeyboard()` → IM-specific layout (**`lime.xml`** / **`lime_cj.xml`** / **`lime_dayi.xml`** / **`lime_array.xml`** / **`lime_phonetic.xml`** etc., resolved via `kConfig.getImkb()`) ([LIMEKeyboardSwitcher.java:541-568](../LimeStudio/app/src/main/java/net/toload/main/hd/LIMEKeyboardSwitcher.java#L541-L568)). | `default` branch reads `persisted_english_mode` from `sharedDefaults` when `mPersistentLanguageMode` is on ([KeyboardViewController.swift:355-362](../LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift#L355-L362)). Layout selection via `resolvedLayoutId(for: activeIM)` ([KeyboardViewController.swift:510-545](../LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift#L510-L545)).<br>**`mEnglishOnly = true` branch — iPhone loads:** `lime_english_number.json` (default) or `lime_english.json`.<br>**`mEnglishOnly = true` branch — iPad loads:** `lime_english_number_ipad.json` (always — the number-row toggle is hidden on iPad).<br>**`mEnglishOnly = false` branch — iPhone loads** (one of, depending on active IM): `lime_phonetic.json`, `lime_cj.json`, `lime_dayi.json`, `lime_array.json`, `lime_et26.json`, `lime_hsu.json`, `lime_et_41.json`, `lime_ez.json`, `phone_simple.json` (for `array10`).<br>**`mEnglishOnly = false` branch — iPad loads** (matching the iPhone choice via `LayoutLoader` substitution): `lime_phonetic_ipad.json`, `lime_cj_ipad.json`, `lime_dayi_ipad.json`, `lime_array_ipad.json`, `lime_et26_ipad.json`, `lime_hsu_ipad.json`, `lime_et_41_ipad.json`, `lime_ez_ipad.json`. `phone_simple` has no `_ipad` variant — iPad falls back to `phone_simple.json`. | None — already matches Android. | None. |
| Persisted Chinese/English mode in restricted fields | Restricted branches (`TYPE_CLASS_NUMBER`, `TYPE_CLASS_DATETIME`, `TYPE_CLASS_PHONE`, email/password variations) set `mEnglishOnly = true` directly and never read `mLIMEPref.getLanguageMode()`. URL/search is intentionally excluded from this restricted bucket and follows generic text. For date/time, the system picker path normally bypasses LIME entirely. | Restricted iOS branches (`.numberPad`, `.decimalPad`, `.asciiCapableNumberPad`, `.emailAddress`) set `mEnglishOnly = true` before the `default` branch reads `persisted_english_mode`, so persisted mode is isolated from restricted fields. `.URL` / `.webSearch` intentionally follow default text. `.phonePad` is normally system-owned and bypasses LIME; the Swift fallback branch is also English-only if ever invoked. | None. | Confirm `TYPE_CLASS_NUMBER` stays isolated after routing to the restricted phone layout. No Android date/time keyboard change is needed for the normal system-picker path. |
| Remembered Chinese/English mode in normal text fields | When `mPersistentLanguageMode` is on, last mode is stored in prefs and restored in the `default` branch on next `onStartInput`. ([LIMEService.java:942-956](../LimeStudio/app/src/main/java/net/toload/main/hd/LIMEService.java#L942-L956)) | Same behaviour via `sharedDefaults` `"persisted_english_mode"` ([KeyboardViewController.swift:357-361](../LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift#L357-L361)). | None. | None. Already #74-compliant for normal text. |

## Layout assets that already exist

Both platforms already ship restricted layout assets. The remaining work is
mostly wiring, not new layout design.

### Android (`LimeStudio/app/src/main/res/xml/`)

Registered in `LIMEKeyboardSwitcher.getKeyboardXMLID`
([LIMEKeyboardSwitcher.java:285-431](../LimeStudio/app/src/main/java/net/toload/main/hd/LIMEKeyboardSwitcher.java#L285-L431)):

- **Restricted-field layouts (currently routed):** `phone_number.xml`
  (`MODE_PHONE`).
- **Chinese-IM-family alphabet/number layers** — these all carry a
  mode-switch key (`-9` EN to leave alphabet, `-10` 中 to return to IM),
  so they only make sense alongside a Chinese IM, not in restricted
  English/number fields:
  - `lime.xml`, `lime_cj.xml`, `lime_dayi.xml`, `lime_array.xml`,
    `lime_phonetic.xml`, `lime_et26.xml`, `lime_et_41.xml`, `lime_ez.xml`,
    `lime_hs.xml`, `lime_hsu.xml`, `lime_wb.xml`, `phone_simple.xml`
    (loaded via `kConfig.getImkb()`).
  - `lime_english.xml` / `lime_english_number.xml` and their `_shift`
    variants — the English alphabet layer of the IM family (carries `中` at
    `-10`). Loaded by `MODE_TEXT` English / `MODE_EMAIL` /
    persisted-English-mode paths, **even when no Chinese IM is active**.
    The `中` key is effectively a no-op in that case but the layout is
    re-used as the English-only fallback.
  - `lime_abc.xml` / `lime_abc_shift.xml` — the English alphabet layer
    used by the WB IM in `MODE_EMAIL`.
  - `lime_number.xml`, `lime_number_shift.xml`, `lime_number_symbol.xml`,
    `lime_number_symbol_shift.xml` — pure-numbers layer with `EN` return
    key at `-9` ([lime_number.xml:91](../LimeStudio/app/src/main/res/xml/lime_number.xml#L91)).
    Registered in `getKeyboardXMLID` but **not referenced by any current
    caller**. Belongs to the IM family, not the restricted-field family —
    must not be reused for #74.
- **Symbol layouts (loaded on demand):** `symbols1.xml`, `symbols2.xml`,
  `symbols3.xml`. `MODE_TEXT + isSymbol` opens `symbols1.xml`, which is
  also what number/date-time fields hit today.
- **Removed in #74 cleanup:** `lime_url.xml`, `lime_email.xml` were unused.
  URL/search now follows the generic text path, and `MODE_EMAIL` routes to
  `lime_english_number.xml` / `lime_english.xml` directly, with `lime_abc*`
  for WB IM. Both XML files
  and their `R.xml` switch-case entries in `LIMEKeyboardSwitcher.getKeyboardXMLID`
  have been deleted.
- **Missing for #74:** no new XML is required for the current Android number
  decision because `TYPE_CLASS_NUMBER` reuses the existing restricted
  `phone_number.xml` route. Do not add a date/time XML unless a real host
  invokes LIME for `TYPE_CLASS_DATETIME`; the observed Android behaviour is the
  system date/time picker.

### iOS (`LimeIME-iOS/LimeKeyboard/Layouts/`)

`LayoutLoader` automatically substitutes `_ipad` / `_ipad_shift` variants when
`hostIsPad` is true, falling back to the iPhone JSON if no variant exists
([LayoutLoader.swift:75-100](../LimeIME-iOS/LimeKeyboard/LayoutLoader.swift#L75-L100)):

- **Restricted-field layouts:** observed `.phonePad` uses the system's own
  phone/symbol keyboard, so LIME normally loads no JSON for it. A Swift
  fallback path still names `phone_number.json` if `.phonePad` reaches the
  extension directly. No `phone_number_ipad.json` exists, so that fallback
  would use the same iPhone JSON on iPad.
- **Chinese-IM-family alphabet/number layers** — like their Android
  siblings these carry mode-switch keys (`-9` EN / `-10` 中) and only make
  sense alongside a Chinese IM:
  - `lime_phonetic.json`, `lime_cj.json`, `lime_dayi.json`,
    `lime_dayi_sym.json`, `lime_array.json`, `lime_et26.json`,
    `lime_et_41.json`, `lime_ez.json`, `lime_hsu.json`, `phone_simple.json`
    (for the `array10` IM). Loaded via `resolvedLayoutId(for: activeIM)`.
    Each has `_shift`, `_ipad`, `_ipad_shift` companions.
  - `lime_english.json` / `lime_english_number.json` and their `_shift`
    / `_ipad` / `_ipad_shift` variants — the English alphabet layer of the
    IM family (carries `中` at `-10`). Re-used as the iOS English-only
    fallback for `.numberPad` / `.decimalPad` / `.asciiCapableNumberPad`
    / `.emailAddress` / persisted-English-mode, even with no IM
    active. On iPad, only the `_number_ipad*` variants are loaded because
    the number-row toggle is hidden in iPad settings
    ([PreferencesTabView.swift:96-98](../LimeIME-iOS/LimeSettings/Views/PreferencesTabView.swift#L96-L98));
    `lime_english_ipad.json` / `lime_english_ipad_shift.json` ship but
    are effectively dead on iPad.
  - `lime_abc.json` and its `_shift` / `_ipad` / `_ipad_shift` companions —
    English alphabet layer for the WB IM.
  - `lime_number.json`, `lime_number_shift.json`, `lime_number_ipad.json`,
    `lime_number_ipad_shift.json` — pure-numbers layer with `EN` return
    key at `-9` (`LimeKeyCode.switchToEnglish`). Present in `Layouts/`
    but **never loaded by current Swift code** (`grep "lime_number"` →
    zero hits). Belongs to the IM family, not the restricted-field
    family — must not be reused for #74.
- **Symbol layouts (loaded on demand):** `symbols1.json`, `symbols2.json`,
  `symbols3.json` and their `_ipad` variants; plus `popup_symbol_mode.json`.
- **Removed in #74 cleanup:** `lime_url.json`, `lime_url_ipad.json`,
  `lime_email.json`, `lime_email_ipad.json` were unused (no Swift caller
  passed `"lime_url"` or `"lime_email"` to `LayoutLoader.load`). All four
  files have been deleted, and their PBXBuildFile / PBXFileReference /
  PBXGroup / PBXResourcesBuildPhase entries have been removed from
  `LimeIME.xcodeproj/project.pbxproj`. The `.emailAddress`
  path continues to load `lime_english_number.json` (iPhone) /
  `lime_english_number_ipad.json` (iPad).
- **Missing for #74:** a mode-key-free `lime_numeric_field.json` (digits
  + decimal + sign + delete + enter + globe, no `-2` / `-9` / `-10`) for
  `.numberPad` / `.decimalPad` / `.asciiCapableNumberPad`.

### iPad-specific notes

- `lime_url_ipad.json` and `lime_email_ipad.json` are **intentionally unused**
  even after #74. URL/search now follows normal text/persisted mode, while
  iPad still loads `lime_english_number_ipad.json` for English-only email
  fields (the `number_row_in_english` toggle is hidden on iPad — see notation
  at top of table). That layout already provides the full number row plus
  `.` / `@` / `-` / `/`, so a dedicated iPad email layout adds nothing.
- Do **not** wire `lime_number_ipad.json` to `.numberPad / .decimalPad /
  .asciiCapableNumberPad`. The file exists, but it carries an `EN` key
  (`code: -9 / switchToEnglish`) because it is the numbers layer for a
  Chinese IM, not a restricted-field layout. #74's iPad number layout
  needs a new mode-key-free `lime_numeric_field_ipad.json` (or to share a
  single mode-key-free layout across iPhone and iPad via the existing
  `LayoutLoader` `_ipad` substitution).
- `phone_number.json` is only a fallback/legacy iOS route today; observed
  `.phonePad` displays the system phone/symbol keyboard instead of LIME. No
  `phone_number_ipad.json` exists.

## Return-key adaptation per `returnKeyType` / `imeOptions`

Both Apple's and Google's built-in keyboards adapt the Enter key's icon/label
to the host field's hint — URL/search fields show a magnifier, Go/Send/Done
fields show the matching text label. LIME matches this without adding any
new layout assets.

- **Android — already wired.** After every layout build,
  `LIMEKeyboardSwitcher.setKeyboardMode` calls
  `keyboard.setImeOptions(resources, mMode, imeOptions)`
  ([LIMEKeyboardSwitcher.java:586](../LimeStudio/app/src/main/java/net/toload/main/hd/LIMEKeyboardSwitcher.java#L586)).
  The Android `KeyboardView` framework adapts the action key
  (`KEYCODE_DONE = -4`) from `EditorInfo.imeOptions`
  (`IME_ACTION_SEARCH` → magnifier, `IME_ACTION_GO` → "Go", etc.). No further
  code change. Verify in a real browser that the host actually sends
  `IME_ACTION_SEARCH` for its address-bar field; otherwise the key shows the
  default action symbol.
- **iOS — wired in `KeyboardView.swift`.** A new `returnKeyType: UIReturnKeyType`
  property ([KeyboardView.swift:275](../LimeIME-iOS/LimeKeyboard/KeyboardView.swift#L275))
  is set from `textDocumentProxy.returnKeyType` at the top of
  `KeyboardViewController.initOnStartInput`
  ([KeyboardViewController.swift:412](../LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift#L412)).
  Its `didSet` triggers a key rebuild (and is a no-op on the initial load,
  since `rowViews` is empty until `setLayout` runs). `styleKeyContent` calls
  `enterKeyOverride(for:)` ([KeyboardView.swift:812](../LimeIME-iOS/LimeKeyboard/KeyboardView.swift#L812))
  which, for the Enter key (code 10), substitutes:
  - `.search` / `.google` / `.yahoo` → `magnifyingglass` icon
  - `.go` → `arrow.right` icon (matches Apple's iPad URL-bar Enter key)
  - `.send` / `.next` / `.join` / `.done` / `.route` / `.continue` →
    matching text label (no icon)
  - `.default` / `.emergencyCall` / unknown → no override (keeps the JSON's
    `return` icon)
  Implementation lives in `KeyboardView.swift` plus the one-line assignment
  in `KeyboardViewController.initOnStartInput`. **No layout JSON is added or
  modified.**
- **iOS — accent-blue "primary action" tint.** When `enterKeyOverride(for:)`
  returns non-nil (any non-`.default` returnKeyType), `applyButtonStyle`
  paints the Enter key with `.systemBlue` background
  ([KeyboardView.swift:786-804](../LimeIME-iOS/LimeKeyboard/KeyboardView.swift#L786))
  and `styleKeyContent` forces the foreground (icon/label) to white. This matches Apple's
  built-in keyboard treatment of the primary action key (blue "search" /
  "Go" / "Send" / "Done") so the user knows tapping Enter submits the
  field. `.default` keeps the standard modifier-grey key.

### Field-change re-adaptation (iOS)

`UIInputViewController.viewWillAppear` (which is where `initOnStartInput`
runs) only fires when the keyboard view first appears. Tapping from one
input field to another **while the keyboard stays on screen** does *not*
re-trigger it, so without an explicit detector LIME would keep the
previous field's layout / `mEnglishOnly` / `returnKeyType`.

The detector lives in `textDidChange`
([KeyboardViewController.swift:312-340](../LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift#L312-L340)):

- Two trackers — `lastSeenKeyboardType` and `lastSeenReturnKeyType` — record
  the field hints LIME last adapted to.
- On every `textDidChange` (excluding self-update keystrokes), the proxy's
  current `keyboardType` and `returnKeyType` are compared to the trackers.
- If either changed, `initOnStartInput()` is re-run so the layout, the
  `mEnglishOnly` / `mPredictionOn` flags, and the Enter-key adaptation all
  reflect the new field. The end of `initOnStartInput` writes the trackers
  back, so subsequent text keystrokes don't keep re-firing the re-adapt
  branch.

Effect: tapping from a Chinese-IM text input into a number / email / URL
input — or from any input into one with a different `enterkeyhint` /
`returnKeyType` — now updates LIME's keyboard immediately. No need to
dismiss the keyboard and re-pop.

## #139 — iOS number-field routing refinement (proposed)

> **Update (2026-06-29) — implemented & kept for Android parity (unverifiable on iOS).**
> A simulator investigation (see `docs/#139_ISSUE.md` → "Simulator investigation findings")
> showed this routing is **not observable on iOS**: iOS hands numeric web inputs
> (`inputmode="numeric"` / `pattern`) — and native numeric fields — to its own system
> keyboard, and a bare `type="number"` keeps LIME's active-IM keyboard (`phone_simple` for
> Array10). So LIME is never actually shown for `.numberPad`/`.decimalPad`. The
> `.numberPad`/`.decimalPad` → `phone_number` split below is **kept** anyway, because it
> aligns iOS routing with Android's `TYPE_CLASS_NUMBER → phone_number`; it simply can't be
> verified on-device. `.asciiCapableNumberPad` stays on `symbols1`.

Context: GitHub issue [#139](https://github.com/lime-ime/limeime/issues/139)
(Array10 numeric-field routing). A reporter's video shows a field where LIME
switches to the symbols keyboard (`symbols1`). The web test page
[`docs/keyboard-type-field-test.html`](keyboard-type-field-test.html) cannot
reproduce it, because a web page can only express a *subset* of `UIKeyboardType`
through `type` / `inputmode`; the keyboard type that drives this case is
native-only.

### The three iOS "number" keyboard types are not equivalent

`KeyboardViewController.layoutIdForCurrentInputField`
([KeyboardViewController.swift:524-531](../LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift#L524-L531))
currently routes **all three** number-class types to `symbols1`:

| `UIKeyboardType` | Allows | Pure number? | Web-reproducible? |
| --- | --- | --- | --- |
| `.numberPad` | digits 0–9 only (PIN-style) | yes | `inputmode="numeric"` (browser-dependent) |
| `.decimalPad` | digits + decimal point | yes (decimal) | `inputmode="decimal"` (browser-dependent) |
| `.asciiCapableNumberPad` | a number pad **that also allows ASCII letters** | **no** | **none — native only** |

`.asciiCapableNumberPad` is the odd one out: it exists precisely because the
field expects ASCII letters to be reachable (serial numbers, alphanumeric
codes, some 2FA inputs). Routing it to the letter-less `symbols1` is a poor
default. It is not a hard trap — `symbols1`'s bottom row carries `EN` (`-2` →
English layout) and `中` (`-10`), so letters are one tap away — but the field
opens on the symbol page when letters were the point. The original
justification ("mirror Android's number handling") does not hold for this type,
because **Android has no `.asciiCapableNumberPad`**; it was lumped in by name.

### Current cross-platform divergence on number fields

The current iOS `symbols1` routing does **not** actually match Android:

| | Number field → | Mode keys (`123` / `ABC` / `中`)? |
| --- | --- | --- |
| **Android** (`TYPE_CLASS_NUMBER`) | `phone_number.xml` | **none** (strict) |
| **iOS** (`.numberPad` / `.decimalPad`) | `symbols1.json` | **yes** (`EN`, `中`, space) |

Android sends every `TYPE_CLASS_NUMBER` to the locked-down `phone_number.xml`
([LIMEService.java:924-928](../LimeStudio/app/src/main/java/net/toload/main/hd/LIMEService.java#L924-L928)
→ `getRestrictedFieldKeyboardMode` returns `MODE_PHONE`,
[:141-148](../LimeStudio/app/src/main/java/net/toload/main/hd/LIMEService.java#L141-L148)
→ switcher [:485-487](../LimeStudio/app/src/main/java/net/toload/main/hd/LIMEKeyboardSwitcher.java#L485-L487)).
The iOS code comment claims to "mirror Android's `MODE_TEXT + isSymbol` path",
but that path is Android's **date/time** fallback, not its number layout — so
the two platforms already diverge.

### Layout mode-key reference (verified from the shipped assets)

| Layout (`.xml` / `.json`) | Mode keys present | Role |
| --- | --- | --- |
| `phone_number` | **none** — `+ - . / ( ) * #`, digits, delete, hide, return only | strict numeric; Android `TYPE_CLASS_NUMBER` / phone target |
| `symbols1` | `EN` (`-2`), `中` (`-10`) | symbols page; iOS number target today |
| `phone_simple` | `123` (`-2`), `ABC` (`-9`) | array10 IM keypad (+ Android feat#124 "123" long-press) — **not** a restricted layout |

`phone_number.json` already ships on iOS (today only the `.phonePad` fallback)
and is key-for-key identical to Android's `phone_number.xml` — **no new asset is
required**. `phone_simple` is **not** a candidate for restricted number fields:
it carries `123`/`ABC` switch keys, so it is no more "restricted" than
`symbols1`, and it is the array10 input keypad rather than a numeric-field
layout.

### Proposed routing (iOS only)

Split the three types at
[KeyboardViewController.swift:524-531](../LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift#L524-L531):

| iOS keyboard type | Route to | Rationale |
| --- | --- | --- |
| `.numberPad`, `.decimalPad` | **`phone_number`** | strict numeric, no escape keys — **aligns iOS with Android** |
| `.asciiCapableNumberPad` | **`symbols1`** (unchanged) | ASCII-capable — keep `EN` / `中` so letters stay reachable |
| `.phonePad` | `phone_number` (unchanged) | already restricted, one branch above |

```swift
if keyboardType == .numberPad || keyboardType == .decimalPad {
    return "phone_number"        // strict numeric, matches Android
}
if keyboardType == .asciiCapableNumberPad {
    return "symbols1"            // ASCII-capable: EN/中 keep letters reachable
}
```

`updateInputModeForCurrentField`
([KeyboardViewController.swift:498-513](../LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift#L498-L513))
can keep all three as `mEnglishOnly = true`; no change needed there.

**Android:** no change — `TYPE_CLASS_NUMBER` already routes to `phone_number`,
and Android has no `.asciiCapableNumberPad` equivalent.

### Tests / verification

- iOS unit test (extend `KeyboardViewControllerTest`): assert
  `layoutIdForCurrentInputField(keyboardType:…)` returns `"phone_number"` for
  `.numberPad` and `.decimalPad`, and `"symbols1"` for `.asciiCapableNumberPad`.
- Device test (the part the HTML **cannot** cover): a native field is required.
  Build a small SwiftUI/UIKit harness with `.keyboardType(.numberPad)`,
  `.decimalPad`, and `.asciiCapableNumberPad` (or use the real Array10 app) and
  confirm: numberPad / decimalPad → the strict `phone_number` keypad;
  asciiCapableNumberPad → `symbols1` with `EN` reaching letters.
- **Open caveat to settle on device:** confirm iOS actually surfaces the LIME
  keyboard (not the system numeric pad) for `.numberPad` / `.decimalPad`. If iOS
  shows its own numeric keyboard for those (as it does for `.phonePad`), the
  routing is moot for those two types and only `.asciiCapableNumberPad` matters
  in practice — which would make the split above primarily an
  `.asciiCapableNumberPad` correctness fix.

## Open questions (carry over from #74)

1. Should Android number and decimal keep sharing `phone_number.xml`, or should
   a later pass create a dedicated numeric layout with a different key set?
2. Which Android number flags affect the visible keys? `TYPE_NUMBER_FLAG_DECIMAL`,
   `TYPE_NUMBER_FLAG_SIGNED`, `TYPE_NUMBER_VARIATION_PASSWORD`. iOS equivalent:
   `.numberPad` (integers) vs `.decimalPad` (decimal) — these are the only
   sub-hints iOS provides.
3. If a real Android app/WebView is found that sends `TYPE_CLASS_DATETIME` to
   LIME instead of showing the system picker, which separators are needed:
   `:`, `/`, `-`, `.`, space? iOS has no surface for this.
4. Should WebView/Chrome `inputmode="numeric|decimal|tel"` map to the same
   behaviour as native `TYPE_CLASS_NUMBER` / `TYPE_CLASS_PHONE`? Browser-
   dependent and only testable manually. iOS Safari Web Extensions: same
   question against `.numberPad` / `.decimalPad` / `.phonePad`.
5. ~~URL/URI product direction (allow Chinese for search vs strict English).~~
   **Resolved (#74):** URL/search fields are normal text on both platforms.
   They follow `persistent_language_mode` / `persisted_english_mode` so browser
   address bars can start in Chinese when the user last selected Chinese.
   Cleanup actions:
   deleted the unused `lime_url.xml` / `lime_url*.json` assets, their
   pbxproj entries, and the `R.xml.lime_url` switch-case entry.

## Verification plan (mirrors #74)

Run on both Android and iOS with LIME enabled, then verify each case:

- Normal text — remembered Chinese/English mode still works.
- URL/URI/search — follows normal text behavior and remembered Chinese/English mode.
  With persisted language mode off, URL/search starts in Chinese mode like
  normal text.
- Email and password — English-only, no prediction.
- Phone — Android shows the restricted `phone_number.xml` layout with no mode
  key; iOS shows the system phone/symbol keyboard.
- Number — Android shows the restricted `phone_number.xml` layout with no mode
  key; iOS currently shows `symbols1*`.
- Date/time (Android only) — system date/time picker appears; no LIME keyboard
  change is expected unless a host actually invokes the IME instead of the
  native picker.
- Array 10 normal Chinese layout — mode-switch still works for normal text
  fields (the original reporter workflow that motivated #74).

Suggested web fields to test on each platform's browser/WebView:

```html
<input type="tel">
<input type="number">
<input type="text" inputmode="numeric">
<input type="text" inputmode="decimal">
<input type="text" inputmode="tel">
<input type="url">
<input type="email">
<input type="password">
```
