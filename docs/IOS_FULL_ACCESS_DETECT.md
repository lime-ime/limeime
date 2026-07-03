# iOS Keyboard Enabled / Full Access Detection

Scope: how LimeSettings detects (1) "LimeIME is enabled in system Settings" and (2) "Full Access is granted", and what is actually knowable on a real device.

## Bottom line

Enabled detection is reliable and already correctly implemented with two app-side methods that need no help from the keyboard. Full Access detection is fundamentally different: only the keyboard process knows `hasFullAccess`, and it can only report the value through a keyboard→App-Group write — which is exactly the write that Full Access OFF blocks. Therefore the app can confirm Full Access is ON, but can never confirm it is OFF. Detection must be modeled as a tri-state (confirmed-on / stale / unknown), never a boolean.

## Channel facts that bound the problem

| Actor → Target | Full Access OFF | Full Access ON |
|---|---|---|
| App → App Group | read/write | read/write |
| Keyboard → App Group | read-only | read/write |
| Keyboard → its own container | read/write | read/write |
| App → keyboard's own container | never | never |

- The keyboard's heartbeat writes (`keyboard_extension_loaded`, `keyboard_has_full_access`, `keyboard_last_seen_at` — `KeyboardViewController.swift:297-299`, `:328-330`) are keyboard→App-Group shared-defaults writes. With Full Access OFF they are silently dropped on device.
- Simulator does not enforce this, so heartbeat-based detection appears to work in Simulator and fails on device.
- Shared UserDefaults reads by the app are always fine (the app is never restricted).

## Enabled detection — current implementation (KEEP)

`SetupTabView.swift` `refreshStatus()` (`:496-526`), union of two methods:

- Method A — `UITextInputMode.activeInputModes`, identifying LimeIME via the private `identifier` KVC key (bundle-ID prefix `org.limeime`). Guarded with `responds(to:)` so removal of the private key degrades to `false` instead of crashing. Same pattern used by Gboard/SwiftKey. Public `primaryLanguage` is not usable alone ("zh-Hant" collides with Apple's built-in keyboards).
- Method B — the system `AppleKeyboards` array (list of enabled keyboard bundle IDs), read from `UserDefaults.standard` and the `.GlobalPreferences` domain. Added because Method A's private key stopped resolving on newer iOS (iOS 26).

`keyboardEnabled = viaInputModes || viaAppleKeyboards`, refreshed by a 1-second poll while the tab is visible (`:552-557`).

This requires no keyboard cooperation, works in every Full Access state, and needs no change.

## Full Access detection — current implementation (BROKEN on device)

Current logic (`SetupTabView.swift:528-544`):

- Read `keyboard_has_full_access` from the App Group suite.
- Missing key → assume granted (avoid false-orange before the extension ever runs).
- Explicit `false` → show "no Full Access" state.

Why this fails on a real device:

1. The extension's `false` write happens exactly when Full Access is OFF — so it is dropped. The explicit-false branch is unreachable on device; it only fires in Simulator or from a stale warm process.
2. Stale-true: Full Access ON writes `true`; if the user later revokes Full Access, the corrective `false` is dropped. The app shows "granted" forever based on the old `true`.
3. Net effect: the banner can never truthfully display "Full Access is off" from this signal alone.

### Probe field (KEEP, with its caveat)

`SetupTabView.swift:158-162`, `:570-575`: an invisible text field is focused so iOS loads the active keyboard; if that is LimeIME, `viewWillAppear` fires and (with Full Access ON) rewrites fresh heartbeat values, picked up by the 1-second poll.

Caveat: focusing the probe loads the *currently selected* keyboard. If the user's active keyboard is Apple's, LimeIME never runs and no fresh value arrives. Therefore "no report after probing" does NOT imply Full Access is off.

## Corrected model — tri-state with freshness

Only three states are knowable by the app:

| State | Evidence | Display |
|---|---|---|
| Confirmed ON | `keyboard_has_full_access == true` AND `keyboard_last_seen_at` is recent (fresh write since probe / within threshold) | "完整取用權限：已開啟（已學習字詞會納入備份）" |
| Stale | `true` but `keyboard_last_seen_at` old | treat as Unknown; optionally trigger probe |
| Unknown | key missing, or no fresh write | neutral copy — Full Access is optional; never claim broken |

Rules:

- Never render "Full Access is OFF" as an error. OFF and never-ran are indistinguishable on device, and the keyboard is fully functional without Full Access (App Review 4.4.1 requires this anyway).
- Use `keyboard_last_seen_at` (already written alongside the flag) as the freshness signal. A `true` without freshness is not proof of the current state.
- The probe field is a best-effort freshness trigger, not an oracle.
- Banner copy positions Full Access as a feature unlock ("include learned words in backup / sync installs immediately"), not a requirement.

## Required implementation changes

- `SetupTabView.refreshStatus()`: replace the boolean `fullAccessEnabled` with the tri-state above, keyed on value + `keyboard_last_seen_at` freshness.
- Remove the "missing → assume granted" green state; missing is Unknown with neutral copy.
- Keep enabled detection (Methods A+B) and the 1-second poll unchanged.
- Keep the probe field; treat its silence as no-information.
- Keyboard side: keep writing all three heartbeat keys on `viewDidLoad`/`viewWillAppear` (harmless when dropped, definitive when Full Access is ON). Additionally mirror them to the keyboard's own `UserDefaults.standard` so the extension can self-diagnose (`keyboard_db_last_error` has the same App-Group-write weakness — `KeyboardViewController.swift:715`).

## Test matrix

- Device, fresh install, keyboard not enabled: red/neutral "not enabled" (Methods A/B), no Full Access claim.
- Device, enabled, Full Access never granted, LimeIME active, tap probe: state stays Unknown (write dropped) — banner neutral, no "broken" claim.
- Device, enabled, Full Access ON, LimeIME active, tap probe: Confirmed ON within ~2 s (poll picks up fresh heartbeat).
- Device, Full Access ON then revoked, extension killed: old `true` present but stale → Unknown, not green.
- Device, enabled but Apple keyboard is the active one: probe loads Apple keyboard; state remains Unknown; no false conclusions.
- Simulator: explicit `false` may arrive (loose sandbox) — display it as informational only; do not rely on it existing on device.

## Implementation-phase addendum (2026-07-04)

The tri-state model above is unchanged. These notes record findings from implementation-phase review.

### Confirmed: why the probe never fires on device (current code)

The probe trigger is `guard keyboardEnabled && !fullAccessEnabled` (`SetupTabView.swift:571`), and `fullAccessEnabled` derives from `keyboard_has_full_access` with missing-key → assume `true` (`:537-541`). On hardware that guard is false in **every** FA state:

- FA off, extension ran: its `false` write is dropped → key missing → assumed `true` → no probe.
- FA off, never ran: key missing → assumed `true` → no probe.
- FA on: `true` lands → no probe.

So on device the banner is always green once the keyboard is enabled, the orange state is unreachable, and the probe field never focuses — the keyboard popup seen when visiting the Setup tab in Simulator (where the loose sandbox lets the explicit `false` land) never happens on hardware. The design was circular: the probe's trigger was gated on already knowing the answer the probe existed to fetch. The Task 5.1 rewrite must trigger on **lack of fresh evidence** (`keyboardEnabled && !hasFreshEvidence`), which is decidable app-side, fires on hardware exactly when the app is ignorant, and stops recurring once evidence arrives.

### Amendment — Confirmed OFF is achievable via non-FA-gated report channels

"The app can never confirm OFF" holds for *inference from silence* — silence still proves nothing (wrong keyboard summoned, never ran, killed early), and the tri-state stays as the fallback taxonomy. But FA OFF does not gate every report channel, only App-Group writes. When LIME provably runs during a foreground probe, it can *state* FA is off through:

1. **Darwin name-encoded ping** (cheapest): keyboard posts `org.limeime.fa.on` / `org.limeime.fa.off` on appear — the bit rides in the notification name; the keyboard reads `hasFullAccess` directly, no write-attempt inference. Live-only, but the app is foreground and listening during a probe by construction.
2. **Liveness + missing-ack inference**: any live Darwin ping confirms "keyboard ran"; a run without a heartbeat/receipt file inside a short grace window implies the write was sandbox-dropped → FA off. (Superseded by 1, which needs no grace-period tuning.)
3. **insertText probe relay** (see IOS_FULL_ACCESS.md addendum): the typed payload carries the FA bit alongside pref deltas; FA-independent by construction; needs a hardware spike first.

Consequence: `FAState` may gain a `confirmedOff` case beyond Task 5.1's `{confirmedOn, unknown}` — an optional upgrade, not a prerequisite. Display rules are unchanged: `confirmedOff` renders the same feature-unlock copy ("開啟完整取用權限以備份已學習字詞"), never an error state; it just lets footnotes be definite instead of neutral.

### Why every scheme still requires the keyboard to run

FA is a static per-keyboard system setting, but it is enforced as a sandbox profile on the **keyboard extension's process** — the App Group container carries no marker, ACL, or attribute the app can inspect (the app's own access is never restricted, so the container looks identical in every FA state). Exactly one process in the system can observe the answer (`hasFullAccess`), so detection is a *reporting* problem, never a knowing problem: know → report → run → be summoned → be the active keyboard. App-written inbox/ping files don't change this — they wait in the App Group until a LIME instance eventually scans them (guaranteed eventual pickup, same property as the desired-state table folder), which is fine for sync but not for interactive answers; those need the probe.

### Backup-button gating restated (Task 5.2)

Default **disabled**; enable only on fresh Confirmed-ON evidence ("off until proven on" — the provable direction on device); probe fires on DB-tab appear; stale-enabled races (FA revoked seconds ago) are caught by the backup flow's own receipt timeout → FA guidance. Never implement it as "disable when FA off" — that predicate is undecidable from silence.
