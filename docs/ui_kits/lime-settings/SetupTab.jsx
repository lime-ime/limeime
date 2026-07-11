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
  // App Store “write a review” deep link. Replace XXXXXXXXXX with the real
  // numeric App ID once the app is published (the ?action=write-review query
  // opens the rating sheet directly).
  const APPSTORE_REVIEW_URL = "https://apps.apple.com/app/id6784694460?action=write-review";

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

  // A row of five filled gold stars for the rating prompt.
  function StarRow({ size = 18 }) {
    const star = React.createElement("svg", { width: size, height: size, viewBox: "0 0 24 24", fill: "#FFB400" },
      React.createElement("path", { d: "M12 2l2.9 6.26 6.9.7-5.1 4.62 1.45 6.74L12 17.3 5.85 20.32 7.3 13.58 2.2 8.96l6.9-.7z" }));
    return React.createElement("div", { style: { display: "flex", gap: 4 } },
      Array.from({ length: 5 }, (_, i) => React.cloneElement(star, { key: i })));
  }

  // iOS-style alert asking how to hide the rating card (已完成 / 以後再說 / 取消).
  // Rendered over the whole device (position: fixed is contained by the scaled
  // #device frame, so it dims the entire screen like a real UIAlertController).
  function DismissDialog({ onDone, onLater, onCancel }) {
    const btn = (label, cb, bold) => React.createElement("button", {
      type: "button", onClick: cb,
      style: { width: "100%", height: 44, border: "none", borderTop: "0.5px solid var(--separator)",
        background: "transparent", color: "var(--accent-blue)", cursor: "pointer",
        font: (bold ? 600 : 400) + " 17px/1 var(--font-sans)" },
    }, label);
    return React.createElement("div", {
      onClick: onCancel,
      style: { position: "fixed", inset: 0, zIndex: 50, display: "flex", alignItems: "center",
        justifyContent: "center", background: "rgba(0,0,0,.28)" },
    },
      React.createElement("div", {
        onClick: (e) => e.stopPropagation(),
        style: { width: 270, borderRadius: 14, overflow: "hidden", background: "var(--surface, #fff)",
          boxShadow: "0 10px 40px rgba(0,0,0,.35)" },
      },
        React.createElement("div", { style: { padding: "18px 16px 14px", textAlign: "center" } },
          React.createElement("div", { style: { font: "600 17px/22px var(--font-sans)", color: "var(--text-primary)" } }, "隱藏評分邀請？"),
          React.createElement("div", { style: { font: "400 13px/18px var(--font-sans)", color: "var(--text-secondary)", marginTop: 6 } },
            "如果您已給評分，選「已完成」即可不再顯示；還沒決定的話，選「以後再說」，我們稍後再提醒您。")
        ),
        btn("已完成", onDone, true),
        btn("以後再說", onLater, false),
        btn("取消", onCancel, false)
      )
    );
  }

  // Rating prompt card — a tonal card inviting a 5-star App Store review,
  // placed in the Setup tab's app-info area (below IM status, above About).
  // The × opens DismissDialog. In this mockup the dismiss is in-memory (resets on
  // reload); production persists it to UserDefaults — ratingPromptDismissed (已完成,
  // permanent) / ratingPromptSnoozeUntil (以後再說, +14 days). See LIME_SETTINGS §4.4.
  function RateCard() {
    const [dismissed, setDismissed] = React.useState(false);
    const [confirm, setConfirm] = React.useState(false);
    if (dismissed) return null;
    return React.createElement(React.Fragment, null,
      React.createElement("div", { style: { position: "relative" } },
        React.createElement("a", {
          href: APPSTORE_REVIEW_URL, target: "_blank", rel: "noopener noreferrer",
          style: {
            display: "flex", alignItems: "center", gap: 14, textDecoration: "none",
            padding: "16px 18px", borderRadius: 16, background: "var(--fill-quaternary)",
            WebkitTapHighlightColor: "transparent",
          },
        },
          React.createElement("div", { style: { flex: 1, display: "flex", flexDirection: "column", gap: 6, paddingRight: 18 } },
            React.createElement("div", { style: { font: "600 17px/22px var(--font-sans)", color: "var(--text-primary)" } }, "喜歡萊姆輸入法嗎？"),
            React.createElement(StarRow, null),
            React.createElement("div", { style: { font: "400 14px/19px var(--font-sans)", color: "var(--text-secondary)" } }, "到 App Store 給個 5 星好評，支持作者持續開發。")
          ),
          React.createElement("svg", { width: 8, height: 14, viewBox: "0 0 8 14", fill: "none", style: { flex: "0 0 auto" } },
            React.createElement("path", { d: "M1 1l6 6-6 6", stroke: "var(--text-tertiary)", strokeWidth: 2, strokeLinecap: "round", strokeLinejoin: "round" }))
        ),
        // Dismiss × — its own tap target in the top-right corner; stopPropagation
        // so it never opens the store. Opens the confirm dialog.
        React.createElement("button", {
          type: "button", "aria-label": "隱藏",
          onClick: (e) => { e.preventDefault(); e.stopPropagation(); setConfirm(true); },
          style: {
            position: "absolute", top: 8, right: 8, width: 22, height: 22, borderRadius: "50%",
            border: "none", background: "var(--fill-tertiary, rgba(120,120,128,.16))", color: "var(--text-secondary)",
            display: "flex", alignItems: "center", justifyContent: "center", cursor: "pointer", padding: 0,
          },
        },
          React.createElement("svg", { width: 11, height: 11, viewBox: "0 0 12 12", fill: "none" },
            React.createElement("path", { d: "M3 3l6 6M9 3l-6 6", stroke: "currentColor", strokeWidth: 1.6, strokeLinecap: "round" })))
      ),
      confirm && React.createElement(DismissDialog, {
        // 已完成 → permanent hide; 以後再說 → snooze. Both hide the card in this mockup.
        onDone: () => { setConfirm(false); setDismissed(true); },
        onLater: () => { setConfirm(false); setDismissed(true); },
        onCancel: () => setConfirm(false),
      })
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
      // Rating prompt — sits between the IM status card and the About footer.
      React.createElement(RateCard),
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
