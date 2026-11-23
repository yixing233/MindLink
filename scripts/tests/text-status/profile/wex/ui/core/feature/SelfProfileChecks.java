package wex.ui.core.feature;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.SystemClock;
import dev.mindlink.xposed.XposedBridge;
import java.util.Arrays;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Real SelfProfile, a latched fake database, and a controlled MAIN completion queue. */
public final class SelfProfileChecks {
    private static final String A = "wxid_A", B = "wxid_B";
    private static final String CONFIG_NAME = "config-nickname-private", NEXT_CONFIG_NAME = "replacement-config-nickname-private";
    private static final String STATUS_DESCRIPTION = "status-description-private", PRIVATE_MESSAGE = "private-config-host-message";
    private static final java.util.concurrent.atomic.AtomicInteger messageReads = new java.util.concurrent.atomic.AtomicInteger();
    private static final AtomicReference<Throwable> uncaught = new AtomicReference<>();
    private static final java.util.concurrent.atomic.AtomicInteger networkAttempts = new java.util.concurrent.atomic.AtomicInteger();
    private static int assertions;

    public static void main(String[] args) throws Exception {
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> uncaught.compareAndSet(null, error));
        // Default rows have empty avatarUrl. Seeded, already-loaded images never download;
        // deny and count every accidental URL connection as an independent assertion.
        URL.setURLStreamHandlerFactory(protocol -> new URLStreamHandler() {
            @Override protected URLConnection openConnection(URL url) {
                networkAttempts.incrementAndGet();
                throw new AssertionError("Network access prohibited by isolated profile test");
            }
        });
        contract();
        switch (args[0]) {
            case "not-confirmed": notConfirmed(); break;
            case "query-confirmed": queryConfirmed(); break;
            case "account-revoked": accountRevoked(); break;
            case "query-failure": queryFailure(); break;
            case "io-rejected": ioRejected(); break;
            case "main-rejected": mainRejected(); break;
            case "queued-account-change": queuedAccountChange(); break;
            case "stale-database-account": staleDatabaseAccount(); break;
            case "missing-optional": missingOptional(); break;
            case "nickname-error": optionalError(HostDatabaseBridge.Stage.NICKNAME); break;
            case "avatar-error": optionalError(HostDatabaseBridge.Stage.AVATAR); break;
            case "optional-existing-account": optionalExistingAccount(); break;
            case "optional-new-account": optionalNewAccount(); break;
            case "optional-stale-result": optionalStaleResult(); break;
            case "mandatory-failure-revokes": mandatoryFailureRevokes(); break;
            case "config-only-identity": configOnlyIdentity(); break;
            case "config-subscribed-refresh": configSubscribedRefresh(); break;
            case "config-capture-confirmed": configCaptureConfirmed(); break;
            case "config-db-identity-mismatch": configDatabaseMismatch(); break;
            case "config-database-stale": configDatabaseStale(); break;
            case "config-preference-mismatch": configPreferenceMismatch(); break;
            case "config-old-token": configOldToken(); break;
            case "config-mandatory-failure": configMandatoryFailure(); break;
            case "config-read-failure": configReadFailure(); break;
            case "config-empty-name-fallback": configEmptyNameFallback(); break;
            case "config-optional-error": configOptionalError(); break;
            case "config-lost-database": configLostDatabase(); break;
            case "profile-listeners": profileListeners(); break;
            case "profile-summary-status": profileSummaryStatus(); break;
            case "force-while-loading": forceWhileLoading(); break;
            case "force-followup-bounded": forceFollowupBounded(); break;
            case "force-failure-recovery": forceFailureRecovery(); break;
            case "force-generation-isolation": forceGenerationIsolation(); break;
            case "force-old-failure-isolation": forceOldFailureIsolation(); break;
            case "force-main-rejection-isolation": forceMainRejectionIsolation(); break;
            case "force-main-throw-isolation": forceMainThrowIsolation(); break;
            case "self-read-ready-suppressed": selfReadReadySuppressed(); break;
            case "listener-force-bounded": listenerForceBounded(); break;
            case "config-empty-name-error-retained": configEmptyNameErrorRetained(); break;
            case "config-name-unknown-empty": configNameUnknownEmpty(); break;
            case "profile-stage-booleans": profileStageBooleans(); break;
            default: throw new AssertionError("Unknown profile case " + args[0]);
        }
        check(uncaught.get() == null, "no uncaught profile worker error");
        check(HostDatabaseBridge.violations.isEmpty(), "SQL/order/params/off-MAIN contract violations: " + HostDatabaseBridge.violations);
        check(HostDatabaseBridge.queries == HostDatabaseBridge.accountQueries
                + HostDatabaseBridge.nicknameQueries + HostDatabaseBridge.avatarQueries,
                "all queries are counted by exact SQL stage");
        check(networkAttempts.get() == 0, "profile checks must not attempt any external connection");
        check(HostProfileBridge.violations.isEmpty(), "config reads stay off MAIN");
        for (String log : XposedBridge.logs) checkPrivate(log);
        for (String stage : HostStageLog.stages) checkPrivate(stage);
        System.out.println("PASS profile/" + args[0] + " (" + assertions + " assertions)");
    }

    private static void contract() throws Exception {
        Method method = SelfProfile.class.getDeclaredMethod("statusAccountReady", String.class);
        int modifiers = method.getModifiers();
        check(Modifier.isStatic(modifiers) && method.getReturnType() == boolean.class,
                "statusAccountReady(String) must be static boolean");
        check(!Modifier.isPublic(modifiers) && !Modifier.isPrivate(modifiers) && !Modifier.isProtected(modifiers),
                "statusAccountReady is a package API");
    }

    private static void initialize() {
        Context.preferenceAccount = A; HostDatabaseBridge.ready = false;
        SelfProfile.initialize(new Context());
    }

    private static void notConfirmed() throws Exception {
        initialize();
        check(A.equals(SelfProfile.wxid()), "preference account is immediately available");
        HostDatabaseBridge.ready = true;
        check(!SelfProfile.statusAccountReady(A), "database ready plus nonempty preference wxid is not confirmation");
        check(!SelfProfile.statusAccountReady("") && !SelfProfile.statusAccountReady(null), "empty/null account never ready");
        check(HostDatabaseBridge.queries == 0 && !loading(), "initialization does not fabricate query success");
    }

    private static void queryConfirmed() throws Exception {
        initialize(); HostDatabaseBridge.ready = true;
        HostDatabaseBridge.Request request = HostDatabaseBridge.prepare(A, null);
        SelfProfile.refresh(true); awaitEntered();
        check(loading(), "real background query marks profile loading");
        check(!SelfProfile.statusAccountReady(A), "queued/in-flight query does not confirm account");
        complete(); checkQueries(request, 3);
        check(HostDatabaseBridge.accountQueries == 1 && HostDatabaseBridge.nicknameQueries == 1
                && HostDatabaseBridge.avatarQueries == 1, "success runs one required and two separate optional queries");
        check(SelfProfile.statusAccountReady(A), "successful query confirms exact account in this process");
        check(!SelfProfile.statusAccountReady(B), "confirmation is not transferable to another account");
        check(!loading() && "verified nickname".equals(SelfProfile.nickname()), "success clears loading and reads production row");
        check(TextStatusBridge.refreshes > 0, "confirmation requests text status refresh");
        HostDatabaseBridge.ready = false;
        check(!SelfProfile.statusAccountReady(A), "database ready revocation immediately gates confirmed account");
    }

    private static void accountRevoked() throws Exception {
        initialize(); HostDatabaseBridge.ready = true;
        querySuccess(A);
        check(SelfProfile.statusAccountReady(A), "first account is query-confirmed");
        Context.preferenceAccount = B; HostDatabaseBridge.ready = false;
        SelfProfile.refresh(true);
        check(B.equals(SelfProfile.wxid()), "account preference change is observed without database query");
        check(!SelfProfile.statusAccountReady(A) && !SelfProfile.statusAccountReady(B), "account change revokes prior confirmation");
        check(B.equals(TextStatusBridge.changedTo), "account change invalidates text status token");
        HostDatabaseBridge.ready = true;
        check(!SelfProfile.statusAccountReady(B), "database recovery alone does not confirm replacement account");
        querySuccess(B);
        check(SelfProfile.statusAccountReady(B) && !SelfProfile.statusAccountReady(A), "replacement account must be queried successfully");
    }

    private static void queryFailure() throws Exception {
        initialize(); HostDatabaseBridge.ready = true;
        for (Throwable failure : new Throwable[] {new IllegalStateException("database unavailable"),
                new Throwable("direct database throwable"), new AssertionError("database assertion")}) {
            HostDatabaseBridge.prepare(A, failure);
            SelfProfile.refresh(true); awaitEntered();
            check(loading(), "failed query begins loading");
            complete();
            check(!loading(), failure.getClass().getSimpleName() + " query clears loading");
            check(!SelfProfile.statusAccountReady(A), "failure never confirms account");
        }
        querySuccess(A);
        check(SelfProfile.statusAccountReady(A) && !loading(), "query failure remains retryable");
    }

    private static void ioRejected() throws Exception {
        initialize(); HostDatabaseBridge.ready = true;
        Field io = SelfProfile.class.getDeclaredField("IO"); io.setAccessible(true);
        ((java.util.concurrent.ExecutorService) io.get(null)).shutdown();
        SelfProfile.refresh(true);
        check(!loading(), "shut-down real IO executor rejection clears loading");
        check(!SelfProfile.statusAccountReady(A), "rejected IO request cannot confirm account");
        check(HostDatabaseBridge.queries == 0, "rejected executor never enters fake database");
    }

    private static void mainRejected() throws Exception {
        initialize(); HostDatabaseBridge.ready = true;
        for (Throwable failure : new Throwable[] {null, new Throwable("query failed before MAIN rejection")}) {
            HostDatabaseBridge.prepare(A, failure);
            SelfProfile.refresh(true); awaitEntered();
            Handler.rejectPosts = true;
            HostDatabaseBridge.release.countDown();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (loading() && uncaught.get() == null && System.nanoTime() < deadline) Thread.sleep(2);
            check(uncaught.get() == null && !loading(), "rejected MAIN completion clears volatile loading without escaping IO");
            check(Handler.pending() == 0, "rejected profile callback does not run inline");
            check(!SelfProfile.statusAccountReady(A), "unpublished query does not confirm account");
            Handler.rejectPosts = false;
        }
        querySuccess(A);
        check(SelfProfile.statusAccountReady(A) && !loading(), "MAIN queue recovery permits confirmation retry");
    }

    private static void queuedAccountChange() throws Exception {
        initialize(); HostDatabaseBridge.ready = true;
        HostDatabaseBridge.prepare(A, null);
        SelfProfile.refresh(true); awaitEntered();
        HostDatabaseBridge.release.countDown(); awaitMain();
        // The real success callback must recheck preferences before accepting old token.
        Context.preferenceAccount = B;
        Handler.drain();
        check(B.equals(SelfProfile.wxid()), "MAIN completion observes replacement preference account");
        check(!SelfProfile.statusAccountReady(A) && !SelfProfile.statusAccountReady(B), "old queued query cannot confirm replacement account");
        check(!loading(), "preference account invalidation clears old request loading");
        check(B.equals(TextStatusBridge.changedTo), "queued account switch invalidates status token");
        querySuccess(B);
        check(SelfProfile.statusAccountReady(B), "fresh query confirms replacement account");
    }

    private static void staleDatabaseAccount() throws Exception {
        initialize();
        Context.preferenceAccount = B;
        SelfProfile.refresh(true); // Adopt B while database is not ready; no query is pending.
        HostDatabaseBridge.ready = true;
        querySuccess(B);
        check(B.equals(SelfProfile.wxid()) && SelfProfile.statusAccountReady(B),
                "current preference account is stable and previously query-confirmed");
        check(Handler.pending() == 0, "no old MAIN callback is pending before stale database query");
        int refreshes = TextStatusBridge.refreshes, accountChanges = TextStatusBridge.accountChanges;
        HostDatabaseBridge.prepare(A, null); // Latched old database still reports A, not current B.
        SelfProfile.refresh(true); awaitEntered();
        Field request = SelfProfile.class.getDeclaredField("requestId"); request.setAccessible(true);
        long token = request.getLong(null);
        check(loading(), "stale database query begins loading");
        check(B.equals(Context.preferenceAccount), "preferences already match current wxid before query completion");
        complete(); // No preference/account change or new request while this query is pending.
        check(request.getLong(null) == token, "stale result is rejected without any pending token change");
        check(B.equals(SelfProfile.wxid()), "old database account must not overwrite current wxid");
        Field confirmed = SelfProfile.class.getDeclaredField("confirmedAccount"); confirmed.setAccessible(true);
        check("".equals(confirmed.get(null)), "stale database result revokes prior account confirmation");
        check(!SelfProfile.statusAccountReady(A) && !SelfProfile.statusAccountReady(B),
                "neither stale nor current account remains confirmed after mismatch");
        check(TextStatusBridge.refreshes == refreshes, "rejected database account never triggers status refresh");
        check(TextStatusBridge.accountChanges == accountChanges + 1 && B.equals(TextStatusBridge.changedTo),
                "mismatch invalidates status snapshot using unchanged current wxid");
        check(!loading(), "mismatched database success callback clears loading");
        querySuccess(B);
        check(SelfProfile.statusAccountReady(B) && !loading(), "matching fresh database query remains retryable");
    }

    private static void missingOptional() throws Exception {
        initialize(); HostDatabaseBridge.ready = true;
        HostDatabaseBridge.Request request = HostDatabaseBridge.prepare(A, null);
        request.missingNickname = true; request.missingAvatar = true;
        runRequest(request); checkQueries(request, 3);
        check(SelfProfile.statusAccountReady(A) && !loading(), "missing optional rows do not block account confirmation");
        check(SelfProfile.nickname().isEmpty() && value("avatarUrl").equals(""), "missing nickname/avatar rows yield blank optional fields");
        check(TextStatusBridge.refreshes == 1, "missing optional rows still allow status refresh");
    }

    private static void optionalError(HostDatabaseBridge.Stage stage) throws Exception {
        initialize(); HostDatabaseBridge.ready = true;
        HostDatabaseBridge.Request request = HostDatabaseBridge.prepare(A, null);
        if (stage == HostDatabaseBridge.Stage.NICKNAME) request.nicknameFailure = new Throwable("missing rcontact table");
        else request.avatarFailure = new AssertionError("missing img_flag table");
        runRequest(request); checkQueries(request, 3);
        check(SelfProfile.statusAccountReady(A) && !loading(), stage + " optional failure must not prevent confirmation");
        check(A.equals(SelfProfile.wxid()), "optional failure cannot change required account result");
        check((stage == HostDatabaseBridge.Stage.NICKNAME ? "" : "verified nickname").equals(SelfProfile.nickname()),
                "nickname stage result is applied independently of avatar stage");
        check(value("avatarUrl").equals(""), "failed/missing avatar address stays blank for fresh account");
        check(TextStatusBridge.refreshes == 1, "optional failure still requests status refresh");
    }

    private static void optionalExistingAccount() throws Exception {
        initialize(); HostDatabaseBridge.ready = true; querySuccess(A);
        seedLoadedProfile("retained nickname", "https://qlogo.cn/already-loaded.png");
        int refreshes = TextStatusBridge.refreshes, changes = TextStatusBridge.accountChanges;
        HostDatabaseBridge.Request request = HostDatabaseBridge.prepare(A, null);
        request.nicknameFailure = new Throwable("rcontact inaccessible");
        request.avatarFailure = new AssertionError("img_flag inaccessible");
        runRequest(request); checkQueries(request, 3);
        check(SelfProfile.statusAccountReady(A) && !loading(), "both optional failures retain current confirmed account");
        check("retained nickname".equals(SelfProfile.nickname())
                && "https://qlogo.cn/already-loaded.png".equals(value("avatarUrl")),
                "existing account keeps previous nickname and avatar URL after optional errors");
        check(SelfProfile.avatar() != null, "existing already-loaded avatar remains available");
        check(TextStatusBridge.refreshes == refreshes + 1 && TextStatusBridge.accountChanges == changes,
                "optional errors refresh status without account revocation");
    }

    private static void optionalNewAccount() throws Exception {
        initialize(); HostDatabaseBridge.ready = true; querySuccess(A);
        seedLoadedProfile("old account nickname", "https://qlogo.cn/old-account.png");
        Context.preferenceAccount = ""; // No preference account: required DB row is authoritative.
        int refreshes = TextStatusBridge.refreshes, changes = TextStatusBridge.accountChanges;
        HostDatabaseBridge.Request request = HostDatabaseBridge.prepare(B, null);
        request.nicknameFailure = new Throwable("rcontact inaccessible for replacement account");
        request.avatarFailure = new AssertionError("img_flag inaccessible for replacement account");
        runRequest(request); checkQueries(request, 3);
        check(B.equals(SelfProfile.wxid()) && SelfProfile.statusAccountReady(B) && !SelfProfile.statusAccountReady(A),
                "required replacement account confirms even when both optional queries fail");
        check(SelfProfile.nickname().isEmpty() && "".equals(value("avatarUrl")) && SelfProfile.avatar() == null,
                "new account must not inherit old nickname, URL or loaded image after optional failures");
        check(TextStatusBridge.refreshes == refreshes + 1 && TextStatusBridge.accountChanges == changes + 1
                && B.equals(TextStatusBridge.changedTo), "new required account invalidates old status then refreshes");
        check(!loading(), "replacement-account optional errors clear loading");
    }

    private static void optionalStaleResult() throws Exception {
        initialize(); HostDatabaseBridge.ready = true; querySuccess(A);
        // Block each optional stage independently, then deliver its OLD A rows after B
        // is adopted and a new request token exists. Old completion must not clear B loading.
        for (HostDatabaseBridge.Stage stage : new HostDatabaseBridge.Stage[] {
                HostDatabaseBridge.Stage.NICKNAME, HostDatabaseBridge.Stage.AVATAR}) {
            Context.preferenceAccount = A; querySuccess(A);
            HostDatabaseBridge.Request old = HostDatabaseBridge.prepare(A, null);
            old.nickname = "stale nickname for A"; old.avatarUrl = "https://qlogo.cn/stale-A.png";
            old.block(stage);
            SelfProfile.refresh(true); awaitEntered(); old.allow(HostDatabaseBridge.Stage.ACCOUNT);
            check(old.entered[stage.ordinal()].await(5, TimeUnit.SECONDS), "old account must enter blocked " + stage + " query");
            check(loading() && Handler.pending() == 0, "optional stage remains in flight before account switch");
            Context.preferenceAccount = B;
            HostDatabaseBridge.Request current = HostDatabaseBridge.prepare(B, null);
            current.nickname = "fresh nickname for B";
            SelfProfile.refresh(true);
            check(B.equals(SelfProfile.wxid()) && loading(), "replacement account request owns loading while old optional query is blocked");
            check(!SelfProfile.statusAccountReady(A) && !SelfProfile.statusAccountReady(B), "account switch revokes confirmation before optional completion");
            int refreshes = TextStatusBridge.refreshes, changes = TextStatusBridge.accountChanges;
            old.allow(stage); awaitMain(); Handler.runOne(); checkQueries(old, 3);
            check(B.equals(SelfProfile.wxid()) && SelfProfile.nickname().isEmpty()
                    && "".equals(value("avatarUrl")) && SelfProfile.avatar() == null,
                    "stale " + stage + " result must not install old account profile into B");
            check(!SelfProfile.statusAccountReady(A) && !SelfProfile.statusAccountReady(B), "old optional completion cannot confirm either account");
            check(loading(), "old optional completion cannot clear replacement request loading");
            check(TextStatusBridge.refreshes == refreshes && TextStatusBridge.accountChanges == changes,
                    "discarded old optional result neither refreshes nor invalidates new status");
            awaitEntered(); current.allow(HostDatabaseBridge.Stage.ACCOUNT); awaitMain(); Handler.drain();
            checkQueries(current, 3);
            check(B.equals(SelfProfile.wxid()) && SelfProfile.statusAccountReady(B) && !loading(), "fresh B optional results complete own request");
            check("fresh nickname for B".equals(SelfProfile.nickname()) && "".equals(value("avatarUrl")), "only new account optional data is published");
        }
    }

    private static void mandatoryFailureRevokes() throws Exception {
        initialize(); HostDatabaseBridge.ready = true;
        for (int scenario = 0; scenario < 5; scenario++) {
            querySuccess(A);
            check(SelfProfile.statusAccountReady(A), "required failure fixture begins with confirmed account");
            Throwable failure = scenario == 0 ? new IllegalStateException("userinfo inaccessible")
                    : scenario == 1 ? new Throwable("direct required query failure")
                    : scenario == 2 ? new AssertionError("required query error") : null;
            HostDatabaseBridge.Request request = HostDatabaseBridge.prepare(scenario == 4 ? "" : A, failure);
            request.missingAccount = scenario == 3;
            int refreshes = TextStatusBridge.refreshes, changes = TextStatusBridge.accountChanges;
            runRequest(request); checkQueries(request, 1);
            check(!loading() && "".equals(value("confirmedAccount")) && !SelfProfile.statusAccountReady(A),
                    "required failure/empty row must revoke previously confirmed account");
            check(A.equals(SelfProfile.wxid()), "required failure must not fabricate a replacement wxid");
            check(TextStatusBridge.refreshes == refreshes && TextStatusBridge.accountChanges == changes + 1,
                    "required failure invalidates status without refreshing it");
        }
        querySuccess(A);
        check(SelfProfile.statusAccountReady(A) && !loading(), "required failures remain retryable");
    }

    private static void configOnlyIdentity() throws Exception {
        initialize();
        check(HostProfileBridge.subscriptions == 1 && HostDatabaseBridge.subscriptions == 1,
                "initialize subscribes to both readiness sources exactly once");
        HostProfileBridge.Request request = HostProfileBridge.prepare(A, CONFIG_NAME);
        runConfig(request);
        checkConfigIdentity(request);
        check(A.equals(SelfProfile.wxid()) && CONFIG_NAME.equals(SelfProfile.nickname()) && SelfProfile.hasAny(),
                "observed config refresh successfully publishes exact account and nickname before database readiness");
        check(!HostDatabaseBridge.ready && HostDatabaseBridge.queries == 0,
                "config-only identity refresh must not query unavailable database");
        check(!loading() && "".equals(value("confirmedAccount")) && !SelfProfile.statusAccountReady(A),
                "config nickname is successful profile data but never database confirmation");
        check(SelfProfile.summary().contains("配置=IDENTITY_READY") && SelfProfile.summary().contains("状态门禁=false"),
                "summary reports config identity readiness separately from status gate");
        HostDatabaseBridge.ready = true;
        check(!SelfProfile.statusAccountReady(A), "turning database ready after config-only completion cannot invent confirmation");
        HostDatabaseBridge.ready = false;
    }

    private static void configSubscribedRefresh() throws Exception {
        initialize();
        SelfProfile.initialize(new Context());
        check(HostProfileBridge.subscriptions == 1 && HostDatabaseBridge.subscriptions == 1,
                "repeated initialize does not duplicate config/database subscription");
        HostProfileBridge.Request request = HostProfileBridge.prepare(A, CONFIG_NAME);
        android.os.Looper.runOffMain(HostProfileBridge::publish);
        check(Handler.pending() == 1 && HostProfileBridge.reads == 0,
                "host config notification off MAIN posts real refresh instead of reading inline");
        Handler.runOne(); awaitConfigEntered(request); request.allow(); awaitMain(); Handler.drain();
        checkConfigIdentity(request);
        check(CONFIG_NAME.equals(SelfProfile.nickname()) && !loading() && !SelfProfile.statusAccountReady(A),
                "subscribed notification actually refreshes config-only nickname while preserving gate");
    }

    private static void configCaptureConfirmed() throws Exception {
        initialize();
        HostProfileBridge.Request config = HostProfileBridge.prepare(A, CONFIG_NAME);
        config.captureDatabase = true;
        HostDatabaseBridge.Request database = HostDatabaseBridge.prepare(A, null);
        database.skipNickname = true;
        SelfProfile.refresh(true); awaitConfigEntered(config);
        check(!HostDatabaseBridge.ready && HostDatabaseBridge.queries == 0 && !SelfProfile.statusAccountReady(A),
                "config-backed database cannot be ready or confirm before identity read completes");
        config.allow(); awaitEntered();
        check(HostDatabaseBridge.ready && loading() && !SelfProfile.statusAccountReady(A),
                "database captured during config read still needs mandatory id2 query and MAIN publication");
        database.allow(HostDatabaseBridge.Stage.ACCOUNT); awaitMain(); Handler.drain();
        checkConfigIdentity(config); checkConfigQueries(database, false);
        check(A.equals(SelfProfile.wxid()) && CONFIG_NAME.equals(SelfProfile.nickname()) && SelfProfile.statusAccountReady(A),
                "captured database id2 matches config and confirms exact account, retaining config nickname");
        check(!loading() && TextStatusBridge.refreshes == 1, "successful matching capture clears loading and requests status refresh");
    }

    private static void configDatabaseMismatch() throws Exception {
        initialize();
        configDatabaseSuccess(A, CONFIG_NAME);
        int refreshes = TextStatusBridge.refreshes, changes = TextStatusBridge.accountChanges;
        HostProfileBridge.Request config = HostProfileBridge.prepare(B, NEXT_CONFIG_NAME);
        HostDatabaseBridge.Request database = HostDatabaseBridge.prepare(A, null);
        SelfProfile.refresh(true); awaitConfigEntered(config); config.allow(); awaitEntered();
        database.allow(HostDatabaseBridge.Stage.ACCOUNT); awaitMain(); Handler.drain();
        checkConfigIdentity(config); checkQueries(database, 1);
        check(!loading() && A.equals(SelfProfile.wxid()) && CONFIG_NAME.equals(SelfProfile.nickname()),
                "database/config account mismatch cannot publish mismatched account or nickname");
        check("".equals(value("confirmedAccount")) && !SelfProfile.statusAccountReady(A) && !SelfProfile.statusAccountReady(B),
                "config mismatch revokes existing database confirmation, not just new request");
        check(TextStatusBridge.refreshes == refreshes && TextStatusBridge.accountChanges == changes + 1,
                "mismatched identity invalidates status without requesting content refresh");
        configDatabaseSuccess(A, CONFIG_NAME);
        check(SelfProfile.statusAccountReady(A), "matching identity remains retryable after account mismatch");
    }

    private static void configDatabaseStale() throws Exception {
        initialize();
        configDatabaseSuccess(A, CONFIG_NAME);
        HostProfileBridge.Request config = HostProfileBridge.prepare(A, NEXT_CONFIG_NAME);
        HostDatabaseBridge.Request database = HostDatabaseBridge.prepare(B, null);
        int refreshes = TextStatusBridge.refreshes;
        SelfProfile.refresh(true); awaitConfigEntered(config); config.allow(); awaitEntered();
        database.allow(HostDatabaseBridge.Stage.ACCOUNT); awaitMain(); Handler.drain();
        checkConfigIdentity(config); checkQueries(database, 1);
        check(A.equals(SelfProfile.wxid()) && NEXT_CONFIG_NAME.equals(SelfProfile.nickname()) && !loading(),
                "Current config identity successfully displays nickname despite stale different database id2 account");
        check("".equals(value("confirmedAccount")) && !SelfProfile.statusAccountReady(A) && !SelfProfile.statusAccountReady(B),
                "Stale database/config mismatch cannot confirm either account or retain previous confirmation");
        check(TextStatusBridge.refreshes == refreshes + 1,
                "Successful config publication schedules status service to recheck the false account gate");
    }

    private static void configPreferenceMismatch() throws Exception {
        initialize();
        HostProfileBridge.Request config = HostProfileBridge.prepare(B, NEXT_CONFIG_NAME);
        runConfig(config); checkConfigIdentity(config);
        check(A.equals(SelfProfile.wxid()) && SelfProfile.nickname().isEmpty() && !loading(),
                "config-only different account cannot replace a nonempty current preference account");
        check(!SelfProfile.statusAccountReady(A) && !SelfProfile.statusAccountReady(B) && HostDatabaseBridge.queries == 0,
                "config/preference mismatch never bypasses status gate or queries unavailable database");
        check(TextStatusBridge.refreshes == 0, "rejected config identity does not refresh status service");
    }

    private static void configOldToken() throws Exception {
        initialize();
        HostProfileBridge.Request old = HostProfileBridge.prepare(A, CONFIG_NAME);
        SelfProfile.refresh(true); awaitConfigEntered(old);
        Field requestId = SelfProfile.class.getDeclaredField("requestId"); requestId.setAccessible(true);
        long oldToken = requestId.getLong(null);
        Context.preferenceAccount = B;
        HostProfileBridge.Request current = HostProfileBridge.prepare(B, NEXT_CONFIG_NAME);
        SelfProfile.refresh(true);
        check(requestId.getLong(null) > oldToken && B.equals(SelfProfile.wxid()) && loading(),
                "replacement config request owns a new token and loading before old identity returns");
        int refreshes = TextStatusBridge.refreshes, changes = TextStatusBridge.accountChanges;
        old.allow(); awaitMain(); Handler.runOne(); checkConfigIdentity(old);
        check(B.equals(SelfProfile.wxid()) && SelfProfile.nickname().isEmpty() && loading(),
                "old config identity cannot publish old nickname or clear replacement request loading");
        check(TextStatusBridge.refreshes == refreshes && TextStatusBridge.accountChanges == changes,
                "stale config token cannot refresh or invalidate current status state");
        awaitConfigEntered(current); current.allow(); awaitMain(); Handler.drain(); checkConfigIdentity(current);
        check(B.equals(SelfProfile.wxid()) && NEXT_CONFIG_NAME.equals(SelfProfile.nickname()) && !loading(),
                "fresh config identity alone publishes only replacement account nickname");
        check(!SelfProfile.statusAccountReady(A) && !SelfProfile.statusAccountReady(B) && HostDatabaseBridge.queries == 0,
                "neither old nor fresh config token can fabricate database confirmation");
    }

    private static void configMandatoryFailure() throws Exception {
        initialize();
        for (int scenario = 0; scenario < 5; scenario++) {
            configDatabaseSuccess(A, CONFIG_NAME);
            check(SelfProfile.statusAccountReady(A), "mandatory failure starts with matching config plus confirmed database");
            Throwable failure = scenario == 0 ? new IllegalStateException(PRIVATE_MESSAGE)
                    : scenario == 1 ? new PrivateThrowable() : scenario == 2 ? new AssertionError(PRIVATE_MESSAGE) : null;
            HostProfileBridge.Request config = HostProfileBridge.prepare(A, NEXT_CONFIG_NAME);
            HostDatabaseBridge.Request database = HostDatabaseBridge.prepare(scenario == 4 ? "" : A, failure);
            database.missingAccount = scenario == 3;
            int refreshes = TextStatusBridge.refreshes, changes = TextStatusBridge.accountChanges;
            SelfProfile.refresh(true); awaitConfigEntered(config); config.allow(); awaitEntered();
            database.allow(HostDatabaseBridge.Stage.ACCOUNT); awaitMain(); Handler.drain();
            checkConfigIdentity(config); checkQueries(database, 1);
            check(!loading() && "".equals(value("confirmedAccount")) && !SelfProfile.statusAccountReady(A),
                    "mandatory id2 failure/empty row revokes gate even with successful observed config identity");
            check(A.equals(SelfProfile.wxid()) && NEXT_CONFIG_NAME.equals(SelfProfile.nickname()),
                    "Successful current config nickname publishes independently from failed mandatory database confirmation");
            check(TextStatusBridge.refreshes == refreshes + 1 && TextStatusBridge.accountChanges == changes,
                    "Config-only publication may recheck false status gate but never changes stable current account");
        }
        configDatabaseSuccess(A, CONFIG_NAME);
        check(SelfProfile.statusAccountReady(A), "fresh successful database can restore confirmation after mandatory failures");
    }

    private static void configReadFailure() throws Exception {
        initialize();
        HostProfileBridge.Request config = HostProfileBridge.prepare(A, CONFIG_NAME);
        config.failure = new PrivateThrowable();
        runConfig(config);
        check(!loading() && SelfProfile.nickname().isEmpty() && !SelfProfile.statusAccountReady(A),
                "failed mandatory config reader never publishes data or confirms missing database");
        check(config.ids.equals(java.util.List.of(2)) && HostDatabaseBridge.queries == 0,
                "mandatory config failure stops before optional nickname and unavailable SQL");
        HostDatabaseBridge.ready = true;
        HostProfileBridge.Request failed = HostProfileBridge.prepare(A, NEXT_CONFIG_NAME);
        failed.failure = new PrivateThrowable();
        HostDatabaseBridge.Request database = HostDatabaseBridge.prepare(A, null);
        SelfProfile.refresh(true); awaitConfigEntered(failed); failed.allow(); awaitEntered();
        database.allow(HostDatabaseBridge.Stage.ACCOUNT); awaitMain(); Handler.drain(); checkQueries(database, 3);
        check(SelfProfile.statusAccountReady(A) && "verified nickname".equals(SelfProfile.nickname()),
                "unavailable optional config source does not block independently successful mandatory database path");
    }

    private static void configEmptyNameFallback() throws Exception {
        initialize(); HostDatabaseBridge.ready = true;
        HostProfileBridge.Request config = HostProfileBridge.prepare(A, "");
        HostDatabaseBridge.Request database = HostDatabaseBridge.prepare(A, null);
        SelfProfile.refresh(true); awaitConfigEntered(config); config.allow(); awaitEntered();
        database.allow(HostDatabaseBridge.Stage.ACCOUNT); awaitMain(); Handler.drain();
        checkConfigIdentity(config); checkQueries(database, 3);
        check("verified nickname".equals(SelfProfile.nickname()) && SelfProfile.statusAccountReady(A) && !loading(),
                "empty config nickname falls back to optional rcontact while id2 still confirms matching identity");
    }

    private static void configOptionalError() throws Exception {
        initialize(); HostDatabaseBridge.ready = true;
        HostProfileBridge.Request config = HostProfileBridge.prepare(A, CONFIG_NAME);
        HostDatabaseBridge.Request database = HostDatabaseBridge.prepare(A, null);
        database.skipNickname = true; database.avatarFailure = new PrivateThrowable();
        SelfProfile.refresh(true); awaitConfigEntered(config); config.allow(); awaitEntered();
        database.allow(HostDatabaseBridge.Stage.ACCOUNT); awaitMain(); Handler.drain();
        checkConfigIdentity(config); checkConfigQueries(database, false);
        check(CONFIG_NAME.equals(SelfProfile.nickname()) && "".equals(value("avatarUrl"))
                        && SelfProfile.statusAccountReady(A) && !loading(),
                "optional img failure keeps successful config nickname and mandatory database confirmation");
        HostProfileBridge.Request empty = HostProfileBridge.prepare(A, "");
        HostDatabaseBridge.Request unavailable = HostDatabaseBridge.prepare(A, null);
        unavailable.nicknameFailure = new PrivateThrowable(); unavailable.avatarFailure = new AssertionError(PRIVATE_MESSAGE);
        SelfProfile.refresh(true); awaitConfigEntered(empty); empty.allow(); awaitEntered();
        unavailable.allow(HostDatabaseBridge.Stage.ACCOUNT); awaitMain(); Handler.drain(); checkQueries(unavailable, 3);
        check(SelfProfile.statusAccountReady(A) && !loading() && "".equals(value("avatarUrl")),
                "both optional SQL errors still degrade without revoking required matching id2 confirmation");
    }

    private static void configLostDatabase() throws Exception {
        initialize(); configDatabaseSuccess(A, CONFIG_NAME);
        HostProfileBridge.Request config = HostProfileBridge.prepare(A, NEXT_CONFIG_NAME);
        HostDatabaseBridge.Request database = HostDatabaseBridge.prepare(A, null); database.skipNickname = true;
        SelfProfile.refresh(true); awaitConfigEntered(config); config.allow(); awaitEntered();
        database.allow(HostDatabaseBridge.Stage.ACCOUNT); awaitMain();
        HostDatabaseBridge.ready = false; Handler.drain(); checkConfigQueries(database, false);
        check(!loading() && !SelfProfile.statusAccountReady(A) && "".equals(value("confirmedAccount")),
                "database loss before MAIN completion revokes confirmation despite matching config identity");
        check(CONFIG_NAME.equals(SelfProfile.nickname()), "revoked pending database result cannot publish new config nickname");
        HostProfileBridge.Request retry = HostProfileBridge.prepare(A, NEXT_CONFIG_NAME);
        runConfig(retry);
        check(NEXT_CONFIG_NAME.equals(SelfProfile.nickname()) && !SelfProfile.statusAccountReady(A) && !loading(),
                "subsequent config-only retry can refresh nickname but still cannot restore status gate");
    }

    private static void profileListeners() throws Exception {
        initialize();
        java.util.concurrent.atomic.AtomicInteger healthy = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger failed = new java.util.concurrent.atomic.AtomicInteger();
        Runnable listener = healthy::incrementAndGet;
        Runnable bad = () -> { failed.incrementAndGet(); HostTargetResolver.<RuntimeException>raise(new PrivateThrowable()); };
        SelfProfile.subscribe(listener); SelfProfile.subscribe(bad);
        check(healthy.get() == 1 && failed.get() == 1, "immediate profile listeners isolate direct Throwable");
        @SuppressWarnings("unchecked") java.util.List<java.lang.ref.WeakReference<Runnable>> listeners =
                (java.util.List<java.lang.ref.WeakReference<Runnable>>) value("LISTENERS");
        listeners.add(new java.lang.ref.WeakReference<>(null));
        HostProfileBridge.Request config = HostProfileBridge.prepare(A, CONFIG_NAME);
        runConfig(config);
        check(healthy.get() == 2 && failed.get() == 2 && listeners.size() == 2,
                "failed listener and dead weak reference do not block successful config profile publication");
        check(CONFIG_NAME.equals(SelfProfile.nickname()) && !SelfProfile.statusAccountReady(A),
                "listener failure cannot hide successful config nickname or change status gate");
        SelfProfile.unsubscribe(bad);
        HostProfileBridge.Request next = HostProfileBridge.prepare(A, NEXT_CONFIG_NAME);
        runConfig(next);
        check(healthy.get() == 3 && failed.get() == 2, "unsubscribed failed listener no longer runs while healthy listener persists");
    }

    private static void profileSummaryStatus() throws Exception {
        initialize();
        check(SelfProfile.summary().contains("配置=UNINITIALIZED") && SelfProfile.summary().contains("状态门禁=false"),
                "real summary exposes default config bridge state, not an unconditionally empty fake");
        HostProfileBridge.Request config = HostProfileBridge.prepare(A, CONFIG_NAME); runConfig(config);
        String summary = SelfProfile.summary();
        check(summary.contains("配置=IDENTITY_READY") && summary.contains("账号=true") && summary.contains("昵称=true"),
                "summary has real successful config state and profile-presence booleans");
        check(!summary.contains(A) && !summary.contains(CONFIG_NAME), "profile summary does not print account or nickname values");
        TextStatusBridge.value.kind = TextStatusBridge.Kind.READY; TextStatusBridge.value.description = STATUS_DESCRIPTION;
        check(STATUS_DESCRIPTION.equals(SelfProfile.status()), "SelfProfile status consumes actual nonempty READY snapshot in test seam");
        for (TextStatusBridge.Kind kind : new TextStatusBridge.Kind[] {
                TextStatusBridge.Kind.NONE, TextStatusBridge.Kind.LOADING, TextStatusBridge.Kind.ERROR}) {
            TextStatusBridge.value.kind = kind;
            check(SelfProfile.status().isEmpty(), "non-READY status snapshot is not presented as text");
        }
        HostDatabaseBridge.ready = true;
        configDatabaseSuccess(A, CONFIG_NAME);
        check(SelfProfile.summary().contains("状态门禁=true"), "summary reports true only after matching database id2 confirmation");
    }

    private static void forceWhileLoading() throws Exception {
        initialize();
        HostProfileBridge.Request first = HostProfileBridge.prepare(A, CONFIG_NAME);
        SelfProfile.refresh(true); awaitConfigEntered(first);
        long token = ((Long) value("requestId")).longValue();
        HostProfileBridge.Request next = HostProfileBridge.prepare(A, NEXT_CONFIG_NAME);
        for (int i = 0; i < 4; i++) SelfProfile.refresh(true);
        check(loading() && ((Long) value("requestId")).longValue() == token && HostProfileBridge.reads == 1,
                "force notifications during loading must coalesce without concurrent replacement reads");
        first.allow(); awaitMain(); Handler.drain(); checkConfigIdentity(first);
        boolean followed = next.entered.await(2, TimeUnit.SECONDS);
        if (!followed) System.err.println("FAIL profile/force-while-loading: loading dropped the forced follow-up");
        check(followed, "force while loading must schedule one follow-up after completion");
        check(loading() && HostProfileBridge.reads == 2, "follow-up owns loading and executes on the real IO executor");
        next.allow(); awaitMain(); Handler.drain(); checkConfigIdentity(next);
        check(NEXT_CONFIG_NAME.equals(SelfProfile.nickname()) && !loading() && HostProfileBridge.reads == 2,
                "coalesced force publishes the fresh nickname with exactly one follow-up");
        check(!SelfProfile.statusAccountReady(A) && HostDatabaseBridge.queries == 0,
                "config follow-up never fabricates database confirmation or SQL");
    }

    private static void forceFollowupBounded() throws Exception {
        initialize();
        HostProfileBridge.Request first = HostProfileBridge.prepare(A, CONFIG_NAME);
        SelfProfile.refresh(true); awaitConfigEntered(first);
        HostProfileBridge.Request next = HostProfileBridge.prepare(A, NEXT_CONFIG_NAME);
        for (int i = 0; i < 8; i++) {
            HostProfileBridge.publish(); HostDatabaseBridge.publishReady(); SelfProfile.refresh(false);
        }
        first.allow(); awaitMain(); Handler.drain(); checkConfigIdentity(first);
        awaitConfigEntered(next);
        check(HostProfileBridge.reads == 2 && loading(), "many ready signals produce exactly one active trailing read");
        long token = ((Long) value("requestId")).longValue();
        HostProfileBridge.Request unexpected = HostProfileBridge.prepare(A, "must-not-be-read");
        for (int i = 0; i < 8; i++) { HostProfileBridge.publish(); HostDatabaseBridge.publishReady(); }
        next.allow(); awaitMain(); Handler.drain(); checkConfigIdentity(next); awaitIoIdle();
        check(unexpected.ids.isEmpty() && HostProfileBridge.reads == 2 && !loading(),
                "ready signals within a follow-up cannot turn it into an unbounded request loop");
        check(((Long) value("requestId")).longValue() == token && !(Boolean) value("refreshPending"),
                "bounded follow-up clears dirty state without creating another generation");
        check(NEXT_CONFIG_NAME.equals(SelfProfile.nickname()) && !SelfProfile.statusAccountReady(A),
                "bounded config-only refresh publishes the read data without changing the safety gate");
    }

    private static void forceFailureRecovery() throws Exception {
        initialize();
        HostProfileBridge.Request failed = HostProfileBridge.prepare(A, CONFIG_NAME);
        failed.failure = new PrivateThrowable();
        SelfProfile.refresh(true); awaitConfigEntered(failed);
        HostProfileBridge.Request next = HostProfileBridge.prepare(A, NEXT_CONFIG_NAME);
        SelfProfile.refresh(true);
        failed.allow(); awaitMain(); Handler.drain();
        check(failed.ids.equals(java.util.List.of(2)), "mandatory failure still stops before optional name");
        awaitConfigEntered(next);
        check(loading() && !SelfProfile.statusAccountReady(A), "failed primary request transfers loading only to the queued retry");
        next.allow(); awaitMain(); Handler.drain(); checkConfigIdentity(next); awaitIoIdle();
        check(NEXT_CONFIG_NAME.equals(SelfProfile.nickname()) && !loading() && HostProfileBridge.reads == 2,
                "one pending force recovers from the failed first read without another user trigger");
        check(!SelfProfile.statusAccountReady(A) && HostDatabaseBridge.queries == 0,
                "failure recovery never confirms an unavailable database account");
    }

    private static void forceGenerationIsolation() throws Exception {
        initialize();
        HostProfileBridge.Request old = HostProfileBridge.prepare(A, CONFIG_NAME);
        SelfProfile.refresh(true); awaitConfigEntered(old); SelfProfile.refresh(true);
        Context.preferenceAccount = B;
        HostProfileBridge.Request current = HostProfileBridge.prepare(B, NEXT_CONFIG_NAME);
        SelfProfile.refresh(true);
        long token = ((Long) value("requestId")).longValue();
        check(B.equals(SelfProfile.wxid()) && loading() && !(Boolean) value("refreshPending"),
                "preference switch discards the old generation's pending force before starting B");
        SelfProfile.refresh(true);
        check((Boolean) value("refreshPending"), "B owns a distinct pending refresh");
        old.allow(); awaitMain(); Handler.runOne(); checkConfigIdentity(old);
        check(loading() && (Boolean) value("refreshPending") && ((Long) value("requestId")).longValue() == token,
                "stale A completion cannot clear B loading, pending force, or generation");
        awaitConfigEntered(current);
        HostProfileBridge.Request tail = HostProfileBridge.prepare(B, "new-generation-tail");
        current.allow(); awaitMain(); Handler.drain(); checkConfigIdentity(current);
        awaitConfigEntered(tail); tail.allow(); awaitMain(); Handler.drain(); checkConfigIdentity(tail); awaitIoIdle();
        check("new-generation-tail".equals(SelfProfile.nickname()) && HostProfileBridge.reads == 3 && !loading(),
                "only B's force causes one B follow-up, never the invalidated A dirty request");
        check(!SelfProfile.statusAccountReady(A) && !SelfProfile.statusAccountReady(B) && HostDatabaseBridge.queries == 0,
                "generation isolation cannot transfer or fabricate confirmation");
    }

    private static void forceOldFailureIsolation() throws Exception {
        initialize();
        HostProfileBridge.Request old = HostProfileBridge.prepare(A, CONFIG_NAME); old.failure = new PrivateThrowable();
        SelfProfile.refresh(true); awaitConfigEntered(old); SelfProfile.refresh(true);
        Context.preferenceAccount = B;
        HostProfileBridge.Request current = HostProfileBridge.prepare(B, NEXT_CONFIG_NAME);
        SelfProfile.refresh(true); SelfProfile.refresh(true);
        long token = ((Long) value("requestId")).longValue();
        int changes = TextStatusBridge.accountChanges;
        old.allow(); awaitMain(); Handler.runOne();
        check(old.ids.equals(java.util.List.of(2)) && loading() && (Boolean) value("refreshPending"),
                "old mandatory failure cannot clear current-generation loading or pending force");
        check(((Long) value("requestId")).longValue() == token && TextStatusBridge.accountChanges == changes,
                "old failure cannot change current-generation account state or token");
        awaitConfigEntered(current);
        HostProfileBridge.Request tail = HostProfileBridge.prepare(B, "failure-isolation-tail");
        current.allow(); awaitMain(); Handler.drain(); checkConfigIdentity(current);
        awaitConfigEntered(tail); tail.allow(); awaitMain(); Handler.drain(); checkConfigIdentity(tail); awaitIoIdle();
        check("failure-isolation-tail".equals(SelfProfile.nickname()) && !loading() && HostProfileBridge.reads == 3,
                "current generation completes its one follow-up after old failure cleanup");
    }

    private static void forceMainRejectionIsolation() throws Exception {
        initialize();
        HostProfileBridge.Request old = HostProfileBridge.prepare(A, CONFIG_NAME);
        SelfProfile.refresh(true); awaitConfigEntered(old); SelfProfile.refresh(true);
        Context.preferenceAccount = B;
        HostProfileBridge.Request current = HostProfileBridge.prepare(B, NEXT_CONFIG_NAME);
        SelfProfile.refresh(true); SelfProfile.refresh(true);
        long token = ((Long) value("requestId")).longValue();
        java.util.concurrent.CountDownLatch rejecting = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch releaseReject = new java.util.concurrent.CountDownLatch(1);
        HostStageLog.afterWrite = stage -> {
            if (!stage.equals("profile stage=reject main=false")) return;
            rejecting.countDown();
            try {
                if (!releaseReject.await(5, TimeUnit.SECONDS)) throw new AssertionError("Rejected MAIN cleanup was not released");
            } catch (InterruptedException error) { throw new AssertionError(error); }
        };
        try {
            Handler.rejectPosts = true; old.allow();
            check(rejecting.await(5, TimeUnit.SECONDS), "old completion reaches rejected MAIN cleanup on real IO");
            check(loading() && (Boolean) value("refreshPending"), "B still owns loading before old rejected post cleanup");
        } finally {
            Handler.rejectPosts = false; HostStageLog.afterWrite = null; releaseReject.countDown();
        }
        awaitConfigEntered(current);
        check(loading() && (Boolean) value("refreshPending") && ((Long) value("requestId")).longValue() == token,
                "old rejected MAIN completion cannot clear replacement loading, pending force or token");
        HostProfileBridge.Request tail = HostProfileBridge.prepare(B, "main-rejection-tail");
        current.allow(); awaitMain(); Handler.drain(); checkConfigIdentity(current);
        awaitConfigEntered(tail); tail.allow(); awaitMain(); Handler.drain(); checkConfigIdentity(tail); awaitIoIdle();
        check("main-rejection-tail".equals(SelfProfile.nickname()) && !loading() && HostProfileBridge.reads == 3,
                "replacement request and one follow-up survive old MAIN rejection");
    }

    private static void forceMainThrowIsolation() throws Exception {
        initialize();
        HostProfileBridge.Request old = HostProfileBridge.prepare(A, CONFIG_NAME);
        SelfProfile.refresh(true); awaitConfigEntered(old);
        HostProfileBridge.Request[] current = new HostProfileBridge.Request[1];
        TextStatusBridge.beforeRefresh = () -> {
            TextStatusBridge.beforeRefresh = null;
            Context.preferenceAccount = B;
            current[0] = HostProfileBridge.prepare(B, NEXT_CONFIG_NAME);
            SelfProfile.refresh(true); SelfProfile.refresh(true);
            HostTargetResolver.<RuntimeException>raise(new PrivateThrowable());
        };
        old.allow(); awaitMain(); Handler.runOne(); checkConfigIdentity(old);
        check(B.equals(SelfProfile.wxid()) && loading() && (Boolean) value("refreshPending"),
                "old MAIN callback's Throwable cannot clear reentrant replacement request state");
        check(SelfProfile.nickname().isEmpty() && !SelfProfile.statusAccountReady(A) && !SelfProfile.statusAccountReady(B),
                "MAIN callback failure cannot restore old profile data or transfer confirmation");
        awaitConfigEntered(current[0]);
        HostProfileBridge.Request tail = HostProfileBridge.prepare(B, "main-throw-tail");
        current[0].allow(); awaitMain(); Handler.drain(); checkConfigIdentity(current[0]);
        awaitConfigEntered(tail); tail.allow(); awaitMain(); Handler.drain(); checkConfigIdentity(tail); awaitIoIdle();
        check("main-throw-tail".equals(SelfProfile.nickname()) && !loading() && HostProfileBridge.reads == 3,
                "new generation retains its one follow-up after an old guarded MAIN exception");
    }

    private static void selfReadReadySuppressed() throws Exception {
        initialize();
        HostProfileBridge.Request config = HostProfileBridge.prepare(A, CONFIG_NAME);
        config.captureDatabase = true; config.signalDuringRead = true;
        HostDatabaseBridge.Request database = HostDatabaseBridge.prepare(A, null); database.skipNickname = true;
        SelfProfile.refresh(true); awaitConfigEntered(config); config.allow(); awaitEntered();
        check(Handler.pending() == 0 && !(Boolean) value("refreshPending"),
                "synchronous ready callbacks from module self-reading must not enqueue or mark another read");
        database.allow(HostDatabaseBridge.Stage.ACCOUNT); awaitMain(); Handler.drain(); awaitIoIdle();
        checkConfigIdentity(config); checkConfigQueries(database, false);
        check(HostProfileBridge.reads == 1 && !loading() && SelfProfile.statusAccountReady(A),
                "same request consumes the captured ready database and terminates after one profile read");
        @SuppressWarnings("unchecked") ThreadLocal<Boolean> reading = (ThreadLocal<Boolean>) value("READING");
        Field io = SelfProfile.class.getDeclaredField("IO"); io.setAccessible(true);
        check(((java.util.concurrent.ExecutorService) io.get(null)).submit(() -> reading.get() == null).get(5, TimeUnit.SECONDS),
                "profile IO request removes its thread-local self-reading guard after completion");
    }

    private static void listenerForceBounded() throws Exception {
        initialize();
        HostProfileBridge.Request first = HostProfileBridge.prepare(A, CONFIG_NAME);
        Runnable listener = () -> SelfProfile.refresh(true);
        SelfProfile.subscribe(listener); awaitConfigEntered(first);
        HostProfileBridge.Request next = HostProfileBridge.prepare(A, NEXT_CONFIG_NAME);
        first.allow(); awaitMain(); Handler.drain(); checkConfigIdentity(first);
        awaitConfigEntered(next);
        HostProfileBridge.Request unexpected = HostProfileBridge.prepare(A, "recursive-listener-read");
        next.allow(); awaitMain(); Handler.drain(); checkConfigIdentity(next); awaitIoIdle();
        SelfProfile.unsubscribe(listener);
        check(HostProfileBridge.reads == 2 && unexpected.ids.isEmpty() && !loading(),
                "publication listener force is merged while loading and cannot recursively sustain follow-ups");
        check(NEXT_CONFIG_NAME.equals(SelfProfile.nickname()), "listener recursion cannot replace the completed fresh profile");
    }

    private static void configEmptyNameErrorRetained() throws Exception {
        initialize(); configDatabaseSuccess(A, CONFIG_NAME);
        HostProfileBridge.Request config = HostProfileBridge.prepare(A, "");
        HostDatabaseBridge.Request database = HostDatabaseBridge.prepare(A, null);
        database.nicknameFailure = new PrivateThrowable();
        SelfProfile.refresh(true); awaitConfigEntered(config); config.allow(); awaitEntered();
        database.allow(HostDatabaseBridge.Stage.ACCOUNT); awaitMain(); Handler.drain();
        checkConfigIdentity(config); checkQueries(database, 3);
        if (!CONFIG_NAME.equals(SelfProfile.nickname()))
            System.err.println("FAIL profile/config-empty-name-error-retained: fallback failure cleared the known nickname");
        check(CONFIG_NAME.equals(SelfProfile.nickname()),
                "empty config name plus failed nickname fallback must retain the same account's known nickname");
        check(SelfProfile.statusAccountReady(A) && !loading(), "optional nickname failure preserves required confirmation");
    }

    private static void configNameUnknownEmpty() throws Exception {
        initialize();
        runConfig(HostProfileBridge.prepare(A, CONFIG_NAME));
        runConfig(HostProfileBridge.prepare(A, null));
        check(CONFIG_NAME.equals(SelfProfile.nickname()), "unknown optional config nickname retains same-account known value");
        runConfig(HostProfileBridge.prepare(A, ""));
        check(SelfProfile.nickname().isEmpty(), "confirmed-empty optional config nickname is not treated as a read error");
        runConfig(HostProfileBridge.prepare(A, CONFIG_NAME));
        HostDatabaseBridge.ready = true;
        HostProfileBridge.Request unknown = HostProfileBridge.prepare(A, null);
        HostDatabaseBridge.Request failed = HostDatabaseBridge.prepare(A, null);
        failed.nicknameFailure = new PrivateThrowable();
        SelfProfile.refresh(true); awaitConfigEntered(unknown); unknown.allow(); awaitEntered();
        failed.allow(HostDatabaseBridge.Stage.ACCOUNT); awaitMain(); Handler.drain(); checkQueries(failed, 3);
        check(CONFIG_NAME.equals(SelfProfile.nickname()) && SelfProfile.statusAccountReady(A),
                "unknown config name and fallback error preserve nickname without weakening database confirmation");
        HostProfileBridge.Request empty = HostProfileBridge.prepare(A, "");
        HostDatabaseBridge.Request confirmedEmpty = HostDatabaseBridge.prepare(A, null); confirmedEmpty.missingNickname = true;
        SelfProfile.refresh(true); awaitConfigEntered(empty); empty.allow(); awaitEntered();
        confirmedEmpty.allow(HostDatabaseBridge.Stage.ACCOUNT); awaitMain(); Handler.drain(); checkQueries(confirmedEmpty, 3);
        check(SelfProfile.nickname().isEmpty() && SelfProfile.statusAccountReady(A),
                "successful missing nickname row is confirmed empty and clears the previous display value");
        Context.preferenceAccount = B; HostDatabaseBridge.ready = false;
        runConfig(HostProfileBridge.prepare(B, null));
        check(B.equals(SelfProfile.wxid()) && SelfProfile.nickname().isEmpty() && !SelfProfile.statusAccountReady(B),
                "a new account's unknown nickname cannot inherit previous account data");
    }

    private static void profileStageBooleans() throws Exception {
        initialize(); HostStageLog.stages.clear(); XposedBridge.logs.clear();
        HostProfileBridge.Request config = HostProfileBridge.prepare(A, CONFIG_NAME);
        runConfig(config);
        checkStage("stage=request force=true followup=false config=true database=false");
        checkStage("stage=read success=true identity=true confirmed=false nameKnown=true nickname=true avatarUrlKnown=false avatarUrl=false");
        checkStage("stage=success changed=true confirmed=false nickname=true avatarUrl=false");
        checkStage("stage=publish account=true nickname=true avatar=false confirmed=false");
        int publications = stageCount("stage=publish");
        runConfig(HostProfileBridge.prepare(B, NEXT_CONFIG_NAME));
        checkStage("stage=reject current=true preferences=false database=true");
        check(stageCount("stage=publish") == publications && CONFIG_NAME.equals(SelfProfile.nickname()),
                "preference rejection has a distinct safe marker and never publishes rejected profile data");
        HostProfileBridge.Request failed = HostProfileBridge.prepare(A, CONFIG_NAME); failed.failure = new PrivateThrowable();
        runConfig(failed); checkStage("stage=read success=false current=true");
        for (String diagnostic : HostStageLog.stages) {
            if (!diagnostic.startsWith("profile stage=")) continue;
            String[] tokens = diagnostic.split(" ");
            check(tokens.length >= 3 && tokens[0].equals("profile")
                    && tokens[1].matches("stage=(request|deferred|wait|read|reject|success|publish)"),
                    "profile stage identifiers are from a fixed finite set");
            for (int i = 2; i < tokens.length; i++)
                check(tokens[i].matches("[A-Za-z]+=(true|false)"), "profile stage payloads contain booleans only");
            check(XposedBridge.logs.contains("WEX: 本人资料: " + diagnostic.substring("profile ".length())),
                    "safe stage uses the exact existing profile Xposed prefix as well as HostStageLog");
        }
    }

    private static void checkStage(String expected) {
        check(HostStageLog.stages.contains("profile " + expected), "required safe profile stage is emitted: " + expected);
    }
    private static int stageCount(String prefix) {
        int count = 0;
        for (String value : HostStageLog.stages) if (value.startsWith("profile " + prefix)) count++;
        return count;
    }
    private static void awaitIoIdle() throws Exception {
        Field io = SelfProfile.class.getDeclaredField("IO"); io.setAccessible(true);
        ((java.util.concurrent.ExecutorService) io.get(null)).submit(() -> { }).get(5, TimeUnit.SECONDS);
        Handler.drain();
    }

    private static void configDatabaseSuccess(String account, String name) throws Exception {
        HostDatabaseBridge.ready = true;
        HostProfileBridge.Request config = HostProfileBridge.prepare(account, name);
        HostDatabaseBridge.Request database = HostDatabaseBridge.prepare(account, null);
        database.skipNickname = !name.isEmpty();
        SelfProfile.refresh(true); awaitConfigEntered(config); config.allow(); awaitEntered();
        database.allow(HostDatabaseBridge.Stage.ACCOUNT); awaitMain(); Handler.drain();
        checkConfigIdentity(config); checkConfigQueries(database, name.isEmpty());
    }
    private static void runConfig(HostProfileBridge.Request request) throws Exception {
        SelfProfile.refresh(true); awaitConfigEntered(request); request.allow(); awaitMain(); Handler.drain();
    }
    private static void awaitConfigEntered(HostProfileBridge.Request request) throws Exception {
        check(request.entered.await(5, TimeUnit.SECONDS), "real IO executor must enter the configured observed identity reader");
    }
    private static void checkConfigIdentity(HostProfileBridge.Request request) {
        check(request.ids.equals(java.util.List.of(2, 4)), "config identity reads exactly account id2 then optional nickname id4");
    }
    private static void checkConfigQueries(HostDatabaseBridge.Request request, boolean nickname) {
        HostDatabaseBridge.Stage[] stages = nickname ? new HostDatabaseBridge.Stage[] {
                HostDatabaseBridge.Stage.ACCOUNT, HostDatabaseBridge.Stage.NICKNAME, HostDatabaseBridge.Stage.AVATAR}
                : new HostDatabaseBridge.Stage[] {HostDatabaseBridge.Stage.ACCOUNT, HostDatabaseBridge.Stage.AVATAR};
        check(request.calls.size() == stages.length, "matching config request issues only mandatory account and required optional stages");
        for (int i = 0; i < stages.length; i++) {
            HostDatabaseBridge.Call call = request.calls.get(i);
            check(call.request == request && call.stage == stages[i], "config/database SQL stage order is exact");
            check(call.stage == HostDatabaseBridge.Stage.ACCOUNT ? call.args == null
                            : Arrays.equals(call.args, new String[]{request.account}),
                    "config-backed optional query binds only confirmed account; id2 has null arguments");
        }
    }
    private static final class PrivateThrowable extends Throwable {
        PrivateThrowable() { super(PRIVATE_MESSAGE); }
        @Override public String getMessage() { messageReads.incrementAndGet(); return PRIVATE_MESSAGE; }
    }
    private static void checkPrivate(String text) {
        check(!text.contains(A) && !text.contains(B) && !text.contains(CONFIG_NAME) && !text.contains(NEXT_CONFIG_NAME)
                        && !text.contains(STATUS_DESCRIPTION) && !text.contains(PRIVATE_MESSAGE),
                "logs and diagnostic stages must contain no profile/account/status values or host message");
        check(messageReads.get() == 0, "failure logging never reads host exception message");
    }

    private static void querySuccess(String account) throws Exception {
        HostDatabaseBridge.Request request = HostDatabaseBridge.prepare(account, null);
        runRequest(request); checkQueries(request, 3);
    }
    private static void runRequest(HostDatabaseBridge.Request request) throws Exception {
        SelfProfile.refresh(true); awaitEntered(); request.allow(HostDatabaseBridge.Stage.ACCOUNT); awaitMain(); Handler.drain();
    }
    private static void checkQueries(HostDatabaseBridge.Request request, int count) {
        check(request.calls.size() == count, "request must issue exactly " + count + " stage-specific queries");
        String[] sql = {HostDatabaseBridge.ACCOUNT_SQL, HostDatabaseBridge.NICKNAME_SQL, HostDatabaseBridge.AVATAR_SQL};
        for (int i = 0; i < count; i++) {
            HostDatabaseBridge.Call call = request.calls.get(i);
            check(call.request == request && call.stage.ordinal() == i && sql[i].equals(call.sql), "SQL stage " + i + " is recorded in required order");
            check(i == 0 ? call.args == null : Arrays.equals(call.args, new String[]{request.account}),
                    "optional SQL binds only the queried account; required SQL has null params");
        }
    }
    private static Object value(String name) throws Exception {
        Field field = SelfProfile.class.getDeclaredField(name); field.setAccessible(true); return field.get(null);
    }
    private static void seedLoadedProfile(String name, String url) throws Exception {
        for (String fieldName : new String[] {"nickname", "avatarUrl", "imageLoadedFor", "avatar", "lastImageAttempt"}) {
            Field field = SelfProfile.class.getDeclaredField(fieldName); field.setAccessible(true);
            if (fieldName.equals("nickname")) field.set(null, name);
            else if (fieldName.equals("avatarUrl") || fieldName.equals("imageLoadedFor")) field.set(null, url);
            else if (fieldName.equals("avatar")) field.set(null, new Bitmap());
            else field.setLong(null, SystemClock.uptimeMillis());
        }
    }
    private static void awaitEntered() throws Exception {
        check(HostDatabaseBridge.entered.await(5, TimeUnit.SECONDS), "real IO executor must enter queued database query");
    }
    private static void complete() throws Exception {
        HostDatabaseBridge.release.countDown(); awaitMain(); Handler.drain();
    }
    private static void awaitMain() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (Handler.pending() == 0 && uncaught.get() == null && System.nanoTime() < deadline) Thread.sleep(2);
        if (uncaught.get() != null) throw new AssertionError("Throwable escaped real SelfProfile IO executor", uncaught.get());
        check(Handler.pending() > 0, "query must enqueue MAIN success/failure cleanup");
    }
    private static boolean loading() throws Exception {
        Field field = SelfProfile.class.getDeclaredField("loading"); field.setAccessible(true); return field.getBoolean(null);
    }
    private static void check(boolean condition, String message) {
        assertions++; if (!condition) throw new AssertionError(message);
    }
}
