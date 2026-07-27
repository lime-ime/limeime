#!/usr/bin/env python3
"""Regression tests for the source-backed Tricode downloadable database."""

from __future__ import annotations

import contextlib
import hashlib
import importlib.util
import sqlite3
import subprocess
import sys
import tempfile
import time
import unittest
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "Database" / "tricode-20260727.1.cin"
ARCHIVE = ROOT / "Database" / "tricode.limedb"
BUILDER_PATH = ROOT / "scripts" / "build_tricode_db.py"
ORDER_GATE_PATH = ROOT / "scripts" / "test_limedb_order.py"
PR_WORKFLOW = ROOT / ".github" / "workflows" / "database-integrity.yml"
RELEASE_WORKFLOW = ROOT / ".github" / "workflows" / "new_release.yml"
SOURCE_SHA256 = "e04e98f48c7b4d9265b81ded11bacbb1e75771c093e04c66697058b935925e88"


def load_builder():
    spec = importlib.util.spec_from_file_location("build_tricode_db", BUILDER_PATH)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"Cannot load {BUILDER_PATH}")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def load_order_gate():
    spec = importlib.util.spec_from_file_location("test_limedb_order", ORDER_GATE_PATH)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"Cannot load {ORDER_GATE_PATH}")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class TricodeSourceTest(unittest.TestCase):
    def test_committed_source_is_the_reviewed_20260727_1_table(self):
        self.assertTrue(SOURCE.is_file(), f"missing authoritative source: {SOURCE}")
        self.assertEqual(SOURCE_SHA256, hashlib.sha256(SOURCE.read_bytes()).hexdigest())

        parsed = load_builder().parse_cin(SOURCE)
        self.assertEqual("20260727.1", parsed["version"])
        self.assertEqual("1234567890", parsed["selkey"])
        self.assertEqual(31, len(parsed["keynames"]))
        self.assertEqual(15_934, len(parsed["rows"]))
        self.assertEqual(len(parsed["rows"]), len(set(parsed["rows"])))

    def test_order_gate_matches_builder_normalization(self):
        source = "%chardef begin\n ABC \t 字 \nabc\t字\nX Y\n# comment\n%chardef end\n"
        with tempfile.TemporaryDirectory() as temporary_directory:
            path = Path(temporary_directory) / "synthetic.cin"
            path.write_text(source, encoding="utf-8")
            self.assertEqual(
                [("abc", "字"), ("x", "Y")],
                load_order_gate().parse_tricode_cin(path),
            )


class TricodeBuildTest(unittest.TestCase):
    def build(self, output: Path) -> None:
        subprocess.run(
            [
                sys.executable,
                str(BUILDER_PATH),
                "--cin",
                str(SOURCE),
                "--out",
                str(output),
                "--date",
                "2026-07-27 00:00:00 +0800",
            ],
            cwd=ROOT,
            check=True,
            capture_output=True,
            text=True,
        )

    def test_repeated_builds_are_byte_identical(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            first = Path(temporary_directory) / "first.limedb"
            second = Path(temporary_directory) / "second.limedb"
            self.build(first)
            time.sleep(2.1)
            self.build(second)
            self.assertEqual(first.read_bytes(), second.read_bytes())

    def test_default_build_uses_the_committed_source(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            output = Path(temporary_directory) / "default-source.limedb"
            subprocess.run(
                [
                    sys.executable,
                    str(BUILDER_PATH),
                    "--out",
                    str(output),
                    "--date",
                    "2026-07-27 00:00:00 +0800",
                ],
                cwd=ROOT,
                check=True,
                capture_output=True,
                text=True,
            )
            with zipfile.ZipFile(output) as archive:
                archive.extract("tricode.db", temporary_directory)
            database = Path(temporary_directory) / "tricode.db"
            with contextlib.closing(sqlite3.connect(database)) as connection:
                rows = connection.execute(
                    "SELECT code, word FROM custom ORDER BY _id ASC"
                ).fetchall()
                metadata = dict(
                    connection.execute(
                        "SELECT title, desc FROM im WHERE code = 'tricode'"
                    ).fetchall()
                )
            self.assertEqual(load_builder().parse_cin(SOURCE)["rows"], rows)
            self.assertEqual("20260727.1", metadata.get("version"))

    def test_committed_archive_matches_source_order_and_metadata(self):
        builder = load_builder()
        expected_rows = builder.parse_cin(SOURCE)["rows"]
        expected_basescores = builder.lookup_basescores(
            expected_rows, ROOT / "LimeStudio/app/src/main/res/raw/hanconvertv2.db"
        )
        with tempfile.TemporaryDirectory() as temporary_directory:
            with zipfile.ZipFile(ARCHIVE) as archive:
                self.assertEqual(["tricode.db"], archive.namelist())
                archive.extract("tricode.db", temporary_directory)

            database = Path(temporary_directory) / "tricode.db"
            with contextlib.closing(sqlite3.connect(database)) as connection:
                self.assertEqual("ok", connection.execute("PRAGMA integrity_check").fetchone()[0])
                actual_rows = connection.execute(
                    "SELECT _id, code, code3r, word, related, score, basescore "
                    "FROM custom ORDER BY _id ASC"
                ).fetchall()
                self.assertEqual(len(expected_rows), len(actual_rows))
                for expected_id, ((expected_code, expected_word), actual) in enumerate(
                    zip(expected_rows, actual_rows), 1
                ):
                    self.assertEqual(expected_id, actual[0])
                    self.assertEqual(expected_code, actual[1])
                    self.assertEqual("", actual[2])
                    self.assertEqual(expected_word, actual[3])
                    self.assertIsNone(actual[4])
                    self.assertEqual(0, actual[5])
                    self.assertEqual(expected_basescores.get(expected_word, 0), actual[6])

                indexes = {
                    row[1]
                    for row in connection.execute("PRAGMA index_list(custom)").fetchall()
                }
                self.assertIn("custom_idx_code", indexes)

                metadata = dict(
                    connection.execute(
                        "SELECT title, desc FROM im WHERE code = 'tricode'"
                    ).fetchall()
                )
                self.assertEqual("20260727.1", metadata.get("version"))
                self.assertEqual("15934", metadata.get("amount"))
                self.assertEqual(
                    "https://3code-type.github.io/3code.cin", metadata.get("source")
                )
                self.assertEqual("1234567890", metadata.get("selkey"))


class TricodeWorkflowTest(unittest.TestCase):
    def test_pr_and_release_workflows_run_tricode_gate(self):
        command = "python3 scripts/test_tricode_limedb.py"
        self.assertIn(command, PR_WORKFLOW.read_text(encoding="utf-8"))
        self.assertIn(command, RELEASE_WORKFLOW.read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
