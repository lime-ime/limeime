#!/usr/bin/env python3
# build_tricode_db.py
#
# Build Database/tricode.limedb -- the downloadable-IM cloud asset for 三碼輸入法
# (3code / "tricode"), issue #159. Parses the reviewed upstream .cin table
# committed under Database/ (downloaded from https://3code-type.github.io/3code.cin)
# into the modern .limedb container format documented in docs/LIMEDB_SPEC.md: a
# `custom` mapping table plus an `im` metadata-property-rows table, zipped as a
# single inner `tricode.db`. Model file: Database/hahacj.limedb (inner cj4.db).
# See docs/TRI_CODE_IM.md "Phase 1 -- Build Database/tricode.limedb" for the
# full spec this script implements.
#
# cin parsing handles the quirks documented in docs/TRI_CODE_IM.md "Source
# facts": '#'-comment lines interleaved inside the %chardef block, trailing
# spaces after words, tab-delimited fields, and duplicate (code, word) pairs
# (deduped, first-seen order preserved). Multi-character candidate words (e.g.
# "sr 啊！") are kept as-is.
#
# basescore is looked up per single-character candidate word in
# LimeStudio/app/src/main/res/raw/hanconvertv2.db table TCSC(code, word, score),
# matched on TCSC.code = word -- the same frequency source the runtime
# getBaseScore() importer uses. basescore is 0 when the word is absent from
# TCSC or is multi-character.
#
# Usage:
#   python3 scripts/build_tricode_db.py --date "2026-07-20 00:00:00 +0800"
#
# Optional overrides (defaults match the repo layout):
#   --cin PATH             input .cin (default Database/tricode-20260727.1.cin)
#   --out PATH              output .limedb (default Database/tricode.limedb)
#   --hanconvert-db PATH    basescore source (default
#                           LimeStudio/app/src/main/res/raw/hanconvertv2.db)
#   --version STR           override the version string parsed from the cin's
#                           header comment ("#版本：v.<version>")

import argparse
import datetime
import os
import re
import sqlite3
import sys
import tempfile
import zipfile


IM_CODE = "tricode"
IM_NAME = "三碼"
IM_SOURCE_URL = "https://3code-type.github.io/3code.cin"
KEYBOARD_DESC = "LIME+數字符號鍵盤2"
KEYBOARD_CODE = "limenumsym2"


def log(msg):
    print(f"[build_tricode_db] {msg}", file=sys.stderr)


def parse_cin(path):
    """Parse %selkey, %keyname begin/end, and %chardef begin/end from the cin.

    Returns a dict: selkey (str), version (str or None, from the header
    comment), keynames (list of (key, label) in file order), rows (list of
    (code, word) deduped on exact tuple match, first-seen order preserved).
    """
    with open(path, encoding="utf-8") as f:
        lines = f.readlines()

    selkey = None
    version = None
    keynames = []
    rows = []
    seen = set()

    section = None
    for raw in lines:
        line = raw.rstrip("\n")
        stripped = line.strip()

        if section is None:
            m = re.match(r"^#版本[：:]\s*v\.(\S+)", stripped)
            if m:
                version = m.group(1)
            if stripped.startswith("%selkey"):
                parts = stripped.split()
                if len(parts) >= 2:
                    selkey = parts[1]
            if stripped == "%keyname begin":
                section = "keyname"
            elif stripped == "%chardef begin":
                section = "chardef"
            continue

        if section == "keyname":
            if stripped == "%keyname end":
                section = None
                continue
            if not stripped:
                continue
            parts = line.split("\t")
            if len(parts) < 2:
                continue
            keynames.append((parts[0].strip(), parts[1].strip()))
            continue

        if section == "chardef":
            if stripped == "%chardef end":
                section = None
                continue
            if not stripped:
                continue
            if stripped.startswith("#"):
                continue
            parts = line.split("\t")
            if len(parts) < 2:
                log(f"WARNING: skipping malformed chardef line: {line!r}")
                continue
            code = parts[0].strip().lower()
            word = parts[1].strip()
            key = (code, word)
            if key in seen:
                continue
            seen.add(key)
            rows.append(key)
            continue

    if selkey is None:
        log("ERROR: %selkey not found in cin")
        sys.exit(1)
    if not keynames:
        log("ERROR: %keyname block not found/empty in cin")
        sys.exit(1)
    if not rows:
        log("ERROR: %chardef block not found/empty in cin")
        sys.exit(1)

    return {"selkey": selkey, "version": version, "keynames": keynames, "rows": rows}


def lookup_basescores(rows, hanconvert_path):
    """Return {word: basescore} for every distinct single-character word.

    Matches TCSC.code = word (same source runtime getBaseScore() uses).
    Multi-character words are not looked up (caller treats them as 0).
    """
    words = sorted({word for _, word in rows if len(word) == 1})
    scores = {}
    if not words:
        return scores
    if not os.path.exists(hanconvert_path):
        log(f"ERROR: hanconvertv2.db not found: {hanconvert_path}")
        sys.exit(1)
    con = sqlite3.connect(f"file:{hanconvert_path}?mode=ro", uri=True)
    try:
        cur = con.cursor()
        for word in words:
            cur.execute(
                "SELECT CAST(score AS INTEGER) FROM TCSC WHERE code = ? LIMIT 1", (word,)
            )
            row = cur.fetchone()
            scores[word] = row[0] if row else 0
    finally:
        con.close()
    return scores


def build_inner_db(db_path, cin_data, basescores, date_str, version):
    """Create the inner SQLite db (custom + im tables) and return the row count."""
    con = sqlite3.connect(db_path)
    try:
        con.execute(
            """
            CREATE TABLE custom (
                _id       INTEGER PRIMARY KEY AUTOINCREMENT,
                code      TEXT,
                code3r    TEXT,
                word      TEXT,
                related   TEXT,
                score     INTEGER DEFAULT 0,
                basescore INTEGER DEFAULT 0
            )
            """
        )
        con.execute("CREATE INDEX custom_idx_code ON custom(code)")

        rows = cin_data["rows"]
        con.executemany(
            "INSERT INTO custom(code, code3r, word, related, score, basescore) "
            "VALUES (?, '', ?, NULL, 0, ?)",
            [(code, word, basescores.get(word, 0)) for code, word in rows],
        )

        con.execute(
            """
            CREATE TABLE im (
                _id        INTEGER PRIMARY KEY AUTOINCREMENT,
                code       TEXT,
                title      TEXT,
                desc       TEXT,
                keyboard   TEXT,
                disable    BOOLEAN,
                selkey     TEXT,
                endkey     TEXT,
                spacestyle TEXT
            )
            """
        )

        imkeys = "".join(k for k, _ in cin_data["keynames"])
        imkeynames = "|".join(label for _, label in cin_data["keynames"])

        # (title, desc, keyboard) -- matches the hahacj.limedb property-row model:
        # the value lives in `desc` for every row except "keyboard", where `desc`
        # holds the display name and `keyboard` holds the keyboard code.
        prop_rows = [
            ("source", IM_SOURCE_URL, ""),
            ("version", version, ""),
            ("name", IM_NAME, ""),
            ("amount", str(len(rows)), ""),
            ("import", date_str, ""),
            ("selkey", cin_data["selkey"], ""),
            ("imkeys", imkeys, ""),
            ("imkeynames", imkeynames, ""),
            ("keyboard", KEYBOARD_DESC, KEYBOARD_CODE),
        ]
        con.executemany(
            "INSERT INTO im(code, title, desc, keyboard) VALUES (?, ?, ?, ?)",
            [(IM_CODE, title, desc, keyboard) for title, desc, keyboard in prop_rows],
        )

        con.commit()
    finally:
        con.close()
    return len(rows)


def zip_limedb(db_path, out_path, inner_name, date_str):
    out_dir = os.path.dirname(out_path)
    if out_dir:
        os.makedirs(out_dir, exist_ok=True)
    if os.path.exists(out_path):
        os.remove(out_path)

    archive_time = datetime.datetime.strptime(date_str, "%Y-%m-%d %H:%M:%S %z")
    info = zipfile.ZipInfo(inner_name, date_time=archive_time.timetuple()[:6])
    info.compress_type = zipfile.ZIP_DEFLATED
    info.create_system = 3
    info.external_attr = 0o100644 << 16
    with open(db_path, "rb") as source:
        database_bytes = source.read()
    with zipfile.ZipFile(out_path, "w", compression=zipfile.ZIP_DEFLATED) as zf:
        zf.writestr(info, database_bytes)


def main():
    ap = argparse.ArgumentParser(description="Build Database/tricode.limedb from 3code.cin")
    ap.add_argument(
        "--cin",
        default="Database/tricode-20260727.1.cin",
        help="input .cin path",
    )
    ap.add_argument("--out", default="Database/tricode.limedb", help="output .limedb path")
    ap.add_argument(
        "--hanconvert-db",
        default="LimeStudio/app/src/main/res/raw/hanconvertv2.db",
        help="basescore source (TCSC table)",
    )
    ap.add_argument(
        "--date",
        required=True,
        help="value for the im 'import' property row, e.g. '2026-07-20 00:00:00 +0800'",
    )
    ap.add_argument(
        "--version",
        default=None,
        help="override the version string parsed from the cin header comment",
    )
    args = ap.parse_args()

    if not os.path.exists(args.cin):
        log(f"ERROR: cin not found: {args.cin}")
        sys.exit(1)

    cin_data = parse_cin(args.cin)
    version = args.version or cin_data["version"]
    if not version:
        log("ERROR: could not determine version (not in cin header, --version not given)")
        sys.exit(1)

    log(
        f"parsed {len(cin_data['rows'])} deduped (code, word) rows, "
        f"{len(cin_data['keynames'])} keynames, selkey={cin_data['selkey']}, version={version}"
    )

    basescores = lookup_basescores(cin_data["rows"], args.hanconvert_db)

    with tempfile.TemporaryDirectory() as tmpdir:
        tmp_db = os.path.join(tmpdir, "tricode.db")
        count = build_inner_db(tmp_db, cin_data, basescores, args.date, version)
        zip_limedb(tmp_db, args.out, "tricode.db", args.date)

    size_kb = os.path.getsize(args.out) / 1024.0
    log(f"wrote {args.out} ({count} rows, {size_kb:.1f} KB compressed)")


if __name__ == "__main__":
    main()
