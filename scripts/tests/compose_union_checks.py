"""Check union resource normalization and conflict handling without Android execution."""

import importlib.util
from pathlib import Path
import sys
import tempfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("mindlink_union_test_subject", ROOT / "scripts/prepare_compose_resources.py")
PREPARE = importlib.util.module_from_spec(spec)
spec.loader.exec_module(PREPARE)


def rejected(action, expected):
    try:
        action()
    except ValueError as error:
        assert expected in str(error), str(error)
    else:
        raise AssertionError("conflicting resource was accepted")


def main():
    assertions = 0
    for density in ["ldpi", "mdpi", "tvdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi", "nodpi", "anydpi", "280dpi"]:
        for version in ["", "-v1", "-v3", "-v4"]:
            assert PREPARE.canonical_folder("drawable-" + density + version) == "drawable-" + density + "-v4"
            assertions += 1
        assert PREPARE.canonical_folder("drawable-" + density + "-v21") == "drawable-" + density + "-v21"
        assertions += 1
    for folder in ["values", "values-zh-rCN", "drawable-v21", "layout-land-v17", "drawable-night-hdpi-v26"]:
        assert PREPARE.canonical_folder(folder) == folder
        assertions += 1
    assert PREPARE.canonical_folder("drawable-night-hdpi") == "drawable-night-hdpi-v4"
    assertions += 1
    rejected(lambda: PREPARE.canonical_folder("drawable-v4-hdpi"), "qualifier order")
    rejected(lambda: PREPARE.canonical_folder("drawable-hdpi-v4-v21"), "qualifier order")
    assertions += 2

    union = PREPARE.UnionResources()
    union.add_file("drawable-hdpi/notification.9.png", b"same-nine-patch", "desktop:core")
    union.add_file("drawable-hdpi-v4/notification.9.png", b"same-nine-patch", "sidepanel:core")
    assert len(union.files) == 1 and len(union.file_deduplications) == 1
    assert union.files["drawable-hdpi-v4", "notification"] == ("drawable-hdpi-v4/notification.9.png", b"same-nine-patch")
    assert union.symbols == {"drawable/notification"}
    assert union.qualifier_normalizations[0]["path"] == "drawable-hdpi/notification.9.png"
    assertions += 4
    rejected(lambda: union.add_file("drawable-hdpi/notification.9.png", b"different-nine-patch", "sidepanel:other"), "Resource file conflict")
    assert len(union.files) == 1
    union.add_file("drawable-hdpi-v21/notification.9.png", b"new-version", "sidepanel:core")
    assert len(union.files) == 2
    assertions += 3

    union.add_file("drawable-mdpi/vector.xml", b'<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dip" android:height="24dp"/>', "desktop:core")
    union.add_file("drawable-mdpi-v4/vector.xml", b'<vector xmlns:android="http://schemas.android.com/apk/res/android" android:height="24.0dp" android:width="24dp" />', "sidepanel:core")
    assert ("drawable-mdpi-v4", "vector") in union.files
    assertions += 1

    original = PREPARE.UnionResources({"drawable/notification", "color/accent"})
    original.add_file("drawable-hdpi/notification.png", b"library", "desktop:core")
    original.add_file("drawable-hdpi-v4/notification.png", b"original-app", "original")
    assert original.files["drawable-hdpi-v4", "notification"][1] == b"original-app"
    assert original.original_overlays[0]["symbol"] == "drawable/notification"
    assert original.original_overlays[0]["previous_sha256"] != original.original_overlays[0]["original_sha256"]
    assertions += 3
    rejected(lambda: original.add_file("drawable-hdpi/unknown.png", b"lib-one", "desktop:core") or original.add_file("drawable-hdpi-v4/unknown.png", b"app-two", "original"), "Resource file conflict")
    rejected(lambda: original.add_file("drawable-hdpi/notification.png", b"other-library", "sidepanel:other"), "Resource file conflict")
    assertions += 2

    original.add_file("values/colors.xml", b'<resources><color name="accent">#FFFFFF</color></resources>', "desktop:core")
    original.add_file("values/colors.xml", b'<resources><color name="accent">#ff0000</color></resources>', "original")
    assert PREPARE.canonical(original.values["values", "color", "accent"])[2] == "#ffff0000"
    assertions += 1
    values = PREPARE.UnionResources()
    values.add_file("values-hdpi/a.xml", b'<resources><integer name="count">1</integer></resources>', "desktop:core")
    values.add_file("values-hdpi-v4/b.xml", b'<resources><integer name="count">1.0</integer></resources>', "sidepanel:core")
    assert len(values.values) == 1
    assertions += 1
    rejected(lambda: values.add_file("values-hdpi-v4/c.xml", b'<resources><integer name="count">2</integer></resources>', "sidepanel:other"), "Resource value conflict")
    assertions += 1
    for path in ["../evil.png", "drawable/../evil.png", "drawable\\evil.png"]:
        rejected(lambda path=path: values.add_file(path, b"bad", "library"), "Invalid resource path")
        assertions += 1

    with tempfile.TemporaryDirectory(prefix="mindlink-union-check-") as temporary:
        output = Path(temporary)
        union.write(output)
        names = sorted(path.relative_to(output).as_posix() for path in output.rglob("*") if path.is_file())
        assert names == ["drawable-hdpi-v21/notification.9.png", "drawable-hdpi-v4/notification.9.png", "drawable-mdpi-v4/vector.xml"]
        assert (output / "drawable-hdpi-v4/notification.9.png").read_bytes() == b"same-nine-patch"
        assertions += 2
    with tempfile.TemporaryDirectory(prefix="mindlink-inline-attr-check-") as temporary:
        shared = PREPARE.UnionResources()
        body = b'<resources><declare-styleable name="Font"><attr name="fontStyle"><enum name="normal" value="0"/><enum name="italic" value="1"/></attr></declare-styleable></resources>'
        shared.add_file("values/a.xml", body, "desktop:font")
        shared.add_file("values/b.xml", body, "sidepanel:font")
        shared.add_file("values/attrs.xml", b'<resources><attr name="fontStyle"><enum name="normal" value="0"/><enum name="italic" value="1"/></attr></resources>', "original")
        for (_, kind, _), element in shared.values.items():
            if kind == "declare-styleable":
                for child in element:
                    name = child.get("name")
                    child.clear()
                    child.set("name", name)
        shared.write(Path(temporary))
        root = ET.parse(Path(temporary) / "values/mindlink_union.xml").getroot()
        assert not any(child.tag == "attr" and child.get("name") == "fontStyle" for child in root)
        assert len(shared.delegated_inline_attrs) == 1 and shared.delegated_inline_attrs[0]["symbol"] == "attr/fontStyle"
        assert shared.symbols >= {"attr/fontStyle", "id/normal", "id/italic"}
        assert root.find("declare-styleable/attr").get("name") == "fontStyle"
        assertions += 4
        rejected(lambda: shared.add_file("values/c.xml", body.replace(b'value="1"', b'value="2"'), "sidepanel:other"), "Resource attr member conflict")
        assertions += 1
    with tempfile.TemporaryDirectory(prefix="mindlink-one-graph-attr-check-") as temporary:
        partial = PREPARE.UnionResources()
        partial.add_file("values/a.xml", body, "desktop:font")
        partial.write(Path(temporary))
        root = ET.parse(Path(temporary) / "values/mindlink_union.xml").getroot()
        assert root.find("attr").get("name") == "fontStyle" and not partial.delegated_inline_attrs
        assertions += 1
    with tempfile.TemporaryDirectory(prefix="mindlink-stable-id-check-") as temporary:
        path = Path(temporary) / "stable.txt"
        path.write_text("dev.mindlink:id/owner = 0x7f06007a\ndev.mindlink:styleable/FontFamily = 0x7f0e0001\n", encoding="utf-8")
        assert len(PREPARE.CHECK.read_stable_ids(path)) == 2
        assert PREPARE.CHECK.table_stable_ids(path) == {"id/owner": 0x7f06007a}
        assertions += 2
        path.write_text("dev.mindlink:id/owner = 0x7f06007a\ndev.mindlink:string/missing = 0x7f0b0200\n", encoding="utf-8")
        assert PREPARE.CHECK.table_stable_ids(path) == {"id/owner": 0x7f06007a, "string/missing": 0x7f0b0200}
        assertions += 1
    print(f"PASS: resource union {assertions} assertions; implied density versions deduplicate, distinct versions survive, incompatible libraries fail and original public/inline definitions are audited")


if __name__ == "__main__":
    main()
