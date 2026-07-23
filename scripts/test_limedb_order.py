#!/usr/bin/env python3
"""Enforce source-order contracts for every downloadable ``Database/*.limedb``.

Existing archives without committed source material are hash-pinned. A future PR may
not update that hash: it must replace the grandfathered entry with a source-backed
contract, which is then checked row-for-row in SQLite ``_id`` order.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import re
import sqlite3
import subprocess
import tempfile
import zipfile
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
DATABASE_DIR = ROOT / "Database"
MANIFEST = DATABASE_DIR / "limedb-order-contracts.json"
SQL_IDENTIFIER = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*$")


class ContractError(RuntimeError):
    pass


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def parse_cin(path: Path) -> list[tuple[str, str]]:
    rows: list[tuple[str, str]] = []
    in_chardef = False
    for line_number, raw_line in enumerate(
        path.read_text(encoding="utf-8-sig").splitlines(), 1
    ):
        line = raw_line.strip("\r")
        if line == "%chardef begin":
            in_chardef = True
            continue
        if line == "%chardef end":
            in_chardef = False
            continue
        if not in_chardef or not line or line.startswith("#"):
            continue
        if "\t" in line:
            code, word = line.split("\t", 1)
        else:
            parts = line.split(maxsplit=1)
            if len(parts) != 2:
                raise ContractError(f"{path}:{line_number}: invalid CIN mapping")
            code, word = parts
        rows.append((code, word))
    if not rows:
        raise ContractError(f"{path}: no mappings found")
    return rows


def parse_lime_text_v2(path: Path) -> list[tuple[str, str]]:
    rows: list[tuple[str, str]] = []
    in_chardef = False
    for line_number, line in enumerate(
        path.read_text(encoding="utf-8-sig").splitlines(), 1
    ):
        if line == "%chardef begin":
            in_chardef = True
            continue
        if line == "%chardef end":
            in_chardef = False
            continue
        if not in_chardef:
            continue
        fields = next(
            csv.reader(
                [line], delimiter="|", escapechar="\\", quoting=csv.QUOTE_NONE
            )
        )
        if len(fields) < 2:
            raise ContractError(f"{path}:{line_number}: invalid lime-text-v2 mapping")
        rows.append((fields[0], fields[1]))
    if not rows:
        raise ContractError(f"{path}: no mappings found")
    return rows


def parse_source(path: Path, source_format: str) -> list[tuple[str, str]]:
    if source_format == "cin":
        return parse_cin(path)
    if source_format == "lime-text-v2":
        return parse_lime_text_v2(path)
    raise ContractError(f"Unsupported source format: {source_format}")


def read_manifest(path: Path = MANIFEST) -> dict[str, Any]:
    data = json.loads(path.read_text(encoding="utf-8"))
    if data.get("version") != 1 or not isinstance(data.get("archives"), dict):
        raise ContractError(f"Invalid order-contract manifest: {path}")
    return data


def read_base_manifest(base_ref: str) -> dict[str, Any] | None:
    if not base_ref:
        return None
    commit_check = subprocess.run(
        ["git", "cat-file", "-e", f"{base_ref}^{{commit}}"],
        cwd=ROOT,
        capture_output=True,
        text=True,
    )
    if commit_check.returncode != 0:
        raise ContractError(f"Base ref is unavailable: {base_ref}")
    tree_check = subprocess.run(
        [
            "git",
            "ls-tree",
            "-r",
            "--name-only",
            base_ref,
            "--",
            "Database/limedb-order-contracts.json",
        ],
        cwd=ROOT,
        capture_output=True,
        text=True,
    )
    if tree_check.returncode != 0:
        raise ContractError(f"Cannot inspect base ref: {base_ref}")
    if not tree_check.stdout.strip():
        # Initial reviewed introduction of the manifest.
        return None
    result = subprocess.run(
        ["git", "show", f"{base_ref}:Database/limedb-order-contracts.json"],
        cwd=ROOT,
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        raise ContractError(f"Cannot read order-contract manifest from {base_ref}")
    data = json.loads(result.stdout)
    if not isinstance(data.get("archives"), dict):
        raise ContractError("Base order-contract manifest is invalid")
    return data


def validate_transition(
    current: dict[str, Any], base: dict[str, Any] | None
) -> None:
    if base is None:
        return
    current_archives = current["archives"]
    base_archives = base["archives"]
    for name, old_contract in base_archives.items():
        new_contract = current_archives.get(name)
        if new_contract is None:
            raise ContractError(f"{name}: order contract may not be removed")
        old_is_grandfathered = "grandfathered_sha256" in old_contract
        new_is_grandfathered = "grandfathered_sha256" in new_contract
        if old_is_grandfathered and new_is_grandfathered:
            if new_contract != old_contract:
                raise ContractError(
                    f"{name}: grandfathered hash is immutable; add a source-backed "
                    "order contract for this table update"
                )
        elif not old_is_grandfathered and new_is_grandfathered:
            raise ContractError(
                f"{name}: source-backed order contract may not be downgraded"
            )
    for name, contract in current_archives.items():
        if name not in base_archives and "grandfathered_sha256" in contract:
            raise ContractError(
                f"{name}: new archives require a source-backed order contract"
            )


def extracted_database(archive_path: Path, contract: dict[str, Any], temp_dir: str) -> Path:
    try:
        archive = zipfile.ZipFile(archive_path)
    except zipfile.BadZipFile as error:
        raise ContractError(f"{archive_path}: invalid ZIP archive") from error
    with archive:
        database_entries = [
            name for name in archive.namelist() if name.lower().endswith(".db")
        ]
        expected_inner = contract.get("inner_database")
        if expected_inner:
            if database_entries != [expected_inner]:
                raise ContractError(
                    f"{archive_path}: expected only {expected_inner}, got {database_entries}"
                )
            selected = expected_inner
        elif len(database_entries) == 1:
            selected = database_entries[0]
        else:
            raise ContractError(
                f"{archive_path}: expected exactly one inner SQLite database"
            )
        archive.extract(selected, temp_dir)
    return Path(temp_dir) / selected


def validate_sqlite(archive_path: Path, contract: dict[str, Any]) -> None:
    with tempfile.TemporaryDirectory() as temp_dir:
        database_path = extracted_database(archive_path, contract, temp_dir)
        connection = sqlite3.connect(database_path)
        try:
            integrity = connection.execute("PRAGMA integrity_check").fetchone()[0]
            if integrity != "ok":
                raise ContractError(f"{archive_path}: SQLite integrity is {integrity!r}")

            source_name = contract.get("source")
            if not source_name:
                return
            source_path = ROOT / source_name
            if not source_path.is_file():
                raise ContractError(f"{archive_path}: source does not exist: {source_name}")
            expected_hash = contract.get("source_sha256")
            if expected_hash and sha256(source_path) != expected_hash:
                raise ContractError(f"{archive_path}: authoritative source hash changed")
            expected_rows = parse_source(source_path, contract["source_format"])
            table = contract.get("mapping_table", "custom")
            if not isinstance(table, str) or not SQL_IDENTIFIER.fullmatch(table):
                raise ContractError(
                    f"{archive_path}: invalid mapping-table identifier: {table!r}"
                )
            actual_rows = connection.execute(
                f'SELECT code, word FROM "{table}" ORDER BY _id ASC'
            ).fetchall()
            if actual_rows != expected_rows:
                mismatch = next(
                    (
                        index
                        for index, pair in enumerate(
                            zip(expected_rows, actual_rows), 1
                        )
                        if pair[0] != pair[1]
                    ),
                    min(len(expected_rows), len(actual_rows)) + 1,
                )
                raise ContractError(
                    f"{archive_path}: source order differs at row {mismatch}; "
                    f"source={len(expected_rows)} archive={len(actual_rows)}"
                )

            expected_metadata = contract.get("expected_metadata", {})
            if expected_metadata:
                metadata_code = contract["metadata_code"]
                metadata = dict(
                    connection.execute(
                        "SELECT title, desc FROM im WHERE code = ?", (metadata_code,)
                    ).fetchall()
                )
                for key, expected_value in expected_metadata.items():
                    if metadata.get(key) != expected_value:
                        raise ContractError(
                            f"{archive_path}: metadata {key!r} is "
                            f"{metadata.get(key)!r}, expected {expected_value!r}"
                        )
        finally:
            connection.close()


def validate_all(base_ref: str = "") -> None:
    manifest = read_manifest()
    validate_transition(manifest, read_base_manifest(base_ref))
    contracts = manifest["archives"]
    actual_names = {path.name for path in DATABASE_DIR.glob("*.limedb")}
    registered_names = set(contracts)
    if actual_names != registered_names:
        missing = sorted(actual_names - registered_names)
        stale = sorted(registered_names - actual_names)
        raise ContractError(
            f"Order-contract registry mismatch: unregistered={missing}, missing={stale}"
        )

    for name in sorted(actual_names):
        archive_path = DATABASE_DIR / name
        contract = contracts[name]
        grandfathered_hash = contract.get("grandfathered_sha256")
        if grandfathered_hash and sha256(archive_path) != grandfathered_hash:
            raise ContractError(
                f"{name}: archive changed; replace the grandfathered hash with a "
                "source-backed order contract"
            )
        if bool(grandfathered_hash) == bool(contract.get("source")):
            raise ContractError(
                f"{name}: contract must contain exactly one of grandfathered_sha256/source"
            )
        validate_sqlite(archive_path, contract)
        mode = "source order" if contract.get("source") else "grandfathered hash"
        print(f"OK {name}: {mode}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--all", action="store_true", help="validate every .limedb archive")
    parser.add_argument("--base-ref", default="", help="base Git ref for anti-bypass checks")
    args = parser.parse_args()
    if not args.all:
        parser.error("--all is required")
    try:
        validate_all(args.base_ref)
    except (ContractError, KeyError, sqlite3.DatabaseError) as error:
        raise SystemExit(f"LIMEDB ORDER GATE FAILED: {error}") from error


if __name__ == "__main__":
    main()
