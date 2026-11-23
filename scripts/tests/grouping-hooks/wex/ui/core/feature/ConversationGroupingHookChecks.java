package wex.ui.core.feature;

import android.content.Context;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.HeaderViewListAdapter;
import android.widget.ListView;
import dev.mindlink.xposed.XposedBridge;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordingFile;

/** Runs actual production callbacks, not a copy of the snapshot/cache algorithm. */
public final class ConversationGroupingHookChecks {
    static final Context CONTEXT = new Context();
    static int builds;
    static final class CountRow extends HashMap<String, Object> {
        Runnable onMatch;
        CountRow(String name, int unread) {
            put("field_username", name); put("field_unReadCount", unread); put("field_unReadMuteCount", 0);
        }
        @Override public boolean containsKey(Object key) {
            if ("field_username".equals(key) && StackWalker.getInstance().walk(frames -> frames.anyMatch(
                    frame -> frame.getClassName().equals(ConversationPositions.class.getName()) && frame.getMethodName().equals("build")))) {
                builds++;
                if (onMatch != null) { Runnable task = onMatch; onMatch = null; task.run(); }
            }
            return super.containsKey(key);
        }
    }
    public static class BaseAdapter implements android.widget.ListAdapter {
        List<Object> rows = new ArrayList<>();
        int countOverride = -1, itemCalls, notifications;
        Runnable observer;
        public int getCount() { return countOverride < 0 ? rows.size() : countOverride; }
        public Object getItem(int position) { itemCalls++; return rows.get(position); }
        public long getItemId(int position) { return 100L + position; }
        public int getItemViewType(int position) { return position + 10; }
        public boolean isEnabled(int position) { return position % 2 == 0; }
        public void notifyDataSetChanged() { notifications++; if (observer != null) observer.run(); }
        public void notifyDataSetInvalidated() { notifications++; if (observer != null) observer.run(); }
    }
    public static class Adapter extends BaseAdapter {
        public View getView(int position, View recycled, ViewGroup parent) { return new RowView(position); }
    }
    public static final class SecondAdapter extends BaseAdapter {
        public View getView(int position, View recycled, ViewGroup parent) { return new RowView(position); }
    }
    static final class RowView extends View {
        final int raw;
        RowView(int raw) { super(CONTEXT); this.raw = raw; }
    }
    public static final class Clicks implements AdapterView.OnItemClickListener {
        int last = -1, calls;
        public void onItemClick(AdapterView parent, View row, int position, long id) { last = position; calls++; }
    }
    public static final class LongClicks implements AdapterView.OnItemLongClickListener {
        final Clicks clicks;
        LongClicks(Clicks clicks) { this.clicks = clicks; }
        public boolean onItemLongClick(AdapterView parent, View row, int position, long id) {
            clicks.last = position; clicks.calls++; return true;
        }
    }
    static Object call(Object owner, String name, Object... args) throws Throwable {
        Class<?>[] types;
        if (name.equals("getView")) types = new Class<?>[]{int.class, View.class, ViewGroup.class};
        else if (name.startsWith("onItem")) types = new Class<?>[]{AdapterView.class, View.class, int.class, long.class};
        else types = args.length == 0 ? new Class<?>[0] : new Class<?>[]{int.class};
        return XposedBridge.invoke(owner, name, types, args);
    }
    static Object checkedCall(Object owner, String name, Object... args) {
        try { return call(owner, name, args); }
        catch (Throwable error) { throw new AssertionError("hook call " + name, error); }
    }
    static void install(Class<?> owner) throws Exception {
        Method method = ConversationListBridge.class.getDeclaredMethod("install", Method.class);
        method.setAccessible(true);
        method.invoke(null, owner.getDeclaredMethod("getView", int.class, View.class, ViewGroup.class));
    }
    static Adapter setup() throws Exception {
        wex.ui.core.WexStore.INSTANCE.values.put(ConversationGroupingFeature.KEY_ENABLED, "true");
        wex.ui.core.WexStore.INSTANCE.values.put(ConversationGroupingFeature.KEY_ACTIVE, "unread");
        ConversationGroupingFeature.reload(); Handler.drainForTest();
        install(Adapter.class);
        Adapter adapter = new Adapter();
        adapter.rows.add(new CountRow("dummy-a", 0));
        adapter.rows.add(new CountRow("dummy-b", 1));
        adapter.rows.add(new CountRow("dummy-c", 2));
        builds = 0;
        return adapter;
    }
    static ListView list(Adapter adapter, Clicks clicks) {
        ListView list = new ListView(CONTEXT);
        list.adapter = new HeaderViewListAdapter(adapter); list.click = clicks; list.longClick = new LongClicks(clicks);
        return list;
    }
    static View bind(Adapter adapter, ListView list, int visible) throws Throwable {
        list.visiblePosition = visible + list.headers;
        return (View) call(adapter, "getView", visible, null, list);
    }
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    static int count(Object adapter) throws Throwable { return (Integer) call(adapter, "getCount"); }
    /** libxposed's ORIGIN invoker unusable on device: filtering must still apply via the plain path. */
    static void originFallback() throws Throwable {
        XposedBridge.originBroken = true;
        Adapter adapter = setup(); Clicks clicks = new Clicks(); ListView list = list(adapter, clicks);
        check(count(adapter) == 2, "unread group filters even when the ORIGIN invoker throws");
        check(call(adapter, "getItem", 0) == adapter.rows.get(1), "mapped item read through the plain fallback");
        int attempts = XposedBridge.originCalls;
        for (int i = 0; i < 20; i++) count(adapter);
        check(XposedBridge.originCalls == attempts, "a broken invoker is tried once, not per row or frame");
        ConversationGroupingFeature.setActive(ConversationGroupStore.ALL_ID); Handler.drainForTest();
        check(count(adapter) == 3, "switching back to all still restores every row");
        System.out.println("PASS origin-fallback: grouping filters without the framework ORIGIN invoker");
    }
    static void epoch() throws Throwable {
        Adapter adapter = setup(); Clicks clicks = new Clicks(); ListView list = list(adapter, clicks);
        check(count(adapter) == 2, "initial unread mapping");
        for (int i = 0; i < 200; i++) {
            check(count(adapter) == 2, "stable count");
            check(call(adapter, "getItem", 0) == adapter.rows.get(1), "mapped item");
            check(call(adapter, "getItemViewType", 1).equals(12), "mapped type");
            check(call(adapter, "getItemId", 0).equals(101L), "mapped id");
            check(call(adapter, "isEnabled", 1).equals(true), "mapped enabled");
        }
        check(builds == 3, "same epoch must build exactly once; row predicate reads=" + builds);
        View row = bind(adapter, list, 0);
        int beforeExtraReads = builds;
        check(ConversationListBridge.rawRows().size() == 3, "rawRows all backing rows");
        call(clicks, "onItemClick", list, row, 1, 0L);
        check(clicks.last == 2, "header offset preserved");
        check(builds == beforeExtraReads, "rawRows/click do not rebuild snapshot");
        check(builds == 3, "same epoch must build exactly once; row predicate reads=" + builds);
        Object snapshot = snapshotOf(adapter);
        Field rows = snapshot.getClass().getDeclaredField("rows"); rows.setAccessible(true);
        try { ((List<?>) rows.get(snapshot)).clear(); throw new AssertionError("snapshot rows must be immutable"); }
        catch (UnsupportedOperationException expected) { }
        System.out.println("PASS epoch: 200 count/item/type/id/enabled cycles + bind/rawRows/click, one build, immutable rows");
    }
    static Object snapshotOf(Object adapter) throws Exception {
        for (Field field : ConversationListBridge.class.getDeclaredFields()) {
            if (!Map.class.isAssignableFrom(field.getType())) continue;
            field.setAccessible(true); Object entry = ((Map<?, ?>) field.get(null)).get(adapter);
            if (entry == null) continue;
            if (entry.getClass().getSimpleName().equals("Snapshot")) return entry;
            for (Field nested : entry.getClass().getDeclaredFields()) {
                if (nested.getType().getSimpleName().equals("Snapshot")) { nested.setAccessible(true); return nested.get(entry); }
            }
        }
        return null;
    }
    static void mutations() throws Throwable {
        Adapter adapter = setup(); check(count(adapter) == 2, "initial");
        final int[] observed = {-1};
        adapter.observer = () -> observed[0] = (Integer) checkedCall(adapter, "getCount");
        ((CountRow) adapter.rows.get(1)).put("field_unReadCount", 0);
        call(adapter, "notifyDataSetChanged");
        check(observed[0] == 1 && builds == 6, "same-count unread clearing is dirty before observer");
        ((CountRow) adapter.rows.get(0)).put("field_unReadCount", 3);
        call(adapter, "notifyDataSetChanged");
        check(observed[0] == 2 && builds == 9, "same-count new unread");
        Collections.swap(adapter.rows, 0, 2);
        call(adapter, "notifyDataSetChanged");
        check(call(adapter, "getItem", 0) == adapter.rows.get(0) && builds == 12, "same-count reorder/original order");
        adapter.rows.set(0, new CountRow("dummy-replacement", 0));
        call(adapter, "notifyDataSetChanged");
        check(observed[0] == 1 && call(adapter, "getItem", 0) == adapter.rows.get(2) && builds == 15, "same-count replacement");
        ((CountRow) adapter.rows.get(1)).put("field_unReadCount", 4);
        call(adapter, "notifyDataSetInvalidated");
        check(observed[0] == 2 && builds == 18, "invalidated before observer");
        ConversationGroupingFeature.setActive("manual");
        check(count(adapter) == 1 && call(adapter, "getItem", 0) == adapter.rows.get(1) && builds == 21, "group generation invalidates synchronously");
        ConversationGroupingFeature.setActive("unread");
        check(count(adapter) == 2 && builds == 24, "switch generation rebuilds once");
        adapter.rows.add(new CountRow("dummy-added", 1));
        check(count(adapter) == 3 && builds == 28, "raw count alone invalidates");
        // Install the real tab host in the actual feature weak host registry.
        ListView hostList = new ListView(CONTEXT);
        android.widget.FrameLayout parent = new android.widget.FrameLayout(CONTEXT);
        parent.addView(hostList, new ViewGroup.LayoutParams(400, 600));
        ConversationGroupTabsHost host = new ConversationGroupTabsHost(hostList); host.install();
        hosts().put(hostList, new WeakReference<>(host));
        Handler.drainForTest();
        int reads = wex.ui.core.WexStore.INSTANCE.boolReads.getOrDefault(ConversationGroupingFeature.KEY_EQUAL, 0);
        int notifications = adapter.notifications;
        for (int i = 0; i < 10; i++) call(adapter, "notifyDataSetChanged");
        Handler.drainForTest();
        check(adapter.notifications == notifications + 10, "host refresh must never notify-loop");
        check(wex.ui.core.WexStore.INSTANCE.boolReads.getOrDefault(ConversationGroupingFeature.KEY_EQUAL, 0) == reads + 1,
                "notify label refresh is independently coalesced");
        System.out.println("PASS mutations: unread clear/new, reorder, replacement, invalidation, grouping generation, count and coalesced labels");
    }
    static void click() throws Throwable {
        Adapter adapter = setup(); Clicks clicks = new Clicks(); ListView list = list(adapter, clicks);
        View row = bind(adapter, list, 0); int before = builds;
        call(clicks, "onItemClick", list, row, 1, 0L);
        check(clicks.last == 2 && builds == before, "click uses cached mapping plus lightweight identity");
        check(call(list.longClick, "onItemLongClick", list, row, 1, 0L).equals(true), "long click preserves delegate");
        // No notify: protect against stale backing-row identity without a full rescan.
        adapter.rows.set(1, new CountRow("dummy-other", 1));
        int calls = clicks.calls;
        call(clicks, "onItemClick", list, row, 1, 0L);
        check(clicks.calls == calls, "stale real raw identity must deny click");
        list.visiblePosition = 0;
        call(list.longClick, "onItemLongClick", list, new View(CONTEXT), 0, 0L);
        check(clicks.calls == calls, "untracked header must deny native chat click");
        Handler.drainForTest();
        View newRow = bind(adapter, list, 0);
        ((CountRow) adapter.rows.get(1)).put("field_unReadCount", 0);
        call(adapter, "notifyDataSetChanged");
        call(clicks, "onItemClick", list, newRow, 1, 0L);
        check(clicks.calls == calls, "old bound row after generation change denies click");
        System.out.println("PASS click: identity freshness, notified stale rows, header offsets and click/long-click delegates");
    }
    static void guards() throws Throwable {
        Adapter target = setup(); count(target);
        BaseAdapter other = new BaseAdapter(); other.rows.add(new CountRow("dummy-other", 1));
        check(count(other) == 1 && call(other, "getItem", 0) == other.rows.get(0), "shared BaseAdapter accessor guard");
        int before = builds; call(other, "notifyDataSetChanged"); call(other, "notifyDataSetInvalidated");
        check(count(target) == 2 && builds == before, "non-target notifications do not dirty target");
        install(SecondAdapter.class); install(Adapter.class);
        SecondAdapter second = new SecondAdapter();
        second.rows.add(new CountRow("dummy-second-a", 1));
        second.rows.add(new CountRow("dummy-second-b", 0));
        second.rows.add(new CountRow("dummy-second-c", 0));
        check(count(second) == 1 && call(second, "getItem", 0) == second.rows.get(0), "same-count adapters keep distinct snapshots");
        before = builds;
        check(count(target) == 2 && builds == before, "second adapter build leaves first generation cached");
        for (Map.Entry<Method, List<dev.mindlink.xposed.XC_MethodHook>> entry : XposedBridge.hooks.entrySet())
            check(entry.getValue().size() == 1, "deduplicate inherited methods " + entry.getKey().getName());
        check(call(target, "getItem", -1) == null && call(target, "getItem", 2) == null, "invalid visible item");
        check(call(target, "getItemId", 2).equals(-1L), "invalid mapped id must not use hidden raw row");
        check(call(target, "isEnabled", 2).equals(false), "invalid mapped row is disabled");
        System.out.println("PASS guards: shared inherited hooks, non-target adapters, hook deduplication and out-of-range positions");
    }
    static void notifyFailure() throws Throwable {
        XposedBridge.failHook = "notifyDataSetInvalidated";
        Adapter adapter = setup();
        check(!ConversationListBridge.supports(adapter), "both notify hooks required before publishing binding");
        check(count(adapter) == 3 && call(adapter, "getItem", 0) == adapter.rows.get(0), "notify installation failure leaves native accessor untouched");
        for (Method method : XposedBridge.hooks.keySet())
            check(method.getName().startsWith("notify"), "failure must not install accessor/getView cache");
        XposedBridge.failHook = null; install(Adapter.class);
        check(ConversationListBridge.supports(adapter) && count(adapter) == 2, "successful retry installs safely");
        System.out.println("PASS notify-failure: default deny before accessors and retry after partial notify installation");
    }
    static void snapshotFailure() throws Throwable {
        Adapter adapter = setup(); check(count(adapter) == 2, "success before failure");
        adapter.countOverride = 3; adapter.rows.clear(); call(adapter, "notifyDataSetChanged");
        check(count(adapter) == 3, "failed snapshot falls through to native count, not stale/empty success");
        check(snapshotOf(adapter) == null, "dirty failed snapshot cannot retain stale published success");
        int itemCalls = adapter.itemCalls;
        for (int i = 0; i < 100; i++) check(count(adapter) == 3, "failed generation remains native");
        check(adapter.itemCalls == itemCalls, "same failed epoch does not repeat snapshot probing");
        adapter.rows.add(new CountRow("dummy-restored", 1)); adapter.countOverride = -1;
        call(adapter, "notifyDataSetInvalidated"); check(count(adapter) == 1, "failure recovers after invalidation");
        ((CountRow) adapter.rows.get(0)).onMatch = () -> checkedCall(adapter, "notifyDataSetChanged");
        call(adapter, "notifyDataSetChanged");
        check(count(adapter) == 1 && snapshotOf(adapter) == null, "epoch changing during build cannot publish old success");
        check(count(adapter) == 1 && snapshotOf(adapter) != null, "next read builds the new epoch");
        System.out.println("PASS snapshot-failure: no empty/stale publication, failure memoization, recovery and mid-build epoch guard");
    }
    static void fields() throws Throwable {
        MissingMuted row = new MissingMuted();
        check(ConversationGroupingFeature.unread(row) == 1, "initial normal unread");
        Path recording = Files.createTempFile("mindlink-grouping-fields-", ".jfr");
        int misses = 0;
        try (Recording events = new Recording()) {
            events.enable("jdk.JavaExceptionThrow").withStackTrace(); events.start();
            for (int i = 0; i < 200; i++) {
                row.field_unReadCount = i;
                check(ConversationGroupingFeature.unread(row) == i, "positive metadata cache never caches live unread value");
                row.field_username = "dummy-" + i;
                check(ConversationGroupingFeature.username(row).equals(row.field_username), "username remains live");
            }
            events.stop(); events.dump(recording);
        }
        for (var event : RecordingFile.readAllEvents(recording)) {
            String message = event.getString("message");
            if (event.getClass("thrownClass").getName().equals("java.lang.NoSuchFieldException")
                    && ("field_unReadMuteCount".equals(message) || "unReadMuteCount".equals(message))
                    && event.getStackTrace().getFrames().stream().anyMatch(frame -> frame.getMethod().getType().getName().equals(ConversationGroupingFeature.class.getName()))) misses++;
        }
        Files.delete(recording);
        check(misses == 0, "200 missing-field reads must use class negative metadata cache; JFR misses=" + misses);
        AliasChild alias = new AliasChild();
        check(ConversationGroupingFeature.unread(alias) == 7, "alias-name priority before child hierarchy priority");
        check(ConversationGroupingFeature.username(alias).equals("dummy-parent"), "parent preferred field before child alternate alias");
        CountRow map = new CountRow("dummy-map", 0); map.remove("field_unReadMuteCount");
        check(ConversationGroupingFeature.unread(map) == 0, "map missing key");
        map.put("unReadMuteCount", 5);
        check(ConversationGroupingFeature.unread(map) == 5, "map absence cannot poison same-class keys");
        CountRow another = new CountRow("dummy-another", 9);
        check(ConversationGroupingFeature.unread(another) == 9, "same map class different keys/values remain real-time");
        RecoverChild recover = new RecoverChild();
        check(ConversationGroupingFeature.unread(recover) == 11, "private child field resolution");
        Field cached = cachedField(RecoverChild.class, "field_unReadCount");
        check(cached != null, "positive metadata stores Field not unread value");
        cached.setAccessible(false);
        check(ConversationGroupingFeature.unread(recover) == 4, "instance Field.get failure still falls through to parent");
        cached.setAccessible(true);
        check(ConversationGroupingFeature.unread(recover) == 11, "transient Field.get failure cannot poison metadata permanently");
        System.out.println("PASS fields: JFR 0 missing-field exceptions after warmup/200 reads, live values, alias/hierarchy, map keys and transient get recovery");
    }
    static Field cachedField(Class<?> type, String name) throws Exception {
        for (Field field : ConversationGroupingFeature.class.getDeclaredFields()) {
            if (!Map.class.isAssignableFrom(field.getType())) continue;
            field.setAccessible(true); Object cache = field.get(null);
            if (cache instanceof Map<?, ?> map) {
                Object found = map.get(type);
                if (found instanceof Map<?, ?> names) return findField(names.get(name));
            }
        }
        return null;
    }
    static Field findField(Object value) {
        if (value instanceof Field field) return field;
        if (value instanceof Iterable<?> values) for (Object item : values) { Field field = findField(item); if (field != null) return field; }
        return null;
    }
    static void transition() throws Throwable {
        Adapter adapter = setup();
        initializeGeometry();
        adapter.rows.clear();
        for (int i = 0; i < 16; i++) adapter.rows.add(new CountRow(i == 1 ? "dummy-b" : "dummy-" + i, 1));
        ConversationGroupingFeature.setActive(ConversationGroupStore.ALL_ID); Handler.drainForTest();
        com.tencent.mm.ui.conversation.ConversationListView list = new com.tencent.mm.ui.conversation.ConversationListView(CONTEXT);
        list.adapter = new HeaderViewListAdapter(adapter); list.click = new Clicks();
        HeaderViewListAdapter wrapper = (HeaderViewListAdapter) list.adapter;
        wrapper.footers.add(new View(CONTEXT));
        bind(adapter, list, 13);
        check(wrapper.getCount() == 18, "initial count includes header/footer");
        list.onStop = () -> check((Integer) checkedCall(adapter, "getCount") == 16,
                "stop old fling before publishing the smaller group");
        final int[] observed = {-1};
        adapter.observer = () -> {
            check(!list.flinging, "notify cannot leave old fling active");
            list.events.add("notify");
            observed[0] = wrapper.getCount();
        };
        list.flinging = true;
        int notifications = adapter.notifications;
        ConversationGroupingFeature.setActive("manual");
        // Give the old frame a chance to run BEFORE draining queued refreshes.
        list.flingStepForTest(14);
        check(!list.flinging && list.stops == 1, "switch stops fling in the same main task");
        check(adapter.notifications == notifications + 1 && observed[0] == 3,
                "switch publishes mapping and notifies observers synchronously");
        check(list.selection == 0, "old first/selected position reset after shrink");
        check(list.events.equals(Arrays.asList("notify","footer","selection")),
                "short group updates native filler before its selection/layout");
        check(wrapper.getView(1, null, list) instanceof RowView, "new filtered row binds");
        check(wrapper.getView(2, null, list) == wrapper.footers.get(0), "new footer offset");
        int afterSwitch = builds;
        for (int i = 0; i < 100; i++) check(count(adapter) == 1, "new stable group count");
        Handler.drainForTest();
        check(builds == afterSwitch && adapter.notifications == notifications + 1,
                "queued labels cannot rebuild or notify the adapter again");
        list.onStop = null;
        SettingsChanges.failSave = true; list.flinging = true;
        ConversationGroupingFeature.setActive("unread");
        check(list.flinging && list.stops == 1 && count(adapter) == 1, "failed save preserves current group and scroll");
        SettingsChanges.failSave = false;
        ConversationGroupingFeature.setActive("unread");
        check(!list.flinging && wrapper.getCount() == 18, "expanding group is synchronous too");
        ConversationGroupStore.decoded = Arrays.asList(new ConversationGroupStore.Group(ConversationGroupStore.ALL_ID,
                ConversationGroupStore.Type.MANUAL), new ConversationGroupStore.Group("empty", ConversationGroupStore.Type.MANUAL));
        ConversationGroupingFeature.reload(); Handler.drainForTest();
        list.flinging = true;
        ConversationGroupingFeature.setActive("empty"); list.flingStepForTest(14);
        check(wrapper.getCount() == 2 && list.selection == 0 && !list.flinging, "zero rows with header/footer");
        check(wrapper.getView(1, null, list) == wrapper.footers.get(0), "empty group footer binds safely");
        int fillerChecks=list.footerChecks;
        ConversationListBridge.refresh(false); Handler.drainForTest();
        check(list.footerChecks==fillerChecks,"data-only refresh must not invoke selection-changing native filler");
        System.out.println("PASS transition: shrink/expand/empty, native wrapper footer branch, stop-before-publish, synchronous notify, selection, failed save and no queued rebuild");
    }
    static void initializeGeometry() throws Exception {
        Class<?> nativeList = com.tencent.mm.ui.conversation.ConversationListView.class;
        HostTargetResolver.resolved=Adapter.class.getDeclaredMethod("getView",int.class,View.class,ViewGroup.class);
        HostTargetResolver.footerResolved=nativeList.getDeclaredMethod("checkEmptyFooterHost");
        HostTargetResolver.barResolved=nativeList.getDeclaredMethod("resetActionBarHost");
        HostTargetResolver.barField=nativeList.getDeclaredField("actionBar");
        ConversationListBridge.initialize(new dev.mindlink.xposed.callbacks.XC_LoadPackage.LoadPackageParam());
        Handler.drainForTest();
    }
    static void selection() throws Throwable {
        Adapter adapter = setup(); ListView list = list(adapter, new Clicks());
        bind(adapter, list, 0); Handler.drainForTest();
        long generation = ConversationGroupingFeature.generation();
        int before = builds, notifications = adapter.notifications;
        list.flinging = true;
        ConversationGroupingFeature.setActive("unread");
        ConversationGroupingFeature.reload(); Handler.drainForTest();
        check(ConversationGroupingFeature.generation() == generation && count(adapter) == 2 && builds == before,
                "same group/reload reuses mapping generation");
        check(list.flinging && list.stops == 0 && adapter.notifications == notifications,
                "same group/reload leaves scroll and adapter untouched");
        List<ConversationGroupStore.Group> groups = new ArrayList<>(ConversationGroupStore.defaults());
        groups.add(new ConversationGroupStore.Group("sql", ConversationGroupStore.Type.SQL));
        ConversationGroupStore.decoded = groups;
        ConversationGroupingFeature.reload(); Handler.drainForTest();
        HostDatabaseBridge.result = Collections.emptyList();
        // WeKit member-cache semantics: the first SQL selection resolves synchronously and
        // caches per group id; no deferred background task is left pending.
        ConversationGroupingFeature.setActive("sql"); Handler.drainForTest();
        check(count(adapter) == 0 && ConversationGroupingFeature.generation() == generation + 1,
                "first SQL selection resolves and publishes synchronously");
        HostTargetResolver.defer = true;
        list.flinging = true;
        ConversationGroupingFeature.setActive("sql");
        check(HostTargetResolver.pending.isEmpty() && count(adapter) == 0,
                "repeat SQL selection uses the member cache without a background task");
        ConversationGroupingFeature.setActive("manual");
        check(count(adapter) == 1, "manual selection after cached SQL");
        int manualBuilds = builds;
        HostDatabaseBridge.result = Arrays.asList("dummy-a");
        check(ConversationGroupingFeature.activeGroupId().equals("manual") && count(adapter) == 1 && builds == manualBuilds,
                "no deferred SQL completion can overwrite later manual selection");
        notifications = adapter.notifications; generation = ConversationGroupingFeature.generation();
        HostDatabaseBridge.fail = true;
        ConversationGroupingFeature.setActive("manual");
        check(ConversationGroupingFeature.activeGroupId().equals("manual") && count(adapter) == 1
                && adapter.notifications == notifications && ConversationGroupingFeature.generation() == generation,
                "failed SQL re-resolution keeps the cached members, visible mapping and generation");
        ConversationGroupingFeature.reload(); Handler.drainForTest();
        check(ConversationGroupingFeature.error().isEmpty() && count(adapter) == 1, "cached SQL reload does not query or error");
        HostDatabaseBridge.fail = false;
        // An edited save invalidates the cache: the next SQL selection re-resolves live.
        int cachedBuilds = builds;
        List<ConversationGroupStore.Group> edited = new ArrayList<>(groups);
        edited.set(edited.size() - 1, new ConversationGroupStore.Group("sql", ConversationGroupStore.Type.SQL,
                Collections.emptyList(), "r.username", "1=1 AND r.username IS NOT NULL"));
        ConversationGroupStore.decoded = edited;
        wex.ui.core.WexStore.INSTANCE.values.put(ConversationGroupingFeature.KEY_GROUPS, "edited");
        HostDatabaseBridge.result = Arrays.asList("dummy-a", "dummy-c");
        check(ConversationGroupingFeature.saveGroups(edited), "saveGroups persists the edit");
        Handler.drainForTest();
        ConversationGroupingFeature.setActive("sql"); Handler.drainForTest();
        check(count(adapter) == 2 && builds > cachedBuilds && call(adapter, "getItem", 0) == adapter.rows.get(0)
                && call(adapter, "getItem", 1) == adapter.rows.get(2),
                "saveGroups clears the member cache and re-resolution publishes new members");
        ConversationGroupingFeature.setActive(ConversationGroupStore.ALL_ID); Handler.drainForTest();
        check(count(adapter) == 3 && ConversationGroupingFeature.isAllActive(), "ALL restores every row");
        ConversationGroupingFeature.setActive("sql"); Handler.drainForTest();
        check(count(adapter) == 2, "cached reselection after ALL restores the two SQL rows");
        wex.ui.core.WexStore.INSTANCE.values.put(ConversationGroupingFeature.KEY_ENABLED, "false");
        list.flinging = true; ConversationGroupingFeature.reload(); Handler.drainForTest();
        check(!list.flinging && count(adapter) == 3 && HostTargetResolver.pending.isEmpty(),
                "disable synchronously restores all without a query");
        wex.ui.core.WexStore.INSTANCE.values.put(ConversationGroupingFeature.KEY_ENABLED, "true");
        ConversationGroupingFeature.reload(); Handler.drainForTest();
        check(count(adapter) == 2 && ConversationGroupingFeature.activeGroupId().equals("sql"),
                "enable reselects the stored SQL group from cache");
        System.out.println("PASS selection: synchronous cached SQL resolution, repeat without requery, edited save re-resolves and disable/enable");
    }
    /** Throwaway debug driver for the selection cache flow; removed after triage. */
    static void enableDebug() throws Throwable {
        Adapter adapter = setup(); ListView list = list(adapter, new Clicks());
        bind(adapter, list, 0); Handler.drainForTest();
        List<ConversationGroupStore.Group> groups = new ArrayList<>(ConversationGroupStore.defaults());
        groups.add(new ConversationGroupStore.Group("sql", ConversationGroupStore.Type.SQL));
        ConversationGroupStore.decoded = groups;
        wex.ui.core.WexStore.INSTANCE.values.put(ConversationGroupingFeature.KEY_GROUPS, "cfg");
        HostDatabaseBridge.result = Arrays.asList("dummy-a", "dummy-c");
        ConversationGroupingFeature.setActive("sql"); Handler.drainForTest();
        System.out.println("DBG sql active: count=" + count(adapter) + " active=" + ConversationGroupingFeature.activeGroupId()
                + " stored=" + wex.ui.core.WexStore.INSTANCE.values.get(ConversationGroupingFeature.KEY_ACTIVE));
        wex.ui.core.WexStore.INSTANCE.values.put(ConversationGroupingFeature.KEY_ENABLED, "false");
        ConversationGroupingFeature.reload(); Handler.drainForTest();
        System.out.println("DBG disabled: count=" + count(adapter) + " enabled-state-cached=" + (wex.ui.core.WexStore.INSTANCE.values.get(ConversationGroupingFeature.KEY_ENABLED)));
        wex.ui.core.WexStore.INSTANCE.values.put(ConversationGroupingFeature.KEY_ENABLED, "true");
        ConversationGroupingFeature.reload(); Handler.drainForTest();
        System.out.println("DBG re-enabled: count=" + count(adapter) + " active=" + ConversationGroupingFeature.activeGroupId()
                + " all=" + ConversationGroupingFeature.isAllActive());
    }
    static void reentry() throws Throwable {
        Adapter adapter = setup(); ListView list = list(adapter, new Clicks());
        bind(adapter, list, 0); Handler.drainForTest();
        int notifications = adapter.notifications;
        list.flinging = true;
        list.onStop = () -> {
            list.onStop = null;
            ConversationGroupingFeature.setActive("groups");
        };
        ConversationGroupingFeature.setActive("manual"); Handler.drainForTest();
        check(ConversationGroupingFeature.activeGroupId().equals("groups") && count(adapter) == 0,
                "scroll-state reentrant selection wins over outer request");
        check(list.stops == 2 && adapter.notifications == notifications + 1,
                "nested transition waits for outer stop and never notifies obsolete request");
        System.out.println("PASS reentry: synchronous end-fling listener selection supersedes outer group safely");
    }
    @SuppressWarnings("unchecked")
    static Map<ListView, WeakReference<ConversationGroupTabsHost>> hosts() throws Exception {
        Field field = ConversationGroupingFeature.class.getDeclaredField("HOSTS"); field.setAccessible(true);
        return (Map<ListView, WeakReference<ConversationGroupTabsHost>>) field.get(null);
    }
    static android.widget.LinearLayout tabs(ConversationGroupBar bar) { return (android.widget.LinearLayout) bar.getChildAt(0); }
    static void hostLifecycle() throws Throwable {
        setup(); Handler.drainForTest();
        initializeGeometry();
        android.app.Activity activity = new android.app.Activity();
        android.widget.FrameLayout parent = new android.widget.FrameLayout(activity);
        ListView list = new ListView(activity); list.headers = 0;
        parent.addView(list, new ViewGroup.LayoutParams(400, 600));
        ConversationGroupTabsHost host = new ConversationGroupTabsHost(list); host.install();
        hosts().put(list, new WeakReference<>(host));
        check(list.headerAdds == 1 && list.headers == 1 && parent.getChildCount() == 2, "single initial header and overlay");
        parent.attachedForTest(false);
        Handler.drainForTest();
        check(list.headerRemoves == 0 && list.headers == 1 && host.getParent() == parent && hosts().get(list).get() == host,
                "temporary home detach must retain its existing header/host, not remove and reinsert it");
        parent.attachedForTest(true);
        check(host.getViewTreeObserver().listeners.size() == 1, "observer restored exactly once on reattach");
        check(list.headerAdds == 1 && list.headerRemoves == 0, "home reattach does not mutate native headers");
        ConversationGroupingFeature.INSTANCE.releaseList(list);
        check(list.headers == 0 && list.headerRemoves == 1 && host.getParent() == null && !hosts().containsKey(list),
                "explicit final release restores native header/overlay state");
        check(host.getViewTreeObserver().listeners.isEmpty(), "final release detaches pre-draw observer");
        int reads = wex.ui.core.WexStore.INSTANCE.boolReads.getOrDefault(ConversationGroupingFeature.KEY_EQUAL, 0);
        host.refresh();
        check(reads == wex.ui.core.WexStore.INSTANCE.boolReads.getOrDefault(ConversationGroupingFeature.KEY_EQUAL, 0),
                "disposed host cannot rebuild from a stale refresh");
        // Activity terminal detach and disabling retain the original native/final-disposal paths.
        android.widget.FrameLayout terminalParent = new android.widget.FrameLayout(activity);
        ListView terminalList = new ListView(activity); terminalList.headers = 0;
        terminalParent.addView(terminalList, new ViewGroup.LayoutParams(400, 600));
        ConversationGroupTabsHost terminalHost = new ConversationGroupTabsHost(terminalList); terminalHost.install();
        hosts().put(terminalList, new WeakReference<>(terminalHost));
        activity.finishing = true; terminalParent.attachedForTest(false); Handler.drainForTest();
        check(terminalList.headerRemoves == 1 && terminalHost.getParent() == null && !hosts().containsKey(terminalList),
                "terminal Activity detach still releases native header and registry");
        ListView disableList = new ListView(CONTEXT); disableList.headers = 0;
        android.widget.FrameLayout disableParent = new android.widget.FrameLayout(CONTEXT);
        disableParent.addView(disableList, new ViewGroup.LayoutParams(400, 600));
        ConversationGroupTabsHost disableHost = new ConversationGroupTabsHost(disableList); disableHost.install();
        hosts().put(disableList, new WeakReference<>(disableHost));
        wex.ui.core.WexStore.INSTANCE.values.put(ConversationGroupingFeature.KEY_ENABLED, "false");
        ConversationGroupingFeature.reload(); Handler.drainForTest();
        check(disableList.headerRemoves == 1 && disableHost.getParent() == null, "disable restores native list layout");
        ListView orphan = new ListView(CONTEXT); int oldHeaders = orphan.headers;
        try { new ConversationGroupTabsHost(orphan).install(); throw new AssertionError("missing parent must fail closed"); }
        catch (IllegalStateException expected) { }
        check(orphan.headers == oldHeaders && orphan.headerAdds == 0, "unsupported parent leaves native header untouched");
        resumeScroll();
        overlayGeometry();
        System.out.println("PASS host-lifecycle: temporary detach/reattach retains header, final/terminal/disable dispose, observer cleanup, stale refresh and native fallback");
    }
    static void overlayGeometry() throws Throwable {
        setup(); initializeGeometry();
        android.widget.FrameLayout parent=new android.widget.FrameLayout(CONTEXT);
        com.tencent.mm.ui.conversation.ConversationListView list=new com.tencent.mm.ui.conversation.ConversationListView(CONTEXT);
        list.headers=0; list.sizeForTest(400,600); parent.addView(list,new ViewGroup.LayoutParams(400,600));
        list.actionBar=new View(CONTEXT);list.actionBar.sizeForTest(400,72);
        ConversationGroupTabsHost host=new ConversationGroupTabsHost(list);host.install();host.sizeForTest(400,48);
        Field field=ConversationGroupTabsHost.class.getDeclaredField("header");field.setAccessible(true);
        View header=(View)field.get(host);
        header.attachedForTest(false);list.visiblePosition=0;host.onPreDraw();
        check(host.getVisibility()==View.INVISIBLE,"header below viewport must not appear as a pinned overlay");
        list.visiblePosition=2;list.actionBar.setAlpha(.5f);host.onPreDraw();
        int[] location=new int[2];host.getLocationInWindow(location);
        check(host.getVisibility()==View.VISIBLE&&location[1]==72&&host.getAlpha()==.5f,
                "scrolled-past pinned overlay follows native action-bar bottom and alpha");
        list.actionBar.setVisibility(View.INVISIBLE);host.onPreDraw();
        check(host.getVisibility()==View.INVISIBLE,"hidden native action bar hides group overlay");
        list.actionBar=null;host.onPreDraw();
        check(host.getVisibility()==View.INVISIBLE,"unresolved native action bar cannot publish an unanchored overlay");
        host.dispose();
    }
    static void resume(android.app.Activity activity) throws Throwable {
        XposedBridge.invoke(activity, "onResume", new Class<?>[0]);
        Handler.drainForTest();
    }
    static void resumeScroll() throws Throwable {
        Adapter adapter = setup();
        adapter.rows.clear();
        for (int i = 0; i < 40; i++) adapter.rows.add(new CountRow("dummy-resume-" + i, 1));
        HostTargetResolver.resolved = Adapter.class.getDeclaredMethod("getView", int.class, View.class, ViewGroup.class);
        ConversationGroupingFeature.INSTANCE.init(new dev.mindlink.xposed.callbacks.XC_LoadPackage.LoadPackageParam());
        Handler.drainForTest();
        com.tencent.mm.ui.LauncherUI activity = new com.tencent.mm.ui.LauncherUI();
        android.widget.FrameLayout parent = (android.widget.FrameLayout) activity.getWindow().getDecorView();
        ListView list = new com.tencent.mm.ui.conversation.ConversationListView(activity);
        list.headers = 0;
        HeaderViewListAdapter wrapper = new HeaderViewListAdapter(adapter); wrapper.headers = 0;
        wrapper.footers.add(new View(activity)); list.adapter = wrapper;
        parent.addView(list, new ViewGroup.LayoutParams(400, 600));
        // Real getView bind listener queues the real mount, without injecting HOSTS manually.
        bind(adapter, list, 0); Handler.drainForTest();
        ConversationGroupTabsHost host = hosts().get(list).get();
        check(host != null && list.headerAdds == 1 && list.headers == 1, "production bind mounts one native header");
        host.sizeForTest(400, 56); host.onPreDraw();
        int layouts = host.layouts;
        int selections = list.selections, notifications = adapter.notifications;
        long generation = ConversationGroupingFeature.generation();
        list.scrollForTest(13, -17); list.flinging = true;
        // Real Activity resume hook includes reload + onHomeReady, not just direct host detach.
        resume(activity);
        check(ConversationGroupingFeature.generation() == generation && adapter.notifications == notifications
                && list.selections == selections && list.stops == 0 && list.visiblePosition == 13 && list.firstTop == -17,
                "preset same-group resume leaves mapping, fling and native scroll untouched");
        parent.attachedForTest(false); Handler.drainForTest();
        parent.attachedForTest(true); resume(activity); host.onPreDraw();
        check(list.headerAdds == 1 && list.headerRemoves == 0 && hosts().get(list).get() == host && host.layouts == layouts,
                "resume/reattach retains mounted overlay, spacer geometry and header identity");
        ConversationGroupingFeature.setActive(ConversationGroupStore.ALL_ID); Handler.drainForTest();
        selections = list.selections; notifications = adapter.notifications; int stops = list.stops;
        generation = ConversationGroupingFeature.generation(); list.scrollForTest(13, -17);
        for (int i = 0; i < 4; i++) {
            resume(activity); checkedCall(adapter, "notifyDataSetChanged"); Handler.drainForTest();
            bind(adapter, list, 12); list.scrollForTest(13, -17); host.onPreDraw();
        }
        check(ConversationGroupingFeature.generation() == generation && list.selections == selections && list.stops == stops
                && adapter.notifications == notifications + 4 && list.headerAdds == 1 && list.headerRemoves == 0
                && host.layouts == layouts, "ALL resume/data notify/rebind does not remount, relayout or force selection");
        List<ConversationGroupStore.Group> configured = new ArrayList<>(ConversationGroupStore.defaults());
        configured.add(new ConversationGroupStore.Group("sql", ConversationGroupStore.Type.SQL));
        ConversationGroupStore.decoded = configured;
        wex.ui.core.WexStore.INSTANCE.values.put(ConversationGroupingFeature.KEY_GROUPS, "resume-sql");
        ConversationGroupingFeature.reload(); Handler.drainForTest();
        List<String> original = new ArrayList<>();
        for (int i = 0; i < 30; i++) original.add("dummy-resume-" + i);
        HostDatabaseBridge.result = original;
        ConversationGroupingFeature.setActive("sql"); Handler.drainForTest();
        check(count(adapter) == 30 && list.selection == 0, "actual selection into SQL still goes to top");
        list.scrollForTest(13, -17); list.flinging = true;
        generation = ConversationGroupingFeature.generation(); selections = list.selections;
        notifications = adapter.notifications; stops = list.stops;
        // WeKit semantics: resume never re-queries or re-publishes anything. The cached
        // members keep the visible mapping, the fling and the native scroll intact.
        HostDatabaseBridge.fail = true;
        HostTargetResolver.defer = true;
        resume(activity); Handler.drainForTest();
        check(ConversationGroupingFeature.generation() == generation && list.flinging && list.stops == stops
                && adapter.notifications == notifications && list.selections == selections
                && HostTargetResolver.pending.isEmpty() && count(adapter) == 30,
                "same SQL resume never queries and preserves mapping, fling and native scroll");
        // Membership changes are data updates that happen only through real group edits
        // (saveGroups clearing the cache); a later tab selection re-resolves live.
        HostDatabaseBridge.fail = false;
        List<String> updated = new ArrayList<>(original); updated.set(29, "dummy-resume-30");
        HostDatabaseBridge.result = updated;
        list.onStop = () -> check(countForObserver(adapter) == 30, "changed-members selection stops old fling before publication");
        adapter.observer = () -> check(countForObserver(adapter) == 30, "changed-members selection notifies with the new mapping");
        check(ConversationGroupingFeature.saveGroups(configured), "group edit persists and clears the cache");
        Handler.drainForTest();
        ConversationGroupingFeature.setActive("sql"); Handler.drainForTest();
        check(!list.flinging && list.stops == stops + 1 && adapter.notifications == notifications + 1
                && ConversationGroupingFeature.generation() == generation + 1
                && call(adapter, "getItem", 29) == adapter.rows.get(30), "re-selection after cache invalidation publishes changed SQL data synchronously after stopping");
        check(list.selections == selections && list.visiblePosition == 13 && list.firstTop == -17,
                "same SQL ID membership update must not force setSelection(0); position=" + list.visiblePosition
                        + " top=" + list.firstTop + " selectionCalls=" + (list.selections - selections));
        list.onStop = null; adapter.observer = null;
        // A rule edit is a real selection change even when the resolved rows happen to stay equal.
        selections = list.selections; stops = list.stops; notifications = adapter.notifications;
        configured.set(configured.size() - 1, new ConversationGroupStore.Group("sql", ConversationGroupStore.Type.SQL,
                Collections.emptyList(), "r.username", "1=1 AND r.username IS NOT NULL"));
        ConversationGroupStore.decoded = configured;
        list.flinging = true;
        HostDatabaseBridge.result = Collections.singletonList("dummy-resume-0");
        check(ConversationGroupingFeature.saveGroups(configured), "edited rule persists and clears the cache");
        Handler.drainForTest();
        check(!list.flinging && list.stops == stops + 1 && adapter.notifications == notifications + 1
                && list.selections == selections + 1 && list.selection == 0, "same SQL ID with edited rule still resets selection synchronously");
        selections = list.selections;
        // WeKit semantics: nothing re-publishes on resume; a same-rule re-selection over an
        // unchanged cache is a pure no-op that never touches the fling or the mapping.
        HostDatabaseBridge.result = Collections.singletonList("dummy-resume-0"); list.flinging = true;
        stops = list.stops; notifications = adapter.notifications;
        ConversationGroupingFeature.setActive("sql"); Handler.drainForTest();
        check(list.flinging && list.stops == stops && adapter.notifications == notifications
                && list.selections == selections,
                "same-rule no-op selection never stops a fling or re-notifies");
        list.flinging = false;
        scrollTraceChecks(activity, list, adapter, parent);
        ConversationGroupingFeature.INSTANCE.releaseList(list); Handler.drainForTest();
        System.out.println("PASS resume-scroll: real Activity hook/bind/mount, ALL/preset no-churn, resume never re-queries SQL, edited-rule re-resolution and selection-token reentry");
    }
    static void scrollTraceChecks(android.app.Activity activity, ListView list, Adapter adapter,
                                  android.widget.FrameLayout parent) throws Throwable {
        String identity = " list=" + System.identityHashCode(list) + " ";
        check(HostStageLog.events.stream().anyMatch(event -> event.contains(identity) && event.contains("event=new-header")),
                "new header diagnostic must execute through production mount");
        Object result = XposedBridge.invoke(list, "setSelectionFromTop", new Class<?>[]{int.class, int.class}, 7, -11);
        check(result == null && list.visiblePosition == 7 && list.firstTop == -11,
                "selection diagnostic preserves native result and position/top");
        String selected = HostStageLog.events.get(HostStageLog.events.size() - 1);
        check(selected.contains("schema=group-scroll-v2 event=setSelectionFromTop") && selected.contains("position=7 top=-11"),
                "diagnostic contains only schema and numeric selection arguments");
        RuntimeException failure = new IllegalStateException("dummy native selection failure");
        list.selectionFailure = failure;
        try { XposedBridge.invoke(list, "setSelection", new Class<?>[]{int.class}, 9); throw new AssertionError("native selection must throw"); }
        catch (RuntimeException error) { check(error == failure, "diagnostic must preserve exact native exception"); }
        list.selectionFailure = null;
        HostStageLog.fail = true;
        XposedBridge.invoke(list, "setSelection", new Class<?>[]{int.class}, 8);
        check(list.visiblePosition == 8, "log failure cannot block native selection");
        HostStageLog.fail = false;
        XposedBridge.invoke(list, "setAdapter", new Class<?>[]{android.widget.ListAdapter.class}, adapter);
        check(list.adapter == adapter && list.adapterSets == 1, "adapter diagnostic cannot replace native adapter");
        selected = HostStageLog.events.get(HostStageLog.events.size() - 1);
        check(selected.contains("event=setAdapter") && selected.contains("adapterChanged=false"),
                "wrapper unwrap preserves adapter identity in diagnostic");
        Adapter replacement = new Adapter(); replacement.rows.add(new CountRow("dummy-never-log", 1));
        XposedBridge.invoke(list, "setAdapter", new Class<?>[]{android.widget.ListAdapter.class}, replacement);
        check(HostStageLog.events.get(HostStageLog.events.size() - 1).contains("adapterChanged=true"),
                "adapter replacement diagnostic stores identity only");
        XposedBridge.invoke(list, "setAdapter", new Class<?>[]{android.widget.ListAdapter.class}, adapter);
        int logged = HostStageLog.events.size();
        ListView other = new ListView(activity); parent.addView(other, new ViewGroup.LayoutParams(400, 600));
        XposedBridge.invoke(other, "setSelection", new Class<?>[]{int.class}, 4);
        check(other.visiblePosition == 4 && HostStageLog.events.size() == logged, "non-target native selection is untouched and never logged");
        ListView foreignWindow = new com.tencent.mm.ui.conversation.ConversationListView(activity);
        ConversationListBridge.traceList(foreignWindow, "home-ready");
        XposedBridge.invoke(foreignWindow, "setSelection", new Class<?>[]{int.class}, 5);
        check(foreignWindow.visiblePosition == 5 && HostStageLog.events.size() == logged,
                "exact list class outside Activity window must not be observed");
        activity.finishing = true;
        XposedBridge.invoke(list, "setSelection", new Class<?>[]{int.class}, 6);
        activity.finishing = false;
        check(HostStageLog.events.size() == logged, "terminal Activity is not diagnosed");
        ListView.dispatchSelectionHooks = true;
        try {
            list.scrollForTest(13, -17);
            ((com.tencent.mm.ui.conversation.ConversationListView) list).onLayout(false, 0, 0, 400, 600);
            check(list.visiblePosition == 13, "unchanged native layout leaves position alone");
            ((com.tencent.mm.ui.conversation.ConversationListView) list).onLayout(true, 0, 0, 400, 620);
            check(list.visiblePosition == 13 && list.firstTop == -17,
                    "same-group changed-layout on chat return preserves the native scroll anchor");
            ConversationListBridge.refresh(false); Handler.drainForTest();
            ConversationListBridge.refresh(true); Handler.drainForTest();
        } finally { ListView.dispatchSelectionHooks = false; }
        check(HostStageLog.events.stream().anyMatch(event -> event.contains(identity) && event.contains("origin=module-refresh")),
                "real module refresh native selection has a fixed whitelist origin");
        for (int i = 0; i < 100; i++) XposedBridge.invoke(list, "setSelection", new Class<?>[]{int.class}, i);
        long bounded = HostStageLog.events.stream().filter(event -> event.contains(identity)).count();
        check(bounded > 0 && bounded <= 48 && list.visiblePosition == 99, "MAX48 bounds events without limiting native calls");
        ListView.dispatchSelectionHooks = true;
        try {
            ((com.tencent.mm.ui.conversation.ConversationListView) list).onLayout(true, 0, 0, 400, 640);
            check(list.visiblePosition == 99, "scroll preservation remains active after diagnostic budget exhaustion");
        } finally { ListView.dispatchSelectionHooks = false; }
        logged = HostStageLog.events.size();
        resume(activity); ConversationGroupingFeature.INSTANCE.releaseList(list); Handler.drainForTest();
        bind(adapter, list, 0); Handler.drainForTest();
        XposedBridge.invoke(list, "setSelection", new Class<?>[]{int.class}, 101);
        check(HostStageLog.events.size() == logged, "resume/release/remount never resets the per-list lifetime diagnostic budget");
        firstBindBeforeDecorObservesLater();
        disabledGroupingStillDiagnoses(activity);
        for (String event : HostStageLog.events) check(!event.contains("dummy-") && !event.contains("@chatroom")
                && !event.contains("SQL") && !event.contains("\tat "), "diagnostic must not contain host values or stack contents");
        System.out.println("PASS scroll-trace: actual native selection/adapter hooks, numeric-only events, throw/log failure/native fallback, window ownership and MAX48 lifetime budget");
    }
    /**
     * Regression for the installed-build zero-event evidence: a ConversationListView whose first
     * binds happen before it sits under the LauncherUI decor must be observed once it does, and a
     * repeated bind of the same list must retry registration instead of being blocked by the
     * single-shot latestList gate.
     */
    static void firstBindBeforeDecorObservesLater() throws Throwable {
        Adapter adapter = setup(); Clicks clicks = new Clicks();
        com.tencent.mm.ui.LauncherUI activity = new com.tencent.mm.ui.LauncherUI();
        android.widget.FrameLayout decor = (android.widget.FrameLayout) activity.getWindow().getDecorView();
        ListView orphan = new com.tencent.mm.ui.conversation.ConversationListView(activity);
        orphan.headers = 0;
        orphan.adapter = new HeaderViewListAdapter(adapter);
        String identity = " list=" + System.identityHashCode(orphan) + " ";
        int before = HostStageLog.events.size();
        // First bind while the list is not yet under the Activity decor (fragment inflation order):
        // real production getView path must not log, but the list must stay pending for retry.
        bind(adapter, orphan, 0);
        check(HostStageLog.events.size() == before,
                "bind outside the Activity window is not diagnosed before ownership");
        // Same list now sits under decor; the next real bind must retry the pending registration.
        decor.addView(orphan, new ViewGroup.LayoutParams(400, 600));
        bind(adapter, orphan, 0);
        XposedBridge.invoke(orphan, "setSelection", new Class<?>[]{int.class}, 3);
        check(HostStageLog.events.size() > before
                        && HostStageLog.events.subList(before, HostStageLog.events.size()).stream()
                                .anyMatch(event -> event.contains(identity) && event.contains("event=setSelection")),
                "a pending first-bind list is registered once it sits under the decor and observed");
        XposedBridge.invoke(orphan, "setSelection", new Class<?>[]{int.class}, 3);
        check(HostStageLog.events.get(HostStageLog.events.size() - 1).contains(identity)
                        && HostStageLog.events.get(HostStageLog.events.size() - 1).contains("event=setSelection"),
                "registered pending list observes native selection without a restart");
        int withEvents = HostStageLog.events.size();
        decor.removeView(orphan);
        // A list that left the window ancestry never fabricates events again.
        bind(adapter, orphan, 0);
        XposedBridge.invoke(orphan, "setSelection", new Class<?>[]{int.class}, 2);
        check(HostStageLog.events.size() == withEvents,
                "list outside the Activity window is never diagnosed after registration lapses");
        System.out.println("PASS scroll-trace-pending: first bind before decor stays pending, later bind registers, selection observed, window lapse never fabricates");
    }

    /** The grouping switch must not gate scroll diagnostics; only the host mount is gated. */
    static void disabledGroupingStillDiagnoses(android.app.Activity activity) throws Throwable {
        Adapter adapter = setup();
        wex.ui.core.WexStore.INSTANCE.values.put(ConversationGroupingFeature.KEY_ENABLED, "false");
        ConversationGroupingFeature.reload(); Handler.drainForTest();
        com.tencent.mm.ui.LauncherUI home = new com.tencent.mm.ui.LauncherUI();
        android.widget.FrameLayout decor = (android.widget.FrameLayout) home.getWindow().getDecorView();
        ListView off = new com.tencent.mm.ui.conversation.ConversationListView(home);
        off.headers = 0;
        off.adapter = new HeaderViewListAdapter(adapter);
        decor.addView(off, new ViewGroup.LayoutParams(400, 600));
        bind(adapter, off, 0);
        int before = HostStageLog.events.size();
        // Real production onHomeReady with grouping disabled must still emit the home-ready event.
        ConversationGroupingFeature.INSTANCE.onHomeReady(home);
        Handler.drainForTest();
        String identity = " list=" + System.identityHashCode(off) + " ";
        check(HostStageLog.events.size() > before
                        && HostStageLog.events.subList(before, HostStageLog.events.size()).stream()
                                .anyMatch(event -> event.contains(identity) && event.contains("event=home-ready")),
                "grouping-disabled home still emits the home-ready diagnostic");
        check(hosts().get(off) == null || hosts().get(off).get() == null,
                "grouping-disabled home-ready never mounts a host");
        System.out.println("PASS scroll-trace-disabled: grouping switch no longer gates scroll diagnostics and never mounts");
    }
    static int countForObserver(Object adapter) { return (Integer) checkedCall(adapter, "getCount"); }
    static void tabUpdates() throws Throwable {
        Adapter adapter = setup(); Handler.drainForTest();
        adapter.rows.add(new CountRow("gh_dummy", 0));
        ListView list = list(adapter, new Clicks()); bind(adapter, list, 0);
        ConversationGroupingFeature.setActive("manual"); Handler.drainForTest();
        List<ConversationGroupStore.Group> manualEdit = new ArrayList<>(ConversationGroupStore.defaults());
        manualEdit.set(manualEdit.size() - 1, new ConversationGroupStore.Group("manual", ConversationGroupStore.Type.MANUAL, "gh_dummy"));
        int selections = list.selections; list.flinging = true;
        check(ConversationGroupingFeature.saveGroups(manualEdit), "manual config edit saves"); Handler.drainForTest();
        check(!list.flinging && list.selections == selections + 1 && list.selection == 0 && count(adapter) == 1
                && call(adapter, "getItem", 0) == adapter.rows.get(3), "same manual ID membership edit retains original synchronous switch contract");
        ConversationGroupingFeature.saveGroups(ConversationGroupStore.defaults()); Handler.drainForTest();
        android.widget.FrameLayout parent = new android.widget.FrameLayout(CONTEXT);
        parent.addView(list, new ViewGroup.LayoutParams(400, 600));
        ConversationGroupTabsHost host = new ConversationGroupTabsHost(list); host.install();
        hosts().put(list, new WeakReference<>(host));
        ConversationGroupBar bar = (ConversationGroupBar) host.getChildAt(0);
        android.widget.LinearLayout tabs = tabs(bar);
        List<View> originals = new ArrayList<>();
        for (int i = 0; i < tabs.getChildCount(); i++) originals.add(tabs.getChildAt(i));
        int adds = tabs.additions, removes = tabs.removals, layouts = host.layouts;
        int animatedBefore = ConversationGroupCard.animated, revealsBefore = ConversationGroupCard.reveals;
        // The actual tab click publishes synchronously; its immediate rebuild and queued host refresh must be idempotent.
        tabs.getChildAt(4).performClick(); Handler.drainForTest();
        check(ConversationGroupingFeature.activeGroupId().equals("officials") && count(adapter) == 1
                && call(adapter, "getItem", 0) == adapter.rows.get(3),
                "official group still publishes the existing business mapping");
        check(tabs.additions == adds && tabs.removals == removes,
                "selection-only official switch must not destroy/reinsert all tab views");
        check(host.layouts == layouts, "selection-only refresh must not force host/header relayout");
        for (int i = 0; i < originals.size(); i++) check(tabs.getChildAt(i) == originals.get(i), "tab identity retained " + i);
        check(tabs.getChildAt(4).isSelected() && !tabs.getChildAt(1).isSelected(), "selection style updates in place");
        check(ConversationGroupCard.animated == animatedBefore + 1,
                "the selection capsule slides to the newly selected tab once, not on every refresh");
        check(ConversationGroupCard.reveals == revealsBefore + 1, "the newly selected tab is scrolled into view once");
        for (int i = 0; i < 10; i++) call(adapter, "notifyDataSetChanged"); Handler.drainForTest();
        check(tabs.additions == adds && tabs.removals == removes && host.layouts == layouts,
                "data notifications with unchanged labels cannot churn tabs/header");
        check(ConversationGroupCard.animated == animatedBefore + 1, "data notifications never replay the capsule slide");
        wex.ui.core.WexStore.INSTANCE.values.put(ConversationGroupingFeature.KEY_EQUAL, "true"); host.refresh();
        check(((android.widget.LinearLayout.LayoutParams) tabs.getChildAt(0).getLayoutParams()).weight == 1f,
                "equal-width setting still changes geometry");
        List<ConversationGroupStore.Group> configured = new ArrayList<>(ConversationGroupStore.defaults());
        configured.add(new ConversationGroupStore.Group("new", ConversationGroupStore.Type.MANUAL));
        ConversationGroupStore.decoded = configured; ConversationGroupingFeature.reload(); Handler.drainForTest();
        check(tabs.getChildCount() == configured.size() + 1, "group configuration change still rebuilds structure");
        System.out.println("PASS tab-updates: real official tab click preserves child identities/no header relayout, coalesced notifications, equal width and configuration update");
    }
    public static void main(String[] args) throws Throwable {
        switch (args[0]) {
            case "epoch": epoch(); break;
            case "mutations": mutations(); break;
            case "click": click(); break;
            case "guards": guards(); break;
            case "notify-failure": notifyFailure(); break;
            case "snapshot-failure": snapshotFailure(); break;
            case "fields": fields(); break;
            case "transition": transition(); break;
            case "selection": selection(); break;
            case "reentry": reentry(); break;
            case "host-lifecycle": hostLifecycle(); break;
            case "pull-wallpaper": pullWallpaper(); break;
            case "tab-updates": tabUpdates(); break;
            case "origin-fallback": originFallback(); break;
            default: throw new IllegalArgumentException(args[0]);
        }
    }
    static void pullWallpaper() throws Exception {
        setup(); initializeGeometry();
        java.lang.reflect.Field actionBarField = ConversationListBridge.class.getDeclaredField("actionBarField");
        actionBarField.setAccessible(true);
        actionBarField.set(null, com.tencent.mm.ui.conversation.ConversationListView.class.getField("actionBar"));

        com.tencent.mm.ui.conversation.MainUIView page = new com.tencent.mm.ui.conversation.MainUIView(CONTEXT);
        com.tencent.mm.plugin.multitask.ui.bg.DynamicBgContainer wallpaper =
                new com.tencent.mm.plugin.multitask.ui.bg.DynamicBgContainer(CONTEXT);
        com.tencent.mm.dynamicbackground.view.DynamicBackgroundGLSurfaceView surface =
                new com.tencent.mm.dynamicbackground.view.DynamicBackgroundGLSurfaceView(CONTEXT);
        wallpaper.addView(surface, new ViewGroup.LayoutParams(400, 600));
        page.addView(wallpaper, new ViewGroup.LayoutParams(400, 600));
        android.widget.FrameLayout listParent = new android.widget.FrameLayout(CONTEXT);
        page.addView(listParent, new ViewGroup.LayoutParams(400, 600));
        com.tencent.mm.ui.conversation.ConversationListView list =
                new com.tencent.mm.ui.conversation.ConversationListView(CONTEXT);
        list.headers = 0; list.sizeForTest(400, 600);
        list.setBackgroundColor(0xffededed);
        View actionBar = new View(CONTEXT); actionBar.sizeForTest(400, 72); list.actionBar = actionBar;
        listParent.addView(list, new ViewGroup.LayoutParams(400, 600));
        ConversationGroupTabsHost host = new ConversationGroupTabsHost(list); host.install(); host.sizeForTest(400, 48);

        host.onPreDraw();
        check(surface.getVisibility() == View.INVISIBLE && host.getVisibility() == View.VISIBLE,
                "the exact fullscreen pull layer is suppressed without hiding group tabs");
        Field headerField = ConversationGroupTabsHost.class.getDeclaredField("header");
        headerField.setAccessible(true);
        View header = (View) headerField.get(host);
        check(header.getBackground() instanceof android.graphics.drawable.ColorDrawable
                        && ((android.graphics.drawable.ColorDrawable) header.getBackground()).getColor() == 0xffededed,
                "native header must cover the host's purple onDraw wash with the list background");
        check(header.getBackground() != list.getBackground(),
                "header must not steal the list drawable's callback or bounds");
        Object background = header.getBackground();
        host.onPreDraw();
        check(header.getBackground() == background, "unchanged frames retain the header background");
        ((android.graphics.drawable.ColorDrawable) list.getBackground()).setColor(0xff191919);
        host.onPreDraw();
        check(((android.graphics.drawable.ColorDrawable) header.getBackground()).getColor() == 0xff191919,
                "an in-place night-theme change updates the header before drawing");
        list.setBackground(null); host.onPreDraw();
        check(header.getBackground() == null, "removed list background cannot leave an obsolete header fill");
        wallpaper.removeView(surface);
        host.onPreDraw();
        check(surface.getVisibility() == View.VISIBLE && host.getVisibility() == View.VISIBLE,
                "removing the pull layer restores GL and keeps the group capsule at its native anchor");
        check(ConversationGroupCard.glassRefreshes > 0, "the restored capsule refreshes its backdrop after the pull layer closes");

        wallpaper.addView(surface, new ViewGroup.LayoutParams(400, 600));
        host.onPreDraw();
        check(surface.getVisibility() == View.INVISIBLE, "a later pull suppresses the recreated GL surface");
        host.dispose();
        check(surface.getVisibility() == View.VISIBLE, "disposing the host restores a surface it suppressed");
        System.out.println("PASS pull-wallpaper: GL visibility and group capsule restore across the pull lifecycle");
    }
}
class MissingMuted { public String field_username = "dummy-missing"; public int field_unReadCount = 1; }
class AliasParent { public String field_username = "dummy-parent"; public int field_unReadCount = 7; }
class AliasChild extends AliasParent { public String username = "dummy-child"; public int unReadCount = 19; }
class RecoverParent { private int field_unReadCount = 4; }
class RecoverChild extends RecoverParent { private int field_unReadCount = 11; }
