# iOS DB Cold/Hot Re-Architecture 2 — Autonomous Goal-Mode Plan

**Goal mode:** run every iteration end-to-end in one session, without pausing for approval
between iterations. The ONLY permitted stop conditions are (a) the Final Target Gate is fully
green, or (b) a hard block that cannot be resolved from the design doc + code even after the
rule-of-three research step — in which case drive everything else to green and report the
residue as an explicit checklist. Known maintainer-only residuals are pre-declared in
§Residuals; they do not count as blocks.

## Design sources

1. **[IOS_DB_COLD_HOT_REARCH2.md](IOS_DB_COLD_HOT_REARCH2.md)** — the **accepted, frozen**
   design and task list (status header: accepted 2026-08-01). This campaign implements its
   Tasks 1–5 **exactly as written**: sections §1–§7 are authoritative for every behavior,
   schema, ordering rule, and test fixture. Do not re-design; a genuine contradiction found
   during implementation is a hard-block candidate, not a license to improvise. Tick its
   `- [ ]` checkboxes as steps complete, in the same commit as the work.
2. **[IOS_DB_COLD_HOT.md](IOS_DB_COLD_HOT.md)** — the shipped architecture. Everything the
   design doc does not change (epoch/restore, generation, backup, emoji, `im.json`,
   cross-process reopen, §1.7/§1.8 runtime rules) must keep working. Task 5 rewrites its §1.4
   and §2 to match as-built.
3. **[IOS_ACTIVE_KB_DETECT.md](IOS_ACTIVE_KB_DETECT.md)** — the relay / FA-detection layer.
   Consumed, never rewritten. Active-keyboard detection stays relay-only; the Apple
   active-keyboard API / `UITextInputMode` KVC must never reappear (crash-fix guard).
4. **[LIME_SETTINGS.md](LIME_SETTINGS.md)** — settings UI spec. The editor read-only *gate*
   is removed by design (editors always editable on cold); everything else in the §4 setup UI
   is untouched.
5. **[DB_DEDUPE.md](DB_DEDUPE.md)** — out-of-scope guard. Nothing from that file lands here.
6. **[#209_ISSUE.md](%23209_ISSUE.md)** — the defect record. Task 5 updates it truthfully;
   never delete its history.

## Baseline and branch

- **Branch:** create `ios-db-rearch2` from `fix/209-ios-db-handoff-final-v7` at `4127d03e`
  (superpowers:using-git-worktrees). The v7 branch is the baseline because the ownership
  machinery this campaign deletes (suspension, `EditorRefreshFileLock`, request/receipt,
  probe) lives there, and Task 3 refactors its `flock` code into `KeyboardFlushLock`.
- **Known-dirty baseline:** commit `613d1b0e` (generalized lease) has **no native
  compile/test evidence** (#209_ISSUE.md). I0's first job is a green native gate; if
  `613d1b0e` breaks it, revert that commit outright — its machinery is deleted by Task 5
  anyway — and record the revert in the flight log.
- **Uncommitted docs** (`IOS_DB_COLD_HOT_REARCH2.md`, `DB_DEDUPE.md`, this plan,
  `.claude/txt/db_duplicates_full_list.txt`) are committed in I0 as
  `docs(ios): accept editor-sync re-architecture 2 design`.
- **PR #221 is not touched.** It stays draft on its branch. On completion this campaign
  pushes `ios-db-rearch2` and reports; opening/closing PRs is the maintainer's decision
  (#209_ISSUE.md's churn lesson: no replacement-PR churn without Jeremy).

## Scope

The old file-freeze regime is formally superseded (design doc, Global Constraints). Scope is
now guarded by **invariants** plus a closed White list.

### Hard invariants (checked at every gate; violation = stop and re-scope)

- `LimeStudio/**` (Android) and `Database/**` (seeded assets): **byte-identical to the branch
  point**. `git diff --stat 4127d03e -- LimeStudio Database` must be empty.
- LimeDB `user_version` stays **105**; no unique-index creation on data tables; the three
  sync tables (§3.1–§3.3) are `CREATE TABLE IF NOT EXISTS` and never enter `migrate()`.
- Zero references to the Apple active-keyboard API / `UITextInputMode` active-mode KVC.
- The relay / FA / active-detection types in `SyncContract.swift` (`RelayActiveState`,
  `FAStateResolver`, `RelayPrefSync`, relay token/payload codecs) are not modified — the file
  is White for the sync layer, those types are Amber inside it.
- `emoji.db`, `im.json` machinery, backup handshake, epoch/restore workflow: behavior
  unchanged (files may be touched only where the tasks say so).
- No new `try?` on any changed write path; typed failures only (design doc Global
  Constraints).

### White list — the ONLY sources the campaign may modify or add

Modify (the union of the design doc's five task file lists):

- `Shared/Database/LimeDB.swift`, `LimeDBProtocol.swift`, `DBServer.swift`,
  `SyncContract.swift`, `TableSyncEngine.swift`, `ColdPublisher.swift`
- `Shared/Search/SearchServer.swift` (§4.5 `learningQueue` + `postFinishInput(completion:)`
  ONLY — no candidate-query or learning-decision change)
- `LimeKeyboard/KeyboardViewController.swift` (dismiss/appearance wiring only)
- `LimeSettings/AppDelegate.swift`, `Controllers/ManageImController.swift`,
  `ManageRelatedController.swift`, `SetupImController.swift`,
  `Views/RecordListView.swift`, `RelatedListView.swift`
- `LimeIME-iOS/LimeIME.xcodeproj/project.pbxproj` (hand-edit target membership; never
  xcodegen)
- Tests: `LimeDBTest`, `SearchServerTest`, `ManageImControllerTest`,
  `ManageRelatedControllerTest`, `SetupImControllerTest`, `DBServerTest`,
  `ColdPublisherTest`, `TableSyncEngineTest`, `SyncContractTest`,
  `KeyboardViewControllerTest`, `RecordEditingCapabilityTest`
- Docs (Task 5 + tracking): `IOS_DB_COLD_HOT.md`, `#209_ISSUE.md`, `BACKLOG.md`,
  `IOS_DB_COLD_HOT_REARCH2.md` (checkboxes only), this plan (flight log only)

Add:

- One new source file for `KeyboardFlushLock` if cleaner than in-place; new `LimeTests/*.swift`
  files for new fixtures; `.claude/scripts/*`, `.claude/txt/*` (briefs, reports, gate script).

### Amber — read/consume, never rewrite

`LimeSettingsView.swift`, `SetupTabView.swift`, `DBManagerView.swift` (backup gate),
`LIMEPreferenceManager.swift`, `IMDetailView`/`IMListView`/`IMStoreView`/`IMInstallView`
(unless a Task 2 routing change proves unavoidable — then stop, justify against the design
doc, log the deviation), and the relay/FA types inside `SyncContract.swift`.

## Test scope

- **Keep-green — never rewrite an existing assertion:** `FAStateTest`, `RelayPayloadTest`,
  `RelayPrefSyncTest`, `SetupDetectionTest`, `LIMEPreferenceManagerTest`, and every
  Keep-green suite from the first campaign that still exists. Methods may be added.
- **Extend (White):** the eleven suites listed above — new methods per the design doc's task
  steps. An existing assertion may be **deleted only in Task 5** and only if it asserts
  machinery Task 5 deletes (suspension, request/receipt, probe delay, read-only gate);
  record every such deletion in the Task 5 commit message.
- **Anti-gaming rule:** never weaken or delete a test to make a gate pass outside the Task 5
  carve-out above. A red Keep-green test means the change is wrong — revert and re-scope.

## Ground rules (every iteration)

- One commit per design-doc task, using the design doc's exact commit messages. I0 commits
  separately. **No Claude co-author trailer** (repo rule).
- Build/test oracle: `.claude/scripts/ios-gate.sh` (created in I0 — wraps the design doc's
  `xcodebuild test … -only-testing:LimeTests` full-suite command; always prefix xcodebuild
  with `GIT_CONFIG_COUNT=0`). Focused RED/GREEN cycles use the per-task
  `-only-testing:` commands from the design doc verbatim.
- Encoding: Swift / `.md` UTF-8 **with** BOM; `.json` / shell without BOM. Scripts in
  `.claude/scripts/`, notes in `.claude/txt/` — never new files at repo root.
- TDD (superpowers:test-driven-development): every task lands its Step-1 failing tests
  first, runs Step 2 to prove RED, then implements.
- Rule of three (repo rule 7): 3 failed attempts on one issue → stop, switch to
  superpowers:systematic-debugging + external research before the next attempt.
- Tick the design doc's checkboxes in the same commit as the completed step.

## Skills map

| Skill | When |
| --- | --- |
| superpowers:executing-plans | outer loop across iterations |
| superpowers:test-driven-development | inside every task |
| superpowers:systematic-debugging | any red gate |
| superpowers:verification-before-completion | before declaring ANY gate green — fresh command output, never memory |
| /code-review → ponytail:ponytail-review | review stack at every iteration gate |
| superpowers:subagent-driven-development + dispatching-parallel-agents | build tasks (Codex CLI dispatch; parallel only where file-disjoint) |
| superpowers:finishing-a-development-branch | after the Final Target Gate |

## Subagent workflow (Codex CLI)

Implementation is delegated to **Codex CLI**; **review is NEVER delegated** — Claude reviews
every result against the design doc and the invariants.

1. **Brief** — `.claude/txt/rearch2-t<N>-brief.md`: the exact design-doc task text (verbatim),
   the White-list files it may touch, the invariants, the Amber list, encoding + pbxproj
   rules, and the standing instruction to use test-driven-development /
   systematic-debugging / verification-before-completion throughout.
2. **Dispatch** — GPT-5.5, `xhigh` effort, `priority` serving:

   ```sh
   codex exec -C <worktree> -s workspace-write \
     -m gpt-5.5 -c model_reasoning_effort="xhigh" -c service_tier="priority" \
     -o .claude/txt/rearch2-t<N>-report.md \
     "$(cat .claude/txt/rearch2-t<N>-brief.md)"
   ```

   Tasks 1 and 2 are file-disjoint enough to dispatch concurrently after I0; Tasks 3–5 are
   serial (each consumes the previous). Every git command inside a brief uses
   `git -C <abs worktree path>` (Codex cwd resets between turns).
3. **Invariant check (Claude, FIRST)** — `git diff --stat 4127d03e -- LimeStudio Database`
   empty; every changed/added file on the White list; grep guards (KVC, `user_version`,
   relay types untouched). A violation is rejected outright and re-briefed.
4. **Gap review (Claude)** — diff the result against the design-doc task line by line:
   every Step-1 fixture present and meaningful, every ordering rule (§4.3 order, §4.4
   steps, §5.1 transaction shape) implemented as written, no silent scope changes. Small
   gaps → fix directly; structural → re-brief.
5. **Review stack (Claude)** — /code-review (zero unresolved CONFIRMED correctness
   findings) then ponytail:ponytail-review (delete over-engineering; new abstractions the
   design doesn't require are defects).
6. Repeat until the iteration gate is green, then commit.

## Iteration gate template (every iteration passes ALL)

- **G1 Build** — `ios-gate.sh` green (app + appex + full LimeTests in one process).
- **G2 Tests** — this task's RED fixtures were observed failing before implementation and
  pass after; no prior-task or Keep-green regressions; anti-gaming rule holds.
- **G3 Invariants** — the §Scope hard invariants, from fresh command output.
- **G4 Gap review** — implementation matches the design-doc task and its referenced
  sections; any genuinely forced deviation is written back into
  `IOS_DB_COLD_HOT_REARCH2.md` in the same commit and logged below.
- **G5 /code-review** — zero unresolved CONFIRMED correctness findings.
- **G6 ponytail-review** — zero unresolved over-engineering findings; deliberate ceilings
  carry `// ponytail:` comments.
- **G7 verification-before-completion** — every green claim backed by fresh output this
  session.

## Iterations

### I0 — Baseline (green gate + campaign scaffolding)

1. Create worktree/branch `ios-db-rearch2` from `4127d03e`.
2. Commit the accepted design docs (`IOS_DB_COLD_HOT_REARCH2.md`, `DB_DEDUPE.md`, this
   plan, `.claude/txt/db_duplicates_full_list.txt`).
3. Write `.claude/scripts/ios-gate.sh` (full-suite oracle, `GIT_CONFIG_COUNT=0` prefix,
   fails on nonzero).
4. Run the gate. If `613d1b0e` does not compile or fails natively, `git revert 613d1b0e`
   (message: `revert(ios): drop unvalidated lease slice before rearch2`) and re-run.
   Iterate until green. This is the first native validation this branch has ever had —
   do not proceed on a red baseline.
5. Record the baseline test count in the flight log.

Gate: G1/G3/G7 only (no new behavior yet).

### I1 — Design-doc Task 1: Atomic hot learning and serialized learning completion

Execute Task 1 exactly (learn_outbox + hot-role tracking in `LimeDB` transactions; serial
`learningQueue`; `postFinishInput(completion:)`). Campaign notes: the §4.5 queue change is
the one place `SearchServer` may be touched; the serialization-is-ordering-only assertion is
mandatory, not optional. Gate (+ template).

### I2 — Design-doc Task 2: Atomic cold editor and table-lifecycle intent

Execute Task 2 exactly (`performEditorMutation`, `performTableLifecycleMutation`, staging
importers, §3.3 lifecycle table, deferred publication + launch recovery). Campaign notes:
this is the largest task — if the staging-importer rework of `SetupImController` proves to
need an Amber view file, stop and log rather than silently widening. May be dispatched in
parallel with I1 (file overlap is only `LimeDB.swift`; if both touch it, serialize the
merge, I1 first). Gate (+ template).

### I3 — Design-doc Task 3: Causal reconcile, locked flush, and hot recovery

Execute Task 3 exactly (fence application, revision-ordered lifecycle handling,
`KeyboardFlushLock`, marker+epoch-validated flush, §4.6 hot rebuild). Campaign notes: the
two epoch-race fixtures and the rebuilt-hot metadata assertions are the highest-value tests
in the whole campaign — they must be real behavior tests, never source-string assertions.
Gate (+ template).

### I4 — Design-doc Task 4: Dismiss push, appearance retry, immediate editors, honest upgrade

Execute Task 4 exactly (dismiss wiring, §5.1 scoped baseline with exact content comparison,
§5.2 keyboard transition, editor gating removal). Campaign notes: build the upgrade
fixtures from **v7-shaped state** (databases produced by the code at the branch point), not
hand-crafted approximations — the fixtures' whole value is fidelity to what upgrading
devices will actually contain. Both upgrade orders (app-first / keyboard-first) get
independent end-state assertions. Gate (+ template).

### I5 — Design-doc Task 5: Delete #209 machinery and run final gates

Execute Task 5 exactly (delete suspension/request-receipt/probe/read-only machinery and its
contract tests; static greps; full suite; both target builds; doc updates). Campaign notes:

- `#209_ISSUE.md` update: append a dated section stating the ownership approach was
  superseded by the accepted rearch2 design, what was deleted, and the new invariant —
  never rewrite its history.
- `IOS_DB_COLD_HOT.md`: rewrite §1.4 (and the §2 boundary contract) to describe the fence /
  outbox / flush architecture as-built; mark the freeze as superseded.
- `BACKLOG.md`: fence/lifecycle-table GC threshold from measurements; remaining
  sentinel-return / `try?` debt explicitly NOT retired (design doc §6).
- Simulator proxies for the Task 5 Step-5 measurements (see §Residuals for the device pass).

Gate (+ template) plus the design doc's own static checks.

## Final Target Gate (the ONLY successful stop)

Each verified with fresh output (superpowers:verification-before-completion):

1. `ios-gate.sh` fully green on `ios-db-rearch2`; both iOS targets build.
2. Every design-doc checkbox ticked; every acceptance-matrix row (§7) mapped to at least
   one passing named test — produce the mapping table in the final report.
3. Static checks: the Task 5 greps return empty; `git diff --stat 4127d03e -- LimeStudio
   Database` empty; `user_version` grep shows 105; zero KVC references; relay types
   byte-unchanged.
4. Editor-entry behavior proven by test: no probe sleep, no request/receipt write, no
   suspension call, no read-only fallback on either editor; editable with the keyboard
   process absent and with Full Access off.
5. Both upgrade orders converge in the fixtures; the marker-gated flush, epoch-race, and
   hot-rebuild tests pass.
6. Review stack clean over the whole branch diff (/code-review zero unresolved CONFIRMED;
   ponytail-review zero unresolved; ponytail-debt ledger emitted).
7. Docs truthful and consistent: `IOS_DB_COLD_HOT.md`, `#209_ISSUE.md`, `BACKLOG.md`,
   design-doc checkboxes, this flight log.
8. superpowers:finishing-a-development-branch — branch merge-ready, per-task commits
   intact, no co-author trailers, pushed; report ends with the §Residuals checklist for
   the maintainer. Do not open, close, or merge any PR.

Failure at any item → identify the owning iteration, re-enter it through its gate, re-run
the Final Gate. Loop until green. No other stop conditions.

## Residuals — pre-declared maintainer-only items (not campaign blocks)

1. **On-device measurements** (design doc Task 5 Step 5) — editor time-to-first-editable-row,
   baseline comparison time, flush timings, fence-growth numbers on an older physical
   iPhone. The campaign records simulator proxies and leaves the device pass + final
   `BACKLOG.md` numbers to Jeremy.
2. **Exact-final-SHA Xcode Cloud test + archive run** — requires the maintainer's Xcode
   Cloud trigger (#209 lesson: no green claim without it; the campaign must state plainly
   that its evidence is local/simulator until this runs).
3. **PR disposition** — whether `ios-db-rearch2` becomes a new PR and what happens to draft
   PR #221.
4. **Affected-device Related-Phrase Management confirmation** — the original reporter
   scenario, on hardware, FA on and off.

## Flight log

| Iter | Commit | Result / notes |
| --- | --- | --- |
| I0 | 693eb397 | Baseline GREEN: first native validation of v7 head — 1134 passed / 9 skipped / 0 failed (1049s). 613d1b0e compiles+passes, no revert needed. Deviations: .claude/** is gitignored (gate script + audit dump stay local-only); destination needs OS=18.6 pin (iPhone 16 sim absent on OS 26.5). |
| I1 | (this) | Task 1 GREEN. Codex r1 wrote RED tests, honestly blocked (sandbox can't run xcodebuild) → workflow split: Codex codes, orchestrator builds. RED observed (missing postFinishInput(completion:)); Codex r2 implemented §3.1+§4.1+§4.5; orchestrator fixed one test type error (LimeRecord.id is String). Focused GREEN + full suite GREEN. G5 findings: none; Codex additionally removed two pre-existing try? swallows and fixed relatedScore cache poisoning on rollback. |
| I2 | (this) | Task 2 GREEN. Codex wrote tests+impl in one pass (sandbox split); orchestrator RED via stash (valid missing-API RED) then GREEN. Orchestrator fixed 2 compile errors (`try` right of OR-operator `\|\|`; async `queue.write` needs await); Codex fix-round root-caused 3 assertion failures to shared /tmp/cold.limedb fixture pollution (per-test dirs + publisher-replaces-snapshot regression added; production unchanged). Full suite: 1 expected regression — IntegrationTestBackupRestore cloud-reimport asserts app-side restore that §3.3 relocates keyboard-side; XCTSkip'd with MANDATORY I3 re-enable through the intent/reconcile path (in t3 brief). View-level publish-on-exit wiring carried to T4 brief (views are T4 files). Full suite GREEN after skip. |
| I3 | (this) | Task 3 GREEN. Codex delivered fence-window reconcile, revision-ordered lifecycle intents (<table>_user reuse + outbox journaling at install revision), marker+epoch-validated flush with correct ack semantics, §4.6 rebuild, and re-enabled the T2-skipped integration test end-to-end through intent→reconcile→flush. Debug saga: one test failed with misleading Cocoa remove-errors; Codex r2 fixed sidecar handling (real hardening, wrong root cause); orchestrator instrumentation found the true cause — VACUUM INTO cannot create the missing hot/ parent dir (SQLite error 14) — fixed with createDirectory before vacuum. Focused + full suite GREEN. |
| I4 | (this) | Task 4 GREEN over 3 Codex rounds. r1: dismiss flush wiring, editor gating removal, scoped baseline, legacy transition (Step 2 honestly left open). r2: registerIM production bug found via test collision (created unfenced rev — now metadata-publish only); post-transition no-op test properly re-scoped; full Step-2 upgrade fixture matrix both orders. r3: fixture-construction root cause (independent live/snapshot builds diverged on seeded tables — now publish-then-diverge), steady-state generation tests pre-marked, fresh-install guard added (no snapshot → marker without fences + new fixture). Orchestrator fixed 1 try-in-OR-operator compile error. Focused + full suite GREEN. Publication triggers verified: both views' onDisappear → controller → DBServer; AppDelegate background → DBServer. |
| I5 | (this) | Task 5 GREEN. Codex deleted the #209 ownership/handshake machinery (suspension, EditorRefreshFileLock/SessionGate, editor request/receipt, editor probe+1.5s delay, attached harvest, read-only-on-failure) + its contract tests; KeyboardFlushLock preserved; backup-flow probe in DBManagerView correctly retained (backup handshake kept by design). Docs rewritten: IOS_DB_COLD_HOT.md §1.4/§2 now describe as-built fences/outbox/flush, freeze superseded, stale 104 refs corrected to 105; #209_ISSUE.md appended (history intact); BACKLOG.md GC-threshold entry (simulator-only, device pending). Orchestrator: static greps empty, full suite 1163 passed / 0 failed / 5 skipped (was 9 skipped at I0 — 4 pre-existing skips re-enabled along the way), both targets BUILD SUCCEEDED. Step-5 device measurements = pre-declared maintainer residual; simulator proxies are the timed XCTests cited in t5 report. |
| Final Gate | (this) | ALL ITEMS GREEN except pre-declared maintainer residuals. Fresh evidence this session: full suite 1163/0 (5 skipped, pre-existing), both targets build, all static greps empty, LimeStudio+Database byte-identical to 4127d03e, user_version 105, relay types untouched, zero KVC, all design-doc checkboxes ticked. Branch pushed. Residuals for maintainer: on-device measurements, exact-SHA Xcode Cloud test+archive, PR disposition (#221 untouched per plan), reporter-scenario device confirmation. |
| A1 | (this) | Post-campaign amendment (maintainer decision after WJIP17 verification): restore editor mutation gate on editingCapability == .live to prevent stale-score edits superseding undelivered learning. UI-only rebind of canEdit in both editor views + score masking + unlock hint; entry stays immediate (no probe/handshake resurrection); sync protocol unchanged. Docs amended first (design doc Amendment A1, IOS_DB_COLD_HOT.md §1.4 wording), then code + tests. Focused + full suite GREEN; no test constructs the views directly so the new EnvironmentObject is safe; add-buttons were already canEdit-gated (skeleton survived T4). Deployed to WJIP17. |
| A2 | (this) | Second post-campaign amendment (maintainer decision): A1's unlock evidence (relay answer) races the appearance flush, so a long outbox could unlock editing on stale scores; rejection-on-flush is correct but the edit decision was already stale-informed. Gate becomes live ∧ outbox drained: keyboard reports `pend=N` in the relay payload (additive field; -1 on count failure), `RelayActiveState.editingCapability` requires `pendingSyncCount == 0` (nil fail-safe read-only), `LimeSettingsView` re-probes boundedly while pending, editors show 同步中 and reload on unlock. Widens three Amber items by explicit maintainer authorization (payload codec additive field, `RelayActiveState`, `LimeSettingsView`); relay timing/Setup detection untouched. Docs amended first (design doc Amendment A2), then code + tests + full gate + WJIP17 deploy. |
| A2.1 | (this) | Amendment acceptance matrix added as design doc §7.1 (maintainer request: the A1/A2 gate races can't be staged on a physical device, so tests are the proof). Every A1/A2 row now maps to a named passing LimeTests test, closing the gaps with: `TableSyncEngineTest.testFlushDrainsPendingLearningCountToZero` (FA-on flush → count 0 = unlock condition), `testFlushWithoutFullAccessLeavesPendingLearningCount` (FA-off fail-safe), and `EditorSyncGateSourceTest` (source-contract pins for the keyboard answer path, LimeSettingsView bounded re-probe, and both views' gate/syncing-state/reload-on-unlock wiring — same pattern as `EditorPublishSourceTest`). Focused + full suite GREEN. |
| A3 | (this) | **PR #223 merge-review blocker fixed** (found by exact-head review at 9dd72807, independently confirmed): production keyboard datasources were opened via `LimeDB(path:)` with `tracksHotLearning` defaulting to false — `SharedDatabase.openDatasource()` and every reopen/rebind path — so normal keyboard learning updated hot rows but NEVER journaled `learn_outbox`; cold never received learning while the A2 gate read a truthful-but-vacuous `pend=0`. Task 1/A2 tests missed it because every journaling fixture constructed `LimeDB(..., tracksHotLearning: true)` directly. Fix: `SharedDatabase` derives the role once (`SyncDatabaseLocator.isKeyboardExtension()`, injectable for tests) and `makeLimeDB(path:)` is now the single constructor for ALL live-datasource opens (initial, reopen, backup/restore rebinds). Regression through the production seam: `DBServerTest.testKeyboardRoleRuntimeJournalsLearningThroughProductionSeam` (DBServer → prepareKeyboardRuntimeDatabase → SearchServer learning → postFinishInput → learn_outbox row asserted) + `testAppRoleRuntimeDoesNotCreateLearnOutbox` (cold-role inverse). Also fixed: the Linux gate `scripts/test_issue_209_ios_editor_refresh_lifecycle.py` (3/10 red — still pinned deleted `EditorRefreshFileLock`/`activeAccesses`) now pins the rearch2 surfaces (`KeyboardFlushLock` bounded lock, hot-role journal wiring, retired-surface removal) — 10/10. Lesson recorded: fixture-level flags must always have a production-seam twin test. |
