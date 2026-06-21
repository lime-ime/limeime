# Issue #127: Quick Cangjie root installation fails on Android

## Problem statement

Community reporter `s9228034david-spec` reports that on a Samsung A55, installing the `快倉` input-method roots showed the Android error message:

> 匯入失敗，可能是檔案毀損或格式錯誤，請再試一次

The report is for the Android IM install/download path. The initial failure was reproducible from repository and GitHub Contents metadata: Android points the `快倉` cloud download to `Database/scj.zip`, and that file was missing from `master` when the issue was triaged.

## Evidence and current code path

- Live issue: https://github.com/lime-ime/limeime/issues/127
- Current reporter retest request: https://github.com/lime-ime/limeime/issues/127#issuecomment-4761898280
- `LimeStudio/app/src/main/java/net/toload/main/hd/global/LIME.java` defines `DATABASE_CLOUD_IM_SCJ = DATABASE_CLOUD_URL_BASED + "scj.zip"`.
- `LimeStudio/app/src/main/java/net/toload/main/hd/ui/view/ImInstallFragment.java` uses `LIME.DATABASE_CLOUD_IM_SCJ` for the `快倉字根` install button.
- Initial GitHub Contents API check during triage:
  - `Database/scj.zip`: missing / 404.
  - `Database/scj.limedb`: present, blob SHA `5b5d864b54ecefe493d156b9ba0570fa46ad0278`, size `1178764` bytes.
- A concurrent follow-up commit restored `Database/scj.zip` on `master`: `2f0ecdf58a1f8854636456c8fcaae355e40442df` (`Restore legacy SCJ zip artifact`). Current GitHub Contents metadata for `Database/scj.zip`: blob SHA `dbe53d5e71c58660cd6e56758794ce95455a91f4`, size `1491808` bytes.
- Download verification after the restore: `https://raw.githubusercontent.com/lime-ime/limeime/master/Database/scj.zip` is a valid ZIP archive, SHA-256 `ab2bc2777cd79a08a28f3c4b4cb2d4e730b20950455aaabb764786b8b158f24d`, containing `scj.db`.
- Release `v6.1.23` is published with APK `LIMEHD202661230-6.1.23.apk` at https://github.com/lime-ime/limeime/releases/download/v6.1.23/LIMEHD202661230-6.1.23.apk. The APK Contents blob SHA is `2e7fee05de15139119db5a3ea1908bd7f2b611ec`, size `7406573` bytes, downloaded SHA-256 `e64db9d33118dfc4bf127f951f5a0f873d939918496a54cc89254c71fe31eb95`.

## Root cause

Android's `快倉` install button downloads `Database/scj.zip` directly from the repository. That legacy ZIP artifact was absent from `master`, so Android downloaded a GitHub 404 response instead of a valid database artifact and then surfaced the generic corrupt/format-error import message.

This is separate from #111's `scj` table-data issue (`x` / `z` -> `1991` rows), although both involve the `快倉` artifact.

## Fix / remaining investigation plan

1. The immediate repository artifact problem is source-side fixed by commit `2f0ecdf58a1f8854636456c8fcaae355e40442df`, which restored `Database/scj.zip` at the URL Android already uses.
2. The retained live public comment asks the reporter to update to v6.1.23 and retry the same `快倉字根` install path. This is valid because the install path downloads the repository artifact at runtime; the restored `master` artifact is the critical fix, while the release APK gives the reporter a clean current app build for confirmation.
3. Add or update a focused Android regression check so every `ImInstallFragment` cloud variant points to a repository artifact that exists and can be imported by the matching `.zip` / `.limedb` path.
4. Consider improving the download path so GitHub 404/HTML responses are rejected by status or file signature before reaching the generic database-import failure.
5. Separately verify whether `DATABASE_CLOUD_IM_SCJ_KEYBOARD` should remain `limenum` or align with the newer imported-table/default-catalog mapping (`cjnum`) recorded for `scj` in #119 and iOS catalog metadata.

## Follow-up questions

If the reporter still sees the failure after retrying, ask for the LIME version, Android version, and whether the failure happens through the in-app `快倉字根` download button or from a manually selected file.

## Verification plan

- Android: ask the reporter to update to v6.1.23 and retry the Samsung A55 `快倉字根` install path after the `scj.zip` artifact restore.
- Android: install/update `快倉字根` from the in-app IM install screen on a clean profile and confirm the table imports successfully, appears in the IM list, and can enter basic `快倉` candidates.
- Artifact check: verify `Database/scj.zip` exists on `master`, is a valid ZIP archive, and contains `scj.db`.
- Regression check: ensure missing GitHub artifact URLs fail before import with a clearer download/error state.

## Platform impact analysis

### Android

Confirmed affected path before the restore. Android `LIME.java` points `快倉` to `scj.zip`, `ImInstallFragment.java` uses that value for the `快倉字根` cloud install button, and the remote `Database/scj.zip` file was missing. The artifact is now restored on `master`, so reporter confirmation should verify whether the runtime download path is fixed on the current release.

### iOS

No confirmed iOS impact from this specific broken URL. `LimeIME-iOS/LimeSettings/IMCatalog.swift` already lists `快倉` with filename `scj.limedb`, and `Database/scj.limedb` exists on `master`. iOS should still be included in release QA if the shared artifact is changed, but this report currently identifies an Android-only catalog URL/artifact mismatch.

## Current status

- 2026-06-21: Classified as a confirmed Android catalog/download bug. `bug` + `Usability` labels and `jrywu` assignment were applied, and acknowledgement was posted at https://github.com/lime-ime/limeime/issues/127#issuecomment-4761878700.
- 2026-06-21: Commit `2f0ecdf58a1f8854636456c8fcaae355e40442df` restored `Database/scj.zip`; Hermes verified the restored artifact is a valid ZIP containing `scj.db`. Release `v6.1.23` was published with APK `LIMEHD202661230-6.1.23.apk`, and the retained public retest request is https://github.com/lime-ime/limeime/issues/127#issuecomment-4761898280. Issue remains open pending reporter confirmation.
