package wex.ui.core.feature;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import wex.ui.core.WexStore;

/** Editable, local-only conversation groups. Only a successful save replaces the live snapshot. */
public final class ConversationGroupingFeature {
    public static final ConversationGroupingFeature INSTANCE = new ConversationGroupingFeature();
    static final String KEY_ENABLED = "conversation_filter_enabled";
    static final String KEY_GROUPS = "conversation_groups";
    static final String KEY_ACTIVE = "conversation_group_active";
    static final String KEY_PIN = "conversation_grouping_pin_tabs";
    static final String KEY_EQUAL = "conversation_grouping_equal_width_tabs";
    static final String KEY_UNREAD = "conversation_grouping_show_unread";
    static final String KEY_OFFICIAL = "conversation_grouping_include_official_unread";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final WeakHashMap<ListView, WeakReference<ConversationGroupTabsHost>> HOSTS = new WeakHashMap<>();
    private static volatile List<ConversationGroupStore.Group> groups = Collections.unmodifiableList(ConversationGroupStore.defaults());
    private static volatile ConversationGroupStore.Group active = groups.get(0);
    private static volatile Set<String> members = Collections.emptySet();
    // WeKit groupMembersCache semantics: SQL/manual members resolve once per group id and
    // stay cached across resumes; only saveGroups (a real user edit) invalidates them.
    private static final Map<String, Set<String>> MEMBERS = new HashMap<>();
    private static volatile boolean enabled;
    private static volatile long generation;
    private static long selectionVersion;
    private static boolean initialized;
    private static String configError = "";
    // Signature of the config consumed by the last reload; a matching resume does no I/O.
    private static volatile String configSignature;
    // Metadata only: an empty list is a real class/name miss; instance values stay live.
    private static final Map<Class<?>, Map<String, List<Field>>> FIELDS = new HashMap<>();
    private static boolean hostRefreshQueued;

    private ConversationGroupingFeature() { }

    public synchronized void init(XC_LoadPackage.LoadPackageParam lpparam) {
        if (initialized) return;
        initialized = true;
        reload();
        ConversationListBridge.initialize(lpparam);
        ConversationListBridge.addListener((adapter, row, conversation, position, count, previous, next) -> {
            ListView list = ConversationListBridge.currentList();
            if (list != null && enabled) mount(list);
        });
        XposedBridge.hookAllMethods(Activity.class, "onResume", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                if (param.thisObject instanceof Activity) {
                    Activity activity = (Activity) param.thisObject;
                    if ("com.tencent.mm.ui.LauncherUI".equals(activity.getClass().getName())) {
                        // WeKit semantics: resume never re-reads config or re-publishes the
                        // active group; it only completes a missing header mount. Selection,
                        // members and scroll position survive returning from a chat unchanged.
                        onHomeReady(activity);
                    }
                }
            }
        });
    }

    static List<ConversationGroupStore.Group> groups() { return groups; }
    static String activeGroupId() { return active.id; }
    static long generation() { return generation; }
    static String error() { return configError; }
    static boolean isAllActive() { return !enabled || active.isAll(); }
    static boolean isEnabled() { return enabled; }

    static void reload() {
        if (Looper.myLooper() != Looper.getMainLooper()) { MAIN.post(ConversationGroupingFeature::reload); return; }
        String signature = WexStore.INSTANCE.getString(KEY_GROUPS, "") + '\n'
                + WexStore.INSTANCE.getBool(KEY_ENABLED, false);
        if (initialized && signature.equals(configSignature)) return;
        configSignature = signature;
        boolean nextEnabled = WexStore.INSTANCE.getBool(KEY_ENABLED, false);
        try {
            List<ConversationGroupStore.Group> next = ConversationGroupStore.decode(WexStore.INSTANCE.getSettingChecked(KEY_GROUPS));
            groups = Collections.unmodifiableList(next);
            configError = "";
        } catch (Exception error) {
            configError = "分组配置损坏，保留文件；请在编辑器修复或重置";
            log(configError);
        }
        String id = WexStore.INSTANCE.getString(KEY_ACTIVE, ConversationGroupStore.ALL_ID);
        select(id, false, nextEnabled);
        if (!nextEnabled) for (WeakReference<ConversationGroupTabsHost> ref : new ArrayList<>(HOSTS.values())) {
            ConversationGroupTabsHost host = ref.get();
            if (host != null) host.dispose();
        }
    }

    static boolean saveGroups(List<ConversationGroupStore.Group> next) {
        final String encoded;
        try { encoded = ConversationGroupStore.encode(next); }
        catch (IllegalArgumentException error) { configError = error.getMessage(); return false; }
        if (!SettingsChanges.saveString(KEY_GROUPS, encoded)) return false;
        groups = Collections.unmodifiableList(new ArrayList<>(next));
        // A saved edit changes rules/membership for possibly every group; the persistent
        // member cache must not survive it (WeKit clears groupMembersCache in saveGroups).
        synchronized (MEMBERS) { MEMBERS.clear(); }
        configSignature = null;
        configError = "";
        select(active.id, false);
        return true;
    }

    static void setActive(String id) { select(id, true); }

    private static void select(String id, boolean persist) {
        if (Looper.myLooper() != Looper.getMainLooper()) { MAIN.post(() -> select(id, persist)); return; }
        select(id, persist, enabled);
    }

    private static void select(String id, boolean persist, boolean nextEnabled) {
        ConversationGroupStore.Group selected = groups.get(0);
        for (ConversationGroupStore.Group group : groups) if (group.id.equals(id)) { selected = group; break; }
        if (persist && !SettingsChanges.saveString(KEY_ACTIVE, selected.id)) return;
        final ConversationGroupStore.Group requested = selected;
        long version = ++selectionVersion;
        if (nextEnabled && selected.type == ConversationGroupStore.Type.SQL && !selected.isAll()) {
            // WeKit semantics: first resolution per group is cached and reused until the
            // user edits groups. Repeat selections/resumes publish synchronously instead of
            // re-querying SQL and churning the visible mapping on every return to home.
            Set<String> cached;
            synchronized (MEMBERS) {
                cached = MEMBERS.get(requested.id);
                if (cached == null) cached = resolveSqlMembers(requested);
                if (cached != null) MEMBERS.put(requested.id, cached);
            }
            if (cached != null) {
                publishGroup(requested, cached, nextEnabled, version);
            } else {
                configError = "规则查询失败，保留当前分类";
                log(configError);
                refreshHostLabels();
            }
        } else {
            publishGroup(requested, Collections.unmodifiableSet(new HashSet<>(selected.members)), nextEnabled, version);
        }
    }

    /** One blocking SQL resolution; runs on the caller thread or a MAIN-posted reload path. */
    private static Set<String> resolveSqlMembers(ConversationGroupStore.Group requested) {
        try {
            String fields = requested.selectFields.trim().isEmpty() ? "r.username" : requested.selectFields;
            String where = requested.whereClause.trim().isEmpty() ? "1=1" : requested.whereClause;
            List<String> result = HostDatabaseBridge.queryStrings("SELECT " + fields
                    + " FROM rcontact r LEFT JOIN img_flag i ON i.username=r.username"
                    + " LEFT JOIN rconversation c ON c.username=r.username WHERE " + where, null);
            return Collections.unmodifiableSet(new HashSet<>(result));
        } catch (Exception error) {
            return null;
        }
    }

    private static void publishGroup(ConversationGroupStore.Group requested, Set<String> resolved, boolean nextEnabled, long version) {
        if (version != selectionVersion) return;
        boolean selectionChanged = enabled != nextEnabled || !active.id.equals(requested.id) || active.type != requested.type
                || !active.selectFields.equals(requested.selectFields) || !active.whereClause.equals(requested.whereClause);
        boolean changed = selectionChanged || !members.equals(resolved);
        if (changed) {
            ConversationListBridge.changeGroup(() -> {
                if (version != selectionVersion) return false;
                active = requested; members = resolved; enabled = nextEnabled; generation++;
                refreshHostLabels();
                return true;
            }, selectionChanged || requested.type != ConversationGroupStore.Type.SQL);
            // Same SQL rule resolving different rows is a data update, not a new tab selection.
            // Native observers may clamp a vanished anchor; do not force every resume to row zero.
        } else {
            active = requested;
        }
        refreshHostLabels();
    }

    // Host data notifications only refresh tab labels; never send another adapter notification.
    static void refreshHostLabels() {
        synchronized (HOSTS) {
            if (hostRefreshQueued) return;
            hostRefreshQueued = true;
        }
        MAIN.post(() -> {
            synchronized (HOSTS) { hostRefreshQueued = false; }
            for (WeakReference<ConversationGroupTabsHost> ref : new ArrayList<>(HOSTS.values())) {
                ConversationGroupTabsHost host = ref.get();
                if (host != null) host.refresh();
            }
        });
    }

    static boolean matches(Object conversation) {
        if (isAllActive()) return true;
        String username = username(conversation);
        if (username == null) return false;
        switch (active.type) {
            case PRESET_UNREAD: return unread(conversation) > 0;
            case PRESET_GROUPS: return username.endsWith("@chatroom");
            case PRESET_FRIENDS: return !username.endsWith("@chatroom") && !official(username);
            case PRESET_OFFICIALS: return official(username);
            case MANUAL:
            case SQL: return members.contains(username);
            default: return true;
        }
    }

    static boolean official(String name) {
        return name != null && (name.startsWith("gh_") || name.equals("officialaccounts") || name.equals("service_officialaccounts"));
    }

    static String username(Object value) {
        Object name = field(value, "field_username", "username");
        return name instanceof String ? (String) name : null;
    }

    static int unread(Object value) {
        Object normal = field(value, "field_unReadCount", "unReadCount");
        Object muted = field(value, "field_unReadMuteCount", "unReadMuteCount");
        return (normal instanceof Number ? ((Number) normal).intValue() : 0)
                + (muted instanceof Number ? ((Number) muted).intValue() : 0);
    }

    private static Object field(Object value, String... names) {
        if (value == null) return null;
        for (String name : names) {
            if (value instanceof Map && ((Map<?, ?>) value).containsKey(name)) return ((Map<?, ?>) value).get(name);
            for (Field field : fields(value.getClass(), name)) {
                try { return field.get(value); }
                catch (ReflectiveOperationException ignored) {
                    // Access can fail for this instance; do not turn a resolved field into a class miss.
                }
            }
        }
        return null;
    }

    private static List<Field> fields(Class<?> actualClass, String name) {
        synchronized (FIELDS) {
            Map<String, List<Field>> names = FIELDS.get(actualClass);
            if (names == null) { names = new HashMap<>(); FIELDS.put(actualClass, names); }
            List<Field> saved = names.get(name);
            if (saved != null) return saved;
            List<Field> found = new ArrayList<>();
            for (Class<?> type = actualClass; type != null; type = type.getSuperclass()) {
                try {
                    Field field = type.getDeclaredField(name);
                    field.setAccessible(true);
                    found.add(field);
                } catch (NoSuchFieldException ignored) { }
            }
            saved = Collections.unmodifiableList(found);
            names.put(name, saved);
            return saved;
        }
    }

    public void onHomeReady(Activity activity) {
        if (activity == null || !"com.tencent.mm.ui.LauncherUI".equals(activity.getClass().getName()) || activity.getWindow() == null) return;
        WeakReference<Activity> ref = new WeakReference<>(activity);
        activity.getWindow().getDecorView().post(new Runnable() {
            int attempts;
            @Override public void run() {
                Activity target = ref.get();
                if (target == null || target.isDestroyed() || target.isFinishing()) return;
                ListView list = findList(target.getWindow().getDecorView());
                // Diagnostics run regardless of the grouping switch; the host mount stays gated.
                if (list != null) ConversationListBridge.traceList(list, "home-ready");
                if (!enabled) return;
                if (list != null && ConversationListBridge.supports(ConversationListBridge.unwrapAdapter(list.getAdapter()))) {
                    mount(list);
                } else if (++attempts < 12) MAIN.postDelayed(this, 150);
                else log("列表未准备好或当前后端不支持；未改动原列表");
            }
        });
    }

    public void releaseList(ListView list) {
        WeakReference<ConversationGroupTabsHost> ref = HOSTS.remove(list);
        ConversationGroupTabsHost host = ref == null ? null : ref.get();
        if (list != null) ConversationListBridge.traceList(list, "release-header");
        if (host != null) host.dispose();
    }

    private static void mount(ListView list) {
        if (!enabled || Looper.myLooper() != Looper.getMainLooper()) return;
        WeakReference<ConversationGroupTabsHost> ref = HOSTS.get(list);
        if (ref != null && ref.get() != null) return;
        // Header registration must happen after the current adapter bind returns.
        ConversationListBridge.traceList(list, "mount-request");
        HOSTS.put(list, new WeakReference<>(null));
        list.post(() -> {
            if (!list.isAttachedToWindow() || !enabled) { HOSTS.remove(list); return; }
            WeakReference<ConversationGroupTabsHost> existing = HOSTS.get(list);
            if (existing != null && existing.get() != null) return;
            try {
                ConversationGroupTabsHost host = new ConversationGroupTabsHost(list);
                ConversationListBridge.traceList(list, "new-header");
                host.install(); HOSTS.put(list, new WeakReference<>(host));
            } catch (Exception error) { HOSTS.remove(list); log("分组栏挂载失败 " + error.getClass().getSimpleName()); }
        });
    }

    private static ListView findList(View root) {
        ArrayDeque<View> queue = new ArrayDeque<>(); queue.add(root);
        for (int visited = 0; !queue.isEmpty() && visited < 1024; visited++) {
            View view = queue.removeFirst();
            if (view instanceof ListView && view.getClass().getName().equals("com.tencent.mm.ui.conversation.ConversationListView")) return (ListView) view;
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) queue.addLast(group.getChildAt(i));
            }
        }
        return null;
    }

    private static void log(String message) { XposedBridge.log("WEX: 用户分组: " + message); }
}
