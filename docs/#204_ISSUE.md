# Issue #204 Analysis: Numeric Fields Show the Dial-Pad Layout With No Directly Tappable Decimal Point

## Live issue state

- Issue: https://github.com/lime-ime/limeime/issues/204
- Labels: `bug`, `Usability`
- Status: Closed as completed after Android reporter confirmation. After initially reporting a failed v6.1.37 retest, `01disney` clarified at https://github.com/lime-ime/limeime/issues/204#issuecomment-5084922049 that Taishin's TWD field intentionally accepts integers only, while USD and other foreign-currency fields can enter a decimal point. The project account acknowledged that result at https://github.com/lime-ime/limeime/issues/204#issuecomment-5084926319 and closed the issue as completed. This confirms the reported Android workflow only. The supplementary iOS 6.1.36 report remains separately unverified.
- Reporters: `01disney` (Android, Taishin Bank transfer-amount field); `SmithCCho` (supplementary iOS report on 6.1.36).
- Reported versions: Android GitHub test APK v6.1.37 for the reporter-confirmed workflow; iOS LIME 6.1.36 for the supplementary report.

## Problem statement

Tapping the transfer-amount field in the Taishin Bank app brings up LIME's telephone dial pad rather than an ordinary numeric keypad. The reporter's complaint is specific: **there is no way to type a decimal point directly**, which makes amount entry impractical. The reporter contrasts it with LINE, where opening the `123` numeric key produces a keypad with the expected punctuation.

`SmithCCho` adds that iOS is affected as well: long-pressing the key that used to yield `.` no longer produces one on 6.1.36, by either tap or long press.

## Reproduction

- Android: Taishin Bank app → transfer → amount field, with LIME active. The original report includes screenshots comparing LIME's pad against LINE's numeric keyboard. The v6.1.37 retest was performed on Android 16 / Xiaomi Poco F6 Pro after the reporter downloaded and installed the GitHub test APK. The reporter first interpreted the TWD field's lack of a decimal point as a failed retest, then clarified that TWD is integer-only and that USD and other foreign-currency fields can enter a decimal point.
- iOS 6.1.36: reported against the field circled in the reporter's screenshot; neither tap nor long press yields `.`.
- A field-type matrix for manual testing is published at
  https://lime-ime.github.io/limeime/keyboard-type-field-test.html (source: `docs/keyboard-type-field-test.html`,
  served from the `/docs` GitHub Pages root).

## Root-cause analysis

The source diagnosis established two concerns: **which** layout a numeric field gets, and **what** that layout contained. v6.1.37 corrected the directly tappable punctuation problem. The reporter's later clarification shows that the apparent failed Taishin path was caused by comparing an integer-only TWD field with decimal-capable foreign-currency fields, not by failure of the shipped layout change.

### 1. Number fields and phone fields resolve to the same layout

On Android, `LIMEService.initOnStartInput()` maps `TYPE_CLASS_NUMBER` to `MODE_PHONE` (`LIMEService.java:141-142`), and `TYPE_CLASS_PHONE` maps to the same mode (`LIMEService.java:964-967`). `LIMEKeyboardSwitcher` then resolves `MODE_PHONE` to `R.xml.phone_number` (`LIMEKeyboardSwitcher.java:587-589`).

iOS mirrors this: `layoutIdForCurrentInputField` returns `phone_number` for `.phonePad`, `.numberPad` and `.decimalPad` alike (`KeyboardViewController.swift:877-885`).

So a bank amount field (`TYPE_CLASS_NUMBER`) and a telephone field (`TYPE_CLASS_PHONE`) receive the identical dial pad. That conflation is the structural cause of the mismatch the reporter describes.

### 2. The dial pad had no decimal-point key

`phone_number` allocated the two edge keys of row 3 to `(` (code 40) and `)` (code 41). `.` existed only as a **hint plus long-press popup** on the `/` key:

```xml
<Key limehd:codes="47" limehd:keyLabel=".\n/"
    limehd:popupCharacters="." limehd:popupKeyboard="@xml/popup_template" limehd:keyEdgeFlags="right"/>
```

`LIMEKeyboardBaseView` splits a `\n` label into `subLabel = labelA[0]` (small hint) and `label = labelA[1]` (the character actually emitted) — `LIMEKeyboardBaseView.java:1120-1126`. The key therefore **emitted `/` on tap**; `.` was reachable only by long press. That is precisely the reporter's complaint: no directly tappable decimal point.

The iOS long-press regression on 6.1.36 was **not** separately root-caused. The layout change promotes `.` to a first-class tappable key on `phone_number`, but that does not explain the long-press regression or prove that the supplementary iOS report uses this layout. Keep its diagnosis separate.

## Shipped fix

The shipped v6.1.37 change rebalanced `phone_number` so the two characters an amount field needs are direct keys, while the two characters a telephone field needs survive as long-press alternates with visible hints. The reporter confirmed that decimal entry works in Taishin's USD and other foreign-currency transfer fields. The TWD field remains integer-only by the bank's design.

| Row/position | Before | After | Long press |
| --- | --- | --- | --- |
| row 2, right edge | `.` over `/` (emits `/`) | `/` | — |
| row 3, left edge | `(` | `(` over `.` (emits `.`) | `(` |
| row 3, right edge | `)` | `)` over `,` (emits `,`) | `)` |

Changed files:

- `LimeStudio/app/src/main/res/xml/phone_number.xml` — code 47 becomes a plain `/`; code 40 → code 46 with `keyLabel="(\n."` and `popupCharacters="("`; code 41 → code 44 with `keyLabel=")\n,"` and `popupCharacters=")"`.
- `LimeIME-iOS/LimeKeyboard/Layouts/phone_number.json` — the same three keys. iOS renders `label` small above `sublabel` large (`KeyboardView.swift:1165-1166`), matching Android's split, so the hint goes in `label` and the emitted character in `sublabel`. This file is hand-authored and excluded from the XML→JSON converter (`convert_keyboard_layouts.py:81-82`), so it is edited in step with the XML.

Rationale for the specific characters:

- `.` and `,` are what a decimal amount field needs, and both are now reachable with a single tap.
- `(` and `)` remain available by long press with an on-key hint, so telephone-number formatting such as `(02) 1234-5678` is not lost. The hint matters: this codebase renders long-press affordances as manual label text, not automatically from `popupCharacters`, so an unhinted popup would have been undiscoverable.
- `,` is additionally the standard DTMF pause character, so it is not foreign to a dial pad.

## Test coverage

- `scripts/test_number_symbol_layout_ios.py` and `scripts/test_custom_im_keyboard_ios.py` pass (18 tests) after the change.
- XML↔JSON parity was checked by parsing both files and comparing `(code, label, sublabel, popupCharacters)` for every key, using the converter's own `parse_label()` from `scripts/convert_keyboard_layouts.py` so the Android `\n` convention is applied exactly as the converter would. All 20 keys match.

Gap: there is no dedicated regression test asserting `phone_number`'s key set, so a future edit could silently drop the decimal key or desynchronize the two platform files. A parity test over `phone_number.xml` / `phone_number.json` would close this and is the natural follow-up to add alongside the fix.

## Verification

- Both layout files parse; parity verified as above (2026-07-26).
- Maintainer confirmed on 2026-07-26 that the updated layout renders correctly on iOS.
- The rebuilt v6.1.37 GitHub testing APK is verified and contains the layout change.
- Reporter initially reported the Android v6.1.37 retest as failed, then clarified that Taishin's TWD field is integer-only and confirmed that USD and other foreign-currency fields can enter a decimal point. This confirms the reporter-visible Android workflow on the tested device and APK.

## Scope and limits

- **The shipped change is a layout-level remedy, not a routing split.** `phone_number` still serves both `TYPE_CLASS_PHONE` and `TYPE_CLASS_NUMBER`, so a bank amount field can still show `*`, `#`, `+` and `/`. Splitting telephone and numeric layouts remains a possible future product/engineering improvement, but the reporter confirmation does not establish a remaining Android defect requiring that work.
- The `,` key is new to this pad; `(` and `)` moved from tap to long press. Neither character is lost, but users who tapped the parens on a telephone field must now long-press them.
- The iOS 6.1.36 long-press regression reported by `SmithCCho` is worked around rather than explained. If his field is not numeric-typed (see Platform impact), the layout he is looking at is not `phone_number` and his specific report is untouched by this fix — that needs his field/app details to resolve.

## Platform impact

### Android

The corrected `phone_number` resource is delivered in the verified v6.1.37 GitHub testing APK. On Android 16 / Xiaomi Poco F6 Pro, the reporter confirmed that Taishin's USD and other foreign-currency transfer fields can enter a decimal point and clarified that the TWD field intentionally accepts integers only. Android reporter verification is complete.

### iOS

The maintainer confirmed the updated layout renders on iOS, so `phone_number` is reachable there through at least one path.

Separately, real-device testing against the field matrix on 2026-07-26 found that **web** numeric fields never reach LIME at all: iOS substitutes its own system keypad and the extension is not invoked, so `layoutIdForCurrentInputField` never runs. This matches the June 29 simulator investigation recorded in `docs/#139_ISSUE.md:165` and commit `0863e6f2` ("Apple platform behaviour, not a LIME bug"), with one difference worth noting: that investigation found bare `type="number"` / `type="num"` **kept** LIME active and only the `inputmode`/`pattern` variants were replaced, whereas the 2026-07-26 device test saw all numeric fields replaced. `docs/#139_ISSUE.md:165` asks for the numeric-routing scope to be reopened if real-device evidence appears; this is such evidence, and it is recorded here rather than acted on.

The practical consequence for `SmithCCho`'s report: he sees a *LIME* keyboard missing `.`, so his field cannot be numeric-typed, or iOS would have replaced LIME with its own keypad. Whatever layout he is on — `symbols1` or an iPad layout, his screenshot being 1320×878 — is not `phone_number` and is not addressed here.

## Verification plan

Done:

1. Traced the input-class → layout resolution on both platforms and confirmed number and phone fields share `phone_number`.
2. Confirmed from the label-split rendering code that the old `.\n/` key emitted `/` on tap, leaving no directly tappable decimal point.
3. Applied the layout change to both platform files and verified key-by-key parity with the converter's own label parser.
4. Ran the existing layout test suites (18 tests, passing).
5. Confirmed the updated layout renders on iOS.

Reporter verification:

6. `01disney` confirmed decimal entry in Taishin's USD and other foreign-currency transfer fields on Android v6.1.37 and clarified that the TWD field is integer-only.

Future internal checks, not active Android reporter watches:

7. Add a dedicated parity/behavior contract for the `phone_number` decimal and comma keys if this layout is changed again.
8. Obtain `SmithCCho`'s field/app details before separately diagnosing the supplementary iOS 6.1.36 long-press report.
