# Issue #225 Analysis: iOS keyboard absent in Microsoft Authenticator number-entry prompt

## Classification

Plausible iOS interoperability bug, with the current evidence pointing first to iOS or host-app keyboard eligibility/fallback rather than a confirmed LIME rendering defect.

No LIME source fix is confirmed yet. Keep this issue out of `docs/BACKLOG.md` until runtime evidence shows that the LIME extension is launched and fails, or identifies another LIME-controlled correction.

## Problem statement

In a Microsoft Authenticator authentication flow, focusing the popup's numeric entry field shows Apple's numeric keypad when an Apple keyboard is active. When LIME is the selected keyboard, no keyboard appears, so the required number cannot be entered.

The issue reports the failure on iOS in Microsoft Authenticator. It does not yet identify the iOS version, Authenticator version, field keyboard trait, whether iOS launches the LIME extension, or whether another third-party keyboard behaves the same way.

## Reproduction steps from the report

1. Start a Microsoft Authenticator flow that displays a number-entry popup.
2. With an Apple keyboard active, focus the numeric field and confirm that the system numeric keypad appears.
3. Repeat with LIME active.
4. Focus the same field.
5. Observe that no keyboard appears.

## Evidence summary

- Live issue #225 was opened on 2026-08-02 by the maintainer account `limeimetw` and labeled `bug`.
- The issue currently has no comments, assignee, linked commit, or linked pull request.
- No screenshot, recording, device model, iOS version, Authenticator version, or extension lifecycle log is attached.
- Repository issue search found no earlier LIME issue owning the same Microsoft Authenticator or missing-numeric-keyboard scope.

## Source and history findings

- The extension entry point is `NSExtensionPrincipalClass = $(PRODUCT_MODULE_NAME).KeyboardViewController`, and `KeyboardViewController` is a `UIInputViewController`. There is no LIME code in the host application that can choose or instantiate that controller for another app; UIKit owns extension selection and launch.
- If UIKit does instantiate LIME, `viewDidLoad()` synchronously builds the keyboard UI and applies its content-driven height before database setup continues asynchronously. `viewWillAppear()` then calls `initOnStartInput()`, which reads `textDocumentProxy.keyboardType`, updates the input mode, and applies the matching layout. Field changes while the extension remains visible are re-read by `textDidChange()`.
- The extension manifest declares `IsASCIICapable = true`; no activation rule or Authenticator-specific exclusion exists in LIME's manifest or source.
- Commit `0863e6f2` (`#139: iOS numeric-field routing investigation + Android-parity split`) is the relevant history. Its field-matrix investigation observed that iOS used the system keyboard and never invoked LIME for restricted numeric inputs. It added the current fallback routing for the cases where a numeric trait does reach LIME; it did not establish that those branches are reachable in every host.
- The later #139 height work makes the extension height content-driven and has regression assertions around that structure. Nothing in that history identifies an Authenticator-specific zero-height or first-presentation path.

These findings do **not** prove whether Authenticator's prompt launches LIME. Static source, unit tests, and the repository's own test host cannot automate or observe another application's private authentication prompt or UIKit's extension-eligibility decision. That missing runtime boundary is the current blocker to a responsible RED test and source fix.

## Existing implementation and test coverage

LIME already has field-type routing when its keyboard extension is running:

- `KeyboardViewController.updateInputModeForCurrentField()` treats `.numberPad`, `.decimalPad`, `.asciiCapableNumberPad`, and `.phonePad` as forced-English numeric/phone contexts. Prediction remains enabled for the first three types and is disabled for `.phonePad`.
- `KeyboardViewController.layoutIdForCurrentInputField(...)` routes `.phonePad`, `.numberPad`, and `.decimalPad` to the `phone_number` layout. It routes `.asciiCapableNumberPad` to `symbols1` so letters remain reachable.
- `KeyboardViewControllerTest.testNumberFieldRoutingSplitsPureNumberFromAsciiCapable()` and `testLayoutResolverPreservesNumericAndPhoneFieldOverrides()` cover only `layoutIdForCurrentInputField(...)`. These two tests do not directly exercise the private `updateInputModeForCurrentField()` mapping.

These tests cover layout selection after LIME receives the host field's keyboard type. They do not cover whether iOS instantiates or displays the extension inside Microsoft Authenticator's authentication prompt.

Comments in both `KeyboardViewController.swift` and `KeyboardViewControllerTest.swift` currently state broadly that iOS system-replaces numeric fields. Apple's documented restriction is narrower: custom keyboards are ineligible for secure text inputs and for `.phonePad` / `.namePhonePad` fields, and iOS should temporarily replace the custom keyboard with the system keyboard. A host app can also reject custom keyboards entirely. The exact Authenticator field classification therefore matters before treating the existing numeric-layout code as reachable or unreachable.

## Likely root cause

The leading hypothesis is a host/system keyboard-arbitration failure:

1. Microsoft Authenticator marks this authentication field as secure, uses an ineligible phone-pad trait, or disallows custom keyboard extensions for the prompt.
2. iOS consequently does not launch LIME for that field.
3. iOS or the host prompt then fails to present the expected system-keyboard fallback, leaving the field without a keyboard.

This hypothesis fits the authentication context and the fact that Apple's keyboard can present a numeric keypad, but it is not verified. If lifecycle logging shows that `KeyboardViewController` is instantiated and presented for the field, investigation should instead move to LIME's input-view height, constraints, startup/database path, and field-change handling.

## Code fragility assessment

The pure routing function is simple and has focused unit coverage, so there is no current source evidence that choosing a numeric layout causes the entire keyboard to disappear. The uncovered boundary is earlier: host eligibility and extension presentation. UIKit owns that boundary, and a keyboard extension cannot force itself into a host field that rejects custom keyboards.

A LIME defect remains plausible if the extension launches but its view is hidden, has an invalid height, fails during startup, or does not reinitialize correctly when the Authenticator popup becomes first responder. Runtime evidence is required to distinguish these paths.

## Proposed investigation and solution

1. Reproduce on the reported iOS and Microsoft Authenticator versions with LIME, Apple's keyboard, and one unrelated third-party keyboard.
2. Capture LIME extension lifecycle and layout logs while focusing the prompt. Determine whether `viewDidLoad`, appearance callbacks, and input-view height/layout code run.
3. If LIME runs, record `textDocumentProxy.keyboardType`, `returnKeyType`, view/window geometry, and any startup failure, then add a focused regression for the proven LIME-controlled boundary before implementing the smallest fix.
4. If LIME never runs and another third-party keyboard also fails, classify this as an iOS/Microsoft Authenticator limitation or upstream defect. Document the practical system-keyboard workaround rather than changing unreachable LIME layout code.
5. If only LIME fails while another third-party keyboard appears, compare extension metadata and lifecycle behavior before selecting a fix.

Do not remove LIME's existing numeric-layout routing based only on this report. It remains the correct fallback when UIKit exposes a supported numeric field to the extension.

## Exact privacy-safe physical-device evidence plan

Use a development-signed build whose bundle IDs are known to the tester, on a physical device attached to Xcode. Do not record the screen, typed number, account identifier, notification text, QR code, tenant, or organization name.

1. Record only this version tuple in the issue notes: device model, iOS build, Authenticator version, LIME version/build, and comparison-keyboard version.
2. In Xcode, attach the debugger to the LIME keyboard-extension process by its known development bundle/process name and set symbolic breakpoints on:
   - `KeyboardViewController.viewDidLoad()`
   - `KeyboardViewController.viewWillAppear(_:)`
   - `KeyboardViewController.viewDidAppear(_:)`
   - `KeyboardViewController.viewWillDisappear(_:)`
   - `KeyboardViewController.textDidChange(_:)`
3. Before opening the sensitive flow, focus an ordinary non-sensitive field with LIME selected. Confirm the first three breakpoints hit; this validates the debugger attachment without collecting text.
4. Clear the debugger console. Open the Authenticator number-entry prompt and focus its field once. Record only a yes/no lifecycle sequence and timestamps, for example `viewDidLoad: no; viewWillAppear: no; viewDidAppear: no`. Do not inspect or print `documentContextBeforeInput` or `documentContextAfterInput`.
5. If `viewWillAppear` hits, pause on that breakpoint and record only these non-content values: `textDocumentProxy.keyboardType`, `textDocumentProxy.returnKeyType`, `view.bounds`, `view.window != nil`, `keyboardView != nil`, `keyboardView?.preferredHeight`, and `keyboardHeightConstraint?.constant`. Continue and record whether `viewDidAppear` hits and whether Auto Layout reports an unsatisfiable constraint. Never evaluate document-context properties.
6. Repeat the same prompt without changing account/session using Apple's keyboard and one unrelated third-party keyboard. Record only `visible`, `not visible`, or `system fallback visible`, plus whether digit entry succeeds; do not record the digit.
7. Manually switch back to Apple's keyboard, finish or cancel the flow, then redact the console export to retain only the lifecycle names, trait enum values, geometry, constraint diagnostics, timestamps, and version tuple. Review the redacted file before sharing it.

Decision table:

| Evidence | Ownership / next action |
| --- | --- |
| No LIME lifecycle breakpoint; unrelated third-party keyboard also absent | Host/UIKit eligibility or fallback path. Document workaround/upstream result; no LIME source test or fix. |
| No LIME lifecycle breakpoint; unrelated third-party keyboard appears | Compare the two extension manifests/signing capabilities and repeat once after reboot before proposing any LIME change. |
| `viewWillAppear` hits but `viewDidAppear` does not | Capture the privacy-safe lifecycle timestamps and UIKit/extension termination reason; isolate a lifecycle regression in LIME's own test host if reproducible. |
| `viewDidAppear` hits with zero/invalid geometry or constraint failure | Reduce the captured trait/geometry sequence to a unit or UI regression, run it RED, then fix only that boundary. |
| `viewDidAppear` hits with valid nonzero geometry but nothing is visible | Capture a view-hierarchy screenshot containing only the keyboard window (crop all host content), then test the proven visibility/layout cause. |

## Follow-up questions

- Which iPhone/iPad model and exact iOS version reproduce the problem?
- Which Microsoft Authenticator version is installed?
- Does another third-party keyboard, such as Gboard or SwiftKey, appear in the same prompt?
- Is the numeric value masked, treated as a credential/verification field, or shown as ordinary text?
- Can the user switch to Apple's keyboard from the globe/input-mode control after the empty prompt appears?
- Can a short screen recording and extension lifecycle log be captured without exposing authentication numbers or account details?

## Verification plan

### Diagnostic gate

- Confirm whether iOS launches LIME for the exact Authenticator field.
- Compare Apple and at least one unrelated third-party keyboard in the same prompt.
- Record device, iOS, Authenticator, and LIME versions.

### If a LIME-controlled failure is confirmed

- Add a regression at the identified lifecycle, layout-height, or field-transition boundary.
- Run the focused test RED before the fix and GREEN afterward.
- Run the relevant iOS unit suite and build the keyboard extension.
- Verify on a physical iOS device in the actual Microsoft Authenticator flow.
- Confirm that the numeric keypad appears and enters digits without exposing account or authentication data.

### If LIME is not launched

- Verify the same behavior with another third-party keyboard.
- Confirm that manually selecting Apple's keyboard restores input.
- Record the issue as host/system-owned and provide concise workaround guidance. No LIME source change or backlog fix should be claimed.

## Platform impact analysis

### iOS

Reported scope only. The failure is reported in Microsoft Authenticator's number-entry popup but has not yet been independently reproduced. The owning boundary is not yet known. LIME's numeric resolver exists and is unit-tested, but the reported host-app presentation path is untested and may bypass the extension entirely.

### Android

No Android failure is reported or inferred from the iOS extension-hosting behavior. Android uses a different IME lifecycle and field negotiation mechanism, so this report does not establish Android impact. Test Android Microsoft Authenticator separately only if parity evidence is needed. Do not create an Android backlog item from the current evidence.
