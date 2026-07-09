/* @ds-bundle: {"format":3,"namespace":"LIMEDesignSystem_6ca3c0","components":[{"name":"Button","sourcePath":"docs/components/controls/Button.jsx"},{"name":"SegmentedControl","sourcePath":"docs/components/controls/SegmentedControl.jsx"},{"name":"Stepper","sourcePath":"docs/components/controls/Stepper.jsx"},{"name":"Switch","sourcePath":"docs/components/controls/Switch.jsx"},{"name":"StatusBanner","sourcePath":"docs/components/feedback/StatusBanner.jsx"},{"name":"ListGroup","sourcePath":"docs/components/layout/ListGroup.jsx"},{"name":"ListRow","sourcePath":"docs/components/layout/ListRow.jsx"},{"name":"TabBar","sourcePath":"docs/components/navigation/TabBar.jsx"}],"sourceHashes":{"docs/components/controls/Button.jsx":"a3ae8ba725df","docs/components/controls/SegmentedControl.jsx":"6183c26aa632","docs/components/controls/Stepper.jsx":"8da541debe53","docs/components/controls/Switch.jsx":"a5e25bd31aa3","docs/components/feedback/StatusBanner.jsx":"a1ba72add6d3","docs/components/layout/ListGroup.jsx":"dd49a671dbf1","docs/components/layout/ListRow.jsx":"d62b265fd7e8","docs/components/navigation/TabBar.jsx":"9acfc7edbd2f","docs/ui_kits/lime-settings-android/AndroidDBTab.jsx":"a1cdd7959595","docs/ui_kits/lime-settings-android/AndroidIMTab.jsx":"3baf3f9ff5c4","docs/ui_kits/lime-settings-android/AndroidPrefsTab.jsx":"d24119993d96","docs/ui_kits/lime-settings-android/AndroidSetupTab.jsx":"d6972d2e7f85","docs/ui_kits/lime-settings-android/m3.jsx":"b7f39bdb1ffa","docs/ui_kits/lime-settings/DBTab.jsx":"1a323e981938","docs/ui_kits/lime-settings/IMTab.jsx":"026cd82ff383","docs/ui_kits/lime-settings/PrefsTab.jsx":"164202675494","docs/ui_kits/lime-settings/SetupTab.jsx":"b965e8d80d37","docs/ui_kits/lime-settings/icons.jsx":"d0270dc800f4"},"inlinedExternals":[],"unexposedExports":[]} */

(() => {

const __ds_ns = (window.LIMEDesignSystem_6ca3c0 = window.LIMEDesignSystem_6ca3c0 || {});

const __ds_scope = {};

(__ds_ns.__errors = __ds_ns.__errors || []);

// docs/components/controls/Button.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
/**
 * LIME Button — iOS-HIG button rendered in the LIME brand.
 * Variants: prominent (filled brand green), bordered (tinted fill),
 * plain (text only), destructive. Sizes: large / regular / small.
 */
function Button({
  variant = "prominent",
  size = "regular",
  destructive = false,
  fullWidth = false,
  disabled = false,
  icon = null,
  children,
  style = {},
  ...rest
}) {
  const sizes = {
    large: {
      pad: "14px 22px",
      font: "17px",
      weight: 600,
      radius: "var(--radius-button)",
      gap: "8px"
    },
    regular: {
      pad: "10px 18px",
      font: "17px",
      weight: 600,
      radius: "var(--radius-button)",
      gap: "6px"
    },
    small: {
      pad: "6px 12px",
      font: "15px",
      weight: 600,
      radius: "8px",
      gap: "5px"
    }
  };
  const s = sizes[size] || sizes.regular;
  const tint = destructive ? "var(--danger)" : "var(--accent)";
  const base = {
    display: "inline-flex",
    alignItems: "center",
    justifyContent: "center",
    gap: s.gap,
    width: fullWidth ? "100%" : "auto",
    padding: s.pad,
    fontFamily: "var(--font-sans)",
    fontSize: s.font,
    fontWeight: s.weight,
    lineHeight: 1.1,
    border: "none",
    borderRadius: s.radius,
    cursor: disabled ? "default" : "pointer",
    opacity: disabled ? 0.4 : 1,
    transition: "filter .12s ease, opacity .12s ease, background .12s ease",
    WebkitTapHighlightColor: "transparent"
  };
  const variants = {
    prominent: {
      background: tint,
      color: "#fff"
    },
    bordered: {
      background: destructive ? "rgba(255,59,48,0.12)" : "rgba(0,148,68,0.12)",
      color: tint
    },
    plain: {
      background: "transparent",
      color: tint,
      padding: s.pad.replace(/\d+px$/, "0")
    }
  };
  return /*#__PURE__*/React.createElement("button", _extends({
    type: "button",
    disabled: disabled,
    style: {
      ...base,
      ...(variants[variant] || variants.prominent),
      ...style
    },
    onMouseDown: e => {
      if (!disabled) e.currentTarget.style.filter = "brightness(0.92)";
    },
    onMouseUp: e => {
      e.currentTarget.style.filter = "none";
    },
    onMouseLeave: e => {
      e.currentTarget.style.filter = "none";
    }
  }, rest), icon && /*#__PURE__*/React.createElement("span", {
    style: {
      display: "inline-flex",
      width: "1.1em",
      height: "1.1em"
    }
  }, icon), children);
}
Object.assign(__ds_scope, { Button });
})(); } catch (e) { __ds_ns.__errors.push({ path: "docs/components/controls/Button.jsx", error: String((e && e.message) || e) }); }

// docs/components/controls/SegmentedControl.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
/**
 * LIME SegmentedControl — iOS segmented picker (used for 簡繁轉換, search-by
 * 字根/文字, etc). `options` is an array of strings or {label,value}.
 */
function SegmentedControl({
  options = [],
  value,
  onChange,
  style = {},
  ...rest
}) {
  const opts = options.map(o => typeof o === "string" ? {
    label: o,
    value: o
  } : o);
  const active = value ?? opts[0]?.value;
  return /*#__PURE__*/React.createElement("div", _extends({
    style: {
      display: "inline-flex",
      padding: 2,
      gap: 2,
      background: "rgba(120,120,128,0.12)",
      borderRadius: 9,
      fontFamily: "var(--font-sans)",
      ...style
    }
  }, rest), opts.map(o => {
    const on = o.value === active;
    return /*#__PURE__*/React.createElement("button", {
      key: o.value,
      type: "button",
      onClick: () => onChange && onChange(o.value),
      style: {
        flex: 1,
        padding: "6px 16px",
        fontSize: 13,
        fontWeight: on ? 600 : 500,
        color: "var(--text-primary)",
        background: on ? "#fff" : "transparent",
        border: "none",
        borderRadius: 7,
        boxShadow: on ? "0 1px 3px rgba(0,0,0,0.12), 0 1px 0.5px rgba(0,0,0,0.04)" : "none",
        cursor: "pointer",
        transition: "background .18s ease, box-shadow .18s ease",
        whiteSpace: "nowrap",
        WebkitTapHighlightColor: "transparent"
      }
    }, o.label);
  }));
}
Object.assign(__ds_scope, { SegmentedControl });
})(); } catch (e) { __ds_ns.__errors.push({ path: "docs/components/controls/SegmentedControl.jsx", error: String((e && e.message) || e) }); }

// docs/components/controls/Stepper.jsx
try { (() => {
/**
 * LIME Stepper — the −/＋ numeric score control from the record/related row
 * editors (AddRecordView / EditRecordView). Direct typing + clamp to [min,max].
 */
function Stepper({
  value = 0,
  min = 0,
  max = 9999,
  onChange,
  style = {}
}) {
  const set = v => onChange && onChange(Math.max(min, Math.min(max, v)));
  const circ = (content, fn, key) => /*#__PURE__*/React.createElement("button", {
    key: key,
    type: "button",
    onClick: fn,
    style: {
      width: 28,
      height: 28,
      flex: "0 0 auto",
      display: "inline-flex",
      alignItems: "center",
      justifyContent: "center",
      border: "none",
      borderRadius: "50%",
      background: "transparent",
      color: "var(--accent)",
      fontSize: 24,
      lineHeight: 0,
      cursor: "pointer",
      WebkitTapHighlightColor: "transparent"
    }
  }, content);
  return /*#__PURE__*/React.createElement("div", {
    style: {
      display: "inline-flex",
      alignItems: "center",
      gap: 6,
      fontFamily: "var(--font-sans)",
      ...style
    }
  }, circ("\u2212", () => set(value - 1), "minus"), /*#__PURE__*/React.createElement("input", {
    type: "text",
    inputMode: "numeric",
    value: value,
    onChange: e => {
      const n = parseInt(e.target.value.replace(/\D/g, ""), 10);
      set(Number.isNaN(n) ? 0 : n);
    },
    style: {
      width: 64,
      height: 34,
      textAlign: "center",
      fontSize: 17,
      fontWeight: 500,
      color: "var(--text-primary)",
      border: "1px solid var(--separator)",
      borderRadius: 8,
      background: "var(--bg)",
      outline: "none"
    }
  }), circ("+", () => set(value + 1), "plus"));
}
Object.assign(__ds_scope, { Stepper });
})(); } catch (e) { __ds_ns.__errors.push({ path: "docs/components/controls/Stepper.jsx", error: String((e && e.message) || e) }); }

// docs/components/controls/Switch.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
/**
 * LIME Switch — the iOS toggle with the LIME-green ON track.
 * Matches LimeSettings ToggleSwitchIcon (track #4CAF50, white thumb).
 */
function Switch({
  checked = false,
  disabled = false,
  onChange,
  style = {},
  ...rest
}) {
  const W = 51,
    H = 31,
    T = 27;
  return /*#__PURE__*/React.createElement("button", _extends({
    type: "button",
    role: "switch",
    "aria-checked": checked,
    disabled: disabled,
    onClick: () => !disabled && onChange && onChange(!checked),
    style: {
      position: "relative",
      width: W,
      height: H,
      flex: "0 0 auto",
      padding: 0,
      border: "none",
      borderRadius: H / 2,
      background: checked ? "var(--switch-on)" : "rgba(120,120,128,0.16)",
      cursor: disabled ? "default" : "pointer",
      opacity: disabled ? 0.5 : 1,
      transition: "background .22s ease",
      WebkitTapHighlightColor: "transparent",
      ...style
    }
  }, rest), /*#__PURE__*/React.createElement("span", {
    style: {
      position: "absolute",
      top: (H - T) / 2,
      left: checked ? W - T - 2 : 2,
      width: T,
      height: T,
      borderRadius: "50%",
      background: "#fff",
      boxShadow: "0 1px 1px rgba(0,0,0,0.18), 0 3px 8px rgba(0,0,0,0.10)",
      transition: "left .22s cubic-bezier(.4,.0,.2,1)"
    }
  }));
}
Object.assign(__ds_scope, { Switch });
})(); } catch (e) { __ds_ns.__errors.push({ path: "docs/components/controls/Switch.jsx", error: String((e && e.message) || e) }); }

// docs/components/feedback/StatusBanner.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
/**
 * LIME StatusBanner — the color-coded setup banner (§4.2). Status drives icon
 * + text color; the card itself uses a subtle tint.
 */
function StatusBanner({
  status = "success",
  children,
  icon,
  style = {},
  ...rest
}) {
  const map = {
    success: {
      fg: "var(--success-ink)",
      tint: "var(--status-tint-green)",
      sym: CHECK
    },
    warning: {
      fg: "var(--warning-ink)",
      tint: "var(--status-tint-yellow)",
      sym: WARN
    },
    danger: {
      fg: "var(--danger-ink)",
      tint: "var(--status-tint-red)",
      sym: XMARK
    }
  };
  const m = map[status] || map.success;
  return /*#__PURE__*/React.createElement("div", _extends({
    style: {
      display: "flex",
      alignItems: "center",
      gap: 10,
      padding: "12px 16px",
      borderRadius: "var(--radius-card)",
      background: m.tint,
      color: m.fg,
      font: "var(--weight-semibold) 15px/20px var(--font-sans)",
      ...style
    }
  }, rest), /*#__PURE__*/React.createElement("span", {
    style: {
      flex: "0 0 auto",
      width: 20,
      height: 20,
      display: "inline-flex"
    }
  }, icon || m.sym), /*#__PURE__*/React.createElement("span", null, children));
}
const CHECK = /*#__PURE__*/React.createElement("svg", {
  viewBox: "0 0 20 20",
  fill: "currentColor",
  width: "20",
  height: "20"
}, /*#__PURE__*/React.createElement("path", {
  d: "M10 0a10 10 0 100 20 10 10 0 000-20zm4.7 7.7l-5.4 5.4a1 1 0 01-1.4 0L5.3 10.5a1 1 0 011.4-1.4l1.9 1.9 4.7-4.7a1 1 0 011.4 1.4z"
}));
const WARN = /*#__PURE__*/React.createElement("svg", {
  viewBox: "0 0 20 20",
  fill: "currentColor",
  width: "20",
  height: "20"
}, /*#__PURE__*/React.createElement("path", {
  d: "M8.6 1.5L.7 15.2A1.6 1.6 0 002.1 17.6h15.8a1.6 1.6 0 001.4-2.4L11.4 1.5a1.6 1.6 0 00-2.8 0zM10 6a1 1 0 011 1v4a1 1 0 01-2 0V7a1 1 0 011-1zm0 9.5a1.2 1.2 0 110-2.4 1.2 1.2 0 010 2.4z"
}));
const XMARK = /*#__PURE__*/React.createElement("svg", {
  viewBox: "0 0 20 20",
  fill: "currentColor",
  width: "20",
  height: "20"
}, /*#__PURE__*/React.createElement("path", {
  d: "M10 0a10 10 0 100 20 10 10 0 000-20zm3.5 12.1a1 1 0 01-1.4 1.4L10 11.4l-2.1 2.1a1 1 0 01-1.4-1.4L8.6 10 6.5 7.9a1 1 0 011.4-1.4L10 8.6l2.1-2.1a1 1 0 011.4 1.4L11.4 10z"
}));
Object.assign(__ds_scope, { StatusBanner });
})(); } catch (e) { __ds_ns.__errors.push({ path: "docs/components/feedback/StatusBanner.jsx", error: String((e && e.message) || e) }); }

// docs/components/layout/ListGroup.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
/**
 * LIME ListGroup — an iOS grouped form section: optional uppercase footnote
 * header, a rounded card holding ListRows (auto-separated), optional footer.
 */
function ListGroup({
  header,
  footer,
  children,
  inset = true,
  style = {},
  ...rest
}) {
  const rows = React.Children.toArray(children).filter(Boolean);
  return /*#__PURE__*/React.createElement("section", _extends({
    style: {
      fontFamily: "var(--font-sans)",
      ...style
    }
  }, rest), header && /*#__PURE__*/React.createElement("div", {
    style: {
      font: "var(--weight-regular) 13px/18px var(--font-sans)",
      color: "var(--text-secondary)",
      textTransform: "uppercase",
      letterSpacing: "0.3px",
      padding: `0 ${inset ? 20 : 4}px 6px`
    }
  }, header), /*#__PURE__*/React.createElement("div", {
    style: {
      background: "var(--surface)",
      borderRadius: "var(--radius-card)",
      overflow: "hidden"
    }
  }, rows.map((row, i) => /*#__PURE__*/React.createElement(React.Fragment, {
    key: i
  }, row, i < rows.length - 1 && /*#__PURE__*/React.createElement("div", {
    style: {
      height: 0.5,
      background: "var(--separator)",
      marginLeft: 16
    }
  })))), footer && /*#__PURE__*/React.createElement("div", {
    style: {
      font: "var(--weight-regular) 13px/18px var(--font-sans)",
      color: "var(--text-secondary)",
      padding: `6px ${inset ? 20 : 4}px 0`
    }
  }, footer));
}
Object.assign(__ds_scope, { ListGroup });
})(); } catch (e) { __ds_ns.__errors.push({ path: "docs/components/layout/ListGroup.jsx", error: String((e && e.message) || e) }); }

// docs/components/layout/ListRow.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
/**
 * LIME ListRow — a versatile grouped-list row. Leading icon tile, title +
 * optional subtitle, and a trailing slot (value text, chevron, Switch, etc).
 */
function ListRow({
  icon = null,
  iconColor = "var(--accent)",
  leading = null,
  title,
  subtitle,
  value,
  trailing = null,
  chevron = false,
  destructive = false,
  onClick,
  style = {},
  ...rest
}) {
  return /*#__PURE__*/React.createElement("div", _extends({
    onClick: onClick,
    style: {
      display: "flex",
      alignItems: "center",
      gap: 12,
      minHeight: 44,
      padding: "11px 16px",
      cursor: onClick ? "pointer" : "default",
      fontFamily: "var(--font-sans)",
      WebkitTapHighlightColor: "transparent",
      ...style
    }
  }, rest), leading, !leading && icon && /*#__PURE__*/React.createElement("span", {
    style: {
      width: 29,
      height: 29,
      flex: "0 0 auto",
      display: "inline-flex",
      alignItems: "center",
      justifyContent: "center",
      borderRadius: 6.5,
      background: "var(--icon-tile)",
      color: "#fff"
    }
  }, /*#__PURE__*/React.createElement("span", {
    style: {
      width: 18,
      height: 18,
      display: "inline-flex"
    }
  }, icon)), /*#__PURE__*/React.createElement("div", {
    style: {
      flex: 1,
      minWidth: 0
    }
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      font: "var(--weight-regular) 17px/22px var(--font-sans)",
      color: destructive ? "var(--danger)" : "var(--text-primary)"
    }
  }, title), subtitle && /*#__PURE__*/React.createElement("div", {
    style: {
      font: "var(--weight-regular) 13px/17px var(--font-sans)",
      color: "var(--text-secondary)",
      marginTop: 1
    }
  }, subtitle)), value != null && /*#__PURE__*/React.createElement("span", {
    style: {
      font: "var(--weight-regular) 17px/22px var(--font-sans)",
      color: "var(--text-secondary)",
      whiteSpace: "nowrap"
    }
  }, value), trailing, chevron && /*#__PURE__*/React.createElement("svg", {
    width: "8",
    height: "14",
    viewBox: "0 0 8 14",
    fill: "none",
    style: {
      flex: "0 0 auto"
    }
  }, /*#__PURE__*/React.createElement("path", {
    d: "M1 1l6 6-6 6",
    stroke: "var(--text-tertiary)",
    strokeWidth: "2",
    strokeLinecap: "round",
    strokeLinejoin: "round"
  })));
}
Object.assign(__ds_scope, { ListRow });
})(); } catch (e) { __ds_ns.__errors.push({ path: "docs/components/layout/ListRow.jsx", error: String((e && e.message) || e) }); }

// docs/components/navigation/TabBar.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
/**
 * LIME TabBar — the iOS bottom tab bar for the 4-tab settings app.
 * items: [{ key, label, icon }]. Active tab tinted brand green.
 */
function TabBar({
  items = [],
  active,
  onChange,
  style = {},
  ...rest
}) {
  const cur = active ?? items[0]?.key;
  return /*#__PURE__*/React.createElement("nav", _extends({
    style: {
      display: "flex",
      alignItems: "stretch",
      background: "rgba(249,249,249,0.94)",
      backdropFilter: "saturate(180%) blur(20px)",
      WebkitBackdropFilter: "saturate(180%) blur(20px)",
      borderTop: "0.5px solid var(--separator)",
      fontFamily: "var(--font-sans)",
      ...style
    }
  }, rest), items.map(it => {
    const on = it.key === cur;
    const color = on ? "var(--accent)" : "var(--text-secondary)";
    return /*#__PURE__*/React.createElement("button", {
      key: it.key,
      type: "button",
      onClick: () => onChange && onChange(it.key),
      style: {
        flex: 1,
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        gap: 3,
        padding: "7px 0 9px",
        border: "none",
        background: "transparent",
        color,
        cursor: "pointer",
        WebkitTapHighlightColor: "transparent"
      }
    }, /*#__PURE__*/React.createElement("span", {
      style: {
        width: 26,
        height: 26,
        display: "inline-flex",
        alignItems: "center",
        justifyContent: "center"
      }
    }, it.icon), /*#__PURE__*/React.createElement("span", {
      style: {
        fontSize: 10,
        fontWeight: 500,
        letterSpacing: "0.1px"
      }
    }, it.label));
  }));
}
Object.assign(__ds_scope, { TabBar });
})(); } catch (e) { __ds_ns.__errors.push({ path: "docs/components/navigation/TabBar.jsx", error: String((e && e.message) || e) }); }

// docs/ui_kits/lime-settings-android/AndroidDBTab.jsx
try { (() => {
/* 資料庫 — Android DB Manager. Faithful to the real Android source
   LimeStudio/app/src/main/res/layout/fragment_db_manager.xml: a 28sp bold inline
   heading, then three sections each = a small secondary label + an UnelevatedButton
   (radius 10dp, neutral setup_status_bg background, left-aligned tinted text+icon)
   + a 12sp secondary footer. Backup is colorPrimary; restore + reset are red. */
(function () {
  const {
    Icon,
    Button
  } = window.LimeM3;

  // Action + its supporting description, stacked tightly (no redundant title).
  function Action({
    button,
    footer,
    gap,
    warn
  }) {
    return React.createElement("div", {
      style: {
        marginBottom: gap,
        display: "flex",
        flexDirection: "column",
        gap: 8
      }
    }, button, footer && React.createElement("div", {
      style: {
        display: "flex",
        alignItems: "flex-start",
        gap: 6,
        padding: "0 4px",
        font: "400 13px/18px 'Roboto', var(--font-sans)",
        color: warn ? "var(--md-error)" : "var(--md-on-surface-variant)"
      }
    }, warn && React.createElement(Icon, {
      name: "warning",
      size: 15,
      fill: true,
      color: "var(--md-error)",
      style: {
        flex: "0 0 auto",
        marginTop: 1
      }
    }), React.createElement("span", null, footer)));
  }
  function AndroidDBTab() {
    return React.createElement("div", {
      style: {
        padding: "16px 16px 24px",
        display: "flex",
        flexDirection: "column"
      }
    },
    // 28sp bold inline heading (textColorPrimary)
    React.createElement("div", {
      style: {
        font: "700 34px/41px 'Roboto', var(--font-sans)",
        color: "var(--md-on-surface)",
        padding: "8px 0 18px"
      }
    }, "資料庫管理"), React.createElement(Action, {
      gap: 12,
      footer: "備份包含所有字根、關聯字及喜好設定。",
      button: React.createElement(Button, {
        variant: "filled",
        icon: "upload",
        full: true
      }, "備份資料庫")
    }), React.createElement(Action, {
      gap: 12,
      footer: "還原後鍵盤將重新載入資料庫。",
      button: React.createElement(Button, {
        variant: "outlined",
        icon: "download",
        full: true
      }, "還原資料庫")
    }), React.createElement(Action, {
      gap: 0,
      warn: true,
      footer: "警告：將清除目前所有輸入法資料表，還原為萊姆內建的空白預設資料庫，此動作無法復原。",
      button: React.createElement(Button, {
        variant: "error",
        icon: "refresh",
        full: true
      }, "還原預設資料庫")
    }));
  }
  window.AndroidDBTab = AndroidDBTab;
})();
})(); } catch (e) { __ds_ns.__errors.push({ path: "docs/ui_kits/lime-settings-android/AndroidDBTab.jsx", error: String((e && e.message) || e) }); }

// docs/ui_kits/lime-settings-android/AndroidIMTab.jsx
try { (() => {
/* 輸入法 — Android IM Manager (Material 3). §5.1 IM list + 關聯字庫, with a
   Material drill-down detail. window.AndroidIMTab + window.AndroidIMDetail. */
(function () {
  const {
    Icon,
    Switch,
    Button
  } = window.LimeM3;
  const IMS = [{
    id: "phonetic",
    label: "注音",
    glyph: "ㄅ",
    color: "#5b8a2c",
    on: true
  }, {
    id: "cj",
    label: "倉頡",
    glyph: "倉",
    color: "#36618e",
    on: true
  }, {
    id: "ecj",
    label: "速成",
    glyph: "速",
    color: "#4c662b",
    on: true
  }, {
    id: "dayi",
    label: "大易",
    glyph: "易",
    color: "#8f4c38",
    on: false
  }, {
    id: "array",
    label: "行列",
    glyph: "行",
    color: "#6750a4",
    on: false
  }, {
    id: "pinyin",
    label: "拼音",
    glyph: "拼",
    color: "#a83b6f",
    on: false
  }];
  function Avatar({
    glyph
  }) {
    return React.createElement("span", {
      style: {
        width: 40,
        height: 40,
        flex: "0 0 auto",
        borderRadius: 11,
        background: "#8e8e93",
        color: "#fff",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        font: "500 18px/1 'Noto Sans TC', var(--font-sans)"
      }
    }, glyph);
  }
  function Row({
    m,
    onToggle,
    onOpen,
    selected
  }) {
    return React.createElement("div", {
      onClick: () => onOpen && onOpen(m),
      style: {
        display: "flex",
        alignItems: "center",
        gap: 16,
        minHeight: 64,
        padding: "8px 8px",
        cursor: "pointer",
        opacity: m.on ? 1 : 0.55,
        WebkitTapHighlightColor: "transparent",
        borderRadius: 16,
        background: selected ? "var(--md-secondary-container)" : "transparent",
        transition: "background .15s"
      }
    }, React.createElement(Avatar, {
      glyph: m.glyph
    }), React.createElement("div", {
      style: {
        flex: 1,
        minWidth: 0,
        font: "500 17px/22px 'Roboto', var(--font-sans)",
        color: selected ? "var(--md-on-secondary-container)" : "var(--md-on-surface)"
      }
    }, m.label), React.createElement("span", {
      onClick: e => e.stopPropagation()
    }, React.createElement(Switch, {
      checked: m.on,
      onChange: () => onToggle(m.id)
    })), React.createElement(Icon, {
      name: "chevron_right",
      size: 22,
      color: "var(--md-on-surface-variant)"
    }));
  }
  function AndroidIMTab({
    onOpen,
    selectedId,
    hideFab
  }) {
    const [ims, setIms] = React.useState(IMS);
    const toggle = id => setIms(s => s.map(m => m.id === id ? {
      ...m,
      on: !m.on
    } : m));
    return React.createElement("div", {
      style: {
        position: "relative",
        minHeight: "100%",
        paddingBottom: 24
      }
    }, React.createElement("div", {
      style: {
        padding: "4px 16px 0"
      }
    }, React.createElement("div", {
      style: {
        font: "700 34px/41px 'Roboto', var(--font-sans)",
        color: "var(--md-on-surface)",
        padding: "12px 8px 10px"
      }
    }, "管理輸入法"), React.createElement("div", {
      style: {
        font: "500 13px/18px 'Roboto', var(--font-sans)",
        color: "var(--md-primary)",
        padding: "8px 8px 6px"
      }
    }, "已安裝的輸入法"), ...ims.map(m => React.createElement(Row, {
      key: m.id,
      m,
      onToggle: toggle,
      onOpen,
      selected: selectedId === m.id
    })), React.createElement("div", {
      style: {
        height: 1,
        background: "var(--md-outline-variant)",
        margin: "10px 8px 8px"
      }
    }), React.createElement("div", {
      style: {
        font: "500 13px/18px 'Roboto', var(--font-sans)",
        color: "var(--md-primary)",
        padding: "8px 8px 6px"
      }
    }, "關聯字庫"), React.createElement("div", {
      onClick: () => onOpen && onOpen({
        id: "related",
        label: "關聯字庫",
        related: true
      }),
      style: {
        display: "flex",
        alignItems: "center",
        gap: 16,
        minHeight: 64,
        padding: "8px",
        cursor: "pointer",
        borderRadius: 16,
        background: selectedId === "related" ? "var(--md-secondary-container)" : "transparent",
        transition: "background .15s"
      }
    }, React.createElement("span", {
      style: {
        width: 40,
        height: 40,
        borderRadius: 11,
        background: "#8e8e93",
        color: "#fff",
        display: "flex",
        alignItems: "center",
        justifyContent: "center"
      }
    }, React.createElement(Icon, {
      name: "chat",
      size: 22,
      fill: true
    })), React.createElement("div", {
      style: {
        flex: 1,
        font: "500 17px/22px 'Roboto', var(--font-sans)",
        color: "var(--md-on-surface)"
      }
    }, "關聯字庫"), React.createElement(Icon, {
      name: "chevron_right",
      size: 22,
      color: "var(--md-on-surface-variant)"
    }))),
    // Compact round FAB — + only, no label
    !hideFab && React.createElement("button", {
      type: "button",
      style: {
        position: "absolute",
        right: 16,
        bottom: 16,
        width: 56,
        height: 56,
        borderRadius: "50%",
        border: 0,
        background: "var(--md-primary-container)",
        color: "var(--md-on-primary-container)",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        boxShadow: "0 3px 8px rgba(0,0,0,.2)",
        cursor: "pointer"
      }
    }, React.createElement(Icon, {
      name: "add",
      size: 26
    })));
  }

  // ── IM detail (drill-down) ─────────────────────────────────────────────
  function DetailGroup({
    header,
    children
  }) {
    return React.createElement("div", {
      style: {
        display: "flex",
        flexDirection: "column",
        gap: 4
      }
    }, React.createElement("div", {
      style: {
        font: "500 13px/18px 'Roboto', var(--font-sans)",
        color: "var(--md-primary)",
        padding: "0 6px"
      }
    }, header), React.createElement("div", {
      style: {
        background: "var(--md-surface-container-low)",
        borderRadius: 20,
        overflow: "hidden"
      }
    }, children));
  }
  function DetailRow({
    title,
    value,
    chevron,
    last,
    destructive,
    onClick,
    trailing
  }) {
    return React.createElement("div", {
      onClick,
      style: {
        display: "flex",
        alignItems: "center",
        gap: 12,
        minHeight: 56,
        padding: "10px 18px",
        cursor: onClick ? "pointer" : "default",
        borderBottom: last ? "none" : "1px solid var(--md-outline-variant)"
      }
    }, React.createElement("div", {
      style: {
        flex: 1,
        font: "400 17px/22px 'Roboto', var(--font-sans)",
        color: destructive ? "var(--md-error)" : "var(--md-on-surface)"
      }
    }, title), value != null && React.createElement("span", {
      style: {
        font: "400 17px/22px 'Roboto', var(--font-sans)",
        color: "var(--md-on-surface-variant)"
      }
    }, value), trailing, chevron && React.createElement(Icon, {
      name: "chevron_right",
      size: 22,
      color: "var(--md-on-surface-variant)"
    }));
  }
  function AndroidIMDetail({
    im,
    onClose,
    embedded
  }) {
    const [backup, setBackup] = React.useState(true);
    const open = !!im;
    const data = im || {};

    // Two-pane empty state — shown in the right pane before any IM is chosen.
    if (embedded && !im) {
      return React.createElement("div", {
        style: {
          height: "100%",
          display: "flex",
          flexDirection: "column",
          alignItems: "center",
          justifyContent: "center",
          gap: 14,
          color: "var(--md-on-surface-variant)",
          padding: 40
        }
      }, React.createElement("span", {
        style: {
          width: 88,
          height: 88,
          borderRadius: "50%",
          background: "var(--md-surface-container-high)",
          display: "flex",
          alignItems: "center",
          justifyContent: "center"
        }
      }, React.createElement(Icon, {
        name: "touch_app",
        size: 40,
        color: "var(--md-on-surface-variant)"
      })), React.createElement("div", {
        style: {
          font: "400 17px/22px 'Roboto', var(--font-sans)",
          textAlign: "center"
        }
      }, "請從左側選擇輸入法以檢視及編輯詳細資料"));
    }
    const body = React.createElement("div", {
      style: {
        padding: "8px 16px 28px",
        display: "flex",
        flexDirection: "column",
        gap: 22
      }
    }, !data.related && React.createElement(DetailGroup, {
      header: "輸入法資訊"
    }, React.createElement(DetailRow, {
      title: "名稱",
      value: data.label,
      chevron: true
    }), React.createElement(DetailRow, {
      title: "版本",
      value: "2024.03",
      chevron: true
    }), React.createElement(DetailRow, {
      title: "結束鍵",
      value: "—",
      chevron: true
    }), React.createElement(DetailRow, {
      title: "筆數",
      value: "34,838",
      last: true
    })), !data.related && React.createElement(DetailGroup, {
      header: "軟鍵盤配置"
    }, React.createElement(DetailRow, {
      title: "鍵盤佈局",
      value: "標準",
      chevron: true,
      last: true
    })), React.createElement(DetailGroup, {
      header: data.related ? "關聯字庫" : "字根資料表"
    }, React.createElement(DetailRow, {
      title: data.related ? "瀏覽 / 編輯關聯字庫" : "瀏覽 / 編輯資料表",
      chevron: true,
      last: true
    })), !data.related && React.createElement(DetailGroup, {
      header: "選項"
    }, React.createElement(DetailRow, {
      title: "刪除時備份已學習記錄",
      last: true,
      trailing: React.createElement(Switch, {
        checked: backup,
        onChange: setBackup
      })
    })), !data.related && React.createElement("div", {
      style: {
        padding: "4px 2px"
      }
    }, React.createElement(Button, {
      variant: "error",
      icon: "delete",
      full: true,
      onClick: onClose
    }, "移除輸入法")));

    // Embedded (tablet two-pane): static pane, app bar shows the IM name, no slide-over.
    if (embedded) {
      return React.createElement("div", {
        style: {
          height: "100%",
          display: "flex",
          flexDirection: "column",
          background: "var(--md-surface)"
        }
      }, React.createElement("div", {
        className: "appbar"
      }, React.createElement("span", {
        className: "title"
      }, data.label || "")), React.createElement("div", {
        className: "scroll"
      }, body));
    }

    // Phone: full-screen slide-over with status bar + back affordance.
    return React.createElement("div", {
      className: "detail" + (open ? " open" : "")
    }, React.createElement("div", {
      className: "appbar"
    }, React.createElement("span", {
      className: "leading",
      onClick: onClose
    }, React.createElement(Icon, {
      name: "arrow_back",
      size: 24
    })), React.createElement("span", {
      className: "title"
    }, data.label || "")), React.createElement("div", {
      className: "scroll"
    }, body));
  }
  window.AndroidIMTab = AndroidIMTab;
  window.AndroidIMDetail = AndroidIMDetail;
})();
})(); } catch (e) { __ds_ns.__errors.push({ path: "docs/ui_kits/lime-settings-android/AndroidIMTab.jsx", error: String((e && e.message) || e) }); }

// docs/ui_kits/lime-settings-android/AndroidPrefsTab.jsx
try { (() => {
/* 喜好設定 — Android IM Preferences (Material 3). Mirrors §8 / iOS PrefsTab.
   Material preference rows: title + summary, trailing switch or value. */
(function () {
  const {
    Icon,
    Switch
  } = window.LimeM3;
  function Group({
    header,
    children
  }) {
    return React.createElement("div", {
      style: {
        display: "flex",
        flexDirection: "column"
      }
    }, React.createElement("div", {
      style: {
        font: "500 13px/18px 'Roboto', var(--font-sans)",
        color: "var(--md-primary)",
        padding: "10px 8px 4px"
      }
    }, header), children);
  }
  // Material preference row — leading icon optional, title + summary, trailing slot.
  function Pref({
    icon,
    title,
    summary,
    value,
    chevron,
    trailing,
    dim
  }) {
    return React.createElement("div", {
      style: {
        display: "flex",
        alignItems: "center",
        gap: 16,
        minHeight: 60,
        padding: "10px 8px",
        opacity: dim ? 0.5 : 1,
        WebkitTapHighlightColor: "transparent",
        borderRadius: 16,
        cursor: chevron ? "pointer" : "default"
      }
    }, icon && React.createElement("span", {
      style: {
        width: 24,
        display: "flex",
        justifyContent: "center",
        color: "var(--md-on-surface-variant)"
      }
    }, React.createElement(Icon, {
      name: icon,
      size: 24
    })), React.createElement("div", {
      style: {
        flex: 1,
        minWidth: 0
      }
    }, React.createElement("div", {
      style: {
        font: "400 17px/22px 'Roboto', var(--font-sans)",
        color: "var(--md-on-surface)"
      }
    }, title), summary && React.createElement("div", {
      style: {
        font: "400 13px/18px 'Roboto', var(--font-sans)",
        color: "var(--md-on-surface-variant)",
        marginTop: 1
      }
    }, summary)), value != null && React.createElement("span", {
      style: {
        font: "400 17px/22px 'Roboto', var(--font-sans)",
        color: "var(--md-primary)"
      }
    }, value), trailing, chevron && React.createElement(Icon, {
      name: "chevron_right",
      size: 22,
      color: "var(--md-on-surface-variant)"
    }));
  }
  function Divider() {
    return React.createElement("div", {
      style: {
        height: 1,
        background: "var(--md-outline-variant)",
        margin: "6px 8px"
      }
    });
  }

  // M3 single-select segmented buttons — equal-width segments (flex: 1).
  function Segmented({
    options,
    value,
    onChange
  }) {
    return React.createElement("div", {
      style: {
        display: "flex",
        width: "100%",
        borderRadius: 20,
        overflow: "hidden",
        boxShadow: "inset 0 0 0 1px var(--md-outline)"
      }
    }, options.map((o, i) => {
      const sel = o === value;
      return React.createElement("button", {
        key: o,
        onClick: () => onChange(o),
        style: {
          flex: 1,
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          gap: 4,
          height: 38,
          padding: "0 8px",
          border: 0,
          borderLeft: i ? "1px solid var(--md-outline)" : "none",
          background: sel ? "var(--md-secondary-container)" : "transparent",
          color: sel ? "var(--md-on-secondary-container)" : "var(--md-on-surface)",
          font: "500 13px/1 'Roboto', var(--font-sans)",
          cursor: "pointer",
          whiteSpace: "nowrap"
        }
      }, sel && React.createElement(Icon, {
        name: "check",
        size: 16
      }), o);
    }));
  }
  function AndroidPrefsTab() {
    const [s, setS] = React.useState({
      numberRow: true,
      vibrate: true,
      sound: false,
      smart: true,
      autoSymbol: false,
      persistLang: false,
      related: true,
      learn: true,
      dict: true,
      autoCap: true
    });
    const t = k => () => setS(p => ({
      ...p,
      [k]: !p[k]
    }));
    const sw = k => React.createElement(Switch, {
      checked: s[k],
      onChange: t(k)
    });
    const [han, setHan] = React.useState("繁轉簡");
    return React.createElement("div", {
      style: {
        padding: "0 16px 28px",
        display: "flex",
        flexDirection: "column"
      }
    }, React.createElement("div", {
      style: {
        font: "700 34px/41px 'Roboto', var(--font-sans)",
        color: "var(--md-on-surface)",
        padding: "12px 8px 10px"
      }
    }, "喜好設定"), React.createElement(Group, {
      header: "鍵盤外觀"
    }, React.createElement(Pref, {
      icon: "palette",
      title: "鍵盤樣式",
      value: "放鬆綠",
      chevron: true
    }), React.createElement(Pref, {
      title: "鍵盤大小",
      value: "一般",
      chevron: true
    }), React.createElement(Pref, {
      title: "字型大小",
      value: "一般",
      chevron: true
    }), React.createElement(Pref, {
      title: "數字列英文鍵盤",
      summary: "在英文鍵盤顯示數字列 (5 列鍵盤)",
      trailing: sw("numberRow")
    }), React.createElement(Pref, {
      title: "顯示方向鍵",
      value: "無",
      chevron: true
    })), React.createElement(Divider), React.createElement(Group, {
      header: "鍵盤回饋"
    }, React.createElement(Pref, {
      icon: "vibration",
      title: "打字震動",
      trailing: sw("vibrate")
    }), React.createElement(Pref, {
      title: "震動強度",
      value: "中",
      chevron: true,
      dim: !s.vibrate
    }), React.createElement(Pref, {
      title: "打字音效",
      trailing: sw("sound")
    })), React.createElement(Divider), React.createElement(Group, {
      header: "輸入法行為"
    }, React.createElement(Pref, {
      icon: "auto_awesome",
      title: "開啟中文智慧組詞",
      summary: "部份輸入法可能會影響中英混打功能",
      trailing: sw("smart")
    }), React.createElement(Pref, {
      title: "自動中文標點模式",
      summary: "無候選字詞時顯示中文標點選項",
      trailing: sw("autoSymbol")
    }), React.createElement(Pref, {
      title: "記憶中英模式",
      summary: "下次切換前保持中英模式",
      trailing: sw("persistLang")
    }), React.createElement(Pref, {
      icon: "search",
      title: "字根反查設定",
      chevron: true
    })), React.createElement(Divider), React.createElement(Group, {
      header: "簡繁轉換"
    }, React.createElement("div", {
      style: {
        padding: "10px 8px",
        display: "flex",
        flexDirection: "column",
        gap: 12
      }
    }, React.createElement("div", {
      style: {
        font: "400 17px/22px 'Roboto', var(--font-sans)",
        color: "var(--md-on-surface)"
      }
    }, "字碼轉換"), React.createElement(Segmented, {
      options: ["無", "繁轉簡", "簡轉繁"],
      value: han,
      onChange: setHan
    }), React.createElement("div", {
      style: {
        font: "400 13px/18px 'Roboto', var(--font-sans)",
        color: "var(--md-on-surface-variant)"
      }
    }, "套用於所有輸入法的候選字輸出。"))), React.createElement(Divider), React.createElement(Group, {
      header: "關聯字與學習"
    }, React.createElement(Pref, {
      icon: "chat",
      title: "啟用關聯字庫",
      summary: "啟用關聯字庫功能",
      trailing: sw("related")
    }), React.createElement(Pref, {
      title: "自動學習新詞",
      summary: "從常用關聯字學習新詞",
      trailing: sw("learn")
    })), React.createElement(Divider), React.createElement(Group, {
      header: "英文鍵盤"
    }, React.createElement(Pref, {
      icon: "abc",
      title: "啟用英文字典",
      summary: "英文模式下顯示英文建議字",
      trailing: sw("dict")
    }), React.createElement(Pref, {
      title: "首字自動大寫",
      summary: "句首字母自動轉為大寫",
      trailing: sw("autoCap")
    })));
  }
  window.AndroidPrefsTab = AndroidPrefsTab;
})();
})(); } catch (e) { __ds_ns.__errors.push({ path: "docs/ui_kits/lime-settings-android/AndroidPrefsTab.jsx", error: String((e && e.message) || e) }); }

// docs/ui_kits/lime-settings-android/AndroidSetupTab.jsx
try { (() => {
/* 設定 — Android App Setup tab. Grounded in the real Android source
   LimeStudio/app/src/main/res/layout/fragment_setup.xml + SetupFragment.java
   (horizontal brand row, neutral status card, About card), but kept VISUALLY
   ALIGNED to the iOS SetupTab per the user's request (b): same structure —
   brand hero, success status, 設定萊姆輸入法 step guide, 前往設定 button, and a
   three-chip About footer (使用手冊 / 版權說明 / 原始碼) + copyright banner.
   Theme colour is inherited from the system (Material You) — no in-app control. */
(function () {
  const {
    Icon,
    Button
  } = window.LimeM3;
  const LICENSE_URL = "https://lime-ime.github.io/limeime/pages/license.html"; // R.string.url_license_limeime
  const MANUAL_URL = "https://lime-ime.github.io/limeime/pages/index.html";
  const GITHUB_URL = "https://github.com/lime-ime/limeime"; // R.string.url_github_limeime

  const FG_GREEN = "#2e7d32"; // @color/setup_status_fg_green
  const STATUS_BG = "color-mix(in srgb, #808080 12%, transparent)"; // @color/setup_status_bg

  // Status card — neutral background, icon + text in the state colour (iOS parity).
  function StatusCard() {
    return React.createElement("div", {
      style: {
        display: "flex",
        alignItems: "center",
        gap: 10,
        padding: "12px 14px",
        borderRadius: 12,
        background: STATUS_BG
      }
    }, React.createElement(Icon, {
      name: "check_circle",
      size: 20,
      fill: true,
      color: FG_GREEN
    }), React.createElement("span", {
      style: {
        font: "500 15px/20px 'Roboto', var(--font-sans)",
        color: FG_GREEN
      }
    }, "萊姆輸入法已啟用"));
  }

  // Green M3 toggle visual used in the activation step guide (matches iOS GreenToggle).
  function GreenToggle() {
    return React.createElement("div", {
      style: {
        width: 30,
        height: 18,
        borderRadius: 9,
        background: "var(--md-primary)",
        position: "relative",
        flex: "0 0 auto"
      }
    }, React.createElement("div", {
      style: {
        position: "absolute",
        right: 2,
        top: 2,
        width: 14,
        height: 14,
        borderRadius: "50%",
        background: "var(--md-on-primary)",
        boxShadow: "0 1px 1px rgba(0,0,0,.18)"
      }
    }));
  }
  function StepRow({
    icon,
    text
  }) {
    return React.createElement("div", {
      style: {
        display: "flex",
        alignItems: "center",
        gap: 16
      }
    }, React.createElement("div", {
      style: {
        width: 32,
        display: "flex",
        justifyContent: "center",
        color: "var(--md-primary)"
      }
    }, icon), React.createElement("div", {
      style: {
        font: "400 17px/22px 'Roboto', var(--font-sans)",
        color: "var(--md-on-surface)"
      }
    }, text));
  }

  // Compact link chip aligned to the iOS footer: equal-width, rounded, tonal fill,
  // icon over label + external glyph.
  function LinkChip({
    href,
    icon,
    children
  }) {
    return React.createElement("a", {
      href,
      target: "_blank",
      rel: "noopener noreferrer",
      style: {
        flex: 1,
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        gap: 7,
        padding: "15px 8px 13px",
        borderRadius: 14,
        background: "var(--md-surface-container-high)",
        color: "var(--md-primary)",
        textDecoration: "none",
        WebkitTapHighlightColor: "transparent"
      }
    }, React.createElement(Icon, {
      name: icon,
      size: 22,
      color: "var(--md-primary)"
    }), React.createElement("span", {
      style: {
        display: "inline-flex",
        alignItems: "center",
        gap: 3,
        font: "500 14px/18px 'Roboto', var(--font-sans)"
      }
    }, children, React.createElement(Icon, {
      name: "open_in_new",
      size: 13,
      style: {
        opacity: .7
      }
    })));
  }
  function AndroidSetupTab() {
    return React.createElement("div", {
      style: {
        padding: "8px 24px 28px",
        display: "flex",
        flexDirection: "column",
        gap: 24
      }
    },
    // Brand hero — plain logo + wordmark, horizontal (aligned to iOS)
    React.createElement("div", {
      style: {
        display: "flex",
        flexDirection: "row",
        alignItems: "center",
        justifyContent: "center",
        gap: 16,
        paddingTop: 20
      }
    }, React.createElement("img", {
      src: "../../assets/lime-logo-android.png",
      alt: "LIME",
      style: {
        width: 92,
        height: 92,
        objectFit: "contain"
      }
    }), React.createElement("div", {
      style: {
        font: "700 30px/36px 'Roboto', var(--font-sans)",
        color: "var(--md-on-surface)"
      }
    }, "萊姆輸入法")), React.createElement(StatusCard), React.createElement("div", {
      style: {
        font: "700 28px/34px 'Roboto', var(--font-sans)",
        color: "var(--md-on-surface)"
      }
    }, "設定萊姆輸入法"), React.createElement("div", {
      style: {
        display: "flex",
        flexDirection: "column",
        gap: 16
      }
    }, React.createElement(StepRow, {
      icon: React.createElement(Icon, {
        name: "keyboard",
        size: 24,
        color: "var(--md-primary)"
      }),
      text: "輕觸「鍵盤」"
    }), React.createElement(StepRow, {
      icon: React.createElement(GreenToggle),
      text: "開啟萊姆輸入法"
    }), React.createElement(StepRow, {
      icon: React.createElement(GreenToggle),
      text: "開啟「允許完整取用」"
    })), React.createElement("div", {
      style: {
        font: "400 15px/20px 'Roboto', var(--font-sans)",
        color: "var(--md-on-surface-variant)",
        textAlign: "center"
      }
    }, "完整取用用於備份資料庫、按鍵震動回饋與編輯字根資料表。不開啟也能正常輸入、安裝或匯入輸入法，萊姆輸入法不會收集或傳送任何個人資料。"), React.createElement(Button, {
      variant: "filled",
      full: true
    }, "前往設定"), React.createElement("div", {
      style: {
        font: "400 13px/18px 'Roboto', var(--font-sans)",
        color: "var(--md-on-surface-variant)",
        textAlign: "center"
      }
    }, "若設定未直接顯示萊姆輸入法，請到「設定」>「系統」>「語言與輸入」>「螢幕鍵盤」開啟。"),
    // About footer — three equal-width chips + one-line copyright (aligned to iOS)
    React.createElement("div", {
      style: {
        display: "flex",
        flexDirection: "column",
        gap: 16,
        paddingTop: 10
      }
    }, React.createElement("div", {
      style: {
        height: 1,
        background: "var(--md-outline-variant)",
        margin: "0 -24px"
      }
    }), React.createElement("div", {
      style: {
        display: "flex",
        gap: 10
      }
    }, React.createElement(LinkChip, {
      href: MANUAL_URL,
      icon: "menu_book"
    }, "使用手冊"), React.createElement(LinkChip, {
      href: LICENSE_URL,
      icon: "description"
    }, "版權說明"), React.createElement(LinkChip, {
      href: GITHUB_URL,
      icon: "code"
    }, "原始碼")), React.createElement("div", {
      style: {
        font: "400 13px/18px 'Roboto', var(--font-sans)",
        color: "var(--md-on-surface-variant)",
        textAlign: "center",
        paddingTop: 6
      }
    }, "© LIME 萊姆輸入法 6.1.15 - 2026")));
  }
  window.AndroidSetupTab = AndroidSetupTab;
})();
})(); } catch (e) { __ds_ns.__errors.push({ path: "docs/ui_kits/lime-settings-android/AndroidSetupTab.jsx", error: String((e && e.message) || e) }); }

// docs/ui_kits/lime-settings-android/m3.jsx
try { (() => {
/* Material 3 primitives + Material You palette engine for the LIME Android kit.
   Exposed on window.LimeM3. Icons use the Material Symbols Rounded webfont. */
(function () {
  // ── Material You light-scheme tonal palettes (seed → roles) ──────────────
  // Re-seeding these on #device is the whole point: on Android the app chrome
  // adopts the SYSTEM colour scheme (Material You), unlike the fixed-brand iOS app.
  // Each palette carries BOTH a light (v) and dark (d) tonal role map. Material You
  // generates both from the one system seed; day/night is MODE_NIGHT_FOLLOW_SYSTEM.
  const PALETTES = {
    green: {
      label: "綠",
      seed: "#5b8a2c",
      v: {
        "primary": "#386a20",
        "on-primary": "#ffffff",
        "primary-container": "#b7f397",
        "on-primary-container": "#042100",
        "secondary-container": "#d7e8c8",
        "on-secondary-container": "#121f0d",
        "surface": "#f8faf0",
        "surface-container-low": "#f2f5e9",
        "surface-container": "#ecefe3",
        "surface-container-high": "#e6e9de",
        "surface-container-highest": "#e0e4d8",
        "on-surface": "#1a1c16",
        "on-surface-variant": "#44483b",
        "outline": "#75796c",
        "outline-variant": "#c5c8b9",
        "error": "#ba1a1a"
      },
      d: {
        "primary": "#9cd67d",
        "on-primary": "#0a3900",
        "primary-container": "#1f5200",
        "on-primary-container": "#b7f397",
        "secondary-container": "#3a4a31",
        "on-secondary-container": "#d7e8c8",
        "surface": "#11140d",
        "surface-container-low": "#1a1c16",
        "surface-container": "#1e201a",
        "surface-container-high": "#282b24",
        "surface-container-highest": "#33362e",
        "on-surface": "#e2e3d8",
        "on-surface-variant": "#c5c8b9",
        "outline": "#8f9285",
        "outline-variant": "#44483b",
        "error": "#ffb4ab"
      }
    },
    purple: {
      label: "紫",
      seed: "#6750a4",
      v: {
        "primary": "#65558f",
        "on-primary": "#ffffff",
        "primary-container": "#e9ddff",
        "on-primary-container": "#21005d",
        "secondary-container": "#e8def8",
        "on-secondary-container": "#1d192b",
        "surface": "#fdf7ff",
        "surface-container-low": "#f7f2fa",
        "surface-container": "#f2ecf4",
        "surface-container-high": "#ece6ee",
        "surface-container-highest": "#e6e0e9",
        "on-surface": "#1d1b20",
        "on-surface-variant": "#49454e",
        "outline": "#7a757f",
        "outline-variant": "#cac4cf",
        "error": "#ba1a1a"
      },
      d: {
        "primary": "#cfbdfe",
        "on-primary": "#36275d",
        "primary-container": "#4d3d75",
        "on-primary-container": "#e9ddff",
        "secondary-container": "#4a4458",
        "on-secondary-container": "#e8def8",
        "surface": "#141218",
        "surface-container-low": "#1d1b20",
        "surface-container": "#211f26",
        "surface-container-high": "#2b2930",
        "surface-container-highest": "#36343b",
        "on-surface": "#e6e0e9",
        "on-surface-variant": "#cac4cf",
        "outline": "#948f99",
        "outline-variant": "#49454e",
        "error": "#ffb4ab"
      }
    },
    blue: {
      label: "藍",
      seed: "#2196f3",
      v: {
        "primary": "#36618e",
        "on-primary": "#ffffff",
        "primary-container": "#d1e4ff",
        "on-primary-container": "#001d36",
        "secondary-container": "#d7e3f7",
        "on-secondary-container": "#101c2b",
        "surface": "#f8f9ff",
        "surface-container-low": "#f2f3fa",
        "surface-container": "#eceef4",
        "surface-container-high": "#e6e8ee",
        "surface-container-highest": "#e1e2e8",
        "on-surface": "#191c20",
        "on-surface-variant": "#43474e",
        "outline": "#73777f",
        "outline-variant": "#c3c6cf",
        "error": "#ba1a1a"
      },
      d: {
        "primary": "#a0cafd",
        "on-primary": "#003258",
        "primary-container": "#194975",
        "on-primary-container": "#d1e4ff",
        "secondary-container": "#3b4858",
        "on-secondary-container": "#d7e3f7",
        "surface": "#101418",
        "surface-container-low": "#191c20",
        "surface-container": "#1d2024",
        "surface-container-high": "#272a2f",
        "surface-container-highest": "#32353a",
        "on-surface": "#e1e2e8",
        "on-surface-variant": "#c3c6cf",
        "outline": "#8d9199",
        "outline-variant": "#43474e",
        "error": "#ffb4ab"
      }
    },
    coral: {
      label: "橘",
      seed: "#b0573c",
      v: {
        "primary": "#8f4c38",
        "on-primary": "#ffffff",
        "primary-container": "#ffdbd1",
        "on-primary-container": "#3a0b01",
        "secondary-container": "#ffdbcf",
        "on-secondary-container": "#2c150d",
        "surface": "#fff8f6",
        "surface-container-low": "#fef1ec",
        "surface-container": "#f8ebe6",
        "surface-container-high": "#f3e5e0",
        "surface-container-highest": "#ece0da",
        "on-surface": "#231917",
        "on-surface-variant": "#53433f",
        "outline": "#85736e",
        "outline-variant": "#d8c2bb",
        "error": "#ba1a1a"
      },
      d: {
        "primary": "#ffb5a0",
        "on-primary": "#561f0f",
        "primary-container": "#723523",
        "on-primary-container": "#ffdbd1",
        "secondary-container": "#5d4037",
        "on-secondary-container": "#ffdbcf",
        "surface": "#1a110f",
        "surface-container-low": "#231917",
        "surface-container": "#271d1b",
        "surface-container-high": "#322825",
        "surface-container-highest": "#3d3230",
        "on-surface": "#f1dfda",
        "on-surface-variant": "#d8c2bb",
        "outline": "#a08c87",
        "outline-variant": "#53433f",
        "error": "#ffb4ab"
      }
    }
  };
  let _seed = "green",
    _mode = "light";
  function applyPalette(key, mode) {
    if (key) _seed = key;
    if (mode) _mode = mode;
    const p = PALETTES[_seed];
    if (!p) return;
    const dev = document.getElementById("device");
    if (!dev) return;
    const roles = _mode === "dark" ? p.d : p.v;
    Object.entries(roles).forEach(([k, val]) => dev.style.setProperty("--md-" + k, val));
    // Semantic status colours are palette-independent (Material You themes only
    // `error`); set success/warning per light/dark so banners read in both modes.
    const status = _mode === "dark" ? {
      success: "#9cd67d",
      warning: "#ffb951"
    } : {
      success: "#2e7d32",
      warning: "#8a6100"
    };
    dev.style.setProperty("--md-success", status.success);
    dev.style.setProperty("--md-warning", status.warning);
    dev.setAttribute("data-mode", _mode);
  }
  const Icon = ({
    name,
    size = 24,
    fill = false,
    color,
    style
  }) => React.createElement("span", {
    className: "msr" + (fill ? " fill" : ""),
    style: {
      fontSize: size,
      color: color || "inherit",
      ...style
    }
  }, name);

  // ── Material 3 switch ────────────────────────────────────────────────────
  function Switch({
    checked,
    onChange,
    disabled
  }) {
    const on = !!checked;
    return React.createElement("button", {
      onClick: () => !disabled && onChange && onChange(!on),
      "aria-pressed": on,
      style: {
        flex: "0 0 auto",
        width: 52,
        height: 32,
        borderRadius: 16,
        position: "relative",
        border: 0,
        padding: 0,
        cursor: disabled ? "default" : "pointer",
        transition: "background .18s, border-color .18s",
        background: on ? "var(--md-primary)" : "var(--md-surface-container-highest)",
        boxShadow: on ? "none" : "inset 0 0 0 2px var(--md-outline)",
        opacity: disabled ? .38 : 1
      }
    }, React.createElement("span", {
      style: {
        position: "absolute",
        top: "50%",
        left: on ? 30 : 8,
        transform: "translateY(-50%)",
        width: on ? 24 : 16,
        height: on ? 24 : 16,
        borderRadius: "50%",
        background: on ? "var(--md-on-primary)" : "var(--md-outline)",
        transition: "left .18s cubic-bezier(.2,0,0,1), width .18s, height .18s, background .18s",
        display: "flex",
        alignItems: "center",
        justifyContent: "center"
      }
    }, on && React.createElement("span", {
      className: "msr fill",
      style: {
        fontSize: 16,
        color: "var(--md-on-primary-container)"
      }
    }, "check")));
  }

  // ── Material 3 buttons ───────────────────────────────────────────────────
  function Button({
    variant = "filled",
    icon,
    children,
    onClick,
    color,
    full
  }) {
    const base = {
      display: "inline-flex",
      alignItems: "center",
      justifyContent: "center",
      gap: 8,
      height: 46,
      padding: icon ? "0 24px 0 16px" : "0 24px",
      borderRadius: 20,
      border: 0,
      font: "600 17px/1 'Roboto', var(--font-sans)",
      letterSpacing: ".1px",
      cursor: "pointer",
      width: full ? "100%" : "auto",
      WebkitTapHighlightColor: "transparent"
    };
    const skins = {
      filled: {
        background: "var(--md-primary)",
        color: "var(--md-on-primary)"
      },
      tonal: {
        background: "var(--md-secondary-container)",
        color: "var(--md-on-secondary-container)"
      },
      // Aligned to the iOS Button "bordered" variant: a subtle 12% tinted fill
      // (no outline), text in the tint colour. Adapts across light/dark + palettes.
      outlined: {
        background: "color-mix(in srgb, " + (color || "var(--md-primary)") + " 12%, transparent)",
        color: color || "var(--md-primary)"
      },
      text: {
        background: "transparent",
        color: color || "var(--md-primary)",
        padding: "0 12px"
      },
      error: {
        background: "color-mix(in srgb, var(--md-error) 12%, transparent)",
        color: "var(--md-error)"
      }
    };
    return React.createElement("button", {
      onClick,
      style: {
        ...base,
        ...skins[variant]
      }
    }, icon && React.createElement(Icon, {
      name: icon,
      size: 18,
      color: skins[variant].color
    }), children);
  }

  // ── Bottom NavigationBar (M3) ────────────────────────────────────────────
  function NavBar({
    items,
    active,
    onChange
  }) {
    return React.createElement("nav", {
      style: {
        display: "flex",
        height: 80,
        background: "var(--md-surface-container)",
        paddingTop: 12,
        transition: "background .35s ease"
      }
    }, items.map(it => {
      const sel = it.key === active;
      return React.createElement("button", {
        key: it.key,
        onClick: () => onChange(it.key),
        style: {
          flex: 1,
          display: "flex",
          flexDirection: "column",
          alignItems: "center",
          gap: 4,
          border: 0,
          background: "transparent",
          cursor: "pointer",
          padding: 0,
          WebkitTapHighlightColor: "transparent"
        }
      }, React.createElement("span", {
        style: {
          width: 64,
          height: 32,
          borderRadius: 16,
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          background: sel ? "var(--md-secondary-container)" : "transparent",
          transition: "background .18s"
        }
      }, React.createElement(Icon, {
        name: it.icon,
        size: 24,
        fill: sel,
        color: sel ? "var(--md-on-secondary-container)" : "var(--md-on-surface-variant)"
      })), React.createElement("span", {
        style: {
          font: (sel ? 700 : 500) + " 12px/16px 'Roboto', var(--font-sans)",
          color: sel ? "var(--md-on-surface)" : "var(--md-on-surface-variant)"
        }
      }, it.label));
    }));
  }

  // ── Navigation Rail (M3, tablet) ─────────────────────────────────────────
  function NavRail({
    items,
    active,
    onChange,
    fab
  }) {
    return React.createElement("nav", {
      style: {
        width: 88,
        flex: "0 0 auto",
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        gap: 12,
        padding: "20px 0",
        background: "var(--md-surface)",
        transition: "background .35s ease"
      }
    }, fab && React.createElement("button", {
      type: "button",
      onClick: fab.onClick,
      style: {
        width: 56,
        height: 56,
        borderRadius: 16,
        border: 0,
        marginBottom: 8,
        background: "var(--md-primary-container)",
        color: "var(--md-on-primary-container)",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        cursor: "pointer",
        boxShadow: "0 1px 3px rgba(0,0,0,.3)"
      }
    }, React.createElement(Icon, {
      name: fab.icon || "add",
      size: 24
    })), items.map(it => {
      const sel = it.key === active;
      return React.createElement("button", {
        key: it.key,
        onClick: () => onChange(it.key),
        style: {
          display: "flex",
          flexDirection: "column",
          alignItems: "center",
          gap: 4,
          border: 0,
          background: "transparent",
          cursor: "pointer",
          padding: 0,
          WebkitTapHighlightColor: "transparent"
        }
      }, React.createElement("span", {
        style: {
          width: 56,
          height: 32,
          borderRadius: 16,
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          background: sel ? "var(--md-secondary-container)" : "transparent",
          transition: "background .18s"
        }
      }, React.createElement(Icon, {
        name: it.icon,
        size: 24,
        fill: sel,
        color: sel ? "var(--md-on-secondary-container)" : "var(--md-on-surface-variant)"
      })), React.createElement("span", {
        style: {
          font: (sel ? 700 : 500) + " 12px/16px 'Roboto', var(--font-sans)",
          color: sel ? "var(--md-on-surface)" : "var(--md-on-surface-variant)"
        }
      }, it.label));
    }));
  }

  // ── Material You palette chip switcher ───────────────────────────────────
  function PaletteSwitcher({
    value,
    onChange
  }) {
    return React.createElement("div", {
      style: {
        display: "flex",
        flexDirection: "column",
        gap: 10
      }
    }, React.createElement("div", {
      style: {
        display: "flex",
        alignItems: "center",
        gap: 8,
        color: "var(--md-on-surface-variant)",
        font: "500 13px/1 'Roboto', var(--font-sans)"
      }
    }, React.createElement(Icon, {
      name: "palette",
      size: 18
    }), "系統佈景主題色 (Material You)"), React.createElement("div", {
      style: {
        display: "flex",
        gap: 12
      }
    }, Object.entries(PALETTES).map(([key, p]) => {
      const sel = key === value;
      return React.createElement("button", {
        key,
        onClick: () => onChange(key),
        title: p.label,
        style: {
          width: 44,
          height: 44,
          borderRadius: "50%",
          cursor: "pointer",
          background: p.seed,
          border: sel ? "3px solid var(--md-on-surface)" : "3px solid transparent",
          outline: sel ? "2px solid var(--md-surface)" : "none",
          outlineOffset: -5,
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          WebkitTapHighlightColor: "transparent"
        }
      }, sel && React.createElement("span", {
        className: "msr fill",
        style: {
          fontSize: 22,
          color: "#fff"
        }
      }, "check"));
    })));
  }
  window.LimeM3 = {
    Icon,
    Switch,
    Button,
    NavBar,
    NavRail,
    PaletteSwitcher,
    PALETTES,
    applyPalette
  };
  window.LimeM3.getMode = () => _mode;
})();
})(); } catch (e) { __ds_ns.__errors.push({ path: "docs/ui_kits/lime-settings-android/m3.jsx", error: String((e && e.message) || e) }); }

// docs/ui_kits/lime-settings/DBTab.jsx
try { (() => {
/* 資料庫 — DB Manager tab. Mirrors DBManagerView.swift §7.
   Re-laid-out to match the Android kit: each action is a Button inside a
   grouped section with a supporting footer (備份 / 還原 / 初始資料庫). */
(function () {
  const {
    Button
  } = window.LIMEDesignSystem_6ca3c0;
  const I = window.LimeIcons;
  function Action({
    button,
    footer,
    gap,
    warn
  }) {
    return React.createElement("div", {
      style: {
        marginBottom: gap,
        display: "flex",
        flexDirection: "column",
        gap: 8
      }
    }, button, footer && React.createElement("div", {
      style: {
        display: "flex",
        alignItems: "flex-start",
        gap: 6,
        padding: "0 4px",
        font: "400 13px/18px var(--font-sans)",
        color: warn ? "var(--danger-ink)" : "var(--text-secondary)"
      }
    }, warn && React.createElement("span", {
      style: {
        width: 15,
        height: 15,
        flex: "0 0 auto",
        display: "inline-flex",
        marginTop: 1
      }
    }, I.info ? I.info({
      size: 15
    }) : null), React.createElement("span", null, footer)));
  }
  function DBTab() {
    return React.createElement("div", {
      style: {
        padding: "0 24px 28px",
        display: "flex",
        flexDirection: "column"
      }
    }, React.createElement("div", {
      style: {
        font: "700 34px/41px var(--font-sans)",
        letterSpacing: "-.4px",
        padding: "8px 0 20px"
      }
    }, "資料庫管理"), React.createElement(Action, {
      gap: 12,
      footer: "備份包含所有字根、關聯字及喜好設定。",
      button: React.createElement(Button, {
        variant: "prominent",
        fullWidth: true,
        icon: I.upload({
          size: 18
        })
      }, "備份資料庫")
    }), React.createElement(Action, {
      gap: 12,
      footer: "還原後鍵盤將重新載入資料庫。",
      button: React.createElement(Button, {
        variant: "bordered",
        fullWidth: true,
        icon: I.download({
          size: 18
        })
      }, "還原資料庫")
    }), React.createElement(Action, {
      gap: 12,
      warn: true,
      footer: "警告：將清除目前所有輸入法資料表，還原為萊姆內建的空白預設資料庫，此動作無法復原。",
      button: React.createElement(Button, {
        variant: "bordered",
        destructive: true,
        fullWidth: true,
        icon: I.refresh({
          size: 18
        })
      }, "還原預設資料庫")
    }), React.createElement("div", {
      style: {
        display: "flex",
        alignItems: "center",
        gap: 8,
        padding: "4px 4px 0",
        color: "var(--text-secondary)"
      }
    }, React.createElement("span", {
      style: {
        width: 16,
        height: 16,
        display: "inline-flex"
      }
    }, I.info ? I.info({
      size: 16
    }) : null), React.createElement("span", {
      style: {
        font: "400 13px/18px var(--font-sans)"
      }
    }, "上次備份：lime_backup_1718.zip · 2.4 MB")));
  }
  window.DBTab = DBTab;
})();
})(); } catch (e) { __ds_ns.__errors.push({ path: "docs/ui_kits/lime-settings/DBTab.jsx", error: String((e && e.message) || e) }); }

// docs/ui_kits/lime-settings/IMTab.jsx
try { (() => {
/* 輸入法 — IM Manager tab. SetupImFragment IM grid + 關聯字庫 (§5.1).
   Stateful enable toggles, reorder-style list, floating + FAB. window.IMTab. */
(function () {
  const {
    ListGroup,
    ListRow,
    Switch
  } = window.LIMEDesignSystem_6ca3c0;
  const I = window.LimeIcons;

  // Grey rounded-square + Chinese-character avatar — matches the Android kit's IM list.
  function CharAvatar({
    glyph
  }) {
    return React.createElement("span", {
      style: {
        width: 30,
        height: 30,
        flex: "0 0 auto",
        borderRadius: 7,
        background: "var(--icon-tile)",
        color: "#fff",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        font: "500 15px/1 var(--font-sans)"
      }
    }, glyph);
  }
  function IconAvatar({
    icon
  }) {
    return React.createElement("span", {
      style: {
        width: 30,
        height: 30,
        flex: "0 0 auto",
        borderRadius: 7,
        background: "var(--icon-tile)",
        color: "#fff",
        display: "flex",
        alignItems: "center",
        justifyContent: "center"
      }
    }, React.createElement("span", {
      style: {
        width: 17,
        height: 17,
        display: "inline-flex"
      }
    }, icon));
  }
  const IMS = [{
    id: "phonetic",
    label: "注音",
    glyph: "ㄅ",
    on: true
  }, {
    id: "cj",
    label: "倉頡",
    glyph: "倉",
    on: true
  }, {
    id: "ecj",
    label: "速成",
    glyph: "速",
    on: true
  }, {
    id: "dayi",
    label: "大易",
    glyph: "易",
    on: false
  }, {
    id: "array",
    label: "行列",
    glyph: "行",
    on: false
  }, {
    id: "pinyin",
    label: "拼音",
    glyph: "拼",
    on: false
  }];
  function IMTab({
    onOpen
  }) {
    const [ims, setIms] = React.useState(IMS);
    const toggle = id => setIms(s => s.map(m => m.id === id ? {
      ...m,
      on: !m.on
    } : m));
    return React.createElement("div", {
      style: {
        position: "relative",
        minHeight: "100%"
      }
    }, React.createElement("div", {
      style: {
        padding: "0 24px 28px",
        display: "flex",
        flexDirection: "column",
        gap: 22
      }
    }, React.createElement("div", {
      style: {
        font: "700 34px/41px var(--font-sans)",
        letterSpacing: "-.4px",
        padding: "8px 0 0"
      }
    }, "管理輸入法"), React.createElement(ListGroup, {
      header: "已安裝的輸入法"
    }, ...ims.map(m => React.createElement(ListRow, {
      key: m.id,
      leading: React.createElement(CharAvatar, {
        glyph: m.glyph
      }),
      title: m.label,
      style: {
        opacity: m.on ? 1 : 0.55
      },
      onClick: () => onOpen && onOpen(m),
      chevron: true,
      trailing: React.createElement("span", {
        onClick: e => e.stopPropagation()
      }, React.createElement(Switch, {
        checked: m.on,
        onChange: () => toggle(m.id)
      }))
    }))), React.createElement(ListGroup, {
      header: "關聯字庫"
    }, React.createElement(ListRow, {
      leading: React.createElement(IconAvatar, {
        icon: I.bubble({
          size: 17
        })
      }),
      title: "關聯字庫",
      chevron: true,
      onClick: () => onOpen && onOpen({
        id: "related",
        label: "關聯字庫",
        related: true
      })
    }))),
    // Floating action button
    React.createElement("button", {
      type: "button",
      style: {
        position: "absolute",
        right: 20,
        bottom: 20,
        width: 52,
        height: 52,
        borderRadius: "50%",
        border: "none",
        background: "var(--accent-blue)",
        color: "#fff",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        boxShadow: "var(--shadow-fab)",
        cursor: "pointer"
      }
    }, I.plus({
      size: 26
    })));
  }
  window.IMTab = IMTab;
})();
})(); } catch (e) { __ds_ns.__errors.push({ path: "docs/ui_kits/lime-settings/IMTab.jsx", error: String((e && e.message) || e) }); }

// docs/ui_kits/lime-settings/PrefsTab.jsx
try { (() => {
/* 喜好設定 — IM Preferences. Mirrors PreferencesTabView.swift §8.
   Stateful toggles + 簡繁轉換 segmented control. window.PrefsTab. */
(function () {
  const {
    ListGroup,
    ListRow,
    Switch,
    SegmentedControl
  } = window.LIMEDesignSystem_6ca3c0;
  const I = window.LimeIcons;
  function PrefsTab() {
    const [s, setS] = React.useState({
      vibrate: true,
      sound: false,
      numberRow: true,
      smart: true,
      autoSymbol: false,
      persistLang: false,
      related: true,
      learn: true,
      dict: true,
      autoCap: true
    });
    const t = k => () => setS(p => ({
      ...p,
      [k]: !p[k]
    }));
    const sw = k => React.createElement(Switch, {
      checked: s[k],
      onChange: t(k)
    });
    const [han, setHan] = React.useState("繁轉簡");
    return React.createElement("div", {
      style: {
        padding: "0 24px 28px",
        display: "flex",
        flexDirection: "column",
        gap: 22
      }
    }, React.createElement("div", {
      style: {
        font: "700 34px/41px var(--font-sans)",
        letterSpacing: "-.4px",
        padding: "8px 0 0"
      }
    }, "喜好設定"), React.createElement(ListGroup, {
      header: "鍵盤外觀"
    }, React.createElement(ListRow, {
      icon: I.palette({
        size: 17
      }),
      iconColor: "var(--green-strong)",
      title: "鍵盤樣式",
      value: "放鬆綠",
      chevron: true
    }), React.createElement(ListRow, {
      title: "鍵盤大小",
      value: "一般",
      chevron: true
    }), React.createElement(ListRow, {
      title: "字型大小",
      value: "一般",
      chevron: true
    }), React.createElement(ListRow, {
      title: "數字列英文鍵盤",
      subtitle: "在英文鍵盤顯示數字列 (5 列鍵盤)",
      trailing: sw("numberRow")
    }), React.createElement(ListRow, {
      title: "顯示方向鍵",
      value: "無",
      chevron: true
    })), React.createElement(ListGroup, {
      header: "鍵盤回饋"
    }, React.createElement(ListRow, {
      icon: I.bell({
        size: 17
      }),
      iconColor: "#e0883a",
      title: "打字震動",
      trailing: sw("vibrate")
    }), React.createElement(ListRow, {
      title: "震動強度",
      value: "中",
      chevron: true,
      style: {
        opacity: s.vibrate ? 1 : 0.5
      }
    }), React.createElement(ListRow, {
      title: "打字音效",
      trailing: sw("sound")
    })), React.createElement(ListGroup, {
      header: "輸入法行為"
    }, React.createElement(ListRow, {
      icon: I.sparkles({
        size: 17
      }),
      iconColor: "var(--lime-green)",
      title: "開啟中文智慧組詞",
      subtitle: "部份輸入法可能會影響中英混打功能",
      trailing: sw("smart")
    }), React.createElement(ListRow, {
      title: "自動中文標點模式",
      subtitle: "無候選字詞時顯示中文標點選項",
      trailing: sw("autoSymbol")
    }), React.createElement(ListRow, {
      title: "記憶中英模式",
      subtitle: "下次切換前保持中英模式",
      trailing: sw("persistLang")
    }), React.createElement(ListRow, {
      icon: I.search({
        size: 17
      }),
      iconColor: "#777",
      title: "字根反查設定",
      chevron: true
    })), React.createElement(ListGroup, {
      header: "簡繁轉換",
      footer: "套用於所有輸入法的候選字輸出。"
    }, React.createElement("div", {
      style: {
        padding: "12px 16px",
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        gap: 12
      }
    }, React.createElement("span", {
      style: {
        font: "400 17px/22px var(--font-sans)"
      }
    }, "字碼轉換"), React.createElement(SegmentedControl, {
      options: ["無", "繁轉簡", "簡轉繁"],
      value: han,
      onChange: setHan,
      style: {
        width: 222
      }
    }))), React.createElement(ListGroup, {
      header: "關聯字與學習"
    }, React.createElement(ListRow, {
      icon: I.bubble({
        size: 17
      }),
      iconColor: "#5b9",
      title: "啟用關聯字庫",
      subtitle: "啟用關聯字庫功能",
      trailing: sw("related")
    }), React.createElement(ListRow, {
      title: "自動學習新詞",
      subtitle: "從常用關聯字學習新詞",
      trailing: sw("learn")
    })), React.createElement(ListGroup, {
      header: "英文鍵盤"
    }, React.createElement(ListRow, {
      icon: I.english({
        size: 17
      }),
      iconColor: "#0a84c4",
      title: "啟用英文字典",
      subtitle: "英文模式下顯示英文建議字",
      trailing: sw("dict")
    }), React.createElement(ListRow, {
      title: "首字自動大寫",
      subtitle: "句首字母自動轉為大寫",
      trailing: sw("autoCap")
    })));
  }
  window.PrefsTab = PrefsTab;
})();
})(); } catch (e) { __ds_ns.__errors.push({ path: "docs/ui_kits/lime-settings/PrefsTab.jsx", error: String((e && e.message) || e) }); }

// docs/ui_kits/lime-settings/SetupTab.jsx
try { (() => {
/* 設定 — App Setup tab. Faithful to SetupTabView.swift §4, re-laid-out with the
   LIME brand hero. Exposes window.SetupTab. */
(function () {
  const {
    ListGroup,
    ListRow,
    Switch,
    Button,
    StatusBanner
  } = window.LIMEDesignSystem_6ca3c0;
  const I = window.LimeIcons;

  // Canonical destinations (LIME_SETTINGS.md §4.1). The 版權說明 page now lives on
  // the project site; 原始碼 points at the GitHub repo.
  const LICENSE_URL = "https://lime-ime.github.io/limeime/pages/license.html";
  const MANUAL_URL = "https://lime-ime.github.io/limeime/pages/index.html";
  const GITHUB_URL = "https://github.com/lime-ime/limeime";

  // An iOS-style inline link: brand-blue label + a small up-right arrow glyph so
  // users can tell it leaves the app. Opens in a new tab.
  function ExternalLink({
    href,
    children
  }) {
    return React.createElement("a", {
      href,
      target: "_blank",
      rel: "noopener noreferrer",
      style: {
        display: "inline-flex",
        alignItems: "center",
        gap: 4,
        color: "var(--accent-blue)",
        textDecoration: "none",
        font: "400 17px/22px var(--font-sans)",
        WebkitTapHighlightColor: "transparent"
      }
    }, children, React.createElement("svg", {
      width: 11,
      height: 11,
      viewBox: "0 0 12 12",
      fill: "none",
      style: {
        opacity: .7,
        flex: "0 0 auto"
      }
    }, React.createElement("path", {
      d: "M3.5 2.5h6v6M9.5 2.5L2.5 9.5",
      stroke: "currentColor",
      strokeWidth: 1.5,
      strokeLinecap: "round",
      strokeLinejoin: "round"
    })));
  }

  // A compact iOS-style link chip for the optimized About footer: equal-width,
  // rounded, subtle fill, icon + brand-blue label + external arrow.
  function LinkChip({
    href,
    icon,
    children
  }) {
    return React.createElement("a", {
      href,
      target: "_blank",
      rel: "noopener noreferrer",
      style: {
        flex: 1,
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        gap: 7,
        padding: "15px 8px 13px",
        borderRadius: 14,
        background: "var(--fill-quaternary)",
        color: "var(--accent)",
        textDecoration: "none",
        WebkitTapHighlightColor: "transparent"
      }
    }, React.createElement("span", {
      style: {
        color: "var(--accent)",
        display: "flex"
      }
    }, icon), React.createElement("span", {
      style: {
        display: "inline-flex",
        alignItems: "center",
        gap: 3,
        font: "500 14px/18px var(--font-sans)"
      }
    }, children, React.createElement("svg", {
      width: 10,
      height: 10,
      viewBox: "0 0 12 12",
      fill: "none",
      style: {
        opacity: .6
      }
    }, React.createElement("path", {
      d: "M3.5 2.5h6v6M9.5 2.5L2.5 9.5",
      stroke: "currentColor",
      strokeWidth: 1.5,
      strokeLinecap: "round",
      strokeLinejoin: "round"
    }))));
  }
  const GreenToggle = () => React.createElement("div", {
    style: {
      width: 30,
      height: 18,
      borderRadius: 9,
      background: "var(--switch-on)",
      position: "relative",
      flex: "0 0 auto"
    }
  }, React.createElement("div", {
    style: {
      position: "absolute",
      right: 2,
      top: 2,
      width: 14,
      height: 14,
      borderRadius: "50%",
      background: "#fff",
      boxShadow: "0 1px 1px rgba(0,0,0,.18)"
    }
  }));
  function StepRow({
    icon,
    text
  }) {
    return React.createElement("div", {
      style: {
        display: "flex",
        alignItems: "center",
        gap: 16
      }
    }, React.createElement("div", {
      style: {
        width: 32,
        display: "flex",
        justifyContent: "center",
        color: "var(--accent)"
      }
    }, icon), React.createElement("div", {
      style: {
        font: "400 17px/22px var(--font-sans)",
        color: "var(--text-primary)"
      }
    }, text));
  }
  function SetupTab() {
    return React.createElement("div", {
      style: {
        padding: "8px 24px 28px",
        display: "flex",
        flexDirection: "column",
        gap: 24
      }
    },
    // Brand hero — Android style: plain logo (no white rounded-rect tile), horizontal row
    React.createElement("div", {
      style: {
        display: "flex",
        flexDirection: "row",
        alignItems: "center",
        justifyContent: "center",
        gap: 16,
        paddingTop: 20
      }
    }, React.createElement("img", {
      src: "../../assets/lime-logo-android.png",
      alt: "LIME",
      style: {
        width: 92,
        height: 92,
        objectFit: "contain"
      }
    }), React.createElement("div", {
      style: {
        font: "700 30px/36px var(--font-sans)",
        letterSpacing: "-.4px",
        color: "var(--text-primary)"
      }
    }, "萊姆輸入法")), React.createElement(StatusBanner, {
      status: "success"
    }, "萊姆輸入法已啟用"), React.createElement("div", {
      style: {
        font: "700 28px/34px var(--font-sans)",
        letterSpacing: "-.4px"
      }
    }, "設定萊姆輸入法"), React.createElement("div", {
      style: {
        display: "flex",
        flexDirection: "column",
        gap: 16
      }
    }, React.createElement(StepRow, {
      icon: I.keyboard({
        size: 22
      }),
      text: "輕觸「鍵盤」"
    }), React.createElement(StepRow, {
      icon: React.createElement(GreenToggle),
      text: "開啟萊姆輸入法"
    }), React.createElement(StepRow, {
      icon: React.createElement(GreenToggle),
      text: "開啟「允許完整取用」"
    })), React.createElement("div", {
      style: {
        font: "400 15px/20px var(--font-sans)",
        color: "var(--text-secondary)",
        textAlign: "center"
      }
    }, "完整取用用於備份資料庫、按鍵震動回饋與編輯字根資料表。不開啟也能正常輸入、安裝或匯入輸入法，萊姆輸入法不會收集或傳送任何個人資料。"), React.createElement(Button, {
      variant: "prominent",
      size: "large",
      fullWidth: true
    }, "前往設定"), React.createElement("div", {
      style: {
        font: "400 13px/18px var(--font-sans)",
        color: "var(--text-secondary)",
        textAlign: "center"
      }
    }, "若設定未直接顯示萊姆輸入法，請到「設定」>「Apps」>「萊姆輸入法」>「Keyboards」開啟。"),
    // About — optimized footer: app identity + version, then three equal-width
    // link chips (使用手冊 / 版權說明 / 原始碼) laid out consistently. Replaces the
    // old grouped list whose lone left-aligned GitHub row looked inconsistent.
    React.createElement("div", {
      style: {
        display: "flex",
        flexDirection: "column",
        gap: 16,
        paddingTop: 10
      }
    }, React.createElement("div", {
      style: {
        height: 1,
        background: "var(--separator)",
        margin: "0 -24px"
      }
    }), React.createElement("div", {
      style: {
        display: "flex",
        gap: 10
      }
    }, React.createElement(LinkChip, {
      href: MANUAL_URL,
      icon: I.book({
        size: 21
      })
    }, "使用手冊"), React.createElement(LinkChip, {
      href: LICENSE_URL,
      icon: I.doc({
        size: 21
      })
    }, "版權說明"), React.createElement(LinkChip, {
      href: GITHUB_URL,
      icon: I.code({
        size: 21
      })
    }, "原始碼")),
    // One-line copyright banner at the very bottom.
    React.createElement("div", {
      style: {
        font: "400 13px/18px var(--font-sans)",
        color: "var(--text-secondary)",
        textAlign: "center",
        paddingTop: 6
      }
    }, "© LIME 萊姆輸入法 6.1.15 - 2026")));
  }
  window.SetupTab = SetupTab;
})();
})(); } catch (e) { __ds_ns.__errors.push({ path: "docs/ui_kits/lime-settings/SetupTab.jsx", error: String((e && e.message) || e) }); }

// docs/ui_kits/lime-settings/icons.jsx
try { (() => {
/* Shared icon set for the LIME Settings UI kit.
   SF Symbols are proprietary, so these are Lucide-style stroke equivalents
   chosen to match each SF Symbol the app references. Exposed on window.LimeIcons. */
(function () {
  const S = (paths, opts = {}) => React.createElement("svg", {
    viewBox: "0 0 24 24",
    width: opts.size || 24,
    height: opts.size || 24,
    fill: opts.fill || "none",
    stroke: opts.fill ? "none" : "currentColor",
    strokeWidth: opts.sw || 1.9,
    strokeLinecap: "round",
    strokeLinejoin: "round"
  }, paths);
  const P = d => React.createElement("path", {
    d,
    key: Math.random()
  });
  const Icons = {
    gear: o => S([P("M12 15a3 3 0 100-6 3 3 0 000 6z"), P("M19.4 15a1.6 1.6 0 00.3 1.8l.1.1a2 2 0 11-2.8 2.8l-.1-.1a1.6 1.6 0 00-1.8-.3 1.6 1.6 0 00-1 1.5V21a2 2 0 01-4 0v-.1A1.6 1.6 0 009 19.4a1.6 1.6 0 00-1.8.3l-.1.1a2 2 0 11-2.8-2.8l.1-.1a1.6 1.6 0 00.3-1.8 1.6 1.6 0 00-1.5-1H3a2 2 0 010-4h.1A1.6 1.6 0 004.6 9a1.6 1.6 0 00-.3-1.8l-.1-.1a2 2 0 112.8-2.8l.1.1a1.6 1.6 0 001.8.3H9a1.6 1.6 0 001-1.5V3a2 2 0 014 0v.1a1.6 1.6 0 001 1.5 1.6 1.6 0 001.8-.3l.1-.1a2 2 0 112.8 2.8l-.1.1a1.6 1.6 0 00-.3 1.8V9a1.6 1.6 0 001.5 1H21a2 2 0 010 4h-.1a1.6 1.6 0 00-1.5 1z")], o),
    list: o => S([P("M8 6h13"), P("M8 12h13"), P("M8 18h13"), P("M3 6h.01"), P("M3 12h.01"), P("M3 18h.01")], o),
    sliders: o => S([P("M4 21v-7"), P("M4 10V3"), P("M12 21v-9"), P("M12 8V3"), P("M20 21v-5"), P("M20 12V3"), P("M1 14h6"), P("M9 8h6"), P("M17 16h6")], o),
    archive: o => S([P("M21 8v13H3V8"), P("M1 3h22v5H1z"), P("M10 12h4")], o),
    keyboard: o => S([P("M2 6h20v12H2z"), P("M6 10h.01"), P("M10 10h.01"), P("M14 10h.01"), P("M18 10h.01"), P("M6 14h12")], o),
    palette: o => S([P("M12 2a10 10 0 100 20c.6 0 1-.4 1-1 0-.3-.1-.5-.3-.7-.2-.2-.3-.4-.3-.8 0-.5.5-1 1-1H15a5 5 0 005-5c0-5-4.5-9.5-8-9.5z"), P("M6.5 12.5h.01"), P("M9.5 8.5h.01"), P("M14.5 8.5h.01")], o),
    bell: o => S([P("M18 8a6 6 0 00-12 0c0 7-3 9-3 9h18s-3-2-3-9"), P("M13.7 21a2 2 0 01-3.4 0")], o),
    type: o => S([P("M4 7V5h16v2"), P("M9 19h6"), P("M12 5v14")], o),
    convert: o => S([P("M4 7h13l-3-3"), P("M20 17H7l3 3")], o),
    sparkles: o => S([P("M12 3l1.9 4.6L18.5 9l-4.6 1.9L12 15l-1.9-4.1L5.5 9l4.6-1.4z"), P("M19 14l.8 2 2 .8-2 .8-.8 2-.8-2-2-.8 2-.8z")], o),
    english: o => S([P("M5 19V5h7a4 4 0 010 8H5"), P("M12 13a4 4 0 010 8H5")], o),
    bubble: o => S([P("M21 11.5a8.4 8.4 0 01-9 8.4 9 9 0 01-4-1L3 21l1.3-4.5A8.4 8.4 0 1121 11.5z")], o),
    search: o => S([P("M11 19a8 8 0 100-16 8 8 0 000 16z"), P("M21 21l-4.3-4.3")], o),
    upload: o => S([P("M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4"), P("M17 8l-5-5-5 5"), P("M12 3v12")], o),
    download: o => S([P("M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4"), P("M7 10l5 5 5-5"), P("M12 15V3")], o),
    refresh: o => S([P("M3 12a9 9 0 0115-6.7L21 8"), P("M21 3v5h-5"), P("M21 12a9 9 0 01-15 6.7L3 16"), P("M3 21v-5h5")], o),
    plus: o => S([P("M12 5v14"), P("M5 12h14")], o),
    chevronLeft: o => S([P("M15 18l-6-6 6-6")], o),
    info: o => S([P("M12 22a10 10 0 100-20 10 10 0 000 20z"), P("M12 16v-4"), P("M12 8h.01")], o),
    array: o => S([P("M3 3h7v7H3z"), P("M14 3h7v7h-7z"), P("M14 14h7v7h-7z"), P("M3 14h7v7H3z")], o),
    pen: o => S([P("M12 20h9"), P("M16.5 3.5a2.1 2.1 0 013 3L7 19l-4 1 1-4z")], o),
    grid: o => S([P("M3 3h8v8H3z"), P("M13 3h8v8h-8z"), P("M13 13h8v8h-8z"), P("M3 13h8v8H3z")], o),
    pinyin: o => S([P("M4 7V5h16v2"), P("M12 5v14"), P("M8 19h8")], o),
    stroke: o => S([P("M5 12h14"), P("M12 5l7 7-7 7"), P("M5 5v14")], o),
    book: o => S([P("M4 19.5A2.5 2.5 0 016.5 17H20"), P("M6.5 2H20v20H6.5A2.5 2.5 0 014 19.5v-15A2.5 2.5 0 016.5 2z")], o),
    doc: o => S([P("M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z"), P("M14 2v6h6"), P("M9 13h6"), P("M9 17h6")], o),
    code: o => S([P("M16 18l6-6-6-6"), P("M8 6l-6 6 6 6")], o)
  };
  window.LimeIcons = Icons;
})();
})(); } catch (e) { __ds_ns.__errors.push({ path: "docs/ui_kits/lime-settings/icons.jsx", error: String((e && e.message) || e) }); }

__ds_ns.Button = __ds_scope.Button;

__ds_ns.SegmentedControl = __ds_scope.SegmentedControl;

__ds_ns.Stepper = __ds_scope.Stepper;

__ds_ns.Switch = __ds_scope.Switch;

__ds_ns.StatusBanner = __ds_scope.StatusBanner;

__ds_ns.ListGroup = __ds_scope.ListGroup;

__ds_ns.ListRow = __ds_scope.ListRow;

__ds_ns.TabBar = __ds_scope.TabBar;

})();
