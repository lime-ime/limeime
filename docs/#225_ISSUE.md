# Issue #225 Analysis: iPad numeric fields show no keyboard when LIME is active

## Classification

Resolved as an **iPadOS 26 platform limitation**, not a LIME defect. On iPadOS 26, numeric fields are presented as a compact keypad popover that third-party keyboard extensions cannot provide, so nothing is shown when LIME is active. Apple has confirmed the popover presentation is intended behavior, and the only opt-outs are host-application-side. No LIME source fix exists or is possible; the corrective change belongs to each host application or to Apple.

This is not limited to popups or to Microsoft Authenticator. It affects **any** iPad field using `UIKeyboardType.numberPad` or `.decimalPad`, inline or presented, in any app whose host has not opted out.

Do not add this to `docs/BACKLOG.md`. There is no LIME-owned boundary to fix, and the existing numeric-layout routing must not be changed on the basis of this report.

## Problem statement

On **iPad** running iPadOS 26, focusing a numeric field with an Apple keyboard active raises a small **compact numeric keypad popover** anchored to the field, and the number can be entered. When LIME is the active keyboard, **no keyboard appears at all**, and there is no way to enter the number — and no globe key to switch keyboards, because nothing is presented.

Two independent first-hand repros:

1. **Microsoft Authenticator** — number-entry prompt in a presented sheet containing a numeric field (original report).
2. **591 房屋交易** (Taiwanese real-estate app, 2026-08-04) — the price-filter panel's custom price-range row ("自訂") with two plain **inline** numeric min/max fields. The Apple keyboard shows the keypad popover with digits, ".", and delete (consistent with `.decimalPad`); LIME shows nothing. This field is not in a popup, sheet, or modal.

Reported on iPad only. LIME was active and normally docked before focusing the field; the keyboard was not in floating mode beforehand.

## Root cause

iPadOS 26 changed how iPad presents `UIKeyboardType.numberPad` and `.decimalPad`. Instead of the docked full-width keyboard, iPadOS renders a **compact keypad popover** ("number pad popover") anchored to the field. This is the default presentation for numeric fields **generally, regardless of presentation context** — plain inline fields in an app's normal UI as well as fields inside presented views and sheets. Apple has confirmed (via a Feedback reply quoted on the Developer Forums) that the popover keypad is **intended behavior** on iPad for digit-only input, not a bug.

Third-party keyboard extensions cannot be presented in that popover form. There is no API for a `UIInputViewController` to opt into it. The only opt-out is **host-side**: iOS 26 adds `UITextInputTraits.allowsNumberPadPopover` (default `YES`; verified in the iOS 26.5 SDK header `UITextInputTraits.h`: "Set this property to NO to disallow the display of the number pad popover for the text input view"), which the host app must set to `NO` on its own field — a keyboard extension cannot set it for another app. When the active keyboard is a third-party extension, iPadOS presents **nothing** — it does not dock the extension, and it does not substitute its own keypad.

The chain is therefore:

1. The host field uses `.numberPad` or `.decimalPad` and has not opted out of the popover.
2. iPadOS 26 selects the compact keypad popover presentation for it.
3. LIME cannot be presented in that mode.
4. iPadOS shows no keyboard, with no fallback.

LIME's extension is never launched for the field, so none of its numeric-layout code participates.

## Evidence

Inline fields are affected, not only presented views:

- The 591 房屋交易 repro above — inline field, no sheet or popup involved.
- Apple Developer Forums thread on `.numberPad` in a plain inline `UITextField` on iPad: first tap shows the compact keypad; an Apple Feedback reply quoted in the thread calls the numberpad "a text input acceleration", describes the behavior as deliberate, and provides the `allowsNumberPadPopover = false` opt-out (host-side, iOS 26.0+). <https://developer.apple.com/forums/thread/797047>
- Flutter issue: standard inline `TextFormField` with `TextInputType.number` gets the floating numeric keypad on iPadOS 26/26.1; sometimes unresponsive or not shown at all. Open, P2, no sheet/popover involved. <https://github.com/flutter/flutter/issues/178096>
- Apple Developer Forums, erratic `.numberPad` behavior reproducible in Apple's own Contacts app (inline field): compact pad on first tap, wrong characters after dismiss/reopen, iPadOS 26.0–26.1, FB21144039. <https://developer.apple.com/forums/thread/808114>
- A related iPadOS 26 crash is documented for `.numberPad` inside `.sheet()` presentations — presented views are one affected context, not the trigger condition. <https://developer.apple.com/forums/thread/820692>

The missing-keyboard symptom was independently diagnosed in the Microsoft Intune App SDK for iOS, where Intune-protected apps showed a PIN entry screen with no keyboard:

- The symptom reproduces in **Apple's own Settings → Face ID & Passcode**, with no Microsoft or third-party code involved. That is the clean, vendor-neutral repro.
- The failure is **iPad-only**. An iPhone on the same iPadOS 26.4 build was unaffected.
- iPadOS 26.4.1 did not fix it.
- Microsoft's resolution was a **host-side** change — moving the field's `UIKeyboardType` off `.numberPad` — shipped in Intune App SDK for iOS 21.6.0 on 2026-05-12. The issue was closed 2026-05-20.
- Apple Developer Forums separately document `.decimalPad` rendering as a floating compact panel on iPadOS 26; known host-side mitigations are setting `keyboardType = .numbersAndPunctuation` (or `.default`) on iPad, or setting `allowsNumberPadPopover = false` (iOS 26.0+).

Status as of 2026-08-04: no iPadOS point release is known to remove the compact keypad presentation or restore third-party keyboards for these fields (26.4.1 confirmed unfixed in the Intune thread; the Flutter issue remains open; Apple calls the presentation intended). The Screen Time keyboard fix in iPadOS 26.5 addressed a different, unrelated passcode-keyboard glitch.

References:

- Apple Developer Forums, inline `.numberPad` compact keypad confirmed intended, `allowsNumberPadPopover` opt-out — <https://developer.apple.com/forums/thread/797047>
- Flutter, floating numeric keypad on inline `TextFormField`, iPadOS 26/26.1 — <https://github.com/flutter/flutter/issues/178096>
- Apple Developer Forums, erratic `.numberPad` in Apple's Contacts app (inline), FB21144039 — <https://developer.apple.com/forums/thread/808114>
- Apple Developer Forums, floating `decimalPad` on iPadOS 26 — <https://developer.apple.com/forums/thread/801458>
- Apple Developer Forums, `numberPad` crash in presented views — <https://developer.apple.com/forums/thread/820692>
- Apple Community, iPadOS 26.4 numeric keypad bug — <https://discussions.apple.com/thread/256271126>
- Apple Developer Forums, no way to trigger floating mode from a keyboard extension — <https://developer.apple.com/forums/thread/124356>
- Intune App SDK for iOS issue #658 — <https://github.com/microsoftconnect/ms-intune-app-sdk-ios/issues/658>
- Intune App SDK for iOS 21.6.0 release — <https://github.com/microsoftconnect/ms-intune-app-sdk-ios/releases/tag/21.6.0>

## Why LIME cannot fix this

- The extension entry point is `NSExtensionPrincipalClass = $(PRODUCT_MODULE_NAME).KeyboardViewController`, a `UIInputViewController`. UIKit alone decides whether to launch and how to present it. LIME has no code path in the host application that can influence that decision.
- `UIInputViewController` exposes no API to request compact/floating presentation, to force docked presentation, or to hand the field back to the system keyboard. `dismissKeyboard()` and `advanceToNextInputMode()` do not provide a system-keyboard fallback.
- Both documented mitigations (`keyboardType = .numbersAndPunctuation`/`.default`, or `allowsNumberPadPopover = false`) are properties of the **host application's** text field. LIME is the keyboard, not the host, and cannot alter another app's field traits.
- Pinch-to-float was verified on a physical iPad: it works on Apple's keyboard and does nothing on LIME, confirming that LIME cannot enter any non-docked presentation.

## Existing implementation — leave unchanged

LIME's field-type routing is correct and stays as-is. It applies whenever UIKit *does* launch the extension for a numeric field, which remains the common case on iPhone and for docked iPad numeric layouts:

- `KeyboardViewController.updateInputModeForCurrentField()` treats `.numberPad`, `.decimalPad`, `.asciiCapableNumberPad`, and `.phonePad` as forced-English numeric/phone contexts. Prediction stays enabled for the first three and is disabled for `.phonePad`.
- `KeyboardViewController.layoutIdForCurrentInputField(...)` routes `.phonePad`, `.numberPad`, and `.decimalPad` to the `phone_number` layout, and `.asciiCapableNumberPad` to `symbols1` so letters remain reachable.
- `KeyboardViewControllerTest.testNumberFieldRoutingSplitsPureNumberFromAsciiCapable()` and `testLayoutResolverPreservesNumericAndPhoneFieldOverrides()` cover the resolver.

No regression test is added for this issue. The failing boundary is UIKit's presentation decision in another application, which is not observable from LIME's test host and would not be exercised by any test we can write.

## Disposition and user guidance

Close #225 as an upstream platform limitation. Suggested reply to the reporter:

1. This affects every iPad app whose numeric fields use `.numberPad`/`.decimalPad` on iPadOS 26, not just Authenticator — a second app (591 房屋交易, plain inline field) reproduces it. It can be confirmed LIME-independent in **Settings → Face ID & Passcode** with LIME active.
2. Apple considers the compact keypad popover intended behavior; the fix for each affected app is host-side (`allowsNumberPadPopover = false` or a different `keyboardType`). For Authenticator specifically, that means shipping Intune App SDK for iOS 21.6.0 or later. Affected apps should be reported to their vendors.
3. Update iPadOS to the latest available build, though no release through 26.5 changes the behavior.

Workaround for affected users, stated plainly: switch to Apple's keyboard **before** entering the affected screen, from a different field or app. Once a numeric field is focused and nothing is shown, there is no globe key available to switch with.

## Platform impact analysis

### iOS

iPad only, iPadOS 26 and later. Any field exposed as `.numberPad` or `.decimalPad` whose host app has not opted out of the keypad popover — inline or presented, in any app. iPhone is unaffected — it retains docked presentation for `.numberPad`. No LIME change.

### Android

Not applicable. Android IMEs negotiate input types through a different mechanism and are not subject to iPadOS presentation modes. Do not create an Android backlog item from this report.
