/* Shared icon set for the LIME Settings UI kit.
   SF Symbols are proprietary, so these are Lucide-style stroke equivalents
   chosen to match each SF Symbol the app references. Exposed on window.LimeIcons. */
(function () {
  const S = (paths, opts = {}) =>
    React.createElement(
      "svg",
      {
        viewBox: "0 0 24 24",
        width: opts.size || 24,
        height: opts.size || 24,
        fill: opts.fill || "none",
        stroke: opts.fill ? "none" : "currentColor",
        strokeWidth: opts.sw || 1.9,
        strokeLinecap: "round",
        strokeLinejoin: "round",
      },
      paths
    );
  const P = (d) => React.createElement("path", { d, key: Math.random() });

  const Icons = {
    gear: (o) => S([P("M12 15a3 3 0 100-6 3 3 0 000 6z"), P("M19.4 15a1.6 1.6 0 00.3 1.8l.1.1a2 2 0 11-2.8 2.8l-.1-.1a1.6 1.6 0 00-1.8-.3 1.6 1.6 0 00-1 1.5V21a2 2 0 01-4 0v-.1A1.6 1.6 0 009 19.4a1.6 1.6 0 00-1.8.3l-.1.1a2 2 0 11-2.8-2.8l.1-.1a1.6 1.6 0 00.3-1.8 1.6 1.6 0 00-1.5-1H3a2 2 0 010-4h.1A1.6 1.6 0 004.6 9a1.6 1.6 0 00-.3-1.8l-.1-.1a2 2 0 112.8-2.8l.1.1a1.6 1.6 0 001.8.3H9a1.6 1.6 0 001-1.5V3a2 2 0 014 0v.1a1.6 1.6 0 001 1.5 1.6 1.6 0 001.8-.3l.1-.1a2 2 0 112.8 2.8l-.1.1a1.6 1.6 0 00-.3 1.8V9a1.6 1.6 0 001.5 1H21a2 2 0 010 4h-.1a1.6 1.6 0 00-1.5 1z")], o),
    list: (o) => S([P("M8 6h13"), P("M8 12h13"), P("M8 18h13"), P("M3 6h.01"), P("M3 12h.01"), P("M3 18h.01")], o),
    sliders: (o) => S([P("M4 21v-7"), P("M4 10V3"), P("M12 21v-9"), P("M12 8V3"), P("M20 21v-5"), P("M20 12V3"), P("M1 14h6"), P("M9 8h6"), P("M17 16h6")], o),
    archive: (o) => S([P("M21 8v13H3V8"), P("M1 3h22v5H1z"), P("M10 12h4")], o),
    keyboard: (o) => S([P("M2 6h20v12H2z"), P("M6 10h.01"), P("M10 10h.01"), P("M14 10h.01"), P("M18 10h.01"), P("M6 14h12")], o),
    palette: (o) => S([P("M12 2a10 10 0 100 20c.6 0 1-.4 1-1 0-.3-.1-.5-.3-.7-.2-.2-.3-.4-.3-.8 0-.5.5-1 1-1H15a5 5 0 005-5c0-5-4.5-9.5-8-9.5z"), P("M6.5 12.5h.01"), P("M9.5 8.5h.01"), P("M14.5 8.5h.01")], o),
    bell: (o) => S([P("M18 8a6 6 0 00-12 0c0 7-3 9-3 9h18s-3-2-3-9"), P("M13.7 21a2 2 0 01-3.4 0")], o),
    vibrate: (o) => S([P("M2 8l2 2-2 2 2 2-2 2"), P("M22 8l-2 2 2 2-2 2 2 2"), P("M9 5h6a1 1 0 011 1v12a1 1 0 01-1 1H9a1 1 0 01-1-1V6a1 1 0 011-1z")], o),
    type: (o) => S([P("M4 7V5h16v2"), P("M9 19h6"), P("M12 5v14")], o),
    convert: (o) => S([P("M4 7h13l-3-3"), P("M20 17H7l3 3")], o),
    sparkles: (o) => S([P("M12 3l1.9 4.6L18.5 9l-4.6 1.9L12 15l-1.9-4.1L5.5 9l4.6-1.4z"), P("M19 14l.8 2 2 .8-2 .8-.8 2-.8-2-2-.8 2-.8z")], o),
    english: (o) => S([P("M5 19V5h7a4 4 0 010 8H5"), P("M12 13a4 4 0 010 8H5")], o),
    bubble: (o) => S([P("M21 11.5a8.4 8.4 0 01-9 8.4 9 9 0 01-4-1L3 21l1.3-4.5A8.4 8.4 0 1121 11.5z")], o),
    search: (o) => S([P("M11 19a8 8 0 100-16 8 8 0 000 16z"), P("M21 21l-4.3-4.3")], o),
    upload: (o) => S([P("M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4"), P("M17 8l-5-5-5 5"), P("M12 3v12")], o),
    download: (o) => S([P("M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4"), P("M7 10l5 5 5-5"), P("M12 15V3")], o),
    refresh: (o) => S([P("M3 12a9 9 0 0115-6.7L21 8"), P("M21 3v5h-5"), P("M21 12a9 9 0 01-15 6.7L3 16"), P("M3 21v-5h5")], o),
    plus: (o) => S([P("M12 5v14"), P("M5 12h14")], o),
    chevronLeft: (o) => S([P("M15 18l-6-6 6-6")], o),
    info: (o) => S([P("M12 22a10 10 0 100-20 10 10 0 000 20z"), P("M12 16v-4"), P("M12 8h.01")], o),
    array: (o) => S([P("M3 3h7v7H3z"), P("M14 3h7v7h-7z"), P("M14 14h7v7h-7z"), P("M3 14h7v7H3z")], o),
    pen: (o) => S([P("M12 20h9"), P("M16.5 3.5a2.1 2.1 0 013 3L7 19l-4 1 1-4z")], o),
    grid: (o) => S([P("M3 3h8v8H3z"), P("M13 3h8v8h-8z"), P("M13 13h8v8h-8z"), P("M3 13h8v8H3z")], o),
    pinyin: (o) => S([P("M4 7V5h16v2"), P("M12 5v14"), P("M8 19h8")], o),
    stroke: (o) => S([P("M5 12h14"), P("M12 5l7 7-7 7"), P("M5 5v14")], o),
    book: (o) => S([P("M4 19.5A2.5 2.5 0 016.5 17H20"), P("M6.5 2H20v20H6.5A2.5 2.5 0 014 19.5v-15A2.5 2.5 0 016.5 2z")], o),
    doc: (o) => S([P("M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z"), P("M14 2v6h6"), P("M9 13h6"), P("M9 17h6")], o),
    code: (o) => S([P("M16 18l6-6-6-6"), P("M8 6l-6 6 6 6")], o),
  };
  window.LimeIcons = Icons;
})();
