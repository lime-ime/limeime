# feat#N02 — Computer-numpad keyboard for Array10 (行列10)

Requested by Jeremy on 2026-06-30. Add a **computer-numpad** numeric layout as an option
for Array10 (行列10): top row `7 8 9`, then `4 5 6`, then `1 2 3`, with `0` on the bottom
row — the opposite row order to the existing phone-style numpad. The phone-style layout
stays available; this is an additional choice.

## Design — it's a new entry in the global keyboard list, not switcher/service logic

Keyboard layouts are a **global shared list** (the `keyboard` table in `lime.db`). Every IM's
keyboard picker chooses from the same list; an IM stores its choice as the `keyboard` value in
the `im` key-value table, and the keyboard view resolves that to a layout via the row's `imkb`.
Array10 ships pointing at `phonenum` → `phone_simple`.

So feat#N02 adds **one sibling row** `computernum` — identical to `phonenum` except
`imkb`/`imshiftkb` point at a new `computer_simple` layout — plus the layout resource itself.
Selecting it needs no new UI: it appears in the existing global keyboard picker, scoped to
whichever IM the user assigns it to. `phonenum` is untouched.

### Seeding (no schema change, no DB version bump)

The row is seeded **insert-if-absent on every open**, not via a version-gated `onUpgrade`.
Rationale: a restored backup can claim a current `user_version` yet carry stale content,
skipping `onUpgrade` (the #88 restore-crash family; see the comment at `LimeDB.java` onUpgrade).
This helper is the **sole writer** of the `computernum` row, so a present row is by definition
correct and is left untouched (a user-chosen assignment is never overwritten) — hence
insert-if-absent, not upsert, and not `INSERT OR IGNORE` (which would also mask a genuine
insert failure).

- Android: `LimeDB.ensureComputerNumKeyboard(db)`, called from `ensureCurrentDatabase()`.
- iOS: `LimeDB.ensureComputerNumKeyboard(db)`, called from `ensureCurrentDatabase()`.

`computernum` mirrors `phonenum`: name 電腦數字, desc 電腦數字鍵盤, type `phone`, image
`phone_simple_preview` (reused; cosmetic), `imkb`/`imshiftkb` = `computer_simple`,
engkb `lime`/`lime_shift`, symbolkb `symbols`/`symbols_shift`.

### The layout

`computer_simple` is `phone_simple` with **only the digit keys in row 1 and row 3 swapped**
(code+label together: `1 2 3` ⟷ `7 8 9`). Row 2 (`4 5 6`) and the bottom row (`0`) are
unchanged, as are the framing modifier keys (`123`, `ABC`, ⌫, `+-*/=`, space, done, return).

- Android: `res/xml/computer_simple.xml`
- iOS: `LimeKeyboard/Layouts/computer_simple.json` (also registered in `LimeIME.xcodeproj` —
  the `Layouts` group lists files individually, so a new file needs a fileRef + group child +
  Resources build-file entry).

## Android-only gotcha — layouts are resolved by a hardcoded switch (caused a crash)

iOS loads layouts by filename (`LayoutLoader.load("computer_simple")` → `computer_simple.json`),
so a new layout file "just works". **Android does not** — `LIMEKeyboardSwitcher.getKeyboardXMLID(imkb)`
is a hardcoded `switch` mapping layout id → `R.xml.*`, returning `0` for any unknown id.

With `computernum` selected for Array10, `imkb = "computer_simple"` had no case → `getKeyboardXMLID`
returned `0` → loading XML resource id `0` **crashed on switch to Array10**. Fix: add
`case "computer_simple": return R.xml.computer_simple;`.

Second Android spot: Array10 auto-commit (`LIMEService.java`) gated on
`currentSoftKeyboard.contains("phone")`, which `computernum` does not match — so Array10 would
silently lose auto-commit on the computer numpad. (iOS keys this off `activeIM == "array10"`, so
it was fine.) Fix: also accept `currentSoftKeyboard.contains("computernum")`.

Net: any **new keyboard layout on Android** must be registered in `getKeyboardXMLID`, and any
keyboard-name-based special-casing (`.contains("phone")`) must be taught the new code.

## Tests (TDD)

- iOS `LimeDBTest.testComputerNumKeyboardIsSeededAndPointsAtComputerSimpleLayout` — `computernum`
  seeds and `imkb == computer_simple`. (RED→GREEN confirmed.)
- iOS `KeyboardViewControllerTest.testComputerSimpleLayoutUsesComputerNumpadDigitOrder` — digit
  rows are `7 8 9 / 4 5 6 / 1 2 3 / 0`, each digit key emits the ASCII code matching its label.
  (RED→GREEN confirmed.)
- Android `LimeDBTest.testComputerNumKeyboardIsSeededAndPointsAtComputerSimpleLayout` — mirror.
  (GREEN.)
- Android `KeyboardLayoutResourceTest.computerSimpleLayoutUsesComputerNumpadDigitOrder` — mirror.
  (GREEN.)

The data + layout pieces are covered by automated tests on both platforms. The crash fix
(`getKeyboardXMLID` case) is verified by compile + the root-cause analysis above; the private
switch isn't exercised by the unit tests, so the **on-device switch-to-Array10** retest is the
remaining proof.

## Files touched

- iOS: `LimeKeyboard/Layouts/computer_simple.json` (new), `Shared/Database/LimeDB.swift`
  (`ensureComputerNumKeyboard` + call), `LimeIME.xcodeproj/project.pbxproj` (bundle), tests.
- Android: `res/xml/computer_simple.xml` (new), `limedb/LimeDB.java` (`ensureComputerNumKeyboard`
  + call), `LIMEKeyboardSwitcher.java` (`getKeyboardXMLID` case — crash fix),
  `LIMEService.java` (auto-commit parity), tests.

## Status

Implemented on both platforms; unit tests green on both; Android compiles. Pending: on-device /
simulator visual verification (assign 電腦數字 to Array10, switch to Array10 → no crash, digits
read 7 8 9 on top, auto-commit still fires).
