"""MindLink temporary APK share: password-protected Ant Design file list."""
from __future__ import annotations

import gzip
import hashlib
import hmac
import json
import os
import posixpath
import re
import secrets
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, quote, urlparse

ROOT = os.environ.get("SHARE_ROOT", "/data")
STATIC = os.path.join(os.path.dirname(os.path.abspath(__file__)), "static")
PASSWORD = os.environ.get("SHARE_PASSWORD", "xcb157342")
PORT = int(os.environ.get("SHARE_PORT", "8098"))
CHANGELOG = os.environ.get("SHARE_CHANGELOG", os.path.join(os.path.dirname(STATIC), "changelog.json"))
COOKIE = "mindlink_share"
TTL = 7 * 24 * 3600
SECRET = secrets.token_bytes(32)

FAIL_WINDOW = 600
FAIL_LIMIT = 8
_fails: dict[str, list[float]] = {}
_lock = threading.Lock()


def _token(ts: int) -> str:
    mac = hmac.new(SECRET, str(ts).encode(), hashlib.sha256).hexdigest()
    return f"{ts}.{mac}"


def session_ok(value: str | None) -> bool:
    if not value or "." not in value:
        return False
    ts_s, mac = value.rsplit(".", 1)
    try:
        ts = int(ts_s)
    except ValueError:
        return False
    if ts < time.time() - TTL:
        return False
    good = hmac.new(SECRET, ts_s.encode(), hashlib.sha256).hexdigest()
    return hmac.compare_digest(good, mac)


PRUNE_DIRS = {
    ".git", "node_modules", "__pycache__", "smali", "smali_classes2", "smali_classes3",
    "classes", "assets", "res", "lib", "tmp", "scripts", "logs", "original",
    "unknown", "dex", "stub", "java_src", "java_stub", "reports", "intermediates",
    ".gradle", "generated", "obj", "cache", ".idea", ".svn", "build-tmp",
}

_cache_lock = threading.Lock()
_cache: dict = {"items": [], "ts": 0.0}
CACHE_TTL = 20.0


def _scan_uncached() -> list[dict]:
    items = []
    if not os.path.isdir(ROOT):
        return items
    for dirpath, dirnames, filenames in os.walk(ROOT):
        rel_dir = os.path.relpath(dirpath, ROOT)
        depth = 0 if rel_dir == "." else rel_dir.count(os.sep) + 1
        if depth >= 3:
            dirnames[:] = []
        dirnames[:] = [d for d in dirnames if not d.startswith(".") and d not in PRUNE_DIRS]
        for name in filenames:
            if not name.lower().endswith((".apk", ".apks", ".zip")):
                continue
            path = os.path.join(dirpath, name)
            try:
                st = os.stat(path)
            except OSError:
                continue
            rel = os.path.relpath(path, ROOT).replace(os.sep, "/")
            items.append({
                "id": rel,
                "name": name,
                "dir": posixpath.dirname(rel),
                "size": st.st_size,
                "mtime": int(st.st_mtime),
                "url": "/download?f=" + quote(rel, safe=""),
            })
    items.sort(key=lambda i: i["mtime"], reverse=True)
    return items


def refresh() -> None:
    try:
        items = _scan_uncached()
    except Exception:
        return
    with _cache_lock:
        _cache["items"] = items
        _cache["ts"] = time.time()


def scan() -> list[dict]:
    with _cache_lock:
        items, ts = _cache["items"], _cache["ts"]
    if items and time.time() - ts < CACHE_TTL:
        return items
    refresh()
    with _cache_lock:
        return _cache["items"]


def _bg_refresh() -> None:
    refresh()
    while True:
        time.sleep(15)
        refresh()


threading.Thread(target=_bg_refresh, daemon=True).start()


def _load_html(name: str) -> str:
    try:
        with open(os.path.join(STATIC, name), encoding="utf-8") as fh:
            return fh.read()
    except OSError:
        return "<h1>missing template: " + name + "</h1>"


LOGIN_HTML = _load_html("app-login.html")

APP_HTML = _load_html("app-files.html")


NOTE_NAMES = ("CHANGELOG.md", "changelog.md", "NOTES.md", "notes.md", "更新日志.md", "更新日志.txt", "changelog.txt")

_cl_lock = threading.Lock()
_cl_cache: dict = {"data": None, "ts": 0.0}


def _load_changelog() -> dict:
    with _cl_lock:
        if _cl_cache["data"] is not None and time.time() - _cl_cache["ts"] < 15:
            return _cl_cache["data"]
    data = {"versions": []}
    try:
        with open(CHANGELOG, encoding="utf-8-sig") as fh:
            data = json.load(fh)
    except Exception:
        pass
    if not isinstance(data, dict):
        data = {"versions": []}
    with _cl_lock:
        _cl_cache["data"] = data
        _cl_cache["ts"] = time.time()
    return data


def _safe_batch(name: str) -> str:
    name = (name or "").replace("\\", "/").strip("/")
    if name.startswith(".") or ".." in name.split("/"):
        return ""
    return name


def _read_note(batch: str) -> str:
    batch = _safe_batch(batch)
    if not batch:
        return ""
    base = os.path.join(ROOT, *batch.split("/"))
    for name in NOTE_NAMES:
        path = os.path.join(base, name)
        try:
            if os.path.isfile(path):
                with open(path, encoding="utf-8", errors="replace") as fh:
                    return fh.read()[:8000]
        except OSError:
            continue
    return ""


def changelog_view() -> list[dict]:
    data = _load_changelog()
    by_batch: dict[str, list[dict]] = {}
    for item in scan():
        by_batch.setdefault(item["dir"], []).append(item)
    out = []
    for entry in data.get("versions", []):
        if not isinstance(entry, dict):
            continue
        batch = _safe_batch(str(entry.get("batch") or ""))
        files = by_batch.get(batch, [])
        main = next((f for f in files if f["name"].lower() == "mindlink-1.0.apk"), files[0] if files else None)
        item = dict(entry)
        item["batch"] = batch
        item["files"] = files
        item["count"] = len(files)
        item["size"] = sum(f["size"] for f in files)
        item["available"] = bool(files)
        item["mainUrl"] = main["url"] if main else ""
        item["mainName"] = main["name"] if main else ""
        item["note"] = _read_note(batch) if files else ""
        ts = max((f["mtime"] for f in files), default=int(entry.get("time") or 0))
        item["time"] = int(ts)
        if not item.get("date") and ts:
            item["date"] = time.strftime("%Y-%m-%d", time.localtime(ts))
        out.append(item)
    out.sort(key=lambda e: (e.get("time") or 0), reverse=True)
    return out


class Handler(BaseHTTPRequestHandler):

    def log_message(self, fmt, *args):
        print("%s %s" % (self.address_string(), fmt % args), flush=True)

    def _cookie(self) -> str | None:
        raw = self.headers.get("Cookie") or ""
        for part in raw.split(";"):
            k, _, v = part.strip().partition("=")
            if k == COOKIE:
                return v
        return None

    def _authed(self) -> bool:
        return session_ok(self._cookie())

    def _secure_flag(self) -> str:
        proto = (self.headers.get("X-Forwarded-Proto") or "").lower()
        return "; Secure" if proto == "https" else ""

    def _accepts_gzip(self) -> bool:
        return "gzip" in (self.headers.get("Accept-Encoding") or "")

    def _send(self, code: int, body, ctype="text/html; charset=utf-8", extra: dict | None = None):
        data = body.encode("utf-8") if isinstance(body, str) else body
        compressible = ctype.startswith("text/") or "json" in ctype or "javascript" in ctype
        if compressible and len(data) > 512 and self._accepts_gzip():
            data = gzip.compress(data, 6)
            extra = dict(extra or {})
            extra["Content-Encoding"] = "gzip"
            extra["Vary"] = "Accept-Encoding"
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("Referrer-Policy", "no-referrer")
        for k, v in (extra or {}).items():
            self.send_header(k, v)
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(data)

    def _json(self, code: int, payload):
        self._send(code, json.dumps(payload), "application/json; charset=utf-8")

    def _redirect(self, target: str):
        self._send(302, "", extra={"Location": target})

    def do_GET(self):
        path = urlparse(self.path).path
        if path == "/favicon.ico":
            return self._send(204, b"")
        if path.startswith("/static/"):
            return self._static(path[len("/static/"):])
        if path == "/login":
            return self._send(200, LOGIN_HTML) if not self._authed() else self._redirect("/")
        if not self._authed():
            return self._redirect("/login") if path in ("/", "") else self._json(401, {"error": "unauthorized"})
        if path in ("/", ""):
            return self._send(200, APP_HTML)
        if path == "/api/changelog":
            return self._json(200, {"versions": changelog_view()})
        if path == "/api/files":
            files = scan()
            return self._json(200, {"files": files, "generatedAt": int(time.time())})
        if path == "/download":
            return self._download()
        self._json(404, {"error": "not found"})

    def do_HEAD(self):
        self.do_GET()

    def do_POST(self):
        path = urlparse(self.path).path
        if path == "/api/login":
            return self._login()
        if path == "/api/logout":
            return self._send(200, json.dumps({"ok": True}), "application/json; charset=utf-8",
                              {"Set-Cookie": f"{COOKIE}=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax"})
        self._json(404, {"error": "not found"})

    def _login(self):
        ip = self.client_address[0]
        now = time.time()
        with _lock:
            hits = [t for t in _fails.get(ip, []) if now - t < FAIL_WINDOW]
            _fails[ip] = hits
            if len(hits) >= FAIL_LIMIT:
                return self._json(429, {"error": "尝试次数过多，请稍后再试"})
        try:
            length = min(int(self.headers.get("Content-Length") or 0), 4096)
            payload = json.loads(self.rfile.read(length) or b"{}")
        except Exception:
            payload = {}
        given = str(payload.get("password") or "")
        if not hmac.compare_digest(given.encode(), PASSWORD.encode()):
            with _lock:
                _fails.setdefault(ip, []).append(now)
            time.sleep(0.6)
            return self._json(401, {"error": "密码错误"})
        cookie = f"{COOKIE}={_token(int(now))}; Path=/; Max-Age={TTL}; HttpOnly; SameSite=Lax{self._secure_flag()}"
        return self._send(200, json.dumps({"ok": True}), "application/json; charset=utf-8",
                          {"Set-Cookie": cookie})

    def _static(self, name: str):
        if not name or "/" in name or "\\" in name or name.startswith("."):
            return self._json(404, {"error": "not found"})
        path = os.path.join(STATIC, name)
        try:
            data = open(path, "rb").read()
        except OSError:
            return self._json(404, {"error": "not found"})
        ctype = "text/css; charset=utf-8" if name.endswith(".css") else "application/javascript; charset=utf-8"
        etag = '"%d-%s"' % (len(data), hashlib.sha1(data).hexdigest()[:16])
        if self.headers.get("If-None-Match") == etag:
            self.send_response(304)
            self.send_header("ETag", etag)
            self.send_header("Cache-Control", "public, max-age=604800, immutable")
            self.send_header("Content-Length", "0")
            self.end_headers()
            return
        body = gzip.compress(data, 6) if self._accepts_gzip() else data
        self.send_response(200)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "public, max-age=604800, immutable")
        self.send_header("ETag", etag)
        self.send_header("Vary", "Accept-Encoding")
        if body is not data:
            self.send_header("Content-Encoding", "gzip")
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(body)

    def _download(self):
        target = (parse_qs(urlparse(self.path).query).get("f") or [""])[0]
        allowed = {item["id"] for item in scan()}
        if target not in allowed:
            return self._json(404, {"error": "not found"})
        path = os.path.join(ROOT, *target.split("/"))
        try:
            f = open(path, "rb")
        except OSError:
            return self._json(404, {"error": "not found"})
        with f:
            size = os.fstat(f.fileno()).st_size
            start, end = 0, size - 1
            code = 200
            rng = self.headers.get("Range") or ""
            m = re.fullmatch(r"bytes=(\d*)-(\d*)", rng.strip())
            if m and (m.group(1) or m.group(2)):
                if m.group(1):
                    start = int(m.group(1)); end = int(m.group(2)) if m.group(2) else size - 1
                else:
                    start = max(0, size - int(m.group(2))); end = size - 1
                if start > end or start >= size:
                    self.send_response(416)
                    self.send_header("Content-Range", f"bytes */{size}")
                    self.send_header("Content-Length", "0")
                    self.end_headers()
                    return
                code = 206
            length = end - start + 1
            self.send_response(code)
            self.send_header("Content-Type", "application/vnd.android.package-archive")
            self.send_header("Content-Length", str(length))
            self.send_header("Accept-Ranges", "bytes")
            if code == 206:
                self.send_header("Content-Range", f"bytes {start}-{end}/{size}")
            self.send_header("Content-Disposition",
                             f'attachment; filename="{posixpath.basename(target)}"; filename*=UTF-8\'\'{quote(posixpath.basename(target))}')
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            if self.command == "HEAD":
                return
            f.seek(start)
            remaining = length
            while remaining > 0:
                chunk = f.read(min(256 * 1024, remaining))
                if not chunk:
                    break
                self.wfile.write(chunk)
                remaining -= len(chunk)


def main():
    server = ThreadingHTTPServer(("0.0.0.0", PORT), Handler)
    print(f"serving {ROOT} on :{PORT}", flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
