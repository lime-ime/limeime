# Safari / Gemini Extra Space Above Keyboard

This document tracks the intermittent extra top space / rounded host rectangle
seen above LimeIME's candidate bar in Safari, Google search, and Gemini input
fields.

The candidate-bar geometry itself is documented in
[CANDI_LAYOUT.md](CANDI_LAYOUT.md). This document is only about the iOS / host
keyboard-container area outside LimeIME's candidate-bar layout.

---

## Bug Statement

Some host input fields show an extra rounded top area above LimeIME's candidate
bar. It looks like a gap or a rounded rectangle sitting between the app content
and the keyboard.

Observed behavior:

- Safari URL bar can show no extra top space.
- Google search and Gemini input can show the extra top space again.
- Apple's built-in iOS phonetic keyboard does not show the same extra top space
  in the observed case.
- Closing and reopening the keyboard can change the visual result.
- Painting opaque LimeIME pixels can hide the symptom in one host path, then the
  same host/app or another input field can show it again.

Desired behavior:

- The top edge above LimeIME's candidate bar should remain consistently tight.
- The keyboard background should keep the iOS keyboard's blended bottom/action
  row appearance.

Current conclusion:

The visible extra top space is system / host padding outside LimeIME's keyboard
input view area. The DEBUG geometry log proves LimeIME's root input view,
candidate bar, and composing label are already flush at `y = 0` inside the
extension window. The built-in Apple phonetic keyboard not showing the same gap
suggests the behavior is specific to the third-party keyboard hosting path, not
to the text field alone.

Important correction from the user screenshots: the `up / down / done`
form-assistant row above the keyboard is real host UI, but it is not the
specific LimeIME artifact being fought here. The target artifact is the rounded
dark host container / padding immediately around LimeIME's third-party keyboard
surface on iOS 26.

Second correction from the iOS 26.4 simulator repro: when LimeIME is active, a
visible blank band can also appear at the top of LimeIME's own
candidate/composing bar. That band is not the 68 pt WebKit accessory row. It is
also not the whole iOS host container. It is at least partly explained by
LimeIME's `CandidateBarView` reserving a composing-keyname strip while keeping
the bar background clear and shifting candidate glyphs down by half the strip
height.

---

## Geometry Proof

A bad case was measured with temporary DEBUG logging:

```text
view.frame=(x:0.0,y:0.0,w:440.0,h:313.3)
view.bounds=(x:0.0,y:0.0,w:440.0,h:313.3)
viewInWindow=(x:0.0,y:0.0,w:440.0,h:313.3)
window.bounds=(x:0.0,y:0.0,w:440.0,h:313.3)
candidateBar.frame=(x:0.0,y:0.0,w:440.0,h:59.3)
candidateBarInWindow=(x:0.0,y:0.0,w:440.0,h:59.3)
keyboardView.frame=(x:0.0,y:59.3,w:440.0,h:254.0)
composingLabel=(x:28.0,y:0.0,w:367.0,h:27.0)
heightConstraint=313.4
barHeightConstraint=59.4
```

Interpretation:

- `viewInWindow.y == 0`: LimeIME's root input view starts at the top of its
  keyboard extension window.
- `candidateBar.frame.y == 0`: the candidate bar starts at the top of LimeIME's
  root view.
- `candidateBarInWindow.y == 0`: the candidate bar is flush with the extension
  window.
- `composingLabel.frame.y == 0`: the composing keyname / reverse lookup label is
  not shifted downward inside the candidate bar.
- `keyboardView.frame.y == candidateBar.height`: the key grid starts immediately
  below the candidate bar.

Therefore the extra space is not caused by LimeIME Auto Layout placing the
candidate bar too low. There is no internal LimeIME top offset to remove.

---

## Screenshot Clarification

The user-provided iOS 26 screenshots show two different host-owned surfaces:

- The `up / down / done` row above the keyboard is the web form assistant /
  accessory UI. It is adjacent to the keyboard, but it is not the target bug.
- The rounded dark container / padding immediately surrounding LimeIME below
  that row is the target artifact. This is the iOS 26 third-party keyboard host
  presentation, not LimeIME's candidate-bar layout.

Any future screenshot proof must show LimeIME visibly active and must not use
Apple’s system keyboard as a stand-in. A screenshot with the Apple system
keyboard only proves the host accessory row exists; it does not prove the
LimeIME extra-space behavior.

The iOS 26.4 simulator repro adds one more visible layer:

- A blank gray band at the top of LimeIME's candidate/composing bar itself.
  `CandidateBarView` currently pins the bar to `view.topAnchor`, but internally
  reserves `LayoutMetrics.ComposingPopup.stripHeight` for the keyname strip and
  biases candidate buttons downward by `composingStripHeight / 2`. Because the
  bar background is `.clear`, this reserved area blends into the rounded host
  backdrop and looks like extra top space above the composing keyname / candidate
  row.

So there are three things that must not be conflated:

- WebKit form assistant: the `up / down / done` row.
- iOS 26 host container: the rounded third-party keyboard backdrop.
- LimeIME candidate-bar reserve: the blank top band inside the clear
  candidate/composing bar.

### iOS 26.4 simulator screenshot proof

Computer Use was used on the globe key to select / reach `萊姆輸入法` directly.
The active keyboard then showed LimeIME's mixed Latin/Zhuyin keycaps, and the
globe key's next-keyboard value changed to `繁體注音`. The proof that the
screenshot is LimeIME-active is the simultaneous simulator log:

```text
2026-05-07 00:18:10.321 ... Successfully spawned LimeKeyboard[76781]
2026-05-07 00:18:10.323 ... got pid from ready request: 76781
2026-05-07 00:20:17.168 ... Activity: viewControllerAppearance; appearState: appeared
```

Screenshot captured from that state:

```text
.Codex/txt/limeime-keyboard-active-direct-select.png
```

What this proves on iOS 26.4:

- The active keyboard extension was `org.limeime.keyboard`, not Apple's
  system keyboard.
- The `up / down / done` row was still host/WebKit form-assistant UI above the
  keyboard. It is not the LimeIME bug.
- The rounded keyboard host backdrop is still outside normal LimeIME key layout
  ownership. LimeIME can draw inside its extension view; it cannot directly
  remove a host-owned accessory row or outer host container.

---

## What We Tried And Why It Failed

### 1. Candidate layout tuning

An early attempt treated the issue as candidate-bar internal layout and adjusted
candidate button / pill geometry.

Result: failed and reverted. It affected candidate spacing and expanded-panel
parity, but the measured top coordinates showed the candidate bar was already
flush.

### 2. DEBUG geometry logging

Temporary logging compared the root view, window, candidate bar, key grid, and
composing label frames.

Result: useful, then removed. It proved the extra space is not inside the
candidate bar or inside the extension window's measured layout.

### 3. Solid candidate bar background

Changing `CandidateBarView.backgroundColor` from `.clear` to a solid palette
color appeared to cover the rounded host area in some cases.

Result: failed. It made the candidate area look like a flat strip and did not
solve all host paths. It was only masking pixels inside our bounds, not changing
the host's decision to reserve or draw the top area.

### 4. Root view solid background

Setting the root keyboard `view.backgroundColor` to an opaque keyboard-like
color appeared to fix the Safari URL bar path.

Result: failed. Google search and Gemini input still showed the extra top space.
This is the key evidence that background color is not the root cause. If iOS
were measuring or adding the padding because of our background color, a root
opaque background would consistently change the result. It did not.

### 5. `UIVisualEffectView` blur/material backdrop

A full-size material blur view was added behind the candidate bar and key grid
to preserve the iOS keyboard material look.

Result: failed. A blur is translucent and can still reveal host-rendered shapes.
It also introduced unsafe behavior in the hosted keyboard path.

### 6. Opaque custom backdrop subview

A plain opaque `UIView` backdrop was added behind the whole keyboard, with a
color chosen to resemble the bottom globe/mic action-row background.

Result: unsafe. On device, the keyboard extension hit an uncaught UIKit / host
exception involving `UIPeripheralHost` and `_fallbackTraitCollection`. Adding
extra root-level backdrop views inside the private keyboard host is risky.

### 7. Phone-only top cover strip / increased keyboard height

A small top cover strip was added above the candidate bar, and the root height
constraint was increased so LimeIME would theoretically own more pixels above
the previous top edge.

Result: failed. Gemini still showed the extra space. This strongly suggests the
problem area is not the top of the old LimeIME view. It is a host-owned area
above or around the input view, or the host inserts its own accessory/suggestion
container above the third-party keyboard for that focused field.

---

## Root Cause Analysis

The root cause is not candidate-bar layout, not the height constraint, and
not the keyboard background color. The geometry log proves the candidate
bar and composing label are at `y = 0` inside the extension window, and
the failed root-background experiment proves background color does not
change iOS's allocation decision.

The extra rounded top space is system / host UI drawn ABOVE LimeIME's
keyboard extension window, in a layer the extension fundamentally cannot
reach. There are several actors, and which one appears depends on the
host context and iOS version.

External research grounding this analysis is preserved in
[SAFARI_EXTRA_SPACE_PLAN.md](SAFARI_EXTRA_SPACE_PLAN.md) under
"External research synthesis (raw)".

### Actors that draw above the third-party keyboard window

1. **WKWebView form-assistant `inputAccessoryView` (Prev / Next / Done bar)**
   — owned by `WKContentView`, the private first responder inside any
   `WKWebView` (Safari page content, Gemini web app, Google search box).
   Rendered by the host app process above the keyboard. Appears for any
   focused HTML `<input>` / `<textarea>`. In the user screenshot this is the
   `up / down / done` row. It is real and host-owned, but it is not the
   rounded LimeIME extra-space artifact. A keyboard extension has zero API to
   suppress or restyle it. Removing it is only possible from the host app side,
   by swizzling `WKContentView.inputAccessoryView` to `nil`, which Safari /
   Gemini / Google do not do. The Safari URL bar is a native `UITextField`,
   not a `WKWebView` input — it does not show this bar, which explains why the
   URL-bar path can look flush while Gemini / Google `<input>` paths do not.
   - Source: rdar://27763084; Apple Developer Forums thread 81650;
     `https://www.technetexperts.com/hide-ios-input-bar/`.

2. **iOS 26 Liquid Glass keyboard host insets** — starting in iOS 26,
   host apps that adopt the Liquid Glass design (Messages, Notes, Safari,
   etc.) embed third-party keyboards inside a rounded container with
   grey margins on the left, right, and top. The margin region is owned
   by the `UIInputSetHostView` window stack and is not drawable by the
   extension. This is the best match for the user-provided LimeIME screenshot:
   LimeIME is active, the web form assistant row is above it, and the actual
   unwanted area is the rounded host container around the third-party keyboard.
   This affects both web and native hosts on iOS ≥ 26.
   - Source: Apple Developer Forums thread 800838.

3. **iOS 26 Quick Actions / App Shortcuts bar** — a new system rounded-
   rectangle bar above the keyboard in iOS 26 web form contexts, showing
   passwords / Apple Pay / location / checklist icons. System-owned; not
   developer-disable-able from the extension or the host.
   - Source: Apple Community threads 256177528, 256173518.

4. **iPad `UITextInputAssistantItem` shortcut bar** — the host text
   view's shortcut bar above the keyboard on iPad. Not relevant on
   iPhone. [IPAD_ASSIST_BAR.md](IPAD_ASSIST_BAR.md) §8.1 already
   documents that setting
   `self.inputAssistantItem.trailingBarButtonGroups` from inside
   `UIInputViewController` is silently ignored — the assistant item the
   on-screen bar reads belongs to the host text view's responder chain,
   not the extension.

### Actors that are NOT the cause

- **System QuickType / predictive-text strip.** Apple's App Extension
  Programming Guide is explicit that a custom keyboard can draw only
  within the primary view of its `UIInputViewController`. iOS does not
  inject a system QuickType strip above a third-party keyboard. Apple's
  built-in keyboard renders QuickType inside its own view, which is why
  it can blend visually; LimeIME provides its own candidate bar inside
  its own view.
- **Genmoji / Image Playground picker.** Opens a separate panel, not a
  top accessory.
- **iOS 18 Writing Tools accessory.** Selection-triggered, not always-on.
- **Password AutoFill strip on credential fields.** iOS blocks
  third-party keyboards from being selected for fields recognized for
  password AutoFill (Apple Developer Forums thread 94889), so LimeIME
  would not be active there at all — this case cannot produce the
  observed artifact.
- **Background color of the extension.** Already proven irrelevant.

### Why Apple's phonetic keyboard does not show the same gap

Apple's built-in keyboards are not hosted as third-party extensions.
They run on a private hosting path that can integrate predictive UI,
accessory chrome, and the Quick Actions strip directly with the keyboard
surface. Third-party extensions render in a separate keyboard window
that the host chrome cannot merge with, so the same accessory chrome
that visually blends above Apple's keyboard appears as a distinct
rounded rectangle above LimeIME.

### Conclusion

The full visible artifact is mixed:

- The WebKit form-assistant row is host-owned.
- The iOS 26 rounded keyboard container is host / OS-owned.
- The blank band at the top of LimeIME's own composing/candidate bar is
  LimeIME-owned visual layout, because `CandidateBarView` reserves strip space
  and paints the bar clear.

Candidate-bar geometry is therefore not the root cause of the WebKit accessory
or iOS 26 host container, but it can contribute to the extra-space perception
inside LimeIME's own bounds.

---

## How iOS Measures Our Keyboard Input View Height

Short answer: iOS measures LimeIME's keyboard input view height from Auto Layout
constraints / fitting size, not from background color.

In LimeIME, the relevant height request is in `applyHeight()`:

```swift
let totalHeight = candidateBarHeight + keysHeight
let c = view.heightAnchor.constraint(equalToConstant: totalHeight)
c.priority = UILayoutPriority(rawValue: 999)
c.isActive = true
keyboardHeightConstraint = c
```

When size-related settings change, LimeIME updates this constraint's constant.
iOS then lays out the extension root view inside the system keyboard host.

What can affect the measured input view height:

- LimeIME's root `view.heightAnchor` constraint.
- Constraint priorities and satisfiability inside the extension view.
- System maximum/minimum rules for third-party keyboards.
- Trait and size-class changes that cause LimeIME to compute a different
  `candidateBarHeight` or `keysHeight`.

What does not affect the measured input view height:

- `backgroundColor`.
- Clear vs opaque pixels.
- `UIVisualEffectView` vs plain color.
- Candidate text, button highlight, or composing-label drawing.

Background color changes only what LimeIME paints inside the height iOS already
allocated. It is not an input to iOS height measurement.

---

## Is The Extra Padding Caused By Background Color?

No.

The strongest evidence is the failed root-background experiment:

- With an opaque root background, Safari URL bar could show no extra space.
- With the same general approach, Google/Gemini still showed the extra space.

If background color were the reason iOS decided to add the padding, the result
would be deterministic for the same keyboard build. Instead, the result follows
the focused host input context.

Background color is only a reveal/mask factor:

- Clear or translucent areas can reveal host-owned rounded shapes.
- Opaque areas can cover host pixels inside LimeIME's own bounds.
- Opaque areas cannot cover padding outside LimeIME's input view.

So background color can make the symptom easier or harder to see, but it does
not cause iOS to allocate the extra top padding.

---

## What We Still Need To Learn

The unanswered question is not "where is the candidate bar?" We know it is at
`y = 0` inside the extension.

The host-owned unanswered question is:

Why does iOS 26 / the host app choose the rounded third-party keyboard host
container with extra top / side padding for Gemini or Google search, but not
always for Safari URL bar?

The LimeIME-owned unanswered question is:

How should the composing-keyname strip be drawn so it does not create a blank
top band inside the candidate bar while still avoiding tone-mark clipping and
candidate/keyname overlap?

Related question:

Why does the built-in Apple phonetic keyboard avoid this padding in the same
kind of input context, while LimeIME does not?

Possible iOS decision inputs:

- `UITextInputTraits`, such as keyboard type, return key type, autocorrection,
  spell checking, smart insert/delete, secure text entry, and text content type.
- Host-owned input accessory views or suggestion containers above the keyboard,
  which are adjacent evidence but not the target rounded-container artifact.
- Browser/search UI overlays that are visually grouped with the keyboard but are
  not part of the third-party keyboard extension view.
- Differences between URL/search fields and normal multiline/editable web fields.
- Private `UIPeripheralHost` / keyboard-host heuristics for third-party keyboards.
- The private system-keyboard path versus the public third-party keyboard
  extension path.

We probably cannot fully know the private heuristic from public API alone, but
we can collect enough evidence to classify each host path as either:

- inside LimeIME's input view, fixable by LimeIME layout; or
- outside LimeIME's input view, not directly drawable or removable by LimeIME.

The existing geometry log already puts the observed bad case in the second
category unless a future log shows different bounds. The user screenshots also
show that the iOS 26 rounded container must be tracked separately from the web
form-assistant row.

---

## Current Fix Boundary

The current evidence must be read conservatively.

Simulator proof split the visual problem into at least two layers:

- The iOS 26 rounded third-party keyboard container and the WebKit
  form-assistant row are host-owned. LimeIME cannot remove them.
- LimeIME's candidate row must stay vertically stable across composing,
  associated-candidate, and normal candidate states. The composing/keyname strip
  cannot be treated as active only when `composingText` is non-empty.

### Failed and rejected idea: conditional composing-strip reserve

Attempted LimeIME-owned cleanup, now reverted:

- `CandidateBarView` used `activeComposingStripHeight == 0` when
  `composingText` was empty.
- Candidate buttons, dismiss button, separator, and chevron used that active
  height for vertical bias.
- The whole candidate bar stayed the same height, so the keyboard height would
  not bounce between idle and composing states.

Result:

- Failed. The visible Safari / web-input extra space still appeared on hardware.
- Worse, it made the candidate row unstable. When LimeIME shows associated
  candidates, there may be no composing keyname in the composing strip, but the
  candidate row still needs the same vertical reservation / bias as nearby
  candidate states.
- Returning strip height `0` only because `composingText` is empty causes the
  candidate line to move up/down between associated-candidate and composing
  states. That is a bad UX regression and not an acceptable fix.
- Therefore `activeComposingStripHeight == 0 when composingText is empty` is a
  rejected approach, not just a failed Safari-gap fix.
- This also confirms the conditional composing-strip reserve is not the root
  cause of the real-device Safari / web-input artifact.

Updated conclusion:

- Not proven fixable in LimeIME: the real-device Safari / web-input extra top
  space.
- Do not retry `composingText`-based strip-height changes. Associated
  candidates need stable vertical placement even with an empty composing
  keyname.
- Still possibly fixable in LimeIME: a separate internal candidate-bar drawing
  issue, if a screenshot shows the gap is below LimeIME's own top edge and the
  fix preserves candidate-row stability across associated/composing states.
- Not fixable in LimeIME if proven above the extension top edge: the iOS 26
  rounded host container and WebKit accessory row.

The next move is not another visual tweak. The next move is a real-device
classification probe that draws and logs the extension's exact top boundary.

### Step 1 - Real-device boundary probe

Add temporary DEBUG-only proof markers in `KeyboardViewController`:

- A 1 px red line pinned to `view.topAnchor`.
- A 1 px green line pinned to `candidateBar.topAnchor`.
- A 1 px blue line pinned to `keyboardView.topAnchor`.
- Optional labels are not needed; colored lines are enough and reduce layout
  disturbance.

Then capture real-device screenshots in the failing Safari / Google / Gemini
field with LimeIME visibly active.

Interpretation:

- If the unwanted space is above the red/green line, it is outside LimeIME's
  extension view. LimeIME cannot remove it with candidate-bar layout, root
  background, cover strips, or height changes.
- If the unwanted space is between the red/green line and the first visible
  candidate/key content, it is inside `CandidateBarView`. Then the next fix is a
  candidate-bar drawing/layout change, not a root keyboard-host experiment.
- If the red line itself is not flush with the top of the visible keyboard
  surface, the host is clipping/insetting the third-party keyboard surface.
  Treat this as iOS 26 host-owned behavior and file Feedback Assistant.

### Step 2 - One-shot DEBUG classification log

Add temporary DEBUG-only logging in `KeyboardViewController` that fires
once per focus change. For each focused field, log:

- iOS context
  - `UIDevice.current.systemVersion` (major version determines whether
    iOS 26 Liquid Glass host insets are in play)
  - `traitCollection.userInterfaceIdiom`
  - `traitCollection.horizontalSizeClass`, `verticalSizeClass`
- Host text input traits (read from `textDocumentProxy`)
  - `keyboardType.rawValue` (`.URL` = 3, `.webSearch` = 9, `.default` = 0,
    `.emailAddress`, etc.)
  - `returnKeyType.rawValue`
  - `autocorrectionType.rawValue`
  - `smartDashesType.rawValue`, `smartQuotesType.rawValue`
- Geometry (already partially logged; keep these)
  - `view.frame`, `view.bounds`, `view.safeAreaInsets`
  - `view.window?.frame`, `view.window?.bounds`,
    `view.window?.safeAreaInsets`
  - `candidateBar.frame`, candidateBar-in-window
  - `keyboardView.frame`
  - root height-constraint constant
- Optional, diagnosis-only
  - class names of `view.window`'s immediate subviews / superviews
    (do not depend on private class names in production code)

Capture matched logs and screenshots for these scenarios:

- Safari URL bar (no extra space expected — native `UITextField`)
- Google search `<input>` (web form assistant row may appear; separately check
  for iOS 26 rounded third-party keyboard host container)
- Gemini textarea (web form assistant row may appear; separately check for iOS
  26 rounded third-party keyboard host container)
- Apple's built-in phonetic keyboard in the same Gemini / Google context
  (visual baseline only — Apple's private hosting path; we cannot log
  internals there)

Run the same set on iOS 25 and iOS 26 if both are available, since the
expected actors differ by major version.

### Step 3 - Decision table

| Finding | Classification | Action |
| --- | --- | --- |
| Candidate bar `y > 0` in any future log | LimeIME root layout regressed | Fix candidate-bar constraints. Bug is in LimeIME. |
| Candidate bar `y == 0`, but the bar itself has a visible blank top band above the composing/candidate content | LimeIME candidate-bar reserve / clear-background presentation | LimeIME-owned visual issue. Rework `CandidateBarView` strip drawing, padding, or background treatment. |
| Candidate bar `y == 0`, composing label `y == 0`, AND only the `up / down / done` row is visible above the keyboard | WKWebView form-assistant `inputAccessoryView` (Prev / Next / Done) | Host-owned adjacent accessory. Not the LimeIME extra-space artifact. Document separately and stop. |
| Same geometry on iOS ≤ 25 in Safari URL bar | Native `UITextField` — different accessory or none | If URL bar still shows extra space, capture and re-classify; otherwise expected behavior. |
| iOS ≥ 26 with LimeIME visibly active and rounded dark padding / container around the keyboard | iOS 26 Liquid Glass third-party keyboard host container / insets | Host-owned target artifact. Not fixable from the extension. File Feedback Assistant referencing forum thread 800838. |
| iPad-only artifact above keyboard | `UITextInputAssistantItem` shortcut bar | See [IPAD_ASSIST_BAR.md](IPAD_ASSIST_BAR.md) §8.1 — extension-side suppression is silently ignored. Not fixable from the extension. |

### Step 4 - Close-out

For every row except a confirmed internal LimeIME row, the action is the same:
stop iterating on LimeIME layout for this case, mark the bug as host /
OS-owned, and either file Feedback Assistant or accept the behavior.
Specifically:

- File Feedback Assistant referencing Apple Developer Forums thread
  800838 (iOS 26 Liquid Glass extra grey margin around third-party
  keyboard extensions) for the iOS ≥ 26 case.
- If separately documenting the web form-assistant row, reference
  rdar://27763084 (allow disabling default `inputAccessoryView` on
  WKWebView), but do not present it as the LimeIME extra-space root cause.
- Add a one-line link in `IOS_STATUS.md` (or equivalent) noting the
  artifact and the version gating, so future contributors do not re-open
  the candidate-bar branch of this investigation.

### Hard rule

No more root-view backdrops, top cover strips, or `UIVisualEffectView`
experiments for the host-owned portions of this bug. Candidate-bar work is only
appropriate for the LimeIME-owned blank band inside `CandidateBarView` itself,
and should be scoped to the composing-keyname strip / candidate padding rather
than trying to cover host pixels above the input view.
