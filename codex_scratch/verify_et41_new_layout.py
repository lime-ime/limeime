import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
NS = {"limehd": "http://schemas.android.com/apk/res-auto"}


def android_rows(path):
    tree = ET.parse(ROOT / path)
    rows = []
    for row in tree.getroot().findall("Row"):
        keys = []
        for key in row.findall("Key"):
            icon = key.get(f"{{{NS['limehd']}}}keyIcon", "")
            label = key.get(f"{{{NS['limehd']}}}keyLabel", "")
            if icon:
                icon_name = icon.replace("@drawable/", "")
                if "shift" in icon_name:
                    keys.append("Shift")
                elif "delete" in icon_name:
                    keys.append("Delete")
                elif "done" in icon_name:
                    keys.append("KeyboardDown")
                elif "return" in icon_name:
                    keys.append("Return")
                elif "space" in icon_name:
                    keys.append("Space")
                else:
                    keys.append(icon_name)
            else:
                keys.append(label.replace("\\@", "@").replace("\\#", "#").replace("\\?", "?").replace("\\n", "/"))
        rows.append(keys)
    return rows


def ios_rows(path):
    data = json.loads((ROOT / path).read_text(encoding="utf-8"))
    rows = []
    for row in data["rows"]:
        keys = []
        for key in row["keys"]:
            icon = key.get("icon", "")
            label = key.get("label", "")
            sublabel = key.get("sublabel", "")
            if icon:
                if "shift" in icon:
                    keys.append("Shift")
                elif "delete" in icon:
                    keys.append("Delete")
                elif "keyboard.chevron" in icon:
                    keys.append("KeyboardDown")
                elif icon == "return":
                    keys.append("Return")
                elif icon == "space.bar":
                    keys.append("Space")
                else:
                    keys.append(icon)
            elif label == "EN":
                keys.append("EN")
            elif sublabel:
                keys.append(f"{label}/{sublabel}")
            else:
                keys.append(label)
        rows.append(keys)
    return rows



def android_key_by_code(path, code):
    tree = ET.parse(ROOT / path)
    for key in tree.getroot().iter("Key"):
        if key.get(f"{{{NS['limehd']}}}codes") == str(code):
            return key
    raise AssertionError(f"{path}: missing code {code}")


def ios_key_by_code(path, code):
    data = json.loads((ROOT / path).read_text(encoding="utf-8"))
    for row in data["rows"]:
        for key in row["keys"]:
            if key.get("code") == code:
                return key
    raise AssertionError(f"{path}: missing code {code}")


def verify_long_press_hints():
    android_path = "LimeStudio/app/src/main/res/xml/lime_et_41.xml"
    ios_path = "LimeIME-iOS/LimeKeyboard/Layouts/lime_et_41.json"
    expected = {
        45: ("-\\nㄥ", "5"),
        61: ("=\\nㄦ", "6"),
    }
    for code, (android_label, popup_char) in expected.items():
        key = android_key_by_code(android_path, code)
        if key.get(f"{{{NS['limehd']}}}keyLabel") != android_label:
            raise AssertionError(f"Android code {code}: wrong keyLabel")
        if key.get(f"{{{NS['limehd']}}}popupKeyboard") != "@xml/popup_template":
            raise AssertionError(f"Android code {code}: missing popup_template")
        if key.get(f"{{{NS['limehd']}}}popupCharacters") != popup_char:
            raise AssertionError(f"Android code {code}: wrong popupCharacters")

        key = ios_key_by_code(ios_path, code)
        if key.get("popupKeyboard") != "@xml/popup_template":
            raise AssertionError(f"iOS code {code}: missing popup_template")
        if key.get("popupCharacters") != popup_char:
            raise AssertionError(f"iOS code {code}: wrong popupCharacters")


EXPECTED_NORMAL = [
    ["1/˙", "2/ˊ", "3/ˇ", "4/ˋ", "7/ㄑ", "8/ㄢ", "9/ㄣ", "0/ㄤ", "-/ㄥ", "=/ㄦ"],
    ["q/ㄟ", "w/ㄝ", "e/一", "r/ㄜ", "t/ㄊ", "y/ㄡ", "u/ㄩ", "i/ㄞ", "o/ㄛ", "p/ㄆ"],
    ["a/ㄚ", "s/ㄙ", "d/ㄉ", "f/ㄈ", "g/ㄐ", "h/ㄏ", "j/ㄖ", "k/ㄎ", "l/ㄌ", ";/ㄗ"],
    ["z/ㄠ", "x/ㄨ", "c/ㄒ", "v/ㄍ", "b/ㄅ", "n/ㄋ", "m/ㄇ", ",/ㄓ", "./ㄔ", "//ㄕ"],
    ["KeyboardDown", "Shift", "EN", "Space", "'/ㄘ", "Delete", "Return"],
]

EXPECTED_SHIFT = [
    ["!/˙", "@/ˊ", "#/ˇ", "$/ˋ", "&", "*", "(", ")", "_", "+"],
    ["Q/ㄟ", "W/ㄝ", "E/一", "R/ㄜ", "T/ㄊ", "Y/ㄡ", "U/ㄩ", "I/ㄞ", "O/ㄛ", "P/ㄆ"],
    ["A/ㄚ", "S/ㄙ", "D/ㄉ", "F/ㄈ", "G/ㄐ", "H/ㄏ", "J/ㄖ", "K/ㄎ", "L/ㄌ", ":"],
    ["Z/ㄠ", "X/ㄨ", "C/ㄒ", "V/ㄍ", "B/ㄅ", "N/ㄋ", "M/ㄇ", "<", ">", "?"],
    ["KeyboardDown", "Shift", "EN", "Space", '"', "Delete", "Return"],
]


CHECKS = [
    ("Android normal", android_rows, "LimeStudio/app/src/main/res/xml/lime_et_41.xml", EXPECTED_NORMAL),
    ("Android shift", android_rows, "LimeStudio/app/src/main/res/xml/lime_et_41_shift.xml", EXPECTED_SHIFT),
    ("iOS normal", ios_rows, "LimeIME-iOS/LimeKeyboard/Layouts/lime_et_41.json", EXPECTED_NORMAL),
    ("iOS shift", ios_rows, "LimeIME-iOS/LimeKeyboard/Layouts/lime_et_41_shift.json", EXPECTED_SHIFT),
]


failed = False
for name, reader, path, expected in CHECKS:
    actual = reader(path)
    if actual != expected:
        failed = True
        print(f"\n{name} mismatch: {path}")
        print("expected:")
        for row in expected:
            print("  " + "  ".join(row))
        print("actual:")
        for row in actual:
            print("  " + "  ".join(row))

if failed:
    sys.exit(1)

try:
    verify_long_press_hints()
except AssertionError as error:
    print(error)
    sys.exit(1)

print("ET41 layout rows match the issue #137 plan.")

