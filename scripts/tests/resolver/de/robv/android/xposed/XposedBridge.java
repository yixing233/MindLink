package de.robv.android.xposed;

import java.util.ArrayList;
import java.util.List;

public final class XposedBridge {
    public static final List<String> logs = new ArrayList<>();
    public static volatile Throwable logFailure;
    public static void log(String text) {
        logs.add(text);
        if (logFailure != null) XposedBridge.<RuntimeException>sneakyThrow(logFailure);
    }
    public static void hookMethod(java.lang.reflect.Member method, XC_MethodHook callback) {
        throw new AssertionError("Unexpected hook registration in resolver checks");
    }
    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void sneakyThrow(Throwable error) throws T {
        throw (T) error;
    }

    public static int getXposedVersion() { return 0; }
}
