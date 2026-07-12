# Issue #94: Android backup file shown as 0 B — reporter-confirmed fixed in 6.1.16

## Current status

Resolved/closed. Reporter `ejmoog` confirmed in https://github.com/lime-ime/limeime/issues/94#issuecomment-4633066872 that APK `6.1.16` makes both backup and restore usable, then closed the issue on 2026-06-05. `limeimetw` posted closing acknowledgement https://github.com/lime-ime/limeime/issues/94#issuecomment-4633078498.

Verified scope: Android APK `LIMEHD2026-6.1.16.apk` on the reporter's Samsung A52 / Android 15 path; this confirms fresh non-zero backup creation and restore for the reported Android scenario. iOS backup/restore parity is outside this Android report.

## Confirmed facts

Reporter `ejmoog` originally said every Android backup produced an empty file and could not be restored.

Known environment from the reporter's first follow-up comment:

- Device: Samsung A52
- Android: 15
- LIME: 6.1.15

Original issue evidence:

- Symptom: `limeBackup.zip` exists and is displayed as `0 B` in screenshots
- Reporter says, in hedged wording, that the problem appears to have existed for a long time

Live GitHub state checked after reporter validation on 2026-06-05:

- Issue: https://github.com/lime-ime/limeime/issues/94
- State: closed/completed
- Labels: `bug`, `Usability`
- Assignee: `jrywu`
- Initial public acknowledgement by `limeimetw`: https://github.com/lime-ime/limeime/issues/94#issuecomment-4544340087
- Diagnostic clarification/request by `limeimetw`: https://github.com/lime-ime/limeime/issues/94#issuecomment-4545079892
- Scoped follow-up asking for stale-file confirmation and logcat: https://github.com/lime-ime/limeime/issues/94#issuecomment-4545450059
- Reporter confirmed old backup files were deleted and attached logcat: https://github.com/lime-ime/limeime/issues/94#issuecomment-4549686546
- Scoped retest request for Android test APK `LIMEHD2026-6.1.16.apk`: https://github.com/lime-ime/limeime/issues/94#issuecomment-4624477896
- Reporter confirmed `6.1.16` backup and restore are usable: https://github.com/lime-ime/limeime/issues/94#issuecomment-4633066872
- Closing acknowledgement by `limeimetw`: https://github.com/lime-ime/limeime/issues/94#issuecomment-4633078498

Reporter diagnostics answered the earlier questions:

1. Phone storage is not close to full; screenshot says there is enough storage.
2. Saving to another local destination (`document/tmp`) still produced a 0 B backup file.
3. Old `limeBackup*.zip` files were deleted before the latest attempt.
4. Logcat captured during backup contains an actionable LIME stack trace.

## Logcat result from 2026-05-27

The attached `lime_backup.txt` contains the relevant failure at `05-27 06:39:32.561`:

```text
E/DBServer: Error backing up database
E/DBServer: java.io.FileNotFoundException: /data/user/0/net.toload.main.hd2026/databases/lime.db-journal: open failed: ENOENT (No such file or directory)
E/DBServer:     at net.toload.main.hd.global.LIMEUtilities.addFileToZip(LIMEUtilities.java:185)
E/DBServer:     at net.toload.main.hd.global.LIMEUtilities.zip(LIMEUtilities.java:154)
E/DBServer:     at net.toload.main.hd.DBServer.backupDatabase(DBServer.java:438)
E/DBServer:     at net.toload.main.hd.ui.controller.SetupImController.performBackup(SetupImController.java:184)
E/DBServer:     at net.toload.main.hd.ui.view.DbManagerFragment.performBackup(DbManagerFragment.java:214)
```

This identified the pre-fix app-side failure path for the reporter's 0 B backup:

Before PR #101:
- `DBServer.backupDatabase(Uri)` always added `lime.db-journal` to the backup file list.
- On the reporter's device, `lime.db-journal` did not exist at backup time.
- `LIMEUtilities.addFileToZip(...)` threw `FileNotFoundException` instead of skipping the missing optional journal file.
- `DBServer.backupDatabase(Uri)` caught the exception and logged/showed an error notification, but did not rethrow to `SetupImController` / `DbManagerFragment`.
- Because no exception reached `DbManagerFragment.performBackup(...)`, the UI could still set the status to backup success even though ZIP creation failed, leaving the selected output file at 0 B.

The log also contains many unrelated Samsung/system/other-app messages. Do not treat those as LIME root cause; the actionable LIME stack is the `DBServer` / `lime.db-journal` failure above.

## Identified root cause

Root cause for this reproduced path was identified as:

1. The backup ZIP generation treated `lime.db-journal` as required even though SQLite journal files can be absent depending on database state.
2. The backup failure was swallowed in `DBServer.backupDatabase(Uri)`, allowing UI success status after a failed backup.

This does **not** mean the entire backup implementation is generally broken. Existing tests and #88 still show backup/restore can work in other paths. #94 is specifically a missing-journal/error-propagation bug in the Android backup path.

## Android implementation and resolution status

Implemented and merged to `master` via PR #101 (`43aa6c887d9eebf162891549d0ef04fca9b6fe50`) and delivered in Android test APK `LIMEHD2026-6.1.16.apk` in `LimeStudio/app/release/`.

- PR #101 supersedes/recreates the relevant PR #97 behavior instead of depending on a separate PR merge.
- `lime.db-journal` is included only when it exists, because it is a transient SQLite rollback journal.
- Backup failures propagate to callers instead of allowing UI success status after ZIP/copy failure.
- Regression tests were added to cover backup without `lime.db-journal` and output-write failure propagation.
- Android test APK `LIMEHD2026-6.1.16.apk` contains the fix: https://raw.githubusercontent.com/lime-ime/limeime/master/LimeStudio/app/release/LIMEHD2026-6.1.16.apk (verified APK blob SHA `eb99705bc3f6a2668889e89c05f7d9914c574639`, size 11983378 bytes)

## Fixed code paths

### Backup implementation

File: `LimeStudio/app/src/main/java/org/limeime/DBServer.java`

Code state before PR #101:

- `backupDatabase(Uri)` built a backup list containing:
  - `lime.db`
  - `lime.db-journal`
  - shared prefs backup
  - preference manifest
- It called `LIMEUtilities.zip(...)` at about line 438.
- It caught broad exceptions at about lines 450-452, logged `Error backing up database`, showed an error notification, and did not rethrow.

Implemented behavior:

- The backup path treats the transient `lime.db-journal` file as optional instead of letting its absence fail the whole archive.
- Required backup files such as `lime.db` and preference/manifest files still fail loudly if unavailable.
- Backup failures propagate to `DbManagerFragment.performBackup(...)`, so the UI can show `db_status_backup_fail` instead of `db_status_backup_ok` after a failed backup.
- Future hardening, if needed: log temp ZIP size and copied byte count, and fail visibly if copied bytes are zero.

### ZIP helper

File: `LimeStudio/app/src/main/java/org/limeime/global/LIMEUtilities.java`

Code state before PR #101:

- `addFileToZip(...)` has an old commented-out missing-file skip:

```java
//if( item==null || !item.exists()) return; //skip if the file is not exist
```

Implemented behavior:

- The ZIP flow does not blindly ignore every missing file; the optional handling is scoped to the transient SQLite rollback journal.

### UI flow

File: `LimeStudio/app/src/main/java/org/limeime/ui/view/DbManagerFragment.java`

Code state before PR #101:

- `performBackup(Uri)` called `setupImController.performBackup(uri)` and then set `db_status_backup_ok` if no exception was thrown.

Implemented behavior:

- With DBServer rethrowing failures, the existing UI catch path displays `db_status_backup_fail` instead of success.
- Regression coverage protects the failure-reporting path.

## Verification status

Developer-side checks for the fix:

1. Regression coverage verifies backup succeeds when `lime.db-journal` is absent and still creates a non-empty ZIP containing the required DB/preferences/manifest.
2. Regression coverage verifies genuine backup/ZIP/copy failures reach the UI/controller instead of reporting success.
3. Android compile checks passed during PR #101 review.
4. Reporter retested Android APK `LIMEHD2026-6.1.16.apk` and confirmed backup and restore are usable. The verified community scope is Android/Samsung A52/Android 15 backup creation plus restore using 6.1.16.

## Public follow-up status

A concise public reply was posted after the logcat attachment to confirm that the log identified the likely failure path and that no more logs are needed before a fix.

A scoped retest request was posted and then edited after the targeted #94 backup fix reached the current `LIMEHD2026-6.1.16.apk` test APK. Reporter `ejmoog` confirmed `6.1.16` makes backup and restore usable, `limeimetw` posted one closing acknowledgement, and the issue is closed/completed. No active retest watch remains unless the issue is reopened or new backup/restore evidence appears.
