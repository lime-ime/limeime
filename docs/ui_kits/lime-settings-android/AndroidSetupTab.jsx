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
  // Google Play listing for the rating prompt. The market:// scheme opens the
  // Play app directly; the https form is the web fallback.
  const PLAY_REVIEW_URL = "https://play.google.com/store/apps/details?id=org.limeime";

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

  // A row of five filled gold stars for the rating prompt.
  function StarRow({ size = 18 }) {
    const star = React.createElement("svg", { width: size, height: size, viewBox: "0 0 24 24", fill: "#FFB400" },
      React.createElement("path", { d: "M12 2l2.9 6.26 6.9.7-5.1 4.62 1.45 6.74L12 17.3 5.85 20.32 7.3 13.58 2.2 8.96l6.9-.7z" }));
    return React.createElement("div", { style: { display: "flex", gap: 4 } },
      Array.from({ length: 5 }, (_, i) => React.cloneElement(star, { key: i })));
  }

  // Material 3 dialog asking how to hide the rating card (已完成 / 以後再說 / 取消).
  // position: fixed is contained by the scaled #device, so it dims the whole screen
  // like a real MaterialAlertDialog.
  function DismissDialog({ onDone, onLater, onCancel }) {
    const action = (label, cb, emphasis) => React.createElement("button", {
      type: "button", onClick: cb,
      style: { height: 40, padding: "0 12px", border: "none", borderRadius: 20, background: "transparent",
        color: "var(--md-primary)", cursor: "pointer",
        font: (emphasis ? 600 : 500) + " 14px/1 'Roboto', var(--font-sans)" },
    }, label);
    return React.createElement("div", {
      onClick: onCancel,
      style: { position: "fixed", inset: 0, zIndex: 50, display: "flex", alignItems: "center",
        justifyContent: "center", padding: 24, background: "rgba(0,0,0,.32)" },
    },
      React.createElement("div", {
        onClick: (e) => e.stopPropagation(),
        style: { width: "100%", maxWidth: 312, borderRadius: 28, padding: "24px 24px 18px",
          background: "var(--md-surface-container-high)", boxShadow: "0 10px 40px rgba(0,0,0,.35)" },
      },
        React.createElement("div", { style: { font: "500 24px/32px 'Roboto', var(--font-sans)", color: "var(--md-on-surface)" } }, "隱藏評分邀請？"),
        React.createElement("div", { style: { font: "400 14px/20px 'Roboto', var(--font-sans)", color: "var(--md-on-surface-variant)", marginTop: 16 } },
          "如果您已給評分，選「已完成」即可不再顯示；還沒決定的話，選「以後再說」，我們稍後再提醒您。"),
        React.createElement("div", { style: { display: "flex", justifyContent: "flex-end", gap: 8, marginTop: 24 } },
          action("取消", onCancel, false),
          action("以後再說", onLater, false),
          action("已完成", onDone, true))
      )
    );
  }

  // Rating prompt card — invites a 5-star Google Play review, placed in the
  // Setup tab's app-info area (below the status sections, above About). The ×
  // opens DismissDialog. In this mockup the dismiss is in-memory (resets on reload);
  // production persists it to SharedPreferences — rating_prompt_dismissed (已完成,
  // permanent) / rating_prompt_snooze_until (以後再說, +14 days). See LIME_SETTINGS §4.4.
  function RateCard() {
    const [dismissed, setDismissed] = React.useState(false);
    const [confirm, setConfirm] = React.useState(false);
    if (dismissed) return null;
    return React.createElement(React.Fragment, null,
      React.createElement("div", { style: { position: "relative" } },
        React.createElement("a", { href: PLAY_REVIEW_URL, target: "_blank", rel: "noopener noreferrer",
          style: { display: "flex", alignItems: "center", gap: 14, textDecoration: "none",
            padding: "16px 18px", borderRadius: 16, background: "var(--md-surface-container-high)",
            WebkitTapHighlightColor: "transparent" } },
          React.createElement("div", { style: { flex: 1, display: "flex", flexDirection: "column", gap: 6, paddingRight: 18 } },
            React.createElement("div", { style: { font: "600 17px/22px 'Roboto', var(--font-sans)", color: "var(--md-on-surface)" } }, "喜歡萊姆輸入法嗎？"),
            React.createElement(StarRow, null),
            React.createElement("div", { style: { font: "400 14px/19px 'Roboto', var(--font-sans)", color: "var(--md-on-surface-variant)" } }, "到 Google Play 給個 5 星好評，支持作者持續開發。")
          ),
          React.createElement(Icon, { name: "chevron_right", size: 22, color: "var(--md-on-surface-variant)", style: { flex: "0 0 auto" } })
        ),
        // Dismiss × — own tap target, top-right corner; stopPropagation so it never
        // opens the store. Opens the confirm dialog.
        React.createElement("button", {
          type: "button", "aria-label": "隱藏",
          onClick: (e) => { e.preventDefault(); e.stopPropagation(); setConfirm(true); },
          style: { position: "absolute", top: 8, right: 8, width: 24, height: 24, borderRadius: "50%",
            border: "none", background: "transparent", color: "var(--md-on-surface-variant)",
            display: "flex", alignItems: "center", justifyContent: "center", cursor: "pointer", padding: 0 },
        }, React.createElement(Icon, { name: "close", size: 18 }))
      ),
      confirm && React.createElement(DismissDialog, {
        // 已完成 → permanent hide; 以後再說 → snooze. Both hide the card in this mockup.
        onDone: () => { setConfirm(false); setDismissed(true); },
        onLater: () => { setConfirm(false); setDismissed(true); },
        onCancel: () => setConfirm(false),
      })
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
        "萊姆輸入法僅需完整取用以啟用按鍵震動回饋。若不需要此功能，可不開啟。萊姆輸入法不會收集或傳送任何個人資料。"),
      React.createElement(Button, { variant: "filled", full: true }, "前往設定"),
      React.createElement("div", { style: { font: "400 13px/18px 'Roboto', var(--font-sans)", color: "var(--md-on-surface-variant)", textAlign: "center" } },
        "若設定未直接顯示萊姆輸入法，請到「設定」>「系統」>「語言與輸入」>「螢幕鍵盤」開啟。"),
      // §4.3 — Installed-IM status section.
      React.createElement(IMStatusSection, { imStatus, imCount, onManageIM }),
      // §4 — LIME inline-dictation microphone permission section.
      React.createElement(VoiceSection, { voiceStatus, onAllowMic }),
      // Rating prompt — below the mic-permission card, above the About footer.
      React.createElement(RateCard),
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
