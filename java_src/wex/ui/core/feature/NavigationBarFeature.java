package wex.ui.core.feature;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import wex.ui.core.WexStore;

/** WeKit-inspired visual replacement; native pager count, items and logical indices remain untouched. */
public final class NavigationBarFeature {
    static final String KEY_ENABLED = "bottom_bar_enabled";
    static final String KEY_FLOATING = "nav_bar_use_floating";
    static final String KEY_AUTO_HIDE = "nav_bar_auto_hide_on_scroll";
    static final String KEY_BACKDROP = "nav_bar_use_backdrop";
    static final String KEY_EFFECT = "nav_bar_glass_mode";
    static final String KEY_ANIMATE = "nav_bar_animate_page_change";
    static final String KEY_FINDER_BADGE = "nav_bar_show_finder_badge";
    static final String KEY_HIDE_LABELS = "nav_bar_hide_labels";
    static final String KEY_NATIVE_ICONS = "nav_bar_use_wechat_icons";
    static final String KEY_BLUR = "nav_bar_blur_radius";
    static final String KEY_SCALE = "nav_bar_scale";
    static final String KEY_GRAVITY = "nav_bar_dynamic_gravity_highlight";

    static final class Config {
        final boolean enabled = flag(KEY_ENABLED, false);
        final boolean floating = flag(KEY_FLOATING, true);
        final boolean autoHide = flag(KEY_AUTO_HIDE, false);
        final int effect = BubbleSettings.readInt(KEY_EFFECT, flag(KEY_BACKDROP, true) ? 2 : 0, 0, 2);
        final boolean backdrop = effect != 0;
        final boolean liquid = effect == 2;
        final boolean animate = flag(KEY_ANIMATE, true);
        final boolean finderBadge = flag(KEY_FINDER_BADGE, true);
        final boolean nativeIcons = flag(KEY_NATIVE_ICONS, false);
        final boolean hideLabels = flag(KEY_HIDE_LABELS, false);
        final boolean gravityHighlight = flag(KEY_GRAVITY, false);
        final int blur = BubbleSettings.readInt(KEY_BLUR, effect == 1 ? 25 : 8, 0, 40);
        final int scale = BubbleSettings.readInt(KEY_SCALE, 100, 50, 150);
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Set<Method> HOOKS = new HashSet<>();
    private static final WeakHashMap<Activity, Runnable> RESUMES = new WeakHashMap<>();
    private static final WeakHashMap<Activity, AttachAttempt> ATTACHES = new WeakHashMap<>();
    private static final ThreadLocal<Boolean> TAB_TAP = new ThreadLocal<>();
    private static WeakReference<Activity> launcher = new WeakReference<>(null);
    private static WeakReference<Activity> resumedLauncher = new WeakReference<>(null);
    private static WeakReference<View> pager = new WeakReference<>(null);
    private static volatile Config config = new Config();
    private static boolean initialized;

    private NavigationBarFeature() { }
    static Config config() { return config; }
    static View pager() { return pager.get(); }
    static boolean resumed(Activity activity) { return activity != null && resumedLauncher.get() == activity; }

    static boolean flag(String key, boolean def) {
        try { return WexStore.INSTANCE.getBool(key, def); }
        catch (RuntimeException error) { return def; }
    }

    public static synchronized void init(XC_LoadPackage.LoadPackageParam lpparam) {
        if (initialized) return;
        initialized = true;
        config = new Config();
        ClassLoader loader = HostTargetResolver.hostClassLoader();
        if (loader == null && lpparam != null) loader = lpparam.classLoader;
        try {
            Class<?> nativeBar = XposedHelpers.findClass("com.tencent.mm.ui.LauncherUIBottomTabView", loader);
            XposedBridge.hookAllConstructors(nativeBar, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    ViewGroup bar = (ViewGroup) param.thisObject;
                    bar.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                        @Override public void onViewAttachedToWindow(View view) { scheduleAttach((ViewGroup) view); }
                        @Override public void onViewDetachedFromWindow(View view) {
                            cancelAttach(ChatToolbarLifecycle.findActivity(view.getContext()));
                        }
                    });
                    scheduleAttach(bar);
                }
            });
            optionalHook(nativeBar, "setTo", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (config.enabled && !param.hasThrowable()) NavigationBarViews.nativeSelected(param.thisObject, (Integer) param.args[0]);
                }
            }, int.class);
        } catch (Throwable error) { log("原生底栏接线失败 " + error.getClass().getSimpleName()); }
        try {
            Class<?> adapter = XposedHelpers.findClass("com.tencent.mm.ui.MainTabUI$TabsAdapter", loader);
            optionalHook(adapter, "onPageScrolled", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (config.enabled && !param.hasThrowable()) NavigationBarViews.scrolled(param.thisObject, (Integer) param.args[0], (Float) param.args[1]);
                }
            }, int.class, float.class, int.class);
            optionalHook(adapter, "onPageSelected", new XC_MethodHook() {
                private final WeakHashMap<MethodHookParam, Long> epochs = new WeakHashMap<>();
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    if (config.enabled) epochs.put(param, NavigationBarViews.beginSelection(param.thisObject));
                }
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    Long epoch = epochs.remove(param);
                    // A synchronous host event can reenter a newer request/selection in the body.
                    if (config.enabled && !param.hasThrowable() && epoch != null)
                        NavigationBarViews.selected(param.thisObject, (Integer) param.args[0], epoch);
                }
            }, int.class);
            optionalHook(adapter, "onPageScrollStateChanged", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (config.enabled && !param.hasThrowable()) NavigationBarViews.scrollState(param.thisObject, (Integer) param.args[0]);
                }
            }, int.class);
        } catch (Throwable error) { log("分页回调类不可用 " + error.getClass().getSimpleName()); }
        try {
            Class<?> pagerClass = XposedHelpers.findClass("com.tencent.mm.ui.mogic.WxViewPager", loader);
            for (String name : new String[]{"setCurrentItem", "setCurrentItemNotify"}) {
                optionalHook(pagerClass, name, new XC_MethodHook() {
                    private final WeakHashMap<MethodHookParam, Object> requests = new WeakHashMap<>();
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        if (!config.enabled || param.thisObject != pager.get()) return;
                        boolean tabTap = Boolean.TRUE.equals(TAB_TAP.get());
                        if (config.animate && tabTap) param.args[1] = true;
                        requests.put(param, NavigationBarViews.switchRequested(param.thisObject, (Integer) param.args[0], tabTap));
                    }
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        Object request = requests.remove(param);
                        if (config.enabled && param.thisObject == pager.get() && request != null)
                            NavigationBarViews.switchFinished(param.thisObject, request, !param.hasThrowable());
                    }
                }, int.class, boolean.class);
            }
        } catch (Throwable error) { log("分页切换类不可用 " + error.getClass().getSimpleName()); }
        try {
            Class<?> listClass = XposedHelpers.findClass("com.tencent.mm.ui.conversation.ConversationListView", loader);
            optionalHook(listClass, "onScroll", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (!config.enabled || param.hasThrowable()) return;
                    AbsListView list = (AbsListView) param.thisObject;
                    View first = list.getChildAt(0);
                    NavigationBarViews.listScroll(list, (Integer) param.args[1], first == null ? 0 : first.getTop(), first == null);
                }
            }, AbsListView.class, int.class, int.class, int.class);
            optionalHook(listClass, "onScrollStateChanged", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (config.enabled) NavigationBarViews.listScrollState((AbsListView) param.thisObject,
                            (Integer) param.args[1] == AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL);
                }
            }, AbsListView.class, int.class);
        } catch (Throwable error) { log("会话滚动类不可用 " + error.getClass().getSimpleName()); }
        hookFrosted(loader);
        resolveCallbacks();
        XposedBridge.hookAllMethods(Activity.class, "onResume", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                Activity activity = (Activity) param.thisObject;
                if (!isLauncher(activity) || param.hasThrowable()) return;
                postResume(activity);
            }
        });
        XposedBridge.hookAllMethods(Activity.class, "onPause", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                Activity activity = (Activity) param.thisObject;
                if (isLauncher(activity)) {
                    cancelResume(activity);
                    cancelAttach(activity);
                    if (resumedLauncher.get() == activity) resumedLauncher.clear();
                    NavigationBarViews.lifecycle(activity, false);
                }
            }
        });
        XposedBridge.hookAllMethods(Activity.class, "onDestroy", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                Activity activity = (Activity) param.thisObject;
                if (isLauncher(activity)) {
                    cancelResume(activity);
                    cancelAttach(activity);
                    if (resumedLauncher.get() == activity) resumedLauncher.clear();
                    if (launcher.get() == activity) { launcher.clear(); pager.clear(); }
                    NavigationBarViews.remove(activity);
                }
            }
        });
    }

    private static void hookFrosted(ClassLoader loader) {
        final Class<?> frosted;
        try { frosted = XposedHelpers.findClass("com.tencent.mm.ui.FrostedContentView", loader); }
        catch (Throwable error) { log("原生底部背景类不可用 " + error.getClass().getSimpleName()); return; }
        try {
            Set<Method> matches = new HashSet<>();
            for (Method method : frosted.getDeclaredMethods()) {
                if (method.getReturnType() == void.class && java.util.Arrays.equals(method.getParameterTypes(),
                        new Class<?>[]{boolean.class, int.class, float.class})) matches.add(method);
            }
            if (matches.size() != 1) throw new IllegalStateException("Frosted configuration requires one structural match");
            hook(matches.iterator().next(), new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    NavigationFrostedController.beginConfiguration((View) param.thisObject);
                }
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    boolean outermost = NavigationFrostedController.endConfiguration((View) param.thisObject);
                    if (outermost && !param.hasThrowable()) NavigationBarViews.frostedUpdated((View) param.thisObject, null);
                }
            });
        } catch (Throwable error) { log("原生底部背景配置接线失败 " + error.getClass().getSimpleName()); }
        try {
            hook(method(frosted, "setBottomBlurAreaHeight", int.class), new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (!param.hasThrowable()) NavigationBarViews.frostedUpdated((View) param.thisObject, (Integer) param.args[0]);
                }
            });
        } catch (Throwable error) { log("原生底部背景高度接线失败 " + error.getClass().getSimpleName()); }
        try {
            hook(method(frosted, "dispatchDraw", android.graphics.Canvas.class), new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    NavigationBarViews.frostedDrawing((View) param.thisObject);
                }
            });
        } catch (Throwable error) { log("原生底部背景绘制接线失败 " + error.getClass().getSimpleName()); }
    }

    private static void resolveCallbacks() {
        String owner = "com.tencent.mm.ui.LauncherUIBottomTabView";
        resolve("nav_main_unread", new HostTargetResolver.MethodSpec().declaredClass(owner).returnType("void")
                .parameterTypes("int").usingExactStrings("MicroMsg.LauncherUITabView", "updateMainTabUnread %d"), true, false);
        resolve("nav_contact_unread", new HostTargetResolver.MethodSpec().declaredClass(owner).returnType("void")
                .parameterTypes("int").usingExactStrings("[updateContactTabUnread] unread : "), true, false);
        resolve("nav_friend_unread", new HostTargetResolver.MethodSpec().declaredClass(owner).returnType("void")
                .parameterTypes("int").usingExactStrings("[updateFriendTabUnread] unread : "), true, false);
        resolve("nav_friend_point", new HostTargetResolver.MethodSpec().declaredClass(owner).returnType("void")
                .parameterTypes("boolean").usingExactStrings("[showFriendPoint] show : "), true, false);
        resolve("nav_settings_unread", new HostTargetResolver.MethodSpec().declaredClass(owner).returnType("void")
                .parameterTypes("int").usingExactStrings("[updateSettingsTabUnread] unread : "), true, false);
        resolve("nav_recent_open", new HostTargetResolver.MethodSpec()
                .classStrings("com/tencent/mm/plugin/taskbar/ui/TaskBarAnimController$6").name("onAnimationStart")
                .parameterTypes("android.animation.Animator").returnType("void"), false, true);
        resolve("nav_recent_close", new HostTargetResolver.MethodSpec()
                .usingExactStrings("com/tencent/mm/plugin/taskbar/ui/TaskBarAnimController", "onClose")
                .parameterTypes("boolean").returnType("void"), false, false);
    }

    private static void resolve(String id, HostTargetResolver.MethodSpec spec, boolean badge, boolean recent) {
        HostTargetResolver.resolveMethod(id, spec, new HostTargetResolver.MethodCallback() {
            @Override public void onResolved(Method method) {
                MAIN.post(() -> {
                    try {
                        hook(method, new XC_MethodHook() {
                            @Override protected void afterHookedMethod(MethodHookParam param) {
                                if (!config.enabled || param.hasThrowable()) return;
                                if (badge) {
                                    WeakReference<Object> owner = new WeakReference<>(param.thisObject);
                                    MAIN.post(() -> { Object current = owner.get(); if (current != null) NavigationBarViews.badges(current); });
                                } else MAIN.post(() -> NavigationBarViews.recentHidden(recent));
                            }
                        });
                        if (badge && config.enabled) NavigationBarViews.badges();
                    } catch (Throwable error) { log(id + " Hook 失败 " + error.getClass().getSimpleName()); }
                });
            }
            @Override public void onFailure(Throwable error) { log(id + " 不可用；不伪造对应状态"); }
        });
    }

    private static void postResume(Activity activity) {
        cancelResume(activity);
        WeakReference<Activity> reference = new WeakReference<>(activity);
        Runnable resume = new Runnable() {
            @Override public void run() {
                Activity current = reference.get();
                if (current == null || RESUMES.get(current) != this) return;
                RESUMES.remove(current);
                if (current.isFinishing() || current.isDestroyed()) return;
                launcher = new WeakReference<>(current);
                resumedLauncher = new WeakReference<>(current);
                // Activity's after-hook still runs inside LauncherUI.onResume's super call.
                // The main-queue turn observes the host's after-super initialization instead.
                refresh();
                NavigationBarViews.lifecycle(current, true);
            }
        };
        RESUMES.put(activity, resume);
        MAIN.post(resume);
    }

    private static void cancelResume(Activity activity) {
        Runnable resume = RESUMES.remove(activity);
        if (resume != null) MAIN.removeCallbacks(resume);
    }

    public static void refresh() {
        if (Looper.myLooper() != Looper.getMainLooper()) { MAIN.post(NavigationBarFeature::refresh); return; }
        config = new Config();
        if (!config.enabled) for (Activity activity : new ArrayList<>(ATTACHES.keySet())) cancelAttach(activity);
        NavigationBarViews.refresh();
        Activity activity = launcher.get();
        if (!resumed(activity) || activity.isFinishing() || activity.isDestroyed() || !config.enabled) return;
        scheduleAttach(activity);
    }

    private static void scheduleAttach(ViewGroup bar) {
        scheduleAttach(ChatToolbarLifecycle.findActivity(bar.getContext()));
    }

    private static void scheduleAttach(Activity activity) {
        if (!config.enabled || !isLauncher(activity) || !resumed(activity) || activity.isFinishing()
                || activity.isDestroyed() || ATTACHES.containsKey(activity) || activity.getWindow() == null) return;
        AttachAttempt attempt = new AttachAttempt(activity, activity.getWindow().getDecorView());
        ATTACHES.put(activity, attempt);
        attempt.queue();
    }

    private static void cancelAttach(Activity activity) {
        AttachAttempt attempt = ATTACHES.remove(activity);
        if (attempt != null) {
            View root = attempt.root.get();
            if (root != null) root.removeCallbacks(attempt);
        }
    }

    /** One bounded frame callback per resumed host; no sleeps and no retained Activity/View. */
    private static final class AttachAttempt implements Runnable {
        private static final int MAX_FRAMES = 120;
        final WeakReference<Activity> activity;
        final WeakReference<View> root;
        int frames;
        boolean warned;
        AttachAttempt(Activity activity, View root) {
            this.activity = new WeakReference<>(activity); this.root = new WeakReference<>(root);
        }
        void queue() { View view = root.get(); if (view != null) view.postOnAnimation(this); }
        @Override public void run() {
            Activity current = activity.get(); View view = root.get();
            if (current == null || ATTACHES.get(current) != this) return;
            if (!config.enabled || !resumed(current) || current.isDestroyed() || current.isFinishing()
                    || view == null || current.getWindow() == null || current.getWindow().getDecorView() != view) {
                cancelAttach(current); return;
            }
            frames++;
            try {
                ViewGroup bar = findNativeBar(view);
                if (bar != null && attach(bar)) { cancelAttach(current); return; }
            } catch (Throwable error) {
                if (!warned) { warned = true; log("底栏尚未就绪，保留原生栏 " + error.getClass().getSimpleName()); }
            }
            if (frames >= MAX_FRAMES) { cancelAttach(current); log("底栏首帧重试已结束；保留原生栏"); }
            else queue();
        }
    }

    private static boolean attach(ViewGroup nativeBar) {
        if (!nativeBar.isAttachedToWindow()) return false;
        View parent = nativeBar.getParent() instanceof View ? (View) nativeBar.getParent() : null;
        if (parent == null) return false;
        View source = findPager(parent);
        if (source == null || !source.isAttachedToWindow() || source.getWidth() <= 0 || source.getHeight() <= 0) return false;
        Object adapter = XposedHelpers.callMethod(source, "getAdapter");
        if (adapter == null) return false;
        if (!"com.tencent.mm.ui.MainTabUI$TabsAdapter".equals(adapter.getClass().getName())
                || ((Number) XposedHelpers.callMethod(adapter, "getCount")).intValue() != 4) {
            log("宿主不是原生四页，保留原生栏"); return true;
        }
        NavigationBarViews.install(nativeBar, source, adapter);
        pager = new WeakReference<>(source);
        return true;
    }

    static boolean homeVisible(Activity activity, View source) {
        if (!isLauncher(activity) || !resumed(activity) || source == null || !source.isShown()) return false;
        ViewGroup root = (ViewGroup) activity.getWindow().getDecorView();
        ArrayDeque<View> queue = new ArrayDeque<>(); queue.add(root);
        for (int visited = 0; !queue.isEmpty() && visited < 1024; visited++) {
            View view = queue.removeFirst();
            if (view.getVisibility() != View.VISIBLE || view.getAlpha() <= 0f) continue;
            if ("com.tencent.mm.pluginsdk.ui.chat.ChattingUILayout".equals(view.getClass().getName())
                    && view.getGlobalVisibleRect(new android.graphics.Rect())) return false;
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) queue.addLast(group.getChildAt(i));
            }
        }
        return queue.isEmpty();
    }

    private static boolean isLauncher(Activity activity) {
        return activity != null && "com.tencent.mm.ui.LauncherUI".equals(activity.getClass().getName());
    }

    private static ViewGroup findNativeBar(View root) {
        ArrayDeque<View> queue = new ArrayDeque<>(); queue.add(root);
        for (int visited = 0; !queue.isEmpty() && visited < 768; visited++) {
            View view = queue.removeFirst();
            if (view instanceof ViewGroup && "com.tencent.mm.ui.LauncherUIBottomTabView".equals(view.getClass().getName())) return (ViewGroup) view;
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) queue.addLast(group.getChildAt(i));
            }
        }
        return null;
    }

    private static View findPager(View root) {
        View result = null;
        ArrayDeque<View> queue = new ArrayDeque<>(); queue.add(root);
        for (int visited = 0; !queue.isEmpty() && visited < 192; visited++) {
            View view = queue.removeFirst();
            if ("com.tencent.mm.ui.mogic.WxViewPager".equals(view.getClass().getName())
                    || "com.tencent.mm.ui.base.CustomViewPager".equals(view.getClass().getName())) {
                if (result != null) return null;
                result = view;
            } else if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) queue.addLast(group.getChildAt(i));
            }
        }
        return queue.isEmpty() ? result : null;
    }

    static void fromTabTap(Runnable action) {
        Boolean previous = TAB_TAP.get(); TAB_TAP.set(true);
        try { action.run(); }
        finally { if (previous == null) TAB_TAP.remove(); else TAB_TAP.set(previous); }
    }

    private static Method method(Class<?> owner, String name, Class<?>... parameters) throws NoSuchMethodException {
        for (Class<?> current = owner; current != null; current = current.getSuperclass()) {
            try { Method method = current.getDeclaredMethod(name, parameters); method.setAccessible(true); return method; }
            catch (NoSuchMethodException ignored) { }
        }
        throw new NoSuchMethodException(owner.getName() + "#" + name);
    }

    private static void optionalHook(Class<?> owner, String name, XC_MethodHook callback, Class<?>... parameters) {
        try { hook(method(owner, name, parameters), callback); }
        catch (Throwable error) { log(owner.getSimpleName() + "#" + name + " 接线失败 " + error.getClass().getSimpleName()); }
    }

    private static synchronized void hook(Method method, XC_MethodHook callback) {
        if (HOOKS.contains(method)) return;
        XposedBridge.hookMethod(method, callback); HOOKS.add(method);
    }

    static void log(String message) {
        try { XposedBridge.log("WEX: 底栏: " + message); } catch (Throwable ignored) { }
    }
}
