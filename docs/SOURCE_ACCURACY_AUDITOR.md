# Source Accuracy Auditor

This auditor checks every manual claim against the design/spec/reference docs under `docs/`.

## Required Evidence

For each page, identify the topic-relevant source docs before editing. Common sources include `LIME_SETTINGS.md`, `KEYBOARD_THEME.md`, `ANDROID_IPHONE_KEYBOARD.md`, `KEYBOARD_TYPE.md`, `ANDROID_VOICE_INPUT.md`, `IPAD_KEYBOARD.md`, `IPAD_KB_SIZE_TIERS.md`, and issue notes such as `#88_ISSUE.md`.

## Reject If

- A UI name, tab name, setting label, or behavior is invented.
- `喜好設定` is described as generic `設定`.
- `資料庫` / DB Manager is missing from backup, restore, or default restore guidance.
- DB Manager does not cover `備份資料庫`, `還原資料庫`, and `還原預設資料庫`.
- iPhone `允許完整取用` is described as required for basic input, IM install/import/delete, restore preparation, or normal app use.
- iPhone `允許完整取用` is described as an internal App Group, cross-process, or generic database-access requirement instead of the user-facing unlock for in-app backup, key vibration feedback, and table editing.
- iPad size tiers are presented as implemented.
- Android voice input behavior differs from `docs/ANDROID_VOICE_INPUT.md`.
- A Settings App screen is described without using the relevant existing `assets/lime_settings_*` screenshot.

## Pass Evidence

A passing review lists the source files used and the exact sensitive claims checked.
