# Issue #244: iOS cold/hot production-seam tests are timing-sensitive in Xcode Cloud

## Status

- GitHub issue: https://github.com/lime-ime/limeime/issues/244
- Classification: confirmed iOS test/CI reliability defect; no shipped production defect established
- State: open, source unfixed
- Reporter: maintainer account `limeimetw`
- Affected gate: required Xcode Cloud `LimeTests` action

## Problem statement

The two iOS cold/hot production-seam regressions that verify hot-role learning journaling and the cold-role inverse do not complete reliably across Xcode Cloud destinations. Run 52 at `1a2a781bc417decb243864fc73ea5f9a88b88d9e` failed its required TEST action because the tests timed out after 10 seconds on some or all destinations. Increasing both waits to 60 seconds allowed the exact `v6.1.38` tag to pass once on one iOS 26.5 simulator, and the unchanged tests later passed in Xcode Cloud run 60 at PR #243 head `a4c7b5869c678c5adedff98493e70b7382f28fd7`. Those passes show destination and scheduling sensitivity but do not establish deterministic synchronization.

This tracker covers the test and CI reliability boundary only. There is no current evidence that shipped cold/hot learning journaling fails in production. Issue #242, PR #243, and the separate `pinyin.zip` fixture resolver defect remain out of scope.

## Evidence

### Confirmed Cloud failure

Xcode Cloud run 52 executed the required full `LimeTests` action at `1a2a781bc417decb243864fc73ea5f9a88b88d9e`:

- `DBServerTest.testKeyboardRoleRuntimeJournalsLearningThroughProductionSeam`
  - timed out after 10 seconds on two of four destinations;
  - passed on the other two;
  - one failed destination subsequently found no lazily-created `learn_outbox` table.
- `DBServerTest.testAppRoleRuntimeDoesNotCreateLearnOutbox`
  - timed out after 10 seconds on all four destinations.
- The required Archive action succeeded while the required TEST action failed.

### Later passing evidence

- Before v6.1.38, commit `35f590ee60808d377bffe0c144ffad2d81823547` raised both waits from 10 to 60 seconds and changed a missing `learn_outbox` into a clean assertion result.
- Both 60-second tests passed once on one iOS 26.5 simulator from the exact `v6.1.38` tag.
- Xcode Cloud run 60 passed its required TEST and ARCHIVE actions at PR #243 head `a4c7b5869c678c5adedff98493e70b7382f28fd7`; these two tests were unchanged from `v6.1.38`.

### 2026-08-21 local remediation evidence

The exact `origin/master` head `8724ffb97d8eb8f85b9c688b3328dd00cb675905` was exercised on the project macOS host with Xcode 26.6 and an iOS 26.5 iPhone 17 Pro simulator:

- With process relaunch enabled for every repetition, both tests passed in 19 completed iterations. Xcode then failed before the twentieth iteration because the cloned simulator could not launch `org.limeime`. The result was an XCTest host-launch failure, not either production-seam timeout.
- With one test process and 20 repetitions, both tests passed all 20 iterations. The cold-role test ranged from 0.346 to 3.399 seconds, and the hot-role test ranged from 0.310 to 0.433 seconds.
- The first remediation slice adds thread-safe phase tracing around bootstrap, learning submission, finish enqueue, completion callback, and database inspection. Timeout failures now report the last completed phase rather than only a generic XCTest timeout. Production code and the 60-second deadlock guard remain unchanged while evidence is gathered.

This local run does not reproduce the historical Xcode Cloud completion timeout. It does establish a separate simulator relaunch boundary and provides the diagnostics needed to distinguish Cloud queue non-execution from bootstrap or inspection delay on the next failing run.

A later green run does not erase the earlier cross-destination timeout or prove repeatability. It does narrow this issue to synchronization and test-environment reliability unless an independent production reproduction is found.

## Architecture preflight

### Accepted sources

The following current and superseding documents and production paths were reviewed in full or at the relevant production seam before classification:

- `docs/IOS_DB_COLD_HOT.md` §1.4, especially atomic keyboard learning, `SearchServer.postFinishInput(completion:)`, and the hot-to-cold retry contract; §2 boundary and invariants
- `docs/IOS_DB_COLD_HOT_REARCH.md`, including its historical campaign constraints and residual Xcode Cloud/device gates
- `docs/IOS_DB_COLD_HOT_REARCH2.md` Global Constraints, §3.1, §4.1, §4.5, Task 1, and acceptance-matrix A3
- `docs/IOS_DB_COLD_HOT_REARCH2_PLAN.md`, including the accepted/superseding status, invariant gates, residual exact-SHA Xcode Cloud requirement, and A3 chronology
- `LimeIME-iOS/Shared/Database/DBServer.swift` (`SharedDatabase`, role-aware datasource construction, open/reopen path)
- `LimeIME-iOS/Shared/Database/LimeDB.swift` (`tracksHotLearning`, lazy `learn_outbox`, atomic journaling)
- `LimeIME-iOS/Shared/Search/SearchServer.swift` (serial `learningQueue`, `postFinishInput(completion:)` barrier)
- `LimeIME-iOS/LimeTests/DBServerTest.swift` (the two production-seam tests)

The rearch2 design explicitly supersedes the older absolute freeze on `LimeDB.swift` and `SearchServer.swift`. The accepted current contract requires serialized completion after submitted learning, but dismissal remains a best-effort latency path and production correctness also relies on durable outbox state plus appearance retry.

### Constraint ledger

| Required behavior | Governing invariant | Platform limit | Removable behavior | Consequence of an over-broad change |
|---|---|---|---|---|
| The production-seam tests deterministically observe completion and assert the hot/cold role distinction across the intended Xcode Cloud matrix. | Keyboard-role learning mutates hot data and `learn_outbox` atomically; app-role writes must not create the outbox; `postFinishInput(completion:)` follows previously submitted work on one serial learning queue. | Xcode Cloud destinations can schedule utility-QoS asynchronous work differently and XCTest timeout timing is not itself a production synchronization contract. | The arbitrary fixed wait can be replaced by a deterministic seam or explicit observable completion tied to the work under test. | Changing production queue QoS, bypassing the production seam, eagerly creating the outbox, or weakening assertions could hide a real role/journaling regression or alter runtime behavior. |

## Likely root cause

The exact cause is not established. The current evidence supports four candidates that must be distinguished rather than guessed:

1. utility-QoS starvation or destination-specific scheduling delay before `learningQueue` executes;
2. a test race in the order that candidate learning, `postFinishInput`, and database inspection are arranged;
3. SQLite initialization or lock contention while the isolated production datasource is bootstrapped and later reopened for inspection;
4. a real lifecycle race in the production completion seam.

The asymmetric run-52 result is important: the hot-role test passed on two destinations while the cold-role inverse timed out on all four. The tests share the same asynchronous completion mechanism but differ in role-specific database work. A longer timeout and isolated green runs are insufficient to select among these causes.

## Proposed solution

Keep the production-seam coverage and replace timeout-dependent success with deterministic synchronization around the exact work being asserted.

1. Reproduce both tests repeatedly and independently across the intended Xcode Cloud destination matrix while recording elapsed phases: datasource bootstrap, score-work submission, `postFinishInput` enqueue, completion callback, and database inspection.
2. Prove whether the callback is not scheduled, scheduled late, blocked by earlier learning work, or completed before the asserted database state is externally visible.
3. Add the smallest test seam or completion primitive that observes the production queue barrier without changing production learning decisions, queue ownership, hot/cold role selection, or atomic journaling.
4. Keep a finite timeout only as a deadlock guard, not as the success mechanism. Failure output should identify the last completed phase and role.
5. If an independently reproduced app behavior shows learning or completion failure, separate it into the full production-defect workflow with physical-runtime evidence rather than widening this CI tracker.

## Platform impact

### iOS

Confirmed affected at the XCTest/Xcode Cloud reliability boundary. The tests exercise iOS-only `DBServer` → `SearchServer` → `LimeDB` cold/hot architecture. No shipped production failure is currently established.

### Android

Not affected by this test/CI defect. Android does not use the iOS cold/hot database architecture or these XCTest seams. Android learning behavior remains a product-parity oracle where relevant, but no Android source or test change is indicated.

## Follow-up questions

- Which run-52 destinations passed or timed out, and do failures correlate with simulator OS/device class?
- At timeout, was `learningQueue` waiting to start, blocked in a database write, or finished without invoking the callback?
- Does each test pass repeatedly when run alone, and does ordering the two tests differently change the result?
- Does a deterministic test-only scheduler/barrier reproduce the role assertions without changing production QoS?
- Can SQLite tracing distinguish datasource bootstrap/lock delay from queue scheduling delay?

## Verification plan

### Automated

1. Establish RED by repeating each test enough times to reproduce the historical failure mode on at least one representative Cloud destination or an equivalent controlled scheduler.
2. Add phase-specific diagnostics that fail with the last observed stage rather than only a generic timeout.
3. GREEN each test repeatedly in isolation and in the full `DBServerTest` class.
4. Run the full `LimeTests` suite on every required Xcode Cloud destination at the exact fix head.
5. Require both TEST and ARCHIVE actions to pass at that exact head.
6. Preserve assertions that hot-role production opens create/journal `learn_outbox`, cold-role opens do not, and all initial/reopen/backup/restore datasource paths retain their role.
7. Run the Linux issue-209 source-contract gate to ensure the production seam remains pinned.

### Runtime boundary

No reporter or public-build retest is required for this CI-only tracker. If investigation changes production synchronization code, add simulator and physical-device checks for normal keyboard learning, dismissal, next-appearance retry, outbox drain, cold delivery, and A2 editor unlock before closing.
