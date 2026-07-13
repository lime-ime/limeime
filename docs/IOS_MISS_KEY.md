# iOS LimeKeyboard — Missed-Key Investigation & Fix Plan

## Symptom (reported)

When the user types fast, the keyboard occasionally drops a keypress in the middle of a run. The missed keystroke is silent — no character is inserted and no composing-popup tick is emitted for it — even though the user's finger clearly contacted the key.

Initially this was attributed to the haptic feedback path (see [docs/IOS_HAPTIC.md](IOS_HAPTIC.md)). After fixing the haptic generator pattern (pre-warmed stored generator + 25 ms throttle), the missed-key symptom **persists**, and a sharper observation rules haptics out as the dominant cause:

> Symbol keys never miss. Only compose keys miss.

Both kinds of key fire the same `fireHaptic()` at `.touchDown` (see [LimeIME-iOS/LimeKeyboard/KeyboardView.swift:953](../LimeIME-iOS/LimeKeyboard/KeyboardView.swift#L953)). If haptics were starving touch dispatch, symbol keys would miss too. They don't — so the differentiator must be something that **only compose keys do**.

## What is different about a compose key

A symbol-key tap calls `insertText`/`deleteBackward` directly through `UITextDocumentProxy` and is done. No DB hop. No candidate reload. Main-thread time per tap ≈ haptic + button press-state colour flip.

A compose-key tap routes through [`KeyboardViewController.updateCandidates()`](../LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift#L1381-L1469):

```
keyUp → keyboardKeyTapped → didPress → onKey(primaryCode:) → handleCharacter(code)
      → mComposing.append(...)                                  [main, sync, cheap]
      → updateCandidates()
            → showComposingPopup()                              [main, sync, visible work]
            → DispatchQueue.global(.userInteractive).async {
                  ss.getMappingByCode(code, …)                  [bg, SQLite]
                  DispatchQueue.main.async {
                      setSuggestions(results)                   [main, HEAVY]
                  }
                  // optional second hop for stage-2 truncation:
                  ss.getMappingByCode(code, getAllRecords: true) [bg, SQLite]
                  DispatchQueue.main.async {
                      applyFullCandidateResults(...)             [main, medium]
                  }
              }
```

The two main-thread chunks that come back later — `setSuggestions(...)` and `applyFullCandidateResults(...)` — are what symbol keys do not have. Inside `setSuggestions` the dominant cost is `CandidateBarView.rebuildButtons()`.

## The hot spot — `CandidateBarView.rebuildButtons()`

[`CandidateBarView.swift:629-664`](../LimeIME-iOS/LimeKeyboard/CandidateBarView.swift#L629-L664):

```swift
private func rebuildButtons() {
    stackView.arrangedSubviews.forEach { $0.removeFromSuperview() }
    candidateButtons.removeAll(keepingCapacity: true)

    for (index, mapping) in candidates.enumerated() {
        let btn = makeCandidateButton(mapping: mapping, index: index)
        stackView.addArrangedSubview(btn)
        btn.heightAnchor.constraint(equalTo: stackView.heightAnchor).isActive = true
        candidateButtons.append(btn)
        applyHighlightStyle(button: btn, index: index, mapping: mapping)
    }
    // … plus visibility-flag updates for the bar chrome …
}
```

Per stroke this:

1. Tears down **every** existing candidate cell (`removeFromSuperview` × N).
2. Allocates **N** fresh `UIButton`s.
3. `addArrangedSubview` × N — each call invalidates the `UIStackView`'s layout.
4. Activates a fresh `heightAnchor.constraint(equalTo:)` per button — each constraint activation walks the AutoLayout graph again.
5. Applies highlight styling to each.

For N = 20–50 candidates this is a real chunk of synchronous main-thread time. It runs **every stroke**, even when 80–100 % of the candidates are unchanged from the previous stroke (which is the common case during composition — a new stroke usually just appends one row, the rest of the list is the same).

[docs/IOS_PROFILING.md §4.2](IOS_PROFILING.md) already names this hot spot — it just doesn't prescribe the fix.

## Why this drops touches

`rebuildButtons()` runs on the main thread. While it is executing, the main runloop **cannot dispatch the next `.touchDown`/`.touchUp`**. UIKit buffers incoming touch events.

Two coalescing/dropping modes can then bite:

1. **Hit-test against a frame that is changing.** `addArrangedSubview` invalidates layout on the candidate bar; if a touch's hit-test runs against a partially-laid-out view tree, the result is undefined.
2. **`touchBegan`/`touchEnded` pair compression.** If a user's quick finger-tap produces both `.touchBegan` and `.touchEnded` while the main thread is mid-rebuild, UIKit can merge / drop them in the dispatch queue — the keyboard button never sees a complete press cycle, so `keyDown` and `keyUp` never fire, so `fireHaptic` and `didPress` don't either. From the user's point of view: silently swallowed.

Mode (2) explains why the user sees **no haptic and no character output** for the missed key, not a half-finished one.

## What we can do — in priority order

### P1 — Diffable rebuild instead of teardown-and-rebuild

**Largest single win.** Convert `rebuildButtons` to a diff-based update:

- Reuse existing `UIButton` cells in place (update title, tag, highlight only).
- Allocate new cells only when the new candidate list is longer than what's already on screen.
- Remove trailing cells when the new list is shorter.
- Activate the height constraint **once** per cell at allocation time, never again.

Sketch:

```swift
private func setCandidates(_ next: [Mapping]) {
    candidates = next

    // Grow: allocate any new cells needed at the tail.
    while candidateButtons.count < next.count {
        let i = candidateButtons.count
        let btn = makeCandidateButton(mapping: next[i], index: i)
        stackView.addArrangedSubview(btn)
        btn.heightAnchor.constraint(equalTo: stackView.heightAnchor).isActive = true
        candidateButtons.append(btn)
    }
    // Shrink: hide / remove the tail.
    while candidateButtons.count > next.count {
        let last = candidateButtons.removeLast()
        last.removeFromSuperview()
    }
    // Update in place — cheap.
    for (i, mapping) in next.enumerated() {
        configure(candidateButtons[i], mapping: mapping, index: i)
        applyHighlightStyle(button: candidateButtons[i], index: i, mapping: mapping)
    }
    updateChromeVisibility(hasCandidates: !next.isEmpty)
}
```

Common-case cost per stroke drops from "N button allocations + N constraint activations + N `addArrangedSubview`" to "0–2 allocations + N label updates" — typically a ~10× reduction. The main thread frees up enough for queued touches to drain.

### P2 — Defer the reload one runloop tick

**Quickest experiment, ships in one line.** In [`updateCandidates()`](../LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift#L1426-L1443), the stage-1 result currently runs `setSuggestions` synchronously inside the `DispatchQueue.main.async` block. Wrap that block's body in a second `DispatchQueue.main.async`:

```swift
DispatchQueue.main.async { [weak self] in
    DispatchQueue.main.async { [weak self] in        // <— extra hop
        guard let self = self, self.currentSearchID == sid else { … }
        results.isEmpty ? self.clearSuggestions() : self.setSuggestions(results)
    }
}
```

One runloop hop = invisible to the user (the candidate bar updates ~16 ms later) but gives UIKit one full iteration to dispatch any queued `touchDown`/`touchUp` events **before** the heavy reload locks the main thread again.

If this alone substantially reduces dropped keys it confirms the main-thread-starvation hypothesis. If it does not, P1 is still needed but the root cause is elsewhere and we should profile before changing more code.

### P3 — Don't call `showComposingPopup` twice per stroke

[`KeyboardViewController.swift:1397`](../LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift#L1397) calls it eagerly for T1 latency; [`KeyboardViewController.swift:1521`](../LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift#L1521) calls it again inside `setSuggestions`. The second call is only useful when `mComposing` changed between the two — which it usually didn't, since the DB query doesn't alter `mComposing`. Skip the second call when `mComposing` is unchanged.

Small saving, but every freed millisecond of main-thread time helps.

### P4 — Pre-cache the candidate-cell height constraint

Once P1 lands, the cell list grows/shrinks in place. The height constraint is then created once per cell and stays installed for the cell's lifetime. AutoLayout never re-walks it per stroke. Comes for free with P1.

### P5 — Capture a baseline trace before changing more code

`docs/IOS_PROFILING.md` §3 has the workflow wired (`Stroke`, `CandidateReload`, `ComposingPopup`, `CandidateSwap` signposts). On the WJIP17 device, record one fast-typing burst trace and confirm:

- `CandidateReload` p95 dominates the inter-stroke gap (expected on the current code).
- After P2 ships, `CandidateReload` still dominates — but is now off the main runloop's "dispatch touches" hot path because the deferred-hop pattern gives touches priority. The dropped-key rate falls.
- After P1 ships, `CandidateReload` p95 itself shrinks.

Per `docs/IOS_PROFILING.md` §5: don't apply optimisations beyond P2 until a trace shows the budget is being exceeded.

## Recommended sequencing

1. **Ship P2** as a same-day experiment on the WJIP17 device. One-line change, fully reversible.
2. **Test fast-typing burst** with the existing typing-pad / Notes app. Confirm whether dropped keys reduce.
3. If P2 reduces drops:
   - Capture a baseline `Stroke` / `CandidateReload` trace per [docs/IOS_PROFILING.md](IOS_PROFILING.md) §3.
   - Implement P1 (diffable rebuild).
   - Re-capture trace, compare `CandidateReload` p95.
4. If P2 does **not** reduce drops:
   - Stop guessing. Capture the trace first. The hypothesis is wrong somewhere.
   - Possible alternates to investigate: (a) gesture-recognizer interactions with the candidate-bar pan/scroll, (b) `cancelsTouchesInView` settings on the keyboard's gesture recognizers, (c) `UIInputViewController` host-app coalescing under fast input.

## What this plan deliberately does NOT do

- **Does not change the touch path itself.** `keyDown/keyUp` already use `.touchDown` / `.touchUpInside` which is the fastest UIKit can dispatch. Adding lower-level `touchesBegan` overrides would not help.
- **Does not move the haptic off main.** That change is already shipped (see [docs/IOS_HAPTIC.md](IOS_HAPTIC.md)). Symbol keys with haptic on confirm haptic is not the bottleneck for missed keys.
- **Does not introduce a touch-coalescing queue.** UIKit already does that correctly when the main thread isn't blocked. The fix is to **stop blocking the main thread**, not to add a parallel touch queue.

## Verification once both P1 and P2 are in

- Type a long fast burst (e.g. `wo3jiao4li2ming2wo3jiao4li2ming2…`) at maximum speed on the WJIP17 device, with haptic on, and confirm every keystroke produces a haptic tick and a character/compose update — zero silent drops.
- Compare side-by-side with the iOS system keyboard for any residual perceptual difference.
- Capture an Instruments trace and confirm `CandidateReload` p95 is < 10 ms.

## Session log

### 2026-05-21 / 2026-05-22 — P2 applied; diagnostic counter attempted but inconclusive

**P2 applied** at [`KeyboardViewController.updateCandidates()`](../LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift) — extra `DispatchQueue.main.async` hop wrapping the stage-1 reload, with stale-stroke check re-done inside the inner block. Compiles clean (BUILD SUCCEEDED on iPhone 17 Pro iOS 26.5 simulator). Deployed to the WJIP17.

**Result of P2 on device: drops still occur.** Per the doc's own "If P2 does not reduce drops" guidance, this means main-thread starvation from `CandidateReload` is **not the dominant cause** — the reload is heavy but is not what blocks the next touchDown. The bottleneck must be earlier on the compose path. Most likely candidate: the **synchronous chain inside `keyDown`** —

```
keyDown → fireHaptic → didPress → onKey → handleCharacter → updateCandidates → showComposingPopup()
```

`showComposingPopup()` runs synchronously on the main thread *before* the DB query is dispatched. It does:

- `searchServer?.keyToKeyname(code)` — synchronous call into `LimeDB.keyToKeyName(...)` ([SearchServer.swift:116-119](../LimeIME-iOS/Shared/Search/SearchServer.swift#L116-L119)) which holds a per-table cache but takes a lock + map lookup
- `candidateBar.composingText = display` — fires `applyComposingText()` ([CandidateBarView.swift:476-483](../LimeIME-iOS/LimeKeyboard/CandidateBarView.swift#L476-L483)) which mutates a label and calls `bringSubviewToFront`
- `expandedComposingLabel?.attributedText = …` — NSAttributedString allocation per stroke

This entire chain blocks the touch dispatcher just like `rebuildButtons()` does — but P2 didn't touch it. **A natural next experiment is to defer `showComposingPopup()` itself one runloop tick** (or move only the UI-update half off-main while keeping `mComposing` correctness eager).

### Diagnostic probe attempt — no logs observed

Added three `os_log` calls (`keyDown`, `touchCancel`, `handleChar`) tagged `subsystem:tw.jeremy.limeime category:misskey`. After deploy to the WJIP17, Console.app showed no entries. Suspects:

1. **`type: .info` is filtered to disk by default** — fixed mid-session by switching to `.default`, but no retest before sign-off.
2. **Extension binary cached** — installing the LimeIME app via Xcode does not always swap the keyboard extension's running binary. The Console-empty result is consistent with the keyboard still running the *pre-probe* build. **Reliable refresh on iOS:** remove the LimeIME app from the home screen, then reinstall via the LimeIME scheme. Also disable + re-enable the keyboard in Settings → General → Keyboards.
3. **Console filter syntax** — try plain text `misskey` instead of `subsystem:tw.jeremy.limeime` if the structured filter shows nothing.
4. **NSLog fallback** — if `os_log` continues to produce nothing after a clean reinstall, swap to `NSLog("[misskey] keyDown code=%d", code)`. NSLog uses the legacy logging system, always streams to Console, no level filtering.

All three probes have been reverted at end of session — re-add via the markers in the next session's first 5 minutes.

### Important gotcha — Xcode scheme selection

**Always launch via the LimeIME scheme. Never the LimeKeyboard scheme.**

The LimeKeyboard scheme attaches the debugger directly to the keyboard extension process. On iOS 26.x device the debugger-attached path produces a hard crash on **switching to Chinese mode**:

```
*** Terminating app due to uncaught exception 'NSInvalidArgumentException',
    reason: '-[_NSXPCDistantObject ___nsx_pingHost:]: unrecognized selector
            sent to instance 0x103188730'
```

This is the same family as the iOS 26 simulator §10 fault in [docs/IOS_PROFILING.md](IOS_PROFILING.md#10-ios-26-simulator-blocker-verified-may-2026) — the XPC inspection layer that gets injected when a debugger is attached to a keyboard extension calls a selector the extension's `NSXPCInterface` does not implement. Apple's own keyboard extensions reportedly have the same crash under the same setup. **Not a real-user bug**; runs cleanly when the extension is unattended.

**The LimeIME scheme puts the debugger on the settings app** and lets the keyboard extension run unattended — same way it runs for end users. No `nsx_pingHost:` crash; missed-key behaviour can be observed normally.

### Pre-existing constraint conflict (separate issue, non-fatal)

Reproducibly observed in console during keyboard layout:

```
"<NSLayoutConstraint UIInputView.height == 956 (UIView-Encapsulated-Layout-Height)>",
"<NSLayoutConstraint CandidateBarView.height == 63.8>",
"<NSLayoutConstraint UIView.height == 50> × 4 + <UIView.height == 54>",
"<KeyboardView.bottom == UIInputView.bottom>",
…
Will attempt to recover by breaking constraint <UIView.height == 54>
```

`UIInputView` is force-sized to 956 pt by `UIView-Encapsulated-Layout-Height`, but `CandidateBarView` (63.8 pt) plus the five rows of `KeyboardView` (50 + 50 + 50 + 50 + 54 = 254 pt) sum to only 317.8 pt. UIKit auto-recovers by stretching the bottom row.

None of this session's edits touch keyboard sizing — this is pre-existing. Likely fix candidates: change `KeyboardView.bottom == UIInputView.bottom` to `lessThanOrEqual`, or set `view.heightAnchor` constraint at a high priority so UIInputView shrinks to fit. **Out of scope for the missed-key investigation** — separate ticket.

### 2026-05-24 — New hypothesis (4): UIView default rejects the second simultaneous touch; multi-touch enabled at every container

**New hypothesis (4) — container-level multi-touch rejection.** All previous hypotheses (1)–(3) frame the missed-key cause as main-thread starvation during the candidate reload. A complementary mechanism is also consistent with the symptom — and interlocks with (2):

`UIView.isMultipleTouchEnabled` defaults to **`false`** in UIKit. Apple's documented behaviour for that default: *"the view receives only the first touch event in a multi-touch sequence."* A `grep` across [KeyboardView.swift](../LimeIME-iOS/LimeKeyboard/KeyboardView.swift), [KeyboardViewController.swift](../LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift), and [CandidateBarView.swift](../LimeIME-iOS/LimeKeyboard/CandidateBarView.swift) confirms `isMultipleTouchEnabled` (and `isExclusiveTouch`) are never set anywhere — every container in the hit-test chain sits at the UIKit default.

**Interaction with the main-thread-block model.** When `rebuildButtons()` (or `showComposingPopup()`) blocks the main thread, finger A's `touchUp` queues but is not yet processed. From UIKit's bookkeeping POV finger A is *still tracking* when finger B's `touchBegan` arrives. With multi-touch disabled at the container level, finger B is treated as the **second touch in a sequence** and dropped at the dispatch layer — never reaches button B, no `keyDown`, no `fireHaptic`, no character. This is consistent with:

- The "silent miss" symptom (touch dropped pre-dispatch, no half-finished press).
- The "only compose keys miss" differentiator: symbol keys' `touchUp` drains immediately, so the next touch is always "first touch again" and goes through. Compose keys' `touchUp` is held behind heavy main-thread work, so the next touch is position-1 in the multi-touch sequence and is silently dropped.
- Why P2 alone didn't fix it: P2 trimmed the *reload* tail, but `showComposingPopup()` still runs synchronously before the DB hop, and any main-thread block (however short) is enough to keep `touchUp` from draining before the next `touchBegan`.

**Change applied — `isMultipleTouchEnabled = true` at five spots in [LimeIME-iOS/LimeKeyboard/KeyboardView.swift](../LimeIME-iOS/LimeKeyboard/KeyboardView.swift):**

- `KeyboardView.init(layout:)` — root view (~line 418).
- `makeSplitRow` rowView (~line 578) — iPad split mode.
- `makeSplitRow` contentView per half (~line 605).
- `makeRow` rowView (~line 647) — standard rows.
- `makeRow` contentView (~line 657).

Multi-touch enable is **per-view, not inherited** — setting only the root is insufficient. Every view in the hit-test chain from window down to the leaf `KeyButton` must accept multi-touch, otherwise the dispatcher can drop at any level. No other code touched (no candidate-bar changes, no gesture-recognizer changes, no touch-handler changes, no candidate-reload changes). Compiles clean — BUILD SUCCEEDED on iPhone 17 Pro iOS 26.5 simulator.

**Why this is worth trying before P1 / probes.**

- Cheapest possible change (five one-line additions).
- Fully reversible.
- Orthogonal to P1 / P2 / P3 / P5 — does not preclude or interfere with any of them.
- If it works, the entire missed-key thread closes without needing to rewrite the reload path.
- If it does not work, the next-session probe + P1 plan still stands unchanged.

**What this hypothesis does *not* claim:**

- It does **not** replace P1. The reload is still heavy; the diffable rebuild is still the right structural fix for perceived latency and battery, independent of the missed-key symptom.
- It does **not** invalidate P2. P2 still helps drain queued touches faster between strokes.
- It assumes the underlying mechanism in hypothesis (2) is real — multi-touch rejection only matters when `touchUp` for the previous key is delayed. If the main thread were never blocked, multi-touch enable would be a no-op for normal one-finger typing.

### Verification protocol (test on WJIP17)

1. Deploy via the **LimeIME scheme** (per gotcha above — never LimeKeyboard scheme on iOS 26.x).
2. **Force-refresh the extension binary:** remove LimeIME from the home screen → reinstall via LimeIME scheme → disable + re-enable the keyboard in Settings → General → Keyboards. Required because iOS aggressively caches the extension binary.
3. Type the fast burst `wo3jiao4li2ming2wo3jiao4li2ming2…` and confirm whether silent drops disappear or substantially reduce.
4. Side-by-side: type the same burst on the iOS system keyboard for a control.
5. If drops disappear → root cause confirmed; close this thread, defer P1 until [docs/IOS_PROFILING.md](IOS_PROFILING.md) trace shows `CandidateReload` p95 > 10 ms.
6. If drops persist → revert the five lines, fall back to the existing "next-session entry point" below (re-add probes, capture trace, then P1).

## Next-session entry point

1. **Test hypothesis (4) first** — multi-touch enable is already applied. Build the LimeIME scheme to WJIP17, force-refresh the extension binary, run the fast-burst test. Outcome decides next branch:
   - **Drops gone or substantially reduced** → root cause was container-level multi-touch rejection. Update the doc with the verified result; defer probes and P1 unless profiling later shows reload p95 is still a problem.
   - **Drops persist** → revert the five `isMultipleTouchEnabled = true` lines in [KeyboardView.swift](../LimeIME-iOS/LimeKeyboard/KeyboardView.swift) (single-file, no other dependencies) and proceed with steps 2–7 below.
2. **Confirm reinstall protocol works.** Remove LimeIME from home screen, reinstall via LimeIME scheme, verify keyboard re-enabled in Settings. Without this, no diagnostic build will actually run on the device.
3. **Re-add the three `os_log` probes** (or fall back to `NSLog`). Grep markers: `DIAG: missed-key`.
4. **Reproduce the drop with Console streaming.** Count `keyDown` / `handleChar` / `touchCancel` lines vs visible characters → bucket the failure as per the table in the session-end summary.
5. If hypothesis (2) lights up — `keyDown` count = taps but `handleChar` < taps, or both = taps but characters < taps — apply the next targeted fix: **defer `showComposingPopup` one runloop tick** (mirror of P2 but on the eager half of the compose path) and re-test.
6. If hypothesis (1) lights up — `keyDown` count < taps — investigate gesture recognizers on candidate-bar pan / dual-row iPad / popup long-press; check `cancelsTouchesInView` settings.
7. If `touchCancel` lines appear during drops, that's hypothesis (3) — UIKit pre-empted; trace gesture-recognizer arbitration.
8. **Hypothesis (4) diagnostic signature** (if re-investigating after revert): `keyDown` count **< taps**, and **no `touchCancel`** lines. That distinguishes container-level multi-touch rejection from (1)/(2)/(3) at the probe layer.

### 2026-06-30 — Screen-edge `touchDown` eating: CONFIRMED with hard data, and FIXED (same swallowed-touchDown family)

While wiring the cj4 `;`-key long-press ([#140](%23140_ISSUE.md)), hit — and fully diagnosed — a concrete, reproducible instance of the "button never sees the press" outcome this doc is about, plus a fix that works **inside** a keyboard extension. Different trigger from the fast-typing drops (this one is the **screen edge**, not main-thread starvation), but the same end state: `keyDown` < presses, no `touchCancel`.

**Symptom.** The **rightmost keys** — `=` in the et_41 phonetic number row, and the cj4 `;` appended to the home row, both sitting at the screen's right edge (x = 100%) — drop their `keyDown` on a **held** press: no key preview, no haptic. Quick **taps** fire `keyDown` normally. The user pinpointed it precisely: pressing the **left portion** of the same key = normal; the **right portion near the bezel** = eaten.

**Diagnosis (hard data, not theory).** Console never surfaced the extension's logs on this device (same wall as the 2026-05 probe attempt). Worked around it with a **file logger → the extension's own container Documents**, pulled off the device with `devicectl` (see recipe below). Per-key trace:

```
-  (inner, code 45):  keyDown 45 → keyDown->showPreview 45 → showPreviewFor (hasWindow=1) → [~0.40s] → popupLP began
=  (edge,  code 61):  popupLP began 61            ← NO keyDown / showPreview before it
=  (edge)  tapped:    keyDown 61 → showPreview → keyUp → keyTapped   ← taps fire keyDown fine
```

So iOS holds the touch in the thin **screen-edge strip** to disambiguate its own edge gestures; on a **held** press that delay outlasts the long-press recognition, and the button's `touchDown` UIControl event is cancelled before it ever fires. The long-press **gesture recognizer still fires** (`popupLP began`) — that's the key asymmetry.

**`preferredScreenEdgesDeferringSystemGestures` does NOT help a keyboard extension.** It's queried on the **host app's** VC, not the input VC. Overriding it on `KeyboardViewController` (`return .all` + `setNeedsUpdateOfScreenEdgesDeferringSystemGestures()`) changed nothing on device. Confirmed by [SO 61227987](https://stackoverflow.com/questions/61227987/) and [blog.kulman.sk](https://blog.kulman.sk/why-ios-gestures-lag-at-the-screen-edges/).

**The fix (works in the extension).** Gesture recognizers — and `hitTest` / `point(inside:)` — get `.began` / are invoked **without** the edge delay, even when the button's `touchDown` is being eaten. So drive the press feedback off a recognizer instead of `touchDown`:

- Added a `UILongPressGestureRecognizer(minimumPressDuration: 0)` to **every key button** (`pressFeedbackGesture`), with `cancelsTouchesInView = false` and a `UIGestureRecognizerDelegate` returning `shouldRecognizeSimultaneouslyWith = true` so it never fights the button's own tracking, the popup long-press, the dual-row pan, or the generic long-press.
- **Moved haptic / sound / key-preview off `keyDown` onto that recognizer's `.began`**; dismiss the preview on `.ended/.cancelled`, and in `popupKeyLongPressed`'s `.began` (the edge eats the `keyCancel` that used to dismiss it). `keyDown` keeps only its non-feedback work (press color, `didPress` deferral, repeat timer).
- Verified on the WJIP17: edge `=` / `;` now show preview + haptic on a held press, identical to inner keys. 102/0 keyboard unit tests still green.

**Why this matters for the fast-typing drops.** The screen-edge case is a *different trigger* (system edge-gesture disambiguation, not `rebuildButtons()` main-thread block) but the **same failure shape** as hypotheses (1)/(4): the button never gets the press, `keyDown` < presses, no `touchCancel`. The takeaway for the fast-typing thread: **a gesture recognizer (or `hitTest`/`point(inside:)`) sees touches UIKit is delaying** — so if the drops are about events not reaching the button (rather than the *commit* being lost downstream), driving the keypress off a recognizer is a candidate fix there too, not just for the edge.

**Reusable Console-independent device-diagnostics recipe** (Console genuinely doesn't show this extension's `NSLog`/`os_log` here — stop fighting it):

- **Log to a file** in the extension's own container: `FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first` → append lines (reset on `viewDidLoad`).
- **Pull it:** `xcrun devicectl device copy from --device <id> --domain-type appDataContainer --domain-identifier org.limeime.keyboard --source Documents/<file> --destination <local>`. ⚠️ The `appGroupDataContainer` domain is **broken** (bogus `File paths cannot contain '..'` error) — use the extension's own `appDataContainer` instead.
- **Build + install from CLI** (eliminates Xcode stale-binary doubt): `xcodebuild -project LimeIME-iOS/LimeIME.xcodeproj -scheme LimeIME -destination 'platform=iOS,id=<dev>' -allowProvisioningUpdates -derivedDataPath <dd> build`, then `xcrun devicectl device install app --device <dev> <…/LimeIME.app>`. `devicectl device uninstall app … org.limeime` first to force a fresh extension binary; a plain reinstall keeps the keyboard enabled but may keep the **cached** running process (a device reboot busts it for sure).

### 2026-07-02 — RESOLVED. Root cause was UIKit touch-delivery delay, NOT `rebuildButtons` starvation

**Fixed and device-confirmed on branch `ios-touch-rewrite`.** After the touch rewrite (docs/IOS_TOUCH_REWRITE.md) moved ordinary keys to `KeyTouchLayer` ownership, the residual high-speed misses were closed via the plan in **docs/IOS_OPT_TOUCH.md**. At this point the implementation has one owner **per row/half**, not one full-keyboard owner; see the 2026-07-13 correction below. The result still reframes this doc's central missed-key hypothesis.

**Correction to this document's main theory.** The dominant cause was **not** `CandidateBarView.rebuildButtons()` main-thread starvation. Two pieces of evidence:

1. **Misses reproduce on the ENGLISH keyboard with prediction OFF** — a path that never touches the candidate bar (`updateEnglishPrediction()` does `guard englishPredictionOn else { return }` before any candidate work, and `rebuildButtons` isn't reached). So the misses cannot be primarily the candidate rebuild.
2. **A simulator micro-benchmark measured `KeyDetector` at ~6.4 µs build+resolve / ~5.4 µs resolve per tap** — ~0.04 % of a frame. Detection was never the bottleneck either.

The actual dominant cause was **UIKit delaying/dropping `touchesBegan`** — the same "events never reach the button" family as the 2026-06-30 screen-edge finding, but triggered by the input window's system gesture recognizers rather than the screen edge.

**What actually fixed it (docs/IOS_OPT_TOUCH.md Phase 1, commit `49845bba` — the biggest win):**
- `delaysTouchesBegan = false` on the input window's gesture recognizers (azooKey pattern), set from `viewDidAppear` + `viewDidLayoutSubviews`.
- `KeyTouchLayer` hit-surface hardening (Tasty pattern): `isOpaque=false`, empty `draw(_:)`, and a `hitTest` that claims the layer for plain keys + transparent gaps (while still returning interactive globe/legacy-`-3` subview hits).

**Defense-in-depth main-thread reductions (helped, but were not the primary cause):**
- Reuse the key-preview view instead of rebuilding it per keystroke (`6ed2c884`) — the preview was allocating a `UIView` + `CAShapeLayer` + adding to the window every keystroke.
- Commit plain keys on `touchesBegan` not release (`d97d52f3`) — a dropped `touchesEnded` can no longer silently swallow a key.
- **P1 of this doc (diffable `rebuildButtons`) finally landed** (`4daae397`, docs/IOS_OPT_TOUCH.md Task 6): grow/shrink/configure-in-place instead of teardown-all. Still worthwhile for Chinese/candidate-active bursts and battery, just not the root of the misses. **P2 (defer one runloop) was NOT needed** and left unimplemented.

**Hypothesis (4) update (2026-05-24 multi-touch).** The five scattered `isMultipleTouchEnabled = true` flags were subsumed by the rewrite: multi-touch now lives on each `KeyTouchLayer` that can own a touch, and the scattered container flags were removed in the rewrite's P4. Rollover is handled by one `TouchTracker` per `UITouch`. P5 in IOS_TOUCH_REWRITE.md will consolidate those row/half owners into one keyboard-wide layer.

**Device outcome (WJIP17, LimeIME scheme, force-refreshed per the protocol above):** high-speed misses went from frequent → "much better" (after Phase 1) → confirmed fixed by the user. Full headless suite green (130/0).

### 2026-07-12 — Residual "less responsive than built-in" is a NEW thread, not a miss regression

The missed-key fix (2026-07-02) holds — no dropped taps. The remaining complaint (keyboard *feels* less responsive than the system keyboard) is a different symptom family — feedback latency / frame rate / `textDocumentProxy` XPC insert latency — and is tracked in [docs/IOS_TOUCH_REWRITE.md §13](IOS_TOUCH_REWRITE.md). Do not reopen this doc's hypotheses for it. First experiments applied there: key-preview fade-in 80 ms → 0, and `CADisableMinimumFrameDurationOnPhone` (ProMotion 120 Hz opt-in) added to the extension Info.plist.

### 2026-07-13 — Gap taps expose a touch-surface coverage bug, not a return of fast-typing misses

**New reproducible symptom:** a tap on some visible space between keys produces no key
event. The iOS built-in keyboard visually separates keys but extends their logical hit
regions into the gaps, normally dividing the space at the nearest-key centerline.

This does **not** invalidate the 2026-07-02 resolution. Fast taps that land on owned key
surfaces are still delivered; the new failure occurs because the shipped touch rewrite
does not yet own and map the complete two-dimensional key grid:

- `makeRow` creates one `KeyTouchLayer` per row, constrained to that row's content width;
- `makeSplitRow` creates one layer per split half;
- `plainTouchContext(in:)` builds the existing `KeyDetector` from only that layer's
  descendant keys; and
- `KeyDetector.keyAt` can return `nil` after applying a center-distance threshold.

The result is good horizontal correction inside many rows, but it cannot guarantee
vertical/diagonal gaps, centered-row outer space, split-half boundaries, or cross-row
flint. A touch also stays with the view in which it began, so a row-local detector
cannot discover another row during `touchesMoved`.

**Planned correction:** IOS_TOUCH_REWRITE.md P5/§14 replaces the row/half owners with
one keyboard-wide `KeyTouchLayer` and reuses one instance of the existing `KeyDetector`
containing every key frame in a common coordinate system. For points inside the owned
key grid, nearest-key selection is unbounded and based on distance to the key rectangle;
therefore horizontal, vertical, and diagonal gaps resolve exactly once instead of
returning `nil`. The normal proximity threshold remains available outside that surface.

**Diagnostic distinction:** `TouchBegan` absent means the point was outside the current
row/half layer; `TouchBegan code=0` means the layer received it but its row-local detector
returned `nil`; a real code with no output is a downstream commit issue. P5 must cover
the first two signatures without changing the already-fixed delivery/commit path.

**Status:** documented and open; no Swift implementation or device verification yet.
