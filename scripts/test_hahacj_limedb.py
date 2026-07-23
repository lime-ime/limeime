#!/usr/bin/env python3
"""Regression checks for the bundled 哈哈倉頡 archive (GitHub issue #194)."""

from __future__ import annotations

import sqlite3
import tempfile
import unittest
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "Database" / "hahacj-20260723.txt"
ARCHIVE = ROOT / "Database" / "hahacj.limedb"


def parse_source(path: Path) -> tuple[str, list[tuple[str, str]]]:
    version = ""
    rows: list[tuple[str, str]] = []
    in_chardef = False
    for raw_line in path.read_text(encoding="utf-8-sig").splitlines():
        if raw_line.startswith("%version "):
            version = raw_line.split(maxsplit=1)[1]
        elif raw_line == "%chardef begin":
            in_chardef = True
        elif raw_line == "%chardef end":
            in_chardef = False
        elif in_chardef:
            code, word = raw_line.split("\t", 1)
            rows.append((code, word))
    if not version or not rows:
        raise ValueError(f"Incomplete 哈哈倉頡 source: {path}")
    return version, rows


class HahacjArchiveTest(unittest.TestCase):
    def test_archive_preserves_source_order_and_metadata(self) -> None:
        version, expected_rows = parse_source(SOURCE)

        with tempfile.TemporaryDirectory() as temp_dir:
            with zipfile.ZipFile(ARCHIVE) as archive:
                self.assertEqual(archive.namelist(), ["cj4.db"])
                archive.extract("cj4.db", temp_dir)

            connection = sqlite3.connect(Path(temp_dir) / "cj4.db")
            try:
                actual_rows = connection.execute(
                    "SELECT code, word FROM custom ORDER BY _id"
                ).fetchall()
                metadata = dict(
                    connection.execute(
                        "SELECT title, desc FROM im WHERE code = 'cj4'"
                    ).fetchall()
                )
            finally:
                connection.close()

        self.assertEqual(actual_rows, expected_rows)
        self.assertEqual(metadata.get("version"), version)
        self.assertEqual(metadata.get("amount"), str(len(expected_rows)))
        self.assertEqual(metadata.get("limeendkey"), ",.")

    def test_reported_single_key_order_starts_with_du(self) -> None:
        _, rows = parse_source(SOURCE)
        self.assertEqual([word for code, word in rows if code == "j"], ["都", "十"])


if __name__ == "__main__":
    unittest.main()
