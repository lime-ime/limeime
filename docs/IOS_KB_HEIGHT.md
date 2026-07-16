# iOS Keyboard Height: Reporting Model and Geometry-Change Rules

Design doc for how the LIME iOS keyboard extension declares its height, how iOS turns that into the keyboard frame that host apps see, and the rules for responding to geometry changes. Distilled from the #139 investigation (see `#139_ISSUE.md` for the full evidence trail); all numbers below were measured on a physical iPhone 17 Pro Max test device, default `keyboard_size`.

## 1. Height model inside the extension

`KeyboardViewController.applyHeight()` is the single choke point for height. Nothing else moves height constraints.

```text
keysHeight  = KeyboardView.preferredHeight        (sums real per-row heights)
barH        = activeCandidateBarHeight
totalHeight = emojiSearchHeaderHeight + barH + keysHeight
              (emoji panel: max(totalHeight, panel preferred height))
kbTarget    = totalHeight - emojiSearchHeaderHeight - barH
```

The extension view's height is **content-driven**: there is no explicit `view.heightAnchor` constant. The subview chain defines it:

```text
candidateBar.top      == view.top (+ emoji search header offset)
candidateBar.height   == barH                      (constant, updated in applyHeight)
keyboardView.top      == candidateBar.bottom
keyboardView.height   == kbTarget                  (constant, .required, updated in applyHeight)
keyboardView.bottom   == view.bottom
⇒ view height         == header + barH + kbTarget == totalHeight
```

After moving a constant, `publishKeyboardHeightToUIKit()` calls `setNeedsUpdateConstraints()` on `view` and `inputView`. That is the entire publication API available to an extension — everything else is iOS-internal.

History: the height constraint originally lived on `view` directly (`view.heightAnchor == totalHeight`, priority 999). The content-driven form is what shipped with the #139 fix; both forms measured identically in the rotation probe, so the chain is kept mainly because it matches how peer keyboards size themselves and removes one iOS-vs-us constraint fight on `view`.

## 2. How iOS consumes the declared height

- iOS installs its own **required** constraint on the extension view, identifier `UIView-Encapsulated-Layout-Height` ("enc"). The view's on-screen size is whatever *enc* says; our constraint is only an input that iOS reads and copies into enc, usually within one layout pass — but **iOS's copy is asynchronous and iOS-paced**. During rotation, enc holds the old value until after iOS has already notified hosts.
- The keyboard frame reported to hosts is **not** the view frame. iOS adds its own bottom band (home-indicator / safe-area region): portrait 440×956 → view 312 tall is reported as **404** (top y=552); landscape 956×440 → view 232 is reported as **253**. The extension never sees or controls this band; do not try to compensate for it.
- Hosts learn the frame two ways:
  - **`keyboardLayoutGuide`** (modern hosts) — tracks the true frame continuously and self-heals after rotation.
  - **`keyboardWillChangeFrame` / `keyboardDidChangeFrame` notifications** (LINE-style hosts) — a host that caches its bottom inset from the notification is only as correct as the last notification it received. This is the fragile path and the one #139 broke.
- On a fresh **presentation** (keyboard show triggered by a field becoming first responder), iOS pre-computes the reported frame from the *declared* height before the view even finishes settling — presentation is always correct.
- An **in-place keyboard switch** (globe key, same input session) is NOT a presentation: iOS sizes the incoming keyboard correctly but notifies the host of **nothing** — see §5.

## 3. THE rule: never move height inside a rotation transaction

Measured failure mode (#139): any height-constant change made while a rotation transition is in flight gets baked into iOS's keyboard-frame notification at a stale or interpolated value, and iOS applies the extension's new height *after* its last notification, **silently** — notification-based hosts keep the stale inset until the keyboard is dismissed and reopened. Ten variants of "change the height mid-rotation, then try to force a corrective notification" all failed identically (jiggles at every timing, fresh constraints, `.required` priority, `allowsSelfSizing`, coordinator-timed resizes, writing enc directly — the last one crashes; see §5).

Stationary height changes — keyboard_size, emoji panel open/close, expanded candidates, arrow row, layout switches while the device is not rotating — go through the same `applyHeight()` and are reported to hosts **correctly**. Only transaction-internal changes are swallowed.

Therefore:

```text
viewWillTransition(to:with:)   → rotationSettling = true
applyHeight() while settling   → does NOT move the height constant (everything else proceeds)
coordinator completion + 0.3 s → rotationSettling = false; applyHeight() once
                                → plain stationary resize → correct notification fires
```

The visible behavior is intentional: after rotating, the keyboard keeps the previous orientation's height for ~0.3 s, then snaps to the correct height. **The snap is the host notification.** Do not "fix" the snap by moving the height change earlier — that recreates #139.

Edge case: a second rotation started inside the 0.3 s window can momentarily apply mid-rotation; that rotation's own deferred apply self-heals it one beat later. Accepted.

## 4. Rules for responding to geometry changes

| Change | When to apply | Path |
| --- | --- | --- |
| Rotation (orientation height difference) | 0.3 s AFTER the coordinator completes | `rotationSettling` defer in `viewWillTransition` |
| keyboard_size preference | immediately | `applyHeight()` (stationary) |
| Emoji panel / search header / expanded candidates | immediately | `applyHeight()` (stationary) |
| Row-count / layout change | immediately | `applyHeight()` (stationary) |
| iPad split mode | immediately (width-only unless rows change) | `viewWillLayoutSubviews` |

Supporting rules:

- Orientation is detected from `UIScreen.main.bounds` (view bounds are useless for this — the extension view is always wider than tall).
- `viewWillLayoutSubviews` may call `applyHeight()` freely; the settling gate inside `applyHeight()` makes that safe during rotations.
- Never call `layoutIfNeeded()` recursively from `viewWillLayoutSubviews`.
- `keyboard_size` stays authoritative for row sizing; never shrink or cap a tall layout to hide host coverage (#139 non-fixes still apply).

## 5. The in-place keyboard-switch gap — FIXED by the attach overshoot

Switching keyboards with the globe key while a field stays focused reuses the existing input session — and iOS treats the swap completely differently from a presentation. Probe-measured on the physical test iPhone (2026-07-16):

| Transition | Host notified? |
| --- | --- |
| Fresh open with LIME (field becomes first responder) | ✅ `WillShow inset=404`, correct |
| LIME → Apple keyboard | ✅ `WillChangeFrame inset=345/335`, correct |
| Apple keyboard variant change (autocorrect bar, 335↔345) | ✅ notified |
| **Apple → LIME (switch-in)** | ❌ **total silence** — no notification, no layout-guide update |

During the switch-in, iOS *does* read LIME's constraint and sizes the keyboard window correctly (LIME renders full height) — but silently. The host keeps the previous keyboard's inset (Apple ≈335 vs LIME's true 404 ⇒ ~69 pt of host content covered; in LINE that is the entire composer). External research confirms this is documented platform behavior: keyboard notifications fire on first-responder/input-session changes, **never on globe switches to a custom keyboard**.

After the swap, the height pipeline (§2) is **dormant for the rest of the session**. Three channels were tested post-appearance and all were ignored (enc frozen, view pinned, zero notification):

1. a real height-constraint dip held 200 ms behind the settling gate (note: an ungated dip gets reverted by the next `applyHeight` layout pass before it can render — gate it, though it changes nothing here);
2. iOS's `enc` constraint (frozen — never re-derived);
3. `preferredContentSize` (the remote-view-controller sizing channel — keyboards don't use it).

“Report the system keyboard height first, then grow and re-report” does not work either: the grow lands in the same dormant pipeline (measured — same silence as the dip), we cannot know the outgoing keyboard's height (335/345 vary by device/mode), a failed grow leaves LIME *stuck short with clipped rows* (worse than coverage, and equivalent to the forbidden shrink), and fresh presentations would regress to a visible grow-flash whose first notification naive hosts would cache.

**The extension cannot even detect the covered state.** Covered and correct look identical from inside: LIME's view, constraint, enc and window frames are all right in both. The staleness is the host's cached bottom inset — a value in the host process, invisible across the sandbox. No lifecycle signal distinguishes a switch-in from a fresh open on the extension side (both run full `viewDidLoad`/`viewWillAppear`/`viewDidAppear` with identical values).

Why peers appear unaffected: they default to the **system keyboard height**, so their stale inset equals their real height — the gap exists but is 0 pt. LIME is legitimately taller, so the gap is visible. Do not shrink LIME to match (standing non-fix).

Recovery: the user taps any text field (new input session → fresh `WillShow`) or dismisses/reopens the keyboard. Disposition: known limitation; a strong Apple Feedback candidate (Apple→Apple switches notify, Apple→third-party does not — a clean asymmetry with a minimal repro).

**Host-side escape hatch (measured):** iOS fires `UITextInputMode.currentInputModeDidChangeNotification` on every globe switch, and `keyboardLayoutGuide` — stale at that instant — reads the **correct** new frame ~0.5 s later after a forced layout (`guideTop` 611→552 measured, both switch directions). Hosts can self-heal by re-reading the guide on that signal, or by resigning/re-acquiring first responder (forces a fresh `WillShow`). This is host-side only; the extension still has no channel.

### The fix: attach overshoot (2026-07-16, probe- and LINE-verified)

System-log analysis of a working Gboard switch-in vs a failing LIME one exposed the machinery:

- The host announces a switched-in custom keyboard with a **provisional frame** and corrects it **only on a post-attach resize edge** of the keyboard's remote proxy view (Gboard's proxy grows 243→274 at +85 ms → corrected cascade).
- iOS sizes the attaching view from **its own memory** of the keyboard's height (ignoring the declared constraint) and **pins it after the transaction settles** — so the only reachable edge is a target **above the remembered height**, applied **while the attach transaction is still open**.

Implementation (`KeyboardViewController`): `attachOvershoot` — at `viewDidAppear` (mid-attach) applyHeight targets `kbTarget + attachOvershootDelta` (20 pt); the first layout pass with the view rendered at the overshoot size restores `kbTarget`; a 0.5 s fallback guarantees the keyboard never sticks tall. Both edges are honored and announced (`WillChangeFrame` 424 → 404, probe host ends `overlap=-4`; LINE composer stays visible on Apple→LIME switch). Cosmetic cost: a brief ~20 pt bounce on every keyboard appearance (Δ tunable, untested below 20).

Rules that remain true (probe-falsified alternatives): below-remembered targets are masked by the memory restore; post-settle changes of any kind are pinned; `enc`, `preferredContentSize`, `allowsSelfSizing`, and early declaration in `viewDidLoad` are all dead channels on this path. The host-side escape hatch below remains valid guidance for hosts (e.g. MAUI) on older LIME builds.

## 6. What NOT to do (all probe-falsified, #139 attempts 1–10)

1. Republish (`setNeedsUpdateConstraints`) right after rotation — no notification results.
2. 1 pt height jiggle, at any timing (settle-synced, +350 ms, render-forced with `layoutIfNeeded`) — the view is enc-pinned; either nothing changes or the change is swallowed.
3. Deactivate/recreate the height constraint + `invalidateIntrinsicContentSize()` — iOS does not re-derive.
4. `inputView.allowsSelfSizing = true` — Apple DTS: for input *accessory* views, not keyboards; also caused a 772 pt show-time overshoot.
5. Resize inside `coordinator.animate(alongsideTransition:)` — enc pins the view at the old height through the transition regardless.
6. Raise our constraint to `.required` to out-rank enc — AutoLayout resolves the required-vs-required conflict in the system's favor during rotation.
7. **Write `enc.constant` yourself.** From a layout pass this loops layout and the watchdog kills the extension (crash, reproduced). From a rotation callback it lands but is ignored — the notification value is not read from enc at fire time.

## 7. Diagnostics

The probe used for the #139 investigation was **stripped from the codebase after verification** (it was DEBUG-only, but was removed entirely so no debug UI exists to leak). Restoration snippets live in `.claude/txt/139-geometry-probe-restoration.md`; re-add them when instrumenting any future height-path change. The probe consists of:

- `GeoProbe` (Shared) — append-only log in the app-group container; `readAll()`/`clear()` from the app.
- `KeyboardViewController.geoDump(_:)` — one-line geometry snapshot: screen/view/input sizes, `preferredHeight`, our constraint constant, iOS's **enc constant**, rendered window frames. Hook into `applyHeight`, `viewWillLayoutSubviews`, `viewWillTransition`, the deferred post-rotation apply, and (for switch-path work) `viewWillAppear`/`viewDidAppear`.
- `GeometryProbeHostVC` + viewer (LimeSettings 資料庫 tab) — a LINE-style host whose field is positioned from a **cached `keyboardWillChangeFrame` inset**, not the layout guide, so it reproduces notification staleness objectively (readout: `overlap > 0` ⇒ ❌ COVERED).

Verification recipe for any future height-path change: restore the probe → 清除 → 開啟主機探針 → switch to LIME → rotate portrait → landscape → portrait → wait 1 s → expect ✅ CLEAR and a `HOST WillChangeFrame` with the settled inset (404 portrait on the physical test iPhone) after the deferred post-rotation apply.

## 8. References

- `#139_ISSUE.md` — the investigation this doc distills: evidence, all ten failed attempts, external research (Apple DTS thread 799003, archagon writeup), and the fix verification (probe + LINE).
- `IOS_KB_GAP.md`, `LAYOUT_PARAM.md` — related keyboard metrics docs.
