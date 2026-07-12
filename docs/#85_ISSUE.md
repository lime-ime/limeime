# Issue #85: DB restore can silently fail for cloud on-demand backup files

## Problem Statement

Maintainer-created Android and iOS bug tracking issue for database backup restore. When a user selects a full database backup ZIP from a cloud-backed document provider while the file is still an on-demand/offline placeholder, restore may fail without a clear visible error. The UI could show restore success even though the database and preferences were not restored.

## Classification

- Issue: #85
- Type: bug
- Platform: Android and iOS
- Area: database backup/restore, Android Storage Access Framework, iOS File Provider / security-scoped URL handling
- Reporter/source: maintainer account (`limeimetw`).
- Live labels/assignee at closure: `bug`, assigned to `jrywu`.
- Current live state: closed by maintainer `jrywu` on 2026-05-26 via GitHub-visible commit `520416371d32c2392190e42883d3bd68b933fe19` (`Fix #85 #88 #92 iOS restore feedback and emoji DB repair`).

## Reproduction Notes

1. Store a valid `limeBackup.zip` in a cloud-backed provider such as Google Drive or OneDrive.
2. Ensure the file is not cached locally on the test device.
3. In LIME Android or LIME iOS, open database backup/restore and choose restore.
4. Select the cloud-only backup file from the picker and confirm.
5. Observe that restore may return without actually restoring data, and the UI may still indicate success.

## Relevant Code Paths Inspected

### Android

- `LimeStudio/app/src/main/java/org/limeime/ui/view/DbManagerFragment.java`
  - `restoreLocalDrive()` checks for a restore-capable picker and shows a confirmation dialog; `launchRestoreFilePicker()` uses `Intent.ACTION_GET_CONTENT`, `CATEGORY_OPENABLE`, and MIME type `application/zip`.
  - `performRestore(Uri)` previously called `setupImController.performRestore(uri)` and then showed success if no exception reached the fragment.
- `LimeStudio/app/src/main/java/org/limeime/ui/controller/SetupImController.java`
  - `performRestore(Uri)` previously handled lower-level restore failures without reliably surfacing them to the fragment.
- `LimeStudio/app/src/main/java/org/limeime/DBServer.java`
  - `restoreDatabase(Uri)` copies the selected URI stream into a cache temp ZIP and calls the path-based restore.
  - Earlier code could convert restore failures into logs/notifications without an explicit failure result to the UI caller.

### iOS

- `LimeIME-iOS/LimeSettings/Views/DBManagerView.swift`
  - `.fileImporter` accepts a selected backup URL and calls `performRestore(from:)`.
  - `performRestore(from:)` shows restore status based on `SetupImController.restoreDB(from:)`.
- `LimeIME-iOS/LimeSettings/Controllers/SetupImController.swift`
  - `restoreDB(from:)` previously called `server.restoreDatabase(uri:)`, then re-registered IMs, signalled keyboard reload, dismissed progress, and returned `.success(())` even when lower-level restore failed.
- `LimeIME-iOS/Shared/Database/DBServer.swift`
  - `restoreDatabase(uri:)` uses security-scoped URL access and `NSFileCoordinator` around the selected URL, which is the right direction for File Provider/on-demand downloads.
  - Earlier coordinator/copy/archive/extract failures were printed/returned rather than propagated to callers.

## Root Cause

Both platform restore flows lacked a consistent explicit success/failure contract. Cloud-backed providers may delay, fail, or provide an unreadable/incomplete stream while a selected file is being fetched on demand. Even though iOS already attempted File Provider coordination, lower-level coordinator/copy/archive/extract failures were not propagated to the SwiftUI caller. The UI false-success problem was broader than cloud placeholders: unreadable, zero-byte, incomplete, invalid, or wrong backup archives could be reported as successful if the error was handled internally.

## Implementation Status

- Android: fixed on `master` through PR #87 (`fix(android): surface database restore failures`), merged as `d22afcfddc82c0fc1260a5a09789590778199ec1` on 2026-05-25. Follow-up commit `f80ce0cb8884` restored compatibility with legacy Android backup archives that contain leading-slash entries. Android release APK `LIMEHD2026-6.1.12.apk` was rebuilt after those fixes.
- iOS: fixed on `master` by commit `520416371d32c2392190e42883d3bd68b933fe19` on 2026-05-26. The commit changes `DBServer.restoreDatabase(uri:)` / `restoreDatabase(srcFilePath:)` to throw on invalid source, empty file, invalid archive, missing `lime.db`, coordinator/copy failures, or extraction failures. `SetupImController.restoreDB(from:)` now returns failure instead of unconditional success, and restore-related re-registration / keyboard reload happens only after successful restore. The direct callback restore path also reports errors to the view. Tests were updated to expect failures for invalid restore inputs.
- Related but separate changes in the same commit: #92 iOS progress-overlay/styling adjustments and #88 iOS emoji FTS repair coverage. Those should not be treated as additional #85 verification scope.

## Verification Plan / Evidence

- Android PR #87 reported these checks as passing: `./gradlew :app:compileDebugJavaWithJavac`, `./gradlew :app:compileDebugAndroidTestJavaWithJavac`, `git diff --check`, and a narrow Claude Code review.
- iOS commit `520416371d32c2392190e42883d3bd68b933fe19` includes updated tests in `DBServerTest.swift` and `SetupImControllerTest.swift` for invalid restore sources reporting errors instead of silent success.
- Remaining release QA, if needed, is platform delivery verification: Android fix delivery was recorded in the 6.1.12 APK rebuild; iOS source is on `master` and should be verified when the next TestFlight/App Store build is prepared.
- If this issue resurfaces, test restore from valid local ZIP, cloud/on-demand ZIP, zero-byte file, invalid ZIP, missing-`lime.db` archive, and stream-open/read failures, then confirm the UI reports failure and does not show restore success.

## Follow-up Condition

Closed as maintainer-fixed on 2026-05-26. Do not keep #85 as an active public watch and do not post community retest requests because it is a maintainer-created tracking issue. Reopen or create a new issue only if Android/iOS restore still silently succeeds after a failed restore, or if release QA finds a platform-specific regression not covered by the implemented failure-propagation fixes.
