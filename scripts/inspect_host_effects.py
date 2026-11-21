"""Collect content-free MindLink hook stages and read-only LSPosed enable/scope evidence.

Device logs are filtered to module tags before collection, then reduced to known
stages, error types and counters. No original log lines, user profiles, SQL
arguments or unrelated module configuration are published or stored here.
"""

import argparse
import hashlib
import json
from pathlib import Path
import re
import sqlite3
import subprocess
import sys

sys.stdout.reconfigure(encoding="utf-8", errors="replace")

PREFIXES = {
    "bootstrap": "WEX: 新功能启动: ",
    "navigation": "WEX: 底栏: ",
    "footer": "WEX: 悬浮输入栏: ",
    "database": "WEX: 数据库桥: ",
    "profile": "WEX: 本人资料: ",
    "status": "WEX: 微信状态: ",
    "resolver": "WEX: 目标解析: ",
}
SAFE_STAGES = [
    r"stage=baseline registered enabled=(?:true|false)",
    r"stage=observe registered attached=(?:true|false)",
    r"stage=discover (?:found attempts=\d+|exhausted budget=\d+)",
    r"stage=resolve started",
    r"stage=hook registered [A-Za-z0-9_.$#]+",
    r"stage=hook failed [A-Za-z0-9_$]+",
    r"stage=storage-observe registered=\d+",
    r"stage=capture waiting for initialized CoreStorage database",
    r"stage=capture (?:ready [A-Za-z0-9_.$#(),\[\];]+|failed [A-Za-z0-9_$]+)",
    r"背景采样 reason=[A-Z_]+ visited=\d+/\d+",
    r"(?:本人资料|本人配置存储|数据库桥|宿主目标解析|首页底栏|悬浮输入栏|悬浮顶栏|系统栏适配) (?:已初始化|初始化失败 [A-Za-z0-9_$]+)",
    r"(?:读取失败|读取提交失败|刷新失败|配置资料读取失败|昵称读取失败|头像读取失败|头像地址读取失败|数据库入口不可用|状态账号确认失败|解析失败|调用失败) [A-Za-z0-9_$]+",
]
MARKERS = ["替换栏已挂载", "当前背景不可采样", "真实背景采样失败", "原生底栏接线失败", "分页回调类不可用", "状态服务尚未就绪"]
ERROR_TYPES = ["ExceptionInInitializerError", "NoSuchMethodError", "NoClassDefFoundError", "StackOverflowError", "VerifyError", "IllegalStateException", "IllegalArgumentException", "IllegalAccessException", "InvocationTargetException"]


def run(adb, serial, command, binary=False):
    result = subprocess.run([str(adb), "-s", serial, "exec-out", "su", "-c", command], capture_output=True, timeout=30)
    if result.returncode:
        raise RuntimeError("read-only device command failed with exit " + str(result.returncode))
    return result.stdout if binary else result.stdout.decode("utf-8", errors="replace")


def digest_remote(adb, serial, path):
    value = run(adb, serial, "toybox sha256sum " + path)
    match = re.match(r"([0-9a-f]{64})\s", value)
    if not match:
        raise RuntimeError("device hash unavailable")
    return match[1]


def framework_state(connection, user_id=0):
    tables = {row[0] for row in connection.execute("SELECT name FROM sqlite_master WHERE type='table'")}
    if not {"modules", "scope"} <= tables:
        return {"snapshot_stable": True, "supported_schema": False}
    module_cols = {row[1] for row in connection.execute("PRAGMA table_info(modules)")}
    scope_cols = {row[1] for row in connection.execute("PRAGMA table_info(scope)")}
    result = {"snapshot_stable": True, "supported_schema": True, "user_id": user_id}
    if {"module_pkg_name", "apk_path"} <= module_cols and {"module_pkg_name", "app_pkg_name", "user_id"} <= scope_cols and "modules_state" in tables:
        state_cols = {row[1] for row in connection.execute("PRAGMA table_info(modules_state)")}
        if not {"module_pkg_name", "user_id", "enabled", "scope_request_blocked"} <= state_cols:
            return {"snapshot_stable": True, "supported_schema": False}
        row = connection.execute("SELECT apk_path FROM modules WHERE module_pkg_name=?", ("dev.mindlink",)).fetchone()
        result["module_found"] = row is not None
        if row:
            state = connection.execute("SELECT enabled,scope_request_blocked FROM modules_state WHERE module_pkg_name=? AND user_id=?", ("dev.mindlink", user_id)).fetchone()
            result["enable_record_found"] = state is not None
            result["module_enabled"] = bool(state[0]) if state else None
            result["scope_request_blocked"] = bool(state[1]) if state else None
            result["recorded_apk_path"] = row[0]
            result["wechat_scope_present"] = connection.execute("SELECT 1 FROM scope WHERE module_pkg_name=? AND app_pkg_name=? AND user_id=? LIMIT 1", ("dev.mindlink", "com.tencent.mm", user_id)).fetchone() is not None
        result["schema"] = "per-user-module-state"
        return result
    if {"mid", "module_pkg_name", "enabled", "apk_path"} <= module_cols and {"mid", "app_pkg_name"} <= scope_cols:
        if user_id != 0 and "user_id" not in scope_cols:
            return {"snapshot_stable": True, "supported_schema": False}
        row = connection.execute("SELECT mid,enabled,apk_path FROM modules WHERE module_pkg_name=?", ("dev.mindlink",)).fetchone()
        result["module_found"] = row is not None
        if row:
            result["module_enabled"] = bool(row[1])
            result["recorded_apk_path"] = row[2]
            sql = "SELECT 1 FROM scope WHERE mid=? AND app_pkg_name=?"
            params = (row[0], "com.tencent.mm")
            if "user_id" in scope_cols:
                sql += " AND user_id=?"
                params += (user_id,)
            result["wechat_scope_present"] = connection.execute(sql + " LIMIT 1", params).fetchone() is not None
        result["schema"] = "legacy-module-state"
        return result
    return {"snapshot_stable": True, "supported_schema": False, "module_columns": sorted(module_cols), "scope_columns": sorted(scope_cols)}


def framework(adb, serial, output):
    remote = "/data/adb/lspd/config/modules_config.db"
    before = digest_remote(adb, serial, remote)
    data = run(adb, serial, "toybox dd if=" + remote + " bs=16384 2>/dev/null", binary=True)
    after = digest_remote(adb, serial, remote)
    if before != after or hashlib.sha256(data).hexdigest() != before:
        return {"snapshot_stable": False}
    snapshot = output / "framework-readonly"
    snapshot.mkdir()
    path = snapshot / "modules_config.db"
    path.write_bytes(data)
    connection = sqlite3.connect(path.resolve().as_uri() + "?mode=ro&immutable=1", uri=True)
    try:
        connection.execute("PRAGMA query_only=ON")
        return framework_state(connection)
    finally:
        connection.close()


def logs(adb, serial):
    names = run(adb, serial, "toybox ls -1 /data/adb/lspd/log").splitlines()
    files = sorted(name for name in names if re.fullmatch(r"modules_[0-9T:.\-]+\.log", name))
    selected = list(dict.fromkeys(files[:2] + files[-2:]))
    result = []
    terms = " ".join("-e '" + prefix.rstrip() + "'" for prefix in PREFIXES.values())
    for filename in selected:
        command = "toybox grep -F " + terms + " /data/adb/lspd/log/" + filename
        proc = subprocess.run([str(adb), "-s", serial, "exec-out", "su", "-c", command], capture_output=True, timeout=30)
        if proc.returncode not in (0, 1):
            result.append({"file": filename, "read_failed": True})
            continue
        text = proc.stdout.decode("utf-8", errors="replace")
        stages = []
        component_counts = {name: text.count(prefix) for name, prefix in PREFIXES.items()}
        for line in text.splitlines():
            for component, prefix in PREFIXES.items():
                if prefix not in line:
                    continue
                message = line.split(prefix, 1)[1]
                for pattern in SAFE_STAGES:
                    match = re.search(pattern, message)
                    if match:
                        safe = {"component": component, "stage": match[0]}
                        timestamp = re.search(r"\d{4}-\d{2}-\d{2}[ T]\d{2}:\d{2}:\d{2}(?:\.\d+)?", line.split(prefix, 1)[0])
                        if timestamp:
                            safe["timestamp"] = timestamp[0]
                        stages.append(safe)
        # Preserve first registration and last observable results, not unbounded repeats.
        unique = []
        seen = set()
        for stage in stages:
            key = stage["component"], stage["stage"]
            if key not in seen:
                seen.add(key)
                unique.append(stage)
        result.append({"file": filename, "module_component_counts": component_counts,
                       "known_effect_marker_counts": {marker: text.count(marker) for marker in MARKERS},
                       "error_type_counts": {name: text.count(name) for name in ERROR_TYPES},
                       "unique_safe_stages": unique[:60], "last_safe_stages": stages[-12:]})
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", required=True, type=Path)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    args.output.mkdir(exist_ok=False, parents=True)
    report = {"framework": framework(args.adb, args.serial, args.output), "module_logs": logs(args.adb, args.serial),
              "read_only": True, "raw_module_logs_saved": False, "device_configuration_changed": False}
    (args.output / "sanitized-effect-evidence.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    try:
        main()
    except (OSError, RuntimeError, sqlite3.Error, subprocess.TimeoutExpired) as error:
        print("Evidence read failed: " + type(error).__name__, file=sys.stderr)
        raise SystemExit(1)
