# iOS Full Access Permission

Scope: LimeKeyboard extension and LimeSettings DB/import flow.

## Bottom line

LimeIME cannot require Full Access for the keyboard to function. Apple App Review Guideline 4.4.1 says keyboard extensions must provide input, provide a next-keyboard method, and remain functional without requiring Full Access.

But Apple also documents that a keyboard without open access has no shared container with its containing app. Therefore Settings-installed IM tables in the App Group `lime.db` are a Full Access feature, not the no-permission baseline.

Sources:

- Apple App Review Guidelines 4.4.1: https://developer.apple.com/app-store/review/guidelines/
- Apple Custom Keyboard guide: https://developer.apple.com/library/archive/documentation/General/Conceptual/ExtensibilityPG/CustomKeyboard.html

## Correct model

Full Access OFF:

- Keyboard must not be blank.
- Keyboard must type.
- Keyboard must provide the globe / next-keyboard path.
- Keyboard can read bundled resources.
- Keyboard can read its own extension container.
- Keyboard can use `UILexicon`.
- Keyboard must not depend on App Group `lime.db`.

Full Access ON:

- Keyboard can use the App Group shared container.
- Keyboard can read Settings-managed `lime.db`.
- Settings install/import/restore can affect the keyboard.
- Keyboard can copy App Group DB into its own private snapshot.
- Features such as shared learning, shared preferences, cloud/import sync, and DB restore can work.

## Why the old assumption was wrong

The old doc said App Group access works without Full Access. That is not a safe product contract.

It may appear true in Simulator or in a warm keyboard process because:

- the extension already opened the DB before permission changed,
- the process kept a live SQLite handle,
- Simulator behavior is looser or stale,
- App Group prefs may still appear writable in a specific run.

Do not design around that. Cold start, reboot, extension kill, reinstall, or App Review can behave differently.

## Way out

Use a fallback DB source order:

1. App Group `lime.db`, when Full Access / App Group access works.
2. Keyboard-private snapshot `lime.db`, if previously copied.
3. Bundled default `lime.db` inside `LimeKeyboard.appex`.
4. English-only keyboard as the final fallback.

This keeps the keyboard functional without Full Access and still lets Full Access unlock install/import/restore.

## Product behavior

Never granted Full Access:

- Keyboard uses bundled/private default DB.
- Settings-installed IMs are unavailable to the keyboard.
- Import/download UI must explain that applying new IMs to the keyboard requires Full Access.

Full Access granted:

- Settings installs/imports/restores into App Group `lime.db`.
- Keyboard reads App Group DB.
- Keyboard updates its private snapshot after a successful DB open.

Full Access later turned off:

- Keyboard falls back to its private snapshot.
- Last synced IMs continue to work.
- New Settings changes are not visible until Full Access is granted again.

## Required implementation

- Bundle a default `lime.db` with the keyboard extension.
- Add a keyboard-private DB path under the extension container.
- Change keyboard DB open to use the fallback order above.
- When App Group DB opens successfully, copy it to the keyboard-private snapshot.
- Keep the existing DB-open retry and `keyboard_db_last_error` breadcrumb.
- Settings UI must not claim the keyboard is broken without Full Access; it should say install/import/sync needs Full Access.

## Test matrix

- Fresh install, Full Access OFF: keyboard types and can switch keyboards.
- Fresh install, Full Access OFF: default bundled IM works or English fallback works.
- Full Access ON, install IM: keyboard sees installed IM.
- Full Access ON, restore default DB: keyboard reopens App Group DB.
- Full Access ON, install IM, then Full Access OFF, kill extension: keyboard uses private snapshot.
- Full Access OFF, install IM in Settings: Settings warns that keyboard sync requires Full Access.

