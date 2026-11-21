"""Verify APK assembly ownership with synthetic fixtures; does not execute Android UI."""

from pathlib import Path
import subprocess
import sys
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[2]


def archive(path, entries):
    with zipfile.ZipFile(path, "w") as output:
        for name, data in entries.items():
            output.writestr(name, data)


def main():
    with tempfile.TemporaryDirectory(prefix="mindlink-compose-merge-") as directory:
        root = Path(directory)
        stage, ui, java, output = [root / name for name in ["stage.apk", "ui.apk", "java", "merged.apk"]]
        java.mkdir()
        (java / "classes.dex").write_bytes(b"java-one")
        (java / "classes2.dex").write_bytes(b"java-two")
        native = "lib/arm64-v8a/libandroidx.graphics.path.so"
        archive(stage, {"AndroidManifest.xml": b"old-manifest", "classes.dex": b"old-classes",
                        "resources.arsc": b"old-resources", "res/raw/old": b"old-path",
                        "assets/xposed_init": b"entry", "assets/dexopt/baseline.prof": b"old-profile",
                        native: b"same-native", "lib/arm64-v8a/libdexkit.so": b"host-lib"})
        ui_entries = {"AndroidManifest.xml": b"must-not-merge", "classes.dex": b"isolated-one",
                      "classes2.dex": b"isolated-two", "resources.arsc": b"single-final-table",
                      "res/raw/new": b"linked-path", "assets/mindlink/LICENSE.txt": b"local-notice",
                      "assets/dexopt/baseline.prof": b"ignore-ui-profile", native: b"same-native"}
        archive(ui, ui_entries)
        command = [sys.executable, "-I", str(ROOT / "scripts/merge.py"), str(stage), str(java), str(output), "--ui-apk", str(ui)]
        result = subprocess.run(command, capture_output=True, text=True)
        assert result.returncode == 0, result.stdout + result.stderr
        with zipfile.ZipFile(output) as merged:
            names = merged.namelist()
            assert len(names) == len(set(names))
            assert merged.read("AndroidManifest.xml") == b"old-manifest"
            assert merged.read("classes.dex") == b"old-classes"
            assert merged.read("classes2.dex") == b"java-one" and merged.read("classes3.dex") == b"java-two"
            assert merged.read("resources.arsc") == b"single-final-table" and "res/raw/old" not in names
            assert merged.read("assets/xposed_init") == b"entry"
            assert merged.read("assets/dexopt/baseline.prof") == b"old-profile"
            assert merged.read("lib/arm64-v8a/libdexkit.so") == b"host-lib"
            assert merged.read("assets/compose-payload/classes.bin") == b"isolated-one"
            assert merged.read("assets/compose-payload/classes2.bin") == b"isolated-two"
            assert merged.read("assets/mindlink/LICENSE.txt") == b"local-notice"
            for name in ["classes2.dex", "classes3.dex"]:
                assert merged.getinfo(name).compress_type == zipfile.ZIP_STORED
            assert merged.getinfo("assets/compose-payload/classes.bin").compress_type == zipfile.ZIP_DEFLATED
        ui_entries[native] = b"different-native"
        archive(ui, ui_entries)
        result = subprocess.run(command, capture_output=True, text=True)
        assert result.returncode != 0 and "Native library differs" in result.stderr + result.stdout
        assert output.exists(), "failed merge must preserve earlier output, not claim fresh candidate"
    print("PASS: APK assembly preserves host manifest/classes/assets/profile/JNI, uses one linked resource table, packs isolated payload and rejects JNI conflicts")


if __name__ == "__main__":
    main()
