"""Check scoped class ownership and strict candidate resource semantics without compiling."""

import importlib.util
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]


def load(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
    return module


def main():
    scope = load("mindlink_scoped_test", ROOT / "scripts/build_scoped_features.py")
    gates = load("mindlink_candidate_test", ROOT / "scripts/tests/complete_candidate_checks.py")
    assert len(scope.FAMILIES) == 22
    for name in ("ChatFooterOverlay", "ChatTailSpacer", "ChatGlassSurface", "FloatingHeaderStyle"):
        assert scope.target("Lwex/ui/core/feature/" + name + ";")
    assert scope.target("Lwex/ui/core/feature/HomeSidePanelSnapshot;")
    assert scope.target("Lwex/ui/core/feature/HomeSidePanelSnapshot$NativeStatusIcon;")
    assert scope.target("Lwex/ui/core/feature/HomeSidePanelSnapshot-IA;")
    assert not scope.target("Lwex/ui/core/feature/HomeSidePanelFeature-IA;")
    assert not scope.target("Lwex/ui/core/feature/HomeSidePanelSnapshotOther;")
    assert not scope.target("Lwex/ui/core/feature/HomeSidePanelSnapshot-Other;")
    for name in ["BubbleApply", "BubbleGlass", "FloatingChatHeaderFeature", "TextStatusBridge", "HostTargetResolver"]:
        assert not scope.target("Lwex/ui/core/feature/" + name + ";"), name
    assert gates.same_value(("value", (28, 4280125243)), ("value", (29, 4280125243)))
    assert not gates.same_value(("value", (28, 4280125243)), ("value", (29, 4280125244)))
    assert not gates.same_value(("value", (28, 2130706432)), ("value", (29, 2130706432)))
    assert not gates.same_value(("value", (1, 0x7f030001)), ("value", (1, 0x7f030002)))
    print("PASS: exact scoped ownership, retained functional families and strict Android resource semantics")


if __name__ == "__main__":
    main()
