package de.robv.android.xposed;

import java.lang.reflect.Constructor;
import java.lang.reflect.Member;

public final class XposedBridge {
    private XposedBridge() {}
    public static void log(String text) {}
    public static void log(Throwable t) {}
    public static int getXposedVersion() { return 0; }
    public static Object invokeOriginalMethod(Member method, Object receiver, Object[] args) throws Throwable { return null; }
    public static XC_MethodHook.Unhook hookMethod(Member hookMethod, XC_MethodHook callback) { return null; }
    public static java.util.Set<XC_MethodHook.Unhook> hookAllMethods(Class<?> hookClass, String methodName, XC_MethodHook callback) { return null; }
    public static java.util.Set<XC_MethodHook.Unhook> hookAllConstructors(Class<?> hookClass, XC_MethodHook callback) { return null; }
}
