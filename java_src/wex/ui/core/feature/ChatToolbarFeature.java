package wex.ui.core.feature;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.ref.WeakReference;
import java.lang.reflect.Member;
import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.mindlink.xposed.XC_MethodHook;
import dev.mindlink.xposed.XposedBridge;
import dev.mindlink.xposed.XposedHelpers;
import dev.mindlink.xposed.callbacks.XC_LoadPackage;
import wex.ui.core.WexStore;

/**
 * 聊天工具栏。
 *
 * 来源：工具名表、系统拍摄、面板尺寸喂入算法（initAppGrid 前注入 width/height、
 * MMDotView 22dp、竖屏 215dp / 横屏 158dp 兜底）与工具排序/启用配置均参照
 * WeKit 的 `features/items/chat/ChatToolbar.kt`（GPL-3.0）；本文件改写为 Java 实现。
 *
 * 关键设计：
 *
 *  1. 不主动预热！ 而是 hook AppPanel.initAppGrid：
 *     - hookBefore：在微信即将执行 initAppGrid 前，替它把「面板尺寸」算好并喂给 measurer，
 *       因为微信依赖 MMFlipper.onMeasure 才会把真实尺寸给 measurer（g.a）；
 *     - hookAfter：微信自己跑完 initAppGrid 后再快照工具列表。
 *     这样微信何时初始化面板由它自己决定，我们只是「搭便车」，
 *     不会在不适当时机触发面板逻辑（避免「待对方回复才可以发送」这类副作用）。
 *
 *  2. 点工具时直接执行「缓存的格子点击回调」：
 *     MenuItem 里保存 grid + itemView + position + OnItemClickListener，
 *     点击时直接 listener.onItemClick(grid, itemView, position, 0)，完全不展开面板。
 *
 *  3. 尺寸计算：
 *     width  = screen width
 *     height = 容器(0,0) 的 layoutParams.height - 22dp（dot 条）
 *     取不到容器高度时，竖屏 fallback 215dp / 横屏 158dp。
 *
 *  4. 界面：图标 + 文字的胶囊 chip，横向 LazyRow（这里用 HorizontalScrollView + LinearLayout 等价实现）。
 */
public final class ChatToolbarFeature {

    private static final String TAG = "WEX: 聊天工具栏: ";
    private static final String KEY_ENABLED = "chat_toolbar_enabled";
    private static final String KEY_ORDER = "chat_toolbar_order";
    private static final String KEY_ENABLED_ITEMS = "chat_toolbar_enabled_items";
    private static final String TAG_TOOLBAR = "wex_chat_toolbar";
    private static final int TAG_TRANSPARENT_WATCH = 0x7E000101;
    private static final int TAG_LIFECYCLE = 0x7E000102;
    private static final WeakHashMap<ViewGroup, ChatToolbarLifecycle> FOOTERS =
            new WeakHashMap<ViewGroup, ChatToolbarLifecycle>();
    private static final Set<Member> HOOKED = new HashSet<Member>();
    private static final android.os.Handler MAIN = new android.os.Handler(android.os.Looper.getMainLooper());
    private static final String LAUNCHER_ACTIVITY = "com.tencent.mm.ui.LauncherUI";
    private static final String CHAT_ACTIVITY = "com.tencent.mm.ui.chatting.ChattingUI";
    private static WeakReference<Activity> resumed = new WeakReference<>(null);
    private static ToolbarDiscovery discovery;
    private static final WeakHashMap<ViewGroup, InputWatch> INPUT_WATCHES = new WeakHashMap<>();
    private static final WeakHashMap<ViewGroup, Runnable> LOAD_TASKS = new WeakHashMap<>();
    private static final WeakHashMap<View, Drawable> ORIGINAL_BACKGROUNDS =
            new WeakHashMap<View, Drawable>();
    private static final WeakHashMap<View, View.OnLayoutChangeListener> BACKGROUND_LISTENERS =
            new WeakHashMap<View, View.OnLayoutChangeListener>();
    /** 必须始终透明的 View（微信会反复重设背景，这里统一拦截）。 */
    private static final java.util.WeakHashMap<View, Boolean> KEEP_TRANSPARENT =
            new java.util.WeakHashMap<View, Boolean>();
    private static final ThreadLocal<Boolean> SELF_BG = new ThreadLocal<Boolean>();
    private static boolean bgGuardInstalled = false;
    private static final String CLS_FOOTER = "com.tencent.mm.pluginsdk.ui.chat.ChatFooter";
    private static final String CLS_APP_PANEL = "com.tencent.mm.pluginsdk.ui.chat.AppPanel";
    /** 工具栏图标渲染边长（dp）。 */
    private static final float ICON_DP = 18f;
    /** Grid units of inner padding: Lucide's round stroke edges reach the box edge and were clipped. */
    private static final float ICON_PADDING = 1f;

    private static final String[][] TOOLS = {
            {"相册", "album.png"},
            {"拍摄", "shoot.png"},
            {"系统拍摄", "shoot.png"},
            {"视频通话", "video_call.png"},
            {"语音通话", "voice_call.png"},
            {"位置", "location.png"},
            {"红包", "redpacket.png"},
            {"礼物", "gift.png"},
            {"转账", "transfer.png"},
            {"语音输入", "voice_input.png"},
            {"文件", "file.png"},
            {"收藏", "favorite.png"},
            {"接龙", "chain.png"},
            {"个人名片", "card.png"},
            {"音乐", "music.png"},
    };

    private static final class PanelActions {
        final Map<String, Runnable> clicks = new HashMap<String, Runnable>();
        Runnable systemCamera;
    }

    /** Each ChatFooter owns one AppPanel; actions must never leak between conversations. */
    private static final WeakHashMap<ViewGroup, PanelActions> PANEL_ACTIONS =
            new WeakHashMap<ViewGroup, PanelActions>();
    private static android.content.res.AssetManager moduleAssets;
    private static volatile Method initAppGridMethod;

    public static final ChatToolbarFeature INSTANCE = new ChatToolbarFeature();

    private ChatToolbarFeature() {
    }

    public synchronized void init(XC_LoadPackage.LoadPackageParam lpparam) {
        final ClassLoader cl = lpparam.classLoader;
        try {
            Class<?> footer = XposedHelpers.findClass(CLS_FOOTER, cl);
            for (Constructor<?> ctor : footer.getDeclaredConstructors()) {
                if (HOOKED.contains(ctor)) {
                    continue;
                }
                XposedBridge.hookMethod(ctor, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            if (param.thisObject instanceof ViewGroup) {
                                observeFooter((ViewGroup) param.thisObject);
                            } else {
                                log("输入组件不是 ViewGroup，无法挂载");
                            }
                        } catch (Throwable t) {
                            log("监听输入组件失败 " + t);
                        }
                    }
                });
                HOOKED.add(ctor);
            }
            Method attached = inherited(footer, "onAttachedToWindow");
            if (!HOOKED.contains(attached)) {
                XposedBridge.hookMethod(attached, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        if (param.thisObject instanceof ViewGroup) observeFooter((ViewGroup) param.thisObject);
                    }
                });
                HOOKED.add(attached);
            }
            Method laidOut = inherited(footer, "onLayout", boolean.class, int.class, int.class, int.class, int.class);
            if (!HOOKED.contains(laidOut)) {
                XposedBridge.hookMethod(laidOut, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        if (param.thisObject instanceof ViewGroup && CLS_FOOTER.equals(param.thisObject.getClass().getName())
                                && !FOOTERS.containsKey((ViewGroup) param.thisObject)) {
                            observeFooter((ViewGroup) param.thisObject);
                        }
                    }
                });
                HOOKED.add(laidOut);
            }
            log("输入组件构造与 attach Hook 已注册，父类=" + footer.getSuperclass().getName()
                    + "，开关=" + isEnabled());
        } catch (Throwable t) {
            log("输入组件注册失败 " + t);
        }
        hookActivity(Activity.class);
        for (String name : new String[]{LAUNCHER_ACTIVITY, CHAT_ACTIVITY}) {
            try { hookActivity(XposedHelpers.findClass(name, cl)); }
            catch (Throwable error) { log("stage=activity-class unavailable=" + error.getClass().getSimpleName()); }
        }
        try {
            final Class<?> panel = XposedHelpers.findClass(CLS_APP_PANEL, cl);
            HostTargetResolver.resolveMethod("chat_toolbar_init_grid", new HostTargetResolver.MethodSpec()
                    .declaredClass(CLS_APP_PANEL).returnType("void").parameterTypes(CLS_APP_PANEL)
                    .usingExactStrings("MicroMsg.AppPanel", "initAppGrid()"), new HostTargetResolver.MethodCallback() {
                @Override public void onResolved(Method method) {
                    MAIN.post(() -> {
                        try { hookInitGrid(method); initAppGridMethod = method; }
                        catch (Throwable error) { log("stage=init-grid unavailable=" + error.getClass().getSimpleName()); }
                    });
                }
                @Override public void onFailure(Throwable error) { log("initAppGrid 解析失败；工具栏保留但原生工具暂不可派发"); }
            });
        } catch (Throwable t) {
            log("工具列表注册失败 " + t);
        }
        probeEnable();
    }

    private static synchronized void hookInitGrid(Method initGrid) {
        if (initGrid == null || HOOKED.contains(initGrid)) return;
        XposedBridge.hookMethod(initGrid, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                if (!isEnabled()) return;
                Object panel = panelArgument(param);
                if (panel instanceof ViewGroup) feedPanelSize((ViewGroup) panel);
            }
            @Override protected void afterHookedMethod(MethodHookParam param) {
                if (!isEnabled()) return;
                Object panel = panelArgument(param);
                if (panel instanceof ViewGroup) {
                    snapshot((ViewGroup) panel);
                    PanelActions actions=panelActions((ViewGroup)panel,false);
                    log("initAppGrid after 快照，工具数="+(actions==null?0:actions.clicks.size()));
                    View current = (View) panel;
                    StringBuilder parents = new StringBuilder();
                    boolean found = false;
                    for (int depth = 0; current != null && depth < 24; depth++) {
                        if (panelOwnerLogs < 8 && depth < 8) {
                            if (parents.length() > 0) parents.append(" > ");
                            parents.append(current.getClass().getName());
                        }
                        if (CLS_FOOTER.equals(current.getClass().getName()) && current instanceof ViewGroup) {
                            observeFooter((ViewGroup) current);
                            found = true;
                            break;
                        }
                        current = current.getParent() instanceof View ? (View) current.getParent() : null;
                    }
                    if (panelOwnerLogs < 8) {
                        panelOwnerLogs++;
                        log("stage=panel-owner found=" + found + " attached=" + ((View) panel).isAttachedToWindow()
                                + " chain=" + parents);
                    }
                }
            }
        });
        HOOKED.add(initGrid);log("已解析并 Hook initAppGrid "+initGrid.toGenericString());
    }

    private static int panelOwnerLogs;

    private static Object panelArgument(XC_MethodHook.MethodHookParam param) {
        if(param.thisObject instanceof ViewGroup&&CLS_APP_PANEL.equals(param.thisObject.getClass().getName()))return param.thisObject;
        for(Object arg:param.args)if(arg instanceof ViewGroup&&CLS_APP_PANEL.equals(arg.getClass().getName()))return arg;
        return null;
    }

    static boolean isEnabled() {
        try {
            return WexStore.INSTANCE.getBool(KEY_ENABLED, false);
        } catch (Throwable t) {
            return false;
        }
    }

    private static void observeFooter(final ViewGroup footer) {
        if (footer == null || !CLS_FOOTER.equals(footer.getClass().getName())) return;
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            WeakReference<ViewGroup> ref = new WeakReference<>(footer); MAIN.post(() -> observeFooter(ref.get())); return;
        }
        ChatToolbarLifecycle existing = FOOTERS.get(footer);
        if (existing != null) {
            InputWatch watch = INPUT_WATCHES.get(footer); if (watch != null) watch.sync();
            existing.refresh();
            return;
        }
        Object nativeTag = footer.getTag(TAG_LIFECYCLE);
        if (nativeTag != null) log("stage=tag occupied type=" + nativeTag.getClass().getName());
        final WeakReference<ViewGroup> ref = new WeakReference<ViewGroup>(footer);
        final ChatToolbarLifecycle lifecycle = new ChatToolbarLifecycle(new ChatToolbarLifecycle.Target() {
            @Override
            public boolean isEnabled() {
                return ChatToolbarFeature.isEnabled();
            }

            @Override
            public boolean isAttached() {
                ViewGroup view = ref.get();
                return view != null && view.isAttachedToWindow();
            }

            @Override
            public boolean mount() {
                ViewGroup view = ref.get();
                return view != null && attach(view);
            }

            @Override
            public void unmount() {
                ViewGroup view = ref.get();
                if (view != null) {
                    removeToolbar(view);
                }
            }

            @Override
            public boolean post(Runnable task, long delay) {
                ViewGroup view = ref.get();
                return view != null && view.postDelayed(task, delay);
            }

            @Override
            public void cancel(Runnable task) {
                ViewGroup view = ref.get();
                if (view != null) {
                    view.removeCallbacks(task);
                }
            }

            @Override
            public void onExhausted() {
                ViewGroup view = ref.get();
                log("挂载仍在等待输入容器，容器=" + (view != null && findInputContainer(view) != null)
                        + " Activity=" + (view != null
                        && ChatToolbarLifecycle.findActivity(view.getContext()) != null)
                        + "（转为慢速重试，直到离开页面）");
            }
        });
        FOOTERS.put(footer, lifecycle);
        if (nativeTag == null) footer.setTag(TAG_LIFECYCLE, lifecycle);
        final InputWatch inputWatch = new InputWatch(footer, lifecycle);
        INPUT_WATCHES.put(footer, inputWatch);
        log("观察输入组件 " + footer.getClass().getName()
                + "，attached=" + footer.isAttachedToWindow());
        footer.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                log("输入组件已附着，开始同步工具栏");
                inputWatch.sync(); lifecycle.refresh();
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                inputWatch.stop(); lifecycle.detach();
                if (discovery != null && discovery.activityRef.get() == ChatToolbarLifecycle.findActivity(v.getContext())) cancelDiscovery();
                ViewGroup target = ref.get();
                if (target != null) {
                    View root = target.getRootView();
                    boolean shared = false;
                    for (ViewGroup other : new ArrayList<ViewGroup>(FOOTERS.keySet())) {
                        if (other != null && other != target && other.isAttachedToWindow()
                                && other.getRootView() == root && other.findViewWithTag(TAG_TOOLBAR) != null) {
                            shared = true;
                            break;
                        }
                    }
                    if (!shared) {
                        restoreBackgrounds(root);
                    }
                }
            }
        });
        inputWatch.sync(); lifecycle.refresh();
    }

    /** Retry on input identity or mounted-shell loss, never on every layout frame. */
    private static final class InputWatch implements android.view.ViewTreeObserver.OnGlobalLayoutListener, Runnable {
        final WeakReference<ViewGroup> footerRef;
        final ChatToolbarLifecycle lifecycle;
        WeakReference<ViewGroup> input = new WeakReference<>(null);
        android.view.ViewTreeObserver observer;
        boolean ready, mounted;
        boolean probeScheduled, settling;
        InputWatch(ViewGroup footer, ChatToolbarLifecycle lifecycle) { footerRef = new WeakReference<>(footer); this.lifecycle = lifecycle; }
        void sync() {
            ViewGroup footer = footerRef.get();
            if (!isEnabled() || footer == null || !footer.isAttachedToWindow()) { stop(); return; }
            android.view.ViewTreeObserver next = footer.getViewTreeObserver();
            if (observer != next) {
                stop();
                if (next.isAlive()) {
                    observer = next;
                    observer.addOnGlobalLayoutListener(this);
                    schedule(0L);
                }
            }
        }
        void verifyAfterMount() {
            if (!settling) {
                settling = true;
                schedule(300L);
            }
        }
        private void schedule(long delay) {
            if (probeScheduled || !isEnabled()) return;
            probeScheduled = true;
            if (!MAIN.postDelayed(this, delay)) probeScheduled = false;
        }
        void stop() {
            if (observer != null && observer.isAlive()) observer.removeOnGlobalLayoutListener(this);
            MAIN.removeCallbacks(this);
            observer = null; input.clear(); ready = false; mounted = false;
            probeScheduled = false; settling = false;
        }
        @Override public void onGlobalLayout() {
            schedule(0L);
        }
        @Override public void run() {
            probeScheduled = false;
            ViewGroup footer = footerRef.get();
            if (!isEnabled() || footer == null || !footer.isAttachedToWindow()) { stop(); return; }
            ViewGroup current = findInputContainer(footer);
            View shell = current == null ? null : footer.findViewWithTag(TAG_TOOLBAR);
            boolean present = visuallyMounted(current, shell);
            input = new WeakReference<>(current); ready = current != null;
            if (present) {
                mounted = true;
                if (settling) {
                    settling = false;
                } else {
                    verifyAfterMount();
                }
                return;
            }
            // Chat entrance animates visibility and the input column arrives late on a
            // cold start, after a notification launch and on every re-entry. Ask again
            // until the shell is really mounted instead of counting a few tries.
            mounted = false;
            settling = false;
            if (!lifecycle.isPending()) lifecycle.refresh();
        }
    }

    public static void refresh() {
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                if (!isEnabled()) {
                    synchronized (PANEL_ACTIONS) {
                        PANEL_ACTIONS.clear();
                    }
                    restoreBackgrounds(null);
                }
                for (InputWatch watch : new ArrayList<InputWatch>(INPUT_WATCHES.values())) watch.sync();
                for (ChatToolbarLifecycle lifecycle : new ArrayList<ChatToolbarLifecycle>(FOOTERS.values())) {
                    lifecycle.refresh();
                }
                Activity activity = resumed.get();
                if (!isEnabled() || !chatActivity(activity)) { cancelDiscovery(); return; }
                if (discovery == null) { discovery = new ToolbarDiscovery(activity); MAIN.post(discovery); }
            }
        });
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
        hookLifecycle(type, "onResume", entered);
        // A notification tap can deliver the chat page through onNewIntent or onStart
        // only, with no further onResume for that Activity.
        hookLifecycle(type, "onNewIntent", entered, Intent.class);
        hookLifecycle(type, "onStart", entered);
        XC_MethodHook stop = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                if (resumed.get() == param.thisObject) {
                    resumed.clear();
                    cancelDiscovery();
                    for (InputWatch watch : new ArrayList<InputWatch>(INPUT_WATCHES.values())) watch.stop();
                    for (ChatToolbarLifecycle lifecycle : new ArrayList<ChatToolbarLifecycle>(FOOTERS.values())) lifecycle.suspend();
                }
            }
        };
        hookLifecycle(type, "onPause", stop); hookLifecycle(type, "onDestroy", stop);
    }

    private static int entryLogs;

    private static void logEntry(XC_MethodHook.MethodHookParam param) {
        if (entryLogs >= 8) return;
        entryLogs++;
        log("stage=entry trigger=" + param.method.getName() + " host=" + param.thisObject.getClass().getName());
    }

    private static void hookLifecycle(Class<?> type, String name, XC_MethodHook callback, Class<?>... parameters) {
        try {
            Method method = inherited(type, name, parameters);
            if (!HOOKED.contains(method)) { XposedBridge.hookMethod(method, callback); HOOKED.add(method); }
        } catch (Throwable error) { log("stage=" + name + " unavailable=" + error.getClass().getSimpleName()); }
    }

    private static boolean chatActivity(Activity activity) {
        return activity != null && !activity.isFinishing() && !activity.isDestroyed()
                && (LAUNCHER_ACTIVITY.equals(activity.getClass().getName()) || CHAT_ACTIVITY.equals(activity.getClass().getName()));
    }

    private static void cancelDiscovery() { if (discovery != null) discovery.cancel(); }

    /** The config store lives on external media; a notification cold start can read it
     *  before that storage is mounted. Each refusal unmounts and stops every retry, so
     *  the first chat entry stayed bare until the page was left and re-entered. */
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
                if (isEnabled()) { probing = false; return; }
                probe(attempt + 1);
            }
        }, PROBE_DELAYS[attempt]);
    }

    private static final class ToolbarDiscovery implements Runnable, View.OnAttachStateChangeListener {
        final WeakReference<Activity> activityRef;
        final WeakReference<View> rootRef;
        int attempts;
        boolean cancelled;
        ToolbarDiscovery(Activity activity) {
            activityRef = new WeakReference<>(activity);
            View root = activity.getWindow() == null ? null : activity.getWindow().getDecorView();
            rootRef = new WeakReference<>(root); if (root != null) root.addOnAttachStateChangeListener(this);
        }
        void cancel() {
            cancelled = true; MAIN.removeCallbacks(this);
            View root = rootRef.get(); if (root != null) root.removeOnAttachStateChangeListener(this);
            if (discovery == this) discovery = null;
        }
        @Override public void run() {
            Activity activity = activityRef.get(); View root = rootRef.get();
            // The decor identity is the stop condition. Resume can belong to another
            // WeChat Activity after a notification launch while this tree stays alive.
            if (cancelled || !isEnabled() || root == null || activity == null
                    || activity.isFinishing() || activity.isDestroyed()
                    || activity.getWindow() == null || activity.getWindow().getDecorView() != root) { cancel(); return; }
            ViewGroup footer = root.isAttachedToWindow() ? findActiveFooter(root, activity) : null;
            if (footer != null) { observeFooter(footer); log("stage=discover found"); cancel(); }
            else {
                attempts++;
                if (attempts == 8) log("stage=discover waiting root=" + root.getClass().getName());
                // The chat tree can remain attached while WeChat inflates the input
                // column after a cold launch or notification transition. Keep one
                // discoverer alive until the Activity/decor leaves, with a slow tail.
                long delay = attempts < 8 ? 120L : 500L;
                MAIN.postDelayed(this, delay);
            }
        }
        @Override public void onViewAttachedToWindow(View view) {
            if (cancelled) return;
            attempts = 0;
            MAIN.removeCallbacks(this);
            MAIN.post(this);
        }
        @Override public void onViewDetachedFromWindow(View view) { cancel(); }
    }

    private static ViewGroup findActiveFooter(View root, Activity activity) {
        ViewGroup structural = null, ready = null, visible = null;
        int structuralCount = 0, readyCount = 0, visibleCount = 0;
        java.util.ArrayDeque<View> queue = new java.util.ArrayDeque<>(); queue.add(root); int admitted = 1;
        while (!queue.isEmpty()) {
            View view = queue.removeFirst();
            if (view.getVisibility() != View.VISIBLE) continue;
            String name = view.getClass().getName();
            if (CLS_FOOTER.equals(name)) {
                if (view instanceof ViewGroup && view.isAttachedToWindow() && view.isShown()
                        && ChatToolbarLifecycle.findActivity(view.getContext()) == activity) {
                    structuralCount++;
                    structural = (ViewGroup) view;
                    if (findInputContainer((ViewGroup) view) != null) {
                        readyCount++;
                        ready = (ViewGroup) view;
                    }
                    if (visibleInTree(view)) {
                        visibleCount++;
                        visible = (ViewGroup) view;
                    }
                }
                continue;
            }
            if (view instanceof android.widget.AbsListView || CLS_APP_PANEL.equals(name)
                    || "com.tencent.mm.view.SmileyPanel".equals(name)
                    || "com.tencent.mm.ui.chatting.view.MMChattingListView".equals(name)
                    || "com.tencent.mm.pluginsdk.ui.tools.ScrollControlRecyclerView".equals(name)
                    || "com.tencent.mm.pluginsdk.ui.tools.ChattingRecyclerView".equals(name)) continue;
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                if (group.getChildCount() > 768 - admitted) return null;
                for (int i = 0; i < group.getChildCount(); i++) { queue.addLast(group.getChildAt(i)); admitted++; }
            }
        }
        // During a footer replacement the old tree can remain visible while the new
        // one is structurally complete but still alpha-zero. Prefer the only footer
        // that can actually host the input toolbar.
        if (readyCount == 1) return ready;
        // A cold or notification transition can temporarily animate the only footer's
        // alpha to zero. Preserve the structural identity instead of waiting for a
        // later layout that the host may never dispatch.
        if (structuralCount == 1) return structural;
        return visibleCount == 1 ? visible : null;
    }

    private static boolean visibleInTree(View view) {
        if (view == null || !view.isAttachedToWindow() || !view.isShown()) return false;
        for (View current = view; current != null;
                current = current.getParent() instanceof View ? (View) current.getParent() : null) {
            if (current.getAlpha() < 0.01f) return false;
        }
        return true;
    }

    private static void removeToolbar(ViewGroup footer) {
        Runnable load = LOAD_TASKS.remove(footer); if (load != null) footer.removeCallbacks(load);
        try {
            removeTaggedToolbars(footer, null);
            ViewGroup panel = findAppPanel(footer);
            if (panel != null) {
                synchronized (PANEL_ACTIONS) {
                    PANEL_ACTIONS.remove(panel);
                }
            }
        } catch (Throwable t) {
            log("移除工具栏失败 " + t);
        }
    }

    // ------------------------------------------------------------ 插工具栏

    private static boolean attach(ViewGroup footer) {
        try {
            if (!isEnabled() || !footer.isAttachedToWindow()) {
                return false;
            }
            ViewGroup host = findInputContainer(footer);
            if (host == null) {
                diagnoseMount(footer, null, null, "NO_INPUT_COLUMN");
                return false;
            }
            View previousToolbar = footer.findViewWithTag(TAG_TOOLBAR);
            if (previousToolbar != null) {
                if (previousToolbar.getParent() != host) {
                    if (previousToolbar.getParent() instanceof ViewGroup) {
                        ((ViewGroup) previousToolbar.getParent()).removeView(previousToolbar);
                    }
                    host.addView(previousToolbar, 0, new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                }
                removeTaggedToolbars(footer, previousToolbar);
                if (previousToolbar.getVisibility() != View.VISIBLE) previousToolbar.setVisibility(View.VISIBLE);
                if (previousToolbar.getAlpha() <= 0f) previousToolbar.setAlpha(1f);
                InputWatch watch = INPUT_WATCHES.get(footer);
                boolean mounted = visuallyMounted(host, previousToolbar);
                if (watch != null) watch.mounted = mounted;
                if (watch != null && mounted) watch.verifyAfterMount();
                diagnoseMount(footer, host, previousToolbar, mounted ? "MOUNTED" : "NOT_VISIBLE");
                return mounted;
            }
            Context ctx = footer.getContext();
            Activity activity = ChatToolbarLifecycle.findActivity(ctx);
            if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
                diagnoseMount(footer, host, null, "NO_ACTIVITY");
                return false;
            }
            float density = ctx.getResources().getDisplayMetrics().density;

            // 用的是「无背景的 LazyRow」：整条工具栏透明，
            // 只有每个 chip 自己是 Material3 AssistChip 的胶囊底。
            FrameLayout shell = new FrameLayout(ctx);
            shell.setTag(TAG_TOOLBAR);
            int side = dp(density, 8);
            shell.setPadding(side, dp(density, 6), side, dp(density, 6));
            // 底栏透明由 ChatFooterTransparent 的 tag 拦截机制负责，
            // 这里保持 shell 透明即可，让聊天壁纸自然透出。
            shell.setBackgroundColor(Color.TRANSPARENT);

            HorizontalScrollView scroll = new HorizontalScrollView(ctx);
            scroll.setHorizontalScrollBarEnabled(false);
            scroll.setClipToPadding(false);
            scroll.setBackgroundColor(Color.TRANSPARENT);
            shell.addView(scroll, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(density, 8), dp(density, 7), dp(density, 8), dp(density, 7));
            scroll.addView(row, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            for (String[] tool : orderedTools()) {
                final String name = tool[0];
                if (!isToolEnabled(name)) {
                    continue;
                }
                row.addView(buildChip(activity, density, name, tool[1], new Runnable() {
                    @Override
                    public void run() {
                        dispatch(name, footer);
                    }
                }, null));
            }
            installDragSort(row, scroll);

            Drawable hostBg = host.getBackground();
            log("插入前 host=" + host.getClass().getSimpleName()
                    + " 子数=" + host.getChildCount()
                    + " 背景=" + (hostBg == null ? "null" : hostBg.getClass().getSimpleName()));
            host.addView(shell, 0, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            InputWatch watch = INPUT_WATCHES.get(footer);
            boolean mounted = visuallyMounted(host, shell);
            if (watch != null) watch.mounted = mounted;
            if (watch != null) watch.verifyAfterMount();
            diagnoseMount(footer, host, shell, mounted ? "MOUNTED" : "NOT_VISIBLE");
            if (hostBg instanceof android.graphics.drawable.ColorDrawable) {
                keepViewTransparent(host);
            }

            // 输入框上方固定色带仅在未启用悬浮输入栏时清理。
            // 两项同时启用时，悬浮输入栏拥有 footer 圆角表面，工具栏不能把它重新置透明。
            if (!FloatingChatFooterFeature.enabled()) {
                try {
                    keepFooterMainAreaTransparent(footer);
                    Drawable fb = footer.getBackground();
                    log("footer 背景=" + (fb == null ? "null" : fb.getClass().getSimpleName()));
                    keepViewTransparent(footer);
                    android.view.ViewParent vp = host;
                    int depth = 0;
                    while (vp instanceof View && depth < 10) {
                        View av = (View) vp;
                        Drawable abg = av.getBackground();
                        if (abg instanceof android.graphics.drawable.ColorDrawable) {
                            int c = ((android.graphics.drawable.ColorDrawable) abg).getColor();
                            int r = (c >> 16) & 0xFF;
                            int g = (c >> 8) & 0xFF;
                            int b = c & 0xFF;
                            boolean grayish = Math.abs(r - g) < 12 && Math.abs(g - b) < 12
                                    && r > 0xE0 && ((c >>> 24) & 0xFF) >= 0xE0;
                            if (grayish) {
                                keepViewTransparent(av);
                            }
                        }
                        vp = av.getParent();
                        depth++;
                    }
                } catch (Throwable ignore) {
                }
            } else {
                log("悬浮输入栏已启用，保留 footer 圆角表面");
            }
            log("工具栏已插入，host=" + host.getClass().getName());
            shell.post(new Runnable() {
                @Override
                public void run() {
                    log("工具栏布局尺寸=" + shell.getWidth() + "x" + shell.getHeight()
                            + "，visible=" + shell.getVisibility()
                            + "，shown=" + shell.isShown());
                }
            });

            // 微信自己在 AppPanel.init() 里会调 loadData()（延迟 1s）去拉工具列表，
            // 最终走到 initAppGrid，那一刻我们的 hookAfter 会做快照。
            // 这里只是在微信漏掉该调度时补一次（例如同时创建多个 footer 时任务被取消）。
            final WeakReference<ViewGroup> footerRef = new WeakReference<>(footer);
            Runnable load = new Runnable() {
                @Override
                public void run() {
                    ViewGroup current = footerRef.get();
                    if (current == null || LOAD_TASKS.get(current) != this) return;
                    LOAD_TASKS.remove(current);
                    if (isEnabled() && current.isAttachedToWindow() && current.findViewWithTag(TAG_TOOLBAR) != null) triggerLoadData(current);
                }
            };
            Runnable previous = LOAD_TASKS.put(footer, load); if (previous != null) footer.removeCallbacks(previous);
            footer.postDelayed(load, 1200);
            return mounted;
        } catch (Throwable t) {
            log("插入异常 " + t);
            return false;
        }
    }

    private static boolean visuallyMounted(ViewGroup host, View shell) {
        if (host == null || shell == null || shell.getParent() != host) return false;
        if (!host.isAttachedToWindow() || !host.isShown() || !shell.isAttachedToWindow() || !shell.isShown()) return false;
        if (host.getWidth() <= 0 || host.getHeight() <= 0) return false;
        if (shell.getWidth() <= 0 || shell.getHeight() <= 0) return false;
        for (View current = shell; current != null;
                current = current.getParent() instanceof View ? (View) current.getParent() : null) {
            if (current.getAlpha() < 0.99f) return false;
        }
        android.graphics.Rect shellBounds = new android.graphics.Rect();
        android.graphics.Rect hostBounds = new android.graphics.Rect();
        if (!shell.getGlobalVisibleRect(shellBounds) || !host.getGlobalVisibleRect(hostBounds)) return false;
        return shellBounds.left < hostBounds.right && shellBounds.right > hostBounds.left
                && shellBounds.top < hostBounds.bottom && shellBounds.bottom > hostBounds.top;
    }

    private static void removeTaggedToolbars(View root, View keep) {
        if (root == null) return;
        List<View> shells = new ArrayList<>();
        collectTagged(root, shells, 0);
        for (View shell : shells) {
            if (shell == keep || !(shell.getParent() instanceof ViewGroup)) continue;
            ((ViewGroup) shell.getParent()).removeView(shell);
        }
    }

    private static void collectTagged(View view, List<View> found, int visited) {
        if (view == null || visited >= 512) return;
        if (TAG_TOOLBAR.equals(view.getTag())) found.add(view);
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        int limit = Math.min(group.getChildCount(), 512 - visited);
        for (int i = 0; i < limit; i++) collectTagged(group.getChildAt(i), found, visited + 1 + i);
    }

    private static ViewGroup findInputContainer(ViewGroup root) {
        return ChatToolbarViews.findInputContainer(root);
    }

    private static final WeakHashMap<ViewGroup, String> MOUNT_STAGES = new WeakHashMap<>();
    private static int mountLogs;

    private static void diagnoseMount(ViewGroup footer, ViewGroup host, View shell, String reason) {
        if (mountLogs >= 32) return;
        String stage = reason + " host=" + (host == null ? "none" : host.getClass().getName());
        if (stage.equals(MOUNT_STAGES.get(footer))) return;
        MOUNT_STAGES.put(footer, stage); mountLogs++;
        log("stage=mount reason=" + stage + " footer=" + footer.getWidth() + "x" + footer.getHeight()
                + " shell=" + (shell == null ? "none" : shell.getWidth() + "x" + shell.getHeight()));
        View current = shell == null ? footer : shell;
        for (int depth = 0; current != null && depth < 5; depth++) {
            int[] origin = new int[2]; current.getLocationOnScreen(origin);
            log("stage=mount-node depth=" + depth + " class=" + current.getClass().getName()
                    + " xy=" + origin[0] + "," + origin[1] + " size=" + current.getWidth() + "x" + current.getHeight()
                    + " visibility=" + current.getVisibility() + " alpha=" + current.getAlpha());
            current = current.getParent() instanceof View ? (View) current.getParent() : null;
        }
        if (host == null) {
            List<View> nodes = new ArrayList<>(); List<Integer> depths = new ArrayList<>();
            nodes.add(footer); depths.add(0);
            int admitted = 1;
            for (int i = 0; i < nodes.size() && i < 24; i++) {
                View node = nodes.get(i); int depth = depths.get(i);
                int[] origin = new int[2]; node.getLocationOnScreen(origin);
                log("stage=mount-child depth=" + depth + " class=" + node.getClass().getName()
                        + " id=" + node.getId() + " xy=" + origin[0] + "," + origin[1]
                        + " size=" + node.getWidth() + "x" + node.getHeight()
                        + " visibility=" + node.getVisibility()
                        + " orientation=" + (node instanceof LinearLayout ? ((LinearLayout) node).getOrientation() : -1));
                String name = node.getClass().getName();
                if (depth >= 4 || !(node instanceof ViewGroup) || node instanceof android.widget.AbsListView
                        || CLS_APP_PANEL.equals(name) || "com.tencent.mm.view.SmileyPanel".equals(name)) continue;
                ViewGroup group = (ViewGroup) node;
                for (int j = 0; j < group.getChildCount() && admitted < 24; j++) {
                    nodes.add(group.getChildAt(j)); depths.add(depth + 1); admitted++;
                }
            }
        }
    }

    private static boolean containsEditText(ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof EditText) {
                return true;
            }
            if (child instanceof ViewGroup && containsEditText((ViewGroup) child)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Material3 AssistChip 的等价实现：
     *  - 高 32dp、圆角 8dp、白底、1dp 浅灰描边（M3 AssistChip 默认外观）
     *  - 左图标 18dp + 文字 14sp（M3 AssistChipDefaults.IconSize / LabelLarge）
     *  - chip 之间 8dp 间距
     */
    private static View buildChip(Activity activity, float density, String text, String iconName,
                                  final Runnable onClick, final Runnable onLongClick) {
        LinearLayout chip = new LinearLayout(activity);
        chip.setOrientation(LinearLayout.HORIZONTAL);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        int padH = dp(density, 12);
        chip.setPadding(padH, 0, padH, 0);
        chip.setMinimumHeight(dp(density, 32));

        GradientDrawable bg = new GradientDrawable();
        boolean dark = isDarkMode(activity);
        // M3 AssistChip 官方默认：containerColor 透明、描边 outlineVariant、
        // 图标 onSurfaceVariant(18dp)、文字 onSurface（浅色 surface=#F6FBF2 上透明胶囊 + #C1C9BE 描边）。
        bg.setColor(0x00000000);   // 用户要求：胶囊底色透明，只保留描边
        bg.setCornerRadius(dp(density, 8));
        bg.setStroke(dp(density, 1), dark ? 0x66FFFFFF : 0xFFC1C9BE);
        chip.setBackground(bg);
        chip.setClickable(true);
        chip.setTag(text);

        Drawable icon = loadAssetIcon(activity, iconName, dark ? 0xFFE6E6E6 : 0xFF1A1A1A);
        if (icon != null) {
            ImageView iv = new ImageView(activity);
            iv.setImageDrawable(icon);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                    dp(density, 18), dp(density, 18));
            ilp.rightMargin = dp(density, 8);
            chip.addView(iv, ilp);
        }

        TextView label = new TextView(activity);
        label.setText(text);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.0f);
        label.setTextColor(dark ? 0xFFDFE4DC : 0xFF181D18);
        label.setSingleLine(true);
        chip.addView(label);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(density, 32));
        lp.rightMargin = dp(density, 8);
        chip.setLayoutParams(lp);

        chip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onClick.run();
            }
        });
        if (onLongClick != null) {
            chip.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    onLongClick.run();
                    return true;
                }
            });
        }
        return chip;
    }


    public static String[] toolNames() {
        java.util.List<String[]> ordered = orderedTools();
        String[] names = new String[ordered.size()];
        for (int i = 0; i < ordered.size(); i++) {
            names[i] = ordered.get(i)[0];
        }
        return names;
    }

    public static boolean isToolEnabled(String name) {
        if (name == null) {
            return false;
        }
        try {
            String raw = WexStore.INSTANCE.getString(KEY_ENABLED_ITEMS, "");
            if (raw == null || raw.length() == 0) {
                return true;
            }
            for (String value : raw.split(",")) {
                if (name.equals(value.trim())) {
                    return true;
                }
            }
            return false;
        } catch (Throwable ignored) {
            return true;
        }
    }

    public static void setToolEnabled(String name, boolean enabled) {
        java.util.LinkedHashSet<String> values = new java.util.LinkedHashSet<String>();
        for (String[] tool : orderedTools()) {
            if (isToolEnabled(tool[0])) {
                values.add(tool[0]);
            }
        }
        if (enabled) {
            values.add(name);
        } else {
            values.remove(name);
        }
        StringBuilder raw = new StringBuilder();
        for (String value : values) {
            if (raw.length() > 0) {
                raw.append(',');
            }
            raw.append(value);
        }
        WexStore.INSTANCE.putString(KEY_ENABLED_ITEMS, raw.length() == 0 ? "__none__" : raw.toString());
        refresh();
    }

    public static void moveTool(String name, int direction) {
        java.util.List<String[]> tools = orderedTools();
        int index = -1;
        for (int i = 0; i < tools.size(); i++) {
            if (tools.get(i)[0].equals(name)) {
                index = i;
                break;
            }
        }
        int target = index + direction;
        if (index < 0 || target < 0 || target >= tools.size()) {
            return;
        }
        String[] value = tools.remove(index);
        tools.add(target, value);
        saveOrder(tools);
        refresh();
    }

    /** 按用户拖拽保存的顺序返回工具表。 */
    private static java.util.List<String[]> orderedTools() {
        java.util.List<String[]> base = new java.util.ArrayList<String[]>();
        java.util.Collections.addAll(base, TOOLS);
        try {
            String saved = wex.ui.core.WexStore.INSTANCE.getString(KEY_ORDER, "");
            if (saved == null || saved.length() == 0) {
                return base;
            }
            String[] names = saved.split(",");
            java.util.List<String[]> out = new java.util.ArrayList<String[]>();
            for (String n : names) {
                for (String[] t : TOOLS) {
                    if (t[0].equals(n)) {
                        out.add(t);
                    }
                }
            }
            // 追加新增（旧配置里没有的）工具
            for (String[] t : base) {
                boolean exists = false;
                for (String[] o : out) {
                    if (o[0].equals(t[0])) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    out.add(t);
                }
            }
            return out.isEmpty() ? base : out;
        } catch (Throwable t) {
            return base;
        }
    }

    private static void saveOrder(java.util.List<String[]> tools) {
        try {
            StringBuilder raw = new StringBuilder();
            for (String[] tool : tools) {
                if (raw.length() > 0) {
                    raw.append(',');
                }
                raw.append(tool[0]);
            }
            WexStore.INSTANCE.putString(KEY_ORDER, raw.toString());
        } catch (Throwable ignored) {
        }
    }

    /** 保存当前工具顺序。 */
    private static void saveOrder(LinearLayout row) {
        try {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < row.getChildCount(); i++) {
                Object tag = row.getChildAt(i).getTag();
                if (tag instanceof String) {
                    if (sb.length() > 0) {
                        sb.append(',');
                    }
                    sb.append((String) tag);
                }
            }
            wex.ui.core.WexStore.INSTANCE.putString(KEY_ORDER, sb.toString());
            log("工具栏顺序已保存: " + sb);
        } catch (Throwable ignore) {
        }
    }

    /**
     * 工具栏容器尺寸固定后，重新计算各 chip 中心点，供长按拖动换位使用。
     */
    private static void installDragSort(final LinearLayout row, final HorizontalScrollView scroll) {
        try {
            for (int i = 0; i < row.getChildCount(); i++) {
                final View chip = row.getChildAt(i);
                chip.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        startDrag(v, row, scroll);
                        return true;
                    }
                });
            }
        } catch (Throwable ignore) {
        }
    }

    /** 长按后进入拖动：移动手指时与相邻 chip 交换位置。 */
    private static void startDrag(final View chip, final LinearLayout row,
                                  final HorizontalScrollView scroll) {
        try {
            chip.setAlpha(0.5f);
            chip.setElevation(dp(chip.getResources().getDisplayMetrics().density, 8));
            final float[] startX = new float[1];
            chip.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, android.view.MotionEvent e) {
                    switch (e.getActionMasked()) {
                        case android.view.MotionEvent.ACTION_DOWN:
                            startX[0] = e.getRawX();
                            return true;
                        case android.view.MotionEvent.ACTION_MOVE: {
                            float dx = e.getRawX() - startX[0];
                            int my = row.indexOfChild(v);
                            if (dx > dp(v.getResources().getDisplayMetrics().density, 30)) {
                                if (my < row.getChildCount() - 1) {
                                    row.removeView(v);
                                    row.addView(v, my + 1);
                                    startX[0] = e.getRawX();
                                }
                            } else if (dx < -dp(v.getResources().getDisplayMetrics().density, 30)) {
                                if (my > 0) {
                                    row.removeView(v);
                                    row.addView(v, my - 1);
                                    startX[0] = e.getRawX();
                                }
                            }
                            return true;
                        }
                        case android.view.MotionEvent.ACTION_UP:
                        case android.view.MotionEvent.ACTION_CANCEL:
                            v.setAlpha(1f);
                            v.setElevation(0f);
                            v.setOnTouchListener(null);
                            v.setOnLongClickListener(null);
                            installDragSort(row, scroll);
                            saveOrder(row);
                            return true;
                    }
                    return false;
                }
            });
        } catch (Throwable ignore) {
        }
    }

    /**
     * 从根布局向下：把「纯色背景」的容器登记为持续透明。
     * 但跳过：EditText 及其祖先（输入框白底要留）、以及含 EditText 的容器。
     */
    private static void markTreeTransparentExceptEditable(ViewGroup g, int depth) {
        if (depth > 3) {
            return;
        }
        for (int i = 0; i < g.getChildCount(); i++) {
            View c = g.getChildAt(i);
            if (c == null || c instanceof EditText) {
                continue;
            }
            Drawable bg = c.getBackground();
            // 只处理「不承载面板/输入」的纯装饰层；AppPanel（表情/加号面板）必须保留底图。
            if ((bg instanceof android.graphics.drawable.NinePatchDrawable
                    || bg instanceof android.graphics.drawable.LayerDrawable)
                    && !(c instanceof android.view.ViewGroup && hasAppPanel((ViewGroup) c))
                    && !containsEditText(c)) {
                log("底图透明化: " + c.getClass().getSimpleName()
                        + " " + bg.getClass().getSimpleName());
                keepViewTransparent(c);
            }
            if (bg instanceof android.graphics.drawable.ColorDrawable) {
                int col = ((android.graphics.drawable.ColorDrawable) bg).getColor();
                // 只处理浅灰/白这类「页面底色」
                int r = (col >> 16) & 0xFF, gg = (col >> 8) & 0xFF, b = col & 0xFF;
                boolean grayish = Math.abs(r - gg) < 12 && Math.abs(gg - b) < 12
                        && r > 0xE0 && ((col >>> 24) & 0xFF) >= 0xE0;
                if (grayish) {
                    log("根层透明化: " + c.getClass().getSimpleName()
                            + " color=" + Integer.toHexString(col));
                    keepViewTransparent(c);
                }
            }
            if (c instanceof ViewGroup) {
                markTreeTransparentExceptEditable((ViewGroup) c, depth + 1);
            }
        }
    }

    /**
     * 取「屏幕壁纸在当前 View 区域的那一块」作为 Drawable。
     * 与聊天页壁纸用同一张图、同样的 CENTER_CROP 变换 + 屏幕偏移，
     * 视觉上完全吻合，从而实现「看起来透明」。
     */
    private static android.graphics.drawable.Drawable wallpaperSlice(final Context ctx) {
        try {
            String path = wex.ui.core.WexStore.INSTANCE.getString("tab_bg_global", "");
            if (path == null || path.length() == 0) {
                return null;
            }
            final android.graphics.Bitmap bmp;
            synchronized (WALLPAPER_LOCK) {
                if (wallpaperCache == null || !path.equals(wallpaperPath)
                        || wallpaperCache.isRecycled()) {
                    wallpaperCache = android.graphics.BitmapFactory.decodeFile(path);
                    wallpaperPath = path;
                }
                bmp = wallpaperCache;
            }
            if (bmp == null) {
                return null;
            }
            return new android.graphics.drawable.Drawable() {
                private final android.graphics.Paint paint =
                        new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG);
                private final int[] loc = new int[2];

                @Override
                public void draw(android.graphics.Canvas canvas) {
                    android.graphics.Rect b = getBounds();
                    if (b.isEmpty()) {
                        return;
                    }
                    // 屏幕尺寸（壁纸按整屏 CENTER_CROP 绘制，这里截取本区域那一块）
                    android.util.DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
                    int sw = dm.widthPixels;
                    int sh = dm.heightPixels;
                    float scale = Math.max((float) sw / bmp.getWidth(),
                            (float) sh / bmp.getHeight());
                    float dw = bmp.getWidth() * scale;
                    float dh = bmp.getHeight() * scale;
                    float left = (sw - dw) / 2f;
                    float top = (sh - dh) / 2f;
                    // 本 View 在屏幕上的位置
                    android.view.View v = null;
                    try {
                        v = (android.view.View) getCallback();
                    } catch (Throwable ignore) {
                    }
                    int vx = 0, vy = 0;
                    if (v != null) {
                        v.getLocationOnScreen(loc);
                        vx = loc[0];
                        vy = loc[1];
                    }
                    canvas.save();
                    canvas.translate(b.left, b.top);
                    canvas.clipRect(0, 0, b.width(), b.height());
                    canvas.translate(-vx, -vy);
                    canvas.drawBitmap(bmp, null,
                            new android.graphics.RectF(left, top, left + dw, top + dh), paint);
                    canvas.restore();
                }

                @Override
                public void setAlpha(int alpha) {
                    paint.setAlpha(alpha);
                }

                @Override
                public void setColorFilter(android.graphics.ColorFilter cf) {
                    paint.setColorFilter(cf);
                }

                @Override
                public int getOpacity() {
                    return android.graphics.PixelFormat.OPAQUE;
                }
            };
        } catch (Throwable t) {
            return null;
        }
    }

    private static final Object WALLPAPER_LOCK = new Object();
    private static android.graphics.Bitmap wallpaperCache;
    private static String wallpaperPath;

    /** 该容器子树里是否含 AppPanel（表情/加号面板，底图不能清）。 */
    private static boolean hasAppPanel(ViewGroup g) {
        for (int i = 0; i < g.getChildCount(); i++) {
            View c = g.getChildAt(i);
            if (c == null) {
                continue;
            }
            if (c.getClass().getSimpleName().contains("AppPanel")) {
                return true;
            }
            if (c instanceof ViewGroup && hasAppPanel((ViewGroup) c)) {
                return true;
            }
        }
        return false;
    }

    /** 把 ChatFooter 的 idMainArea / idVoiceBar 等「整块底栏背景」持续保持透明。 */
    private static void keepFooterMainAreaTransparent(View footer) {
        try {
            Class<?> ft = Class.forName(
                    "wex.ui.core.feature.ChatFooterTransparent", false,
                    ChatToolbarFeature.class.getClassLoader());
            String[] fields = {"idMainArea", "idVoiceBar", "idSpeechArea"};
            Res res = new Res(footer);
            for (String fn : fields) {
                int id = res.get(fn);
                if (id == 0) {
                    continue;
                }
                View v = footer.findViewById(id);
                if (v == null) {
                    continue;
                }
                log("持续透明: " + fn + " " + v.getClass().getSimpleName());
                keepViewTransparent(v);
            }
        } catch (Throwable t) {
            log("keepFooterMainArea 失败 " + t.getMessage());
        }
    }

    /** 读取 ChatFooterTransparent 里的静态 id 缓存。 */
    private static final class Res {
        private final View root;

        Res(View root) {
            this.root = root;
        }

        int get(String field) {
            try {
                Class<?> ft = Class.forName(
                        "wex.ui.core.feature.ChatFooterTransparent", false,
                        ChatToolbarFeature.class.getClassLoader());
                java.lang.reflect.Field f = ft.getDeclaredField(field);
                f.setAccessible(true);
                // 静态字段直接取
                Object v = f.get(null);
                if (v instanceof Integer) {
                    return (Integer) v;
                }
            } catch (Throwable ignore) {
            }
            return 0;
        }
    }

    /**
     * hook View 的所有背景设置方法：只要是「已登记必须透明」的 View，
     * 微信无论怎么设背景都直接丢弃。这样键盘弹出/footer 重建都不会变灰。
     */
    private static void installBgGuard() {
        if (bgGuardInstalled) {
            return;
        }
        try {
            XC_MethodHook guard = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (SELF_BG.get() != null || !(param.thisObject instanceof View)) {
                        return;
                    }
                    View view = (View) param.thisObject;
                    if (KEEP_TRANSPARENT.containsKey(view)) {
                        clearRegisteredBackground(view);
                    }
                }
            };
            for (String name : new String[]{"setBackground", "setBackgroundColor",
                    "setBackgroundDrawable", "setBackgroundResource"}) {
                XposedBridge.hookAllMethods(View.class, name, guard);
            }
            bgGuardInstalled = true;
            log("工具栏背景守卫已安装");
        } catch (Throwable t) {
            log("背景守卫失败 " + t);
        }
    }

    private static void clearRegisteredBackground(View view) {
        if (!KEEP_TRANSPARENT.containsKey(view) || !isEnabled()) {
            return;
        }
        Drawable background = view.getBackground();
        if (background != null) {
            ORIGINAL_BACKGROUNDS.put(view, background);
            try {
                SELF_BG.set(Boolean.TRUE);
                view.setBackground(null);
            } finally {
                SELF_BG.remove();
            }
        }
    }

    /** 只登记本功能改过的背景，关闭时移除监听并恢复。 */
    private static void keepViewTransparent(final View view) {
        try {
            if (KEEP_TRANSPARENT.containsKey(view)) {
                return;
            }
            KEEP_TRANSPARENT.put(view, Boolean.TRUE);
            ORIGINAL_BACKGROUNDS.put(view, view.getBackground());
            installBgGuard();
            clearRegisteredBackground(view);
            final WeakReference<View> ref = new WeakReference<View>(view);
            View.OnLayoutChangeListener listener = new View.OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View v, int l, int t, int r, int b,
                                           int ol, int ot, int or, int ob) {
                    View target = ref.get();
                    if (target != null) {
                        clearRegisteredBackground(target);
                    }
                }
            };
            BACKGROUND_LISTENERS.put(view, listener);
            view.addOnLayoutChangeListener(listener);
        } catch (Throwable t) {
            log("保持背景透明失败 " + t);
        }
    }

    private static void restoreBackgrounds(View root) {
        for (View view : new ArrayList<View>(KEEP_TRANSPARENT.keySet())) {
            if (view == null || (root != null && view.getRootView() != root)) {
                continue;
            }
            KEEP_TRANSPARENT.remove(view);
            View.OnLayoutChangeListener listener = BACKGROUND_LISTENERS.remove(view);
            if (listener != null) {
                view.removeOnLayoutChangeListener(listener);
            }
            Drawable original = ORIGINAL_BACKGROUNDS.remove(view);
            try {
                SELF_BG.set(Boolean.TRUE);
                view.setBackground(original);
            } catch (Throwable t) {
                log("恢复背景失败 " + t);
            } finally {
                SELF_BG.remove();
            }
        }
    }

    /** host 持续保持透明（微信会在 layout 后重设，故挂监听反复清）。 */
    private static void keepHostTransparent(final View host) {
        try {
            if (host.getTag(TAG_TRANSPARENT_WATCH) != null) {
                return;
            }
            host.setTag(TAG_TRANSPARENT_WATCH, Boolean.TRUE);
            host.setBackground(null);
            host.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View v, int l, int t, int r, int b,
                                           int ol, int ot, int or, int ob) {
                    Drawable bg = v.getBackground();
                    if (bg instanceof android.graphics.drawable.ColorDrawable) {
                        v.setBackground(null);
                    }
                }
            });
            // 微信重建 footer 时背景会被重新 setBackground，用 post 再补一次
            host.post(new Runnable() {
                @Override
                public void run() {
                    Drawable bg = host.getBackground();
                    if (bg instanceof android.graphics.drawable.ColorDrawable) {
                        host.setBackground(null);
                    }
                }
            });
            log("host 已设为持续透明");
        } catch (Throwable ignore) {
        }
    }

    /** 把原 host 底色补到「承载输入框的那个子容器」上，保证对话栏外观不变。 */
    private static void applyColorToInputRow(ViewGroup host, int color) {
        try {
            for (int i = 0; i < host.getChildCount(); i++) {
                View child = host.getChildAt(i);
                if (child == null || TAG_TOOLBAR.equals(child.getTag())) {
                    continue;
                }
                if (containsEditText(child)) {
                    child.setBackgroundColor(color);
                    log("输入栏底色已恢复: " + Integer.toHexString(color));
                    break;
                }
                if (child instanceof ViewGroup && containsEditText((ViewGroup) child)) {
                    child.setBackgroundColor(color);
                    log("输入栏底色已恢复(嵌套): " + Integer.toHexString(color));
                    break;
                }
            }
        } catch (Throwable ignore) {
        }
    }

    private static boolean containsEditText(View v) {
        return v instanceof ViewGroup && containsEditText((ViewGroup) v);
    }

    /**
     * 让某个容器「持续」保持透明背景：立刻清一次 + 每次布局后再清一次。
     * 微信会在 layout 结束后重设 ColorDrawable，只清一次会被覆盖。
     */
    private static void enforceTransparent(final View v) {
        try {
            clearOneSolidBg(v);
            if (v.getTag(TAG_TRANSPARENT_WATCH) != null) {
                return;
            }
            v.setTag(TAG_TRANSPARENT_WATCH, Boolean.TRUE);
            v.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View vv, int l, int t, int r, int b,
                                           int ol, int ot, int or, int ob) {
                    clearOneSolidBg(vv);
                }
            });
        } catch (Throwable ignore) {
        }
    }

    /** 清掉单个 View 的不透明纯色背景。 */
    private static void clearOneSolidBg(View v) {
        try {
            Drawable b = v.getBackground();
            if (b instanceof android.graphics.drawable.ColorDrawable) {
                int c = ((android.graphics.drawable.ColorDrawable) b).getColor();
                if (((c >>> 24) & 0xFF) >= 0xF0) {
                    v.setBackground(null);
                }
            }
        } catch (Throwable ignore) {
        }
    }

    /** 打印 View 树（类名 / 背景 / 尺寸 / 可见性），用于定位灰色底。 */
    private static void dumpTree(View v, int depth) {
        try {
            if (depth > 5) {
                return;
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < depth; i++) {
                sb.append("  ");
            }
            Drawable bg = v.getBackground();
            String bgDesc = "null";
            if (bg instanceof android.graphics.drawable.ColorDrawable) {
                bgDesc = "Color(" + Integer.toHexString(
                        ((android.graphics.drawable.ColorDrawable) bg).getColor()) + ")";
            } else if (bg != null) {
                bgDesc = bg.getClass().getSimpleName();
            }
            log("TREE " + sb + v.getClass().getSimpleName()
                    + " bg=" + bgDesc
                    + " " + v.getWidth() + "x" + v.getHeight()
                    + " vis=" + v.getVisibility());
            if (v instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) v;
                for (int i = 0; i < g.getChildCount(); i++) {
                    dumpTree(g.getChildAt(i), depth + 1);
                }
            }
        } catch (Throwable ignore) {
        }
    }

    /** 递归清掉 View 树里的不透明纯色背景（跳过 EditText，保留输入框白底）。 */
    private static void clearSolidBackgrounds(View v, int depth) {
        if (v == null || depth > 8) {
            return;
        }
        try {
            if (!(v instanceof EditText)) {
                Drawable b = v.getBackground();
                if (b instanceof android.graphics.drawable.ColorDrawable) {
                    int c = ((android.graphics.drawable.ColorDrawable) b).getColor();
                    // 只清不透明纯色（alpha=FF）；半透明/透明不动
                    if (((c >>> 24) & 0xFF) >= 0xF0) {
                        log("清背景: " + v.getClass().getSimpleName()
                                + " color=" + Integer.toHexString(c));
                        v.setBackground(null);
                    }
                }
            }
            if (v instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) v;
                for (int i = 0; i < g.getChildCount(); i++) {
                    clearSolidBackgrounds(g.getChildAt(i), depth + 1);
                }
            }
        } catch (Throwable ignore) {
        }
    }

    private static boolean isDarkMode(Context ctx) {
        try {
            int mode = ctx.getResources().getConfiguration().uiMode & 0x30;
            return mode == 0x20;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 读取图标（默认主题色）。
     */
    private static Drawable loadAssetIcon(Context ctx, String name) {
        return loadAssetIcon(ctx, name, 0);
    }

    /**
     * 读取图标。
     *
     * 已换成 Lucide 矢量渲染：24 栅格、2px 描边、圆头圆角，与原手绘 PNG 同规格。
     * 矢量没有透明边，不再需要裁边与位图缩放；染色沿用调用方传入的 tint。
     */
    private static Drawable loadAssetIcon(Context ctx, String name, int tint) {
        try {
            if (!LucideIcons.has(name)) {
                return null;
            }
            int color = tint != 0 ? tint : LucideIcons.themeColor();
            return LucideIcons.drawableDp(name, ICON_DP, color, ICON_PADDING);
        } catch (Throwable t) {
            return null;
        }
    }

    private static InputStream openModuleAsset(String name) {
        try {
            if (moduleAssets == null) {
                Field f = XposedHelpers.findField(
                        XposedHelpers.findClass("wex.ui.core.xposed.HookEntry",
                                ChatToolbarFeature.class.getClassLoader()), "e");
                String apk = (String) f.get(null);
                if (apk == null) {
                    return null;
                }
                android.content.res.AssetManager am =
                        (android.content.res.AssetManager) android.content.res.AssetManager.class
                                .newInstance();
                am.getClass().getMethod("addAssetPath", String.class).invoke(am, apk);
                moduleAssets = am;
            }
            return moduleAssets.open(name);
        } catch (Throwable t) {
            log("读图标失败 " + name + " " + t.getMessage());
            return null;
        }
    }

    /** Invokes the uniquely resolved AppPanel initializer when the host delayed load was cancelled. */
    private static void triggerLoadData(View footer) {
        try {
            ViewGroup panel = findAppPanel(footer);
            if (panel == null) return;
            PanelActions actions = panelActions(panel, false);
            if (actions != null && (!actions.clicks.isEmpty() || actions.systemCamera != null)) return;
            Method init = initAppGridMethod;
            if (init == null) { log("initAppGrid 尚未解析"); return; }
            Object receiver = java.lang.reflect.Modifier.isStatic(init.getModifiers()) ? null : panel;
            Object[] args = init.getParameterTypes().length == 0 ? new Object[0] : new Object[]{panel};
            init.invoke(receiver, args);
            log("已直接调用 initAppGrid 触发面板初始化");
        } catch (Throwable t) { log("initAppGrid 调用失败 " + t.getClass().getSimpleName()); }
    }

    private static ViewGroup findAppPanel(View footer) {
        if (!(footer instanceof ViewGroup)) {
            return null;
        }
        ViewGroup g = (ViewGroup) footer;
        for (int i = 0; i < g.getChildCount(); i++) {
            View child = g.getChildAt(i);
            if (child != null && CLS_APP_PANEL.equals(child.getClass().getName())) {
                return (ViewGroup) child;
            }
            if (child instanceof ViewGroup) {
                ViewGroup nested = findAppPanel(child);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------ 喂尺寸

    /**
     * 尺寸算法：
     *   width  = 屏幕宽
     *   height = 容器(0,0) 的高度 - 22dp（MMDotView 条）
     *   取不到容器高度时，竖屏 215dp / 横屏 158dp
     * 然后构造 measurer（com.tencent.mm.pluginsdk.ui.chat.g）并调用 a(width,height)。
     */
    private static void feedPanelSize(ViewGroup panel) {
        try {
            float density = panel.getResources().getDisplayMetrics().density;
            int width = panel.getResources().getDisplayMetrics().widthPixels;

            int fallbackDp = panel.getResources().getDisplayMetrics().widthPixels
                    < panel.getResources().getDisplayMetrics().heightPixels ? 215 : 158;

            int containerHeight = 0;
            try {
                if (panel.getChildCount() > 0 && panel.getChildAt(0) instanceof ViewGroup) {
                    ViewGroup c0 = (ViewGroup) panel.getChildAt(0);
                    if (c0.getChildCount() > 0) {
                        View c00 = c0.getChildAt(0);
                        if (c00 != null && c00.getLayoutParams() != null) {
                            containerHeight = c00.getLayoutParams().height;
                        }
                    }
                }
            } catch (Throwable ignore) {
            }
            if (containerHeight <= 0) {
                containerHeight = (int) (fallbackDp * density);
            }
            int dotStrip = (int) (22 * density);
            int height = Math.max(1, containerHeight - dotStrip);

            // 顺手把 AppPanel 的 t/u 也写好（initAppGrid 内部守卫会用到）
            try {
                Field ft = findField(panel.getClass(), "t");
                if (ft != null) {
                    ft.setAccessible(true);
                    ft.setInt(panel, width);
                }
                Field fu = findField(panel.getClass(), "u");
                if (fu != null) {
                    fu.setAccessible(true);
                    fu.setInt(panel, height);
                }
            } catch (Throwable ignore) {
            }

            Class<?> measurer = XposedHelpers.findClass(
                    "com.tencent.mm.pluginsdk.ui.chat.g", panel.getClass().getClassLoader());
            Constructor<?> ctor = measurer.getDeclaredConstructor(panel.getClass());
            ctor.setAccessible(true);
            Object m = ctor.newInstance(panel);
            Method measure = measurer.getMethod("a", int.class, int.class);
            measure.setAccessible(true);
            measure.invoke(m, width, height);
            log("feedPanelSize width=" + width + " height=" + height);
        } catch (Throwable t) {
            log("feedPanelSize 失败 " + t.getMessage());
        }
    }

    // ------------------------------------------------------------ 快照

    private static PanelActions panelActions(ViewGroup panel, boolean create) {
        synchronized (PANEL_ACTIONS) {
            PanelActions actions = PANEL_ACTIONS.get(panel);
            if (actions == null && create) {
                actions = new PanelActions();
                PANEL_ACTIONS.put(panel, actions);
            }
            return actions;
        }
    }

    private static void snapshot(ViewGroup panel) {
        PanelActions actions = panelActions(panel, true);
        if (actions == null) {
            return;
        }
        actions.clicks.clear();
        actions.systemCamera = null;
        snapshot(panel, actions);
    }

    private static void snapshot(ViewGroup panel, PanelActions actions) {
        try {
            for (int i = 0; i < panel.getChildCount(); i++) {
                View child = panel.getChildAt(i);
                if (child instanceof android.widget.AbsListView) {
                    snapshotGrid((android.widget.AbsListView) child, actions);
                } else if (child instanceof ViewGroup) {
                    snapshot((ViewGroup) child, actions);
                }
            }
        } catch (Throwable t) {
            log("snapshot 失败 " + t.getMessage());
        }
    }

    private static void snapshotGrid(final android.widget.AbsListView grid,
                                     final PanelActions actions) {
        try {
            final android.widget.ListAdapter adapter = grid.getAdapter();
            if (adapter == null) {
                return;
            }
            final android.widget.AdapterView.OnItemClickListener listener = readListener(grid);
            final android.widget.AdapterView.OnItemLongClickListener longListener =
                    grid.getOnItemLongClickListener();
            if (listener == null) {
                return;
            }
            int count = adapter.getCount();
            for (int position = 0; position < count; position++) {
                final int pos = position;
                try {
                    final View item = adapter.getView(pos, null, grid);
                    if (item == null) {
                        continue;
                    }
                    final String label = findLabel(item);
                    if (label == null) {
                        continue;
                    }
                    actions.clicks.put(label, new Runnable() {
                        @Override
                        public void run() {
                            View real = null;
                            for (int i = 0; i < grid.getChildCount(); i++) {
                                View c = grid.getChildAt(i);
                                if (c != null && label.equals(findLabel(c))) {
                                    real = c;
                                    break;
                                }
                            }
                            listener.onItemClick(grid, real != null ? real : item, pos, 0);
                        }
                    });
                    if (pos == 0 && longListener != null && actions.systemCamera == null) {
                        actions.systemCamera = new Runnable() {
                            @Override
                            public void run() {
                                longListener.onItemLongClick(grid, item, 0, 0);
                            }
                        };
                    }
                } catch (Throwable ignore) {
                }
            }
        } catch (Throwable t) {
            log("snapshotGrid 失败 " + t.getMessage());
        }
    }

    private static android.widget.AdapterView.OnItemClickListener readListener(
            android.widget.AbsListView grid) {
        try {
            Field f = android.widget.AdapterView.class.getDeclaredField("mOnItemClickListener");
            f.setAccessible(true);
            return (android.widget.AdapterView.OnItemClickListener) f.get(grid);
        } catch (Throwable t) {
            return null;
        }
    }

    // ------------------------------------------------------------ 点击

    private static void dispatch(String name, View footer) {
        try {
            if (!isEnabled() || !footer.isAttachedToWindow()) {
                return;
            }
            ViewGroup panel = findAppPanel(footer);
            PanelActions actions = panel == null ? null : panelActions(panel, false);
            Runnable action = actions == null ? null
                    : ("系统拍摄".equals(name) ? actions.systemCamera : actions.clicks.get(name));
            if (action != null) {
                action.run();
                return;
            }
            // 缓存还没就绪：补一次 loadData（不展开面板），稍后重试
            triggerLoadData(footer);
            final String tool = name;
            final View f = footer;
            footer.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!isEnabled() || !f.isAttachedToWindow()) {
                        return;
                    }
                    ViewGroup currentPanel = findAppPanel(f);
                    PanelActions current = currentPanel == null ? null
                            : panelActions(currentPanel, false);
                    Runnable retry = current == null ? null
                            : ("系统拍摄".equals(tool)
                            ? current.systemCamera : current.clicks.get(tool));
                    if (retry != null) {
                        retry.run();
                    } else {
                        Activity activity = ChatToolbarLifecycle.findActivity(f.getContext());
                        if (activity != null) {
                            toast(activity, "工具准备中，请稍后重试");
                        }
                        log("dispatch: 缓存仍为空 " + tool);
                    }
                }
            }, 800);
        } catch (Throwable t) {
            log("派发失败 " + name + " " + t.getMessage());
        }
    }

    private static View findPlusByDesc(View root) {
        if (!(root instanceof ViewGroup)) {
            return null;
        }
        ViewGroup g = (ViewGroup) root;
        for (int i = 0; i < g.getChildCount(); i++) {
            View child = g.getChildAt(i);
            CharSequence desc = child.getContentDescription();
            if (desc != null) {
                String s = desc.toString();
                if (s.contains("更多") || s.contains("More")) {
                    return child;
                }
            }
            if (child instanceof ViewGroup) {
                View nested = findPlusByDesc(child);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }

    private static String findLabel(View view) {
        CharSequence text = textOf(view);
        if (text != null && text.length() > 0) {
            return text.toString().trim();
        }
        if (view instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) view;
            for (int i = 0; i < g.getChildCount(); i++) {
                String found = findLabel(g.getChildAt(i));
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static CharSequence textOf(View view) {
        if (view == null) {
            return null;
        }
        if (view instanceof TextView) {
            return ((TextView) view).getText();
        }
        try {
            Object v = view.getClass().getMethod("getText").invoke(view);
            return v instanceof CharSequence ? (CharSequence) v : null;
        } catch (Throwable t) {
            return null;
        }
    }

    // ------------------------------------------------------------ 快捷回复（已按需求移除）

    // ------------------------------------------------------------ 工具

    /**
     * 精确定位 initAppGrid。
     *
     * 不能靠「方法签名」判断：AppPanel 里有多个 `static void xxx(AppPanel)` 方法
     * （8.0.76 里 d / e / g / n 都符合），猜错就会调错方法（之前 hook 到 d 就是这个问题）。
     *
     * 微信 8.0.76 反编译确认：initAppGrid 就是 `public static n(AppPanel)`，
     * 方法体里带有 "MicroMsg.AppPanel" + "initAppGrid()" 两个日志字符串。
     * 因此这里按「方法名 + 签名」精确锁定，并对其它版本做签名兜底。
     */
    private static Method findInitAppGrid(Class<?> panelClass) {
        // 首选：8.0.76 的已知名称
        for (Method m : panelClass.getDeclaredMethods()) {
            Class<?>[] ps = m.getParameterTypes();
            if ("n".equals(m.getName())
                    && ps.length == 1 && ps[0] == panelClass
                    && m.getReturnType() == void.class
                    && java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
                return m;
            }
        }
        // 兜底：唯一一个 static void xxx(AppPanel) 且方法名很短
        Method candidate = null;
        int hits = 0;
        for (Method m : panelClass.getDeclaredMethods()) {
            Class<?>[] ps = m.getParameterTypes();
            if (ps.length == 1 && ps[0] == panelClass
                    && m.getReturnType() == void.class
                    && java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
                hits++;
                if (m.getName().length() == 1) {
                    candidate = m;
                }
            }
        }
        return hits == 1 ? candidate : null;
    }

    private static Field findField(Class<?> cls, String name) {
        Class<?> c = cls;
        while (c != null) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        return null;
    }

    private static Method inherited(Class<?> type,String name,Class<?>...parameters)throws NoSuchMethodException{for(Class<?> current=type;current!=null;current=current.getSuperclass())try{Method method=current.getDeclaredMethod(name,parameters);method.setAccessible(true);return method;}catch(NoSuchMethodException ignored){}throw new NoSuchMethodException(name);}

    private static void toast(Activity activity, String msg) {
        try {
            ImagePicker.INSTANCE.toast(activity, msg);
        } catch (Throwable ignore) {
        }
    }

    private static void log(String msg) {
        try {
            XposedBridge.log(TAG + msg);
        } catch (Throwable ignore) {
        }
    }

    private static int dp(float density, float value) {
        return (int) (value * density + 0.5f);
    }
}
