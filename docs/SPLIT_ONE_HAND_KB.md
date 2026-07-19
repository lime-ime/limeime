# Split & one-handed keyboard — reach-based geometry (tablet split fine-tune, phone one-hand, numpad anchoring)

## Source request

Jeremy requested this product work on 2026-07-17. Three related asks, all driven by the same
observation: key sizes today are derived from screen width fractions, not from what a human hand
can actually reach.

1. Tablet/iPad (7"+): the split keyboard we already have needs fine-tuning for two-handed
   (two-thumb) operation. Current iPad split keys are too wide — key width should be computed
   from the area an ordinary thumb can reach while gripping the device left and right, both
   horizontally and vertically, for 7" / 11" / 13" classes.
2. Phones: add a one-handed layout option that shrinks and anchors the keyboard left or right,
   sized to the one-thumb reach zone. (Originally scoped to ≈5.5"+ phones; issue #169 removed
   that width gate — the option now applies to **every** phone, and the width helper only clamps
   the block to the available screen width.)
3. Tablet/iPad numpad-based layouts (phone numpad, computer numpad, restricted numeric fields):
   these are one-handed by nature and should get an anchor option — left / right / center /
   fit (current full-width style) — with key size from real one-hand reach geometry.

Split keyboard must exclude numpad-based layouts (they get the anchor treatment instead).

Options live in app preferences AND in the in-keyboard menu (Android key long-press options
panel, iOS globe/hamburger menu), so on iOS they must be cold/hot prefs (§1.8 pattern).

## Status

Implemented on branch `feat/split-one-hand-kb` (both platforms). Implementation decisions are
recorded in `docs/SPLIT_ONE_HAND_KB_PLAN.md`. Deviations from / additions to the original
proposal, all shipped:

- **Geometry changes apply in place** — the chevron tap and the keyboard-menu apply rebuild the
  current keyboard without dismissing the IME (`LIMEService.applyGeometryChangeInPlace()` +
  `LIMEKeyboardSwitcher.rebuildCurrentKeyboard()`; originally `handleClose()` was called, which
  `requestHideSelf`'d the whole keyboard).
- **Phone geometry is being corrected after #169**. v6.1.33 made phone split landscape-only and
  exposed `split_keyboard_mode` and `one_hand_mode` as independent settings. That broke the legacy
  portrait split contract and allowed contradictory stored choices. The replacement design uses
  one integrated, mutually exclusive portrait mode plus a separate phone-landscape split boolean.
  Tablet split remains in its existing portable tablet preference. See the migration and
  eligibility sections below.
- **iOS split partition matches Android** — `SplitPartition.partition` (`KeyLayout.swift`) splits
  at floor(columns/2) standard columns and clones a border-crossing wide key (space) onto both
  halves, replacing the old 50%-cumulative rule that put the whole space bar in the left half.
  Unit-tested in `ReachGeometryTests`.
- **Numpad exclusion set grew** to include the T9-style phone-IM keypads (`phone` /
  `phone_shift`) on both platforms, and iOS gained the missing `phone_number` exclusion.
- **Candidate strip AND expanded-candidates panel follow the anchor insets** (the expanded panel
  was originally a v1 non-goal; it now wraps and positions to the anchored block width on both
  platforms).

## What already exists (do not rebuild)

- **Android split engine** — `LIMEBaseKeyboard.java`: modes NEVER/ALWAYS/LANDSCAPE_ONLY
  (`:113-115`), enable decision `:806-808`, split key width
  `mSplitKeyWidth = displayWidth / (keysInRow + reservedColumns)` `:1371-1381`, right-half shift +
  center arrow-key injection `:1175-1327`. `reservedColumns` scales by device bucket only:
  2 (`values/config.xml`), 3 (`values-land`), 7 (`values-xlarge-land`) — a screen-fraction model,
  not a reach model.
- **iOS split (iPad only)** — `KeyboardViewController.swift:134` `splitKeyboardMode` (0/1/2),
  applied `:575-576`; `KeyboardView.swift:775-834` `makeSplitRow()` splits each row at the 50%
  cumulative-width point; each half gets `(1 − splitGapFraction)/2` of the view with
  `splitGapFraction = 0.06` fixed (`LayoutMetrics.swift:318`). So each half is always ~47% of the
  keyboard width regardless of physical size — on a 13" iPad landscape (1366 pt) that is a 642 pt
  half with ~128 pt (~25 mm) keys. This is the "too wide" complaint.
- **iOS cold/hot pref template** — `split_keyboard_mode` is already fully wired: cold in App Group
  defaults (`LIMEPreferenceManager.swift`), hot via `seededHotInt`
  (`KeyboardViewController.swift:1140-1146`, seeded at `:1260`), app→keyboard `PrefInbox` drain
  (`:1164-1198`), keyboard→app `relay-prefs.json` writeback (`:4464-4466`). Copy this wiring
  verbatim for the two new prefs.
- **In-keyboard menus** — Android `LIMEService.handleOptions()` `:3646-3780` already has a
  segmented 分離鍵盤 block (`:3733-3750`); iOS `showGlobeMenu` `:4410-4469` already has a
  segmented 分離鍵盤 entry gated by `isOnPad` (`:4432-4439`). New toggles are additional
  segmented rows in the same two places.
- **Numpad layouts** — Android `phone_simple.xml` (phone-style 1-2-3) and `computer_simple.xml`
  (computer-style 7-8-9), both 4 rows × 5 columns (`keyWidth="20%p"`), plus `phone_number.xml`
  for restricted fields (#74) and the T9-style phone-IM keypads `phone.xml` / `phone_shift.xml`
  (same 5-column grid class). iOS mirrors the same set: layout ids `phone*` and
  `computer_simple*`. Restricted-field mapping in `KeyboardTypePolicy.swift:29`
  (`.numberPad/.decimalPad/.asciiCapableNumberPad/.phonePad`) and the feat#N02 computer-numpad
  keyboard-list entry.

## Reach geometry model (shared by all three features)

All widths below are physical millimetres, converted to px/pt at apply time:

- **Android**: exact — `px = mm × DisplayMetrics.xdpi / 25.4`.
- **iOS**: pt/mm is device-class dependent; add a constant table in `LayoutMetrics.swift`
  keyed off the existing `IPadSizeClass` (`LayoutMetrics.swift:45-54`):
  `.small` (iPad mini class, 163 pt/in) → 6.4 pt/mm; `.medium`/`.large` (132 pt/in) → 5.2 pt/mm;
  iPhone → 6.0 pt/mm. Named constants, one place.

Anthropometric constants (named, single definition per platform — these are calibration knobs,
expect tuning after on-device trials; do not scatter the numbers):

| Constant | Value | Meaning |
|---|---|---|
| `REACH_TWO_HAND_MM` | 72 | functional thumb sweep radius from a side-bezel two-hand grip |
| `BEZEL_INSET_MM` | 6 | grip pivot sits at the bezel, not the screen edge |
| `SPLIT_HALF_MAX_MM` | 66 | = `REACH_TWO_HAND_MM − BEZEL_INSET_MM`; max width of one split half |
| `SPLIT_KEY_MIN_MM` / `SPLIT_KEY_MAX_MM` | 9 / 13 | clamp for alphabetic split key width |
| `SPLIT_ROW_MAX_MM` | 12 | split-mode row-height cap (vertical thumb sweep, 4 rows ≤ ~50 mm) |
| `REACH_ONE_HAND_MM` | 60 | comfortable one-thumb zone radius from a bottom-corner grip |
| `ONE_HAND_MAX_W_MM` | 60 | = `REACH_ONE_HAND_MM` — target one-hand block width. `oneHandWidth` clamps this to the available screen width, so a narrow phone simply renders full width. This is a **width clamp only, never a gate**: the one-hand mode is offered and applied on every phone regardless of screen width (issue #169). |
| `NUMPAD_KEY_MM` | 14 | tap-target size for anchored numpad keys (finger tap, not thumb grip) |
| `NUMPAD_ANCHOR_MAX_FRACTION` | 0.40 | anchored numpad never exceeds 40% of screen width |

## Feature A — tablet split fine-tune (7" / 11" / 13")

Applies wherever split already applies (Android split modes, iOS iPad split). Alphabetic and
per-IM layouts only — **numpad-based layouts never split** (see eligibility matrix).

Rule: each split half is capped by thumb reach, never wider than today's behavior.

```text
halfWidth   = min(currentHalfWidth, mm2units(SPLIT_HALF_MAX_MM))
keyWidth    = clamp(halfWidth / columnsInHalf, SPLIT_KEY_MIN_MM, SPLIT_KEY_MAX_MM)
centerGap   = keyboardWidth − keysInRow × keyWidth   (absorbs everything the halves gave up)
rowHeight   = min(currentRowHeight, mm2units(SPLIT_ROW_MAX_MM))   (split mode only)
```

Expected outcomes (landscape, 10-column row, 5 columns/half):

| Device | Width | Current half / key | New half / key |
|---|---|---|---|
| iPad mini 8.3" (7-8" class) | 1133 pt | 532 pt / ~16.6 mm keys | 413 pt / 13 mm keys (cap binds slightly) |
| iPad 11" | 1194 pt | 561 pt / ~21.6 mm keys | 338 pt / 13 mm keys |
| iPad 13" | 1366 pt | 642 pt / ~24.7 mm keys | 338 pt / 13 mm keys |
| 7" Android tablet portrait | ~600 dp | reach cap wider than half screen | unchanged (cap doesn't bind) |

So small tablets keep today's split; 11"/13" shrink dramatically toward thumb-typeable halves.

Platform notes:

- **Android**: change `mSplitKeyWidth` in `LIMEBaseKeyboard.java:1371` from the
  reserved-columns formula to the reach formula above (keep the reserved-columns value as the
  floor for the center gap so arrow-key injection still fits). `splitDistance` / `leftSplitBorder`
  math at `:1175-1301` is reused unchanged — it already derives from `mSplitKeyWidth`.
- **iOS**: in `makeSplitRow()` replace the fixed `splitGapFraction` with
  `halfFraction = min((1 − 0.06)/2, splitHalfMaxPt(sizeClass) / viewWidth)`; 6% stays as the
  minimum gap. Row-height cap goes through the existing `rowHeight(isPad:)` path in
  `LayoutMetrics.swift` gated on split mode. Height change flows through `applyHeight()`
  (`KeyboardViewController.swift:1618-1670`) — observe the #139 geometry-change rules in
  `docs/IOS_KB_HEIGHT.md`; no new height writers.

## Feature B — integrated phone portrait geometry (all phones)

Canonical pref `phone_portrait_keyboard_mode`: `0` standard (default), `1` split, `2` one-hand
left, `3` one-hand right. **Every phone, no screen-width gate** (issue #169) — applies to every
Android phone (`smallestScreenWidthDp < 600`) and every iPhone regardless of width. These are
mutually exclusive because split and one-hand both own the same horizontal key geometry and
candidate insets.

Behavior: the key area shrinks horizontally to `mm2units(ONE_HAND_MAX_W_MM)` and anchors to the
chosen edge. Horizontal-only transform: keyboard height, candidate bar width, and emoji panel are
untouched in v1 (avoids any #139-class frame churn on iOS). One-hand mode applies in portrait
only, replicating the iOS built-in one-handed keyboard; in landscape the keyboard renders full
width and the setting persists.

UI style — replicate the iOS built-in one-handed keyboard: the vacated strip shows a single
chevron arrow (`❮` when anchored right, `❯` when anchored left), vertically centered, pointing
toward the empty side. Tapping the arrow restores full width by setting
`phone_portrait_keyboard_mode = 0` (persisted — on iOS written to the hot store exactly like a
globe-menu change, so the choice survives keyboard restarts and relays back to the settings app).
The strip is otherwise plain keyboard background. Same style on both platforms.

Availability: shown and applied on **every phone**, with **no screen-width or physical-size
gate** (issue #169). The one-hand width helper (`ReachGeometry.oneHandWidth`) still clamps the
block to the available width, so on a narrow phone the block simply renders full width (empty
strip = 0, no chevron) while the mode is always honored. There is no separate availability helper.
Device class is the only distinction: Android tablets (`smallestScreenWidthDp >= 600`, ≈7"+) and every iPad use the
tablet split / numpad-anchor model instead and never show the phone controls.

Example: 6.7" phone, screen ~71 mm wide → key block ~60 mm (~85%), leaving a ~11 mm reach-relief
strip; 10-column key width drops to ~6 mm so the far column center lands inside the 60 mm
one-thumb zone. On a narrow 4.7" phone the 60 mm target exceeds the screen, so the block clamps
to full width and the mode is a no-op visually — but it is still offered and stored, never gated.

Scope on phone: one-hand applies to **all portrait layouts**, including numpad-based ones. Portrait
split applies only to split-eligible alphabetic/per-IM layouts; numpad layouts remain unsplit.
Landscape never reads the portrait mode: it uses `phone_landscape_split`, and numpad layouts remain
excluded. No precedence rule is needed because one keyboard instance reads only one canonical mode.

Platform notes:

- **Android**: migrate the legacy `one_hand_mode` / `split_keyboard_mode` pair once, then apply the
  integrated portrait mode at keyboard build time in `LIMEBaseKeyboard`
  (same shape as the split right-half shift: scale each key's width/x by
  `oneHandWidth / displayWidth`, then add `displayWidth − oneHandWidth` when anchored right).
  Recreate keyboards on pref change like `LIMEService.java:912/1042` does for split.
- **iOS**: apply in `KeyboardView` layout as a container inset (leading or trailing inset of
  `viewWidth − oneHandWidthPt`); `widthPercent`-driven key layout then needs no per-key changes.

## Feature C — tablet numpad anchoring (left / right / center / fit)

New pref `numpad_anchor`: `0` fit (default — current full-width behavior), `1` left, `2` right,
`3` center. Tablet/iPad only.

Applies to all numpad-based layouts:

- Android: `phone_simple.xml`, `computer_simple.xml`, `phone_number.xml` (restricted fields),
  `phone.xml` / `phone_shift.xml` (T9-style phone-IM keypads) — `LIMEKeyboardSwitcher.isNumpadXml`.
  Per-IM `*_number` layers are 10-column full-width layouts and stay ordinary (they split /
  one-hand like any other layout); only the true numpad grids above anchor.
- iOS: layout ids `phone*` and `computer_simple*` (`KeyboardViewController.isNumpadLayout`) —
  covering the restricted-field numeric layouts selected via `KeyboardTypePolicy.swift` and the
  feat#N02 computer-numpad keyboard-list entry.

Geometry (one hand tapping, device resting or held by the other hand — finger-tap targets, not
thumb-grip targets):

```text
numpadWidth = min(columns × mm2units(NUMPAD_KEY_MM), NUMPAD_ANCHOR_MAX_FRACTION × keyboardWidth)
```

With the existing 5-column layouts that is ~70 mm (≈364 pt on an 11"/13" iPad, ≈450 pt on an
iPad mini — the 40% clamp binds on the mini). `fit` bypasses all of this and keeps today's
rendering. Anchoring is the same inset/offset mechanism as Feature B, reused — left/right pin to
the respective edge, center splits the leftover evenly. Row height in anchored modes is capped at
`NUMPAD_KEY_MM` so keys stay roughly square.

Split exclusion: when the active layout is numpad-based, the split pref is ignored entirely
(Android: skip the split branch in `LIMEBaseKeyboard` for these XMLs; iOS: skip `makeSplitRow()`
for these layouts). `numpad_anchor` is the only geometry pref that applies to them on tablets.

## Layout eligibility matrix

| Device / layout | Alphabetic & per-IM layouts | Numpad-based layouts |
|---|---|---|
| **Any phone** (Android `smallestScreenWidthDp < 600`, every iPhone) | portrait `phone_portrait_keyboard_mode`; landscape `phone_landscape_split` | portrait standard/one-hand only; landscape full width |
| **Android tablet** (`smallestScreenWidthDp >= 600`, ≈7"+) / **iPad** | `split_keyboard_mode` (reach-capped, Feature A) | `numpad_anchor` (Feature C); split ignored |

There is **no phone-width gate**: every phone shows and applies the phone controls. The only
distinction is device class (phone vs Android tablet ≥600dp vs iPad). Phones never expose
`split_keyboard_mode` / `numpad_anchor`; tablets/iPads never expose the phone controls.

Phone portrait split remains available for backward compatibility. The integrated mode makes it
mutually exclusive with one-hand. Phone landscape split is independent. A portrait split user may
therefore keep the legacy two-thumb layout, while a one-hand user chooses an anchored side without
creating a conflicting pair of enabled preferences.

## Preferences and surfaces

Shared Android/iOS canonical geometry prefs exposed in app settings and the in-keyboard menu:

| Key | Values | Shown when |
|---|---|---|
| `phone_portrait_keyboard_mode` | 0 standard / 1 split / 2 left / 3 right | every phone (no width gate) |
| `phone_landscape_split` | false / true | every phone (no width gate) |
| `split_keyboard_mode` | 0 off / 1 always / 2 landscape-only | Android tablet ≥600dp / iPad only |
| `numpad_anchor` | 0 fit / 1 left / 2 right / 3 center | tablet / iPad |

Presentation: phone portrait uses one integrated list/segmented control named **直向鍵盤模式**
(標準/分離/靠左/靠右). Phone landscape uses a binary **橫向分離鍵盤** control.
`numpad_anchor` uses the same segmented style with four options.

Keyboard-side menu exclusivity — split and anchor are never shown together; the menu keys off
the **currently active layout**, not just the device:

- Ordinary (alphabetic / per-IM) layout active → tablet: 分離鍵盤 (tri-state); phone
  portrait: 直向鍵盤模式 (standard/split/left/right); phone landscape: 橫向分離鍵盤
  (off/on). Hide 數字鍵盤位置.
- Numpad-based layout active → show 數字鍵盤位置 only on tablets and hide split. On phones there
  is no `numpad_anchor`: portrait exposes the integrated mode without the split choice, and
  landscape stays full width because numpads are split-ineligible.

The settings app shows all prefs applicable to the device (it has no "active layout" context);
the exclusivity rule is a keyboard-menu rule only.

- **Android**: device-scoped entries in `res/xml/preference.xml`; accessors and one-time migration
  in `LIMEPreferenceManager`; integrated segmented rows in `handleOptions()`, gated per the table
  above; applied
  on dialog dismiss via `applyGeometryChangeInPlace()` — an in-place rebuild
  (`rebuildCurrentKeyboard()`), NOT `handleClose()`, which would dismiss the IME.
- **iOS**: use the same key names and values as Android. Both phone keys are **cold/hot prefs**:
  App Group defaults + LimeSettings UI, hot via `seededHotInt`, app→keyboard `PrefInbox` drain,
  and keyboard→app relay writeback. iPhone uses the same portrait and landscape choices as an
  Android phone. iPad continues to use `split_keyboard_mode` and `numpad_anchor`.

User-facing names:

- `phone_portrait_keyboard_mode` → **直向鍵盤模式** (標準/分離/靠左/靠右), phones only.
- `phone_landscape_split` → **橫向分離鍵盤** (關閉/開啟), phones only.
- `split_keyboard_mode` → **分離鍵盤**, tablets/iPads only.
- `numpad_anchor` → **數字鍵盤位置** (滿版/靠左/置中/靠右), tablets only; 滿版 is the
  fit/current-style default.

### Migration and portability

The old pair is migration input only on phones:

```text
split_keyboard_mode = 0 off / 1 always / 2 landscape-only
one_hand_mode       = 0 off / 1 left / 2 right
```

When `phone_portrait_keyboard_mode` is absent, migrate once with deterministic precedence:

1. old one-hand left/right → new left/right
2. otherwise old split `always` → new portrait split
3. otherwise → standard

When `phone_landscape_split` is absent, Android derives it from old split being nonzero. iOS
defaults it off because iPhone split did not previously exist. Migration writes both canonical
keys even when they hold defaults, so key presence is the migration marker.

Phone changes never rewrite `split_keyboard_mode`: that key belongs to the tablet/iPad profile.
Backups include all four canonical keys. Phone keys remain dormant on tablets, and tablet keys
remain dormant on phones, so restoring the same backup between form factors preserves both
profiles without trying to map one-hand geometry onto a tablet.

## Chrome alignment with the anchored block

When the key block is horizontally anchored (one-hand mode, numpad anchor):

- **Candidate bar**: its usable area aligns with the shifted key block — same leading/trailing
  insets, chrome buttons included — so candidates stay inside the same reach zone as the keys.
  Android reads the insets off the rendered keyboard (`applyHorizontalAnchor` records them,
  `CandidateInInputViewContainer.updateCandidateViewWidthConstraint` applies them); iOS drives
  the bar's stored leading/trailing constraints from the same insets computed for
  `setHorizontalAnchor`.
- **Expanded candidates panel**: same insets. Android sizes/positions the popup window to the
  anchored width and passes it to `CandidateExpandedView.setContentWidth` for row wrapping (the
  expanded view lives inside the popup hierarchy and cannot read the insets itself); iOS mirrors
  the bar's anchor constants onto the panel's leading/trailing constraints and wraps cells to
  the inset width in `reloadExpandedCandidates`.

## Non-goals (v1)

- Emoji panel and emoji search header stay full width in v1.
- No per-orientation memory (one setting, both orientations; split already handles
  landscape-only as a mode value; the phone menu's orientation gating changes which control is
  shown, not what is stored).
- No new iPad email/URL layouts and no change to the #74 URL-field policy — this work is
  geometry only.

## Acceptance criteria

- 11" and 13" iPad landscape split halves are ≤ 66 mm physical with 13 mm keys; iPad mini split
  changes only marginally; no regression when split is off.
- Android xlarge-land split keys land in the 9–13 mm band (verify at 160/240/320 dpi buckets).
- The integrated portrait mode offers standard/split/left/right as mutually exclusive choices on
  Android and iOS phones. All four choices are available on **every** phone regardless of screen
  width — there is no gate. On a narrow phone the one-hand block clamps to full width (no visible
  shrink) but the choice is still offered, stored, and honored.
- Device class is the only distinction: Android tablets (`smallestScreenWidthDp >= 600`) and every
  iPad use the tablet split / numpad-anchor model and never show the phone controls.
- One-hand empty strip shows the restore chevron (iOS built-in style); tapping it sets
  `phone_portrait_keyboard_mode = 0`, persists through each platform's normal preference transport,
  and restores full width immediately.
- Keyboard-side menu exclusivity: phone portrait uses one integrated mode control; tablet/iPad
  numpad layouts show anchor and never split.
- Phone portrait and landscape controls use the same names, values, and persisted keys on Android
  and iOS. Tablet/iPad split and numpad preferences remain independent and portable.
- Anchored numpad on iPad: left/right/center/fit all render, keys ≈14 mm square, `fit`
  pixel-identical to today.
- Numpad-based layouts never render split regardless of `split_keyboard_mode`.
- iOS: both prefs behave as cold/hot — change in LimeSettings reaches a live keyboard via
  PrefInbox; change in the globe menu persists in the hot store and relays back; verified with
  Full Access off (per `docs/IOS_FULL_ACCESS.md` transport rules).
- iOS keyboard height honors `docs/IOS_KB_HEIGHT.md` rules; no #139-style frame churn when
  toggling any of the new modes.

## Task checklist

Phase 1 — shared geometry
- [x] Android: mm→px helper + named constants (`ReachGeometry.java`, unit-tested)
- [x] iOS: mm→pt table + constants (`ReachGeometry` in `LayoutMetrics.swift`, keyed on `IPadSizeClass` / iPhone; `ReachGeometryTests`)

Phase 2 — Feature A (tablet split fine-tune)
- [x] Android: reach-capped `mSplitKeyWidth` (`ReachGeometry.splitKeyWidth`); reserved-columns kept as center-gap floor
- [x] iOS: computed `halfFraction` in `makeSplitRow()`; split row-height cap; height via `applyHeight()`
- [x] iOS partition parity with Android (`SplitPartition`): floor(columns/2) border, space cloned to both halves
- [ ] Verify reach-cap table on iPad mini / 11" / 13" simulators and an Android xlarge AVD

Phase 3 — Feature C (numpad anchoring, tablets)
- [x] `numpad_anchor` pref both platforms (Android pref.xml + manager; iOS cold/hot clone of split wiring)
- [x] Anchor/inset rendering + split exclusion for numpad layouts, both platforms (exclusion set:
      `phone_simple`, `computer_simple`, `phone_number`, `phone`, `phone_shift`)
- [x] Menu entries: Android options panel row; iOS globe menu segmented (iPad-gated), shown
      only when the active layout is numpad-based (split row hidden in that case)

Phase 4 — Feature B original v6.1.33 implementation (superseded by Phase 6)
- [x] Original separate `one_hand_mode` pref on both platforms; replaced by the integrated
      all-phone model in Phase 6
- [x] Width scale + anchor at keyboard build (Android) / container inset (iOS); phone split is
      landscape-only so portrait always belongs to one-hand
- [x] Restore chevron in the empty strip (both platforms): tap → `one_hand_mode = 0`, persist +
      relay, rebuild in place (keyboard stays up)
- [x] Menu entries (phone-gated), segmented style matching the split row; orientation-gated on
      phones (portrait: one-hand only; landscape: binary split only)
- [x] Candidate strip + expanded candidates panel follow the anchor insets (both platforms)

Phase 5 — verification
- [x] Android phone AVD (Pixel 9 Pro XL, API 37): menu gating both orientations, one-hand R +
      chevron restore in place, landscape split with dual space, anchored expanded popup —
      screenshot-verified 2026-07-18
- [ ] Android tablet AVD: split reach-cap + numpad anchors
- [x] iOS: build gate + unit gate (`ReachGeometryTests` incl. split-partition tests) PASS
- [ ] iOS: `ios-visual-verify` on iPhone and iPad simulators, Full-Access-off pref transport check

Phase 6 — #169 integrated phone model correction (width gate removed — every phone, no gate)
- [x] Shared policy `PhoneKeyboardModePolicy` (Android + Swift), RED/GREEN unit tests incl.
      narrow-phone no-gate proof (`phoneControlsApply`, `oneHandAnchor` width-independent)
- [x] Android: RED/GREEN migration tests (legacy split-only → portrait split + landscape split;
      one-hand wins; landscape-only ≠ portrait split; conflicting/default); tablet split key never
      rewritten (`PhoneKeyboardModePolicyTest`, `PhoneKeyboardPreferenceMigrationTest`)
- [x] Android: replaced phone settings/menu rows with 直向鍵盤模式 (4-seg) and 橫向分離鍵盤
      (binary); `split_keyboard_mode` gated to tablets; **no width gate** anywhere
- [x] Android: removed `ReachGeometry.oneHandAvailable` gating from render (`LIMEKeyboardSwitcher`),
      menu (`handleOptions`), and settings visibility (`LIMEPreference`)
- [x] iOS: matching canonical keys/values, migration (legacyPhoneSplitSupported: false),
      LimeSettings UI (no width gate), hot/inbox/relay transport (incl. FA-off text relay
      `pp=`/`pls=` + `RelayPrefSync.apply`), iPhone split/one-hand rendering + chevron restore
- [x] Shared backup manifests preserve phone and tablet/iPad profiles without cross-writing
      (Android `PreferenceBackupAdapter`, iOS `PreferenceBackupAdapter` + hot→cold flush + restore push)
- [ ] Android phone device verification and iPhone/iPad Xcode Cloud/unit verification (on-device
      only; Linux CI ran Android unit tests + iOS source contracts)
