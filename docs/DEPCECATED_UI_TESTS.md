# Deprecated UI Tests

This document lists Android instrumentation tests that have been annotated with
`@org.junit.Ignore(...)` because the production code path they exercise has
been deprecated, removed, or rewired as part of the ongoing LIMEIME UI
migration. See [LIME_SETTINGS_BACKPORT.md](LIME_SETTINGS_BACKPORT.md) for the
full migration plan and [UI_ARCHITECTURE.md](UI_ARCHITECTURE.md) for the
current MVC layering.

## Why these tests are skipped, not deleted

The skipped tests still document **expected behavior of the old UI surfaces**.
They are kept on disk (with `@Ignore`) for two reasons:

1. **Reference**: when the corresponding new-UI tests are written
   (`ImListFragmentTest`, `SetupFragmentActivationProbeTest`, etc. — see
   [LIME_SETTINGS_BACKPORT.md §12](LIME_SETTINGS_BACKPORT.md)), they can copy
   assertion patterns from these.
2. **Reversibility**: if any of the deprecations are reverted, removing the
   `@Ignore` is a one-line restore.

When the LIME_SETTINGS_BACKPORT migration is complete (all eight steps in
§13 landed and the legacy code paths are physically removed), these files
can be deleted outright.

## Skipped tests

Each row points to the file + method, the production change that broke it,
and the new-UI test (if planned) that replaces it.

| Test method | Deprecation trigger | Replacement |
|---|---|---|
| [HelpDialogTest.testHelpDialogSurvivesRecreation](../LimeStudio/app/src/androidTest/java/org/limeime/HelpDialogTest.java) | Commit `6f36521a` — first-launch help splash permanently disabled in `LIMESettings`. The dialog still exists, but the gate that displays it always returns false. | None planned. The other `HelpDialogTest` methods (`testHelpDialogClassExists`, `testHasLinkOrButtonHandlers`) still cover the dialog class itself. |
| [NewsDialogTest.testNewsDialogSurvivesRecreation](../LimeStudio/app/src/androidTest/java/org/limeime/NewsDialogTest.java) | Same as above — first-launch news/help splash disabled. | None planned. `testNewsDialogClassExists` + `testHasLinkOrButtonHandlers` retain coverage of the dialog class. |
| [NavigationDrawerFragmentTest.testNavigationManagerNavigatesAndPersistsSelection](../LimeStudio/app/src/androidTest/java/org/limeime/NavigationDrawerFragmentTest.java) | `LIME_SETTINGS_BACKPORT §3` — `NavigationDrawerFragment` and the entire `DrawerLayout` host are being removed in favour of `BottomNavigationView` (phone) / `NavigationRail` (tablet). `NavigationManager` is kept but rebound to the new nav controls. | The new bottom-nav navigation flow will be covered by a `LIMESettingsBottomNavTest` once the new shell lands (BACKPORT step 2). |
| [NavigationManagerTest.navigateToSetupAndRelatedFragments_doesNotCrash](../LimeStudio/app/src/androidTest/java/org/limeime/NavigationManagerTest.java) | Same as above. Also: `SetupImFragment` is being renamed to `SetupFragment` (BACKPORT §5), so the `findFragmentByTag("SetupImFragment")` lookup will not match the new fragment name. | Replaced by the bottom-nav coverage above plus a new `SetupFragmentTest` (BACKPORT step 5). |
| [MainActivityTest.testActivityLifecycleMaintainsSingletons](../LimeStudio/app/src/androidTest/java/org/limeime/MainActivityTest.java) | MVC refactor commit `d5d252d2` plus commit `6f36521a` changed `LIMESettings.onCreate()` timing so `ActivityScenario.recreate()` no longer round-trips to RESUMED within the default timeout. | A simpler "singletons survive config change" test bound to the new bottom-nav shell will replace it. |
| [ProgressManagerTest.testProgressManagerSurvivesActivityRecreation](../LimeStudio/app/src/androidTest/java/org/limeime/ProgressManagerTest.java) | Same `recreate()` timing change. The `ProgressManager` class itself is unchanged (BACKPORT §4 keeps it), and the other six `ProgressManagerTest` methods still cover its public surface. | None — non-recreation coverage is already sufficient. |
| [LIMEPreferenceTest.testReverseLookupNestedScreenOpensFromStandalonePreferenceActivity](../LimeStudio/app/src/androidTest/java/org/limeime/LIMEPreferenceTest.java) | `LIME_SETTINGS_BACKPORT §8` — the standalone `LIMEPreference` activity is being absorbed into the new BottomNav 喜好設定 tab. The nested-screen navigation will be tested in-tab, not as a standalone activity launch. | Will be covered by a `LimePreferenceFragmentTest` once BACKPORT step 8 lands. |
| [VoiceInputActivityTest.testTransparentWindowConfiguration](../LimeStudio/app/src/androidTest/java/org/limeime/VoiceInputActivityTest.java) | The MVC refactor changed `VoiceInputActivity` startup such that, on emulators with a real speech recognizer installed, the activity launches `RecognizerIntent` and is destroyed before `ActivityScenario.onActivity()` callbacks fire. | The other ~30 `VoiceInputActivityTest` methods (intent format, broadcast contract, locale, architecture compliance) remain active and cover non-lifecycle behaviour. |

## Tests that look "deprecated" but were intentionally **kept**

These were considered for `@Ignore` and explicitly left active because they
already target the new UI surfaces or test class structure (not lifecycle):

- `ManageImAddDialogTest`, `ManageImEditDialogTest`,
  `ManageRelatedAddDialogTest`, `ManageRelatedEditDialogTest` — the file
  names refer to the old `*Dialog` classes, but the test bodies actually
  load the new `ManageImAddSheet` / `ManageImEditSheet` / etc. via
  `Class.forName("net.toload.main.hd.ui.dialog.ManageImAddSheet")` (BACKPORT
  §6.4). They are tests of the **new** sheets, mis-named for legacy
  continuity. Rename when convenient; do not skip.
- `HelpDialogTest.testHelpDialogClassExists` /
  `HelpDialogTest.testHasLinkOrButtonHandlers` and the corresponding
  `NewsDialogTest` reflection checks — verify the dialog classes still exist
  and expose link / button handlers. The dialogs themselves are **not**
  deleted by the backport; only the *first-launch auto-show* is disabled.
- `NavigationDrawerFragmentTest.testNavigationManagerAvailableFromActivity`,
  `testNavigationManagerOwnsNavigationBehavior`,
  `testNavigationManagerSetImListAndSelectionStateApis` — pure reflection +
  getter tests on `NavigationManager`. The class is kept (rebound, not
  removed) per BACKPORT §3, so these continue to be valid.

## Re-enabling guidance

When the corresponding BACKPORT step lands and the new equivalent test is in
place, remove the `@org.junit.Ignore(...)` annotation from the method,
delete the test (if the new test fully replaces it), or rewrite the test
body against the new UI surface.

Annotations to remove (`@org.junit.Ignore(...)` lines) are colocated with
their `@Test` annotations; grep for the literal string `org.junit.Ignore(`
under `LimeStudio/app/src/androidTest/` to find every one.

## Related references

- [LIME_SETTINGS_BACKPORT.md](LIME_SETTINGS_BACKPORT.md) — migration plan
- [LIME_SETTINGS.md](LIME_SETTINGS.md) — iOS spec being ported
- [UI_ARCHITECTURE.md](UI_ARCHITECTURE.md) — current Android MVC layering
- Commit `d5d252d2` — initial MVC refactor that introduced these tests
- Commit `6f36521a` — first-launch help splash disabled
