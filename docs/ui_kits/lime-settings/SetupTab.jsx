/* 設定 — App Setup tab. Faithful to SetupTabView.swift §4, re-laid-out with the
   LIME brand hero. Exposes window.SetupTab. */
(function () {
  const { ListGroup, ListRow, Switch, Button, StatusBanner } = window.LIMEDesignSystem_6ca3c0;
  const I = window.LimeIcons;

  // Canonical destinations (LIME_SETTINGS.md §4.1). The 版權說明 page now lives on
  // the project site; 原始碼 points at the GitHub repo.
  const LICENSE_URL = "https://lime-ime.github.io/limeime/pages/license.html";
  const MANUAL_URL = "https://lime-ime.github.io/limeime/pages/index.html";
  const GITHUB_URL = "https://github.com/lime-ime/limeime";

  // An iOS-style inline link: brand-blue label + a small up-right arrow glyph so
  // users can tell it leaves the app. Opens in a new tab.
  function ExternalLink({ href, children }) {
    return React.createElement("a", {
      href, target: "_blank", rel: "noopener noreferrer",
      style: {
        display: "inline-flex", alignItems: "center", gap: 4,
        color: "var(--accent-blue)", textDecoration: "none",
        font: "400 17px/22px var(--font-sans)", WebkitTapHighlightColor: "transparent",
      },
    }, children,
      React.createElement("svg", { width: 11, height: 11, viewBox: "0 0 12 12", fill: "none", style: { opacity: .7, flex: "0 0 auto" } },
        React.createElement("path", { d: "M3.5 2.5h6v6M9.5 2.5L2.5 9.5", stroke: "currentColor", strokeWidth: 1.5, strokeLinecap: "round", strokeLinejoin: "round" })
      )
    );
  }

  // A compact iOS-style link chip for the optimized About footer: equal-width,
  // rounded, subtle fill, icon + brand-blue label + external arrow.
  function LinkChip({ href, icon, external, children }) {
    return React.createElement("a", {
      href, target: "_blank", rel: "noopener noreferrer",
      style: {
        flex: 1, display: "flex", flexDirection: "column", alignItems: "center", gap: 7,
        padding: "15px 8px 13px", borderRadius: 14, background: "var(--fill-quaternary)",
        color: "var(--accent)", textDecoration: "none", WebkitTapHighlightColor: "transparent",
      },
    },
      React.createElement("span", { style: { color: "var(--accent)", display: "flex" } }, icon),
      React.createElement("span", { style: { display: "inline-flex", alignItems: "center", gap: 3, font: "500 14px/18px var(--font-sans)" } },
        children,
        external && React.createElement("svg", { width: 10, height: 10, viewBox: "0 0 12 12", fill: "none", style: { opacity: .6 } },
          React.createElement("path", { d: "M3.5 2.5h6v6M9.5 2.5L2.5 9.5", stroke: "currentColor", strokeWidth: 1.5, strokeLinecap: "round", strokeLinejoin: "round" }))
      )
    );
  }

  const GreenToggle = () =>
    React.createElement("div", {
      style: {
        width: 30, height: 18, borderRadius: 9, background: "var(--switch-on)",
        position: "relative", flex: "0 0 auto",
      },
    }, React.createElement("div", {
      style: {
        position: "absolute", right: 2, top: 2, width: 14, height: 14,
        borderRadius: "50%", background: "#fff", boxShadow: "0 1px 1px rgba(0,0,0,.18)",
      },
    }));

  function StepRow({ icon, text }) {
    return React.createElement("div", { style: { display: "flex", alignItems: "center", gap: 16 } },
      React.createElement("div", { style: { width: 32, display: "flex", justifyContent: "center", color: "var(--accent)" } }, icon),
      React.createElement("div", { style: { font: "400 17px/22px var(--font-sans)", color: "var(--text-primary)" } }, text)
    );
  }

  // §4.3 — Installed-IM status. Mirrors the IM tab's reality so the Setup tab
  // can surface a problem and route the user to fix it:
  //   none     → danger  (no IM table installed)   → 「安裝輸入法」
  //   disabled → warning (installed but all off)    → 「啟用輸入法」
  //   ok       → success (≥1 installed & enabled)   → no action
  const IM_STATUS = {
    none:     { status: "danger",  text: "尚未安裝任何輸入法",      cta: "安裝輸入法" },
    disabled: { status: "warning", text: "已安裝輸入法，但全部已停用", cta: "啟用輸入法" },
    ok:       { status: "success", text: "輸入法已就緒，隨時可用",   cta: null }, // text overridden with the installed count
  };

  function IMStatusSection({ imStatus = "none", imCount = 0, onManageIM }) {
    const m = IM_STATUS[imStatus] || IM_STATUS.none;
    const text = imStatus === "ok" ? ("已安裝 " + imCount + " 個輸入法")
      : imStatus === "disabled" ? ("已安裝 " + imCount + " 個輸入法，但全部停用")
      : m.text;
    return React.createElement("div", { style: { display: "flex", flexDirection: "column", gap: 16, paddingTop: 4 } },
      React.createElement("div", { style: { height: 1, background: "var(--separator)", margin: "0 -24px 4px" } }),
      React.createElement(StatusBanner, { status: m.status }, text),
      m.cta && React.createElement(Button, { variant: "prominent", size: "large", fullWidth: true, onClick: onManageIM }, m.cta)
    );
  }

  function SetupTab({ imStatus, imCount, onManageIM }) {
    return React.createElement("div", { style: { padding: "8px 24px 28px", display: "flex", flexDirection: "column", gap: 24 } },
      // Brand hero — Android style: plain logo (no white rounded-rect tile), horizontal row
      React.createElement("div", { style: { display: "flex", flexDirection: "row", alignItems: "center", justifyContent: "center", gap: 16, paddingTop: 20 } },
        React.createElement("img", { src: "../../assets/lime-logo-android.png", alt: "LIME",
          style: { width: 92, height: 92, objectFit: "contain" } }),
        React.createElement("div", { style: { font: "700 30px/36px var(--font-sans)", letterSpacing: "-.4px", color: "var(--text-primary)" } }, "萊姆輸入法")
      ),
      // Heading leads the section; the activation status banner sits BELOW it.
      React.createElement("div", { style: { font: "700 28px/34px var(--font-sans)", letterSpacing: "-.4px" } }, "設定萊姆輸入法"),
      React.createElement(StatusBanner, { status: "success" }, "萊姆輸入法已啟用"),
      React.createElement("div", { style: { display: "flex", flexDirection: "column", gap: 16 } },
        React.createElement(StepRow, { icon: I.keyboard({ size: 22 }), text: "輕觸「鍵盤」" }),
        React.createElement(StepRow, { icon: React.createElement(GreenToggle), text: "開啟萊姆輸入法" }),
        React.createElement(StepRow, { icon: React.createElement(GreenToggle), text: "開啟「允許完整取用」" })
      ),
      React.createElement("div", { style: { font: "400 15px/20px var(--font-sans)", color: "var(--text-secondary)", textAlign: "center" } },
        "萊姆輸入法僅需完整取用以啟用按鍵震動回饋。若不需要此功能，可不開啟。萊姆輸入法不會收集或傳送任何個人資料。"),
      React.createElement(Button, { variant: "prominent", size: "large", fullWidth: true }, "前往設定"),
      React.createElement("div", { style: { font: "400 13px/18px var(--font-sans)", color: "var(--text-secondary)", textAlign: "center" } },
        "若設定未直接顯示萊姆輸入法，請到「設定」>「Apps」>「萊姆輸入法」>「Keyboards」開啟。"),
      // §4.3 — Installed-IM status section.
      React.createElement(IMStatusSection, { imStatus, imCount, onManageIM }),
      // About — optimized footer: app identity + version, then three equal-width
      // link chips (使用手冊 / 版權說明 / 原始碼) laid out consistently. Replaces the
      // old grouped list whose lone left-aligned GitHub row looked inconsistent.
      React.createElement("div", { style: { display: "flex", flexDirection: "column", gap: 16, paddingTop: 10 } },
        React.createElement("div", { style: { height: 1, background: "var(--separator)", margin: "0 -24px" } }),
        React.createElement("div", { style: { display: "flex", gap: 10 } },
          React.createElement(LinkChip, { href: MANUAL_URL, icon: I.book({ size: 21 }) }, "使用手冊"),
          React.createElement(LinkChip, { href: LICENSE_URL, icon: I.doc({ size: 21 }) }, "版權說明"),
          React.createElement(LinkChip, { href: GITHUB_URL, icon: I.code({ size: 21 }), external: true }, "原始碼")
        ),
        // One-line copyright banner at the very bottom.
        React.createElement("div", { style: { font: "400 13px/18px var(--font-sans)", color: "var(--text-secondary)", textAlign: "center", paddingTop: 6 } },
          "© LIME 萊姆輸入法 6.1.15 - 2026")
      )
    );
  }
  window.SetupTab = SetupTab;
})();
