package wex.ui.core.feature;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.ImageView;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import dev.mindlink.xposed.XC_MethodHook;
import dev.mindlink.xposed.XposedBridge;
import wex.ui.core.WexStore;

/** WeChat status service, following WeKit WeTextStatusApi (GPL-3.0). No status text is inferred from views. */
final class TextStatusBridge {
    enum Kind { LOADING, READY, NONE, ERROR }

    static final class Snapshot {
        final Kind kind;
        final String account, statusId, description, userText, iconId;
        final long expires;
        Snapshot(Kind kind, String account, String statusId, String description,
                 String userText, String iconId, long expires) {
            this.kind = kind; this.account = account; this.statusId = statusId;
            this.description = description; this.userText = userText; this.iconId = iconId; this.expires = expires;
        }
        String label() {
            switch (kind) {
                case READY: return description;
                case NONE: return "+ 状态";
                case ERROR: return disabled ? "状态暂不可用 · 请重启微信" : "状态暂不可用 · 点击重试";
                default: return "读取状态…";
            }
        }
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final List<WeakReference<Runnable>> LISTENERS = new ArrayList<>();
    private static volatile Snapshot snapshot = empty(Kind.LOADING, "");
    private static volatile Targets targets;
    private static volatile boolean disabled, loading;
    private static boolean initialized;
    private static long lastRequest, syncGeneration;
    private static volatile long requestId;

    private TextStatusBridge() { }

    static synchronized void init() {
        if (initialized) return;
        XposedBridge.hookAllMethods(Activity.class, "onResume", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                try {
                    if (!(param.thisObject instanceof Activity)) return;
                    Activity activity = (Activity) param.thisObject;
                    if ("com.tencent.mm.ui.LauncherUI".equals(activity.getClass().getName())) refresh(false);
                } catch (Throwable error) { log("恢复刷新失败", error); }
            }
        });
        initialized = true;
    }

    static void accountChanged(String account) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            post(() -> accountChanged(account));
            return;
        }
        requestId++;
        syncGeneration++;
        loading = false;
        lastRequest = 0;
        publish(empty(disabled ? Kind.ERROR : Kind.LOADING, account));
    }

    static Snapshot snapshot() {
        Snapshot value = snapshot;
        if (value.kind == Kind.READY && !available(value.account))
            return empty(disabled ? Kind.ERROR : Kind.LOADING, SelfProfile.wxid());
        if (value.kind == Kind.READY && !validStatus(value.statusId, value.expires, System.currentTimeMillis() / 1000)) {
            refresh(false);
            return empty(Kind.NONE, value.account);
        }
        return value;
    }

    static void subscribe(Runnable listener) {
        if (listener == null) return;
        post(() -> {
            unsubscribe(listener);
            LISTENERS.add(new WeakReference<>(listener));
            notifyListener(listener); refresh(false);
        });
    }

    static void unsubscribe(Runnable listener) {
        for (int i = LISTENERS.size() - 1; i >= 0; i--) {
            Runnable value = LISTENERS.get(i).get();
            if (value == null || value == listener) LISTENERS.remove(i);
        }
    }

    static void refresh(boolean force) {
        if (Looper.myLooper() != Looper.getMainLooper()) { post(() -> refresh(force)); return; }
        try {
            if (!WexStore.INSTANCE.getBool("top_profile_enabled", false)
                    && !WexStore.INSTANCE.getBool("home_side_panel_enabled", false)) return;
            String account = SelfProfile.wxid();
            if (!available(account)) {
                publish(empty(disabled ? Kind.ERROR : Kind.LOADING, account));
                return;
            }
            long now = SystemClock.uptimeMillis();
            if (loading || !force && now - lastRequest < 1500) return;
            loading = true; lastRequest = now;
            final long token = ++requestId;
            try {
                HostTargetResolver.background(() -> {
                    Snapshot next;
                    try {
                        if (!available(account)) next = empty(disabled ? Kind.ERROR : Kind.LOADING, account);
                        else {
                            Targets resolved = targets;
                            if (resolved == null) targets = resolved = Targets.resolve();
                            next = available(account) ? resolved.read(account)
                                    : empty(disabled ? Kind.ERROR : Kind.LOADING, account);
                        }
                    } catch (Throwable error) {
                        failed("读取失败", error);
                        next = empty(Kind.ERROR, account);
                    }
                    final Snapshot result = next;
                    if (!post(() -> {
                        if (token != requestId) return;
                        loading = false;
                        publish(available(account) ? result : empty(disabled ? Kind.ERROR : Kind.LOADING, account));
                    })) { if (token == requestId) loading = false; }
                });
            } catch (Throwable error) {
                loading = false;
                failed("读取提交失败", error);
                publish(empty(Kind.ERROR, account));
            }
        } catch (Throwable error) { log("刷新失败", error); }
    }

    static void open(Activity activity) {
        try {
            if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
            Snapshot state = snapshot();
            if (!available(state.account) || state.kind == Kind.ERROR || state.kind == Kind.LOADING) {
                refresh(true);
                toast(activity, disabled ? "状态服务初始化失败，请重启微信" : "状态服务尚未就绪，请稍后再试");
                return;
            }
            WeakReference<Activity> owner = new WeakReference<>(activity);
            final String expectedAccount = state.account;
            HostTargetResolver.background(() -> {
                try {
                    if (!available(expectedAccount)) return;
                    Targets resolved = targets;
                    if (resolved == null) throw new IllegalStateException("Status service unavailable");
                    Current current = resolved.current(expectedAccount);
                    // Action target resolution is optional for reading; do it only when the user taps.
                    if (current != null) resolved.resolveActions();
                    post(() -> {
                        Activity target = owner.get();
                        if (target == null || target.isFinishing() || target.isDestroyed()
                                || !available(expectedAccount) || !expectedAccount.equals(snapshot.account)) return;
                        try {
                            if (current == null) openEditor(target);
                            else resolved.showActions(target, current);
                            scheduleSync();
                        } catch (Throwable error) {
                            failed("状态操作不可用", error);
                            toast(target, disabled ? "状态服务初始化失败，请重启微信" : "无法打开当前状态操作，未修改状态");
                        }
                    });
                } catch (Throwable error) {
                    failed("状态操作读取失败", error);
                    post(() -> toast(owner.get(), disabled ? "状态服务初始化失败，请重启微信" : "状态服务尚未就绪，请重试"));
                }
            });
        } catch (Throwable error) { log("状态操作失败", error); }
    }

    static void renderIcon(ImageView image, Snapshot value) {
        try {
            if (image == null) return;
            image.setImageDrawable(null);
            Targets resolved = targets;
            if (resolved == null || value == null || !available(value.account)
                    || value.kind != Kind.READY || value.iconId.isEmpty()) return;
            resolved.setIcon.invoke(null, resolved.iconHelper(), image, value.iconId,
                    null, null, null, false, false, 124, null);
        } catch (Throwable error) { failed("图标渲染不可用", error); }
    }

    private static void openEditor(Activity activity) throws Exception {
        for (String name : new String[]{"com.tencent.mm.plugin.textstatus.ui.TextStatusDoWhatActivityV2",
                "com.tencent.mm.plugin.textstatus.ui.TextStatusDoWhatActivity"}) {
            Intent intent = new Intent().setClassName(activity.getPackageName(), name).putExtra("KEY_IS_ENTER", true);
            if (intent.resolveActivity(activity.getPackageManager()) != null) { activity.startActivity(intent); return; }
        }
        throw new IllegalStateException("Status editor unavailable");
    }

    private static void scheduleSync() {
        final long token = ++syncGeneration;
        MAIN.postDelayed(new Runnable() {
            int attempts;
            @Override public void run() {
                if (token != syncGeneration || ++attempts > 24 || LISTENERS.isEmpty()
                        || !available(snapshot.account)) return;
                try {
                    refresh(true);
                    MAIN.postDelayed(this, 700);
                } catch (Throwable error) { log("同步刷新失败", error); }
            }
        }, 700);
    }

    private static void publish(Snapshot next) {
        Snapshot old = snapshot; snapshot = next;
        if (old.kind == next.kind && Objects.equals(old.account, next.account)
                && Objects.equals(old.statusId, next.statusId) && Objects.equals(old.description, next.description)
                && Objects.equals(old.iconId, next.iconId) && old.expires == next.expires) return;
        for (int i = LISTENERS.size() - 1; i >= 0; i--) {
            Runnable listener = LISTENERS.get(i).get();
            if (listener == null) LISTENERS.remove(i); else notifyListener(listener);
        }
    }

    private static boolean available(String account) {
        return !disabled && SelfProfile.statusAccountReady(account);
    }

    private static void failed(String operation, Throwable error) {
        for (Throwable cause = error; cause != null; ) {
            if (cause instanceof LinkageError) { disabled = true; break; }
            Throwable next = cause.getCause();
            if (next == cause) break;
            cause = next;
        }
        log(operation, error);
        if (disabled) post(() -> {
            syncGeneration++;
            loading = false;
            publish(empty(Kind.ERROR, SelfProfile.wxid()));
        });
    }

    private static boolean post(Runnable task) {
        try {
            return MAIN.post(() -> {
                try { task.run(); }
                catch (Throwable error) { log("主线程回调失败", error); }
            });
        } catch (Throwable error) { log("主线程提交失败", error); return false; }
    }

    private static void notifyListener(Runnable listener) {
        try { listener.run(); }
        catch (Throwable error) { log("视图刷新失败", error); }
    }

    private static void toast(Activity activity, String message) {
        try {
            if (activity != null && !activity.isFinishing() && !activity.isDestroyed())
                ImagePicker.INSTANCE.toast(activity, message);
        } catch (Throwable error) { log("提示失败", error); }
    }

    private static void log(String operation, Throwable error) {
        try { XposedBridge.log("WEX: 微信状态: " + operation + " " + error.getClass().getSimpleName()); }
        catch (Throwable ignored) { }
    }

    static boolean validStatus(String id, long expirySeconds, long nowSeconds) {
        return id != null && !id.trim().isEmpty() && expirySeconds > nowSeconds;
    }

    private static Snapshot empty(Kind kind, String account) { return new Snapshot(kind, account, "", "", "", "", 0); }

    private static final class Current {
        final Object host, record;
        final String id;
        final long expires;
        Current(Object host, Object record, String id, long expires) {
            this.host = host; this.record = record; this.id = id; this.expires = expires;
        }
    }

    private static final class Targets {
        final Method latest, accessor, topic, setIcon, iconAccessor, description;
        final Field singleton;
        final Class<?> recordClass;
        Method actions;

        Targets(Method latest, Method accessor, Field singleton, Method topic, Method setIcon,
                Method iconAccessor, Method description, Class<?> recordClass) {
            this.latest = latest; this.accessor = accessor; this.singleton = singleton;
            this.topic = topic; this.setIcon = setIcon; this.iconAccessor = iconAccessor;
            this.description = description; this.recordClass = recordClass;
        }

        static Targets resolve() throws Exception {
            Method latest = HostTargetResolver.resolveMethodNow("status_latest_v1", new HostTargetResolver.MethodSpec()
                    .parameterTypes("java.lang.String").usingExactStrings("MicroMsg.TextStatus.StatusInfoAffStorage", "getLatestStatusByUserName: failed"));
            List<Class<?>> interfaces = new ArrayList<>();
            for (Class<?> iface : latest.getDeclaringClass().getInterfaces()) {
                for (Method method : iface.getMethods()) {
                    if (method.getName().equals(latest.getName()) && Arrays.equals(method.getParameterTypes(), latest.getParameterTypes())
                            && method.getReturnType() == latest.getReturnType()) { interfaces.add(iface); break; }
                }
            }
            if (interfaces.size() != 1) throw new IllegalStateException("Ambiguous status storage interface");
            List<Method> accessors = HostTargetResolver.findMethods(new HostTargetResolver.MethodSpec()
                    .parameterTypes().returnType(interfaces.get(0).getName()));
            List<Method> candidates = new ArrayList<>();
            for (Method method : accessors) if (singletonField(method.getDeclaringClass()) != null) candidates.add(method);
            Method accessor = HostTargetResolver.unique(candidates, "Status storage accessor");
            Field singleton = singletonField(accessor.getDeclaringClass());
            Method topic = HostTargetResolver.resolveMethodNow("status_topic_v1", new HostTargetResolver.MethodSpec()
                    .usingExactStrings("Super calls with default arguments not supported in this target, function: getTopicInfo")
                    .parameterCount(4));
            Method setIcon = HostTargetResolver.resolveMethodNow("status_icon_v1", new HostTargetResolver.MethodSpec()
                    .usingExactStrings("Super calls with default arguments not supported in this target, function: setIcon")
                    .parameterCount(10));
            Class<?> helper = setIcon.getParameterTypes()[0];
            Method iconAccessor = HostTargetResolver.unique(HostTargetResolver.findMethods(new HostTargetResolver.MethodSpec()
                    .parameterTypes().returnType(helper.getName()).modifiers(Modifier.STATIC)), "Status icon helper");
            Method description = HostTargetResolver.unique(HostTargetResolver.findMethods(new HostTargetResolver.MethodSpec()
                    .declaredClass(helper.getName()).parameterTypes(topic.getReturnType().getName()).returnType(String.class.getName())), "Status description");
            Class<?> record = HostTargetResolver.classWithFields("field_UserName", "field_StatusID", "field_IconID",
                    "field_Description", "field_ExpireTime", "field_EmojiInfo");
            return new Targets(latest, accessor, singleton, topic, setIcon, iconAccessor, description, record);
        }

        Object iconHelper() throws Exception { return iconAccessor.invoke(null); }

        Current current(String wxid) throws Exception {
            Object service;
            try {
                // A failed static initializer cannot be retried in this host ClassLoader.
                service = singleton.get(null);
            } catch (IllegalAccessException error) { throw error; }
            catch (Throwable error) {
                disabled = true;
                throw new ExceptionInInitializerError(error);
            }
            if (service == null) throw new IllegalStateException("Status singleton unavailable");
            Object storage = accessor.invoke(service);
            Object host = latest.invoke(storage, wxid);
            if (host == null) return null;
            Object record = host;
            if (!recordClass.isInstance(record)) {
                List<Field> fields = fields(host.getClass(), recordClass);
                if (fields.size() != 1) throw new IllegalStateException("Ambiguous wrapped status record");
                record = fields.get(0).get(host);
            }
            if (record == null) throw new IllegalStateException("Missing status record");
            String id = string(readField(record, "field_StatusID"));
            Object expiry = readField(record, "field_ExpireTime");
            if (!(expiry instanceof Number)) throw new IllegalStateException("Invalid status expiry");
            long expires = ((Number) expiry).longValue();
            return validStatus(id, expires, System.currentTimeMillis() / 1000) ? new Current(host, record, id, expires) : null;
        }

        Snapshot read(String wxid) throws Exception {
            Current current = current(wxid);
            if (current == null) return empty(Kind.NONE, wxid);
            Object info = topic.invoke(null, current.host, false, 1, null);
            String label = string(description.invoke(iconHelper(), info));
            if (label.isEmpty()) throw new IllegalStateException("Empty status label");
            return new Snapshot(Kind.READY, wxid, current.id, label,
                    string(readField(current.record, "field_Description")), string(readField(current.record, "field_IconID")), current.expires);
        }

        void resolveActions() throws Exception {
            if (actions != null) return;
            List<Method> candidates = new ArrayList<>();
            for (Method method : HostTargetResolver.findMethods(new HostTargetResolver.MethodSpec()
                    .parameterTypes(Context.class.getName(), latest.getReturnType().getName()).returnType("void")
                    .usingExactStrings("MicroMsg.MMSubMenuHelper", "show, menu empty"))) {
                if (HostTargetResolver.usesFieldThroughDirectInvoke(method, "field_UserName")) candidates.add(method);
            }
            actions = HostTargetResolver.unique(candidates, "Current status actions");
        }

        void showActions(Context context, Current current) throws Exception {
            if (actions == null) throw new IllegalStateException("Actions unresolved");
            Constructor<?>[] constructors = actions.getDeclaringClass().getDeclaredConstructors();
            if (constructors.length != 1 || constructors[0].getParameterCount() != 1) throw new IllegalStateException("Unknown status action constructor");
            Constructor<?> parameter = constructors[0].getParameterTypes()[0].getDeclaredConstructor();
            parameter.setAccessible(true); constructors[0].setAccessible(true);
            Object logic = constructors[0].newInstance(parameter.newInstance());
            List<Field> contexts = fields(logic.getClass(), Context.class);
            List<Field> statuses = fields(logic.getClass(), actions.getParameterTypes()[1]);
            if (contexts.size() != 1 || statuses.size() != 1) throw new IllegalStateException("Unknown status action fields");
            contexts.get(0).set(logic, context); statuses.get(0).set(logic, current.host);
            actions.invoke(logic, context, current.host);
        }
    }

    private static Field singletonField(Class<?> type) {
        Field result = null;
        for (Field field : type.getDeclaredFields()) if (field.getType() == type && Modifier.isStatic(field.getModifiers())) {
            if (result != null) return null;
            field.setAccessible(true); result = field;
        }
        return result;
    }

    private static List<Field> fields(Class<?> type, Class<?> fieldType) {
        List<Field> matches = new ArrayList<>();
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) if (!Modifier.isStatic(field.getModifiers()) && field.getType() == fieldType) {
                field.setAccessible(true); matches.add(field);
            }
        }
        return matches;
    }

    private static Object readField(Object object, String name) throws ReflectiveOperationException {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(object); }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }

    private static String string(Object value) { return value instanceof String ? (String) value : ""; }
}
