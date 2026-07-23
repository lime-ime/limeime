#!/usr/bin/env python3
"""Regression contract for issue #202 Array period-key long press parity."""

import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
NS = "{http://schemas.android.com/apk/res-auto}"


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    raise SystemExit(1)


def android_labels() -> list[str]:
    path = ROOT / "LimeStudio/app/src/main/res/xml/popup_c_punctuation.xml"
    root = ET.parse(path).getroot()
    return [key.attrib.get(f"{NS}keyLabel", "") for key in root.iter("Key")]


def ios_labels() -> list[str]:
    path = ROOT / "LimeIME-iOS/LimeKeyboard/Layouts/popup_c_punctuation.json"
    data = json.loads(path.read_text(encoding="utf-8"))
    return [key["label"] for row in data["rows"] for key in row["keys"]]


def assert_array_key_uses_popup() -> None:
    android = ET.parse(ROOT / "LimeStudio/app/src/main/res/xml/lime_array.xml").getroot()
    android_period = next(
        (key for key in android.iter("Key") if key.attrib.get(f"{NS}codes") == "46"),
        None,
    )
    if android_period is None or android_period.attrib.get(f"{NS}popupKeyboard") != "@xml/popup_c_punctuation":
        fail("Android Array code 46 must use popup_c_punctuation")

    for layout_id in ("lime_array", "lime_array_ipad", "lime_array_ipad_narrow"):
        path = ROOT / f"LimeIME-iOS/LimeKeyboard/Layouts/{layout_id}.json"
        data = json.loads(path.read_text(encoding="utf-8-sig"))
        keys = [key for row in data["rows"] for key in row["keys"]]
        period = next((key for key in keys if key["code"] == 46), None)
        if period is None or period.get("popupKeyboard") != "@xml/popup_c_punctuation":
            fail(f"iOS {layout_id} code 46 must use popup_c_punctuation")


def assert_popup_contract(platform: str, labels: list[str]) -> None:
    if labels.count(".") != 1:
        fail(f"{platform} popup must contain ASCII period exactly once")
    for existing in ("。", "．"):
        if existing not in labels:
            fail(f"{platform} popup must preserve {existing}")


def main() -> int:
    assert_array_key_uses_popup()
    android = android_labels()
    ios = ios_labels()
    assert_popup_contract("Android", android)
    assert_popup_contract("iOS", ios)
    android_periods = [label for label in android if label in (".", "．", "。")]
    ios_periods = [label for label in ios if label in (".", "．", "。")]
    if android_periods != ios_periods:
        fail("Android and iOS period options must have matching order")
    print("PASS: Array period popup exposes ASCII '.', preserves Chinese periods, and matches across platforms")
    return 0


if __name__ == "__main__":
    sys.exit(main())
