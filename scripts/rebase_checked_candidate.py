"""Rebase a verified host/sidepanel candidate onto a later desktop-only installation.

Both APK identities and the earlier feature baseline are pinned. No source code is
compiled, rewritten or reverted; the installed desktop's original compressed
streams are copied intact, and all other verified candidate streams are retained.
"""

import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
CHECK_SPEC = importlib.util.spec_from_file_location("mindlink_rebase_resources", ROOT / "scripts/check_compose_resources.py")
CHECK = importlib.util.module_from_spec(CHECK_SPEC)
sys.modules[CHECK_SPEC.name] = CHECK
CHECK_SPEC.loader.exec_module(CHECK)
HELPER_SHA256 = "573aff0e87d9d9cb8f01f65fe553c50bb58fbf3d836d9d46503ece9e9994fa0e"
FEATURE_BASELINE_SHA256 = "ed9559aebf8ff83632f3d7461b53dfdcef9767ccaa19a35f35c1ceab2c9a38bc"
DESKTOP = "assets/compose-payload/"
NOTICE = "assets/mindlink/THIRD-PARTY-NOTICES.md"
APACHE = "assets/mindlink/APACHE-2.0.txt"


def sha(data):
    return hashlib.sha256(data).hexdigest()


def signature(name):
    return name.upper().startswith("META-INF/") and name.upper().endswith((".RSA", ".DSA", ".EC", ".SF", ".MF"))


def desktop_notice(data):
    text = data.decode("utf-8-sig")
    return text[text.index("## 二、桌面页面设计参考"):text.index("## 三、第三方库")]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ["candidate", "current", "feature-baseline", "helper", "output", "report"]:
        parser.add_argument("--" + name, required=True, type=Path)
    for name in ["candidate-sha256", "current-sha256"]:
        parser.add_argument("--" + name, required=True)
    args = parser.parse_args()
    for path, expected in [(args.candidate, args.candidate_sha256), (args.current, args.current_sha256),
                           (args.feature_baseline, FEATURE_BASELINE_SHA256), (args.helper, HELPER_SHA256)]:
        CHECK.require(re.fullmatch(r"[0-9a-f]{64}", expected) and CHECK.digest_file(path) == expected,
                      "Pinned rebase input identity differs: " + str(path))
    CHECK.require(not args.output.exists() and not args.report.exists(), "Rebase outputs must be new")
    CHECK.require(args.output.resolve() not in {args.candidate.resolve(), args.current.resolve(), args.feature_baseline.resolve()},
                  "Rebase must not overwrite an input")
    helper_spec = importlib.util.spec_from_file_location("mindlink_verified_raw_zip_copy", args.helper)
    helper = importlib.util.module_from_spec(helper_spec)
    helper_spec.loader.exec_module(helper)
    with CHECK.checked_zip(args.feature_baseline) as original, CHECK.checked_zip(args.current) as current, CHECK.checked_zip(args.candidate) as candidate:
        original_names, current_names, candidate_names = set(original.namelist()), set(current.namelist()), set(candidate.namelist())
        current_changes = []
        for name in original_names | current_names:
            if signature(name):
                continue
            if name in original_names & current_names and original.read(name) == current.read(name):
                continue
            CHECK.require(name.startswith(DESKTOP) or name in {NOTICE, APACHE},
                          "Later installation changed more than desktop/notices; a code/resource rebase is needed: " + name)
            current_changes.append(name)
        for name in ["classes.dex", "classes2.dex", "AndroidManifest.xml", "resources.arsc"]:
            CHECK.require(original.read(name) == current.read(name), "Current feature baseline bytes differ: " + name)
        CHECK.require(candidate.read("classes.dex") == current.read("classes.dex"), "Verified candidate primary differs")
        CHECK.require(candidate.read("AndroidManifest.xml") == current.read("AndroidManifest.xml"), "Verified candidate manifest differs")
        CHECK.require(desktop_notice(current.read(NOTICE)) == desktop_notice(candidate.read(NOTICE)),
                      "Candidate would lose the later desktop attribution section")
        if APACHE in current_names & candidate_names:
            CHECK.require(current.read(APACHE) == candidate.read(APACHE), "Apache license assets conflict")
        final_table = CHECK.read_resource_table(candidate.read("resources.arsc"))
        current_table = CHECK.read_resource_table(current.read("resources.arsc"))
        for symbol, resource_id in current_table.symbols.items():
            CHECK.require(final_table.symbols.get(symbol) == resource_id, "Current resource ID changed in candidate: " + symbol)
        index = current.read(DESKTOP + "index.txt").decode("ascii").splitlines()
        CHECK.require(index, "Current desktop index is empty")
        for position, line in enumerate(index, 1):
            name, expected = line.split(" ")
            CHECK.require(name == "classes" + (str(position) if position > 1 else "") + ".bin", "Current desktop index order differs")
            data = current.read(DESKTOP + name)
            CHECK.require(sha(data) == expected, "Current desktop DEX integrity differs")
            CHECK.validate_dex_resources(CHECK.inspect_dex(data), current_table, final_table, "Current desktop rebase")
        new_names = (candidate_names - {name for name in candidate_names if name.startswith(DESKTOP)}
                     | {name for name in current_names if name.startswith(DESKTOP)} | {APACHE})
        new_names = {name for name in new_names if not signature(name)}
        CHECK.require(current_names - {name for name in current_names if signature(name)} <= new_names,
                      "Rebased APK would remove a current baseline entry")
        temporary = args.output.with_name(args.output.name + ".partial")
        CHECK.require(not temporary.exists(), "Rebase temporary output exists")
        args.output.parent.mkdir(parents=True, exist_ok=True)
        copied = {}
        try:
            with zipfile.ZipFile(temporary, "x") as output:
                for name in sorted(new_names):
                    source = current if name.startswith(DESKTOP) or name == APACHE else candidate
                    CHECK.require(name in source.namelist(), "Chosen rebase entry is absent: " + name)
                    helper.copy_raw_entry(source, output, source.getinfo(name))
                    copied[name] = {"from": "current" if source is current else "candidate", "sha256": sha(source.read(name)),
                                    "compressed_bytes": source.getinfo(name).compress_size, "zip_method": source.getinfo(name).compress_type}
            with CHECK.checked_zip(temporary) as output:
                for name, record in copied.items():
                    source = current if record["from"] == "current" else candidate
                    CHECK.require(output.read(name) == source.read(name), "Rebase content differs: " + name)
                    CHECK.require(helper.raw_zip_member(output, output.getinfo(name))[1]
                                  == helper.raw_zip_member(source, source.getinfo(name))[1], "Rebase compressed stream differs: " + name)
                CHECK.require(output.read("classes2.dex") == candidate.read("classes2.dex"), "Verified feature DEX changed")
                CHECK.require({name: output.read(name) for name in new_names if name.startswith(DESKTOP)}
                              == {name: current.read(name) for name in current_names if name.startswith(DESKTOP)},
                              "Installed desktop payload changed")
            temporary.replace(args.output)
        finally:
            if temporary.exists():
                temporary.unlink()
    CHECK.write_report(args.report, {"candidate_sha256": args.candidate_sha256, "current_sha256": args.current_sha256,
                       "feature_baseline_sha256": FEATURE_BASELINE_SHA256, "current_desktop_only_changes": sorted(current_changes),
                       "output_sha256": CHECK.digest_file(args.output), "bytes": args.output.stat().st_size,
                       "desktop_original_compressed_streams_preserved": True, "verified_feature_dex_preserved": True,
                       "entries": copied, "device_installed": False, "source_modified": False})
    print("PASS: current installed desktop and original compressed streams preserved; verified host/sidepanel DEX retained")
    print("Unsigned rebased APK SHA-256: " + CHECK.digest_file(args.output))


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, KeyError, zipfile.BadZipFile) as error:
        print("FAIL: " + str(error), file=sys.stderr)
        raise SystemExit(1)
