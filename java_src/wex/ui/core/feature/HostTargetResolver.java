package wex.ui.core.feature;

import android.app.Application;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import wex.ui.core.WexStore;

/** Resolves obfuscated host targets with the DexKit runtime already bundled in the APK. */
public final class HostTargetResolver {
    public interface MethodCallback {
        void onResolved(Method method);
        void onFailure(Throwable error);
    }

    public static final class MethodSpec {
        String[] packages;
        String declaredClass;
        String name;
        String returnType;
        String[] strings;
        String[] classStrings;
        String[] parameterTypes;
        String[] classFields;
        MethodSpec invoked;
        int modifiers = -1;
        int parameterCount = -1;
        boolean exactStrings;

        public MethodSpec classStrings(String... value) { classStrings = value; return this; }
        public MethodSpec classFields(String... value) { classFields = value; return this; }
        public MethodSpec parameterTypes(String... value) { parameterTypes = value; return this; }
        public MethodSpec modifiers(int value) { modifiers = value; return this; }
        public MethodSpec invoking(MethodSpec value) { invoked = value; return this; }

        String identity() {
            return java.util.Arrays.toString(packages) + "|" + declaredClass + "|" + name + "|"
                    + returnType + "|" + java.util.Arrays.toString(strings) + "|"
                    + java.util.Arrays.toString(classStrings) + "|" + java.util.Arrays.toString(parameterTypes)
                    + "|" + java.util.Arrays.toString(classFields) + "|" + modifiers + "|" + parameterCount
                    + "|" + exactStrings + "|" + (invoked == null ? "" : invoked.identity());
        }

        public MethodSpec packages(String... value) {
            packages = value;
            return this;
        }

        public MethodSpec declaredClass(String value) {
            declaredClass = value;
            return this;
        }

        public MethodSpec name(String value) {
            name = value;
            return this;
        }

        public MethodSpec returnType(String value) {
            returnType = value;
            return this;
        }

        public MethodSpec parameterCount(int value) {
            parameterCount = value;
            return this;
        }

        public MethodSpec usingStrings(String... value) {
            strings = value;
            exactStrings = false;
            return this;
        }

        public MethodSpec usingExactStrings(String... value) {
            strings = value;
            exactStrings = true;
            return this;
        }
    }

    private static final String TAG = "WEX: 目标解析: ";
    private static final Object LOCK = new Object();
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "MindLink-target-resolver");
        thread.setDaemon(true);
        return thread;
    });

    private static volatile ClassLoader hostClassLoader;
    private static boolean attachedLoaderSelected;
    private static volatile Context application;
    private static volatile Object bridge;
    private static volatile Throwable bridgeFailure;

    private HostTargetResolver() {
    }

    public static void initialize(XC_LoadPackage.LoadPackageParam lpparam, Context context) {
        synchronized (LOCK) {
            Context attached = context;
            if (context instanceof Application) {
                try {
                    Context base = ((Application) context).getBaseContext();
                    if (base != null) attached = base;
                } catch (Throwable ignored) {
                }
            }
            // During Application.attach, getApplicationContext() may still be null.
            if (attached != null) application = attached;
            if (!attachedLoaderSelected && attached != null && bridge == null && bridgeFailure == null) {
                ClassLoader loader = null;
                try { loader = attached.getClassLoader(); } catch (Throwable ignored) { }
                if (loader == null && attached != context) {
                    try { loader = context.getClassLoader(); } catch (Throwable ignored) { }
                }
                if (loader != null) {
                    ClassLoader initial = hostClassLoader != null ? hostClassLoader
                            : lpparam == null ? null : lpparam.classLoader;
                    hostClassLoader = loader;
                    attachedLoaderSelected = true;
                    log("stage=runtime-loader source=attached loaderChanged=" + (loader != initial)
                            + " loaderAvailable=true");
                }
            }
            // Load-only callers retain the initial fallback, but never undo a live selection.
            // An already-created DexKit bridge is never rebound to a different loader.
            if (hostClassLoader == null && !attachedLoaderSelected && lpparam != null)
                hostClassLoader = lpparam.classLoader;
        }
    }

    public static ClassLoader hostClassLoader() {
        return hostClassLoader;
    }

    public static void resolveMethod(final String id, final MethodSpec spec,
                                     final MethodCallback callback) {
        try {
            EXECUTOR.execute(new Runnable() {
                @Override
                public void run() {
                    final Method method;
                    try {
                        method = resolveMethodNow(id, spec);
                    } catch (Throwable error) {
                        logFailure(id, "解析失败", error);
                        notifyFailure(id, callback, error);
                        return;
                    }
                    try {
                        callback.onResolved(method);
                    } catch (Throwable error) {
                        logFailure(id, "成功回调失败", error);
                    }
                }
            });
        } catch (Throwable error) {
            logFailure(id, "任务提交失败", error);
            notifyFailure(id, callback, error);
        }
    }

    private static void notifyFailure(String id, MethodCallback callback, Throwable error) {
        try {
            callback.onFailure(error);
        } catch (Throwable callbackError) {
            logFailure(id, "失败回调失败", callbackError);
        }
    }

    public static Method resolveMethodNow(String id, MethodSpec spec) throws Exception {
        ClassLoader loader = hostClassLoader;
        if (loader == null) {
            throw new IllegalStateException("Host ClassLoader is unavailable");
        }
        Object dex = bridge();
        String cacheKey = "host_target_" + id;
        String fingerprint = fingerprint() + ":" + digest(spec.identity());
        String cached = WexStore.INSTANCE.getString(cacheKey, "");
        if (cached != null && cached.startsWith(fingerprint + "\n")) {
            String descriptor = cached.substring(cached.indexOf('\n') + 1);
            Method method = methodFromDescriptor(dex, descriptor, loader);
            if (method != null) {
                method.setAccessible(true);
                return method;
            }
        }

        Object matcher = matcher(spec);
        Object finder = newInstance("org.luckypray.dexkit.query.FindMethod");
        Class<?> matcherClass = moduleClass("org.luckypray.dexkit.query.matchers.MethodMatcher");
        invoke(finder, "matcher", new Class<?>[]{matcherClass}, matcher);
        if (spec.packages != null && spec.packages.length > 0) {
            invoke(finder, "searchPackages", new Class<?>[]{String[].class}, (Object) spec.packages);
        }

        Class<?> finderClass = moduleClass("org.luckypray.dexkit.query.FindMethod");
        Object result = invoke(dex, "findMethod", new Class<?>[]{finderClass}, finder);
        if (!(result instanceof List)) {
            throw new IllegalStateException("DexKit returned an unsupported result list");
        }
        List<?> methods = (List<?>) result;
        if (methods.size() != 1) {
            throw new IllegalStateException("Expected one method, found " + methods.size());
        }
        Object data = methods.get(0);
        Method method = (Method) invoke(data, "getMethodInstance",
                new Class<?>[]{ClassLoader.class}, loader);
        String descriptor = String.valueOf(invoke(data, "getDescriptor", new Class<?>[0]));
        method.setAccessible(true);
        WexStore.INSTANCE.putString(cacheKey, fingerprint + "\n" + descriptor);
        return method;
    }

    private static Object matcher(MethodSpec spec) throws Exception {
        Object matcher = newInstance("org.luckypray.dexkit.query.matchers.MethodMatcher");
        if (spec.classStrings != null || spec.classFields != null) {
            Object owner = newInstance("org.luckypray.dexkit.query.matchers.ClassMatcher");
            if (spec.classStrings != null) invoke(owner, "usingEqStrings", new Class<?>[]{String[].class}, (Object) spec.classStrings);
            if (spec.classFields != null) {
                for (String field : spec.classFields) invoke(owner, "addFieldForName", new Class<?>[]{String.class}, field);
            }
            if (spec.declaredClass != null) invoke(owner, "className", new Class<?>[]{String.class}, spec.declaredClass);
            invoke(matcher, "declaredClass", new Class<?>[]{owner.getClass()}, owner);
        } else if (spec.declaredClass != null) {
            invoke(matcher, "declaredClass", new Class<?>[]{String.class}, spec.declaredClass);
        }
        if (spec.name != null) invoke(matcher, "name", new Class<?>[]{String.class}, spec.name);
        if (spec.returnType != null) invoke(matcher, "returnType", new Class<?>[]{String.class}, spec.returnType);
        if (spec.parameterTypes != null) invoke(matcher, "paramTypes", new Class<?>[]{String[].class}, (Object) spec.parameterTypes);
        if (spec.parameterCount >= 0) invoke(matcher, "paramCount", new Class<?>[]{int.class}, spec.parameterCount);
        if (spec.modifiers >= 0) invoke(matcher, "modifiers", new Class<?>[]{int.class}, spec.modifiers);
        if (spec.strings != null && spec.strings.length > 0) {
            invoke(matcher, spec.exactStrings ? "usingEqStrings" : "usingStrings",
                    new Class<?>[]{String[].class}, (Object) spec.strings);
        }
        if (spec.invoked != null) {
            Object nested = matcher(spec.invoked);
            invoke(matcher, "addInvoke", new Class<?>[]{nested.getClass()}, nested);
        }
        return matcher;
    }

    /** Runtime queries for structure-dependent targets; callers must reject ambiguous candidates. */
    static List<Method> findMethods(MethodSpec spec) throws Exception {
        Object finder = newInstance("org.luckypray.dexkit.query.FindMethod");
        Object match = matcher(spec);
        invoke(finder, "matcher", new Class<?>[]{match.getClass()}, match);
        if (spec.packages != null) invoke(finder, "searchPackages", new Class<?>[]{String[].class}, (Object) spec.packages);
        Object result = invoke(bridge(), "findMethod", new Class<?>[]{finder.getClass()}, finder);
        if (!(result instanceof List)) throw new IllegalStateException("Unexpected DexKit result");
        List<Method> methods = new java.util.ArrayList<>();
        for (Object item : (List<?>) result) {
            Method method = (Method) invoke(item, "getMethodInstance", new Class<?>[]{ClassLoader.class}, hostClassLoader);
            method.setAccessible(true); methods.add(method);
        }
        return methods;
    }

    static Class<?> classWithFields(String... fields) throws Exception {
        Object matcher = newInstance("org.luckypray.dexkit.query.matchers.ClassMatcher");
        for (String field : fields) invoke(matcher, "addFieldForName", new Class<?>[]{String.class}, field);
        Object finder = newInstance("org.luckypray.dexkit.query.FindClass");
        invoke(finder, "matcher", new Class<?>[]{matcher.getClass()}, matcher);
        Object result = invoke(bridge(), "findClass", new Class<?>[]{finder.getClass()}, finder);
        if (!(result instanceof List) || ((List<?>) result).size() != 1) {
            throw new IllegalStateException("Status record class must have one structural match");
        }
        return (Class<?>) invoke(((List<?>) result).get(0), "getInstance",
                new Class<?>[]{ClassLoader.class}, hostClassLoader);
    }

    static boolean usesFieldThroughDirectInvoke(Method method, String fieldName) throws Exception {
        Object data = invoke(bridge(), "getMethodData", new Class<?>[]{Method.class}, method);
        if (usesField(data, fieldName)) return true;
        for (Object child : (List<?>) invoke(data, "getInvokes", new Class<?>[0])) {
            if (usesField(child, fieldName)) return true;
        }
        return false;
    }

    private static boolean usesField(Object data, String name) throws Exception {
        for (Object access : (List<?>) invoke(data, "getUsingFields", new Class<?>[0])) {
            Object field = invoke(access, "getField", new Class<?>[0]);
            if (name.equals(invoke(field, "getFieldName", new Class<?>[0]))) return true;
        }
        return false;
    }

    static java.lang.reflect.Field fieldReadBy(Method method, Class<?> type) throws Exception {
        Object data = invoke(bridge(), "getMethodData", new Class<?>[]{Method.class}, method);
        java.util.Set<java.lang.reflect.Field> matches = new java.util.HashSet<>();
        for (Object access : (List<?>) invoke(data, "getUsingFields", new Class<?>[0])) {
            Object fieldData = invoke(access, "getField", new Class<?>[0]);
            String name = (String) invoke(fieldData, "getFieldName", new Class<?>[0]);
            java.lang.reflect.Field field;
            try { field = method.getDeclaringClass().getDeclaredField(name); }
            catch (NoSuchFieldException unrelatedOwner) { continue; }
            if (field.getType() == type && !java.lang.reflect.Modifier.isStatic(field.getModifiers())) matches.add(field);
        }
        if (matches.size() != 1) throw new IllegalStateException("Expected one field in resolved reader");
        java.lang.reflect.Field field = matches.iterator().next();
        field.setAccessible(true);
        return field;
    }

    static Method unique(List<Method> methods, String description) {
        if (methods.size() != 1) throw new IllegalStateException(description + ": expected one target, found " + methods.size());
        return methods.get(0);
    }

    static void background(final Runnable task) {
        // Submission errors belong to the caller; worker errors must not escape the shared executor.
        EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    task.run();
                } catch (Throwable error) {
                    logFailure("background", "任务失败", error);
                }
            }
        });
    }

    private static String digest(String value) throws Exception {
        byte[] bytes = java.security.MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder text = new StringBuilder();
        for (byte b : bytes) text.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
        return text.toString();
    }

    private static Method methodFromDescriptor(Object dex, String descriptor,
                                               ClassLoader loader) {
        try {
            Object data = invoke(dex, "getMethodData", new Class<?>[]{String.class}, descriptor);
            if (data == null) {
                return null;
            }
            return (Method) invoke(data, "getMethodInstance",
                    new Class<?>[]{ClassLoader.class}, loader);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object bridge() throws Exception {
        Object current = bridge;
        if (current != null) {
            return current;
        }
        if (bridgeFailure != null) {
            throw new IllegalStateException("DexKit initialization previously failed", bridgeFailure);
        }
        synchronized (LOCK) {
            if (bridge != null) {
                return bridge;
            }
            try {
                loadNative();
                Class<?> bridgeClass = moduleClass("org.luckypray.dexkit.DexKitBridge");
                Method create = bridgeClass.getMethod("create", ClassLoader.class, boolean.class);
                bridge = create.invoke(null, hostClassLoader, Boolean.TRUE);
                return bridge;
            } catch (Throwable error) {
                bridgeFailure = error;
                if (error instanceof Exception) {
                    throw (Exception) error;
                }
                throw new IllegalStateException(error);
            }
        }
    }

    private static void loadNative() throws Exception {
        Class<?> adapt = moduleClass("wex.ui.core.feature.DexKitAutoAdapt");
        Object instance = adapt.getField("INSTANCE").get(null);
        Method method = adapt.getDeclaredMethod("loadNative");
        method.setAccessible(true);
        if (!Boolean.TRUE.equals(method.invoke(instance))) {
            throw new IllegalStateException("libdexkit.so could not be loaded");
        }
    }

    private static Object newInstance(String name) throws Exception {
        return moduleClass(name).getDeclaredConstructor().newInstance();
    }

    private static Class<?> moduleClass(String name) throws ClassNotFoundException {
        return Class.forName(name, true, HostTargetResolver.class.getClassLoader());
    }

    private static Object invoke(Object receiver, String name, Class<?>[] parameterTypes,
                                 Object... args) throws Exception {
        Method method = receiver.getClass().getMethod(name, parameterTypes);
        method.setAccessible(true);
        return method.invoke(receiver, args);
    }

    private static String fingerprint() {
        try {
            Context context = application;
            if (context == null) {
                context = currentApplication();
            }
            PackageInfo info = context.getPackageManager().getPackageInfo("com.tencent.mm", 0);
            long versionCode = Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
            StringBuilder identity = new StringBuilder().append(info.versionName).append(':')
                    .append(versionCode).append(':').append(info.lastUpdateTime);
            android.content.pm.ApplicationInfo app = context.getApplicationInfo();
            identity.append(':').append(app.sourceDir);
            java.io.File apk = new java.io.File(app.sourceDir);
            identity.append(':').append(apk.length()).append(':').append(apk.lastModified());
            if (app.splitSourceDirs != null) for (String split : app.splitSourceDirs) {
                java.io.File file = new java.io.File(split);
                identity.append(':').append(split).append(':').append(file.length()).append(':').append(file.lastModified());
            }
            return digest(identity.toString());
        } catch (Exception error) {
            throw new IllegalStateException("Cannot fingerprint host; refusing ambiguous cache", error);
        }
    }

    private static Context currentApplication() throws Exception {
        Method current = Class.forName("android.app.ActivityThread")
                .getDeclaredMethod("currentApplication");
        current.setAccessible(true);
        Object value = current.invoke(null);
        if (!(value instanceof Application)) {
            throw new IllegalStateException("Application is unavailable");
        }
        return (Application) value;
    }

    private static void logFailure(String id, String stage, Throwable error) {
        try {
            // Host exception messages can contain user data; record only the failure type.
            log(id + " " + stage + " " + error.getClass().getSimpleName());
        } catch (Throwable ignored) {
        }
    }

    private static void log(String message) {
        try {
            XposedBridge.log(TAG + message);
        } catch (Throwable ignored) {
        }
    }
}
