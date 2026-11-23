"""Generate MindLink's Lucide icon sources from lucide-static's icon-nodes.json.

Usage (project root):
    python -I scripts/import_lucide_icons.py --nodes <lucide-static>/icon-nodes.json --version 1.77.0

Writes, from one mapping table, every vector icon the module draws:
  * java_src/wex/ui/core/feature/LucideIcons.java  (data block between GENERATED markers;
    the hand-written renderer around it is kept)
  * java_src/wex/ui/core/feature/NavigationIcons.java  (home bar tabs, android.graphics.Path)
  * ui-compose/sidepanel/src/main/java/dev/mindlink/ui/LucideIcons.kt  (Compose ImageVector)

SVG elements (path / circle / ellipse / rect / line / polyline / polygon) are converted to
absolute M / L / C / Z only: relative commands, H/V/S/Q/T and elliptical arcs become lines and
cubic Beziers, so the Java parser needs no PathParser. The input is parsed as data and never
executed. Lucide is ISC licensed (Copyright (c) Lucide Icons and Contributors).
"""
import argparse
import hashlib
import json
import math
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

# Java key -> Lucide name. Keys keep the historical asset names so callers need no change.
JAVA_ICONS = [
    ("album.png", "image"), ("shoot.png", "camera"), ("video_call.png", "video"),
    ("voice_call.png", "phone"), ("location.png", "map-pin"), ("redpacket.png", "gift"),
    ("transfer.png", "arrow-left-right"), ("file.png", "file-text"), ("favorite.png", "star"),
    ("chain.png", "list-ordered"), ("card.png", "contact"), ("music.png", "eighth-notes"),
    ("more.png", "ellipsis"), ("mark_read.png", "check-check"), ("wex_setting_icon.png", "settings"),
    ("topbar_search.png", "search"), ("gift.png", "package"), ("live.png", "radio"),
    ("group_tool.png", "users"), ("voice_input.png", "mic"), ("reply.png", "message-square-text"),
    ("上一曲.png", "skip-back"), ("下一曲.png", "skip-forward"), ("关闭.png", "x"),
    ("搜索.png", "search"), ("播放列表.png", "list-music"), ("点击播放.png", "play"),
    ("点击暂停.png", "pause"), ("已收藏.png", "star"), ("未收藏.png", "star"),
    ("shell_chat", "message-circle"), ("shell_home", "house"), ("shell_top", "panel-top"),
    ("shell_bg", "wallpaper"), ("shell_more", "circle-ellipsis"), ("shell_close", "x"),
    ("shell_back", "chevron-left"), ("shell_next", "chevron-right"), ("shell_restart", "refresh-cw"),
    ("qrcode", "qr-code"), ("wallet", "wallet"), ("user-plus", "user-plus"),
    ("id-badge", "id-card"), ("users-group", "users"),
]
# Lucide has no filled set: these render as filled outline + same-colour stroke.
JAVA_FILLED = [
    ("star-filled", "star"), ("play-filled", "play"), ("pause-filled", "pause"),
    ("prev-filled", "skip-back"), ("next-filled", "skip-forward"),
]
# Home bar: chats-home, contacts, discover, me.
NAVIGATION = ["house", "book-user", "compass", "user"]
# Compose icons used by the side panel / settings payload.
COMPOSE = [
    "plus", "x", "pencil", "save", "settings", "refresh-cw", "chevron-right", "chevron-up",
    "chevron-down", "arrow-left", "grip-horizontal", "ratio", "map-pin", "thermometer",
    "droplets", "wind", "wallet", "scan-qr-code", "quote", "sun", "cloud-sun", "cloud",
    "cloud-rain", "cloud-lightning", "cloud-hail", "cloud-snow", "cloud-drizzle",
    "cloud-rain-wind", "zap", "sun-snow", "snowflake", "cloud-fog", "haze", "tornado",
    "question-mark", "user-plus", "aperture", "clapperboard", "star", "puzzle", "rotate-cw",
    "circle-x", "message-circle-check",
    # Settings overview: one icon per feature page.
    "panel-top", "panel-bottom", "folders", "panel-left", "check-check", "app-window",
    "toolbox", "keyboard", "message-circle", "layers", "wallpaper", "globe", "bug",
]

NUM = re.compile(r"[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?")
KAPPA = 0.5522847498307936


def fmt(value):
    value = round(value, 3)
    if value == 0:
        value = 0.0
    text = ("%.3f" % value).rstrip("0").rstrip(".")
    return "0" if text in ("", "-0") else text


class Builder:
    """Absolute M/L/C/Z command list."""

    def __init__(self):
        self.ops = []
        self.x = self.y = self.sx = self.sy = 0.0

    def move(self, x, y):
        self.ops.append(("M", x, y)); self.x, self.y, self.sx, self.sy = x, y, x, y

    def line(self, x, y):
        self.ops.append(("L", x, y)); self.x, self.y = x, y

    def cubic(self, x1, y1, x2, y2, x, y):
        self.ops.append(("C", x1, y1, x2, y2, x, y)); self.x, self.y = x, y

    def close(self):
        self.ops.append(("Z",)); self.x, self.y = self.sx, self.sy


def arc(b, rx, ry, phi_deg, large, sweep, x2, y2):
    x1, y1 = b.x, b.y
    if (x1, y1) == (x2, y2):
        return
    if rx == 0 or ry == 0:
        b.line(x2, y2); return
    rx, ry = abs(rx), abs(ry)
    phi = math.radians(phi_deg % 360)
    cp, sp = math.cos(phi), math.sin(phi)
    dx, dy = (x1 - x2) / 2, (y1 - y2) / 2
    x1p, y1p = cp * dx + sp * dy, -sp * dx + cp * dy
    lam = (x1p * x1p) / (rx * rx) + (y1p * y1p) / (ry * ry)
    if lam > 1:
        s = math.sqrt(lam); rx *= s; ry *= s
    num = rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p
    den = rx * rx * y1p * y1p + ry * ry * x1p * x1p
    coef = math.sqrt(max(0.0, num / den)) if den else 0.0
    if large == sweep:
        coef = -coef
    cxp, cyp = coef * rx * y1p / ry, -coef * ry * x1p / rx
    cx = cp * cxp - sp * cyp + (x1 + x2) / 2
    cy = sp * cxp + cp * cyp + (y1 + y2) / 2

    def angle(ux, uy, vx, vy):
        a = math.atan2(ux * vy - uy * vx, ux * vx + uy * vy)
        return a

    t1 = angle(1, 0, (x1p - cxp) / rx, (y1p - cyp) / ry)
    dt = angle((x1p - cxp) / rx, (y1p - cyp) / ry, (-x1p - cxp) / rx, (-y1p - cyp) / ry)
    if not sweep and dt > 0:
        dt -= 2 * math.pi
    elif sweep and dt < 0:
        dt += 2 * math.pi
    segments = max(1, int(math.ceil(abs(dt) / (math.pi / 2) - 1e-9)))
    delta = dt / segments
    k = 4 / 3 * math.tan(delta / 4)

    def point(t):
        ct, st = math.cos(t), math.sin(t)
        return (cx + rx * ct * cp - ry * st * sp, cy + rx * ct * sp + ry * st * cp,
                -rx * st * cp - ry * ct * sp, -rx * st * sp + ry * ct * cp)

    t = t1
    for i in range(segments):
        ax, ay, adx, ady = point(t)
        bx, by, bdx, bdy = point(t + delta)
        if i == segments - 1:
            bx, by = x2, y2
        b.cubic(ax + k * adx, ay + k * ady, bx - k * bdx, by - k * bdy, bx, by)
        t += delta


def parse_path(b, d):
    tokens = re.findall(r"[MmLlHhVvCcSsQqTtAaZz]|[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?", d)
    i, cmd = 0, None
    last_c = last_q = None

    def nums(count):
        nonlocal i
        out = [float(tokens[i + n]) for n in range(count)]
        i += count
        return out

    while i < len(tokens):
        tok = tokens[i]
        if tok.isalpha():
            cmd = tok; i += 1
            if cmd in "Zz":
                b.close(); last_c = last_q = None
                continue
        if cmd is None:
            raise ValueError("path data starts without a command: " + d)
        rel = cmd.islower()
        ox, oy = (b.x, b.y) if rel else (0.0, 0.0)
        c = cmd.upper()
        if c == "M":
            x, y = nums(2); b.move(ox + x, oy + y)
            cmd = "l" if rel else "L"; last_c = last_q = None
        elif c == "L":
            x, y = nums(2); b.line(ox + x, oy + y); last_c = last_q = None
        elif c == "H":
            (x,) = nums(1); b.line(ox + x if rel else x, b.y); last_c = last_q = None
        elif c == "V":
            (y,) = nums(1); b.line(b.x, oy + y if rel else y); last_c = last_q = None
        elif c == "C":
            x1, y1, x2, y2, x, y = nums(6)
            b.cubic(ox + x1, oy + y1, ox + x2, oy + y2, ox + x, oy + y)
            last_c, last_q = (ox + x2, oy + y2), None
        elif c == "S":
            x2, y2, x, y = nums(4)
            x1, y1 = (2 * b.x - last_c[0], 2 * b.y - last_c[1]) if last_c else (b.x, b.y)
            b.cubic(x1, y1, ox + x2, oy + y2, ox + x, oy + y)
            last_c, last_q = (ox + x2, oy + y2), None
        elif c in "QT":
            if c == "Q":
                qx, qy, x, y = nums(4); qx, qy = ox + qx, oy + qy
            else:
                x, y = nums(2)
                qx, qy = (2 * b.x - last_q[0], 2 * b.y - last_q[1]) if last_q else (b.x, b.y)
            x, y = ox + x, oy + y
            b.cubic(b.x + 2 / 3 * (qx - b.x), b.y + 2 / 3 * (qy - b.y),
                    x + 2 / 3 * (qx - x), y + 2 / 3 * (qy - y), x, y)
            last_q, last_c = (qx, qy), None
        elif c == "A":
            rx, ry, rot = nums(3)
            # Flags may be written without separators ("a6 6 0 10-12 0").
            flags = []
            while len(flags) < 2:
                t = tokens[i]
                if t in ("0", "1"):
                    flags.append(int(t)); i += 1
                elif re.fullmatch(r"[01]\d*\.?\d*", t):
                    flags.append(int(t[0])); tokens[i] = t[1:]
                else:
                    raise ValueError("bad arc flags in " + d)
            x, y = nums(2)
            arc(b, rx, ry, rot, flags[0], flags[1], ox + x, oy + y)
            last_c = last_q = None
        else:
            raise ValueError("unsupported command " + cmd)


def ellipse(b, cx, cy, rx, ry):
    b.move(cx + rx, cy)
    b.cubic(cx + rx, cy + KAPPA * ry, cx + KAPPA * rx, cy + ry, cx, cy + ry)
    b.cubic(cx - KAPPA * rx, cy + ry, cx - rx, cy + KAPPA * ry, cx - rx, cy)
    b.cubic(cx - rx, cy - KAPPA * ry, cx - KAPPA * rx, cy - ry, cx, cy - ry)
    b.cubic(cx + KAPPA * rx, cy - ry, cx + rx, cy - KAPPA * ry, cx + rx, cy)
    b.close()


def rect(b, x, y, w, h, rx, ry):
    if rx is None and ry is None:
        rx = ry = 0.0
    rx = ry if rx is None else rx
    ry = rx if ry is None else ry
    rx, ry = min(rx, w / 2), min(ry, h / 2)
    if rx <= 0 or ry <= 0:
        b.move(x, y); b.line(x + w, y); b.line(x + w, y + h); b.line(x, y + h); b.close(); return
    kx, ky = KAPPA * rx, KAPPA * ry
    b.move(x + rx, y); b.line(x + w - rx, y)
    b.cubic(x + w - rx + kx, y, x + w, y + ry - ky, x + w, y + ry); b.line(x + w, y + h - ry)
    b.cubic(x + w, y + h - ry + ky, x + w - rx + kx, y + h, x + w - rx, y + h); b.line(x + rx, y + h)
    b.cubic(x + rx - kx, y + h, x, y + h - ry + ky, x, y + h - ry); b.line(x, y + ry)
    b.cubic(x, y + ry - ky, x + rx - kx, y, x + rx, y); b.close()


def icon(nodes, name):
    if name not in nodes:
        raise SystemExit("Lucide icon not found: " + name)
    parts = []
    for tag, attrs in nodes[name]:
        b = Builder()
        f = lambda key, default=None: float(attrs[key]) if key in attrs else default
        if tag == "path":
            parse_path(b, attrs["d"])
        elif tag == "circle":
            ellipse(b, f("cx", 0.0), f("cy", 0.0), f("r"), f("r"))
        elif tag == "ellipse":
            ellipse(b, f("cx", 0.0), f("cy", 0.0), f("rx"), f("ry"))
        elif tag == "rect":
            rect(b, f("x", 0.0), f("y", 0.0), f("width"), f("height"), f("rx"), f("ry"))
        elif tag == "line":
            b.move(f("x1", 0.0), f("y1", 0.0)); b.line(f("x2", 0.0), f("y2", 0.0))
        elif tag in ("polyline", "polygon"):
            values = [float(v) for v in NUM.findall(attrs["points"])]
            b.move(values[0], values[1])
            for n in range(2, len(values), 2):
                b.line(values[n], values[n + 1])
            if tag == "polygon":
                b.close()
        else:
            raise SystemExit("Unsupported SVG element <%s> in %s" % (tag, name))
        parts.append(b.ops)
    return parts


def java_data(parts):
    out = []
    for ops in parts:
        words = []
        for op in ops:
            words.append(op[0] + (" " + " ".join(fmt(v) for v in op[1:]) if len(op) > 1 else ""))
        out.append(" ".join(words))
    return "\\n".join(out)


def pascal(name):
    return "".join(part[:1].upper() + part[1:] for part in name.split("-"))


def kotlin_ops(ops, indent):
    lines = []
    for op in ops:
        if op[0] == "M":
            lines.append("moveTo(%sf, %sf)" % (fmt(op[1]), fmt(op[2])))
        elif op[0] == "L":
            lines.append("lineTo(%sf, %sf)" % (fmt(op[1]), fmt(op[2])))
        elif op[0] == "C":
            lines.append("curveTo(%s)" % ", ".join(fmt(v) + "f" for v in op[1:]))
        else:
            lines.append("close()")
    return [indent + line for line in lines]


def digest(parts):
    return hashlib.sha256(json.dumps(parts).encode("utf-8")).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--nodes", required=True, type=Path)
    parser.add_argument("--version", required=True)
    parser.add_argument("--root", type=Path, default=ROOT)
    args = parser.parse_args()
    nodes = json.loads(args.nodes.read_text(encoding="utf-8"))
    root = args.root
    header = "Lucide %s · ISC · Copyright (c) Lucide Icons and Contributors" % args.version

    # ---- Java renderer data block
    java_path = root / "java_src/wex/ui/core/feature/LucideIcons.java"
    source = java_path.read_text(encoding="utf-8")
    begin, end = "    // BEGIN GENERATED (scripts/import_lucide_icons.py)\n", "    // END GENERATED\n"
    a, z = source.index(begin) + len(begin), source.index(end)
    block = ["    /** %s。键沿用原 assets 文件名，调用方无需改动。 */" % header,
             "    private static final String[][] ICONS = {"]
    for key, name in JAVA_ICONS:
        block.append('        { "%s", "%s" }, // %s' % (key, java_data(icon(nodes, name)), name))
    block += ["    };", "", "    /** 实心态：同一 Lucide 轮廓，填充闭合区域并以同色描边。 */",
              "    private static final String[][] FILLED_ICONS = {"]
    for key, name in JAVA_FILLED:
        block.append('        { "%s", "%s" }, // %s' % (key, java_data(icon(nodes, name)), name))
    block.append("    };")
    java_path.write_text(source[:a] + "\n".join(block) + "\n" + source[z:], encoding="utf-8", newline="\r\n")

    # ---- Navigation tabs (android.graphics.Path, 24 grid)
    nav = ["// " + header + ".", "// Generated by scripts/import_lucide_icons.py. See THIRD-PARTY-NOTICES.md.",
           "package wex.ui.core.feature;", "", "import android.graphics.Path;", "",
           "/** Home bar tab outlines in Lucide's 24-unit grid; draw with a 2-unit round stroke. */",
           "final class NavigationIcons {", "    static final float GRID = 24f;", "",
           "    private NavigationIcons() { }", "",
           "    /** Lucide has no filled set: the selected tab uses the same outline (heavier stroke, accent). */",
           "    static Path create(int index, boolean filled) {", "        switch (index) {"]
    for index, name in enumerate(NAVIGATION):
        nav.append("            case %d: return %s();" % (index, pascal(name)[0].lower() + pascal(name)[1:]))
    nav += ['            default: throw new IllegalArgumentException("Unknown native tab index");', "        }", "    }"]
    for name in NAVIGATION:
        parts = icon(nodes, name)
        method = pascal(name)[0].lower() + pascal(name)[1:]
        nav += ["", "    // %s commands SHA-256: %s" % (name, digest(parts)),
                "    private static Path %s() {" % method, "        Path path = new Path();"]
        for ops in parts:
            for op in ops:
                if op[0] == "M":
                    nav.append("        path.moveTo(%sf, %sf);" % (fmt(op[1]), fmt(op[2])))
                elif op[0] == "L":
                    nav.append("        path.lineTo(%sf, %sf);" % (fmt(op[1]), fmt(op[2])))
                elif op[0] == "C":
                    nav.append("        path.cubicTo(%s);" % ", ".join(fmt(v) + "f" for v in op[1:]))
                else:
                    nav.append("        path.close();")
        nav += ["        return path;", "    }"]
    nav.append("}")
    (root / "java_src/wex/ui/core/feature/NavigationIcons.java").write_text(
        "\n".join(nav) + "\n", encoding="utf-8", newline="\r\n")

    # ---- Compose ImageVectors
    kt = ["// " + header + ".", "// Generated by scripts/import_lucide_icons.py. See THIRD-PARTY-NOTICES.md.",
          "package dev.mindlink.ui", "",
          "import androidx.compose.ui.graphics.Color",
          "import androidx.compose.ui.graphics.SolidColor",
          "import androidx.compose.ui.graphics.StrokeCap",
          "import androidx.compose.ui.graphics.StrokeJoin",
          "import androidx.compose.ui.graphics.vector.ImageVector",
          "import androidx.compose.ui.graphics.vector.PathBuilder",
          "import androidx.compose.ui.graphics.vector.path",
          "import androidx.compose.ui.unit.dp", "",
          "/** Lucide outline icons: 24 grid, 2-unit round stroke, tinted by Icon(). */",
          "@Suppress(\"MagicNumber\")", "object LucideIcons {",
          "    private fun lucide(name: String, block: PathBuilder.() -> Unit): ImageVector = ImageVector.Builder(",
          "        name = \"Lucide.$name\",", "        defaultWidth = 24.dp,", "        defaultHeight = 24.dp,",
          "        viewportWidth = 24f,", "        viewportHeight = 24f,",
          "    ).apply {",
          "        path(",
          "            fill = null,", "            stroke = SolidColor(Color.Black),", "            strokeLineWidth = 2f,",
          "            strokeLineCap = StrokeCap.Round,", "            strokeLineJoin = StrokeJoin.Round,",
          "            pathBuilder = block,", "        )", "    }.build()"]
    for name in COMPOSE:
        parts = icon(nodes, name)
        kt += ["", "    val %s: ImageVector by lazy {" % pascal(name), "        lucide(\"%s\") {" % name]
        for ops in parts:
            kt += kotlin_ops(ops, "            ")
        kt += ["        }", "    }"]
    kt.append("}")
    (root / "ui-compose/sidepanel/src/main/java/dev/mindlink/ui/LucideIcons.kt").write_text(
        "\n".join(kt) + "\n", encoding="utf-8", newline="\r\n")
    used = sorted({n for _, n in JAVA_ICONS + JAVA_FILLED} | set(NAVIGATION) | set(COMPOSE))
    print("Lucide %s: %d distinct icons (Java %d keys, navigation %d, Compose %d)"
          % (args.version, len(used), len(JAVA_ICONS) + len(JAVA_FILLED), len(NAVIGATION), len(COMPOSE)))


if __name__ == "__main__":
    main()
