package de.robv.android.xposed;

public class XposedBridge {
    public static String lastLog;
    public static void log(String value) { lastLog = value; }

    public static int getXposedVersion() { return 0; }
}
