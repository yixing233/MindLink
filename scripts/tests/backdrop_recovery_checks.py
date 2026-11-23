"""Compile real backdrop production Java against private, controllable Android doubles.

These are deterministic host-JVM assertions, not Android/GPU rendering validation.
No public navigation doubles or regression sources are used.
"""

import argparse
from pathlib import Path
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SUITE = ROOT / "scripts/tests/backdrop-recovery"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jdk", required=True, type=Path)
    parser.add_argument("--compile-only", action="store_true")
    parser.add_argument("--negative-replay", action="store_true",
                        help="Replay the frozen old failure-budget and parent-background defects in isolation")
    args = parser.parse_args()
    executables = {}
    for name in ("javac", "java"):
        candidate = args.jdk / "bin" / (name + ".exe")
        executables[name] = candidate if candidate.is_file() else args.jdk / "bin" / name
        if not executables[name].is_file():
            parser.error(f"JDK executable is missing: {executables[name]}")
    sources = sorted(SUITE.rglob("*.java"))
    sources += [ROOT / "scripts/tests/wex/ui/core/feature/BackdropCaptureStateChecks.java"]
    sources += [ROOT / "java_src/wex/ui/core/feature" / (name + ".java") for name in
                ("BackdropCaptureState", "ViewBackdropSampler", "LiquidGlassLayer", "LiquidGlassShaders",
                 "ChatGlassSurface", "ConversationPullWallpaperGuard")]
    with tempfile.TemporaryDirectory(prefix="mindlink-backdrop-recovery-") as temporary:
        classes = Path(temporary) / "classes"
        classes.mkdir()
        empty_sourcepath = Path(temporary) / "empty-sourcepath"
        empty_sourcepath.mkdir()
        command = [str(executables["javac"]), "-J-Duser.language=en", "-J-Duser.country=US",
                   "-encoding", "UTF-8", "-implicit:none", "-sourcepath", str(empty_sourcepath),
                   "-classpath", str(classes), "-d", str(classes), *map(str, sources)]
        result = subprocess.run(command, cwd=ROOT, capture_output=True, text=True,
                                encoding="utf-8", errors="replace")
        print(result.stdout + result.stderr, end="", flush=True)
        if result.returncode:
            return result.returncode
        print("PASS: real BackdropCaptureState, ViewBackdropSampler, LiquidGlassLayer and shaders compiled", flush=True)
        if args.compile_only:
            return 0
        print("Scope: controllable host Canvas/RenderNode/SystemClock doubles; no GPU/device claim", flush=True)
        for check in ("BackdropCaptureStateChecks", "BackdropRecoveryChecks", "ChatGlassSurfaceChecks",
                      "ConversationPullWallpaperGuardChecks"):
            command = [str(executables["java"]), "-ea", "-cp", str(classes),
                       "wex.ui.core.feature." + check]
            result = subprocess.run(command, cwd=ROOT, capture_output=True, text=True,
                                    encoding="utf-8", errors="replace")
            print(result.stdout + result.stderr, end="", flush=True)
            if result.returncode:
                return result.returncode
        if args.negative_replay:
            frozen = ROOT / "build/module-effects-candidate-20261114-01/inputs/frozen-src/wex/ui/core/feature/ViewBackdropSampler.java"
            if not frozen.is_file():
                parser.error(f"Frozen negative replay source is missing: {frozen}")
            replay_source = Path(temporary) / "negative-src/wex/ui/core/feature/BackdropNegativeReplayChecks.java"
            replay_source.parent.mkdir(parents=True)
            replay_source.write_text(r'''package wex.ui.core.feature;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.RenderNode;
import android.graphics.drawable.ColorDrawable;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
public final class BackdropNegativeReplayChecks {
    static final Rect SAMPLE = new Rect(20,30,100,90);
    public static void main(String[] args) {
        RenderNode.reset(); SystemClock.setUptimeMillis(1000L);
        View source = new View(), consumer = new View(); consumer.windowToken=source.windowToken;
        ViewBackdropSampler sampler = new ViewBackdropSampler(source,consumer); sampler.start(); source.failDraws=30;
        check(!sampler.prepare(new Canvas(),SAMPLE), "first old failure");
        SystemClock.setUptimeMillis(1080L); consumer.dispatchDueCallbacks(); check(!sampler.prepare(new Canvas(),SAMPLE), "second old failure");
        SystemClock.setUptimeMillis(1320L); consumer.dispatchDueCallbacks(); check(!sampler.prepare(new Canvas(),SAMPLE), "third old failure");
        SystemClock.setUptimeMillis(2000L); source.failDraws=0; source.content="actual-new-host"; source.dirty=true;
        source.getViewTreeObserver().dispatchOnPreDraw();
        check(!sampler.prepare(new Canvas(),SAMPLE) && source.drawCalls==3, "old budget permanently refuses real new dirty source");
        System.out.println("RED confirmed: frozen source cannot recover three failures after actual host content changes"); sampler.stop();
        ViewGroup parent=new ViewGroup(); parent.height=800; ColorDrawable background=new ColorDrawable(0xFF112233); parent.background=background;
        View pager=new View(); pager.height=120; parent.add(pager); View overlay=new View(); overlay.y=500; parent.add(overlay);
        ViewBackdropSampler old=new ViewBackdropSampler(pager,overlay); old.source(parent,pager); old.start();
        check(old.prepare(new Canvas(),SAMPLE), "old parent initial capture");
        background.setColor(0xFF445566); parent.dirty=true; parent.getViewTreeObserver().dispatchOnPreDraw();
        check(old.prepare(new Canvas(),SAMPLE) && pager.drawCalls==1, "old parent ignores independent observable color change");
        System.out.println("RED confirmed: frozen sampler reuses old parent background after same-Drawable color change"); old.stop();
    }
    static void check(boolean value,String message) { if(!value)throw new AssertionError(message); }
}
''', encoding="utf-8")
            budget = ROOT / "java_src/wex/ui/core/feature/BackdropCaptureState.java"
            replay_budget = replay_source.parent / "BackdropCaptureState.java"
            current = budget.read_text(encoding="utf-8")
            start = current.index("    private void updateBudget(Key key) {")
            end = current.index("    Decision decide(Key key, long now)", start)
            old_budget = '''    private void updateBudget(Key key) {
        if (failed != null && !failed.sameEnvironment(key)) {
            failed = null; failures = 0; retryAt = 0L;
        }
    }

'''
            replay_budget.write_text(current[:start] + old_budget + current[end:], encoding="utf-8")
            replay_classes = Path(temporary) / "negative-classes"
            replay_classes.mkdir()
            replay_sources = sorted((SUITE / "android").rglob("*.java"))
            replay_sources += [SUITE / "wex/ui/core/feature/NavigationBarFeature.java", frozen, replay_budget, replay_source]
            command = [str(executables["javac"]), "-encoding", "UTF-8", "-implicit:none", "-sourcepath", str(empty_sourcepath),
                       "-classpath", str(replay_classes), "-d", str(replay_classes), *map(str, replay_sources)]
            result = subprocess.run(command, cwd=ROOT, capture_output=True, text=True, encoding="utf-8", errors="replace")
            print(result.stdout + result.stderr, end="", flush=True)
            if result.returncode:
                return result.returncode
            result = subprocess.run([str(executables["java"]), "-ea", "-cp", str(replay_classes),
                                     "wex.ui.core.feature.BackdropNegativeReplayChecks"], cwd=ROOT,
                                    capture_output=True, text=True, encoding="utf-8", errors="replace")
            print(result.stdout + result.stderr, end="", flush=True)
            if result.returncode:
                return result.returncode
    return 0


if __name__ == "__main__":
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    raise SystemExit(main())
