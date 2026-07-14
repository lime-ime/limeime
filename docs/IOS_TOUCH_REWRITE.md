# iOS LimeKeyboard — Touch-Layer Rewrite Plan (Flint / Glide + Feel Parity with Android)

## 0. Goal-mode execution contract (read first)

This section makes the plan runnable end-to-end by an autonomous agent on macOS. Everything below §0 is rationale/design; §0 is the contract.

### GOAL (done = all true)

1. iOS keyboard supports **flint**: pressing a key and sliding to a neighboring key releases the old key and presses the new one; releasing commits the key currently under the finger.
2. Tap input has **proximity correction** (near-miss taps resolve to the nearest key) — parity with Android's `ProximityKeyDetector`.
3. All existing key behaviors (shift, backspace repeat, long-press popup, iPad dual-row, space caret drag, candidate bar) **unchanged**.
4. Every gate below is green.

### Environment (goal mode requires macOS — cannot run on the authoring Windows box)

- Xcode with the iOS 26.5 simulator (**iPhone 17 Pro**, per [docs/IOS_MISS_KEY.md](IOS_MISS_KEY.md)).
- Repo constants (verified): project `LimeIME-iOS/LimeIME.xcodeproj`; app target/scheme **`LimeIME`**; keyboard extension target **`LimeKeyboard`**; unit-test target **`LimeTests`**; UI-test target **`LimeUITests`**.

### Commands (run from repo root)

```bash
DEST='platform=iOS Simulator,name=iPhone 17 Pro,OS=26.5'
PROJ='LimeIME-iOS/LimeIME.xcodeproj'

# GATE 1 — build
xcodebuild -project "$PROJ" -scheme LimeIME -destination "$DEST" build

# GATE 2 — KeyDetector unit test (add to LimeTests target)
xcodebuild test -project "$PROJ" -scheme LimeIME -destination "$DEST" -only-testing:LimeTests/KeyDetectorTests

# GATE 3 — flint XCUITest (add to LimeUITests target) + existing regression UI tests
xcodebuild test -project "$PROJ" -scheme LimeUITests -destination "$DEST"
```

(If the `LimeIME` scheme's test action does not list `LimeTests`/`LimeUITests`, add them to the scheme first — that is a one-time scheme edit, not a code change.)

### Gates & pass criteria (machine-checkable)

| Gate | Pass criterion |
|---|---|
| G1 build | `** BUILD SUCCEEDED **`, and **no new compiler warnings** on files this loop touched |
| G2 unit | `KeyDetectorTests` all pass — asserts: key-center→self, gap-point→nearest within threshold, sub-hysteresis wobble does **not** switch key |
| G3 flint XCUITest | new `FlintUITest` passes: synthesized drag `q→w→e→r` types **`r`** (release key), and a sub-key-width wobble types the **start** key once (no double-fire); existing `LimeUITests` still pass |
| G4 chord XCUITest | new `ChordUITest` passes: **shift-hold** (hold shift, tap `a` → `A`, shift stays active while held); **rollover** (press `a`, press `s` before releasing `a` → types `as` in order); a held modifier that drifts does **not** flint |
| G5 popup-slide XCUITest | new `PopupSlideUITest` passes: long-press a key with alternates, slide into the popup, release on the 2nd alternate → that alternate is typed (§4.2 #9); swipe-left across the keyboard → backspace, swipe-right → commit candidate (§4.2 #12) |

### Per-phase Definition of Done (advance only when its DoD is green)

- **P0** `KeyModel` + `KeyDetector` exist; G2 green. No runtime change (G1 + existing G3 green).
- **P1** `KeyTouchLayer` owns the basic tap; per-key `UIButton` press targets disabled; **shift-hold chord + rollover preserved** (§4.1 — `ShiftHoldTouchPolicy` fed from live trackers), `isMultipleTouchEnabled` on the layer; new `ChordUITest` added; G1+G2+G3(existing)+G4 green; proximity correction observable in G2.
- **P2** flint live; **modifier touches pinned (no flint, §4.1 rule 1)**; new `FlintUITest` added and green; G1+G2+G3+G4 all green.
- **P3** long-press/repeat/dual-row/space re-hosted onto the layer; **slide-into-popup select (§4.2 #9)** and **swipe-left/right commands (§4.2 #12)** implemented; **repeat cancelled when 2+ non-modifier keys down (§4.1 rule 3)**; new `PopupSlideUITest` added; G1+G2+G3+G4+G5 green with the full regression sweep (§7).
- **P4** dead `UIButton`/gesture plumbing **and the five scattered `isMultipleTouchEnabled` flags** removed; G1+G2+G3+G4+G5 green; accessibility elements present (see §6).

### Failure & stop policy

- A red gate → fix in place and re-run **that phase's** gates; do not advance.
- **Per CLAUDE.md rule 7:** after **3** failed attempts on the same gate failure, stop blind-retrying — research the API/error, then change approach. Do not thrash.
- **Never** blank-and-rewrite a source file (CLAUDE.md rule 3); targeted edits only; track edits for manual revert (rule 4).
- **Encoding:** `.swift` is BOM-tolerant → save UTF-8 **with** BOM (CLAUDE.md rule 5).

### Autonomy boundary — what goal mode may NOT self-certify

Goal mode runs entirely in the **simulator**. It **cannot** verify the subjective "feels less clunky than Android" complaint, the **physical haptic**, or real-finger drop-rate — those need the **WJIP17 device** and a human (LimeIME scheme + force-refresh protocol, IOS_MISS_KEY.md §verification). When all gates are green, goal mode must report: *"simulator gates green; on-device feel/haptic verification is the remaining human step,"* and must **not** claim the feel complaint is resolved.

## 1. Why this exists

Two long-standing complaints about the iOS soft keyboard, both traced to the **same root cause**:

1. **Typing feels "clunky" vs Android** — presses land slightly off-key or during heavy main-thread work and are silently dropped (see [docs/IOS_MISS_KEY.md](IOS_MISS_KEY.md) for the missed-key thread).
2. **Flint is missing** — "flint" = press a key, slide the finger to a neighboring key, and have the selection follow the finger (release old key / press new key as you cross the boundary). Android has this; iOS has **none**.

Neither is a bug in a specific handler. Both fall out of an architectural choice made early in the iOS port, and neither can be fixed incrementally without changing it.

## 2. Root cause — who owns the touch stream

| | **Android (has flint, forgiving)** | **iOS (no flint, clunky)** |
|---|---|---|
| Touch owner | **One** custom view owns the whole `MotionEvent` stream — `LIMEKeyboardBaseView.onTouchEvent()` | **N** individual `UIButton`s, each captures only its own touch — `.touchDown` / `.touchUpInside` targets ([KeyboardView.swift](../LimeIME-iOS/LimeKeyboard/KeyboardView.swift), key build ~L774–892) |
| Hit-testing | App-owned: `ProximityKeyDetector.getKeyIndexAndNearbyCodes()` maps (x,y)→key with a grid prefilter **and proximity correction** | UIKit-owned: each button's rectangular frame only; no `hitTest` override, no proximity forgiveness |
| Per-finger state | `PointerTracker` per pointer id holds a **"current key"** and switches it on move | none — a touch belongs to the button it started on until release; UIKit will not hand it to a neighbor |
| Cross-key slide | `onMoveEvent` → release old / press new (this *is* flint) | impossible in this model: sliding off button A yields `.touchUpOutside` (a cancel), never a press on B |

**The single differentiator is touch ownership.** As long as each key is its own `UIButton` doing its own hit-testing, flint cannot be expressed and near-misses cannot be recovered.

## 3. Gap analysis (behavior-by-behavior)

Reference: Android `PointerTracker.java` / `ProximityKeyDetector.java` / `LIMEKeyboardBaseView.java` under `LimeStudio/app/src/main/java/org/limeime/keyboard/`; iOS `KeyboardView.swift`.

| Behavior | Android | iOS today | Gap |
|---|---|---|---|
| Key hit detection | `onTouchEvent` + `KeyDetector` on app-owned coords | per-key `UIButton` rectangle | **Architecture** |
| **Flint / glide to neighbor** | `onMoveEvent` re-detects key, `onRelease(old)` + `onPress(new)`, sets `mIsInSlidingKeyInput` (`PointerTracker.java:324–384`) | **absent** | **Missing feature** |
| Slide hysteresis / anti-jitter | `isMinorMoveBounce()` w/ `mKeyHysteresisDistanceSquared` (`:456`) | n/a | Missing |
| Proximity / fat-finger correction | `mProximityThresholdSquare` + `getNearestKeys()` grid (`ProximityKeyDetector.java:45`) | none — must hit the frame | **Feel gap** (contributes to "clunky") |
| Serialized multi-pointer | one view sequences all pointers via `PointerTracker`s | `isMultipleTouchEnabled=true` workaround (patches a symptom of the UIButton model, see IOS_MISS_KEY.md) | Feel gap |
| Long-press → popup keyboard | `startLongPressTimer` → `openPopupIfRequired` (`:497`, `:1491`) | `UILongPressGestureRecognizer` on the button (~L838) | Re-host needed |
| Key repeat (delete/arrows) | repeat timer in `PointerTracker.onDownEvent` (`:314`) | `Timer` in `keyDown` (~L1256) | Re-host needed |
| Same-key slide (iPad dual-row) | n/a | `UIPanGestureRecognizer` (~L1365) | Re-host needed |
| Space-bar caret drag | `handleSpaceCaretMove()` (`LIMEKeyboardView.java:146`) | `SpaceKeyButton.touchesMoved` (~L1470) | Fold into new layer |
| Pressed highlight / preview | `Key.onPressed()` + `showKey()` popup | `backgroundColor` flip in `keyDown`; preview via delegate (~L1216, KeyboardViewController ~L2928) | Re-drive from detector |

**Feel gap ("clunky") = proximity correction absent + touch-drop under main-thread load.** The drop side is partly mitigated in IOS_MISS_KEY.md; the proximity side has never existed on iOS. This rewrite closes both because the new layer owns hit-testing.

## 4. Target architecture

Mirror Android's model. Keys stay as views for **rendering only** — they stop being the touch owners.

```
KeyboardView (or a dedicated full-bleed KeyTouchLayer on top of the rows)
  ├─ overrides touchesBegan / touchesMoved / touchesEnded / touchesCancelled
  ├─ KeyDetector.keyAt(point) -> KeyModel?         // frame test + proximity threshold
  ├─ per-UITouch TouchTracker { currentKey, downKey, isSliding, longPressTimer, repeatTimer }
  └─ fires existing callbacks (didPress / fireHaptic / showPreviewFor / didLongPress)
       from the tracker instead of from UIButton targets
```

New iOS pieces (each maps to a named Android piece):

| New iOS type | Android analog | Responsibility |
|---|---|---|
| `KeyDetector` | `ProximityKeyDetector` | `keyAt(CGPoint) -> KeyModel?`, frame test + `proximityThreshold`, hysteresis dead-zone |
| `TouchTracker` (per `UITouch`) | `PointerTracker` | holds `currentKey`; on move re-detects, fires release-old/press-new; owns long-press + repeat timers |
| `KeyTouchLayer` | `onTouchEvent` dispatch in `LIMEKeyboardBaseView` | routes each `UITouch` to its `TouchTracker` by identity |
| `KeyModel` | `Key` | frame + codes + primary/secondary glyph + flags (repeatable, popup, dual-row, space) |

Key views remain `UIView`/`UILabel` for drawing and highlight; they no longer register control-event targets or gesture recognizers for the main press path.

### 4.1 Multi-touch & modifier chords (shift-hold + tap, key rollover)

Multi-touch is **not a special case** in the new model — it is the reason to adopt it. `touchesBegan/Moved/Ended` deliver a `Set<UITouch>`, each `UITouch` has stable identity, so **one `TouchTracker` per `UITouch`** = Android's one `PointerTracker` per pointer id. Concurrent fingers are tracked independently by construction.

This directly covers the two chorded cases:

- **Shift-hold + tap** (finger A holds shift, finger B taps a letter → shifted letter, shift stays active while A stays down). Today this rides on `KeyboardView.updateShiftHoldTracking()` ([KeyboardView.swift:1296–1312](../LimeIME-iOS/LimeKeyboard/KeyboardView.swift#L1296)), which counts `event.allTouches` via `ShiftHoldTouchPolicy.isShiftStillHeld(...)` and notifies `didUpdateShiftHoldActive`. **Preserve this policy unchanged** — just feed `activeTouchCount` from the count of live `TouchTracker`s instead of `event.allTouches` (the layer already has them; more reliable than re-deriving from `UIEvent`). Android's analog is `tracker.isModifier()` + `updateShiftHoldTracking`.
- **Key rollover** (fast typist presses the next key before releasing the previous). Each press is its own tracker, so both resolve — this is the *clean* fix for the symptom the `isMultipleTouchEnabled = true` patch was papering over ([docs/IOS_MISS_KEY.md](IOS_MISS_KEY.md) hypothesis 4). The old model dropped finger B as "second touch in a sequence" while finger A's `touchUp` was queued; the single-owner layer never has that failure mode.

Rules the trackers must enforce:

1. **Modifier touches are pinned — they do NOT flint.** A finger holding shift/symbol that drifts must not slide-commit a neighbor (mark the tracker `isModifier`, skip the §5-P2 move-to-new-key logic for it). Mirrors Android cancelling slide/repeat when a modifier is among multiple pointers.
2. **`isMultipleTouchEnabled = true` moves to the single touch layer.** The five scattered per-view flags from IOS_MISS_KEY.md §2026-05-24 collapse to one flag on the `KeyTouchLayer` (multi-touch is per-view and must be set on the actual touch owner). The scattered flags are removed in P4.
3. **Repeat is cancelled when 2+ non-modifier keys are down** (Android `cancelKeyRepeatTimer`), so a rollover next to a held backspace doesn't machine-gun.

**Pointer-queue ordering (Android `PointerQueue`, [LIMEKeyboardBaseView.java:1741–1773](../LimeStudio/app/src/main/java/org/limeime/keyboard/LIMEKeyboardBaseView.java#L1741), release helpers 486–540):** the trackers form an ordered queue. On a **modifier down**, release all existing non-modifier pointers first (`releaseAllPointersExcept(null)`); on a **non-modifier up**, release only pointers *older* than it; a **modifier up** releases everything except the modifier. Port this ordering — it's what makes shift+letter, symbol-toggle mid-type, and 3-finger rollover resolve deterministically instead of racing.

## 4.2 Android source-of-truth parity map (read the cited code before implementing each)

The Mac-side agent must treat the Android implementation as the executable spec: **open each cited block and match its behavior.** Base path shorthand `AND/` = `LimeStudio/app/src/main/java/org/limeime/`. Priority: **M** = must-have for parity, **P** = preserve existing iOS behavior through the rewrite, **V** = verify desirability on iOS before porting (command gestures that may clash with iOS conventions).

| # | Behavior | Android source (file:line) — what it does | iOS today | iOS target / phase | Pri |
| --- | --- | --- | --- | --- | --- |
| 1 | Touch dispatch owner | `AND/keyboard/LIMEKeyboardBaseView.java:1778` `onTouchEvent`; `AND/keyboard/LIMEKeyboardView.java:113` | per-key `UIButton` | `KeyTouchLayer` / P1 | M |
| 2 | Per-pointer tracking | `AND/keyboard/PointerTracker.java` `onDown:290 onMove:324 onUp:386` | none | `TouchTracker` per `UITouch` / P1 | M |
| 3 | **Flint** slide-to-neighbor | `PointerTracker.java:350–370` — `onRelease(old)`+`onPress(new)`, sets `mIsInSlidingKeyInput` | **absent** | move re-detect / P2 | M |
| 4 | Hysteresis (to key **edge**, not center) | `PointerTracker.java:456–467 isMinorMoveBounce`, `469–479 getSquareDistanceToKeyEdge` | absent | detector dead-zone / P2 | M |
| 5 | Hit-test + **proximity correction** | `AND/keyboard/ProximityKeyDetector.java:45–95`; `getNearestKeys` grid | absent | `KeyDetector` / P1 | M |
| 6 | Up/cancel semantics | `PointerTracker.java:386–410 onUpEvent` (sub-hysteresis → commit **previous** fixed key; repeatable key → **no** send on up), `412–424 onCancelEvent` (abort, **no** send) | UIButton default | tracker / P2 | M |
| 7 | Layout change mid-touch | `PointerTracker.java:307–310, 343–346, 363–366` — re-detect key on new layout after `onPress/onRelease` | n/a | tracker / P2 | M |
| 8 | Long-press → popup open | `PointerTracker.java:497 startLongPressTimer`; `LIMEKeyboardBaseView.java:1491–1508 openPopupIfRequired` | `UILongPressGestureRecognizer` | tracker timer / P3 | P |
| 9 | **Slide-into-popup + release-to-select** | `LIMEKeyboardBaseView.java:1674–1682` (inject `ACTION_DOWN` into mini-kb), `1819–1830` (forward same pointer), `1717–1720 generateMiniKeyboardMotionEvent`; `AND/keyboard/MiniKeyboardKeyDetector.java:29–67` (slide allowance, top-edge 2×); dismiss-outside `1894–1916` | **absent** (popup uses plain `.touchUpInside` buttons) | popup owns sub-tracker / P3 | M |
| 10 | Key repeat (delete/arrows) | `PointerTracker.java:314–317`; `LIMEKeyboardBaseView.java:349–354` repeat timer | `Timer` in `keyDown` | tracker timer / P3 | P |
| 11 | Preview bubble + timing | `LIMEKeyboardBaseView.java:1359–1447 showKey`, delays `678–679`; suppress for modifiers `PointerTracker.java:490–494` | delegate preview (phone) | re-drive from tracker / P3 | P |
| 12 | **Swipe fling (4-way commands)** | detect `LIMEKeyboardBaseView.java:703–740` + `AND/keyboard/SwipeTracker.java:29–165`; actions `AND/LIMEService.java`: R:5833 **commit candidate**, L:5839 **backspace**, D:5858 **close**, U:5862 **options** | **absent** (no `onSwipe` in iOS) | `KeyTouchLayer` fling / P3 | V |
| 13 | Space caret drag + accel curve | `LIMEKeyboardView.java:146–236` (`stepsForSpaceDisplacement:222–236` non-linear), lock-in `AND/keyboard/LIMEKeyboard.java:291–323` | `SpaceKeyButton.touchesMoved` | fold into layer / P3 | P |
| 14 | Space long-press | `LIMEKeyboardView.java:98–101` (fires when drag < ⅕ key height) | space timer | tracker / P3 | P |
| 15 | Shift-lock state machine (OFF→ON→LOCKED) | `LIMEKeyboard.java:50–52, 161–214`; sticky `AND/keyboard/LIMEBaseKeyboard.java:648–653 onReleased` | present (`shiftLock` in `KeyboardView`) | preserve / P1 | P |
| 16 | Multi-tap char cycling | `PointerTracker.java:572–591 checkMultiTap`, `524–530` (delete-prev then send `codes[tapCount]`) | present (`multiTap` in `LayoutMetrics`/`KeyboardViewController`) | preserve / P1 | P |
| 17 | Gesture-detector off while popup open | `LIMEKeyboardBaseView.java:1799–1805` | n/a | layer rule / P3 | M |

Notes on the **V** rows: #12 swipe-left=backspace and swipe-right=commit are useful and low-risk; swipe-down=close and swipe-up=options may collide with iOS gestures (keyboard dismiss, Control Center) — confirm with the user before wiring those two. Everything else is M or P.

## 5. Phased plan (glide can land incrementally)

Ordered so each phase is shippable and reversible on its own.

### Phase 0 — Extract a key model + detector (no behavior change)
- Introduce `KeyModel` and `KeyDetector.keyAt(point)` computed from the **existing** button frames.
- Wire nothing to it yet; add a unit self-check: given the current layout frames, every key center resolves to itself and gap points resolve to the nearest key within threshold.
- **Ship/verify:** builds clean, zero runtime change.

### Phase 1 — Own the touch stream for the basic tap
- Add `KeyTouchLayer` overriding `touchesBegan/Moved/Ended/Cancelled` over the key rows.
- On `began`: detect key, apply pressed highlight, fire haptic + preview, start any repeat/long-press timers.
- On `ended` inside the same key: fire `didPress` (existing callback).
- **Disable** the per-key `UIButton` press targets (highlight, `keyDown/keyUp`) so there's one owner. Keep buttons as views.
- Proximity correction is now live → near-miss taps resolve. This alone should reduce the "clunky" feel.
- **Verify:** every existing single-tap behavior (letters, symbols, compose, shift, backspace) unchanged; measure drop rate vs pre-change.

### Phase 2 — Flint (the feature)
- On `touchesMoved`: `KeyDetector.keyAt(newPoint)`; if it differs from `tracker.currentKey` and the move exceeds the hysteresis dead-zone → `onRelease(oldKey)` (clear highlight) + `onPress(newKey)` (highlight, haptic, preview, restart long-press). Set `isSliding`.
- On `ended` while sliding: commit the key currently under the finger.
- Match Android's `isMinorMoveBounce` so boundary jitter doesn't rapid-switch (§4.2 #4 — measure to key **edge**, not center).
- Up/cancel + layout-change semantics (§4.2 #6, #7): sub-hysteresis up commits the **previous** fixed key; repeatable keys do **not** re-send on up; `touchesCancelled` aborts with no send; if a press/slide switches layout under the finger, re-detect on the new layout.
- **Verify:** press `q`, slide to `w` `e` `r`, release on `r` → commits `r`; slow boundary wobble does not double-fire.

### Phase 3 — Re-host the auxiliary gestures onto the new layer
- Long-press popup keyboard, iPad dual-row same-key slide, and key repeat move from `UIGestureRecognizer`/`Timer`-on-button to the `TouchTracker` (Android already unifies these in `PointerTracker`).
- **Slide-into-popup + release-to-select (§4.2 #9 — must-have, currently absent on iOS):** when long-press opens the popup, the *same* `TouchTracker` keeps ownership and forwards moves into the popup's own sub-detector; releasing commits the popup key under the finger; sliding outside (with top-edge tolerance) dismisses. Mirror `MiniKeyboardKeyDetector` + the injected-DOWN forwarding.
- **Swipe fling commands (§4.2 #12):** add a fling recognizer on `KeyTouchLayer` → swipe-left = backspace, swipe-right = commit candidate. **Ask the user before wiring swipe-down (close) / swipe-up (options)** — possible iOS gesture clash.
- Space-bar caret drag folds into `touchesMoved` for the space key (special-cased like Android's `mCurrentlyInSpace`); keep the non-linear accel curve (§4.2 #13) and space long-press (§4.2 #14). Disable the fling recognizer while a popup is open (§4.2 #17).
- **Verify:** long-press accents **with slide-select**, dual-row secondary glyph, backspace repeat, space caret drag, swipe-left backspace all still work.

### Phase 4 — Cleanup
- Remove the now-dead `UIButton` target/gesture-recognizer press plumbing and the `isMultipleTouchEnabled` symptom-patch if the new serialized model makes it moot.
- Confirm haptic throttle, composing popup, and candidate reload paths are untouched.

## 6. Risks & watch-items

- **Gesture arbitration:** today long-press/pan recognizers sit on the buttons and some keys defer `didPress` to `.touchUpInside` so a recognizer can pre-empt (globe/dismiss/popup, ~L873). Under the new model *we* own that arbitration in the tracker — port the defer logic deliberately, don't drop it.
- **Space key duality:** space is both a tap (space char) and a drag (caret). Keep Android's "sticky while dragging" flag semantics so a short drag past the dead-zone doesn't emit a space.
- **iPad split layout:** two content halves; the touch layer must span both or be installed per half. Android's grid detector is layout-agnostic — keep the iOS detector driven off actual frames, not hard-coded columns.
- **Compose-path interaction:** flint fires more `press` events faster than tapping. Confirm it doesn't worsen the candidate-reload starvation in IOS_MISS_KEY.md — the diffable `rebuildButtons` (P1 there) becomes more important, not less.
- **Accessibility:** individual `UIButton`s currently give VoiceOver per-key elements for free. Owning touch means we must re-expose keys as accessibility elements manually. **Do not skip** — add `UIAccessibilityElement`s per key.
- **Do not empty/rewrite `KeyboardView.swift`** — per repo rules, all changes are targeted edits; the new layer is additive, the old plumbing is disabled then removed incrementally.

## 7. Test / verification plan

- Phase 1 & 2 land a runnable check: a headless `KeyDetector` test asserting frame/proximity/hysteresis resolution against a captured layout.
- On the WJIP17 device (LimeIME scheme, force-refresh the extension binary per IOS_MISS_KEY.md §gotcha):
  - Fast-burst `wo3jiao4li2ming2…` → drop rate before/after Phase 1.
  - Flint sweep `q→w→e→r` commits the release key; boundary wobble is stable.
  - Regression sweep: shift, backspace repeat, long-press accents, iPad dual-row, space caret drag, emoji/candidate bar untouched.
  - Side-by-side vs iOS system keyboard for residual feel.

## 8. Non-goals

- Not gesture-typing / swipe-to-word (that's a path decoder, a separate feature). Flint here = discrete key-under-finger selection, matching Android.
- Not changing the compose / candidate-reload path (owned by IOS_MISS_KEY.md); this plan only stops that path from being starved by giving touch a clean owner.
- Not a visual redesign — key rendering, themes, and layout JSON (`lime_et_41.json` etc.) are unchanged.

## 9. Can we finish it at once? — Yes, as one continuous gated loop

Short answer: **yes, in a single run** — the four phases are not human stops, they are the loop's **test gates**. The loop runs continuously, phase→phase, and only pauses if a gate fails:

```text
for phase in [0,1,2,3,4]:
    write/modify code for `phase`      # targeted edits, never blank-and-rewrite
    build (xcodebuild, simulator)      # GATE 1 — must compile clean
    run KeyDetector unit test          # GATE 2 — geometry/proximity/hysteresis asserts
    run regression sweep (see §7)      # GATE 3 — no existing behavior regressed
    commit phase                       # checkpoint; loop continues automatically
    if any gate fails: fix in place, re-run gates for this phase, do not advance
```

The earlier "Phase 0→1 first, then measure" note was a *feel-tuning* suggestion (proximity correction alone might fix the clunky complaint) — it is **not** a required stop. Since flint is wanted regardless, the loop runs all phases end-to-end. The only thing that ever halts it is a red gate, which is the point of a gate.

**Why the phases must still exist inside the one loop:** each phase's gate compiles and tests against the previous phase's *verified* output. Writing all four phases before the first compile would stack unverified code (Phase 2 flint on an unproven Phase 1 tap layer) — the gates would then all fail at once with no way to localize the break. Phase boundaries keep each failure attributable. This is loop structure, not caution.

## 10. Execution model (where this actually runs)

**This authoring environment is Windows with no Swift/Xcode toolchain** (`swift`/`xcodebuild`/`xcrun` absent; UIKit and CoreGraphics do not exist on Windows). Therefore:

- **On Windows (here):** author this plan and, if asked, author the Swift source as targeted edits. **No gate can run** — not the build, not the simulator, not even the pure `KeyDetector` test (it needs CoreGraphics geometry). Any code produced here is **unverified until built on Apple hardware** and must be labeled as such.
- **On the Mac-side Claude/CI:** run the gated loop in §9. That environment has `xcodebuild` + the iOS simulator (per [docs/IOS_MISS_KEY.md](IOS_MISS_KEY.md): iPhone 17 Pro, iOS 26.5) and is the only place GATE 1–3 are meaningful. Deploy/observe on the WJIP17 device via the **LimeIME scheme** with the force-refresh protocol from IOS_MISS_KEY.md §verification.

**Consequence for "finish at once":** the *loop* finishes in one run — but that run must execute on the Mac-side Claude/CI, because the gates are the loop. If code is drafted on Windows first, it is a pre-seed for the Mac loop, not a finished result; the Mac loop still has to compile it, and the first-ever compile of a blind-drafted touch rewrite will surface errors that only the gate can catch. Cleanest path: **run the whole loop on the Mac side from Phase 0**, so every phase is authored against a live compiler.

## 11. Recommended sequencing

Run §9 as one continuous loop on the Mac-side Claude/CI, Phase 0 through 4, committing per phase. Do not pre-draft all phases blind on Windows. If a device feel-check after Phase 1 shows proximity correction already resolved the "clunky" complaint, that's a bonus to note — but the loop continues to Phase 2 for flint regardless.

---

## 12. Status — SHIPPED (2026-07-02, branch `ios-touch-rewrite`, device-confirmed)

The rewrite was executed as the gated loop above, on the Mac side, entirely against a live compiler/simulator (the "authoring on Windows" premise in §10 did not apply). **All five phases committed; flint + proximity + popup-slide + swipe + accessibility are on-device confirmed by the user.**

| Phase | Commit | Result |
|---|---|---|
| P0 KeyModel + KeyDetector | `d6ecce2f` | G1 build + G2 unit (5) green |
| P1 KeyTouchLayer owns tap | `cc22834b` | proximity + rollover; shift-hold fed from live trackers; unit green |
| P2 flint | `72b5cb85` | `q→w→e→r` commits `r`; hysteresis; modifier/repeatable pinned |
| P3 re-host + popup-slide + swipe | `008fd44f` | popup slide-select; swipe L=backspace / R=commit; space/dual-row/repeat re-hosted |
| P4 cleanup + accessibility | `9fa4bf3b` | dead `SpaceKeyButton`/GRs removed; `isMultipleTouchEnabled` collapsed to the layer; per-key VoiceOver |

**Verification reality (updates §0 autonomy boundary):** G2 (headless unit) is the reliable oracle and was expanded to cover flint/tracker/popup-detector/swipe-classifier logic. The G3/G4/G5 **XCUITests were written and compile, but can't be *run* headlessly** — driving a 3rd-party keyboard needs it enabled in the simulator's Settings (no `simctl`/Computer-Use path here). Behaviour was therefore proven via the expanded unit layer + **on-device by the user** (the §0 human step) — now **CONFIRMED working**, so the "clunky/feel" complaint is resolved (not just "sim gates green").

**Decisions / deviations:**
- **Swipe up/down NOT wired** (§4.2 #12 V-rows) — user opted out; only left=backspace / right=commit shipped.
- **Commit timing:** P2 committed non-repeatable keys on `touchesEnded` (needed for flint). A later fast-typing fix (`d97d52f3`) moved plain-key commit to `touchesBegan` for robustness, preserving flint via **delete-then-commit on each slide crossing**.
- **Pre-existing test fix:** 3 `popupCharacters` fixture-decoder failures (unrelated, on master) fixed in `fd638637`.

**Final whole-branch review found + fixed 4 runtime bugs** (`11a80da6`): popup slide-select used layer-local coords where the delegate expected keyboard-local; per-touch state/timers leaked on a mid-touch layout rebuild; an open popup was orphaned on `touchesCancelled`; a horizontal flick on a repeatable/modifier key double-acted.

**Follow-on work (see docs/IOS_OPT_TOUCH.md and docs/IOS_MISS_KEY.md 2026-07-02):** the residual high-speed missed-key issue was then closed (delivery hardening + diffable candidate rebuild + preview reuse); and **flint onto a popup key now opens its mini-keyboard** instead of stopping short (`69b9b132`).

## 13. Residual feel gap — still less responsive than the built-in keyboard (2026-07-12)

Missed keys stay fixed (§12), but the keyboard still *feels* less responsive than the iOS system keyboard. This is a **new, unmeasured symptom** — not a reopening of the missed-key thread (no drops; every tap lands).

### 13.1 Decomposition — the fix path differs per stage

| Stage | Owner |
|---|---|
| (a) tap → key highlight / preview visible | us, fully |
| (b) tap → haptic / click sound | us, fully (already optimized: pre-warmed generator, `AudioServicesPlaySystemSound`) |
| (c) tap → character appears in host text field | **partly irreducible** — `textDocumentProxy.insertText` is an XPC hop into the host-app process; the system keyboard has in-process paths. A third-party keyboard can never fully match (c). |
| (d) focus a field → keyboard fully drawn | us (extension process launch + `viewDidLoad` cost) — classic third-party keyboard weakness |

Perceived responsiveness is dominated by (a)+(b) — the stages we own.

### 13.2 Two code suspects — experiments applied 2026-07-12

1. **Key preview fades in over 80 ms.** `showPreviewFor` animated alpha 0→1 + scale 0.88→1 as a spring with `KeyPreview.appearDuration = 0.08` ([LayoutMetrics.swift](../LimeIME-iOS/LimeKeyboard/LayoutMetrics.swift), used at [KeyboardViewController.swift:3676](../LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift#L3676)). The system keyboard's preview **pops instantly, no fade** — an 80 ms ramp is ~5 frames of visible mush per keystroke. **Change: `appearDuration` 0.08 → 0** (instant pop). `disappearDuration` untouched — the system preview does animate out.
2. **No ProMotion opt-in.** The keyboard extension's Info.plist had no `CADisableMinimumFrameDurationOnPhone`; on a ProMotion device (WJIP17) our Core Animation work may be capped at 60 Hz while the system keyboard runs the full 120 Hz touch-to-photon path. **Change: added the key = `true`** to [LimeKeyboard/Info.plist](../LimeIME-iOS/LimeKeyboard/Info.plist). Hypothesis only — Apple documents the key for apps; whether it is honored for a keyboard extension's process is exactly what the device test decides. Check with the FPS overlay (IOS_PROFILING.md §3.1).

### 13.3 Verification (human + WJIP17)

1. Deploy via the LimeIME scheme + force-refresh protocol (IOS_MISS_KEY.md §gotcha).
2. Feel-test a typing burst side-by-side vs the system keyboard.
3. **If still off, measure before more code** — film 240 fps slo-mo of a finger tapping LimeIME then the system keyboard in the same app; count frames from contact → highlight and contact → character (~4 ms/frame resolution, measures exactly what is perceived). Branch on where the gap lives:
   - gap in (a) → keep these fixes, next look at highlight/pressed-state path and frame pacing;
   - gap in (c) → measure the XPC floor first; if the whole gap is there, local polish can only *mask* it via (a)/(b) crispness — say so and stop;
   - gap in (d) → profile keyboard-spawn (`viewDidLoad`, DB open) and lazy-load.

### 13.4 Keep press feedback out of the touch-critical path (2026-07-12)

Plain-key input previously ran haptic and sound feedback synchronously before
`didPress`, making feedback setup part of tap-to-commit latency. Haptic UIKit APIs
must remain on the main thread, and both sound playback APIs already return after
starting asynchronous playback, so moving either API to a background queue is not
safe or useful.

**Change:** commit the key synchronously, then enqueue haptic and sound feedback for
the next main-queue turn. The feedback generators/player stay cached and prepared;
the existing 40 Hz haptic throttle remains unchanged. A focused unit test asserts
that commit occurs before the deferred haptic request. Device verification remains
the final oracle for whether the one-turn feedback delay feels acceptable.

## 14. Follow-on — gap taps needed a non-transparent hit surface (2026-07-15)

§12's `KeyTouchLayer` `hitTest` claims plain keys **and transparent gaps** — but
claiming a region in `hitTest` is necessary, not sufficient. iOS's custom-keyboard
touch gate drops a touch on a fully transparent pixel **before** `hitTest` runs
([docs/IOS_CANDI_TOUCH.md §Resolution](IOS_CANDI_TOUCH.md)), and the layer's
background was `.clear` — so taps in the gaps / edges / margins **between** keys were
silently dead at any typing speed (on-key taps always worked; key frames have solid
backgrounds).

**Fix (`7188a4ae`):** give `KeyTouchLayer` `LayoutMetrics.TouchTrap.fill`
(`white 0.5, alpha 0.01`, non-transparent so the gate passes the touch through) and
widen the per-row touch layer to the **full row width** (keys still centered via a
layout guide) so indented-row side margins resolve to the edge key by proximity. The
expanded candidate panel got the same fill on its scroll content + cells. Regression
test in `TouchLayerGestureTests`. Dead-tap-family framing: [docs/IOS_MISS_KEY.md
§2026-07-15](IOS_MISS_KEY.md); layout-author framing: [ANDROID_IPHONE_KEYBOARD.md
§Touch Handling](ANDROID_IPHONE_KEYBOARD.md) and [IPAD_KEYBOARD.md §4.2.7](IPAD_KEYBOARD.md).
