"""Dedicated offline SidePanel regression runner; never builds desktop or accesses a device.

Android/JSON fixtures are generated only under the requested output directory. Compose runtime,
LifecycleRegistry and createLifecycleAwareWindowRecomposer remain the actual pinned artifacts.
This is a JVM boundary test, NOT AndroidComposeView or rendered-pixel validation.
"""
from pathlib import Path
import argparse
import hashlib
import json
import subprocess
import sys

FIXTURES = {
    "android/os/Bundle.java": r'''package android.os;
import java.util.*;
public class Bundle {
 private final Map<String,Object> values = new LinkedHashMap<>();
 public Bundle() {}
 public Bundle(Bundle other) { values.putAll(other.values); }
 public Object get(String key) { return values.get(key); }
 public Set<String> keySet() { return values.keySet(); }
 public boolean containsKey(String key) { return values.containsKey(key); }
 public void putString(String key,String value) { values.put(key,value); }
 public void putBoolean(String key,boolean value) { values.put(key,value); }
 public void putInt(String key,int value) { values.put(key,value); }
 public void putLong(String key,long value) { values.put(key,value); }
 public void putDouble(String key,double value) { values.put(key,value); }
 public void putFloat(String key,float value) { values.put(key,value); }
 public void putBundle(String key,Bundle value) { values.put(key,value); }
 public <T> void putParcelableArrayList(String key,ArrayList<T> value) { values.put(key,value); }
 public Bundle getBundle(String key) { return (Bundle) values.get(key); }
 public String getString(String key) { return (String) values.get(key); }
}''',
    "android/os/Looper.java": r'''package android.os;
public class Looper {
 private static final Looper MAIN = new Looper();
 public static Looper myLooper() { return MAIN; }
 public static Looper getMainLooper() { return MAIN; }
 public Thread getThread() { return Thread.currentThread(); }
}''',
    "android/os/Trace.java": r'''package android.os;
public class Trace { public static void beginSection(String name) {} public static void endSection() {} public static boolean isEnabled() { return false; } }''',
    "android/graphics/Bitmap.java": r'''package android.graphics;
public class Bitmap { public boolean isRecycled() { return false; } public int getWidth() { return 24; } public int getHeight() { return 24; } }''',
    "android/content/Context.java": r'''package android.content;
public class Context { public Context getApplicationContext() { return this; } }''',
    "android/view/View.java": r'''package android.view;
import android.content.Context;
import java.util.*;
public class View {
 public interface OnAttachStateChangeListener { void onViewAttachedToWindow(View view); void onViewDetachedFromWindow(View view); }
 private final Context context;
 private final List<OnAttachStateChangeListener> listeners = new ArrayList<>();
 public View(Context context) { this.context = context; }
 public Context getContext() { return context; }
 public void addOnAttachStateChangeListener(OnAttachStateChangeListener listener) { listeners.add(listener); }
 public void removeOnAttachStateChangeListener(OnAttachStateChangeListener listener) { listeners.remove(listener); }
 public void fireDetached() { for (OnAttachStateChangeListener listener : new ArrayList<>(listeners)) listener.onViewDetachedFromWindow(this); }
 public int attachListenerCount() { return listeners.size(); }
}''',
    "org/json/JSONArray.java": r'''package org.json;
import java.util.*;
public class JSONArray {
 final List<Object> values = new ArrayList<>();
 public JSONArray put(Object value) { values.add(value); return this; }
 public int length() { return values.size(); }
 public JSONObject getJSONObject(int index) { return (JSONObject) values.get(index); }
}''',
    "org/json/JSONObject.java": r'''package org.json;
import java.util.*;
public class JSONObject {
 public static final Object NULL = new Object();
 private static final Map<String,Map<String,Object>> encoded = new HashMap<>();
 private final Map<String,Object> values = new LinkedHashMap<>();
 public JSONObject() {}
 public JSONObject(String text) { if (!encoded.containsKey(text)) throw new IllegalArgumentException("Fixture token missing"); values.putAll(encoded.get(text)); }
 public JSONObject put(String key,Object value) { values.put(key,value); return this; }
 public String getString(String key) { return (String) values.get(key); }
 public JSONArray getJSONArray(String key) { return (JSONArray) values.get(key); }
 public int getInt(String key) { return ((Number) values.get(key)).intValue(); }
 public int optInt(String key,int fallback) { Object value=values.get(key);return value instanceof Number?((Number)value).intValue():fallback; }
 public String optString(String key,String fallback) { Object value=values.get(key);return value instanceof String?(String)value:fallback; }
 public boolean optBoolean(String key,boolean fallback) { Object value=values.get(key);return value instanceof Boolean?(Boolean)value:fallback; }
 public JSONObject optJSONObject(String key) { Object value=values.get(key);return value instanceof JSONObject?(JSONObject)value:null; }
 public JSONArray optJSONArray(String key) { Object value=values.get(key);return value instanceof JSONArray?(JSONArray)value:null; }
 public boolean has(String key) { return values.containsKey(key); }
 public boolean isNull(String key) { return values.get(key)==null||values.get(key)==NULL; }
 public String toString() { String token="fixture-"+UUID.randomUUID();encoded.put(token,new LinkedHashMap<>(values));return token; }
}''',
}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", required=True)
    parser.add_argument("--ui-build", required=True)
    parser.add_argument("--runtime-inputs", required=True)
    parser.add_argument("--jdk", default="C:/Program Files/Microsoft/jdk-21.0.12.101-hotspot")
    args = parser.parse_args()
    output = Path(args.output).resolve()
    if output.exists() and any(output.iterdir()):
        raise SystemExit("Dedicated test output must be a new empty directory")
    output.mkdir(parents=True, exist_ok=True)
    fixture = output / "fixtures"
    classes = output / "classes"
    classes.mkdir()
    for name, body in FIXTURES.items():
        path = fixture / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(body, encoding="utf-8")
    inputs = json.loads(Path(args.runtime_inputs).read_text(encoding="utf-8"))
    project = Path(__file__).resolve().parent.parent
    state = project / "src/main/java/dev/mindlink/sidepanel/vendor/wekit/HomeSidePanelState.kt"
    payloads = project / "src/main/java/dev/mindlink/compose/SidePanelEditorPayloads.kt"
    layout = project.parent.parent / "java_src/wex/ui/core/feature/HomeSidePanelLayout.java"
    ui_classes = Path(args.ui_build).resolve() / "tmp/kotlin-classes/release"
    pinned = [path for path in inputs["compile_classpath"] if Path(path).name != "android.jar"]
    classpath = [str(classes), str(ui_classes), *pinned, inputs["android_jar"]]
    commands = []
    results = []
    def run(label, command):
        commands.append({"label": label, "command": command})
        result = subprocess.run(command, capture_output=True, text=True, encoding="utf-8", errors="replace", cwd=str(project.parent.parent))
        log = output / (label + ".log")
        log.write_text(result.stdout + result.stderr, encoding="utf-8")
        print(label, "exit", result.returncode, flush=True)
        if result.stdout:
            print(result.stdout, flush=True)
        if result.stderr:
            print(result.stderr, flush=True)
        results.append({"label": label, "exit_code": result.returncode, "log": str(log)})
        if result.returncode:
            raise RuntimeError(label + " failed")
    record = {"commands": commands, "results": results, "ui_build": str(Path(args.ui_build).resolve()),
              "claim": "JVM boundary checks using real pinned Compose/Lifecycle; only Android and JSON platform fixtures; no rendering/device validation",
              "source_inputs": {str(path): hashlib.sha256(path.read_bytes()).hexdigest()
                                for path in [Path(__file__), state, payloads, layout, project / "tests/SidePanelBoundaryChecks.kt", project / "tests/HomeSidePanelLayoutChecks.java"]}}
    try:
        run("fixture-compile", [str(Path(args.jdk) / "bin/javac.exe"), "-encoding", "UTF-8", "-d", str(classes), *map(str, fixture.rglob("*.java"))])
        run("layout-compile", [str(Path(args.jdk) / "bin/javac.exe"), "-encoding", "UTF-8", "-cp", str(classes), "-d", str(classes), str(layout), str(project / "tests/HomeSidePanelLayoutChecks.java")])
        run("layout-checks", [str(Path(args.jdk) / "bin/java.exe"), "-cp", str(classes), "wex.ui.core.feature.HomeSidePanelLayoutChecks"])
        run("state-real-runtime-compile", [str(Path(args.jdk) / "bin/java.exe"), "-cp", ";".join(inputs["compiler_classpath"]),
            "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler", "-no-stdlib", "-no-reflect", "-jvm-target", "17", "-classpath", ";".join(classpath), "-d", str(classes),
            str(state), str(payloads), str(project / "src/main/java/dev/mindlink/sidepanel/vendor/wekit/HomeSidePanelHitokoto.kt"), str(project / "tests/SidePanelBoundaryChecks.kt")])
        run("real-runtime-boundary-checks", [str(Path(args.jdk) / "bin/java.exe"), "-cp", ";".join(classpath), "dev.mindlink.compose.tests.SidePanelBoundaryChecks"])
        record["passed"] = True
    finally:
        (output / "test-result.json").write_text(json.dumps(record, indent=2), encoding="utf-8")


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    main()
