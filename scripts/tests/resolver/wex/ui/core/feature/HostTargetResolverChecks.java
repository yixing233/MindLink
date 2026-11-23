package wex.ui.core.feature;

import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import dev.mindlink.xposed.XposedBridge;
import dev.mindlink.xposed.callbacks.XC_LoadPackage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import wex.ui.core.WexStore;

/** Compiles the actual resolver/bridge, not a copy of their dispatch algorithm. */
public final class HostTargetResolverChecks {
    private static final String SECRET = "private-host-message-must-not-be-logged";
    private static final String DESCRIPTOR = "resolver-test-descriptor";
    private static final AtomicInteger messageReads = new AtomicInteger();
    private static final AtomicReference<Throwable> uncaught = new AtomicReference<>();
    private static final ClassLoader LOADER = HostTargetResolverChecks.class.getClassLoader();
    private static int checks;
    private static Thread worker;

    // This is deliberately neither Exception nor Error.
    private static final class BareThrowable extends Throwable {
        BareThrowable() { super(SECRET); }
        @Override public String getMessage() {
            messageReads.incrementAndGet();
            return SECRET;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void sneakyThrow(Throwable error) throws T {
        throw (T) error;
    }

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    private static Field field(Class<?> owner, String name) throws Exception {
        Field result = owner.getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }

    private static Method privateMethod(Class<?> owner, String name, Class<?>... types) throws Exception {
        Method result = owner.getDeclaredMethod(name, types);
        result.setAccessible(true);
        return result;
    }

    private static XC_LoadPackage.LoadPackageParam loadPackage() {
        XC_LoadPackage.LoadPackageParam load = new XC_LoadPackage.LoadPackageParam();
        load.classLoader = LOADER;
        return load;
    }

    /** A following task must run on the same worker, not merely a replacement thread. */
    private static void checkpoint(String description) throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Thread> actual = new AtomicReference<>();
        HostTargetResolver.background(() -> {
            actual.set(Thread.currentThread());
            completed.countDown();
        });
        check(completed.await(5, TimeUnit.SECONDS), description + ": following task ran");
        if (worker == null) worker = actual.get();
        check(actual.get() == worker, description + ": original executor worker survived");
        check(uncaught.get() == null, description + ": no uncaught Throwable");
    }

    private static void backgroundFailures(Throwable[] errors) throws Exception {
        for (Throwable error : errors) {
            AtomicInteger calls = new AtomicInteger();
            int logCount = XposedBridge.logs.size();
            // Even a logger throwing a direct Throwable must not escape the worker.
            XposedBridge.logFailure = new BareThrowable();
            HostTargetResolver.background(() -> {
                calls.incrementAndGet();
                HostTargetResolverChecks.<RuntimeException>sneakyThrow(error);
            });
            checkpoint("background " + error.getClass().getSimpleName());
            XposedBridge.logFailure = null;
            check(calls.get() == 1, "failing background task executed exactly once");
            check(XposedBridge.logs.size() == logCount + 1, "background failure logged despite broken logger");
            check(XposedBridge.logs.get(logCount).contains("任务失败"), "background failure stage recorded");
        }
    }

    private static void unavailableLoaderFailures(Throwable[] errors) throws Exception {
        // A real static parser failure; no fake resolver and no DexKit is involved.
        for (Throwable callbackError : errors) {
            AtomicInteger resolved = new AtomicInteger(), failed = new AtomicInteger();
            AtomicReference<Throwable> seen = new AtomicReference<>();
            XposedBridge.logFailure = new AssertionError(SECRET);
            HostTargetResolver.resolveMethod("missing-loader", new HostTargetResolver.MethodSpec(),
                    new HostTargetResolver.MethodCallback() {
                        public void onResolved(Method method) { resolved.incrementAndGet(); }
                        public void onFailure(Throwable error) {
                            seen.set(error);
                            failed.incrementAndGet();
                            HostTargetResolverChecks.<RuntimeException>sneakyThrow(callbackError);
                        }
                    });
            checkpoint("missing loader and throwing failure callback");
            XposedBridge.logFailure = null;
            check(resolved.get() == 0 && failed.get() == 1, "parser failure delivers only one failure callback");
            check(seen.get() instanceof IllegalStateException, "real parser rejects unavailable host loader");
        }
    }

    private static final class FailingContext extends Context {
        private final Throwable error;
        FailingContext(Throwable error) { this.error = error; }
        @Override public PackageManager getPackageManager() {
            HostTargetResolverChecks.<RuntimeException>sneakyThrow(error);
            return null;
        }
    }

    private static void parserThrowables(Throwable[] errors) throws Exception {
        for (Throwable parserError : errors) {
            HostTargetResolver.initialize(loadPackage(), new FailingContext(parserError));
            AtomicInteger resolved = new AtomicInteger(), failed = new AtomicInteger();
            AtomicReference<Throwable> seen = new AtomicReference<>();
            XposedBridge.logFailure = new BareThrowable();
            HostTargetResolver.resolveMethod("fingerprint-failure", new HostTargetResolver.MethodSpec(),
                    new HostTargetResolver.MethodCallback() {
                        public void onResolved(Method method) { resolved.incrementAndGet(); }
                        public void onFailure(Throwable error) {
                            seen.set(error);
                            failed.incrementAndGet();
                            throw new AssertionError(SECRET);
                        }
                    });
            checkpoint("real fingerprint throws " + parserError.getClass().getSimpleName());
            XposedBridge.logFailure = null;
            check(resolved.get() == 0 && failed.get() == 1, "direct parser Throwable delivers failure once");
            check(seen.get() == parserError, "failure callback receives original parser Throwable");
        }
    }

    /** Only the reflective DexKit metadata API needed by the production cache hit. */
    public static final class FakeDex {
        final Method target;
        int descriptorReads, methodReads;
        FakeDex(Method target) { this.target = target; }
        public FakeMethodData getMethodData(String descriptor) {
            if (!DESCRIPTOR.equals(descriptor)) throw new AssertionError("wrong descriptor");
            descriptorReads++;
            return new FakeMethodData(this);
        }
    }

    public static final class FakeMethodData {
        final FakeDex dex;
        FakeMethodData(FakeDex dex) { this.dex = dex; }
        public Method getMethodInstance(ClassLoader loader) {
            if (loader != LOADER) throw new AssertionError("wrong host class loader");
            dex.methodReads++;
            return dex.target;
        }
    }

    private static void target() {}

    private static void successCallbacks(FakeDex dex, Throwable[] errors) throws Exception {
        HostTargetResolver.initialize(loadPackage(), new Application());
        HostTargetResolver.MethodSpec spec = new HostTargetResolver.MethodSpec().name("target").parameterCount(0);
        // Obtain the real fingerprint and spec digest; do not recreate the cache algorithm.
        String fingerprint = (String) privateMethod(HostTargetResolver.class, "fingerprint").invoke(null);
        String digest = (String) privateMethod(HostTargetResolver.class, "digest", String.class)
                .invoke(null, spec.identity());
        WexStore.INSTANCE.putString("host_target_cached-success", fingerprint + ":" + digest + "\n" + DESCRIPTOR);
        for (Throwable callbackError : errors) {
            AtomicInteger resolved = new AtomicInteger(), failed = new AtomicInteger();
            AtomicReference<Method> seen = new AtomicReference<>();
            int reads = dex.descriptorReads;
            XposedBridge.logFailure = new BareThrowable();
            HostTargetResolver.resolveMethod("cached-success", spec, new HostTargetResolver.MethodCallback() {
                public void onResolved(Method method) {
                    seen.set(method);
                    resolved.incrementAndGet();
                    if (callbackError != null) HostTargetResolverChecks.<RuntimeException>sneakyThrow(callbackError);
                }
                public void onFailure(Throwable error) {
                    failed.incrementAndGet();
                    throw new AssertionError("success callback must not trigger failure callback");
                }
            });
            checkpoint("cached success callback " + (callbackError == null ? "normal" : "throwing"));
            XposedBridge.logFailure = null;
            check(resolved.get() == 1 && failed.get() == 0, "success callback failure is not a parser failure");
            check(dex.target.equals(seen.get()), "real descriptor cache path returns expected Method");
            check(dex.descriptorReads == reads + 1 && dex.methodReads == dex.descriptorReads,
                    "real parser fetched fake descriptor metadata exactly once");
        }
    }

    public static final class FakeDatabase {
        public Cursor rawQuery(String sql, String[] args) { return null; }
    }

    private static void readyListeners(Throwable[] errors) throws Exception {
        @SuppressWarnings("unchecked")
        List<java.lang.ref.WeakReference<Runnable>> listeners =
                (List<java.lang.ref.WeakReference<Runnable>>) field(HostDatabaseBridge.class, "READY").get(null);
        HostDatabaseBridge.subscribeReady(null);
        check(listeners.isEmpty(), "null ready listener ignored");
        AtomicInteger healthyCalls = new AtomicInteger();
        AtomicInteger[] failedCalls = {new AtomicInteger(), new AtomicInteger()};
        Runnable healthy = () -> healthyCalls.incrementAndGet();
        Runnable[] failing = new Runnable[errors.length];
        // notifyReady traverses in reverse: bad listeners must not prevent this healthy one.
        HostDatabaseBridge.subscribeReady(healthy);
        for (int i = 0; i < errors.length; i++) {
            final int index = i;
            failing[i] = () -> {
                failedCalls[index].incrementAndGet();
                HostTargetResolverChecks.<RuntimeException>sneakyThrow(errors[index]);
            };
            HostDatabaseBridge.subscribeReady(failing[i]);
        }
        check(healthyCalls.get() == 0 && failedCalls[0].get() == 0 && failedCalls[1].get() == 0,
                "not-ready listeners deferred");
        FakeDatabase database = new FakeDatabase();
        privateMethod(HostDatabaseBridge.class, "capture", Object.class).invoke(null, database);
        checkpoint("capture worker completed");
        check(HostDatabaseBridge.isReady(), "real capture makes database ready");
        check(healthyCalls.get() == 1 && failedCalls[0].get() == 1 && failedCalls[1].get() == 1,
                "notifyReady catches every direct Throwable and non-Linkage Error");
        for (Runnable listener : failing) HostDatabaseBridge.subscribeReady(listener);
        HostDatabaseBridge.subscribeReady(healthy);
        check(healthyCalls.get() == 2 && failedCalls[0].get() == 2 && failedCalls[1].get() == 2,
                "immediate ready callbacks isolate failures and later subscribers still run");
        check(listeners.size() == 6, "immediate callbacks remain subscribed as before");
        listeners.clear();
    }

    private static void submissionFailures(ExecutorService executor, Throwable[] errors) throws Exception {
        // Shut down the actual executor only after all asynchronous tests, to get a real rejection.
        executor.shutdown();
        check(executor.awaitTermination(5, TimeUnit.SECONDS), "executor terminated for rejection checks");
        for (Throwable callbackError : errors) {
            AtomicInteger resolved = new AtomicInteger(), failed = new AtomicInteger();
            AtomicReference<Throwable> seen = new AtomicReference<>();
            XposedBridge.logFailure = new BareThrowable();
            HostTargetResolver.resolveMethod("rejected-submit", new HostTargetResolver.MethodSpec(),
                    new HostTargetResolver.MethodCallback() {
                        public void onResolved(Method method) { resolved.incrementAndGet(); }
                        public void onFailure(Throwable error) {
                            seen.set(error);
                            failed.incrementAndGet();
                            HostTargetResolverChecks.<RuntimeException>sneakyThrow(callbackError);
                        }
                    });
            XposedBridge.logFailure = null;
            check(resolved.get() == 0 && failed.get() == 1, "submission failure reported once despite throwing callback");
            check(seen.get() instanceof RejectedExecutionException, "failure callback receives real submission rejection");
        }
        AtomicInteger taskCalls = new AtomicInteger();
        try {
            HostTargetResolver.background(() -> taskCalls.incrementAndGet());
            throw new AssertionError("background submission rejection must reach caller");
        } catch (RejectedExecutionException expected) {
            check(taskCalls.get() == 0, "background rejection left to caller; task never ran");
        }
    }

    private static void runtimeLoader() throws Exception {
        XC_LoadPackage.LoadPackageParam load = loadPackage();
        ClassLoader initial = load.classLoader;
        ClassLoader live = new ClassLoader(initial) {
            @Override public String toString() { throw new AssertionError("Never print a loader identity"); }
        };
        Context base = new Context() {
            @Override public Context getApplicationContext() { return null; }
            @Override public ClassLoader getClassLoader() { return live; }
        };
        Application attached = new Application() {
            @Override public Context getBaseContext() { return base; }
            @Override public ClassLoader getClassLoader() { throw new AssertionError("Prefer attached base loader"); }
            @Override public Context getApplicationContext() { return null; }
        };
        HostTargetResolver.initialize(load, null);
        check(HostTargetResolver.hostClassLoader() == initial, "Load-only path retains initial fallback");
        HostTargetResolver.initialize(load, attached);
        check(HostTargetResolver.hostClassLoader() == live && live != initial,
                "Actual Application.baseContext loader wins over lpparam and Java defining loader");
        check(field(HostTargetResolver.class, "application").get(null) == base,
                "Actual attached base context is retained when application context is null");
        check(base.getApplicationContext() == null && load.classLoader == initial,
                "Null application context is safe and shared LoadPackageParam is untouched");
        HostTargetResolver.initialize(load, null);
        check(HostTargetResolver.hostClassLoader() == live
                        && field(HostTargetResolver.class, "application").get(null) == base,
                "Legacy initialize(load,null) cannot overwrite selected live loader or saved base context");
        HostTargetResolver.initialize(load, new Context());
        check(HostTargetResolver.hostClassLoader() == live, "Later non-live context cannot rebind selection");
        check(field(HostTargetResolver.class, "bridge").get(null) == null,
                "Selection precedes every DexKit bridge/native/cache initialization");
        check(XposedBridge.logs.stream().filter(s -> s.contains("stage=runtime-loader source=attached")).count() == 1,
                "Attached loader metadata is emitted once");
        check(XposedBridge.logs.stream().anyMatch(s -> s.contains("loaderChanged=true loaderAvailable=true")),
                "Diagnostic reports booleans only, never loader identity/path");
        System.out.println("PASS runtime-loader (" + checks + " real resolver assertions)");
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 0 && "runtime-loader".equals(args[0])) {
            runtimeLoader();
            return;
        }
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> uncaught.compareAndSet(null, error));
        ExecutorService executor = (ExecutorService) field(HostTargetResolver.class, "EXECUTOR").get(null);
        Throwable[] errors = {new BareThrowable(), new AssertionError(SECRET)};
        try {
            check(!(errors[0] instanceof Exception) && !(errors[0] instanceof Error), "direct Throwable fixture");
            check(errors[1] instanceof Error && !(errors[1] instanceof LinkageError), "non-Linkage Error fixture");
            checkpoint("baseline");
            check(HostTargetResolver.hostClassLoader() == null, "initial host loader unavailable");
            backgroundFailures(errors);
            unavailableLoaderFailures(errors);
            FakeDex dex = new FakeDex(privateMethod(HostTargetResolverChecks.class, "target"));
            field(HostTargetResolver.class, "bridge").set(null, dex);
            parserThrowables(errors);
            successCallbacks(dex, new Throwable[]{errors[0], errors[1], null});
            readyListeners(errors);
            checkpoint("ready listeners completed");
            submissionFailures(executor, errors);
            check(uncaught.get() == null, "no executor errors escaped");
            check(messageReads.get() == 0, "host Throwable.getMessage was never read");
            for (String text : XposedBridge.logs) check(!text.contains(SECRET), "logs contain no private host message");
            check(XposedBridge.logs.stream().anyMatch(text -> text.contains("成功回调失败")), "distinct success callback log stage");
            check(XposedBridge.logs.stream().anyMatch(text -> text.contains("失败回调失败")), "distinct failure callback log stage");
            check(XposedBridge.logs.stream().anyMatch(text -> text.contains("任务提交失败")), "distinct submission failure log stage");
            System.out.println("PASS: " + checks + " real resolver/background/callback/ready/submission/privacy assertions");
            System.out.println("Coverage: unavailable-loader and fingerprint failures; cached success via fake metadata; "
                    + "direct Throwable + non-Linkage Error; throwing logger; same-worker continuation; real executor rejection.");
            System.out.println("Not exercised: Android runtime, native DexKit initialization, uncached matcher/query resolution.");
        } finally {
            XposedBridge.logFailure = null;
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
            Thread.setDefaultUncaughtExceptionHandler(previous);
        }
    }
}
