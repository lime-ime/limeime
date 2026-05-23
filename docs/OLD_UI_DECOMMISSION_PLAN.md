# Old UI Decommission Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Keep each batch small, verify after every batch, and do not touch protected new UI files unless a compile error proves a stale old-UI reference remains.

**Goal:** Remove obsolete Android settings UI code, resources, and tests after the completed LIME Settings backport while preserving the working new UI and all import/share/backup behavior.

**Architecture:** This cleanup is deletion-first and evidence-driven. The protected new UI surface remains the source of truth; old drawer/setup/import-grid artifacts are removed only after `rg` proves their references are stale or after tests are rewritten to cover the new surface.

**Tech Stack:** Android Java app under `LimeStudio`, Gradle, AndroidX Fragment/Test, Material Components, existing instrumentation tests on `emulator-5554`.

---

## Baseline Recorded Before Cleanup

- Worktree: `.Codex/worktrees/cleanup-old-settings-ui`
- Branch: `cleanup-old-settings-ui`
- `./gradlew clean build`: PASS, `BUILD SUCCESSFUL`, 83 tasks.
- `adb devices`: `emulator-5554 device`.
- `./gradlew connectedDebugAndroidTest`: baseline unstable before any cleanup.
  - Ran 654/1056 tests.
  - 3 skipped, 1 failed.
  - Failure: `net.toload.main.hd.ManageImFragmentTest#testAsynchronousRecordLoadingIsThreadSafe`.
  - Instrumentation stopped after the app/test process was killed with signal 9.
- Focus rerun:
  - `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=net.toload.main.hd.ManageImFragmentTest#testAsynchronousRecordLoadingIsThreadSafe`
  - PASS, 1/1 tests.
- Interpretation: final cleanup must pass `clean build`; full connected tests should be rerun, but the existing full-suite instability must be compared against this baseline rather than treated as a cleanup regression by itself.

## Protected New UI Surface

Do not edit these files during teardown unless a compiler/resource error points to a stale old-UI reference. If any protected file changes, record the reason in this plan before proceeding.

- `LimeStudio/app/src/main/java/net/toload/main/hd/ui/LIMESettings.java`
- `LimeStudio/app/src/main/java/net/toload/main/hd/ui/NavigationManager.java`
- `LimeStudio/app/src/main/java/net/toload/main/hd/ui/ProgressManager.java`
- `LimeStudio/app/src/main/java/net/toload/main/hd/ui/ShareManager.java`
- `LimeStudio/app/src/main/java/net/toload/main/hd/ui/IntentHandler.java`
- `LimeStudio/app/src/main/java/net/toload/main/hd/ui/LIMEPreference.java`
- `LimeStudio/app/src/main/java/net/toload/main/hd/ui/controller/BaseController.java`
- `LimeStudio/app/src/main/java/net/toload/main/hd/ui/controller/ManageImController.java`
- `LimeStudio/app/src/main/java/net/toload/main/hd/ui/controller/SetupImController.java`
- `LimeStudio/app/src/main/java/net/toload/main/hd/ui/view/SetupFragment.java`
- `LimeStudio/app/src/main/java/net/toload/main/hd/ui/view/ImListFragment.java`
- `LimeStudio/app/src/main/java/net/toload/main/hd/ui/view/ImDetailFragment.java`
- `LimeStudio/app/src/main/java/net/toload/main/hd/ui/view/ImInstallFragment.java`
- `LimeStudio/app/src/main/java/net/toload/main/hd/ui/view/TwoPaneHostFragment.java`
- `LimeStudio/app/src/main/java/net/toload/main/hd/ui/view/DbManagerFragment.java`
- `LimeStudio/app/src/main/java/net/toload/main/hd/ui/view/LimePreferenceFragment.java`
- `LimeStudio/app/src/main/java/net/toload/main/hd/ui/view/ManageImFragment.java`
- `LimeStudio/app/src/main/java/net/toload/main/hd/ui/view/ManageRelatedFragment.java`
- `LimeStudio/app/src/main/java/net/toload/main/hd/ui/dialog/ImportDialog.java`
- `LimeStudio/app/src/main/java/net/toload/main/hd/ui/dialog/ShareDialog.java`
- `LimeStudio/app/src/main/java/net/toload/main/hd/ui/dialog/ManageImAddSheet.java`
- `LimeStudio/app/src/main/java/net/toload/main/hd/ui/dialog/ManageImEditSheet.java`
- `LimeStudio/app/src/main/java/net/toload/main/hd/ui/dialog/ManageRelatedAddSheet.java`
- `LimeStudio/app/src/main/java/net/toload/main/hd/ui/dialog/ManageRelatedEditSheet.java`
- `LimeStudio/app/src/main/res/layout/activity_main.xml`
- `LimeStudio/app/src/main/res/layout-sw600dp/activity_main.xml`
- `LimeStudio/app/src/main/res/menu/main_nav.xml`
- `LimeStudio/app/src/main/res/menu/im_list_menu.xml`
- `LimeStudio/app/src/main/res/menu/im_install_menu.xml`
- `LimeStudio/app/src/main/res/menu/menu_im_detail.xml`
- `LimeStudio/app/src/main/res/layout/fragment_setup.xml`
- `LimeStudio/app/src/main/res/layout/fragment_two_pane_im_host.xml`
- `LimeStudio/app/src/main/res/layout/fragment_im_list.xml`
- `LimeStudio/app/src/main/res/layout/fragment_im_detail.xml`
- `LimeStudio/app/src/main/res/layout/fragment_im_install.xml`
- `LimeStudio/app/src/main/res/layout/fragment_db_manager.xml`
- `LimeStudio/app/src/main/res/layout/fragment_lime_preference_host.xml`
- `LimeStudio/app/src/main/res/layout/sheet_manage_im_add.xml`
- `LimeStudio/app/src/main/res/layout/sheet_manage_im_edit.xml`
- `LimeStudio/app/src/main/res/layout/sheet_manage_related_add.xml`
- `LimeStudio/app/src/main/res/layout/sheet_manage_related_edit.xml`

## Candidate Old UI Artifacts

These are candidates, not automatic deletions. Each must pass the task-specific reference checks before removal.

- `LimeStudio/app/src/main/java/net/toload/main/hd/ui/view/NavigationDrawerView.java`
- `LimeStudio/app/src/main/java/net/toload/main/hd/ui/view/NavigationMenuItem.java`
- `LimeStudio/app/src/main/java/net/toload/main/hd/ui/view/SetupImFragment.java`
- `LimeStudio/app/src/main/java/net/toload/main/hd/ui/view/SetupImView.java`
- `LimeStudio/app/src/main/res/layout/fragment_setup_im.xml`
- `LimeStudio/app/src/main/res/layout/fragment_main.xml`
- `LimeStudio/app/src/main/res/layout/activity_setup_im_google.xml`
- `LimeStudio/app/src/main/res/menu/main.xml`
- `LimeStudio/app/src/main/res/values/strings.xml` entry `title_activity_setup_im_google`
- `LimeStudio/app/src/androidTest/java/net/toload/main/hd/NavigationDrawerFragmentTest.java`
- `LimeStudio/app/src/androidTest/java/net/toload/main/hd/NavigationManagerTest.java`
- old-name test classes that already validate new sheets and should be renamed or left for a later rename-only task:
  - `ManageImAddDialogTest.java`
  - `ManageImEditDialogTest.java`
  - `ManageRelatedAddDialogTest.java`
  - `ManageRelatedEditDialogTest.java`

## Task 1: Inventory References And Freeze The Removal List

**Files:**
- Modify: `docs/OLD_UI_DECOMMISSION_PLAN.md`
- Inspect: `LimeStudio/app/src/main/java`
- Inspect: `LimeStudio/app/src/main/res`
- Inspect: `LimeStudio/app/src/androidTest/java`

- [ ] **Step 1: Run old UI symbol inventory**

Run:

```bash
cd LimeStudio
rg -n "NavigationDrawer|NavigationDrawerView|NavigationMenuItem|SetupImFragment|SetupImView|fragment_setup_im|fragment_main|activity_setup_im_google|SetupImList|title_activity_setup_im_google|@menu/main|R.menu.main|R.layout.fragment_main|R.layout.fragment_setup_im|R.layout.activity_setup_im_google" app/src/main app/src/androidTest
```

Expected: every match is either in a candidate old artifact, in a test that proves old UI has been removed, or in `SetupImController` compatibility code that must be evaluated separately.

- [ ] **Step 2: Record kept references**

If any match is not removed in later tasks, add it here with reason:

```text
Kept reference:
- Path:
- Symbol:
- Reason:
```

- [ ] **Step 3: Run protected-file status check**

Run:

```bash
git diff --name-only
```

Expected: only `docs/OLD_UI_DECOMMISSION_PLAN.md` appears before source cleanup begins.

## Task 2: Remove Dead Navigation Drawer View Contracts

**Files:**
- Delete if unreferenced: `LimeStudio/app/src/main/java/net/toload/main/hd/ui/view/NavigationDrawerView.java`
- Delete if unreferenced: `LimeStudio/app/src/main/java/net/toload/main/hd/ui/view/NavigationMenuItem.java`
- Modify only if required by compile errors: `LimeStudio/app/src/main/java/net/toload/main/hd/ui/controller/SetupImController.java`
- Test: `LimeStudio/app/src/androidTest/java/net/toload/main/hd/NavigationDrawerFragmentTest.java`

- [ ] **Step 1: Prove whether old drawer view contracts are still used**

Run:

```bash
cd LimeStudio
rg -n "NavigationDrawerView|net\.toload\.main\.hd\.ui\.view\.NavigationMenuItem|new NavigationMenuItem|setNavigationDrawerView|navigationDrawerView" app/src/main/java app/src/androidTest/java
```

Expected before removal: matches may exist in `SetupImController`, `NavigationDrawerView.java`, `NavigationMenuItem.java`, and old drawer tests.

- [ ] **Step 2: Remove stale controller drawer-view callback only if it is unused**

If Step 1 shows `setNavigationDrawerView(...)` has no active caller, remove from `SetupImController`:

```java
import net.toload.main.hd.ui.view.NavigationDrawerView;
import net.toload.main.hd.ui.view.NavigationMenuItem;

private NavigationDrawerView navigationDrawerView;

public void setNavigationDrawerView(NavigationDrawerView view) {
    this.navigationDrawerView = view;
}
```

Also remove the private helper and callback block that exist solely to build drawer menu items for `navigationDrawerView`. Do not remove `getImConfigList()` or import/download methods used by the new IM Install screen.

- [ ] **Step 3: Delete unused drawer view files**

Delete only after Step 2 removes all references:

```text
LimeStudio/app/src/main/java/net/toload/main/hd/ui/view/NavigationDrawerView.java
LimeStudio/app/src/main/java/net/toload/main/hd/ui/view/NavigationMenuItem.java
```

- [ ] **Step 4: Verify compile/resource gate**

Run:

```bash
cd LimeStudio
./gradlew build
```

Expected: `BUILD SUCCESSFUL`.

## Task 3: Remove Legacy SetupIm Fragment And Layout

**Files:**
- Delete if unreferenced: `LimeStudio/app/src/main/java/net/toload/main/hd/ui/view/SetupImFragment.java`
- Delete if unreferenced: `LimeStudio/app/src/main/java/net/toload/main/hd/ui/view/SetupImView.java`
- Delete if unreferenced: `LimeStudio/app/src/main/res/layout/fragment_setup_im.xml`
- Modify only if stale references exist: tests that still import or instantiate `SetupImFragment`
- Protected replacement: `SetupFragment.java` and `fragment_setup.xml`

- [ ] **Step 1: Prove replacement wiring uses SetupFragment**

Run:

```bash
cd LimeStudio
rg -n "SetupFragment|SetupImFragment|SetupImView|fragment_setup_im|fragment_setup" app/src/main/java app/src/main/res app/src/androidTest/java
```

Expected: active activity/navigation code references `SetupFragment` and `fragment_setup`; only obsolete tests or obsolete compatibility code reference `SetupImFragment`, `SetupImView`, or `fragment_setup_im`.

- [ ] **Step 2: Remove obsolete SetupIm tests or retarget them**

For `LimeStudio/app/src/androidTest/java/net/toload/main/hd/SetupImFragmentTest.java`, choose one:

```text
Delete when every assertion targets controls that moved to SetupFragment/ImInstallFragment/DbManagerFragment and equivalent coverage exists elsewhere.
```

or:

```text
Retarget to SetupFragment only for activation buttons/status/About card, then rename in a later rename-only cleanup if desired.
```

Do not add broad new UI tests in this teardown task; keep it to removal or direct retargeting.

- [ ] **Step 3: Delete obsolete SetupIm source/layout**

Delete only after Step 1 and Step 2 leave no active references:

```text
LimeStudio/app/src/main/java/net/toload/main/hd/ui/view/SetupImFragment.java
LimeStudio/app/src/main/java/net/toload/main/hd/ui/view/SetupImView.java
LimeStudio/app/src/main/res/layout/fragment_setup_im.xml
```

- [ ] **Step 4: Verify compile/resource gate**

Run:

```bash
cd LimeStudio
./gradlew build
```

Expected: `BUILD SUCCESSFUL`.

## Task 4: Remove Obsolete Layout/Menu Resources

**Files:**
- Delete if unreferenced: `LimeStudio/app/src/main/res/layout/fragment_main.xml`
- Delete if unreferenced: `LimeStudio/app/src/main/res/layout/activity_setup_im_google.xml`
- Delete if unreferenced: `LimeStudio/app/src/main/res/menu/main.xml`
- Modify if unreferenced string remains: `LimeStudio/app/src/main/res/values/strings.xml`

- [ ] **Step 1: Check resource references**

Run:

```bash
cd LimeStudio
rg -n "R\.layout\.fragment_main|@layout/fragment_main|R\.layout\.activity_setup_im_google|@layout/activity_setup_im_google|R\.menu\.main|@menu/main|title_activity_setup_im_google" app/src/main app/src/androidTest
```

Expected: only the resource files themselves and possibly the string definition remain.

- [ ] **Step 2: Delete unreferenced obsolete resources**

Delete only resources with no references:

```text
LimeStudio/app/src/main/res/layout/fragment_main.xml
LimeStudio/app/src/main/res/layout/activity_setup_im_google.xml
LimeStudio/app/src/main/res/menu/main.xml
```

- [ ] **Step 3: Remove obsolete string if unused**

From `LimeStudio/app/src/main/res/values/strings.xml`, remove only this exact string if Step 1 shows no use:

```xml
<string name="title_activity_setup_im_google">SetupImGoogleActivity</string>
```

- [ ] **Step 4: Verify compile/resource gate**

Run:

```bash
cd LimeStudio
./gradlew build
```

Expected: `BUILD SUCCESSFUL`.

## Task 5: Remove Or Retarget Old UI Tests

**Files:**
- Delete or retarget: `LimeStudio/app/src/androidTest/java/net/toload/main/hd/NavigationDrawerFragmentTest.java`
- Delete or retarget: `LimeStudio/app/src/androidTest/java/net/toload/main/hd/NavigationManagerTest.java`
- Inspect only: `LimeStudio/app/src/androidTest/java/net/toload/main/hd/MainActivityTest.java`
- Inspect only: `LimeStudio/app/src/androidTest/java/net/toload/main/hd/LIMEPreferenceTest.java`
- Inspect only: `LimeStudio/app/src/androidTest/java/net/toload/main/hd/ManageImFragmentTest.java`

- [ ] **Step 1: Classify old UI tests**

Run:

```bash
cd LimeStudio
rg -n "NavigationDrawer|SetupImFragment|fragment_setup_im|SetupImList|MainActivity|LIMESettings|BottomNavigation|NavigationRail" app/src/androidTest/java/net/toload/main/hd
```

Expected:

```text
Delete: tests that only assert deprecated drawer/setup implementation details.
Keep: tests that verify LIMESettings, NavigationManager, BottomNavigationView/NavigationRail, preferences, IM manager, DB manager, import/share flows.
Retarget: tests with useful behavioral checks but old class/resource names.
```

- [ ] **Step 2: Remove old drawer-only tests**

Delete `NavigationDrawerFragmentTest.java` if its remaining assertions only verify stale drawer naming or deprecated compatibility. If it contains useful NavigationManager behavior coverage, move that coverage into `MainActivityTest.java` or `NavigationManagerTest.java` before deleting.

- [ ] **Step 3: Remove or retarget NavigationManagerTest**

If `NavigationManagerTest.java` only contains ignored/deprecated setup-fragment checks, delete it. If it contains active bottom-nav/tab selection coverage, keep and update names away from drawer language.

- [ ] **Step 4: Keep old-name sheet tests for a separate rename task**

Do not rename these during deletion cleanup unless they block compilation:

```text
ManageImAddDialogTest.java
ManageImEditDialogTest.java
ManageRelatedAddDialogTest.java
ManageRelatedEditDialogTest.java
```

They already assert the new `*Sheet` classes. Renaming them is cosmetic and should be separate from teardown.

- [ ] **Step 5: Verify focused test compile/package**

Run:

```bash
cd LimeStudio
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=net.toload.main.hd.MainActivityTest,net.toload.main.hd.LIMEPreferenceTest,net.toload.main.hd.ManageImFragmentTest,net.toload.main.hd.ManageImKeyboardDialogTest,net.toload.main.hd.ShareManagerTest
```

Expected: tests run. If the emulator reports an install/runtime infrastructure error instead of test failures, record it and immediately run `./gradlew build` again before proceeding.

## Task 6: Confirm Import, Share, Backup, And Controller Paths Were Not Removed

**Files:**
- Protected: `LimeStudio/app/src/main/java/net/toload/main/hd/ui/IntentHandler.java`
- Protected: `LimeStudio/app/src/main/java/net/toload/main/hd/ui/dialog/ImportDialog.java`
- Protected: `LimeStudio/app/src/main/java/net/toload/main/hd/ui/dialog/ShareDialog.java`
- Protected: `LimeStudio/app/src/main/java/net/toload/main/hd/ui/controller/SetupImController.java`
- Protected: `LimeStudio/app/src/main/java/net/toload/main/hd/ui/controller/ManageImController.java`
- Protected: `LimeStudio/app/src/main/java/net/toload/main/hd/DBServer.java`
- Protected: `LimeStudio/app/src/main/java/net/toload/main/hd/SearchServer.java`

- [ ] **Step 1: Run behavior-preservation tests**

Run:

```bash
cd LimeStudio
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=net.toload.main.hd.IntentHandlerTest,net.toload.main.hd.ImportDialogTest,net.toload.main.hd.ShareDialogTest,net.toload.main.hd.ShareManagerTest,net.toload.main.hd.SetupImControllerFlowsTest,net.toload.main.hd.IntegrationTestBackupRestore
```

Expected: no new failures compared with baseline. Any failure here blocks deletion because these paths are explicitly protected by `docs/LIME_SETTINGS_BACKPORT.md`.

- [ ] **Step 2: Verify no controller/server deletion occurred**

Run:

```bash
git diff --name-only -- LimeStudio/app/src/main/java/net/toload/main/hd/ui/controller LimeStudio/app/src/main/java/net/toload/main/hd/DBServer.java LimeStudio/app/src/main/java/net/toload/main/hd/SearchServer.java
```

Expected: empty, unless Task 2 required removing dead drawer callback compatibility from `SetupImController.java`. If non-empty, inspect and document why.

## Task 7: Final Verification And Diff Review

**Files:**
- Inspect: all changed files
- Modify: `docs/OLD_UI_DECOMMISSION_PLAN.md` only to record final verification evidence

- [ ] **Step 1: Run full clean build**

Run:

```bash
cd LimeStudio
./gradlew clean build
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run full connected suite**

Run:

```bash
cd LimeStudio
./gradlew connectedDebugAndroidTest
```

Expected: ideally PASS. If it fails with the known baseline pattern around `ManageImFragmentTest#testAsynchronousRecordLoadingIsThreadSafe` or emulator install/process instability, rerun the failing test(s) alone and record whether they pass alone.

- [ ] **Step 3: Run old-symbol absence check**

Run:

```bash
cd LimeStudio
rg -n "NavigationDrawerView|net\.toload\.main\.hd\.ui\.view\.NavigationMenuItem|SetupImFragment|SetupImView|fragment_setup_im|fragment_main|activity_setup_im_google|SetupImList|title_activity_setup_im_google|@menu/main|R\.menu\.main" app/src/main app/src/androidTest
```

Expected: no matches, except intentional historical comments in docs if any.

- [ ] **Step 4: Review changed files against protected list**

Run:

```bash
git diff --name-only
```

Expected: changed files are limited to this plan plus deleted obsolete old UI artifacts/tests and tightly scoped reference cleanup. Any protected new UI file in the diff must have a documented reason.

- [ ] **Step 5: Capture final summary**

Append final evidence here:

```text
Final evidence:
- clean build: `cd LimeStudio && ./gradlew clean build` passed, `BUILD SUCCESSFUL in 50s`, 83 actionable tasks executed.
- focused connected tests: `MainActivityTest,LIMEPreferenceTest,ManageImFragmentTest,ManageImKeyboardDialogTest,ShareManagerTest` passed, `BUILD SUCCESSFUL in 7m 54s`, 36 tests finished, 2 skipped, 0 failed.
- protected behavior tests: `IntentHandlerTest,ImportDialogTest,ShareDialogTest,ShareManagerTest,SetupImControllerFlowsTest,IntegrationTestBackupRestore` passed, `BUILD SUCCESSFUL in 1m 36s`, 42 tests finished, 0 failed.
- full connected tests: `cd LimeStudio && ./gradlew connectedDebugAndroidTest` passed, `BUILD SUCCESSFUL in 6m 45s`, 1047 tests finished, 7 skipped, 0 failed.
- old-symbol absence check: exact `rg` pattern for removed drawer/setup/load-dialog symbols returned no matches.
- protected-file diff review: protected files changed only where stale old-UI references had to be removed (`LIMESettings.java`, `NavigationManager.java`, `SetupImController.java`, `DbManagerFragment.java`, `ImInstallFragment.java`); import/share/backup focused tests passed afterward.
```

## Stop Conditions

- Stop if `./gradlew build` fails after a deletion batch and the fix would require changing new UI behavior instead of removing stale references.
- Stop if import/share/backup tests fail after deleting UI artifacts.
- Stop if a protected controller/server file needs non-trivial logic changes.
- Stop after three repeated attempts to stabilize the same emulator/instrumentation failure; investigate with external Android/Gradle documentation before the next move.
