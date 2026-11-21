package wex.ui.core.feature;

import android.os.Looper;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Three exact SQL stages, with per-request outcomes so queued accounts cannot share rows. */
final class HostDatabaseBridge {
    static final String ACCOUNT_SQL = "SELECT value AS wxid FROM userinfo WHERE id=2 LIMIT 1";
    static final String NICKNAME_SQL = "SELECT nickname FROM rcontact WHERE username=? LIMIT 1";
    static final String AVATAR_SQL = "SELECT reserved2 AS avatarUrl FROM img_flag WHERE username=? LIMIT 1";
    enum Stage { ACCOUNT, NICKNAME, AVATAR }

    static volatile boolean ready;
    static volatile int queries, accountQueries, nicknameQueries, avatarQueries;
    static volatile CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
    static final List<Call> calls = new CopyOnWriteArrayList<>();
    static final List<String> violations = new CopyOnWriteArrayList<>();
    static final List<Runnable> readyListeners = new CopyOnWriteArrayList<>();
    static int subscriptions;
    private static volatile Request prepared;
    private static final ThreadLocal<Request> ACTIVE = new ThreadLocal<>();

    static boolean currentCapture() { return true; }
    static boolean readCaptureCurrent() {
        if (Looper.myLooper() == Looper.getMainLooper()) throw invalid("Capture open check executed on MAIN");
        return currentCapture(); // This suite controls confirmed DB availability separately via ready.
    }
    static long captureVersion() { return 0L; }
    static void requestProfileCapture() { }
    static boolean isReady() { return ready; }
    static void subscribeReady(Runnable listener) {
        if (listener == null) return;
        subscriptions++;
        readyListeners.add(listener);
        if (ready) notifyListener(listener);
    }
    static void publishReady() { for (Runnable listener : readyListeners) notifyListener(listener); }
    private static void notifyListener(Runnable listener) {
        try { listener.run(); } catch (Throwable ignored) { }
    }
    static Request prepare(String account, Throwable error) {
        Request request = new Request(account, error);
        prepared = request;
        entered = request.entered[Stage.ACCOUNT.ordinal()];
        release = request.release[Stage.ACCOUNT.ordinal()];
        return request;
    }

    static List<Map<String, Object>> query(String sql, String[] args) throws Exception {
        if (Looper.myLooper() == Looper.getMainLooper()) throw invalid("Profile SQL executed on MAIN");
        Stage stage;
        if (ACCOUNT_SQL.equals(sql)) stage = Stage.ACCOUNT;
        else if (NICKNAME_SQL.equals(sql)) stage = Stage.NICKNAME;
        else if (AVATAR_SQL.equals(sql)) stage = Stage.AVATAR;
        else throw invalid("Unexpected profile SQL: " + sql);
        Request request = stage == Stage.ACCOUNT ? prepared : ACTIVE.get();
        if (request == null) throw invalid("No prepared request for " + stage);
        if (stage == Stage.ACCOUNT) ACTIVE.set(request);
        int position = request.calls.size();
        Call call = new Call(request, stage, sql, args);
        request.calls.add(call); calls.add(call);
        queries++;
        switch (stage) {
            case ACCOUNT: accountQueries++; break;
            case NICKNAME: nicknameQueries++; break;
            case AVATAR: avatarQueries++; break;
        }
        int expected = stage.ordinal() - (request.skipNickname && stage == Stage.AVATAR ? 1 : 0);
        if (position != expected || request.skipNickname && stage == Stage.NICKNAME)
            throw invalid("Unexpected query order for " + stage);
        if (stage == Stage.ACCOUNT ? args != null : !Arrays.equals(args, new String[]{request.account}))
            throw invalid("Incorrect bound parameters for " + stage);
        request.entered[stage.ordinal()].countDown();
        if (!request.release[stage.ordinal()].await(5, TimeUnit.SECONDS))
            throw invalid("Test did not release " + stage + " query");
        Throwable failure;
        String key, value;
        boolean missing;
        switch (stage) {
            case ACCOUNT:
                failure = request.accountFailure; key = "wxid"; value = request.account; missing = request.missingAccount;
                break;
            case NICKNAME:
                failure = request.nicknameFailure; key = "nickname"; value = request.nickname; missing = request.missingNickname;
                break;
            default:
                failure = request.avatarFailure; key = "avatarUrl"; value = request.avatarUrl; missing = request.missingAvatar;
        }
        if (failure != null) HostTargetResolver.<RuntimeException>raise(failure);
        if (missing) return Collections.emptyList();
        Map<String, Object> row = new HashMap<>(); row.put(key, value);
        return Collections.singletonList(row);
    }

    static final class Request {
        final String account;
        final Throwable accountFailure;
        String nickname = "verified nickname", avatarUrl = "";
        Throwable nicknameFailure, avatarFailure;
        boolean missingAccount, missingNickname, missingAvatar, skipNickname;
        final List<Call> calls = new CopyOnWriteArrayList<>();
        final CountDownLatch[] entered = {new CountDownLatch(1), new CountDownLatch(1), new CountDownLatch(1)};
        final CountDownLatch[] release = {new CountDownLatch(1), new CountDownLatch(0), new CountDownLatch(0)};
        Request(String account, Throwable failure) { this.account = account; this.accountFailure = failure; }
        void block(Stage stage) { release[stage.ordinal()] = new CountDownLatch(1); }
        void allow(Stage stage) { release[stage.ordinal()].countDown(); }
    }
    static final class Call {
        final Request request;
        final Stage stage;
        final String sql;
        final String[] args;
        Call(Request request, Stage stage, String sql, String[] args) {
            this.request = request; this.stage = stage; this.sql = sql; this.args = args == null ? null : args.clone();
        }
    }
    private static AssertionError invalid(String message) {
        violations.add(message); return new AssertionError(message);
    }
}
