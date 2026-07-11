# Issue #153: iOS Array30 `w#` Symbol Input

## Status

- GitHub issue: https://github.com/lime-ime/limeime/issues/153
- Classification: `bug` + `Usability`
- Platform reported: iOS
- Android comparison: the same Array30 `w#` flow works correctly.
- State: open, pending iOS investigation and fix.

## Problem Statement

With Array30 active on iOS, entering `w` followed by a digit key (`w#`, where `#` means any digit key from `0` through `9`) fails to show the expected Array-style symbol input/candidates. Android produces the expected symbol candidates for the equivalent input.

## Likely Root Cause

The platform difference points to the iOS input pipeline rather than the Array30 table itself. The first investigation should compare iOS and Android handling for:

- digit-key routing while a table code is composing
- normalization and preservation of `w` plus the digit
- special Array-style symbol-code detection
- database lookup and candidate generation for the resulting code
- layout-specific handling that may treat the digit as direct numeric output instead of part of the composing code

The exact failing layer is not yet confirmed. Do not change the Array30 database until the composed code and lookup behavior have been captured on both platforms.

## Proposed Solution

1. Add focused logging/tests for Array30 input sequences `w0` through `w9` on iOS.
2. Compare the produced composing code and lookup request with Android.
3. Align iOS digit-key routing and Array symbol handling with the working Android behavior.
4. Preserve ordinary digit entry and behavior for non-Array input methods.

## Follow-up Questions

- Does every `w0`–`w9` sequence fail, or only specific digit keys?
- Is the digit inserted directly into the host app, ignored, or retained in the composing buffer?
- Does the failure occur on every iOS Array30 keyboard layout or only a specific layout?

These details improve diagnosis but do not block initial Android/iOS source comparison.

## Verification Plan

### iOS

- With Array30 active, verify each `w0` through `w9` sequence shows the expected Array-style symbol candidates.
- Verify selecting a symbol commits the expected character.
- Verify Backspace, candidate dismissal, Space, and Enter remain correct after `w#` composition.
- Verify ordinary digit input outside an Array symbol sequence remains unchanged.
- Test relevant phone and standard Array30 layouts.

### Android

- Preserve the current working `w0`–`w9` behavior.
- Use Android as the behavioral reference, but add or retain regression coverage where practical.

## Platform Impact

- **iOS:** confirmed affected by maintainer report; fix and regression coverage required.
- **Android:** reported working; no behavior change is intended. Audit only for parity/reference and guard against regressions.
