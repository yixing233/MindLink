"""Build a shared resource-only staging tree from original resources and two pinned graphs.

No dependency is installed or executed. AAR contents are read in place, not extracted
into the script directory. Both AGP links must use this same staging tree. Conflicting
simple values/files fail; compatible attrs, styles and styleables may add members.
"""

import argparse
import copy
from decimal import Decimal
import hashlib
import importlib.util
import json
from pathlib import Path, PurePosixPath
import re
import shutil
import sys
import xml.etree.ElementTree as ET
import zipfile


def load_checker():
    path = Path(__file__).with_name("check_compose_resources.py")
    spec = importlib.util.spec_from_file_location("mindlink_resource_checker", path)
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


CHECK = load_checker()


def normalized_scalar(value):
    value = value.strip()
    if re.fullmatch(r"#[0-9a-fA-F]{3,8}", value):
        digits = value[1:]
        if len(digits) in (3, 4):
            digits = "".join(char * 2 for char in digits)
        if len(digits) == 6:
            digits = "ff" + digits
        if len(digits) == 8:
            return "#" + digits.lower()
    dimension = re.fullmatch(r"([+-]?(?:\d+(?:\.\d*)?|\.\d+))(dip|dp|sp|px|pt|in|mm)", value)
    if dimension:
        return str(Decimal(dimension[1]).normalize()) + ("dp" if dimension[2] == "dip" else dimension[2])
    if re.fullmatch(r"[+-]?(?:\d+(?:\.\d*)?|\.\d+)", value):
        return str(Decimal(value).normalize())
    if value == "fill_parent":
        return "match_parent"
    if value.startswith("@+id/"):
        return "@id/" + value[len("@+id/"):]
    if value.startswith("?android:attr/"):
        return "?android:" + value[len("?android:attr/"):]
    if value.startswith("?attr/"):
        return "?" + value[len("?attr/"):]
    return value


def canonical(element):
    attributes = []
    for key, value in element.attrib.items():
        if key.startswith("{http://schemas.android.com/tools}"):
            continue
        if key.endswith("}pathData"):
            value = re.sub(r"\s+", " ", value).strip()
        attributes.append((key, normalized_scalar(value)))
    return (element.tag, tuple(sorted(attributes)), normalized_scalar(element.text or ""),
            tuple(canonical(child) for child in element))


def canonical_folder(folder):
    # AGP's DensityQualifier.since() is 4. Its resource merger treats an omitted
    # (or lower) version and explicit -v4 as the same density configuration.
    parts = folder.split("-")
    density = re.compile(r"(?:ldpi|mdpi|tvdpi|hdpi|xhdpi|xxhdpi|xxxhdpi|nodpi|anydpi|[1-9]\d*dpi)\Z")
    if not any(density.fullmatch(part) for part in parts[1:]):
        return folder
    versions = [index for index, part in enumerate(parts[1:], 1) if re.fullmatch(r"v[1-9]\d*", part)]
    CHECK.require(len(versions) <= 1 and (not versions or versions[0] == len(parts) - 1),
                  "Invalid version qualifier order: " + folder)
    version = int(parts[-1][1:]) if versions else 1
    if versions:
        parts.pop()
    return "-".join(parts + ["v" + str(max(4, version))])


def equivalent_file(relative, first, second):
    if first == second:
        return True
    if relative.endswith(".xml"):
        return canonical(ET.fromstring(first)) == canonical(ET.fromstring(second))
    return False


def attr_definition(element):
    attributes = dict(element.attrib)
    formats = set(attributes.pop("format", "").split("|")) - {""}
    name = attributes.pop("name", None)
    return name, attributes, formats, {child.get("name"): canonical(child) for child in element}


def merge_attr(existing, incoming, label):
    _, old_attrs, old_formats, old_children = attr_definition(existing)
    _, new_attrs, new_formats, new_children = attr_definition(incoming)
    for key in old_attrs.keys() & new_attrs.keys():
        CHECK.require(old_attrs[key] == new_attrs[key], "Resource attr conflict: " + label + " (" + key + ")")
    for key in old_children.keys() & new_children.keys():
        CHECK.require(old_children[key] == new_children[key], "Resource attr member conflict: " + label + "/" + str(key))
    CHECK.require(not old_formats or not new_formats or old_formats == new_formats or old_formats <= new_formats or new_formats <= old_formats,
                  "Resource attr format conflict: " + label)
    existing.attrib.update(new_attrs)
    if old_formats | new_formats:
        existing.set("format", "|".join(sorted(old_formats | new_formats)))
    for child in incoming:
        if child.get("name") not in old_children:
            existing.append(copy.deepcopy(child))


class UnionResources:
    def __init__(self, original_symbols=()):
        self.values = {}
        self.files = {}
        self.origins = {}
        self.attr_definitions = {}
        self.symbols = set()
        self.original_symbols = set(original_symbols)
        self.original_overlays = []
        self.qualifier_normalizations = []
        self.file_deduplications = []
        self.library_inline_attrs = {"desktop": {}, "sidepanel": {}}
        self.delegated_inline_attrs = []

    def original_overlay(self, symbol, origin, previous, old_data, new_data):
        if origin.startswith("original") and symbol in self.original_symbols:
            self.original_overlays.append({"symbol": symbol, "source": origin, "overrides": previous,
                                           "previous_sha256": hashlib.sha256(old_data).hexdigest(),
                                           "original_sha256": hashlib.sha256(new_data).hexdigest()})
            return True
        return False

    def add_file(self, relative, data, origin):
        path = PurePosixPath(relative)
        CHECK.require(len(path.parts) == 2 and all(part not in (".", "..") for part in path.parts) and "\\" not in relative,
                      "Invalid resource path: " + relative)
        folder, filename = path.parts
        original_relative = relative
        folder = canonical_folder(folder)
        relative = folder + "/" + filename
        if relative != original_relative:
            self.qualifier_normalizations.append({"source": origin, "path": original_relative,
                                                  "canonical_path": relative,
                                                  "sha256": hashlib.sha256(data).hexdigest()})
        if folder.startswith("values"):
            CHECK.require(filename.endswith(".xml"), "Non-XML values resource: " + relative)
            root = ET.fromstring(data)
            CHECK.require(root.tag == "resources", "Invalid values root: " + relative)
            for element in root:
                self.add_value(folder, element, origin + ":" + original_relative)
        else:
            kind = folder.split("-", 1)[0]
            name = filename[:-6] if filename.endswith(".9.png") else filename.rsplit(".", 1)[0]
            key = folder, name
            if key in self.files:
                old_relative, old_data = self.files[key]
                equal = old_relative == relative and equivalent_file(relative, old_data, data)
                if equal:
                    self.file_deduplications.append({"path": relative, "source": origin,
                                                     "kept_source": self.origins[key],
                                                     "sha256": hashlib.sha256(data).hexdigest(),
                                                     "kept_sha256": hashlib.sha256(old_data).hexdigest()})
                elif self.original_overlay(kind + "/" + name, origin, self.origins[key], old_data, data):
                    self.files[key], self.origins[key] = (relative, data), origin
                else:
                    raise ValueError("Resource file conflict: " + kind + "/" + name + " ("
                                     + self.origins[key] + " vs " + origin + ")")
            else:
                self.files[key], self.origins[key] = (relative, data), origin
            self.symbols.add(kind + "/" + name)

    def collect_attr(self, folder, element, origin):
        name = element.get("name")
        CHECK.require(name, "Unnamed attr resource")
        if name.startswith("android:"):
            return
        key = folder, name
        if key not in self.attr_definitions:
            self.attr_definitions[key] = copy.deepcopy(element)
        else:
            merge_attr(self.attr_definitions[key], element, "attr/" + name + " (" + origin + ")")
        self.symbols.add("attr/" + name)
        for child in element:
            if child.tag in ("enum", "flag") and child.get("name") and not child.get("name").startswith("android:"):
                self.symbols.add("id/" + child.get("name"))

    def add_value(self, folder, element, origin):
        tag = element.tag
        if tag in ("public", "public-group", "eat-comment", "skip", "overlayable"):
            # Original IDs are enforced by a stable ID manifest, not AAR public.xml.
            # AAR visibility/overlay policy is not a value definition in this app.
            return
        name = element.get("name")
        CHECK.require(name, "Unnamed resource in " + origin)
        if tag == "attr":
            self.collect_attr(folder, element, origin)
            return
        kind = element.get("type") if tag == "item" else ("array" if tag in ("string-array", "integer-array") else tag)
        CHECK.require(kind, "Untyped value resource in " + origin)
        key = folder, kind, name
        if tag == "declare-styleable":
            for child in element:
                CHECK.require(child.tag == "attr", "Invalid styleable member in " + origin)
                self.collect_attr(folder, child, origin)
                graph = origin.split(":", 1)[0]
                if graph in self.library_inline_attrs and len(child) and not child.get("name", "").startswith("android:"):
                    inline = self.library_inline_attrs[graph]
                    attr_key = folder, child.get("name")
                    if attr_key not in inline:
                        inline[attr_key] = copy.deepcopy(child), origin
                    else:
                        merge_attr(inline[attr_key][0], child, "inline attr/" + child.get("name"))
        if kind != "declare-styleable":
            self.symbols.add(kind + "/" + name)
        if key not in self.values:
            self.values[key], self.origins[key] = copy.deepcopy(element), origin
            return
        existing = self.values[key]
        if canonical(existing) == canonical(element):
            return
        if tag == "style" and kind + "/" + name in self.original_symbols and origin.startswith("original"):
            self.original_overlay(kind + "/" + name, origin, self.origins[key],
                                  ET.tostring(existing, encoding="utf-8"), ET.tostring(element, encoding="utf-8"))
            self.values[key], self.origins[key] = copy.deepcopy(element), origin
            return
        if tag in ("style", "declare-styleable") and existing.tag == tag:
            for attr in existing.attrib.keys() & element.attrib.keys():
                CHECK.require(existing.get(attr) == element.get(attr), "Resource " + tag + " conflict: " + name + " (" + attr + ")")
            existing.attrib.update(element.attrib)
            old_children = {child.get("name"): child for child in existing}
            for child in element:
                member = child.get("name")
                CHECK.require(member, "Unnamed resource member: " + name)
                if member not in old_children:
                    existing.append(copy.deepcopy(child))
                elif tag == "declare-styleable":
                    merge_attr(old_children[member], child, name + "/" + member)
                else:
                    CHECK.require(canonical(old_children[member]) == canonical(child),
                                  "Resource style member conflict: " + name + "/" + member)
            return
        if kind == "id" and not (existing.text or "").strip() and not (element.text or "").strip():
            return
        if self.original_overlay(kind + "/" + name, origin, self.origins[key],
                                 ET.tostring(existing, encoding="utf-8"), ET.tostring(element, encoding="utf-8")):
            self.values[key], self.origins[key] = copy.deepcopy(element), origin
            return
        raise ValueError("Resource value conflict: " + kind + "/" + name + " [" + folder + "] ("
                         + self.origins[key] + " vs " + origin + ")")

    def write(self, output):
        # A single canonical attr definition avoids duplicate enum bodies when
        # both a top-level attr and multiple styleables declare the same attr.
        for (folder, name), element in self.attr_definitions.items():
            definitions = [inline.get((folder, name)) for inline in self.library_inline_attrs.values()]
            if all(definition is not None and canonical(definition[0]) == canonical(element) for definition in definitions):
                # Both AGP links reintroduce this identical inline enum from their
                # own pinned AAR. A second top-level enum would be a duplicate value.
                self.delegated_inline_attrs.append({"symbol": "attr/" + name, "folder": folder,
                                                   "sha256": hashlib.sha256(ET.tostring(element, encoding="utf-8")).hexdigest(),
                                                   "sources": {graph: inline[folder, name][1]
                                                               for graph, inline in self.library_inline_attrs.items()}})
            else:
                self.values[folder, "attr", name] = element
        grouped = {}
        for (folder, kind, name), element in sorted(self.values.items()):
            grouped.setdefault(folder, []).append(element)
        for folder, elements in grouped.items():
            directory = output / folder
            directory.mkdir(parents=True, exist_ok=True)
            root = ET.Element("resources")
            root.extend(elements)
            ET.indent(root, space="    ")
            ET.ElementTree(root).write(directory / "mindlink_union.xml", encoding="utf-8", xml_declaration=True)
        for relative, data in self.files.values():
            path = output / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(data)


def add_directory(union, source, label):
    CHECK.require(source.is_dir() and not source.is_symlink(), "Resource source directory missing or linked: " + str(source))
    for path in sorted(source.rglob("*")):
        CHECK.require(not path.is_symlink(), "Resource source contains a symlink: " + str(path))
        if path.is_file():
            union.add_file(path.relative_to(source).as_posix(), path.read_bytes(), label)


def write_new(path, content):
    CHECK.require(not path.exists(), "Resource preparation output must be new: " + str(path))
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("x", encoding="utf-8", newline="\n") as stream:
        stream.write(content)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--desktop-res", action="append", type=Path, default=[])
    parser.add_argument("--sidepanel-res", action="append", type=Path, default=[])
    for name in ["inventory", "checksums", "sidepanel-inventory", "sidepanel-checksums", "public", "stable-ids-output", "manifest-output"]:
        parser.add_argument("--" + name, type=Path)
    parser.add_argument("--baseline-apk", type=Path)
    parser.add_argument("--baseline-sha256")
    parser.add_argument("--expected-public-count", type=int, default=371)
    args = parser.parse_args()
    temporary = None
    try:
        CHECK.require(not args.output.exists(), "Resource staging target must be new")
        source, output = args.source.resolve(), args.output.resolve()
        CHECK.require(source != output and source not in output.parents and output not in source.parents,
                      "Resource staging must not be inside or replace its source")
        graph_args = [args.inventory, args.checksums, args.sidepanel_inventory, args.sidepanel_checksums]
        if not any(graph_args):
            # Preserve the original one-runtime prepare command for existing callers.
            shutil.copytree(source, output)
            attrs = output / "values/attrs.xml"
            if attrs.exists():
                tree = ET.parse(attrs)
                duplicated = {"embeddingDividerType", "fontProviderFetchStrategy", "fontProviderFetchTimeout", "fontStyle"}
                for child in list(tree.getroot()):
                    if child.tag == "attr" and child.get("name") in duplicated:
                        tree.getroot().remove(child)
                tree.write(attrs, encoding="utf-8", xml_declaration=True)
            print("Prepared isolated original resource staging")
            return 0
        CHECK.require(all(graph_args) and args.public and args.stable_ids_output and args.manifest_output,
                      "Union staging requires both pinned inventories, original public, stable-ids-output and manifest-output")
        desktop, desktop_lock = CHECK.verify_inventory(args.inventory, args.checksums)
        sidepanel, side_lock = CHECK.verify_inventory(args.sidepanel_inventory, args.sidepanel_checksums)
        original = CHECK.read_public(args.public, args.expected_public_count)
        seed = dict(original)
        baseline_record = None
        CHECK.require(bool(args.baseline_apk) == bool(args.baseline_sha256), "Baseline APK and SHA-256 must be provided together")
        if args.baseline_apk:
            CHECK.require(re.fullmatch(r"[0-9a-f]{64}", args.baseline_sha256) is not None,
                          "Invalid expected baseline SHA-256")
            actual_hash = CHECK.digest_file(args.baseline_apk)
            CHECK.require(actual_hash == args.baseline_sha256, "Installed baseline APK SHA-256 differs")
            with CHECK.checked_zip(args.baseline_apk) as archive:
                baseline_table = CHECK.read_resource_table(archive.read("resources.arsc"))
            by_id = {resource_id: symbol for symbol, resource_id in seed.items()}
            for symbol, resource_id in baseline_table.symbols.items():
                CHECK.require(symbol not in seed or seed[symbol] == resource_id, "Baseline resource ID conflicts with original public: " + symbol)
                CHECK.require(resource_id not in by_id or by_id[resource_id] == symbol, "Baseline resource ID alias: " + symbol)
                seed[symbol], by_id[resource_id] = resource_id, symbol
            baseline_record = {"apk_sha256": actual_hash, "resource_symbols": baseline_table.symbols}
        union = UnionResources(original)
        for label, artifacts in [("desktop", desktop), ("sidepanel", sidepanel)]:
            for (coordinate, _), path in sorted(artifacts.items()):
                if path.suffix.lower() != ".aar":
                    continue
                # Shared bytes still belong to both graphs: retain each graph's
                # inline-attribute contract before deduplicating resource values.
                with CHECK.checked_zip(path) as archive:
                    for name in sorted(archive.namelist()):
                        if name.startswith("res/") and not name.endswith("/"):
                            union.add_file(name[4:], archive.read(name), label + ":" + coordinate)
        add_directory(union, source, "original")
        for directory in args.desktop_res:
            add_directory(union, directory, "desktop-app")
        for directory in args.sidepanel_res:
            add_directory(union, directory, "sidepanel-app")
        # All original entries must be material definitions, not public.xml-only placeholders.
        CHECK.require(seed.keys() <= union.symbols, "Baseline/public entries missing from union definitions: " + repr(sorted(seed.keys() - union.symbols)))
        # Strip inline attr bodies after collect_attr has checked and unioned them.
        for (_, kind, _), element in union.values.items():
            if kind == "declare-styleable":
                for child in element:
                    name = child.get("name")
                    child.clear()
                    child.set("name", name)
        temporary = output.with_name(output.name + ".partial")
        CHECK.require(not temporary.exists(), "Resource staging temporary target must be new")
        temporary.mkdir(parents=True)
        union.write(temporary)
        manifest = {"schema": 1, "package": CHECK.PACKAGE, "original_public_sha256": CHECK.digest_file(args.public),
                    "original_public_resources": len(original), "stable_seed_entries": len(seed),
                    "baseline": baseline_record, "desktop_lock": desktop_lock, "sidepanel_lock": side_lock,
                    "resource_symbols": sorted(union.symbols), "original_app_overlays": union.original_overlays,
                    "qualifier_normalizations": union.qualifier_normalizations,
                    "file_deduplications": union.file_deduplications,
                    "delegated_inline_attrs": union.delegated_inline_attrs,
                    "files": [{"path": path.relative_to(temporary).as_posix(), "sha256": CHECK.digest_file(path)}
                              for path in sorted(temporary.rglob("*")) if path.is_file()]}
        stable_text = "".join(f"{CHECK.PACKAGE}:{name} = {value:#010x}\n" for name, value in sorted(seed.items(), key=lambda item: item[1]))
        CHECK.require(not args.stable_ids_output.exists() and not args.manifest_output.exists(), "Stable IDs or staging manifest output already exists")
        temporary.replace(output)
        temporary = None
        write_new(args.stable_ids_output, stable_text)
        write_new(args.manifest_output, json.dumps(manifest, indent=2, sort_keys=True) + "\n")
        print(f"PASS: shared union staging {output}; {len(original)} original IDs, {len(union.symbols)} resource symbols, two pinned runtime locks")
        return 0
    except (ValueError, OSError, KeyError, UnicodeError, ET.ParseError, zipfile.BadZipFile) as error:
        print("FAIL: " + str(error), file=sys.stderr)
        return 1
    finally:
        if temporary is not None and temporary.exists():
            shutil.rmtree(temporary)


if __name__ == "__main__":
    raise SystemExit(main())
