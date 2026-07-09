# Manual Visual Designer

This role reviews how `docs/manuals/**/*.md` renders as a GitHub Pages manual. It owns visual hierarchy, screenshot placement, CSS component use, mobile readability, and warning visibility.

## Design Goal

Manual pages should feel like practical product documentation, not raw notes. A reader should quickly know:

- whether the page matches their situation
- which screen or tab to open
- what success looks like
- where to go when the result is wrong

## CSS Components

The manual may use the following HTML/CSS components in Markdown. Styles live in `docs/assets/css/style.scss`.

### Task Entry

Use `.manual-hero` for the page's first task-oriented entry point.

```html
<div class="manual-hero">
  <p class="manual-kicker">快速開始</p>
  <h1>先啟用鍵盤，再處理碼表</h1>
  <p>第一次使用請走啟用流程；換機使用者請先備份舊裝置資料庫。</p>
</div>
```

### Routing Cards

Use `.manual-card-grid` and `.manual-card` for choices such as new setup, device migration, or troubleshooting.

```html
<div class="manual-card-grid">
  <a class="manual-card" href="quick-start.md">
    <strong>第一次安裝</strong>
    <span>完成系統鍵盤啟用。</span>
  </a>
  <a class="manual-card" href="database-management.md">
    <strong>換機還原</strong>
    <span>從舊裝置備份完整資料庫。</span>
  </a>
</div>
```

### Screenshot Pair

Use `.manual-screenshot-pair` for iPhone/Android pairs from `docs/LIME_SETTINGS.md`.

```html
<div class="manual-screenshot-pair">
  <figure>
    <img src="../assets/lime_settings_ios_setup.png" alt="iPhone LIME 設定分頁">
    <figcaption>iPhone</figcaption>
  </figure>
  <figure>
    <img src="../assets/lime_settings_android_setup.png" alt="Android LIME 設定分頁">
    <figcaption>Android</figcaption>
  </figure>
</div>
```

### Notes and Warnings

```html
<div class="manual-note">
  「允許完整取用」是選用功能解鎖，用於備份資料庫、按鍵震動回饋與編輯字根資料表。
</div>

<div class="manual-warning">
  還原資料庫會覆蓋目前資料。還原前請先備份。
</div>
```

## Review Rules

- The first screen should show the page purpose and primary task entry.
- Screenshots must sit near the steps they explain.
- High-risk information must use `.manual-warning`.
- Permission, platform difference, and data-overwrite notes should use `.manual-note` or `.manual-warning`.
- Use numbered lists for operations.
- Use tables only for comparison; if a table is wide or dense, use grouped lists.
- The mobile layout must stack cards and screenshots cleanly.
- Use HTML sparingly; do not make a Markdown page difficult to maintain.

## Reject If

- The page is a plain text wall.
- Screenshots are raw Markdown dumps without context or controlled layout.
- A screenshot appears far away from the relevant task.
- A warning, permission note, or data-overwrite risk is buried in normal prose.
- Navigation is only a link list and does not help users choose.
- Tables are used as layout hacks.
