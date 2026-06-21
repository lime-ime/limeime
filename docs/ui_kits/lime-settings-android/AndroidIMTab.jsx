/* 輸入法 — Android IM Manager (Material 3). §5.1 IM list + 關聯字庫, with a
   Material drill-down detail. window.AndroidIMTab + window.AndroidIMDetail. */
(function () {
  const { Icon, Switch, Button } = window.LimeM3;

  // One-time keyframe injection for the empty-state FAB nudge (radar pulse + bob).
  if (!document.getElementById("androidimtab-nudge-css")) {
    const css = document.createElement("style");
    css.id = "androidimtab-nudge-css";
    css.textContent = `
      @keyframes aim-pulse { 0%{transform:scale(1);opacity:.38} 70%{opacity:0} 100%{transform:scale(2.5);opacity:0} }
      @keyframes aim-breath { 0%,62%,100%{transform:scale(1)} 72%{transform:scale(1.08)} 82%{transform:scale(.98)} }
      @keyframes aim-bob { 0%,100%{transform:translateY(0);opacity:.95} 50%{transform:translateY(4px);opacity:1} }
      .aim-ring { animation: aim-pulse 2.4s cubic-bezier(.22,.61,.36,1) infinite; }
      .aim-ring.r2 { animation-delay: 1.2s; }
      .aim-fab.attn { animation: aim-breath 2.4s ease-in-out infinite; }
      .aim-callout { animation: aim-bob 1.8s ease-in-out infinite; }
      @media (prefers-reduced-motion: reduce) {
        .aim-ring { animation: none; opacity: .14; transform: scale(1.9); }
        .aim-fab.attn, .aim-callout { animation: none; }
      }`;
    document.head.appendChild(css);
  }

  const IMS = [
    { id: "phonetic", label: "注音", glyph: "ㄅ", color: "#5b8a2c", on: true },
    { id: "cj",       label: "倉頡", glyph: "倉", color: "#36618e", on: true },
    { id: "ecj",      label: "速成", glyph: "速", color: "#4c662b", on: true },
    { id: "dayi",     label: "大易", glyph: "易", color: "#8f4c38", on: false },
    { id: "array",    label: "行列", glyph: "行", color: "#6750a4", on: false },
    { id: "pinyin",   label: "拼音", glyph: "拼", color: "#a83b6f", on: false },
  ];

  function Avatar({ glyph }) {
    return React.createElement("span", {
      style: { width: 40, height: 40, flex: "0 0 auto", borderRadius: 11, background: "#8e8e93", color: "#fff",
        display: "flex", alignItems: "center", justifyContent: "center", font: "500 18px/1 'Noto Sans TC', var(--font-sans)" },
    }, glyph);
  }

  function Row({ m, onToggle, onOpen, selected }) {
    return React.createElement("div", {
      onClick: () => onOpen && onOpen(m),
      style: { display: "flex", alignItems: "center", gap: 16, minHeight: 64, padding: "8px 8px",
        cursor: "pointer", opacity: m.on ? 1 : 0.55, WebkitTapHighlightColor: "transparent", borderRadius: 16,
        background: selected ? "var(--md-secondary-container)" : "transparent", transition: "background .15s" },
    },
      React.createElement(Avatar, { glyph: m.glyph }),
      React.createElement("div", { style: { flex: 1, minWidth: 0, font: "500 17px/22px 'Roboto', var(--font-sans)", color: selected ? "var(--md-on-secondary-container)" : "var(--md-on-surface)" } }, m.label),
      React.createElement("span", { onClick: (e) => e.stopPropagation() },
        React.createElement(Switch, { checked: m.on, onChange: () => onToggle(m.id) })),
      React.createElement(Icon, { name: "chevron_right", size: 22, color: "var(--md-on-surface-variant)" })
    );
  }

  // Empty state shown in place of the installed-IM list when none is installed.
  function EmptyState() {
    return React.createElement("div", {
      style: { display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center",
        textAlign: "center", padding: "40px 24px 28px", gap: 16 },
    },
      React.createElement("span", { style: { width: 96, height: 96, borderRadius: 28, display: "flex", alignItems: "center", justifyContent: "center",
        background: "var(--md-surface-container-high)", color: "var(--md-primary)" } },
        React.createElement(Icon, { name: "keyboard", size: 46 })),
      React.createElement("div", { style: { font: "500 22px/28px 'Roboto', 'Noto Sans TC', var(--font-sans)", color: "var(--md-on-surface)" } }, "尚未安裝任何輸入法"),
      React.createElement("div", { style: { font: "400 15px/21px 'Roboto', 'Noto Sans TC', var(--font-sans)", color: "var(--md-on-surface-variant)", maxWidth: 260 } },
        "點選右下角的 ＋ 下載或匯入輸入法表格，即可開始使用。")
    );
  }

  // Round FAB with radar pulse + bobbing 「安裝輸入法」 callout (empty only).
  function FabNudge({ empty }) {
    return React.createElement("div", { style: { position: "absolute", right: 16, bottom: 16, width: 56, height: 56, zIndex: 20 } },
      empty && React.createElement("span", { className: "aim-callout", style: { position: "absolute", right: 0, bottom: 66, zIndex: 5, pointerEvents: "none" } },
        React.createElement("span", { style: { position: "relative", display: "block", background: "var(--md-primary)", color: "var(--md-on-primary)",
          font: "500 14px/1 'Roboto', 'Noto Sans TC', var(--font-sans)", padding: "9px 13px", borderRadius: 11, boxShadow: "0 4px 14px rgba(0,0,0,.2)", whiteSpace: "nowrap" } },
          "安裝輸入法",
          React.createElement("span", { style: { position: "absolute", right: 18, bottom: -5, width: 12, height: 12, background: "var(--md-primary)", transform: "rotate(45deg)", borderRadius: 2 } }))),
      empty && React.createElement("span", { className: "aim-ring", style: { position: "absolute", left: "50%", top: "50%", width: 56, height: 56, margin: "-28px 0 0 -28px", borderRadius: "50%", background: "var(--md-primary)", opacity: 0, zIndex: 1 } }),
      empty && React.createElement("span", { className: "aim-ring r2", style: { position: "absolute", left: "50%", top: "50%", width: 56, height: 56, margin: "-28px 0 0 -28px", borderRadius: "50%", background: "var(--md-primary)", opacity: 0, zIndex: 1 } }),
      React.createElement("button", {
        type: "button",
        className: "aim-fab" + (empty ? " attn" : ""),
        style: { position: "absolute", inset: 0, width: 56, height: 56, borderRadius: "50%", border: 0,
          background: "var(--md-primary-container)", color: "var(--md-on-primary-container)", display: "flex", alignItems: "center", justifyContent: "center",
          boxShadow: "0 3px 8px rgba(0,0,0,.2)", cursor: "pointer", zIndex: 2 },
      }, React.createElement(Icon, { name: "add", size: 26 }))
    );
  }

  function AndroidIMTab({ onOpen, selectedId, hideFab, startEmpty, allDisabled }) {
    const [ims, setIms] = React.useState(
      startEmpty ? [] : (allDisabled ? IMS.map((m) => ({ ...m, on: false })) : IMS)
    );
    const toggle = (id) => setIms((s) => s.map((m) => (m.id === id ? { ...m, on: !m.on } : m)));
    const isEmpty = ims.length === 0;
    return React.createElement("div", { style: { position: "relative", minHeight: "100%", paddingBottom: 24 } },
      React.createElement("div", { style: { padding: "4px 16px 0" } },
        React.createElement("div", { style: { font: "700 34px/41px 'Roboto', var(--font-sans)", color: "var(--md-on-surface)", padding: "12px 8px 10px" } }, "管理輸入法"),
        React.createElement("div", { style: { font: "500 13px/18px 'Roboto', var(--font-sans)", color: "var(--md-primary)", padding: "8px 8px 6px" } }, "已安裝的輸入法"),
        isEmpty
          ? React.createElement(EmptyState)
          : ims.map((m) => React.createElement(Row, { key: m.id, m, onToggle: toggle, onOpen, selected: selectedId === m.id })),
        React.createElement("div", { style: { height: 1, background: "var(--md-outline-variant)", margin: "10px 8px 8px" } }),
        React.createElement("div", { style: { font: "500 13px/18px 'Roboto', var(--font-sans)", color: "var(--md-primary)", padding: "8px 8px 6px" } }, "關聯字庫"),
        React.createElement("div", {
          onClick: () => onOpen && onOpen({ id: "related", label: "關聯字庫", related: true }),
          style: { display: "flex", alignItems: "center", gap: 16, minHeight: 64, padding: "8px", cursor: "pointer", borderRadius: 16,
            background: selectedId === "related" ? "var(--md-secondary-container)" : "transparent", transition: "background .15s" },
        },
          React.createElement("span", { style: { width: 40, height: 40, borderRadius: 11, background: "#8e8e93", color: "#fff", display: "flex", alignItems: "center", justifyContent: "center" } },
            React.createElement(Icon, { name: "chat", size: 22, fill: true })),
          React.createElement("div", { style: { flex: 1, font: "500 17px/22px 'Roboto', var(--font-sans)", color: "var(--md-on-surface)" } }, "關聯字庫"),
          React.createElement(Icon, { name: "chevron_right", size: 22, color: "var(--md-on-surface-variant)" })
        )
      ),
      !hideFab && React.createElement(FabNudge, { empty: isEmpty })
    );
  }

  // ── IM detail (drill-down) ─────────────────────────────────────────────
  function DetailGroup({ header, children }) {
    return React.createElement("div", { style: { display: "flex", flexDirection: "column", gap: 4 } },
      React.createElement("div", { style: { font: "500 13px/18px 'Roboto', var(--font-sans)", color: "var(--md-primary)", padding: "0 6px" } }, header),
      React.createElement("div", { style: { background: "var(--md-surface-container-low)", borderRadius: 20, overflow: "hidden" } }, children)
    );
  }
  function DetailRow({ title, value, chevron, last, destructive, onClick, trailing }) {
    return React.createElement("div", {
      onClick,
      style: { display: "flex", alignItems: "center", gap: 12, minHeight: 56, padding: "10px 18px", cursor: onClick ? "pointer" : "default",
        borderBottom: last ? "none" : "1px solid var(--md-outline-variant)" },
    },
      React.createElement("div", { style: { flex: 1, font: "400 17px/22px 'Roboto', var(--font-sans)", color: destructive ? "var(--md-error)" : "var(--md-on-surface)" } }, title),
      value != null && React.createElement("span", { style: { font: "400 17px/22px 'Roboto', var(--font-sans)", color: "var(--md-on-surface-variant)" } }, value),
      trailing,
      chevron && React.createElement(Icon, { name: "chevron_right", size: 22, color: "var(--md-on-surface-variant)" })
    );
  }

  function AndroidIMDetail({ im, onClose, embedded }) {
    const [backup, setBackup] = React.useState(true);
    const open = !!im;
    const data = im || {};

    // Two-pane empty state — shown in the right pane before any IM is chosen.
    if (embedded && !im) {
      return React.createElement("div", { style: { height: "100%", display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", gap: 14, color: "var(--md-on-surface-variant)", padding: 40 } },
        React.createElement("span", { style: { width: 88, height: 88, borderRadius: "50%", background: "var(--md-surface-container-high)", display: "flex", alignItems: "center", justifyContent: "center" } },
          React.createElement(Icon, { name: "touch_app", size: 40, color: "var(--md-on-surface-variant)" })),
        React.createElement("div", { style: { font: "400 17px/22px 'Roboto', var(--font-sans)", textAlign: "center" } }, "請從左側選擇輸入法以檢視及編輯詳細資料")
      );
    }

    const body = React.createElement("div", { style: { padding: "8px 16px 28px", display: "flex", flexDirection: "column", gap: 22 } },
      !data.related && React.createElement(DetailGroup, { header: "輸入法資訊" },
        React.createElement(DetailRow, { title: "名稱", value: data.label, chevron: true }),
        React.createElement(DetailRow, { title: "版本", value: "2024.03", chevron: true }),
        React.createElement(DetailRow, { title: "結束鍵", value: "—", chevron: true }),
        React.createElement(DetailRow, { title: "筆數", value: "34,838", last: true })
      ),
      !data.related && React.createElement(DetailGroup, { header: "軟鍵盤配置" },
        React.createElement(DetailRow, { title: "鍵盤佈局", value: "標準", chevron: true, last: true })
      ),
      React.createElement(DetailGroup, { header: data.related ? "關聯字庫" : "字根資料表" },
        React.createElement(DetailRow, { title: data.related ? "瀏覽 / 編輯關聯字庫" : "瀏覽 / 編輯資料表", chevron: true, last: true })
      ),
      !data.related && React.createElement(DetailGroup, { header: "選項" },
        React.createElement(DetailRow, { title: "刪除時備份已學習記錄", last: true,
          trailing: React.createElement(Switch, { checked: backup, onChange: setBackup }) })
      ),
      !data.related && React.createElement("div", { style: { padding: "4px 2px" } },
        React.createElement(Button, { variant: "error", icon: "delete", full: true, onClick: onClose }, "移除輸入法"))
    );

    // Embedded (tablet two-pane): static pane, app bar shows the IM name, no slide-over.
    if (embedded) {
      return React.createElement("div", { style: { height: "100%", display: "flex", flexDirection: "column", background: "var(--md-surface)" } },
        React.createElement("div", { className: "appbar" },
          React.createElement("span", { className: "title" }, data.label || "")),
        React.createElement("div", { className: "scroll" }, body)
      );
    }

    // Phone: full-screen slide-over with status bar + back affordance.
    return React.createElement("div", { className: "detail" + (open ? " open" : "") },
      React.createElement("div", { className: "appbar" },
        React.createElement("span", { className: "leading", onClick: onClose }, React.createElement(Icon, { name: "arrow_back", size: 24 })),
        React.createElement("span", { className: "title" }, data.label || "")),
      React.createElement("div", { className: "scroll" }, body)
    );
  }

  window.AndroidIMTab = AndroidIMTab;
  window.AndroidIMDetail = AndroidIMDetail;
})();
