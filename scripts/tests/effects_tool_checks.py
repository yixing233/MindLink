"""Read-only schema and exact-family boundary checks for the effects tools."""

import importlib.util
from pathlib import Path
import sqlite3

ROOT = Path(__file__).resolve().parents[2]


def load(name, relative):
    spec = importlib.util.spec_from_file_location(name, ROOT / relative)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def main():
    collector = load("effects_schema_fixture", "scripts/inspect_host_effects.py")
    packager = load("effects_package_scope_fixture", "scripts/build_effects_candidate.py")
    verifier = load("effects_verifier_scope_fixture", "scripts/tests/effects_candidate_checks.py")
    checks = 0

    def check(value):
        nonlocal checks
        assert value
        checks += 1

    check(packager.FAMILIES == verifier.FAMILIES and len(packager.FAMILIES) == 10)
    for family in packager.FAMILIES:
        for descriptor in ("L" + family + ";", "L" + family + "$1;", "L" + family + "$Nested$2;"):
            check(packager.target(descriptor) and verifier.target(descriptor))
        for descriptor in ("L" + family + "-IA;", "L" + family + "Extra;", "L" + family + "SettingCard;"):
            check(not packager.target(descriptor) and not verifier.target(descriptor))
    for name in ("HomeSidePanelFeature", "HomeSidePanelData", "HomeSidePanelWallet", "ProfileView", "HomeSidePanelSnapshot",
                 "SidePanelComposeBridge", "ComposePayloadBridge", "ComposePayloadLoader", "ChatToolbarFeature", "FloatingChatHeaderFeature",
                 "BubbleFeature", "BubbleApply", "TextStatusBridge", "HostTargetResolver", "NavigationBarFeature", "BackdropCaptureState", "LiquidGlassLayer"):
        check(not packager.target("Lwex/ui/core/feature/" + name + ";"))
        check(not verifier.target("Lwex/ui/core/feature/" + name + "$1;"))

    with sqlite3.connect(":memory:") as connection:
        connection.executescript("""
            CREATE TABLE modules(module_pkg_name TEXT PRIMARY KEY, apk_path TEXT);
            CREATE TABLE modules_state(module_pkg_name TEXT, user_id INTEGER, enabled BOOLEAN, scope_request_blocked BOOLEAN);
            CREATE TABLE scope(module_pkg_name TEXT, app_pkg_name TEXT, user_id INTEGER);
            INSERT INTO modules VALUES('dev.mindlink', '/fixture/mindlink.apk');
            INSERT INTO modules VALUES('unrelated.module', '/fixture/unrelated.apk');
            INSERT INTO modules_state VALUES('dev.mindlink', 0, 1, 0);
            INSERT INTO modules_state VALUES('dev.mindlink', 10, 0, 1);
            INSERT INTO modules_state VALUES('unrelated.module', 0, 1, 0);
            INSERT INTO scope VALUES('dev.mindlink', 'com.tencent.mm', 0);
            INSERT INTO scope VALUES('dev.mindlink', 'different.host', 10);
            INSERT INTO scope VALUES('unrelated.module', 'com.tencent.mm', 10);
        """)
        connection.execute("PRAGMA query_only=ON")
        before = connection.total_changes
        state = collector.framework_state(connection)
        check(state["supported_schema"] and state["module_found"] and state["module_enabled"])
        check(state["enable_record_found"] and not state["scope_request_blocked"] and state["wechat_scope_present"])
        check(state["schema"] == "per-user-module-state" and state["user_id"] == 0)
        check(state["recorded_apk_path"] == "/fixture/mindlink.apk")
        secondary = collector.framework_state(connection, 10)
        check(not secondary["module_enabled"] and secondary["scope_request_blocked"])
        check(not secondary["wechat_scope_present"])
        absent = collector.framework_state(connection, 11)
        check(not absent["enable_record_found"] and absent["module_enabled"] is None and absent["scope_request_blocked"] is None)
        check(not absent["wechat_scope_present"])
        check(connection.total_changes == before)

    with sqlite3.connect(":memory:") as connection:
        connection.executescript("""
            CREATE TABLE modules(mid INTEGER PRIMARY KEY, module_pkg_name TEXT, enabled BOOLEAN, apk_path TEXT);
            CREATE TABLE scope(mid INTEGER, app_pkg_name TEXT);
            INSERT INTO modules VALUES(7, 'dev.mindlink', 1, '/fixture/legacy.apk');
            INSERT INTO scope VALUES(7, 'com.tencent.mm');
        """)
        connection.execute("PRAGMA query_only=ON")
        state = collector.framework_state(connection)
        check(state["supported_schema"] and state["module_enabled"] and state["wechat_scope_present"])
        check(state["schema"] == "legacy-module-state")
        check(not collector.framework_state(connection, 10)["supported_schema"])

    with sqlite3.connect(":memory:") as connection:
        connection.executescript("CREATE TABLE modules(module_pkg_name TEXT); CREATE TABLE scope(app_pkg_name TEXT);")
        connection.execute("PRAGMA query_only=ON")
        state = collector.framework_state(connection)
        check(not state["supported_schema"] and "module_enabled" not in state)

    print(f"PASS: effects packaging boundaries and read-only framework schemas ({checks} assertions)")


if __name__ == "__main__":
    main()
