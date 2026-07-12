# API 25 Compatibility Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the Android production database paths and instrumentation tests work on API 25 without regressing API 36 or the Play Console edge-to-edge remediation.

**Architecture:** Reproduce database failures individually on a clean API 25 app sandbox, then change only the shared attach/import transaction boundary proven responsible. Keep test-runtime compatibility and edge-to-edge expectations as independent commits.

**Tech Stack:** Java, Android SQLite, AndroidX Test/JUnit 4, Gradle.

## Global Constraints

- Preserve user database rollback safety and existing minSdk 21.
- Do not restore deprecated `Window.setStatusBarColor` or `setNavigationBarColor` calls.
- Keep production, test compatibility, and edge-to-edge corrections in separate commits.

---

### Task 1: API 25 database attach/import compatibility

**Files:**
- Modify: `LimeStudio/app/src/main/java/org/limeime/limedb/LimeDB.java`
- Test: `LimeStudio/app/src/androidTest/java/org/limeime/LimeDB103IntegrationTest.java`

- [ ] Run `freshInstallCopies103SeedAndRefreshesEmojiData` alone on a clean API 25 sandbox and retain the exact failing stack/log.
- [ ] Trace every `ATTACH DATABASE` caller and verify transaction state at the shared boundary.
- [ ] Add one regression assertion proving payload attachment occurs outside a transaction while merge writes remain transactional.
- [ ] Move only the proven attach/detach boundary outside the transaction.
- [ ] Re-run the isolated seed, emoji, dictionary, reset, and restore tests on API 25 and API 36.
- [ ] Commit production database compatibility independently.

### Task 2: API 25 instrumentation helper compatibility

**Files:**
- Modify: `LimeStudio/app/src/androidTest/java/org/limeime/LimeDBTest.java`

- [ ] Run one export metadata test on API 25 and confirm `File.toPath()` is the sole failure.
- [ ] Replace the test helper with `FileInputStream` plus `InputStreamReader(StandardCharsets.UTF_8)`.
- [ ] Run all affected export/import tests on API 25 and API 36.
- [ ] Commit test-runtime compatibility independently.

### Task 3: Edge-to-edge legacy layout compatibility

**Files:**
- Modify: `LimeStudio/app/src/main/java/org/limeime/ui/LIMESettings.java`
- Modify: `LimeStudio/app/src/main/java/org/limeime/ui/LIMEPreference.java`
- Modify: `LimeStudio/app/src/androidTest/java/org/limeime/LIMEPreferenceTest.java`
- Test: `LimeStudio/app/src/test/java/org/limeime/ActivityEdgeToEdgePolicyTest.java`

- [ ] Keep `EdgeToEdge.enable()` gated to API 35+ and preserve each activity's prior pre-35 decor-fitting behavior.
- [ ] Replace the deprecated color assertion with API-policy and unobscured-content checks.
- [ ] Run policy/unit tests and affected activity instrumentation tests.
- [ ] Capture API 25 setup/preferences screenshots and compare with the master baseline.
- [ ] Commit edge-to-edge compatibility independently.

### Task 4: Full verification

- [ ] Run JVM tests, Android lint, and the full connected suite on a clean API 25 emulator.
- [ ] Run the full connected suite on API 36 with a matching debug signature.
- [ ] Install phonetic and dayi, confirm IM/config/mapping rows, and capture candidate behavior for both.
- [ ] Report any remaining failures with exact evidence; do not classify timeouts as passes.
