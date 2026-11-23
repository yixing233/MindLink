package wex.ui.core.feature;

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HeaderViewListAdapter;
import android.widget.ListView;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import dev.mindlink.xposed.XC_MethodHook;
import dev.mindlink.xposed.XposedBridge;
import dev.mindlink.xposed.callbacks.XC_LoadPackage;

/** Host ListView binding with one raw/visible index contract. Resolver misses leave the host untouched. */
public final class ConversationListBridge {
    public interface BindListener {
        void onBind(Object adapter, View row, Object conversation, int position,
                    int itemCount, Object previous, Object next);
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final CopyOnWriteArrayList<BindListener> LISTENERS = new CopyOnWriteArrayList<BindListener>();
    private static final List<Binding> BINDINGS = new ArrayList<Binding>();
    private static final Set<Method> HOOKED = new HashSet<Method>();
    private static final Set<Method> CLICK_HOOKS = new HashSet<Method>();
    private static final WeakHashMap<View, String> ROW_IDENTITIES = new WeakHashMap<View, String>();
    // Values never retain their weak adapter keys.
    private static final WeakHashMap<Object, AdapterState> STATES = new WeakHashMap<Object, AdapterState>();
    private static final WeakHashMap<ListView, ScrollTrace> SCROLL_TRACES = new WeakHashMap<>();
    // Lists seen binding before they sit under the LauncherUI decor; retried on later binds.
    private static final Map<ListView, Boolean> PENDING_TRACES = new WeakHashMap<>();
    // One pending-state stage line per list instance; the watch evidence must not spam.
    private static final Set<ListView> PENDING_WATCH_LOGGED = Collections.newSetFromMap(new WeakHashMap<ListView, Boolean>());
    private static final Set<Method> SCROLL_TRACE_HOOKS = new HashSet<>();
    private static final int MAX_SCROLL_TRACE = 48;
    private static final ThreadLocal<Integer> RAW_DEPTH = new ThreadLocal<Integer>();
    private static WeakReference<Object> latestAdapter = new WeakReference<Object>(null);
    private static WeakReference<ListView> latestList = new WeakReference<ListView>(null);
    private static final ConversationAdapterSnapshotReader READER = new ConversationAdapterSnapshotReader();
    private static boolean initializing, changingGroup;
    private static volatile String status = "会话列表尚未解析";
    private static volatile Method emptyFooter;
    private static volatile Field actionBarField;

    private ConversationListBridge() { }

    public static synchronized void initialize(XC_LoadPackage.LoadPackageParam lpparam) {
        if (initializing) return;
        initializing = true;
        HostTargetResolver.resolveMethod("conversation_mvvm_get_view_v2",
                new HostTargetResolver.MethodSpec()
                        .classStrings("MicroMsg.ConversationAdapter.MvvmConversationAdapter",
                                "Get Item duplicated: positionMaps: %s username [%s, %d] Map: %s datas: %d")
                        .name("getView").returnType("android.view.View")
                        .parameterTypes("int", "android.view.View", "android.view.ViewGroup"),
                new HostTargetResolver.MethodCallback() {
                    @Override public void onResolved(Method method) { MAIN.post(() -> install(method)); }
                    @Override public void onFailure(Throwable error) {
                        status = "会话列表解析失败，保留原列表";
                        log(status + ": " + error.getClass().getSimpleName());
                    }
                });
        HostTargetResolver.resolveMethod("conversation_empty_footer_v1",
                new HostTargetResolver.MethodSpec()
                        .declaredClass("com.tencent.mm.ui.conversation.ConversationListView")
                        .parameterCount(0).returnType("void")
                        .usingExactStrings("[checkEmptyFooter] isRealFull:", "[checkEmptyFooter] setSelection"),
                new HostTargetResolver.MethodCallback() {
                    @Override public void onResolved(Method method) { emptyFooter = method; }
                    @Override public void onFailure(Throwable error) { log("分组填充校正解析失败 " + error.getClass().getSimpleName()); }
                });
        HostTargetResolver.resolveMethod("conversation_action_bar_reader_v1",
                new HostTargetResolver.MethodSpec()
                        .declaredClass("com.tencent.mm.ui.conversation.ConversationListView")
                        .parameterCount(0).returnType("void").usingExactStrings("resetActionBarView"),
                new HostTargetResolver.MethodCallback() {
                    @Override public void onResolved(Method method) {
                        try { actionBarField = HostTargetResolver.fieldReadBy(method, View.class); }
                        catch (Exception error) { onFailure(error); }
                    }
                    @Override public void onFailure(Throwable error) { log("分组顶栏读取解析失败 " + error.getClass().getSimpleName()); }
                });
    }

    public static String status() { return status; }
    public static boolean supports(Object adapter) { return binding(adapter) != null; }
    public static void addListener(BindListener listener) { if (listener != null) LISTENERS.addIfAbsent(listener); }
    public static void removeListener(BindListener listener) { LISTENERS.remove(listener); }
    public static Object currentAdapter() { return latestAdapter.get(); }
    public static ListView currentList() { return latestList.get(); }
    static View actionBar(ListView list) {
        Field field = actionBarField;
        if (field == null || !field.getDeclaringClass().isInstance(list)) return null;
        try { return (View) field.get(list); }
        catch (IllegalAccessException error) { return null; }
    }

    public static void refresh(boolean scrollToTop) {
        MAIN.post(() -> refreshNow(scrollToTop));
    }

    static void changeGroup(BooleanSupplier publish, boolean scrollToTop) {
        if (Looper.myLooper() != Looper.getMainLooper() || changingGroup) {
            MAIN.post(() -> changeGroup(publish, scrollToTop));
            return;
        }
        changingGroup = true;
        try {
            ListView list = latestList.get();
            Object adapter = latestAdapter.get();
            // endFling can synchronously call a scroll listener; publish rechecks the request token.
            // Resolved members changing on resume still need this boundary, even without a tab change.
            if (list != null && adapter != null && unwrapAdapter(list.getAdapter()) == adapter) list.smoothScrollBy(0, 0);
            if (publish.getAsBoolean()) refreshNow(scrollToTop);
        } finally { changingGroup = false; }
    }

    private static void refreshNow(boolean scrollToTop) {
        synchronized (STATES) {
            for (AdapterState state : STATES.values()) state.dirty();
        }
        Object adapter = latestAdapter.get();
        ListView list = latestList.get();
        if (adapter == null || list == null || unwrapAdapter(list.getAdapter()) != adapter) {
            reportOnce("切换分组未刷新：" + (adapter == null ? "尚无已绑定的适配器" : list == null ? "尚无会话列表" : "列表适配器已更换"));
            return;
        }
        try {
            traceList(list, scrollToTop ? "refresh-top" : "refresh-data");
            method(adapter.getClass(), "notifyDataSetChanged").invoke(adapter);
            if (scrollToTop) {
                Method footer = emptyFooter;
                if (footer != null && footer.getDeclaringClass().isInstance(list)) footer.invoke(list);
                list.setSelection(0);
            }
        } catch (Exception error) { log("刷新失败 " + error.getClass().getSimpleName()); }
    }

    static List<Object> rawRows() {
        Object adapter = currentAdapter();
        Binding target = binding(adapter);
        if (target == null) return Collections.emptyList();
        try {
            Snapshot snapshot = snapshot(adapter, target, rawCount(adapter, target));
            return snapshot == null ? Collections.emptyList() : snapshot.rows;
        } catch (Throwable error) { return Collections.emptyList(); }
    }

    private static synchronized void install(Method getView) {
        try {
            final Binding target = new Binding(getView);
            if (HOOKED.contains(getView)) return;
            // Cache accessors are unsafe unless BOTH data notification paths can dirty first.
            hookNotify(method(target.owner, "notifyDataSetChanged"));
            hookNotify(method(target.owner, "notifyDataSetInvalidated"));
            hookAccessor(target.count, false);
            hookAccessor(target.item, true);
            for (String name : new String[]{"getItemId", "getItemViewType", "isEnabled"}) {
                try { hookPositionMethod(method(target.owner, name, int.class)); }
                catch (NoSuchMethodException ignored) { }
            }
            XposedBridge.hookMethod(getView, new XC_MethodHook(100) {
                @Override protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (rawMode() || binding(param.thisObject) != target) return;
                    Object adapter = param.thisObject;
                    int visiblePosition = (Integer) param.args[0];
                    Snapshot snapshot = snapshot(adapter, target, rawCount(adapter, target));
                    if (snapshot == null) return; // No successful snapshot: preserve the native host call.
                    int raw = snapshot.positions.raw(visiblePosition);
                    if (raw < 0) {
                        View empty = new View(((ViewGroup) param.args[2]).getContext());
                        empty.setLayoutParams(new android.widget.AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
                        empty.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                        param.setResult(empty);
                        refresh(false);
                        return;
                    }
                    Object[] args = param.args.clone();
                    args[0] = raw;
                    View row = (View) original(getView, adapter, args);
                    param.setResult(row);
                    latestAdapter = new WeakReference<Object>(adapter);
                    if (args[2] instanceof ListView) {
                        ListView list = (ListView) args[2];
                        // Retry while the list is only pending (first binds can precede decor attach).
                        if (latestList.get() != list || scrollTracePending(list)) watchScrollTrace(list);
                        latestList = new WeakReference<ListView>(list);
                        hookClicks(list);
                    }
                    if (row != null) ROW_IDENTITIES.put(row, ConversationGroupingFeature.username(snapshot.rows.get(raw)));
                    if (row != null) for (BindListener listener : LISTENERS) {
                        try {
                            int previous = snapshot.positions.raw(visiblePosition - 1);
                            int next = snapshot.positions.raw(visiblePosition + 1);
                            listener.onBind(adapter, row, snapshot.rows.get(raw), visiblePosition,
                                    snapshot.positions.size(), previous < 0 ? null : snapshot.rows.get(previous),
                                    next < 0 ? null : snapshot.rows.get(next));
                        } catch (Exception error) { log("绑定监听失败 " + error.getClass().getSimpleName()); }
                    }
                }
            });
            HOOKED.add(getView);
            BINDINGS.add(target);
            status = "已解析 ListView 会话适配器";
            log(status + " " + target.owner.getName());
        } catch (Exception error) {
            status = "会话接口不完整，保持原列表";
            log(status + ": " + error.getClass().getSimpleName());
        }
    }

    private static void hookNotify(Method notify) {
        if (HOOKED.contains(notify)) return;
        XposedBridge.hookMethod(notify, new XC_MethodHook(100) {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                if (binding(param.thisObject) == null) return;
                synchronized (STATES) { state(param.thisObject).dirty(); }
                ConversationGroupingFeature.refreshHostLabels();
            }
        });
        HOOKED.add(notify);
    }

    private static void hookClicks(ListView list) {
        for (Object listener : new Object[]{list.getOnItemClickListener(), list.getOnItemLongClickListener()}) {
            if (listener == null) continue;
            boolean longClick = listener instanceof android.widget.AdapterView.OnItemLongClickListener;
            try {
                Method click = method(listener.getClass(), longClick ? "onItemLongClick" : "onItemClick",
                        android.widget.AdapterView.class, View.class, int.class, long.class);
                if (CLICK_HOOKS.contains(click)) continue;
                XposedBridge.hookMethod(click, new XC_MethodHook(100) {
                    @Override protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (rawMode() || ConversationGroupingFeature.isAllActive() || !(param.args[0] instanceof ListView)) return;
                        ListView source = (ListView) param.args[0];
                        Object adapter = unwrapAdapter(source.getAdapter());
                        Binding target = binding(adapter);
                        if (target == null) return;
                        View row = param.args[1] instanceof View ? (View) param.args[1] : null;
                        int visible = row == null ? -1 : source.getPositionForView(row) - source.getHeaderViewsCount();
                        String identity = row == null ? null : ROW_IDENTITIES.get(row);
                        Snapshot current = snapshot(adapter, target, rawCount(adapter, target));
                        int raw = current == null ? -1 : current.positions.raw(visible);
                        String currentIdentity = null;
                        if (raw >= 0) {
                            try {
                                currentIdentity = ConversationGroupingFeature.username(original(target.item, adapter, new Object[]{raw}));
                            } catch (Throwable ignored) { }
                        }
                        if (identity == null || raw < 0 || !identity.equals(currentIdentity)) {
                            param.setResult(longClick ? Boolean.FALSE : null);
                            refresh(false);
                            return;
                        }
                        Object[] args = param.args.clone();
                        args[2] = raw + source.getHeaderViewsCount();
                        param.setResult(original(click, param.thisObject, args));
                    }
                });
                CLICK_HOOKS.add(click);
            } catch (ReflectiveOperationException error) { log("会话点击接口未就绪 " + error.getClass().getSimpleName()); }
        }
    }

    private static void hookAccessor(Method accessor, boolean item) {
        if (HOOKED.contains(accessor)) return;
        XposedBridge.hookMethod(accessor, new XC_MethodHook(100) {
            @Override protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (rawMode()) return;
                Binding target = binding(param.thisObject);
                if (target == null || ConversationGroupingFeature.isAllActive()) return;
                Snapshot snapshot = snapshot(param.thisObject, target, rawCount(param.thisObject, target));
                if (snapshot == null) return;
                if (!item) { param.setResult(snapshot.positions.size()); return; }
                int raw = snapshot.positions.raw((Integer) param.args[0]);
                param.setResult(raw < 0 ? null : original(accessor, param.thisObject, new Object[]{raw}));
            }
        });
        HOOKED.add(accessor);
    }

    private static void hookPositionMethod(Method method) {
        if (HOOKED.contains(method)) return;
        XposedBridge.hookMethod(method, new XC_MethodHook(100) {
            @Override protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (rawMode() || ConversationGroupingFeature.isAllActive()) return;
                Binding target = binding(param.thisObject);
                if (target == null) return;
                Snapshot snapshot = snapshot(param.thisObject, target, rawCount(param.thisObject, target));
                if (snapshot == null) return;
                int raw = snapshot.positions.raw((Integer) param.args[0]);
                if (raw >= 0) param.setResult(original(method, param.thisObject, new Object[]{raw}));
                else if ("isEnabled".equals(method.getName())) param.setResult(Boolean.FALSE);
                else if ("getItemId".equals(method.getName())) param.setResult(-1L);
                else param.setResult(-1);
            }
        });
        HOOKED.add(method);
    }

    private static Snapshot snapshot(Object adapter, Binding target, int count) {
        synchronized (STATES) {
            AdapterState state = state(adapter);
            long epoch = state.dataEpoch;
            long generation = ConversationGroupingFeature.generation();
            Snapshot saved = state.snapshot;
            if (saved != null && saved.dataEpoch == epoch && saved.generation == generation && saved.rawCount == count) return saved;
            state.snapshot = null;
            if (state.building || (state.failed && state.failedEpoch == epoch
                    && state.failedGeneration == generation && state.failedCount == count)) return null;
            state.building = true;
            try {
                List<Object> rows = READER.read(adapter, count, index -> {
                    try { return original(target.item, adapter, new Object[]{index}); }
                    catch (Throwable error) { throw new IllegalStateException("Original getItem failed", error); }
                });
                if (rows == null) throw new IllegalStateException("MVVM backing list unavailable");
                rows = Collections.unmodifiableList(new ArrayList<Object>(rows));
                ConversationPositions positions = ConversationPositions.build(rows, ConversationGroupingFeature::matches);
                if (state.dataEpoch != epoch || ConversationGroupingFeature.generation() != generation
                        || rows.size() != count || rawCount(adapter, target) != count) {
                    reportOnce("快照与适配器条数不一致 rows=" + rows.size() + " count=" + count);
                    return null;
                }
                Snapshot next = new Snapshot(rows, positions, epoch, generation, count);
                state.snapshot = next;
                state.failed = false;
                return next;
            } catch (Throwable error) {
                state.failed = true;
                state.failedEpoch = epoch; state.failedGeneration = generation; state.failedCount = count;
                status = "MVVM backing list 未识别，保留原列表";
                Throwable cause = error.getCause() != null ? error.getCause() : error;
                // Host messages can contain user data; record failure types only.
                reportOnce("分组快照失败 " + error.getClass().getSimpleName() + " / " + cause.getClass().getName());
                return null;
            } finally { state.building = false; }
        }
    }

    // Called while holding STATES; no strong adapter reference is stored in the value.
    private static AdapterState state(Object adapter) {
        AdapterState state = STATES.get(adapter);
        if (state == null) { state = new AdapterState(); STATES.put(adapter, state); }
        return state;
    }

    private static Binding binding(Object adapter) {
        if (adapter == null) return null;
        for (Binding target : BINDINGS) if (target.owner.isInstance(adapter)) return target;
        return null;
    }

    private static int rawCount(Object adapter, Binding target) throws Throwable {
        return ((Number) original(target.count, adapter, new Object[0])).intValue();
    }

    private static Object original(Method method, Object receiver, Object[] args) throws Throwable {
        Integer before = RAW_DEPTH.get();
        RAW_DEPTH.set(before == null ? 1 : before + 1);
        try {
            if (!originBroken) {
                try { return XposedBridge.invokeOriginalMethod(method, receiver, args); }
                catch (XposedBridge.OriginalUnavailable unavailable) {
                    originBroken = true;
                    log("原方法调用不可用，改为直接调用 " + unavailable.getCause());
                }
            }
            // RAW_DEPTH is set, so every grouping hook on this method passes through untouched:
            // a plain call returns exactly the host's own result.
            try { method.setAccessible(true); return method.invoke(receiver, args); }
            catch (java.lang.reflect.InvocationTargetException error) {
                throw error.getCause() != null ? error.getCause() : error;
            }
        }
        finally { if (before == null) RAW_DEPTH.remove(); else RAW_DEPTH.set(before); }
    }

    /** Set once the framework's ORIGIN invoker failed; later calls go straight to the plain path. */
    private static volatile boolean originBroken;
    private static final java.util.Set<String> REPORTED = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    /** One log line per distinct reason: these paths run per row and per frame. */
    private static void reportOnce(String reason) { if (REPORTED.add(reason)) log(reason); }

    private static boolean rawMode() { Integer depth = RAW_DEPTH.get(); return depth != null && depth > 0; }
    static Method method(Class<?> type, String name, Class<?>... args) throws NoSuchMethodException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try { Method method = current.getDeclaredMethod(name, args); method.setAccessible(true); return method; }
            catch (NoSuchMethodException ignored) { }
        }
        throw new NoSuchMethodException(name);
    }

    public static Object unwrapAdapter(Object adapter) {
        return adapter instanceof HeaderViewListAdapter ? ((HeaderViewListAdapter) adapter).getWrappedAdapter() : adapter;
    }

    // Event-only diagnostics. Never scan rows, query settings, stringify host objects or log frames.
    static void traceList(ListView list, String event) {
        try {
            watchScrollTrace(list);
            traceScroll(list, event, unwrapAdapter(list.getAdapter()), null, "module");
        } catch (Throwable ignored) { }
    }

    private static void watchScrollTrace(ListView list) {
        if (list == null) return;
        // The known conversation ListView remains the primary target; the adapter-binding
        // fallback below covers hosts where the visible list class differs (obfuscated
        // subclass or a RecyclerView-backed shell) so scroll tracing cannot silently stay empty.
        if ("com.tencent.mm.ui.conversation.ConversationListView".equals(list.getClass().getName())) {
            tryTrace(list);
            return;
        }
        if (latestList.get() == list && binding(unwrapAdapter(list.getAdapter())) != null) {
            tryTrace(list);
            return;
        }
        // Not a traceable list at all; leave it unregistered without polling.
    }

    private static void tryTrace(ListView list) {
        try {
            synchronized (SCROLL_TRACES) {
                if (SCROLL_TRACES.containsKey(list) || PENDING_TRACES.containsKey(list)) return;
            }
            if (!traceHomeList(list)) {
                // First bind can happen before the list sits under the LauncherUI decor; keep
                // it pending so later binds/traceList calls retry instead of never observing.
                synchronized (SCROLL_TRACES) {
                    if (!SCROLL_TRACES.containsKey(list)) PENDING_TRACES.put(list, Boolean.TRUE);
                }
                return;
            }
            registerScrollTrace(list);
            HostStageLog.write("group-scroll", "schema=trace-watch event=registered list="
                    + System.identityHashCode(list) + " class=" + list.getClass().getName());
        } catch (Throwable ignored) { }
    }

    private static void registerScrollTrace(ListView list) {
        synchronized (SCROLL_TRACES) {
            if (SCROLL_TRACES.containsKey(list)) return;
            PENDING_TRACES.remove(list);
            SCROLL_TRACES.put(list, new ScrollTrace());
        }
        for (String name : new String[]{"setSelection", "setSelectionFromTop", "setAdapter"}) {
            try {
                Method target = "setAdapter".equals(name)
                        ? method(list.getClass(), name, android.widget.ListAdapter.class)
                        : "setSelection".equals(name) ? method(list.getClass(), name, int.class)
                        : method(list.getClass(), name, int.class, int.class);
                synchronized (SCROLL_TRACE_HOOKS) {
                    if (SCROLL_TRACE_HOOKS.contains(target)) continue;
                    XposedBridge.hookMethod(target, new XC_MethodHook(100) {
                        @Override protected void beforeHookedMethod(MethodHookParam param) {
                            try {
                                if (!(param.thisObject instanceof ListView)) return;
                                ListView source = (ListView) param.thisObject;
                                // Chat return resets the list to its first banner through
                                // several host paths (layout, footer/actionbar helpers), not
                                // only onLayout. Hold any host-originated reset to the top
                                // while the user is scrolled into a grouped list; explicit
                                // tab switches and module refreshes still reset.
                                String origin = selectionOrigin();
                                int target = param.args[0] instanceof Integer ? (Integer) param.args[0] : -1;
                                int top = Math.max(1, source.getHeaderViewsCount());
                                if (!changingGroup && !"module-refresh".equals(origin) && target >= 0 && target <= top
                                        && source.getFirstVisiblePosition() > top
                                        && ConversationGroupingFeature.isEnabled()
                                        && "com.tencent.mm.ui.conversation.ConversationListView".equals(source.getClass().getName())
                                        && binding(unwrapAdapter(source.getAdapter())) != null
                                        && traceHomeList(source)) {
                                    param.setResult(null);
                                    traceScroll(source, "host-reset-held", unwrapAdapter(source.getAdapter()), param.args, origin);
                                    return;
                                }
                                synchronized (SCROLL_TRACES) {
                                    ScrollTrace trace = SCROLL_TRACES.get(source);
                                    if (trace == null || trace.events >= MAX_SCROLL_TRACE) return;
                                }
                                Object adapter = "setAdapter".equals(name) ? unwrapAdapter(param.args[0])
                                        : unwrapAdapter(source.getAdapter());
                                traceScroll(source, name, adapter,
                                        "setAdapter".equals(name) ? null : param.args,
                                        "setAdapter".equals(name) ? "host-adapter" : selectionOrigin());
                            } catch (Throwable ignored) { }
                        }
                    });
                    SCROLL_TRACE_HOOKS.add(target);
                }
            } catch (Throwable ignored) { }
        }
    }

    private static boolean traceHomeList(ListView list) {
        // The known class is verified against the decompiled 8.0.76 host; any other ListView
        // only qualifies when it is the adapter-bound list currently driving home binds.
        if (!"com.tencent.mm.ui.conversation.ConversationListView".equals(list.getClass().getName())
                && latestList.get() != list) return false;
        android.app.Activity activity = ChatToolbarLifecycle.findActivity(list.getContext());
        if (activity == null || !"com.tencent.mm.ui.LauncherUI".equals(activity.getClass().getName())
                || activity.isDestroyed() || activity.isFinishing() || activity.getWindow() == null) return false;
        View decor = activity.getWindow().getDecorView();
        View current = list;
        for (int depth = 0; current != null && depth < 32; depth++) {
            if (current == decor) return true;
            current = current.getParent() instanceof View ? (View) current.getParent() : null;
        }
        return false;
    }

    private static void traceScroll(ListView list, String event, Object adapter, Object[] selection, String origin) {
        try {
            synchronized (SCROLL_TRACES) {
                ScrollTrace trace = SCROLL_TRACES.get(list);
                if (trace == null) {
                    // A pending list has not passed window ownership yet; retry registration
                    // and drop this event (never fabricate an event without the ownership check).
                    if (!PENDING_TRACES.containsKey(list)) {
                        if (PENDING_WATCH_LOGGED.add(list)) HostStageLog.write("group-scroll",
                                "schema=trace-watch event=pending list=" + System.identityHashCode(list)
                                        + " class=" + list.getClass().getName());
                        return;
                    }
                }
            }
            if (!SCROLL_TRACES.containsKey(list)) {
                if (traceHomeList(list)) registerScrollTrace(list);
                else return;
            }
            synchronized (SCROLL_TRACES) {
                ScrollTrace trace = SCROLL_TRACES.get(list);
                if (trace == null || trace.events >= MAX_SCROLL_TRACE) return;
            }
            if (!traceHomeList(list)) return;
            String stage;
            synchronized (SCROLL_TRACES) {
                ScrollTrace trace = SCROLL_TRACES.get(list);
                if (trace == null || trace.events >= MAX_SCROLL_TRACE) return;
                long generation = ConversationGroupingFeature.generation();
                stage = "schema=group-scroll-v2 event=" + event + " list=" + System.identityHashCode(list)
                        + " adapterChanged=" + (trace.adapter.get() != adapter)
                        + " generationSame=" + (trace.generation == generation) + " origin=" + origin;
                if (selection != null) {
                    stage += " position=" + ((Number) selection[0]).intValue();
                    if (selection.length == 2) stage += " top=" + ((Number) selection[1]).intValue();
                }
                trace.adapter = new WeakReference<>(adapter); trace.generation = generation; trace.events++;
            }
            HostStageLog.write("group-scroll", stage);
        } catch (Throwable ignored) { }
    }

    private static boolean scrollTracePending(ListView list) {
        synchronized (SCROLL_TRACES) { return PENDING_TRACES.containsKey(list); }
    }

    private static String selectionOrigin() {
        // Only fixed class/method labels leave this process; never emit stack trace contents.
        for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
            String type = frame.getClassName(), name = frame.getMethodName();
            if ("wex.ui.core.feature.ConversationListBridge".equals(type) && "refreshNow".equals(name)) return "module-refresh";
            if ("com.tencent.mm.ui.conversation.ConversationListView".equals(type)) {
                if ("onLayout".equals(name)) return "layout";
                if ("f".equals(name)) return "footer";
                if ("B".equals(name) || "w".equals(name) || "y".equals(name)) return "actionbar";
            }
            if ("com.tencent.mm.ui.conversation.MainUI".equals(type)) {
                if ("onResume".equals(name)) return "resume";
                if ("m0".equals(name)) return "mainui-setup";
            }
            if ("wex.ui.core.feature.FilterBarPlacement".equals(type)
                    && ("onLayoutChange".equals(name) || "install".equals(name) || "reserve".equals(name))) return "filterbar";
        }
        return "other";
    }

    private static final class ScrollTrace {
        int events;
        long generation = ConversationGroupingFeature.generation();
        WeakReference<Object> adapter = new WeakReference<>(null);
    }

    private static final class Binding {
        final Class<?> owner;
        final Method count, item;
        Binding(Method view) throws NoSuchMethodException {
            owner = view.getDeclaringClass(); count = method(owner, "getCount"); item = method(owner, "getItem", int.class);
        }
    }

    private static final class AdapterState {
        long dataEpoch, failedEpoch, failedGeneration;
        int failedCount;
        boolean failed, building;
        Snapshot snapshot;
        void dirty() { dataEpoch++; snapshot = null; failed = false; }
    }

    private static final class Snapshot {
        final List<Object> rows;
        final ConversationPositions positions;
        final long dataEpoch, generation;
        final int rawCount;
        Snapshot(List<Object> rows, ConversationPositions positions, long dataEpoch, long generation, int rawCount) {
            this.rows = rows; this.positions = positions; this.dataEpoch = dataEpoch;
            this.generation = generation; this.rawCount = rawCount;
        }
    }

    private static void log(String message) { XposedBridge.log("WEX: 会话列表桥: " + message); }
}
