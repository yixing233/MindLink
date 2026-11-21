package wex.ui.core.feature;

import android.os.Looper;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Unobserved by default so existing database-only profile cases keep their contract. */
final class HostProfileBridge {
    static final int WXID = 2, NAME = 4;
    static final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    static final List<String> violations = new CopyOnWriteArrayList<>();
    static volatile boolean observed;
    static volatile String state = "UNINITIALIZED";
    static volatile int reads, subscriptions;
    private static volatile Request prepared;

    static final class Identity {
        final String account, nickname;
        Identity(String account, String nickname) { this.account = account; this.nickname = nickname; }
    }
    static final class Request {
        final String account, nickname;
        final List<Integer> ids = new CopyOnWriteArrayList<>();
        final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        Throwable failure;
        boolean captureDatabase, signalDuringRead;
        Request(String account, String nickname) { this.account = account; this.nickname = nickname; }
        void allow() { release.countDown(); }
    }

    static boolean observed() { return observed; }
    static String status() { return state; }
    static void subscribe(Runnable listener) {
        if (listener == null) return;
        subscriptions++;
        listeners.add(listener);
        if (observed()) notifyListener(listener);
    }
    static Request prepare(String account, String nickname) {
        Request request = new Request(account, nickname);
        prepared = request;
        observed = true;
        state = "OBSERVED";
        return request;
    }
    static void publish() { for (Runnable listener : listeners) notifyListener(listener); }
    static Identity readIdentity() throws Exception {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            violations.add("Config identity read executed on MAIN");
            throw new AssertionError("Config identity must be read on IO");
        }
        Request request = prepared;
        if (!observed || request == null) throw new IllegalStateException("ConfigStorage not observed");
        reads++;
        request.entered.countDown();
        if (!request.release.await(5, TimeUnit.SECONDS)) throw new AssertionError("Config read was not released");
        request.ids.add(WXID);
        if (request.signalDuringRead) { publish(); HostDatabaseBridge.publishReady(); }
        if (request.failure != null) HostTargetResolver.<RuntimeException>raise(request.failure);
        if (request.account == null || request.account.trim().isEmpty())
            throw new IllegalStateException("Config account unavailable");
        request.ids.add(NAME);
        state = "IDENTITY_READY";
        if (request.captureDatabase) HostDatabaseBridge.ready = true;
        return new Identity(request.account, request.nickname);
    }
    private static void notifyListener(Runnable listener) {
        try { listener.run(); } catch (Throwable ignored) { }
    }
}
