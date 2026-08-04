# Issue #225 Analysis: iPad numeric popup shows no keyboard when LIME is active

## Classification

Resolved as an **Apple iPadOS 26 platform defect**, not a LIME defect. No LIME source fix exists or is possible. The corrective change belongs to the host application (Microsoft Authenticator / Intune App SDK) or to Apple.

Do not add this to `docs/BACKLOG.md`. There is no LIME-owned boundary to fix, and the existing numeric-layout routing must not be changed on the basis of this report.

## Problem statement

On **iPad**, Microsoft Authenticator's number-entry prompt is a popup (presented sheet) containing a numeric field. When an Apple keyboard is active, focusing that field raises a small **popup/compact numeric keypad** and the number can be entered. When LIME is the active keyboard, **no keyboard appears at all**, and there is no way to enter the number — and no globe key to switch keyboards, because nothing is presented.

Reported on iPad only. LIME was active and normally docked before the popup appeared; the keyboard was not in floating mode beforehand.

## Root cause

iPadOS 26 changed how iPad presents `UIKeyboardType.numberPad` and `.decimalPad`. Instead of the docked full-width keyboard, iPadOS renders a **compact floating keypad panel**, particularly for fields inside presented views and sheets. That is the "popup numpad" seen with Apple's keyboard.

Third-party keyboard extensions cannot be presented in that compact/floating form. There is no API for a `UIInputViewController` to opt into it, and no API for a host app to opt out of it. When the active keyboard is a third-party extension, iPadOS presents **nothing** — it does not dock the extension, and it does not substitute its own keypad.

The chain is therefore:

1. Authenticator's popup field uses `.numberPad` (or `.decimalPad`).
2. iPadOS 26 selects the compact floating keypad presentation for it.
3. LIME cannot be presented in that mode.
4. iPadOS shows no keyboard, with no fallback.

LIME's extension is never launched for the field, so none of its numeric-layout code participates.

## Evidence

The same iPadOS 26 regression was reported and diagnosed independently in the Microsoft Intune App SDK for iOS, where Intune-protected apps showed a PIN entry screen with no keyboard:

- The symptom reproduces in **Apple's own Settings → Face ID & Passcode**, with no Microsoft or third-party code involved. That is the clean, vendor-neutral repro.
- The failure is **iPad-only**. An iPhone on the same iPadOS 26.4 build was unaffected.
- iPadOS 26.4.1 did not fix it.
- Microsoft's resolution was a **host-side** change — moving the field's `UIKeyboardType` off `.numberPad` — shipped in Intune App SDK for iOS 21.6.0 on 2026-05-12. The issue was closed 2026-05-20.
- Apple Developer Forums separately document `.decimalPad` rendering as a floating compact panel on iPadOS 26, with **no official opt-out API**; the only known mitigation is the host app setting `keyboardType = .numbersAndPunctuation` (or `.default`) on iPad.
- A related iPadOS 26 crash is documented for `.numberPad` inside `.sheet()` presentations, confirming that presented views are the trigger context.

References:

- Intune App SDK for iOS issue #658 — <https://github.com/microsoftconnect/ms-intune-app-sdk-ios/issues/658>
- Intune App SDK for iOS 21.6.0 release — <https://github.com/microsoftconnect/ms-intune-app-sdk-ios/releases/tag/21.6.0>
- Apple Developer Forums, floating `decimalPad` on iPadOS 26 — <https://developer.apple.com/forums/thread/801458>
- Apple Developer Forums, `numberPad` floating in presented views — <https://developer.apple.com/forums/thread/820692>
- Apple Community, iPadOS 26.4 numeric keypad bug — <https://discussions.apple.com/thread/256271126>
- Apple Developer Forums, no way to trigger floating mode from a keyboard extension — <https://developer.apple.com/forums/thread/124356>

## Why LIME cannot fix this

- The extension entry point is `NSExtensionPrincipalClass = $(PRODUCT_MODULE_NAME).KeyboardViewController`, a `UIInputViewController`. UIKit alone decides whether to launch and how to present it. LIME has no code path in the host application that can influence that decision.
- `UIInputViewController` exposes no API to request compact/floating presentation, to force docked presentation, or to hand the field back to the system keyboard. `dismissKeyboard()` and `advanceToNextInputMode()` do not provide a system-keyboard fallback.
- The only documented mitigation sets `keyboardType` on the **host application's** text field. LIME is the keyboard, not the host, and cannot alter another app's field traits.
- Pinch-to-float was verified on a physical iPad: it works on Apple's keyboard and does nothing on LIME, confirming that LIME cannot enter any non-docked presentation.

## Existing implementation — leave unchanged

LIME's field-type routing is correct and stays as-is. It applies whenever UIKit *does* launch the extension for a numeric field, which remains the common case on iPhone and for docked iPad numeric layouts:

- `KeyboardViewController.updateInputModeForCurrentField()` treats `.numberPad`, `.decimalPad`, `.asciiCapableNumberPad`, and `.phonePad` as forced-English numeric/phone contexts. Prediction stays enabled for the first three and is disabled for `.phonePad`.
- `KeyboardViewController.layoutIdForCurrentInputField(...)` routes `.phonePad`, `.numberPad`, and `.decimalPad` to the `phone_number` layout, and `.asciiCapableNumberPad` to `symbols1` so letters remain reachable.
- `KeyboardViewControllerTest.testNumberFieldRoutingSplitsPureNumberFromAsciiCapable()` and `testLayoutResolverPreservesNumericAndPhoneFieldOverrides()` cover the resolver.

No regression test is added for this issue. The failing boundary is UIKit's presentation decision in another application, which is not observable from LIME's test host and would not be exercised by any test we can write.

## Disposition and user guidance

Close #225 as an upstream platform issue. Suggested reply to the reporter:

1. Confirm the exact iPadOS version and Microsoft Authenticator version.
2. Ask them to reproduce in **Settings → Face ID & Passcode** with LIME active. If the keypad is also missing there, the defect is Apple's and unrelated to Authenticator.
3. Update Microsoft Authenticator. The fix requires Authenticator to ship Intune App SDK for iOS 21.6.0 or later; until it does, the app hits the unfixed path.
4. Update iPadOS to the latest available build.

Workaround for affected users, stated plainly: switch to Apple's keyboard **before** starting the authentication flow, in a different app. Once the empty popup is on screen there is no globe key available to switch with.

## Platform impact analysis

### iOS

iPad only, iPadOS 26 and later, limited to numeric fields that iPadOS elects to present as a compact floating keypad. iPhone is unaffected — it retains docked presentation for `.numberPad`. No LIME change.

### Android

Not applicable. Android IMEs negotiate input types through a different mechanism and are not subject to iPadOS presentation modes. Do not create an Android backlog item from this report.
