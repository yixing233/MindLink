package wex.ui.core.feature;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import dev.mindlink.xposed.XC_MethodHook;
import dev.mindlink.xposed.XposedBridge;

/** Reads WeKit's ConfigStorage identity fields only after a host-owned instance is observed. */
final class HostProfileBridge {
    static final int WXID = 2, NAME = 4;
    private static final List<WeakReference<Runnable>> LISTENERS = new ArrayList<>();
    private static final ThreadLocal<Boolean> READING = new ThreadLocal<>();
    private static volatile Method reader;
    private static volatile WeakReference<Object> storage = new WeakReference<>(null);
    private static volatile String status = "UNINITIALIZED";
    private static volatile boolean installed;
    private static long lastHostSignal;

    static final class Identity {
        final String account, nickname;
        Identity(String account, String nickname) { this.account = account; this.nickname = nickname; }
    }

    private HostProfileBridge() { }

    static synchronized void initialize() {
        if (installed) return;
        installed = true;
        status = "RESOLVING";
        try {
            HostTargetResolver.background(() -> {
                try {
                    List<Method> methods = HostTargetResolver.findMethods(new HostTargetResolver.MethodSpec()
                            .packages("com.tencent.mm.storage")
                            .classStrings("MicroMsg.ConfigStorage", "shouldProcessEvent db is close :%s")
                            .parameterTypes("int", "java.lang.Object").returnType("java.lang.Object"));
                    Method method = HostTargetResolver.unique(methods, "ConfigStorage identity reader");
                    if (Modifier.isStatic(method.getModifiers()) || Modifier.isAbstract(method.getModifiers()))
                        throw new IllegalStateException("ConfigStorage reader is not an instance implementation");
                    method.setAccessible(true);
                    reader = method;
                    XposedBridge.hookMethod(method, new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam param) {
                            if (Boolean.TRUE.equals(READING.get()) || HostDatabaseBridge.profileReadInProgress() || param.hasThrowable()) return;
                            // Identity notifications only; unrelated host settings reads are not refresh triggers.
                            if (param.args == null || param.args.length == 0 || !(param.args[0] instanceof Integer)) return;
                            int key = (Integer) param.args[0];
                            if (key == WXID || key == NAME) hostObserved(param.thisObject);
                        }
                    });
                    if (storage.get() == null) status = "HOOKED";
                    log("stage=hook registered");
                    HostDatabaseBridge.replayProfileCapture();
                } catch (Throwable error) {
                    reader = null; storage = new WeakReference<>(null);
                    status = "FAILED:" + error.getClass().getSimpleName();
                    installed = false;
                    log("stage=resolve failed " + error.getClass().getSimpleName());
                }
            });
        } catch (Throwable error) {
            installed = false;
            status = "FAILED:" + error.getClass().getSimpleName();
            log("stage=submit failed " + error.getClass().getSimpleName());
        }
    }

    static void subscribe(Runnable listener) {
        if (listener == null) return;
        boolean ready;
        synchronized (HostProfileBridge.class) {
            LISTENERS.add(new WeakReference<>(listener));
            ready = observed();
        }
        if (ready) notifyListener(listener);
    }

    static void invalidate() {
        synchronized (HostProfileBridge.class) {
            storage = new WeakReference<>(null); lastHostSignal = 0L;
            if (reader != null) status = "HOOKED";
        }
    }

    static boolean readInProgress() { return Boolean.TRUE.equals(READING.get()); }
    static boolean observed() { return storage.get() != null && reader != null && HostDatabaseBridge.configCurrent(storage.get()); }
    static Class<?> storageType() { Method method = reader; return method == null ? null : method.getDeclaringClass(); }
    static String status() { return status; }

    static boolean accepts(Object candidate) {
        Method method = reader;
        return candidate != null && method != null && method.getDeclaringClass().isInstance(candidate);
    }

    static void observe(Object candidate) {
        if (readInProgress() || HostDatabaseBridge.profileReadInProgress() || !accepts(candidate)) return;
        HostDatabaseBridge.captureConfigStorage(candidate);
    }

    /** Worker-only publication of an account-validated actual holder. */
    static void publishObserved(Object candidate) {
        if (!accepts(candidate) || !HostDatabaseBridge.configCurrent(candidate)) return;
        synchronized (HostProfileBridge.class) {
            if (!HostDatabaseBridge.configCurrent(candidate) || storage.get() == candidate) return;
            storage = new WeakReference<>(candidate);
            status = "OBSERVED"; lastHostSignal = System.nanoTime();
        }
        log("stage=capture observed");
        notifyReady();
    }

    static void hostObserved(Object candidate) {
        if (readInProgress() || HostDatabaseBridge.profileReadInProgress() || !accepts(candidate)) return;
        boolean changed = storage.get() != candidate;
        long now = System.nanoTime();
        synchronized (HostProfileBridge.class) {
            if (!changed && lastHostSignal != 0L && now - lastHostSignal < 2_000_000_000L) return;
            lastHostSignal = now;
        }
        // Throttle before any DB capture work, not after scanning.
        HostDatabaseBridge.captureConfigStorage(candidate);
        if (!changed && HostDatabaseBridge.configCurrent(candidate)) {
            log("stage=host-read signaled available=true"); notifyReady();
        }
    }

    private static void notifyReady() {
        List<Runnable> listeners = new ArrayList<>();
        synchronized (HostProfileBridge.class) {
            for (int i = LISTENERS.size() - 1; i >= 0; i--) {
                Runnable listener = LISTENERS.get(i).get();
                if (listener == null) LISTENERS.remove(i); else listeners.add(listener);
            }
        }
        for (Runnable listener : listeners) notifyListener(listener);
    }

    static Identity readIdentity() throws Exception {
        if (!HostDatabaseBridge.readCaptureCurrent()) throw new IllegalStateException("Observed account is not ready");
        Object current = storage.get();
        Method method = reader;
        long version = HostDatabaseBridge.captureVersion();
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()
                || current == null || method == null || !HostDatabaseBridge.configCurrent(current))
            throw new IllegalStateException("ConfigStorage requires current worker capture");
        Boolean previous = READING.get();
        READING.set(true);
        try {
            Object accountValue = method.invoke(current, WXID, "");
            if (!(accountValue instanceof String) || ((String) accountValue).trim().isEmpty())
                throw new IllegalStateException("ConfigStorage account unavailable");
            String nickname = null;
            try {
                Object nameValue = method.invoke(current, NAME, "");
                if (nameValue instanceof String) nickname = (String) nameValue;
            } catch (Throwable error) { log("stage=name failed " + error.getClass().getSimpleName()); }
            if (version != HostDatabaseBridge.captureVersion() || reader != method || storage.get() != current
                    || !HostDatabaseBridge.configCurrent(current) || !HostDatabaseBridge.readCaptureCurrent())
                throw new IllegalStateException("ConfigStorage capture changed during read");
            status = "IDENTITY_READY";
            log("stage=read ready account=true nickname=" + (nickname != null && !nickname.isEmpty()));
            return new Identity((String) accountValue, nickname);
        } finally {
            if (previous == null) READING.remove(); else READING.set(previous);
        }
    }

    private static void notifyListener(Runnable listener) {
        try { listener.run(); }
        catch (Throwable error) { log("stage=listener failed " + error.getClass().getSimpleName()); }
    }

    private static void log(String message) {
        HostStageLog.write("profile-config", message);
        try { XposedBridge.log("WEX: 本人配置: " + message); }
        catch (Throwable ignored) { }
    }
}
