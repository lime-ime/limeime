# Issue #127: Quick Cangjie root installation fails on Android

## Problem statement

Community reporter `s9228034david-spec` reports that on a Samsung A55, installing the `快倉` input-method roots shows the Android error message:

> 匯入失敗，可能是檔案毀損或格式錯誤，請再試一次

The report is for the Android IM install/download path. No attached log or app version was provided yet. At triage time the failure was reproducible from repository metadata because Android pointed the `快倉` cloud download to `Database/scj.zip` while that file was missing on `master`.

## Evidence and current code path

- Live issue: https://github.com/lime-ime/limeime/issues/127
- `LimeStudio/app/src/main/java/net/toload/main/hd/global/LIME.java` defines `DATABASE_CLOUD_IM_SCJ = DATABASE_CLOUD_URL_BASED + "scj.zip"`.
- `LimeStudio/app/src/main/java/net/toload/main/hd/ui/view/ImInstallFragment.java` uses `LIME.DATABASE_CLOUD_IM_SCJ` for the `快倉字根` install button.
- Initial GitHub Contents API check during triage:
  - `Database/scj.zip`: missing / 404.
  - `Database/scj.limedb`: present, blob SHA `5b5d864b54ecefe493d156b9ba0570fa46ad0278`, size `1178764` bytes.
- Initial HTTP check against `https://github.com/lime-ime/limeime/raw/master/Database/scj.zip` returned 404 content rather than the expected database artifact. This could pass the current download-size guard and then fail during database import as an invalid/corrupt file.

## Likely root cause

Android's `快倉` download catalog was left on the legacy `scj.zip` filename after the maintained artifact moved to `scj.limedb`. The app downloads a GitHub 404 response instead of a valid LIME database, then the import layer reports the generic corrupt/format-error message.

This is separate from #111's `scj` table-data issue (`x` / `z` -> `1991` rows), although both involve the `快倉` artifact. #127 blocks installation before candidate data can be tested.

## Proposed solution / implemented source fix

The first source-side fix restored the legacy `Database/scj.zip` artifact on `master` so the existing Android `快倉` cloud download constant can resolve to a valid database package again. This is a minimal compatibility fix for the current app code path, rather than a broader catalog migration to `scj.limedb`.

Important build boundary: Android APK v6.1.23 (`LIMEHD202661230-6.1.23.apk`, Contents blob SHA `2e7fee05de15139119db5a3ea1908bd7f2b611ec`, size `7406573` bytes) was built from commit `568d20ec` before `2f0ecdf` merged. Downloaded APK inspection found no bundled `scj.zip`, and the public follow-up comment was corrected to tell the reporter not to retest #127 with v6.1.23.

Follow-up opportunities remain:

1. Consider migrating the Android `快倉` cloud download path to `scj.limedb` directly.
2. Improve the download path so GitHub 404/HTML responses are rejected by status or file signature before reaching the generic database-import failure.
3. Verify whether `DATABASE_CLOUD_IM_SCJ_KEYBOARD` should remain `limenum` or align with the newer imported-table/default-catalog mapping (`cjnum`) recorded for `scj` in #119 and iOS catalog metadata.

## Follow-up questions

No additional reporter data is required to confirm the broken Android catalog URL. If a newer fixed APK still fails on the reporter's device, ask for the LIME version, Android version, and whether the failure happens through the in-app `快倉字根` download button or from a manually selected file.

## Verification plan

- Android: after a newer APK is built from commit `2f0ecdf58a1f8854636456c8fcaae355e40442df` or later, install/update `快倉字根` from the in-app IM install screen on a clean profile and confirm the table imports successfully, appears in the IM list, and can enter basic `快倉` candidates.
- Android: verify the reporter's Samsung A55 path only after that newer APK exists. Do not ask the reporter to retest v6.1.23 for #127.
- Artifact check: verify `Database/scj.zip` still exists on `master`, has a nonzero GitHub Contents size, and is imported through the Android `downloadAndImportZippedDb` / `importZippedDb` path.
- Regression check: ensure missing GitHub artifact URLs fail before import with a clearer download/error state.

## Platform impact analysis

### Android

Confirmed affected path. Android `LIME.java` points `快倉` to `scj.zip`, `ImInstallFragment.java` uses that value for the `快倉字根` cloud install button, and the remote `Database/scj.zip` file was missing when the issue was triaged. Source now restores `Database/scj.zip`, but v6.1.23 predates that source fix, so a newer Android APK is still needed before asking the reporter to retest.

### iOS

No confirmed iOS impact from this specific broken Android URL. `LimeIME-iOS/LimeSettings/IMCatalog.swift` lists `快倉` with filename `scj.limedb`, and `Database/scj.limedb` exists on `master`. iOS should still be included in release QA if the shared artifact is changed, but this report currently identifies an Android catalog URL/artifact mismatch.

## Current status

- 2026-06-21: Classified as a confirmed Android catalog/download bug.
- 2026-06-21: Commit `2f0ecdf58a1f` restored `Database/scj.zip` on `master`, but Android APK v6.1.23 was built before that restoration and is not valid for #127 verification.
- Corrected public follow-up: https://github.com/lime-ime/limeime/issues/127#issuecomment-4761898280 tells the reporter not to retest with v6.1.23 and to wait for the next Android test APK that contains the fix.
- Keep the issue open pending a newer verified APK/build and reporter confirmation from the Samsung A55 install path.
