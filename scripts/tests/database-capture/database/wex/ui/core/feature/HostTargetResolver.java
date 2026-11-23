package wex.ui.core.feature;

import android.content.Context;
import dev.mindlink.xposed.callbacks.XC_LoadPackage;
import java.lang.reflect.Method;
import java.util.Arrays;

/** Resolver callback control only; no DexKit metadata or resolution algorithm. */
public final class HostTargetResolver {
    public static int initializeCalls;
    public static int resolveCalls;
    public static String lastKey;
    public static MethodSpec lastSpec;
    public static MethodSpec accountSpec;
    public static java.util.List<Method> accountCandidates = java.util.List.of();
    private static MethodCallback pending;
    private static ClassLoader loader;
    public static ClassLoader hostClassLoader() { return loader; }
    public static void initialize(XC_LoadPackage.LoadPackageParam load, Context context) {
        initializeCalls++;
        if (loader == null && load != null) loader = load.classLoader;
    }
    public static final class MethodSpec {
        public String[] strings;
        public String declaredClass, name, returnType;
        public int count = -1, access = -1;
        public MethodSpec declaredClass(String value) { declaredClass = value; return this; }
        public MethodSpec name(String value) { name = value; return this; }
        public MethodSpec returnType(String value) { returnType = value; return this; }
        public MethodSpec modifiers(int value) { access = value; return this; }
        public MethodSpec usingStrings(String... values) { strings = Arrays.copyOf(values, values.length); return this; }
        public MethodSpec parameterCount(int value) { count = value; return this; }
    }
    private static final java.util.ArrayDeque<Runnable> tasks = new java.util.ArrayDeque<>();
    static void background(Runnable task) { tasks.add(task); }
    static void drain() {
        int count = 0;
        while (!tasks.isEmpty()) {
            if (++count > 32) throw new AssertionError("Unbounded fixture worker tasks");
            android.os.Looper.worker(tasks.remove());
        }
    }
    static java.util.List<Method> findMethods(MethodSpec spec) {
        if (spec.strings != null && Arrays.asList(spec.strings).contains("mCoreAccount not initialized!")) {
            accountSpec = spec;
            return accountCandidates;
        }
        return java.util.List.of();
    }
    static Method unique(java.util.List<Method> methods, String description) {
        if (methods.size() != 1) throw new IllegalStateException("Fixture has no initialized Kernel metadata");
        return methods.get(0);
    }
    public interface MethodCallback {
        void onResolved(Method method);
        void onFailure(Throwable error);
    }
    public static void resolveMethod(String key, MethodSpec spec, MethodCallback callback) {
        if (pending != null) throw new AssertionError("Unexpected duplicate resolve while pending");
        lastKey = key;
        lastSpec = spec;
        pending = callback;
        resolveCalls++;
    }
    public static void succeed(Method method) {
        MethodCallback callback = take();
        callback.onResolved(method);
    }
    public static void fail(Throwable error) {
        MethodCallback callback = take();
        callback.onFailure(error);
    }
    private static MethodCallback take() {
        if (pending == null) throw new AssertionError("No pending resolver callback");
        MethodCallback callback = pending;
        pending = null;
        return callback;
    }
}
