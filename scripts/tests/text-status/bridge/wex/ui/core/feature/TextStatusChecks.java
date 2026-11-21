package wex.ui.core.feature;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

/** Runs one case per JVM; the bridge and its private Targets are real production code. */
public final class TextStatusChecks {
    private static final String A = "wxid_A", B = "wxid_B";
    private static int assertions;

    public static void main(String[] args) throws Exception {
        String test = args[0];
        switch (test) {
            case "not-ready": notReady(false); break;
            case "account-mismatch": notReady(true); break;
            case "queued-read-revoked": queuedReadRevoked(); break;
            case "queued-open-revoked": queuedOpenRevoked(); break;
            case "ready-read": readyRead(); break;
            case "ordinary-throwable": ordinaryFailure(new Throwable("direct host throwable")); break;
            case "ordinary-error": ordinaryFailure(new AssertionError("direct host error")); break;
            case "singleton-init-failure": brokenSingleton(BrokenInitService.class); break;
            case "singleton-error": brokenSingleton(BrokenErrorService.class); break;
            case "open-throwable": openThrowable(); break;
            case "render-throwable": renderThrowable(); break;
            case "listeners": listeners(); break;
            case "stale-token": staleToken(); break;
            case "off-main-revoked": offMainRevoked(); break;
            case "background-rejected": backgroundRejected(); break;
            case "main-rejected": mainRejected(false); break;
            case "main-throws": mainRejected(true); break;
            default: throw new AssertionError("Unknown case " + test);
        }
        System.out.println("PASS bridge/" + test + " (" + assertions + " assertions)");
    }

    private static void notReady(boolean mismatch) throws Exception {
        SelfProfile.account = A;
        SelfProfile.databaseReady = mismatch;
        SelfProfile.confirmed = mismatch ? B : "";
        inject(UnreadyService.class);
        TextStatusBridge.refresh(true);
        final int[] listenerCalls = {0};
        Runnable listener = () -> listenerCalls[0]++;
        TextStatusBridge.subscribe(listener);
        Handler.drain();
        // Exercise the action and icon gates even if a previously cached snapshot is READY.
        set("snapshot", ready(A));
        Activity owner = new Activity();
        TextStatusBridge.open(owner);
        ImageView image = new ImageView();
        TextStatusBridge.renderIcon(image, ready(A));
        HostTargetResolver.drain(); Handler.drain();
        check(Probe.initializations == 0, "unconfirmed nonempty wxid must not initialize host singleton");
        check(Probe.reads == 0 && Probe.icons == 0 && Probe.actions == 0,
                "refresh(force)/subscribe/open/renderIcon must all gate on same ready account");
        check(HostTargetResolver.resolutions == 0, "unready account must not resolve host targets");
        check(!loading(), "unready refresh must not leave loading latched");
        check(owner.opened == 0, "unready action must not launch editor");
        check(listenerCalls[0] > 0, "subscription still notifies local listener");
        check(image.clearCalls > 0, "unready render clears stale drawable");
    }

    private static void queuedReadRevoked() throws Exception {
        SelfProfile.ready(A); inject(UnreadyService.class);
        TextStatusBridge.refresh(true);
        check(HostTargetResolver.pending() == 1 && loading(), "ready refresh queues one loading request");
        SelfProfile.databaseReady = false;
        HostTargetResolver.runOne(); Handler.drain();
        check(Probe.initializations == 0 && Probe.reads == 0, "worker must recheck ready before Field.get");
        check(!loading(), "revoked queued request clears loading");
        check(TextStatusBridge.snapshot().kind != TextStatusBridge.Kind.READY, "revoked request cannot publish READY");
    }

    private static void queuedOpenRevoked() throws Exception {
        SelfProfile.ready(A); inject(UnreadyService.class); set("snapshot", ready(A));
        Activity owner = new Activity(); TextStatusBridge.open(owner);
        check(HostTargetResolver.pending() == 1, "ready open queues host operation");
        SelfProfile.confirmed = "";
        HostTargetResolver.runOne(); Handler.drain();
        check(Probe.initializations == 0 && Probe.reads == 0, "open worker rechecks account confirmation");
        check(Probe.actions == 0 && owner.opened == 0, "revoked open cannot perform host action");
    }

    private static void readyRead() throws Exception {
        SelfProfile.ready(A); inject(HealthyService.class);
        TextStatusBridge.refresh(true);
        check(Probe.initializations == 0, "queueing itself does not initialize host");
        HostTargetResolver.runOne();
        check(loading(), "worker result waits for controlled MAIN queue");
        Handler.drain();
        TextStatusBridge.Snapshot state = TextStatusBridge.snapshot();
        check(state.kind == TextStatusBridge.Kind.READY && A.equals(state.account), "confirmed account reads READY");
        check("busy".equals(state.description) && "status-1".equals(state.statusId), "real Targets extracts record and topic");
        check(Probe.initializations == 1 && Probe.reads == 1, "real singleton Field.get initializes exactly once");
        check(!loading(), "successful request clears loading");
        TextStatusBridge.renderIcon(new ImageView(), state);
        check(Probe.icons == 1, "ready icon invokes real target signature");
        Activity owner = new Activity(); TextStatusBridge.open(owner);
        HostTargetResolver.drain(); Handler.drain();
        check(Probe.actions == 1, "ready action uses real action constructor and method");
    }

    private static void ordinaryFailure(Throwable failure) throws Exception {
        SelfProfile.ready(A); HostTargetResolver.resolveFailure = failure;
        TextStatusBridge.refresh(true);
        // Deliberately no Method.invoke wrapper here: resolver throws Throwable/Error directly.
        HostTargetResolver.runOne(); Handler.drain();
        check(!loading(), "direct " + failure.getClass().getSimpleName() + " clears loading");
        check(TextStatusBridge.snapshot().kind == TextStatusBridge.Kind.ERROR, "direct failure publishes ERROR");
        check(!disabled(), "ordinary Throwable/Error is retryable, not permanent linkage disablement");
        HostTargetResolver.resolveFailure = null; inject(HealthyService.class);
        TextStatusBridge.refresh(true); HostTargetResolver.drain(); Handler.drain();
        check(TextStatusBridge.snapshot().kind == TextStatusBridge.Kind.READY, "ordinary failure can retry successfully");
        check(Probe.reads == 1, "retry executes healthy target once");
    }

    private static void brokenSingleton(Class<?> service) throws Exception {
        SelfProfile.ready(A); inject(service); TextStatusBridge.refresh(true);
        // Class is not initialized by class literal/getDeclaredField. Production current()
        // performs Field.get(null), so initializer Errors reach the catch boundary directly.
        check(Probe.initializations == 0, "fixture reflection does not initialize poisoned host");
        HostTargetResolver.runOne(); Handler.drain();
        check(Probe.initializations == 1, "real singleton initialization was attempted");
        check(!loading(), "singleton initializer failure clears loading");
        check(TextStatusBridge.snapshot().kind == TextStatusBridge.Kind.ERROR, "singleton failure publishes ERROR");
        check(disabled(), "any real Field.get initializer failure immediately disables status service");
        TextStatusBridge.refresh(true); HostTargetResolver.drain(); Handler.drain();
        check(!loading(), "disabled forced retry also leaves loading clear");
        Field flag = field("disabled");
        check(Modifier.isVolatile(flag.getModifiers()), "disabled is volatile across MAIN/worker");
        // clinit count alone cannot detect repeated NCDFE Field.get. Swap in a healthy
        // singleton while preserving disabled, then prove *all* entrypoints stay inert.
        inject(HealthyService.class);
        int reads = Probe.reads, icons = Probe.icons, actions = Probe.actions;
        TextStatusBridge.refresh(true);
        set("snapshot", ready(A));
        TextStatusBridge.open(new Activity());
        TextStatusBridge.renderIcon(new ImageView(), ready(A));
        HostTargetResolver.drain(); Handler.drain();
        check(Probe.initializations == 1, "disabled refresh/open/render cannot initialize replacement host");
        check(Probe.reads == reads && Probe.icons == icons && Probe.actions == actions,
                "disabled bridge never re-reads or renders host targets");
        check(!loading(), "disabled entrypoints do not latch loading");
    }

    private static void openThrowable() throws Exception {
        SelfProfile.ready(A); inject(HealthyService.class); set("snapshot", ready(A));
        Probe.readFailure = new Throwable("status action read");
        TextStatusBridge.open(new Activity()); HostTargetResolver.drain(); Handler.drain();
        check(!loading(), "Throwable during open read does not latch loading");
        check(Probe.actions == 0, "failed read never executes action");
        check(!disabled(), "ordinary reflected Throwable during open remains retryable");
    }

    private static void renderThrowable() throws Exception {
        SelfProfile.ready(A); inject(HealthyService.class);
        Probe.iconFailure = new AssertionError("icon renderer error");
        ImageView image = new ImageView();
        TextStatusBridge.renderIcon(image, ready(A)); Handler.drain();
        check(image.clearCalls == 1 && Probe.icons == 1, "failed renderer attempted target and cleared old image");
        check(!loading(), "render error does not latch loading");
        check(!disabled(), "ordinary renderer Error remains retryable");
    }

    private static void listeners() throws Exception {
        SelfProfile.ready(A); inject(HealthyService.class);
        final int[] goodCalls = {0}, badCalls = {0};
        Runnable good = () -> goodCalls[0]++;
        Runnable bad = () -> { badCalls[0]++; HostTargetResolver.<RuntimeException>raise(new Throwable("listener failed")); };
        TextStatusBridge.subscribe(good); Handler.drain();
        TextStatusBridge.subscribe(bad); Handler.drain();
        check(badCalls[0] == 1, "initial subscriber Throwable is individually isolated");
        // Publish iterates from end; bad is last and must not prevent good notification.
        int goodBefore = goodCalls[0], badBefore = badCalls[0];
        HostTargetResolver.drain(); Handler.drain();
        check(goodCalls[0] > goodBefore && badCalls[0] > badBefore, "publish continues after individual listener Throwable");
        check(!loading() && TextStatusBridge.snapshot().kind == TextStatusBridge.Kind.READY,
                "listener failures do not damage completed request");
        TextStatusBridge.unsubscribe(good); TextStatusBridge.unsubscribe(bad);
        check(((List<?>) field("LISTENERS").get(null)).isEmpty(), "listeners remain individually removable");
    }

    private static void staleToken() throws Exception {
        SelfProfile.ready(A); inject(HealthyService.class); TextStatusBridge.refresh(true);
        HostTargetResolver.runOne(); // Old A result is now queued on MAIN, not published.
        SelfProfile.ready(B); TextStatusBridge.accountChanged(B);
        TextStatusBridge.refresh(true);
        check(loading(), "new account request starts after accountChanged invalidates old token");
        Handler.runOne();
        check(B.equals(TextStatusBridge.snapshot().account), "old token cannot overwrite accountChanged snapshot");
        check(loading(), "old completion cannot clear new request loading");
        HostTargetResolver.runOne(); Handler.drain();
        check(TextStatusBridge.snapshot().kind == TextStatusBridge.Kind.READY
                && B.equals(TextStatusBridge.snapshot().account), "new account result is published");
        check(!loading(), "new request completion clears its own loading");
    }

    private static void offMainRevoked() throws Exception {
        SelfProfile.ready(A); inject(UnreadyService.class);
        Looper.runOffMain(() -> TextStatusBridge.refresh(true));
        check(Handler.pending() == 1 && HostTargetResolver.pending() == 0, "off-main refresh marshals before host work");
        SelfProfile.databaseReady = false; Handler.drain(); HostTargetResolver.drain(); Handler.drain();
        check(Probe.initializations == 0 && Probe.reads == 0, "MAIN queued refresh gates revoked ready");
        check(!loading(), "revoked off-main refresh leaves loading clear");
    }

    private static void backgroundRejected() throws Exception {
        SelfProfile.ready(A); inject(HealthyService.class);
        HostTargetResolver.submissionFailure = new java.util.concurrent.RejectedExecutionException("host queue rejected");
        TextStatusBridge.refresh(true); Handler.drain();
        check(!loading(), "rejected worker submission clears loading synchronously");
        check(TextStatusBridge.snapshot().kind == TextStatusBridge.Kind.ERROR, "worker rejection publishes ERROR");
        check(Probe.initializations == 0 && HostTargetResolver.pending() == 0, "rejected worker never touches host");
        check(!disabled(), "ordinary queue rejection is retryable");
        HostTargetResolver.submissionFailure = null;
        TextStatusBridge.refresh(true); HostTargetResolver.drain(); Handler.drain();
        check(TextStatusBridge.snapshot().kind == TextStatusBridge.Kind.READY, "worker queue recovery permits retry");
    }

    private static void mainRejected(boolean throwsFailure) throws Exception {
        SelfProfile.ready(A); inject(HealthyService.class); TextStatusBridge.refresh(true);
        if (throwsFailure) Handler.postFailure = new AssertionError("MAIN queue failed");
        else Handler.rejectPosts = true;
        HostTargetResolver.runOne();
        check(Handler.pending() == 0, "MAIN rejection does not secretly execute result inline");
        check(!loading(), "MAIN result submission failure clears volatile loading");
        check(TextStatusBridge.snapshot().kind != TextStatusBridge.Kind.READY, "rejected completion is not published");
        check(!disabled(), "MAIN rejection is not host initializer failure");
        Handler.rejectPosts = false; Handler.postFailure = null;
        TextStatusBridge.refresh(true); HostTargetResolver.drain(); Handler.drain();
        check(TextStatusBridge.snapshot().kind == TextStatusBridge.Kind.READY && !loading(), "MAIN recovery permits successful retry");
    }

    private static TextStatusBridge.Snapshot ready(String account) {
        return new TextStatusBridge.Snapshot(TextStatusBridge.Kind.READY, account,
                "status-1", "busy", "host text", "icon-1", System.currentTimeMillis() / 1000 + 3600);
    }
    private static void check(boolean condition, String message) {
        assertions++; if (!condition) throw new AssertionError(message);
    }
    private static Field field(String name) throws Exception {
        Field result = TextStatusBridge.class.getDeclaredField(name); result.setAccessible(true); return result;
    }
    private static void set(String name, Object value) throws Exception { field(name).set(null, value); }
    private static boolean loading() throws Exception { return field("loading").getBoolean(null); }
    private static boolean disabled() throws Exception { return field("disabled").getBoolean(null); }
    private static void inject(Class<?> service) throws Exception {
        Class<?> targets = Class.forName("wex.ui.core.feature.TextStatusBridge$Targets");
        Constructor<?> constructor = targets.getDeclaredConstructor(Method.class, Method.class, Field.class,
                Method.class, Method.class, Method.class, Method.class, Class.class);
        constructor.setAccessible(true);
        Field singleton = service.getDeclaredField("INSTANCE"); singleton.setAccessible(true);
        Object value = constructor.newInstance(Storage.class.getMethod("latest", String.class),
                BaseService.class.getMethod("storage"), singleton,
                Topics.class.getMethod("topic", Record.class, boolean.class, int.class, Object.class),
                Helper.class.getMethod("setIcon", Helper.class, ImageView.class, String.class,
                        Object.class, Object.class, Object.class, boolean.class, boolean.class, int.class, Object.class),
                Helper.class.getMethod("get"), Helper.class.getMethod("description", Topic.class), Record.class);
        Field actions = targets.getDeclaredField("actions"); actions.setAccessible(true);
        actions.set(value, Actions.class.getMethod("show", Context.class, Record.class));
        set("targets", value);
    }

    public static final class Probe {
        static int initializations, reads, icons, actions;
        static Throwable readFailure, iconFailure;
    }
    public static class BaseService { public Storage storage() { return new Storage(); } }
    public static final class HealthyService extends BaseService {
        public static final HealthyService INSTANCE = initialize();
        private static HealthyService initialize() { Probe.initializations++; return new HealthyService(); }
    }
    public static final class UnreadyService extends BaseService {
        public static final UnreadyService INSTANCE = initialize();
        private static UnreadyService initialize() { Probe.initializations++; return new UnreadyService(); }
    }
    public static final class BrokenInitService extends BaseService {
        public static final BrokenInitService INSTANCE = initialize();
        private static BrokenInitService initialize() {
            Probe.initializations++; throw new IllegalStateException("host initialization unavailable");
        }
    }
    public static final class BrokenErrorService extends BaseService {
        public static final BrokenErrorService INSTANCE = initialize();
        private static BrokenErrorService initialize() {
            Probe.initializations++; throw new AssertionError("host initialization assertion");
        }
    }
    public static final class Storage {
        public Record latest(String account) throws Throwable {
            Probe.reads++;
            if (Probe.readFailure != null) throw Probe.readFailure;
            return new Record(account);
        }
    }
    public static final class Record {
        public final String field_UserName, field_StatusID = "status-1", field_Description = "host text", field_IconID = "icon-1";
        public final long field_ExpireTime = System.currentTimeMillis() / 1000 + 3600;
        public final Object field_EmojiInfo = null;
        Record(String account) { field_UserName = account; }
    }
    public static final class Topic { }
    public static final class Topics {
        public static Topic topic(Record record, boolean flag, int mask, Object ignored) { return new Topic(); }
    }
    public static final class Helper {
        public static Helper get() { return new Helper(); }
        public String description(Topic info) { return "busy"; }
        public static void setIcon(Helper helper, ImageView image, String icon, Object a, Object b, Object c,
                                   boolean d, boolean e, int mask, Object ignored) throws Throwable {
            Probe.icons++; if (Probe.iconFailure != null) throw Probe.iconFailure;
        }
    }
    public static final class Seed { public Seed() { } }
    public static final class Actions {
        public Context context;
        public Record record;
        public Actions(Seed seed) { }
        public void show(Context context, Record record) { Probe.actions++; }
    }
}
