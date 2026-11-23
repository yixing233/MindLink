package dev.mindlink.xposed;

import java.lang.reflect.Member;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Only callback registration/delivery; production observe/read/capture code remains real. */
public final class XposedBridge {
    public static final Map<Member, List<XC_MethodHook>> hooks = new LinkedHashMap<>();
    public static final Map<Member, Integer> attempts = new LinkedHashMap<>();
    public static final Map<Member, Throwable> failures = new LinkedHashMap<>();
    public static final List<String> logs = new java.util.concurrent.CopyOnWriteArrayList<>();
    public static final List<String> events = new java.util.concurrent.CopyOnWriteArrayList<>();
    public static Throwable logFailure;
    public static Member firstCallbackTarget;
    public static Object firstCallbackOwner;
    public static Object firstCallbackResult;
    public static Object[] firstCallbackArgs = new Object[0];
    public static Throwable failAfterFirstCallback;

    @SuppressWarnings("unchecked")
    public static <T extends Throwable> void raise(Throwable error) throws T { throw (T) error; }

    public static XC_MethodHook.Unhook hookMethod(Member member, XC_MethodHook callback) {
        attempts.merge(member, 1, Integer::sum);
        if (failures.containsKey(member)) XposedBridge.<RuntimeException>raise(failures.get(member));
        hooks.computeIfAbsent(member, ignored -> new ArrayList<>()).add(callback);
        if (member.equals(firstCallbackTarget)) {
            try { after(member, firstCallbackOwner, firstCallbackResult, null, firstCallbackArgs); }
            catch (Throwable error) { XposedBridge.<RuntimeException>raise(error); }
            if (failAfterFirstCallback != null) {
                hooks.get(member).remove(callback);
                XposedBridge.<RuntimeException>raise(failAfterFirstCallback);
            }
        }
        return callback.new Unhook();
    }

    public static void log(String text) {
        logs.add(text);
        events.add("log " + text);
        if (logFailure != null) XposedBridge.<RuntimeException>raise(logFailure);
    }

    public static void after(Member member, Object owner, Object result) throws Throwable {
        after(member, owner, result, null, new Object[0]);
    }
    public static void after(Member member, Object owner, Object result, Throwable error, Object[] args) throws Throwable {
        List<XC_MethodHook> callbacks = hooks.get(member);
        if (callbacks == null || callbacks.isEmpty()) throw new AssertionError("Unregistered callback target");
        XC_MethodHook.MethodHookParam param = new XC_MethodHook.MethodHookParam();
        param.method = member; param.thisObject = owner; param.args = args;
        param.setResult(result); param.setThrowable(error);
        for (XC_MethodHook callback : new ArrayList<>(callbacks)) callback.afterHookedMethod(param);
    }

    public static int getXposedVersion() { return 0; }
}
