# Issue #158: Android End-Key Hot Path Blocks Main Thread on SQLite

## Status

- GitHub issue: https://github.com/lime-ime/limeime/issues/158
- Classification: `bug` + `Type-Defect` + `Priority-Medium`
- State: open; source-level root cause identified from one Android Vitals sample
- Platform: Android only
- Fix state: not implemented

## Summary

Android Vitals reported an input-dispatch timeout/ANR in LIME v6.1.30 while the IME main thread was handling a key-up event. The main thread was parked waiting for a SQLite connection from `SQLiteConnectionPool`.

## Environment

- Device: OPPO Reno8 5G
- Android: 14 (SDK 34)
- LIME: v6.1.30 (`202661300`)
- Vitals samples: 1

## Relevant stack

```text
"main" tid=1 Timed Waiting
at android.database.sqlite.SQLiteConnectionPool.waitForConnection
at android.database.sqlite.SQLiteDatabase.rawQuery
at org.limeime.limedb.LimeDB.openDBConnection
at org.limeime.limedb.LimeDB.checkDBConnection
at org.limeime.limedb.LimeDB.getImConfig
at org.limeime.SearchServer.getImConfig
at org.limeime.LIMEService.handleEndkeyCommit
at org.limeime.LIMEService.onKey
at org.limeime.keyboard.PointerTracker.onUpEvent
```

## Source analysis

`LIMEService.handleEndkeyCommit()` runs in the ordinary character-key touch path and currently reads two IM metadata values synchronously for every ordinary character key:

```java
endkey = SearchSrv.getImConfig(activeIM, LIME.IM_LIME_ENDKEY);
imkeys = SearchSrv.getImConfig(activeIM, IMKEYS_CONFIG);
```

Each `LimeDB.getImConfig()` calls `checkDBConnection()`, which calls `openDBConnection(false)`. For an already-open database, that method executes `SELECT 1` before the requested metadata query. One key can therefore perform up to four SQLite connection acquisitions on the IME main thread.

The Vitals sample was captured while a `SELECT 1` health check was waiting for a pooled connection. The available trace does not identify which other thread held the connection, but the touch handler must not depend on SQLite availability.

`currentImKeys` is already cached when the active IM is initialized. The end-key path bypasses that cache and performs new database reads.

## Proposed fix

1. Cache the active IM's `limeendkey` and the exact stored `imkeys` value when the IM is initialized or changed.
2. Make `handleEndkeyCommit()` use only the cached strings, with no database calls in the key touch path.
3. Refresh/invalidate the cache after active-IM changes, mapping import/reload, metadata editing, and database restore/reset.
4. Review the `SELECT 1` probe run on every already-open `openDBConnection(false)` call separately. Removing that probe alone is not sufficient because the following metadata query can still block.

Use a separate cached end-key `imkeys` value rather than automatically substituting `currentImKeys`, because phonetic keyboard variants resolve `currentImKeys` differently and the ANR fix should preserve current end-key behavior.

## Acceptance criteria

- `handleEndkeyCommit()` performs no SQLite/database access.
- The cache is populated before the first ordinary character-key event.
- End-key behavior remains unchanged for built-in, phonetic-variant, and custom IM tables.
- Changing an active IM or its editable `limeendkey` metadata refreshes the cached values.
- Import/restore paths cannot leave stale end-key metadata indefinitely.
- Unit/instrumented tests cover cache refresh and end-key handling without a database read on key-up.
- Android regression tests pass.

## Scope

Android only. This is independent of iOS issue #139.
