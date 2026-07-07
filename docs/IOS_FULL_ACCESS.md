# iOS Full Access — Permission Model & Cross-Process Communication

Scope: the **LimeKeyboard** extension and the **LimeSettings** app — what Full Access (FA)
actually gates, and how the two processes **communicate and stay in sync across the FA
boundary**. The cold/hot **database data model** those messages carry lives in the companion
doc; this doc is the transport and permission layer beneath it.

Companion docs: [IOS_DB_COLD_HOT.md](IOS_DB_COLD_HOT.md) (the cold/hot database design — roles,
`sync_meta`, full-replace vs. incremental, per-table content sync), [IOS_FULL_ACCESS_DETECT.md](IOS_FULL_ACCESS_DETECT.md)
(enabled / Full Access detection in the Settings UI), [IOS_GOTO_SETTINGS.md](IOS_GOTO_SETTINGS.md)
(Settings deep-link reliability).

## Bottom line

LimeIME **cannot require Full Access** for the keyboard to function (App Review Guideline
4.4.1: a keyboard must type, provide the globe / next-keyboard path, and stay functional
without FA). So the **entire design works FA-off** — typing, install, import, uninstall,
restore, learning, learned-record preservation. Full Access **ON** unlocks only three things:

1. **In-app backup** — the keyboard exports its **hot DB** (the sole home of learned scores;
   fixed tables are re-downloadable).
2. **Key haptic feedback** (按鍵震動回饋) — a system restriction: keyboard extensions cannot
   play haptics FA-off. Key-click *sound* works without it.
3. **Table-record editing with real scores** — the editor's hot-snapshot refresh needs the
   same keyboard→App-Group write as backup. FA-off the record screens are read-only.

FA is a **feature unlock, never a requirement**. Settings copy must present it that way.

Sources: [App Review Guidelines 4.4.1](https://developer.apple.com/app-store/review/guidelines/),
[Apple UIKit open-access guide](https://developer.apple.com/documentation/uikit/configuring-open-access-for-a-custom-keyboard).

## Permission facts

Apple's open-access documentation, verbatim: without open access a keyboard has *"No access to
the file system apart from the keyboard's own sandbox container, and read-only access to the
containing app's shared containers."*

| Actor → Target | FA OFF | FA ON |
| --- | --- | --- |
| App → App Group | read/write | read/write |
| Keyboard → App Group | **read-only** | read/write |
| Keyboard → its own container | read/write | read/write |
| App → keyboard's own container | never | never |

- The **containing app is never restricted** — FA limits only the keyboard extension. The app
  owns the cold DB outright and always works.
- The keyboard's own `Application Support` data **persists** across process restarts, reboot,
  and FA toggles; removed only on uninstall.
- Reading App Group file *bytes* is safe FA-off; **opening the shared DB live is not** — even a
  "read-only" SQLite open of a WAL database wants to create `-wal`/`-shm` sidecars. So the
  keyboard never opens the App Group DB in place; it **copies the file** into its own container
  first (the cold→hot full replace, cold/hot doc §1.2).
- **Shared UserDefaults**: app-written prefs are readable by the keyboard FA-off (re-read on
  appear), but keyboard *writes* are dropped FA-off, change notifications don't fire, and
  cfprefsd may serve a stale cache — so defaults carry **user preferences only, never
  correctness signals** (those use files).
- **Darwin notifications** are not FA-gated in either direction, but carry **no payload** and
  reach only a live, listening process.
- The **Simulator does not enforce** the keyboard sandbox — FA behavior MUST be validated on a
  real device.

## The cross-process channel

The app and the keyboard are **separate processes** that share nothing but the App Group
container. All coordination is **file-based**, with payload-free **Darwin notifications** as
doorbells that only mean *"go look at the folder."* No shared memory, no RPC, no `UserDefaults`
correctness signal.

**Shared files (App Group container):**

| File | Written by | Meaning |
| --- | --- | --- |
| `cold.limedb` | app | the published cold snapshot; carries its own `sync_meta` (epoch + generation) |
| `im.json` | app | published `im`-table snapshot (`schemaVersion` + `generation`); keyboard reads it **fresh, FA-off** via the §1.5 decorator |
| `inbox/prefs.json` | app (writes) | app→keyboard preference deltas, `seq`-stamped (keyboard best-effort deletes) |
| `outbox/export.request.json` | app | "please snapshot hot" `{requestUUID, expiresAt}` |
| `outbox/backup.limedb` | keyboard (FA-on) | the hot snapshot produced on request |
| `outbox/receipt.json` | keyboard (FA-on) | snapshot ready `{requestUUID, epochUUID, at}` |
| `outbox/heartbeat.json` | keyboard (FA-on) | `{hasFullAccess, lastSeenAt, lastDBError}` — FA / liveness |

**Darwin doorbells (`org.limeime.*`, no payload):**

| Signal | Posted by | Wakes the reader to… |
| --- | --- | --- |
| `tables.updated` | app | re-scan cold (new generation and/or epoch) |
| `outbox.updated` | app | check for an export request |
| `import.done` / `import.failed` | keyboard | reflect import status |
| `fa.on` / `fa.off` | keyboard | FA-state ping (name-encoded, so "Confirmed OFF" is representable) |
| `sync.scan.done` | keyboard | a cold→hot scan just finished — the app dismisses its sync probe now |

A doorbell only makes a **live** FA-on keyboard act *now*; the durable path is
**scan-on-appear** — the keyboard re-checks cold every time it becomes visible, so a missed
notification just delays delivery to the next appearance, never drops it.

## What Full Access gates — reads vs. writes

The single rule behind the whole design: **FA gates the keyboard's *writes* to the App Group,
not its reads.**

- The **cold→hot sync runs FA-off** because it only **reads** the App Group (cold DB + inboxes)
  and **writes** the keyboard's own container (hot DB + its cursors). Applying a restore, an
  install, an incremental sync — all FA-independent.
- The keyboard's **App Group writes are FA-on**: the backup snapshot + receipt, the
  editor-refresh receipt, the durable heartbeat. These are the only FA-gated operations.
- The **app side always works** — it owns cold read/write regardless of FA.

So the cold/hot **split exists for write isolation** (the app editing cold must not race the
keyboard learning into hot), and it makes the keyboard FA-off-functional for free: everything
the keyboard needs to *apply* is a read-cold / write-own-container operation.

> **UI gating is a separate, deliberate choice.** The 備份 button and the table editor's
> live-edit unlock may still require FA-Confirmed-ON as a conservative product decision — a UX
> gate on the *button*, not a technical requirement of App Group access. The **cold→hot sync
> that surfaces installed IMs is never FA-gated.**

**Behavior when FA is toggled OFF later:** nothing changes for typing, IMs, or learning — the
hot DB is keyboard-owned and unaffected. Only backup export, haptics, live record editing, and
durable status stop; the Settings UI degrades to neutral copy and editors fall back to
read-only. FA can be turned on and off freely with no data loss.

## Signal channels (summary)

| Channel | App → Keyboard | Keyboard → App |
| --- | --- | --- |
| App Group files | ✅ backbone (keyboard reads FA-off) | ✅ FA-**ON** only (receipts, status, snapshots) |
| Scan-on-appear | ✅ guaranteed eventual delivery | — |
| Darwin notification | ✅ instant doorbell, not FA-gated | ✅ live-only ping, not FA-gated (no payload) |
| Probe text field | ✅ summons the keyboard to load & scan now | ✅ **relay** — typed text, FA-off (below) |
| Shared UserDefaults | ⚠️ user prefs only (reads FA-off; may be one session stale) | ❌ writes dropped FA-off |
| Pasteboard / openURL / network | ❌ | ❌ FA-gated or dead for keyboards |

## The summon probe — trigger a sync before the user leaves the app

After a restore or an install / delete the app wants the cold→hot sync to happen **before the
user leaves Settings**, so the IM shows on the **first** keyboard appearance instead of only
after a re-open. But a keyboard extension is **dormant** — it runs only as some app's active
input view — so the app **summons** it with an **invisible focused probe field**. Focusing the
field brings the keyboard up; its appearance handler enqueues the sync scan. That is all the
probe needs to do: **trigger** the scan.

- **Dismiss on `sync.scan.done`, not a fixed timer.** The keyboard rings the name-only
  `sync.scan.done` the moment the scan returns, and the probe dismisses on it — so the popup
  lasts only as long as the scan actually takes (a fixed hold left a blank keyboard on screen
  far longer than the sub-second scan). A short timeout remains only as a fallback for a missed
  signal; the scan is idempotent and re-runs on the next appearance regardless.
- **Why "done", not "appeared".** Dismissing the instant the keyboard *appears* would unfocus
  the field and let iOS suspend / kill the extension **mid-scan** — the sync cut and re-run
  (with 同步中) on the user's first real open. Waiting for the **done** ping keeps the extension
  alive through the scan, so it is guaranteed complete.
- **The probe runs in a *different process* than the user's next keyboard.** The probe summons
  the keyboard inside **Settings**; the user's next real keyboard (e.g. Safari's) is a
  **separate process** of the same extension — iOS runs one keyboard process per host app. The
  full replace the probe triggers lands in Settings' keyboard process, which can leave the
  *other* process reading a stale hot-DB file handle; recovering from that is the cross-process
  reopen (cold/hot doc §1.8).

## FA-off-safe transport — how the keyboard consumes without writing the App Group

The keyboard **cannot delete or rewrite an App Group inbox FA-off** (read-only). So no
app→keyboard stream is consumed by removing the file. The keyboard tracks what it has applied in
its **own container** and treats the App Group file as read-only. Two streams use this, with
different "already applied?" gates:

- **Preference inbox (§1.8) — a `seq` cursor.** The app appends `seq`-stamped records to
  `inbox/prefs.json` (App→App Group is writable in every FA state) and rings `tables.updated`.
  The keyboard reads it (read-only ✓ FA-off) and applies each record whose `seq` is beyond a
  cursor it keeps in its own `UserDefaults`, then advances the cursor. A lingering
  already-applied record is skipped by the cursor, so it can't re-fire — **the cursor, not the
  file's presence, is the consume gate.** The keyboard best-effort deletes the file when it can
  (FA-on); FA-off it just lingers until the app overwrites it, which is harmless.
- **IM-lifecycle inbox (§1.6) — the table `rev`.** Same read-only contract, but a lifecycle
  record applies only when its table's `rev` moves (a DB-content gate, cold/hot doc §1.6), so no
  seq cursor is needed, and the keyboard never deletes it.

Neither stream needs the keyboard to write the App Group; correctness is the own-container
marker (seq cursor or `rev`), never the file's presence.

> **`im` is *not* on this inbox transport.** IM metadata is read from the app-published
> **`im.json`** (cold/hot doc §1.5) — a plain-file snapshot the keyboard reads **fresh on demand**
> through a `LimeDBProtocol` decorator, not a `seq`/cursor inbox. There is no `im` inbox, cursor,
> or GC; and (unlike the abandoned overlay) **no live open of the cold DB** — reading `im.json`
> *bytes* is the FA-off-safe carve-out, the same one that keeps `cold.limedb` a copy-then-read
> rather than an in-place open. That whole seq-inbox mechanism, and its races, was retired once
> `im` became an on-demand file read.

**The relay (keyboard → app, FA-off).** Typing is the keyboard's core function, so `insertText`
works in every FA state. The probe field doubles as a **keyboard→app channel**: the app
prefills a magic token and focuses the field; the keyboard recognizes its own app's sync field
and types **one compact payload** (protocol version, FA bit, timestamp, the current
keyboard-owned pref values); the app observes its own field binding, parses, applies, and clears it.
The token handshake guarantees the payload is never typed into a real text field. This is the
one keyboard→app path FA cannot block, so it carries everything the keyboard must report without
an App Group write.

## Keyboard-owned preferences across the FA boundary

Four preferences can change on the **keyboard** side — 漢字轉換 (`han_convert_option`),
分離鍵盤 (`split_keyboard_mode`), the per-IM 字根反查 (`<im>_im_reverselookup`), and the
**active IM** (`active_im`). **Every other pref is app-write-only**, and the keyboard reads it
straight from the App Group. These four are *not* in `lime.db` — they are `UserDefaults`, so
they ride this transport layer, not the cold/hot sync.

- **The bug this fixes.** The keyboard used to write these to the App Group, but **FA-off that
  write is silently dropped**. A hamburger-menu change or IM switch never persisted; the
  keyboard re-read the App Group next appearance and **reverted** to the stale value.
- **Model — the keyboard owns the value in its own container.** The authoritative store is the
  keyboard's own `UserDefaults` (extension-private, always writable, survives restarts /
  reboots / FA toggles). The keyboard reads and writes all four **only** there.
- **kb→app** is the **relay** (typed text); the app's shared defaults become a **display** copy.
- **app→kb** is the **seq-guarded inbox** — but only a **wholesale restore** ever sets these
  from the app (delivering the restored backup's values); normal app enable/disable never
  writes them. The keyboard **drains the inbox before answering the relay**, so the relay always
  reports the post-drain value and an app change is never bounced back.
- **Cold is written for exactly one purpose: backup.** These reach the App Group only when the
  keyboard snapshots for a backup — an **FA-on** moment — so they ride the backup zip and
  return on restore through the inbox. There is no ongoing cold write, and no cross-writer race
  (the keyboard runs only on-screen, so app-drain and keyboard-edit never overlap).

## What Full Access actually gates (final list)

1. **Keyboard→App Group writes**: backup snapshot export, editor snapshot refresh, durable
   receipts / status / heartbeat.
2. **Key haptic feedback** (system restriction on keyboard extensions).
3. Consequence of 1: **real-score visibility**, and therefore table-record editing.

That's all. FA is presented as a feature unlock (備份已學習字詞、按鍵震動回饋、編輯字根資料表),
never a requirement.

## History — the superseded v1 model

An earlier **v1** design moved IM data as a **desired-state folder of per-table files**
(`tables/`, `restore.limedb`) plus shared-UserDefaults signals. It is **superseded** by the
current cold/hot snapshot model: a single atomic `cold.limedb` snapshot the keyboard tracks by
epoch / generation / per-table rev (see [IOS_DB_COLD_HOT.md](IOS_DB_COLD_HOT.md)), with
file-based signals only. The v1 per-table transport, its migration steps, and its test matrix
are removed; nothing in the shipping design depends on them.
