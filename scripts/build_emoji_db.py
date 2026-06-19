#!/usr/bin/env python3
# build_emoji_db.py - rebuild Android raw emoji.db from Emoji/CLDR source data.
# Usage: python3 scripts/build_emoji_db.py --output LimeStudio/app/src/main/res/raw/emoji.db

from __future__ import annotations

import argparse
import json
import re
import shutil
import sqlite3
from datetime import datetime, timezone
from pathlib import Path


EMOJI_DATA_COLUMNS = (
    "value",
    "cp",
    "group_name",
    "subgroup",
    "sort_order",
    "name_en",
    "name_tw",
    "tags_en",
    "tags_tw",
    "version",
)

EMOJI_TEST_RE = re.compile(
    r"^(?P<codepoints>[0-9A-F ]+)\s*;\s*(?P<status>[a-z-]+)\s*#\s*(?P<emoji>\S+)"
)


def is_han_character(char: str) -> bool:
    codepoint = ord(char)
    return (
        0x3400 <= codepoint <= 0x4DBF
        or 0x4E00 <= codepoint <= 0x9FFF
        or 0xF900 <= codepoint <= 0xFAFF
        or 0x20000 <= codepoint <= 0x2A6DF
        or 0x2A700 <= codepoint <= 0x2B73F
        or 0x2B740 <= codepoint <= 0x2B81F
        or 0x2B820 <= codepoint <= 0x2CEAF
        or 0x30000 <= codepoint <= 0x3134F
    )


def unique_ordered(values: list[str]) -> list[str]:
    seen = set()
    result = []
    for value in values:
        if value and value not in seen:
            seen.add(value)
            result.append(value)
    return result


def expand_tw_tags(tags: list[str]) -> list[str]:
    expanded = list(tags)
    for tag in tags:
        expanded.extend(char for char in tag if is_han_character(char))
    return unique_ordered(expanded)


def normalize_tags(tags: list[str] | str, expand_cjk: bool = False) -> str:
    if isinstance(tags, str):
        parts = [part.strip() for part in tags.split("|")]
    else:
        parts = [str(part).strip() for part in tags]
    parts = unique_ordered([part for part in parts if part])
    if expand_cjk:
        parts = expand_tw_tags(parts)
    return "|".join(parts)


def codepoints_from_hexcode(hexcode: str) -> str:
    return ",".join(part for part in hexcode.replace(" ", "-").split("-") if part)


def parse_emoji_test(text: str) -> list[dict[str, object]]:
    group_name = ""
    subgroup = ""
    rows = []
    for line in text.splitlines():
        stripped = line.strip()
        if stripped.startswith("# group:"):
            group_name = stripped.split(":", 1)[1].strip()
            continue
        if stripped.startswith("# subgroup:"):
            subgroup = stripped.split(":", 1)[1].strip()
            continue
        match = EMOJI_TEST_RE.match(stripped)
        if not match or match.group("status") != "fully-qualified":
            continue
        hexcode = "-".join(match.group("codepoints").split())
        rows.append(
            {
                "hexcode": hexcode,
                "value": match.group("emoji"),
                "group_name": group_name,
                "subgroup": subgroup,
                "sort_order": len(rows) + 1,
            }
        )
    return rows


def load_json(path: Path) -> object:
    with path.open("r", encoding="utf-8-sig") as handle:
        return json.load(handle)


def parse_emojibase_records(records: list[dict[str, object]]) -> dict[str, dict[str, object]]:
    parsed = {}
    for record in records:
        hexcode = record.get("hexcode")
        if not hexcode:
            continue
        tags = record.get("tags") or record.get("shortcodes") or []
        if isinstance(tags, str):
            tags = [tags]
        parsed[str(hexcode)] = {
            "label": record.get("label") or record.get("annotation") or "",
            "tags": [str(tag) for tag in tags],
            "version": record.get("version", record.get("emojiVersion", 0.0)),
        }
    return parsed


def build_rows(
    emoji_order: list[dict[str, object]],
    en_records: dict[str, dict[str, object]],
    tw_records: dict[str, dict[str, object]],
) -> list[dict[str, object]]:
    rows = []
    for ordered in emoji_order:
        hexcode = str(ordered["hexcode"])
        en = en_records.get(hexcode)
        if not en:
            continue
        tw = tw_records.get(hexcode, {})
        name_en = str(en.get("label", ""))
        rows.append(
            {
                "value": ordered["value"],
                "cp": codepoints_from_hexcode(hexcode),
                "group_name": ordered["group_name"],
                "subgroup": ordered["subgroup"],
                "sort_order": ordered["sort_order"],
                "name_en": name_en,
                "name_tw": tw.get("label") or name_en,
                "tags_en": en.get("tags", []),
                "tags_tw": tw.get("tags") or en.get("tags", []),
                "version": en.get("version", 0.0),
            }
        )
    return rows


def create_schema(db: sqlite3.Connection) -> None:
    db.executescript(
        """
        DROP TABLE IF EXISTS emoji_data;
        DROP TABLE IF EXISTS im;

        CREATE TABLE emoji_data (
            value      TEXT PRIMARY KEY,
            cp         TEXT NOT NULL,
            group_name TEXT NOT NULL,
            subgroup   TEXT NOT NULL,
            sort_order INTEGER NOT NULL,
            name_en    TEXT,
            name_tw    TEXT,
            tags_en    TEXT,
            tags_tw    TEXT,
            version    REAL NOT NULL
        );
        CREATE INDEX idx_emoji_group ON emoji_data(group_name, sort_order);

        CREATE TABLE im (
            code       TEXT,
            title      TEXT,
            desc       TEXT,
            keyboard   TEXT,
            disable    TEXT,
            selkey     TEXT,
            endkey     TEXT,
            spacestyle TEXT
        );
        """
    )


def write_database(
    output_path: Path,
    rows: list[dict[str, object]],
    emoji_version: str,
    build_timestamp: str,
) -> None:
    output_path.parent.mkdir(parents=True, exist_ok=True)
    if output_path.exists():
        output_path.unlink()

    with sqlite3.connect(output_path) as db:
        create_schema(db)
        for row in rows:
            normalized = dict(row)
            normalized["tags_en"] = normalize_tags(row.get("tags_en", []))
            normalized["tags_tw"] = normalize_tags(row.get("tags_tw", []), expand_cjk=True)
            db.execute(
                """
                INSERT INTO emoji_data (
                    value, cp, group_name, subgroup, sort_order,
                    name_en, name_tw, tags_en, tags_tw, version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                tuple(normalized[column] for column in EMOJI_DATA_COLUMNS),
            )

        metadata = [
            ("emoji", "version", emoji_version),
            ("emoji", "name", f"Emoji {emoji_version} Dataset"),
            ("emoji", "source", "emoji.db"),
            ("emoji", "amount", str(len(rows))),
            ("emoji", "import", build_timestamp),
        ]
        db.executemany(
            """
            INSERT INTO im (
                code, title, desc, keyboard, disable, selkey, endkey, spacestyle
            ) VALUES (?, ?, ?, '', 0, '', '', '')
            """,
            metadata,
        )


def build_from_files(
    emoji_test_path: Path,
    en_json_path: Path,
    tw_json_path: Path,
    output_path: Path,
    emoji_version: str,
    build_timestamp: str,
    copy_targets: list[Path] | None = None,
) -> None:
    emoji_order = parse_emoji_test(emoji_test_path.read_text(encoding="utf-8-sig"))
    en_records = parse_emojibase_records(load_json(en_json_path))
    tw_records = parse_emojibase_records(load_json(tw_json_path))
    rows = build_rows(emoji_order, en_records, tw_records)
    write_database(output_path, rows, emoji_version, build_timestamp)
    for target in copy_targets or []:
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(output_path, target)


def main() -> int:
    parser = argparse.ArgumentParser(description="Build the LimeIME emoji.db artifact.")
    parser.add_argument("--output", type=Path, default=Path("LimeStudio/app/src/main/res/raw/emoji.db"))
    parser.add_argument("--version", default="17.0")
    parser.add_argument("--emoji-test", type=Path, required=True)
    parser.add_argument("--en-json", type=Path, required=True)
    parser.add_argument("--tw-json", type=Path, required=True)
    parser.add_argument(
        "--copy-to",
        type=Path,
        action="append",
        default=[],
        help="Additional emoji.db destination path. Can be repeated.",
    )
    args = parser.parse_args()
    build_from_files(
        args.emoji_test,
        args.en_json,
        args.tw_json,
        args.output,
        args.version,
        datetime.now(timezone.utc).replace(microsecond=0).isoformat(),
        args.copy_to,
    )
    return 0


if __name__ == "__main__":
    main()
