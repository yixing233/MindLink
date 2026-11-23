package wex.ui.core.feature;

import android.content.Context;
import android.database.Cursor;
import dev.mindlink.xposed.XposedBridge;
import dev.mindlink.xposed.callbacks.XC_LoadPackage;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Calls real HostProfileBridge and HostDatabaseBridge using reflection targets and after callbacks. */
public final class ProfileConfigChecks {
    private static final String ACCOUNT = "private-config-account", NAME = "private-config-nickname";
    private static final String PATH = "/private/config/database/path", MESSAGE = "private-host-throwable-message";
    private static final String SQL = "SELECT value AS wxid FROM userinfo WHERE id=2 LIMIT 1";
    private static final AtomicInteger connections = new AtomicInteger(), messageReads = new AtomicInteger();
    private static int checks;
    private static boolean databaseStarted;
    private static Kernel fixtureKernel;

    public static final class PrivateThrowable extends Throwable {
        PrivateThrowable() { super(MESSAGE); }
        @Override public String getMessage() { messageReads.incrementAndGet(); return MESSAGE; }
    }
    public static final class EmptyCursor implements Cursor {
        public String[] getColumnNames() { return new String[0]; }
        public boolean moveToNext() { return false; }
        public int getType(int index) { return FIELD_TYPE_NULL; }
        public long getLong(int index) { return 0; }
        public double getDouble(int index) { return 0; }
        public byte[] getBlob(int index) { return null; }
        public String getString(int index) { return null; }
        public void close() { }
    }
    public interface DatabaseMetadata {
        String getPath(); boolean isOpen(); Cursor rawQuery(String sql, Object[] args);
    }
    public static final class Database implements DatabaseMetadata {
        boolean open = true;
        int paths, openReads, queries;
        String sql;
        Object[] args;
        final Cursor cursor = new EmptyCursor();
        public String getPath() { paths++; throw new AssertionError("Database path body must not be read"); }
        public boolean isOpen() {
            check(android.os.Looper.myLooper() != android.os.Looper.getMainLooper(), "Database isOpen never executes on MAIN");
            openReads++; return open;
        }
        public Cursor rawQuery(String sql, Object[] args) {
            check(android.os.Looper.myLooper() != android.os.Looper.getMainLooper(), "Database query never executes on MAIN");
            queries++; this.sql = sql; this.args = args; return cursor;
        }
    }
    public static final class BadDatabase {
        int reads;
        public Object getPath() { reads++; return PATH; }
        public boolean isOpen() { reads++; return true; }
        public Cursor rawQuery(String sql, Object[] args) { reads++; throw new AssertionError("Invalid metadata database queried"); }
    }
    public static final class NestedDatabase { Database database = new Database(); }
    public static class ConfigStore {
        public DatabaseMetadata database;
        public DatabaseMetadata second;
        public BadDatabase invalid;
        public NestedDatabase nested;
        public Object erased;
        public static DatabaseMetadata staticDatabase;
        final List<Integer> ids = new ArrayList<>();
        final List<Object> defaults = new ArrayList<>();
        Object account = ACCOUNT, nickname = NAME;
        Throwable accountFailure, nicknameFailure;
        int getterCalls;
        boolean emitAfter;
        Object callbackOwner;

        public Object identity(int id, Object fallback) throws Exception {
            ids.add(id); defaults.add(fallback);
            Object value = id == 2 ? account : id == 4 ? nickname : fallback;
            Throwable error = id == 2 ? accountFailure : id == 4 ? nicknameFailure : null;
            if (emitAfter) {
                try { XposedBridge.after(readerMethod(), callbackOwner == null ? this : callbackOwner,
                        value, error, new Object[]{id, fallback}); }
                catch (Throwable failure) { HostTargetResolver.raise(failure); }
            }
            if (error != null) HostTargetResolver.raise(error);
            return value;
        }
        public DatabaseMetadata databaseGetter() { getterCalls++; return database; }
    }
    public static final class SameShape {
        public Database database = new Database();
        public Object identity(int id, Object fallback) { return id == 2 ? ACCOUNT : NAME; }
    }
    public static final class OtherConfig {
        public Object identity(int id, Object fallback) { return fallback; }
    }
    public static final class StaticReader {
        public static Object identity(int id, Object fallback) { return fallback; }
    }
    public abstract static class AbstractReader {
        public abstract Object identity(int id, Object fallback);
    }
    public enum AccountPhase { NotReady, AccountHasReady }
    public static final class AccountStorage {
        public volatile AccountPhase phase = AccountPhase.NotReady;
        int assertions, readinessReads;
        public void assertReady() { assertions++; throw new AssertionError("Account getter body must not be called proactively"); }
        public boolean isReady() { readinessReads++; throw new AssertionError("Readiness getter body must not be called proactively"); }
    }
    public static final class ReadyTask implements Runnable {
        public final AccountStorage account;
        int runs;
        ReadyTask(AccountStorage account) { this.account = account; }
        public void run() { runs++; throw new AssertionError("Ready task body must never be executed by bridge"); }
    }
    public static final class CoreStorage {
        public ConfigStore config;
        public DatabaseMetadata direct;
        int getters;
        public ConfigStore configGetter() { getters++; return config; }
    }
    public static final class Kernel {
        public CoreStorage core;
        public AccountStorage account;
        public static Kernel singleton;
        static int getters;
        public static CoreStorage coreStorage() { getters++; throw new AssertionError("Kernel storage getter must not run"); }
        public static AccountStorage accountStorage() { getters++; throw new AssertionError("Kernel account getter must not run"); }
        public static Kernel instance() { getters++; throw new AssertionError("Kernel instance getter must not run"); }
    }
    public static final class UninitializedKernel {
        static { InitializationGuard.kernelInitializations++; }
        public CoreStorage core;
        public AccountStorage account;
        public static UninitializedKernel singleton;
        public static CoreStorage coreStorage() { throw new AssertionError("Uninitialized kernel getter must not run"); }
        public static AccountStorage accountStorage() { throw new AssertionError("Uninitialized account getter must not run"); }
        public static UninitializedKernel instance() { throw new AssertionError("Uninitialized instance getter must not run"); }
    }
    public static final class InitializationGuard { static int kernelInitializations; }
    public static final class AmbiguousCoreKernel {
        public CoreStorage core, second;
        public AccountStorage account;
        public static CoreStorage coreStorage() { throw new AssertionError("Getter must not run"); }
        public static AccountStorage accountStorage() { throw new AssertionError("Getter must not run"); }
        public static AmbiguousCoreKernel instance() { throw new AssertionError("Getter must not run"); }
    }
    public static final class AmbiguousAccountKernel {
        public CoreStorage core;
        public AccountStorage account, second;
        public static CoreStorage coreStorage() { throw new AssertionError("Getter must not run"); }
        public static AccountStorage accountStorage() { throw new AssertionError("Getter must not run"); }
        public static AmbiguousAccountKernel instance() { throw new AssertionError("Getter must not run"); }
    }
    public static final class ErasedCoreKernel {
        public Object core;
        public AccountStorage account;
        public static CoreStorage coreStorage() { throw new AssertionError("Getter must not run"); }
        public static AccountStorage accountStorage() { throw new AssertionError("Getter must not run"); }
        public static ErasedCoreKernel instance() { throw new AssertionError("Getter must not run"); }
    }
    public static final class StaticCoreKernel {
        public static CoreStorage core;
        public AccountStorage account;
        public static CoreStorage coreStorage() { throw new AssertionError("Getter must not run"); }
        public static AccountStorage accountStorage() { throw new AssertionError("Getter must not run"); }
        public static StaticCoreKernel instance() { throw new AssertionError("Getter must not run"); }
    }
    public static final class NestedCoreKernel {
        public CoreHolder nested;
        public AccountStorage account;
        public static CoreStorage coreStorage() { throw new AssertionError("Getter must not run"); }
        public static AccountStorage accountStorage() { throw new AssertionError("Getter must not run"); }
        public static NestedCoreKernel instance() { throw new AssertionError("Getter must not run"); }
    }
    public static final class CoreHolder { public CoreStorage core; }
    public static final class AmbiguousInstanceKernel {
        public CoreStorage core;
        public AccountStorage account;
        public static CoreStorage coreStorage() { throw new AssertionError("Getter must not run"); }
        public static AccountStorage accountStorage() { throw new AssertionError("Getter must not run"); }
        public static AmbiguousInstanceKernel instance() { throw new AssertionError("Getter must not run"); }
        public static AmbiguousInstanceKernel alternative() { throw new AssertionError("Getter must not run"); }
    }
    public static final class AmbiguousState {
        public volatile AccountPhase phase, second;
    }
    public static final class AmbiguousStateKernel {
        public CoreStorage core;
        public AmbiguousState account;
        public static CoreStorage coreStorage() { throw new AssertionError("Getter must not run"); }
        public static AmbiguousState accountStorage() { throw new AssertionError("Getter must not run"); }
        public static AmbiguousStateKernel instance() { throw new AssertionError("Getter must not run"); }
    }

    public static void main(String[] args) throws Throwable {
        check(android.os.Looper.myLooper() == android.os.Looper.getMainLooper(), "Fixture entrypoint is MAIN");
        URL.setURLStreamHandlerFactory(protocol -> new URLStreamHandler() {
            @Override protected URLConnection openConnection(URL url) {
                connections.incrementAndGet(); throw new AssertionError("External connection prohibited");
            }
        });
        switch (args[0]) {
            case "unique-instance-target": uniqueTarget(); break;
            case "first-callback-during-hook": firstCallbackDuringHook(false); break;
            case "failed-first-callback": firstCallbackDuringHook(true); break;
            case "collision": collision(false); break;
            case "missing-target": collision(true); break;
            case "static-target": nonInstance(StaticReader.class); break;
            case "abstract-target": nonInstance(AbstractReader.class); break;
            case "resolve-failure": resolveFailure(); break;
            case "submit-failure": submitFailure(); break;
            case "hook-failure": hookFailure(); break;
            case "host-after-observation": hostAfter(); break;
            case "self-reading-recursion": selfReadingRecursion(); break;
            case "mandatory-reader-failure": mandatoryReaderFailure(); break;
            case "optional-name-failure": optionalNameFailure(); break;
            case "listener-isolation": listenerIsolation(); break;
            case "getter-after-config-first": getterAfterConfigFirst(); break;
            case "identity-bound-direct-db": identityBoundDatabase(); break;
            case "closed-config-db": closedDatabase(); break;
            case "reject-nested-erased-db": rejectNestedDatabase(); break;
            case "ambiguous-config-db": ambiguousDatabase(); break;
            case "late-config-db": lateDatabase(); break;
            case "privacy-logger-failure": privacyLogger(); break;
            case "kernel-ready-direct-replay": kernelReadyReplay(false); break;
            case "kernel-before-reader": kernelReadyReplay(true); break;
            case "kernel-not-ready": kernelNotReady(); break;
            case "kernel-failed-after": kernelFailedAfter(); break;
            case "no-kernel-initialization": noKernelInitialization(); break;
            case "constructor-partial-denied": constructorPartial(); break;
            case "ready-runnable-after": readyRunnable(false); break;
            case "ready-runnable-failed-after": readyRunnable(true); break;
            case "kernel-ambiguous-core": malformedKernel(AmbiguousCoreKernel.class); break;
            case "kernel-ambiguous-account": malformedKernel(AmbiguousAccountKernel.class); break;
            case "kernel-static-core": malformedKernel(StaticCoreKernel.class); break;
            case "kernel-erased-core": malformedKernel(ErasedCoreKernel.class); break;
            case "kernel-nested-core": malformedKernel(NestedCoreKernel.class); break;
            case "kernel-ambiguous-instance": malformedKernel(AmbiguousInstanceKernel.class); break;
            case "kernel-ambiguous-state": malformedKernel(AmbiguousStateKernel.class); break;
            case "same-instance-throttle": sameInstanceThrottle(); break;
            default: throw new AssertionError("Unknown profile-config case");
        }
        privacy();
        check(connections.get() == 0, "No external connection attempted");
        System.out.println("PASS profile-config/" + args[0] + " (" + checks + " assertions)");
    }

    private static Method readerMethod() throws Exception { return ConfigStore.class.getMethod("identity", int.class, Object.class); }
    private static Field field(Class<?> owner, String name) throws Exception {
        Field result = owner.getDeclaredField(name); result.setAccessible(true); return result;
    }
    private static Method privateMethod(Class<?> owner, String name, Class<?>... args) throws Exception {
        Method result = owner.getDeclaredMethod(name, args); result.setAccessible(true); return result;
    }
    private static Object activeDatabase() throws Exception { return ((WeakReference<?>) field(HostDatabaseBridge.class, "database").get(null)).get(); }
    private static Object observedStorage() throws Exception { return ((WeakReference<?>) field(HostProfileBridge.class, "storage").get(null)).get(); }
    private static void observeReady(ConfigStore config) throws Throwable {
        if (!databaseStarted) startDatabase();
        if (fixtureKernel == null) fixtureKernel = kernel(AccountPhase.AccountHasReady, false);
        fixtureKernel.core.config = config;
        kernelAfter(fixtureKernel, null);
        HostTargetResolver.drain();
    }
    private static HostProfileBridge.Identity workerIdentity() throws Exception {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) return HostProfileBridge.readIdentity();
        return HostTargetResolver.onWorker(HostProfileBridge::readIdentity);
    }
    private static void identityAfter(Object owner, Object result) throws Throwable {
        XposedBridge.after(readerMethod(), owner, result, null, new Object[]{2, ""});
    }
    private static boolean installed() throws Exception { return field(HostProfileBridge.class, "installed").getBoolean(null); }
    private static void start() throws Exception { start(false); }
    private static void start(boolean replay) throws Exception {
        HostTargetResolver.candidates = List.of(readerMethod());
        HostProfileBridge.initialize();
        check("RESOLVING".equals(HostProfileBridge.status()) && !HostProfileBridge.observed(),
                "initialize queues resolver without pretend observation");
        check(HostTargetResolver.workers.size() >= 1 && (!XposedBridge.hooks.containsKey(readerMethod())), "Resolution is deferred off initialization");
        HostTargetResolver.drain();
        check(installed() && (replay ? HostProfileBridge.observed() : "HOOKED".equals(HostProfileBridge.status()) && !HostProfileBridge.observed()),
                "Unique concrete reader actually hooks; capture requires an existing host callback instance");
        check(XposedBridge.hooks.get(readerMethod()).size() == 1, "Exactly one reader after callback is registered");
        check(HostProfileBridge.accepts(new ConfigStore()) && !HostProfileBridge.accepts(new SameShape()),
                "Reader declaring class, not object shape, anchors ConfigStorage acceptance");
    }
    private static void uniqueTarget() throws Exception {
        start();
        HostTargetResolver.MethodSpec spec = HostTargetResolver.lastSpec;
        check(Arrays.equals(spec.packages, new String[]{"com.tencent.mm.storage"}), "Reader search is confined to host storage package");
        check(Arrays.equals(spec.classStrings, new String[]{"MicroMsg.ConfigStorage", "shouldProcessEvent db is close :%s"}),
                "Both exact ConfigStorage class identity strings are retained");
        check(spec.strings == null, "ConfigStorage identity strings belong to class metadata, not method-body search");
        check(Arrays.equals(spec.parameterTypes, new String[]{"int", "java.lang.Object"})
                && "java.lang.Object".equals(spec.returnType), "Resolver requests primitive-int/Object arguments and Object return");
        check(HostTargetResolver.uniques == 1 && "ConfigStorage identity reader".equals(HostTargetResolver.uniqueDescription),
                "Production reader requires one unique candidate");
        check(!Modifier.isStatic(readerMethod().getModifiers()), "Reflection target is an actual nonstatic implementation");
        int attempts = XposedBridge.attempts.get(readerMethod());
        HostProfileBridge.initialize();
        check(HostTargetResolver.submissions == 1 && XposedBridge.attempts.get(readerMethod()) == attempts,
                "Duplicate initialize does not resolve or hook twice");
        try { workerIdentity(); throw new AssertionError("Unobserved read accepted"); }
        catch (IllegalStateException expected) { check(!HostProfileBridge.observed(), "Unobserved read is fail-closed"); }
        check(Kernel.getters == 0 && !HostDatabaseBridge.isReady(), "Initialization does not call Kernel getters or fabricate database");
    }
    private static void firstCallbackDuringHook(boolean fail) throws Throwable {
        ConfigStore config = new ConfigStore();
        startDatabase(); fixtureKernel = kernel(AccountPhase.AccountHasReady, false);
        fixtureKernel.core.config = config; kernelAfter(fixtureKernel, null);
        HostTargetResolver.drain();
        AtomicInteger notifications = new AtomicInteger(); Runnable listener = notifications::incrementAndGet;
        HostProfileBridge.subscribe(listener);
        HostTargetResolver.candidates = List.of(readerMethod());
        XposedBridge.firstCallbackTarget = readerMethod(); XposedBridge.firstCallbackOwner = config;
        XposedBridge.firstCallbackArgs = new Object[]{2, ""};
        if (fail) XposedBridge.failAfterFirstCallback = new PrivateThrowable();
        HostProfileBridge.initialize(); HostTargetResolver.runOne();
        check(notifications.get() == 0 && !HostProfileBridge.observed(), "Early hook callback never publishes inline before worker capture");
        HostTargetResolver.drain();
        if (fail) {
            check(!installed() && !HostProfileBridge.observed() && !HostProfileBridge.accepts(config),
                    "Registration failure after early callback clears reader and storage acceptance");
            check(field(HostProfileBridge.class, "reader").get(null) == null && observedStorage() == null,
                    "Failed registration clears both weak identity references");
            check(HostProfileBridge.status().startsWith("FAILED:"), "Early callback cannot hide later hook failure");
            check(notifications.get() == 0, "Failed early registration cannot publish queued observation");
        } else {
            check(HostProfileBridge.observed() && observedStorage() == config && HostProfileBridge.status().equals("OBSERVED"),
                    "Early callback is retained through deferred Ready tuple capture");
            check(notifications.get() == 1, "First host callback during registration is delivered exactly once after drain");
            identity(config, true);
        }
        check(config.ids.size() == (fail ? 0 : 2), "No proactive identity read occurs inside callback registration");
    }
    private static void collision(boolean missing) throws Exception {
        HostTargetResolver.candidates = missing ? List.of() : List.of(readerMethod(), OtherConfig.class.getMethod("identity", int.class, Object.class));
        HostProfileBridge.initialize(); HostTargetResolver.runOne();
        check(!installed() && HostProfileBridge.status().startsWith("FAILED:") && !HostProfileBridge.observed(),
                "Missing or colliding reader fails closed instead of picking first");
        check(XposedBridge.hooks.isEmpty() && !HostProfileBridge.accepts(new ConfigStore()),
                "Rejected resolver result never publishes reader or hooks");
        start();
    }
    private static void nonInstance(Class<?> owner) throws Exception {
        HostTargetResolver.candidates = List.of(owner.getMethod("identity", int.class, Object.class));
        HostProfileBridge.initialize(); HostTargetResolver.runOne();
        check(!installed() && HostProfileBridge.status().startsWith("FAILED:") && XposedBridge.hooks.isEmpty(),
                "Static or abstract identity reader is rejected before hooking");
        check(field(HostProfileBridge.class, "reader").get(null) == null && !HostProfileBridge.accepts(new ConfigStore()),
                "Invalid reader cannot open acceptance gate");
    }
    private static void resolveFailure() throws Exception {
        HostTargetResolver.resolveFailure = new PrivateThrowable();
        HostProfileBridge.initialize(); HostTargetResolver.runOne();
        check(!installed() && !HostProfileBridge.observed() && XposedBridge.hooks.isEmpty(), "Resolver Throwable is isolated and fail-closed");
        check(HostProfileBridge.status().startsWith("FAILED:"), "Resolver failure reports explicit stage");
        HostTargetResolver.resolveFailure = null; start();
    }
    private static void submitFailure() throws Exception {
        HostTargetResolver.submitFailure = new AssertionError(MESSAGE);
        HostProfileBridge.initialize();
        check(!installed() && !HostProfileBridge.observed() && HostTargetResolver.workers.isEmpty(), "Submission Error is isolated and retryable");
        check(HostProfileBridge.status().startsWith("FAILED:") && !HostProfileBridge.accepts(new ConfigStore()), "Submission failure is fail-closed");
        HostTargetResolver.submitFailure = null; start();
    }
    private static void hookFailure() throws Exception {
        HostTargetResolver.candidates = List.of(readerMethod()); XposedBridge.failures.put(readerMethod(), new PrivateThrowable());
        HostProfileBridge.initialize(); HostTargetResolver.runOne();
        check(!installed() && !HostProfileBridge.observed() && XposedBridge.hooks.isEmpty(), "Hook Throwable cannot pretend successful installation");
        check(field(HostProfileBridge.class, "reader").get(null) == null && !HostProfileBridge.accepts(new ConfigStore()),
                "Prepublished reader is cleared on failed hook registration");
        XposedBridge.failures.clear(); start();
        check(XposedBridge.attempts.get(readerMethod()) == 2, "Failed hook does not prevent actual retry");
    }
    private static void hostAfter() throws Throwable {
        start(); startDatabase(); ConfigStore config = new ConfigStore();
        fixtureKernel = kernel(AccountPhase.NotReady, false); fixtureKernel.core.config = config;
        kernelAfter(fixtureKernel, null);
        AtomicInteger notices = new AtomicInteger(); Runnable listener = notices::incrementAndGet;
        HostProfileBridge.subscribe(listener);
        XposedBridge.after(readerMethod(), config, null, new PrivateThrowable(), new Object[]{2, ""});
        HostTargetResolver.drain();
        check(!HostProfileBridge.observed() && notices.get() == 0, "Failed host call after never observes instance");
        identityAfter(new SameShape(), config); HostTargetResolver.drain();
        check(!HostProfileBridge.observed(), "Same-shaped owner is refused even if result is real ConfigStorage");
        identityAfter(config, new SameShape()); HostTargetResolver.drain();
        check(!HostProfileBridge.observed() && notices.get() == 0, "Successful identity callback cannot bypass NotReady account gate");
        fixtureKernel.account.phase = AccountPhase.AccountHasReady;
        identityAfter(config, new SameShape());
        check(!HostProfileBridge.observed() && notices.get() == 0 && config.ids.isEmpty(), "Host after only queues capture, never reads or publishes inline");
        HostTargetResolver.drain();
        check(observedStorage() == config && notices.get() == 1 && config.ids.isEmpty(),
                "Successful host after captures actual holder, not return value, without proactive reader call");
        identityAfter(config, null); HostTargetResolver.drain();
        check(notices.get() == 1, "Same instance observation is idempotent");
        identity(config, true);
    }
    @SuppressWarnings("unchecked")
    private static void selfReadingRecursion() throws Throwable {
        start(); ConfigStore original = new ConfigStore(), replacement = new ConfigStore();
        AtomicInteger notices = new AtomicInteger(); Runnable listener = notices::incrementAndGet; HostProfileBridge.subscribe(listener);
        observeReady(original); original.emitAfter = true; original.callbackOwner = replacement;
        HostTargetResolver.onWorker(() -> {
            ThreadLocal<Boolean> reading = (ThreadLocal<Boolean>) field(HostProfileBridge.class, "READING").get(null);
            identity(original, true);
            check(observedStorage() == original && notices.get() == 1 && HostTargetResolver.workers.isEmpty(), "Self-read hooked callbacks cannot replace identity or recursively enqueue capture");
            check(reading.get() == null && !HostProfileBridge.readInProgress(), "Self-read removes previously absent thread-local guard");
            reading.set(false); identity(original, true);
            check(Boolean.FALSE.equals(reading.get()) && observedStorage() == original, "Nested guard restores previous false value");
            reading.set(true); identity(original, true);
            check(Boolean.TRUE.equals(reading.get()) && HostProfileBridge.readInProgress(), "Nested guard restores previous true value");
            reading.remove(); return null;
        });
        fixtureKernel.core.config = replacement; identityAfter(replacement, null); HostTargetResolver.drain();
        check(observedStorage() == replacement && notices.get() == 2, "Subsequent host callback with actual replacement holder may observe new storage");
    }
    @SuppressWarnings("unchecked")
    private static void mandatoryReaderFailure() throws Throwable {
        start(); ConfigStore config = new ConfigStore(); observeReady(config);
        HostTargetResolver.onWorker(() -> {
            ThreadLocal<Boolean> reading = (ThreadLocal<Boolean>) field(HostProfileBridge.class, "READING").get(null);
            for (Object value : new Object[]{null, "", "  ", Integer.valueOf(2)}) {
                config.account = value; config.ids.clear(); config.defaults.clear();
                try { workerIdentity(); throw new AssertionError("Invalid account accepted"); }
                catch (Exception expected) { check(config.ids.equals(List.of(2)), "Invalid mandatory account stops before optional name read"); }
                check(reading.get() == null && !HostProfileBridge.readInProgress() && !HostDatabaseBridge.isReady(), "Failed account read clears worker guard and cannot make unrelated database ready");
            }
            for (Throwable failure : new Throwable[]{new PrivateThrowable(), new AssertionError(MESSAGE)}) {
                config.account = ACCOUNT; config.accountFailure = failure; config.ids.clear();
                try { workerIdentity(); throw new AssertionError("Failing account reader accepted"); }
                catch (Exception expected) { check(config.ids.equals(List.of(2)) && reading.get() == null && !HostProfileBridge.readInProgress(), "Reflected reader failure is fail-closed and clears actual worker recursion guard"); }
            }
            return null;
        });
        config.accountFailure = null; config.ids.clear(); config.defaults.clear(); identity(config, true);
    }
    private static void optionalNameFailure() throws Throwable {
        start(); ConfigStore config = new ConfigStore(); observeReady(config);
        for (Object value : new Object[]{null, Integer.valueOf(4)}) {
            config.nickname = value; config.ids.clear(); config.defaults.clear();
            HostProfileBridge.Identity result = workerIdentity();
            check(result.nickname == null && ACCOUNT.equals(result.account), "Non-string optional name is unavailable, not an explicit empty result");
        }
        config.nickname = ""; config.ids.clear(); config.defaults.clear();
        check("".equals(workerIdentity().nickname), "Explicit empty optional name remains distinct from unavailable null");
        config.nicknameFailure = new PrivateThrowable(); config.ids.clear(); config.defaults.clear();
        check(workerIdentity().nickname == null, "Optional name reader failure returns null to preserve known same-account nickname");
        check(HostProfileBridge.status().equals("IDENTITY_READY"), "Optional name Throwable still yields identity-ready account");
    }
    @SuppressWarnings("unchecked")
    private static void listenerIsolation() throws Throwable {
        start(); AtomicInteger healthy = new AtomicInteger(), failed = new AtomicInteger();
        Runnable good = healthy::incrementAndGet;
        Runnable bad = () -> { failed.incrementAndGet(); HostTargetResolver.raise(new PrivateThrowable()); };
        HostProfileBridge.subscribe(null); HostProfileBridge.subscribe(good); HostProfileBridge.subscribe(bad);
        List<WeakReference<Runnable>> listeners = (List<WeakReference<Runnable>>) field(HostProfileBridge.class, "LISTENERS").get(null);
        listeners.add(new WeakReference<>(null));
        check(healthy.get() == 0 && failed.get() == 0, "Listeners are deferred until a real host instance is observed");
        ConfigStore first = new ConfigStore(); observeReady(first);
        check(healthy.get() == 1 && failed.get() == 1 && listeners.size() == 2, "Bad listener and dead weak ref cannot block healthy callback");
        HostProfileBridge.subscribe(bad); HostProfileBridge.subscribe(good);
        check(healthy.get() == 2 && failed.get() == 2, "Immediate observed subscriptions also isolate listener failures");
        ConfigStore replacement = new ConfigStore(); observeReady(replacement);
        check(healthy.get() == 4 && failed.get() == 4, "Replacement storage still notifies all surviving listeners");
    }
    private static void startDatabase() throws Exception { startDatabase(Kernel.class, false); }
    private static void startDatabase(Class<?> kernel, boolean readyCallback) throws Exception {
        if (databaseStarted) return;
        databaseStarted = true;
        HostTargetResolver.accountCandidates = List.of(kernel.getMethod("accountStorage"));
        HostTargetResolver.readyCandidates = readyCallback ? List.of(ReadyTask.class.getMethod("run")) : List.of();
        XC_LoadPackage.LoadPackageParam load = new XC_LoadPackage.LoadPackageParam(); load.classLoader = ProfileConfigChecks.class.getClassLoader();
        HostDatabaseBridge.initialize(load, new Context());
        HostTargetResolver.resolveDatabase(kernel.getMethod("coreStorage"));
        check(!HostProfileBridge.observed() && !HostDatabaseBridge.isReady(),
                "Real database bridge installed without proactive Kernel query or fake profile capture");
        if (kernel == Kernel.class || kernel == UninitializedKernel.class)
            check("HOOKED".equals(HostDatabaseBridge.status()), "Structurally valid kernel reaches registered stage");
        else check("HOOKED".equals(HostDatabaseBridge.status()) && indexOf(XposedBridge.events, "stage=kernel-observe failed") >= 0,
                "Malformed optional kernel metadata does not poison valid database hook status");
        check(XposedBridge.hooks.containsKey(CoreStorage.class.getMethod("configGetter")), "Existing CoreStorage config getter-after is observed");
    }
    private static Kernel kernel(AccountPhase phase, boolean hasDatabase) {
        Kernel value = new Kernel(); value.core = new CoreStorage(); value.account = new AccountStorage();
        value.account.phase = phase; value.core.config = new ConfigStore();
        if (hasDatabase) value.core.config.database = new Database();
        return value;
    }
    private static void kernelAfter(Object kernel, Throwable failure) throws Throwable {
        XposedBridge.after(kernel.getClass().getMethod("instance"), null, kernel, failure, new Object[0]);
    }
    private static void getterAfterConfigFirst() throws Throwable {
        start(); startDatabase();
        CoreStorage storage = new CoreStorage(); storage.config = new ConfigStore(); storage.config.database = new Database();
        AtomicInteger notices = new AtomicInteger();
        Runnable listener = () -> {
            check(HostDatabaseBridge.isReady(), "Config direct database is captured before profile listeners");
            check(android.os.Looper.myLooper() != android.os.Looper.getMainLooper(), "Capture publication occurs on worker");
            notices.incrementAndGet();
        };
        HostProfileBridge.subscribe(listener);
        XposedBridge.after(CoreStorage.class.getMethod("configGetter"), storage, storage.config);
        HostTargetResolver.drain();
        check(!HostProfileBridge.observed() && !HostDatabaseBridge.isReady() && notices.get() == 0,
                "Getter result alone cannot prove account Ready or fabricate observation");
        fixtureKernel = kernel(AccountPhase.AccountHasReady, false); fixtureKernel.core = storage;
        kernelAfter(fixtureKernel, null);
        check(!HostDatabaseBridge.isReady() && ((Database) storage.config.database).openReads == 0 && storage.config.ids.isEmpty(),
                "Ready callback remains host-call/SQL/identity-free until worker drain");
        HostTargetResolver.drain();
        check(HostProfileBridge.observed() && observedStorage() == storage.config && activeDatabase() == storage.config.database,
                "Worker captures existing actual ConfigStorage and direct open DB under Ready tuple");
        check(notices.get() == 1 && storage.getters == 0 && storage.config.getterCalls == 0 && Kernel.getters == 0,
                "Observation never invokes CoreStorage, ConfigStorage or Kernel getters proactively");
        check(storage.config.ids.isEmpty(), "Capture never proactively reads identity values");
        check(HostDatabaseBridge.status().startsWith("READY:"), "Worker validated direct DB has explicit ready stage");
        List<String> events = XposedBridge.events;
        int configEvent = indexOf(events, "stage=capture observed"), dbEvent = indexOf(events, "stage=capture ready");
        check(configEvent >= 0 && dbEvent > configEvent, "Profile publication precedes DB readiness notification after capture");
        CoreStorage second = new CoreStorage(); second.config = new ConfigStore(); second.direct = new Database();
        fixtureKernel.core = second;
        events.clear(); XposedBridge.after(CoreStorage.class.getMethod("configGetter"), second, second.config);
        HostTargetResolver.drain();
        check(indexOf(events, "stage=capture observed") >= 0 && indexOf(events, "stage=capture ready") > indexOf(events, "stage=capture observed"),
                "Merged getter-after capture preserves profile then database notification order");
        check(observedStorage() == second.config && activeDatabase() == second.direct, "Current CoreStorage captures direct DB and actual profile holder");
        check(second.getters == 0 && Kernel.getters == 0, "Existing-instance capture still calls no getters");
    }
    private static void identityBoundDatabase() throws Throwable {
        start(); SameShape same = new SameShape();
        HostDatabaseBridge.captureConfigStorage(same);
        check(!HostDatabaseBridge.isReady() && same.database.openReads == 0, "Runtime direct DB capture refuses same-shaped nondeclaring-class ConfigStorage before probing DB body");
        ConfigStore config = new ConfigStore(); config.database = new Database();
        HostDatabaseBridge.captureConfigStorage(config);
        check(!HostDatabaseBridge.isReady(), "Accepted standalone config still cannot bypass Ready kernel gate");
        observeReady(config);
        check(HostDatabaseBridge.isReady() && activeDatabase() == config.database, "Ready actual reader-declaring-class config captures interface-typed direct database metadata");
        Database db = (Database) config.database;
        check(db.paths == 0 && db.openReads == 1 && db.queries == 0 && config.getterCalls == 0,
                "Worker capture checks metadata/isOpen only, never database path, SQL, or config getter");
        int identityReads = config.ids.size(), openReads = db.openReads;
        try { HostProfileBridge.readIdentity(); throw new AssertionError("MAIN identity read accepted"); }
        catch (IllegalStateException expected) { check(config.ids.size() == identityReads && db.openReads == openReads, "MAIN identity refusal never executes host identity/isOpen"); }
        try { HostDatabaseBridge.rawQuery(SQL, new String[]{ACCOUNT}); throw new AssertionError("MAIN SQL read accepted"); }
        catch (IllegalStateException expected) { check(db.queries == 0 && db.openReads == openReads, "MAIN query refusal never executes host query/isOpen"); }
        String[] params = {ACCOUNT};
        check(HostTargetResolver.onWorker(() -> HostDatabaseBridge.rawQuery(SQL, params)) == db.cursor && db.sql.equals(SQL) && db.args == params,
                "Real Object[] rawQuery reflection preserves SQL bind array identity");
        check(db.queries == 1 && db.paths == 0, "Only explicitly requested read reaches captured database");
    }
    private static void closedDatabase() throws Throwable {
        start(); ConfigStore config = new ConfigStore(); Database db = new Database(); db.open = false; config.database = db;
        observeReady(config);
        check(!HostProfileBridge.observed() && !HostDatabaseBridge.isReady() && db.openReads == 1,
                "Closed direct DB worker validation revokes both config observation and readiness");
        db.open = true; config.ids.clear(); config.defaults.clear();
        observeReady(config); identity(config, true);
        check(HostDatabaseBridge.isReady() && activeDatabase() == db, "Genuine Ready host callback recaptures now-open existing direct DB");
    }
    private static void rejectNestedDatabase() throws Throwable {
        start(); ConfigStore config = new ConfigStore();
        config.nested = new NestedDatabase(); config.erased = new Database(); config.invalid = new BadDatabase();
        ConfigStore.staticDatabase = new Database();
        HostProfileBridge.observe(config); HostTargetResolver.drain();
        check(!HostDatabaseBridge.isReady() && activeDatabase() == null && ((Database) config.erased).openReads == 0,
                "Erased runtime database cannot bypass missing Ready tuple");
        config.erased = null; observeReady(config);
        check(!HostDatabaseBridge.isReady() && activeDatabase() == null, "No nested, static or invalid-metadata DB is captured");
        check(config.nested.database.openReads == 0 && ((Database) ConfigStore.staticDatabase).openReads == 0 && config.invalid.reads == 0,
                "Rejected fields never execute database path/open/query bodies");
        config.database = new Database(); observeReady(config);
        check(activeDatabase() == config.database && config.getterCalls == 0, "Valid actual direct DB can capture without recursive/getter fallback");
    }
    private static void ambiguousDatabase() throws Throwable {
        start(); ConfigStore config = new ConfigStore(); config.database = new Database(); config.second = new Database();
        observeReady(config);
        check(!HostProfileBridge.observed() && !HostDatabaseBridge.isReady() && activeDatabase() == null,
                "Two distinct direct DB candidates fail closed before publishing profile or database");
        check(((Database) config.database).openReads == 0 && ((Database) config.second).openReads == 0,
                "Ambiguous direct fields are rejected before open/query bodies");
        config.second = config.database; observeReady(config);
        check(HostDatabaseBridge.isReady() && activeDatabase() == config.database, "Two field aliases to same DB instance are not false ambiguity");
    }
    private static void lateDatabase() throws Throwable {
        start(); ConfigStore config = new ConfigStore(); AtomicInteger ready = new AtomicInteger(); Runnable listener = ready::incrementAndGet;
        HostDatabaseBridge.subscribeReady(listener); observeReady(config);
        check(!HostDatabaseBridge.isReady() && ready.get() == 0, "Config instance observation alone does not fabricate missing DB");
        config.database = new Database(); observeReady(config); identity(config, true);
        check(HostDatabaseBridge.isReady() && ready.get() == 1 && activeDatabase() == config.database,
                "Genuine Ready callback recaptures late actual direct DB and publishes readiness");
        identity(config, true); HostDatabaseBridge.captureConfigStorage(config); HostTargetResolver.drain();
        check(ready.get() == 1, "Repeated identical DB capture does not renotify");
        Database replacement = new Database(); config.database = replacement; observeReady(config); identity(config, true);
        check(ready.get() == 2 && activeDatabase() == replacement, "Replacement direct config DB is captured and notified once");
    }
    private static void privacyLogger() throws Throwable {
        start(); XposedBridge.logFailure = new PrivateThrowable();
        ConfigStore config = new ConfigStore(); config.database = new Database(); observeReady(config);
        config.nicknameFailure = new PrivateThrowable(); identity(config, false);
        check(HostProfileBridge.observed() && HostDatabaseBridge.isReady(), "Throwing logger cannot poison real observe/identity/direct capture behavior");
        XposedBridge.logFailure = null;
        try { HostDatabaseBridge.rawQuery("DELETE FROM userinfo", new String[]{ACCOUNT}); throw new AssertionError("Write SQL accepted"); }
        catch (SecurityException expected) { check(((Database) config.database).queries == 0, "Rejected write never reaches captured DB"); }
    }
    private static void kernelReadyReplay(boolean beforeReader) throws Throwable {
        if (!beforeReader) start();
        startDatabase();
        Kernel value = kernel(AccountPhase.AccountHasReady, true);
        AtomicInteger profile = new AtomicInteger(), database = new AtomicInteger();
        Runnable profileListener = profile::incrementAndGet, databaseListener = database::incrementAndGet;
        HostProfileBridge.subscribe(profileListener); HostDatabaseBridge.subscribeReady(databaseListener);
        kernelAfter(value, null);
        check(!HostProfileBridge.observed() && !HostDatabaseBridge.isReady() && ((Database) value.core.config.database).openReads == 0,
                "Kernel callback defers all DB validation/publication to worker");
        HostTargetResolver.drain();
        if (beforeReader) {
            check(!HostProfileBridge.observed(), "Existing kernel arriving before reader does not guess ConfigStorage type");
            start(true);
        }
        check(HostProfileBridge.observed() && observedStorage() == value.core.config,
                "Ready existing kernel replays its unique direct typed ConfigStorage without a getter invocation");
        check(HostDatabaseBridge.isReady() && activeDatabase() == value.core.config.database,
                "Kernel direct typed config database becomes ready from real capture code");
        check(profile.get() == 1 && database.get() == 1 && value.core.config.ids.isEmpty(),
                "Reader resolution replay publishes each readiness once without reading identity proactively");
        check(Kernel.getters == 0 && value.core.getters == 0 && value.account.readinessReads == 0 && value.account.assertions == 0,
                "Kernel, storage and account gate method bodies are never proactively executed");
        HostTargetResolver.MethodSpec spec = HostTargetResolver.accountSpec;
        check(spec != null && Kernel.class.getName().equals(spec.declaredClass)
                        && Arrays.equals(spec.strings, new String[]{"mCoreAccount not initialized!"})
                        && spec.count == 0 && spec.access == (Modifier.PUBLIC | Modifier.STATIC),
                "Account getter is uniquely resolved by same kernel owner, exact anchor and public static zeroargs");
    }
    private static void kernelNotReady() throws Throwable {
        start(); startDatabase(); Kernel value = kernel(AccountPhase.NotReady, true);
        value.core.direct = new Database();
        AtomicInteger profile = new AtomicInteger(), database = new AtomicInteger();
        Runnable a = profile::incrementAndGet, b = database::incrementAndGet;
        HostProfileBridge.subscribe(a); HostDatabaseBridge.subscribeReady(b);
        kernelAfter(value, null);
        check(!HostProfileBridge.observed() && !HostDatabaseBridge.isReady() && profile.get() == 0 && database.get() == 0,
                "NotReady existing enum cannot publish config/database/listeners");
        check(((Database) value.core.config.database).openReads == 0 && ((Database) value.core.direct).openReads == 0,
                "NotReady gate rejects before database open/path/query body access");
        value.account.phase = null; kernelAfter(value, null);
        check(!HostProfileBridge.observed() && !HostDatabaseBridge.isReady(), "Null readiness enum remains default-deny");
        value.account.phase = AccountPhase.AccountHasReady; kernelAfter(value, null); HostTargetResolver.drain();
        check(HostProfileBridge.observed() && profile.get() == 1 && HostDatabaseBridge.isReady(),
                "Later genuine Ready existing kernel callback recovers without getter invocation");
    }
    private static void kernelFailedAfter() throws Throwable {
        start(); startDatabase(); Kernel value = kernel(AccountPhase.AccountHasReady, true);
        kernelAfter(value, new PrivateThrowable());
        check(!HostProfileBridge.observed() && !HostDatabaseBridge.isReady(), "Failed instance-getter callback is never retained/captured");
        XposedBridge.after(Kernel.class.getMethod("instance"), null, new SameShape());
        check(!HostProfileBridge.observed(), "Wrong instance result class is refused");
        check(((Database) value.core.config.database).openReads == 0, "Failed after never probes config database");
        kernelAfter(value, null); HostTargetResolver.drain(); check(HostProfileBridge.observed(), "Subsequent successful instance result remains retryable");
    }
    private static void noKernelInitialization() throws Throwable {
        check(InitializationGuard.kernelInitializations == 0, "Synthetic uninitialized host starts untouched");
        start(); startDatabase(UninitializedKernel.class, false);
        check(InitializationGuard.kernelInitializations == 0, "Resolution and passive hook registration must not initialize kernel class");
        check(!HostProfileBridge.observed() && !HostDatabaseBridge.isReady(), "No initialized instance means no fake capture");
        check(XposedBridge.hooks.containsKey(UninitializedKernel.class.getMethod("instance")), "Uninitialized host instance getter is only hooked");
        XposedBridge.after(UninitializedKernel.class.getMethod("instance"), null, null);
        check(InitializationGuard.kernelInitializations == 0 && !HostProfileBridge.observed(),
                "Null after result cannot read a static singleton or trigger host clinit");
    }
    private static void constructorPartial() throws Throwable {
        start(); startDatabase(); CoreStorage storage = new CoreStorage(); storage.config = new ConfigStore();
        storage.config.database = new Database();
        XposedBridge.after(CoreStorage.class.getDeclaredConstructor(), storage, null);
        check(!HostProfileBridge.observed() && !HostDatabaseBridge.isReady(),
                "CoreStorage constructor alone cannot make nested pre-ready config/database observable");
        check(((Database) storage.config.database).openReads == 0,
                "Partial constructor callback does not probe nested config database metadata bodies");
        Kernel value = kernel(AccountPhase.NotReady, false);
        value.core.direct = new Database();
        XposedBridge.after(CoreStorage.class.getDeclaredConstructor(), value.core, null);
        HostTargetResolver.drain();
        check(!HostDatabaseBridge.isReady() && ((Database) value.core.direct).openReads == 0
                        && !HostProfileBridge.observed() && value.core.config.ids.isEmpty(),
                "Partial constructor direct DB cannot bypass genuine kernel account Ready gate");
        XposedBridge.after(CoreStorage.class.getDeclaredConstructor(), storage, null, new PrivateThrowable(), new Object[0]);
        check(!HostProfileBridge.observed(), "Failed partial constructor is discarded");
    }
    private static void readyRunnable(boolean failed) throws Throwable {
        start(); startDatabase(Kernel.class, true); Kernel value = kernel(AccountPhase.NotReady, true);
        AtomicInteger notices = new AtomicInteger(); Runnable listener = notices::incrementAndGet; HostProfileBridge.subscribe(listener);
        kernelAfter(value, null); check(!HostProfileBridge.observed(), "Ready callback scenario begins with only existing NotReady kernel");
        ReadyTask task = new ReadyTask(value.account); value.account.phase = AccountPhase.AccountHasReady;
        XposedBridge.after(ReadyTask.class.getMethod("run"), task, null, failed ? new PrivateThrowable() : null, new Object[0]);
        check(!HostProfileBridge.observed() && !HostDatabaseBridge.isReady(), "Ready task after never publishes inline");
        HostTargetResolver.drain();
        check(HostProfileBridge.observed() == !failed && HostDatabaseBridge.isReady() == !failed && notices.get() == (failed ? 0 : 1),
                "Ready Runnable after captures only successful existing same-account Ready state");
        check(task.runs == 0 && Kernel.getters == 0, "Only synthetic after delivery occurs, no Ready or Kernel host body execution");
        HostTargetResolver.MethodSpec spec = HostTargetResolver.readySpec;
        check(spec != null && Arrays.equals(spec.strings, new String[]{"changeToAccountHasReady"})
                        && "run".equals(spec.name) && "void".equals(spec.returnType)
                        && spec.count == 0 && spec.access == Modifier.PUBLIC,
                "Optional Ready task uses strict run/void/noargs/public and compiled Ready marker");
        if (failed) {
            XposedBridge.after(ReadyTask.class.getMethod("run"), task, null); HostTargetResolver.drain();
            check(HostProfileBridge.observed() && notices.get() == 1, "Successful later Ready callback can recover after throwable");
        }
    }
    private static void malformedKernel(Class<?> type) throws Throwable {
        start(); startDatabase(type, false);
        Method getter = type.getMethod("instance");
        if (XposedBridge.hooks.containsKey(getter)) {
            Object instance = type.getDeclaredConstructor().newInstance();
            for (Field candidate : type.getDeclaredFields()) {
                if (Modifier.isStatic(candidate.getModifiers())) continue;
                if (candidate.getType() == CoreStorage.class) { CoreStorage core = new CoreStorage(); core.config = new ConfigStore(); core.config.database = new Database(); candidate.set(instance, core); }
                else if (candidate.getType() == AccountStorage.class) { AccountStorage account = new AccountStorage(); account.phase = AccountPhase.AccountHasReady; candidate.set(instance, account); }
            }
            XposedBridge.after(getter, null, instance);
        }
        HostTargetResolver.drain();
        check(!HostProfileBridge.observed() && !HostDatabaseBridge.isReady(),
                "Ambiguous/static/Object/nested/invalid singleton or readiness structural fields are rejected");
    }
    private static void sameInstanceThrottle() throws Throwable {
        start(); ConfigStore config = new ConfigStore(); AtomicInteger notices = new AtomicInteger();
        Runnable listener = notices::incrementAndGet; HostProfileBridge.subscribe(listener);
        observeReady(config);
        long scans = field(HostDatabaseBridge.class, "captureEvents").getLong(null);
        for (int i = 0; i < 5; i++) identityAfter(config, null);
        check(HostTargetResolver.workers.isEmpty() && field(HostDatabaseBridge.class, "captureEvents").getLong(null) == scans,
                "Throttled same instance callback performs no scan or worker submission");
        check(notices.get() == 1, "Same instance host reader callbacks are throttled to one notice per two seconds");
        Thread.sleep(2100); identityAfter(config, null);
        check(notices.get() == 2, "Genuine later same-instance host read republishes after throttle expires");
        HostTargetResolver.drain();
        config.emitAfter = true; identity(config, true);
        check(notices.get() == 2 && HostTargetResolver.workers.isEmpty(), "Self-reader invocation does not schedule recursive same-instance profile refresh");
    }
    private static void identity(ConfigStore config, boolean hasName) throws Exception {
        int before = config.ids.size(); HostProfileBridge.Identity identity = workerIdentity();
        check(ACCOUNT.equals(identity.account) && (hasName ? NAME.equals(identity.nickname) : identity.nickname == null || identity.nickname.isEmpty()), "Real reader yields exact account and independently optional nickname");
        check(config.ids.subList(before, config.ids.size()).equals(List.of(2, 4)), "Real reader invokes only identity id2 then nickname id4");
        check(config.defaults.subList(before, config.defaults.size()).equals(List.of("", "")), "Both real reflected reads receive empty default Object");
        check(HostProfileBridge.status().equals("IDENTITY_READY"), "Successful real reader publishes explicit identity-ready state");
    }
    private static int indexOf(List<String> values, String token) {
        for (int i = 0; i < values.size(); i++) if (values.get(i).contains(token)) return i;
        return -1;
    }
    private static void privacy() {
        List<String> diagnostics = new ArrayList<>(XposedBridge.logs); diagnostics.addAll(HostStageLog.stages);
        for (String text : diagnostics) check(!text.contains(ACCOUNT) && !text.contains(NAME) && !text.contains(PATH)
                && !text.contains(MESSAGE) && !text.contains(SQL), "Logs/stages have no account, nickname, path, SQL or host exception message");
        check(messageReads.get() == 0, "No logger reads host Throwable message");
    }
    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}
