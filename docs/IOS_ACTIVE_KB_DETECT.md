# iOS Active Keyboard Detection

This document describes how the Settings app decides whether LIME is the current active keyboard. iOS has no public API that directly says "the current keyboard is this extension." `AppleKeyboards` only proves that the keyboard is enabled in Settings, not that it is the keyboard currently shown for this app.

## Rule

LIME is treated as active only when the app receives fresh LIME-origin evidence after starting a probe.

The active proof is:

```text
LIME-origin event timestamp >= probe timestamp
and
LIME-origin event timestamp - probe timestamp <= the current probe mode timeout
```

This is implemented by `FAStateResolver.isActiveThisSession(faPingAt:probeFiredAt:mode:)`.
The automatic probe mode uses 1.5 seconds; the manual-switch probe mode uses 10 seconds.

## Probe Flow

1. `SetupTabView` calls `requestRootRelay()` when the keyboard is enabled and active proof is missing or stale.
2. `requestRootRelay()` records `activeProbeFiredAt` and posts `.limeTriggerRelay`.
3. `LimeSettingsView` receives `.limeTriggerRelay`, focuses its hidden UIKit `RelayProbeField`, and writes `RelayToken.request` (`"LIMERELAYREQ?"`) into that field.
4. If LIME is the current keyboard, `KeyboardViewController` can read the token from `textDocumentProxy.documentContextBeforeInput` / `documentContextAfterInput`.
5. The keyboard answers once per appearance by inserting `encodeRelayPayload(faOn:ts:prefs:)` into the probe field.
6. `LimeSettingsView` decodes that payload with `decodeRelayPayload(_:)`, posts `.limeRelayPayloadReceived`, and finishes the root relay.
7. `SetupTabView` receives `.limeRelayPayloadReceived`, stores the payload timestamp as `faPingAt`, clears `activeProbePending`, and resolves Section 2 from `FAStateResolver.isActiveThisSession(..., mode: activeProbeMode)`.

Only LIME can complete this round trip because only the current keyboard extension can read the probe token and insert the relay payload.

## Darwin Ping Fallback

`KeyboardViewController.reportFullAccessStatus()` also posts Darwin notifications:

```text
org.limeime.fa.on
org.limeime.fa.off
```

`SetupTabView` listens through `FAPingObserver`. During a manual activation attempt (`activating == true`), a fresh FA ping is also accepted as active-keyboard evidence because it can only arrive when the LIME keyboard extension appears.

The relay payload remains the richer proof because it carries a timestamp and preferences through the focused probe field.

## Timeout And UI States

Section 2 uses two different timeouts:

| Path | Timeout | Purpose |
| --- | ---: | --- |
| Automatic active-keyboard probe | 1.5 seconds | Check whether LIME is already the current keyboard. If it is, the relay payload should arrive almost immediately. |
| Manual switch attempt | 10 seconds | After the user taps `選用萊姆輸入法`, keep waiting long enough for the user to open the globe keyboard menu and choose LIME. |

In both paths, fresh relay proof finishes immediately as soon as the payload is decoded. The timeout only controls the no-response path.

`activeProbePending == true` renders Section 2 as:

```text
萊姆輸入法檢查中…
```

If no LIME-origin event lands inside the active window, the root relay times out, posts `.limeRelayResolvedNotActive`, and Section 2 becomes:

```text
已啟用，但尚未切換萊姆輸入法
```

If fresh proof lands, Section 2 becomes:

```text
萊姆輸入法已啟用且為目前輸入法 ✓
```

## Important Boundaries

- The app cannot switch keyboards programmatically.
- The Section 2 action can only focus the probe field so iOS shows a keyboard; the user must choose LIME manually from the globe menu.
- Enabled-keyboard detection and active-keyboard detection are separate. Enabled means LIME exists in iOS Settings; active means LIME answered this foreground-session probe.
- Stale pings are not enough. The proof must be at or after the current probe timestamp and within the current probe mode's active-session window.

## Code Map

| Responsibility | File |
| --- | --- |
| Root hidden UIKit probe field and relay timeout | `LimeIME-iOS/LimeSettings/LimeSettingsView.swift` |
| Section 2 probe state and banner resolution | `LimeIME-iOS/LimeSettings/Views/SetupTabView.swift` |
| Relay token, payload encoding/decoding, active-window check | `LimeIME-iOS/Shared/Database/SyncContract.swift` |
| Keyboard token detection and payload insertion | `LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift` |
