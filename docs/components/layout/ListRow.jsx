import React from "react";

/**
 * LIME ListRow — a versatile grouped-list row. Leading icon tile, title +
 * optional subtitle, and a trailing slot (value text, chevron, Switch, etc).
 */
export function ListRow({
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
  return (
    <div
      onClick={onClick}
      style={{
        display: "flex",
        alignItems: "center",
        gap: 12,
        minHeight: 44,
        padding: "11px 16px",
        cursor: onClick ? "pointer" : "default",
        fontFamily: "var(--font-sans)",
        WebkitTapHighlightColor: "transparent",
        ...style,
      }}
      {...rest}
    >
      {leading}
      {!leading && icon && (
        <span
          style={{
            width: 29, height: 29, flex: "0 0 auto",
            display: "inline-flex", alignItems: "center", justifyContent: "center",
            borderRadius: 6.5, background: "var(--icon-tile)", color: "#fff",
          }}
        >
          <span style={{ width: 18, height: 18, display: "inline-flex" }}>{icon}</span>
        </span>
      )}
      <div style={{ flex: 1, minWidth: 0 }}>
        <div
          style={{
            font: "var(--weight-regular) 17px/22px var(--font-sans)",
            color: destructive ? "var(--danger)" : "var(--text-primary)",
          }}
        >
          {title}
        </div>
        {subtitle && (
          <div style={{ font: "var(--weight-regular) 13px/17px var(--font-sans)", color: "var(--text-secondary)", marginTop: 1 }}>
            {subtitle}
          </div>
        )}
      </div>
      {value != null && (
        <span style={{ font: "var(--weight-regular) 17px/22px var(--font-sans)", color: "var(--text-secondary)", whiteSpace: "nowrap" }}>
          {value}
        </span>
      )}
      {trailing}
      {chevron && (
        <svg width="8" height="14" viewBox="0 0 8 14" fill="none" style={{ flex: "0 0 auto" }}>
          <path d="M1 1l6 6-6 6" stroke="var(--text-tertiary)" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      )}
    </div>
  );
}
