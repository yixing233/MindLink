package de.robv.android.xposed;
import java.util.ArrayList;
import java.util.List;
public final class XposedBridge {
    public static final List<String> logs = new ArrayList<>();
    public static void log(String value) { logs.add(value); }
    public static void log(Throwable value) { logs.add(value.getClass().getSimpleName()); }
    public static void hookAllMethods(Class<?> type, String name, XC_MethodHook hook) { }

    public static int getXposedVersion() { return 0; }
}
