# APK / AAB Release & Sideload Workflow

This document describes how LimeIME (Android, module `LimeStudio`) is built and
released, the two distinct distribution channels (Google Play vs. off-Play
sideload), and the **signing keys** involved — because confusing them causes the
"app is unusable, redirects user back to Google Play to redownload" failure.

App identity: `applicationId = org.limeime`, namespace `net.toload.main.hd`.

---

## TL;DR

| Channel | Artifact | Signed with | Where it comes from | Sideloads cleanly? |
|---|---|---|---|---|
| **Google Play** | AAB → split APKs | **Google's Play App Signing key** | Uploaded AAB, re-signed by Google | ❌ No — Play redirects |
| **Off-Play / direct** | universal APK | **Your upload key** | Android Studio signing wizard (local) | ✅ Yes |

**Rule:** Never hand users an APK **downloaded from the Play Console**. Those are
Google-signed and trigger the Play Store "go back to Google Play to redownload"
redirect when sideloaded. For sideloading, build locally with your own key.

---

## The two signing keys (read this first)

Google Play App Signing splits signing into two keys:

| Key | Who holds it | Signs |
|---|---|---|
| **Upload key** | You | The AAB you upload to Play; local sideload APKs |
| **Play App Signing key** | Google (you never see it) | What users download from the Play Store |

The upload keystore is a private file kept **outside the repo** (never committed).
You select it in the Android Studio signing wizard when building (Workflow B).

### How Play re-signing works (the upload key is NOT replaced)

Each release:

1. You sign the AAB with your **upload key** and upload it.
2. Google **verifies** the upload-key signature — this is purely a gatekeeper check
   that confirms it's really you uploading.
3. Google **strips** your upload signature and **re-signs** the generated APKs with
   its own **Play App Signing key** before serving them to users.

So "re-sign" does not replace or rotate your upload key — your upload key keeps
working for every future release, and it never signs what the end user installs.
The Play App Signing key also stays constant (unless you deliberately rotate it),
which is what lets users keep updating the app.

> If you ever lose the upload keystore, you request an **upload key reset** in the
> Play Console — that replaces only the *upload* key. The Play App Signing key is
> untouched, so existing users keep updating seamlessly.

Consequences:

- An APK you build + sign with your **upload key** has a **different signature**
  from the Play Store version (which is Google-signed).
- ✅ Installs fine on a device that has **no** LimeIME installed.
- ❌ **Cannot install over** an existing **Play-Store** copy — Android rejects it
  with a signature mismatch. The user must uninstall the Play version first
  (losing app data). This is inherent to off-Play distribution; the build cannot
  change it.

### How to tell which key signed an APK

Inspect the signer certificate with `apksigner` (from the Android SDK build-tools):

```bash
apksigner verify --print-certs <path-to-apk>
```

- **Your own build (safe for sideload):** the `Signer #1 certificate DN` is the
  LIME upload key — `C=TW, ST=Taiwan, L=Taipei, O=LIME IME Team, OU=LIME IME Team,
  CN=LIME IME` — with no Source Stamp line.
- **Console download (will redirect — do NOT distribute):** DN shows
  `CN=Android, O=Google Inc.` and a `Source Stamp Signer` line is present.

---

## Workflow A — Google Play release (AAB)

This is the normal store-distribution path.

1. Bump `versionCode` / `versionName` in
   [LimeStudio/app/build.gradle](../LimeStudio/app/build.gradle) (`defaultConfig`).
2. Build the AAB, signed with your **upload key**, via Android Studio
   *Build > Generate Signed Bundle / APK > Android App Bundle*. (For a headless
   build, run `cd LimeStudio && ./gradlew bundleRelease` with signing configured
   per the command-line setup in Workflow B; output: `app/build/outputs/bundle/release/`.)
3. Upload the `.aab` to the Play Console (Internal testing → Production track).
4. Google **re-signs** it with the Play App Signing key and generates the split
   APKs users actually download.

### Testing a Play release BEFORE shipping to users

Do **not** sideload an App-bundle-explorer download to test. Instead:

- **Internal Testing track (recommended):** upload the AAB, open the opt-in link
  on the device, install **through the Play Store**. This installs the real
  Play-signed split set the proper way — no redirect.
- **`bundletool` (offline):**
  ```bash
  bundletool build-apks --bundle=app-release.aab --output=app.apks --connected-device
  bundletool install-apks --apks=app.apks
  ```
  Note: this can still trigger the Play redirect on a device signed into the
  Google account that owns the app. Internal Testing is the reliable path.

> The APKs under `LimeStudio/app/release/` named `LIMEHD2026-*.apk` are historical
> **Console downloads** (Google-signed). They are kept for archival/inspection
> only and are **not** suitable for sideloading.

---

## Workflow B — Off-Play sideload release (local universal APK)

Use this to give users an APK to install **outside** the Play Store (direct
download, China, alternative stores).

### Standard: Android Studio wizard

This is the normal way LimeIME APKs are built.

1. *Build > Generate Signed Bundle / APK > APK*.
2. Select your upload keystore, enter the passwords/alias.
3. Choose the `release` build variant, finish.

The wizard signs with your **upload key**, producing a standalone universal APK at
`LimeStudio/app/release/LIMEHD<versionCode>-<versionName>.apk`
(e.g. `LIMEHD202661221-6.1.22.apk`). It installs via `adb install` or by tapping it
on-device — no Play redirect. Confirm the signer before distributing (see
"How to tell which key signed an APK" above):

```bash
apksigner verify --print-certs \
  LimeStudio/app/release/LIMEHD<versionCode>-<versionName>.apk
# Expect Signer DN: ...O=LIME IME Team, CN=LIME IME  (NOT CN=Android, O=Google Inc.)
```

Distribute via GitHub Releases (per `.gitignore`, AABs are not committed; release
APKs are kept under `app/release/` by the `archiveReleaseApk` task).

### Alternative: command-line build (for CI / headless agents)

For a build without Android Studio, the gradle release `signingConfig` reads
credentials from a gitignored `keystore.properties` file. Create
[LimeStudio/keystore.properties](../LimeStudio/keystore.properties) with:

```properties
storeFile=<absolute path to your upload keystore, kept outside the repo>
storePassword=...
keyAlias=...
keyPassword=...
```

Then `cd LimeStudio && ./gradlew assembleRelease`. If the file is absent or still
has placeholder values, the release build falls back to **unsigned** (so the repo
still builds for anyone without the keystore). `keystore.properties`, `*.jks`, and
`*.keystore` are gitignored.

---

## Build internals (how outputs are named/archived)

In [LimeStudio/app/build.gradle](../LimeStudio/app/build.gradle):

- Output filename is forced to `LIMEHD${versionCode}-${versionName}.apk` via the
  `androidComponents.onVariants` block.
- `release` buildType uses `minifyEnabled true` + `shrinkResources true` +
  ProGuard (`proguard-rules.pro`).
- Custom tasks preserve prior release APKs and copy new ones into
  `app/release/`: `preserveReleaseApks` → `packageRelease` →
  `createReleaseApkListingFileRedirect` → `archiveReleaseApk` (chained via
  `afterEvaluate`).

---

## Troubleshooting

**Symptom:** Installed app immediately redirects user back to Google Play to
redownload.
**Cause:** The APK was a **Play-Console download** (Google-signed) sideloaded
outside Play. Play Protect/Store reclaims its own package.
**Fix:** Distribute a **local upload-key-signed** universal APK (Workflow B).
Verify the signer with `apksigner verify --print-certs <apk>` before shipping.

**Symptom:** Sideload install fails with a signature mismatch.
**Cause:** The device already has the **Play-Store** (Google-signed) copy; your
off-Play APK has a different signature.
**Fix:** User must uninstall the Play version first (data loss), or install your
APK only on devices without the Play copy.

**Symptom:** Release APK won't install (no signature).
**Cause:** Unsigned build — either the wizard wasn't pointed at a keystore, or (CLI
path) `keystore.properties` is missing/placeholder so gradle fell back to unsigned.
**Fix:** Rebuild via the wizard with your keystore, or complete the CLI signing
setup in Workflow B.

---

## Related files

- [LimeStudio/app/build.gradle](../LimeStudio/app/build.gradle) — signing config + release tasks
- [LimeStudio/keystore.properties](../LimeStudio/keystore.properties) — gitignored signing credentials (CLI build path)
- `apksigner verify --print-certs <apk>` — check upload-key vs Google-signed (Android SDK build-tools)
