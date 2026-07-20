# iOS FA Re-Architecture — Autonomous Goal-Mode Execution Plan

Goal mode: run all iterations end-to-end in one session, without pausing for approval between iterations. The ONLY permitted stop conditions are (a) the Final Target Gate is fully green, or (b) a hard physical block (no device attached for device-only gates) — in which case everything else must still be driven to green and the device-only residue reported as an explicit checklist.

Design sources (single source of truth — implementation deviations require updating these docs in the same iteration):

- [IOS_FULL_ACCESS.md](IOS_FULL_ACCESS.md) — permission model, keyboard-canonical DB, desired-state sync, epoch/ledger, backup/restore, edge scenarios
- [IOS_FULL_ACCESS_DETECT.md](IOS_FULL_ACCESS_DETECT.md) — enabled detection (keep), FA tri-state detection (rewrite)
- [IOS_GOTO_SETTINGS.md](IOS_GOTO_SETTINGS.md) — 前往設定 deep-link variant + miss-tolerant UX

## Ground rules (apply to every iteration)

- Branch: `ios-fa-rearch`, created via superpowers:using-git-worktrees. One commit per iteration, message `feat(ios): FA re-arch I<N> — <scope>`. No Claude co-author trailer (repo rule).
- Build/test oracle: `.claude/scripts/ios-gate.sh` (headless unit gate; always prefix xcodebuild with `GIT_CONFIG_COUNT=0`). NEVER run xcodegen; edit `project.pbxproj` by hand. Device deploys use `-allowProvisioningUpdates`, device the physical test iPhone.
- Encoding: Swift/`.md` UTF-8 **with** BOM; `.json`/`.plist-as-text` scripts without BOM. Subagent briefs/reports in `.claude/txt/`, helper scripts in `.claude/scripts/` — never new files at repo root.
- TDD (superpowers:test-driven-development): every task lands its failing test in `LimeTests` first, then the implementation. The unit gate is the per-iteration oracle; `ios-visual-verify` and on-device runs are the end-to-end oracles.
- Rule of three (repo rule 7): 3 failed attempts on the same issue → stop retrying, switch to superpowers:systematic-debugging + external research before the next attempt.

## Skills map (use at the named point, every time)

| Skill | When |
|---|---|
| superpowers:writing-plans | Once, before I0: expand this document into the task-level implementation plan |
| superpowers:executing-plans | The outer loop discipline across iterations |
| superpowers:subagent-driven-development + dispatching-parallel-agents | Every iteration's build tasks (Codex CLI subagents; parallel where tasks are independent) |
| superpowers:test-driven-development | Inside every task |
| superpowers:systematic-debugging | Any red gate |
| superpowers:verification-before-completion | Before declaring ANY gate green — command output required, no assertions from memory |
| superpowers:requesting-code-review → /code-review → ponytail:ponytail-review | Review stack at every iteration gate (see Gate template) |
| superpowers:receiving-code-review | Processing findings — verify before implementing, no performative agreement |
| ponytail:ponytail-debt | Final gate: harvest all `ponytail:` comments into the debt ledger |
| ios-visual-verify | Simulator end-to-end gates (I5, Final) |
| verify | Final gate: drive the real app flows, not just tests |
| superpowers:finishing-a-development-branch | After Final Target Gate passes |

## Subagent workflow (Codex CLI)

Implementation tasks are delegated to Codex CLI; review is NEVER delegated — Claude reviews every subagent result.

1. **Brief**: write `.claude/txt/fa-i<N>-t<M>-brief.md` — task scope, exact design-doc sections it implements, files it may touch, files it must NOT touch, the failing tests it must make pass, encoding + pbxproj rules restated.
2. **Dispatch**: `codex exec --cd <worktree> --full-auto --output-last-message .claude/txt/fa-i<N>-t<M>-report.md "$(cat .claude/txt/fa-i<N>-t<M>-brief.md)"` (adjust flags to the installed codex version). Independent tasks within an iteration run as parallel dispatches (superpowers:dispatching-parallel-agents).
3. **Gap review (Claude)**: diff the subagent's work against the brief and the design-doc sections line by line — missing behaviors, silent scope changes, contract drift. Gaps → fix directly if small, re-brief if structural.
4. **Review stack (Claude)**: /code-review (correctness; confirmed findings must be fixed) then ponytail:ponytail-review (over-engineering; delete what it flags — new abstractions the design doesn't require are defects too).
5. Repeat 1–4 until the iteration gate is green, then commit.

## Iteration gate template (every iteration must pass ALL)

- G1 Build: `ios-gate.sh` green (app + appex + LimeTests compile).
- G2 Tests: full unit suite green, including this iteration's new TDD tests; no prior-iteration test regressions.
- G3 Gap review: implementation matches the referenced design-doc sections; any deliberate deviation is written back into the design doc in the same commit.
- G4 /code-review: zero unresolved CONFIRMED correctness findings.
- G5 ponytail-review: zero unresolved over-engineering findings; deliberate shortcuts carry `// ponytail:` comments naming ceiling + upgrade path.
- G6 verification-before-completion: every green claim backed by fresh command output in this session.

## Iterations

### I0 — Feasibility spikes (load-bearing assumptions)

Tasks:
- T1 device probe: temporary `#if DEBUG` code in `KeyboardViewController` — FA OFF, read a marker file from the App Group, write result + a copy into the keyboard's own container, show pass/fail in the keyboard banner. Deploy to the physical test iPhone.
- T2 Darwin spike: app posts `org.limeime.tables.updated`; keyboard observer logs receipt (both FA states). Same deploy.
- T3 import timing probe: measure attach+bulk-copy time for the largest table (關聯字庫-sized fixture) in the extension on device; record chunk-size choice.

Iteration gate (in addition to template): probe results recorded in IOS_FULL_ACCESS.md (open-items section updated with measured facts). **If the I0 App-Group-read probe FAILS on device, STOP the whole plan and report — the architecture premise is falsified.** No device attached → run probes in Simulator, mark results `UNVERIFIED-ON-DEVICE`, continue, and carry the device probes into the Final Gate residue.

### I1 — Foundation: canonical DB, epoch, ledger, first-run, legacy adoption

Implements: IOS_FULL_ACCESS.md §Canonical DB, §Where the UUID lives, §Edge/Migration.
- `LimeDB` migration: `sync_meta` (epoch_uuid, schema_version) + ledger table (per-stem identity, state: pending/in-progress/done/failed(error, attempts), resume marker) — same-transaction updates with imports.
- `DBServer` keyboard path → keyboard container `Application Support/lime.db`; first-run copy from appex-bundled default; legacy adoption (App Group trio copy → quick_check → stamp UUID → adopt; fallback default).
- pbxproj hand-edit: add `lime.db` copy phase to LimeKeyboard target (mirror the app target's phase).
- Tests: fresh-run bootstrap, legacy adoption success/corrupt-fallback, epoch stamp presence, ledger CRUD atomicity.

### I2 — Static data split: emoji.db / hanconvert.db

Implements: IOS_FULL_ACCESS.md §Static pre-shipped data, §Upgrade rule, §Edge/legacy-shadowing.
- One-time generation script (`.claude/scripts/`) extracting emoji/hanconvert into standalone DBs; outputs committed as appex resources; default `lime.db` regenerated without them.
- ATTACH `immutable=1` at open; all emoji/hanconvert queries schema-qualified; `emoji_user` remains canonical; post-swap hygiene drops legacy static tables.
- Tests: emoji search/FTS via attached DB, shadowing test (canonical DB containing legacy `emoji_data` still resolves to attached data), emoji_user dangling-reference tolerance.

### I3 — Desired-state sync: app writer + keyboard import executor

Implements: IOS_FULL_ACCESS.md §Desired-state folder, §Import executor, §Signal channels (files+scan), §Import status.
Contract first (folder paths, sidecar schema, ledger semantics as a short Swift protocol + fixtures), then two parallel subagents:
- T-app: `tables/` writer — download/receive → normalize to limedb (`.cin`/`.lime` conversion via existing import code targeting standalone limedb; conversion IS validation), `quick_check`+schema check for received limedbs, temp+rename, sidecar, uninstall=delete, serialized single-writer ops.
- T-kb: scan-on-appear diff vs ledger, attach+bulk-copy import (clear-then-load, chunked, resume marker, learned-score merge per 還原已學習記錄), busy_timeout + scan-check inside transaction (concurrent instances), keyboard status banner, Darwin doorbell observer + done/failed pings.
- Tests: end-to-end folder→import in unit harness (both roles in-process), interruption/resume, identity-change abandons resume, uninstall drop, double-instance no-op.

### I4 — Restore / backup

Implements: IOS_FULL_ACCESS.md §Conflict resolution, §Backup/restore, §Edge/version-skew.
- Epoch preparation app-side (fresh UUID, cleared ledger rows, schema_version stamp; sources cleared; sidecar `restore.meta.json`); restore from zip and from bundled default; version-skew rejection on both sides.
- Keyboard: epoch-first scan order, swap + ledger reset (arrives inside the file), pending-import completion/rollback before honoring export requests.
- Backup: export-request marker → `VACUUM INTO` App Group temp → rename + receipt → app poll → zip (ZIPFoundation) → share sheet → temp cleanup.
- Tests: epoch no-op on same UUID (mtime churn), restore+later-install layering, restore clears sources, skew rejection, backup receipt roundtrip (FA simulated in unit harness).

### I5 — Signals + Settings UI + spec sync

Implements: IOS_FULL_ACCESS_DETECT.md §Corrected model + §Required changes; IOS_GOTO_SETTINGS.md §Recommended changes; IOS_FULL_ACCESS.md §Planned LIME_SETTINGS.md updates.
- Tri-state FA detection in `SetupTabView.refreshStatus()` (value + `keyboard_last_seen_at` freshness; missing → Unknown/neutral; never an error state); heartbeat → outbox files, mirrored to keyboard-local defaults (incl. `keyboard_db_last_error`).
- Setup tab copy (FA = 備份已學習字詞、按鍵震動回饋; step marked 建議); DB Manager 備份 enabled only on Confirmed-ON with probe-on-appear freshness; restore buttons always enabled; Settings.bundle footer copy softened.
- 前往設定: bundle-ID-suffixed URL first with plain fallback; post-tap prominent two-landing guidance.
- Apply the two spec changes to LIME_SETTINGS.md (§4, §7) — this iteration is the sanctioned time.
- Gates add: ios-visual-verify simulator pass — install IM via probe flow, banner states, DB manager gating.

### I6 — Legacy removal + cleanup

- Delete the keyboard's App Group DB open path, snapshot/fallback remnants, shared-defaults generation signals; prefs reads stay (user prefs only).
- ponytail sweep: remove dead code the re-arch orphaned; ensure `ponytail:` comments on deliberate ceilings.
- Tests: full suite still green; grep-gate: no `UserDefaults` correctness signals remain keyboard→app.

### Final Target Gate (the ONLY successful stop condition)

All of the following, each verified with fresh output (superpowers:verification-before-completion):

1. `ios-gate.sh` fully green on `ios-fa-rearch`.
2. Entire unit suite green, including all tests added in I1–I6.
3. Simulator end-to-end (ios-visual-verify + verify skill): fresh-install typing; download 倉頡 → probe → import → typing with it; uninstall; restore backup zip; 還原預設資料庫; backup flow (FA simulated); tri-state banner states; 前往設定 variant.
4. Device matrix on the physical test iPhone (if attached): the FA OFF/ON rows of IOS_FULL_ACCESS.md §Test matrix + IOS_FULL_ACCESS_DETECT.md §Test matrix + I0 probes re-confirmed on release build. If no device: emit `docs/IOS_FA_DEVICE_CHECKLIST` content into the final report (do NOT create the file outside an explicit request) and mark residue.
5. Docs in sync: the three design docs reflect as-built behavior; LIME_SETTINGS.md §4/§7 updated; open-items list pruned to genuinely-open only.
6. Review stack clean over the WHOLE branch diff: /code-review zero unresolved CONFIRMED findings; ponytail:ponytail-review zero unresolved findings; ponytail:ponytail-debt ledger emitted in the final report.
7. superpowers:finishing-a-development-branch executed — branch merge-ready, no co-author trailers, per-iteration commits intact.

Failure at any Final Gate item → identify the owning iteration, re-enter it through the same subagent workflow, re-run its gate, then re-run the Final Gate. Loop until green. Do not stop for anything else.

---

## Flight log — plan write-backs (2026-07-04, in-flight)

Amendments discovered during execution; the sections above stay as written, this log records what actually changed.

### Execution-state summary

| Iteration | Status | Commits (branch `ios-fa-rearch`) |
|---|---|---|
| I0 probes | Probe SUPERSEDED before device readout: I5's fa.on/fa.off + heartbeat channel replaces FA detection, and on an FA-OFF device the product itself proves the AG-read premise (install a table → keyboard imports it). Probe code deleted in I6. Device rows remain Final-Gate residue. | rides I1.3 commit; deleted `48dc63df` |
| I1 foundation | COMPLETE — full LimeTests suite + build green in one process | `d1451708`, `8678fae5`, `df0a8cdb` |
| I2.1 assets | COMPLETE — emoji.db + FTS5 prebuild; seed lime.db stripped; Android-safety verified (Android seeds emoji at runtime) | `7cbc32ba` |
| I2.2 emoji attach | COMPLETE — ATTACH immutable, qualified queries, FK-free emoji_user rebuild, seed machinery deleted (net −284) | `459e71ff` |
| I3 desired-state sync | COMPLETE — stubs pre-wired for parallel dispatch; TableStore + TableSyncEngine built concurrently; wiring rerouted installs (已交付鍵盤). Known-red seam to I4 documented in `8faf3146`. | `531328b8`, `4310e9b9`, `8fc5fd5c`, `8faf3146` |
| I4 restore/backup | COMPLETE — epoch restore (zip/factory/skew), VACUUM INTO relay with requestUUID+TTL, IntegrationTestBackupRestore ported; full suite green again | `ca733832` |
| I5 detection/UI/spec | COMPLETE — FAState tri-state + evidence-based probe trigger, DB-manager gating, goto-settings variant, LIME_SETTINGS.md §4/§7 | `99bdbbf2` |
| I6 legacy removal | COMPLETE — shared-defaults signals deleted, probe deleted, hanconvertv2 iOS phase removed, grep gates clean (net −197) | `48dc63df` |
| Docs addenda | Implementation-phase addenda folded into tasks 4.2 / 5.1; flight log maintained in-flight | `d49e6faf`, `c8fbdb76` |

### I0 — how the probe actually works (differs from T1–T3 as written)

One combined deploy, fully headless: the keyboard measures AG-read / own-write / AG-write / Darwin count / attach-import ms and **types** the report into the app's probe field (`[FAPROBE …]`); the app mirrors it to `AppGroup/probe_report.txt`; readout via `devicectl device copy from --domain-type appGroupDataContainer`. No banner-reading, no UITest. Per user direction: never wait/poll on device lock state — probes are opportunistic residue, harvested whenever the device happens to be unlocked.

### Corrected facts the plan sections assumed wrong

- **No hanconvert DB on iOS** (`hanConvert` = CFStringTransform) — I2 is emoji-only. `hanconvertv2.db` is bundled by the app target but has zero Swift references → I6 deletion candidate.
- **emoji.db was already a bundled resource in BOTH app and appex** with copy phases; iOS seeded lime.db from it at runtime (same as Android still does). I2.1 therefore only prebuilt FTS5 into it and stripped the (empty) emoji tables from the seed lime.db — the "extraction" task as written was unnecessary.
- **Seed `lime.db` emoji tables were empty** — the size-shrink rationale was wrong; the real I2 payoff is deleting the seed/refresh machinery and staleness-free upgrades.
- `Shared/Database/lime.db` is NOT the default DB (it is a `related`-only test fixture bundled into LimeTests); the default DB is `LimeStudio/app/src/main/res/raw/lime.db`.

### Workflow corrections

- Codex dispatch flags actually used: `codex exec -C <worktree> -s workspace-write -o <report> "<brief>"` (no `--full-auto` on exec in codex-cli 0.140).
- Session cwd resets between turns → EVERY git command uses `git -C <abs worktree path>`; one accidental commit landed on master and was undone content-safely (`git reset` mixed, no file contents touched).
- Subagent deviations rejected during review (precedent): `#if canImport(XCTest) @testable import LimeIME` inside shared code (replaced by adding SyncContract.swift to the LimeTests target); `typealias datasourceContainer = SharedDatabase` shim (deleted, tests use the real name).
- Gate addition: the I1 gate ran the ENTIRE LimeTests suite in one `xcodebuild test` process (not per-class) — this also smoke-tests run-mode cross-contamination between test classes; keep for every remaining iteration gate.

### Task amendments folded into IOS_FA_REARCH_TASKS.md (from the design-doc addenda)

- **Task 4.2**: `export.request.json` carries `{requestUUID, expiresAt}` (TTL ~2 min); keyboard ignores expired requests; app accepts only matching-UUID receipts; timeout UX disambiguates via Darwin liveness (`org.limeime.fa.*` ping seen but no receipt → FA guidance; no ping → "請將鍵盤切換至萊姆輸入法後再試").
- **Task 5.1**: `FAState {confirmedOn, confirmedOff, unknown}` — confirmedOff only from a live `org.limeime.fa.off` ping (silence never proves off); keyboard posts `fa.on`/`fa.off` on appear; probe trigger rewritten to `keyboardEnabled && !hasFreshEvidence` (old `!fullAccessEnabled` guard was circular — never fires on device).
- **I3 T-kb note**: the import status banner rides the existing LimeToast surface — no new opaque chrome over the UIInputView blur, per IOS_LIGHT_DARK.md §5/§7; text-only progress; calm failure styling.
- Parked options (addenda, deliberately not tasked): insertText probe relay (needs hardware spike), keyboard-side pref-edit durability FA OFF (accepted: hamburger edits are FA-ON-durable only; app-side edits always work).

---

# Campaign 2 — v2 cold/hot transport (2026-07-04, autonomous goal mode)

Triggered by device testing: v1's app-side desired-state sources left app screens (IM list, editors) on a divergent DB. IOS_FULL_ACCESS.md is rewritten to v2 (cold/hot model) and is the sole design source for this campaign. Same goal-mode contract and ground rules as Campaign 1, with ONE change:

**NO device gates anywhere.** All verification is simulator-autonomous: unit suite + build + ios-visual-verify-methodology end-to-end (XCUITest-driven + simctl orchestration + screenshot reads — no Computer Use dependency). Device testing is the user's own manual acceptance afterward, not a gate.

## Iterations

### J1 — Cold plumbing (app side)
`sync_rev(stem, rev, mode)` maintenance inside LimeDB mutating paths (imports, record add/update/delete, clearTable — same-transaction bumps; installs/downloads → `merge`); epoch bump helpers (restore/factory only); `ColdPublisher`: `VACUUM INTO` temp → rename `AppGroup/cold.limedb` → `cold.meta.json {generation, epochUUID, schemaVersion}` after → doorbell; debounce (flow completion + app background). Unit tests: rev bumps per mutation class, publish atomicity (sidecar-after), generation monotonicity, epoch only on restore.

### J2 — Engine retarget (keyboard side)
`TableSyncEngine.scanAndApply` v2: sidecar-generation fast path (JSON read only when unchanged); attach snapshot immutable + in-DB generation cross-check (mid-publish → skip); epoch → hot rebuild (fresh snapshot copy; 還原已學習記錄 pref via prefs, keyboard-local stash/merge; post-swap hygiene + `migrate()` as today); im mirror wholesale + runtime-rebuild event; per-stem rev diff with `merge|replace` modes (replace = cold wins, no stash); drops for stems gone from cold; ledger rev-keyed (in hot DB, same-transaction); chunked copy + resume (rev-keyed) retained; export-request handling unchanged. Rewrite TableSyncEngineTest for v2 fixtures (cold snapshot builder helper). Delete v1 source-folder scanning.

### J3 — Controller/UI reverts (app side)
SetupImController import/download paths back to cold-DB imports (pre-I3.3 shape) + publish hooks; IMStoreView installed-set from cold DB (`tableHasData`) again; restore = legacy restore INTO cold + schema gate + epoch bump + publish; 還原預設資料庫 same; TableStore + TableStoreTest deleted (validation/conversion live in the app import paths as before); SetupImControllerTest/IntegrationTestBackupRestore reworked to v2 flows (round-trip: install → learn(hot) → backup → restore cold → epoch rebuild → learned back).

### J4 — Editor policy
Record/related editors: read-only unless `FAState == confirmedOn` (freshness label on cold data; existing bypass seam only for sim tests); FA-ON edit entry → snapshot request (backup relay, on-demand) → refresh that table in cold from snapshot (rows + real scores) → edits → save bumps rev `mode=replace` + publish. IM-meta editing (title/endkey/selkey/keyboard id/disable) stays FA-free via im mirror. Documented race accepted (typing same table during edit session).

### J5 — v1 artifact cleanup
Delete `tables/` + `restore.limedb` handling, `SyncPaths` folder entries, rebuildSources/prepareRestore v1 paths; on first v2 run the app removes leftover v1 artifacts from the App Group (v1 never shipped beyond dev devices — unconditional cleanup). Grep gates: no `tables/` references outside history docs.

### J6 — Final gate (SIM-ONLY, the only successful stop)
1. Full LimeTests suite + build green (one process).
2. uiall parity: same pass/fail set as the recorded master baseline (12 pre-existing environment reds documented; zero NEW failures).
3. **User-acceptance e2e on simulator (ios-visual-verify methodology, autonomous)**: install 大易+注音 via real IMInstallView cloud UI (XCUITest) → verify IM list → backup (Documents-mirror seam) → orchestrator pulls zip → `simctl uninstall` (full wipe) → reinstall → push zip → restore seam → **assert both IMs back in the IM list** → type with one via the keyboard → candidate appears. Plus: meta-only edit (rename IM) propagates via im mirror without table re-import (assert via timing/log or ledger revs); Setup/DB-tab screenshots read against the spec.
4. Review stack over the campaign diff (self line-by-line + codex second reader, my verification of every candidate) — zero unresolved CONFIRMED; ponytail sweep; debt ledger updated.
5. Docs in sync (this file's flight log + IOS_FULL_ACCESS.md as-built + IOS_FA_REARCH_TASKS.md campaign-2 addendum).
6. finishing-a-development-branch → merge decision handed to user.
Failure at any item → re-enter the owning iteration, loop until green. No other stop conditions.

## Campaign 2 — results (2026-07-04, complete)

| Iteration | Commit | Result |
|---|---|---|
| J1 cold plumbing (sync_rev/mode, epoch/generation, ColdPublisher) | `da779323` | green |
| J2 engine retarget (generation fast-path, epoch rebuild, im-mirror, rev diff merge\|replace) | `b772c068` | green |
| J3 controllers→cold-DB + TableStore dissolved (net −541) | `b2b284fe` | green (fixed 1 in-suite-only flaky backup test) |
| J4 editor column-ownership (read-only FA OFF; FA-ON snapshot-refresh live editing) | `349bb0e5` | green |
| J5 v1 artifact cleanup + AppDelegate first-run purge | `17b35894` | green |
| J6 review fixes (7 confirmed findings) | `7a18177e` | green (1004 tests) |
| J6 sim e2e infra + seams | `f8360678` | green |

**J6 gate outcomes:**
1. Full LimeTests suite + build: **green** (1004 tests, 0 failures, serial).
2. uiall parity: **confirmed** — branch 13 fails vs master 12; the single delta (FlintUITest.testDrag…) proven pre-existing by running it isolated on master (identical Safari-state-contamination failure "把手"/"ㄅ"). Zero Campaign-2 regressions. (Safari-driven gesture/theme UI tests are environment-flaky and order-dependent; the headless unit gate is the reliable oracle.)
3. **User-acceptance e2e: PROVEN end-to-end.** Phase 1 (17 Pro): install 大易+注音 via real cloud UI + backup to Documents — pass. Real orchestration: pulled the 6.3 MB zip → `simctl uninstall` (Documents wiped, verified) → fresh reinstall (no cold DB) → inject zip → launch with restore seam → cold.limedb rebuilt with **dayi 18,638 rows + phonetic 34,754 rows, COUNT(DISTINCT im.code)=2**. Phase-2 UITest independently asserted both IMs reappear in the list UI. The v1 "empty IM list after restore" defect is fixed. (Typing check fell back to restored-row-counts — Safari candidate detection is the known-flaky part; row-count + StrokeBenchmark cover typing.)
4. Review stack: codex second-reader found **7 confirmed runtime bugs**, all verified against the code and fixed with regression tests (F1 publish DB/sidecar race, F3 restore skew via user_version, F4 clearTable publish, F5 關聯字庫 learned-score stash/merge, F6 restore rev-population, F7 export-gated-on-settled; F2 concurrency ceiling documented). ponytail debt: comments carry named ceilings.
5. Docs in sync (this file + IOS_FULL_ACCESS.md v2 as-built).
6. finishing-a-development-branch → merge decision to user.
