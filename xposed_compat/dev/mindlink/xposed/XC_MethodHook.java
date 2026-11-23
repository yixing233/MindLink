package dev.mindlink.xposed;

import java.lang.reflect.Member;
import java.util.HashMap;

/** 与 de.robv XC_MethodHook 同形的回调基类；运行期由 libxposed 拦截链驱动。 */
public abstract class XC_MethodHook {
    public static final int PRIORITY_DEFAULT = 50;
    public static final int PRIORITY_LOWEST = -10000;
    public static final int PRIORITY_HIGHEST = 10000;

    public final int priority;

    public XC_MethodHook() {
        this(PRIORITY_DEFAULT);
    }

    public XC_MethodHook(int priority) {
        this.priority = priority;
    }

    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
    }

    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
    }

    public static class MethodHookParam {
        public Member method;
        public Object thisObject;
        public Object[] args;
        private Object result;
        private Throwable throwable;
        boolean returnEarly;
        private HashMap<String, Object> extra;

        public Object getResult() {
            return result;
        }

        public void setResult(Object result) {
            this.result = result;
            this.throwable = null;
            this.returnEarly = true;
        }

        public Throwable getThrowable() {
            return throwable;
        }

        public boolean hasThrowable() {
            return throwable != null;
        }

        public void setThrowable(Throwable throwable) {
            this.throwable = throwable;
            this.result = null;
            this.returnEarly = true;
        }

        public Object getResultOrThrowable() throws Throwable {
            if (throwable != null) throw throwable;
            return result;
        }

        public synchronized Object getObjectExtra(String key) {
            return extra == null ? null : extra.get(key);
        }

        public synchronized void setObjectExtra(String key, Object value) {
            if (extra == null) extra = new HashMap<>();
            extra.put(key, value);
        }

        void outcome(Object result, Throwable throwable) {
            this.result = result;
            this.throwable = throwable;
        }
    }

    public static final class Unhook {
        private final Member hookMethod;
        private final XC_MethodHook callback;
        private volatile XposedBridge.HookToken token;

        Unhook(Member hookMethod, XC_MethodHook callback, XposedBridge.HookToken token) {
            this.hookMethod = hookMethod;
            this.callback = callback;
            this.token = token;
        }

        public Member getHookedMethod() {
            return hookMethod;
        }

        public XC_MethodHook getCallback() {
            return callback;
        }

        public void unhook() {
            XposedBridge.HookToken current = token;
            token = null;
            if (current != null) XposedBridge.unhook(current);
        }
    }
}
