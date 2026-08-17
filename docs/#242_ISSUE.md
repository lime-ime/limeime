# Issue #242: iOS cannot complete the 三碼 v.20260816.2 CIN import

## Status

- GitHub issue: https://github.com/lime-ime/limeime/issues/242
- Classification: confirmed iOS defect; runtime boundary not yet isolated
- State: open, source unfixed
- Reporter: `3code-type`
- Reporter-supplied environment: iPhone 17, `OS 27.0 bets5` (understood as iOS 27.0 beta 5), LIME `6.1.38-2026`
- Maintainer reproduction: independently reproduced on a physical iPhone 17 Pro Max using the official `3code.cin` through `三碼 → 匯入 .cin / .lime`; do not infer the reporter's OS or app build for this device

## Problem statement

Importing the official 三碼輸入法 v.20260816.2 `3code.cin` through the named 三碼 import path does not complete successfully on iPhone. The reporter relayed repeated failure on an iPhone 17, and the maintainer independently reproduced the user-visible import failure on an iPhone 17 Pro Max. This confirms the iOS import contract is broken even though the exact failing runtime boundary is not yet known.

The issue also requests a catalog update to v.20260816.2. That data-update request follows the existing downloadable-table workflow and must remain separate from the confirmed manual-import defect.

## Evidence

### Reporter evidence

- Issue body: https://github.com/lime-ime/limeime/issues/242
- Environment comment: https://github.com/lime-ime/limeime/issues/242#issuecomment-5311008263
- Reported environment: iPhone 17, iOS 27.0 beta 5, LIME 6.1.38-2026.
- The GitHub attachment is named `.txt` only because GitHub rejected `.cin`; the reporter says affected users downloaded the official `.cin` from the source site.
- Earlier screenshots do not establish the claimed iPhone execution path because one shows an Android-style chooser. The maintainer's independent physical-iPhone reproduction, not that screenshot, is the confirmation basis.

### Official fixture inspected

The official source at `https://3code-type.github.io/3code.cin` was fetched during triage on 2026-08-17:

- Version metadata: `v.20260816.2`
- Encoding: valid UTF-8
- Size: 185,748 bytes
- SHA-256: `9240641bc03d73f5a2ed1aa41fda65307495db181e2d3ce5aecf13e692aff478`
- Total lines: 23,411
- `%keyname` rows: 31
- `%chardef` rows: 23,359
- Every inspected `%chardef` row has at least a code and output field.

This static inspection rules out an empty file and an immediately malformed UTF-8/CIN envelope. It does not establish that the physical-device picker copied these exact bytes or identify the later failing stage.

## Architecture preflight

### Accepted sources

The following current accepted documents and production paths were reviewed before defect classification:

- `docs/LIME_SETTINGS.md` §3, §5.3, §11, and the import parity checklist
- `docs/CIN_LIME_SPEC.md`
- `docs/CIN_LIME_IMPROVE_PLAN.md`
- `docs/UI_ARCHITECTURE.md`
- `docs/LIMEIME_ARCHITECTURE.md`
- `docs/REFACTORING_ARCHITECTURE.md`
- `LimeIME-iOS/LimeSettings/Views/IMInstallView.swift`
- `LimeIME-iOS/LimeSettings/Controllers/SetupImController.swift`
- `LimeIME-iOS/LimeSettings/Controllers/IntentHandler.swift`
- `LimeIME-iOS/Shared/Database/DBServer.swift`
- `LimeIME-iOS/Shared/Database/LimeDB.swift`
- Related issue records `docs/#119_ISSUE.md`, `docs/#172_ISSUE.md`, and `docs/#176_ISSUE.md`

An independent architecture-aware review returned `NO-ARCHITECTURE-CONFLICT` for classifying this as a confirmed iOS defect with an unknown runtime boundary. No accepted invariant makes successful import impossible.

### Constraint ledger

| Required behavior | Governing invariant | Platform limit | Removable behavior | Consequence of an over-broad change |
|---|---|---|---|---|
| A valid UTF-8 `.cin` selected for the named 三碼 destination imports its mappings and reaches a terminal visible success count, or returns a terminal visible error. | The chosen named IM fixes the destination table; `.cin` uses text parsing; heavy I/O stays off `MainActor`; CIN metadata/chardef semantics and Android parity remain intact; successful replacement completes lifecycle publication. | iOS security-scoped file access and beta-OS behavior can affect the entry path, but neither prohibits import. | None identified before RED/runtime tracing. | Broad parser or lifecycle changes could regress legacy `.cin`/`.lime`, metadata/default-keyboard assignment, learned-record preservation, atomic replacement, or cold/hot publication. |

## Likely root cause

The root cause is not yet established. The current path has four discriminating regions:

1. security-scoped picker access and copy into a temporary `.cin` URL;
2. CIN stream reading and row parsing into a staging database;
3. atomic replacement and learned-record preservation;
4. pending-change publication and UI completion state.

Current evidence confirms only that the end-to-end iOS operation fails. It does not identify an error, imported count, timeout state, file-read result, parser result, transaction result, or publication result. The reporter's beta OS may contribute, but the maintainer reproduction must not inherit that OS/build detail without separate evidence.

## Proposed solution

Do not select an implementation until the first runtime boundary is observed. Add a focused RED/trace that proves whether the selected security-scoped URL is copied to a nonempty, UTF-8-readable temporary `.cin` and handed to `SetupImController.importTxtFile` with destination `tricode`.

- If copy/access fails, preserve iOS security-scoped handling and return a terminal user-visible error.
- If handoff succeeds, move the next RED boundary to staging parse completion and exact inserted count before changing parser behavior.
- Preserve named-destination routing, CIN semantics, learned records, atomic replacement, default `limenumsym2` assignment, and lifecycle publication.

## Platform impact

### iOS

Confirmed affected by the maintainer on a physical iPhone 17 Pro Max. The reporter separately supplied iPhone 17, iOS 27.0 beta 5, and LIME 6.1.38-2026. Exact failing stage and broader iPhone/iPad scope remain unknown.

### Android

The reporter states Android phones can install the same table, and no Android failure is confirmed. Android has a separate file-picker/runtime path but shares CIN semantics; use Android as a compatibility baseline and do not change its behavior without a failing parity test.

## Follow-up questions

No additional reporter evidence is required to classify the defect. Runtime investigation should capture locally:

- maintainer device OS and exact app build;
- elapsed time and terminal UI state;
- temp-copy file size/hash and UTF-8 decode result;
- selected destination table;
- last imported-row count or thrown error;
- staging replacement and publication completion.

## Verification plan

### Automated

1. Use the official v.20260816.2 fixture or a pinned, license-compatible test fixture reproducing its relevant shape.
2. RED/GREEN test the first proven failing boundary only.
3. Assert destination `tricode`, parsed mapping count, version/name metadata, and default keyboard `limenumsym2` after successful import.
4. Preserve tests for legacy `.cin` horizontal whitespace, `.lime` delimiters/escaping, `%keyname`, zero/explicit basescore semantics, and learned-record restoration.
5. Verify atomic failure: a failed import must not destroy the prior installed 三碼 table.
6. Run focused XCTest, the broader iOS database/import suite, and Xcode build/archive checks.

### Physical-device runtime

1. On iPhone, download the pinned official `3code.cin` and verify its size/hash before selection.
2. Import through `三碼 → 匯入 .cin / .lime`.
3. Verify a terminal success count or actionable error; no indefinite `匯入` state.
4. Verify installed/enabled state, `limenumsym2` layout, metadata version `v.20260816.2`, and representative code lookup/input.
5. Repeat on a supported stable iOS release and the reporter's beta line when available; verify iPad separately if the source boundary can be affected by platform/device class.
6. Ask the reporter to retest only after a public iOS build containing the accepted fix is available.
