#!/usr/bin/env python3
"""register_custom_layouts_xcodeproj.py - bundle the lime_custom family (issue #177).

A layout JSON that is not registered in LimeIME.xcodeproj never reaches the
keyboard extension bundle, so `LayoutLoader.load` returns nil at runtime — the
exact failure mode behind #177 (and #160 before it). Generating the JSON is
therefore only half the fix; each file needs all four pbxproj entries:

  1. PBXFileReference          - the file exists in the project
  2. PBXBuildFile              - it participates in a build phase
  3. Layouts group children    - it shows up in the navigator
  4. LimeKeyboard Resources    - it is actually copied into the bundle

Each `lime_custom*` entry is inserted directly after its `lime_abc*`
counterpart in all four sections, so the project file stays grouped the way a
human would have written it.

The script is idempotent: files already registered are skipped.

Usage: python3 scripts/register_custom_layouts_xcodeproj.py
"""

import hashlib
import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[1]
PBXPROJ = REPO_ROOT / "LimeIME-iOS" / "LimeIME.xcodeproj" / "project.pbxproj"

# custom layout -> the lime_abc entry it is inserted after
PAIRS = (
    ("lime_custom.json", "lime_abc.json"),
    ("lime_custom_shift.json", "lime_abc_shift.json"),
    ("lime_custom_ipad.json", "lime_abc_ipad.json"),
    ("lime_custom_ipad_shift.json", "lime_abc_ipad_shift.json"),
    ("lime_custom_ipad_narrow.json", "lime_abc_ipad_narrow.json"),
    ("lime_custom_ipad_narrow_shift.json", "lime_abc_ipad_narrow_shift.json"),
)


def stable_uuid(seed):
    """Deterministic 24-hex-char pbxproj identifier.

    Derived from the filename rather than random so re-running the script (or
    running it on another machine) produces the same project file — a random
    UUID would show up as spurious churn in every diff.
    """
    return hashlib.sha1(seed.encode("utf-8")).hexdigest().upper()[:24]


def insert_after(content, anchor_pattern, new_line, what):
    match = re.search(anchor_pattern, content)
    if match is None:
        raise SystemExit(f"could not find anchor for {what}: {anchor_pattern}")
    end = match.end()
    return content[:end] + "\n" + new_line + content[end:]


def main():
    content = PBXPROJ.read_text(encoding="utf-8")
    added = []

    for fname, anchor in PAIRS:
        if f"/* {fname} */ = {{isa = PBXFileReference" in content:
            print(f"skip  {fname} (already registered)")
            continue

        file_ref = stable_uuid(f"fileRef:{fname}")
        build_file = stable_uuid(f"buildFile:{fname}")

        # 1. PBXFileReference
        content = insert_after(
            content,
            rf"\t\t[0-9A-F]{{24}} /\* {re.escape(anchor)} \*/ = \{{isa = PBXFileReference;[^\n]+",
            f"\t\t{file_ref} /* {fname} */ = {{isa = PBXFileReference; "
            f'lastKnownFileType = text.json; path = {fname}; sourceTree = "<group>"; }};',
            f"{fname} PBXFileReference",
        )

        # 2. PBXBuildFile
        content = insert_after(
            content,
            rf"\t\t[0-9A-F]{{24}} /\* {re.escape(anchor)} in Resources \*/ = \{{isa = PBXBuildFile;[^\n]+",
            f"\t\t{build_file} /* {fname} in Resources */ = {{isa = PBXBuildFile; "
            f"fileRef = {file_ref} /* {fname} */; }};",
            f"{fname} PBXBuildFile",
        )

        # 3. Layouts group children
        content = insert_after(
            content,
            rf"\t\t\t\t[0-9A-F]{{24}} /\* {re.escape(anchor)} \*/,",
            f"\t\t\t\t{file_ref} /* {fname} */,",
            f"{fname} group child",
        )

        # 4. Resources build phase
        content = insert_after(
            content,
            rf"\t\t\t\t[0-9A-F]{{24}} /\* {re.escape(anchor)} in Resources \*/,",
            f"\t\t\t\t{build_file} /* {fname} in Resources */,",
            f"{fname} Resources build phase",
        )

        added.append(fname)
        print(f"added {fname}  (fileRef {file_ref}, buildFile {build_file})")

    if added:
        PBXPROJ.write_text(content, encoding="utf-8")
        print(f"\nregistered {len(added)} layout(s) in {PBXPROJ.name}")
    else:
        print("\nnothing to do")
    return 0


if __name__ == "__main__":
    sys.exit(main())
