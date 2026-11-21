package wex.ui.core.feature;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.robv.android.xposed.XposedBridge;
import wex.ui.core.WexStore;

/** 本人资料快照。数据库和图片读取均在后台执行；不保存副本、不记录资料正文。 */
public final class SelfProfile {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "MindLink-profile");
        thread.setDaemon(true);
        return thread;
    });
    private static final List<WeakReference<Runnable>> LISTENERS = new ArrayList<>();
    private static final Object REQUEST_LOCK = new Object();
    private static final ThreadLocal<Boolean> READING = new ThreadLocal<>();
    private static final Runnable DATABASE_READY = () -> refresh(true);
    private static final Runnable CONFIG_READY = () -> refresh(true);
    private static volatile Context application;
    private static volatile String wxid = "", nickname = "", avatarUrl = "";
    private static volatile String confirmedAccount = "";
    private static volatile Bitmap avatar;
    private static volatile boolean loading;
    private static boolean subscribed;
    private static boolean refreshPending, followingUp;
    private static long lastCheck;
    private static volatile long requestId;
    private static String imageLoadedFor = "";
    private static long lastImageAttempt;
    private static boolean waitingForCapture;
    private static long captureVersion;

    private SelfProfile() { }

    static void initialize(Context context) {
        if (Looper.myLooper() != Looper.getMainLooper()) { post(() -> initialize(context)); return; }
        if (context != null) application = context.getApplicationContext();
        updatePreferenceAccount();
        if (!subscribed) {
            HostDatabaseBridge.subscribeReady(DATABASE_READY);
            HostProfileBridge.subscribe(CONFIG_READY);
            subscribed = true;
        }
    }

    private static void updatePreferenceAccount() {
        String account = accountFromPreferences();
        if (!account.isEmpty() && !account.equals(wxid)) {
            synchronized (REQUEST_LOCK) {
                requestId++;
                loading = false;
                refreshPending = false;
                followingUp = false;
            }
            confirmedAccount = "";
            wxid = account; nickname = ""; avatarUrl = ""; avatar = null; imageLoadedFor = "";
            TextStatusBridge.accountChanged(account);
            publish();
        }
    }

    static boolean statusAccountReady(String account) {
        return account != null && !account.isEmpty() && account.equals(wxid)
                && account.equals(confirmedAccount) && HostDatabaseBridge.isReady();
    }

    private static String accountFromPreferences(){Context context=application;if(context==null)context=ConversationStorageBridge.currentApplication();if(context==null)return "";application=context.getApplicationContext();try{return context.getSharedPreferences("com.tencent.mm_preferences",Context.MODE_PRIVATE).getString("login_weixin_username","");}catch(Throwable error){return "";}}

    public static String wxid() { return wxid; }
    public static String nickname() { return nickname; }
    public static String status() {
        TextStatusBridge.Snapshot value = TextStatusBridge.snapshot();
        return value.kind == TextStatusBridge.Kind.READY ? value.description : "";
    }
    public static Bitmap avatar() {
        Bitmap value = avatar;
        return value != null && !value.isRecycled() ? value : null;
    }
    public static boolean hasAny() { return !nickname.isEmpty() || avatar() != null; }
    public static boolean isRedDot() { return false; }
    public static String summary() {
        return "配置=" + HostProfileBridge.status() + " 账号=" + !wxid.isEmpty()
                + " 昵称=" + !nickname.isEmpty() + " 头像=" + (avatar() != null)
                + " 状态门禁=" + statusAccountReady(wxid);
    }

    static void subscribe(Runnable listener) {
        if (listener == null) return;
        if (Looper.myLooper() != Looper.getMainLooper()) {
            post(() -> subscribe(listener));
            return;
        }
        unsubscribe(listener);
        LISTENERS.add(new WeakReference<>(listener));
        notifyListener(listener);
        refresh(false);
    }

    static void unsubscribe(Runnable listener) {
        for (int i = LISTENERS.size() - 1; i >= 0; i--) {
            Runnable current = LISTENERS.get(i).get();
            if (current == null || current == listener) LISTENERS.remove(i);
        }
    }

    static void refresh(boolean force) { refresh(force, false); }

    private static void refresh(boolean force, boolean followUp) {
        // A database-ready callback caused by this read is consumed by the same request.
        if (Boolean.TRUE.equals(READING.get())) return;
        if (Looper.myLooper() != Looper.getMainLooper()) { post(() -> refresh(force, followUp)); return; }
        long started = 0;
        try {
            if (!WexStore.INSTANCE.getBool("top_profile_enabled", false)
                    && !WexStore.INSTANCE.getBool("home_side_panel_enabled", false)) return;
            updatePreferenceAccount();
            // Merge loading/cooldown requests before bridge validation/demand. Publication still
            // rechecks account, token and capture source version; this never makes Ready true.
            synchronized (REQUEST_LOCK) {
                if (loading) {
                    if (force && !followingUp) refreshPending = true;
                    return;
                }
                if (!force && SystemClock.uptimeMillis() - lastCheck < 2000
                        && captureVersion == HostDatabaseBridge.captureVersion()
                        && HostDatabaseBridge.currentCapture()) return;
            }
            HostDatabaseBridge.currentCapture();
            long version = HostDatabaseBridge.captureVersion();
            if (captureVersion != version) {
                captureVersion = version;
                synchronized (REQUEST_LOCK) { requestId++; loading = false; refreshPending = false; followingUp = false; }
                confirmedAccount = ""; nickname = ""; avatarUrl = ""; avatar = null; imageLoadedFor = "";
                TextStatusBridge.accountChanged(wxid);
                publish();
            }
            HostDatabaseBridge.requestProfileCapture();
            boolean databaseReady = HostDatabaseBridge.isReady(), configObserved = HostProfileBridge.observed();
            if (!databaseReady && !configObserved) {
                if (!confirmedAccount.isEmpty()) {
                    confirmedAccount = "";
                    TextStatusBridge.accountChanged(wxid);
                }
                if (!waitingForCapture) stage("stage=wait config=false database=false");
                waitingForCapture = true;
                return;
            }
            waitingForCapture = false;
            long now = SystemClock.uptimeMillis();
            final long token;
            boolean pending, activeFollowUp;
            synchronized (REQUEST_LOCK) {
                if (loading) {
                    // One trailing read per primary request, never a self-sustaining retry loop.
                    if (force && !followingUp) refreshPending = true;
                    token = 0;
                } else {
                    if (!force && now - lastCheck < 2000) return;
                    loading = true; lastCheck = now;
                    refreshPending = false; followingUp = followUp;
                    token = ++requestId;
                    started = token;
                }
                pending = refreshPending; activeFollowUp = followingUp;
            }
            if (token == 0) {
                if (force) stage("stage=deferred pending=" + pending + " followup=" + activeFollowUp);
                return;
            }
            stage("stage=request force=" + force + " followup=" + followUp
                    + " config=" + configObserved + " database=" + databaseReady);
            try { IO.execute(() -> readProfile(token)); }
            catch (Throwable error) {
                if (requestId == token) confirmedAccount = "";
                log("读取提交失败", error);
                finishRequest(token, false);
            }
        } catch (Throwable error) {
            if (started != 0) finishRequest(started, false);
            log("刷新失败", error);
        }
    }

    private static void readProfile(long token) {
        long storageVersion = HostDatabaseBridge.captureVersion();
        Boolean previous = READING.get();
        READING.set(true);
        try {
            HostProfileBridge.Identity identity = null;
            if (HostProfileBridge.observed()) {
                try { identity = HostProfileBridge.readIdentity(); }
                catch (Throwable error) { log("配置资料读取失败", error); }
            }
            String candidateAccount = identity == null ? "" : identity.account;
            boolean confirmedByDatabase = false;
            if (HostDatabaseBridge.isReady()) {
                try {
                    List<Map<String, Object>> rows = HostDatabaseBridge.query(
                            "SELECT value AS wxid FROM userinfo WHERE id=2 LIMIT 1", null);
                    if (rows.isEmpty()) throw new IllegalStateException("Account unavailable");
                    String databaseAccount = string(rows.get(0).get("wxid"));
                    if (databaseAccount.isEmpty()) throw new IllegalStateException("Account unavailable");
                    if (identity != null && !databaseAccount.equals(identity.account))
                        throw new IllegalStateException("ConfigStorage account mismatch");
                    candidateAccount = databaseAccount;
                    confirmedByDatabase = true;
                } catch (Throwable error) {
                    if (identity == null) throw error;
                    log("状态账号确认失败", error);
                }
            } else if (identity == null) throw new IllegalStateException("Account storage unavailable");
            if (candidateAccount.isEmpty()) throw new IllegalStateException("Account unavailable");
            final String account = candidateAccount;
            final boolean databaseConfirmed = confirmedByDatabase;
            String nextName = identity == null ? null : identity.nickname, nextImage = null;
            if (databaseConfirmed && HostDatabaseBridge.isReady()) {
                if (nextName == null || nextName.isEmpty()) try {
                    List<Map<String, Object>> profile = HostDatabaseBridge.query(
                            "SELECT nickname FROM rcontact WHERE username=? LIMIT 1", new String[]{account});
                    nextName = profile.isEmpty() ? "" : string(profile.get(0).get("nickname"));
                } catch (Throwable error) { nextName = null; log("昵称读取失败", error); }
                try {
                    List<Map<String, Object>> images = HostDatabaseBridge.query(
                            "SELECT reserved2 AS avatarUrl FROM img_flag WHERE username=? LIMIT 1", new String[]{account});
                    nextImage = images.isEmpty() ? "" : string(images.get(0).get("avatarUrl"));
                } catch (Throwable error) { log("头像地址读取失败", error); }
            }
            // The last SQL may close its wrapper without a host callback/version change.
            // Validate open and source version on this worker, outside bridge locks, before
            // posting any successful result; MAIN retains only fixed-field identity checks.
            if (!HostDatabaseBridge.readCaptureCurrent() || HostDatabaseBridge.captureVersion() != storageVersion
                    || requestId != token) throw new IllegalStateException("Profile capture changed after read");
            final String name = nextName, image = nextImage;
            stage("stage=read success=true identity=" + (identity != null) + " confirmed=" + databaseConfirmed
                    + " nameKnown=" + (name != null) + " nickname=" + (name != null && !name.isEmpty())
                    + " avatarUrlKnown=" + (image != null) + " avatarUrl=" + (image != null && !image.isEmpty()));
            if (!post(() -> {
                try {
                    updatePreferenceAccount();
                    if (requestId != token || !HostDatabaseBridge.currentCapture()
                            || HostDatabaseBridge.captureVersion() != storageVersion) { stage("stage=reject current=false"); return; }
                    String preferenceAccount = accountFromPreferences();
                    boolean databaseValid = !databaseConfirmed || HostDatabaseBridge.isReady();
                    boolean preferencesMatch = preferenceAccount.isEmpty() || account.equals(preferenceAccount);
                    if (!databaseValid || !preferencesMatch) {
                        confirmedAccount = "";
                        TextStatusBridge.accountChanged(wxid);
                        stage("stage=reject current=true preferences=" + preferencesMatch + " database=" + databaseValid);
                        return;
                    }
                    boolean accountChanged = !account.equals(wxid);
                    String profileName = name == null ? (accountChanged ? "" : nickname) : name;
                    String profileImage = image == null ? (accountChanged ? "" : avatarUrl) : image;
                    boolean changed = accountChanged || !profileName.equals(nickname) || !profileImage.equals(avatarUrl);
                    if (accountChanged) {
                        confirmedAccount = "";
                        avatar = null;
                        imageLoadedFor = "";
                        TextStatusBridge.accountChanged(account);
                    }
                    wxid = account; nickname = profileName; avatarUrl = profileImage;
                    confirmedAccount = databaseConfirmed ? account : "";
                    stage("stage=success changed=" + changed + " confirmed=" + databaseConfirmed
                            + " nickname=" + !nickname.isEmpty() + " avatarUrl=" + !avatarUrl.isEmpty());
                    if (changed) publish();
                    if (!profileImage.isEmpty() && (!profileImage.equals(imageLoadedFor) || avatar() == null)
                            && (changed || SystemClock.uptimeMillis() - lastImageAttempt > 30000)) {
                        lastImageAttempt = SystemClock.uptimeMillis();
                        loadAvatar(account, profileImage);
                    }
                    TextStatusBridge.refresh(false);
                } finally { finishRequest(token, true); }
            })) {
                stage("stage=reject main=false");
                finishRequest(token, false);
            }
        } catch (Throwable error) {
            stage("stage=read success=false current=" + (requestId == token));
            log("读取失败", error);
            if (!post(() -> {
                try {
                    if (requestId != token) { stage("stage=reject current=false"); return; }
                    confirmedAccount = "";
                    TextStatusBridge.accountChanged(wxid);
                } finally { finishRequest(token, true); }
            })) {
                stage("stage=reject main=false");
                finishRequest(token, false);
            }
        } finally {
            if (previous == null) READING.remove(); else READING.set(previous);
        }
    }

    private static void finishRequest(long token, boolean allowFollowUp) {
        boolean again;
        synchronized (REQUEST_LOCK) {
            // A stale completion must never clear the current request's loading or dirty bit.
            if (requestId != token) return;
            loading = false;
            again = allowFollowUp && refreshPending && !followingUp;
            refreshPending = false; followingUp = false;
        }
        if (again) refresh(true, true);
    }

    private static void loadAvatar(String account, String image) {
        try {
            IO.execute(() -> {
                try {
                    Bitmap decoded = download(image);
                    post(() -> {
                        if (!account.equals(wxid) || !image.equals(avatarUrl)) return;
                        avatar = decoded; imageLoadedFor = image;
                        publish();
                    });
                } catch (Throwable error) { log("头像读取失败", error); }
            });
        } catch (Throwable error) { log("头像提交失败", error); }
    }

    private static Bitmap download(String address) throws Exception {
        URL url = new URL(address);
        // Only fetch the host-provided avatar from WeChat's CDN, never an arbitrary config URL.
        for (int redirects = 0; redirects < 4; redirects++) {
            if (!(url.getProtocol().equals("https") || url.getProtocol().equals("http"))
                    || !avatarHost(url.getHost())) throw new IllegalArgumentException("Unsupported avatar origin");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            try {
                connection.setConnectTimeout(6000); connection.setReadTimeout(6000);
                connection.setInstanceFollowRedirects(false);
                int code = connection.getResponseCode();
                if (code >= 300 && code < 400) {
                    String location = connection.getHeaderField("Location");
                    if (location == null) throw new IllegalStateException("Missing redirect");
                    url = new URL(url, location); continue;
                }
                if (code != 200) throw new IllegalStateException("Avatar HTTP " + code);
                byte[] data;
                try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192]; int count;
                    while ((count = input.read(buffer)) != -1) {
                        if (output.size() + count > 4 * 1024 * 1024) throw new IllegalStateException("Avatar too large");
                        output.write(buffer, 0, count);
                    }
                    data = output.toByteArray();
                }
                BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new IllegalStateException("Invalid avatar");
                BitmapFactory.Options options = new BitmapFactory.Options(); options.inSampleSize = 1;
                while (Math.max(bounds.outWidth, bounds.outHeight) / options.inSampleSize > 256) options.inSampleSize *= 2;
                Bitmap value = BitmapFactory.decodeByteArray(data, 0, data.length, options);
                if (value == null) throw new IllegalStateException("Avatar decode failed");
                return value;
            } finally { connection.disconnect(); }
        }
        throw new IllegalStateException("Too many avatar redirects");
    }

    private static boolean avatarHost(String host) {
        String value = host.toLowerCase(java.util.Locale.ROOT);
        return value.equals("qlogo.cn") || value.endsWith(".qlogo.cn")
                || value.equals("qpic.cn") || value.endsWith(".qpic.cn")
                || value.equals("weixin.qq.com") || value.endsWith(".weixin.qq.com");
    }

    /** Existing smali callback now schedules a profile refresh, without scanning unrelated photos. */
    public static void captureFrom(View root) { refresh(false); }

    private static void publish() {
        stage("stage=publish account=" + !wxid.isEmpty() + " nickname=" + !nickname.isEmpty()
                + " avatar=" + (avatar() != null) + " confirmed=" + statusAccountReady(wxid));
        for (int i = LISTENERS.size() - 1; i >= 0; i--) {
            Runnable listener = LISTENERS.get(i).get();
            if (listener == null) LISTENERS.remove(i);
            else notifyListener(listener);
        }
    }

    private static void notifyListener(Runnable listener) {
        try { listener.run(); }
        catch (Throwable error) { log("视图刷新失败", error); }
    }

    private static boolean post(Runnable task) {
        try {
            return MAIN.post(() -> {
                try { task.run(); }
                catch (Throwable error) { log("主线程回调失败", error); }
            });
        } catch (Throwable error) { log("主线程提交失败", error); return false; }
    }

    private static void stage(String message) {
        HostStageLog.write("profile", message);
        try { XposedBridge.log("WEX: 本人资料: " + message); }
        catch (Throwable ignored) { }
    }

    private static void log(String operation, Throwable error) {
        HostStageLog.write("profile", operation + " " + error.getClass().getSimpleName());
        try { XposedBridge.log("WEX: 本人资料: " + operation + " " + error.getClass().getSimpleName()); }
        catch (Throwable ignored) { }
    }

    private static String string(Object value) { return value instanceof String ? (String) value : ""; }
}
