package de.robv.android.xposed;

import java.lang.reflect.*;
import java.util.*;

/** Explicitly dispatches the real registered hooks around dummy host methods. */
public final class XposedBridge {
    public static final Map<Method, List<XC_MethodHook>> hooks = new LinkedHashMap<>();
    public static String failHook;
    public static void log(String text) { }
    public static void hookMethod(Method method, XC_MethodHook hook) {
        if (method.getName().equals(failHook)) throw new IllegalStateException("dummy hook failure");
        hooks.computeIfAbsent(method, ignored -> new ArrayList<>()).add(hook);
    }
    public static void hookAllMethods(Class<?> owner, String name, XC_MethodHook hook) {
        for (Method method : owner.getDeclaredMethods()) if (method.getName().equals(name)) hookMethod(method, hook);
    }
    public static Object invokeOriginalMethod(Method method, Object owner, Object[] args) throws Throwable {
        try { method.setAccessible(true); return method.invoke(owner, args); }
        catch (InvocationTargetException error) { throw error.getCause(); }
    }
    public static Object invoke(Object owner, String name, Class<?>[] types, Object... args) throws Throwable {
        Method method = null;
        for (Class<?> type = owner.getClass(); type != null && method == null; type = type.getSuperclass()) {
            try { method = type.getDeclaredMethod(name, types); } catch (NoSuchMethodException ignored) { }
        }
        if (method == null) throw new NoSuchMethodException(name);
        XC_MethodHook.MethodHookParam param = new XC_MethodHook.MethodHookParam();
        param.method = method; param.thisObject = owner; param.args = args;
        List<XC_MethodHook> callbacks = hooks.getOrDefault(method, Collections.emptyList());
        int entered = 0;
        for (XC_MethodHook hook : callbacks) {
            entered++; hook.beforeHookedMethod(param);
            if (param.returnEarly) break;
        }
        if (!param.returnEarly) {
            try { param.result = invokeOriginalMethod(method, owner, param.args); }
            catch (Throwable error) { param.throwable = error; }
        }
        for (int i = entered - 1; i >= 0; i--) callbacks.get(i).afterHookedMethod(param);
        if (param.throwable != null) throw param.throwable;
        return param.result;
    }

    public static int getXposedVersion() { return 0; }
}
