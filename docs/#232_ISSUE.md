# Issue #232 — iOS Settings briefly presents the keyboard on activation

## Problem statement

A community reporter using LIME 6.1.38 on iOS 26.6 reports that switching to the LIME Settings app briefly presents and dismisses the keyboard. The reporter says Android does not show the same behavior.

The edited live issue now includes a usable 2.75-second portrait recording (1320 × 2660). Its timing is consistent with the reported transient presentation during the app transition. Source inspection independently confirms that the iOS Settings root can automatically focus a hidden `UITextField` when the app appears or becomes active. Focusing that field necessarily presents the current system keyboard, and the relay later dismisses it.

## Reproduction steps

1. Enable LIME as an iOS keyboard.
2. Leave the LIME Settings app.
3. Switch back to LIME Settings.
4. Observe whether the current keyboard briefly appears and then disappears without tapping a text field.

Reporter-supplied environment:

- Platform: iOS 26.6
- LIME: 6.1.38
- Device model: not provided
- Orientation in the attached recording: portrait
- Active keyboard immediately before the switch: not explicitly stated

## Evidence summary

Reporter evidence:

- The live issue contains a 2.75-second portrait recording after the reporter corrected the original failed upload.
- The reporter identifies iOS 26.6 / LIME 6.1.38 as affected and says Android is not affected.

Source evidence on current `master`:

- `LimeSettingsView` invokes `triggerRootRelay()` from both `.onAppear` and `UIApplication.didBecomeActiveNotification`.
- `SetupTabView` also requests an automatic root relay on appear and foreground activation when LIME is enabled.
- `triggerRootRelay` sets `rootRelayFocused = true`.
- `RelayProbeField.updateUIView` responds by calling `becomeFirstResponder()` on a real `UITextField`, which presents the current keyboard.
- `finishRootRelay` later resigns the first responder and globally ends editing, which dismisses the keyboard.
- `docs/IOS_ACTIVE_KB_DETECT.md` documents this focus-based relay as the mechanism used to prove that LIME is the current active keyboard.

The source control flow therefore provides a direct mechanism for the reported flash. Physical-device reproduction is still required to verify the exact visible sequence and all relevant keyboard states.

## Existing test coverage assessment

The existing `testSetupRelayUsesOnlyRootProbeField` source contract verifies that Setup routes relay requests through the single root probe. Relay payload and state tests cover encoding, response handling, and active/full-access status behavior. They do not distinguish passive Settings activation from an explicit user request to present or switch the keyboard. No current test prevents an automatic activation relay from making the hidden field first responder.

## Code fragility assessment

The current design couples several operations to one focus-based relay path:

1. passive active-keyboard status detection when Settings appears or becomes active
2. keyboard-to-app preference and pending-sync metadata transfer
3. the editor live/read-only gate
4. an explicit user action that may legitimately present the keyboard

Because automatic and manual requests reach the same first-responder call, a passive lifecycle request can present unsolicited keyboard UI. Conversely, simply disabling automatic relay presentation would also remove fresh active-keyboard evidence and may leave editor/pending-sync state stale or conservatively read-only. The fix therefore needs to preserve the required synchronization and safety gates rather than only suppressing the focus call.

## Likely root cause

Source-confirmed mechanism: automatic `.automatic` relay requests use the same hidden first-responder presentation path as `.manualSwitch`. iOS cannot run the keyboard extension through this text-input relay without presenting keyboard UI. The visible flash is therefore a plausible consequence of using that mechanism during passive app activation.

The exact product-safe replacement is not yet confirmed because the relay also carries state used outside the setup banner. A focused candidate now separates automatic and manual presentation permission, but it still needs maintainer/device validation of the resulting conservative passive state.

## Proposed solution

The focused candidate implements this boundary:

- `ActiveKeyboardProbeMode.automatic` cannot present the hidden field, while `.manualSwitch` remains presentation-authorized.
- Automatic requests resolve Setup's pending state without focus, preserve heartbeat-readable Full Access evidence, and conservatively mark active/editor state not active.
- An automatic lifecycle event cannot cancel an already pending manual relay.
- Pending learning-outbox retries remain `.manualSwitch`, because they belong to the explicitly initiated relay session and require a real extension response.
- Regression coverage pins both the mode policy and the ordering between pending-relay protection, presentation permission, passive resolution, and focus.

Known trade-off requiring maintainer/device validation: passive foreground activation no longer forces fresh active-keyboard proof or keyboard-to-app preference transfer. The setup banner/editor gate therefore remain conservative until an explicit manual relay. iOS cannot complete this particular extension round trip without presenting keyboard UI, so the review decision is whether that conservative state is preferable to unsolicited keyboard presentation. The candidate prevents stale-live editor state and preserves manual preference/pending-sync transfer, but cannot provide both silent activation and a fresh focus-based payload.

## Follow-up questions

For reporter/device verification:

- Which device model was used?
- Was LIME or another keyboard active immediately before switching to the Settings app?
- Does the flash occur on every foreground activation, only after using LIME, or only on the first activation after launch?

These details refine runtime coverage but are not required to classify the source path as a plausible iOS defect.

## Verification plan

1. Run the focused mode-policy and source-contract regressions proving passive resolution occurs before focus and cannot cancel an in-flight manual relay.
2. Run existing relay, editor-gate, pending-sync, and Settings status tests.
3. Build the iOS Settings app and keyboard-extension targets.
4. Verify active-keyboard banner transitions with LIME active, another keyboard active, and no fresh relay response.
5. Verify manual preference payload transfer, pending learning-outbox re-probes, and editor live/read-only gating under the revised flow.
6. On a physical iOS device, switch from another app to LIME Settings in portrait and landscape and verify that no keyboard flashes.
7. Repeat with LIME and a non-LIME keyboard active before the app switch.
8. Tap the explicit keyboard-switch action and verify that the manual path still presents/switches to LIME, transfers preferences, and resolves pending sync state.
9. Verify that opening Settings, returning from iOS system Settings, changing tabs, and foregrounding repeatedly do not summon a keyboard or leave editor controls stale-live.

Reporter retest should be requested only after a TestFlight or App Store build containing an accepted fix is available.

## Platform impact

### iOS

Confirmed reporter platform: iOS 26.6 with LIME 6.1.38. Current iOS source contains the focus-based passive relay mechanism matching the report. The affected scope is the iOS Settings app and its keyboard-extension relay lifecycle.

### Android

The reporter explicitly says Android does not show the problem. Android does not use the iOS `UITextField` relay or `ActiveKeyboardProbeMode`, so no Android source change is expected. Android should receive no `fix#232` implementation item unless separate Android evidence appears.
