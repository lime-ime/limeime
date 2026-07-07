# iOS Table Editor — Full Access gating & learned-data sync

How the iOS 字根表 / 相關字 record editors decide **read-only vs. editable**, and how they show the
keyboard's **real (learned) data**. This is the design of record for the editor surface; it builds on
the cold/hot database model in `IOS_DB_COLD_HOT.md` (esp. **§1.4 table-editor sync**), the transport
in `IOS_FULL_ACCESS.md`, and the setup/relay model in `LIME_SETTINGS.md`.

The editors covered here: `RecordListView` (per-IM 字根表), `RelatedListView` (相關字), and the
related-clear control in `IMDetailView`.

---

## 1. Data model: `score` vs `basescore`

Every record row carries two integers:

| Column | Owner | Meaning |
| --- | --- | --- |
| `basescore` | **cold** (app is truth) | Dictionary base weight, set on import. The app already has it. |
| `score` | **hot** (keyboard is truth) | The **learned** value. Seeded `0`; the keyboard bumps it (`UPDATE … SET score = score + 1`) as the user types. Ranking uses `score + basescore`. |

Normal tables key on `(code, word)` (columns `_id, code, word, score, basescore, code3r`); the
`related` table keys on `(pword, cword)` (columns `_id, pword, cword, basescore, score`).

The editor displays **`score`**. Because the app owns structure + `basescore` while the keyboard owns
`score` **and may add rows as it learns** (it is add/update-only), what the app lacks is the
keyboard's learned delta — changed scores **and** any rows the keyboard added. It must never display a
`score` it cannot know is real: unlearned rows are `0`, learned values arrive from the keyboard via
the §3 harvest.

---

## 2. Editing-capability gate (trust boundary)

Editing tables with real data requires Full Access **and** LIME to be the active keyboard this
session (the keyboard is the only writer of the truth). This is a trust boundary — it fails **safe**.

```swift
enum RecordEditingCapability { case readOnly, live }

static func resolve(faState:, activeThisSession: Bool = false, forceLive:) -> RecordEditingCapability {
    forceLive || (faState == .confirmedOn && activeThisSession) ? .live : .readOnly
}
```

`activeThisSession` **defaults to `false`**. Any caller that omits live active-keyboard proof gets
`.readOnly`, never `.live` — a stale ON heartbeat (valid up to 120 s after Full Access is revoked)
can no longer unlock editing.

### Single authority: `RelayActiveState`

The editors do **not** run their own active/FA probes. One shared observable, fed by the **root relay**
in `LimeSettingsView`, is the single source of truth:

```swift
final class RelayActiveState: ObservableObject {
    @Published var isActive: Bool?        // nil = not yet determined
    @Published var hasFullAccess: Bool?
    var editingCapability: RecordEditingCapability {
        (isActive == true && hasFullAccess == true) ? .live : .readOnly   // forceLive in DEBUG
    }
    func markActive(fullAccess: Bool)                 // isActive = true;  hasFullAccess = fullAccess
    func markNotActive(fullAccess: Bool = false)      // isActive = false; preserves fresh FA heartbeat when known
}
```

- The root relay pings LIME on app foreground. A **payload** → `markActive(fullAccess: payload.faOn)`;
  a **timeout** (no LIME response) → `markNotActive(fullAccess: rootFullAccessConfirmedOn)`.
- `RecordListView` / `RelatedListView` / `IMDetailView` read it via `@EnvironmentObject` and derive
  `canEdit = editingCapability == .live`. They no longer own a probe / `FAPingObserver` / poll timer
  for **FA-and-active detection** (~330 lines removed vs. the per-view design). The hidden probe field
  the editor still focuses (§3) is only to **summon** LIME for the harvest — it drives no state, and
  the gate above stays the single authority.
- The editors read the result the relay established at foreground; active/FA state cannot change
  without leaving the app, and returning re-fires the relay — so it stays correct.

### Read-only behaviour (Full Access off, or LIME not active)

`editingCapability == .readOnly`: scores render as `—`, the add / edit / delete controls are
`.disabled`, a lock glyph shows, and the status line shows state-specific unlock copy:

| State | Unlock copy |
| --- | --- |
| Full Access off, LIME active | `開啟完整取用以顯示實際分數及啓用碼表編輯功能` |
| Full Access confirmed, LIME not active | `將目前鍵盤切換為萊姆輸入法以顯示實際分數及開啓編輯功能` |
| Full Access missing/unknown, LIME not active/unknown | `開啟完整取用並將鍵盤切換至萊姆輸入法以顯示實際分數及開啓編輯功能` |

Nothing hangs — there is no probe or round-trip in this path.

---

## 3. Real data (Full Access on): the editor-refresh handshake (hot → cold, DB operation)

Two earlier mechanisms are abandoned. A whole-DB `VACUUM` round-trip moved thousands of rows the app
already had and — because **no keyboard runs inside the app's editor** — went unserviced → 15 s
timeout → read-only. Then a `learned-scores.json` file of `score != 0` rows: proactive but
**scores-only**, so it could not carry rows the keyboard *added*. Both are gone.

The current path is a **per-table DB harvest**: the app summons LIME and asks it to copy its
freshly-learned rows straight into the **live cold** table, so the editor reads real data — new rows
included, not just scores. It needs **Full Access ON + LIME active** (the keyboard is the only writer
of hot, and writing cold is a keyboard→App-Group write). See `IOS_DB_COLD_HOT.md` §1.4.

### Entry — summon + harvest (`refreshHotSnapshotIfNeeded` → `refreshTableFromKeyboard`)

On editor appear the view **always attempts** the harvest — it does **not** gate on
`editingCapability == .live`, because `.live` is only established after the keyboard relays and the
relay needs the keyboard summoned first (a chicken-and-egg that would otherwise strand the editor
read-only).

1. **Summon.** Focus a hidden probe `TextField` (`probeFocused`) so the LIME extension is actually
   running — it can only harvest hot while presented — and wait briefly for it to come up. The editor
   shows `同步中...` + `clock.arrow.circlepath` and grays the rows (`isRefreshingHotSnapshot`;
   `canEdit = !isRefreshingHotSnapshot && editingCapability == .live`).
2. **Request.** Write `outbox/editor.refresh.request.json` `{requestUUID, table, expiresAt}`, ring
   `tables.updated`, then **poll** for `outbox/editor.refresh.receipt.json` whose `requestUUID`
   matches, up to `editorRefreshPollTimeout`.
3. **Keyboard harvest (FA-on, presented).** `processEditorRefreshRequestIfNeeded` validates the TTL /
   safe table name / live-cold presence, then `harvestEditorRefresh` **ATTACHes the live cold DB** and,
   in one write, computes the **dirty keys** — hot rows whose key (`(code, word)`, or `(pword, cword)`
   for `related`) is **absent from cold** (a keyboard-added row) **or** whose `score` differs (learned)
   — into a temp table, then **DELETE + INSERT** exactly those rows into cold. Cold's table now matches
   hot for the changed rows; everything else is untouched. It writes
   `editor.refresh.receipt.json {requestUUID, table, status: done|failed, error, at}` and rings
   `import.done` / `import.failed`.
4. **Apply — no whole-table copy, no poll-forever.** On a `done` receipt the view **reloads cold** (now
   current) and unlocks; on `failed` **or timeout** it sets `hotRefreshFailed` → read-only
   (`即時資料更新逾時，已切換為唯讀`). Full Access **off** or LIME not active → the keyboard's App-Group
   write is dropped / no keyboard answers → the poll times out → read-only. Nothing hangs.

### Edit — straight onto cold

Every add / edit / delete lands in cold's table via the normal `DBServer` CRUD. **No op log, no
per-edit publish** — the state of cold *is* the record (`IOS_DB_COLD_HOT.md` §1.4).

### Close — publish + cold → hot reconcile (`publishEditorCloseIfNeeded` → `publishEditorChanges`)

On `.onDisappear` **and** on `scenePhase == .background` (commit-on-background, so no pending edit
straddles a keyboard-learning window), the view calls `publishEditorChanges(stem)` →
`DBServer.markTableChangedAndPublish(stem)`: **bump the table's `rev`**, publish cold (VACUUM →
snapshot + generation bump + `im.json`), ring `tables.updated`. The keyboard's next scan sees the
moved `rev` and **reconciles cold → hot** for that table (the rev-gated incremental copy), so the
app's edits — including deletions — reach hot with no op log. Idempotent.

The **backup** path (`requestKeyboardBackup`, the full-DB export) is independent and unchanged — this
covers only the per-table editor refresh.

---

## 4. Contract & files

| Piece | Location |
| --- | --- |
| `EditorRefreshRequest {requestUUID, table, expiresAt}`, `EditorRefreshReceipt {…, status: done/failed}`, `SyncPaths.editorRefreshRequest` / `editorRefreshReceipt` (`outbox/editor.refresh.request.json` / `…receipt.json`), `RelayActiveState`, `RecordEditingCapability` | `Shared/Database/SyncContract.swift` |
| `refreshTableFromKeyboard(stem:)` (post request, poll for the matching `done` receipt), `publishEditorChanges(stem:)` (close) | `LimeSettings/Controllers/SetupImController.swift` |
| `processEditorRefreshRequestIfNeeded`, `harvestEditorRefresh` (hot→cold dirty-key delta into live cold), `writeEditorRefreshReceipt`, `editorRefreshKeyColumns`; the cold→hot **close-reconcile** = the rev-gated `applyIncremental` copy | `Shared/Database/TableSyncEngine.swift` |
| `markTableChangedAndPublish(stem:)` (bump `rev` + publish cold) | `Shared/Database/DBServer.swift` |
| Editors — probe summon, `同步中`, `refreshHotSnapshotIfNeeded`, `publishEditorCloseIfNeeded`, `scenePhase == .background` commit, `@EnvironmentObject RelayActiveState` | `LimeSettings/Views/RecordListView.swift`, `RelatedListView.swift`, `IMDetailView.swift` |
| Root relay → `markActive` / `markNotActive` | `LimeSettings/LimeSettingsView.swift` |

Harvest keys: `(code, word)` for mapping tables, `(pword, cword)` for `related` (`editorRefreshKeyColumns`).

## 5. Behaviour summary

| State | Editor |
| --- | --- |
| Full Access **off** (or LIME not active) | Read-only. Scores `—`, edits disabled, unlock hint. Harvest times out → read-only; no hang. |
| Full Access **on**, LIME active | Summons LIME, harvests hot→cold (`同步中...`), then editable with real learned data (keyboard-added rows **and** scores). Edits publish back cold→hot on close/background. |
| Harvest failed / timed out | Read-only (`即時資料更新逾時`); edits disabled until the editor is reopened. |

## 6. Tests

- `TableSyncEngineTest` — `testEditorRefreshHarvestsNewAndScoreChangedRowsIntoLiveCold`,
  `testEditorRefreshHarvestsRelatedRowsByParentChildKey` (harvest keyed by `(pword, cword)`),
  `testCloseReconcileAppliesColdAddEditAndDeleteToHot`,
  `testEditorRefreshThenCloseReconcileRoundTripsLearningAndAppEdits` (full round-trip).
- `SetupImControllerTest` — `testRefreshTableFromKeyboardWaitsForMatchingDoneReceipt`,
  `testRefreshTableFromKeyboardTimesOutAndRemovesRequest` (timeout cleans up the request).
- `RecordEditingCapabilityTest` — fail-safe default (`resolve(faState:)` → read-only; `.live` only
  when `confirmedOn && activeThisSession`); `RelayActiveStateTest` (nil / `fullAccess:false` /
  `markNotActive` → read-only; `fullAccess:true` → live); `EditorRefreshViewSourceTest` pins the
  editors' probe / `同步中` / close-publish source.
