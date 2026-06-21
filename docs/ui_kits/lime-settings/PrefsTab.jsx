/* 喜好設定 — IM Preferences. Mirrors PreferencesTabView.swift §8.
   Stateful toggles + 簡繁轉換 segmented control. window.PrefsTab. */
(function () {
  const { ListGroup, ListRow, Switch, SegmentedControl } = window.LIMEDesignSystem_6ca3c0;
  const I = window.LimeIcons;

  function PrefsTab() {
    const [s, setS] = React.useState({
      vibrate: true, sound: false, numberRow: true,
      smart: true, autoSymbol: false, persistLang: false,
      related: true, learn: true, dict: true, autoCap: true,
    });
    const t = (k) => () => setS((p) => ({ ...p, [k]: !p[k] }));
    const sw = (k) => React.createElement(Switch, { checked: s[k], onChange: t(k) });
    const [han, setHan] = React.useState("繁轉簡");

    return React.createElement("div", { style: { padding: "0 24px 28px", display: "flex", flexDirection: "column", gap: 22 } },
      React.createElement("div", { style: { font: "700 34px/41px var(--font-sans)", letterSpacing: "-.4px", padding: "8px 0 0" } }, "喜好設定"),

      React.createElement(ListGroup, { header: "鍵盤外觀" },
        React.createElement(ListRow, { icon: I.palette({ size: 17 }), iconColor: "var(--green-strong)", title: "鍵盤樣式", value: "放鬆綠", chevron: true }),
        React.createElement(ListRow, { title: "鍵盤大小", value: "一般", chevron: true }),
        React.createElement(ListRow, { title: "字型大小", value: "一般", chevron: true }),
        React.createElement(ListRow, { title: "數字列英文鍵盤", subtitle: "在英文鍵盤顯示數字列 (5 列鍵盤)", trailing: sw("numberRow") }),
        React.createElement(ListRow, { title: "顯示方向鍵", value: "無", chevron: true })
      ),

      React.createElement(ListGroup, { header: "鍵盤回饋" },
        React.createElement(ListRow, { icon: I.vibrate({ size: 17 }), iconColor: "#e0883a", title: "打字震動", trailing: sw("vibrate") }),
        React.createElement(ListRow, { title: "震動強度", value: "中", chevron: true, style: { opacity: s.vibrate ? 1 : 0.5 } }),
        React.createElement(ListRow, { title: "打字音效", trailing: sw("sound") })
      ),

      React.createElement(ListGroup, { header: "輸入法行為" },
        React.createElement(ListRow, { icon: I.sparkles({ size: 17 }), iconColor: "var(--lime-green)", title: "開啟中文智慧組詞", subtitle: "部份輸入法可能會影響中英混打功能", trailing: sw("smart") }),
        React.createElement(ListRow, { title: "自動中文標點模式", subtitle: "無候選字詞時顯示中文標點選項", trailing: sw("autoSymbol") }),
        React.createElement(ListRow, { title: "記憶中英模式", subtitle: "下次切換前保持中英模式", trailing: sw("persistLang") }),
        React.createElement(ListRow, { icon: I.search({ size: 17 }), iconColor: "#777", title: "字根反查設定", chevron: true })
      ),

      React.createElement(ListGroup, { header: "簡繁轉換", footer: "套用於所有輸入法的候選字輸出。" },
        React.createElement("div", { style: { padding: "12px 16px", display: "flex", alignItems: "center", justifyContent: "space-between", gap: 12 } },
          React.createElement("span", { style: { font: "400 17px/22px var(--font-sans)" } }, "字碼轉換"),
          React.createElement(SegmentedControl, { options: ["無", "繁轉簡", "簡轉繁"], value: han, onChange: setHan, style: { width: 222 } })
        )
      ),

      React.createElement(ListGroup, { header: "關聯字與學習" },
        React.createElement(ListRow, { icon: I.bubble({ size: 17 }), iconColor: "#5b9", title: "啟用關聯字庫", subtitle: "啟用關聯字庫功能", trailing: sw("related") }),
        React.createElement(ListRow, { title: "自動學習新詞", subtitle: "從常用關聯字學習新詞", trailing: sw("learn") })
      ),

      React.createElement(ListGroup, { header: "英文鍵盤" },
        React.createElement(ListRow, { icon: I.english({ size: 17 }), iconColor: "#0a84c4", title: "啟用英文字典", subtitle: "英文模式下顯示英文建議字", trailing: sw("dict") }),
        React.createElement(ListRow, { title: "首字自動大寫", subtitle: "句首字母自動轉為大寫", trailing: sw("autoCap") })
      )
    );
  }
  window.PrefsTab = PrefsTab;
})();
