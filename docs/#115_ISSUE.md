# Issue #115: Android initial keyboard layout is wrong after first IM changes / manual Array10 import

## Resolution / current status

Reporter `gontera` confirmed on 2026-06-17 that Android APK `LIMEHD2026-6.1.21.apk` works normally for the requested #115 retest scope: Problems 1 and 2 no longer reproduced in the reporter's current test. The issue is closed after that reporter confirmation, with closing acknowledgement https://github.com/lime-ime/limeime/issues/115#issuecomment-4732347101. Verified scope is Android APK 6.1.21 for the initial-keyboard / second-IM startup paths. Problem 3, the manual Array10 `.lime` default-keyboard path, had already looked normal in the reporter's 6.1.20 retest and was not reported as recurring in the 6.1.21 closure comment. iOS remains out of reporter-tested scope unless shared `.lime` import/default-keyboard semantics change separately.

## Problem statement

Community reporter `gontera` reported three initial-keyboard/default-layout problems on Android LIME 6.1.18 and 6.1.19. The issue body was edited on 2026-06-14 to broaden the first-startup symptom from `行列` / `行列10` to the tested first mounted non-`注音` IMs (`倉頡`, `大易`, `行列`, and `行列10`), and to add a second-IM path where any active IM, including `注音`, could show the same wrong initial keyboard. Problems 1 and 2 were Android startup/keyboard-switching symptoms. Problem 3 was also reported on Android but touches `.lime` import metadata/default-keyboard assignment, so iOS impact is unconfirmed and should be audited only if shared `.lime` interpretation changes.

1. On a fresh LIME install, if the first mounted IM was one of the tested non-`注音` IMs (`倉頡`, `大易`, `行列`, or `行列10`), the first Chinese keyboard could be wrong. The reporter said it visually resembled the English keyboard but still showed the `EN` key; that likely meant LIME was in Chinese IM mode with the wrong/generic soft-keyboard layout. The reporter originally described the `EN` → `中` toggle and target-app restart workarounds for this first-startup path; the later 6.1.20 retest narrowed recovery to `EN` → `中` without restarting the target app.
2. After adding a second IM in LIME, any active IM, including `注音`, could show the same wrong initial keyboard described in problem 1. The later 6.1.20 retest reported the same improved `EN` → `中` recovery, and 6.1.21 was then reporter-confirmed normal.
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

The edited report said `倉頡`, `大易`, `行列`, and `行列10` all reproduced when they were the first mounted IM instead of `注音`. The later 6.1.20 retest showed recovery improved to `EN` → `中` without closing and reopening the target app, and the 6.1.21 retest was normal.

### Problem 2: wrong initial keyboard after adding a second IM

1. Add/mount a second IM in LIME; the edited report did not specify whether this depended on which IM was added second or which IM was mounted first.
2. After the second IM was added, any active IM, including `注音`, could show the same wrong initial keyboard described in problem 1.
3. Expected: adding another IM should not leave any active IM with a stale/missing keyboard mapping on the next first input session.
4. Recovery behavior for this second-IM path was later characterized by the 6.1.20 retest as `EN` → `中` restoring the expected keyboard without restarting the target app; the subsequent 6.1.21 retest was normal.

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

Therefore the screenshot/report wording should not be analyzed as "LIME defaulted to English" without confirmation. A real English keyboard would show the `中` mode key. Because the reported keyboard shows `EN`, the lead interpretation was that LIME was in Chinese mode but resolved the active Chinese IM to the wrong/generic keyboard layout.

The concrete fallback path is in `LIMEKeyboardSwitcher.setKeyboardMode(...)`: for Chinese mode (`isIm=true`), it resolves the active IM through `imConfigMap`. If the mapping is missing, empty, or `custom`, it replaces the keyboard code with `lime` (`LIMEKeyboardSwitcher.java` lines 448-459), then loads that generic Chinese keyboard XML (`LIMEKeyboardSwitcher.java` lines 501-510). This is consistent with the reported English-looking keyboard with an `EN` key, and PR #118 targeted that stale/missing mapping before Chinese keyboard draw.

Related prior fixes are important context but do not by themselves close this issue:

- Commit `537a66c4` (`#107 Optimize LimeIME startup without changing init path`) added the startup keyboard-config snapshot and version tracking.
- Commit `680d34e5` fixed only one first-enabled-IM failure: when persisted `activeIM` still pointed at a disabled/default IM, enabling the first IM now makes the enabled IM active.
- The reporter reported #115 on 6.1.18/6.1.19, including tested first mounted `倉頡` / `大易` / `行列` / `行列10` and after adding a second IM where even `注音` could show the wrong initial keyboard. The concrete fix path was the case where the enabled active IM was correct, but the first startup snapshot / `imConfigMap` lacked the just-installed or just-added IM's keyboard mapping or used the wrong mapping. `LimeDB.setIMConfigKeyboard(...)` writes the IM keyboard row (`LimeDB.java` lines 4879-4895) and, in the #115 fix, now resets the startup config version so the running `LIMEService` refreshes `getAllImKeyboardConfigList()` before the next normal text startup. That directly targeted the stale-snapshot path, including the email/password-first-startup caveat.

### Imported table default keyboard assignment

`LimeDB.importTxtTable(...)` stores metadata and then presets keyboard assignment by table name. In the inspected `LimeDB.java` branch around the import preset logic (lines 4186-4242), `arraynum` is the stored keyboard config for `array`, while `phonenum` is the stored keyboard config for `array10`. The reporter describes `phonenum` as the expected `電話數字鍵盤` behavior. Problem 3 looked normal in the later 6.1.20 retest, so the import-target/default-keyboard path remains only conditional context if the symptom recurs.

- `array` -> `arraynum` (`LimeDB.java` lines 4227-4228)
- `array10` -> `phonenum` (`LimeDB.java` lines 4229-4230)
- otherwise, if no table-specific `Keyboard` exists, the surrounding import preset branch can fall back based on `number_row_in_english` (`LimeDB.java` lines 4235-4240):
  - `limenum` when enabled
  - `lime` when disabled

The attached table's `@cname@` is `行列10`, but if Problem 3 recurs, the next step would be to trace which internal table code LIME uses during the reporter's exact manual-import path. If the table is not imported under the exact `array10` code path, the fallback can plausibly choose `limenum`, which would explain the intermittent `行列+數字列鍵盤` default.

## Likely root cause / hypotheses

### Problems 1 and 2 lead hypothesis: Chinese-mode fallback after the active IM's keyboard mapping is missing/stale on first focus

The first keyboard should be Chinese by default for normal text input. Lead hypothesis: after installing/enabling a first non-phonetic IM, or after adding a second IM, LIME enters Chinese mode, but the first `setKeyboardMode(..., isIm=true, ...)` cannot resolve the intended IM keyboard mapping and falls back to generic `lime`. That fallback would look QWERTY/English-like while still showing `EN`, because it would still be Chinese mode.

Specific suspect areas:

- `buildActivatedIMList()` builds the active IM list from live DB `im` rows when `SearchSrv` is available, not from `keyboard_state`. The old `keyboard_state` preference is only a fallback when `SearchSrv` is unavailable. If the first-installed/enabled IM path races with async DB writes, either the DB-derived enabled list or, more directly, the IM keyboard mapping snapshot can be incomplete.
- `680d34e5` repairs only the case where persisted `activeIM` points outside the enabled list. It does not guarantee that the first startup keyboard-config snapshot already contains the new IM's `title='keyboard'` mapping.
- `setIMConfigKeyboard(...)` and related DB writes can update the IM keyboard assignment without changing the preference-backed startup-config version. If `LIMEService` already applied a snapshot, `refreshStartupConfigSnapshotIfNeeded()` can decide it is clean and keep an old `imConfigMap` for the first focus after install/import/add-IM changes.
- The `LIMEKeyboardSwitcher` fallback to `lime` masks this as a usable but wrong keyboard instead of a hard failure.

This was the lead hypothesis that PR #118 targeted. The older wording "falls back to English" is misleading; the observed `EN` key indicates wrong Chinese keyboard layout, not real English mode.

### Problem 3 hypothesis: custom/manual Array10 imports can miss the `array10` preset-keyboard branch

The attached `.lime` file contains metadata for `行列10`, but the preset keyboard logic inspected in `LimeDB.importTxtTable(...)` is keyed on the internal destination table name (`array10`). If manual import stores the table under `custom` or another table code, the code path may skip the `array10 -> phonenum` assignment and fall back to `limenum` / `lime` based on `number_row_in_english`. This remains conditional follow-up only if the manual Array10 default-keyboard symptom recurs.

This remained a hypothesis; Problem 3 is not actively verified because the 6.1.20 retest looked normal and the 6.1.21 closure did not report recurrence.

## Delivered fix for Problems 1-2 and closure scope

1. Make IM keyboard assignment writes invalidate the startup keyboard-config snapshot:
   - `LimeDB.setIMConfigKeyboard(...)` now calls `mLIMEPref.resetStartupConfigVersion()` after writing the `im` row.
   - This covers built-in table loading, manual `.lime` import preset assignment, and SearchServer/UI callers because they funnel through the same DB method.
   - On the next `initOnStartInput()`, `refreshStartupConfigSnapshotIfNeeded()` sees version `0`, rebuilds the active IM list, re-reads `getKeyboardConfigList()` and `getAllImKeyboardConfigList()`, and applies the fresh IM -> keyboard mapping before `initialIMKeyboard()`.
2. Add regression coverage for both sides of the clue:
   - `LimeDBTest.testSetIMConfigKeyboardInvalidatesStartupKeyboardSnapshot()` locks the DB-write invalidation behavior.
   - `LIMEServiceTest.emailFirstStartupThenNormalTextRefreshesChangedImKeyboardSnapshot()` locks the sequence where the first focused field is forced English/email, then the next normal text field refreshes after a startup-version invalidation and routes to Chinese IM layout.
Closure scope note: do not keep an active watch for the manual `.lime` Array10 default-layout path after closure. The 6.1.20 retest looked normal and the 6.1.21 closure did not report recurrence. If the reporter reopens or provides new evidence, treat that as a separate import-target/default-selection follow-up because manual import UI storage could still skip the `array10 -> phonenum` preset branch.

## Existing coverage / fragility assessment

Relevant tests now cover the stale-startup-snapshot path:

- `LimeDBTest.testSetIMConfigKeyboardInvalidatesStartupKeyboardSnapshot()` verifies IM keyboard DB writes reset the startup config version.
- `LIMEServiceTest.emailFirstStartupThenNormalTextRefreshesChangedImKeyboardSnapshot()` verifies a forced-English/email first focus does not leave the next normal text focus on a stale keyboard snapshot after the IM keyboard mapping changes.
- Existing `LIMEServiceTest` coverage still checks restricted field keyboard policy such as `TYPE_CLASS_NUMBER -> MODE_PHONE` and startup snapshot caching when the version is unchanged.
- `LIMEKeyboardSwitcherPolicyTest` covers English layout resolution with and without number row.
- `KeyboardLayoutResourceTest` checks keyboard XML resource availability.

Remaining coverage gap: the automated tests lock the versioning/startup invariant but do not yet perform an end-to-end UI import of the attached `.lime` file or a real-device soft-keyboard screenshot comparison. The current code remains somewhat fragile because the live keyboard choice depends on consistency between SharedPreferences startup-version invalidation, SearchServer DB reads, cached startup snapshots, and `LIMEKeyboardSwitcher` fallback behavior.

## Reporter retest on Android APK 6.1.20

Reporter `gontera` retested `LIMEHD2026-6.1.20.apk` in https://github.com/lime-ime/limeime/issues/115#issuecomment-4716038267.

Results from that comment:

1. Problems 1 and 2 still reproduced on 6.1.20, but the recovery path improved. The reporter only needed to tap `EN` and then `中` to restore the expected keyboard; closing and reopening the target app was no longer required.
2. Problem 3, the manual Array10 `.lime` default-keyboard path, looked normal in the 6.1.20 retest.

Interpretation:

- The 6.1.20 stale-snapshot invalidation fix did not fully resolve the first-mounted non-`注音` and second-IM initial keyboard paths. At that point, Problems 1 and 2 still required a follow-up source fix; PR #118 later delivered that follow-up. GitHub auto-closed #115 from the PR body, but the issue was reopened for the 6.1.21 reporter retest before the final close after reporter confirmation.
- Problem 3 is not an active watch after closure because the reporter's latest relevant test says the default keyboard looked normal; reopen only if the reporter reports recurrence or provides new evidence.
- The improved recovery path is useful evidence: the wrong state remains within the Chinese/IM keyboard switching path and can be corrected by an in-session `EN` -> `中` toggle without restarting the target app.

## Follow-up source fix after the 6.1.20 negative/partial retest

PR #118 (`fix(android): refresh IM keyboard config before draw`) was merged to `master` on 2026-06-16 after the reporter's 6.1.20 retest. GitHub auto-closed #115 from the PR body. The PR head/merge commit is `676f9b4d50c398126ff7489d48e7db83727a58c2`; the direct startup-keyboard follow-up fix commit is `e984c4c1432ea1efd1996b69285cafe425e6b22c`, and the same PR also includes adjacent expanded-candidate-popup alignment work in `676f9b4d50c398126ff7489d48e7db83727a58c2`.

The source fix targets the remaining Problems 1-2 evidence from the 6.1.20 retest:

- refresh and apply the startup IM keyboard config immediately before Chinese keyboard draw paths, not only during earlier service startup;
- avoid marking the startup config snapshot clean when critical IM keyboard config data is unavailable or empty;
- reset startup config version after generic `im.disable` updates so enable/disable changes force a fresh snapshot;
- add regression coverage for refresh-before-draw ordering and IM-disable invalidation.

Android APK `LIMEHD2026-6.1.21.apk` / versionName `6.1.21` was published by commit `4f4e97069b00005319352f6f5829f9f1602982e5` after PR #118. The verified APK Contents blob SHA is `a8838c47b4186956536cd4c8aa4e3931d579d1da`, size 14055188 bytes. Because this APK contains the PR #118 follow-up fix, #115 was reopened for reporter confirmation and `limeimetw` posted a scoped 6.1.21 retest request at https://github.com/lime-ime/limeime/issues/115#issuecomment-4726813753. Reporter `gontera` confirmed in https://github.com/lime-ime/limeime/issues/115#issuecomment-4732316225 that current tests are normal, and the issue was closed afterward. Problem 3 is not actively watched after closure unless it recurs.

## Reporter verification result

- `LIMEHD2026-6.1.20.apk` was the first targeted reporter retest build. Reporter result: Problems 1 and 2 still reproduced but improved because `EN` -> `中` restored the correct keyboard without restarting the target app. The manual Array10 `.lime` default-keyboard path looked normal.
- `LIMEHD2026-6.1.21.apk` contains the PR #118 follow-up fix for Problems 1-2. Reporter `gontera` confirmed in https://github.com/lime-ime/limeime/issues/115#issuecomment-4732316225 that current tests are normal, and the issue is closed. Closing acknowledgement: https://github.com/lime-ime/limeime/issues/115#issuecomment-4732347101.
- Verified reporter scope: Android APK 6.1.21 for the first-mounted non-`注音` IM startup path and the add-second-IM startup path. The closure comment does not separately prove iOS parity or new manual-import edge cases beyond the previously normal 6.1.20 Problem 3 retest.

## Verification result

### Android

- Automated coverage added/confirmed during the fix series:
  - `LimeDBTest.testSetIMConfigKeyboardInvalidatesStartupKeyboardSnapshot()`
  - `LIMEServiceTest.emailFirstStartupThenNormalTextRefreshesChangedImKeyboardSnapshot()`
- Compile/build verification was performed during the PR #116 / PR #118 workflows before the APK retest builds.
- Android APK `LIMEHD2026-6.1.20.apk` was published after the PR #116 merge; retest request posted at https://github.com/lime-ime/limeime/issues/115#issuecomment-4715747519. Reporter retest was negative/partial for Problems 1-2 and normal for Problem 3.
- Android APK `LIMEHD2026-6.1.21.apk` contains the PR #118 follow-up fix for Problems 1-2; verified blob SHA `a8838c47b4186956536cd4c8aa4e3931d579d1da`, size 14055188 bytes. Scoped retest request: https://github.com/lime-ime/limeime/issues/115#issuecomment-4726813753. Reporter confirmation: https://github.com/lime-ime/limeime/issues/115#issuecomment-4732316225.

### iOS

- Confirmed reporter platform is Android; the report references Android APK versions and Android soft-keyboard behavior.
- The verified fix scope is Android-only. The first-keyboard-display root cause depends on Android `LIMEService`, `LIMEKeyboardSwitcher`, and `EditorInfo` startup paths, so it is not directly portable to iOS.
- Local iOS audit found the same class of startup-layout risk after async DB setup: the fallback initial IM path restored `activeIM` but did not refresh the keyboard layout. The iOS follow-up now applies the resolved active IM layout after DB setup for both saved-IM and fallback-IM paths, using the existing `resolvedLayoutId(for:)` logic.
- If shared `.lime` metadata/default-keyboard semantics change separately, audit iOS text import/default-keyboard registration at that time.

## Final status

Closed after reporter confirmation on Android APK `LIMEHD2026-6.1.21.apk`. PR #116 merged to `master` as `0a03fcca34fd70c51db547ef054f163f35bd7151`, with fix commit `976465e8057d8ca9aa66ceb2159c8ae74945241c`; APK `LIMEHD2026-6.1.20.apk` (blob SHA `cbe1ff21ab7a499eef952c702ee5eb0a40131c05`, size 14053640 bytes) partially improved but did not fully fix Problems 1-2. PR #118 then merged to `master` as `676f9b4d50c398126ff7489d48e7db83727a58c2`, with direct #115 follow-up fix commit `e984c4c1432ea1efd1996b69285cafe425e6b22c`; APK `LIMEHD2026-6.1.21.apk` (blob SHA `a8838c47b4186956536cd4c8aa4e3931d579d1da`, size 14055188 bytes) was reporter-confirmed normal in https://github.com/lime-ime/limeime/issues/115#issuecomment-4732316225. Closing acknowledgement: https://github.com/lime-ime/limeime/issues/115#issuecomment-4732347101. Remove #115 from active backlog/watch unless the reporter reopens or provides new evidence.
