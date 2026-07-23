#!/usr/bin/env python3
"""Regression checks for the bundled 哈哈倉頡 archive (GitHub issue #194)."""

from __future__ import annotations

import hashlib
import sqlite3
import subprocess
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "Database" / "hahacj-20260723.txt"
ARCHIVE = ROOT / "Database" / "hahacj.limedb"
PR_WORKFLOW = ROOT / ".github" / "workflows" / "database-integrity.yml"
RELEASE_WORKFLOW = ROOT / ".github" / "workflows" / "new_release.yml"
EXPECTED_SOURCE_SHA256 = "c1ce0ddd185873597afa469a5156d76d71418867d2f8c8db4ab946839267abd9"
EXPECTED_SOURCE_VERSION = "20260723_082459"
EXPECTED_SOURCE_ROW_COUNT = 33_044


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
    def test_source_matches_authoritative_reporter_attachment(self) -> None:
        self.assertEqual(hashlib.sha256(SOURCE.read_bytes()).hexdigest(), EXPECTED_SOURCE_SHA256)
        version, rows = parse_source(SOURCE)
        self.assertEqual(version, EXPECTED_SOURCE_VERSION)
        self.assertEqual(len(rows), EXPECTED_SOURCE_ROW_COUNT)

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

    def test_builder_is_repeatable_and_matches_source_semantics(self) -> None:
        version, expected_rows = parse_source(SOURCE)
        with tempfile.TemporaryDirectory() as temp_dir:
            rebuilt_paths = [
                Path(temp_dir) / "hahacj-first.limedb",
                Path(temp_dir) / "hahacj-second.limedb",
            ]
            for rebuilt in rebuilt_paths:
                subprocess.run(
                    [
                        sys.executable,
                        str(ROOT / "scripts" / "build_hahacj_limedb.py"),
                        "--source",
                        str(SOURCE),
                        "--output",
                        str(rebuilt),
                    ],
                    check=True,
                    capture_output=True,
                    text=True,
                )
            self.assertEqual(rebuilt_paths[0].read_bytes(), rebuilt_paths[1].read_bytes())

            with zipfile.ZipFile(rebuilt_paths[0]) as archive:
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

    def test_pr_and_release_workflows_run_database_gate(self) -> None:
        pr_workflow = PR_WORKFLOW.read_text(encoding="utf-8")
        release_workflow = RELEASE_WORKFLOW.read_text(encoding="utf-8")
        command = 'python3 scripts/test_limedb_order.py --all --base-ref "$BASE_REF"'
        self.assertIn(command, pr_workflow)
        self.assertIn("github.event.pull_request.base.sha", pr_workflow)
        self.assertIn("fetch-depth: 0", pr_workflow)
        self.assertIn(command, release_workflow)
        self.assertIn("if: github.ref == 'refs/heads/master'", release_workflow)
        self.assertIn("git describe --tags --abbrev=0 HEAD^", release_workflow)
        self.assertIn("fetch-depth: 0", release_workflow)


if __name__ == "__main__":
    unittest.main()
