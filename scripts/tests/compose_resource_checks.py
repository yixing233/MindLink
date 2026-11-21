"""Exercise resource gates using a historical transitive-R fixture, not Android UI."""

import importlib.util
from pathlib import Path
import sys
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("mindlink_resource_gate_test", ROOT / "scripts/check_compose_resources.py")
CHECK = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = CHECK
spec.loader.exec_module(CHECK)


def main():
    with tempfile.TemporaryDirectory(prefix="mindlink-resource-gates-") as temporary:
        path = Path(temporary) / "legacy.aar"
        with zipfile.ZipFile(path, "x") as archive:
            archive.writestr("R.txt", "int color old_transitive_name 0x7f060001\nint color own_color 0x7f060002\n")
            archive.writestr("res/values/values.xml", '<resources><color name="own_color">#ffffffff</color><declare-styleable name="Font"><attr name="fontStyle"><enum name="italic" value="1"/></attr></declare-styleable></resources>')
            archive.writestr("res/layout/example.xml", '<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android" android:id="@+id/actual_child"/>')
        names = CHECK.dependency_symbols({("legacy:test:1", path.name): path})
        assert ("color", "own_color") in names and ("color", "old_transitive_name") not in names
        assert names >= {("attr", "fontStyle"), ("id", "italic"), ("layout", "example"), ("id", "actual_child")}
        assert ("declare-styleable", "Font") not in names
        table = CHECK.ResourceTable({
            "color/own_color": CHECK.ResourceEntry(0x7f030001),
            "id/host_view": CHECK.ResourceEntry(0x7f090001),
        }, set(), set(), {0x7f: "dev.mindlink"})
        empty = {"classes": set(), "r_fields": [], "resource_field_refs": set(), "constants": set()}
        assert CHECK.validate_dex_resources(empty, table, table, "fixture") == (0, set())
        missing = dict(empty, resource_field_refs={("Llegacy/R$color;", "I", "old_transitive_name")})
        try:
            CHECK.validate_dex_resources(missing, table, table, "fixture")
        except ValueError as error:
            assert "no table entry" in str(error)
        else:
            raise AssertionError("actual executable missing R reference was accepted")
        invalid = dict(empty, r_fields=[("Llegacy/R$color;", "color", "own_color", 0x7f030002)])
        try:
            CHECK.validate_dex_resources(invalid, table, table, "fixture")
        except ValueError as error:
            assert "ID differs" in str(error)
        else:
            raise AssertionError("wrong compiled R ID was accepted")
        host_view = dict(empty, constants={0x7f090267})
        assert CHECK.validate_dex_resources(host_view, table, table, "fixture",
                                            allowed_missing={0x7f090267}) == (0, {0x7f090267})
        unknown_host = dict(empty, constants={0x7f090268})
        try:
            CHECK.validate_dex_resources(unknown_host, table, table, "fixture",
                                         allowed_missing={0x7f090267})
        except ValueError as error:
            assert "absent final resource entries" in str(error)
        else:
            raise AssertionError("unlisted host view constant was accepted")
    print("PASS: actual AAR definitions, historical transitive names and executable R-field/reference gates")


if __name__ == "__main__":
    main()
