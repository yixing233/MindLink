package dev.mindlink.xposed;

import java.lang.reflect.Member;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Registry and explicit callback delivery, never a replacement capture algorithm. */
public final class XposedBridge {
    public static final Map<Member, List<XC_MethodHook>> hooks = new LinkedHashMap<>();
    public static final Map<Member, Integer> attempts = new LinkedHashMap<>();
    public static final Map<Member, Throwable> failures = new LinkedHashMap<>();
    public static final List<String> logs = new ArrayList<>();
    public static Throwable logFailure;

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void raise(Throwable error) throws T { throw (T) error; }

    public static XC_MethodHook.Unhook hookMethod(Member member, XC_MethodHook callback) {
        attempts.merge(member, 1, Integer::sum);
        if (failures.containsKey(member)) XposedBridge.<RuntimeException>raise(failures.get(member));
        hooks.computeIfAbsent(member, ignored -> new ArrayList<>()).add(callback);
        return callback.new Unhook();
    }

    public static void log(String text) {
        logs.add(text);
        if (logFailure != null) XposedBridge.<RuntimeException>raise(logFailure);
    }

    public static void after(Member member, Object owner, Object result) throws Throwable {
        after(member, owner, result, null);
    }

    public static void after(Member member, Object owner, Object result, Throwable failure) throws Throwable {
        List<XC_MethodHook> callbacks = hooks.get(member);
        if (callbacks == null || callbacks.isEmpty()) throw new AssertionError("No callback registered for " + member);
        XC_MethodHook.MethodHookParam param = new XC_MethodHook.MethodHookParam();
        param.method = member;
        param.thisObject = owner;
        param.setResult(result);
        param.setThrowable(failure);
        for (XC_MethodHook callback : new ArrayList<>(callbacks)) callback.afterHookedMethod(param);
    }

    public static int getXposedVersion() { return 0; }
}
