import React from "react";

/**
 * LIME Stepper — the −/＋ numeric score control from the record/related row
 * editors (AddRecordView / EditRecordView). Direct typing + clamp to [min,max].
 */
export function Stepper({ value = 0, min = 0, max = 9999, onChange, style = {} }) {
  const set = (v) => onChange && onChange(Math.max(min, Math.min(max, v)));
  const circ = (content, fn, key) => (
    <button
      key={key}
      type="button"
      onClick={fn}
      style={{
        width: 28, height: 28, flex: "0 0 auto",
        display: "inline-flex", alignItems: "center", justifyContent: "center",
        border: "none", borderRadius: "50%",
        background: "transparent", color: "var(--accent)",
        fontSize: 24, lineHeight: 0, cursor: "pointer",
        WebkitTapHighlightColor: "transparent",
      }}
    >
      {content}
    </button>
  );
  return (
    <div style={{ display: "inline-flex", alignItems: "center", gap: 6, fontFamily: "var(--font-sans)", ...style }}>
      {circ("\u2212", () => set(value - 1), "minus")}
      <input
        type="text"
        inputMode="numeric"
        value={value}
        onChange={(e) => {
          const n = parseInt(e.target.value.replace(/\D/g, ""), 10);
          set(Number.isNaN(n) ? 0 : n);
        }}
        style={{
          width: 64, height: 34, textAlign: "center",
          fontSize: 17, fontWeight: 500, color: "var(--text-primary)",
          border: "1px solid var(--separator)", borderRadius: 8,
          background: "var(--bg)", outline: "none",
        }}
      />
      {circ("+", () => set(value + 1), "plus")}
    </div>
  );
}
