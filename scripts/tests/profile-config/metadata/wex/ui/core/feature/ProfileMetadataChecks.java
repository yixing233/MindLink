package wex.ui.core.feature;

import android.content.Context;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.query.matchers.MethodMatcher;

/** Real HostTargetResolver matcher/invoke/findMethods/background plus real ConfigStorage bridge. */
public final class ProfileMetadataChecks {
    private static final String ACCOUNT = "private-metadata-account", NAME = "private-metadata-nickname";
    private static final AtomicInteger connections = new AtomicInteger();
    private static final AtomicReference<Throwable> uncaught = new AtomicReference<>();
    private static final ClassLoader LOADER = ProfileMetadataChecks.class.getClassLoader();
    private static int checks;

    public static final class ConfigStorage {
        final List<Integer> ids = new ArrayList<>();
        final List<Object> defaults = new ArrayList<>();
        int databaseGetterCalls;
        public Object read(int id, Object fallback) {
            ids.add(id); defaults.add(fallback);
            return id == 2 ? ACCOUNT : id == 4 ? NAME : fallback;
        }
        public Object databaseGetter() { databaseGetterCalls++; throw new AssertionError("Config getter must not be called"); }
    }
    public static final class AlternativeStorage {
        public Object read(int id, Object fallback) { return fallback; }
    }
    public enum Phase { NotReady, AccountHasReady }
    public static final class Account { public volatile Phase phase = Phase.NotReady; }
    public static final class Core { public ConfigStorage config; }
    public static final class Kernel {
        public Core core;
        public Account account;
        public static Core coreStorage() { throw new AssertionError("Kernel core getter must not run"); }
        public static Account accountStorage() { throw new AssertionError("Kernel account getter must not run"); }
        public static Kernel instance() { throw new AssertionError("Kernel instance getter must not run"); }
    }
    public static final class MethodData {
        final Method method;
        int methodReads;
        MethodData(Method method) { this.method = method; }
        public Method getMethodInstance(ClassLoader loader) {
            check(loader == LOADER, "Real metadata lookup forwards exact host ClassLoader");
            methodReads++; return method;
        }
    }
    public static final class DexMetadata {
        final List<MethodData> rows;
        final List<FindMethod> finders = new ArrayList<>();
        DexMetadata(List<MethodData> rows) { this.rows = rows; }
        public List<MethodData> findMethod(FindMethod finder) {
            finders.add(finder);
            String[] strings = finder.target.strings;
            if (strings != null && Arrays.asList(strings).contains("mCoreAccount not initialized!")) {
                try { return List.of(new MethodData(Kernel.class.getMethod("accountStorage"))); }
                catch (ReflectiveOperationException error) { throw new AssertionError(error); }
            }
            if (strings != null && Arrays.asList(strings).contains("changeToAccountHasReady")) return List.of();
            return rows;
        }
    }
    public static final class TypedReceiver {
        Object[] seen;
        int primitive;
        public Object[] typed(Object[] value, int flag) { seen = value; primitive = flag; return value; }
    }

    public static void main(String[] args) throws Exception {
        check(android.os.Looper.myLooper() == android.os.Looper.getMainLooper(), "Metadata fixture entrypoint is MAIN");
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            uncaught.compareAndSet(null, error);
            error.printStackTrace(System.err);
        });
        URL.setURLStreamHandlerFactory(protocol -> new URLStreamHandler() {
            @Override protected URLConnection openConnection(URL url) {
                connections.incrementAndGet(); throw new AssertionError("Metadata suite prohibits external connections");
            }
        });
        ExecutorService executor = (ExecutorService) field(HostTargetResolver.class, "EXECUTOR").get(null);
        try {
            switch (args[0]) {
                case "real-config-metadata": realMetadata(false); break;
                case "real-metadata-collision": realMetadata(true); break;
                case "typed-array-primitive": typedArrayPrimitive(); break;
                default: throw new AssertionError("Unknown metadata case");
            }
            check(uncaught.get() == null && connections.get() == 0, "Real resolver worker has no escaping Throwable or network access");
            for (String log : XposedBridge.logs) check(!log.contains(ACCOUNT) && !log.contains(NAME), "Metadata logs do not include profile values");
            for (String log : HostStageLog.stages) check(!log.contains(ACCOUNT) && !log.contains(NAME), "Metadata stage sink does not include profile values");
            System.out.println("PASS profile-config/" + args[0] + " (" + checks + " assertions)");
        } finally {
            executor.shutdownNow();
            check(executor.awaitTermination(5, TimeUnit.SECONDS), "Real resolver executor stops after local test");
        }
    }
    private static Field field(Class<?> owner, String name) throws Exception {
        Field value = owner.getDeclaredField(name); value.setAccessible(true); return value;
    }
    private static Method privateMethod(Class<?> owner, String name, Class<?>... types) throws Exception {
        Method value = owner.getDeclaredMethod(name, types); value.setAccessible(true); return value;
    }
    private static void checkpoint() throws Exception {
        CountDownLatch done = new CountDownLatch(1); HostTargetResolver.background(done::countDown);
        check(done.await(5, TimeUnit.SECONDS), "Real single-thread resolver completes preceding profile resolution");
    }
    private static void realMetadata(boolean collision) throws Exception {
        Method reader = ConfigStorage.class.getMethod("read", int.class, Object.class);
        List<MethodData> rows = new ArrayList<>(); rows.add(new MethodData(reader));
        if (collision) rows.add(new MethodData(AlternativeStorage.class.getMethod("read", int.class, Object.class)));
        DexMetadata metadata = new DexMetadata(rows);
        field(HostTargetResolver.class, "bridge").set(null, metadata);
        XC_LoadPackage.LoadPackageParam load = new XC_LoadPackage.LoadPackageParam(); load.classLoader = LOADER;
        HostTargetResolver.initialize(load, new Context()); HostProfileBridge.initialize(); checkpoint();
        check(metadata.finders.size() == 1, "Real findMethods dispatched exactly one reflected FindMethod metadata request");
        FindMethod finder = metadata.finders.get(0); MethodMatcher matcher = finder.target;
        check(Arrays.equals(finder.packages, new String[]{"com.tencent.mm.storage"}), "Real finder searchPackages receives one intact String[] argument");
        check(matcher != null && matcher.owner != null
                        && Arrays.equals(matcher.owner.equalStrings, new String[]{"MicroMsg.ConfigStorage", "shouldProcessEvent db is close :%s"}),
                "Real matcher applies both exact ConfigStorage identity strings through ClassMatcher.usingEqStrings(String[])");
        check(Arrays.equals(matcher.parameters, new String[]{"int", "java.lang.Object"}) && "java.lang.Object".equals(matcher.result),
                "Real matcher correctly reflects paramTypes(String[]) and returnType(String) without vararg spreading");
        check(rows.stream().allMatch(row -> row.methodReads == 1), "Real findMethods converts each metadata row to runtime Method once");
        if (collision) {
            check(HostProfileBridge.status().startsWith("FAILED:") && !HostProfileBridge.observed()
                    && !HostProfileBridge.accepts(new ConfigStorage()) && XposedBridge.hooks.isEmpty(),
                    "Real unique() rejects colliding metadata before reader registration");
        } else {
            check(HostProfileBridge.status().equals("HOOKED") && XposedBridge.hooks.get(reader).size() == 1,
                    "Real metadata lookup actually registers exact concrete reader after callback");
            ConfigStorage storage = new ConfigStorage();
            try { XposedBridge.after(reader, storage, NAME, null, new Object[]{2, ""}); }
            catch (Throwable error) { throw new AssertionError("Real metadata hook callback escaped", error); }
            check(!HostProfileBridge.observed() && storage.ids.isEmpty(), "Real resolved reader callback alone cannot bypass Ready account gate");
            field(HostDatabaseBridge.class, "storageClass").set(null, Core.class);
            privateMethod(HostDatabaseBridge.class, "observeStorage", Class.class).invoke(null, Core.class);
            privateMethod(HostDatabaseBridge.class, "observeKernel", Method.class).invoke(null, Kernel.class.getMethod("coreStorage"));
            Kernel kernel = new Kernel(); kernel.core = new Core(); kernel.core.config = storage; kernel.account = new Account();
            try { XposedBridge.after(Kernel.class.getMethod("instance"), null, kernel); }
            catch (Throwable error) { throw new AssertionError(error); }
            checkpoint();
            check(!HostProfileBridge.observed() && storage.ids.isEmpty(), "Metadata path still rejects NotReady actual tuple");
            kernel.account.phase = Phase.AccountHasReady;
            try { XposedBridge.after(Kernel.class.getMethod("instance"), null, kernel); }
            catch (Throwable error) { throw new AssertionError(error); }
            checkpoint();
            check(HostProfileBridge.observed() && storage.ids.isEmpty(), "Real resolver worker publishes Ready actual holder without identity reads");
            java.util.concurrent.atomic.AtomicReference<HostProfileBridge.Identity> value = new java.util.concurrent.atomic.AtomicReference<>();
            java.util.concurrent.atomic.AtomicReference<Throwable> failure = new java.util.concurrent.atomic.AtomicReference<>();
            HostTargetResolver.background(() -> {
                try { value.set(HostProfileBridge.readIdentity()); }
                catch (Throwable error) { failure.set(error); }
            });
            checkpoint();
            if (failure.get() != null) throw new AssertionError("Worker identity read failed", failure.get());
            check(ACCOUNT.equals(value.get().account) && NAME.equals(value.get().nickname) && storage.ids.equals(List.of(2, 4)),
                    "Bridge worker invokes exact reflected primitive-int/Object ConfigStorage reader for account and name");
            check(storage.defaults.equals(List.of("", "")) && storage.databaseGetterCalls == 0 && !HostDatabaseBridge.isReady(),
                    "Identity fields use empty Object defaults without proactively calling database getter");
            check(HostProfileBridge.status().equals("IDENTITY_READY"), "Genuine metadata/reflection path exposes nonempty identity-ready stage");
        }
    }
    private static void typedArrayPrimitive() throws Exception {
        Method invoke = privateMethod(HostTargetResolver.class, "invoke", Object.class, String.class, Class[].class, Object[].class);
        TypedReceiver receiver = new TypedReceiver(); Object[] supplied = {"int", "java.lang.Object"};
        Object value = invoke.invoke(null, new Object[]{receiver, "typed", new Class<?>[]{Object[].class, int.class}, new Object[]{supplied, Integer.valueOf(7)}});
        check(value == supplied && receiver.seen == supplied && receiver.primitive == 7,
                "Real reflection dispatcher retains typed Object[] as one argument and unboxes primitive int");
        HostTargetResolver.MethodSpec spec = new HostTargetResolver.MethodSpec()
                .classStrings("MicroMsg.ConfigStorage", "shouldProcessEvent db is close :%s")
                .parameterTypes("int", "java.lang.Object").returnType("java.lang.Object").parameterCount(2).modifiers(Modifier.PUBLIC);
        MethodMatcher matcher = (MethodMatcher) privateMethod(HostTargetResolver.class, "matcher", HostTargetResolver.MethodSpec.class).invoke(null, spec);
        check(matcher.count == 2 && matcher.access == Modifier.PUBLIC && matcher.owner instanceof ClassMatcher,
                "Real matcher invokes public primitive paramCount(int)/modifiers(int) overloads");
        check(MethodMatcher.class.getMethod("paramTypes", String[].class).isVarArgs(),
                "Metadata fake exposes bundled exact public String[] varargs method, not invented Object[] parameter API");
    }
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
}
