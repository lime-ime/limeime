# Issue #158: Cache Android End-Key IM Configuration Off the Key Path

## Status

- GitHub issue: https://github.com/lime-ime/limeime/issues/158
- Classification: `bug` + `Type-Defect` + `Priority-Medium`
- Platform: Android only
- Root cause: confirmed from Android Vitals and source tracing
- State: closed by `jrywu` on 2026-07-17 through GitHub-visible commit `a2dc99d6853b5d834b79b7ac8a91cb600062f4c4`
- Fix: merged to `master` with RED/GREEN instrumentation coverage
- Delivery: source-fixed; the latest Android GitHub Release remains v6.1.30, so verification in the next Android build remains release QA rather than an active issue watch

## Failure

Android Vitals reported an input-dispatch timeout/ANR in LIME v6.1.30 on an OPPO Reno8 5G running Android 14. The IME main thread was handling key-up while waiting for a SQLite connection:

```text
PointerTracker.onUpEvent
→ LIMEService.onKey
→ LIMEService.handleEndkeyCommit
→ SearchServer.getImConfig
→ LimeDB.getImConfig
→ LimeDB.checkDBConnection
→ LimeDB.openDBConnection
→ SQLiteDatabase.rawQuery
→ SQLiteConnectionPool.waitForConnection
```

Before the fix, every ordinary key entering `handleEndkeyCommit()` synchronously read both `limeendkey` and stored `imkeys`, even when the key was not an end key. Each read also ran the connection-health `SELECT 1`, so one key could acquire a SQLite connection four times.

The touch path must not depend on database availability.

## Fix: backport the iOS IM-config cache

iOS `KeyboardViewController` caches `limeendkey`, `imkeys`, and `imkeynames` per active IM. Android now caches the same three fields in a field-keyed map:

```java
private final HashMap<String, String> imConfigCache = new HashMap<>();
```

`refreshImConfigCache()` eagerly reads the active IM's exact stored `limeendkey`, `imkeys`, and `imkeynames`. `handleEndkeyCommit()` reads only the map and performs no `SearchServer`, `LimeDB`, or SQLite call.

The composing popup passes cached `imkeys` and `imkeynames` through `SearchServer.keyToKeyname()` to `LimeDB.keyToKeyName()`. On a key-map cache miss, `LimeDB` builds the map from those preloaded values instead of querying IM metadata. The legacy overload remains available and falls back to database reads when no preloaded values are supplied.

Android deliberately keeps cached stored `imkeys` separate from `currentImKeys`:

- `imConfigCache["imkeys"]` preserves the exact stored metadata used by the pre-fix end-key path and key-name mapping.
- `currentImKeys` remains the composing-acceptance set.
- Phonetic ET26/HSU variants continue resolving `currentImKeys` through `getPhoneticImKeys()`.
- Android does not copy iOS's empty-config fallback to `currentImKeys`, because that would change existing Android phonetic/custom-table behavior.

For non-phonetic IMs, initialization reuses cached stored `imkeys` as `currentImKeys`, avoiding a duplicate metadata query.

## Android lifecycle

The iOS cache is lazy and clears on `activeIM.didSet`. Android needs a stronger lifecycle because a first-touch miss would still reach SQLite.

Android refreshes eagerly at the shared `initialIMKeyboard()` boundary. Existing flows already route through that boundary:

| Event | Refresh behavior |
|---|---|
| Keyboard process/startup | `onStartInput()` initializes the active Chinese IM before key handling |
| New input field / keyboard reopened | `onStartInput()` reinitializes the active IM |
| Next/previous IM | `switchToNextActivatedIM()` calls `initialIMKeyboard()` |
| Explicit IM selection | `handleIMSelection()` calls `initialIMKeyboard()` |
| Same-IM `limeendkey` edit | Settings closes the keyboard; the next input start reloads metadata |
| Mapping import/reload | The next input start reloads metadata from the imported IM rows |
| Database restore/factory reset | The next input start reloads metadata from the reopened/replaced database |

No new observer, broadcast, global cache, or invalidation framework is required. The values may be stale only while the keyboard is not accepting input; they are refreshed before the next Chinese key path becomes active.

## Test-driven implementation

### RED: deterministic ANR-boundary reproduction

`LIMEServiceTest.endkeyHotPathDoesNotWaitForUnavailableImConfigSource()` replaces the IM-config source with a controlled blocking source, invokes the real private `handleEndkeyCommit()` path on a named IME key thread, captures the blocked stack, and releases the latch so the suite cannot hang permanently.

Before the fix it failed with:

```text
CountDownLatch.await
→ SearchServer.getImConfig
→ LIMEService.handleEndkeyCommit

AssertionError: Key handling reached the unavailable IM-config source
```

This is the deterministic regression boundary for the production `SQLiteConnectionPool.waitForConnection` failure: key handling must not enter the configuration source at all.

### GREEN: cache backport

The minimal production change:

1. Adds an iOS-shaped cache for stored `limeendkey`, `imkeys`, and `imkeynames` to `LIMEService`.
2. Eagerly refreshes all three fields in `initialIMKeyboard()`.
3. Makes `handleEndkeyCommit()` use only cached strings.
4. Reuses cached stored `imkeys` for non-phonetic composing acceptance.
5. Passes cached `imkeys` and `imkeynames` through the composing key-name path.

The reproduction test then passes while its configuration source remains unavailable.

### Behavior regression coverage

Existing end-key tests now preload through the same cache refresh and continue covering:

- Lime end-key opt-in detection.
- Appending an end key that belongs to stored `imkeys`.
- Committing the current candidate before an end key outside stored `imkeys`.
- Fresh trigger mapping and raw-trigger fallback.
- Stale prefix candidate rejection.
- Conventional `endkey` metadata remaining distinct from `limeendkey`.
- Empty and null metadata normalization.

The cache/source boundary test additionally proves zero `getImConfig()` calls during key handling after preload.

## iOS parity

| Behavior | iOS | Android |
|---|---|---|
| Cache owner | `KeyboardViewController` | `LIMEService` |
| Cached IM-config fields | `limeendkey`, `imkeys`, `imkeynames` | `limeendkey`, `imkeys`, `imkeynames` |
| Hot-path storage reads after preload | None | None |
| Active-IM refresh | Clear on `activeIM.didSet`, lazy refill | Eager refill in `initialIMKeyboard()` |
| First key after refresh | May perform first JSON lookup | Memory-only |
| Composing key set | Separate `currentImKeys` | Separate `currentImKeys` |
| Custom key-name map input | Cached `imkeys` + `imkeynames` | Cached `imkeys` + `imkeynames` passed to `LimeDB` |
| Empty stored-`imkeys` fallback | Falls back to `currentImKeys` | Preserves existing Android empty-string behavior |

The implementations are output-equivalent for populated stored metadata. Android intentionally strengthens preload timing and preserves its existing fallback semantics.

## Verification evidence

Focused reproduction, Nexus 6 AVD / Android 5.0.2 API 21:

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=org.limeime.LIMEServiceTest#endkeyHotPathDoesNotWaitForUnavailableImConfigSource
```

Result: `BUILD SUCCESSFUL`, 1/1 test passed.

Focused end-key behavior suite:

```text
Finished 8 tests on Nexus_6(AVD) - 5.0.2
BUILD SUCCESSFUL
```

Focused startup/IM-switch lifecycle suite: 4/4 passed (`startupConfigSnapshotAvoidsRepeatedKeyboardConfigQueriesWhenVersionUnchanged`, email-first invalidation, switch-to-IM refresh, and explicit IM-selection refresh).

The latest combined run executed 285 tests (`LIMEServiceTest` plus two focused `SearchServerTest` parity cases): 284 passed. The only failure was the pre-existing data-dependent `test_5_19_SwitchBetweenIM`, which directly expects a Dayi mapping for code `x`; the fresh Nexus 6 AVD did not have a Dayi table installed. The focused cache, end-key, and lifecycle suites were green.

The final four-test parity boundary passed: three-field preload, cached `imkeynames` propagation, legacy key-name caching, and the #158 no-wait regression.

Compilation:

```bash
./gradlew :app:compileDebugJavaWithJavac :app:compileDebugAndroidTestJavaWithJavac
```

Result: `BUILD SUCCESSFUL`; existing unchecked/deprecation warnings only.

## Acceptance criteria

- [x] `handleEndkeyCommit()` performs no database/configuration access.
- [x] Cache population occurs before the active Chinese key path.
- [x] Stored end-key `imkeys` remains separate from phonetic composing acceptance.
- [x] `imkeynames` is cached and consumed by the composing key-name path.
- [x] Active-IM switches refresh through the shared initialization boundary.
- [x] Same-IM edit/import/restore paths refresh on the next input start.
- [x] Deterministic regression test reproduces the old blocking boundary.
- [x] Existing focused end-key behavior tests pass.
- [ ] Full Android instrumentation regression suite passes.
- [ ] Device stress verification confirms no new main-thread SQLite sample while typing during database maintenance.

## Out of scope

Removing the `SELECT 1` probe in `openDBConnection(false)` is separate work. It is no longer reachable from end-key key handling, and removing it alone would not have fixed the following metadata query.

iOS requires no code change for this Android Vitals incident.
