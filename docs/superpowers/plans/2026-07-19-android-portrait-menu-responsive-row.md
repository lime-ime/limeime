# Android Portrait Menu Responsive Row Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep the Android keyboard side menu's portrait-mode title and choices on one line when they fit, with full-width zero-indent fallbacks for every segmented menu control.

**Architecture:** Put the portrait title and control in one row by default. The existing helper compares visible label widths with the measured group width, moves a constrained group below its title at full width, and stacks its buttons only if full width still cannot fit them; other title-above controls are full width directly in XML.

**Tech Stack:** Android XML, Java 11, Material `MaterialButtonToggleGroup`, AndroidX instrumentation tests.

## Global Constraints

- Keep the existing icon, labels, selected-value mapping, persistence, and numpad-only hidden split option.
- Add no dependency or new production component.
- Edited XML must be UTF-8 with BOM; Java must remain UTF-8 without BOM.

---

### Task 1: Responsive portrait-mode menu row

**Files:**
- Modify: `LimeStudio/app/src/main/res/layout/keyboard_menu_panel.xml:63`
- Modify: `LimeStudio/app/src/main/java/org/limeime/ui/view/SegmentedHanPreference.java:98`
- Create: `LimeStudio/app/src/androidTest/java/org/limeime/PortraitModeMenuLayoutTest.java`

**Interfaces:**
- Consumes: `SegmentedHanPreference.stackIfClipped(MaterialButtonToggleGroup)` and `R.layout.keyboard_menu_panel`.
- Produces: `SegmentedHanPreference.stackIfClippedNow(MaterialButtonToggleGroup group)`, which preserves a horizontal row when labels fit, otherwise uses the full content width before vertically stacking buttons.

- [ ] **Step 1: Write the failing instrumentation test**

Create `PortraitModeMenuLayoutTest.java`. Inflate `keyboard_menu_panel`, assert the portrait control stays horizontal at a wide width, assert its narrow fallback moves below the title at full width with zero indentation, and assert every similar title-above control has zero indentation.

- [ ] **Step 2: Run the focused test to verify RED**

Run:

```bash
./LimeStudio/gradlew -p LimeStudio connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=org.limeime.PortraitModeMenuLayoutTest
```

Expected: FAIL because the existing helper's ellipsis signal does not implement the measured-width rule.

- [ ] **Step 3: Implement the minimal responsive row**

In `keyboard_menu_panel.xml`, place the portrait title and group in one row and remove the 44dp indentation from every title-above segmented group. In `SegmentedHanPreference.java`, sum visible label widths and padding; when constrained, move the group below the title at full width with zero indentation, then stack buttons only if needed.

```java
group.post(() -> stackIfClippedNow(group));
```

- [ ] **Step 4: Run focused and regression checks**

Run:

```bash
./LimeStudio/gradlew -p LimeStudio connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=org.limeime.PortraitModeMenuLayoutTest
./LimeStudio/gradlew -p LimeStudio testDebugUnitTest assembleDebug
```

Expected: focused test PASS; unit tests and debug build succeed.

- [ ] **Step 5: Visually verify both layouts**

Manual Samsung-phone verification is left to the user by explicit request.

- [ ] **Step 6: Commit**

```bash
git add LimeStudio/app/src/main/res/layout/keyboard_menu_panel.xml \
  LimeStudio/app/src/main/java/org/limeime/ui/view/SegmentedHanPreference.java \
  LimeStudio/app/src/androidTest/java/org/limeime/PortraitModeMenuLayoutTest.java
git commit -m "fix(android): adapt portrait menu row to width"
```
