# Google Play Submission Checklist — LimeIME

Preparation checklist for publishing **LIME IME 6** (`org.limeime`) to the Google Play Store.

> **Decisions locked:** Personal developer account · Free app · privacy policy hosted on GitHub Pages.
> **Prepared content is ready to paste** — see [GOOGLE_PLAY_LISTING.md](GOOGLE_PLAY_LISTING.md) (store text, Data Safety answers, asset spec) and [ANDROID_PRIVACY.md](ANDROID_PRIVACY.md) (privacy policy draft, EN + zh-TW).

Current build facts (from [LimeStudio/app/build.gradle](../LimeStudio/app/build.gradle)):

| Field | Value |
| --- | --- |
| `applicationId` (Play) | **`org.limeime`** |
| Java `namespace` | `net.toload.main.hd` (unchanged — internal package, not the app id) |
| `versionName` | `6.1.18` |
| `versionCode` | `2026` |
| `minSdkVersion` | `21` (Android 5.0) |
| `targetSdkVersion` / `compileSdkVersion` | `36` |
| App type | Input Method Editor (IME) + settings/launcher activity |
| License | GPL v3 |

> The Play package id is **`org.limeime`**. *(Note: current beta builds still ship
> as `net.toload.main.hd2026`; `applicationId` is switched to `org.limeime` at Play
> onboarding — edit [build.gradle](../LimeStudio/app/build.gradle#L34) and the
> [test assertion](../LimeStudio/app/src/androidTest/java/net/toload/main/hd/ApplicationTest.java#L74) then.)*

**Public store listing URL:** `https://play.google.com/store/apps/details?id=org.limeime`
(`market://details?id=org.limeime` opens the Play app directly). The 設定 tab's in-app
**rating prompt** (`RateCard`, [LIME_SETTINGS.md §4.4](LIME_SETTINGS.md)) deep-links here to
invite a 5-star review — the iOS counterpart uses `https://apps.apple.com/app/id6784694460?action=write-review`.

---

## 1. Developer account & one-time setup

- [ ] **Google Play Developer account** registered (one-time US$25 fee). Verify identity/D-U-N-S if asked — Google requires identity + address verification for all developer accounts.
- [x] **Account type decided: Personal** (no D-U-N-S needed). Org accounts would require a D-U-N-S number.
- [ ] Confirm the **package name `org.limeime` is available** for this account. The package name is permanent and cannot be changed or reused once published. This id is **version-neutral and ownership-neutral** — chosen over the original `net.toload.*` prefix (named by the now-inactive original developer) and over a year-stamped `hd2026`, so it reflects the current project and never looks dated.
- [ ] Add required team members and accept the **Developer Distribution Agreement**.

---

## 2. App signing (blocker — current release is unsigned)

The `signingConfigs {}` block in [LimeStudio/app/build.gradle](../LimeStudio/app/build.gradle#L30) is currently **empty**, and the release type uses `minifyEnabled false`. Before upload:

- [ ] **Enroll in Play App Signing** (now mandatory for new apps). Google holds the app signing key; you keep an **upload key**.
- [ ] Generate (or locate) the **upload keystore** (`.jks`/`.keystore`). Store it and its passwords securely **outside** the repo and **off** version control.
- [ ] Wire a real `signingConfig` into the `release` build type (or sign via `bundletool` / Play Console). Do **not** commit keystore or passwords — load them from `~/.gradle/gradle.properties`, env vars, or a local `keystore.properties` that is git-ignored.
- [ ] Record the **SHA-256 / SHA-1** of the upload certificate (needed if any Google service config relies on it).
- [ ] Back up the upload key in at least two secure locations — losing it requires a Play Console upload-key reset.

---

## 3. Build the release artifact

- [ ] **Build an Android App Bundle (`.aab`)** — Play requires AAB for new submissions, not APK. The current Gradle config produces APKs (`LIMEHD2026-6.1.17.apk`); add `bundleRelease` to the workflow.

  ```bash
  ./gradlew :app:bundleRelease
  ```

- [ ] Confirm `versionCode` is **higher than any previously uploaded** build (currently `2026`). Each upload must strictly increase it.
- [ ] Verify the AAB is **signed with the upload key**.
- [ ] Consider enabling `minifyEnabled true` + R8/ProGuard for the release (currently `false`). If enabled, **test the shrunk build end-to-end** — IME services, AIDL, and reflection paths are easy to break with shrinking. Keep [proguard-rules.pro](../LimeStudio/app/proguard-rules.pro) updated.
- [ ] Test-install the exact release bundle locally via `bundletool build-apks --connected-device` before uploading.

---

## 4. Target API level & policy compliance

- [ ] **Target API level** meets Play's current minimum for new app/update submissions. `targetSdkVersion 36` is current — keep it at or above Google's enforced minimum at submission time.
- [ ] Review **per-permission policies** for each declared permission in [AndroidManifest.xml](../LimeStudio/app/src/main/AndroidManifest.xml):
  - `RECORD_AUDIO` — voice input. **Sensitive permission**: must be justified in the Data Safety form and Play Console permissions declaration; clearly tied to the optional voice-typing feature.
  - `POST_NOTIFICATIONS`, `VIBRATE`, `INTERNET`, `ACCESS_NETWORK_STATE` — standard; confirm each is still used.
- [ ] Confirm **no foreground-service / background-location / all-files-access / SMS-call-log** sensitive permissions are present (none currently — keep it that way to avoid extra declaration forms).
- [ ] **IME-specific note:** As an Input Method Editor, the app can capture user-typed text. Play scrutinizes keyboards heavily. Be ready to attest that LimeIME does **not** transmit keystrokes/personal data off-device. Map this to the Data Safety form (Section 8).

---

## 5. Store listing — text assets

> ✅ **Drafted** (EN + zh-TW): title, short/full description, category, contact, release notes — see [GOOGLE_PLAY_LISTING.md → Section A](GOOGLE_PLAY_LISTING.md#a-store-listing-text-section-5). Just review and paste.

- [ ] **App title** (≤ 30 chars).
- [ ] **Short description** (≤ 80 chars).
- [ ] **Full description** (≤ 4000 chars) — list supported input methods (Zhuyin/Bopomofo, Cangjie, Pinyin, Dayi, English, etc.), emoji, voice input, backup/restore.
- [ ] Decide **localized listings** (at minimum `zh-TW` / `zh-Hant` and `en-US`, given the user base). Translate title, descriptions, and graphics per locale.
- [ ] **App category:** Tools (or Productivity). Tags as appropriate.
- [ ] **Contact details:** required Play contact email (use a dedicated alias, not a personal address), website `https://lime-ime.github.io/limeime/`, optional phone. No email shown in public copy — point users to GitHub Issues.

---

## 6. Store listing — graphic assets

> ⚠️ **Spec sheet ready** — see [GOOGLE_PLAY_LISTING.md → Section E](GOOGLE_PLAY_LISTING.md#e-graphic-asset-spec-sheet-section-6). Note: the in-repo `logo.png` is only 144×144, **too small for the 512 icon** — regenerate from a ≥512px master, do not upscale.

- [ ] **App icon** — 512×512 PNG (32-bit, with alpha). Derive from `@drawable/logo`.
- [ ] **Feature graphic** — 1024×500 PNG/JPG (required).
- [ ] **Phone screenshots** — 2–8, PNG/JPG, 16:9 or 9:16, min 320px side. Show the keyboard in real apps for several input methods.
- [ ] **7" and 10" tablet screenshots** — recommended (the app supports tablet/landscape layouts).
- [ ] Optional **promo video** (YouTube URL).
- [ ] Ensure no screenshots show another app's copyrighted UI in a misleading way, and no placeholder/lorem-ipsum content.

---

## 7. Content rating

> ✅ **Answers prepared** — see [GOOGLE_PLAY_LISTING.md → Section D](GOOGLE_PLAY_LISTING.md#d-content-rating-iarc--prepared-answers-section-7). Expected result: **Everyone / PEGI 3**.

- [ ] Complete the **IARC content rating questionnaire** in Play Console. A keyboard with no objectionable content should rate **Everyone**, but the questionnaire is mandatory before release.
- [ ] If the app shows user-generated or web content, answer those questions honestly (LimeIME is largely offline input — confirm `INTERNET` use is limited to updates/dictionary fetch, not arbitrary web content).

---

## 8. Data safety form (mandatory)

> ✅ **Answers prepared** — see [GOOGLE_PLAY_LISTING.md → Section B](GOOGLE_PLAY_LISTING.md#b-data-safety-form-answers-section-8). Position: **"No data collected, no data shared"** (typing/learning stays on-device).

- [ ] Complete the **Data Safety** section in Play Console. Declare for each data type: collected? shared? encrypted in transit? user can request deletion?
- [ ] **Keyboard-specific honesty:** state explicitly whether typed text, learned words, or user dictionaries leave the device. Per the [#103 spec](./%23103_ISSUE.md), per-user `score` learning data is **private and local** — declare it as on-device, not collected/shared.
- [ ] If voice (`RECORD_AUDIO`) audio is processed by a third-party recognizer (system `RECOGNIZE_SPEECH` intent), note that audio handling is delegated to the device's speech service, not collected by LimeIME.
- [ ] Backup/restore writes a user-chosen `.zip` via SAF — clarify this is user-initiated local/SAF storage, not server collection.

---

## 9. Privacy policy

> ✅ **Drafted** (EN + zh-TW): [ANDROID_PRIVACY.md](ANDROID_PRIVACY.md). Content matches the Data Safety answers.

- [x] **Privacy policy written** — [ANDROID_PRIVACY.md](ANDROID_PRIVACY.md), covering on-device learning, voice input handling, backup files, permissions, and contact info.
- [ ] **Publish to GitHub Pages** (repo already uses Jekyll). Add `ANDROID_PRIVACY.md` to the Pages includes (like `LICENSE.md` in [_config.yml](../_config.yml)) so it serves at a public HTTPS URL — target `https://lime-ime.github.io/limeime/PRIVACY/`.
- [ ] Add the live privacy policy URL in **Play Console → App content → Privacy policy**.

---

## 10. App content / declarations (Play Console "App content" section)

> ✅ **Answers prepared** — see [GOOGLE_PLAY_LISTING.md → Section C](GOOGLE_PLAY_LISTING.md#c-app-content-declarations-section-10--prepared-answers).

- [ ] **Privacy policy** (Section 9).
- [ ] **Ads** — declare whether the app contains ads (LimeIME: none → declare "No ads").
- [ ] **App access** — if any feature is behind login, provide test credentials. LimeIME has no login → declare "all functionality available without restrictions."
- [ ] **Content rating** (Section 7).
- [ ] **Target audience & content** — set age groups; confirm not primarily directed at children (avoids Families policy requirements) unless intended.
- [ ] **Data safety** (Section 8).
- [ ] **Government apps / financial / health** — N/A; confirm.
- [ ] **News app** — N/A; confirm.

---

## 11. Pre-launch testing

- [ ] Use a **closed/internal testing track** first. Upload the AAB to **Internal testing**, add testers, and validate install + IME enable flow on real devices.
- [ ] Review the **Play Console Pre-launch report** (automated crawler on real devices) for crashes, accessibility, and security warnings.
- [ ] Verify on a clean install: enabling LimeIME in **Settings → Languages & input**, switching to it, and the fresh-install IM activation fix (commit `680d34e5`).
- [ ] Test on **min (API 21) and recent (API 36)** devices, phone and tablet, portrait and landscape.

---

## 12. Pricing & distribution

- [x] **Pricing decided: Free** (GPL app) — note: free→paid is not reversible later.
- [ ] Select **countries/regions** for distribution.
- [ ] Confirm **device categories** (phone, tablet, Chromebook). Foldable/large-screen support is a plus.
- [ ] **Content guidelines & US export laws** checkboxes acknowledged.

---

## 13. Legal / open-source housekeeping

- [ ] Confirm **GPL v3** distribution via Play is acceptable for all bundled assets and that source remains available (GitHub).
- [ ] **Third-party attribution:** the bundled English `dictionary.db` `basescore` is derived from **Google Books Ngrams** under **CC BY 3.0** — already disclosed in [LICENSE.md](../LICENSE.md). Ensure attribution remains visible (in-app About and/or store listing acknowledgements).
- [ ] Verify no bundled dictionary/data has a license that forbids redistribution through a commercial store.
- [ ] Confirm app icon, screenshots, and graphics are original or properly licensed.

---

## 14. Pre-submission final review

- [ ] All **mandatory Play Console sections show green/complete** (Store listing, Content rating, Data safety, App content, Pricing).
- [x] Release notes / "What's new" **drafted** (EN + zh-TW) in [GOOGLE_PLAY_LISTING.md](GOOGLE_PLAY_LISTING.md#release-notes--whats-new-for-6117--500-chars). Covers English completion fixes ([#103](./%23103_ISSUE.md)) and the fresh-install IM activation fix.
- [ ] AAB uploaded to the **Production** track (after testing tracks pass).
- [ ] Staged **rollout percentage** chosen (e.g., start at 20%).
- [ ] Submit for review and monitor Play Console for policy review status.

---

## Quick "blockers first" summary

The items most likely to stop a first submission, in order:

1. **No signing config** — release build is unsigned ([build.gradle](../LimeStudio/app/build.gradle#L30)). Set up Play App Signing + upload key. (Section 2)
2. **No AAB** — Gradle currently outputs APK only; add `bundleRelease`. (Section 3)
3. **Privacy policy URL** — required (and doubly so due to `RECORD_AUDIO`). (Section 9)
4. **Data Safety form** — must truthfully cover keyboard typing/learning data. (Section 8)
5. **Sensitive-permission justification** — `RECORD_AUDIO` voice input. (Section 4)
6. **Graphic assets** — 512 icon + 1024×500 feature graphic + screenshots. (Section 6)
