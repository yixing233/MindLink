"""Fail-closed checks for two independently linked Compose runtimes and one resource table.

The AndroidX graphs stay in their own DEX assets. Resource IDs, unlike classes, are
process-wide: both final links must use the same full --stable-ids file. This module
also exposes the binary readers used by merge.py and offline synthetic tests.
"""

import argparse
from dataclasses import dataclass, field
import hashlib
import json
from pathlib import Path
import re
import struct
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile
import zlib


PACKAGE = "dev.mindlink"
PACKAGE_ID = 0x7F
ANDROID_NS = "http://schemas.android.com/apk/res/android"
DEX_NAME = re.compile(r"classes(?:[2-9]|[1-9][0-9]+)?\.dex\Z")


def require(condition, message):
    if not condition:
        raise ValueError(message)


def digest_file(path):
    with Path(path).open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def read_public(path, expected_count=None):
    root = ET.parse(path).getroot()
    require(root.tag == "resources", "Invalid original public resource manifest")
    result, ids = {}, {}
    for item in root:
        require(item.tag == "public", "Unexpected element in original public resources")
        kind, name, value = item.get("type"), item.get("name"), item.get("id")
        require(kind and name and value, "Incomplete original public resource")
        resource_id = int(value, 0)
        require(resource_id >> 24 == PACKAGE_ID and (resource_id >> 16) & 0xFF,
                "Invalid original resource ID: " + kind + "/" + name)
        symbol = kind + "/" + name
        require(symbol not in result and resource_id not in ids, "Duplicate original public resource: " + symbol)
        result[symbol], ids[resource_id] = resource_id, symbol
    require(result, "Original public resources are empty")
    if expected_count is not None:
        require(len(result) == expected_count, f"Expected {expected_count} original public resources, found {len(result)}")
    return result


def read_stable_ids(path):
    result, ids = {}, {}
    for number, line in enumerate(Path(path).read_text(encoding="utf-8-sig").splitlines(), 1):
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        match = re.fullmatch(r"([^\s=]+)\s*=\s*(0x[0-9a-fA-F]{8})", line)
        if match:
            symbol, value = match.groups()
        else:
            match = re.fullmatch(r"(0x[0-9a-fA-F]{8})\s+([^\s=]+)", line)
            require(match is not None, f"Invalid stable ID line {number}")
            value, symbol = match.groups()
        if ":" in symbol:
            package, symbol = symbol.split(":", 1)
            require(package == PACKAGE, "Stable ID belongs to another package: " + package)
        require(re.fullmatch(r"[^/\s]+/[^/\s]+", symbol), "Invalid stable resource name: " + symbol)
        resource_id = int(value, 16)
        require(resource_id >> 24 == PACKAGE_ID and (resource_id >> 16) & 0xFF, "Invalid stable resource ID: " + symbol)
        require(symbol not in result, "Duplicate stable resource name: " + symbol)
        require(resource_id not in ids, "Stable resource ID alias: " + symbol)
        result[symbol], ids[resource_id] = resource_id, symbol
    require(result, "Stable ID manifest is empty")
    return result


def table_stable_ids(path):
    # AAPT also emits synthetic styleable IDs. Styleables compile to R arrays and
    # indices, not resources.arsc entries; their actual attr references are checked in DEX.
    return {symbol: resource_id for symbol, resource_id in read_stable_ids(path).items()
            if not symbol.startswith("styleable/")}


def verify_inventory(inventory, checksums):
    """Identical duplicate resolution lines are legal; different artifacts are not."""
    records = json.loads(Path(checksums).read_text(encoding="utf-8-sig"))
    require(isinstance(records, list) and records, "Runtime checksum lock is empty or invalid")
    expected = {}
    for item in records:
        require(isinstance(item, dict) and set(item) >= {"coordinate", "file", "sha256"}, "Incomplete runtime checksum lock")
        coordinate, name, digest = item["coordinate"], item["file"], item["sha256"]
        require(isinstance(coordinate, str) and len(coordinate.split(":")) == 3, "Invalid locked runtime coordinate")
        require(isinstance(name, str) and name and "/" not in name and "\\" not in name, "Invalid locked artifact filename")
        require(isinstance(digest, str) and re.fullmatch(r"[0-9a-f]{64}", digest), "Invalid locked artifact SHA-256")
        key = coordinate, name
        require(key not in expected, "Duplicate artifact in runtime checksum lock: " + coordinate)
        expected[key] = digest
    actual, artifacts, duplicate_lines = {}, {}, 0
    for number, line in enumerate(Path(inventory).read_text(encoding="utf-8-sig").splitlines(), 1):
        require(line and len(line.split("\t")) == 2, f"Invalid runtime inventory line {number}")
        coordinate, filename = line.split("\t")
        path = Path(filename)
        require(path.is_absolute() and path.is_file(), "Runtime artifact missing: " + coordinate)
        key, digest = (coordinate, path.name), digest_file(path)
        if key in actual:
            require(actual[key] == digest and artifacts[key].resolve() == path.resolve(),
                    "Conflicting duplicate runtime artifact: " + coordinate)
            duplicate_lines += 1
        actual[key], artifacts[key] = digest, path
    require(actual, "Runtime inventory is empty")
    if actual != expected:
        missing = sorted(coordinate + "/" + name for coordinate, name in expected.keys() - actual.keys())
        extra = sorted(coordinate + "/" + name for coordinate, name in actual.keys() - expected.keys())
        changed = sorted(coordinate + "/" + name for coordinate, name in actual.keys() & expected.keys()
                         if actual[coordinate, name] != expected[coordinate, name])
        raise ValueError("Resolved runtime graph differs from pinned checksums; missing=" + repr(missing)
                         + "; extra=" + repr(extra) + "; changed=" + repr(changed))
    report = {"inventory_sha256": digest_file(inventory), "checksums_sha256": digest_file(checksums),
              "identical_duplicate_lines": duplicate_lines,
              "artifacts": [{"coordinate": coordinate, "file": name, "sha256": actual[coordinate, name]}
                            for coordinate, name in sorted(actual)]}
    return artifacts, report


def unpack(data, fmt, offset, end=None):
    size = struct.calcsize(fmt)
    require(offset >= 0 and offset + size <= (len(data) if end is None else end), "Truncated Android binary structure")
    return struct.unpack_from(fmt, data, offset)


def chunks(data, start, end):
    position = start
    while position < end:
        kind, header, size = unpack(data, "<HHI", position, end)
        require(header >= 8 and size >= header and size % 4 == 0 and position + size <= end,
                "Invalid Android resource chunk")
        yield position, kind, header, size
        position += size
    require(position == end, "Misaligned Android resource chunks")


def string_pool(data, offset):
    kind, header, size = unpack(data, "<HHI", offset)
    require(kind == 1 and header >= 28 and offset + size <= len(data), "Invalid Android string pool")
    count, styles, flags, strings_start, _ = unpack(data, "<IIIII", offset + 8, offset + header)
    require(header + (count + styles) * 4 <= size and strings_start < size, "Invalid Android string offsets")
    result = []

    def length8(position):
        value = unpack(data, "<B", position, offset + size)[0]
        if value & 0x80:
            return ((value & 0x7F) << 8) | unpack(data, "<B", position + 1, offset + size)[0], position + 2
        return value, position + 1

    def length16(position):
        value = unpack(data, "<H", position, offset + size)[0]
        if value & 0x8000:
            return ((value & 0x7FFF) << 16) | unpack(data, "<H", position + 2, offset + size)[0], position + 4
        return value, position + 2

    for index in range(count):
        relative = unpack(data, "<I", offset + header + index * 4, offset + size)[0]
        position = offset + strings_start + relative
        if flags & 0x100:
            _, position = length8(position)
            length, position = length8(position)
            require(position + length < offset + size and data[position + length] == 0, "Invalid UTF-8 resource string")
            result.append(data[position:position + length].decode("utf-8"))
        else:
            length, position = length16(position)
            require(position + length * 2 + 2 <= offset + size and data[position + length * 2:position + length * 2 + 2] == b"\0\0",
                    "Invalid UTF-16 resource string")
            result.append(data[position:position + length * 2].decode("utf-16-le"))
    return result


@dataclass
class ResourceEntry:
    resource_id: int
    configs: dict = field(default_factory=dict)


@dataclass
class ResourceTable:
    entries: dict
    references: set
    files: set
    packages: dict

    @property
    def symbols(self):
        return {name: entry.resource_id for name, entry in self.entries.items()}

    @property
    def ids(self):
        return set(self.symbols.values())


def read_resource_table(data):
    kind, header, size = unpack(data, "<HHI", 0)
    require(kind == 2 and header >= 12 and size == len(data), "Invalid resources.arsc table")
    package_count = unpack(data, "<I", 8, header)[0]
    global_strings, package_chunks = None, []
    for offset, kind, child_header, child_size in chunks(data, header, size):
        if kind == 1:
            require(global_strings is None, "Duplicate global resource string pool")
            global_strings = string_pool(data, offset)
        elif kind == 0x200:
            package_chunks.append((offset, child_header, child_size))
        else:
            raise ValueError(f"Unsupported top-level resource chunk: {kind:#x}")
    require(global_strings is not None and len(package_chunks) == package_count, "Resource package count mismatch")
    entries, by_id, references, files, packages = {}, {}, set(), set(), {}

    def reference(value):
        if value and value >> 24 not in (0, 1):
            references.add(value)

    def value_at(position, end):
        value_size, zero, value_type, value = unpack(data, "<HBBI", position, end)
        require(value_size == 8 and zero == 0, "Invalid Android resource value")
        if value_type == 3:
            require(value < len(global_strings), "Invalid resource string index")
            value = global_strings[value]
            if value.startswith("res/"):
                files.add(value)
        elif value_type in (1, 2, 7, 8):
            reference(value)
        return value_type, value

    for package_offset, package_header, package_size in package_chunks:
        require(package_header >= 284, "Invalid resource package header")
        package_end = package_offset + package_size
        package_id = unpack(data, "<I", package_offset + 8, package_end)[0]
        name = data[package_offset + 12:package_offset + 268].decode("utf-16-le").split("\0", 1)[0]
        require(package_id not in packages and package_id == PACKAGE_ID and name == PACKAGE,
                "Resource table package must remain dev.mindlink (0x7f)")
        packages[package_id] = name
        type_offset = unpack(data, "<I", package_offset + 268, package_end)[0]
        key_offset = unpack(data, "<I", package_offset + 276, package_end)[0]
        type_id_offset = unpack(data, "<I", package_offset + 284, package_end)[0] if package_header >= 288 else 0
        types, keys = string_pool(data, package_offset + type_offset), string_pool(data, package_offset + key_offset)
        for offset, child_kind, child_header, child_size in chunks(data, package_offset + package_header, package_end):
            if child_kind in (1, 0x202):
                continue
            require(child_kind == 0x201, f"Unsupported resource package chunk: {child_kind:#x}")
            require(child_header >= 24, "Invalid resource type header")
            type_id, flags, reserved, count, entries_start = unpack(data, "<BBHII", offset + 8, offset + child_header)
            require(0 < type_id <= len(types) and reserved == 0 and not flags & ~3, "Invalid resource type")
            config_size = unpack(data, "<I", offset + 20, offset + child_header)[0]
            require(config_size >= 4 and 20 + config_size <= child_header, "Invalid resource configuration")
            config = data[offset + 24:offset + 20 + config_size].rstrip(b"\0")
            entry_end = offset + child_size
            if flags & 1:
                require(not flags & 2 and child_header + count * 4 <= entries_start, "Invalid sparse resource entries")
                offsets = [unpack(data, "<HH", offset + child_header + index * 4, entry_end) for index in range(count)]
                offsets = [(index, relative * 4) for index, relative in offsets]
            elif flags & 2:
                require(child_header + count * 2 <= entries_start, "Invalid 16-bit resource entries")
                offsets = [(index, unpack(data, "<H", offset + child_header + index * 2, entry_end)[0]) for index in range(count)]
                offsets = [(index, relative * 4) for index, relative in offsets if relative != 0xFFFF]
            else:
                require(child_header + count * 4 <= entries_start, "Invalid resource entry offsets")
                offsets = [(index, unpack(data, "<I", offset + child_header + index * 4, entry_end)[0]) for index in range(count)]
                offsets = [(index, relative) for index, relative in offsets if relative != 0xFFFFFFFF]
            for index, relative in offsets:
                position = offset + entries_start + relative
                entry_size, entry_flags, key = unpack(data, "<HHI", position, entry_end)
                require(entry_size >= 8 and key < len(keys) and not entry_flags & ~7, "Unsupported or invalid resource entry")
                symbol = types[type_id - 1] + "/" + keys[key]
                resource_id = (package_id << 24) | ((type_id + type_id_offset) << 16) | index
                require(index <= 0xFFFF and type_id + type_id_offset <= 0xFF, "Resource ID overflow")
                require(resource_id not in by_id or by_id[resource_id] == symbol, "Resource ID names differ across configurations")
                require(symbol not in entries or entries[symbol].resource_id == resource_id, "Resource has more than one ID: " + symbol)
                by_id[resource_id] = symbol
                entry = entries.setdefault(symbol, ResourceEntry(resource_id))
                if entry_flags & 1:
                    require(entry_size >= 16, "Invalid resource bag header")
                    parent, map_count = unpack(data, "<II", position + 8, entry_end)
                    reference(parent)
                    maps = []
                    for map_index in range(map_count):
                        map_position = position + entry_size + map_index * 12
                        key_id = unpack(data, "<I", map_position, entry_end)[0]
                        reference(key_id)
                        maps.append((key_id, value_at(map_position + 4, entry_end)))
                    require(len({key_id for key_id, _ in maps}) == len(maps), "Duplicate resource bag member: " + symbol)
                    value = ("bag", parent, tuple(sorted(maps)))
                else:
                    value = ("value", value_at(position + entry_size, entry_end))
                require(config not in entry.configs, "Duplicate resource configuration: " + symbol)
                entry.configs[config] = value
    require(entries and packages == {PACKAGE_ID: PACKAGE}, "Linked resource table is empty or has another package")
    return ResourceTable(entries, references, files, packages)


def read_binary_xml(data):
    """Return a manifest-compatible tree and typed resource references, not guessed hex colors."""
    if data.lstrip().startswith(b"<"):
        return ET.fromstring(data), set()
    kind, header, size = unpack(data, "<HHI", 0)
    require(kind == 3 and size == len(data), "Invalid compiled XML")
    strings, root, stack, references = None, None, [], set()

    def string(index):
        require(strings is not None and index < len(strings), "Invalid compiled XML string index")
        return strings[index]

    def typed_value(position, end):
        value_size, zero, value_type, value = unpack(data, "<HBBI", position, end)
        require(value_size == 8 and zero == 0, "Invalid compiled XML typed value")
        if value_type in (1, 2, 7, 8) and value and value >> 24 not in (0, 1):
            references.add(value)
        return string(value) if value_type == 3 else str(value)

    for offset, child_kind, child_header, child_size in chunks(data, header, size):
        end = offset + child_size
        if child_kind == 1:
            require(strings is None, "Duplicate compiled XML string pool")
            strings = string_pool(data, offset)
        elif child_kind == 0x180:
            for position in range(offset + child_header, end, 4):
                value = unpack(data, "<I", position, end)[0]
                if value and value >> 24 not in (0, 1):
                    references.add(value)
        elif child_kind == 0x102:
            require(child_header >= 16, "Invalid compiled XML node header")
            namespace, name, start, attr_size, count, _, _, _ = unpack(data, "<IIHHHHHH", offset + child_header, end)
            require(attr_size >= 20, "Invalid compiled XML attribute size")
            tag = string(name) if namespace == 0xFFFFFFFF else "{" + string(namespace) + "}" + string(name)
            element = ET.Element(tag)
            for index in range(count):
                position = offset + child_header + start + index * attr_size
                ns, attr_name, raw = unpack(data, "<III", position, end)
                value = typed_value(position + 12, end)
                key = string(attr_name) if ns == 0xFFFFFFFF else "{" + string(ns) + "}" + string(attr_name)
                require(key not in element.attrib, "Duplicate compiled XML attribute")
                element.set(key, string(raw) if raw != 0xFFFFFFFF else value)
            if stack:
                stack[-1].append(element)
            else:
                require(root is None, "Multiple compiled XML roots")
                root = element
            stack.append(element)
        elif child_kind == 0x103:
            require(stack, "Unbalanced compiled XML end tag")
            _, name = unpack(data, "<II", offset + child_header, end)
            require(stack[-1].tag.split("}")[-1] == string(name), "Compiled XML end tag differs")
            stack.pop()
        elif child_kind == 0x104:
            typed_value(offset + child_header + 4, end)
        else:
            require(child_kind in (0x100, 0x101), f"Unsupported compiled XML chunk: {child_kind:#x}")
    require(root is not None and not stack, "Incomplete compiled XML tree")
    return root, references


def validate_launcher(manifest, required=True):
    root, references = read_binary_xml(manifest)
    require(root.tag == "manifest" and root.get("package") == PACKAGE, "Root manifest package must remain dev.mindlink")
    application = root.find("application")
    require(application is not None, "Root application manifest is missing")
    launchers = []
    for activity in list(application.findall("activity")) + list(application.findall("activity-alias")):
        for intent in activity.findall("intent-filter"):
            actions = {item.get("{" + ANDROID_NS + "}name") for item in intent.findall("action")}
            categories = {item.get("{" + ANDROID_NS + "}name") for item in intent.findall("category")}
            if "android.intent.category.LAUNCHER" in categories:
                require("android.intent.action.MAIN" in actions, "Launcher filter lost its MAIN action")
                launchers.append(activity.get("{" + ANDROID_NS + "}name"))
    if required:
        require(launchers == ["wex.ui.core.MainActivity"], "Original launcher must remain wex.ui.core.MainActivity, exactly once")
    else:
        require(not launchers, "A module build without the standalone desktop must not declare a launcher activity")
    return references


def checked_zip(path):
    archive = zipfile.ZipFile(path)
    try:
        names = archive.namelist()
        require(len(names) == len(set(names)), "Duplicate ZIP entries: " + Path(path).name)
        for item in archive.infolist():
            name = item.filename
            require(name and not name.startswith("/") and "\\" not in name and not any(part in (".", "..") for part in name.split("/")),
                    "Unsafe ZIP entry: " + name)
            require(not item.flag_bits & 1, "Encrypted ZIP entry: " + name)
        require(archive.testzip() is None, "ZIP CRC failure: " + Path(path).name)
        return archive
    except BaseException:
        archive.close()
        raise


def ensure_references(references, table, label):
    missing = sorted(value for value in references if value not in table.ids)
    require(not missing, label + " references absent final resource entries: " + ", ".join(f"{value:#010x}" for value in missing[:12]))


def table_from_apk(path, aapt2=None):
    with checked_zip(path) as archive:
        require("resources.arsc" in archive.namelist(), "Linked resource table missing: " + Path(path).name)
        table = read_resource_table(archive.read("resources.arsc"))
        require(table.files <= set(archive.namelist()), "Linked resource file entries missing: " + repr(sorted(table.files - set(archive.namelist()))))
        references = set(table.references)
        for name in archive.namelist():
            if name.startswith("res/") and name.endswith(".xml"):
                data = archive.read(name)
                if data[:2] == b"\x03\x00":
                    _, xml_references = read_binary_xml(data)
                    references.update(xml_references)
        ensure_references(references, table, "Linked APK")
    if aapt2:
        result = subprocess.run([str(aapt2), "dump", "resources", str(path)], capture_output=True,
                                text=True, encoding="utf-8", errors="replace")
        require(result.returncode == 0, "aapt2 resource inspection failed")
        dumped = {}
        for match in re.finditer(r"\bresource (0x[0-9a-fA-F]{8}) ([^\s]+)", result.stdout):
            symbol = match[2].split(":", 1)[-1]
            value = int(match[1], 16)
            require(symbol not in dumped or dumped[symbol] == value, "aapt2 reported inconsistent resource IDs")
            dumped[symbol] = value
        require(dumped == table.symbols, "Binary resource IDs differ from aapt2 inspection")
    return table, references


def uleb(data, position):
    result = 0
    for shift in range(0, 35, 7):
        value = unpack(data, "<B", position)[0]
        position += 1
        result |= (value & 0x7F) << shift
        if not value & 0x80:
            require(result <= 0xFFFFFFFF, "DEX ULEB128 overflow")
            return result, position
    raise ValueError("Invalid DEX ULEB128")


def encoded_value(data, position, depth=0):
    require(depth < 32, "DEX encoded value nesting exceeds limit")
    byte = unpack(data, "<B", position)[0]
    position += 1
    kind, argument = byte & 0x1F, byte >> 5
    if kind == 0x1C:
        require(argument == 0, "Invalid DEX encoded array")
        count, position = uleb(data, position)
        values = []
        for _ in range(count):
            item, position = encoded_value(data, position, depth + 1)
            values.append(item)
        return values, position
    if kind == 0x1D:
        require(argument == 0, "Invalid DEX encoded annotation")
        _, position = uleb(data, position)
        count, position = uleb(data, position)
        for _ in range(count):
            _, position = uleb(data, position)
            _, position = encoded_value(data, position, depth + 1)
        return None, position
    if kind == 0x1E:
        require(argument == 0, "Invalid DEX encoded null")
        return None, position
    if kind == 0x1F:
        require(argument <= 1, "Invalid DEX encoded boolean")
        return bool(argument), position
    maxima = {0: 0, 2: 1, 3: 1, 4: 3, 6: 7, 0x10: 3, 0x11: 7,
              0x15: 3, 0x16: 3, 0x17: 3, 0x18: 3, 0x19: 3, 0x1A: 3, 0x1B: 3}
    require(kind in maxima and argument <= maxima[kind], "Unsupported DEX encoded value")
    size = argument + 1
    require(position + size <= len(data), "Truncated DEX encoded value")
    raw = data[position:position + size]
    value = int.from_bytes(raw, "little", signed=kind in (0, 2, 4, 6)) if kind in (0, 2, 3, 4, 6) else None
    return value, position + size


def instructions(data, code_offset):
    require(code_offset % 4 == 0, "Unaligned DEX code item")
    units_count = unpack(data, "<I", code_offset + 12)[0]
    start, end = code_offset + 16, code_offset + 16 + units_count * 2
    require(end <= len(data), "Truncated DEX instructions")
    units = unpack(data, "<" + str(units_count) + "H", start) if units_count else ()
    position = 0
    while position < units_count:
        word, opcode = units[position], units[position] & 0xFF
        if word in (0x100, 0x200, 0x300):
            require(position + 2 <= units_count, "Truncated DEX instruction payload")
            if word == 0x100:
                width = 4 + units[position + 1] * 2
            elif word == 0x200:
                width = 2 + units[position + 1] * 4
            else:
                require(position + 4 <= units_count, "Truncated DEX array payload")
                count = units[position + 2] | units[position + 3] << 16
                width = 4 + (units[position + 1] * count + 1) // 2
        elif opcode in (0x03, 0x06, 0x09, 0x14, 0x17, 0x1B, 0x24, 0x25, 0x26, 0x2A, 0x2B, 0x2C, 0xFC, 0xFD) or 0x6E <= opcode <= 0x72 or 0x74 <= opcode <= 0x78:
            width = 3
        elif opcode == 0x18:
            width = 5
        elif opcode in (0xFA, 0xFB):
            width = 4
        elif opcode in (0x02, 0x05, 0x08, 0x13, 0x15, 0x16, 0x19, 0x1A, 0x1C, 0x1F, 0x20, 0x22, 0x23, 0x29, 0xFE, 0xFF) or 0x2D <= opcode <= 0x3D or 0x44 <= opcode <= 0x6D or 0x90 <= opcode <= 0xAF or 0xD0 <= opcode <= 0xE2:
            width = 2
        elif opcode <= 0x12 or opcode in (0x1D, 0x1E, 0x21, 0x27, 0x28) or 0x7B <= opcode <= 0x8F or 0xB0 <= opcode <= 0xCF:
            width = 1
        else:
            raise ValueError(f"Unsupported DEX opcode {opcode:#x}")
        require(position + width <= units_count, "Truncated DEX instruction")
        yield position, opcode, units[position:position + width], units
        position += width


def int_constant(opcode, words):
    if opcode == 0x12:
        value = words[0] >> 12
        return value - 16 if value & 8 else value
    if opcode == 0x13:
        return words[1] - 0x10000 if words[1] & 0x8000 else words[1]
    if opcode == 0x14:
        value = words[1] | words[2] << 16
        return value - 0x100000000 if value & 0x80000000 else value
    if opcode == 0x15:
        value = words[1] << 16
        return value - 0x100000000 if value & 0x80000000 else value
    return None


def r_clinit(data, code_offset):
    """Decode ordinary AGP R initializers; never infer a resource ID from a field index."""
    registers, assignments, pending_result = {}, {}, None
    for position, opcode, words, units in instructions(data, code_offset):
        if words[0] in (0x100, 0x200, 0x300):
            continue
        value = int_constant(opcode, words)
        if value is not None:
            register = (words[0] >> 8) & 0xF if opcode == 0x12 else words[0] >> 8
            registers[register] = value
        elif opcode in (1, 4, 7):
            registers[(words[0] >> 8) & 0xF] = registers.get(words[0] >> 12)
        elif opcode in (2, 5, 8):
            registers[words[0] >> 8] = registers.get(words[1])
        elif opcode in (3, 6, 9):
            registers[words[1]] = registers.get(words[2])
        elif opcode == 0x23:
            target, size_register = (words[0] >> 8) & 0xF, words[0] >> 12
            length = registers.get(size_register)
            require(isinstance(length, int) and 0 <= length <= 65536, "Unresolved R array length")
            registers[target] = [0] * length
        elif opcode == 0x26:
            target = words[0] >> 8
            relative = words[1] | words[2] << 16
            relative = relative - 0x100000000 if relative & 0x80000000 else relative
            payload = position + relative
            require(0 <= payload and payload + 4 <= len(units) and units[payload] == 0x300, "Invalid R array data target")
            element_width, count = units[payload + 1], units[payload + 2] | units[payload + 3] << 16
            require(element_width == 4 and payload + 4 + count * 2 <= len(units), "Unsupported R array data")
            values = [units[payload + 4 + index * 2] | units[payload + 5 + index * 2] << 16 for index in range(count)]
            require(isinstance(registers.get(target), list) and len(registers[target]) == count, "R array size differs from its data")
            registers[target][:] = values
        elif opcode in (0x24, 0x25):
            count = words[0] >> 12 if opcode == 0x24 else words[0] >> 8
            if opcode == 0x24:
                require(count <= 5, "Invalid filled R array")
                register_ids = [(words[2] >> shift) & 0xF for shift in (0, 4, 8, 12)] + [(words[0] >> 8) & 0xF]
                register_ids = register_ids[:count]
            else:
                register_ids = list(range(words[2], words[2] + count))
            pending_result = [registers.get(index) for index in register_ids]
            require(all(isinstance(value, int) for value in pending_result), "Unresolved filled R array")
        elif opcode == 0x0C:
            require(pending_result is not None, "Unresolved R array result")
            registers[words[0] >> 8], pending_result = pending_result, None
        elif 0x67 <= opcode <= 0x6D:
            value = registers.get(words[0] >> 8)
            require(isinstance(value, (int, list)), "Unresolved R static field assignment")
            assignments[words[1]] = value
        elif opcode not in (0, 0x0E):
            raise ValueError(f"Unsupported R initializer opcode {opcode:#x}; refusing unchecked R IDs")
    return assignments


def inspect_dex(data):
    require(len(data) >= 0x70 and data[:4] == b"dex\n" and data[7] == 0, "DEX magic/header missing")
    require(unpack(data, "<III", 0x20) == (len(data), 0x70, 0x12345678), "DEX size/header/endian mismatch")
    require(hashlib.sha1(data[32:]).digest() == data[12:32], "DEX SHA-1 mismatch")
    require(zlib.adler32(data[12:]) & 0xFFFFFFFF == unpack(data, "<I", 8)[0], "DEX Adler32 mismatch")
    string_count, string_offset, type_count, type_offset = unpack(data, "<IIII", 0x38)
    field_count, field_offset, method_count, method_offset, class_count, class_offset = unpack(data, "<IIIIII", 0x50)
    for count, offset, width in [(string_count, string_offset, 4), (type_count, type_offset, 4),
                                 (field_count, field_offset, 8), (method_count, method_offset, 8), (class_count, class_offset, 32)]:
        require(not count or offset >= 0x70 and offset + count * width <= len(data), "DEX ID section outside file")
    string_cache = {}

    def string(index):
        require(index < string_count, "Invalid DEX string ID")
        if index not in string_cache:
            offset = unpack(data, "<I", string_offset + index * 4)[0]
            _, offset = uleb(data, offset)
            end = data.find(b"\0", offset)
            require(end >= offset, "Unterminated DEX string")
            string_cache[index] = data[offset:end].replace(b"\xc0\x80", b"\0").decode("utf-8", "surrogatepass")
        return string_cache[index]

    def descriptor(index):
        require(index < type_count, "Invalid DEX type ID")
        return string(unpack(data, "<I", type_offset + index * 4)[0])

    fields = []
    for index in range(field_count):
        owner, kind, name = unpack(data, "<HHI", field_offset + index * 8)
        fields.append((descriptor(owner), descriptor(kind), string(name)))
    classes, r_fields, constants, resource_field_refs = set(), [], set(), set()
    for index in range(class_count):
        values = unpack(data, "<IIIIIIII", class_offset + index * 32)
        owner = descriptor(values[0])
        require(owner not in classes and owner.startswith("L") and owner.endswith(";"), "Duplicate or invalid DEX class definition")
        classes.add(owner)
        resource_class = re.search(r"/R\$([^;/]+);\Z", owner)
        static_fields, methods = [], []
        if values[6]:
            position = values[6]
            counts = []
            for _ in range(4):
                count, position = uleb(data, position)
                counts.append(count)
            for section, count in enumerate(counts):
                member_index = 0
                for _ in range(count):
                    difference, position = uleb(data, position)
                    access, position = uleb(data, position)
                    member_index += difference
                    if section < 2:
                        require(member_index < field_count and fields[member_index][0] == owner, "Invalid DEX encoded field")
                        if section == 0:
                            static_fields.append(member_index)
                    else:
                        code, position = uleb(data, position)
                        require(member_index < method_count, "Invalid DEX encoded method")
                        method_owner, _, method_name = unpack(data, "<HHI", method_offset + member_index * 8)
                        require(descriptor(method_owner) == owner, "DEX encoded method owner differs")
                        methods.append((string(method_name), code))
        initial_values = {}
        if values[7]:
            count, position = uleb(data, values[7])
            require(count <= len(static_fields), "Too many DEX static values")
            for field_index in static_fields[:count]:
                value, position = encoded_value(data, position)
                initial_values[field_index] = value
        for name, code in methods:
            if not code:
                continue
            if resource_class and name == "<clinit>":
                initial_values.update(r_clinit(data, code))
            for _, opcode, words, _ in instructions(data, code):
                value = int_constant(opcode, words)
                if value is not None:
                    constants.add(value & 0xFFFFFFFF)
                if 0x52 <= opcode <= 0x6D:
                    field_index = words[1]
                    require(field_index < len(fields), "DEX field instruction outside field IDs")
                    if re.search(r"/R\$[^;/]+;\Z", fields[field_index][0]):
                        resource_field_refs.add(fields[field_index])
        if resource_class:
            for field_index in static_fields:
                field_owner, kind, name = fields[field_index]
                require(kind in ("I", "[I"), "Unsupported R field type")
                value = initial_values.get(field_index, 0 if kind == "I" else None)
                require(isinstance(value, int) if kind == "I" else isinstance(value, list), "Missing R field value: " + owner + "->" + name)
                r_fields.append((field_owner, resource_class[1], name, value))
    return {"classes": classes, "r_fields": r_fields, "constants": constants, "resource_field_refs": resource_field_refs}


def r_symbol_ids(table):
    result = {}
    for symbol, resource_id in table.symbols.items():
        kind, name = symbol.split("/", 1)
        key = kind, re.sub(r"[.:-]", "_", name)
        require(key not in result, "Ambiguous generated R name: " + kind + "/" + key[1])
        result[key] = resource_id
    return result


def validate_dex_resources(dex, source, final, label, require_r=False, allowed_missing=None):
    names, count, arrays = r_symbol_ids(source), 0, {}
    for owner, kind, name, value in dex["r_fields"]:
        if kind == "styleable":
            if isinstance(value, list):
                arrays[owner, name] = [item & 0xFFFFFFFF for item in value]
                ensure_references({item & 0xFFFFFFFF for item in value if item and (item & 0xFFFFFFFF) >> 24 != 1}, final, label + " styleable")
            continue
        require(isinstance(value, int) and (kind, name) in names, label + " R field has no linked entry: " + kind + "/" + name)
        resource_id = value & 0xFFFFFFFF
        require(resource_id == names[kind, name], label + " R field ID differs from linked table: " + kind + "/" + name)
        require(final.symbols.get(next(symbol for symbol, entry_id in source.symbols.items() if entry_id == resource_id)) == resource_id,
                label + " R field absent or changed in final resource table: " + kind + "/" + name)
        count += 1
    for owner, kind, name, value in dex["r_fields"]:
        if kind != "styleable" or not isinstance(value, int):
            continue
        candidates = [(group, values) for (array_owner, group), values in arrays.items() if array_owner == owner and name.startswith(group + "_")]
        require(candidates, label + " styleable index has no array: " + name)
        group, values = max(candidates, key=lambda item: len(item[0]))
        require(0 <= value < len(values), label + " styleable index outside its array: " + name)
        attr_name = name[len(group) + 1:]
        if attr_name.startswith("android_"):
            require(values[value] >> 24 == 1, label + " platform styleable attribute lost its Android ID")
        else:
            require(names.get(("attr", attr_name)) == values[value], label + " styleable index ID differs from attr entry: " + name)
    for owner, kind, name in dex["resource_field_refs"]:
        resource_class = re.search(r"/R\$([^;/]+);\Z", owner)[1]
        if resource_class != "styleable":
            require((resource_class, name) in names, label + " dependency R reference has no table entry: " + resource_class + "/" + name)
    # Resource-valued inlined constants use an application package/type ID. Do not
    # mistake all 32-bit colors, platform IDs, or Integer.MAX_VALUE for references.
    type_ids = {(value >> 16) & 0xFF for value in source.ids | final.ids}
    referenced = {value for value in dex["constants"] if value >> 24 == PACKAGE_ID and (value >> 16) & 0xFF in type_ids}
    allowed_missing = set() if allowed_missing is None else set(allowed_missing)
    ensure_references(referenced - allowed_missing, final, label + " inlined dependency")
    if require_r:
        require(count > 0, label + " compiled R resource fields are missing")
    return count, referenced


def inspect_apk_dex(path, source, final, label, require_r=True):
    classes, resource_count, references, r_fields = set(), 0, set(), []
    with checked_zip(path) as archive:
        names = sorted((name for name in archive.namelist() if DEX_NAME.fullmatch(name)), key=lambda name: int(re.search(r"classes(\d*)", name)[1] or "1"))
        require(names and names[0] == "classes.dex", label + " DEX payload absent")
        require([int(re.search(r"classes(\d*)", name)[1] or "1") for name in names] == list(range(1, len(names) + 1)), label + " DEX numbering has gaps")
        for name in names:
            dex = inspect_dex(archive.read(name))
            require(not classes & dex["classes"], label + " duplicate class definitions across payload DEX")
            classes.update(dex["classes"])
            count, refs = validate_dex_resources(dex, source, final, label)
            resource_count += count
            references.update(refs)
            r_fields.extend(dex["r_fields"])
    require(not require_r or resource_count > 0, label + " compiled R resource fields are missing")
    return {"dex": len(names), "classes": len(classes), "r_resource_fields": resource_count,
            "inlined_resource_references": len(references)}, classes


def dependency_symbols(artifacts):
    # Older AAR R.txt files contain their historical transitive graph. Required
    # entries come from material resources in the resolved AARs, while executable
    # transitive R fields/refs are independently checked in the actual payload DEX.
    symbols = set()
    for (coordinate, _), path in artifacts.items():
        if path.suffix.lower() != ".aar":
            continue
        with checked_zip(path) as archive:
            for relative in archive.namelist():
                if not relative.startswith("res/") or relative.endswith("/"):
                    continue
                parts = relative.split("/")
                require(len(parts) == 3, "Invalid dependency resource path: " + coordinate)
                folder, filename = parts[1:]
                kind = folder.split("-", 1)[0]
                if kind != "values":
                    name = filename[:-6] if filename.endswith(".9.png") else filename.rsplit(".", 1)[0]
                    symbols.add((kind, name))
                if not filename.endswith(".xml"):
                    continue
                root = ET.fromstring(archive.read(relative))
                if kind == "values":
                    for element in root:
                        if element.tag in ("public", "public-group", "eat-comment", "skip", "overlayable"):
                            continue
                        name = element.get("name")
                        require(name, "Unnamed dependency resource: " + coordinate)
                        resource_type = element.get("type") if element.tag == "item" else (
                            "array" if element.tag in ("string-array", "integer-array") else element.tag)
                        if resource_type != "declare-styleable":
                            symbols.add((resource_type, re.sub(r"[.:-]", "_", name)))
                        for child in element.iter("attr"):
                            attr_name = child.get("name")
                            if attr_name and not attr_name.startswith("android:"):
                                symbols.add(("attr", attr_name))
                        for child in element.iter():
                            if child.tag in ("enum", "flag") and child.get("name"):
                                symbols.add(("id", child.get("name")))
                for element in root.iter():
                    for value in element.attrib.values():
                        if value.startswith("@+id/"):
                            symbols.add(("id", value[len("@+id/"):]))
    return symbols


def validate_linked_resources(desktop, sidepanel, public, stable_ids, desktop_emitted_ids, sidepanel_emitted_ids,
                              inventory, checksums, sidepanel_inventory, sidepanel_checksums,
                              aapt2=None, stage=None, staging_manifest=None, expected_public_count=371):
    """desktop=None validates a sidepanel-only package, where that payload owns every resource."""
    desktop_artifacts, desktop_lock = verify_inventory(inventory, checksums)
    side_artifacts, side_lock = verify_inventory(sidepanel_inventory, sidepanel_checksums)
    original = read_public(public, expected_public_count)
    stable = table_stable_ids(stable_ids)
    desktop_table, desktop_refs = (table_from_apk(desktop, aapt2) if desktop else (None, set()))
    final_table, side_refs = table_from_apk(sidepanel, aapt2)
    runtimes = [("Sidepanel", final_table, sidepanel_emitted_ids)]
    if desktop_table is not None:
        runtimes.insert(0, ("Desktop", desktop_table, desktop_emitted_ids))
    for label, table, emitted in runtimes:
        for symbol, resource_id in original.items():
            require(table.symbols.get(symbol) == resource_id, label + " original resource ID changed or absent: " + symbol)
        require(table_stable_ids(emitted) == table.symbols, label + " emitted IDs differ from actual linked entries")
        require(stable == table.symbols, label + " linked entries differ from common full stable IDs")
    overlap = desktop_table.symbols.keys() & final_table.symbols.keys() if desktop_table is not None else set()
    if desktop_table is not None:
        require(all(desktop_table.symbols[name] == final_table.symbols[name] for name in overlap), "Overlapping desktop/sidepanel resource IDs differ")
        for symbol, entry in desktop_table.entries.items():
            require(symbol in final_table.entries and final_table.entries[symbol].resource_id == entry.resource_id,
                    "Desktop resource absent or changed in final table: " + symbol)
            for config, value in entry.configs.items():
                require(final_table.entries[symbol].configs.get(config) == value, "Desktop resource configuration differs in final union: " + symbol)
    ensure_references(desktop_refs | side_refs, final_table, "Both linked runtimes")
    final_r_symbols = r_symbol_ids(final_table)
    inventory_runtimes = [("Sidepanel", side_artifacts)]
    if desktop_table is not None:
        inventory_runtimes.insert(0, ("Desktop", desktop_artifacts))
    for label, artifacts in inventory_runtimes:
        absent = sorted(kind + "/" + name for kind, name in dependency_symbols(artifacts) if (kind, name) not in final_r_symbols)
        require(not absent, label + " dependency R symbols absent final table: " + repr(absent[:12]))
    side_dex, side_classes = inspect_apk_dex(
        sidepanel, final_table, final_table, "Sidepanel", require_r=False)
    desktop_dex, desktop_classes = None, set()
    if desktop_table is not None:
        desktop_dex, desktop_classes = inspect_apk_dex(desktop, desktop_table, final_table, "Desktop", require_r=False)
        require(desktop_dex["r_resource_fields"] > 0 or desktop_dex["inlined_resource_references"] > 0,
                "Desktop UI has no compiled or inlined resource evidence")
        require("Ldev/mindlink/compose/DesktopUi;" in desktop_classes,
                "Desktop UI entry absent from payload")
    require(side_dex["r_resource_fields"] > 0 or side_dex["inlined_resource_references"] > 0,
            "Sidepanel UI has no compiled or inlined resource evidence")
    require("Ldev/mindlink/compose/SidePanelUi;" in side_classes,
            "Sidepanel UI entry absent from payload")
    if staging_manifest:
        manifest = json.loads(Path(staging_manifest).read_text(encoding="utf-8"))
        require(manifest.get("original_public_sha256") == digest_file(public), "Resource staging used another original public manifest")
        require(manifest.get("sidepanel_lock") == side_lock,
                "Resource staging runtime locks differ from linked payload locks")
        if desktop_table is not None:
            require(manifest.get("desktop_lock") == desktop_lock,
                    "Resource staging runtime locks differ from linked payload locks")
        absent = sorted(symbol for symbol in manifest.get("resource_symbols", []) if symbol not in final_table.symbols)
        require(not absent, "Common staging resource symbols absent final table: " + repr(absent[:12]))
        baseline = manifest.get("baseline")
        if baseline:
            for symbol, resource_id in baseline["resource_symbols"].items():
                require(final_table.symbols.get(symbol) == resource_id, "Installed baseline resource ID changed: " + symbol)
    if stage:
        with checked_zip(stage) as archive:
            require("AndroidManifest.xml" in archive.namelist(), "Primary manifest missing")
            ensure_references(validate_launcher(archive.read("AndroidManifest.xml"), required=desktop_table is not None),
                              final_table, "Primary manifest")
    return {"original_public_resources": len(original), "common_stable_entries": len(stable), "overlap_equal_ids": len(overlap),
            "final_resource_entries": len(final_table.entries), "final_resource_files": len(final_table.files),
            "final_resources_sha256": resource_bytes_digest(sidepanel),
            "desktop_apk_sha256": digest_file(desktop) if desktop else None,
            "sidepanel_apk_sha256": digest_file(sidepanel),
            "desktop_runtime": desktop_lock if desktop else None, "sidepanel_runtime": side_lock,
            "desktop_dex": desktop_dex, "sidepanel_dex": side_dex}, desktop_table, final_table


def resource_bytes_digest(path):
    with checked_zip(path) as archive:
        return hashlib.sha256(archive.read("resources.arsc")).hexdigest()


def write_report(path, report):
    path = Path(path)
    require(not path.exists(), "Verification report output must be new")
    temporary = path.with_name(path.name + ".partial")
    require(not temporary.exists(), "Verification report temporary output already exists")
    temporary.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    temporary.replace(path)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ["apk", "public", "aapt2", "inventory", "checksums", "sidepanel-ui-apk", "sidepanel-inventory",
                 "sidepanel-checksums", "stable-ids", "desktop-emitted-ids", "sidepanel-emitted-ids", "stage-apk",
                 "staging-manifest", "report"]:
        parser.add_argument("--" + name, type=Path)
    parser.add_argument("--inventory-only", action="store_true")
    parser.add_argument("--resource-only", action="store_true", help="Inspect a bootstrap resource link, not a runtime APK")
    parser.add_argument("--expected-public-count", type=int, default=371)
    args = parser.parse_args()
    try:
        require(args.inventory and args.checksums, "--inventory and --checksums are required")
        artifacts, lock = verify_inventory(args.inventory, args.checksums)
        if args.inventory_only:
            require(not args.apk and not args.sidepanel_ui_apk and not args.resource_only, "Inventory-only mode cannot inspect APKs")
            report = {"runtime": lock}
        elif args.sidepanel_ui_apk:
            require(not args.resource_only and all([args.public, args.stable_ids,
                    args.sidepanel_emitted_ids, args.sidepanel_inventory, args.sidepanel_checksums]),
                    "Payload check requires the APK, public resources, common stable IDs, its emitted IDs and inventory/checksum locks")
            if args.apk:
                require(args.desktop_emitted_ids, "Two payloads require the desktop emitted IDs as well")
            report, _, _ = validate_linked_resources(args.apk or None, args.sidepanel_ui_apk, args.public, args.stable_ids,
                    args.desktop_emitted_ids, args.sidepanel_emitted_ids, args.inventory, args.checksums,
                    args.sidepanel_inventory, args.sidepanel_checksums, args.aapt2, args.stage_apk,
                    args.staging_manifest, args.expected_public_count)
        else:
            require(args.apk and args.public, "--apk and --public are required")
            original = read_public(args.public, args.expected_public_count if args.resource_only else None)
            table, references = table_from_apk(args.apk, args.aapt2)
            for symbol, resource_id in original.items():
                require(table.symbols.get(symbol) == resource_id, "Original resource ID changed: " + symbol)
            if args.desktop_emitted_ids:
                require(table_stable_ids(args.desktop_emitted_ids) == table.symbols, "Bootstrap emitted IDs differ from actual entries")
            require(not args.resource_only or args.desktop_emitted_ids, "Bootstrap link must emit its full IDs")
            if not args.resource_only:
                inspect_apk_dex(args.apk, table, table, "Desktop")
            report = {"original_public_resources": len(original), "resource_entries": len(table.entries), "runtime": lock,
                      "resource_only": args.resource_only, "apk_sha256": digest_file(args.apk)}
        if args.report:
            write_report(args.report, report)
        print("PASS: " + ("pinned offline runtime inventory" if args.inventory_only else "linked resource IDs/entries/references and pinned runtimes"))
    except (ValueError, OSError, KeyError, UnicodeError, ET.ParseError, zipfile.BadZipFile) as error:
        print("FAIL: " + str(error), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
