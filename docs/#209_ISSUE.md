# Issue #209: Complete Investigation and Implementation Record

## Current status

- Issue: https://github.com/lime-ime/limeime/issues/209
- State: **shipped — pending post-release runtime validation**
- Classification: confirmed iOS database-concurrency/usability defect
- Origin: private support intake tracked through the maintainer project account
- Accepted pull request: PR #223, https://github.com/lime-ime/limeime/pull/223
- Final PR head: `6e4ad3203a4675ce10335a303a4de9c2a9f433f5`
- Merge commit: `daf9fb260756d1ed1d8954430b9ebfc761e6b58e`
- Merged: 2026-08-03
- Shipped: App Store version 6.1.38, publicly available in Taiwan since 2026-08-11
- Reconciled: 2026-08-24

PR #223 replaces the discarded ownership-handoff design with the accepted cold/hot
re-architecture 2. Settings editors now open cold immediately. Explicit editor and
lifecycle mutations are revisioned in cold, while keyboard learning is journaled in
hot and delivered to cold with marker, epoch, fence, and outbox acknowledgement
guards. The obsolete suspension, refresh request/receipt, attached harvest, hidden
probe, timeout, and read-only-on-refresh-failure machinery is deleted.

The final implementation/test tree is identical to reviewed head `a990f8794d2e028d53678cb5fb32d4e1a17ee4af`.
The later final-head commits only merge unrelated `master` documentation changes.
The required strictly read-only Claude Code review found no source blocker. The
focused Linux lifecycle gate passed 10/10, Python compilation and `git diff --check`
passed, and the maintainer confirmed the full native suite and physical-device A1/A2
gating behavior. The exact-head Xcode Cloud run was unavailable because the monthly
quota was exhausted; the maintainer explicitly waived that gate before approving and merging.

The iOS 6.1.37 source candidate predates PR #223. The fix is contained in tag
`v6.1.38`, and Taiwan App Store version 6.1.38 publicly delivers it with release
notes that identify the database-lock and synchronization improvement. Source
acceptance and public delivery are complete. The remaining gate is runtime
verification of the original Related-Phrase Management path on the public build.

## Superseded ownership-handoff record

The following sections preserve the investigation and discarded PR #221 handover as
historical context. Their incomplete lease design and recommended implementation work
are superseded by PR #223 and must not be treated as current work.

### Completed in the handover branch

1. **Complete history and defect record — `fffb60db`**
   - rewrote this document to record all nine draft PRs, discarded designs, test and review evidence, remaining defects, and inactivity;
   - corrected the prior implication that Run 50 proved complete Settings cold ownership;
   - retained the privacy boundary around the originating support report.
2. **Partial scoped-lease foundation — `613d1b0e`**
   - renamed publisher-specific `activeIndependentAccesses` to general `activeAccesses`;
   - replaced optional `withLiveAccessOperation` with a concrete `withLiveAccess` closure;
   - made unavailable datasource opening throw before incrementing the active count;
   - made suspension drain the generalized count;
   - updated the two existing explicit lease call sites;
   - added a deterministic semaphore-based XCTest for draining an entered scoped operation;
   - added one narrow Linux source-contract guard.
3. **Local verification of the partial slice**
   - the new Linux contract test was observed failing before production changes;
   - the focused test passed after the change;
   - the complete lifecycle script passed 10/10;
   - Python compilation and `git diff --check` passed.

### Explicitly not completed

- Native compilation or XCTest execution of `613d1b0e` was not performed.
- No Xcode Cloud run validates `613d1b0e` or the final handover head.
- Ordinary Settings calls through `database.current()` were not migrated to `withLiveAccess`.
- The stale Settings `SearchServer` fallback was not removed.
- Record, related, metadata, sort, clear, import, register, and restore operations were not converted into atomic mutation-plus-publication leases.
- `try?` and sentinel-success/error-loss paths were not removed.
- Controller and UI failure propagation and optimistic-state rollback were not implemented.
- The deterministic cross-screen suspension matrix was not implemented.
- The complete Settings live-cold call-site ownership audit was not completed.
- Affected-device runtime validation was not performed.
- At handover time, PR #221 had to remain draft and must not have been merged in that state.

### Recommended next maintainer action

Treat `613d1b0e` as an unvalidated foundation, not an accepted fix. Review or revert it before continuing. Then implement the remaining sequence in this document using native RED/GREEN coverage, complete the call-site inventory, and run one exact-final-SHA Xcode Cloud test/archive gate. Do not rely on Run 50 for any source after `f2798d69`.

## Origin and privacy boundary

Issue #209 was created on 2026-07-27 after a private support report supplied direct evidence that iOS Related-Phrase Management could fail its keyboard-to-Settings refresh with `SQLite error 5: database is locked`, leaving the screen read-only so the unwanted related phrase could not be edited or deleted.

The reported environment was LIME 6.1.37 on iOS 26.6 RC. Private mail identity and screenshots are intentionally excluded from this repository document.

The issue was created from the maintainer project account, assigned to `jrywu`, and labeled `bug` and `Usability`. It was automatically closed when PR #223 merged and has no public comments.

## Reporter-visible failure

At the time of the report, opening iOS **Related-Phrase Management** started an editor-refresh handshake:

1. Settings publishes a request.
2. The keyboard extension opens its hot database and attaches Settings' live cold `lime.db`.
3. Current keyboard-side `related` rows are harvested into cold.
4. The keyboard publishes a terminal receipt.
5. Settings reloads cold and allows editing only after successful refresh.

In the reported path, the keyboard's write to attached cold failed with SQLite error 5. The refresh timed out or returned failure, and `RelatedListView` deliberately remained read-only.

This was iOS-specific. Android did not use the keyboard-extension/App-Group hot-to-cold handoff then implemented by `TableSyncEngine.harvestEditorRefresh`.

## Reproduction and original root cause

### Native RED reproduction

A real GRDB/XCTest reproduced the failure before the first proposed fix:

1. Open cold `lime.db` from another connection.
2. Hold an immediate cold write transaction.
3. Run the keyboard-to-Settings `related` harvest.

Observed:

- terminal receipt: `.failed`;
- error: `SQLite error 5: database is locked`;
- failure while writing attached `cold_editor.related`;
- cold rows unchanged;
- failure in approximately 0.003 seconds despite the existing five-second busy timeout.

### Executable SQLite investigation

`scripts/test_issue_209_ios_editor_refresh_lifecycle.py` records the relevant SQLite and POSIX-lock semantics with real SQLite on Linux:

| Boundary | Observed behavior | Consequence |
| --- | --- | --- |
| `BEGIN IMMEDIATE` while another cold writer exists | can consume a substantial part of the caller's deadline | retries reduce the Settings receipt budget |
| attached-database read-to-write promotion | can fail immediately despite `busy_timeout` | increasing timeout does not remove the race |
| `DETACH` inside the GRDB transaction | fails with `database cold_editor is locked` | swallowing the error does not prove release |
| final SQLite connection close | releases sidecar/locking state | explicit connection lifecycle matters |
| descriptor-owned `flock` | excludes a second process until explicit release | suitable cross-process ownership primitive |

### Root cause on the original baseline

The defect was not merely “SQLite needs a retry.” Settings and the keyboard extension used the same cold database without a complete ownership transfer:

1. Settings retained or lazily reopened cold during the handshake.
2. The Settings views could begin cold loading beside the refresh request.
3. Opening `LimeDB` can itself write configuration/migration state.
4. The keyboard attempted `DETACH` inside a transaction and discarded failure with `try?`.
5. A `.done` receipt did not prove the keyboard had detached and closed cold.
6. Settings did not require cross-process ownership before reopening cold.
7. Related and normal table editors shared request/receipt files without complete same-process serialization.

The final-v7 source removed the original collision path, but the later full-call-site audit proved that its process-wide suspension gate was incomplete.

## Complete PR and branch history

Nine ownership-handoff draft PRs were created and all nine eventually closed unmerged.
Eight were replaced within that design sequence; PR #221 was superseded by rearch2
PR #223. This churn is part of the defect history and must not be hidden.

| PR | Created / closed (UTC) | Branch / final head | What it attempted | Why it was not the final fix |
| --- | --- | --- | --- | --- |
| #210 | 2026-07-27 00:02 / 2026-07-29 06:51 | `fix/209-ios-related-db-lock` / `9659a497...` | Retry only `SQLITE_BUSY`/`SQLITE_LOCKED`, with a fresh connection per attempt and an approximately four-second retry window. Added transient-lock, persistent-lock, and later-recovery tests. | It tolerated contention but preserved the ownership race. The measured read-to-write promotion can fail immediately, and repeated attempts consume the receipt budget. Closed when the fix moved to lifecycle ownership. |
| #214 | 2026-07-29 06:51 / 06:58 | `fix/209-ios-db-handoff` / `c7c94f2e...` | First full Settings/keyboard cold-database ownership handoff: suspend/close, request, keyboard harvest, explicit release, receipt, reopen. | Closed almost immediately to recreate the same tree with clean review history as #215. |
| #215 | 2026-07-29 06:58 / 12:20 | `fix/209-ios-db-handoff-clean` / `8e42e4e...` | Clean replacement for #214. | Review found that `fcntl` record locks were process-owned rather than descriptor-owned. Same-process handles could bypass exclusion, closing another descriptor could release ownership, ownership recovery had an unbounded wait, and a double reacquisition failure could leave cold disabled. |
| #216 | 2026-07-29 12:20 / 13:30 | `fix/209-ios-db-handoff-final` / `080fe943...` | Switched to descriptor-owned `flock`, added local serialization, bounded acquisition, one process-wide Settings editor handshake, and Related/normal editor coverage. | Later review found additional contention/deadline and sync-scan behavior that was not yet represented. |
| #217 | 2026-07-29 13:30 / 14:10 | `fix/209-ios-db-handoff-final-v2` / `5df3e52d...` | Deferred editor refresh under brief keyboard lock contention while allowing unrelated cold-to-hot sync to continue; used the remaining request TTL for reacquisition; added notifications after suspension reopened. | Timeout propagation, release-before-signal ordering, queue concurrency, and test-double behavior still needed tightening. |
| #218 | 2026-07-29 14:10 / 14:57 | `fix/209-ios-db-handoff-final-v3` / `f74c4f8e...` | Exposed initial/reacquisition budgets to native tests, released before signaling the keyboard, used a concurrent blocking bridge, and removed timeout-erasing convenience APIs. | Review still found defensive unlock-state, malformed-request, cancellation-polling, and test-quality/error-path gaps. |
| #219 | 2026-07-29 14:57 / 23:45 | `fix/209-ios-db-handoff-final-v4` / `7174d38d...` | Cleared local ownership state even if `LOCK_UN` reported failure, isolated malformed requests, avoided cancellation-driven polling spins, and expanded native/error-path coverage. | The next strict review round found broad/brittle source-text tests and smaller production/error-path issues: receipt queue priority, lock-factory failure aborting unrelated sync, unmatched failure signaling for malformed requests, false failure after final unlock reporting, and test descriptor contamination. Work continued on unpublished review branches before a clean final-v6 replacement. |
| #220 | 2026-07-29 23:45 / 2026-07-30 03:20 | `fix/209-ios-db-handoff-final-v6` / `8fea4b33...` | Clean one-pass replacement after review5/review6. Reduced the Python gate to real behavior plus durable anti-resurrection checks, fixed the review findings, coordinated independent cold publishers/mutations, and preserved fail-closed ownership recovery. | Replaced by #221 after further source changes added datasource-install rejection and final-v7 validation/docs. |
| #221 | 2026-07-30 02:48 / closed unmerged 2026-08-02 | `fix/209-ios-db-handoff-final-v7`; handover adds `fffb60db` and `613d1b0e` | Serialized handoff with publisher drain, fail-closed recovery, datasource-install rejection, focused lifecycle tests, and Xcode Cloud Run 50; the handover adds complete documentation and a partial generalized lease foundation. | A later complete Settings call-site audit found broader unimplemented lease, stale-datasource, atomic-publication, error-propagation, and UI-consistency defects. The partial handover source was not native-validated. PR #221 closed unmerged when rearch2 PR #223 superseded it. |

### Unpublished validation branches and review rounds

The published PR sequence understates the internal iteration. Between #219 and #220, additional local/review branches were used to respond to strict findings. Significant results included:

- reducing a 40-test broad Swift source-text gate to five real SQLite behavior tests plus durable anti-resurrection checks;
- changing receipt polling to the intended queue priority;
- preventing lock-factory failure from aborting unrelated synchronization;
- preventing malformed requests from emitting an unmatched import-failure signal;
- preventing a defensive final unlock error from converting a completed refresh into false failure;
- adding native-test reset for process-lifetime lock descriptors;
- checking exact timeout budgets and release-before-signal ordering;
- running repeated strict code and documentation reviews;
- rebuilding clean replacement branches instead of force-updating published PRs.

This iterative review found real defects, but repeatedly replacing the public PR instead of completing one bounded architecture caused avoidable churn.

## What discarded PR #221 actually fixed

The source at `f2798d69a46ced0c6332460c6206a79081cdd622` materially fixes the original editor handoff:

1. `EditorRefreshFileLock` uses bounded descriptor-owned `flock` with process-local handle serialization.
2. A process-wide async gate permits only one Settings editor handshake at a time.
3. Settings acquires ownership, blocks new tracked cold publishers, drains active tracked publishers, suspends and closes cached cold, publishes the request, releases ownership, and then signals the keyboard.
4. The keyboard acquires ownership, revalidates the request, attaches cold outside the write transaction, performs the harvest transaction, detaches outside the transaction, explicitly closes the connection, and publishes the terminal receipt only after release is established.
5. Settings reacquires ownership before cleanup or cold reopen.
6. Failed reacquisition remains fail-closed instead of reopening cold under keyboard ownership; a later ownership-holding editor attempt can recover.
7. Datasource installation is rejected while suspension is pending or active.
8. Related and normal table editors share the serialized handshake and do not start their initial cold load beside it.
9. The retry-only workaround from #210 is absent.
10. Android and related-word dirty-key semantics are unchanged.

These are valid and tested changes. They are not sufficient to claim complete process-wide cold ownership.

## Historical defects that remained in discarded PR #221

### 1. Ordinary cached-datasource operations escape the lease

`SharedDatabase.current()` checks `accessSuspended` and `suspensionPending` while holding its condition, returns a `LimeDB`, and then releases the condition. The caller performs SQLite work after the ownership check has ended.

`SharedDatabase.suspendLiveAccess()` drains only `activeIndependentAccesses`, which covers the explicit `withLiveAccessOperation` publisher path. It does not count ordinary reads and writes performed through a datasource returned by `current()`.

Consequences:

- suspension can close `cachedDatasource` while an ordinary read/write still uses it;
- “all Settings-side cold access is drained” is not true;
- the new handoff can trade the original lock collision for stale/closed queue failures.

### 2. A retained Settings `SearchServer` bypasses suspension

`DBServer.makeSearchServer()` captures an initial datasource and supplies:

```swift
database.current() ?? initialDatasource
```

During suspension, `database.current()` intentionally returns `nil`, so an existing `SearchServer` falls back to the stale object that suspension is closing or has closed.

Related/record management code retains or recreates this facade and can therefore operate outside the intended fail-closed gate.

### 3. Mutation and publication are split across ownership windows

Management operations can mutate through `SearchServer` or another unleased DB facade and only afterward call `markTableChangedAndPublish()` under `withLiveAccessOperation`.

Suspension can begin between the mutation and publication. The local database change may commit while revision/generation and `im.json` publication are rejected.

Affected categories include:

- mapping add/update/delete;
- related phrase add/update/delete/clear;
- table clear;
- import/register/restore paths;
- metadata and sort operations with separate publication behavior.

### 4. Metadata suspension errors are swallowed

`mutateAndPublishColdMetadata` uses `try? database.withLiveAccessOperation`.

If suspension rejects the lease, the error disappears. These methods cannot report failure:

- `setImConfig`;
- `updateIMEnabled`;
- `setImConfigKeyboard`.

`updateIMSortOrder` is also caught by callers and does not share one complete metadata-publication contract.

Controllers and views can then perform success-only side effects even though persistence was discarded:

- synchronize activated-IM preferences;
- mark keyboard caches dirty;
- invalidate and reload lists;
- save keyboard preferences;
- dismiss a picker/detail view;
- return `.success(())`;
- keep optimistic toggle/order state.

This is a concrete operation-loss defect, not a theoretical cleanup item.

### 5. Sentinel returns convert suspension into fake data or fake success

Several DBServer APIs translate unavailable cold into `[]`, `0`, `-1`, `false`, or a no-op. Callers cannot distinguish:

- a valid empty result;
- validation failure;
- temporary suspension;
- an unavailable or stale datasource.

A screen can replace real displayed state with empty data or continue as though an operation succeeded.

### 6. Direct Settings paths do not share one contract

`IMInstallView`, `IMStoreView`, `IMDetailView`, `SetupImController`, management controllers, and compatibility/protocol methods contain low-level database calls, `try?`, premature completion/dismissal, or optimistic side effects.

Fixing only the metadata helper would leave equivalent loss and race paths in import, install, clear, register, restore, record, and related operations.

### 7. Tests do not cover the complete process-wide invariant

PR #221's tests proved its handoff mechanics, tracked publisher drain, fail-closed reacquisition, and hot-to-cold harvest. They did not prove that:

- an ordinary cached read or write is drained before close;
- a pre-existing Settings `SearchServer` cannot use stale cold during suspension;
- every mutation category is rejected without persistence or UI side effects;
- mutation plus revision/generation/publication cannot be split by suspension;
- every direct Settings call site propagates typed failure;
- displayed data is preserved rather than replaced with sentinel emptiness.

## Superseded final-v7 completion invariant

For Settings:

> Every operation that opens, reads, mutates, snapshots, publishes, replaces, imports, exports, backs up, or restores live cold must hold one scoped `SharedDatabase` access lease for its complete database lifetime. Suspension blocks new leases, drains all existing leases, closes cold, and keeps it closed until cross-process ownership is safely recovered. No Settings `LimeDB` reference may escape a lease, and no caller may translate lease rejection into success.

For user mutations:

> Database mutation, revision/generation update, cold publication, and `im.json` publication execute in one lease. Preference, cache, navigation, callback, and optimistic UI side effects happen only after success. Lease rejection is visible and leaves persisted and optimistic state unchanged.

## Superseded final-v7 implementation sequence

This sequence records the gaps in discarded PR #221. PR #223 supersedes it with the
accepted rearch2 design, so these steps are no longer an active implementation plan.

1. **Generalize the scoped lease.** Count every scoped cold operation, make suspension drain all of them, reject leases while pending/suspended, and never report an unavailable datasource as a successful lease.
2. **Remove stale Settings ownership.** Eliminate the `database.current() ?? initialDatasource` fallback for Settings management. Keep the keyboard's stable hot-runtime datasource path separate.
3. **Create throwing atomic operations.** Put each mutation, revision/generation update, cold publication, and `im.json` publication inside one lease.
4. **Propagate typed failures.** Remove `try?` and sentinel-success behavior for affected Settings operations.
5. **Correct UI ordering.** Do not update preferences/cache/navigation/optimistic state before persistence succeeds; preserve displayed data during temporary suspension.
6. **Add a deterministic cross-screen matrix.** Hold cold suspended, attempt each mutation category, and assert no database/revision/publication/UI side effect. Resume and assert representative retries succeed.
7. **Audit every Settings live-cold call site once.** Record operation, lease API, error propagation, UI result, and behavioral test.
8. **Run one final exact-SHA gate.** Only after local review is clean: native iOS tests plus archive, then truthful docs and PR body reconciliation.

This historical instruction was retired when PR #221 closed unmerged and PR #223
implemented the accepted replacement architecture.

## Verification ledger

### PR #210 retry candidate

- Native RED reproduced exact SQLite error 5 and unchanged cold rows.
- Transient-lock, persistent-lock, and later-recovery native tests were added.
- Linux retry source-contract gate: 4/4 passed.
- Python compile and `git diff --check`: passed.
- Exact-head Xcode Cloud iOS tests and archive: passed.
- Result: technically coherent retry mitigation, rejected because it did not remove the ownership race.

### Ownership-handoff iterations (#214–#220)

Across the iterations, executed evidence included:

- real SQLite attached-database contention;
- same-process lock contention;
- separate-process descriptor-owned `flock` exclusion;
- Related-versus-normal editor single-flight;
- controller-propagated initial and reacquisition timeout budgets;
- release-before-signal ordering;
- bounded persistent lock failure;
- fail-closed reacquisition and later recovery;
- malformed request handling;
- cancellation polling behavior;
- explicit ATTACH/transaction/DETACH/close ordering;
- terminal receipt ordering;
- post-receipt cold writability;
- repeated iOS native-test and archive runs;
- repeated independent code/documentation review rounds.

Several intermediate PR bodies said “ready,” “no blockers,” or “zero unresolved findings.” Those were accurate only for the narrower tree and review scope at that moment. Later review proved additional architectural gaps. They are not evidence that PR #221's complete Settings ownership contract is correct.

### Discarded PR #221

Executed for source SHA `f2798d69a46ced0c6332460c6206a79081cdd622`:

- focused lifecycle gate: 9 tests passed;
- related Python suites passed;
- `python3 -m py_compile scripts/*.py`: passed;
- `git diff --check`: passed;
- Xcode Cloud Run 50 iOS tests: passed;
- Xcode Cloud Run 50 iOS archive: passed;
- production Xcode Cloud workflow configuration restored and verified.

Limitations:

- Run 50 proves the handoff source and native tests that existed at that SHA.
- It does not prove the unimplemented complete-lease invariant.
- PR #221's final head added documentation only after the validated source SHA.
- Affected-device Related-Phrase Management runtime confirmation has not been completed.
- GitHub reported no checks attached directly to PR #221's final head.

After Run 50, handover commit `613d1b0e` changed source and tests. Its local Linux gate passed 10/10, but it has no native compile, XCTest, archive, runtime, or exact-SHA Cloud evidence. Therefore Run 50 must not be presented as validating the handover tree.

### PR #223 (merged)

- Reviewed implementation/test tree: `a990f8794d2e028d53678cb5fb32d4e1a17ee4af`.
- Final PR head: `6e4ad3203a4675ce10335a303a4de9c2a9f433f5`; the intervening commits only merged unrelated `master` documentation and did not change the #209 implementation or tests.
- Merge commit: `daf9fb260756d1ed1d8954430b9ebfc761e6b58e`.
- Required strictly read-only Claude Code review: no source blocker.
- Focused Linux lifecycle gate at the reviewed implementation/test tree: 10/10 passed.
- Python compilation and `git diff --check`: passed.
- Maintainer-reported evidence for the same implementation/test tree: full native suite passed and physical-device A1/A2 gating behavior passed.
- Exact-final-head Xcode Cloud test/archive: not run because the monthly quota was exhausted; the maintainer explicitly waived this gate before approving and merging.
- GitHub checks attached to the final head: none.

## Rejected approaches and lessons

1. **Retry-only mitigation:** rejected because it tolerates but does not remove the ownership race.
2. **Process-owned `fcntl` locking:** rejected because same-process descriptors do not provide the required ownership semantics and descriptor close can release process ownership.
3. **Unbounded ownership recovery:** rejected because a suspended extension could disable cold access indefinitely.
4. **Reopen after failed reacquisition:** rejected because it violates fail-closed ownership and can recreate the original collision.
5. **Broad Swift source-text tests:** reduced because they pinned comments/formatting/implementation fragments rather than native behavior.
6. **Tracking only independent publishers:** incomplete because ordinary cached-datasource operations dominate Settings and can outlive the ownership check.
7. **Returning stale fallback datasources:** invalid during suspension because it defeats the gate.
8. **Splitting mutation and publication:** invalid because cold can change without matching revision/generation/publication.
9. **Swallowing lease rejection:** invalid because UI success can be reported for discarded persistence.
10. **Repeated clean replacement PRs during active review:** created excessive churn. At that stage, further work needed to finish the bounded invariant before another public-state change.

## Activity accountability

- Issue opened: 2026-07-27 07:28 +08:00.
- Nine pre-rearch2 draft PRs were created: retry candidate #210 and ownership-handoff PRs #214–#221.
- All nine closed unmerged. Eight were replaced within that design sequence, and PR #221 was superseded by rearch2 PR #223.
- PR #221 received no source update between 2026-07-30 10:51 +08:00 and the partial local lease commit at 2026-08-01 09:25 +08:00.
- After the full remaining-defect plan was written, implementation did not continue for approximately two days.
- On 2026-08-01 a local validation branch, `fix/209-complete-cold-ownership-validation-v8`, was created from PR #221 and a Codex Task-1 worker was started.
- The worker was stopped immediately, before any production/test edit, when Jeremy directed that this issue document be rewritten first.
- The Codex start/stop produced no tracked source change and no GitHub change.
- Hermes then implemented the bounded Task-1 lease slice directly and committed it as `613d1b0e`.
- Jeremy subsequently ended further fix work and requested this final handover plus PR publication.
- PR #221 closed unmerged on 2026-08-02 after the accepted rearch2 design superseded it.
- PR #223 implemented rearch2 and merged on 2026-08-03 as `daf9fb260756d1ed1d8954430b9ebfc761e6b58e`.

## Platform impact

### iOS

Confirmed affected. The defect was in the iOS Settings/keyboard-extension hot/cold architecture. The deleted handoff code was shared by Related and normal table editors; the original reporter-visible failure was specifically Related-Phrase Management. The accepted rearch2 replacement remains iOS-only.

### Android

No corresponding defect is established. Android does not use this App-Group keyboard-extension handoff. Android source remains out of scope.

## Completion and release-QA boundaries

- [x] Accepted rearch2 source merged through PR #223.
- [x] Final PR head and merge commit verified live.
- [x] Required strictly read-only Claude Code review found no source blocker.
- [x] Focused lifecycle gate, Python compilation, and diff checks passed.
- [x] Maintainer-reported native-suite and physical-device A1/A2 verification passed against the reviewed implementation/test tree.
- Waived: the exact-head Xcode Cloud gate was not run because the monthly quota was exhausted.
- [x] App Store version 6.1.38 containing merge commit `daf9fb260756d1ed1d8954430b9ebfc761e6b58e` is publicly available in Taiwan.
- [ ] Verify the original Related-Phrase Management path on public App Store version 6.1.38.

## 2026-08-02–03 — Superseded and merged as iOS editor-sync re-architecture 2

The ownership-handoff architecture documented above is superseded by the accepted
`docs/IOS_DB_COLD_HOT_REARCH2.md` design. The rearch2 implementation deletes the
Settings cold-suspension/read-drain gate, editor refresh request/receipt polling,
editor refresh file/session locks, keyboard-side ATTACH harvest, and the read-only
editor state that depended on refresh failure.

Preserved behavior: backup export request/receipt for the backup workflow, restore
epoch replacement, generation publication, `KeyboardFlushLock`, relay/Full-Access
state types, and the revisioned cold-table lifecycle path through
`im_lifecycle_intent`.

Current invariant: Settings editors open live cold immediately; editor and lifecycle
mutations commit cold data plus fence/lifecycle revision atomically; keyboard
learning commits hot data plus `learn_outbox` atomically; dismissal/appearance
flush validates marker and epoch inside the cold transaction and acknowledges by
outbox version.

Residual validation belongs to rearch2 post-release QA, not to the deleted #209
handoff. The source review and local checks are complete. The maintainer reported
the native suite and physical-device A1/A2 pass against the reviewed
implementation/test tree, while the exact-final-head Xcode Cloud gate was waived
rather than executed. App Store version 6.1.38 now contains PR #223 and is publicly
available. The only remaining boundary is verification of the original
reporter-visible Related-Phrase Management path on that public build.
