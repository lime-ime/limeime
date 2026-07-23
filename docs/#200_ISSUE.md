# Issue #200 Analysis: LINE ID Search Duplicates Typed Characters on Android

## Problem Statement

On Android, typing one Latin character into LINE's Add Friends ID-search field can insert the character twice. The reported example is one `j` key tap producing `jj`.

Known environment:

- Android 14
- Device model `ASUS_AI2202`
- Independent maintainer reproduction: physical Samsung phone (exact model and Android version not recorded)
- Host app: LINE
- Field: Add Friends → ID search
- LIME version: not yet known
- LINE version: not yet known

The exact Android `EditorInfo.inputType`, IME options, keyboard mode, and whether all characters are affected are not yet known.

## Reproduction Status

The defect is confirmed. A maintainer independently reproduced the same #200 behavior on a physical Samsung phone. This establishes that the duplicate-input symptom is not limited to the reporter's ASUS device.

The exact runtime boundary remains unknown. The Samsung reproduction confirms the user-visible defect, but no privacy-safe dispatch/commit trace has yet shown whether one tap is duplicated before `handleCharacter()`, inside LIME's composition/commit path, or by LINE's editor handling.

Before changing code, collect privacy-safe diagnostics on the reproduced device path that identify:

1. The field's `EditorInfo.inputType`, variation flags, and `imeOptions`.
2. Whether one tap invokes `LIMEService.onKey()` once or twice.
3. Whether LIME calls `InputConnection.commitText()` once or twice.
4. Whether LINE renders one `commitText()` call twice, or whether duplication happens earlier in LIME.
5. Whether the behavior differs between LIME's English keyboard and direct Latin input from a Chinese keyboard.

Do not log the user's actual LINE ID or surrounding field contents.

## Current Source Findings

`LIMEService.initOnStartInput()` classifies password, visible-password, email, and web-email text variations as forced-English fields. In that mode prediction is disabled and the email-style keyboard is selected.

For an ordinary software-key tap in English-only mode, `handleCharacter()` reaches a single direct call:

```java
ic.commitText(String.valueOf((char) primaryCode), 1);
```

Source inspection therefore does not establish an unconditional double-commit in the normal English soft-key path. If the affected LINE field reaches that path and `onKey()` is called once, LIME should issue one commit for `j`.

Issue #74 changed URL and search fields from forced English handling to normal text handling. That change is relevant chronology, but it is not yet evidence for this defect because LINE's exact field variation is unknown and the current forced-English helper still handles password/email variations separately.

## Root-Cause Assessment

Root cause is not yet confirmed. The failure must first be isolated to one boundary:

- duplicate key dispatch before `handleCharacter()`
- duplicate LIME `InputConnection` calls
- a composing/commit transition caused by the field restarting input
- host-editor handling that duplicates a single LIME commit

A source-only fix based on the field name or an assumed Android variation would be speculative and could regress password, email, URL, search, or normal text fields.

## Proposed Solution

No production change should be selected until the device trace identifies the failing boundary.

After capturing the reproduced runtime boundary:

1. Add the smallest RED regression test using the captured `EditorInfo` flags and the real affected LIME key path.
2. Make one focused correction at the proven duplicate-dispatch, composition, or commit boundary.
3. Preserve one-tap/one-character behavior in password, email, URL/search, and ordinary text fields.
4. Keep logs and tests free of user identifiers and entered LINE IDs.

## Follow-up Questions

- Which LIME version is installed?
- Which LINE version is installed?
- Does every Latin letter duplicate, or only `j`?
- Does it occur only in LINE's ID-search field?
- Was LIME showing the English keyboard, or was the letter entered directly from a Chinese keyboard?
- Does another keyboard enter one character correctly in the same field?

## Verification Plan

### Android

1. Preserve the confirmed physical Samsung reproduction and record its model/Android version when available.
2. Repeat on `ASUS_AI2202` if available to compare the original environment.
3. Capture privacy-safe `EditorInfo`, key-dispatch count, and `InputConnection` call count on the reproduced path.
4. Add a failing automated test that proves one key tap currently produces two insertions at the isolated boundary.
5. Verify the focused test turns GREEN after the correction.
6. Run the relevant Android unit and instrumentation suites.
7. Runtime-check LINE ID search plus password, email, URL/search, and ordinary text fields.
8. Ask the original reporter to retest a publicly available build containing the fix before closing the issue.

### iOS

No iOS impact is reported. The iOS keyboard extension uses a separate input path, so this Android-specific report does not establish an iOS defect. No iOS code change is planned unless independent iOS runtime evidence reproduces the same behavior.
