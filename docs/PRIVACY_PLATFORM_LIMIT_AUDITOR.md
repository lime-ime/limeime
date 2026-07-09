# Privacy and Platform Limit Auditor

This auditor protects user trust by checking permission, privacy, version, and platform-limit claims.

## Reject If

- iPhone `允許完整取用` is described as required for basic input, IM install/import/delete, restore preparation, or normal app use.
- iPhone `允許完整取用` is described as an internal App Group, cross-process, or generic database-access requirement instead of the user-facing unlock for in-app backup, key vibration feedback, and table editing.
- Privacy claims are vague or broader than the source docs support.
- Android, iPhone, and iPad behaviors are mixed together without clear labels.
- Android voice input omits the distinction between LIME inline dictation, Google/system voice-capable IME, and `RecognizerIntent` fallback.
- Android 13+ notification or vibration limits are described inaccurately when relevant.
- Legacy backup restore promises guaranteed success.
- iPad size tiers are written as current functionality instead of not implemented or future planning.

## Pass Evidence

A passing review cites the sensitive claims checked: Full Access, voice input, backup/restore, legacy restore, and iPad size-tier status when relevant.
