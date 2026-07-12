# Learning Path Audit

This document maps every runtime path that writes learned data, which preference gates it, and whether the current Android and iOS implementations obey that gate.

> File name intentionally follows the requested path: `docs/LEARING_PATH.md`.

## Preference Meanings

| Pref key | UI label | Intended scope |
|---|---|---|
| `candidate_suggestion` | 啟動自建關聯字 | Build related-phrase records from consecutive committed candidates. |
| `learn_phrase` | 自動學習新詞 | Promote frequent or continuous phrases into the main IM table as learned mappings. |
| `learning_switch` | 啟動選取排序 | Sort candidate results by learned score. This is not a write gate by itself. |
| `smart_chinese_input` | 開啟中文智慧組詞 | Build/show runtime phrase suggestions. It indirectly controls runtime-built phrase selection opportunities. |

## Android Paths

| Path | Entry point | Writes | Gate status |
|---|---|---|---|
| Candidate score update | `LIMEService.commitTyped()` -> `SearchServer.learnRelatedPhraseAndUpdateScore()` -> `updateScoreCache()` | Mapping score via `dbadapter.addScore(cachedMapping)` | Not gated by `learn_phrase`; comments say scores are always learned and only sorted by preference. |
| Related phrase learning | `postFinishInput()` -> `learnRelatedPhrase(scorelistSnapshot)` | Related table via `dbadapter.addOrUpdateRelatedPhraseRecord(unit, unit2)` | Gated by `candidate_suggestion` through `mLIMEPref.getLearnRelatedWord()`. DB layer has the same guard. |
| RP-triggered LD phrase learning | `learnRelatedPhrase()` when RP score is greater than 20 | Main IM table via `learnLDPhrase()` / `addOrUpdateMappingRecord()` | Gated by `learn_phrase` through `mLIMEPref.getLearnPhrase()`. |
| Continuous LD phrase learning | `LIMEService.commitTyped()` calls `SearchSrv.addLDPhrase(...)`; later `postFinishInput()` drains `LDPhraseListArray` into `learnLDPhrase()` | Main IM table via `addOrUpdateMappingRecord()` | Gated by `learn_phrase` inside `SearchServer.addLDPhrase()`. |
| Runtime-built phrase learning | `SearchServer.getRealCodeLength()` when selected mapping is `runtimeBuiltPhrase` | Main IM table via `dbadapter.addOrUpdateMappingRecord(code, word)` | Gated by `learn_phrase` inside `getRealCodeLength()`. |
| Emoji usage recency/frequency | `SearchServer.recordEmojiUsage()` | Emoji user table `emoji_user` | Not gated by learning prefs; appears separate from phrase learning. |

### Android Evidence

- Score update is always invoked after a normal candidate commit: `LIMEService.java` calls `SearchSrv.learnRelatedPhraseAndUpdateScore(committedCandidate)` around line 1792.
- `learnRelatedPhraseAndUpdateScore()` appends to `scorelist` and starts `updateScoreCache()` around `SearchServer.java` lines 1712-1730.
- `updateScoreCache()` writes score with `dbadapter.addScore(cachedMapping)` around `SearchServer.java` line 1305.
- Related phrase learning checks `mLIMEPref.getLearnRelatedWord()` around `SearchServer.java` line 1398.
- RP-triggered LD checks `mLIMEPref.getLearnPhrase()` around `SearchServer.java` line 1435.
- Continuous LD call sites still call `SearchSrv.addLDPhrase(...)`, but `SearchServer.addLDPhrase()` now returns immediately when `learn_phrase=false`.
- Runtime-built phrase learning writes only when `mLIMEPref.getLearnPhrase()` is true.

## iOS Paths

| Path | Entry point | Writes | Gate status |
|---|---|---|---|
| Candidate score update | `KeyboardViewController.commitTyped()` -> `SearchServer.learnRelatedPhraseAndUpdateScore()` | Mapping score via `db.updateScore(...)` | Not gated by `learn_phrase`; aligned with Android's always-learn-score behavior. |
| Related phrase learning | `postFinishInput()` -> `learnRelatedPhrase(snapshot)` | Related table via `db.addOrUpdateRelatedPhraseRecord(unit, unit2)` | Gated by `candidateSuggestion`. DB layer also no-ops when `learnRelatedWords` is false. |
| RP-triggered LD phrase learning | `learnRelatedPhrase()` when RP score is greater than 20 | Main IM table via `learnLDPhraseList()` / `addOrUpdateMappingRecord()` | Gated by `learnPhrasePref`. |
| Continuous LD phrase learning | `KeyboardViewController.commitTyped()` calls `addLDPhrase(...)`; later `postFinishInput()` drains `ldPhraseListArray` into `learnLDPhraseList()` | Main IM table via `addOrUpdateMappingRecord()` | Gated by `learnPhrase` at the controller call sites and defensively gated by `learnPhrasePref` inside `SearchServer.addLDPhrase()`. |
| Runtime-built phrase learning | `SearchServer.getRealCodeLength()` when selected mapping is `runtimeBuiltPhrase` | Main IM table via `db.addOrUpdateMappingRecord(code, word, tableName)` | Gated by `learnPhrasePref` inside `getRealCodeLength()`. |
| Emoji usage recency/frequency | `SearchServer.recordEmojiUsage()` | Emoji user table | Not gated by learning prefs; appears separate from phrase learning. |

### iOS Evidence

- `KeyboardViewController.commitTyped()` calls `getRealCodeLength(...)` before the later `learnPhrase` checks, around lines 2059-2061.
- Continuous LD buffering is gated by `learnPhrase` around `KeyboardViewController.swift`, and `SearchServer.addLDPhrase()` has a second `learnPhrasePref` guard.
- Score update and scorelist append run after normal candidate commit regardless of `learnPhrase`.
- `SearchServer.learnRelatedPhrase()` checks `candidateSuggestion` around `SearchServer.swift` line 567.
- RP-triggered LD checks `learnPhrasePref` around `SearchServer.swift` line 588.
- Runtime-built phrase learning writes only when `learnPhrasePref` is true.

## Current Status

Android and iOS are aligned for the learning preferences.

The three phrase-learning write paths are gated:

1. Runtime-built phrase learning is gated by `learn_phrase`.
2. Continuous LD phrase learning is gated by `learn_phrase`.
3. Related phrase learning is gated by `candidate_suggestion`; RP-triggered LD promotion inside that path is additionally gated by `learn_phrase`.

Candidate score updates remain ungated by `learn_phrase` on both platforms. This is intentional parity with Android: scores may still update, while `learning_switch` controls whether sorting uses those scores.

## Expected Policy After Cleanup

| User action | `candidate_suggestion` off | `learn_phrase` off | `learning_switch` off |
|---|---|---|---|
| Pick a normal candidate | Score may still update; no related-pair record should be created. | Score may still update; no new phrase mapping should be created. | Score may still update; ordering should not use score. |
| Commit two consecutive words | No related table upsert. | Related table may update only if `candidate_suggestion` is on; no LD mapping promotion. | No effect on writes. |
| Continuous typing with remaining composing code | No effect unless later RP learning is involved. | No `addLDPhrase` accumulation or LD mapping write. | No effect on writes. |
| Select runtime-built phrase | Runtime suggestion may still display if `smart_chinese_input` is on. | No runtime-built phrase mapping write. | No effect on writes. |
| Pick emoji | Emoji usage may still update unless a separate emoji privacy pref is added. | Emoji usage may still update. | No effect on writes. |

## Verification Checklist

1. With `learn_phrase=false`, selecting a runtime-built phrase must not call `addOrUpdateMappingRecord()`. Covered by Android and iOS focused tests.
2. With `learn_phrase=false`, continuous LD learning must not populate or write LD phrase data. Covered by Android and iOS focused tests.
3. With `learn_phrase=false`, normal candidate score updates should still occur for Android parity.
4. With `candidate_suggestion=false`, `learnRelatedPhrase()` must not write related records.
5. With `learning_switch=false`, candidate writes may still occur, but candidate ordering must ignore score.

Verified commands:

- iOS focused tests:
  `xcodebuild -project LimeIME-iOS/LimeIME.xcodeproj -scheme LimeIME -destination 'id=F487E48B-16A9-4FBB-8B36-6BCB38EA1764' -only-testing:LimeTests/SearchServerTest/test_getRealCodeLength_runtime_phrase_learning_disabled_by_learnPhrasePref -only-testing:LimeTests/SearchServerTest/test_addLDPhrase_noop_when_learnPhrasePref_disabled test`
- Android compile:
  `./gradlew :app:compileDebugJavaWithJavac :app:compileDebugAndroidTestJavaWithJavac`
- Whitespace check:
  `git diff --check -- docs/LEARING_PATH.md LimeStudio/app/src/main/java/org/limeime/SearchServer.java LimeStudio/app/src/androidTest/java/org/limeime/SearchServerTest.java LimeIME-iOS/Shared/Search/SearchServer.swift LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift LimeIME-iOS/LimeTests/SearchServerTest.swift`
