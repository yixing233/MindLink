"""Validate the embedded, host-loaded ItemDecoration DEX without executing it."""
import base64
import hashlib
from pathlib import Path
import re
import struct
import zlib

ROOT = Path(__file__).resolve().parents[2]


def main():
    source = (ROOT / "java_src/wex/ui/core/feature/ChatTailSpacer.java").read_text(encoding="utf-8")
    match = re.search(r'private static final String DEX = "([A-Za-z0-9+/=]+)";', source)
    assert match, "embedded bridge is missing"
    data = base64.b64decode(match[1], validate=True)
    assert data[:8] == b"dex\n035\0"
    assert struct.unpack_from("<I", data, 32)[0] == len(data)
    assert struct.unpack_from("<I", data, 8)[0] == zlib.adler32(data[12:]) & 0xFFFFFFFF
    assert data[12:32] == hashlib.sha1(data[32:]).digest()
    assert struct.unpack_from("<I", data, 96)[0] == 1, "bridge must define exactly one class"
    for name in (b"Lmdl/spacer/TailDecoration;", b"Landroidx/recyclerview/widget/p2;",
                 b"Landroidx/recyclerview/widget/h3;", b"getItemOffsets", b"Ljava/lang/Object;"):
        assert name in data, name
    assert "view.getClass().getClassLoader()" in source
    assert "newInstance(ByteBuffer.wrap(dex), parent)" in source
    assert 'Class.forName("androidx.recyclerview.widget.p2", false, parent)' in source
    print("PASS: isolated tail bridge DEX length, SHA-1/Adler32, single class and host-loader boundary")
    print("Bridge SHA-256: " + hashlib.sha256(data).hexdigest())


if __name__ == "__main__":
    main()
