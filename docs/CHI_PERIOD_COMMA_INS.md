# Chinese Comma / Period Candidate Insertion

## Goal

When the user types `,` / `<` or `.` / `>`, LIME inserts the full-width Chinese punctuation candidate into the candidate strip:

| Input | Candidate |
|---|---|
| `,` or `<` | `，` |
| `.` or `>` | `。` |

This punctuation must appear immediately before emoji candidates when both are present.

## Position Rule

Keep `enable_emoji_position` default at `6`.

Do not move emoji around just to work around punctuation ordering. The preference should continue to mean the normal emoji insertion slot.

For comma/period queries, `，` or `。` is inserted into the candidate stream before emoji candidates.

Important: the visible candidate list may include a composing-code echo at index `0`, so a DB-level punctuation insertion at DB index `3` can appear at visible index `4`. Emoji insertion must not only check the exact requested slot. It must scan forward from the configured emoji slot and yield if it sees `，` or `。`.

Expected order around the default slot:

```text
candidate[0], candidate[1], candidate[2], ..., `，`/`。`, emoji...
```

## Android Implementation

Primary files:

- `LimeStudio/app/src/main/java/org/limeime/limedb/LimeDB.java`
- `LimeStudio/app/src/main/java/org/limeime/LIMEService.java`
- `LimeStudio/app/src/main/java/org/limeime/global/LIMEPreferenceManager.java`
- `LimeStudio/app/src/main/res/xml/preference.xml`
- `LimeStudio/app/src/main/res/xml-v17/preference.xml`

`LimeDB.buildQueryResult()` injects `，` or `。` at index `3` for comma/period queries. These synthetic mappings must call:

```java
temp.setChinesePunctuationSymbolRecord();
```

`LIMEService.adjustedEmojiInsertionPosition()` clamps the requested emoji index, then scans forward from that index. If it finds a `，` or `。` candidate, or a mapping tagged as a Chinese punctuation symbol record, emoji insertion moves after that punctuation.

This handles both cases:

- Punctuation is exactly at visible index `3`.
- Punctuation is shifted later, for example to visible index `4`, because the composing-code echo was prepended at index `0`.

Android default is:

```text
enable_emoji_position = "6"
```

## iOS Implementation

Primary files:

- `LimeIME-iOS/Shared/Search/SearchServer.swift`
- `LimeIME-iOS/Shared/Database/LimeDB.swift`
- `LimeIME-iOS/Shared/Preferences/LIMEPreferenceManager.swift`
- `LimeIME-iOS/LimeSettings/Views/PreferencesTabView.swift`
- `LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift`

`LimeDB.buildQueryResult()` mirrors Android by inserting `，` or `。` at index `3` for comma/period queries. Those mappings use:

```swift
recordType: Mapping.RecordType.chinesePunctuation
```

`SearchServer.adjustedEmojiInsertionIndex(in:requestedIndex:)` clamps the requested emoji index, then scans forward from that index. If it finds `，` or `。`, or a mapping tagged as `isChinesePunctuationRecord`, emoji insertion moves after that punctuation. Both regular emoji injection and direct word-based emoji injection use this helper.

This forward scan is required because `SearchServer` prepends the composing-code echo before the visible list is shown. Example:

```text
0: ,
1: 力
2: 犭
3: 加
4: ，
```

With `enable_emoji_position = 3`, emoji must be inserted at index `5`, not index `3`.

iOS default is:

```text
enable_emoji_position = 6
```

## Tests

Focused tests:

- Android: `LIMEServiceTest.emojiInsertionPositionSkipsChinesePunctuationAtRequestedSlot`
- Android: `LIMEServiceTest.emojiInsertionPositionSkipsUntypedChineseCommaPeriodAtRequestedSlot`
- Android: `LIMEServiceTest.emojiInsertionPositionYieldsToCommaPeriodShiftedByComposingEcho`
- iOS: `SearchServerTest.test_3_6_4_5_emojiInsertionSkipsChinesePunctuationAtRequestedSlot`
- iOS: `SearchServerTest.test_3_6_4_6_emojiInsertionSkipsUntypedChineseCommaPeriodAtRequestedSlot`
- iOS: `SearchServerTest.test_3_6_4_7_emojiInsertionYieldsToCommaPeriodShiftedByComposingEcho`
- iOS default: `LIMEPreferenceManagerTest.testDefaultEnableEmojiPosition`

Verification commands used:

```sh
cd LimeStudio
./gradlew assembleDebug assembleDebugAndroidTest
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=net.toload.main.hd.LIMEServiceTest#emojiInsertionPositionSkipsChinesePunctuationAtRequestedSlot,net.toload.main.hd.LIMEServiceTest#emojiInsertionPositionSkipsUntypedChineseCommaPeriodAtRequestedSlot,net.toload.main.hd.LIMEServiceTest#emojiInsertionPositionYieldsToCommaPeriodShiftedByComposingEcho
```

```sh
xcodebuild -project LimeIME-iOS/LimeIME.xcodeproj \
  -scheme LimeIME \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro Max,OS=26.4.1' \
  test \
  -only-testing:LimeTests/LIMEPreferenceManagerTest/testDefaultEnableEmojiPosition \
  -only-testing:LimeTests/SearchServerTest/test_3_6_4_5_emojiInsertionSkipsChinesePunctuationAtRequestedSlot \
  -only-testing:LimeTests/SearchServerTest/test_3_6_4_6_emojiInsertionSkipsUntypedChineseCommaPeriodAtRequestedSlot \
  -only-testing:LimeTests/SearchServerTest/test_3_6_4_7_emojiInsertionYieldsToCommaPeriodShiftedByComposingEcho
```

## Related Preference Doc

`docs/PREFS_TABLE.md` documents `enable_emoji_position` as default `6` and notes that Chinese punctuation at the insertion slot stays before emoji.
