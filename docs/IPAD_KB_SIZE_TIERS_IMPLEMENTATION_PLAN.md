# iPad Keyboard Size Tiers Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Execution workflow:**

1. Use `superpowers:subagent-driven-development` to execute this plan in one continuous run. Use `superpowers:executing-plans` only if subagents are unavailable.
2. Run each task through its required implementation, build/test, and review checkpoint before marking it complete.
3. Run `ponytail-review` after all implementation/doc tasks and before final verification; cut unnecessary abstractions or complexity it identifies.
4. Run final build/behavior verification only after `ponytail-review` is clean or all accepted simplifications are applied.

**Goal:** Add orientation-stable iPad size tiers so iPad 11" and iPad mini get square-ish keys while phone behavior and current iPad 13" behavior remain unchanged.

**Architecture:** `LayoutLoader` owns the host iPad flag, the current iPad size tier, and layout filename resolution. `LayoutMetrics`, `KeyboardView`, `CandidateBarView`, and `KeyboardViewController` read tier-aware metric selectors instead of a two-way phone/iPad split. A new Python trimmer generates optional `*_ipad_narrow*.json` files from the finished full iPad JSON files; medium and small tiers use the narrow file when present, then fall back.

**Tech Stack:** Swift/UIKit keyboard extension, bundled JSON layouts, Python 3 layout generation scripts, Xcode XCTest/build verification.

---

## Spec Corrections Before Implementation

The source spec is `docs/IPAD_KB_SIZE_TIERS.md`. Treat these as implementation-time corrections:

- The invariant "iPad 13 behavior bit-for-bit unchanged" wins over the draft numeric table. Current iPad-large constants in `LimeIME-iOS/LimeKeyboard/LayoutMetrics.swift` are the large tier unless the user explicitly asks for a 13" size change.
- Current large values to preserve: row height `64`, bottom row `68`, landscape row `60`, landscape bottom `64`, key gaps `7 / 4`, corner radius `8`, key fonts `24 / 20 / 24`, candidate font `26`, composing-code font `22`, candidate bar base height `74`, candidate horizontal pad `10`.
- The plan still uses small/medium row heights from the spec: small `52 / 56`, medium `58 / 62`, with landscape resolving to the same values as portrait for native iPad tiers.

## File Map

- Encoding rule for implementation: when any Swift or Python source file is edited, preserve or write UTF-8 with BOM as required by this repo's `AGENTS.md`.
- Modify `docs/IPAD_KB_SIZE_TIERS.md`: correct the large-tier numeric table so the parent spec matches "13 unchanged".
- Modify `LimeIME-iOS/LimeKeyboard/LayoutMetrics.swift`: add `IPadSizeClass`, add tier-aware metric selectors, preserve phone and iPad 13 values.
- Modify `LimeIME-iOS/LimeKeyboard/LayoutLoader.swift`: add `iPadSizeClass`, resolve `_ipad_narrow` for small/medium, cache by resolved resource id, prefetch narrow candidates.
- Modify `LimeIME-iOS/LimeKeyboard/KeyboardView.swift`: use tier-aware row heights, gaps, corner radius, key fonts, and icon size.
- Modify `LimeIME-iOS/LimeKeyboard/CandidateBarView.swift`: use tier-aware candidate fonts, composing strip metrics, candidate padding, chevron metrics if needed.
- Modify `LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift`: update `LayoutLoader.iPadSizeClass` during lifecycle/layout changes and reload when tier changes. Do not add production English-family redirects or production symbol-page reductions in this scope.
- Create `scripts/trim_ipad_layout.py`: generate optional `*_ipad_narrow*.json` layout files.
- Add only `LimeIME-iOS/LimeKeyboard/Layouts/*_ipad_narrow*.json`: generate narrow siblings and hand-author `symbols1_ipad_narrow.json` / `symbols2_ipad_narrow.json`.
- Do not modify, delete, regenerate, or reformat any production layout file whose filename does not contain `_ipad_narrow`.
- Modify `LimeIME-iOS/project.yml`: only if XcodeGen resource inclusion does not already copy `LimeIMEKeyboard/Layouts` as a folder resource. Current config already includes that folder.
- Optional modify `LimeIME-iOS/LimeTests/*.swift`: add tests only where constructors and bundle resources make them practical; otherwise rely on script verification and build.

---

### Task 1: Align the Parent Spec With Current Large Metrics

**Files:**
- Modify: `docs/IPAD_KB_SIZE_TIERS.md`

- [ ] **Step 1: Open the existing metric table**

Run: `sed -n '133,168p' docs/IPAD_KB_SIZE_TIERS.md`

Expected: The table still lists large row height `72` and candidate-bar height anchor `60`, which conflict with the current iPad values.

- [ ] **Step 2: Replace the conflicting table values**

Edit the table in `docs/IPAD_KB_SIZE_TIERS.md` so it says:

```markdown
| Constant | phone | small (mini) | medium (11") | large (13" — current behavior) |
|---|---|---|---|---|
| `rowHeightPortrait` / `rowHeightLandscape` | 50 / 34 | **52 / 52** | **58 / 58** | **64 / 60** |
| `bottomRowHeightPortrait` / `bottomRowHeightLandscape` | 54 / 38 | 56 / 56 | 62 / 62 | **68 / 64** |
| `keyHGap` / `keyVGap` | 5 / 2 | 5 / 3 | 6 / 3 | **7 / 4** |
| `keyCornerRadius` | 6 | 6 | 7 | **8** |
| `keySingleLabelFont` (regular) | 22 | 21 | 22 | **24** |
| `keyLabelFont` (light) | 18 | 18 | 19 | **20** |
| `keySublabelFont` (regular) | 22 | 21 | 22 | **24** |
| `baseCandidateFontSize` | 22 | 26 | 28 | **26** |
| `baseComposingCodeFontSize` | 16 | 19 | 21 | **22** |
| `candidateHPad` | 10 | 12 | 14 | **10** |
| Candidate-bar height anchor | 58 | 50 | 54 | **74** |
```

Also add this sentence under the table:

```markdown
Large-tier values intentionally mirror the currently shipped iPad constants in `LayoutMetrics.swift`; changing them would violate the "iPad 13 behavior unchanged" invariant.
```

- [ ] **Step 3: Verify the parent spec no longer contradicts current code**

Run: `rg -n "72 pt|Candidate-bar height anchor|iPad 13 behavior" docs/IPAD_KB_SIZE_TIERS.md`

Expected: Any remaining `72 pt` reference is either removed or explicitly marked as old draft math, and the candidate bar table shows large `74`.

---

### Task 2: Add the Tier Type and Tier-Aware Metrics

**Files:**
- Modify: `LimeIME-iOS/LimeKeyboard/LayoutMetrics.swift`

- [ ] **Step 1: Add `IPadSizeClass` above `enum LayoutMetrics`**

Insert after the file header comments and before `enum LayoutMetrics`:

```swift
enum IPadSizeClass: Equatable {
    case small
    case medium
    case large

    static func resolve(shortSideExtent: CGFloat) -> IPadSizeClass {
        if shortSideExtent >= 870 { return .large }
        if shortSideExtent >= 750 { return .medium }
        return .small
    }
}
```

- [ ] **Step 2: Replace `ComposingPopup.Pad` with tier enums and selectors**

Keep `ComposingPopup.Phone` unchanged except for comments. Replace the current single `Pad` enum and selectors with:

```swift
enum PadSmall {
    static let stripHeight: CGFloat = 24
    static let stripFontSize: CGFloat = 17
    static let candidateFontSize: CGFloat = 26
    static let composingCodeFontSize: CGFloat = 19
    static let barBaseHeight: CGFloat = 50
}

enum PadMedium {
    static let stripHeight: CGFloat = 26
    static let stripFontSize: CGFloat = 18
    static let candidateFontSize: CGFloat = 28
    static let composingCodeFontSize: CGFloat = 21
    static let barBaseHeight: CGFloat = 54
}

enum PadLarge {
    static let stripHeight: CGFloat = 28
    static let stripFontSize: CGFloat = 18
    static let candidateFontSize: CGFloat = 26
    static let composingCodeFontSize: CGFloat = 22
    static let barBaseHeight: CGFloat = 74
}

private static func resolvedIPadSizeClass() -> IPadSizeClass {
    LayoutLoader.iPadSizeClass
}

static func stripHeight(isPad: Bool) -> CGFloat {
    guard isPad else { return Phone.stripHeight }
    switch resolvedIPadSizeClass() {
    case .small: return PadSmall.stripHeight
    case .medium: return PadMedium.stripHeight
    case .large: return PadLarge.stripHeight
    }
}

static func stripFontSize(isPad: Bool) -> CGFloat {
    guard isPad else { return Phone.stripFontSize }
    switch resolvedIPadSizeClass() {
    case .small: return PadSmall.stripFontSize
    case .medium: return PadMedium.stripFontSize
    case .large: return PadLarge.stripFontSize
    }
}

static func candidateFontSize(isPad: Bool) -> CGFloat {
    guard isPad else { return Phone.candidateFontSize }
    switch resolvedIPadSizeClass() {
    case .small: return PadSmall.candidateFontSize
    case .medium: return PadMedium.candidateFontSize
    case .large: return PadLarge.candidateFontSize
    }
}

static func composingCodeFontSize(isPad: Bool) -> CGFloat {
    guard isPad else { return Phone.composingCodeFontSize }
    switch resolvedIPadSizeClass() {
    case .small: return PadSmall.composingCodeFontSize
    case .medium: return PadMedium.composingCodeFontSize
    case .large: return PadLarge.composingCodeFontSize
    }
}

static func barBaseHeight(isPad: Bool) -> CGFloat {
    guard isPad else { return Phone.barBaseHeight }
    switch resolvedIPadSizeClass() {
    case .small: return PadSmall.barBaseHeight
    case .medium: return PadMedium.barBaseHeight
    case .large: return PadLarge.barBaseHeight
    }
}
```

- [ ] **Step 3: Replace `KeyboardRow.Pad` with tier enums and row-height selectors**

Preserve `Phone` and `PadCompat`. Replace current `KeyboardRow.Pad` and add these selectors:

```swift
enum PadSmall {
    static let portraitRow: CGFloat = 52
    static let portraitBottomRow: CGFloat = 56
    static let landscapeRow: CGFloat = 52
    static let landscapeBottomRow: CGFloat = 56
    static let keyHGap: CGFloat = 5
    static let keyVGap: CGFloat = 3
    static let keyCornerRadius: CGFloat = 6
}

enum PadMedium {
    static let portraitRow: CGFloat = 58
    static let portraitBottomRow: CGFloat = 62
    static let landscapeRow: CGFloat = 58
    static let landscapeBottomRow: CGFloat = 62
    static let keyHGap: CGFloat = 6
    static let keyVGap: CGFloat = 3
    static let keyCornerRadius: CGFloat = 7
}

enum PadLarge {
    static let portraitRow: CGFloat = 64
    static let portraitBottomRow: CGFloat = 68
    static let landscapeRow: CGFloat = 60
    static let landscapeBottomRow: CGFloat = 64
    static let keyHGap: CGFloat = 7
    static let keyVGap: CGFloat = 4
    static let keyCornerRadius: CGFloat = 8
}

private static func resolvedIPadSizeClass() -> IPadSizeClass {
    LayoutLoader.iPadSizeClass
}

static func rowHeight(isPadHardware: Bool, isPad: Bool, isLandscape: Bool) -> CGFloat {
    if isPadHardware && !isPad {
        return isLandscape ? PadCompat.landscapeRow : PadCompat.portraitRow
    }
    guard isPad else {
        return isLandscape ? Phone.landscapeRow : Phone.portraitRow
    }
    switch resolvedIPadSizeClass() {
    case .small: return isLandscape ? PadSmall.landscapeRow : PadSmall.portraitRow
    case .medium: return isLandscape ? PadMedium.landscapeRow : PadMedium.portraitRow
    case .large: return isLandscape ? PadLarge.landscapeRow : PadLarge.portraitRow
    }
}

static func bottomRowHeight(isPadHardware: Bool, isPad: Bool, isLandscape: Bool) -> CGFloat {
    if isPadHardware && !isPad {
        return isLandscape ? PadCompat.landscapeBottomRow : PadCompat.portraitBottomRow
    }
    guard isPad else {
        return isLandscape ? Phone.landscapeBottomRow : Phone.portraitBottomRow
    }
    switch resolvedIPadSizeClass() {
    case .small: return isLandscape ? PadSmall.landscapeBottomRow : PadSmall.portraitBottomRow
    case .medium: return isLandscape ? PadMedium.landscapeBottomRow : PadMedium.portraitBottomRow
    case .large: return isLandscape ? PadLarge.landscapeBottomRow : PadLarge.portraitBottomRow
    }
}

static func keyHGap(isPad: Bool) -> CGFloat {
    guard isPad else { return Phone.keyHGap }
    switch resolvedIPadSizeClass() {
    case .small: return PadSmall.keyHGap
    case .medium: return PadMedium.keyHGap
    case .large: return PadLarge.keyHGap
    }
}

static func keyVGap(isPad: Bool) -> CGFloat {
    guard isPad else { return Phone.keyVGap }
    switch resolvedIPadSizeClass() {
    case .small: return PadSmall.keyVGap
    case .medium: return PadMedium.keyVGap
    case .large: return PadLarge.keyVGap
    }
}

static func keyCornerRadius(isPad: Bool) -> CGFloat {
    guard isPad else { return Phone.keyCornerRadius }
    switch resolvedIPadSizeClass() {
    case .small: return PadSmall.keyCornerRadius
    case .medium: return PadMedium.keyCornerRadius
    case .large: return PadLarge.keyCornerRadius
    }
}
```

- [ ] **Step 4: Replace `Key.Pad` with tier enums and selectors**

Preserve `Phone` and `PadCompat`. Replace the single `Pad` enum and the four selectors with:

```swift
enum PadSmall {
    static let singleLabelFontSize: CGFloat = 21
    static let primaryLabelFontSize: CGFloat = 18
    static let sublabelFontSize: CGFloat = 21
    static let iconSize: CGFloat = 23
}

enum PadMedium {
    static let singleLabelFontSize: CGFloat = 22
    static let primaryLabelFontSize: CGFloat = 19
    static let sublabelFontSize: CGFloat = 22
    static let iconSize: CGFloat = 24
}

enum PadLarge {
    static let singleLabelFontSize: CGFloat = 24
    static let primaryLabelFontSize: CGFloat = 20
    static let sublabelFontSize: CGFloat = 24
    static let iconSize: CGFloat = 26
}

private static func resolvedIPadSizeClass() -> IPadSizeClass {
    LayoutLoader.iPadSizeClass
}

static func singleLabelFontSize(isPad: Bool, isPadCompat: Bool = false) -> CGFloat {
    if isPadCompat && !isPad { return PadCompat.singleLabelFontSize }
    guard isPad else { return Phone.singleLabelFontSize }
    switch resolvedIPadSizeClass() {
    case .small: return PadSmall.singleLabelFontSize
    case .medium: return PadMedium.singleLabelFontSize
    case .large: return PadLarge.singleLabelFontSize
    }
}

static func primaryLabelFontSize(isPad: Bool, isPadCompat: Bool = false) -> CGFloat {
    if isPadCompat && !isPad { return PadCompat.primaryLabelFontSize }
    guard isPad else { return Phone.primaryLabelFontSize }
    switch resolvedIPadSizeClass() {
    case .small: return PadSmall.primaryLabelFontSize
    case .medium: return PadMedium.primaryLabelFontSize
    case .large: return PadLarge.primaryLabelFontSize
    }
}

static func sublabelFontSize(isPad: Bool, isPadCompat: Bool = false) -> CGFloat {
    if isPadCompat && !isPad { return PadCompat.sublabelFontSize }
    guard isPad else { return Phone.sublabelFontSize }
    switch resolvedIPadSizeClass() {
    case .small: return PadSmall.sublabelFontSize
    case .medium: return PadMedium.sublabelFontSize
    case .large: return PadLarge.sublabelFontSize
    }
}

static func iconSize(isPad: Bool, isPadCompat: Bool = false) -> CGFloat {
    if isPadCompat && !isPad { return PadCompat.iconSize }
    guard isPad else { return Phone.iconSize }
    switch resolvedIPadSizeClass() {
    case .small: return PadSmall.iconSize
    case .medium: return PadMedium.iconSize
    case .large: return PadLarge.iconSize
    }
}
```

- [ ] **Step 5: Run a compile check**

Run: `xcodebuild -project LimeIME-iOS/LimeIME.xcodeproj -scheme LimeKeyboard -configuration Debug -destination 'generic/platform=iOS Simulator' -derivedDataPath .Codex/DerivedData build`

Expected: The build may still fail until callers are updated in Task 4. It should fail only on references to removed `LayoutMetrics.*.Pad` members or old row-height constants.

---

### Task 3: Teach `LayoutLoader` the Current Tier and Narrow Fallback

**Files:**
- Modify: `LimeIME-iOS/LimeKeyboard/LayoutLoader.swift`

- [ ] **Step 1: Add tier state next to `hostIsPad`**

Insert below `static var hostIsPad: Bool = false`:

```swift
static var iPadSizeClass: IPadSizeClass = .large
```

- [ ] **Step 2: Replace cache lookup so it stores resolved resources**

Replace `load(_:)` with:

```swift
static func load(_ id: String) -> LimeKeyLayout? {
    let candidates = resourceCandidates(for: id)

    for resourceId in candidates {
        lock.lock()
        if let cached = cache[resourceId] {
            lock.unlock()
            return cached
        }
        lock.unlock()

        if let layout = parseFromBundle(id: resourceId) {
            lock.lock()
            cache[resourceId] = layout
            lock.unlock()
            return layout
        }
    }

    return nil
}
```

- [ ] **Step 3: Add resource candidate generation**

Add this private helper above `parseFromBundle(id:)`:

```swift
private static func resourceParts(for id: String) -> (base: String, shifted: Bool) {
    var value = id
    var shifted = false

    if value.hasSuffix("_shift") {
        shifted = true
        value = String(value.dropLast("_shift".count))
    }
    if value.hasSuffix("_ipad_narrow") {
        value = String(value.dropLast("_ipad_narrow".count))
    } else if value.hasSuffix("_ipad") {
        value = String(value.dropLast("_ipad".count))
    }
    if value.hasSuffix("_shift") {
        shifted = true
        value = String(value.dropLast("_shift".count))
    }

    return (value, shifted)
}

private static func resourceId(base: String, iPadSuffix: String?, shifted: Bool) -> String {
    var value = base
    if let iPadSuffix {
        value += iPadSuffix
    }
    if shifted {
        value += "_shift"
    }
    return value
}

private static func resourceCandidates(for id: String) -> [String] {
    let parts = resourceParts(for: id)
    guard hostIsPad else {
        return [resourceId(base: parts.base, iPadSuffix: nil, shifted: parts.shifted)]
    }

    switch iPadSizeClass {
    case .small, .medium:
        return [
            resourceId(base: parts.base, iPadSuffix: "_ipad_narrow", shifted: parts.shifted),
            resourceId(base: parts.base, iPadSuffix: "_ipad", shifted: parts.shifted),
            resourceId(base: parts.base, iPadSuffix: nil, shifted: parts.shifted),
        ]
    case .large:
        return [
            resourceId(base: parts.base, iPadSuffix: "_ipad", shifted: parts.shifted),
            resourceId(base: parts.base, iPadSuffix: nil, shifted: parts.shifted),
        ]
    }
}
```

This normalization is required because `currentLayout.id` is the resolved JSON id. A keyboard can move from `.small` to `.large` while `currentLayout.id == "lime_phonetic_ipad_narrow"`; reloading that id must resolve back to `"lime_phonetic_ipad"` on large. It also preserves the repo's existing shift naming convention: `lime_phonetic_shift` resolves to `lime_phonetic_ipad_shift`, not `lime_phonetic_shift_ipad`.

- [ ] **Step 4: Keep prefetch simple and tier-aware**

Replace `prefetchCommonLayouts()` with:

```swift
static func prefetchCommonLayouts() {
    let ids = [
        "lime_phonetic", "lime_phonetic_shift",
        "lime_abc", "lime_abc_shift",
        "lime_number", "symbols1", "symbols2",
    ]
    DispatchQueue.global(qos: .background).async {
        for id in ids { _ = load(id) }
    }
}
```

This method still calls `load(_:)`; `load(_:)` now tries narrow first on small/medium.

- [ ] **Step 5: Build enough to catch loader errors**

Run: `xcodebuild -project LimeIME-iOS/LimeIME.xcodeproj -scheme LimeKeyboard -configuration Debug -destination 'generic/platform=iOS Simulator' -derivedDataPath .Codex/DerivedData build`

Expected: Any remaining failures point to caller updates in `KeyboardView` or `KeyboardViewController`, not `LayoutLoader` syntax.

---

### Task 4: Update View Metrics Callers

**Files:**
- Modify: `LimeIME-iOS/LimeKeyboard/KeyboardView.swift`
- Modify: `LimeIME-iOS/LimeKeyboard/CandidateBarView.swift`
- Modify: `LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift`

- [ ] **Step 1: Remove stored row-height constants from `KeyboardView`**

Delete these private lets from `KeyboardView`:

```swift
private let rowHeightPortrait:              CGFloat = LayoutMetrics.KeyboardRow.Phone.portraitRow
private let bottomRowHeightPortrait:        CGFloat = LayoutMetrics.KeyboardRow.Phone.portraitBottomRow
private let rowHeightLandscape:             CGFloat = LayoutMetrics.KeyboardRow.Phone.landscapeRow
private let bottomRowHeightLandscape:       CGFloat = LayoutMetrics.KeyboardRow.Phone.landscapeBottomRow
private let rowHeightPortraitIPad:          CGFloat = LayoutMetrics.KeyboardRow.Pad.portraitRow
private let bottomRowHeightPortraitIPad:    CGFloat = LayoutMetrics.KeyboardRow.Pad.portraitBottomRow
private let rowHeightLandscapeIPad:         CGFloat = LayoutMetrics.KeyboardRow.Pad.landscapeRow
private let bottomRowHeightLandscapeIPad:   CGFloat = LayoutMetrics.KeyboardRow.Pad.landscapeBottomRow
private let rowHeightPortraitCompat:        CGFloat = LayoutMetrics.KeyboardRow.PadCompat.portraitRow
private let bottomRowHeightPortraitCompat:  CGFloat = LayoutMetrics.KeyboardRow.PadCompat.portraitBottomRow
private let rowHeightLandscapeCompat:       CGFloat = LayoutMetrics.KeyboardRow.PadCompat.landscapeRow
private let bottomRowHeightLandscapeCompat: CGFloat = LayoutMetrics.KeyboardRow.PadCompat.landscapeBottomRow
```

- [ ] **Step 2: Replace row-height computed properties**

Use:

```swift
private var rowHeight: CGFloat {
    LayoutMetrics.KeyboardRow.rowHeight(
        isPadHardware: isPadHardware,
        isPad: isPad,
        isLandscape: isLandscape
    ) * keySizeScale
}

private var bottomRowHeight: CGFloat {
    LayoutMetrics.KeyboardRow.bottomRowHeight(
        isPadHardware: isPadHardware,
        isPad: isPad,
        isLandscape: isLandscape
    ) * keySizeScale
}
```

- [ ] **Step 3: Make `CandidateBarView` padding dynamic**

Change:

```swift
private let candidateHPad: CGFloat = LayoutMetrics.CandidateBar.candidateHPad
```

to:

```swift
private var candidateHPad: CGFloat { LayoutMetrics.CandidateBar.candidateHPad(isPad: isPad) }
```

Add this selector in `LayoutMetrics.CandidateBar`:

```swift
static func candidateHPad(isPad: Bool) -> CGFloat {
    guard isPad else { return 10 }
    switch LayoutLoader.iPadSizeClass {
    case .small: return 12
    case .medium: return 14
    case .large: return 10
    }
}
```

- [ ] **Step 4: Check expanded panel metric calls**

Run: `rg -n "ComposingPopup|CandidateBar|KeyboardRow|LayoutMetrics\\.Key" LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift LimeIME-iOS/LimeKeyboard/CandidateBarView.swift LimeIME-iOS/LimeKeyboard/KeyboardView.swift`

Expected: All calls compile through existing selectors. Direct references to `LayoutMetrics.*.Pad` should be gone outside `LayoutMetrics.swift`.

- [ ] **Step 5: Build the extension**

Run: `xcodebuild -project LimeIME-iOS/LimeIME.xcodeproj -scheme LimeKeyboard -configuration Debug -destination 'generic/platform=iOS Simulator' -derivedDataPath .Codex/DerivedData build`

Expected: `** BUILD SUCCEEDED **`.

---

### Task 5: Recompute Tier During Controller Layout Changes

**Files:**
- Modify: `LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift`

- [ ] **Step 1: Add a helper to sync host idiom and tier**

Add near the `isOnPad` property:

```swift
@discardableResult
private func syncLayoutEnvironmentFromTraits() -> Bool {
    let oldHostIsPad = LayoutLoader.hostIsPad
    let oldTier = LayoutLoader.iPadSizeClass

    LayoutLoader.hostIsPad = isOnPad
    if isOnPad {
        let screen = UIScreen.main.bounds
        let shortSide = min(screen.width, screen.height)
        LayoutLoader.iPadSizeClass = IPadSizeClass.resolve(shortSideExtent: shortSide)
    } else {
        LayoutLoader.iPadSizeClass = .large
    }

    let changed = oldHostIsPad != LayoutLoader.hostIsPad || oldTier != LayoutLoader.iPadSizeClass
    if changed {
        LayoutLoader.clearCache()
    }
    return changed
}
```

- [ ] **Step 2: Use the helper in `viewDidLoad` before the first layout load**

Replace:

```swift
LayoutLoader.hostIsPad = isOnPad
LayoutLoader.clearCache()
```

with:

```swift
syncLayoutEnvironmentFromTraits()
```

- [ ] **Step 3: Use the helper in `viewWillLayoutSubviews`**

Replace the current `LayoutLoader.hostIsPad != nowPad` block with:

```swift
if syncLayoutEnvironmentFromTraits() {
    if let reloaded = LayoutLoader.load(currentLayout.id) {
        currentLayout = reloaded
        keyboardView?.setLayout(reloaded)
    }
}
```

Keep the existing orientation and split-mode handling after this block. Keep `applyHeight()` at the end.

- [ ] **Step 4: Use the helper in `traitCollectionDidChange`**

Replace the current host-is-pad cache-clear block with the same reload pattern:

```swift
if syncLayoutEnvironmentFromTraits() {
    if let reloaded = LayoutLoader.load(currentLayout.id) {
        currentLayout = reloaded
        keyboardView?.setLayout(reloaded)
    }
}
```

Keep the existing theme-change and horizontal-size-class code.

- [ ] **Step 5: Build the extension**

Run: `xcodebuild -project LimeIME-iOS/LimeIME.xcodeproj -scheme LimeKeyboard -configuration Debug -destination 'generic/platform=iOS Simulator' -derivedDataPath .Codex/DerivedData build`

Expected: `** BUILD SUCCEEDED **`.

---

### Task 6: Keep Production Layout Selection Unchanged

**Files:**
- Read-only audit: `LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift`
- Read-only audit: `LimeIME-iOS/LimeKeyboard/Layouts/*`

- [ ] **Step 1: Confirm no English-family production collapse**

Do not add a controller canonicalizer that redirects production iPad
English-family layouts to a smaller set of full `_ipad` files. That is
outside the narrow-keyboard scope.

Run: `rg -n "canonicalLayoutIdForCurrentHost|lime_email_ipad|lime_url_ipad|lime_english_number_ipad|lime_number_ipad|lime_shift_ipad" LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift`

Expected: No new production-layout collapse logic.

- [ ] **Step 2: Confirm production English layouts remain present**

Run: `rg --files LimeIME-iOS/LimeKeyboard/Layouts | rg "lime_(email|url|english_number|number|shift)_ipad.*\\.json$"`

Expected: Existing production layout files are still present if they
were present before this work. Missing files are out of scope and must
be restored manually.

- [ ] **Step 3: Build the extension**

Run: `xcodebuild -project LimeIME-iOS/LimeIME.xcodeproj -scheme LimeKeyboard -configuration Debug -destination 'generic/platform=iOS Simulator' -derivedDataPath .Codex/DerivedData build`

Expected: `** BUILD SUCCEEDED **`.

---

### Task 7: Add Narrow Symbol Pages Only

**Files:**
- Add: `LimeIME-iOS/LimeKeyboard/Layouts/symbols1_ipad_narrow.json`
- Add: `LimeIME-iOS/LimeKeyboard/Layouts/symbols2_ipad_narrow.json`

- [ ] **Step 1: Add iPad 11-style narrow symbol pages**

Create `symbols1_ipad_narrow.json` and `symbols2_ipad_narrow.json`
by hand. Do not generate them with the generic trimmer.

Required visible layout:

```text
symbols1_ipad_narrow
r0: [Tab] 1 2 3 4 5 6 7 8 9 0 [⌫]
r1: [中] @ # $ & * ( ) ' " [↩]
r2: [#+=] % - + = / ; : ! ? [#+=]
bottom: SYMBOL_BOTTOM_NARROW

symbols2_ipad_narrow
r0: [Tab] 1 2 3 4 5 6 7 8 9 0 [⌫]
r1: [中] € £ ¥ _ ^ [ ] { } [↩]
r2: [123] § | ~ … \ < > ! ? [123]
bottom: SYMBOL_BOTTOM_NARROW
```

Apple's leading `undo` / `redo` slot is replaced with the mode `[中]`
key. Use the existing IM-toggle key code/behavior, not a new command.

- [ ] **Step 2: Confirm production symbol pages are untouched**

Run: `git diff --name-only -- LimeIME-iOS/LimeKeyboard/Layouts | rg "symbols[0-9]+_ipad\\.json" || true`

Expected: No output. Production symbol pages are not part of this scope.

- [ ] **Step 3: Build the extension**

Run: `xcodebuild -project LimeIME-iOS/LimeIME.xcodeproj -scheme LimeKeyboard -configuration Debug -destination 'generic/platform=iOS Simulator' -derivedDataPath .Codex/DerivedData build`

Expected: `** BUILD SUCCEEDED **`.

---

### Task 8: Audit Production Layout Scope Before Narrow Generation

**Files:**
- Read-only audit: `LimeIME-iOS/LimeKeyboard/Layouts/*`

- [ ] **Step 1: Confirm production layouts are clean before generation**

Run: `git diff --name-only -- LimeIME-iOS/LimeKeyboard/Layouts | rg -v "_ipad_narrow" || true`

Expected: No output. Any changed layout file whose filename does not contain `_ipad_narrow` is out of scope and must be manually restored before continuing.

- [ ] **Step 2: Confirm the narrow generator only writes narrow files**

Run: `rg -n "write_text|unlink|remove|rename|replace" scripts/trim_ipad_layout.py`

Expected: Any write path must resolve to a filename containing `_ipad_narrow`. The script may read production `_ipad` layouts as inputs, but must not modify them.

---

### Task 9: Create the Narrow Layout Trimmer

**Files:**
- Create: `scripts/trim_ipad_layout.py`
- Create generated: `LimeIME-iOS/LimeKeyboard/Layouts/*_ipad_narrow*.json`

- [ ] **Step 1: Create the script header and constants**

Create `scripts/trim_ipad_layout.py` with:

```python
#!/usr/bin/env python3
# trim_ipad_layout.py
# Generates *_ipad_narrow*.json layout files from finished *_ipad*.json
# layouts for iPad 11" and iPad mini tiers. A file is emitted when content
# rows or the bottom row differ from the full iPad source.
# Usage: python3 scripts/trim_ipad_layout.py  (run from repo root)
# Output: LimeIME-iOS/LimeKeyboard/Layouts/*_ipad_narrow*.json

import copy
import json
from pathlib import Path

LAYOUTS_DIR = Path("LimeIME-iOS/LimeKeyboard/Layouts")

DROP_QUOTA_NARROW = {
    "digit_left": 1,
    "digit_right": 2,
    "qwerty": 3,
    "asdf": 2,
    "zxcv": 1,
}

SYMBOL_LAYOUTS = {"symbols1", "symbols2", "symbols3"}

IM_ROOTS = {
    "lime_phonetic": "1qaz2wsx3edc4rfv5tgb6yhn7ujm8ik,9ol.0p;/-",
    "lime_cj": "qwertyuiopasdfghjklzxcvbnm",
    "lime_cj_number": "qwertyuiopasdfghjklzxcvbnm",
    "lime_dayi": "1234567890qwertyuiopasdfghjkl;zxcvbnm,./",
    "lime_dayi_sym": "1234567890qwertyuiopasdfghjkl;zxcvbnm,./",
    "lime_array": "qazwsxedcrfvtgbyhnujmik,ol.p;/",
    "lime_array_number": "qazwsxedcrfvtgbyhnujmik,ol.p;/",
    "lime_et26": "qazwsxedcrfvtgbyhnujmikolp,.",
    "lime_et_41": "abcdefghijklmnopqrstuvwxyz12347890-=;',./",
    "lime_hsu": "azwsxedcrfvtgbyhnujmikolpq,.",
    "lime_wb": ",./mn",
    "lime_ez": "',-./0123456789;=[\\]abcdefghijklmnopqrstuvwxyz",
    "lime_hs": "',-./0123456789;=[\\]abcdefghijklmnopqrstuvwxyz",
    "lime_english": "",
    "lime_abc": "",
    "lime_email": "",
    "lime_url": "",
    "lime_english_number": "",
    "lime_number": "",
    "lime_shift": "",
    "symbols1": "",
    "symbols2": "",
    "symbols3": "",
}

BOTTOM_NARROW = [
    {"code": -200, "label": "globe", "sublabel": "", "widthPercent": 9.0, "icon": "globe", "isModifier": True, "isRepeatable": False, "isSticky": False, "popupKeyboard": "", "popupCharacters": "", "longPressCode": -100},
    {"code": -9, "label": "abc", "sublabel": "", "widthPercent": 11.0, "icon": "", "isModifier": True, "isRepeatable": False, "isSticky": False, "popupKeyboard": "", "popupCharacters": "", "longPressCode": 0},
    {"code": -201, "label": "", "sublabel": "", "widthPercent": 8.0, "icon": "face.smiling", "isModifier": True, "isRepeatable": False, "isSticky": False, "popupKeyboard": "", "popupCharacters": "", "longPressCode": 0},
    {"code": 32, "label": "", "sublabel": "", "widthPercent": 53.0, "icon": "space.bar", "isModifier": False, "isRepeatable": True, "isSticky": False, "popupKeyboard": "", "popupCharacters": "", "longPressCode": 0},
    {"code": -2, "label": ".?123", "sublabel": "", "widthPercent": 11.0, "icon": "", "isModifier": True, "isRepeatable": False, "isSticky": False, "popupKeyboard": "", "popupCharacters": "", "longPressCode": 0},
    {"code": -3, "label": "", "sublabel": "", "widthPercent": 8.0, "icon": "keyboard.chevron.compact.down", "isModifier": True, "isRepeatable": False, "isSticky": False, "popupKeyboard": "", "popupCharacters": "", "longPressCode": -100},
]

SYMBOL_BOTTOM_NARROW = [
    {"code": -200, "label": "globe", "sublabel": "", "widthPercent": 9.0, "icon": "globe", "isModifier": True, "isRepeatable": False, "isSticky": False, "popupKeyboard": "", "popupCharacters": "", "longPressCode": -100},
    {"code": -2, "label": "abc", "sublabel": "", "widthPercent": 11.0, "icon": "", "isModifier": True, "isRepeatable": False, "isSticky": False, "popupKeyboard": "", "popupCharacters": "", "longPressCode": 0},
    {"code": -201, "label": "", "sublabel": "", "widthPercent": 8.0, "icon": "face.smiling", "isModifier": True, "isRepeatable": False, "isSticky": False, "popupKeyboard": "", "popupCharacters": "", "longPressCode": 0},
    {"code": 32, "label": "", "sublabel": "", "widthPercent": 53.0, "icon": "space.bar", "isModifier": False, "isRepeatable": True, "isSticky": False, "popupKeyboard": "", "popupCharacters": "", "longPressCode": 0},
    {"code": -2, "label": "abc", "sublabel": "", "widthPercent": 11.0, "icon": "", "isModifier": True, "isRepeatable": False, "isSticky": False, "popupKeyboard": "", "popupCharacters": "", "longPressCode": 0},
    {"code": -3, "label": "", "sublabel": "", "widthPercent": 8.0, "icon": "keyboard.chevron.compact.down", "isModifier": True, "isRepeatable": False, "isSticky": False, "popupKeyboard": "", "popupCharacters": "", "longPressCode": -100},
]
```

- [ ] **Step 2: Add helpers**

Add:

```python
def base_id(layout_id):
    result = layout_id
    if result.endswith("_shift"):
        result = result[:-6]
    if result.endswith("_ipad_narrow"):
        result = result[:-12]
    elif result.endswith("_ipad"):
        result = result[:-5]
    return result


def roots_for(layout_id):
    return set(IM_ROOTS.get(base_id(layout_id), ""))


def spacer_from(key):
    result = copy.deepcopy(key)
    result["code"] = 0
    result["label"] = ""
    result["sublabel"] = ""
    result["icon"] = ""
    result["isModifier"] = False
    result["isRepeatable"] = False
    result["isSticky"] = False
    result["popupKeyboard"] = ""
    result["popupCharacters"] = ""
    result["longPressCode"] = 0
    return result


def is_visible(key):
    return key.get("code", 0) != 0 or key.get("label", "") or key.get("icon", "")


def is_trimmable(key, root_set):
    code = key.get("code", 0)
    if code <= 0:
        return False
    try:
        char = chr(code).lower()
    except ValueError:
        return False
    return (
        char not in root_set
        and "\\n" in key.get("label", "")
        and not key.get("popupKeyboard", "")
    )
```

- [ ] **Step 3: Add row classification**

Add:

```python
def row_class(row):
    if row.get("isBottomRow", False):
        return "bottom"
    keys = row.get("keys", [])
    codes = [key.get("code", 0) for key in keys]
    printable = [code for code in codes if code > 0]
    if (48 in codes and 49 in codes) or (33 in codes and 41 in codes):
        return "digit"
    if printable and printable[-1] in (112, 80):
        return "qwerty"
    if 10 in codes:
        return "asdf"
    if 122 in codes or 90 in codes:
        return "zxcv"
    return "other"
```

- [ ] **Step 4: Add trim functions**

Add:

```python
def trim_right_tail(row, class_name, root_set):
    keys = row["keys"]
    quota = DROP_QUOTA_NARROW[class_name]
    stop = len(keys)

    for idx in range(len(keys) - 1, -1, -1):
        code = keys[idx].get("code", 0)
        if class_name == "qwerty" and code == -5:
            stop = idx
            break
        if class_name == "asdf" and code == 10:
            stop = idx
            break
        if class_name == "zxcv" and code == -1:
            stop = idx
            break

    idx = stop - 1
    while idx >= 0 and quota > 0:
        if not is_trimmable(keys[idx], root_set):
            break
        keys[idx] = spacer_from(keys[idx])
        quota -= 1
        idx -= 1


def trim_digit(row, root_set):
    keys = row["keys"]
    digit_indexes = [idx for idx, key in enumerate(keys) if key.get("code") in range(48, 58)]
    if not digit_indexes:
        return
    min_idx = min(digit_indexes)
    max_idx = max(digit_indexes)

    quota = DROP_QUOTA_NARROW["digit_left"]
    for idx in range(0, min_idx):
        if quota <= 0:
            break
        if not is_trimmable(keys[idx], root_set):
            break
        keys[idx] = spacer_from(keys[idx])
        quota -= 1

    last_non_modifier = len(keys) - 1
    for idx in range(len(keys) - 1, -1, -1):
        if keys[idx].get("code") == -5:
            last_non_modifier = idx - 1
            break

    quota = DROP_QUOTA_NARROW["digit_right"]
    for idx in range(last_non_modifier, max_idx, -1):
        if quota <= 0:
            break
        if not is_trimmable(keys[idx], root_set):
            break
        keys[idx] = spacer_from(keys[idx])
        quota -= 1


def enforce_visible_cap(row, class_name):
    if class_name not in ("qwerty", "asdf", "zxcv"):
        return
    if sum(1 for key in row["keys"] if is_visible(key)) <= 13:
        return

    drop_codes = {
        "qwerty": {9},
        "asdf": {-9},
        "zxcv": {-1},
    }[class_name]
    indexes = range(len(row["keys"]))
    if class_name == "zxcv":
        indexes = range(len(row["keys"]) - 1, -1, -1)
    for idx in indexes:
        if row["keys"][idx].get("code") in drop_codes:
            row["keys"][idx] = spacer_from(row["keys"][idx])
            return
```

- [ ] **Step 5: Add layout processing and main**

Add:

```python
def trim_layout(layout):
    result = copy.deepcopy(layout)
    root_set = roots_for(result["id"])
    changed = False

    for row in result.get("rows", []):
        before = copy.deepcopy(row)
        cls = row_class(row)
        if cls == "bottom":
            row["keys"] = copy.deepcopy(BOTTOM_NARROW)
        elif cls == "digit":
            trim_digit(row, root_set)
        elif cls in ("qwerty", "asdf", "zxcv"):
            trim_right_tail(row, cls, root_set)
            enforce_visible_cap(row, cls)
        if row != before:
            changed = True

    result["id"] = result["id"].replace("_ipad", "_ipad_narrow", 1)
    return result, changed


def main():
    written = 0
    input_paths = sorted(LAYOUTS_DIR.glob("*_ipad.json")) + sorted(LAYOUTS_DIR.glob("*_ipad_shift.json"))
    for path in input_paths:
        if path.name.endswith("_ipad_narrow.json"):
            continue
        if base_id(path.stem) in SYMBOL_LAYOUTS:
            continue
        source = json.loads(path.read_text(encoding="utf-8-sig"))
        trimmed, changed = trim_layout(source)
        out_path = path.with_name(trimmed["id"] + ".json")
        if not changed:
            if out_path.exists():
                out_path.unlink()
            continue
        out_path.write_text(
            json.dumps(trimmed, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8-sig",
        )
        written += 1
    print(f"wrote {written} narrow iPad layouts")


if __name__ == "__main__":
    main()
```

- [ ] **Step 6: Run the trimmer**

Run: `python3 scripts/trim_ipad_layout.py`

Expected: It prints `wrote N narrow iPad layouts` where `N` is greater than `0`.

- [ ] **Step 7: Verify generated JSON parses**

Run: `python3 -c 'import json,pathlib; [json.loads(p.read_text(encoding="utf-8-sig")) for p in pathlib.Path("LimeIME-iOS/LimeKeyboard/Layouts").glob("*_ipad_narrow*.json")]'`

Expected: Exit code `0`.

---

### Task 10: Verify Narrow Layout Invariants

**Files:**
- Create: `.Codex/scripts/verify_ipad_narrow_layouts.py`

- [ ] **Step 1: Create a verification script**

Create `.Codex/scripts/verify_ipad_narrow_layouts.py` with:

```python
#!/usr/bin/env python3
# verify_ipad_narrow_layouts.py
# Verifies generated *_ipad_narrow*.json files preserve row width sums and
# keep root/modifier safety invariants from docs/IPAD_KB_SIZE_TIERS.md.
# Usage: python3 .Codex/scripts/verify_ipad_narrow_layouts.py

import json
from pathlib import Path

LAYOUTS_DIR = Path("LimeIME-iOS/LimeKeyboard/Layouts")


def visible_count(row):
    return sum(
        1 for key in row["keys"]
        if key.get("code", 0) != 0 or key.get("label", "") or key.get("icon", "")
    )


def main():
    files = sorted(LAYOUTS_DIR.glob("*_ipad_narrow.json")) + sorted(LAYOUTS_DIR.glob("*_ipad_narrow_shift.json"))
    if not files:
        raise SystemExit("no *_ipad_narrow*.json files found")

    for path in files:
        data = json.loads(path.read_text(encoding="utf-8-sig"))
        for index, row in enumerate(data["rows"]):
            total = sum(float(key["widthPercent"]) for key in row["keys"])
            if abs(total - 100.0) > 0.01:
                raise SystemExit(f"{path.name} row {index} width sum {total}")
            if not row.get("isBottomRow", False) and visible_count(row) > 13:
                raise SystemExit(f"{path.name} row {index} has too many visible cells")
        bottom_rows = [row for row in data["rows"] if row.get("isBottomRow", False)]
        if len(bottom_rows) != 1:
            raise SystemExit(f"{path.name} has {len(bottom_rows)} bottom rows")
        bottom = bottom_rows[0]["keys"]
        bottom_codes = [key["code"] for key in bottom]
        bottom_icons = [key.get("icon", "") for key in bottom]
        bottom_labels = [key.get("label", "") for key in bottom]
        if -99 in bottom_codes or "mic" in bottom_icons:
            raise SystemExit(f"{path.name} bottom row contains mic")
        if -201 not in bottom_codes or "face.smiling" not in bottom_icons:
            raise SystemExit(f"{path.name} bottom row is missing emoji")
        if data["id"] in {"symbols1_ipad_narrow", "symbols2_ipad_narrow"}:
            if bottom_labels != ["globe", "abc", "", "", "abc", ""]:
                raise SystemExit(f"{path.name} symbol bottom labels {bottom_labels}")
        elif bottom_codes != [-200, -2, -201, 32, -2, -3]:
            raise SystemExit(f"{path.name} bottom row codes {bottom_codes}")

    print(f"verified {len(files)} narrow iPad layouts")


if __name__ == "__main__":
    main()
```

- [ ] **Step 2: Run the verifier**

Run: `python3 .Codex/scripts/verify_ipad_narrow_layouts.py`

Expected: It prints `verified N narrow iPad layouts`.

---

### Task 11: Audit Production Layouts Are Untouched

**Files:**
- No planned edits.

- [ ] **Step 1: Confirm no non-narrow layout files changed**

Run: `git diff --name-only -- LimeIME-iOS/LimeKeyboard/Layouts | rg -v "_ipad_narrow" || true`

Expected: No output.

- [ ] **Step 2: Confirm no production layout deletions**

Run: `git status --short LimeIME-iOS/LimeKeyboard/Layouts | rg "^ D|^D" || true`

Expected: No output. Production `_ipad`, phone, symbol, and other shipped layout files are read-only for this plan.

- [ ] **Step 3: Build resource bundle**

Run: `xcodebuild -project LimeIME-iOS/LimeIME.xcodeproj -scheme LimeKeyboard -configuration Debug -destination 'generic/platform=iOS Simulator' -derivedDataPath .Codex/DerivedData build`

Expected: `** BUILD SUCCEEDED **`.

---

### Task 12: Update Sibling Docs

**Files:**
- Modify: `docs/IPAD_KEYBOARD.md`
- Modify: `docs/IPAD_KB_LAYOUT_COVERTER.md`
- Modify: `docs/IPAD_KB_SIZE_TIERS.md`

- [ ] **Step 1: Add cross-reference notes**

In both sibling docs, add a short note near their iPad layout sections:

```markdown
For per-device iPad size tiers and `_ipad_narrow` fallback behavior, see `IPAD_KB_SIZE_TIERS.md`.
```

- [ ] **Step 2: Verify docs references**

Run: `rg -n "_ipad_narrow|IPadSizeClass|symbols3_ipad|lime_email_ipad" docs/IPAD_KEYBOARD.md docs/IPAD_KB_LAYOUT_COVERTER.md docs/IPAD_KB_SIZE_TIERS.md`

Expected: `_ipad_narrow` and `IPadSizeClass` are documented. Any production `_ipad` layout reference must describe an existing read-only source/fallback layout, not a delete/modify target.

---

### Task 13: Ponytail Review Before Verification

**Files:**
- No planned edits unless review finds unnecessary complexity.

- [ ] **Step 1: Run `ponytail-review` on the implementation diff**

Review the full branch diff for over-engineering only. Findings should use
the `ponytail-review` format: one line per finding, with what to cut and what
replaces it.

- [ ] **Step 2: Apply accepted simplifications**

Use targeted edits only. Do not add abstractions, do not rewrite files from
scratch, and do not use git revert commands.

- [ ] **Step 3: Re-run focused checks for changed files**

Run the smallest command that verifies each simplification. If no findings
exist, record `Lean already. Ship.` and continue.

---

### Task 14: Final Build and Behavior Verification

**Files:**
- No planned edits.

- [ ] **Step 1: Run narrow JSON pipeline only**

Run: `git diff --name-only -- LimeIME-iOS/LimeKeyboard/Layouts | rg -v "_ipad_narrow" || true`

Expected: No output. Any non-narrow layout diff is out of scope.

Run: `python3 scripts/trim_ipad_layout.py`

Expected: Prints generated narrow layout count.

Run: `python3 .Codex/scripts/verify_ipad_narrow_layouts.py`

Expected: Prints verified narrow layout count.

Run: `git status --short LimeIME-iOS/LimeKeyboard/Layouts | rg "^ D|^D" || true`

Expected: No output. No production layout files are deleted.

- [ ] **Step 2: Build app and keyboard schemes**

Run: `xcodebuild -project LimeIME-iOS/LimeIME.xcodeproj -scheme LimeKeyboard -configuration Debug -destination 'generic/platform=iOS Simulator' -derivedDataPath .Codex/DerivedData build`

Expected: `** BUILD SUCCEEDED **`.

Run: `xcodebuild -project LimeIME-iOS/LimeIME.xcodeproj -scheme LimeIME -configuration Debug -destination 'generic/platform=iOS Simulator' -derivedDataPath .Codex/DerivedData build`

Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 3: Run available unit tests**

Run: `xcodebuild test -project LimeIME-iOS/LimeIME.xcodeproj -scheme LimeIME -destination 'platform=iOS Simulator,name=iPhone 16' -derivedDataPath .Codex/DerivedData`

Expected: Existing tests pass. If the simulator name is unavailable, run `xcrun simctl list devices available` and choose an available iPhone simulator.

- [ ] **Step 4: Manual simulator/hardware matrix**

Verify these exact cases:

```text
iPhone host:
- Loads bare phone JSON.
- English email/url/number/symbol pages still work.
- No `_ipad` or `_ipad_narrow` resource is selected.

iPad 13" host:
- `LayoutLoader.iPadSizeClass == .large`.
- Existing `_ipad.json` files load.
- Key row heights and candidate bar height match current shipped behavior.

iPad 11" host:
- `LayoutLoader.iPadSizeClass == .medium`.
- Existing `_ipad_narrow.json` loads when present.
- Missing narrow files fall back to `_ipad.json`.
- Regular row height is 58 before user `keyboard_size` scaling.

iPad mini host:
- `LayoutLoader.iPadSizeClass == .small`.
- Existing `_ipad_narrow.json` loads when present.
- Missing narrow files fall back to `_ipad.json`.
- Regular row height is 52 before user `keyboard_size` scaling.
```

- [ ] **Step 5: Check git diff without reverting anything**

Run: `git status --short`

Expected: Shows only intended source, script, docs, and layout JSON changes.

Run: `git diff --stat`

Expected: Confirms the change footprint matches this plan.

## Commit Guidance

When committing, do not add any Codex, Claude, Anthropic, or AI co-author trailer. Use small commits such as:

```bash
git add docs/IPAD_KB_SIZE_TIERS.md docs/IPAD_KB_SIZE_TIERS_IMPLEMENTATION_PLAN.md
git commit -m "docs: plan iPad keyboard size tiers"

git add LimeIME-iOS/LimeKeyboard/LayoutMetrics.swift LimeIME-iOS/LimeKeyboard/LayoutLoader.swift LimeIME-iOS/LimeKeyboard/KeyboardView.swift LimeIME-iOS/LimeKeyboard/CandidateBarView.swift LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift
git commit -m "feat: add iPad keyboard size tiers"

git add scripts/trim_ipad_layout.py LimeIME-iOS/LimeKeyboard/Layouts/*_ipad_narrow*.json
git commit -m "feat: generate narrow iPad keyboard layouts"
```

## Self-Review Notes

- Spec coverage: Tasks 1-5 cover tier detection, metrics, phone unchanged, and iPad 13 unchanged. Tasks 6-7 cover iPad English/symbol narrow behavior. Tasks 8-10 cover production-layout audit and narrow trimming. Tasks 11-12 cover non-narrow layout audit and docs. Task 13 covers ponytail review. Task 14 covers final verification.
- Placeholder scan: No implementation step is left as "TBD"; where a codebase location must be found, the plan provides the exact search command and the exact code to insert.
- Type consistency: The shared type is `IPadSizeClass`; the shared state is `LayoutLoader.iPadSizeClass`; all metric selectors read that same state.
