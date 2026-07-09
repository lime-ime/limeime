/* 設定 — Android App Setup tab. Grounded in the real Android source
   LimeStudio/app/src/main/res/layout/fragment_setup.xml + SetupFragment.java
   (horizontal brand row, neutral status card, About card), but kept VISUALLY
   ALIGNED to the iOS SetupTab per the user's request (b): same structure —
   brand hero, success status, 設定萊姆輸入法 step guide, 前往設定 button, and a
   three-chip About footer (使用手冊 / 版權說明 / 原始碼) + copyright banner.
   Theme colour is inherited from the system (Material You) — no in-app control. */
(function () {
  const { Icon, Button } = window.LimeM3;

  const LICENSE_URL = "https://lime-ime.github.io/limeime/pages/license.html"; // R.string.url_license_limeime
  const MANUAL_URL = "https://lime-ime.github.io/limeime/pages/index.html";
  const GITHUB_URL = "https://github.com/lime-ime/limeime";                    // R.string.url_github_limeime

  const FG_GREEN = "#2e7d32"; // @color/setup_status_fg_green
  const STATUS_BG = "color-mix(in srgb, #808080 12%, transparent)"; // @color/setup_status_bg

  // Status card — neutral background, icon + text in the state colour (iOS parity).
  function StatusCard() {
    return React.createElement("div", {
      style: { display: "flex", alignItems: "center", gap: 10, padding: "12px 14px", borderRadius: 12, background: STATUS_BG },
    },
      React.createElement(Icon, { name: "check_circle", size: 20, fill: true, color: FG_GREEN }),
      React.createElement("span", { style: { font: "500 15px/20px 'Roboto', var(--font-sans)", color: FG_GREEN } }, "萊姆輸入法已啟用")
    );
  }

  // Green M3 toggle visual used in the activation step guide (matches iOS GreenToggle).
  function GreenToggle() {
    return React.createElement("div", { style: { width: 30, height: 18, borderRadius: 9, background: "var(--md-primary)", position: "relative", flex: "0 0 auto" } },
      React.createElement("div", { style: { position: "absolute", right: 2, top: 2, width: 14, height: 14, borderRadius: "50%", background: "var(--md-on-primary)", boxShadow: "0 1px 1px rgba(0,0,0,.18)" } }));
  }
  function StepRow({ icon, text }) {
    return React.createElement("div", { style: { display: "flex", alignItems: "center", gap: 16 } },
      React.createElement("div", { style: { width: 32, display: "flex", justifyContent: "center", color: "var(--md-primary)" } }, icon),
      React.createElement("div", { style: { font: "400 17px/22px 'Roboto', var(--font-sans)", color: "var(--md-on-surface)" } }, text)
    );
  }

  // §4.3 — Installed-IM status. Mirrors the IM tab so Setup can surface a problem
  // and route the user to fix it (iOS parity):
  //   none → error (red), disabled → warning (orange), ok → success (green).
  const IM_STATUS = {
    none:     { fg: "var(--md-error)", icon: "error",        text: "尚未安裝任何輸入法",      cta: "安裝輸入法" },
    disabled: { fg: "#b56500",         icon: "warning",      text: "已安裝輸入法，但全部已停用", cta: "啟用輸入法" },
    ok:       { fg: FG_GREEN,          icon: "check_circle", text: "輸入法已就緒，隨時可用",   cta: null }, // text overridden with installed count
  };

  // §4 — LIME inline-dictation microphone permission. Optional section shown when
  // inline dictation is enabled; three RECORD_AUDIO permission states (md spec).
  const VOICE_STATUS = {
    granted: { fg: FG_GREEN,          icon: "mic",     text: "萊姆內建語音輸入已啟用",   note: "可直接在萊姆鍵盤內使用語音輸入。", cta: null },
    askable: { fg: "var(--md-error)", icon: "mic_off", text: "萊姆內建語音輸入尚未啟用", note: "若要在萊姆鍵盤內直接語音輸入，請允許麥克風權限；也可略過，改用 Google 語音輸入。", cta: "允許麥克風權限" },
    denied:  { fg: "#b56500",         icon: "warning", text: "需至系統設定開啟麥克風權限", note: "Android 已停止顯示授權視窗。若要使用萊姆內建語音輸入，請前往系統設定，點選「權限」→「麥克風」→「允許」。", cta: "前往系統設定" },
  };

  function VoiceSection({ voiceStatus = "askable", onAllowMic }) {
    const m = VOICE_STATUS[voiceStatus] || VOICE_STATUS.askable;
    return React.createElement("div", { style: { display: "flex", flexDirection: "column", gap: 12, paddingTop: 4 } },
      React.createElement("div", { style: { height: 1, background: "var(--md-outline-variant)", margin: "0 -24px 4px" } }),
      React.createElement("div", {
        style: { display: "flex", alignItems: "center", gap: 10, padding: "12px 14px", borderRadius: 12, background: STATUS_BG },
      },
        React.createElement(Icon, { name: m.icon, size: 20, fill: true, color: m.fg }),
        React.createElement("span", { style: { font: "500 15px/20px 'Roboto', var(--font-sans)", color: m.fg } }, m.text)
      ),
      React.createElement("div", { style: { font: "400 13px/19px 'Roboto', var(--font-sans)", color: "var(--md-on-surface-variant)", padding: "0 2px" } }, m.note),
      m.cta && React.createElement(Button, { variant: "filled", full: true, onClick: onAllowMic }, m.cta)
    );
  }

  function IMStatusSection({ imStatus = "none", imCount = 0, onManageIM }) {
    const m = IM_STATUS[imStatus] || IM_STATUS.none;
    const text = imStatus === "ok" ? ("已安裝 " + imCount + " 個輸入法")
      : imStatus === "disabled" ? ("已安裝 " + imCount + " 個輸入法，但全部停用")
      : m.text;
    return React.createElement("div", { style: { display: "flex", flexDirection: "column", gap: 16, paddingTop: 4 } },
      React.createElement("div", { style: { height: 1, background: "var(--md-outline-variant)", margin: "0 -24px 4px" } }),
      React.createElement("div", {
        style: { display: "flex", alignItems: "center", gap: 10, padding: "12px 14px", borderRadius: 12, background: STATUS_BG },
      },
        React.createElement(Icon, { name: m.icon, size: 20, fill: true, color: m.fg }),
        React.createElement("span", { style: { font: "500 15px/20px 'Roboto', var(--font-sans)", color: m.fg } }, text)
      ),
      m.cta && React.createElement(Button, { variant: "filled", full: true, onClick: onManageIM }, m.cta)
    );
  }

  // Compact link chip aligned to the iOS footer: equal-width, rounded, tonal fill,
  // icon over label + external glyph.
  function LinkChip({ href, icon, external, children }) {
    return React.createElement("a", { href, target: "_blank", rel: "noopener noreferrer",
      style: { flex: 1, display: "flex", flexDirection: "column", alignItems: "center", gap: 7,
        padding: "15px 8px 13px", borderRadius: 14, background: "var(--md-surface-container-high)",
        color: "var(--md-primary)", textDecoration: "none", WebkitTapHighlightColor: "transparent" } },
      React.createElement(Icon, { name: icon, size: 22, color: "var(--md-primary)" }),
      React.createElement("span", { style: { display: "inline-flex", alignItems: "center", gap: 3, font: "500 14px/18px 'Roboto', var(--font-sans)" } },
        children, external && React.createElement(Icon, { name: "open_in_new", size: 13, style: { opacity: .7 } }))
    );
  }

  function AndroidSetupTab({ imStatus, imCount, voiceStatus, onManageIM, onAllowMic }) {
    return React.createElement("div", { style: { padding: "8px 24px 28px", display: "flex", flexDirection: "column", gap: 24 } },
      // Brand hero — plain logo + wordmark, horizontal (aligned to iOS)
      React.createElement("div", { style: { display: "flex", flexDirection: "row", alignItems: "center", justifyContent: "center", gap: 16, paddingTop: 20 } },
        React.createElement("img", { src: "../../assets/lime-logo-android.png", alt: "LIME", style: { width: 92, height: 92, objectFit: "contain" } }),
        React.createElement("div", { style: { font: "700 30px/36px 'Roboto', var(--font-sans)", color: "var(--md-on-surface)" } }, "萊姆輸入法")
      ),
      // Heading leads the section; the activation status banner sits BELOW it.
      React.createElement("div", { style: { font: "700 28px/34px 'Roboto', var(--font-sans)", color: "var(--md-on-surface)" } }, "設定萊姆輸入法"),
      React.createElement(StatusCard),
      React.createElement("div", { style: { display: "flex", flexDirection: "column", gap: 16 } },
        React.createElement(StepRow, { icon: React.createElement(Icon, { name: "keyboard", size: 24, color: "var(--md-primary)" }), text: "輕觸「鍵盤」" }),
        React.createElement(StepRow, { icon: React.createElement(GreenToggle), text: "開啟萊姆輸入法" }),
        React.createElement(StepRow, { icon: React.createElement(GreenToggle), text: "開啟「允許完整取用」" })
      ),
      React.createElement("div", { style: { font: "400 15px/20px 'Roboto', var(--font-sans)", color: "var(--md-on-surface-variant)", textAlign: "center" } },
        "完整取用用於備份資料庫、按鍵震動回饋與編輯字根資料表。不開啟也能正常輸入、安裝或匯入輸入法，萊姆輸入法不會收集或傳送任何個人資料。"),
      React.createElement(Button, { variant: "filled", full: true }, "前往設定"),
      React.createElement("div", { style: { font: "400 13px/18px 'Roboto', var(--font-sans)", color: "var(--md-on-surface-variant)", textAlign: "center" } },
        "若設定未直接顯示萊姆輸入法，請到「設定」>「系統」>「語言與輸入」>「螢幕鍵盤」開啟。"),
      // §4.3 — Installed-IM status section.
      React.createElement(IMStatusSection, { imStatus, imCount, onManageIM }),
      // §4 — LIME inline-dictation microphone permission section.
      React.createElement(VoiceSection, { voiceStatus, onAllowMic }),
      // About footer — three equal-width chips + one-line copyright (aligned to iOS)
      React.createElement("div", { style: { display: "flex", flexDirection: "column", gap: 16, paddingTop: 10 } },
        React.createElement("div", { style: { height: 1, background: "var(--md-outline-variant)", margin: "0 -24px" } }),
        React.createElement("div", { style: { display: "flex", gap: 10 } },
          React.createElement(LinkChip, { href: MANUAL_URL, icon: "menu_book" }, "使用手冊"),
          React.createElement(LinkChip, { href: LICENSE_URL, icon: "description" }, "版權說明"),
          React.createElement(LinkChip, { href: GITHUB_URL, icon: "code", external: true }, "原始碼")
        ),
        React.createElement("div", { style: { font: "400 13px/18px 'Roboto', var(--font-sans)", color: "var(--md-on-surface-variant)", textAlign: "center", paddingTop: 6 } },
          "© LIME 萊姆輸入法 6.1.15 - 2026")
      )
    );
  }
  window.AndroidSetupTab = AndroidSetupTab;
})();
