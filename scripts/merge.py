"""Merge feature DEX and two isolated Compose APKs without moving AndroidX into root DEX.

The legacy --ui-apk-only command remains available. A --sidepanel-ui-apk merge
requires pinned inventories and verified common stable/emitted resource IDs; its
single final resources.arsc comes from the checked sidepanel union link.
"""

import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import sys
import zipfile


def load_checker():
    path = Path(__file__).with_name("check_compose_resources.py")
    spec = importlib.util.spec_from_file_location("mindlink_merge_resource_checker", path)
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


CHECK = load_checker()
PAYLOADS = ("compose-payload", "sidepanel-compose-payload")
# WeChat host view IDs are literals in the module's findViewById paths. They are not
# resources from the module package and therefore cannot appear in resources.arsc.
HOST_VIEW_RESOURCE_IDS = frozenset({
    0x7F090267, 0x7F093C64, 0x7F093D1F,
    0x7F095A10, 0x7F096D7C, 0x7F096D83,
})


def dex_order(name):
    match = re.fullmatch(r"classes(\d*)\.dex", name)
    if not match:
        raise ValueError("Unexpected DEX filename: " + name)
    return int(match[1] or "1")


def stored(name, data):
    item = zipfile.ZipInfo(name)
    item.compress_type = zipfile.ZIP_STORED
    return item, data


def deflated(name, data):
    item = zipfile.ZipInfo(name)
    item.compress_type = zipfile.ZIP_DEFLATED
    return item, data


def signature_entry(name):
    return name.upper().startswith("META-INF/") and name.upper().endswith((".RSA", ".DSA", ".EC", ".SF", ".MF"))


def copy_assets_and_native(entries, archive):
    for item in archive.infolist():
        name = item.filename
        if name.startswith("lib/") and not item.is_dir():
            data = archive.read(name)
            CHECK.require(name not in entries or entries[name][1] == data,
                          "Native library differs from existing runtime: " + name)
            if name not in entries:
                entries[name] = item, data
        elif name.startswith("assets/") and not item.is_dir():
            if name.startswith("assets/dexopt/"):
                continue
            CHECK.require(not any(name.startswith("assets/" + prefix + "/") for prefix in PAYLOADS),
                          "UI APK contains reserved payload assets: " + name)
            data = archive.read(name)
            CHECK.require(name not in entries or entries[name][1] == data, "Conflicting asset: " + name)
            if name not in entries:
                entries[name] = item, data


def add_payload(entries, archive, prefix):
    names = sorted((name for name in archive.namelist() if re.fullmatch(r"classes\d*\.dex", name)), key=dex_order)
    CHECK.require(names, ("Sidepanel" if prefix.startswith("sidepanel") else "UI") + " DEX payload absent")
    CHECK.require([dex_order(name) for name in names] == list(range(1, len(names) + 1)), "UI DEX numbering has gaps or aliases")
    payload = []
    for name in names:
        data = archive.read(name)
        CHECK.require(data, "Empty UI DEX payload: " + name)
        asset = "assets/" + prefix + "/" + name.replace(".dex", ".bin")
        CHECK.require(asset not in entries, "Payload asset would overwrite existing input: " + asset)
        # AssetManager.open returns decompressed bytes; the loader still verifies
        # the DEX hash before creating InMemoryDexClassLoader.
        entries[asset] = deflated(asset, data)
        payload.append((asset.rsplit("/", 1)[1], hashlib.sha256(data).hexdigest()))
    index = "assets/" + prefix + "/index.txt"
    CHECK.require(index not in entries, "Payload index would overwrite existing input: " + index)
    entries[index] = stored(index, "".join(f"{name} {digest}\n" for name, digest in payload).encode("ascii"))
    return payload


def verify_payloads(archive, prefixes):
    for prefix in prefixes:
        start = "assets/" + prefix + "/"
        index = start + "index.txt"
        CHECK.require(index in archive.namelist(), "Merged payload index absent: " + prefix)
        lines = archive.read(index).decode("ascii").splitlines()
        CHECK.require(lines, "Merged payload index empty: " + prefix)
        expected = set()
        for number, line in enumerate(lines, 1):
            match = re.fullmatch(r"(classes(?:[2-9]|[1-9][0-9]+)?\.bin) ([0-9a-f]{64})", line)
            CHECK.require(match is not None, "Malformed payload index: " + prefix)
            name, digest = match.groups()
            CHECK.require(dex_order(name.replace(".bin", ".dex")) == number, "Merged payload index order differs")
            name = start + name
            CHECK.require(name not in expected and name in archive.namelist(), "Payload entry absent or repeated: " + name)
            CHECK.require(hashlib.sha256(archive.read(name)).hexdigest() == digest, "Payload SHA-256 differs: " + name)
            CHECK.require(archive.getinfo(name).compress_type == zipfile.ZIP_DEFLATED,
                          "Payload DEX asset is not DEFLATED")
            expected.add(name)
        CHECK.require({name for name in archive.namelist() if name.startswith(start)} == expected | {index}, "Unexpected payload asset: " + prefix)


def merge(args):
    sidepanel_only = args.sidepanel_ui_apk is not None and args.ui_apk is None
    dual = args.sidepanel_ui_apk is not None and args.ui_apk is not None
    input_paths = [args.stage, args.java, args.ui_apk, args.sidepanel_ui_apk, args.public, args.stable_ids,
                   args.desktop_emitted_ids, args.sidepanel_emitted_ids, args.inventory, args.checksums,
                   args.sidepanel_inventory, args.sidepanel_checksums, args.staging_manifest]
    CHECK.require(args.output.resolve() not in {path.resolve() for path in input_paths if path}, "Output must not overwrite an input")
    if args.java.is_dir():
        java_paths = sorted(args.java.glob("classes*.dex"), key=lambda path: dex_order(path.name))
    else:
        java_paths = [args.java] if args.java.is_file() else []
    CHECK.require(java_paths, "Java DEX missing")
    CHECK.require(args.output.resolve() not in {path.resolve() for path in java_paths}, "Output must not overwrite Java DEX")
    report, desktop_table, final_table = None, None, None
    if dual:
        CHECK.require(all([args.ui_apk, args.public, args.stable_ids, args.desktop_emitted_ids,
                          args.sidepanel_emitted_ids, args.inventory, args.checksums,
                          args.sidepanel_inventory, args.sidepanel_checksums]),
                      "Two payloads require both APKs, original public resources, common stable IDs, both emitted IDs and pinned runtime inventories")
        CHECK.require(len(java_paths) == 1 and java_paths[0].name == "classes.dex", "Feature DEX must be one classes.dex; root only primary + classes2.dex")
        report, desktop_table, final_table = CHECK.validate_linked_resources(args.ui_apk, args.sidepanel_ui_apk, args.public,
                args.stable_ids, args.desktop_emitted_ids, args.sidepanel_emitted_ids, args.inventory, args.checksums,
                args.sidepanel_inventory, args.sidepanel_checksums, args.aapt2, args.stage, args.staging_manifest,
                args.expected_public_count)
    elif sidepanel_only:
        CHECK.require(all([args.public, args.stable_ids, args.sidepanel_emitted_ids, args.inventory, args.checksums,
                           args.sidepanel_inventory, args.sidepanel_checksums]),
                      "Sidepanel-only packaging requires the original public resources, common stable IDs, emitted IDs and pinned runtime inventories")
        CHECK.require(len(java_paths) == 1 and java_paths[0].name == "classes.dex", "Feature DEX must be one classes.dex; root only primary + classes2.dex")
        report, _, final_table = CHECK.validate_linked_resources(None, args.sidepanel_ui_apk, args.public,
                args.stable_ids, None, args.sidepanel_emitted_ids, args.inventory, args.checksums,
                args.sidepanel_inventory, args.sidepanel_checksums, args.aapt2, args.stage, args.staging_manifest,
                args.expected_public_count)
    entries = {}
    with CHECK.checked_zip(args.stage) as stage:
        for item in stage.infolist():
            entries[item.filename] = item, stage.read(item.filename)
    root_dex = {name for name in entries if re.fullmatch(r"classes\d*\.dex", name)}
    if dual or sidepanel_only:
        CHECK.require(root_dex == {"classes.dex"}, "Stage root must contain only primary classes.dex")
        primary_table = CHECK.read_resource_table(entries["resources.arsc"][1])
        for symbol, resource_id in primary_table.symbols.items():
            CHECK.require(final_table.symbols.get(symbol) == resource_id, "Primary resource ID changed in final union: " + symbol)
        primary_dex = CHECK.inspect_dex(entries["classes.dex"][1])
        feature_dex = CHECK.inspect_dex(java_paths[0].read_bytes())
        CHECK.require(not primary_dex["classes"] & feature_dex["classes"], "Duplicate classes across primary/feature root DEX")
        forbidden = ("Landroidx/", "Lorg/jetbrains/compose/", "Ltop/yukonga/miuix/", "Landroid/", "Lde/robv/android/xposed/", "Lcom/tencent/mm/")
        CHECK.require(not any(name.startswith(forbidden) for name in feature_dex["classes"]), "Runtime AndroidX/Compose or compile/host stubs leaked into feature root DEX")
        CHECK.validate_dex_resources(primary_dex, primary_table, final_table, "Primary")
        CHECK.validate_dex_resources(feature_dex, final_table, final_table, "Feature",
                                     allowed_missing=HOST_VIEW_RESOURCE_IDS)
        for prefix in PAYLOADS:
            CHECK.require(not any(name.startswith("assets/" + prefix + "/") for name in entries), "Stage has a stale isolated payload: " + prefix)
    next_dex = max((dex_order(name) for name in root_dex), default=0) + 1
    for path in java_paths:
        name = f"classes{next_dex if next_dex != 1 else ''}.dex"
        entries[name] = stored(name, path.read_bytes())
        next_dex += 1
    payloads = {}
    if args.ui_apk:
        with CHECK.checked_zip(args.ui_apk) as desktop:
            if not dual:
                # Legacy single-runtime assembly retains its original ownership semantics.
                for name in list(entries):
                    if name == "resources.arsc" or name.startswith("res/"):
                        del entries[name]
                for item in desktop.infolist():
                    if item.filename == "resources.arsc" or item.filename.startswith("res/"):
                        entries[item.filename] = item, desktop.read(item.filename)
            payloads[PAYLOADS[0]] = add_payload(entries, desktop, PAYLOADS[0])
            copy_assets_and_native(entries, desktop)
    if dual or sidepanel_only:
        with CHECK.checked_zip(args.sidepanel_ui_apk) as sidepanel:
            # Never concatenate two tables or take one on trust: full validation ran
            # above, including original 371, both dependency graphs, R fields, file
            # entries, overlap IDs and configuration values.
            for item in sidepanel.infolist():
                if item.filename == "resources.arsc":
                    entries[item.filename] = stored(item.filename, sidepanel.read(item.filename))
                elif item.filename.startswith("res/"):
                    entries[item.filename] = item, sidepanel.read(item.filename)
            payloads[PAYLOADS[1]] = add_payload(entries, sidepanel, PAYLOADS[1])
            copy_assets_and_native(entries, sidepanel)
        asset = "assets/compose-runtime-inventory.json"
        CHECK.require(asset not in entries, "Reserved runtime inventory asset conflict")
        entries[asset] = stored(asset, (json.dumps(report, indent=2, sort_keys=True) + "\n").encode("utf-8"))
    temporary = args.output.with_name(args.output.name + ".partial")
    CHECK.require(not temporary.exists(), "Merge temporary output already exists")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    try:
        with zipfile.ZipFile(temporary, "x") as output:
            for name, (item, data) in entries.items():
                if not signature_entry(name):
                    if name.startswith(tuple("assets/" + prefix + "/" for prefix in PAYLOADS)) \
                            and name.endswith(".bin"):
                        output.writestr(item, data, compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)
                    else:
                        output.writestr(item, data)
        with CHECK.checked_zip(temporary) as output:
            verify_payloads(output, payloads)
            if dual or sidepanel_only:
                CHECK.require({name for name in output.namelist() if re.fullmatch(r"classes\d*\.dex", name)} == {"classes.dex", "classes2.dex"},
                              "Merged root must contain exactly primary + feature classes2.dex")
                CHECK.require(output.read("AndroidManifest.xml") == entries["AndroidManifest.xml"][1], "Primary manifest bytes changed")
                merged_table = CHECK.read_resource_table(output.read("resources.arsc"))
                CHECK.require(merged_table.symbols == final_table.symbols, "Merged final resource table differs")
                CHECK.require(final_table.files <= set(output.namelist()), "Final resource file entry missing after merge")
        temporary.replace(args.output)
    finally:
        if temporary.exists():
            temporary.unlink()
    if args.report:
        CHECK.write_report(args.report, {"apk_sha256": CHECK.digest_file(args.output), "payloads": payloads, "resource_check": report})
    print("PASS: root feature DEX merged; isolated payloads " + ", ".join(payloads or ["none"]))
    return 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ["stage", "java", "output"]:
        parser.add_argument(name, type=Path)
    for name in ["ui-apk", "sidepanel-ui-apk", "public", "stable-ids", "desktop-emitted-ids", "sidepanel-emitted-ids",
                 "inventory", "checksums", "sidepanel-inventory", "sidepanel-checksums", "aapt2", "staging-manifest", "report"]:
        parser.add_argument("--" + name, type=Path)
    parser.add_argument("--expected-public-count", type=int, default=371)
    args = parser.parse_args()
    try:
        return merge(args)
    except (ValueError, OSError, KeyError, UnicodeError, zipfile.BadZipFile) as error:
        print("FAIL: " + str(error), file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
