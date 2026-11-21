package wex.ui.core.feature;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Insets;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.widget.RelativeLayout;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import wex.ui.core.WexStore;

/** Floating chat title owned by its ChattingUILayout; ported from WeKit (GPL-3.0-only). */
public final class FloatingChatHeaderFeature {
    static final String KEY_ENABLED = "floating_chat_header_enabled";
    static final String KEY_CORNER = "floating_chat_header_corner_radius";
    static final String KEY_SIDE = "floating_chat_header_side_margin";
    static final String KEY_TOP_GAP = "floating_chat_header_top_gap";
    static final String KEY_EXTRA_GAP = "floating_chat_header_extra_gap";
    static final String KEY_ELEVATION = "floating_chat_header_elevation";
    static final String KEY_COMPONENT_GAP = "floating_chat_header_component_gap";
    static final String KEY_STYLE = "floating_chat_header_layout_style";

    private static final String LAYOUT = "com.tencent.mm.pluginsdk.ui.chat.ChattingUILayout";
    private static final String FOOTER = "com.tencent.mm.pluginsdk.ui.chat.ChatFooter";
    private static final String HEADER = "androidx.appcompat.widget.ActionBarContainer";
    private static final String CHAT_ACTIVITY = "com.tencent.mm.ui.chatting.ChattingUI";
    private static final String SCROLL = "com.tencent.mm.pluginsdk.ui.chat.ChattingScrollLayout";
    private static final String LIST_HOST = "com.tencent.mm.ui.chatting.view.MMChattingListView";
    private static final String LIST_A = "com.tencent.mm.pluginsdk.ui.tools.ScrollControlRecyclerView";
    private static final String LIST_B = "com.tencent.mm.pluginsdk.ui.tools.ChattingRecyclerView";
    /** Group-chat pinned message / announcement stack, inflated by a ViewStub in the chat content. */
    private static final String TIPS = "com.tencent.mm.ui.tipsbar.ChatTipsBarGroup";
    private static final WeakHashMap<View, WeakReference<State>> STATES = new WeakHashMap<>();
    private static Config config = new Config();
    private static boolean initialized;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final java.util.Set<java.lang.reflect.Method> HOOKS = new java.util.HashSet<>();
    private static final String LAUNCHER_ACTIVITY = "com.tencent.mm.ui.LauncherUI";
    private static WeakReference<Activity> resumed = new WeakReference<>(null);
    private static Discovery discovery;

    private FloatingChatHeaderFeature() { }

    public static synchronized void init(ClassLoader loader) throws Exception {
        if (initialized) return;
        config = new Config();
        Class<?> layout = XposedHelpers.findClass(LAYOUT, loader);
        initialized = true;
        try {
            XposedBridge.hookAllConstructors(layout, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (param.thisObject instanceof View) observe((View) param.thisObject);
                }
            });
        } catch (Throwable error) { unavailable("constructor", error); }
        XC_MethodHook changed = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                if (!param.hasThrowable() && param.thisObject instanceof View) observeHost((View) param.thisObject);
            }
        };
        optionalHook(layout, "onLayout", changed, boolean.class, int.class, int.class, int.class, int.class);
        optionalHook(layout, "onAttachedToWindow", changed);
        hookTipsPadding(loader);
        hookActivity(Activity.class);
        for (String name : new String[]{LAUNCHER_ACTIVITY, CHAT_ACTIVITY}) {
            try { hookActivity(XposedHelpers.findClass(name, loader)); }
            catch (Throwable error) { unavailable("activity-class", error); }
        }
        try {
            Class<?> footer = XposedHelpers.findClass(FOOTER, loader);
            optionalHook(footer, "onAttachedToWindow", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (!(param.thisObject instanceof View) || !FOOTER.equals(param.thisObject.getClass().getName())) return;
                    View current = (View) param.thisObject;
                    for (int depth = 0; depth < 24 && current.getParent() instanceof View; depth++) {
                        current = (View) current.getParent();
                        if (LAYOUT.equals(current.getClass().getName())) { observe(current); return; }
                    }
                }
            });
        } catch (Throwable error) { unavailable("footer-attach", error); }
        // Optional across host versions; never blocks baseline/lifecycle registration.
        optionalHook(layout, "fitSystemWindows", changed, Rect.class);
        log("stage=baseline registered enabled=" + config.enabled);
        probeEnable();
    }

    private static void hookTipsPadding(ClassLoader loader) {
        try {
            Class<?> tips = XposedHelpers.findClass(TIPS, loader);
            optionalHook(tips, "setListViewPaddingTop", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    State state = tipsState(param.thisObject);
                    if (state != null) state.releaseListInset();
                }
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    State state = tipsState(param.thisObject);
                    if (state != null) {
                        // A native write is new ownership even when its number equals our last write.
                        state.listWrittenTop = Integer.MIN_VALUE;
                        state.listExtra = 0;
                        View layout = state.layoutRef.get();
                        Activity activity = layout == null ? null : ChatToolbarLifecycle.findActivity(layout.getContext());
                        if (config.enabled && layout != null && layout.isAttachedToWindow() && layout.isShown()
                                && state.header != null && state.header.isAttachedToWindow()
                                && activity != null && !activity.isFinishing() && !activity.isDestroyed()) {
                            state.applyListPadding(layout, state.header);
                        }
                    }
                }
            }, int.class);
        } catch (Throwable error) { unavailable("tips-padding", error); }
    }

    private static State tipsState(Object group) {
        if (!(group instanceof View) || Looper.myLooper() != Looper.getMainLooper()) return null;
        View view = (View) group;
        for (View current = view; current != null;
                current = current.getParent() instanceof View ? (View) current.getParent() : null) {
            State state = state(current);
            if (state != null && isDescendant(view, current)) return state;
        }
        return null;
    }

    private static void optionalHook(Class<?> type, String name, XC_MethodHook callback, Class<?>... parameters) {
        try {
            java.lang.reflect.Method method = inherited(type, name, parameters);
            if (!HOOKS.contains(method)) { XposedBridge.hookMethod(method, callback); HOOKS.add(method); }
        } catch (Throwable error) { unavailable(name, error); }
    }

    private static void hookActivity(Class<?> type) {
        XC_MethodHook entered = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                if (!(param.thisObject instanceof Activity) || param.hasThrowable() || !chatActivity((Activity) param.thisObject)) return;
                resumed = new WeakReference<>((Activity) param.thisObject);
                logEntry(param);
                refresh();
            }
        };
        optionalHook(type, "onResume", entered);
        // A notification tap reuses the running task: the chat page can be reached by
        // onNewIntent or onStart alone, with no further onResume for that Activity.
        optionalHook(type, "onNewIntent", entered, Intent.class);
        optionalHook(type, "onStart", entered);
        XC_MethodHook stop = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                if (resumed.get() == param.thisObject) { resumed.clear(); cancelDiscovery(); }
            }
        };
        optionalHook(type, "onPause", stop); optionalHook(type, "onDestroy", stop);
    }

    private static int entryLogs;

    private static void logEntry(XC_MethodHook.MethodHookParam param) {
        if (entryLogs >= 8) return;
        entryLogs++;
        log("stage=entry trigger=" + param.method.getName() + " host=" + param.thisObject.getClass().getName());
    }

    private static boolean chatActivity(Activity activity) {
        return activity != null && !activity.isFinishing() && !activity.isDestroyed()
                && (LAUNCHER_ACTIVITY.equals(activity.getClass().getName()) || CHAT_ACTIVITY.equals(activity.getClass().getName()));
    }

    private static void unavailable(String stage, Throwable error) { log("stage=" + stage + " unavailable=" + error.getClass().getSimpleName()); }

    static boolean enabled() { return WexStore.INSTANCE.getBool(KEY_ENABLED, false); }
    static int cornerRadiusDp() { return BubbleSettings.readInt(KEY_CORNER, 24, 0, 32); }
    static int sideMarginDp() { return BubbleSettings.readInt(KEY_SIDE, 12, 0, 32); }
    static int topGapDp() { return BubbleSettings.readInt(KEY_TOP_GAP, 4, 0, 24); }
    static int extraGapDp() { return BubbleSettings.readInt(KEY_EXTRA_GAP, 8, 0, 24); }
    static int elevationDp() { return BubbleSettings.readInt(KEY_ELEVATION, 4, 0, 16); }
    static int componentGapDp() { return BubbleSettings.readInt(KEY_COMPONENT_GAP, 8, 0, 24); }
    static boolean separatedStyle() { return "SEPARATED".equalsIgnoreCase(WexStore.INSTANCE.getString(KEY_STYLE, "INTEGRATED")); }

    public static void refresh() {
        MAIN.post(() -> {
            config = new Config();
            for (WeakReference<State> reference : new ArrayList<>(STATES.values())) {
                State state = reference.get(); if (state != null) { state.watch(); state.apply(); }
            }
            Activity activity = resumed.get();
            if (!config.enabled || !chatActivity(activity)) { cancelDiscovery(); return; }
            if (discovery == null) { discovery = new Discovery(activity); MAIN.post(discovery); }
        });
    }

    private static void cancelDiscovery() { if (discovery != null) discovery.cancel(); }

    /** The config store lives on external media, and a notification cold start can read
     *  it before that storage is mounted. Without a bounded re-read the whole chat
     *  decoration stayed off until the user left the page and came back. */
    private static final long[] PROBE_DELAYS = {300L, 700L, 1500L, 3000L, 6000L, 9000L};
    private static boolean probing;

    static void probeEnable() {
        if (probing) return;
        probing = true;
        probe(0);
    }

    private static void probe(final int attempt) {
        if (attempt >= PROBE_DELAYS.length) { probing = false; return; }
        MAIN.postDelayed(new Runnable() {
            @Override public void run() {
                refresh();
                if (enabled()) { probing = false; return; }
                probe(attempt + 1);
            }
        }, PROBE_DELAYS[attempt]);
    }

    private static final class Discovery implements Runnable, View.OnAttachStateChangeListener {
        final WeakReference<Activity> activityRef;
        final WeakReference<View> rootRef;
        int attempts;
        boolean cancelled;
        Discovery(Activity activity) {
            activityRef = new WeakReference<>(activity);
            View root = activity.getWindow() == null ? null : activity.getWindow().getDecorView();
            rootRef = new WeakReference<>(root);
            if (root != null) root.addOnAttachStateChangeListener(this);
        }
        void cancel() {
            cancelled = true; MAIN.removeCallbacks(this);
            View root = rootRef.get(); if (root != null) root.removeOnAttachStateChangeListener(this);
            if (discovery == this) discovery = null;
        }
        @Override public void run() {
            Activity activity = activityRef.get(); View root = rootRef.get();
            // Never require this Activity to still be the resumed one: a notification
            // launch can move resume to LauncherUI while the chat decor keeps its tree.
            // The decor identity is the real stop condition.
            if (cancelled || !config.enabled || root == null || activity == null
                    || activity.isFinishing() || activity.isDestroyed()
                    || activity.getWindow() == null || activity.getWindow().getDecorView() != root) { cancel(); return; }
            View layout = root.isAttachedToWindow() ? findActiveLayout(root, activity) : null;
            if (layout != null) { observe(layout); log("stage=discover found attempts=" + (attempts + 1)); cancel(); return; }
            // The chat tree can arrive long after the notification transition, so the
            // discoverer only slows down; it must not retire while the decor is alive.
            attempts++;
            MAIN.postDelayed(this, attempts < 8 ? 120L : 500L);
        }
        @Override public void onViewAttachedToWindow(View view) {
            if (cancelled) return;
            attempts = 0; MAIN.removeCallbacks(this); MAIN.post(this);
        }
        @Override public void onViewDetachedFromWindow(View view) { cancel(); }
    }

    private static View findActiveLayout(View root, Activity activity) {
        View found = null; ArrayDeque<View> queue = new ArrayDeque<>(); queue.add(root); int admitted = 1;
        while (!queue.isEmpty()) {
            View view = queue.removeFirst();
            if (view.getVisibility() != View.VISIBLE || view.getAlpha() <= 0f) continue;
            String name = view.getClass().getName();
            if (LAYOUT.equals(name)) {
                if (view.isAttachedToWindow() && view.isShown() && ChatToolbarLifecycle.findActivity(view.getContext()) == activity) {
                    if (found != null) return null; found = view;
                }
                continue;
            }
            if (FOOTER.equals(name) || LIST_HOST.equals(name) || LIST_A.equals(name) || LIST_B.equals(name)) continue;
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                if (group.getChildCount() > 768 - admitted) return null;
                for (int i = 0; i < group.getChildCount(); i++) { queue.addLast(group.getChildAt(i)); admitted++; }
            }
        }
        return found;
    }

    private static void observeHost(View view) {
        if (LAYOUT.equals(view.getClass().getName())) { observe(view); return; }
        if (!FOOTER.equals(view.getClass().getName())) return;
        for (int depth = 0; depth < 24 && view.getParent() instanceof View; depth++) {
            view = (View) view.getParent();
            if (LAYOUT.equals(view.getClass().getName())) { observe(view); return; }
        }
    }

    static void observe(View layout) {
        if (layout == null || !LAYOUT.equals(layout.getClass().getName())) return;
        if (Looper.myLooper() != Looper.getMainLooper()) {
            WeakReference<View> ref = new WeakReference<>(layout); MAIN.post(() -> observe(ref.get())); return;
        }
        State state = state(layout);
        if (state == null) {
            state = new State(layout); STATES.put(layout, new WeakReference<>(state));
            layout.addOnAttachStateChangeListener(state);
            log("stage=observe registered attached=" + layout.isAttachedToWindow());
        }
        state.watch(); state.apply();
    }

    private static State state(View layout) {
        WeakReference<State> reference = STATES.get(layout);
        return reference == null ? null : reference.get();
    }

    private static final class Config {
        final boolean enabled = enabled(), separated = separatedStyle();
        final int corner = cornerRadiusDp(), side = sideMarginDp(), top = topGapDp();
        final int extra = extraGapDp(), elevation = elevationDp(), component = componentGapDp();
        final int glass = ChatGlassSurface.mode(), glassBlur = ChatGlassSurface.blurRadiusDp(glass);
    }

    private static final class State implements View.OnAttachStateChangeListener, ViewTreeObserver.OnPreDrawListener {
        final WeakReference<View> layoutRef;
        ViewTreeObserver observer;
        View header, recycler, contentHost;
        FloatingHeaderStyle.WindowHeaderState windowHeader;
        FloatingHeaderStyle.CardState headerStyle;
        FloatingHeaderStyle.SeparatedState separated;
        FloatingHeaderStyle.TopEdgeState topEdge;
        final WeakHashMap<View, FloatingHeaderStyle.CardState> extraStyles = new WeakHashMap<>();
        final WeakHashMap<View, Margin> extraMargins = new WeakHashMap<>();
        final WeakHashMap<View, Chrome> tipsChrome = new WeakHashMap<>();
        FloatingHeaderStyle.CardState tipsStyle;
        Margin tipsMargin;
        View tips;
        int tipsTick;
        int listExtra;
        int listWrittenTop = Integer.MIN_VALUE;
        String tipsStage;
        int tipsLogs;
        HeaderParent originalHeaderParent;
        Padding originalPadding;
        Margin originalHeaderMargin;
        boolean posted, stripCaptured, stripChanged, applying, warned;
        Runnable reparentTask;
        String stage;
        Paint strip;
        int stripAlpha;

        State(View layout) { layoutRef = new WeakReference<>(layout); }

        void watch() {
            View layout = layoutRef.get();
            if (!config.enabled || layout == null || !layout.isAttachedToWindow()) { unwatch(); return; }
            ViewTreeObserver next = layout.getViewTreeObserver();
            if (observer == next && next.isAlive()) return;
            unwatch(); if (next.isAlive()) { observer = next; observer.addOnPreDrawListener(this); }
        }

        @Override public boolean onPreDraw() { apply(); return true; }

        void apply() {
            View layout = layoutRef.get();
            if (layout == null || applying) return;
            if (!config.enabled || !layout.isAttachedToWindow()) { restore(); return; }
            if (!layout.isShown()) { restore(); return; }
            Activity activity = ChatToolbarLifecycle.findActivity(layout.getContext());
            // A ChattingUILayout in the tree already proves the chat context; the host
            // Activity class is not enumerated here because a notification launch may
            // host the same layout in a wrapper we do not know by name.
            if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
            applying = true;
            try {
                View current = header != null && header.isAttachedToWindow() && header.isShown()
                        && originalHeaderParent != null && originalHeaderParent.moved ? header : findHeader(layout, activity);
                if (current == null || current.getWidth() <= 0 || current.getHeight() <= 0) {
                    diagnose("waiting-header"); return;
                }
                if (header != current) { restoreHeader(); header = current; originalHeaderMargin = Margin.of(current); originalHeaderParent = HeaderParent.capture(current); }
                // Establish an exact chat title before touching any window/background ownership.
                topEdge = FloatingHeaderStyle.applyTopEdgeToEdge(layout, activity, topEdge);
                suppressBottomStrip(layout);
                windowHeader = FloatingHeaderStyle.applyWindowHeader(layout, header, activity, windowHeader);
                if (header.getParent() == layout) scheduleReparent(layout, header);
                // WeKit style routing: the integrated card fit owns the header visuals in
                // INTEGRATED mode only. In SEPARATED mode the decoration owns drawing; a
                // card fit here would re-fit a full-width white card every frame (white bar).
                if (config.separated) {
                    separated = FloatingHeaderStyle.applySeparated(header, separated,
                            config.corner, config.component, config.elevation, layout);
                    if (separated == null) headerStyle = FloatingHeaderStyle.applyCard(header, headerStyle,
                            config.corner, config.elevation, config.glass, config.glassBlur);
                } else {
                    FloatingHeaderStyle.restoreSeparated(separated); separated = null;
                    headerStyle = FloatingHeaderStyle.applyCard(header, headerStyle,
                            config.corner, config.elevation, config.glass, config.glassBlur);
                }
                applyMargins(layout, header);
                applyExtras(layout, header);
                applyTips(layout, header);
                applyListPadding(layout, header);
                diagnose("applied");
            } catch (Throwable error) {
                if (!warned) { warned = true; unavailable("apply", error); }
                restore();
            } finally { applying = false; }
        }

        private void diagnose(String next) {
            if (!next.equals(stage)) { stage = next; log("stage=" + next); }
        }

        private void scheduleReparent(View layout, View header) {
            if (posted) return;
            posted = true;
            reparentTask = () -> {
                posted = false; reparentTask = null;
                if (!config.enabled || !layout.isAttachedToWindow() || !layout.isShown() || this.header != header
                        || header.getParent() != layout || !(layout.getParent() instanceof RelativeLayout)) return;
                try {
                    ViewGroup parent = (ViewGroup) header.getParent();
                    RelativeLayout root = (RelativeLayout) layout.getParent();
                    int height = header.getLayoutParams() == null ? ViewGroup.LayoutParams.WRAP_CONTENT : header.getLayoutParams().height;
                    if (originalHeaderParent == null) return;
                    parent.removeView(header); originalHeaderParent.moved = true;
                    RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height);
                    params.addRule(RelativeLayout.ALIGN_PARENT_TOP);
                    root.addView(header, params);
                    apply();
                } catch (Throwable error) { unavailable("reparent", error); restore(); }
            };
            MAIN.post(reparentTask);
        }

        private void applyMargins(View layout, View header) {
            if (!(header.getLayoutParams() instanceof ViewGroup.MarginLayoutParams)) return;
            float density = header.getResources().getDisplayMetrics().density;
            Insets insets = SystemBarsCoordinator.bars(layout);
            int top = insets.top + Math.round(config.top * density);
            int side = Math.round(config.side * density);
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) header.getLayoutParams();
            if (params.leftMargin != side || params.rightMargin != side || params.topMargin != top) {
                params.leftMargin = side; params.rightMargin = side; params.topMargin = top; header.setLayoutParams(params);
            }
        }

        private void applyExtras(View layout, View header) {
            if (!(layout instanceof ViewGroup) || header.getParent() == layout) return;
            View host = contentHost(layout);
            if (host == null) return;
            ViewGroup group = (ViewGroup) layout;
            int gap = Math.round(config.extra * layout.getResources().getDisplayMetrics().density);
            int top = SystemBarsCoordinator.bars(layout).top + header.getHeight() + Math.round(config.top * layout.getResources().getDisplayMetrics().density) + gap;
            boolean first = true;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                if (child == host || TIPS.equals(child.getClass().getName()) || child.getVisibility() == View.GONE
                        || child.getClass().getName().equals("android.view.ViewStub")) continue;
                FloatingHeaderStyle.CardState previous = extraStyles.get(child);
                FloatingHeaderStyle.CardState styled = FloatingHeaderStyle.applyCard(child, previous, config.corner, config.elevation);
                extraStyles.put(child, styled);
                if (!extraMargins.containsKey(child) && child.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                    extraMargins.put(child, Margin.of(child));
                }
                if (child.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                    ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) child.getLayoutParams();
                    params.leftMargin = Math.round(config.side * layout.getResources().getDisplayMetrics().density);
                    params.rightMargin = params.leftMargin; params.topMargin = first ? top : gap;
                    child.setLayoutParams(params); first = false;
                }
            }
        }

        /**
         * Group-chat pinned message / announcement stack ({@code ChatTipsBarGroup}).
         *
         * <p>The host inflates it beside the chat content or inside a wrapping container. Its
         * card therefore starts inside the status bar strip, above the floating title, and would be
         * covered by the glass card. Give it the same side margin, park it directly below the
         * floating title and dress it as a second, independent material card; the host keeps
         * owning its visibility, its data and its fold/expand height.</p>
         *
         * <p>The panel itself paints a background ({@code background_tipsbar}) plus a scrim and a
         * hairline, all of which would hide the material. Their backgrounds are detached while the
         * feature runs and restored on {@link #restoreTips()}.</p>
         */
        private void applyTips(View layout, View header) {
            View host = contentHost(layout);
            if (host == null) { diagnoseTips("NO_CONTENT", layout, null); return; }
            if (tips == null || !tips.isAttachedToWindow() || !isDescendant(tips, layout)) {
                // The stub is inflated lazily by the host, and a re-entered chat inflates a new one.
                if (tips != null) restoreTips();
                if (tipsTick++ % 8 != 0) return;
                tips = findChatChrome(layout, TIPS, 128);
                if (tips == null) { diagnoseTips("MISSING", layout, host); return; }
                tipsMargin = Margin.of(tips);
            }
            float density = layout.getResources().getDisplayMetrics().density;
            int side = Math.round(config.side * density);
            int[] origin = new int[2], parentOrigin = new int[2];
            layout.getLocationOnScreen(origin);
            View parent = tips.getParent() instanceof View ? (View) tips.getParent() : null;
            if (parent != null) parent.getLocationOnScreen(parentOrigin);
            int top = origin[1] - parentOrigin[1] - (parent == null ? 0 : parent.getPaddingTop())
                    + SystemBarsCoordinator.bars(layout).top + Math.round(config.top * density)
                    + header.getHeight() + Math.round(config.extra * density);
            if (tips.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) tips.getLayoutParams();
                if (params.leftMargin != side || params.rightMargin != side || params.topMargin != top) {
                    params.leftMargin = side; params.rightMargin = side; params.topMargin = top;
                    tips.setLayoutParams(params);
                }
            }
            if (tips.getVisibility() != View.VISIBLE || !tips.isShown()) {
                diagnoseTips("HIDDEN", layout, tips); return;
            }
            tipsStyle = FloatingHeaderStyle.applyCard(tips, tipsStyle, config.corner, config.elevation,
                    config.glass, config.glassBlur);
            detachTipsPanel();
            diagnoseTips("APPLIED", layout, tips);
        }

        /**
         * The card, the scrim and the hairline inside the tip group are host chrome; the material
         * replaces them. The host repaints the panel background on every fold/expand, so this runs
         * per frame. The first background seen for each child is kept for restore.
         */
        private void detachTipsPanel() {
            if (!(tips instanceof ViewGroup)) return;
            ArrayDeque<View> queue = new ArrayDeque<>(); queue.add(tips); int admitted = 1;
            while (!queue.isEmpty()) {
                View child = queue.removeFirst();
                int id = child.getId();
                // WeChat 8.0.76 chrome lives inside a wrapping FrameLayout, not on the group.
                if (id == 0x7f093d1f || id == 0x7f096d83 || id == 0x7f096d7c) {
                    if (child.getBackground() != null) {
                        if (!tipsChrome.containsKey(child)) tipsChrome.put(child, new Chrome(child));
                        child.setBackground(null);
                    }
                }
                if (id == 0x7f095a10 || !(child instanceof ViewGroup)) continue;
                ViewGroup group = (ViewGroup) child;
                if (group.getChildCount() > 64 - admitted) continue;
                for (int i = 0; i < group.getChildCount(); i++) { queue.addLast(group.getChildAt(i)); admitted++; }
            }
        }

        private void diagnoseTips(String reason, View layout, View view) {
            String next = reason + " parent=" + (view != null && view.getParent() instanceof View
                    ? view.getParent().getClass().getName() : "none");
            if (next.equals(tipsStage) || tipsLogs >= 12) return;
            tipsStage = next; tipsLogs++;
            int[] origin = new int[2]; if (view != null) view.getLocationOnScreen(origin);
            int margin = view != null && view.getLayoutParams() instanceof ViewGroup.MarginLayoutParams
                    ? ((ViewGroup.MarginLayoutParams) view.getLayoutParams()).topMargin : 0;
            log("stage=tips reason=" + next + " xy=" + origin[0] + "," + origin[1]
                    + " height=" + (view == null ? 0 : view.getHeight()) + " topMargin=" + margin);
            if ("MISSING".equals(reason) || "NO_CONTENT".equals(reason)) {
                for (View root : new View[]{layout, view}) {
                    if (!(root instanceof ViewGroup)) continue;
                    ViewGroup group = (ViewGroup) root;
                    for (int i = 0; i < Math.min(6, group.getChildCount()); i++) {
                        View child = group.getChildAt(i);
                        log("stage=tips-child class=" + child.getClass().getName() + " id=" + child.getId()
                                + " visibility=" + child.getVisibility() + " size=" + child.getWidth() + "x" + child.getHeight());
                    }
                }
            }
        }

        /**
         * Keeps the host's own list padding and only adds our own inset on top of it.
         *
         * <p>{@code ChatTipsBarGroup} manages the message list padding through
         * {@code setListViewPaddingTop()} with a baseline stored in {@code View.setTag(0)}, so any
         * whole-value write here fights the host. Strip our previous inset only while the current
         * value still matches our last write; a different value belongs to the host.
         * The scoped native tips writer hook releases our inset before it reads its baseline.</p>
         */
        private void applyListPadding(View layout, View header) {
            View current = findRecycler(layout);
            if (current == null) return;
            if (recycler != current || originalPadding == null) {
                restoreRecycler();
                recycler = current; originalPadding = Padding.of(current); listExtra = 0;
                listWrittenTop = Integer.MIN_VALUE;
            }
            float density = layout.getResources().getDisplayMetrics().density;
            // While the tip card is up it sits one gap below the title card, so the list must clear
            // that gap too; with no tip card the title is the last row and the gap would be slack.
            boolean card = tips != null && tips.getVisibility() == View.VISIBLE && tips.isShown();
            int inset = SystemBarsCoordinator.bars(layout).top + header.getHeight()
                    + Math.round(config.top * density) + (card ? Math.round(config.extra * density) : 0);
            int host = current.getPaddingTop();
            if (host == listWrittenTop) host -= listExtra;
            int top = host + inset;
            listExtra = inset;
            listWrittenTop = top;
            if (current.getPaddingTop() != top) {
                current.setPadding(current.getPaddingLeft(), top, current.getPaddingRight(), current.getPaddingBottom());
                if (current instanceof ViewGroup) ((ViewGroup) current).setClipToPadding(false);
            }
        }

        private View contentHost(View layout) {
            if (contentHost != null && contentHost.isAttachedToWindow()) return contentHost;
            if (!(layout instanceof ViewGroup)) return null;
            ViewGroup group = (ViewGroup) layout;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                if (containsClass(child, SCROLL, 384)) { contentHost = child; return child; }
            }
            return null;
        }

        private void suppressBottomStrip(View layout) {
            if (!stripCaptured) {
                for (Field field : layout.getClass().getDeclaredFields()) {
                    if (!Modifier.isStatic(field.getModifiers()) && field.getType() == Paint.class) {
                        try { field.setAccessible(true); strip = (Paint) field.get(layout); }
                        catch (IllegalAccessException ignored) { }
                        break;
                    }
                }
                stripCaptured = true;
                if (strip != null) stripAlpha = strip.getAlpha();
            }
            if (strip != null && strip.getAlpha() != 0) { strip.setAlpha(0); stripChanged = true; layout.invalidate(); }
        }

        void restore() {
            if (reparentTask != null) MAIN.removeCallbacks(reparentTask);
            reparentTask = null; posted = false;
            restoreHeader(); restoreTips(); restoreRecycler();
            for (FloatingHeaderStyle.CardState state : extraStyles.values()) FloatingHeaderStyle.restore(state);
            for (Map.Entry<View, Margin> entry : extraMargins.entrySet()) entry.getValue().restore(entry.getKey());
            extraStyles.clear(); extraMargins.clear();
            FloatingHeaderStyle.restoreTopEdge(topEdge); topEdge = null;
            View layout = layoutRef.get(); if (layout != null && stripChanged && strip != null && strip.getAlpha() == 0) { strip.setAlpha(stripAlpha); layout.invalidate(); }
        }
        void restoreHeader() {
            FloatingHeaderStyle.restoreSeparated(separated); separated = null;
            FloatingHeaderStyle.restoreWindowHeader(windowHeader); windowHeader = null;
            FloatingHeaderStyle.restore(headerStyle); headerStyle = null;
            if (header != null && originalHeaderMargin != null) originalHeaderMargin.restore(header);
            if (header != null && originalHeaderParent != null) originalHeaderParent.restore(header);
            header = null; originalHeaderMargin = null; originalHeaderParent = null;
        }
        void restoreRecycler() {
            if (recycler != null && originalPadding != null) {
                releaseListInset();
                if (recycler instanceof ViewGroup) ((ViewGroup) recycler).setClipToPadding(originalPadding.clip);
            }
            recycler = null; originalPadding = null; listExtra = 0; listWrittenTop = Integer.MIN_VALUE;
        }
        void releaseListInset() {
            if (recycler != null && recycler.getPaddingTop() == listWrittenTop) {
                recycler.setPadding(recycler.getPaddingLeft(), recycler.getPaddingTop() - listExtra,
                        recycler.getPaddingRight(), recycler.getPaddingBottom());
            }
            listExtra = 0; listWrittenTop = Integer.MIN_VALUE;
        }
        void restoreTips() {
            FloatingHeaderStyle.restore(tipsStyle); tipsStyle = null;
            for (Chrome chrome : tipsChrome.values()) chrome.restore();
            tipsChrome.clear();
            if (tips != null && tipsMargin != null) tipsMargin.restore(tips);
            tips = null; tipsMargin = null; tipsTick = 0;
        }
        void unwatch() { if (observer != null && observer.isAlive()) observer.removeOnPreDrawListener(this); observer = null; }
        @Override public void onViewAttachedToWindow(View view) { watch(); apply(); }
        @Override public void onViewDetachedFromWindow(View view) {
            unwatch(); restore(); contentHost = null;
            if (discovery != null && discovery.activityRef.get() == ChatToolbarLifecycle.findActivity(view.getContext())) cancelDiscovery();
            // The view still owns this listener; retain the weak entry for idempotent reattach.
        }
    }

    private static View findHeader(View layout, Activity activity) {
        // Notification-entry chats reach the header from outside the feature's own island.
        return ChatEntrySupport.headerFallback(layout, activity, findHeaderOriginal(layout, activity));
    }

    private static View findHeaderOriginal(View layout, Activity activity) {
        HeaderSearch local = titleSearch(layout, activity, 512);
        if (!local.complete || local.titles > 1) return null;
        if (local.title != null) return local.title;
        if (isStandalone(activity, layout)) {
            HeaderSearch all = titleSearch(activity.getWindow().getDecorView(), activity, 768);
            return all.complete && all.titles == 1 && all.layouts == 1 ? all.title : null;
        }
        // Embedded Launcher chat: only its immediate RelativeLayout island. Never
        // climb to decor/content and accidentally take the home ActionBarContainer.
        if (LAUNCHER_ACTIVITY.equals(activity.getClass().getName()) && layout.getParent() instanceof RelativeLayout) {
            View root = (View) layout.getParent();
            if (root == activity.getWindow().getDecorView() || root == activity.findViewById(android.R.id.content)) return null;
            HeaderSearch siblings = titleSearch(root, activity, 384);
            return siblings.complete && siblings.titles == 1 && siblings.layouts == 1 ? siblings.title : null;
        }
        return null;
    }

    private static final class HeaderSearch {
        View title; int titles, layouts; boolean complete = true;
    }

    private static HeaderSearch titleSearch(View root, Activity activity, int limit) {
        HeaderSearch result = new HeaderSearch();
        ArrayDeque<View> queue = new ArrayDeque<>(); queue.add(root); int admitted = 1;
        while (!queue.isEmpty()) {
            View view = queue.removeFirst();
            if (view.getVisibility() != View.VISIBLE || view.getAlpha() <= 0f) continue;
            String name = view.getClass().getName();
            if (LAYOUT.equals(name)) result.layouts++;
            if (HEADER.equals(name)) {
                if (ChatToolbarLifecycle.findActivity(view.getContext()) != activity) { result.complete = false; return result; }
                result.titles++; result.title = view; continue;
            }
            if (FOOTER.equals(name) || LIST_HOST.equals(name) || LIST_A.equals(name) || LIST_B.equals(name)) continue;
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                if (group.getChildCount() > limit - admitted) { result.complete = false; return result; }
                for (int i = 0; i < group.getChildCount(); i++) { queue.addLast(group.getChildAt(i)); admitted++; }
            }
        }
        return result;
    }

    private static boolean isStandalone(Activity activity, View layout) {
        if (!CHAT_ACTIVITY.equals(activity.getClass().getName())) return false;
        View content = activity.findViewById(android.R.id.content);
        return content != null && isDescendant(layout, content);
    }

    private static View findRecycler(View root) {
        View host = findUnique(root, LIST_HOST, 768);
        if (host == null) return null;
        View a = findUnique(host, LIST_A, 512), b = findUnique(host, LIST_B, 512);
        if (a != null && b != null && a != b) return null;
        if (a != null) return a;
        if (b != null) return b;
        try {
            Class<?> recycler = Class.forName("androidx.recyclerview.widget.RecyclerView", false, host.getClass().getClassLoader());
            return findUniqueInstance(host, recycler, 512);
        } catch (ClassNotFoundException ignored) { return null; }
    }

    private static View findUnique(View root, String className, int limit) {
        View found = null; ArrayDeque<View> queue = new ArrayDeque<>(); queue.add(root);
        for (int visited = 0; !queue.isEmpty() && visited < limit; visited++) {
            View view = queue.removeFirst();
            if (className.equals(view.getClass().getName())) { if (found != null && found != view) return null; found = view; }
            if (view instanceof ViewGroup) { ViewGroup group = (ViewGroup) view; for (int i = 0; i < group.getChildCount(); i++) queue.addLast(group.getChildAt(i)); }
        }
        return found;
    }

    private static View findUniqueInstance(View root, Class<?> type, int limit) {
        View found = null; ArrayDeque<View> queue = new ArrayDeque<>(); queue.add(root);
        for (int visited = 0; !queue.isEmpty() && visited < limit; visited++) {
            View view = queue.removeFirst();
            if (type.isInstance(view)) { if (found != null && found != view) return null; found = view; }
            if (view instanceof ViewGroup) { ViewGroup group = (ViewGroup) view; for (int i = 0; i < group.getChildCount(); i++) queue.addLast(group.getChildAt(i)); }
        }
        return found;
    }

    private static boolean containsClass(View root, String name, int limit) { return findUnique(root, name, limit) != null; }
    private static View findChatChrome(View root, String className, int limit) {
        View found = null; ArrayDeque<View> queue = new ArrayDeque<>(); queue.add(root); int admitted = 1;
        while (!queue.isEmpty()) {
            View view = queue.removeFirst(); String name = view.getClass().getName();
            if (className.equals(name)) { if (found != null && found != view) return null; found = view; continue; }
            if (FOOTER.equals(name) || HEADER.equals(name) || LIST_HOST.equals(name)
                    || LIST_A.equals(name) || LIST_B.equals(name)) continue;
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                if (group.getChildCount() > limit - admitted) continue;
                for (int i = 0; i < group.getChildCount(); i++) { queue.addLast(group.getChildAt(i)); admitted++; }
            }
        }
        return found;
    }
    private static boolean isDescendant(View child, View ancestor) { for (View current = child; current != null; current = current.getParent() instanceof View ? (View) current.getParent() : null) if (current == ancestor) return true; return false; }
    private static java.lang.reflect.Method inherited(Class<?> type, String name, Class<?>... parameters) throws NoSuchMethodException { for (Class<?> current = type; current != null; current = current.getSuperclass()) try { java.lang.reflect.Method method = current.getDeclaredMethod(name, parameters); method.setAccessible(true); return method; } catch (NoSuchMethodException ignored) { } throw new NoSuchMethodException(name); }

    private static final class HeaderParent {
        final WeakReference<ViewGroup> parent; final int index; final ViewGroup.LayoutParams params;
        boolean moved;
        HeaderParent(ViewGroup parent, int index, ViewGroup.LayoutParams params) { this.parent=new WeakReference<>(parent);this.index=index;this.params=params; }
        static HeaderParent capture(View view) { if (!(view.getParent() instanceof ViewGroup)) return null; ViewGroup parent=(ViewGroup)view.getParent(); return new HeaderParent(parent,parent.indexOfChild(view),copyParams(view.getLayoutParams())); }
        void restore(View view) { ViewGroup target=parent.get(); if(target==null||view.getParent()==target)return; if(view.getParent() instanceof ViewGroup)((ViewGroup)view.getParent()).removeView(view); target.addView(view,Math.min(Math.max(0,index),target.getChildCount()),params); }
    }
    private static ViewGroup.LayoutParams copyParams(ViewGroup.LayoutParams source){if(source==null)return null;if(source instanceof RelativeLayout.LayoutParams)return new RelativeLayout.LayoutParams((RelativeLayout.LayoutParams)source);if(source instanceof ViewGroup.MarginLayoutParams)return new ViewGroup.MarginLayoutParams((ViewGroup.MarginLayoutParams)source);return new ViewGroup.LayoutParams(source);}
    private static final class Padding { final int left, top, right, bottom; final boolean clip; Padding(int l, int t, int r, int b, boolean clip) { left=l;top=t;right=r;bottom=b;this.clip=clip; } static Padding of(View v){return new Padding(v.getPaddingLeft(),v.getPaddingTop(),v.getPaddingRight(),v.getPaddingBottom(),!(v instanceof ViewGroup)||((ViewGroup)v).getClipToPadding());} void restore(View v){v.setPadding(left,top,right,bottom);if(v instanceof ViewGroup)((ViewGroup)v).setClipToPadding(clip);} }
    private static final class Margin { final int left, top, right, bottom; Margin(int l,int t,int r,int b){left=l;top=t;right=r;bottom=b;} static Margin of(View v){if(!(v.getLayoutParams() instanceof ViewGroup.MarginLayoutParams))return null;ViewGroup.MarginLayoutParams p=(ViewGroup.MarginLayoutParams)v.getLayoutParams();return new Margin(p.leftMargin,p.topMargin,p.rightMargin,p.bottomMargin);} void restore(View v){if(v.getLayoutParams() instanceof ViewGroup.MarginLayoutParams){ViewGroup.MarginLayoutParams p=(ViewGroup.MarginLayoutParams)v.getLayoutParams();p.setMargins(left,top,right,bottom);v.setLayoutParams(p);}} }
    /** Host chrome we detach while the tip card carries the material; only the fill is kept. */
    private static final class Chrome { final View view; final Drawable background; Chrome(View view){this.view=view;this.background=view.getBackground();} void restore(){if(view.getBackground()==null)view.setBackground(background);} }
    private static void log(String message) { XposedBridge.log("WEX: 悬浮顶栏: " + message); }
}
