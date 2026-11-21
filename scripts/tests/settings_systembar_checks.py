"""Settings systembar contract checks against full real SettingsShell + SettingsTheme.
Reuses read-only menu doubles, with only scoped Android additions; no device/payload writes.
"""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
FEATURE = ROOT / "java_src/wex/ui/core/feature"
SUITE = ROOT / "scripts/tests/systembars/wex/ui/core/feature/SettingsBarsChecks.java"


def fixtures():
    menu = ROOT / "scripts/tests/menu"
    sources = {p.relative_to(menu).as_posix(): p.read_text(encoding="utf-8")
               for p in (menu / "android").rglob("*.java")}
    sources["android/graphics/Typeface.java"] = sources["android/graphics/Typeface.java"].replace("DEFAULT_BOLD", "DEFAULT=new Typeface(), DEFAULT_BOLD")
    sources["android/widget/TextView.java"] = sources["android/widget/TextView.java"].replace("public TextView(", "public void setMinHeight(int v){setMinimumHeight(v);}public TextView(")
    sources["android/app/AlertDialog.java"] = sources["android/app/AlertDialog.java"].replace("public Builder setView(", "public Builder setMessage(String s){return this;}public Builder setView(")
    sources["android/content/Context.java"] = sources["android/content/Context.java"].replace("public Resources getResources()", "public int checkSelfPermission(String p){throw new AssertionError(\"No permission operations\");}public ClassLoader getClassLoader(){return getClass().getClassLoader();}public Resources getResources()")
    view = sources["android/view/View.java"]
    view = view.replace("private OnScrollChangeListener scrollListener;", """private OnScrollChangeListener scrollListener;
    private int ui;
    public int getSystemUiVisibility(){return ui;}
    public void setSystemUiVisibility(int value){ui=value;}
    public boolean focused;
    public boolean hasWindowFocus(){return getRootView().focused;}
    private final ViewTreeObserver tree=new ViewTreeObserver();
    public ViewTreeObserver getViewTreeObserver(){return tree;}
    public void focusForTest(boolean value){getRootView().focused=value;tree.focus(value);}
    """)
    sources["android/view/View.java"] = view
    sources["android/view/ViewTreeObserver.java"] = """package android.view;import java.util.*;
public class ViewTreeObserver {public interface OnWindowFocusChangeListener{void onWindowFocusChanged(boolean value);}
public final List<OnWindowFocusChangeListener> listeners=new ArrayList<>();public boolean isAlive(){return true;}
public void addOnWindowFocusChangeListener(OnWindowFocusChangeListener l){listeners.add(l);}
public void removeOnWindowFocusChangeListener(OnWindowFocusChangeListener l){listeners.remove(l);}
public void focus(boolean value){for(var l:new ArrayList<>(listeners))l.onWindowFocusChanged(value);}}
"""
    sources["android/view/WindowInsetsController.java"] = """package android.view;
public class WindowInsetsController {public static final int APPEARANCE_LIGHT_STATUS_BARS=8,APPEARANCE_LIGHT_NAVIGATION_BARS=16;
private int appearance;public int writes;public int getSystemBarsAppearance(){return appearance;}
public void setSystemBarsAppearance(int value,int mask){appearance=(appearance&~mask)|(value&mask);writes++;}}
"""
    window = sources["android/view/Window.java"].replace("private Drawable background;", """private Drawable background;
    public boolean ready;
    public final WindowInsetsController controller=new WindowInsetsController();
    public int status=0xFF000000,navigation=0xFF000000,colorWrites;
    public WindowInsetsController getInsetsController(){return ready?controller:null;}
    public int getStatusBarColor(){return status;}public void setStatusBarColor(int c){status=c;colorWrites++;}
    public int getNavigationBarColor(){return navigation;}public void setNavigationBarColor(int c){navigation=c;colorWrites++;}
    """)
    sources["android/view/Window.java"] = window
    sources["android/view/WindowInsets.java"] = sources["android/view/WindowInsets.java"].replace(
        "public static final class Type", "public int getSystemWindowInsetTop(){return safe.top;}public int getSystemWindowInsetBottom(){return safe.bottom;}public int getSystemWindowInsetLeft(){return safe.left;}public int getSystemWindowInsetRight(){return safe.right;}public static final class Type")
    sources["android/widget/ImageView.java"] = sources["android/widget/ImageView.java"].replace("FIT_CENTER", "FIT_CENTER,CENTER").replace(
        "public void setImageDrawable", "public void setImageBitmap(android.graphics.Bitmap b){}public void setImageDrawable")
    sources["android/graphics/Bitmap.java"] = "package android.graphics;public class Bitmap {}"
    sources["android/os/Process.java"] = "package android.os;public class Process{public static int myPid(){throw new AssertionError(\"No process operations\");}public static void killProcess(int p){throw new AssertionError(\"No process operations\");}}"
    sources["android/app/Dialog.java"] = """package android.app;import android.view.*;import android.content.*;
public class Dialog {public final Window window;public View root;public boolean showing;public int dismisses;
public Dialog(Context c){window=new Window(c);window.getDecorView().attachRootForTest(null);}public Window getWindow(){return window;}public boolean isShowing(){return showing;}
public void setContentView(View v){root=v;((ViewGroup)window.getDecorView()).addView(v);}
public void show(){showing=true;window.ready=true;root.getRootView().attachRootForTest(new Object());root.attachForTest();root.focusForTest(true);}
public void dismiss(){showing=false;dismisses++;root.getRootView().attachRootForTest(null);root.detachForTest();}}
"""
    sources["de/robv/android/xposed/XposedBridge.java"] = "package de.robv.android.xposed;public class XposedBridge {public static void log(String s){}}"
    sources["wex/ui/core/WexStore.java"] = "package wex.ui.core;public class WexStore {public static final WexStore INSTANCE=new WexStore();}"
    sources["wex/ui/core/feature/SettingsChanges.java"] = """package wex.ui.core.feature;public class SettingsChanges {
public interface Listener{void onChanged();void onFailure(String message);}public static boolean hasPending(){return false;}
public static void subscribe(Listener l){}public static void unsubscribe(Listener l){}
public static boolean getBool(wex.ui.core.WexStore s,String k,boolean d){return d;}public static boolean saveBool(String k,boolean v){throw new AssertionError("No preferences writes");}}
"""
    sources["wex/ui/core/feature/LocationPrivacy.java"] = "package wex.ui.core.feature;public class LocationPrivacy{public static final String KEY_ENABLED=\"location\";public static boolean isEnabled(){return false;}}"
    sources["wex/ui/core/feature/ImagePicker.java"] = "package wex.ui.core.feature;public class ImagePicker{public static final ImagePicker INSTANCE=new ImagePicker();public void toast(android.app.Activity a,String s){}}"
    # The panel body is an isolated Compose view; only the host contract is under test here.
    sources["android/os/Bundle.java"] = """package android.os;import java.util.*;
public class Bundle {private final Map<String,String> values=new LinkedHashMap<>();
public void putString(String k,String v){values.put(k,v);}public String getString(String k){return values.get(k);}
public void putBundle(String k,Bundle b){}public Bundle getBundle(String k){return null;}
public Set<String> keySet(){return values.keySet();}}
"""
    sources["wex/ui/core/feature/SettingsComposeBridge.java"] = """package wex.ui.core.feature;
public class SettingsComposeBridge {
public static android.view.View build(android.app.Activity a, android.os.Bundle s, android.os.Bundle v,
java.util.Map<String,java.util.function.Function<String,String>> w, java.util.Map<String,Runnable> c,
java.util.Map<String,java.util.function.Function<String,String>> r, boolean d){return new android.view.View(a);}
public static void dispose(android.view.View v){}}
"""
    sources["wex/ui/core/feature/SettingsPanelModel.java"] = """package wex.ui.core.feature;
public class SettingsPanelModel {
public final android.os.Bundle values=new android.os.Bundle();
public final java.util.Map<String,java.util.function.Function<String,String>> writers=new java.util.HashMap<>();
public final java.util.Map<String,Runnable> actions=new java.util.HashMap<>();
public final java.util.Map<String,java.util.function.Function<String,String>> readers=new java.util.HashMap<>();
static SettingsPanelModel build(android.app.Activity a){return new SettingsPanelModel();}}
"""
    return sources


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jdk", required=True, type=Path)
    parser.add_argument("--out", required=True, type=Path)
    parser.add_argument("--source-dir", type=Path, default=FEATURE)
    parser.add_argument("--baseline", action="store_true")
    args = parser.parse_args()
    args.out.mkdir(parents=True, exist_ok=False)
    actual = [args.source_dir / (n + ".java") for n in ("SettingsShell", "SettingsTheme")]
    actual += sorted((ROOT / "shared-ui/src/main/java").rglob("*.java"))
    sources = []
    for name, content in fixtures().items():
        path = args.out / "fixtures" / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")
        sources.append(path)
    manifest = {str(p.resolve()): hashlib.sha256(p.read_bytes()).hexdigest() for p in actual + [SUITE]}
    (args.out / "source-hashes.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    classes, empty = args.out / "classes", args.out / "empty-sourcepath"
    classes.mkdir(); empty.mkdir()
    logs = []
    def run(command):
        result = subprocess.run(list(map(str, command)), cwd=ROOT, capture_output=True, encoding="utf-8", errors="replace")
        logs.append(result.stdout + result.stderr)
        print(logs[-1], end="", flush=True)
        (args.out / "run.log").write_text("".join(logs), encoding="utf-8")
        return result.returncode
    rc = run([args.jdk / "bin/javac.exe", "-encoding", "UTF-8", "-proc:none", "-implicit:none", "-sourcepath", empty,
              "-classpath", classes, "-d", classes, *sources, *actual, SUITE])
    if rc: return rc
    for scenario in (["settings-light", "settings-dark"] if args.baseline else ["green"]):
        rc = run([args.jdk / "bin/java.exe", "-ea", "-cp", classes, "wex.ui.core.feature.SettingsBarsChecks", scenario])
        if args.baseline:
            if rc == 0 or "AssertionError" not in logs[-1]: raise AssertionError("Expected actual production assertion RED")
            text = "RED confirmed: full actual SettingsShell.build/SettingsTheme fail " + scenario + "\n"
            logs.append(text); print(text, end="", flush=True)
            (args.out / "run.log").write_text("".join(logs), encoding="utf-8")
        elif rc: return rc
    return 0


if __name__ == "__main__":
    if hasattr(sys.stdout, "reconfigure"): sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    raise SystemExit(main())
