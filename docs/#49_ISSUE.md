# Issue #49 — 常用的後選字順序(顯示優先序)，無法即時更新

**Device:** Samsung S25U · Android 16 · App v6.0.1  
**Input Method:** LIME Array30 (行列30)

---

## Reported Symptoms

1. Repeatedly selecting the same candidate does not reorder it. Switching to another IME and back makes the new order appear.
2. Editing display priority directly in the app has no effect. Closing and reopening the app makes it take effect.

---

## Learning Path (How Scores Reach the DB)

When the user selects a candidate:

```
LIMEService.commitTyped()
  └─ SearchSrv.learnRelatedPhraseAndUpdateScore(committedCandidate)
       └─ spawns UpdatingThread
            └─ updateScoreCache(mapping)
                 ├─ dbadapter.addScore(mapping)          ← score+1 written to DB immediately
                 └─ cachedList in-memory reorder          ← only if new_score > predecessor_score
```

The DB score is written correctly on every selection.  
The in-memory cache reorder is attempted immediately, but is vulnerable to a race condition with the next query (see Symptom 1).

---

## Symptom 1 — Typing-Based Learning Not Immediate (The Bug)

**File:** `LimeStudio/app/src/main/java/net/toload/main/hd/SearchServer.java`

When a candidate is selected, `learnRelatedPhraseAndUpdateScore()` spawns a background `UpdatingThread` that runs `updateScoreCache()`. `updateScoreCache()` first writes the new score to the DB, then modifies the live `cachedList` (a `LinkedList`) in-place to reorder it.

`updateScoreCache()` runs on `UpdatingThread` with **no synchronization lock**. Meanwhile, the very next keystroke triggers `getMappingByCode()` on `queryThread`. `getMappingByCode()` is `synchronized` on the `SearchServer` instance, but `updateScoreCache()` is not — so both threads access the same `cachedList` object concurrently:

```
UpdatingThread (no lock):
    cachedList.remove(j)    ← structural modification, increments modCount

queryThread (synchronized getMappingByCode):
    result.addAll(resultlist)   ← iterates cachedList via iterator
        → iterator checks modCount → ConcurrentModificationException thrown
```

`queryThread.run()` only catches `RemoteException`, not `RuntimeException`. The `ConcurrentModificationException` is uncaught and **kills queryThread silently** — candidates are not updated on screen.

Because the DB write in `addScore()` ran before the cache modification, the score IS correctly accumulated in the DB. After an IME switch, `LIMEService` restarts, `initialCache()` clears the stale cache, and the next query reads from the DB with the accumulated score — showing the correct order.

This is a race condition. When the user types slowly, the race doesn't occur (`UpdatingThread` finishes before the next query begins) and the order does update. When the user types quickly, the race fires and the update is silently lost.

---

## Symptom 2 — App Priority Edit Not Taking Effect (The Bug)

**File:** `LimeStudio/app/src/main/java/net/toload/main/hd/ui/controller/ManageImController.java`

`ManageImController.updateRecord()` and `addRecord()` write the new score to the DB correctly, but never invalidate `SearchServer.cache` — the `static ConcurrentHashMap` shared for the lifetime of the process:

```java
public void updateRecord(String table, long id, String code, String word, int score) {
    // ...
    searchServer.updateRecord(table, cv, LIME.DB_COLUMN_ID + " = ?", new String[]{String.valueOf(id)});
    if (manageImView != null) {
        manageImView.refreshRecordList();
    }
    // ← cache is never cleared; keyboard still serves stale cached order
}
```

The cache is not cleared by navigating away (the fragment stays in the back stack). It is only cleared when the process is killed — hence the close/reopen workaround. If the user edits a score and then immediately tests by typing in a text field, the keyboard still reads the stale cached list, regardless of whether they left the screen or not.

`ManageRelatedController` has the same gap — `ManageRelatedFragment.onDestroy()` calls `initialCache()` as a workaround, but that only fires when the fragment is fully destroyed, not when the user switches away to test.

---

## Fix 1 — Synchronize `updateScoreCache()`

**File:** `SearchServer.java`

Add `synchronized` to `updateScoreCache()` so it cannot run concurrently with `getMappingByCode()`:

```java
// Before:
private void updateScoreCache(Mapping cachedMapping) {

// After:
private synchronized void updateScoreCache(Mapping cachedMapping) {
```

`getMappingByCode()` is already `synchronized` on the same `SearchServer` instance. This ensures the `cachedList` can never be structurally modified at the same time as it is being iterated, eliminating the `ConcurrentModificationException` in `queryThread`.

The `UpdatingThread` will block briefly until `getMappingByCode()` releases the lock (or vice versa). The impact is negligible since score updates are infrequent (one per committed candidate).

---

## Fix 2 — Call `initialCache()` immediately after DB write in `ManageImController`

**File:** `ManageImController.java`

Clear the cache right after each DB write, so the very next keystroke re-queries from the updated DB — regardless of whether the user has left the edit screen or not:

```java
public void updateRecord(String table, long id, String code, String word, int score) {
    // ...
    searchServer.updateRecord(table, cv, LIME.DB_COLUMN_ID + " = ?", new String[]{String.valueOf(id)});
    searchServer.initialCache();  // ← add this: cache is stale immediately after any score edit
    if (manageImView != null) {
        manageImView.refreshRecordList();
    }
}

public void addRecord(String table, String code, String word, int score) {
    // ...
    searchServer.addOrUpdateMappingRecord(table, code, word, score);
    searchServer.initialCache();  // ← add this
    if (manageImView != null) {
        manageImView.refreshRecordList();
    }
}
```

After this fix, as soon as the user saves any record edit, the cache is cleared. Any keystroke in the keyboard (whether the user left the edit screen or not) will re-query the DB and reflect the updated priority immediately.

---

## iOS Port Analysis

### Bug 1 (Race Condition) — NOT present in iOS

iOS `SearchServer.swift` protects its cache with an `NSLock` (`cacheLock`) on all reads and writes, and Swift arrays are value types — a background read gets a copy, not a reference to the live cache list. The `ConcurrentModificationException` class of bug cannot happen.

### Bug 2 (Stale Cache After App Edit) — Present, different mechanism, fixed differently

In iOS, the container app (which runs `ManageImController`) and the keyboard extension (which runs `SearchServer` and its cache) are **separate processes**. The app cannot call `searchServer.clearAllCaches()` directly.

The existing IPC channel is the shared App Group `UserDefaults` (`group.org.limeime`), already used for settings.

**Fix applied:**

In `ManageImController.swift` — after every successful DB mutation (`addRecord`, `updateRecord`, `deleteRecord`), write a dirty flag to shared `UserDefaults`:

```swift
private static func markKeyboardCacheDirty() {
    UserDefaults(suiteName: "group.org.limeime")?.set(true, forKey: "needsKeyboardCacheReset")
}
```

In `KeyboardViewController.swift` — at the top of `initOnStartInput()` (called every time the keyboard becomes visible via `viewWillAppear`), check and consume the flag:

```swift
if sharedDefaults?.bool(forKey: "needsKeyboardCacheReset") == true {
    searchServer?.clearAllCaches()
    sharedDefaults?.removeObject(forKey: "needsKeyboardCacheReset")
}
```

When the user edits a record in the app and then taps a text field (bringing up the keyboard), `viewWillAppear` → `initOnStartInput` fires, the flag is found, the cache is cleared, and the first keystroke re-queries the updated DB.
