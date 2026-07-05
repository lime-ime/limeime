# LIME Settings Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the reviewed `docs/LIME_SETTINGS.md` and `docs/IOS_ACTIVE_KB_DETECT.md` setup, active-keyboard, backup, and editor-unlock behavior in the iOS app.

**Architecture:** Keep the change local to the existing settings/shared-contract code. Add one shared active-keyboard probe mode so automatic probes and manual switch attempts can use different windows without inventing a new detection system.

**Tech Stack:** SwiftUI, XCTest, existing `RelayProbeField`, existing NotificationCenter relay, existing App Group heartbeat/Darwin ping detection.

## Global Constraints

- Do not use git revert/reset/checkout.
- Do not add dependencies.
- Do not create new source folders.
- Keep user-facing copy exactly as written here.
- Three status colors are red / orange / green. The orange code token is `statusTintOrange`; `warningInk` remains the semantic ink role.
- Section 2 active-keyboard proof is described in `docs/IOS_ACTIVE_KB_DETECT.md`.

---

## Files

- Modify: `LimeIME-iOS/Shared/Database/SyncContract.swift`
  - Add active-keyboard probe mode and mode-specific timing.
  - Keep existing `FAStateResolver.isActiveThisSession(..., window:)` callable.
- Modify: `LimeIME-iOS/LimeSettings/LimeSettingsView.swift`
  - Read probe mode from `.limeTriggerRelay`.
  - Use mode timeout for root relay timeout.
- Modify: `LimeIME-iOS/LimeSettings/Views/SetupTabView.swift`
  - Section 1 copy and guide visibility.
  - Section 2 red not-active banner, conditional note, manual probe mode.
  - Section 3 separator removal.
- Modify: `LimeIME-iOS/LimeSettings/Views/DBManagerView.swift`
  - Backup enabled/footer requires Full Access and active LIME keyboard.
- Modify: `LimeIME-iOS/LimeSettings/Views/RecordListView.swift`
  - Split editor unlock copy.
- Modify: `LimeIME-iOS/LimeSettings/Views/RelatedListView.swift`
  - Split editor unlock copy.
- Modify if still reachable: `LimeIME-iOS/LimeSettings/Views/IMDetailView.swift`
  - Align related-table unlock copy.
- Modify tests:
  - `LimeIME-iOS/LimeTests/FAStateTest.swift`
  - `LimeIME-iOS/LimeTests/SetupDetectionTest.swift`
  - `LimeIME-iOS/LimeTests/RecordEditingCapabilityTest.swift`
  - Add narrow tests only if needed for backup/footer copy.

## Task 1: Shared Active-Keyboard Probe Timing

**Files:**
- Modify: `LimeIME-iOS/Shared/Database/SyncContract.swift`
- Test: `LimeIME-iOS/LimeTests/FAStateTest.swift`

**Interfaces:**
- Produces: `ActiveKeyboardProbeMode.automatic`, `ActiveKeyboardProbeMode.manualSwitch`
- Produces: `ActiveKeyboardProbeMode.timeout`
- Produces: `ActiveKeyboardProbeMode.notificationUserInfo`
- Produces: `FAStateResolver.isActiveThisSession(faPingAt:probeFiredAt:mode:)`

- [ ] **Step 1: Add failing tests**

Add tests showing automatic mode rejects after `1.5s` and manual mode accepts through `10s`:

```swift
func testActiveThisSessionUsesAutomaticProbeWindow() {
    XCTAssertTrue(FAStateResolver.isActiveThisSession(faPingAt: 101.5,
                                                      probeFiredAt: 100,
                                                      mode: .automatic))
    XCTAssertFalse(FAStateResolver.isActiveThisSession(faPingAt: 101.51,
                                                       probeFiredAt: 100,
                                                       mode: .automatic))
}

func testActiveThisSessionUsesManualSwitchWindow() {
    XCTAssertTrue(FAStateResolver.isActiveThisSession(faPingAt: 110,
                                                      probeFiredAt: 100,
                                                      mode: .manualSwitch))
    XCTAssertFalse(FAStateResolver.isActiveThisSession(faPingAt: 110.01,
                                                       probeFiredAt: 100,
                                                       mode: .manualSwitch))
}
```

- [ ] **Step 2: Run the tests and confirm they fail**

Run:

```bash
./gradlew --version >/dev/null
xcodebuild -project LimeIME-iOS/LimeIME.xcodeproj -scheme LimeIME -destination 'generic/platform=iOS Simulator' build
```

Expected before implementation: compile fails because `ActiveKeyboardProbeMode` / mode overload does not exist.

- [ ] **Step 3: Implement minimal shared timing**

In `SyncContract.swift`, add:

```swift
enum ActiveKeyboardProbeMode: String {
    case automatic
    case manualSwitch

    static let notificationKey = "mode"

    init(notificationUserInfo: [AnyHashable: Any]?) {
        if let raw = notificationUserInfo?[Self.notificationKey] as? String,
           let mode = ActiveKeyboardProbeMode(rawValue: raw) {
            self = mode
        } else {
            self = .automatic
        }
    }

    var timeout: TimeInterval {
        switch self {
        case .automatic: return FAStateResolver.automaticActiveSessionWindow
        case .manualSwitch: return FAStateResolver.manualSwitchActiveSessionWindow
        }
    }

    var notificationUserInfo: [String: String] {
        [Self.notificationKey: rawValue]
    }
}
```

In `FAStateResolver`, replace the single active window constants with:

```swift
static let automaticActiveSessionWindow: TimeInterval = 1.5
static let manualSwitchActiveSessionWindow: TimeInterval = 10
static let activeSessionWindow: TimeInterval = automaticActiveSessionWindow
static let activeProbeWaitNanoseconds: UInt64 = 1_500_000_000
static let manualSwitchWaitNanoseconds: UInt64 = 10_000_000_000

static func isActiveThisSession(faPingAt: TimeInterval?,
                                probeFiredAt: TimeInterval?,
                                mode: ActiveKeyboardProbeMode) -> Bool {
    isActiveThisSession(faPingAt: faPingAt,
                        probeFiredAt: probeFiredAt,
                        window: mode.timeout)
}
```

- [ ] **Step 4: Verify tests pass**

Run:

```bash
xcodebuild -project LimeIME-iOS/LimeIME.xcodeproj -scheme LimeIME -destination 'generic/platform=iOS Simulator' -only-testing:LimeTests/FAStateTest test
```

Expected: `FAStateTest` passes.

## Task 2: Root Relay Uses Probe Mode

**Files:**
- Modify: `LimeIME-iOS/LimeSettings/LimeSettingsView.swift`
- Test: covered by build plus `FAStateTest`

**Interfaces:**
- Consumes: `ActiveKeyboardProbeMode(notificationUserInfo:)`
- Consumes: `ActiveKeyboardProbeMode.timeout`

- [ ] **Step 1: Add root state**

Add:

```swift
@State private var rootRelayMode: ActiveKeyboardProbeMode = .automatic
```

- [ ] **Step 2: Parse mode from notification**

Change the `.limeTriggerRelay` receiver to pass the notification:

```swift
.onReceive(NotificationCenter.default.publisher(for: .limeTriggerRelay)) { note in
    triggerRootRelay(mode: ActiveKeyboardProbeMode(notificationUserInfo: note.userInfo))
}
```

- [ ] **Step 3: Use the mode timeout**

Change `triggerRootRelay()` to:

```swift
private func triggerRootRelay(mode: ActiveKeyboardProbeMode = .automatic) {
    ...
    rootRelayMode = mode
    ...
    DispatchQueue.main.asyncAfter(deadline: .now() + mode.timeout) {
        if rootRelayPending && rootRelayFiredAt == firedAt && !rootActiveThisSession {
            finishRootRelay()
        }
    }
}
```

Use `rootRelayMode` when checking relay payload freshness:

```swift
rootRelayFiredAt = FAStateResolver.isActiveThisSession(faPingAt: payload.ts,
                                                       probeFiredAt: relayFiredAt,
                                                       mode: rootRelayMode)
    ? relayFiredAt
    : payload.ts
```

- [ ] **Step 4: Build**

Run:

```bash
xcodebuild -project LimeIME-iOS/LimeIME.xcodeproj -scheme LimeIME -destination 'generic/platform=iOS Simulator' build
```

Expected: build succeeds.

## Task 3: Setup Tab UI Copy And Section Layout

**Files:**
- Modify: `LimeIME-iOS/LimeSettings/Views/SetupTabView.swift`
- Test: `LimeIME-iOS/LimeTests/SetupDetectionTest.swift` if helpers are added

**Interfaces:**
- Consumes: `ActiveKeyboardProbeMode`
- Produces UI matching Section 1/2/3 spec.

- [ ] **Step 1: Update Section 1 status copy**

Change full-access green text to:

```swift
case .fullyEnabled: return "萊姆輸入法已啓用、完整取用已開啓 ✓"
```

- [ ] **Step 2: Hide setup guide when Section 1 complete**

Wrap the setup steps, note, settings button, and settings guidance in:

```swift
if fullAccessBannerState != .fullyEnabled {
    ...
}
```

Keep `fullAccessStatusBanner` visible.

- [ ] **Step 3: Update Section 1 note**

Use:

```swift
Text("開啓完整取用以啓用備份資料庫、輸入法碼表編輯、按鍵震動回饋。不開啟也能正常輸入與安裝輸入法。")
```

- [ ] **Step 4: Use Settings-style preview rows**

Replace the old three-row step list with the smallest local SwiftUI structure that displays:

```text
輕觸「鍵盤」
萊姆輸入法        [toggle icon]
允許完整取用      [keyboard icon] [toggle icon]
```

Do not add a new file. Use private view helpers in `SetupTabView.swift` if needed.

- [ ] **Step 5: Update Section 2 not-active banner**

In `activeKeyboardStatusBanner`, use red status:

```swift
statusBanner(text: "已啟用，但尚未切換萊姆輸入法",
             systemImage: "xmark.circle.fill",
             ink: SettingsTheme.dangerInk,
             tint: SettingsTheme.statusTintRed) {
    ...
}
```

- [ ] **Step 6: Add Section 2 note**

Under the button/hint, add:

```swift
if faState == .confirmedOn && !activeThisSession {
    Text("啓用備份資料庫與輸入法碼表編輯需切換目前鍵盤為萊姆輸入法。")
        .font(.footnote)
        .foregroundColor(.secondary)
        .multilineTextAlignment(.center)
}
```

- [ ] **Step 7: Remove Section 3 divider**

Remove the `Divider()` at the top of `imStatusSection`.

- [ ] **Step 8: Build**

Run:

```bash
xcodebuild -project LimeIME-iOS/LimeIME.xcodeproj -scheme LimeIME -destination 'generic/platform=iOS Simulator' build
```

Expected: build succeeds.

## Task 4: Setup Tab Probe Mode Logic

**Files:**
- Modify: `LimeIME-iOS/LimeSettings/Views/SetupTabView.swift`
- Test: `LimeIME-iOS/LimeTests/FAStateTest.swift`

**Interfaces:**
- Consumes: `ActiveKeyboardProbeMode`
- Consumes: `.limeTriggerRelay` userInfo mode.

- [ ] **Step 1: Track active probe mode**

Add:

```swift
@State private var activeProbeMode: ActiveKeyboardProbeMode = .automatic
```

- [ ] **Step 2: Use mode in active proof**

Change `activeThisSession` to:

```swift
return FAStateResolver.isActiveThisSession(faPingAt: faPingAt,
                                           probeFiredAt: activeProbeFiredAt,
                                           mode: activeProbeMode)
```

- [ ] **Step 3: Use mode in pending timeout**

Change `refreshStatus()` timeout check to compare against `activeProbeMode.timeout`.

- [ ] **Step 4: Pass mode when requesting root relay**

Change request method:

```swift
private func requestRootRelay(mode: ActiveKeyboardProbeMode) {
    let firedAt = Date().timeIntervalSince1970
    activeProbeMode = mode
    activeProbePending = true
    activeProbeFiredAt = firedAt
    NotificationCenter.default.post(name: .limeTriggerRelay,
                                    object: nil,
                                    userInfo: mode.notificationUserInfo)
}
```

Use:

```swift
requestRootRelay(mode: .automatic)
requestRootRelay(mode: .manualSwitch)
```

- [ ] **Step 5: Use mode when applying relay payload**

Change `applyRelayPayload` to call the mode overload.

- [ ] **Step 6: Preserve manual mode during manual FA ping**

Keep the existing `activating` flow. It may set `activeProbeFiredAt = pingAt`; do not reset `activeProbeMode` until the next probe request.

- [ ] **Step 7: Run tests/build**

Run:

```bash
xcodebuild -project LimeIME-iOS/LimeIME.xcodeproj -scheme LimeIME -destination 'generic/platform=iOS Simulator' -only-testing:LimeTests/FAStateTest -only-testing:LimeTests/SetupDetectionTest test
```

Expected: both test classes pass.

## Task 5: Backup Gating And Copy

**Files:**
- Modify: `LimeIME-iOS/LimeSettings/Views/DBManagerView.swift`

**Interfaces:**
- Consumes: active-keyboard proof from `RelayActiveState`.

- [ ] **Step 1: Require active keyboard for backup**

Add the root relay state:

```swift
@EnvironmentObject private var relayActiveState: RelayActiveState
```

Then:

```swift
private var backupEnabled: Bool {
    #if DEBUG
    if isUITestColdBackupEnabled { return true }
    #endif
    return faState == .confirmedOn && relayActiveState.isActive == true
}
```

- [ ] **Step 2: Update footer copy**

Use:

```swift
private var backupFooter: String {
    if faState != .confirmedOn {
        return "開啟完整取用權限以備份已學習字詞"
    }
    if relayActiveState.isActive != true {
        return "啓用備份資料庫功能需切換目前鍵盤為萊姆輸入法。"
    }
    return "備份包含所有字根、關聯字及喜好設定。"
}
```

- [ ] **Step 3: Remove the pre-backup local active probe wait**

After `guard backupEnabled else { return }`, start backup presentation and call `setupController.backupDBAsync()` directly. The button is already disabled until root relay proves LIME is active, and `backupDBAsync()` still owns request/receipt timeout failure.

- [ ] **Step 4: Build**

Run:

```bash
xcodebuild -project LimeIME-iOS/LimeIME.xcodeproj -scheme LimeIME -destination 'generic/platform=iOS Simulator' build
```

Expected: build succeeds.

## Task 6: Editor Unlock Copy

**Files:**
- Modify: `LimeIME-iOS/LimeSettings/Views/RecordListView.swift`
- Modify: `LimeIME-iOS/LimeSettings/Views/RelatedListView.swift`
- Modify: `LimeIME-iOS/LimeSettings/Views/IMDetailView.swift` if the stale hint is still user-visible.

**Interfaces:**
- Consumes: `relayActiveState.isActive`
- Consumes: `relayActiveState.hasFullAccess`

- [ ] **Step 1: Add state-specific copy helper**

In `RecordListView` and `RelatedListView`, replace the fixed `unlockHint` with:

```swift
private var unlockHint: String {
    if relayActiveState.hasFullAccess == true && relayActiveState.isActive != true {
        return "將目前鍵盤切換為萊姆輸入法以顯示實際分數及開啓編輯功能"
    }
    return "開啟完整取用並將鍵盤切換至萊姆輸入法以顯示實際分數及開啓編輯功能"
}
```

- [ ] **Step 2: Align IMDetailView related hint**

If `relatedUnlockHint` is still shown before pushing `RelatedListView`, use the same helper/copy model there.

- [ ] **Step 3: Build**

Run:

```bash
xcodebuild -project LimeIME-iOS/LimeIME.xcodeproj -scheme LimeIME -destination 'generic/platform=iOS Simulator' build
```

Expected: build succeeds.

## Task 7: Verification

**Files:**
- No source edits unless verification finds a compile/test failure.

- [ ] **Step 1: Run targeted tests**

Run:

```bash
xcodebuild -project LimeIME-iOS/LimeIME.xcodeproj -scheme LimeIME -destination 'generic/platform=iOS Simulator' -only-testing:LimeTests/FAStateTest -only-testing:LimeTests/SetupDetectionTest -only-testing:LimeTests/RecordEditingCapabilityTest test
```

Expected: targeted tests pass.

- [ ] **Step 2: Run app build**

Run:

```bash
xcodebuild -project LimeIME-iOS/LimeIME.xcodeproj -scheme LimeIME -destination 'generic/platform=iOS Simulator' build
```

Expected: build succeeds.

- [ ] **Step 3: Inspect diff**

Run:

```bash
git diff -- docs/LIME_SETTINGS.md docs/IOS_ACTIVE_KB_DETECT.md LimeIME-iOS/Shared/Database/SyncContract.swift LimeIME-iOS/LimeSettings/LimeSettingsView.swift LimeIME-iOS/LimeSettings/Views/SetupTabView.swift LimeIME-iOS/LimeSettings/Views/DBManagerView.swift LimeIME-iOS/LimeSettings/Views/RecordListView.swift LimeIME-iOS/LimeSettings/Views/RelatedListView.swift LimeIME-iOS/LimeSettings/Views/IMDetailView.swift LimeIME-iOS/LimeTests/FAStateTest.swift LimeIME-iOS/LimeTests/SetupDetectionTest.swift LimeIME-iOS/LimeTests/RecordEditingCapabilityTest.swift
```

Expected: diff only contains reviewed spec/code changes.
