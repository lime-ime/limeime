/* 輸入法 — IM Manager tab. SetupImFragment IM grid + 關聯字庫 (§5.1).
   Stateful enable toggles, reorder-style list, floating + FAB. When no IM is
   installed, the list is replaced by an empty state and the + FAB is nudged with
   a radar pulse + bobbing arrow (reduced-motion safe). window.IMTab. */
(function () {
  const { ListGroup, ListRow, Switch } = window.LIMEDesignSystem_6ca3c0;
  const I = window.LimeIcons;

  // One-time keyframe injection so the component stays portable (no host CSS needed).
  if (!document.getElementById("imtab-nudge-css")) {
    const css = document.createElement("style");
    css.id = "imtab-nudge-css";
    css.textContent = `
      @keyframes imtab-fab-pulse { 0%{transform:scale(1);opacity:.45} 70%{opacity:0} 100%{transform:scale(2.6);opacity:0} }
      @keyframes imtab-fab-breath { 0%,62%,100%{transform:scale(1)} 72%{transform:scale(1.08)} 82%{transform:scale(.98)} }
      @keyframes imtab-callout-bob { 0%,100%{transform:translateY(0);opacity:.95} 50%{transform:translateY(4px);opacity:1} }
      .imtab-ring { animation: imtab-fab-pulse 2.4s cubic-bezier(.22,.61,.36,1) infinite; }
      .imtab-ring.r2 { animation-delay: 1.2s; }
      .imtab-fab-attn { animation: imtab-fab-breath 2.4s ease-in-out infinite; }
      .imtab-callout { animation: imtab-callout-bob 1.8s ease-in-out infinite; }
      @media (prefers-reduced-motion: reduce) {
        .imtab-ring { animation: none; opacity: .18; transform: scale(1.9); }
        .imtab-fab-attn, .imtab-callout { animation: none; }
      }`;
    document.head.appendChild(css);
  }

  // Grey rounded-square + Chinese-character avatar — matches the Android kit's IM list.
  function CharAvatar({ glyph }) {
    return React.createElement("span", {
      style: { width: 30, height: 30, flex: "0 0 auto", borderRadius: 7, background: "var(--icon-tile)", color: "#fff",
        display: "flex", alignItems: "center", justifyContent: "center", font: "500 15px/1 var(--font-sans)" },
    }, glyph);
  }
  function IconAvatar({ icon }) {
    return React.createElement("span", {
      style: { width: 30, height: 30, flex: "0 0 auto", borderRadius: 7, background: "var(--icon-tile)", color: "#fff",
        display: "flex", alignItems: "center", justifyContent: "center" },
    }, React.createElement("span", { style: { width: 17, height: 17, display: "inline-flex" } }, icon));
  }

  // Empty state shown in place of the installed-IM list when none is installed
  // (§5.1). Keyboard glyph + one-line guidance pointing the user at the + FAB.
  // Rendered in normal flow so the always-present 關聯字庫 group sits below it.
  function EmptyState() {
    return React.createElement("div", {
      style: { display: "flex", flexDirection: "column",
        alignItems: "center", justifyContent: "center", textAlign: "center", padding: "32px 24px", gap: 16 },
    },
      React.createElement("div", { style: { width: 96, height: 96, borderRadius: 28, display: "flex",
        alignItems: "center", justifyContent: "center", color: "var(--accent)", background: "var(--fill-quaternary)" } },
        I.keyboard({ size: 46 })),
      React.createElement("div", { style: { font: "600 20px/26px var(--font-sans)", color: "var(--text-primary)" } }, "尚未安裝任何輸入法"),
      React.createElement("div", { style: { font: "400 15px/21px var(--font-sans)", color: "var(--text-secondary)", maxWidth: 250 } },
        "點選右下角的 ",
        React.createElement("b", { style: { color: "var(--accent)" } }, "＋"),
        " 下載或匯入輸入法表格，即可開始使用。")
    );
  }

  // A small callout pill that points at the FAB — cleaner than a drawn arrow.
  // Gently bobs toward the FAB; reduced-motion safe.
  function NudgeLabel() {
    return React.createElement("div", { className: "imtab-callout",
      style: { position: "absolute", right: 20, bottom: 84, zIndex: 15, pointerEvents: "none" } },
      React.createElement("div", { style: { position: "relative", background: "var(--accent)", color: "#fff",
        font: "600 14px/1 var(--font-sans)", padding: "9px 13px", borderRadius: 11,
        boxShadow: "0 4px 14px rgba(0,0,0,.18)", whiteSpace: "nowrap" } },
        "安裝輸入法",
        // Caret pointing down toward the FAB, anchored under the pill's right side.
        React.createElement("div", { style: { position: "absolute", right: 16, bottom: -5, width: 12, height: 12,
          background: "var(--accent)", transform: "rotate(45deg)", borderRadius: 2 } }))
    );
  }

  const IMS = [
    { id: "phonetic", label: "注音", glyph: "ㄅ", on: true },
    { id: "cj",       label: "倉頡", glyph: "倉", on: true },
    { id: "ecj",      label: "速成", glyph: "速", on: true },
    { id: "dayi",     label: "大易", glyph: "易", on: false },
    { id: "array",    label: "行列", glyph: "行", on: false },
    { id: "pinyin",   label: "拼音", glyph: "拼", on: false },
  ];

  function IMTab({ onOpen, startEmpty, allDisabled }) {
    const [ims, setIms] = React.useState(
      startEmpty ? [] : (allDisabled ? IMS.map((m) => ({ ...m, on: false })) : IMS)
    );
    const toggle = (id) => setIms((s) => s.map((m) => (m.id === id ? { ...m, on: !m.on } : m)));
    const isEmpty = ims.length === 0;
    return React.createElement("div", { style: { position: "relative", minHeight: "100%" } },
      React.createElement("div", { style: { padding: "0 24px 28px", display: "flex", flexDirection: "column", gap: 22 } },
        React.createElement("div", { style: { font: "700 34px/41px var(--font-sans)", letterSpacing: "-.4px", padding: "8px 0 0" } }, "管理輸入法"),
        // Installed-IM list, or the empty state when nothing is installed.
        isEmpty
          ? React.createElement(ListGroup, { header: "已安裝的輸入法" }, React.createElement(EmptyState))
          : React.createElement(ListGroup, { header: "已安裝的輸入法" },
            ...ims.map((m) =>
              React.createElement(ListRow, {
                key: m.id,
                leading: React.createElement(CharAvatar, { glyph: m.glyph }),
                title: m.label,
                style: { opacity: m.on ? 1 : 0.55 },
                onClick: () => onOpen && onOpen(m),
                chevron: true,
                trailing: React.createElement("span", { onClick: (e) => e.stopPropagation() },
                  React.createElement(Switch, { checked: m.on, onChange: () => toggle(m.id) })),
              })
            )
          ),
        // 關聯字庫 is always present on this page, even with no IM installed.
        React.createElement(ListGroup, { header: "關聯字庫" },
          React.createElement(ListRow, { leading: React.createElement(IconAvatar, { icon: I.bubble({ size: 17 }) }), title: "關聯字庫", chevron: true, onClick: () => onOpen && onOpen({ id: "related", label: "關聯字庫", related: true }) })
        )
      ),
      // Callout nudge — only when no IM is installed.
      isEmpty && React.createElement(NudgeLabel),
      // Floating action button (radar pulse + breath when the list is empty).
      React.createElement("div", { style: { position: "absolute", right: 20, bottom: 20, width: 52, height: 52, zIndex: 20 } },
        isEmpty && React.createElement("span", { className: "imtab-ring", style: { position: "absolute", left: "50%", top: "50%", width: 52, height: 52, margin: "-26px 0 0 -26px", borderRadius: "50%", background: "var(--accent)", opacity: 0, zIndex: 1 } }),
        isEmpty && React.createElement("span", { className: "imtab-ring r2", style: { position: "absolute", left: "50%", top: "50%", width: 52, height: 52, margin: "-26px 0 0 -26px", borderRadius: "50%", background: "var(--accent)", opacity: 0, zIndex: 1 } }),
        React.createElement("button", {
          type: "button",
          className: isEmpty ? "imtab-fab-attn" : undefined,
          style: {
            position: "absolute", inset: 0, width: 52, height: 52,
            borderRadius: "50%", border: "none", background: "var(--accent)", color: "#fff",
            display: "flex", alignItems: "center", justifyContent: "center",
            boxShadow: "var(--shadow-fab)", cursor: "pointer", zIndex: 2,
          },
        }, I.plus({ size: 26 }))
      )
    );
  }
  window.IMTab = IMTab;
})();
