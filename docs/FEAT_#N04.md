# feat#N04 — Unified Google Play and App Store support area

## Source request

Jeremy requested this product work on 2026-07-16 after reviewing how to respond to an iOS App Store report about an imported `.cin` table that could not produce text.

## Status

Confirmed product work. Not implemented yet.

Until the web support area is available, direct users who need private troubleshooting or file inspection to:

```text
limeimetw@gmail.com
```

## Problem

The App Store `App Support` URL currently leads to the general online manual. The manual contains troubleshooting, FAQ, and GitHub links, but it does not provide an obvious support intake area for ordinary Google Play and App Store users.

Sending store reviewers through the manual and then to GitHub is unnecessarily indirect. Maintaining unrelated support destinations for each store also increases management complexity and makes scam or impersonation links harder for users to recognize.

## Product direction

Create one canonical LIME support area on the official website, with clear Android and iOS sections. Use the official store metadata and stable app identifiers to establish a trusted path:

- Google Play package: `org.limeime`
- Apple App Store ID: `6784694460`
- Google Play listing: `https://play.google.com/store/apps/details?id=org.limeime`
- Apple App Store listing: `https://apps.apple.com/app/id6784694460`
- Support email: `limeimetw@gmail.com`
- Public technical reports: `https://github.com/lime-ime/limeime/issues`

Both Google Play and App Store support metadata should point to this same canonical support area, optionally using platform-specific anchors or parameters. The page should link back to the exact official store listings so users can distinguish legitimate LIME support from scam pages or impersonators.

Do not introduce a separate LIME user-account system unless a concrete support requirement justifies it. Prefer the stores' existing app identity and metadata links over adding authentication and account-management complexity.

## Initial page scope

The support area should provide:

1. A prominent `寄信給萊姆小編` action using `limeimetw@gmail.com` for private reports and attachments.
2. Separate Android and iOS troubleshooting entry points.
3. Direct links to the user manual, FAQ, and existing troubleshooting pages.
4. A public GitHub Issues option for users comfortable with public technical reporting.
5. A reporting checklist covering platform, device, OS version, LIME version, input method, reproduction steps, sample input code, and relevant attachments.
6. A privacy warning not to send passwords, verification codes, private typed content, unrelated personal data, or full databases unless specifically requested.
7. Clear official-identity wording: public support identity is `萊姆小編`. Never expose individual author/team identities.
8. Canonical Google Play and App Store badges/links using the real package and app IDs.
9. Scam guidance stating that LIME support will not request passwords, payment credentials, verification codes, signing keys, or remote account access.

## Store integration

After the page is published and verified:

- Update Google Play support/website metadata to use the canonical support URL where the console permits it.
- Update the Apple App Store `App Support` URL to the canonical support URL.
- Verify both public store listings retain the exact URL and that redirects preserve the expected Android/iOS destination.
- Keep one support page and one support email as the canonical intake layer rather than maintaining separate support systems per store.

## Immediate operating rule

Before feat#N04 ships, email support is the practical path for store users who need private troubleshooting or must attach `.cin`, screenshots, or recordings. Store responses should ask users to email `limeimetw@gmail.com` directly instead of sending them through the manual to find GitHub.

## Acceptance criteria

- The official LIME website has a clear support landing page accessible without a user account.
- Google Play and App Store metadata link to the canonical page.
- The page shows the verified `org.limeime` and `6784694460` store destinations.
- Email support, manual/FAQ, and GitHub options are understandable to non-technical users.
- Private versus public reporting is clearly distinguished.
- Scam and sensitive-information warnings are visible.
- Links are verified from the public store listings after deployment.
