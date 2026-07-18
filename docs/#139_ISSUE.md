# Issue #139: iOS Keyboard Frame Becomes Stale After Live Geometry Changes

## Status

- GitHub issue: https://github.com/lime-ime/limeime/issues/139
- Classification: `bug` + `Usability`
- State: closed by the project account on 2026-07-18 after the private reporter could no longer reproduce the locked-portrait bottom-reachability failure following a complete removal and reinstall. The retained closure comment is https://github.com/lime-ime/limeime/issues/139#issuecomment-5010731741. Commit `7c067c64` fixes the LINE rotation path (shipped in 6.1.31), and commit `9dbe1a86a96fe676ac7a79e75f232673a59d3b8c` fixes the measured in-place switch-in path. Xcode Cloud run 13 passed its required tests and archive, and iOS 6.1.32 build 13 was submitted for App Store review. The private result does not prove that 6.1.32 fixed the original path because older TestFlight builds also worked after reinstall. Reopen only if the failure recurs with a new version plus the exact settings and fresh-install-to-failure sequence.
- Platform: iOS only. Android does not use the iOS custom-keyboard extension frame lifecycle.
- Source: the issue began with private email/TestFlight evidence and now also has a maintainer reproduction in LINE. Do not expose the private reporter's identity, company app details, or private videos.
- Tracked scope: host content or an input field could remain partly covered when LIME's keyboard geometry changed while the keyboard stayed visible. Dismissing and reopening the keyboard restored the correct host layout. The issue is no longer an active watch after the clean-reinstall retest stopped reproducing the private path.

## Current conclusion

The earlier fix improved LIME's calculated keyboard height and asked UIKit to update constraints when that numeric height changed. It did not resolve every live geometry transition.

Two independent host-app observations now share the same failure pattern:

1. A private scrollable form cannot reach its true bottom with LIME visible.
2. LINE's message field became partly covered after device rotation while LIME remained visible. Commit `7c067c64` fixes this rotation subcase.
3. On 6.1.31 build 11, switching directly from Apple's shorter keyboard to the taller LIME keyboard while LINE's field stays focused leaves the host at the old keyboard height, and LIME covers the entire message field. Dismissing and reopening LIME clears the overlap.
4. The private reporter's 6.1.31 retest confirms a partial improvement: landscape can now scroll to the true bottom, but locked portrait still cannot.
5. On 6.1.32, the reporter initially reproduced a preference-dependent locked-portrait failure, then completely removed and reinstalled LIME and could reach the bottom across five representative keyboard-size/font-size combinations. Older TestFlight builds also worked afterward.

These observations belong in one issue for now because both indicate that the host's usable area can become stale relative to LIME's visible top edge. They do **not** yet prove one identical code-level root cause. Split the LINE rotation case into a separate issue only if instrumentation shows that the private form receives a correct keyboard frame but mishandles its scroll inset while LINE receives a stale or incorrect frame from LIME/UIKit.

## Evidence

### Private form negative retest

The private reporter retested after the first #139 fix shipped:

- LIME: 6.1.28
- Device/OS: a recent large-screen iPhone on an iOS 26 beta. Exact values remain in the private support thread.
- Result: bottom content remained covered and the scrollbar could not reach the actual bottom.
- Keyboard size: reproduced from minimum through extra large.
- Comparison: the reporter said two other third-party keyboards did not reproduce the problem. Their names remain in the private support thread.
- Evidence: a private follow-up screen recording. The recording shows a custom scrollable form and an accessory toolbar above the keyboard. Keep the recording, exact date, and app details private.
- Host framework: the reporter's engineer confirmed that the private app uses a cross-platform UI framework and found a separate public report of iOS fields being hidden behind the keyboard with that framework. The engineer will continue investigating the host side. Keep the exact framework private. This makes host inset/scroll handling a concrete second factor to test without proving that it is the only cause.

The video is consistent with a stale host viewport or bottom inset, but it cannot by itself prove whether the stale geometry originates in LIME/UIKit or in the private host app.

### Private form 6.1.31 partial retest

The private reporter updated to LIME 6.1.31 and reported:

- Landscape now reaches the true bottom.
- Locked portrait still cannot reach the true bottom.
- This is a partial improvement, not resolution.

The orientation-specific result supports keeping the rotation fix while continuing to investigate the remaining locked-portrait and in-place keyboard-switch paths. The next comparison should distinguish opening the field directly with LIME from switching to LIME while the field remains focused, then compare the host's final scroll range or bottom inset. Keep the host framework, app identity, reporter identity, and private evidence confidential.

### Locked-portrait preference-dependent retest (2026-07-18)

The private reporter found one narrow combination that reaches the true bottom in locked portrait:

- Keyboard size: large.
- Font size: extra large.
- Result with that exact combination: the page can scroll to the true bottom.
- Result after changing either setting: the true bottom becomes unreachable again.
- Comparison: Okidokey and 元書輸入法 remain scrollable at different keyboard sizes according to the reporter.
- Evidence: one private QuickTime recording (`0718測試.mov`, approximately 10.2 MB). Keep the file, attachment metadata, reporter identity, and private app details confidential.
- Version limitation: the accessible Gmail body was empty and its snippet did not state the tested LIME version, so do not attribute this result to a specific build until confirmed from the thread or reporter.

This initial result was superseded by a same-day clean-reinstall retest. The reporter confirmed that the initial test used 6.1.32, then completely removed LIME and reinstalled 6.1.32. After reinstall, the true bottom was reachable with all five tested combinations: extra-large keyboard/small text, large keyboard/extra-large text, normal/normal, small/extra-small, and extra-small/large. The reporter also installed older TestFlight builds and observed the working behavior there.

The clean-reinstall result means the current evidence cannot attribute recovery to the 6.1.32 code change or to one preference combination. The next useful evidence is a recurrence with the exact sequence from fresh install to failure, including whether the host app was restarted, whether LIME settings or layouts changed, and whether the field was opened directly with LIME or reached through an in-place keyboard switch. If the failure recurs, compare persisted LIME preferences/runtime state and host inset state before and after reinstall rather than implementing a size-specific workaround.

### Maintainer LINE rotation reproduction

The maintainer reported a separate, public-app reproduction:

1. Open a LINE conversation.
2. Focus the message field with LIME visible.
3. Rotate the device while keeping the keyboard open.
4. LIME's top edge covers roughly half of LINE's message field.
5. Rotate back to the original orientation.
6. The overlap remains.
7. Dismiss and reopen the keyboard.
8. LINE positions the message field correctly again.

The recovery behavior is important: reopening the keyboard forces a fresh keyboard presentation and clears the stale geometry. This makes a live rotation/frame-publication failure more likely than a permanently incorrect static keyboard height.

Record the exact device, iOS version, LIME build, starting orientation, ending orientation, active LIME layout, and `keyboard_size` before claiming a complete reproduction matrix.

### Maintainer LINE in-place keyboard-switch negative retest

The maintainer tested the #139-fixed 6.1.31 build 11 and found a second live-transition failure:

1. Focus LINE's message field with Apple's built-in keyboard visible.
2. Switch directly to LIME without dismissing the input session.
3. LIME is substantially taller than the Apple keyboard.
4. LINE's composer remains positioned for the shorter Apple keyboard, so the taller LIME keyboard covers the whole message field.
5. Dismiss and reopen LIME.
6. LINE then positions the composer correctly.

This proves that attempt 11 resolved the rotation transaction but did not resolve every in-place keyboard-height transition. Track this under #139 rather than opening a separate LINE issue because the observable contract is the same: the host retains stale geometry until a fresh keyboard presentation.

#### In-place switch instrumentation results (2026-07-16) — initial (superseded) conclusion

The DEBUG geometry probe was restored and the switch path instrumented (`viewWillAppear`/`viewDidAppear` hooks added). Findings on the physical test iPhone:

- **Control (direct open with LIME):** correct — fresh presentation posts `WillShow inset=404`, probe field CLEAR.
- **LIME → Apple switch:** correct — host receives `WillChangeFrame inset=345/335`.
- **Apple → LIME switch:** **total silence.** LIME loads and settles (view 312, `enc=312`) and the host receives *no* notification, *no* layout-guide update, nothing. The host's last knowledge is Apple's 335; LIME's true reported frame is 404 → 69 pt coverage — the full composer in LINE.
- Three extension-side channels were then tested post-appearance and **all were ignored by iOS** (no `enc` re-derivation, no host notification, view pinned at 312):
  1. a height-constraint dip (first attempt was reverted by `applyHeight` before rendering; repeated with the settle-gate held and iOS still ignored the changed constraint for the full 200 ms hold);
  2. the private `enc` constraint (frozen);
  3. `preferredContentSize` (the remote-view-controller sizing channel — silent).
- External research confirms the mechanism is documented platform behavior: **iOS posts keyboard notifications only on first-responder/input-session changes, not on globe switches to custom keyboards** (community-documented; consistent with every probe run).

~~Superseded~~ — the paragraph below originally concluded this was an unfixable iOS limitation. The maintainer's counter-evidence (the reporter's “other keyboards work”) prompted a Gboard control test: Gboard is TALLER than Apple's keyboard and its switch-in IS announced to hosts — falsifying the “iOS limitation” conclusion and reopening the investigation, which produced the fix in “RESOLVED: in-place switch-in” below.

#### RESOLVED: in-place switch-in — attach-overshoot fix (probe- and LINE-verified 2026-07-16)

System-log analysis (`sudo log collect --device`, comparing a failing Apple→LIME switch against a working Apple→Gboard switch) exposed iOS's host-side machinery in `_UIRemoteKeyboards` / `UIPeripheralHost` and three rules:

1. At switch-commit the host computes a **provisional frame** for the incoming custom keyboard (318 on iPhone 17 Pro Max) and announces that; the host app's own process posts only `didShow`.
2. The provisional is **corrected only by a post-attach RESIZE EDGE** of the keyboard's remote proxy view — Gboard's proxy grows 243→274 at +85 ms, which triggers a second `prepareToMoveKeyboard` and the full corrected `willShow`/`WillChangeFrame` cascade. LIME arrived at its final height with no post-attach resize → no edge → never corrected.
3. iOS sizes the attaching view from **its own memory** of the keyboard's height (enc goes straight to the remembered 312 regardless of the declared constraint) and pins it once the attach transaction settles — so an edge can only be produced by targeting a height **above the remembered value** while the transaction is **still open**.

**The fix** (`KeyboardViewController`): at `viewDidAppear` — mid-attach, transaction open — set the height to `kbTarget + 20` (`attachOvershoot`); the first layout pass where the view has rendered at the overshoot size restores `kbTarget`; a 0.5 s fallback guarantees the keyboard never sticks tall. Both changes are honored and announced: probe log shows `WillChangeFrame inset=424` (overshoot) then `inset=404` (true frame), ending `overlap=-4`; the notification-latching probe host ends CORRECT, and the **LINE reproduction passes** (Apple→LIME switch leaves the composer fully visible). Cosmetic cost: a brief ~20 pt grow-shrink bounce on every keyboard appearance.

Failed variants for the record (all probe-falsified): below-stored short-attach, post-settle dip/grow at any delay, constraint reinstall, `preferredContentSize`, `allowsSelfSizing`, early height declaration at `viewDidLoad` (+80 ms is process-spawn-bound and iOS ignores the declaration anyway). The one working cell is above-stored × mid-transaction.

#### MAUI repro verification (2026-07-17) — reporter's form scenario passes on the fixed build

A minimal .NET MAUI iOS app was built to reproduce the reporter's environment first-hand: stock MAUI (`KeyboardAutoManagerScroll` untouched), one `ContentPage` with a `ScrollView` of 20 labeled `Entry` fields and a marked bottom entry, portrait. On the physical test iPhone running the switch-in-fixed LIME build, the true bottom is reachable and the bottom entry editable in **both** flows:

- Apple keyboard focused → in-place switch to LIME (the previously-failing path), and
- fresh open with LIME active.

This predicts the reporter's locked-portrait case resolves with the next LIME release. If their retest still fails, the remaining factor is app-specific on their side (e.g. their custom accessory toolbar or a modified keyboard-scroll handler) — the host-side guidance below still applies.

Repro recipe (the project itself is a throwaway; rebuildable in minutes): `dotnet workload install maui-ios`, `dotnet new maui` (retarget csproj to `net10.0-ios` only and add `<PackageReference Include="Microsoft.Maui.Controls" Version="10.0.20" />`), replace `MainPage` with the ScrollView form (20 entries + bottom marker entry), build the csproj with `-p:RuntimeIdentifier=ios-arm64 -p:CodesignKey="Apple Development: …" -p:CodesignProvision="iOS Team Provisioning Profile: *"`, install with `devicectl`.

#### Host-side escape hatch (probe-verified 2026-07-16) and the MAUI connection

The reporter's engineer disclosed the private app uses **.NET MAUI**. MAUI's `KeyboardAutoManagerScroll` (dotnet/maui, `src/Core/src/Platform/iOS/`) observes **only `UIKeyboardWillShowNotification`** and caches the frame from it — so a switch-in, which posts *no* notification, leaves MAUI stale forever. This also explains the 6.1.31 partial retest: rotating emits our deferred post-rotation apply's notification burst (which includes `WillShow`) → MAUI re-adjusts → landscape heals; locked portrait never receives any late notification → stays stale.

The probe then measured a working recovery path: iOS **does** fire `UITextInputMode.currentInputModeDidChangeNotification` on every globe switch, and although `keyboardLayoutGuide` is stale at that instant (still the previous keyboard's frame), **it reads the correct new frame ~0.5 s later after a forced layout pass** (measured: `guideTop=611` at fire → `guideTop=552` = LIME's true top at +0.5 s; verified in both switch directions). iOS holds the correct frame internally the whole time — it just never pushes it.

Actionable host-side fixes to relay to the reporter (public-safe, no private details needed):

1. **App-level workaround (5 lines):** observe `currentInputModeDidChangeNotification`; when it fires with a field focused, resign and re-acquire first responder on that field. The new input session posts a fresh, correct `WillShow`, which MAUI's existing code consumes. Minor caret flicker.
2. **Proper fix (MAUI-level, worth an upstream issue):** on `currentInputModeDidChangeNotification`, after ~0.5 s, force a layout and re-derive the keyboard frame from `view.keyboardLayoutGuide.layoutFrame` instead of waiting for a `WillShow` that never comes.

Ask the reporter to confirm the discriminator first: does locked portrait fail when the field is opened **directly with LIME already active** (should be fine — fresh presentations are correct), or only after **switching to LIME while the field is focused** (the measured gap)?

## Historical scope no longer active

The original email also discussed numeric-field routing and keyboard-size behavior for Array10. Those are not the active #139 defect:

- A June 29 simulator investigation did not reproduce the reported numeric-field routing symptom: the tested fields either kept LIME active or were replaced by the iOS system keyboard before LIME could select an internal layout. This limited simulator result does not establish universal iOS behavior; reopen the numeric-routing scope if new real-device evidence appears.
- `keyboard_size` must remain authoritative for visual row sizing.
- Do not shrink or cap tall layouts to hide host-content coverage.

The tracked defect was dynamic keyboard-frame publication and host adjustment after live geometry changes. The issue is now closed pending a new recurrence with an exact reproduction sequence.

## Previous fix and why the issue remained open before final closure

Commit `f7088f2853a692dd930bba02c52bd6d99e3a2b8a` (`#139 fix real iOS keyboard height reporting`) shipped in iOS 6.1.28.

It changed `KeyboardViewController.applyHeight()` so that:

- `KeyboardView.preferredHeight` remains the source of the rendered rows' height.
- The keyboard root height constraint is updated when `totalHeight` changes.
- `publishKeyboardHeightToUIKit()` calls `setNeedsUpdateConstraints()` on `view` and `inputView` when the numeric height changes.
- The abandoned `effectiveScale` cap is not used.

That fix addressed stale numeric height constraints after layout changes. The private 6.1.28 negative retest and the LINE rotation reproduction show that the broader lifecycle problem is not resolved.

## Current implementation path

Relevant code is in `LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift`.

### Rotation/layout path

`viewWillLayoutSubviews()` currently:

1. synchronizes the layout environment from traits
2. reloads the active layout when needed
3. derives orientation from `UIScreen.main.bounds`
4. sets `keyboardView.isLandscape`
5. updates split mode
6. calls `applyHeight()`

`traitCollectionDidChange(_:)` calls `applyHeight()` for selected trait/size-class changes.

There is no explicit `viewWillTransition(to:with:)` path that records the transition target and republishes final geometry after the rotation coordinator completes.

### Height publication path

`applyHeight()` computes:

```text
keysHeight = keyboardView.preferredHeight
barHeight = activeCandidateBarHeight
keyboardHeight = emojiSearchHeaderHeight + barHeight + keysHeight
totalHeight = max(keyboardHeight, emoji panel height when applicable)
```

It updates the root height constraint and calls `publishKeyboardHeightToUIKit()` only when the numeric constraint changes by more than 0.5 points or the constraint is first created.

`publishKeyboardHeightToUIKit()` currently performs:

```swift
view.setNeedsUpdateConstraints()
inputView?.setNeedsUpdateConstraints()
```

This means a width/orientation/frame transition that requires republishing geometry but produces the same numeric height can skip publication entirely. It also means publication can occur during an intermediate rotation layout pass rather than after the final orientation/layout state. These are investigation targets, not yet confirmed causes.

## Root-cause hypotheses to test

Do not implement a fix until instrumentation selects one hypothesis.

### H1: transitional orientation is used

`viewWillLayoutSubviews()` reads `UIScreen.main.bounds`. During rotation, the keyboard extension may receive a layout pass before screen bounds represent the final target orientation. LIME may rebuild or report height for an intermediate state and never publish the final state.

### H2: publication is incorrectly gated only by numeric height

The keyboard width, row metrics, or host frame can change while `totalHeight` remains numerically equal. Because `publishKeyboardHeightToUIKit()` is gated by `didChangeHeight`, UIKit may not receive a fresh constraints update for the final frame.

### H3: preferred height is sampled before the final row rebuild

`keyboardView.isLandscape` triggers a rebuild. If `preferredHeight` or constraints are sampled before that rebuild reaches its final geometry, the root constraint can remain synchronized to the previous layout until the keyboard is recreated.

### H4: host-specific inset handling is a second problem

The private app may independently cache a keyboard inset or observe only show/hide notifications. The LINE reproduction reduces the likelihood that the entire issue is private-app-only, but instrumentation must still distinguish an extension-frame problem from a host scroll-inset problem.

## Instrumentation results (2026-07-15) — hypothesis resolved

Instrumented on a physical iPhone 17 Pro Max test device with a DEBUG-only geometry probe:

- **Extension side** (`GeoProbe` logging in `KeyboardViewController`): `viewWillLayoutSubviews`, `applyHeight`, `publishKeyboardHeightToUIKit`, a new `viewWillTransition(to:with:)`, plus LIME's rendered frames.
- **Host side** (`GeometryProbeHostVC`, 資料庫 tab, DEBUG only): a LINE-style composer whose bottom inset is cached **only** from `keyboardWillChangeFrame` (not `keyboardLayoutGuide`), which reproduces the coverage objectively (`overlap > 0`) without needing LINE.

### What the data shows

Portrait ⇄ landscape, keyboard held visible, LIME active, `keyboard_size` default:

| State | LIME declares | LIME renders (`barBot`/`kbBot`/`view`) | iOS reports to host (`keyboardLayoutGuide` **and** `keyboardWillChangeFrame`) |
| --- | --- | --- | --- |
| Clean portrait show | 312 | bar 58, keys→312, view 312 (exact) | keyboard top 552, height **404** (adds a ~92 pt bottom band) — correct, `overlap=-4` |
| Portrait after rotate-back | 312 (unchanged) | 312 (unchanged, exact) | keyboard top ~632, height **~324–370** — **~80 pt short**, `overlap=+13…+30` covered |

### Conclusions (each backed by the log)

1. **LIME's geometry is correct and constant through rotation.** `view.bounds`, `preferredHeight`, and the height constraint all converge to the right values (312 portrait / 232 landscape); `didTransition` confirms the settled state. **H1, H2, H3 are all ruled out** — screen bounds are always the final orientation, every rotation changes numeric height so `publish` fires, and the final rows are correct.
2. **LIME renders exactly what it declares.** `barBot=58`, `kbBot=312`, `view=312`: candidate bar + keys fill the declared height with **zero overflow and zero under-declaration**. The asymmetric candidate layout is **not** involved.
3. **The 92 pt is iOS's own bottom safe-area band**, included in the keyboard frame iOS reports to hosts. After rotation iOS drops ~80 pt of it from the *reported* frame while still rendering the keyboard in the correct place — so a host positioning from the notification (or the layout guide, which is **also** stale — an earlier "guide-based hosts are immune" reading was an artifact of comparing the guide against itself) places its field ~80 pt too low and the real keyboard covers it.
4. **Not fixable from the extension via geometry.** SEVEN post-rotation fixes were tried and **all produced the identical stale result** — the settled portrait host notification is `inset=370` every time (correct is 404), `overlap≈+30`, covered:
   1. `setNeedsUpdateConstraints` immediately after settle;
   2. a 1 pt height-constant jiggle timed to when `view.bounds` first equals the constraint;
   3. the same, delayed 350 ms clear of the rotation transaction;
   4. a render-forced (`layoutIfNeeded`) jiggle held ~50 ms;
   5. a full constraint **reinstall** (deactivate old, activate a fresh `heightAnchor` constraint) plus `invalidateIntrinsicContentSize()` on `view` and `inputView`;
   6. `inputView.allowsSelfSizing = true` (the Apple pattern for a self-sizing input view) — no effect on rotation, and it added a show-time height overshoot (keyboard momentarily reported 772 pt);
   7. resizing the keyboard **inside** the rotation coordinator's `animate(alongsideTransition:)` block — the log shows the view still stays at the landscape height (232) through the whole transition and only reaches 312 *after* `didTransition`, because iOS drives the input-view size during rotation and LIME's constraint doesn't win until it completes.

   The reported `inset` (370 / 387) never moves regardless of the constraint object, sizing mode, or timing. iOS computes the post-rotation keyboard frame **without consulting LIME's geometry** and only re-derives it on a **fresh presentation** (host restarts its input session → `keyboardWillShow` with the correct 404). The extension has no API to trigger that on the host (it doesn't own the first responder). The layout guide *does* recover to the correct value after rotation, so **guide-based hosts self-heal; only notification-based hosts (LINE) stay covered.** This is a **UIKit post-rotation keyboard-frame reporting defect**, not a LIME bug.

### External research + the encapsulated-constraint measurement (closes the case)

Web research (Apple DTS forum thread 799003, the archagon 3rd-party-keyboard writeup, iOS-9 height-constraint threads) converges on one named culprit: iOS installs its own **required (priority 1000)** height constraint, `UIView-Encapsulated-Layout-Height`, and the system reports the keyboard frame from **that**, not from a third-party 999-priority constraint. Community fixes (Bitmoji/Wispr "offset trick") read/reconcile against it. The research also independently **confirmed two dead ends**: Apple DTS states `allowsSelfSizing` is for input *accessory* views, not keyboards (attempt 6), and the whole community documents custom-height-constraint + rotation as a known landmine.

This produced a concrete, falsifiable hypothesis — *the stale value lives in `UIView-Encapsulated-Layout-Height`, which none of the seven attempts touched* — so the probe was extended to log that constraint's constant (`enc=`). **Measurement disproves it:** after rotating back to portrait, `constraint=312 enc=312 view=440x312` — LIME's constraint, iOS's encapsulated constraint, and the view are **all correct at 312** — yet the host notification still reports `inset=387` (`overlap=+13`, covered). The stale value is in **none** of the accessible constraints; it lives solely in UIKit's keyboard-frame *notification* computation, which reads the constraints correctly but publishes an independent stale frame and never re-fires. There is no constraint to rewrite. The case is closed as a UIKit defect with every accessible lever measured.

### Rotation subcase resolved — deferred post-rotation height application (attempt 11, probe-verified 2026-07-15)

The "accept as UIKit limitation" disposition was **wrong** — the reporter's evidence that two other third-party keyboards and the built-in keyboard survive rotation meant the failure was LIME-specific and fixable. Three more attempts followed:

8. Writing iOS's `UIView-Encapsulated-Layout-Height` (`enc`) constant inside `applyHeight()` — **crashed the extension** (mutating it during the layout pass → infinite relayout → watchdog kill). Never mutate `enc` from a layout pass.
9. The same `enc` write, done once in the rotation-completion callback — landed before the notification fired (`enc=312` at `didTransition`), **iOS still published the stale frame**: the notification value is not read from `enc` at fire time.
10. Content-driven height (explicit height constraint moved from `view` to `keyboardView`; view height derived from the subview chain) + required (1000) priority — still stale; during rotation AutoLayout resolves the required-vs-`enc` conflict in the system's favor and the view stays at the old height past the last notification.

**Root cause for the rotation subcase:** every failing rotation variant changed the keyboard height *during* the rotation transaction (from `viewWillLayoutSubviews` mid-rotation). iOS emits its keyboard-frame notifications inside that transaction using the view's current/interpolated size, then silently applies LIME's new height afterward with **no further notification**. The build 11 Apple-keyboard-to-LIME negative retest disproves the assumption that every non-rotation transition, specifically an in-place input-mode keyboard switch, is already safe. Within-LIME stationary changes such as `keyboard_size` and emoji-panel resizing remain separate matrix cases to verify.

**Fix (attempt 11):** `rotationSettling` flag — `viewWillTransition(to:with:)` sets it; `applyHeight()` holds the existing height constant while it is set; 0.3 s after the rotation coordinator completes, the flag clears and `applyHeight()` runs once, applying the new orientation's height as a plain stationary change. The probe log confirms the previously-missing notification now fires with the correct settled frame in both orientations (portrait `inset=404`, landscape `inset=253`, final `overlap=-4`).

Cosmetic trade-off: the keyboard keeps the previous orientation's height for ~0.3 s after rotation, then snaps to the correct height — that snap *is* the host notification. A rapid double-rotation inside the 0.3 s window can momentarily apply mid-rotation, but the second rotation's own deferred apply self-heals it.

**LINE rotation retest: PASSED** (maintainer, physical test iPhone, 2026-07-15) — message field stays fully visible through portrait ↔ landscape ↔ portrait without dismissing the keyboard.

**Historical LINE in-place keyboard-switch retest: FAILED on 6.1.31 build 11** — switching from Apple's shorter keyboard directly to the taller LIME keyboard left LINE's composer at the old height and LIME covered the entire field until dismiss/reopen. This kept #139 unresolved after the rotation-path pass until the later attach-overshoot fix and verification recorded above.

At that stage, the reporter was told that LIME still had a concrete adjustment path and that the deferred post-rotation fix was planned for the next 6.1.31 TestFlight build. Later investigation produced the attach-overshoot fix, and the final clean-reinstall retest stopped reproducing the private form failure. If a future report recurs, compare fresh keyboard presentation against in-place keyboard switching and collect privacy-safe host frame/inset diagnostics before attributing the behavior to LIME or the host framework alone.

- The DEBUG probe (`GeoProbe`, `geoDump`, `GeometryProbeHostVC` + 資料庫-tab viewer) was **stripped before commit** — it never entered git history. Restoration snippets: `.claude/txt/139-geometry-probe-restoration.md`. Re-add them if the private reporter's no-rotation case reproduces on the fixed build.

## Required diagnostic harness

Add a DEBUG-only host screen to the containing LIME app. It should not ship in release UI.

### LINE-style composer probe

- Add a bottom message field with a visible border.
- Anchor one copy to `view.keyboardLayoutGuide.topAnchor`.
- Keep the field focused while rotating portrait → landscape → portrait.
- Detect and display any overlap between the field's converted frame and the keyboard layout guide.

### Scrollable-form probe

- Add a long `UIScrollView` with fields extending below the initial viewport.
- Put an editable field and a visible marker at the true bottom.
- Keep LIME visible while rotating, changing LIME keyboard size, switching between four-row and five-row layouts, and switching input modes.
- Verify the final marker remains reachable without dismissing the keyboard.

### Notification and geometry logging

Record, with timestamps and a transition reason:

```text
orientation and target orientation
UIScreen.main.bounds
keyboard extension view.bounds
inputView.bounds
keyboardView.preferredHeight
activeCandidateBarHeight
computed totalHeight
keyboardHeightConstraint constant before/after
view.bounds and inputView.bounds after the final layout pass
host keyboardLayoutGuide frame
keyboardWillChangeFrame begin/end frames in host coordinates
keyboardDidChangeFrame end frame
host scroll contentInset.bottom
host scroll adjustedContentInset.bottom
active layout id, row count, keyboard_size, split mode, emoji/search state
```

Do not log typed text, candidate contents, document context, reporter data, or other private input.

## Reproduction matrix

Run each case without dismissing the keyboard between geometry changes:

| Host | Transition | Expected |
| --- | --- | --- |
| DEBUG LINE-style probe | portrait → landscape | message field remains completely above LIME |
| DEBUG LINE-style probe | landscape → portrait | message field remains completely above LIME |
| DEBUG scroll probe | portrait → landscape → portrait | true bottom remains reachable |
| DEBUG probes | minimum → extra-large `keyboard_size` | host guide/inset follows every final keyboard height |
| DEBUG probes | four-row ↔ five-row layout | no covered content and no stale gap |
| DEBUG probes | candidate bar ↔ expanded candidates | host guide/inset follows the final panel height |
| DEBUG probes | keyboard ↔ emoji/search modes | no covered content and no stale gap |
| DEBUG probes | arrow row off ↔ on | host guide/inset follows the added or removed row |
| DEBUG probes | active input mode and split-mode changes | final keyboard frame remains synchronized with visible rows |
| LINE | portrait → landscape → portrait | message field is never covered |
| LINE | rotate, then dismiss/reopen control | reopening causes no geometry correction because geometry was already correct |
| LINE | Apple keyboard → taller LIME without dismissing | composer follows LIME's final top edge and remains fully visible |
| LINE | switch, then dismiss/reopen control | reopening causes no geometry correction because the in-place switch was already correct |
| Private app, if available | same transitions | reported final form content remains reachable and the scrollbar reaches its true bottom |
| Apple keyboard / another third-party keyboard | same transitions | collect comparison frames and notifications |

The most useful control is the difference between rotating while the keyboard remains visible and dismissing/reopening after rotation.

## Verification criteria

A fix is acceptable only when all applicable conditions hold:

1. After every live transition, LIME's visible top edge matches the host's keyboard layout guide/final keyboard frame.
2. LINE's message field remains completely visible through portrait ↔ landscape rotations.
3. Rotating back does not preserve stale overlap.
4. Dismissing and reopening the keyboard does not alter host geometry because the live geometry was already correct.
5. The DEBUG scroll probe can reach its true bottom at all supported keyboard sizes and representative four-row/five-row layouts.
6. `keyboard_size` continues to change both visual row height and the published keyboard height together.
7. No fixed cap, forced shrink, or layout redesign is introduced.
8. Candidate, emoji/search, arrow-row, split/orientation, and input-mode changes do not leave covered content or stale gaps.
9. Real-device verification covers the maintainer's LINE reproduction. Simulator/source-inspection tests alone are insufficient.

## Test coverage gaps

Existing tests verify static height math and source structure:

- four-row and five-row `KeyboardView.preferredHeight`
- `keyboard_size` scaling
- arrow-row contribution
- presence of `publishKeyboardHeightToUIKit()`
- absence of the abandoned effective-size cap

They do not verify UIKit's final keyboard frame after rotation or the host app's keyboard layout guide/inset. Add a focused policy/unit test only after the publication rule is defined, and add a real UI/device rotation test or instrumented manual gate for the lifecycle behavior.

XCUITest may automate orientation with `XCUIDevice.shared.orientation`, but selecting and retaining a third-party keyboard can be environment-dependent. Keep a documented real-device manual gate even if simulator automation is added.

## Non-fixes

Do not:

- shrink or cap LIME's keyboard to match another keyboard
- mutate `KeyboardView.keySizeScale` inside height publication
- hardcode a universal keyboard height
- blame the private host app without comparing final keyboard frames
- call `layoutIfNeeded()` recursively from `viewWillLayoutSubviews()`
- force repeated layout passes without first identifying which final geometry signal is missing
- treat dismissal/reopen as an acceptable workaround for release closure

## Platform impact

### iOS

Confirmed scope. The issue concerns `UIInputViewController`, UIKit keyboard-frame publication, rotation, and host-app viewport adjustment.

### Android

Not affected by this iOS lifecycle path. Android uses its own IME window/insets model. No Android source change or retest is required unless a future fix touches shared layout metrics.

## Public and private communication

- Publicly describe the private report only as a bottom-content reachability problem reproduced after 6.1.28.
- Do not publish the reporter identity, company app, email address, or private recordings.
- The LINE reproduction may be documented publicly without private conversation content.
- The framework question was answered (.NET MAUI); keep the framework private in public channels per the reporter's preference.
- Maintainer-side verification passed for LINE rotation, LINE switch-in, and the minimal MAUI-form bottom-reachability repro without dismissing/reopening LIME. The private reporter's 2026-07-18 clean-reinstall result shows all tested size combinations working, including on older TestFlight builds, so do not claim 6.1.32 fixed the private path. The project account closed the issue after that result. Reopen only if a future private report supplies the new version, settings, and exact fresh-install-to-failure sequence needed to isolate the persisted or transient state.

### Historical draft reply to the private reporter

Do not send this release-oriented draft after closure. Reuse only relevant technical details if the private reporter reports a recurrence. The text is public-safe, contains no reporter/app identity, and may be translated as needed.

> Thank you again for the detailed retest — the landscape-vs-locked-portrait result was the clue that cracked the remaining case.
>
> We found and fixed a second LIME-side issue. When switching to LIME in place (globe key) from a shorter keyboard, iOS never announced LIME's real frame to the host app, so apps that track the keyboard via the `keyboardWillShow` notification — which is exactly how .NET MAUI's built-in `KeyboardAutoManagerScroll` works — kept the previous keyboard's inset. In portrait that leaves part of the form hidden behind LIME with no way to scroll to it; rotating generates a fresh notification, which is why landscape recovered after our earlier fix while locked portrait did not.
>
> To make sure this matches your app's behavior, we rebuilt the scenario in a minimal stock .NET MAUI form (a scrollable page of entries with a marked bottom field). On the fixed build the true bottom is reachable and editable in both flows — opening a field with LIME already active, and switching to LIME while a field is focused.
>
> Please retest with LIME <VERSION> in locked portrait, using both flows above. If anything still cannot be reached, the remaining factor is likely app-specific (for example a custom keyboard accessory/toolbar or modified keyboard-scroll handling); in that case, two host-side options we verified: (1) on `UITextInputMode.currentInputModeDidChangeNotification`, re-read `view.keyboardLayoutGuide` after a short delay and a forced layout — it holds the correct frame even when no notification fires; or (2) resign and re-acquire first responder on the focused field, which produces a fresh, correct `keyboardWillShow`.
>
> Thanks for your patience — your comparison against other keyboards was what disproved our early "platform limitation" theory and pushed us to the real fix.
