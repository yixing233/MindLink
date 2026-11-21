package wex.ui.core.feature;

import android.content.Context;
import android.database.Cursor;
import de.robv.android.xposed.XposedBridge;
import java.lang.reflect.Constructor;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static wex.ui.core.feature.CheckSupport.*;

/** Drives the real bridge through resolver success/failure and captured Xposed callbacks. */
public final class DatabaseCaptureChecks {
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
    public static class DatabaseShape {
        public boolean open = true;
        public int pathReads;
        public int openReads;
        public int queries;
        public String selected;
        public String seenSql;
        public Object seenArgs;
        public int flag = -1;
        public final Cursor cursor = new EmptyCursor();
        public String getPath() { pathReads++; return PATH; }
        public boolean isOpen() { openReads++; return open; }
        Cursor record(String method, String sql, Object args, int value) {
            queries++;
            selected = method;
            seenSql = sql;
            seenArgs = args;
            flag = value;
            return cursor;
        }
    }
    public static class NamedStringDatabase extends DatabaseShape {
        public Cursor rawQuery(String sql, String[] args) { return record("rawQuery:String[]", sql, args, -1); }
        public Cursor rawQuery(String sql, Object[] args) { return record("rawQuery:Object[]", sql, args, -1); }
        public Cursor g(String sql, String[] args, int flag) { return record("g", sql, args, flag); }
        public Cursor t(String sql, String[] args, int cancel) { throw new AssertionError("Cancellation path selected"); }
        public Cursor unknown(String sql, Object[] args) { throw new AssertionError("Unknown query selected"); }
    }
    public static final class NamedObjectDatabase extends DatabaseShape {
        public Cursor rawQuery(String sql, Object[] args) { return record("rawQuery:Object[]", sql, args, -1); }
        public Cursor g(String sql, String[] args, int flag) { throw new AssertionError("g precedes named rawQuery"); }
        public Cursor t(String sql, String[] args, int cancel) { throw new AssertionError("Cancellation path selected"); }
    }
    public static final class VerifiedDatabase extends DatabaseShape {
        public Cursor g(String sql, String[] args, int flag) { return record("g", sql, args, flag); }
        public Cursor t(String sql, String[] args, int cancel) { throw new AssertionError("Cancellation path selected"); }
    }
    public static final class UnknownDatabase extends DatabaseShape {
        public Cursor q(String sql, String[] args) { throw new AssertionError("Ambiguous unknown query selected"); }
        public Cursor r(String sql, String[] args, int flag) { throw new AssertionError("Ambiguous unknown query selected"); }
    }
    public static final class SameNameDatabase extends DatabaseShape {
        public Cursor q(String sql, String[] args) { throw new AssertionError("Ambiguous overload selected"); }
        public Cursor q(String sql, Object[] args) { throw new AssertionError("Ambiguous overload selected"); }
    }
    public interface QueryMetadata {
        String getPath();
        boolean isOpen();
        Cursor rawQuery(String sql, String[] args);
    }
    public static final class MetadataDatabase extends NamedStringDatabase implements QueryMetadata { }
    public static final class ConfigAccessor { }
    public static final class Nested {
        public NamedStringDatabase database = new NamedStringDatabase();
    }
    public static final class CoreStorage {
        public NamedStringDatabase direct;
        public NamedObjectDatabase objects;
        public VerifiedDatabase wrapper;
        public UnknownDatabase unknown;
        public SameNameDatabase sameName;
        public QueryMetadata metadata;
        public Nested nested;
        public Object erased;
        public String preferences = PREF;
        public int getterCalls;
        public CoreStorage() { }
        public ConfigAccessor configaccessor() { getterCalls++; return new ConfigAccessor(); }
        public QueryMetadata querymetadata() { getterCalls++; return metadata; }
    }
    public static final class UnrelatedStorage {
        public NamedStringDatabase direct = new NamedStringDatabase();
        public ConfigAccessor configaccessor() { return new ConfigAccessor(); }
    }
    public enum AccountPhase { NotReady, AccountHasReady }
    public static final class AccountStorage {
        public volatile AccountPhase phase = AccountPhase.NotReady;
    }
    public static final class Kernel {
        public static int getterCalls;
        public CoreStorage storage;
        public AccountStorage account;
        public static CoreStorage coreStorage() { getterCalls++; throw new AssertionError("Kernel storage getter must not run proactively"); }
        public static AccountStorage accountStorage() { getterCalls++; throw new AssertionError("Kernel account getter must not run proactively"); }
        public static Kernel instance() { getterCalls++; throw new AssertionError("Kernel instance getter must not run proactively"); }
        public QueryMetadata interfaceStorage() { getterCalls++; return null; }
    }

    private static Method entry() throws Exception { return method(Kernel.class, "coreStorage"); }
    private static Constructor<?> constructor() throws Exception { return CoreStorage.class.getDeclaredConstructor(); }
    private static Method accessor() throws Exception { return method(CoreStorage.class, "configaccessor"); }
    private static boolean installed() throws Exception { return field(HostDatabaseBridge.class, "installed").getBoolean(null); }
    private static Object active() throws Exception { return ((java.lang.ref.WeakReference<?>) field(HostDatabaseBridge.class, "database").get(null)).get(); }
    private static Method queryMethod(Class<?> type) throws Exception {
        return (Method) method(HostDatabaseBridge.class, "findRawQuery", Class.class).invoke(null, type);
    }
    private static void capture(Object storage) throws Exception {
        method(HostDatabaseBridge.class, "capture", Object.class).invoke(null, storage);
        HostTargetResolver.drain();
    }
    private static void initialize() {
        try { HostTargetResolver.accountCandidates = java.util.List.of(method(Kernel.class, "accountStorage")); }
        catch (Exception error) { throw new AssertionError("Fixture account method is present", error); }
        HostDatabaseBridge.initialize(loadPackage(), new Context());
    }
    private static void start() throws Exception {
        initialize();
        check(HostTargetResolver.resolveCalls == 1, "Resolver request dispatched once");
        check("database_storage_get".equals(HostTargetResolver.lastKey), "Correct database resolver key");
        check(HostTargetResolver.lastSpec.count == 0, "CoreStorage resolver uses no-argument target");
        check(HostTargetResolver.lastSpec.strings.length == 1
                        && "mCoreStorage not initialized!".equals(HostTargetResolver.lastSpec.strings[0]),
                "CoreStorage resolver retains verified identity string");
        check("RESOLVING".equals(HostDatabaseBridge.status()) && !HostDatabaseBridge.isReady(),
                "No pretend readiness before resolver completion");
        check(field(HostDatabaseBridge.class, "storageClass").get(null) == null,
                "Runtime identity unassigned before resolver completion");
        HostTargetResolver.succeed(entry());
        check(installed() && "HOOKED".equals(HostDatabaseBridge.status()), "Successful hook is installed but not ready");
        check(field(HostDatabaseBridge.class, "storageClass").get(null) == CoreStorage.class,
                "Runtime capture anchored to resolved concrete CoreStorage identity");
        check(XposedBridge.hooks.containsKey(entry()) && XposedBridge.hooks.containsKey(constructor())
                        && XposedBridge.hooks.containsKey(accessor()),
                "Entry, constructor-after and existing-object getter-after are registered");
        for (Member member : XposedBridge.hooks.keySet())
            check(!(member instanceof Method) || !java.lang.reflect.Modifier.isAbstract(member.getModifiers()),
                    "No abstract interface method hooks");
        check(XposedBridge.hooks.containsKey(method(Kernel.class, "instance")),
                "Valid Kernel/account typed metadata registers passive existing-instance getter");
        HostTargetResolver.MethodSpec account = HostTargetResolver.accountSpec;
        check(account != null && Kernel.class.getName().equals(account.declaredClass)
                        && account.count == 0 && account.access == (java.lang.reflect.Modifier.PUBLIC | java.lang.reflect.Modifier.STATIC)
                        && java.util.Arrays.equals(account.strings, new String[]{"mCoreAccount not initialized!"}),
                "Account metadata is resolved only under same Kernel owner and exact new API constraints");
        check(Kernel.getterCalls == 0, "Initialization never invokes Kernel getter");
    }
    private static Kernel existingKernel;
    private static void observe(CoreStorage storage) throws Throwable {
        if (existingKernel == null) { existingKernel = new Kernel(); existingKernel.account = new AccountStorage(); }
        Kernel kernel = existingKernel; kernel.storage = storage;
        kernel.account.phase = AccountPhase.AccountHasReady;
        XposedBridge.after(method(Kernel.class, "instance"), null, kernel);
        XposedBridge.after(accessor(), storage, new ConfigAccessor());
        HostTargetResolver.drain();
        check(storage.getterCalls == 0 && Kernel.getterCalls == 0,
                "After observation reads thisObject fields without invoking any getter");
    }
    private static void verifyQuery(DatabaseShape db, String name, int flag) throws Exception {
        String[] args = {PARAM};
        check(HostDatabaseBridge.rawQuery(SQL, args) == db.cursor, "Selected query called on captured database");
        check(name.equals(db.selected) && db.flag == flag, "Query method and wrapper flag are exact");
        check(SQL.equals(db.seenSql) && db.seenArgs == args, "SQL and bind array forwarded without alteration");
        check(db.pathReads == 0, "Database path identity never read or logged");
    }

    private static void hookFailure() throws Throwable {
        Method target = entry();
        XposedBridge.failures.put(target, new BareThrowable());
        initialize();
        HostTargetResolver.succeed(target);
        check(!installed(), "hookMethod Throwable rolls installed back to false");
        check(HostDatabaseBridge.status().startsWith("FAILED:"), "hook failure is FAILED, never HOOKED");
        check(!HostDatabaseBridge.isReady() && XposedBridge.hooks.isEmpty(), "Failed hook installs no callbacks or readiness");
        @SuppressWarnings("unchecked")
        Set<Member> registered = (Set<Member>) field(HostDatabaseBridge.class, "HOOKS").get(null);
        check(!registered.contains(target), "Failed hook is absent from production dedup registry");
        check(XposedBridge.logs.stream().noneMatch(text -> text.contains("stage=hook registered")),
                "Failed resolve-success callback must not log fake hook success");
        XposedBridge.failures.clear();
        initialize();
        check(HostTargetResolver.resolveCalls == 2, "Failure remains retryable");
        HostTargetResolver.succeed(target);
        check(installed() && "HOOKED".equals(HostDatabaseBridge.status()), "Retry actually installs hook");
        check(XposedBridge.attempts.get(target) == 2 && XposedBridge.hooks.get(target).size() == 1,
                "Failed first attempt does not suppress retry or duplicate successful callbacks");
        int attempts = XposedBridge.attempts.values().stream().mapToInt(Integer::intValue).sum();
        initialize();
        check(HostTargetResolver.resolveCalls == 2
                        && attempts == XposedBridge.attempts.values().stream().mapToInt(Integer::intValue).sum(),
                "Duplicate successful initialize neither resolves nor hooks again");
    }
    private static void resolverFailure() throws Throwable {
        initialize();
        HostTargetResolver.fail(new HostError());
        check(!installed() && HostDatabaseBridge.status().startsWith("FAILED:"), "Resolver Error produces retryable failure");
        check(XposedBridge.hooks.isEmpty() && !HostDatabaseBridge.isReady(), "Resolver failure cannot capture or register");
        initialize();
        HostTargetResolver.succeed(entry());
        check(installed() && HostTargetResolver.resolveCalls == 2, "Resolver failure retry succeeds");
    }
    private static void lateCapture() throws Throwable {
        start();
        CoreStorage storage = new CoreStorage();
        XposedBridge.after(constructor(), storage, null);
        check(!HostDatabaseBridge.isReady(), "Constructor after with null database fields remains not ready");
        storage.direct = new NamedStringDatabase();
        XposedBridge.after(accessor(), storage, null, new HostError());
        check(!HostDatabaseBridge.isReady(), "Throwing getter after does not capture");
        observe(storage);
        check(HostDatabaseBridge.isReady() && active() == storage.direct,
                "Later existing-object getter captures direct open database via thisObject, not result");
        verifyQuery(storage.direct, "rawQuery:String[]", -1);
    }
    private static void namedString() throws Throwable {
        start();
        CoreStorage storage = new CoreStorage();
        storage.direct = new NamedStringDatabase();
        observe(storage);
        verifyQuery(storage.direct, "rawQuery:String[]", -1);
        Method query = queryMethod(NamedStringDatabase.class);
        check(query.getName().equals("rawQuery") && query.getParameterTypes()[1] == String[].class,
                "Named String[] rawQuery wins over Object[], g, t and unknown overloads");
    }
    private static void namedObject() throws Throwable {
        start();
        CoreStorage storage = new CoreStorage();
        storage.objects = new NamedObjectDatabase();
        observe(storage);
        verifyQuery(storage.objects, "rawQuery:Object[]", -1);
        check(queryMethod(NamedObjectDatabase.class).getParameterTypes()[1] == Object[].class,
                "Named Object[] rawQuery wins over verified wrapper");
    }
    private static void verifiedWrapper() throws Throwable {
        start();
        CoreStorage storage = new CoreStorage();
        storage.wrapper = new VerifiedDatabase();
        observe(storage);
        verifyQuery(storage.wrapper, "g", 0);
        check(queryMethod(VerifiedDatabase.class).getName().equals("g"), "Verified ordinary g chosen, not cancellation t");
    }
    private static void ambiguous(boolean overloads) throws Throwable {
        start();
        CoreStorage storage = new CoreStorage();
        if (overloads) storage.sameName = new SameNameDatabase();
        else storage.unknown = new UnknownDatabase();
        observe(storage);
        check(!HostDatabaseBridge.isReady() && active() == null,
                "Actual getter-after callback must reject ambiguous unknown methods instead of publishing READY");
        check(queryMethod(overloads ? SameNameDatabase.class : UnknownDatabase.class) == null,
                "Ambiguous unknown Cursor methods must be refused, including distinct signatures with same name");
    }
    private static void interfaceMetadata() throws Throwable {
        start();
        Method getter = method(CoreStorage.class, "querymetadata");
        check(XposedBridge.hooks.containsKey(getter), "Concrete storage getter returning query metadata can be observed");
        for (Method abstractMethod : QueryMetadata.class.getDeclaredMethods())
            check(!XposedBridge.hooks.containsKey(abstractMethod) && !XposedBridge.attempts.containsKey(abstractMethod),
                    "Interface metadata is only a field-type contract, never a hook target");
        CoreStorage storage = new CoreStorage();
        storage.metadata = new MetadataDatabase();
        XposedBridge.after(getter, storage, null);
        check(!HostDatabaseBridge.isReady(), "Concrete getter return alone never proves account Ready");
        observe(storage);
        check(HostDatabaseBridge.isReady() && active() == storage.metadata, "Direct interface-typed field captures concrete open DB");
        verifyQuery((DatabaseShape) storage.metadata, "rawQuery:String[]", -1);
    }
    private static void rejectUnrelatedNested() throws Throwable {
        start();
        UnrelatedStorage fake = new UnrelatedStorage();
        XposedBridge.after(entry(), new Kernel(), fake);
        capture(fake);
        check(!HostDatabaseBridge.isReady(), "Same-shaped non-CoreStorage result/instance cannot be captured");
        CoreStorage storage = new CoreStorage();
        storage.nested = new Nested();
        storage.erased = new NamedStringDatabase();
        observe(storage);
        check(!HostDatabaseBridge.isReady() && storage.nested.database.openReads == 0,
                "Runtime never recursively traverses nested DB or arbitrary Object fields");
        storage.direct = new NamedStringDatabase();
        observe(storage);
        check(active() == storage.direct, "Direct resolved storage DB wins without looking at nested same-shaped DB");
    }
    private static void closedDatabase() throws Throwable {
        start();
        CoreStorage storage = new CoreStorage();
        storage.direct = new NamedStringDatabase();
        storage.direct.open = false;
        observe(storage);
        check(!HostDatabaseBridge.isReady() && active() == null, "Closed direct database is not ready");
        storage.direct.open = true;
        observe(storage);
        check(HostDatabaseBridge.isReady() && active() == storage.direct, "Opening later permits capture");
    }
    private static void replacementListeners() throws Throwable {
        start();
        AtomicInteger notifications = new AtomicInteger();
        Runnable listener = notifications::incrementAndGet;
        HostDatabaseBridge.subscribeReady(listener);
        check(notifications.get() == 0, "Listener deferred while not ready");
        CoreStorage storage = new CoreStorage();
        storage.direct = new NamedStringDatabase();
        observe(storage);
        check(notifications.get() == 1, "First ready database notifies exactly once");
        observe(storage);
        XposedBridge.after(entry(), new Kernel(), storage);
        XposedBridge.after(constructor(), storage, null);
        check(notifications.get() == 1, "Repeated capture of identical database never re-notifies");
        CoreStorage newStorage = new CoreStorage();
        newStorage.direct = storage.direct;
        observe(newStorage);
        check(notifications.get() == 2, "New CoreStorage identity invalidates and republishes readiness even when its DB aliases the previous wrapper");
        newStorage.direct = new NamedStringDatabase();
        observe(newStorage);
        check(notifications.get() == 3 && active() == newStorage.direct, "New storage direct DB replacement is published and notified");
        verifyQuery(newStorage.direct, "rawQuery:String[]", -1);
        check(storage.direct.queries == 0, "Replaced database is not used for subsequent queries");
    }
    private static void privacyCase() throws Throwable {
        start();
        CoreStorage storage = new CoreStorage();
        storage.direct = new NamedStringDatabase();
        XposedBridge.logFailure = new BareThrowable();
        observe(storage);
        XposedBridge.logFailure = null;
        verifyQuery(storage.direct, "rawQuery:String[]", -1);
        try {
            HostDatabaseBridge.rawQuery("DELETE FROM userinfo WHERE token='" + PARAM + "'", new String[]{PREF});
            throw new AssertionError("Write SQL unexpectedly accepted");
        } catch (SecurityException expected) {
            check(storage.direct.queries == 1, "Rejected SQL never reaches database");
        }
    }
    private static void invalidAccountMetadata(boolean ambiguous) throws Throwable {
        initialize();
        Method account = method(Kernel.class, "accountStorage");
        HostTargetResolver.accountCandidates = ambiguous ? java.util.List.of(account, account) : java.util.List.of();
        HostTargetResolver.succeed(entry());
        check(installed() && "HOOKED".equals(HostDatabaseBridge.status()) && !HostDatabaseBridge.isReady(),
                "Missing or ambiguous optional account metadata leaves valid DB hook registered without fake readiness");
        check(!XposedBridge.hooks.containsKey(method(Kernel.class, "instance")) && Kernel.getterCalls == 0,
                "Invalid account metadata cannot register/call a guessed Kernel instance getter");
    }
    private static void legacyLocate() throws Throwable {
        // Compatibility only: never initialize and never equate null identity with a runtime hook.
        check(field(HostDatabaseBridge.class, "storageClass").get(null) == null && !installed(),
                "Legacy fixture is explicitly unregistered and has null identity");
        Nested legacy = new Nested();
        capture(legacy);
        check(HostDatabaseBridge.isReady() && active() == legacy.database, "Private legacy locate path remains compatible");
        check(!installed() && XposedBridge.hooks.isEmpty() && HostTargetResolver.resolveCalls == 0,
                "Legacy capture does not count as a resolved/registered runtime success");
    }

    public static void main(String[] args) throws Throwable {
        switch (args[0]) {
            case "hook-failure": hookFailure(); break;
            case "resolver-failure": resolverFailure(); break;
            case "late-capture": lateCapture(); break;
            case "named-string": namedString(); break;
            case "named-object": namedObject(); break;
            case "verified-wrapper": verifiedWrapper(); break;
            case "ambiguous-unknown": ambiguous(false); break;
            case "ambiguous-overloads": ambiguous(true); break;
            case "interface-metadata": interfaceMetadata(); break;
            case "reject-unrelated-nested": rejectUnrelatedNested(); break;
            case "closed-database": closedDatabase(); break;
            case "replacement-listeners": replacementListeners(); break;
            case "privacy": privacyCase(); break;
            case "legacy-locate": legacyLocate(); break;
            case "missing-account-metadata": invalidAccountMetadata(false); break;
            case "ambiguous-account-metadata": invalidAccountMetadata(true); break;
            default: throw new AssertionError("Unknown case " + args[0]);
        }
        passed("database", args[0]);
    }
}
