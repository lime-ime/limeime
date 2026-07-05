# iOS Table Editor — Full Access gating & score sync

How the iOS 字根表 / 相關字 record editors decide **read-only vs. editable**, and how they show the
keyboard's **real (learned) scores**. This is the design of record for the editor surface; it builds
on the cold/hot database model in `IOS_FULL_ACCESS.md` and the setup/relay model in `LIME_SETTINGS.md`.

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

The editor displays **`score`**. Because the app owns structure + `basescore` and the keyboard owns
only `score` — and only rows where `score != 0` are learned — the **sole** thing the app lacks is a
small set of learned scores. It must never display a `score` it cannot know is real: unlearned rows
are `0`, learned rows come from the keyboard.

---

## 2. Editing-capability gate (trust boundary)

Editing tables with real data requires Full Access **and** LIME to be the active keyboard this
session (the keyboard is the only writer of the truth). This is a trust boundary — it fails **safe**.

```
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

```
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
  `canEdit = editingCapability == .live`. They no longer own a probe `TextField`, `FAPingObserver`,
  or poll timer (~330 lines removed vs. the per-view design).
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

## 3. Real scores (Full Access on): learned-delta publish

The old mechanism was a **live round-trip**: the app wrote an `export.request.json` and polled for the
keyboard to `VACUUM` its whole hot DB into a snapshot and write a receipt. But **no keyboard runs
inside the app's editor**, so the request went unserviced → 15 s timeout → "更新分數中" → read-only.
The whole-table snapshot also moved thousands of rows the app already had, to deliver a few learned
scores.

Replaced with a proactive, learned-only publish the editor reads instantly.

### Keyboard side — publish (Full Access only)

- **Dirty tracking:** `SearchServer.scoreDidChange` fires on every score write; the keyboard's
  `markScoresDirty()` sets a lock-guarded `scoresDirty` flag with a generation counter.
- **Publish trigger:** `publishLearnedScoresIfNeeded()` runs on `viewWillDisappear` (when dirty) and
  once on `viewDidAppear` (bootstrap, if the file does not exist yet). It is guarded by `hasFullAccess`
  and runs on `databaseQueue.async` — **never on the main thread, never blocking typing**.
- **Payload:** for each active table, `learnedScoreRows(stem)` selects only learned rows
  (`SELECT idA, idB, score WHERE score != 0 AND idA IS NOT NULL AND idB IS NOT NULL`), and the whole
  set is atomically written as JSON to `outbox/learned-scores.json`. The generation guard prevents a
  concurrent write from clearing the dirty flag prematurely.

Full Access **off**: keyboard writes to the App Group are dropped, so nothing is published — the
editor simply stays read-only.

### App side — apply (no poll, no timeout)

`SetupImController.refreshTableFromKeyboard(stem)`:

1. Read `SyncPaths.learnedScores`. **Missing → `.success` (no-op)** — the editor then shows baseline
   `0`s, which is correct for "nothing learned yet."
2. `DBServer.applyLearnedScores(stem:rows:)` in **one write transaction**:
   `UPDATE <stem> SET score = 0 WHERE score != 0` (clear stale overlay), then for each delta row
   `UPDATE <stem> SET score = ? WHERE idA = ? AND idB = ?`.
3. Return `.success`. `.failure` only on a real IO/DB error — **never a timeout** (there is no polling).

The editor calls this once when `relayEditingCapability == .live` (`refreshHotSnapshotIfNeeded`,
guarded by `didAttemptHotRefresh`); a real failure sets a local `hotRefreshFailed` that ANDs the view
back to read-only. Scores appear instantly.

The **backup** path (`requestKeyboardBackup` → `requestKeyboardSnapshot`, the full-DB export) is
independent and unchanged — this only replaced the per-table score refresh.

---

## 4. Contract & files

| Piece | Location |
| --- | --- |
| `LearnedScoreRow { a, b, s }`, `LearnedScoresFile { tables: [String: [LearnedScoreRow]] }`, `SyncPaths.learnedScores` (`outbox/learned-scores.json`), `RelayActiveState`, `RecordEditingCapability` | `Shared/Database/SyncContract.swift` |
| `applyLearnedScores(stem:rows:)`, `learnedScoreRows(stem:)` | `Shared/Database/DBServer.swift` + `Shared/Database/LimeDB.swift` |
| `refreshTableFromKeyboard(stem:)` (reads the file, applies) | `LimeSettings/Controllers/SetupImController.swift` |
| `markScoresDirty` / `publishLearnedScoresIfNeeded` / triggers | `LimeKeyboard/KeyboardViewController.swift` |
| `scoreDidChange` hook | `Shared/Search/SearchServer.swift` |
| Root relay → `markActive` / `markNotActive` | `LimeSettings/LimeSettingsView.swift` |
| Editors (`@EnvironmentObject RelayActiveState`) | `LimeSettings/Views/RecordListView.swift`, `RelatedListView.swift`, `IMDetailView.swift` |

`a` = `code` (or `pword` for `related`); `b` = `word` (or `cword`); `s` = `score`.

## 5. Behaviour summary

| State | Editor |
| --- | --- |
| Full Access **off** (or LIME not active) | Read-only. Scores `—`, edits disabled, unlock hint. No hang. |
| Full Access **on**, active, snapshot published | Editable. Real learned scores shown instantly (reset + overlay). |
| Full Access **on**, active, no snapshot yet (first run) | Editable. Baseline `0` scores until the keyboard has run one FA-on session. |

## 6. Tests

- `SyncContractTest` — `LearnedScoresFile` encode/decode round-trip.
- `LimeDBTest` — `applyLearnedScores` (learned rows get the delta, a stale non-zero row resets to 0,
  untouched rows stay 0); `learnedScoreRows` emits only `score != 0`.
- `RecordEditingCapabilityTest` — fail-safe default (`resolve(faState:)` → read-only; `.live` only
  when `confirmedOn && activeThisSession`).
- `RelayActiveStateTest` — nil / `fullAccess:false` / `markNotActive` → read-only; `fullAccess:true` → live.
- `SetupImControllerTest` — refresh reads the file and applies; missing file → success no-op.
