# Issue #140: cj4 (四碼倉頡) semicolon key on the Cangjie keyboard

## Status

- GitHub issue: https://github.com/lime-ime/limeime/issues/140
- Classification: `enhancement` + `Usability`
- Reporter: `homchang` (public issue). Maintainer `limeimetw` acknowledged and said the
  Cangjie layout is currently fixed (bottom row: comma / space / period / symbol-switch),
  with no `;` key and no layout-customization feature; the need was added to the authors'
  evaluation list.
- Current state: source merged on `master` in merge commit `be156c4c15fd` after
  implementation commit `1004453b8682`. GitHub Release / APK v6.1.27 is now live as
  `LIMEHD2026-6.1.27.apk` (GitHub Contents blob SHA
  `297a2ffe40e5ab3a6361f9cae8cf301d40bd8292`, size 7410887 bytes, downloaded SHA-256
  `299d579df4dc2ffdceabdb038f708b46098dd721bbcd271f522ebd239d4ae653`) and contains the cj4 semicolon-key / imKeys-unification work for Android/GitHub APK testers.
  `limeimetw` posted the scoped reporter update at
  https://github.com/lime-ime/limeime/issues/140#issuecomment-4846962860, asking GitHub
  APK testers to update to v6.1.27 and Google Play users to wait for the Google Play test
  update. The original reporter later clarified in
  https://github.com/lime-ime/limeime/issues/140#issuecomment-4851381332 that they are an
  iOS tester, so their validation depends on a future TestFlight/App Store build rather than
  the Android GitHub APK. The issue remains open for iOS reporter confirmation and broader
  keyboard-customization evaluation.
- Later product feedback in the same issue:
  - `awei1976` asked for broader keyboard customization, especially hiding or moving the
    keyboard-hide / `EN` keys to make Boshiamy Space wider.
  - `admit888` reported that the Dayi keyboard layout differs between their GitHub APK and
    Google Play installs, and that the Google Play build does not switch input methods from
    the space-key gesture in the way they expected.
  These are related layout/channel/gesture feedback items, not part of the confirmed cj4
  semicolon-key scope unless a maintainer separately confirms them.

## Request

四碼倉頡 (a four-code Cangjie) uses the **`;` key** as part of its input, but LIME's
Cangjie keyboard has no `;`. The reporter asks for either user-customizable keyboard
layouts, or a Cangjie layout that adds a `;` key.

## Decision / approach

Add a single **per-IM preference** on the **cj4 (四碼倉頡)** input method — a toggle shown
**always** in the cj4 IM details page, **default off**. Everything is **programmatic** (no
permanent layout-file edits, no new static layouts). When the toggle is **on**, three things
change:

1. **iPhone + Android** — when the keyboard is `lime_cj` / `lime_cj_number`: add a `;` key
   (code 59) at the **right end of the asdf/home row**, removing the row's front + ending
   spacer so the 9 keys + `;` fill the row (10 × 10% = 100%, aligned with `qwertyuiop`).
   The `;` key **long-presses to `'`** (code 39) — `'` is the table's full-shape-symbol root.
   **Label convention:** tap output (`;`) on the **bottom**, slide/long-press output (`'`) on
   the **top**.
   - iOS asdf row = 9 keys × 10% (90%, centered); Android's `a` key carries
     `horizontalGap="5%p"` with ~5% trailing. Both become a flush 10-key row.
2. **iPad** — when the keyboard is `lime_cj` / `lime_cj_number` (iPad variants):
   **programmatically** rewrite the existing `；|：` dual-sliding key (`code 65306` in the iPad
   cj layout) to **`'` / `;`** — tap → `;` (code 59), slide / long-press → `'` (code 39), both
   table roots (`:` is **not** a root, so it is dropped). Same **label convention** as phone:
   slide output (`'`) on **top**, tap output (`;`) on the **bottom**. **Pref-driven, NOT a
   permanent layout swap** — toggle off ⇒ the key stays `；|：`.
3. **Symbol mapping** — **only while the `;` pref is on**, force `hasSymbolMapping = true` for
   cj4 (pass `symbolMapping: true` to `SearchServer.setTableName` / set
   `searchServer.hasSymbolMapping` on iOS) so `;` is **accepted into composing as a root**
   instead of typed literally. With the pref **off**, cj4 keeps its table-default
   `hasSymbol` (this override is purely pref-gated, never permanent).

When **off**: layouts are untouched (no `;` on phone; `；|：` stays on iPad) and cj4 keeps its
table-default symbol mapping.

> **Resolved by implementation:** the branch did not ship with the temporary
> `hasSymbolMapping` / `setSymbolMapping` / `refreshImKeys` append approach. The final
> implementation landed after [INIMKEYS_UNIFICATION.md](INIMKEYS_UNIFICATION.md): cj4's
> `;` comes from the table-primary `imKeys` path, so Android and iOS accept the table's own
> roots consistently.

## Scope

- **Layouts:** only `lime_cj` and `lime_cj_number` — and their `_shift`, `_ipad`,
  `_ipad_shift`, and `_ipad_narrow*` variants. No other layouts are touched.
- **Platforms:** Android, iOS iPhone, iOS iPad — all driven by the same cj4 toggle (phone =
  add `;` to the asdf row; iPad = pref-driven rewrite of `；|：` → `:|;`).
- **IM:** the toggle is a cj4-specific setting, always shown in the cj4 details page.

## Implementation notes

All programmatic; gated on the cj4 toggle. No new layout files.

### iOS (iPhone + iPad)

- `IMDetailView` already hosts per-IM settings (e.g. `phonetic_keyboard_type`, a per-IM
  dynamic-key pref). Add the cj4 toggle, always shown in cj4's details.
- `LayoutLoader.load(_:)` returns a mutable `LimeKeyLayout` struct. Post-process it when the
  layout is `lime_cj` / `lime_cj_number` (incl. `_shift`, `_ipad`, `_ipad_narrow*`) and the cj4
  toggle is on:
  - **iPhone variants:** insert a `;` `KeyDef` (code 59, `widthPercent` 10) at the end of the
    asdf row; drop the row's centering so it renders as a flush 10-key row.
  - **iPad variants:** find the `；|：` key (`code 65306`) and rewrite it to `:|;` (codes 58 /
    59), keeping its width/position.
- Force `hasSymbolMapping = true` for cj4 at the `setTableName` call site when the toggle is on.

### Android

- Add the cj4 toggle to the IM settings/details path (per-IM pref storage).
- The Cangjie keyboard is XML-inflated (`LIMEKeyboard.createKeyFromXml`); the asdf row has a 5%
  leading `horizontalGap` plus ~5% trailing. When the cj4 toggle is on and the keyboard is
  `lime_cj` / `lime_cj_number`, programmatically insert a `;` `Key` (code 59) at the right end
  and drop the leading/trailing gap so 10 keys fill the row (post-inflation in `LIMEKeyboard`;
  no new static XML). Android has no `；|：` iPad key — phone-style injection only.
- Pass `symbolMapping = true` to `SearchServer.setTableName` for cj4 when the toggle is on.

## Open questions

1. **Broad `hasSymbolMapping` is correct (RESOLVED).** A `;`-using four-code Cangjie table uses
   **multiple symbol roots**, not just `;` — verified against an internal test table whose
   `%keyname` defines `' , . ; ? [ ]` as input keys. So *all* symbol-range codes must be
   accepted into composing; forcing `hasSymbolMapping = true` (broad) is right — do **not**
   narrow it to `;`-only. This also means `,` / `.` correctly compose as the table's roots
   `，` / `。` (the no-`hasSymbol` full-width-insert branch at
   [`KeyboardViewController.swift:1358`](../LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift#L1358)
   is for tables that don't map them as roots).
2. **Table maps `;` (RESOLVED / verified).** An internal test table: `;` is in `%keyname`
   (`; ；`) and appears in **10,055** `%chardef` codes (e.g. `; ；`, `; ：`, `''; ”`). So with
   `hasSymbolMapping` on, `;` composes as a root. (Each user's own table must likewise map `;`;
   this confirms a real `;`-using Cangjie table does.)
3. **Android injection point (RESOLVED).** The merged Android implementation builds the cj4
   semicolon variant in `LIMEKeyboard` / `LIMEKeyboardSwitcher`, gated by the cj4 preference,
   without static layout-file copies.
4. **Applying on pref change — superseded.** The earlier plan was to add
   `SearchServer.setSymbolMapping(boolean)` (Android; iOS equivalent) that just sets
   `hasSymbolMapping`. No full `setTableName` reload is needed: the `;` lookup works on a cache
   miss anyway, and the Android prefetch list already includes `;` (`",./;"`,
   [SearchServer.java:272](../LimeStudio/app/src/main/java/net/toload/main/hd/SearchServer.java#L272)).
   The **keyboard's controller** (`LIMEService` / `KeyboardViewController`) calls it when it
   applies the cj4 layout, reading the current cj4 pref. The **settings / IM-details UI does
   not call it directly** — it is a different process/context from the live keyboard's
   `SearchServer`; the pref change propagates via shared prefs and is applied on the next
   keyboard appearance / `onStartInput`, alongside the layout rebuild. The merged implementation
   no longer uses this setter path because table-primary `imKeys` acceptance replaced it.

## Verification plan

- cj4 active, toggle **on**:
  - iPhone / Android (`lime_cj` / `lime_cj_number`): `;` appears at the right end of the asdf
    row (flush 10-key row); tapping it composes, given a table that maps `;`.
  - iPad (`lime_cj` / `lime_cj_number`): the `；|：` key reads `:|;` and inputs ASCII `:` / `;`,
    composing as a root.
- Toggle **off**: no `;` on phone; iPad key stays `；|：`; cj4 keeps its table-default symbol
  mapping. Other IMs unaffected; the toggle is cj4-only.

## Settings spec & docs to update (on implementation)

The new toggle is a per-IM setting (like `accept_number_index` / `backup_on_delete_*`). One
important difference: those existing per-IM prefs are **settings-only** (stored in
`UserDefaults.standard` / default `SharedPreferences`), but this toggle is **read by the
keyboard extension**, so it must live in the **shared App-Group prefs** (`sharedDefaults` /
the shared keyboard prefs) — otherwise the keyboard can't see it.

- **Pref:** suggested key `cj4_semicolon_key` (or `{tableNick}_semicolon_key`) · Toggle ·
  default **false** · label TBD (e.g. 「加上 ; 鍵」) · shown in the cj4 IM details page ·
  effect gated by the cj / cj_num keyboard · **storage = shared App-Group prefs** (not
  `UserDefaults.standard`).
- **`docs/LIME_SETTINGS.md`** — add the toggle to the IM-details / per-IM settings spec
  (the §5.x / IMDetailView per-IM section), noting the shared-prefs storage tier.
- **`docs/PREFS_TABLE.md` §5.2** (IMDetailView — per-IM prefs, cross-listed) — add a row:
  key · Toggle · default false · label · gating (cj4 / cj-keyboard) · storage (**shared
  App-Group prefs**, unlike `backup_on_delete_*`) · function.
- **Backup / restore** — add the new key to `docs/PREF_BAK_RES.md`'s **"Per-IM options"** row
  (which already lists `accept_number_index` / `accept_symbol_index` / `auto_commit` /
  `phonetic_keyboard_type` / `backup_on_delete_<table>` / `restore_on_import_<table>`), and to
  the backup/restore implementation. Because this toggle lives in the **App-Group prefs** (like
  `accept_*` / `auto_commit`, which the keyboard reads — not the settings-only
  `UserDefaults.standard` bucket used by `backup_on_delete_*`), it rides the App-Group backup
  payload. (Pre-existing gap, not specific to this pref: PREF_BAK_RES.md notes iOS full-archive
  pref restore is still "to be wired into full restore".)
- **`manuals/ime-management.md`** → `## 編輯輸入法與字根` — add a row to the **「專屬設定」
  table** (which already lists 注音 / 行列 10 / 自建): **四碼倉頡 (cj4)** → the `;`-key toggle,
  in Traditional Chinese matching the existing rows' style. (The IM-detail per-IM coverage is
  otherwise complete: `backup_on_delete` here, `restore_on_import` in the import flow.)
- **`docs/LIME_SETTINGS_BACKPORT.md`** — Android placement decision. Its §5.2 keeps per-IM
  prefs (`auto_commit` / `phonetic_keyboard_type` / `accept_*`) in the global `preference.xml`
  rather than `ImDetailFragment`; decide where the cj4 toggle surfaces on Android and record it.
- **`docs/BACKLOG.md`** — `feat#140` tracking entry (added at design time).

## Implementation landed (2026-06-30, branch `feat/140-cangjie-semicolon`)

The placeholder approach above (forced `hasSymbolMapping`, `setSymbolMapping` API, iOS `refreshImKeys` `;`/`'` append) was **reworked out** once [INIMKEYS_UNIFICATION.md](INIMKEYS_UNIFICATION.md) landed. Final shape:

### Acceptance — table-primary `imKeys` (no per-`;` hack)

`isKeyInImkeys` + **table-primary `imKeysForTable`** now drive composing-acceptance on both platforms: a table's roots come from its imported `imkeys`/`imkeynames` (the same data Android reads via `getImConfig`); the hardcoded keymaps are fallback only; the phonetic family stays keyboard-type-driven. So a cj4 table that maps `' , . ; ? [ ]` accepts them straight from its own `imkeys` — **no cj4-specific symbol-mapping override, no `refreshImKeys` append.** iOS keyboard/imkeys handling is now identical to Android except for iPad layouts and the iPhone-popup / iPad-slide gestures.

### `;` → `'` input gesture (the part this session finished)

- **iPad:** the `；|：` dual key (`code 65306`) is rewritten to **`'` / `;`** — tap → `;` (59), **slide** → `'` (39). Unchanged, working (`testCj4SemicolonTransform…`).
- **iPhone:** the appended `;` key (code 59) carries `longPressCode 39`, `popupKeyboard "popup_template"`, `popupCharacters "'"`. Long-press behaviour was unified with the existing **single-key popup** path (et_41 `-`→`5`, `=`→`6`; English `c`→`ç`, `n`→`ñ`): **long-press shows the mini-keyboard popup over the key preview, release commits the lone alternate** (`didReleasePopupKey`), tap types the primary. Same code path as a multi-char popup — single-key just dismisses + sends on release instead of leaving the panel up. Default `cancelsTouchesInView == true` on the popup recognizer prevents the `keyDown` tap from *also* firing (the earlier `';` / `6=` double-output bug).

### Screen-edge fix (required because `;` is a right-edge key)

The cj4 `;` and et_41 `=` sit at the screen's right edge (x = 100%), where iOS eats the button's `touchDown` on a held press → no preview/haptic. Fixed by driving press feedback off a `minimumPressDuration = 0` `UILongPressGestureRecognizer` (`pressFeedbackGesture`) instead of `keyDown`, which gets `.began` without the edge delay. Full diagnosis + the device-diagnostics recipe + why `preferredScreenEdgesDeferringSystemGestures` can't help an extension: see **[IOS_MISS_KEY.md → 2026-06-30 entry](IOS_MISS_KEY.md)**.

### Android

`restoredToDefault` → `ensureCj4Schema` crash (closed `SQLiteDatabase`, pre-existing from #84) fixed in passing: `openDBConnection(force_reload)` now uses the helper's `close()` (drops the cached handle so `getWritableDatabase()` reopens the recreated file) + a live-handle guard in `ensureCurrentDatabase`. Verified green on the emulator **and** the real Samsung A17 (`SearchServerTest#test_3_5_9_2_restoredToDefault_after_reset`).
