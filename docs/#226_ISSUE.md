# Issue #226: iOS Outlook Recipient and Sender Fields Force English Input

## Current status

- Issue: https://github.com/lime-ime/limeime/issues/226
- State: open
- Classification: plausible iOS keyboard-type routing defect
- Reported environment: LIME 6.1.37, iOS 26.6, Outlook 5.2629.0
- Reporter-confirmed platform split: iOS affected; Android works normally

## Problem statement

The reporter cannot use Chinese input in Outlook's recipient and sender fields on iOS. The behavior occurs with both Cangjie and Array, while Android works normally. The report includes a portrait screenshot and exact LIME, iOS, and Outlook versions.

The expected behavior is that address fields which accept contact names should allow the active Chinese input method, while still allowing the user to switch to English for a literal email address.

## Reported reproduction

1. Use LIME 6.1.37 on iOS 26.6 with Outlook 5.2629.0.
2. Focus an Outlook recipient or sender field.
3. Select Cangjie or Array in LIME.
4. Attempt Chinese input.

Actual result: Chinese input is unavailable in both fields.

Expected result: the fields allow Chinese names to be entered with the selected LIME input method.

## Evidence and source assessment

The report is consistent across two Chinese input methods and is limited to iOS. The attached screenshot establishes the Outlook address-entry context, but it does not expose the host field's `UIKeyboardType` value.

Current iOS source duplicates an explicit rule that classifies `.emailAddress` fields as English-only:

- `LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift`: the production `updateInputModeForCurrentField()` switch sets `mEnglishOnly = true` and disables prediction for `.emailAddress`.
- `LimeIME-iOS/Shared/Models/KeyboardTypePolicy.swift`: a separate policy helper also classifies `.emailAddress` as forced English. The inspected production field-routing path does not call this helper.
- `LimeIME-iOS/LimeTests/KeyboardViewControllerTest.swift`: the existing keyboard-type policy test exercises only the helper and explicitly expects `.emailAddress` to be forced English.

Outlook recipient and sender controls plausibly advertise `.emailAddress`, which would reproduce the reported behavior exactly. Runtime logging or an affected-device check is still needed to confirm the host trait and to avoid presenting this source match as device-proven root cause.

## Existing test coverage and gap

One focused policy test covers URL, search, default, email, numeric, decimal, ASCII-number, and phone keyboard types. It currently preserves the forced-English helper result for `.emailAddress`, but it does not execute the duplicated production switch or cover the prediction-disabled side effect.

The uncovered user contract is that an email-address-style field can also accept a localized contact display name. Existing coverage assumes that every `.emailAddress` field should enter English mode and therefore cannot detect the Outlook recipient/sender failure. English mode is not itself an ASCII-only input restriction.

## Likely root cause

The likely root cause is the unconditional `.emailAddress` case duplicated between the production iOS field-routing switch and its policy helper. It conflates two host uses of the same keyboard hint:

1. literal email-address entry, where an English-friendly layout is convenient; and
2. recipient/sender contact-name entry, where Chinese input must remain available.

A custom keyboard cannot reliably infer Outlook's semantic sub-purpose from `UIKeyboardType.emailAddress` alone. Forcing English therefore removes a capability that the host field legitimately needs.

## Proposed solution

Subject to affected-device confirmation, route `.emailAddress` through the normal persisted language-mode path instead of making it English-only. Users can still switch to the English layout when entering a literal address. The production routing switch and policy helper must be reconciled so tests cannot pass after changing only the unused helper while runtime behavior remains broken.

Add focused regression coverage that:

- executes the production field-mode decision and verifies `.emailAddress` no longer forces English or disables prediction solely because of the host hint;
- verifies `.emailAddress` resolves to the active Chinese layout when Chinese mode is active;
- verifies the English layout remains available through the normal language switch;
- preserves strict numeric and phone routing;
- preserves the existing URL and search behavior.

Because iOS exposes only the keyboard-type hint here, an Outlook-specific branch should not be introduced unless runtime evidence identifies an additional stable host signal.

## Follow-up questions

- On an affected device, what `textDocumentProxy.keyboardType` does Outlook expose for each field? The source match predicts `.emailAddress`.
- Does the same behavior occur in Apple Mail or other mail apps' recipient fields?
- Can the reporter switch to a Chinese LIME layout from the field and have it immediately return to English, or is the Chinese switch unavailable entirely?

These questions refine scope but do not invalidate the plausible bug classification.

## Platform impact

### iOS

Confirmed by the reporter in Outlook 5.2629.0 with LIME 6.1.37 on iOS 26.6. Current iOS source plausibly forces the failure for every host field exposed as `.emailAddress`, so other mail/contact-address fields may also be affected. That broader scope remains inferred until runtime-tested.

### Android

The reporter explicitly confirms that Android works normally. Android does not use the iOS `UIKeyboardType` routing code, so no Android source change is currently indicated. Android should remain a regression-control platform only.

## Verification plan

1. On iOS, capture the keyboard and return-key traits for Outlook recipient and sender fields without exposing recipient data.
2. Reproduce the failure on LIME 6.1.37 with Cangjie and Array.
3. Add a focused test against the production field-mode decision that fails while `.emailAddress` forces English, rather than changing only the separate policy-helper assertion.
4. Reconcile the duplicated routing policy, apply the smallest production change, and run the focused test plus the keyboard-type/layout resolver suite.
5. On an affected iPhone, verify Chinese composition and candidate commit in Outlook recipient and sender fields.
6. Verify English email-address entry remains available via the language switch.
7. Check at least one ordinary email-address form, URL/search fields, numeric fields, and phone fields for regressions.
8. Keep Android unchanged and use the reporter-confirmed Android behavior as the parity reference.
9. Ask the reporter to retest only after a newer iOS build contains the relevant fix.
