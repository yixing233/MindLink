package wex.ui.core.feature;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import wex.ui.core.WexStore;

/** Three real production bridges. Doubles exist only at Android/DEX/Xposed/DB boundaries. */
public final class ProfileIntegrationChecks {
    private static final String ACCOUNT = "synthetic-profile-account", NEXT = "synthetic-next-account";
    private static final String NAME = "synthetic-config-nickname", NEW_NAME = "synthetic-fresh-nickname";
    private static final String MESSAGE = "synthetic-host-failure-private";
    private static final String ACCOUNT_SQL = "SELECT value AS wxid FROM userinfo WHERE id=2 LIMIT 1";
    private static final String NAME_SQL = "SELECT nickname FROM rcontact WHERE username=? LIMIT 1";
    private static final String AVATAR_SQL = "SELECT reserved2 AS avatarUrl FROM img_flag WHERE username=? LIMIT 1";
    private static final AtomicInteger network = new AtomicInteger(), messageReads = new AtomicInteger();
    private static final AtomicReference<Throwable> uncaught = new AtomicReference<>();
    private static int checks;

    public enum Phase { NotReady, AccountHasReady }
    public static final class Account {
        public volatile Phase phase = Phase.NotReady;
        public void assertReady() { throw new AssertionError("Do not proactively call account gate"); }
    }
    public interface DbHandle { }
    public static class DbBase { }
    public static class CoreBase { public DbBase inheritedDatabase; }
    public static final class Core extends CoreBase {
        public Config config;
        public Db direct;
        public Object erased;
        public Noise a = new Noise(), b = new Noise(), c = new Noise(), d = new Noise();
        int getterCalls;
        boolean allowRecovery;
        Object recovered;
        public Config configGetter() {
            getterCalls++;
            Kernel owner = TestState.current;
            if (!allowRecovery || owner == null || owner.core != this || owner.account.phase != Phase.AccountHasReady)
                throw new AssertionError("Core getter only runs under proven existing Ready owner");
            config = (Config) recovered;
            return config;
        }
    }
    public static final class Kernel {
        public Core core;
        public Account account;
        public static Kernel singleton;
        static int getterCalls;
        public static Core storage() { getterCalls++; throw new AssertionError("Do not invoke Kernel storage"); }
        public static Account accountStorage() { getterCalls++; throw new AssertionError("Do not invoke Kernel account"); }
        public static Kernel instance() { getterCalls++; throw new AssertionError("Do not invoke Kernel instance"); }
    }
    public static final class Ready implements Runnable {
        public final Account account;
        int calls;
        Ready(Account value) { account = value; }
        public void run() { calls++; throw new AssertionError("Only after callback is simulated"); }
    }
    public static final class PrivateThrowable extends Throwable {
        PrivateThrowable() { super(MESSAGE); }
        @Override public String getMessage() { messageReads.incrementAndGet(); return MESSAGE; }
    }
    public static final class Read {
        final CountDownLatch entered = new CountDownLatch(1), allow = new CountDownLatch(1);
        final String name;
        Read(String value) { name = value; }
    }
    public static final class Config {
        public Db database;
        public DbHandle interfaceDatabase;
        public DbBase wrapperDatabase;
        public Object erased;
        volatile String account = ACCOUNT;
        volatile Object name = NAME;
        volatile Throwable nameFailure;
        volatile boolean echoHook = true;
        final List<Integer> ids = new CopyOnWriteArrayList<>();
        final List<Object> defaults = new CopyOnWriteArrayList<>();
        final List<Boolean> mainReads = new CopyOnWriteArrayList<>();
        final List<Read> plans = new CopyOnWriteArrayList<>();
        final AtomicInteger accountReads = new AtomicInteger(), nicknameReads = new AtomicInteger();
        private final ThreadLocal<Read> active = new ThreadLocal<>();
        int getterCalls;
        public Object read(int id, Object fallback) throws Exception {
            ids.add(id); defaults.add(fallback);
            mainReads.add(Looper.myLooper() == Looper.getMainLooper());
            Object result;
            if (id == 2) {
                int position = accountReads.getAndIncrement();
                Read plan = position < plans.size() ? plans.get(position) : null;
                active.set(plan);
                if (plan != null) {
                    plan.entered.countDown();
                    if (!plan.allow.await(5, TimeUnit.SECONDS)) throw new AssertionError("Synthetic reader was not released");
                }
                result = account;
            } else {
                nicknameReads.incrementAndGet();
                if (nameFailure != null) HostTargetResolver.raise(nameFailure);
                Read plan = active.get(); result = plan == null ? name : plan.name;
            }
            if (echoHook && XposedBridge.hooks.containsKey(reader())) {
                try {
                    XposedBridge.after(reader(), this, result, null, new Object[]{id, fallback});
                    // Exercise the real suppression path of a host nested Kernel getter during the read.
                    Kernel existing = TestState.current;
                    if (existing != null && XposedBridge.hooks.containsKey(instance()))
                        XposedBridge.after(instance(), null, existing);
                } catch (Throwable error) { HostTargetResolver.raise(error); }
            }
            return result;
        }
        public Object databaseGetter() { getterCalls++; throw new AssertionError("Do not invoke Config getter"); }
    }
    private static final class TestState { static Kernel current; }
    public static final class Noise { public Object unrelated() { return null; } }
    public static final class Db extends DbBase implements DbHandle {
        volatile boolean open = true;
        volatile boolean closeOnAvatar;
        final AtomicInteger avatarCloses = new AtomicInteger();
        volatile Read openBlock;
        final List<Boolean> mainOpens = new CopyOnWriteArrayList<>();
        volatile String account = ACCOUNT, nickname = "synthetic-sql-nickname", avatar = "";
        volatile Throwable accountFailure, nameFailure, avatarFailure;
        volatile boolean missingAccount, missingName, missingAvatar;
        final AtomicInteger opens = new AtomicInteger(), queries = new AtomicInteger(), paths = new AtomicInteger();
        final List<String> sqls = new CopyOnWriteArrayList<>();
        final List<String[]> bindings = new CopyOnWriteArrayList<>();
        final List<Boolean> mainQueries = new CopyOnWriteArrayList<>();
        final List<RowCursor> cursors = new CopyOnWriteArrayList<>();
        public String getPath() { paths.incrementAndGet(); throw new AssertionError("Never read a real database path"); }
        public boolean isOpen() {
            check(!Thread.holdsLock(HostDatabaseBridge.class), "isOpen must run outside publication lock");
            mainOpens.add(Looper.myLooper() == Looper.getMainLooper()); opens.incrementAndGet();
            Read block = openBlock;
            if (block != null) {
                block.entered.countDown();
                try { if (!block.allow.await(5, TimeUnit.SECONDS)) throw new AssertionError("Unreleased isOpen boundary"); }
                catch (InterruptedException error) { throw new AssertionError(error); }
            }
            return open;
        }
        public Cursor rawQuery(String sql, String[] args) {
            queries.incrementAndGet(); sqls.add(sql); bindings.add(args == null ? new String[0] : args.clone());
            mainQueries.add(Looper.myLooper() == Looper.getMainLooper());
            Object value; String column; Throwable failure; boolean missing;
            if (ACCOUNT_SQL.equals(sql)) { value = account; column = "wxid"; failure = accountFailure; missing = missingAccount; }
            else if (NAME_SQL.equals(sql)) { value = nickname; column = "nickname"; failure = nameFailure; missing = missingName; }
            else if (AVATAR_SQL.equals(sql)) { value = avatar; column = "avatarUrl"; failure = avatarFailure; missing = missingAvatar; }
            else throw new AssertionError("Unexpected SQL in three-bridge integration");
            if (!ACCOUNT_SQL.equals(sql) && !Arrays.equals(args, new String[]{account}))
                throw new AssertionError("Optional query must bind only the synthetic confirmed account");
            if (ACCOUNT_SQL.equals(sql) && args != null) throw new AssertionError("Account query has null binds");
            if (failure != null) HostTargetResolver.raise(failure);
            if (AVATAR_SQL.equals(sql) && closeOnAvatar) { open = false; avatarCloses.incrementAndGet(); }
            RowCursor cursor = new RowCursor(column, value, missing); cursors.add(cursor); return cursor;
        }
    }
    public static final class RowCursor implements Cursor {
        final String column; final Object value; final boolean empty;
        int position = -1; boolean closed;
        RowCursor(String key, Object data, boolean missing) { column = key; value = data; empty = missing; }
        public String[] getColumnNames() { return new String[]{column}; }
        public boolean moveToNext() { return !empty && ++position == 0; }
        public int getType(int index) { return value == null ? FIELD_TYPE_NULL : FIELD_TYPE_STRING; }
        public long getLong(int index) { throw new AssertionError("No numeric profile field"); }
        public double getDouble(int index) { throw new AssertionError("No floating profile field"); }
        public byte[] getBlob(int index) { throw new AssertionError("No profile blob field"); }
        public String getString(int index) { return (String) value; }
        public void close() { closed = true; }
    }

    public static void main(String[] args) throws Throwable {
        Looper.getMainLooper();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> uncaught.compareAndSet(null, error));
        URL.setURLStreamHandlerFactory(protocol -> new URLStreamHandler() {
            @Override protected URLConnection openConnection(URL url) {
                network.incrementAndGet(); throw new AssertionError("Integration prohibits external network");
            }
        });
        try {
            switch (args[0]) {
                case "actual-db-field-evidence": actualDbFields(); break;
                case "last-query-close-rejected": lastQueryClose(); break;
                case "class-identity-metadata": classIdentity(); break;
                case "source-version-queued-reject": sourceVersion(); break;
                case "hot-getter-bounded": hotGetter(); break;
                case "callback-monitor-independent": monitorIndependent(); break;
                case "rapid-hooks-coalesced": rapidHooks(); break;
                case "self-read-no-capture-queue": selfReadQueue(); break;
                case "late-db-replacement-closed": lateDb(); break;
                case "worker-epoch-reject": workerEpoch(); break;
                case "deferred-before-capture": deferredCapture(); break;
                case "not-ready-to-ready": readyIntegration(false); break;
                case "failed-ready-after": readyIntegration(true); break;
                case "kernel-before-reader": readerOrder(); break;
                case "self-read-coalesced": selfRead(); break;
                case "forced-tail-latest-name": forcedTail(); break;
                case "optional-name-failure-retains": optionalNameRetains(); break;
                case "mandatory-db-failure-config-only": mandatoryFailure(); break;
                case "preference-switch-rejects-old": preferenceSwitch(); break;
                case "callbacks-outside-bridge-locks": locks(); break;
                case "safe-late-config-getter": lateConfigGetter(); break;
                case "ready-without-holder-evidence": readyWithoutHolder(); break;
                case "callback-evidence-budget": callbackBudget(); break;
                case "ready-cache-invalidation": cacheInvalidation(); break;
                case "empty-instance-demand-blocked": emptyDemand(); break;
                case "not-ready-queued-result-rejected": revokedResult(); break;
                case "pinned-early-construct-ready": pinnedEarly(true, args[1]); break;
                case "unpinned-early-skipped": pinnedEarly(false, args[1]); break;
                case "pinned-selected-loader": pinnedSelected(args[1]); break;
                default: throw new AssertionError("Unknown integration case");
            }
            check(uncaught.get() == null && network.get() == 0, "No worker error or external connection escaped");
            check(Kernel.getterCalls == 0, "All existing Kernel callbacks are passive; no getter body invocation");
            privacy();
            System.out.println("PASS profile-integration/" + args[0] + " (" + checks + " assertions)");
        } catch (Throwable failure) {
            failure.printStackTrace(System.err);
            for (String stage : android.util.Log.stages) System.err.println(stage);
            throw failure;
        } finally {
            executor().shutdownNow();
            check(executor().awaitTermination(5, TimeUnit.SECONDS), "Real SelfProfile IO executor shuts down");
        }
    }
    private static Method reader() throws Exception { return Config.class.getMethod("read", int.class, Object.class); }
    private static Method instance() throws Exception { return Kernel.class.getMethod("instance"); }
    private static Field field(Class<?> owner, String name) throws Exception {
        Field result = owner.getDeclaredField(name); result.setAccessible(true); return result;
    }
    private static Object profileField(String name) throws Exception { return field(SelfProfile.class, name).get(null); }
    private static boolean loading() throws Exception { return field(SelfProfile.class, "loading").getBoolean(null); }
    private static ExecutorService executor() throws Exception { return (ExecutorService) profileField("IO"); }
    private static Kernel fixture(boolean withDb) {
        Kernel value = new Kernel(); value.core = new Core(); value.account = new Account(); value.core.config = new Config();
        if (withDb) value.core.config.database = new Db(); TestState.current = value; return value;
    }
    private static void begin(boolean readerFirst) throws Exception {
        Context.preferenceAccount = ACCOUNT; WexStore.INSTANCE.putBool("home_side_panel_enabled", true);
        SelfProfile.initialize(new Context());
        HostTargetResolver.candidates = List.of(reader());
        HostTargetResolver.accountCandidates = List.of(Kernel.class.getMethod("accountStorage"));
        HostTargetResolver.readyCandidates = List.of(Ready.class.getMethod("run"));
        if (readerFirst) { HostProfileBridge.initialize(); Looper.runOffMain(HostTargetResolver::runOne); }
        XC_LoadPackage.LoadPackageParam load = new XC_LoadPackage.LoadPackageParam(); load.classLoader = ProfileIntegrationChecks.class.getClassLoader();
        HostDatabaseBridge.initialize(load, new Context()); HostTargetResolver.resolveDatabase(Kernel.class.getMethod("storage"));
        check(XposedBridge.hooks.containsKey(instance()), "Real DB bridge registered passive initialized-instance observer");
        check(XposedBridge.hooks.containsKey(Ready.class.getMethod("run")), "Real DB bridge registered strict account Ready task observer");
    }
    private static void afterKernel(Kernel value) throws Throwable {
        Looper.runOffMain(() -> {
            Looper.runOffMain(() -> {
                try { XposedBridge.after(instance(), null, value); }
                catch (Throwable error) { HostTargetResolver.raise(error); }
            });
        });
        drainCapture();
    }
    private static void afterReady(Ready task, Throwable failure) throws Throwable {
        Looper.runOffMain(() -> {
            try { XposedBridge.after(Ready.class.getMethod("run"), task, null, failure, new Object[0]); }
            catch (Throwable error) { HostTargetResolver.raise(error); }
        });
        if (failure == null) drainCapture();
    }
    private static void ioCheckpoint() throws Exception {
        CountDownLatch done = new CountDownLatch(1); executor().execute(done::countDown);
        check(done.await(5, TimeUnit.SECONDS), "Real three-bridge IO queue reaches bounded checkpoint");
    }
    private static void settle() throws Exception {
        for (int i = 0; i < 8; i++) {
            Handler.drain();
            drainCapture();
            ioCheckpoint(); Handler.drain();
            if (!loading() && Handler.pending() == 0) return;
        }
        throw new AssertionError("Integration did not settle within bounded MAIN/IO passes");
    }
    private static void readyIntegration(boolean failed) throws Throwable {
        begin(true); Kernel value = fixture(true); Config config = value.core.config; Db db = config.database;
        afterKernel(value);
        check(!HostProfileBridge.observed() && !HostDatabaseBridge.isReady() && Handler.pending() == 0,
                "NotReady Kernel return cannot publish profile/database notification");
        check(config.ids.isEmpty() && db.opens.get() == 0 && db.queries.get() == 0,
                "NotReady gate rejects before real reader and database boundary access");
        value.account.phase = Phase.AccountHasReady; Ready task = new Ready(value.account);
        afterReady(task, failed ? new PrivateThrowable() : null);
        if (failed) {
            check(!HostProfileBridge.observed() && !HostDatabaseBridge.isReady() && Handler.pending() == 0,
                    "Throwable Ready callback cannot capture or schedule SelfProfile");
            afterReady(task, null);
        }
        settle();
        check(ACCOUNT.equals(SelfProfile.wxid()) && NAME.equals(SelfProfile.nickname()) && SelfProfile.statusAccountReady(ACCOUNT),
                "Real three-bridge Ready capture, id2 confirmation and MAIN publication produce exact synthetic profile");
        check(config.accountReads.get() >= 1 && config.accountReads.get() <= 2 && config.nicknameReads.get() == config.accountReads.get(),
                "Two genuine readiness sources cause one primary request and at most one coalesced tail");
        check(!loading() && !config.mainReads.contains(true) && !db.mainQueries.contains(true),
                "Identity and SQL run off MAIN; bounded completion clears loading");
        check(db.sqls.contains(ACCOUNT_SQL) && db.sqls.contains(AVATAR_SQL) && !db.sqls.contains(NAME_SQL),
                "Config nickname bypasses unnecessary SQL name lookup while required confirmation remains separate");
        check(db.paths.get() == 0 && task.calls == 0 && config.getterCalls == 0 && value.core.getterCalls == 0,
                "No path/Kernel/Ready/Core/Config getter body is proactively executed");
        for (RowCursor cursor : db.cursors) check(cursor.closed, "Real HostDatabaseBridge closes every synthetic SQL cursor");
    }
    private static void readerOrder() throws Throwable {
        begin(false); Kernel value = fixture(true); value.account.phase = Phase.AccountHasReady;
        afterKernel(value);
        check(!HostProfileBridge.observed() && !HostDatabaseBridge.isReady(), "Existing Kernel arriving before ConfigStorage reader remains profile-unready");
        HostProfileBridge.initialize(); HostTargetResolver.runOne(); settle();
        check(HostProfileBridge.observed() && SelfProfile.statusAccountReady(ACCOUNT) && NAME.equals(SelfProfile.nickname()),
                "Real reader registration replays previously observed Ready Kernel and refreshes SelfProfile");
        check(value.core.config.accountReads.get() <= 2, "Replay does not create unbounded forced refresh recursion");
    }
    private static void selfRead() throws Throwable {
        begin(true); Kernel value = fixture(false); value.account.phase = Phase.AccountHasReady;
        AtomicInteger notices = new AtomicInteger(); Runnable listener = notices::incrementAndGet; HostProfileBridge.subscribe(listener);
        afterKernel(value); settle();
        check(value.core.config.accountReads.get() == 1 && notices.get() == 1,
                "Config-only real read ignores its own reader/Kernel after callbacks and requests exactly once");
        check(NAME.equals(SelfProfile.nickname()) && !SelfProfile.statusAccountReady(ACCOUNT) && !loading(),
                "Self-read suppression still publishes config-only nickname without database confirmation");
    }
    private static void forcedTail() throws Throwable {
        begin(true); Kernel value = fixture(false); value.account.phase = Phase.AccountHasReady;
        Config config = value.core.config; Read first = new Read(NAME), next = new Read(NEW_NAME);
        config.plans.add(first); config.plans.add(next);
        afterKernel(value); Handler.drain();
        check(first.entered.await(5, TimeUnit.SECONDS), "Real first reader request is in flight");
        for (int i = 0; i < 5; i++) SelfProfile.refresh(true);
        check(config.accountReads.get() == 1 && loading(), "Forced refreshes coalesce while one real request is blocked");
        first.allow.countDown(); awaitMain(); Handler.drain();
        check(next.entered.await(5, TimeUnit.SECONDS), "Deferred force schedules one real trailing request");
        for (int i = 0; i < 4; i++) SelfProfile.refresh(true);
        next.allow.countDown(); settle();
        check(config.accountReads.get() == 2 && NEW_NAME.equals(SelfProfile.nickname()) && !loading(),
                "At most one tail publishes fresh nickname; force during tail cannot self-sustain a retry loop");
        check(!SelfProfile.statusAccountReady(ACCOUNT), "Coalesced ConfigStorage reads cannot fabricate SQL confirmation");
    }
    private static void optionalNameRetains() throws Throwable {
        begin(true); Kernel value = fixture(true); value.account.phase = Phase.AccountHasReady;
        afterKernel(value); settle(); check(NAME.equals(SelfProfile.nickname()), "Seed nickname was genuinely read by three real bridges");
        Config config = value.core.config; Db db = config.database;
        config.nameFailure = new PrivateThrowable(); db.nameFailure = new PrivateThrowable();
        int reads = config.accountReads.get(); SelfProfile.refresh(true); settle();
        check(config.accountReads.get() == reads + 1 && NAME.equals(SelfProfile.nickname()) && SelfProfile.statusAccountReady(ACCOUNT),
                "Real reader unknown null plus SQL optional error retains same-account nickname and account confirmation");
        config.nameFailure = null; config.name = ""; db.nameFailure = new PrivateThrowable();
        SelfProfile.refresh(true); settle();
        check(NAME.equals(SelfProfile.nickname()) && SelfProfile.statusAccountReady(ACCOUNT),
                "Explicit empty config name with failed SQL fallback retains known same-account nickname");
    }
    private static void mandatoryFailure() throws Throwable {
        begin(true); Kernel value = fixture(true); value.account.phase = Phase.AccountHasReady;
        value.core.config.database.accountFailure = new PrivateThrowable();
        afterKernel(value); settle();
        check(NAME.equals(SelfProfile.nickname()) && ACCOUNT.equals(SelfProfile.wxid()) && !SelfProfile.statusAccountReady(ACCOUNT),
                "Failed mandatory SQL confirmation does not discard genuinely read ConfigStorage identity or fake confirmation");
        check(value.core.config.database.sqls.stream().allMatch(ACCOUNT_SQL::equals),
                "Failed confirmation never queries optional SQL fields for unconfirmed account");
    }
    private static void preferenceSwitch() throws Throwable {
        begin(true); Kernel value = fixture(false); value.account.phase = Phase.AccountHasReady;
        Read first = new Read(NAME); value.core.config.plans.add(first);
        afterKernel(value); Handler.drain(); check(first.entered.await(5, TimeUnit.SECONDS), "Old synthetic account read starts");
        Context.preferenceAccount = NEXT; HostDatabaseBridge.isReady(); SelfProfile.refresh(true);
        value.core.config.account = NEXT; value.core.config.name = NEW_NAME;
        first.allow.countDown(); settle();
        check(NEXT.equals(SelfProfile.wxid()) && NEW_NAME.equals(SelfProfile.nickname()) && !loading(),
                "Real token/preference recheck rejects old completion and lets replacement request finish");
        check(!SelfProfile.statusAccountReady(ACCOUNT) && !SelfProfile.statusAccountReady(NEXT),
                "Neither ConfigStorage-only account is database-confirmed");
    }
    private static void locks() throws Throwable {
        begin(true); Kernel value = fixture(true); value.account.phase = Phase.AccountHasReady;
        AtomicInteger callbacks = new AtomicInteger();
        Runnable observer = () -> {
            check(!Thread.holdsLock(HostProfileBridge.class) && !Thread.holdsLock(HostDatabaseBridge.class),
                    "Real bridge listener is invoked outside both bridge monitor locks");
            callbacks.incrementAndGet();
        };
        HostProfileBridge.subscribe(observer); HostDatabaseBridge.subscribeReady(observer);
        CountDownLatch start = new CountDownLatch(1), done = new CountDownLatch(2);
        Thread a = new Thread(() -> { try { start.await(); afterKernel(value); } catch (Throwable t) { uncaught.compareAndSet(null,t); } finally { done.countDown(); } }, "integration-kernel-callback");
        Thread b = new Thread(() -> { try { start.await(); HostProfileBridge.observe(value.core.config); } catch (Throwable t) { uncaught.compareAndSet(null,t); } finally { done.countDown(); } }, "integration-profile-callback");
        a.setDaemon(true); b.setDaemon(true); a.start(); b.start(); start.countDown();
        check(done.await(5, TimeUnit.SECONDS), "Parallel real DB/profile observation completes without lock inversion deadlock");
        settle(); check(callbacks.get() >= 2 && SelfProfile.statusAccountReady(ACCOUNT), "Parallel callbacks preserve real confirmation and listener publication");
    }
    private static void lateConfigGetter() throws Throwable {
        begin(true); Kernel value = fixture(true); Config late = value.core.config;
        value.core.config = null; value.core.recovered = late; value.core.allowRecovery = true;
        value.account.phase = Phase.AccountHasReady;
        afterKernel(value);
        check(!HostProfileBridge.observed(), "Existing Ready Kernel with null Config field is not fabricated");
        SelfProfile.refresh(false); settle();
        check(value.core.getterCalls == 1 && HostProfileBridge.observed() && SelfProfile.statusAccountReady(ACCOUNT),
                "Real demand invokes only unique Core config getter on proven Ready existing owner, then publishes actual synthetic read");
        check(Kernel.getterCalls == 0, "Safe Core recovery never invokes any Kernel getter");
        for (int i = 0; i < 8; i++) SelfProfile.refresh(false);
        settle(); check(value.core.getterCalls == 1, "Recovered holder does not cause meaningless repeated host getter invocation");
    }
    private static void cacheInvalidation() throws Throwable {
        begin(true); Kernel value = fixture(true); value.account.phase = Phase.AccountHasReady;
        afterKernel(value); settle(); check(SelfProfile.statusAccountReady(ACCOUNT), "Seeded real three-bridge capture is confirmed");
        value.account.phase = Phase.NotReady;
        SelfProfile.refresh(false); settle();
        check(!HostProfileBridge.observed() && !HostDatabaseBridge.isReady() && !SelfProfile.statusAccountReady(ACCOUNT)
                        && SelfProfile.nickname().isEmpty(), "NotReady invalidates old config/database/profile confirmation before read");
        Context.preferenceAccount = NEXT;
        value.core = new Core(); value.core.config = new Config(); value.core.config.account = NEXT;
        value.core.config.name = NEW_NAME; value.core.config.database = new Db(); value.core.config.database.account = NEXT;
        value.account = new Account(); value.account.phase = Phase.AccountHasReady;
        afterKernel(value); settle();
        check(NEXT.equals(SelfProfile.wxid()) && NEW_NAME.equals(SelfProfile.nickname()) && SelfProfile.statusAccountReady(NEXT)
                        && !SelfProfile.statusAccountReady(ACCOUNT), "New core/account/config/db must be genuinely reread after A-to-B invalidation");
    }
    private static void emptyDemand() throws Throwable {
        begin(true);
        for (int i = 0; i < 10; i++) SelfProfile.refresh(false);
        check(HostTargetResolver.workers.isEmpty() && Kernel.getterCalls == 0 && !HostProfileBridge.observed() && !HostDatabaseBridge.isReady(),
                "No successful host instance means demand is blocked, not active Kernel/static getter recovery");
        long waits = android.util.Log.stages.stream().filter(s -> s.contains("stage=wait config=false database=false")).count();
        check(waits == 1, "Repeated absent-instance demand emits one fixed wait rather than 500ms log floods");
    }
    private static void readyWithoutHolder() throws Throwable {
        begin(true);
        Ready task = new Ready(new Account()); task.account.phase = Phase.AccountHasReady;
        afterReady(task, new PrivateThrowable()); afterReady(task, null);
        check(android.util.Log.stages.stream().anyMatch(s -> s.contains("stage=account-ready-entry count=1")),
                "Ready callback entry is visible even before an existing Kernel was observed");
        check(android.util.Log.stages.stream().anyMatch(s -> s.contains("stage=account-ready-result threw=true availableKernel=false"))
                        && android.util.Log.stages.stream().anyMatch(s -> s.contains("stage=account-ready-result threw=false availableKernel=false")),
                "Both throwable and successful Ready returns without holder have boolean result evidence");
        HostDatabaseBridge.requestProfileCapture();
        check(android.util.Log.stages.stream().anyMatch(s -> s.contains("stage=demand waiting existingKernel=false weakKernel=false currentCore=false configReader=true typeRegistered=true")),
                "Demand evidence distinguishes absent holder from registered metadata without reflection reads");
        check(!HostProfileBridge.observed() && !HostDatabaseBridge.isReady() && HostTargetResolver.workers.isEmpty()
                        && task.calls == 0 && Kernel.getterCalls == 0,
                "Ready evidence never bypasses absent-instance gate or invokes host getter/task body");
    }
    private static void callbackBudget() throws Throwable {
        begin(true); Kernel value = fixture(false); Ready task = new Ready(value.account);
        for (int i = 0; i < 100; i++) {
            XposedBridge.after(instance(), null, null, i == 0 ? new PrivateThrowable() : null, new Object[0]);
            afterReady(task, i == 0 ? new PrivateThrowable() : null);
            XposedBridge.after(Kernel.class.getMethod("storage"), null, null, i == 0 ? new PrivateThrowable() : null, new Object[0]);
        }
        check(callbackCount() == 18, "Three exercised routes emit entry/result at most three each despite 100 callbacks");
        for (String stage : android.util.Log.stages) if (callbackStage(stage))
            check(stage.contains("count=1") || stage.contains("count=2") || stage.contains("count=3"), "No callback diagnostic exceeds its three-event quota");
        check(((int[]) field(HostDatabaseBridge.class, "CALLBACK_EVENTS").get(null)).length == 8,
                "Four fixed callback routes including pinned ctor bound all new events at 8*3=24/process");
        // Logging exhaustion does not prevent a later real callback from passing the unchanged Ready gate.
        value.account.phase = Phase.AccountHasReady; afterKernel(value); settle();
        check(HostProfileBridge.observed() && callbackCount() == 18, "Exhausted diagnostics preserve subsequent genuine capture behavior");
        java.util.concurrent.ThreadPoolExecutor logIO = (java.util.concurrent.ThreadPoolExecutor) field(HostStageLog.class, "IO").get(null);
        check(logIO.getQueue().size() + logIO.getQueue().remainingCapacity() == 64,
                "Real stage logger retains its existing bounded 64-task queue");
    }
    private static boolean callbackStage(String stage) {
        return stage.contains("-entry") || stage.contains("-result");
    }
    private static long callbackCount() { return android.util.Log.stages.stream().filter(ProfileIntegrationChecks::callbackStage).count(); }
    private static void revokedResult() throws Throwable {
        begin(true); Kernel value = fixture(false); value.account.phase = Phase.AccountHasReady;
        Read blocked = new Read(NAME); value.core.config.plans.add(blocked);
        afterKernel(value); Handler.drain(); check(blocked.entered.await(5, TimeUnit.SECONDS), "Real old config reader starts before revocation");
        value.account.phase = Phase.NotReady; HostDatabaseBridge.currentCapture();
        blocked.allow.countDown(); settle();
        check(!HostProfileBridge.observed() && !HostDatabaseBridge.isReady() && SelfProfile.nickname().isEmpty()
                        && !loading(), "Queued completion from revoked storage version cannot publish stale identity");
    }
    private static void pinnedSelected(String input) throws Throwable {
        check(hm0.Probe.initialized == 0, "Pinned fixture begins without Kernel clinit");
        ClassLoader parent = ProfileIntegrationChecks.class.getClassLoader();
        ClassLoader initial = new ClassLoader(parent) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("hm0.") || name.equals("com.tencent.mm.storage.m3"))
                    throw new ClassNotFoundException("Initial loader cannot see live host");
                return super.loadClass(name, resolve);
            }
        };
        List<String> reads = new ArrayList<>();
        ClassLoader live = new ClassLoader(parent) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("hm0.") || name.equals("com.tencent.mm.storage.m3")) reads.add(name);
                return super.loadClass(name, resolve);
            }
        };
        HostTargetResolver.selectedLoader = live;
        XC_LoadPackage.LoadPackageParam load = new XC_LoadPackage.LoadPackageParam();
        load.classLoader = initial;
        load.appInfo = new android.content.pm.ApplicationInfo(); load.appInfo.sourceDir = input;
        HostDatabaseBridge.initialize(load, new Context());
        check(HostTargetResolver.hostClassLoader() == live && load.classLoader == initial,
                "DB initialize preserves selected loader without rewriting shared LoadPackageParam");
        check(reads.containsAll(List.of("hm0.j1", "hm0.b0", "hm0.m", "hm0.f", "com.tencent.mm.storage.m3")),
                "All pinned metadata classes are requested through selected live loader");
        check(!XposedBridge.hooks.isEmpty() && field(HostDatabaseBridge.class, "kernelClass").get(null) == hm0.j1.class,
                "Full SHA/signature gate still registers only matching passive metadata");
        check(HostDatabaseBridge.matchesRuntimeKernel(live) && !HostDatabaseBridge.matchesRuntimeKernel(initial)
                        && !HostDatabaseBridge.matchesRuntimeKernel(null),
                "Package diagnostic rejects unavailable/wrong loader without retrieving Kernel");
        ClassLoader differentIdentity = new ClassLoader(parent) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!name.equals("hm0.j1")) return super.loadClass(name, resolve);
                Class<?> found = findLoadedClass(name);
                if (found != null) return found;
                try (java.io.InputStream input = parent.getResourceAsStream("hm0/j1.class")) {
                    byte[] bytes = input.readAllBytes();
                    return defineClass(name, bytes, 0, bytes.length);
                } catch (java.io.IOException error) { throw new ClassNotFoundException(name, error); }
            }
        };
        check(!HostDatabaseBridge.matchesRuntimeKernel(differentIdentity),
                "Same exact name under another defining loader is not class identity proof");
        check(hm0.Probe.initialized == 0 && hm0.Probe.getterCalls == 0
                        && !HostDatabaseBridge.isReady() && !HostProfileBridge.observed(),
                "Class.forName(false), identity diagnostics and passive registration cannot clinit/get singleton/infer Ready");
    }

    private static void pinnedEarly(boolean matched, String input) throws Throwable {
        check(hm0.Probe.initialized == 0, "Synthetic pinned Kernel is not initialized before passive registration");
        XC_LoadPackage.LoadPackageParam load = new XC_LoadPackage.LoadPackageParam();
        load.classLoader = ProfileIntegrationChecks.class.getClassLoader();
        load.appInfo = new android.content.pm.ApplicationInfo(); load.appInfo.sourceDir = input;
        HostDatabaseBridge.initialize(load, new Context());
        check(hm0.Probe.initialized == 0 && hm0.Probe.getterCalls == 0,
                "Actual SHA stream and Class.forName(false)/reflection registration never triggers Kernel clinit/getter");
        if (!matched) {
            check(XposedBridge.hooks.isEmpty(), "Fingerprint mismatch installs no early known-name host hook");
            check(!HostDatabaseBridge.matchesRuntimeKernel(load.classLoader)
                            && !HostDatabaseBridge.isReady() && !HostProfileBridge.observed(),
                    "Unknown host is not Kernel identity proof and cannot initialize or infer readiness");
            return;
        }
        check(XposedBridge.hooks.containsKey(hm0.j1.class.getDeclaredConstructor())
                        && XposedBridge.hooks.containsKey(hm0.j1.class.getMethod("i"))
                        && XposedBridge.hooks.containsKey(hm0.f.class.getMethod("run")),
                "Exact stream SHA match installs verified early constructor/i/Ready passive callbacks before resolver completion");
        HostDatabaseBridge.requestProfileCapture();
        XposedBridge.after(hm0.f.class.getMethod("run"), new hm0.f(new hm0.m()), null);
        check(android.util.Log.stages.stream().anyMatch(s -> s.contains("stage=early-ready-entry"))
                        && android.util.Log.stages.stream().anyMatch(s -> s.contains("stage=early-ready-result threw=false availableKernel=false")),
                "Pinned Ready callback without ctor/instance holder records entry and missing-Kernel result");
        check(hm0.Probe.initialized == 0 && hm0.Probe.getterCalls == 0,
                "Missing-holder Ready diagnostics and demand cannot initialize/read the Kernel singleton");
        hm0.j1 kernel = new hm0.j1();
        XposedBridge.after(hm0.j1.class.getDeclaredConstructor(), kernel, null, new PrivateThrowable(), new Object[0]);
        check(field(HostDatabaseBridge.class, "observedKernel").get(null) instanceof java.lang.ref.WeakReference<?>
                        && ((java.lang.ref.WeakReference<?>) field(HostDatabaseBridge.class, "observedKernel").get(null)).get() == null,
                "Throwable constructor callback records evidence without holding partial Kernel");
        XposedBridge.after(hm0.j1.class.getDeclaredConstructor(), kernel, null);
        check(!HostProfileBridge.observed() && !HostDatabaseBridge.isReady(), "Early constructor only holds completed Kernel, never assumes account Ready");
        kernel.d = new hm0.b0(); kernel.e = new hm0.m(); kernel.d.g = new com.tencent.mm.storage.m3();
        XposedBridge.after(hm0.j1.class.getMethod("i"), null, kernel);
        check(!HostProfileBridge.observed(), "Early NotReady existing Kernel remains blocked");
        HostTargetResolver.candidates = List.of(com.tencent.mm.storage.m3.class.getMethod("l", int.class, Object.class));
        HostProfileBridge.initialize(); HostTargetResolver.runOne();
        kernel.e.o = hm0.m.State.AccountHasReady;
        hm0.f task = new hm0.f(kernel.e);
        XposedBridge.after(hm0.f.class.getMethod("run"), task, null, new PrivateThrowable(), new Object[0]);
        check(!HostProfileBridge.observed(), "Early Ready throwable never captures");
        XposedBridge.after(hm0.f.class.getMethod("run"), task, null);
        drainCapture();
        check(HostProfileBridge.observed() && hm0.Probe.getterCalls == 0,
                "Early completed constructor plus successful Ready callback captures config through unique direct field without active Kernel invocation");
        HostTargetResolver.accountCandidates = List.of(hm0.j1.class.getMethod("b"));
        HostTargetResolver.readyCandidates = List.of(hm0.f.class.getMethod("run"));
        HostTargetResolver.resolveDatabase(hm0.j1.class.getMethod("u"));
        for (int i = 0; i < 100; i++) {
            XposedBridge.after(hm0.j1.class.getDeclaredConstructor(), kernel, null);
            XposedBridge.after(hm0.j1.class.getMethod("i"), null, kernel);
            XposedBridge.after(hm0.f.class.getMethod("run"), task, null);
            XposedBridge.after(hm0.j1.class.getMethod("u"), null, kernel.d);
        }
        check(callbackCount() == 24, "Pinned and resolved duplicate registrations share four route quotas, exactly 24 new diagnostic events maximum");
        check(XposedBridge.hooks.get(hm0.j1.class.getMethod("i")).size() == 1
                        && XposedBridge.hooks.get(hm0.f.class.getMethod("run")).size() == 1,
                "Late resolver preserves pinned hook deduplication and does not multiply callback budgets");
    }
    private static long metric(String name) throws Exception {
        try { return field(HostDatabaseBridge.class, name).getLong(null); }
        catch (NoSuchFieldException error) { throw new AssertionError("Missing bounded-capture counter " + name); }
    }
    private static void drainCapture() {
        int passes = 0;
        while (!HostTargetResolver.workers.isEmpty()) {
            if (++passes > 8) throw new AssertionError("Capture queue fails bounded drain");
            Looper.runOffMain(HostTargetResolver::runOne);
        }
    }
    private static void hotGetter() throws Throwable {
        begin(true); Kernel value = fixture(true); value.account.phase = Phase.AccountHasReady;
        XposedBridge.after(instance(), null, value);
        check(value.core.config.database.opens.get() == 0 && HostTargetResolver.workers.size() == 1,
                "First main hot getter only observes/queues; no metadata scan or isOpen");
        drainCapture(); settle();
        long scans = metric("captureEvents"), misses = metric("metadataMisses");
        int opens = value.core.config.database.opens.get(), submissions = HostTargetResolver.submissions;
        for (int i = 0; i < 200; i++) XposedBridge.after(instance(), null, value);
        check(metric("captureEvents") == scans && metric("metadataMisses") == misses
                        && value.core.config.database.opens.get() == opens && HostTargetResolver.submissions == submissions,
                "Same ready tuple with four nonDB fields does zero scan/reflection miss/host open/queue work");
        check(!value.core.config.database.mainOpens.contains(true), "All isOpen calls are worker-only");
        check(metric("negativeMetadataHits") > 0, "NonDB runtime Class negative metadata is reused");
        System.out.println("COUNTERS sameReady hooks=200 scansDelta=" + (metric("captureEvents") - scans)
                + " missesDelta=" + (metric("metadataMisses") - misses) + " opensDelta=" + (value.core.config.database.opens.get() - opens)
                + " submissionsDelta=" + (HostTargetResolver.submissions - submissions) + " mainOpen=false");
    }
    private static void monitorIndependent() throws Throwable {
        begin(true); Kernel value = fixture(false);
        CountDownLatch held = new CountDownLatch(1), release = new CountDownLatch(1);
        Thread holder = new Thread(() -> {
            synchronized (HostDatabaseBridge.class) { held.countDown(); try { release.await(); } catch (InterruptedException e) { throw new AssertionError(e); } }
        }, "synthetic-publish-holder"); holder.setDaemon(true); holder.start(); held.await();
        CountDownLatch callbackDone = new CountDownLatch(1);
        Thread callback = new Thread(() -> {
            try { XposedBridge.after(instance(), null, null); }
            catch (Throwable error) { uncaught.compareAndSet(null, error); }
            finally { callbackDone.countDown(); }
        }, "synthetic-main-callback"); callback.setDaemon(true); callback.start();
        boolean completed;
        try { completed = callbackDone.await(5, TimeUnit.SECONDS); }
        finally { release.countDown(); }
        holder.join(5000); callback.join(5000);
        check(completed && uncaught.get() == null, "Callback evidence completes while publication monitor remains held (liveness watchdog, no latency threshold)");
        for (int i = 0; i < 10; i++) XposedBridge.after(instance(), null, null);
    }
    private static void rapidHooks() throws Throwable {
        begin(true); Kernel value = fixture(true); value.account.phase = Phase.AccountHasReady;
        for (int i = 0; i < 400; i++) XposedBridge.after(instance(), null, value);
        check(HostTargetResolver.workers.size() == 1 && metric("captureEvents") == 0,
                "400 rapid successful callbacks merge into exactly one pending capture, no inline scan");
        drainCapture(); settle();
        check(metric("maxPendingCaptures") <= 1 && metric("captureEvents") <= 3,
                "Merged capture queue stays single-slot and scan count is bounded");
        System.out.println("COUNTERS rapidHooks=400 maxPending=" + metric("maxPendingCaptures") + " scans=" + metric("captureEvents")
                + " metadataMisses=" + metric("metadataMisses") + " queries=" + value.core.config.database.queries.get());
    }
    private static void selfReadQueue() throws Throwable {
        begin(true); Kernel value = fixture(false); value.account.phase = Phase.AccountHasReady;
        afterKernel(value); drainCapture(); settle();
        long scans = metric("captureEvents"); int submits = HostTargetResolver.submissions;
        Looper.runOffMain(() -> { try { HostProfileBridge.readIdentity(); } catch (Exception e) { throw new AssertionError(e); } });
        check(metric("captureEvents") == scans && HostTargetResolver.submissions == submits && HostTargetResolver.workers.isEmpty(),
                "Module read with nested real Kernel callbacks does not scan or enqueue capture");
    }
    private static void lateDb() throws Throwable {
        begin(true); Kernel value = fixture(false); value.account.phase = Phase.AccountHasReady;
        afterKernel(value); drainCapture(); settle(); check(!HostDatabaseBridge.isReady(), "Config-only ready starts without DB");
        value.core.config.database = new Db(); afterKernel(value); drainCapture(); settle();
        check(SelfProfile.statusAccountReady(ACCOUNT), "Same core late DB is observed on genuine callback");
        long version = HostDatabaseBridge.captureVersion(); Db replacement = new Db(); value.core.config.database = replacement;
        XposedBridge.after(instance(), null, value); check(!HostDatabaseBridge.isReady(), "Replacement invalidates before queued worker runs");
        drainCapture(); settle(); check(HostDatabaseBridge.captureVersion() > version && SelfProfile.statusAccountReady(ACCOUNT), "Replacement increments source capture version and reconfirms");
        replacement.open = false;
        Looper.runOffMain(() -> check(!HostDatabaseBridge.isReady(), "Worker rejects actually closed DB"));
        check(!HostDatabaseBridge.isReady() && !HostProfileBridge.observed(), "Closed DB revokes capture instead of retaining stale identity");
    }
    private static void workerEpoch() throws Throwable {
        begin(true); Kernel value = fixture(true); value.account.phase = Phase.AccountHasReady;
        Read block = new Read(""); value.core.config.database.openBlock = block;
        XposedBridge.after(instance(), null, value);
        Thread worker = new Thread(() -> Looper.runOffMain(HostTargetResolver::runOne), "synthetic-capture-worker"); worker.setDaemon(true); worker.start();
        check(block.entered.await(5, TimeUnit.SECONDS), "Worker reaches real isOpen outside monitor");
        value.account = new Account(); value.account.phase = Phase.NotReady;
        XposedBridge.after(instance(), null, value);
        check(block.allow.getCount() == 1 && worker.isAlive(), "Main callback finishes while worker host isOpen remains blocked");
        check(metric("maxPendingCaptures") <= 1, "Concurrent probe has at most one pending slot");
        block.allow.countDown(); worker.join(5000); drainCapture(); settle();
        check(!HostProfileBridge.observed() && !HostDatabaseBridge.isReady(), "Account replacement/revocation while worker scans rejects stale publication");
    }
    private static void deferredCapture() throws Throwable {
        begin(true); Kernel value = fixture(false); value.account.phase = Phase.AccountHasReady;
        Read blocked = new Read(NAME); value.core.config.plans.add(blocked);
        afterKernel(value); drainCapture(); Handler.drain(); check(blocked.entered.await(5, TimeUnit.SECONDS), "Profile read in flight");
        long scans = metric("captureEvents"); int submissions = HostTargetResolver.submissions;
        for (int i = 0; i < 30; i++) SelfProfile.refresh(true);
        check(metric("captureEvents") == scans && HostTargetResolver.submissions == submissions,
                "Deferred profile force returns before capture/requestReady work");
        blocked.allow.countDown(); settle();
    }
    private static void classIdentity() throws Throwable {
        begin(true); Kernel value = fixture(true); value.account.phase = Phase.AccountHasReady;
        afterKernel(value); settle();
        Method metadata = HostDatabaseBridge.class.getDeclaredMethod("databaseMetadata", Class.class); metadata.setAccessible(true);
        ClassLoader parent = ProfileIntegrationChecks.class.getClassLoader();
        ClassLoader other = new ClassLoader(parent) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!name.equals(Noise.class.getName())) return super.loadClass(name, resolve);
                Class<?> loaded = findLoadedClass(name); if (loaded != null) return loaded;
                try (java.io.InputStream input = parent.getResourceAsStream(name.replace('.', '/') + ".class")) {
                    byte[] bytes = input.readAllBytes(); return defineClass(name, bytes, 0, bytes.length);
                } catch (java.io.IOException e) { throw new ClassNotFoundException(name, e); }
            }
        };
        Class<?> twin = Class.forName(Noise.class.getName(), false, other);
        long before = metric("metadataMisses"); AtomicReference<Object> first = new AtomicReference<>(), next = new AtomicReference<>();
        Looper.runOffMain(() -> { try { first.set(metadata.invoke(null, Noise.class)); next.set(metadata.invoke(null, twin)); }
            catch (Exception e) { throw new AssertionError(e); } });
        check(twin != Noise.class && first.get() != next.get() && metric("metadataMisses") == before + 1,
                "Negative cache uses actual defining Class identity, same name different loader incurs independent miss");
        Looper.runOffMain(() -> { try { metadata.invoke(null, twin); } catch (Exception e) { throw new AssertionError(e); } });
        check(metric("metadataMisses") == before + 1, "Twin negative metadata cached without new reflection discovery");
    }
    private static void sourceVersion() throws Throwable {
        begin(true); Kernel value = fixture(false); value.account.phase = Phase.AccountHasReady;
        Read blocked = new Read(NAME); value.core.config.plans.add(blocked);
        afterKernel(value); Handler.drain(); check(blocked.entered.await(5, TimeUnit.SECONDS), "Old source read begins");
        long before = HostDatabaseBridge.captureVersion(); Config next = new Config(); next.name = NEW_NAME; value.core.config = next;
        XposedBridge.after(instance(), null, value); drainCapture();
        check(HostDatabaseBridge.captureVersion() > before, "Config replacement advances captured source version");
        blocked.allow.countDown(); settle();
        check(NEW_NAME.equals(SelfProfile.nickname()) && !NAME.equals(SelfProfile.nickname()) && !loading(),
                "Old config completion cannot publish after same-account source replacement; coalesced tail reads new source");
    }
    private static void actualDbFields() throws Throwable {
        begin(true);
        Kernel erased = fixture(false); erased.account.phase = Phase.AccountHasReady;
        Db unrelated = new Db(); erased.core.erased = unrelated; erased.core.config.erased = unrelated;
        XposedBridge.after(instance(), null, erased); drainCapture(); settle();
        check(!HostDatabaseBridge.isReady() && !SelfProfile.statusAccountReady(ACCOUNT)
                        && unrelated.opens.get() == 0 && unrelated.queries.get() == 0,
                "Resolved storage refuses DB behind arbitrary declared Object fields in core/config");
        // A legitimate typed null slot may still become late DB without account/core replacement.
        Db late = new Db(); erased.core.config.database = late;
        XposedBridge.after(instance(), null, erased); drainCapture(); settle();
        check(SelfProfile.statusAccountReady(ACCOUNT) && late.queries.get() > 0 && unrelated.opens.get() == 0,
                "Typed null-to-late DB succeeds while erased Object DB remains ignored");
        for (int route = 0; route < 3; route++) {
            Kernel value = fixture(false); value.account.phase = Phase.AccountHasReady;
            Db db = new Db();
            if (route == 0) value.core.config.interfaceDatabase = db;
            else if (route == 1) value.core.config.wrapperDatabase = db;
            else value.core.inheritedDatabase = db;
            XposedBridge.after(instance(), null, value);
            check(db.opens.get() == 0 && HostTargetResolver.workers.size() == 1,
                    "Declared nonDB actual DB still only queues from hook");
            drainCapture(); settle();
            check(HostDatabaseBridge.isReady() && SelfProfile.statusAccountReady(ACCOUNT) && db.queries.get() > 0,
                    "Worker learns actual DB field for interface, wrapper and inherited owner route=" + route);
            long scans = metric("captureEvents"), misses = metric("metadataMisses"); int opens = db.opens.get(), submits = HostTargetResolver.submissions;
            for (int i = 0; i < 60; i++) XposedBridge.after(instance(), null, value);
            check(metric("captureEvents") == scans && metric("metadataMisses") == misses
                            && db.opens.get() == opens && HostTargetResolver.submissions == submits,
                    "Learned actual DB evidence is fixed Field.get only on repeated hot getter");
            Db replacement = new Db(); long version = HostDatabaseBridge.captureVersion();
            if (route == 0) value.core.config.interfaceDatabase = replacement;
            else if (route == 1) value.core.config.wrapperDatabase = replacement;
            else value.core.inheritedDatabase = replacement;
            XposedBridge.after(instance(), null, value);
            check(!HostDatabaseBridge.isReady(), "Learned typed field replacement revokes old DB immediately");
            drainCapture(); settle();
            check(HostDatabaseBridge.captureVersion() > version && SelfProfile.statusAccountReady(ACCOUNT) && replacement.queries.get() > 0,
                    "Actual typed DB replacement recaptures and reconfirms");
            Object snapshot = field(HostDatabaseBridge.class, route == 2 ? "coreProbe" : "configProbe").get(null);
            check(((Field[]) field(snapshot.getClass(), "fields").get(snapshot)).length <= 8,
                    "Learned actual DB field evidence remains bounded");
            check(!db.mainOpens.contains(true) && !replacement.mainOpens.contains(true), "All actual typed DB opens stay worker-only");
        }
    }
    private static void lastQueryClose() throws Throwable {
        begin(true); Kernel value = fixture(true); value.account.phase = Phase.AccountHasReady;
        Db db = value.core.config.database; db.closeOnAvatar = true;
        XposedBridge.after(instance(), null, value); drainCapture();
        long version = HostDatabaseBridge.captureVersion(); Handler.drain(); ioCheckpoint();
        check(db.avatarCloses.get() == 1 && db.sqls.get(db.sqls.size() - 1).equals(AVATAR_SQL),
                "Real last SQL boundary closed DB during avatar query without changing account");
        check(android.util.Log.stages.stream().noneMatch(s -> s.contains("stage=read success=true"))
                        && HostDatabaseBridge.captureVersion() > version,
                "Worker final open/sourceVersion gate rejects before successful result is posted");
        Handler.drain(); settle();
        check(SelfProfile.nickname().isEmpty() && !SelfProfile.statusAccountReady(ACCOUNT) && !loading(),
                "Last-query closed DB never publishes stale confirmed profile");
        check(!db.mainOpens.contains(true), "Last-query final open validation runs off MAIN");
        for (RowCursor cursor : db.cursors) check(cursor.closed, "All last-query cursors close on rejection");
    }
    private static void awaitMain() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (Handler.pending() == 0 && uncaught.get() == null && System.nanoTime() < deadline) Thread.sleep(2);
        check(Handler.pending() > 0 && uncaught.get() == null, "Real IO completion enqueues MAIN callback");
    }
    private static void privacy() {
        List<String> logs = new ArrayList<>(XposedBridge.logs); logs.addAll(android.util.Log.stages);
        for (String text : logs) check(!text.contains(ACCOUNT) && !text.contains(NEXT) && !text.contains(NAME)
                        && !text.contains(NEW_NAME) && !text.contains(MESSAGE) && !text.contains(ACCOUNT_SQL),
                "Real stage logs contain booleans/types only, never synthetic private data or SQL");
        for (String stage : android.util.Log.stages)
            check(stage.contains("pid=31415 runtimeDiag=profile-evidence-20261115-v1"),
                    "Real stage sink adds a synthetic PID and fixed runtime diagnostic version to every event");
        check(messageReads.get() == 0, "Real failure log never reads Throwable message");
    }
    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}
