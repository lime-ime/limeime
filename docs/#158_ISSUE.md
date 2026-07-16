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

`LIMEService.handleEndkeyCommit()` runs before the ordinary character and Space/Enter branches and currently reads two IM metadata values synchronously for every key that reaches this branch, even when the key is not configured as a Lime end key:

```java
endkey = SearchSrv.getImConfig(activeIM, LIME.IM_LIME_ENDKEY);
imkeys = SearchSrv.getImConfig(activeIM, IMKEYS_CONFIG);
```

Each `LimeDB.getImConfig()` calls `checkDBConnection()`, which calls `openDBConnection(false)`. For an already-open database, that method executes `SELECT 1` before the requested metadata query. One key can therefore perform up to four SQLite connection acquisitions on the IME main thread.

The Vitals sample was captured while a `SELECT 1` health check was waiting for a pooled connection. The available trace does not identify which other thread held the connection, but the touch handler must not depend on SQLite availability.

`currentImKeys` is already cached when the active IM is initialized. The end-key path bypasses that cache and performs new database reads.

Concrete source anchors on current `master`:

- `LIMEService.onKey()` calls `handleEndkeyCommit(primaryCode)` in the ordinary key path.
- `SearchServer.getImConfig()` delegates directly to `LimeDB.getImConfig()` without a metadata cache.
- `LimeDB.getImConfig()` checks/reopens the connection and then runs its metadata query.
- `LimeDB.openDBConnection(false)` runs `SELECT 1` when an existing handle is open.

## Existing test coverage and gap

- `LIMEServiceTest` covers Lime end-key opt-in detection and commit behavior, but its service tests mock `SearchServer.getImConfig()` during `handleEndkeyCommit()` and therefore preserve rather than reject the blocking architecture.
- `AcceptsIntoComposingTest` covers root acceptance and phonetic-variant behavior, but not the end-key database-access boundary.
- `LimeDBTest` covers imported end-key metadata, and `ManageImControllerTest` covers editing and clearing `limeendkey`.
- Android instrumented `SearchServerTest` covers `getImConfig()` behavior with a stub database, but does not prove that key-up handling avoids `getImConfig()`.

These are regression anchors for the refactor, but no inspected test currently fails when `handleEndkeyCommit()` performs a database read. The new test should make the metadata source observable, preload or refresh the active-IM cache, invoke the end-key decision path, and assert both the expected commit decision and zero database/config reads during key handling. Cache refresh tests must also cover active-IM switches, same-IM metadata edits, import/reload, and restore/reset paths.

## Proposed fix

1. Cache the active IM's `limeendkey` and the exact stored `imkeys` value when the IM is initialized or changed.
2. Make `handleEndkeyCommit()` use only the cached strings, with no database calls in the key touch path.
3. Refresh/invalidate the cache after active-IM changes, mapping import/reload, metadata editing, and database restore/reset.
4. Review the `SELECT 1` probe run on every already-open `openDBConnection(false)` call separately. Removing that probe alone is not sufficient because the following metadata query can still block.

Use a separate cached end-key `imkeys` value rather than automatically substituting `currentImKeys`, because phonetic keyboard variants resolve `currentImKeys` differently and the ANR fix should preserve current end-key behavior.

The iOS implementation is a useful reference, not evidence that Android can reuse `currentImKeys` unchanged. `KeyboardViewController` uses a per-IM `imConfigCache`, clears it when `activeIM` changes, and reads `limeendkey` / configured `imkeys` through that cache in `handleLimeEndkeyCommit()`. Its `activeImkeysForEndkey()` deliberately falls back to `currentImKeys` when configured `imkeys` is empty; Android should preserve its current built-in, phonetic-variant, and custom-table behavior explicitly rather than copying that fallback without regression tests.

## Acceptance criteria

- `handleEndkeyCommit()` performs no SQLite/database access.
- The cache is populated before the first key reaches the end-key decision path.
- End-key behavior remains unchanged for built-in, phonetic-variant, and custom IM tables.
- Changing an active IM or its editable `limeendkey` metadata refreshes the cached values.
- Import/restore paths cannot leave stale end-key metadata indefinitely.
- Unit/instrumented tests cover cache refresh and end-key handling without a database read on key-up.
- Android regression tests pass.

## Follow-up questions

- Does Android Vitals show additional samples, affected IM tables, or preceding long-running database work beyond this single trace?
- Can local stress testing reproduce connection-pool contention while imports, restore, learning, or metadata updates overlap typing?
- Which existing import, metadata-edit, restore, and active-IM-change callbacks can refresh the cache immediately, and which need a new invalidation hook?

These questions can refine concurrency testing, but they do not block removing database access from the touch path.

## Platform impact

### Android

Confirmed affected scope. The Vitals stack and inspected Java call chain place synchronous SQLite work on Android's IME main-thread key-up path. Android needs the cache/invalidation fix and regression coverage described above.

### iOS

This exact ANR path is not shared with iOS: the iOS keyboard uses its own `KeyboardViewController` path and an `imConfigCache` for `imkeys`, `imkeynames`, and `limeendkey`, rather than Android's `LIMEService` / `LimeDB` SQLite call chain. No iOS code change or TestFlight retest is indicated by the available Android trace. The iOS inspection is architectural parity context, not evidence that all iOS metadata-refresh cases are covered.

## Verification plan

1. Add a failing Android regression test proving the current end-key key-up path performs metadata reads.
2. Populate and invalidate the cache through active-IM initialization/change and the identified import/edit/restore hooks.
3. Re-run the focused test and verify end-key behavior for built-in, phonetic-variant, and custom tables.
4. Run Android unit and instrumented regression checks.
5. Exercise typing while database-heavy import/restore or learning activity is running, and monitor main-thread SQLite access/ANR behavior.
