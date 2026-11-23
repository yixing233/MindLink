package wex.ui.core.feature;

import android.os.SystemClock;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import dev.mindlink.xposed.XC_MethodHook;
import dev.mindlink.xposed.XposedBridge;

/** Reads WeChat's local wallet balance cache; never initiates a payment or network request. */
final class HomeSidePanelWallet {
    private static final String CACHE = "com.tencent.mm.wallet_core.model.m1";
    private static final String PLUGIN = "com.tencent.mm.plugin.wxpay.g";
    private static final String BALANCE_KEY = "USERINFO_NEW_BALANCE_LONG_SYNC";
    private static volatile Method serviceMethod, readMethod;
    private static volatile Class<?> pluginClass;
    private static volatile Long balanceFen;
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "MindLink-sidebar-wallet");
        thread.setDaemon(true); return thread;
    });
    private static final AtomicBoolean READING = new AtomicBoolean();
    private static final AtomicLong VERSION = new AtomicLong();
    private static final Object BALANCE_LOCK = new Object();
    private static volatile long lastAttempt;
    private static final long READ_TTL_MS = 30_000L;

    private HomeSidePanelWallet() { }

    static void init() {
        resolve("sidebar_wallet_service", new HostTargetResolver.MethodSpec()
                .modifiers(Modifier.STATIC).parameterTypes("java.lang.Class")
                .usingExactStrings("calling getService(...)"), method -> serviceMethod = method);
        resolve("sidebar_wallet_read", new HostTargetResolver.MethodSpec().declaredClass(CACHE)
                .name("i").parameterCount(2).returnType("java.lang.Object"), method -> readMethod = method);
        resolve("sidebar_wallet_write", new HostTargetResolver.MethodSpec().declaredClass(CACHE)
                .name("j").parameterCount(2).returnType("void"), method -> {
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (param.args.length < 2 || !(param.args[0] instanceof Enum)
                            || !BALANCE_KEY.equals(((Enum<?>) param.args[0]).name())
                            || !(param.args[1] instanceof Long)) return;
                    synchronized (BALANCE_LOCK) {
                        VERSION.incrementAndGet();
                        balanceFen = (Long) param.args[1];
                    }
                    HomeSidePanelFeature.refreshOpenDrawers();
                }
            });
        });
        try { pluginClass = Class.forName(PLUGIN, false, HostTargetResolver.hostClassLoader()); }
        catch (Throwable error) { log("钱包服务类不可用 " + error.getClass().getSimpleName()); }
    }

    /** Non-blocking, read-only snapshot. Reflection never executes on the calling UI thread. */
    static Long read() {
        refresh(false);
        return balanceFen;
    }

    static void refresh(boolean force) {
        long now = SystemClock.uptimeMillis();
        if (!force && lastAttempt != 0 && now - lastAttempt < READ_TTL_MS || !READING.compareAndSet(false, true)) return;
        lastAttempt = now;
        final long version = VERSION.get();
        try {
            IO.execute(() -> {
                try {
                    Long next = readLocalCache();
                    // A captured cache write is newer than an in-flight reflection read.
                    boolean changed = false;
                    synchronized (BALANCE_LOCK) {
                        if (next != null && VERSION.compareAndSet(version, version + 1)) {
                            changed = !next.equals(balanceFen);
                            balanceFen = next;
                        }
                    }
                    if (changed) HomeSidePanelFeature.refreshOpenDrawers();
                } finally { READING.set(false); }
            });
        } catch (RuntimeException error) {
            READING.set(false);
            log("钱包读取提交失败 " + error.getClass().getSimpleName());
        }
    }

    private static Long readLocalCache() {
        Method service = serviceMethod, read = readMethod; Class<?> plugin = pluginClass;
        if (service == null || read == null || plugin == null || plugin.getInterfaces().length != 1) return null;
        try {
            Object pluginService = service.invoke(null, plugin.getInterfaces()[0]);
            if (pluginService == null) return null;
            Method accessor = null;
            for (Method candidate : plugin.getDeclaredMethods()) {
                if (candidate.getParameterTypes().length == 0 && candidate.getReturnType() == read.getDeclaringClass()) {
                    if (accessor != null) return null;
                    accessor = candidate;
                }
            }
            if (accessor == null || read.getParameterTypes().length != 2 || !read.getParameterTypes()[0].isEnum()) return null;
            accessor.setAccessible(true);
            Object cache = accessor.invoke(pluginService);
            if (cache == null) return null;
            Object key = null;
            for (Object value : read.getParameterTypes()[0].getEnumConstants()) {
                if (value instanceof Enum && BALANCE_KEY.equals(((Enum<?>) value).name())) { key = value; break; }
            }
            Object result = key == null ? null : read.invoke(cache, key, null);
            return result instanceof Long ? (Long) result : null;
        } catch (Throwable error) {
            log("钱包余额读取失败 " + error.getClass().getSimpleName());
            return null;
        }
    }

    static String format(Long fen) {
        if (fen == null) return "¥ --";
        DecimalFormat format = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));
        return "¥ " + format.format(BigDecimal.valueOf(fen, 2));
    }

    private interface Resolved { void accept(Method method) throws Throwable; }
    private static void resolve(String id, HostTargetResolver.MethodSpec spec, Resolved action) {
        HostTargetResolver.resolveMethod(id, spec, new HostTargetResolver.MethodCallback() {
            @Override public void onResolved(Method method) { try { action.accept(method); } catch (Throwable error) { log(id + " 接线失败 " + error.getClass().getSimpleName()); } }
            @Override public void onFailure(Throwable error) { log(id + " 解析失败；余额保持未知"); }
        });
    }
    private static void log(String message) { XposedBridge.log("WEX: 侧栏钱包: " + message); }
}
