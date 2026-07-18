# Issue #161: Validate Related-Word Parent Input

## Classification

- GitHub issue: https://github.com/lime-ime/limeime/issues/161
- Type: `bug` + `Usability`
- Reporter platform: Android, based on the attached Settings screenshots
- Cross-platform scope: Android and iOS have equivalent related-record add/update paths
- Product invariant: related-table `pword` is exactly one Chinese Han character

## Problem

The original question asked for an iOS text-replacement / Samsung text-shortcut equivalent. LIME already supports that workflow through each input method's `瀏覽 / 編輯資料表`, and the reporter confirmed that path works.

The follow-up exposed a separate validation bug in `關聯字管理`: add/edit accepted parent values that violate the related-table model. For example, a record with `pword = add` could be saved successfully but could not be found through the designed related-word lookup. Multi-character parent input has the same model mismatch.

## Root cause

The Android and iOS related-record editors checked only that `pword` and `cword` were non-empty. Their controller/database boundaries did not enforce the existing one-Han-character `pword` invariant.

Runtime related-candidate lookup intentionally treats one Chinese character as `pword` and the continuation as `cword`. The defect is missing write-time validation, not the runtime lookup contract. PR #163 was closed unmerged because its broad management substring-search approach assumed multi-character `pword` records should be supported.

## Fix

For both add and update:

- accept exactly one Unicode Han code point as `pword`
- accept supplementary-plane Han characters
- trim surrounding whitespace before validation and storage
- reject two or more Chinese characters
- reject ASCII letters, digits, punctuation, and full-width non-Han characters
- display `首字只能輸入一個中文字`
- keep runtime related-candidate lookup unchanged
- keep deletion available for existing malformed rows
- do not migrate or delete existing records automatically

## Platform impact

### Android

Confirmed affected by the reporter's screenshots and source inspection. Validation is added to both add/edit sheets and `ManageImController`, so bypassing the UI cannot write a malformed parent. Focused unit and instrumentation tests cover Unicode validation, add/edit UI validation, and controller rejection before refresh/database success behavior.

### iOS

Source inspection found the same non-empty-only checks in both async and callback add/update APIs. Matching validation and tests are included. iOS behavior is source-audited but not reporter-confirmed, and XCTest still requires macOS/Xcode execution.

## TDD evidence

Before production changes, focused tests demonstrated that Android accepted `台中` and non-Han parent text at the editor/controller boundary. The corrected tests require exactly one Han code point and verify that rejected writes do not report successful refresh.

## Acceptance criteria

- [x] Exactly one BMP Han character is accepted.
- [x] Exactly one supplementary-plane Han character is accepted.
- [x] Multiple Chinese characters are rejected.
- [x] ASCII and full-width non-Han input are rejected.
- [x] Android add/edit UI and controller boundaries validate input.
- [x] iOS async and callback add/update boundaries validate input.
- [x] Existing malformed rows can still be deleted.
- [ ] iOS XCTest passes under Xcode.
- [ ] Maintainer review/merge.
- [ ] Reporter confirms the fix in a newer Android build.
