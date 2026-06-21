# Issue #127: Quick Cangjie root installation fails on Android

## Problem statement

Community reporter `s9228034david-spec` reports that on a Samsung A55, installing the `快倉` input-method roots shows the Android error message:

> 匯入失敗，可能是檔案毀損或格式錯誤，請再試一次

The report is for the Android IM install/download path. No attached log or app version was provided yet, but the failure was reproducible from repository metadata before the v6.1.23 follow-up: the Android catalog pointed the `快倉` cloud download to `Database/scj.zip`, while `master` no longer had that file.

## Evidence and current code path

- Live issue: https://github.com/lime-ime/limeime/issues/127
- `LimeStudio/app/src/main/java/net/toload/main/hd/global/LIME.java` defines `DATABASE_CLOUD_IM_SCJ = DATABASE_CLOUD_URL_BASED + "scj.zip"`.
- `LimeStudio/app/src/main/java/net/toload/main/hd/ui/view/ImInstallFragment.java` uses `LIME.DATABASE_CLOUD_IM_SCJ` for the `快倉字根` install button.
- GitHub Contents API check:
  - Before the fix, `Database/scj.zip`: missing / 404.
  - Current `Database/scj.zip`: restored on `master` and on tag `v6.1.23`, blob SHA `dbe53d5e71c58660cd6e56758794ce95455a91f4`, size `1491808` bytes.
  - `Database/scj.limedb`: present, blob SHA `5b5d864b54ecefe493d156b9ba0570fa46ad0278`, size `1178764` bytes.
- Before the fix, HTTP check against `https://github.com/lime-ime/limeime/raw/master/Database/scj.zip` returned 404 content rather than the expected database artifact. This could pass the download-size guard and then fail during database import as an invalid/corrupt file.
- The Android app's cloud database base URL points at `https://github.com/lime-ime/limeime/raw/master/Database/`, so restoring `Database/scj.zip` on `master` is sufficient for the current released app path to retest the download/import flow.

## Likely root cause

Android's `快倉` download catalog was left on the legacy `scj.zip` filename after the committed artifact set no longer included that file. The app downloaded a GitHub 404 response instead of a valid LIME database, then the import layer reported the generic corrupt/format-error message.

This is separate from #111's `scj` table-data issue (`x` / `z` -> `1991` rows), although both involve the `快倉` artifact. #127 blocks installation before candidate data can be tested.

## Proposed solution / implemented source fix

The source-side fix restored the legacy `Database/scj.zip` artifact on `master` so the existing Android `快倉` cloud download constant can resolve to a valid database package again. This is a minimal compatibility fix for the current app code path, rather than a broader catalog migration to `scj.limedb`.

Release v6.1.23 was created after commit `2f0ecdf58a1f` restored `Database/scj.zip`, and the Android app downloads this cloud artifact from the repository at runtime. The v6.1.23 reporter retest request is therefore valid.

Follow-up opportunities remain:

1. Have the reporter retest the current Android install path with v6.1.23 or later now that `Database/scj.zip` is restored on `master`.
2. Consider migrating the Android `快倉` cloud download path to `scj.limedb` directly.
3. Improve the download path so GitHub 404/HTML responses are rejected by status or file signature before reaching the generic database-import failure.
4. Verify whether `DATABASE_CLOUD_IM_SCJ_KEYBOARD` should remain `limenum` or align with the newer imported-table/default-catalog mapping (`cjnum`) recorded for `scj` in #119 and iOS catalog metadata.

## Follow-up questions

No additional reporter data is required to confirm the broken Android catalog URL. If the first fix still fails on the reporter's device, ask for the LIME version, Android version, and whether the failure happens through the in-app `快倉字根` download button or from a manually selected file.

## Verification plan

- Android: install/update `快倉字根` from the in-app IM install screen on a clean profile and confirm the table imports successfully, appears in the IM list, and can enter basic `快倉` candidates.
- Android: verify the reporter's Samsung A55 path with v6.1.23 or later after confirming `Database/scj.zip` is available on `master`.
- Artifact check: verify `Database/scj.zip` exists on `master`, has a nonzero GitHub Contents size, and imports through the current `downloadAndImportZippedDb` / `importZippedDb` path. Keep `Database/scj.limedb` verification separate for any future catalog-repointing cleanup.
- Regression check: ensure missing GitHub artifact URLs fail before import with a clearer download/error state.

## Platform impact analysis

### Android

Confirmed affected path. Android `LIME.java` points `快倉` to `scj.zip`, `ImInstallFragment.java` uses that value for the `快倉字根` cloud install button, and the remote `Database/scj.zip` file was missing when the issue was triaged. Release v6.1.23 was created after `Database/scj.zip` was restored on `master`, and the Android app downloads that cloud artifact from the repository at runtime. The reporter retest request for the Samsung A55 install path is valid and pending.

### iOS

No confirmed iOS impact from this specific broken URL. `LimeIME-iOS/LimeSettings/IMCatalog.swift` already lists `快倉` with filename `scj.limedb`, and `Database/scj.limedb` exists on `master`. iOS should still be included in release QA if the shared artifact is changed, but this report currently identifies an Android-only catalog URL mismatch.

## Current status

- 2026-06-21: Classified as a confirmed Android catalog/download bug.
- 2026-06-21: Commit `2f0ecdf58a1f` restored `Database/scj.zip` on `master`; release v6.1.23 was created after that commit and the Android app downloads this cloud artifact from the repository at runtime.
- Retest/correction request: https://github.com/lime-ime/limeime/issues/127#issuecomment-4761898280 explains that the app downloads `Database/scj.zip` from `master`, confirms the artifact is restored, and asks the reporter to update to v6.1.23 and retry the in-app `快倉` root installation path.
- Keep the issue open pending reporter confirmation from the Samsung A55 install path.