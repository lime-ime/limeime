# Issue #115: Android initial keyboard layout is wrong after first IM changes / manual Array10 import

## Problem statement

Community reporter `gontera` reports three initial-keyboard/default-layout problems on Android LIME 6.1.18 and 6.1.19. The issue body was edited on 2026-06-14 to broaden the first-startup symptom from `行列` / `行列10` to the tested first mounted non-`注音` IMs (`倉頡`, `大易`, `行列`, and `行列10`), and to add a second-IM path where any active IM, including `注音`, can show the same wrong initial keyboard. Problems 1 and 2 are Android startup/keyboard-switching symptoms. Problem 3 is also reported on Android but touches `.lime` import metadata/default-keyboard assignment, so iOS impact is unconfirmed and should be audited only if shared `.lime` interpretation changes.

1. On a fresh LIME install, if the first mounted IM is one of the tested non-`注音` IMs (`倉頡`, `大易`, `行列`, or `行列10`), the first Chinese keyboard can be wrong. The reporter says it visually resembles the English keyboard but still shows the `EN` key; that likely means LIME is in Chinese IM mode with the wrong/generic soft-keyboard layout. The reporter originally described the `EN` → `中` toggle and target-app restart workarounds for this first-startup path; confirm during reproduction whether they apply equally to every expanded IM case.
2. After adding a second IM in LIME, any active IM, including `注音`, can show the same wrong initial keyboard described in problem 1. The recovery behavior for this second-IM path is not yet separately confirmed.
3. After manually loading the attached `行列10` `.lime` table, LIME sometimes defaults the table's keyboard layout to `行列+數字列鍵盤`; the reporter expects the default to be `電話數字鍵盤`, as with `老刀行列10字根`.

The report includes a screenshot of the wrong initial keyboard state and an attached table archive:

- `array10a-v2023-1.0-20260614.zip`
- Attachment downloaded from the GitHub issue during triage on 2026-06-14; ZIP SHA-256 `2a558b3b3687ed73adea1e11ab87df53e22ebe2f1e84f826a34bcd5e46f4943e`
- Contained file: `array10a-v2023-1.0-20260614.lime`, SHA-256 `2d42d63952d46153237c42aee8e5654c78893eb0112e0b8674d5bc6652fec2be`
- Relevant metadata: `@format@|lime-text-v2`, `@cname@|行列10`, `@limeendkey@|,.`, `@spacestyle@|2`, `@imkeys@|1234567890`, `@imkeynames@|1\|2\|3\|4\|5\|6\|7\|8\|9\|0`

## Reproduction details from the report

### Problem 1: first keyboard after first mounting a non-phonetic IM

1. Fresh-install LIME and make the first mounted IM one of `倉頡`, `大易`, `行列`, or `行列10` rather than `注音`.
2. Switch to another app and focus a normal text field.
3. On first display, the reporter sees a keyboard whose physical layout resembles an English keyboard while the mode key says `EN`; by LIME convention this suggests Chinese mode with the wrong/generic keyboard layout, not true English mode.
4. Expected: the active Chinese IM should show its configured layout immediately; if the user is on the real English keyboard, the mode key should be `中`.
5. Workarounds originally reported for this first-startup path:
   - Tap `EN`, then tap `中`.
   - Close and reopen the target app.

The edited report says `倉頡`, `大易`, `行列`, and `行列10` all reproduce when they are the first mounted IM instead of `注音`; verify during reproduction whether the same workaround behavior applies to every expanded IM case.

### Problem 2: wrong initial keyboard after adding a second IM

1. Add/mount a second IM in LIME; the edited report does not yet specify whether this depends on which IM is added second or which IM was mounted first.
2. After the second IM is added, any active IM, including `注音`, can show the same wrong initial keyboard described in problem 1.
3. Expected: adding another IM should not leave any active IM with a stale/missing keyboard mapping on the next first input session.
4. Recovery behavior for this second-IM path is not yet separately confirmed; verify whether the `EN` → `中` toggle or target-app restart works here too.

### Problem 3: manual `.lime` Array10 table default layout

1. Manually load the attached `行列10` `.lime` table.
2. LIME sometimes assigns `行列+數字列鍵盤` as the default keyboard layout.
3. Expected: default to `電話數字鍵盤`, matching `老刀行列10字根`.

## Evidence and code areas inspected

### Android startup after the first IM is installed/enabled

The important point is that startup is *not* supposed to default to English for a normal text field. On `master` at commit `387ea90e`:

1. `LIMEService.onCreate()` initializes default preferences, reads `activeIM = mLIMEPref.getActiveIM()`, then calls `buildActivatedIMList()` (`LIMEService.java` lines 400-442).
2. `LIMEPreferenceManager.getActiveIM()` defaults the persisted active IM (`keyboard_list`) to `phonetic` (`LIMEPreferenceManager.java` lines 487-495). The remembered language flag `language_mode` defaults to `no`/Chinese, and `persistent_language_mode` defaults to false (`LIMEPreferenceManager.java` lines 151-162, 404; `preference.xml` lines 132-134).
3. When the settings IM list is available, `buildActivatedIMList()` does **not** use the old `keyboard_state` preference as source of truth. It calls `SearchSrv.getImConfigList(null, LIME.IM_FULL_NAME)`, filters out `emoji`, filters disabled rows, maps DB rows to known `LIME.IM_CODES`, and builds `activatedIMList` / short names from the live DB (`LIMEService.java` lines 3833-3866). Only when `SearchSrv` is unavailable does it fall back to the persisted `keyboard_state` (`LIMEService.java` lines 3869-3905; default is all indices in `LIMEPreferenceManager.java` lines 478-485).
4. After the active list is built, `ensureActiveIMInActivatedList()` corrects `activeIM` only if it is not in the DB-derived enabled list and the enabled list is non-empty. The correction is persisted (`LIMEService.java` lines 3918-3949). This service-side guard also corrects stale `activeIM` when the DB-derived enabled list is available. Separately, commit `680d34e5` fixed the enable-UI path by making a newly enabled IM active when the persisted active IM is not enabled.
5. On the first `onStartInput()` for a normal text field, `initOnStartInput()` reloads `activeIM`, refreshes/applies the startup keyboard snapshot, loads preferences, and then:
   - uses an English/special keyboard only for restricted field classes/variations, or if `persistent_language_mode` is enabled and `language_mode=yes`;
   - otherwise sets `mEnglishOnly=false` and calls `initialIMKeyboard()` (`LIMEService.java` lines 895-999).
6. If the first focused field is an email/password/web-email text variation, that is an expected forced-English path: `isForcedEnglishTextVariation(...)` returns true for those variations, disables prediction, sets `mEnglishOnly=true`, and calls `setKeyboardMode(..., MODE_EMAIL, isIm=false, ...)` (`LIMEService.java` lines 149-154 and 974-980). That startup should show an English/email keyboard and does not by itself prove #115.
7. `initialIMKeyboard()` calls `mKeyboardSwitcher.setKeyboardMode(activeIM, ..., isIm=true, ...)`, so the first normal keyboard should be the active Chinese IM layout (`LIMEService.java` lines 5255-5340).

Therefore the screenshot/report wording should not be analyzed as "LIME defaulted to English" without confirmation. A real English keyboard would show the `中` mode key. Because the reported keyboard shows `EN`, the lead interpretation is that LIME is in Chinese mode but resolves the active Chinese IM to the wrong/generic keyboard layout; confirm that with logging/reproduction.

The concrete fallback path is in `LIMEKeyboardSwitcher.setKeyboardMode(...)`: for Chinese mode (`isIm=true`), it resolves the active IM through `imConfigMap`. If the mapping is missing, empty, or `custom`, it replaces the keyboard code with `lime` (`LIMEKeyboardSwitcher.java` lines 448-459), then loads that generic Chinese keyboard XML (`LIMEKeyboardSwitcher.java` lines 501-510). This is consistent with an English-looking keyboard with an `EN` key, but should be confirmed by logging/reproduction of the first-focus `imConfigMap` state.

Related prior fixes are important context but do not by themselves close this issue:

- Commit `537a66c4` (`#107 Optimize LimeIME startup without changing init path`) added the startup keyboard-config snapshot and version tracking.
- Commit `680d34e5` fixed only one first-enabled-IM failure: when persisted `activeIM` still pointed at a disabled/default IM, enabling the first IM now makes the enabled IM active.
- The reporter reports #115 on 6.1.18/6.1.19, including tested first mounted `倉頡` / `大易` / `行列` / `行列10` and after adding a second IM where even `注音` can show the wrong initial keyboard. The concrete fix path is the case where the enabled active IM is correct, but the first startup snapshot / `imConfigMap` lacks the just-installed or just-added IM's keyboard mapping or uses the wrong mapping. `LimeDB.setIMConfigKeyboard(...)` writes the IM keyboard row (`LimeDB.java` lines 4879-4895) and, in the #115 fix, now resets the startup config version so the running `LIMEService` refreshes `getAllImKeyboardConfigList()` before the next normal text startup. That directly targets the stale-snapshot path, including the email/password-first-startup caveat.

### Imported table default keyboard assignment

`LimeDB.importTxtTable(...)` stores metadata and then presets keyboard assignment by table name. In the inspected `LimeDB.java` branch around the import preset logic (lines 4186-4242), `arraynum` is the stored keyboard config for `array`, while `phonenum` is the stored keyboard config for `array10`. The reporter describes `phonenum` as the expected `電話數字鍵盤` behavior, but this still needs on-device/UI verification against current master.

- `array` -> `arraynum` (`LimeDB.java` lines 4227-4228)
- `array10` -> `phonenum` (`LimeDB.java` lines 4229-4230)
- otherwise, if no table-specific `Keyboard` exists, the surrounding import preset branch can fall back based on `number_row_in_english` (`LimeDB.java` lines 4235-4240):
  - `limenum` when enabled
  - `lime` when disabled

The attached table's `@cname@` is `行列10`, but the issue needs live reproduction or code tracing to confirm which internal table code LIME uses during manual import. If the table is not imported under the exact `array10` code path, the fallback can plausibly choose `limenum`, which would explain the intermittent `行列+數字列鍵盤` default.

## Likely root cause / hypotheses

### Problems 1 and 2 lead hypothesis: Chinese-mode fallback after the active IM's keyboard mapping is missing/stale on first focus

The first keyboard should be Chinese by default for normal text input. Lead hypothesis: after installing/enabling a first non-phonetic IM, or after adding a second IM, LIME enters Chinese mode, but the first `setKeyboardMode(..., isIm=true, ...)` cannot resolve the intended IM keyboard mapping and falls back to generic `lime`. That fallback would look QWERTY/English-like while still showing `EN`, because it would still be Chinese mode.

Specific suspect areas:

- `buildActivatedIMList()` builds the active IM list from live DB `im` rows when `SearchSrv` is available, not from `keyboard_state`. The old `keyboard_state` preference is only a fallback when `SearchSrv` is unavailable. If the first-installed/enabled IM path races with async DB writes, either the DB-derived enabled list or, more directly, the IM keyboard mapping snapshot can be incomplete.
- `680d34e5` repairs only the case where persisted `activeIM` points outside the enabled list. It does not guarantee that the first startup keyboard-config snapshot already contains the new IM's `title='keyboard'` mapping.
- `setIMConfigKeyboard(...)` and related DB writes can update the IM keyboard assignment without changing the preference-backed startup-config version. If `LIMEService` already applied a snapshot, `refreshStartupConfigSnapshotIfNeeded()` can decide it is clean and keep an old `imConfigMap` for the first focus after install/import/add-IM changes.
- The `LIMEKeyboardSwitcher` fallback to `lime` masks this as a usable but wrong keyboard instead of a hard failure.

This is now the lead hypothesis. The older wording "falls back to English" is misleading; the observed `EN` key indicates wrong Chinese keyboard layout, not real English mode.

### Problem 3 hypothesis: custom/manual Array10 imports can miss the `array10` preset-keyboard branch

The attached `.lime` file contains metadata for `行列10`, but the preset keyboard logic inspected in `LimeDB.importTxtTable(...)` is keyed on the internal destination table name (`array10`). If manual import stores the table under `custom` or another table code, the code path may skip the `array10 -> phonenum` assignment and fall back to `limenum` / `lime` based on `number_row_in_english`. This should be confirmed by tracing the import entry point and stored IM config for the reporter's exact manual-import path.

This should be treated as a hypothesis until reproduced on-device or with an import integration test.

## Proposed fix

1. Make IM keyboard assignment writes invalidate the startup keyboard-config snapshot:
   - `LimeDB.setIMConfigKeyboard(...)` now calls `mLIMEPref.resetStartupConfigVersion()` after writing the `im` row.
   - This covers built-in table loading, manual `.lime` import preset assignment, and SearchServer/UI callers because they funnel through the same DB method.
   - On the next `initOnStartInput()`, `refreshStartupConfigSnapshotIfNeeded()` sees version `0`, rebuilds the active IM list, re-reads `getKeyboardConfigList()` and `getAllImKeyboardConfigList()`, and applies the fresh IM -> keyboard mapping before `initialIMKeyboard()`.
2. Add regression coverage for both sides of the clue:
   - `LimeDBTest.testSetIMConfigKeyboardInvalidatesStartupKeyboardSnapshot()` locks the DB-write invalidation behavior.
   - `LIMEServiceTest.emailFirstStartupThenNormalTextRefreshesChangedImKeyboardSnapshot()` locks the sequence where the first focused field is forced English/email, then the next normal text field refreshes after a startup-version invalidation and routes to Chinese IM layout.
3. Keep the manual `.lime` Array10 default-layout path under watch. The current fix ensures the assigned keyboard mapping is not stale, but if the manual import UI stores the table under a target that skips the `array10 -> phonenum` preset branch, that is a separate import-target/default-selection fix.

## Existing coverage / fragility assessment

Relevant tests now cover the stale-startup-snapshot path:

- `LimeDBTest.testSetIMConfigKeyboardInvalidatesStartupKeyboardSnapshot()` verifies IM keyboard DB writes reset the startup config version.
- `LIMEServiceTest.emailFirstStartupThenNormalTextRefreshesChangedImKeyboardSnapshot()` verifies a forced-English/email first focus does not leave the next normal text focus on a stale keyboard snapshot after the IM keyboard mapping changes.
- Existing `LIMEServiceTest` coverage still checks restricted field keyboard policy such as `TYPE_CLASS_NUMBER -> MODE_PHONE` and startup snapshot caching when the version is unchanged.
- `LIMEKeyboardSwitcherPolicyTest` covers English layout resolution with and without number row.
- `KeyboardLayoutResourceTest` checks keyboard XML resource availability.

Remaining coverage gap: the automated tests lock the versioning/startup invariant but do not yet perform an end-to-end UI import of the attached `.lime` file or a real-device soft-keyboard screenshot comparison. The current code remains somewhat fragile because the live keyboard choice depends on consistency between SharedPreferences startup-version invalidation, SearchServer DB reads, cached startup snapshots, and `LIMEKeyboardSwitcher` fallback behavior.

## Follow-up questions for reporter

Only ask if needed after initial code/device reproduction attempts:

1. Does problem 1 happen with `記憶中英模式` (remember Chinese/English mode across fields/apps) enabled, disabled, or both?
2. For problem 1, does the wrong first keyboard appear immediately after importing/loading the table only, or also after later app launches without changing IM settings?
3. For problem 2, after adding the second IM, does the wrong first keyboard happen only immediately after the add/change flow, or continue on later app launches after toggling `EN` / `中` or restarting the target app?
4. For problem 2, does the behavior depend on which IM is added second, or on which IM was mounted first?
5. For problem 3, in the manage-IM list, what is the internal table/import target shown for the attached `.lime` file when the default keyboard becomes `行列+數字列鍵盤`?

## Verification plan

### Android

- Add/confirm automated regression coverage:
  - `LimeDBTest.testSetIMConfigKeyboardInvalidatesStartupKeyboardSnapshot()`
  - `LIMEServiceTest.emailFirstStartupThenNormalTextRefreshesChangedImKeyboardSnapshot()`
- Compile verification: `./gradlew :app:compileDebugJavaWithJavac :app:compileDebugAndroidTestJavaWithJavac` and `./gradlew :app:assembleDebug`.
- Post-APK verification still needed before closing: run focused instrumentation tests on an emulator/device and manually verify `倉頡`, `大易`, `行列`, `行列10`, and `注音` first-normal-text startup after adding/removing IMs.
- Android APK `LIMEHD2026-6.1.20.apk` was published after the PR #116 merge and is the targeted reporter retest build for #115; retest request posted at https://github.com/lime-ime/limeime/issues/115#issuecomment-4715747519.
- The live retest comment asks the reporter to check first-mounted `倉頡` / `大易` / `行列` / `行列10`, adding a second IM including `注音`, and the attached manual Array10 `.lime` import default-keyboard path. Problems 1-2 are the directly targeted stale-snapshot scope; Problem 3 remains a watch item because it may still require a separate import-target/default-keyboard fix if retest stays negative.

### iOS

- Confirmed reporter platform is Android; the report references Android APK versions and Android soft-keyboard behavior.
- The first-keyboard-display symptom is probably Android-only because it depends on Android `LIMEService`, `LIMEKeyboardSwitcher`, and `EditorInfo` startup paths.
- If the chosen fix changes shared `.lime` metadata interpretation or import-target semantics rather than only Android IM-keyboard snapshot invalidation, audit iOS text import/default-keyboard registration separately before claiming cross-platform parity.

## Current status

Open / pending reporter retest. Labeled `bug` + `Usability`, assigned to `jrywu`, and tracked in `docs/BACKLOG.md` under active issue follow-up. PR #116 merged to `master` as `0a03fcca34fd70c51db547ef054f163f35bd7151`, with fix commit `976465e8057d8ca9aa66ceb2159c8ae74945241c`; the later APK bump commit `08816a80ff5f` published `LIMEHD2026-6.1.20.apk` (versionName 6.1.20, blob SHA `cbe1ff21ab7a499eef952c702ee5eb0a40131c05`, size 14053640 bytes) as the targeted #115 retest build. `limeimetw` posted the scoped 6.1.20 retest request at https://github.com/lime-ime/limeime/issues/115#issuecomment-4715747519. Keep the issue open until the reporter or maintainer confirms the result; the requested retest covers first-mounted non-`注音` IMs (`倉頡`, `大易`, `行列`, `行列10`), adding a second IM including `注音`, and the attached manual Array10 `.lime` import default-keyboard path. Problems 1-2 are the directly addressed stale-snapshot scope; Problem 3 is still being checked because it may need a separate import-target/default-keyboard fix if the 6.1.20 retest remains negative. iOS remains out of reporter-tested scope unless shared `.lime` import semantics change separately.
