# #96 — Table IM Lime end-key behavior

## Live issue state

- Issue: https://github.com/lime-ime/limeime/issues/96
- Status: closed as completed after original reporter confirmation
- Current labels: `bug`, `enhancement`, `question`, `Usability`
- Assignee: `jrywu`
- Reporter: `SmithCCho`
- Community context: #95 was consolidated into #96 for the `%endkey ,.` / punctuation-commit discussion.

## Problem statement

Implement generic, opt-in Lime end-key behavior for table IMs without changing candidate ordering or adding punctuation-specific handling.

- Android keeps conventional `.cin %endkey` / `.lime @endkey@` as compatibility metadata only.
- LimeIME runtime commit behavior uses Lime-specific metadata: `.cin %limeendkey ...` and `.lime @limeendkey@ |...`.
- Configured Lime end-key characters should finish the current composition and commit through the same candidate-confirm semantics as existing confirmation keys, even if the asynchronous candidate strip has not yet marked candidates as shown.
- Tables without Lime end-key metadata remain unchanged. Keys such as `,` and `.` may still be ordinary roots/composing codes.
- Search/candidate construction must not special-case Chinese punctuation for this feature.

## Android implementation status

Implemented and merged to `master` via PR #101 (`43aa6c887d9eebf162891549d0ef04fca9b6fe50`), with follow-up commit `c2ec5b77394f2decd2bd900c63d8d8fadc68af20` preserving Lime end-key metadata during export/re-import. The current Android test APK file is `LIMEHD2026-6.1.16.apk` in `LimeStudio/app/release/`, and the original reporter confirmed the scoped 行列10 comma/period + Space full-width punctuation behavior on that build in https://github.com/lime-ime/limeime/issues/96#issuecomment-4629301853.

Feature scope:

- Android imports and persists `.cin %endkey` / `.lime @endkey@` as conventional compatibility metadata.
- Android imports and persists `.cin %limeendkey` / `.lime @limeendkey@` as Lime runtime commit metadata.
- LIME Settings IM detail shows an editable `limeendkey` metadata row labeled `結束鍵`.
- Android runtime treats the active table's configured Lime end-key characters as opt-in commit triggers and resolves the current composing candidates when needed before committing.
- Tables without Lime end-key metadata remain unchanged; roots are not globally converted or reordered.

Official table-data scope:

- This branch implements engine/settings support only. Packaged Android table data is represented by database resources rather than editable `.cin`/`.lime` source tables in this branch, so official table metadata/mapping updates are deferred to separate table-data release coordination.
- iOS/table-format parity remains pending and should align with the Android implementation when addressed.

## Reporter and maintainer clarifications

- Original report asked whether Chinese keyboard punctuation order can prefer full-width `，` / `。`.
- `Limeroshenko` noted a workaround by manually adding table mappings such as `,= ，` and `.= 。`, then warned that some long-time users use `,` and `.` as custom roots or short-phrase codes.
- `Limeroshenko` later clarified that the current official 行列10 `.lime` table does not contain the punctuation mapping rows; adding `,|，` and `.|。` enables the existing comma-plus-Space path, and if `.lime @endkey@` support is implemented they suggested making official Array10 opt in with `@endkey@ |,.` plus those punctuation mappings.
- After testing Android APK `LIMEHD2026-6.1.16.apk`, `Limeroshenko` reported that with the official table and end-key disabled, `,` + Space now directly outputs full-width `，`; with Lime end-key `,.` enabled, pressing `,` directly outputs `，`; and after disabling end-key again, their personal table's `,` prefix symbol mappings still work normally.
- `SmithCCho` later clarified:
  - 行列30 and 大易 use `,` / `.` as roots, so they should not directly output punctuation.
  - 行列10, 嘸蝦米, and 倉頡 are closer to direct punctuation output expectations.
  - A practical distinction is whether `,` / `.` are defined as roots in the table.
- After the 6.1.16 retest request, original reporter `SmithCCho` replied in https://github.com/lime-ime/limeime/issues/96#issuecomment-4629301853 that on 行列10, Chinese-keyboard `,` / `.` plus Space directly outputs full-width punctuation, and thanked the team.
- Android now separates conventional `%endkey` / `@endkey@` metadata from the Lime-specific `%limeendkey` / `@limeendkey@` runtime feature mechanism to avoid conflict with historical CIN semantics.
- Maintainer clarification in https://github.com/lime-ime/limeime/issues/96#issuecomment-4630088298 states the semantic split explicitly: conventional `%endkey` should finish composition and show candidates but not send the highlighted candidate, while `%limeendkey` / `@limeendkey@` is the LIME soft-keyboard opt-in behavior that finishes composition and sends the currently highlighted candidate. Conventional `%endkey` is preserved for future physical-keyboard-style CIN behavior.

## Source evidence inspected

Android candidate construction in the inspected path creates a composing-code `Mapping` and adds it before database results:

- `LimeStudio/app/src/main/java/org/limeime/SearchServer.java`
  - `getMappingByCode(...)` creates `self`, sets `word = code`, `code = code`, and marks it with `setComposingCodeRecord()`.
  - The method then normally `result.add(self)` before `result.addAll(resultlist)`.
  - This means exact direct mappings from the table can be present but still appear after the composing-code record.

Relevant code area on current `master`:

- `SearchServer.java` lines 1018–1022: creates the composing-code `self` record.
- `SearchServer.java` lines 1058–1073: adds `self` before a runtime/English suggestion or as the first result.
- `SearchServer.java` lines 1076–1078: appends database mapping results after `self`.
- `SearchServer.java` lines 1124–1139: cache/DB lookup returns the actual table mappings for the typed code.

Commit behavior confirms why the highlighted/selected candidate matters:

- `LimeStudio/app/src/main/java/org/limeime/LIMEService.java`
  - `commitTyped(...)` commits `selectedCandidate.getWord()`.

Relevant code area on current `master`:

- `LIMEService.java` lines 1664–1678: `commitTyped(...)` requires a selected candidate and commits its `word`.

## Implemented solution

1. Added Android metadata parsing/runtime support for explicit Lime end-key behavior:
   - `.cin`: `%limeendkey ...`
   - `.lime`: `@limeendkey@` equivalent.
2. Preserved conventional `%endkey` / `@endkey@` as imported/exported compatibility metadata without making those conventional fields the LIME runtime commit trigger. This avoids changing the historical CIN-style meaning, where an end key ends composition / displays candidates but does not commit the highlighted candidate.
3. Kept candidate-list ordering unchanged. The composing-code fallback remains item 0.
4. Required table opt-in for any potential end-key character that might also be a root; tables should not put that character in Lime end-key metadata unless they intentionally want end-key behavior.

## Verification results

- Android 6.1.16 reporter-confirmed scope:
  - Original reporter `SmithCCho` confirmed the 行列10 Chinese-keyboard `,` / `.` keys plus Space now directly output full-width punctuation.
- Android 6.1.16 community retest feedback:
  - `Limeroshenko` reported that the official table with end-key disabled now outputs full-width `，` through the comma-plus-Space path.
  - With Lime end-key `,.` enabled, pressing `,` directly outputs `，`.
  - After disabling end-key again, their personal table's `,` prefix symbol mappings still work normally.
- Remaining unverified scope:
  - iOS parity and official table-data coordination are separate release/backlog items, not active #96 reporter-watch items.

## Platform impact analysis

### Android

Confirmed relevant platform for #96. Android runtime/settings/import support is implemented for Lime-specific end-key metadata without SearchServer candidate-order changes. Reporter confirmation is scoped to Android APK 6.1.16 行列10 Chinese-keyboard `,` / `.` plus Space outputting full-width punctuation; it does not separately verify every custom table or hardware-keyboard path.

### iOS / table format

The Lime-specific table-format feature request (`%limeendkey ...` and `.lime @limeendkey@`) can affect both Android and iOS if both platforms import and honor the same table metadata. iOS parity should be assessed separately and aligned with Android later.

## Follow-up status

- Public clarification posted: https://github.com/lime-ime/limeime/issues/96#issuecomment-4556916179
- Android 6.1.16 retest request posted/edited after the metadata export/re-import preservation fix: https://github.com/lime-ime/limeime/issues/96#issuecomment-4624478044
- Earlier reporter-confirmed APK: `LIMEHD2026-6.1.16.apk` (`https://raw.githubusercontent.com/lime-ime/limeime/master/LimeStudio/app/release/LIMEHD2026-6.1.16.apk`), blob SHA `eb99705bc3f6a2668889e89c05f7d9914c574639`, size 11983378 bytes.
- Follow-up APK `LIMEHD2026-6.1.18.apk` (`https://raw.githubusercontent.com/lime-ime/limeime/master/LimeStudio/app/release/LIMEHD2026-6.1.18.apk`), blob SHA `6838b408ba18b60f607a335eda1e0c820f007e68`, size 13931117 bytes, includes the follow-up comma/period no-endkey candidate/highlight alignment. Maintainer retest request: https://github.com/lime-ime/limeime/issues/103#issuecomment-4643262621; reporter confirmation: https://github.com/lime-ime/limeime/issues/103#issuecomment-4643356687.
- Community retest feedback: `Limeroshenko` reported in https://github.com/lime-ime/limeime/issues/96#issuecomment-4625176200 that Android 6.1.16 is close to the requested behavior for their tested paths: official table comma-plus-Space outputs full-width punctuation, opt-in Lime end-key `,.` allows direct comma output, and personal-table `,` prefix symbol behavior remains normal when end-key is disabled.
- Original reporter confirmation: `SmithCCho` reported in https://github.com/lime-ime/limeime/issues/96#issuecomment-4629301853 that the 行列10 Chinese-keyboard `,` / `.` keys plus Space now directly output full-width punctuation, and thanked the team for the continued optimization.
- Closing acknowledgement posted by `limeimetw`: https://github.com/lime-ime/limeime/issues/96#issuecomment-4629315546
- Post-closure maintainer clarification posted/edited by `limeimetw`: https://github.com/lime-ime/limeime/issues/96#issuecomment-4630088298 explains why the implementation uses `%limeendkey` instead of conventional `%endkey`.
- Closure: issue #96 was closed as completed on 2026-06-05 after the original reporter confirmed the Android 6.1.16 行列10 punctuation-plus-Space behavior. Later #103 discussion verified the 6.1.18 no-endkey punctuation follow-up. No active public retest watch remains unless #96/#103 is reopened or new evidence is added.
- Remaining scope: iOS/table-format parity and official table-data coordination, if any, should stay in the normal release/backlog flow and should not be treated as an open #96 reporter-watch item.
