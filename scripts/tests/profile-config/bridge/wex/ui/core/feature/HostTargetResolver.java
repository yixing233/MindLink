package wex.ui.core.feature;

import android.content.Context;
import dev.mindlink.xposed.callbacks.XC_LoadPackage;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;

/** Controlled result list and worker queue, not a copy of production profile behavior. */
public final class HostTargetResolver {
    static final Queue<Runnable> workers = new ArrayDeque<>();
    static List<Method> candidates = List.of();
    static List<Method> accountCandidates = List.of();
    static List<Method> readyCandidates = List.of();
    static MethodSpec lastSpec, accountSpec, readySpec;
    static Throwable accountResolveFailure, readyResolveFailure;
    static int submissions, searches, uniques, initializeCalls, databaseResolutions;
    static String uniqueDescription, databaseKey;
    static MethodSpec databaseSpec;
    static Throwable submitFailure, resolveFailure;
    private static MethodCallback databaseCallback;

    static ClassLoader selectedLoader;
    public static ClassLoader hostClassLoader() { return selectedLoader; }
    public static void initialize(XC_LoadPackage.LoadPackageParam load, Context context) {
        initializeCalls++;
        if (selectedLoader == null && load != null) selectedLoader = load.classLoader;
    }
    static void background(Runnable task) {
        submissions++;
        if (submitFailure != null) raise(submitFailure);
        workers.add(task);
    }
    static void runOne() {
        Runnable task = workers.poll();
        if (task == null) throw new AssertionError("No queued profile resolver task");
        onWorker(() -> { task.run(); return null; });
    }
    interface WorkerCall<T> { T call() throws Throwable; }
    static <T> T onWorker(WorkerCall<T> task) {
        java.util.concurrent.atomic.AtomicReference<T> value = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<Throwable> failure = new java.util.concurrent.atomic.AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper())
                    throw new AssertionError("Worker cannot impersonate MAIN");
                value.set(task.call());
            } catch (Throwable error) { failure.set(error); }
        }, "profile-config-fixture-worker");
        worker.start();
        try { worker.join(5000); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new AssertionError(error); }
        if (worker.isAlive()) { worker.interrupt(); throw new AssertionError("Profile worker did not finish"); }
        if (failure.get() != null) raise(failure.get());
        return value.get();
    }
    static void drain() {
        int remaining = 20;
        while (!workers.isEmpty()) {
            if (--remaining < 0) throw new AssertionError("Unbounded profile capture queue");
            runOne();
        }
    }
    static List<Method> findMethods(MethodSpec spec) throws Exception {
        searches++;
        if (spec.strings != null && Arrays.asList(spec.strings).contains("mCoreAccount not initialized!")) {
            accountSpec = spec;
            if (accountResolveFailure != null) raise(accountResolveFailure);
            return accountCandidates;
        }
        if (spec.strings != null && Arrays.asList(spec.strings).contains("changeToAccountHasReady")) {
            readySpec = spec;
            if (readyResolveFailure != null) raise(readyResolveFailure);
            return readyCandidates;
        }
        lastSpec = spec;
        if (resolveFailure != null) raise(resolveFailure);
        return candidates;
    }
    static Method unique(List<Method> methods, String description) {
        uniques++; uniqueDescription = description;
        if (methods.size() != 1) throw new IllegalStateException("Expected one config target");
        return methods.get(0);
    }
    public interface MethodCallback {
        void onResolved(Method method);
        void onFailure(Throwable error);
    }
    public static void resolveMethod(String key, MethodSpec spec, MethodCallback callback) {
        if (databaseCallback != null) throw new AssertionError("Duplicate database resolve");
        databaseKey = key; databaseSpec = spec; databaseCallback = callback; databaseResolutions++;
    }
    static void resolveDatabase(Method method) {
        MethodCallback callback = databaseCallback; databaseCallback = null;
        if (callback == null) throw new AssertionError("No pending database resolver callback");
        callback.onResolved(method);
    }
    @SuppressWarnings("unchecked")
    static <E extends Throwable> void raise(Throwable error) throws E { throw (E) error; }

    public static final class MethodSpec {
        String[] packages, strings, classStrings, parameterTypes;
        String returnType, declaredClass, name;
        int count = -1, access = -1;
        public MethodSpec name(String value) { name = value; return this; }
        public MethodSpec declaredClass(String value) { declaredClass = value; return this; }
        public MethodSpec modifiers(int value) { access = value; return this; }
        public MethodSpec packages(String... values) { packages = values.clone(); return this; }
        public MethodSpec usingStrings(String... values) { strings = values.clone(); return this; }
        public MethodSpec classStrings(String... values) { classStrings = values.clone(); return this; }
        public MethodSpec parameterTypes(String... values) { parameterTypes = values.clone(); return this; }
        public MethodSpec returnType(String value) { returnType = value; return this; }
        public MethodSpec parameterCount(int value) { count = value; return this; }
    }
}
