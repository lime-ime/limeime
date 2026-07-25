#!/usr/bin/env python3
"""Build Database/hahacj.limedb from its reviewed plaintext source.

The custom table is inserted strictly in source-file order. LIME uses `_id ASC` as
the same-code fallback when selection sorting is disabled, so changing insertion
order changes the user-visible candidate order (GitHub issue #194).
"""

from __future__ import annotations

import argparse
import sqlite3
import tempfile
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_SOURCE = ROOT / "Database" / "hahacj-20260723.txt"
DEFAULT_OUTPUT = ROOT / "Database" / "hahacj.limedb"


def parse_source(path: Path) -> tuple[dict[str, str], list[tuple[str, str]], list[tuple[str, str]]]:
    metadata: dict[str, str] = {}
    keynames: list[tuple[str, str]] = []
    rows: list[tuple[str, str]] = []
    section: str | None = None

    for line_number, raw_line in enumerate(
        path.read_text(encoding="utf-8-sig").splitlines(), start=1
    ):
        if raw_line == "%keyname begin":
            section = "keyname"
            continue
        if raw_line == "%keyname end":
            section = None
            continue
        if raw_line == "%chardef begin":
            section = "chardef"
            continue
        if raw_line == "%chardef end":
            section = None
            continue
        if section == "keyname":
            key, name = raw_line.split(maxsplit=1)
            keynames.append((key, name))
        elif section == "chardef":
            if "\t" not in raw_line:
                raise ValueError(f"Line {line_number}: mapping must be tab-separated")
            code, word = raw_line.split("\t", 1)
            if not code or not word:
                raise ValueError(f"Line {line_number}: empty mapping field")
            rows.append((code, word))
        elif raw_line.startswith(("%ename ", "%cname ", "%version ", "%selkey ")):
            key, value = raw_line[1:].split(maxsplit=1)
            metadata[key] = value

    required = {"ename", "cname", "version", "selkey"}
    missing = required - metadata.keys()
    if missing or not keynames or not rows:
        raise ValueError(f"Incomplete source, missing: {sorted(missing)}")
    return metadata, keynames, rows


def build_database(db_path: Path, source_name: str, metadata: dict[str, str],
                   keynames: list[tuple[str, str]], rows: list[tuple[str, str]]) -> None:
    connection = sqlite3.connect(db_path)
    try:
        connection.executescript(
            """
            CREATE TABLE custom (
                _id INTEGER PRIMARY KEY AUTOINCREMENT,
                code TEXT,
                code3r TEXT,
                word TEXT,
                related TEXT,
                score INTEGER DEFAULT 0,
                basescore INTEGER DEFAULT 0
            );
            CREATE TABLE im (
                _id INTEGER PRIMARY KEY AUTOINCREMENT,
                code TEXT,
                title TEXT,
                desc TEXT,
                keyboard TEXT,
                disable BOOLEAN,
                selkey TEXT,
                endkey TEXT,
                spacestyle TEXT
            );
            CREATE TABLE related (
                _id INTEGER PRIMARY KEY AUTOINCREMENT,
                pword TEXT,
                cword TEXT,
                basescore INTEGER DEFAULT 0,
                score INTEGER DEFAULT 0
            );
            """
        )
        connection.executemany(
            "INSERT INTO custom (code, word, score, basescore) VALUES (?, ?, 0, 0)", rows
        )
        connection.execute("CREATE INDEX custom_idx_code ON custom(code)")

        imkeys = "".join(key for key, _ in keynames)
        imkeynames = "|".join(name for _, name in keynames)
        im_rows = [
            ("source", source_name),
            ("version", metadata["version"]),
            ("name", metadata["cname"]),
            ("amount", str(len(rows))),
            ("import", metadata["version"]),
            ("selkey", metadata["selkey"]),
            ("limeendkey", ",."),
            ("imkeys", imkeys),
            ("imkeynames", imkeynames),
        ]
        connection.executemany(
            "INSERT INTO im (code, title, desc) VALUES ('cj4', ?, ?)", im_rows
        )
        connection.execute(
            "INSERT INTO im (code, title, desc, keyboard) VALUES (?, ?, ?, ?)",
            ("cj4", "keyboard", "倉頡輸入法鍵盤", "cj"),
        )
        connection.commit()
        connection.execute("VACUUM")
    finally:
        connection.close()


def write_archive(source: Path, output: Path) -> None:
    metadata, keynames, rows = parse_source(source)
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as temp_dir:
        db_path = Path(temp_dir) / "cj4.db"
        build_database(db_path, source.name, metadata, keynames, rows)
        archive_entry = zipfile.ZipInfo("cj4.db", date_time=(2026, 7, 23, 0, 0, 0))
        archive_entry.compress_type = zipfile.ZIP_DEFLATED
        archive_entry.external_attr = 0o100644 << 16
        with zipfile.ZipFile(output, "w") as archive:
            archive.writestr(archive_entry, db_path.read_bytes())
    print(f"Built {output} with {len(rows)} mappings from {source}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path, default=DEFAULT_SOURCE)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    args = parser.parse_args()
    write_archive(args.source, args.output)


if __name__ == "__main__":
    main()
