"""Compress only verified sidepanel DEX assets, preserving every other ZIP stream.

The loader opens assets as InputStreams and checks decompressed DEX hashes. Root
DEX, desktop payloads, resources and notices are never recompressed or rewritten.
The output is unsigned and needs the original certificate before installation.
"""

import argparse
import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location("mindlink_recompression_resources", ROOT / "scripts/check_compose_resources.py")
CHECK = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = CHECK
SPEC.loader.exec_module(CHECK)
HELPER_SHA256 = "573aff0e87d9d9cb8f01f65fe553c50bb58fbf3d836d9d46503ece9e9994fa0e"
SIDEPANEL = "assets/sidepanel-compose-payload/"
DESKTOP = "assets/compose-payload/"


def require(condition, message):
    if not condition:
        raise ValueError(message)


def sha(data):
    return hashlib.sha256(data).hexdigest()


def signature(name):
    return name.upper().startswith("META-INF/") and name.upper().endswith((".RSA", ".DSA", ".EC", ".SF", ".MF"))


def load_helper(path):
    require(CHECK.digest_file(path) == HELPER_SHA256, "Verified raw ZIP helper identity differs")
    spec = importlib.util.spec_from_file_location("mindlink_recompression_raw_zip", path)
    helper = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(helper)
    return helper


def payload_entries(archive):
    names = set(archive.namelist())
    require(DESKTOP + "index.txt" in names, "Complete candidate has no desktop payload index")
    require(SIDEPANEL + "index.txt" in names, "Complete candidate has no sidepanel payload index")
    lines = archive.read(SIDEPANEL + "index.txt").decode("ascii").splitlines()
    require(lines, "Sidepanel DEX index is empty")
    targets, classes = set(), set()
    for position, line in enumerate(lines, 1):
        values = line.split(" ")
        require(len(values) == 2, "Malformed sidepanel DEX index")
        name, expected = values
        require(name == "classes" + (str(position) if position > 1 else "") + ".bin", "Sidepanel DEX index order differs")
        require(re.fullmatch(r"[0-9a-f]{64}", expected), "Invalid sidepanel DEX hash")
        path = SIDEPANEL + name
        info = archive.getinfo(path)
        require(info.compress_type in (zipfile.ZIP_STORED, zipfile.ZIP_DEFLATED), "Unsupported sidepanel asset compression")
        data = archive.read(path)
        require(sha(data) == expected, "Sidepanel DEX index/hash differs: " + name)
        defined = CHECK.inspect_dex(data)["classes"]
        require(not classes & defined, "Duplicate class within isolated sidepanel runtime")
        classes.update(defined)
        targets.add(path)
    require({name for name in names if name.startswith(SIDEPANEL)} == targets | {SIDEPANEL + "index.txt"},
            "Unindexed sidepanel payload entry")
    require({name for name in names if re.fullmatch(r"classes\d*\.dex", name)} == {"classes.dex", "classes2.dex"},
            "Complete candidate root DEX layout differs")
    for name in ("classes.dex", "classes2.dex"):
        require(archive.getinfo(name).compress_type == zipfile.ZIP_STORED, "Root DEX must remain STORED: " + name)
    return targets


def verify_recompression(old, new, targets, helper):
    old_names = {name for name in old.namelist() if not signature(name)}
    new_names = {name for name in new.namelist() if not signature(name)}
    require(old_names == new_names, "Recompression changed non-signature entry membership")
    require(old.comment == new.comment, "Recompression changed ZIP comment")
    require(payload_entries(new) == targets, "Recompression changed sidepanel DEX membership")
    entries, changed_streams = {}, []
    for name in sorted(old_names):
        before, after = old.getinfo(name), new.getinfo(name)
        original, actual = old.read(name), new.read(name)
        require(original == actual, "Recompression changed decompressed content: " + name)
        old_stream = helper.raw_zip_member(old, before)[1]
        new_stream = helper.raw_zip_member(new, after)[1]
        raw_equal = old_stream == new_stream
        if name in targets:
            require(after.compress_type == zipfile.ZIP_DEFLATED, "Sidepanel asset was not DEFLATED: " + name)
            require(after.compress_size < after.file_size, "Sidepanel DEX compression did not reduce its size: " + name)
        else:
            require(before.compress_type == after.compress_type and raw_equal,
                    "Non-target compression stream changed: " + name)
        if before.compress_type != after.compress_type or not raw_equal:
            changed_streams.append(name)
        entries[name] = {"sha256": sha(actual), "content_byte_exact": True,
                         "original_zip_method": before.compress_type, "zip_method": after.compress_type,
                         "original_compressed_bytes": before.compress_size, "compressed_bytes": after.compress_size,
                         "compressed_stream_byte_exact": raw_equal, "compressed_stream_sha256": sha(new_stream)}
    require(set(changed_streams) <= targets, "Recompression changed non-target ZIP streams")
    return entries, changed_streams


def recompress(args):
    require(re.fullmatch(r"[0-9a-f]{64}", args.candidate_sha256), "Pinned candidate SHA-256 is required")
    require(CHECK.digest_file(args.candidate) == args.candidate_sha256, "Pinned complete candidate identity differs")
    inputs = {args.candidate.resolve(), args.helper.resolve()}
    temporary = args.output.with_name(args.output.name + ".partial")
    report_temporary = args.report.with_name(args.report.name + ".partial")
    outputs = {args.output.resolve(), args.report.resolve(), temporary.resolve(), report_temporary.resolve()}
    require(len(outputs) == 4 and not inputs & outputs, "Recompression must not overwrite an input or another output")
    require(not any(path.exists() for path in (args.output, args.report, temporary, report_temporary)), "Recompression outputs must be new")
    helper = load_helper(args.helper)
    with CHECK.checked_zip(args.candidate) as candidate:
        targets = payload_entries(candidate)
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.report.parent.mkdir(parents=True, exist_ok=True)
        try:
            with zipfile.ZipFile(temporary, "x") as output:
                output.comment = candidate.comment
                for info in candidate.infolist():
                    if signature(info.filename):
                        continue
                    if info.filename in targets:
                        replacement = copy.copy(info)
                        replacement.compress_type = zipfile.ZIP_DEFLATED
                        output.writestr(replacement, candidate.read(info), compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)
                    else:
                        helper.copy_raw_entry(candidate, output, info)
            with CHECK.checked_zip(temporary) as output:
                entries, changed = verify_recompression(candidate, output, targets, helper)
            require(CHECK.digest_file(args.candidate) == args.candidate_sha256, "Candidate changed during recompression")
            require(CHECK.digest_file(args.helper) == HELPER_SHA256, "Raw ZIP helper changed during recompression")
            temporary.rename(args.output)
        finally:
            if temporary.exists():
                temporary.unlink()
    result = {"candidate": str(args.candidate), "candidate_sha256": args.candidate_sha256,
              "candidate_bytes": args.candidate.stat().st_size, "output": str(args.output),
              "output_sha256": CHECK.digest_file(args.output), "unsigned_bytes": args.output.stat().st_size,
              "changed_decompressed_entries": [], "changed_compressed_streams": changed,
              "all_non_target_compressed_streams_byte_exact": True,
              "desktop_original_compressed_streams_preserved": True, "sidepanel_index_and_dex_bytes_preserved": True,
              "original_sidepanel_compressed_bytes": sum(entries[name]["original_compressed_bytes"] for name in targets),
              "sidepanel_compressed_bytes": sum(entries[name]["compressed_bytes"] for name in targets),
              "entries": entries, "signed": False, "device_operations_performed": False,
              "device_acceptance_complete": False}
    CHECK.write_report(args.report, result)
    print("PASS: sidepanel DEX assets DEFLATED; all other content and original ZIP streams preserved")
    print(f"Unsigned bytes: {result['unsigned_bytes']}; SHA-256: {result['output_sha256']}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("candidate", "helper", "output", "report"):
        parser.add_argument("--" + name, required=True, type=Path)
    parser.add_argument("--candidate-sha256", required=True)
    args = parser.parse_args()
    try:
        recompress(args)
        return 0
    except (ValueError, AssertionError, OSError, KeyError, UnicodeError, zipfile.BadZipFile) as error:
        print("FAIL: " + str(error), file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
