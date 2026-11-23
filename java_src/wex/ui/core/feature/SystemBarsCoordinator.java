package wex.ui.core.feature;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.WeakHashMap;

import dev.mindlink.xposed.XC_MethodHook;
import dev.mindlink.xposed.XposedBridge;
import wex.ui.core.WexStore;

/** Window colors and inset measurements; only module-owned layouts consume insets. */
final class SystemBarsCoordinator {
    static final String KEY_ENABLED = "system_bars_enabled";
    private static final String WRAPPER = "com.tencent.mm.ui.widget.EdgeToEdgeWrapperLayout";
    private static final WeakHashMap<Window, WindowState> WINDOWS = new WeakHashMap<Window, WindowState>();
    // Kept separate from BOTTOM: releasing a footer must not restore home/TOP glyphs.
    private static final WeakHashMap<Window, StatusState> STATUS = new WeakHashMap<>();
    private static final WeakHashMap<Activity, ResumeApply> PENDING = new WeakHashMap<>();
    private static final WeakHashMap<Activity, Boolean> RESUMED = new WeakHashMap<>();
    private static final WeakHashMap<View, ChatVisibility> CHATS = new WeakHashMap<>();
    private static final java.util.Set<ClassLoader> CHAT_LOADERS = java.util.Collections.newSetFromMap(new WeakHashMap<>());
    private static final WeakHashMap<Object, WeakReference<Activity>> HOME_OWNERS = new WeakHashMap<>();
    private static final WeakHashMap<Class<?>, java.lang.reflect.Method> FOREGROUND = new WeakHashMap<>();
    private static final String LAUNCHER = "com.tencent.mm.ui.LauncherUI";
    private static final String CHAT = "com.tencent.mm.pluginsdk.ui.chat.ChattingUILayout";
    private static boolean initialized;

    private SystemBarsCoordinator() { }

    static synchronized void init() {
        if (initialized) return;
        XposedBridge.hookAllMethods(Activity.class, "onResume", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                if (param.thisObject instanceof Activity) {
                    Activity activity = (Activity) param.thisObject;
                    if (!"com.tencent.mm".equals(activity.getPackageName())) return;
                    RESUMED.put(activity, Boolean.TRUE);
                    schedule(activity);
                }
            }
        });
        XposedBridge.hookAllMethods(Activity.class, "onWindowFocusChanged", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                if (param.thisObject instanceof Activity && param.args.length == 1
                        && Boolean.TRUE.equals(param.args[0])) schedule((Activity) param.thisObject);
            }
        });
        XposedBridge.hookAllMethods(Activity.class, "onPause", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                if (param.thisObject instanceof Activity) {
                    Activity activity = (Activity) param.thisObject;
                    RESUMED.remove(activity);
                    cancel(activity);
                }
            }
        });
        XposedBridge.hookAllMethods(Activity.class, "onDestroy", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                if (param.thisObject instanceof Activity) {
                    Activity activity = (Activity) param.thisObject;
                    RESUMED.remove(activity);
                    cancel(activity);
                    removeChatObservers(activity);
                    restoreStatus(activity.getWindow());
                    restore(activity.getWindow());
                }
            }
        });
        initialized = true;
    }

    private static void schedule(Activity activity) {
        if (!"com.tencent.mm".equals(activity.getPackageName())) return;
        cancel(activity);
        Window window = activity.getWindow();
        if (window == null) return;
        if (!WexStore.INSTANCE.getBool(KEY_ENABLED, true)
                || GlobalBackgroundFeature.BLACKLISTED_ACTIVITIES.contains(activity.getClass().getName())) {
            removeChatObservers(activity);
            restoreStatus(window);
            restore(window);
            return;
        }
        if (activity.isDestroyed() || activity.isFinishing()) return;
        if (LAUNCHER.equals(activity.getClass().getName())) {
            hookChatConstructors(activity.getClassLoader());
            bindHome(activity);
        }
        ResumeApply apply = new ResumeApply(activity, window);
        PENDING.put(activity, apply);
        window.getDecorView().post(apply);
    }

    private static void cancel(Activity activity) {
        ResumeApply pending = PENDING.remove(activity);
        if (pending != null) {
            Window window = pending.window.get();
            if (window != null) window.getDecorView().removeCallbacks(pending);
        }
    }

    /** One coalesced queue turn, after the host's after-super window setup. No polling. */
    private static final class ResumeApply implements Runnable {
        final WeakReference<Activity> activity;
        final WeakReference<Window> window;
        ResumeApply(Activity activity, Window window) {
            this.activity = new WeakReference<>(activity);
            this.window = new WeakReference<>(window);
        }
        @Override public void run() {
            Activity current = activity.get();
            Window target = window.get();
            if (current == null || PENDING.get(current) != this) return;
            PENDING.remove(current);
            if (target == null || current.getWindow() != target || current.isDestroyed()
                    || current.isFinishing() || !target.getDecorView().isAttachedToWindow()) return;
            if (!WexStore.INSTANCE.getBool(KEY_ENABLED, true)) {
                removeChatObservers(current);
                restoreStatus(target);
                restore(target);
                return;
            }
            matchNavigationBar(target, pageColor(current));
            if (target.getDecorView().hasWindowFocus()) matchHomeStatus(current, target);
        }
    }

    @SuppressWarnings("deprecation")
    private static void matchHomeStatus(Activity activity, Window window) {
        if (!LAUNCHER.equals(activity.getClass().getName())) return;
        Boolean foreground = chatForeground(activity);
        boolean home = foreground == null ? homeVisible(activity, window.getDecorView())
                : !foreground && window.getDecorView().isShown();
        // Only a live chat owns TOP. A cached/stale TOP token must not veto verified home.
        if (ChatWindowInsetsCoordinator.isTopOwned(window) && (foreground == null || !home)) {
            STATUS.remove(window); return;
        }
        if (!home) { restoreStatus(window); return; }
        Integer surface = statusSurface(activity, window, foreground != null);
        if (surface == null) return; // Transparent/unknown wallpaper is not a measured color.
        WindowInsetsController controller = window.getInsetsController();
        if (controller == null) return;
        int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS;
        int current = controller.getSystemBarsAppearance() & mask;
        View decor = window.getDecorView();
        int legacy = decor.getSystemUiVisibility() & View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        StatusState state = STATUS.get(window);
        if (state == null) { state = new StatusState(current, legacy); STATUS.put(window, state); }
        else {
            if (current != state.last) state.original = current;
            if (legacy != state.lastLegacy) state.originalLegacy = legacy;
        }
        int desired = light(surface) ? mask : 0;
        int desiredLegacy = desired == 0 ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (legacy != desiredLegacy) decor.setSystemUiVisibility(
                (decor.getSystemUiVisibility() & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR) | desiredLegacy);
        // Write after legacy flags: HomeUI uses the legacy API; do not leave two conflicting requests.
        if ((controller.getSystemBarsAppearance() & mask) != desired) controller.setSystemBarsAppearance(desired, mask);
        state.last = desired; state.lastLegacy = desiredLegacy;
    }

    /** Verified 8.0.76 public gate: LauncherUI.e7 -> NewChattingTabUI.k -> chat foreground.
     * Never infer foreground from a cached fragment, hidden title, or attached chat shell. */
    private static Boolean chatForeground(Activity activity) {
        java.lang.reflect.Method method = FOREGROUND.get(activity.getClass());
        if (method == null) return null;
        try { return (Boolean) method.invoke(activity); }
        catch (ReflectiveOperationException | ClassCastException ignored) { return null; }
    }

    private static void bindHome(Activity activity) {
        try {
            Object home = activity.getClass().getMethod("getHomeUI").invoke(activity);
            if (home != null && "com.tencent.mm.ui.HomeUI".equals(home.getClass().getName())) {
                HOME_OWNERS.put(home, new WeakReference<>(activity));
            }
        } catch (ReflectiveOperationException ignored) { }
    }

    /** TOP release runs on the UI thread; its stale snapshot must not be the final home request. */
    static void refreshHome(Activity activity) {
        if (activity != null && android.os.Looper.myLooper() == android.os.Looper.getMainLooper()
                && Boolean.TRUE.equals(RESUMED.get(activity))) schedule(activity);
    }

    @SuppressWarnings("deprecation")
    private static Integer statusSurface(Activity activity, Window window, boolean verifiedHost) {
        // The host paints different status surfaces for Me (a9e) and the other three pages (a07).
        int color = window.getStatusBarColor();
        if (verifiedHost && Color.alpha(color) == 255) return color;
        View content = activity.findViewById(android.R.id.content);
        if (content != null && content.getBackground() instanceof ColorDrawable) {
            color = ((ColorDrawable) content.getBackground()).getColor();
            if (Color.alpha(color) == 255) return color;
        }
        if (window.getDecorView().getBackground() instanceof ColorDrawable) {
            color = ((ColorDrawable) window.getDecorView().getBackground()).getColor();
            if (Color.alpha(color) == 255) return color;
        }
        // Unknown host ABI retains the previous pure-theme contract. On the verified host,
        // no opaque existing surface means no evidence of wallpaper brightness: preserve host.
        return verifiedHost ? null : pageColor(activity);
    }

    /** Discover known chat roots even under hidden ancestors; never enter their message/input body. */
    private static boolean homeVisible(Activity activity, View decor) {
        ArrayDeque<View> queue = new ArrayDeque<>(); queue.add(decor);
        android.graphics.Rect bounds = new android.graphics.Rect();
        boolean home = decor.isShown();
        for (int visited = 0; !queue.isEmpty() && visited < 192; visited++) {
            View view = queue.removeFirst();
            if (CHAT.equals(view.getClass().getName())) {
                observeChat(activity, view);
                if (chatVisible(view, bounds)) home = false;
                continue; // Only structural ownership is relevant, not chat content.
            }
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                if (queue.size() + group.getChildCount() > 192) return false;
                for (int i = 0; i < group.getChildCount(); i++) queue.addLast(group.getChildAt(i));
            }
        }
        return home && queue.isEmpty(); // Unknown/over-budget trees are not proof of home.
    }

    /** Same bounded predicate for discovery and target-only layout notifications. */
    private static boolean chatVisible(View view, android.graphics.Rect bounds) {
        if (!view.isAttachedToWindow()) return false;
        View current = view;
        for (int depth = 0; depth < 32; depth++) {
            if (current.getVisibility() != View.VISIBLE || current.getAlpha() <= 0f) return false;
            if (!(current.getParent() instanceof View)) return view.getGlobalVisibleRect(bounds);
            current = (View) current.getParent();
        }
        return true; // Unverified deep hierarchy is conservatively chat, not evidence of home.
    }

    private static void hookChatConstructors(ClassLoader loader) {
        if (!CHAT_LOADERS.add(loader)) return;
        try {
            Class<?> launcher = Class.forName(LAUNCHER, false, loader);
            java.lang.reflect.Method foreground = launcher.getMethod("e7");
            if (foreground.getReturnType() == boolean.class) FOREGROUND.put(launcher, foreground);
            // Activity's base hook fires before Launcher/HomeUI's after-super changes.
            // These exact host methods also cover same-Activity tab changes and chat exit.
            XC_MethodHook launcherEvent = new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (param.thisObject instanceof Activity) refreshHome((Activity) param.thisObject);
                }
            };
            XposedBridge.hookAllMethods(launcher, "onResume", launcherEvent);
            XposedBridge.hookAllMethods(launcher, "onWindowFocusChanged", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (param.thisObject instanceof Activity && param.args.length == 1
                            && Boolean.TRUE.equals(param.args[0])) refreshHome((Activity) param.thisObject);
                }
            });
            XposedBridge.hookAllMethods(launcher, "closeChatting", launcherEvent);
            XposedBridge.hookAllMethods(launcher, "onSettle", launcherEvent);
            Class<?> home = Class.forName("com.tencent.mm.ui.HomeUI", false, loader);
            XC_MethodHook homeEvent = new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    WeakReference<Activity> owner = HOME_OWNERS.get(param.thisObject);
                    if (owner != null) refreshHome(owner.get());
                }
            };
            XposedBridge.hookAllMethods(home, "w", homeEvent);
            XposedBridge.hookAllMethods(home, "v", homeEvent);
        } catch (ReflectiveOperationException ignored) {
            // Unknown host: bounded structural fallback is still fail-closed, not a larger scan.
        }
        try {
            Class<?> type = Class.forName(CHAT, false, loader);
            if (!View.class.isAssignableFrom(type)) return;
            XposedBridge.hookAllConstructors(type, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (!(param.thisObject instanceof View)) return;
                    View view = (View) param.thisObject;
                    android.content.Context context = view.getContext();
                    for (int depth = 0; context instanceof android.content.ContextWrapper && !(context instanceof Activity)
                            && depth < 16; depth++) {
                        android.content.Context base = ((android.content.ContextWrapper) context).getBaseContext();
                        if (base == context) return;
                        context = base;
                    }
                    if (context instanceof Activity) observeChat((Activity) context, view);
                }
            });
        } catch (Throwable ignored) {
            // This optional host signal is not proof of runtime coverage. Resume/focus remain available.
        }
    }

    private static void observeChat(Activity activity, View view) {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) return;
        if (!LAUNCHER.equals(activity.getClass().getName()) || !CHAT.equals(view.getClass().getName())
                || !WexStore.INSTANCE.getBool(KEY_ENABLED, true) || activity.isDestroyed()
                || activity.isFinishing() || CHATS.containsKey(view)) return;
        ChatVisibility observer = new ChatVisibility(activity, view);
        CHATS.put(view, observer);
        view.addOnAttachStateChangeListener(observer);
        if (view.isAttachedToWindow()) observer.onViewAttachedToWindow(view);
    }

    private static void removeChatObservers(Activity activity) {
        java.util.Iterator<java.util.Map.Entry<View, ChatVisibility>> entries = CHATS.entrySet().iterator();
        while (entries.hasNext()) {
            java.util.Map.Entry<View, ChatVisibility> entry = entries.next();
            if (entry.getValue().activity.get() == activity) {
                entry.getValue().stop();
                entry.getKey().removeOnAttachStateChangeListener(entry.getValue());
                entries.remove();
            }
        }
    }

    /** Only the known chat view's effective visibility is read on layout; no per-frame tree scan. */
    private static final class ChatVisibility implements View.OnAttachStateChangeListener,
            android.view.ViewTreeObserver.OnGlobalLayoutListener {
        final WeakReference<Activity> activity;
        final WeakReference<View> view;
        android.view.ViewTreeObserver tree;
        final android.graphics.Rect bounds = new android.graphics.Rect();
        boolean shown;
        ChatVisibility(Activity activity, View view) {
            this.activity = new WeakReference<>(activity);
            this.view = new WeakReference<>(view);
        }
        void stop() {
            if (tree != null && tree.isAlive()) tree.removeOnGlobalLayoutListener(this);
            tree = null;
        }
        @Override public void onViewAttachedToWindow(View current) {
            stop();
            tree = current.getViewTreeObserver();
            tree.addOnGlobalLayoutListener(this);
            shown = chatVisible(current, bounds);
            changed();
        }
        @Override public void onViewDetachedFromWindow(View current) {
            stop();
            shown = false;
            changed();
        }
        @Override public void onGlobalLayout() {
            View current = view.get();
            if (current == null) { stop(); return; }
            boolean visible = chatVisible(current, bounds);
            if (visible != shown) { shown = visible; changed(); }
        }
        void changed() {
            Activity owner = activity.get();
            if (owner == null || owner.isDestroyed() || owner.isFinishing()) { stop(); return; }
            if (!WexStore.INSTANCE.getBool(KEY_ENABLED, true)) {
                removeChatObservers(owner);
                cancel(owner);
                restoreStatus(owner.getWindow());
                restore(owner.getWindow());
                return;
            }
            if (Boolean.TRUE.equals(RESUMED.get(owner))) schedule(owner);
        }
    }

    @SuppressWarnings("deprecation")
    private static void restoreStatus(Window window) {
        StatusState state = STATUS.remove(window);
        if (state == null || ChatWindowInsetsCoordinator.isTopOwned(window)) return;
        WindowInsetsController controller = window.getInsetsController();
        int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS;
        View decor = window.getDecorView();
        int current = controller == null ? 0 : controller.getSystemBarsAppearance() & mask;
        int restored = current == state.last ? state.original : current;
        if ((decor.getSystemUiVisibility() & View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR) == state.lastLegacy
                && state.lastLegacy != state.originalLegacy) decor.setSystemUiVisibility(
                (decor.getSystemUiVisibility() & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR) | state.originalLegacy);
        if (controller != null && (controller.getSystemBarsAppearance() & mask) != restored) {
            controller.setSystemBarsAppearance(restored, mask);
        }
    }

    private static final class StatusState {
        int original, last, originalLegacy, lastLegacy;
        StatusState(int value, int legacy) { original = last = value; originalLegacy = lastLegacy = legacy; }
    }

    static Insets bars(View view) {
        WindowInsets insets = view.getRootWindowInsets();
        return insets == null ? Insets.NONE : insets.getInsets(WindowInsets.Type.systemBars()
                | WindowInsets.Type.displayCutout());
    }

    static int remainingBottom(View content) {
        View root = content.getRootView();
        WindowInsets insets = root.getRootWindowInsets();
        if (insets == null || !content.isAttachedToWindow()) return 0;
        int[] rootPosition = new int[2];
        int[] contentPosition = new int[2];
        root.getLocationInWindow(rootPosition);
        content.getLocationInWindow(contentPosition);
        return InsetGeometry.remainingBottom(rootPosition[1] + root.getHeight(),
                contentPosition[1] + content.getHeight(),
                insets.getInsets(WindowInsets.Type.navigationBars()).bottom,
                insets.getInsets(WindowInsets.Type.ime()).bottom);
    }

    static void padOverlay(final View owned, final int left, final int top,
                           final int right, final int bottom) {
        final Runnable apply = new Runnable() {
            @Override public void run() {
                Insets insets = bars(owned);
                if (owned.getPaddingLeft() != left + insets.left || owned.getPaddingTop() != top + insets.top
                        || owned.getPaddingRight() != right + insets.right || owned.getPaddingBottom() != bottom + insets.bottom) {
                    owned.setPadding(left + insets.left, top + insets.top, right + insets.right, bottom + insets.bottom);
                }
            }
        };
        owned.setOnApplyWindowInsetsListener((view, insets) -> {
            // Use the callback insets on first attach, before getRootWindowInsets is available.
            Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            view.setPadding(left + bars.left, top + bars.top, right + bars.right, bottom + bars.bottom);
            return insets;
        });
        owned.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) {
                view.requestApplyInsets();
                view.post(apply);
            }
            @Override public void onViewDetachedFromWindow(View view) { view.removeCallbacks(apply); }
        });
        if (owned.isAttachedToWindow()) { owned.requestApplyInsets(); apply.run(); }
    }

    static void refreshBottom(Window window, Activity activity) {
        if (window != null && activity != null) matchNavigationBar(window, pageColor(activity));
    }

    @SuppressWarnings("deprecation")
    static void matchNavigationBar(Window window, int surface) {
        if (window == null) return;
        WindowState state = WINDOWS.get(window);
        if (state == null) {
            state = new WindowState(window);
            WINDOWS.put(window, state);
        }
        state.surface = surface;
        if (ChatWindowInsetsCoordinator.isBottomOwned(window) && state.observer == null) {
            final WeakReference<Window> owner = new WeakReference<>(window);
            state.observer = () -> {
                Window current = owner.get();
                if (current != null && ChatWindowInsetsCoordinator.isBottomOwned(current)) {
                    WindowState value = WINDOWS.get(current);
                    if (value != null) applyNavigationBar(current, value);
                }
                return true;
            };
            state.tree = window.getDecorView().getViewTreeObserver();
            state.tree.addOnPreDrawListener(state.observer);
        }
        applyNavigationBar(window, state);
    }

    @SuppressWarnings("deprecation")
    private static void applyNavigationBar(Window window, WindowState state) {
        View decor = window.getDecorView();
        WindowInsets insets = decor.getRootWindowInsets();
        boolean buttons = insets != null && insets.getInsets(WindowInsets.Type.tappableElement()).bottom > 0;
        // A transparent Window colour alone is not enough: gesture navigation has no tappable
        // buttons, and the EdgeToEdge wrapper draws its own strip from this same colour.
        int color = (!buttons || ChatWindowInsetsCoordinator.shouldExposeBottom(window))
                ? Color.TRANSPARENT : state.surface;
        if (state.applied) {
            if (window.getNavigationBarColor() != state.lastNavigation) state.navigation = window.getNavigationBarColor();
            if (window.getNavigationBarDividerColor() != state.lastNavigation) state.divider = window.getNavigationBarDividerColor();
            if (window.isNavigationBarContrastEnforced() != state.lastContrast) state.contrast = window.isNavigationBarContrastEnforced();
        }
        if (window.getNavigationBarColor() != color) window.setNavigationBarColor(color);
        if (window.getNavigationBarDividerColor() != color) window.setNavigationBarDividerColor(color);
        if (window.isNavigationBarContrastEnforced() != buttons) window.setNavigationBarContrastEnforced(buttons);
        WindowInsetsController controller = window.getInsetsController();
        int light = light(state.surface) ? WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS : 0;
        if (controller != null) {
            int mask = WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            int current = controller.getSystemBarsAppearance() & mask;
            if (state.applied && current != state.lastAppearance) state.appearance = current;
            if (current != light) controller.setSystemBarsAppearance(light, mask);
        }
        ArrayDeque<View> queue = new ArrayDeque<>(); queue.add(decor);
        for (int visited = 0; !queue.isEmpty() && visited < 192; visited++) {
            View view = queue.removeFirst();
            if (WRAPPER.equals(view.getClass().getName())) {
                try {
                    Object value = view.getClass().getMethod("getNavigationBarBackgroundColor").invoke(view);
                    if (value instanceof Integer) {
                        int current = (Integer) value;
                        WrapperState wrapper = state.wrapperColors.get(view);
                        if (wrapper == null) { wrapper = new WrapperState(current); state.wrapperColors.put(view, wrapper); }
                        if (wrapper.applied && current != wrapper.last) wrapper.original = current;
                        if (current != color) view.getClass().getMethod("setNavigationBarBackgroundColor", int.class).invoke(view, color);
                        wrapper.last = color; wrapper.applied = true;
                    }
                } catch (ReflectiveOperationException ignored) { }
            }
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount() && queue.size() < 192; i++) queue.addLast(group.getChildAt(i));
            }
        }
        state.lastNavigation = color; state.lastContrast = buttons; state.lastAppearance = light;
        state.applied = true;
    }

    private static int pageColor(Activity activity) {
        View content = activity.findViewById(android.R.id.content);
        if (content != null && content.getBackground() instanceof ColorDrawable) {
            int color = ((ColorDrawable) content.getBackground()).getColor();
            if (Color.alpha(color) == 255) return color;
        }
        return SettingsTheme.isDark(activity) ? 0xFF191919 : 0xFFF7F7F7;
    }

    private static boolean light(int color) {
        return Color.red(color) * 299 + Color.green(color) * 587 + Color.blue(color) * 114 >= 128000;
    }

    @SuppressWarnings("deprecation")
    static void restore(Window window) {
        if (window == null) return;
        WindowState state = WINDOWS.remove(window);
        if (state == null) return;
        if (state.tree != null && state.tree.isAlive() && state.observer != null) state.tree.removeOnPreDrawListener(state.observer);
        for (java.util.Map.Entry<View, WrapperState> entry : state.wrapperColors.entrySet()) {
            View wrapper = entry.getKey();
            WrapperState value = entry.getValue();
            try {
                Object current = wrapper.getClass().getMethod("getNavigationBarBackgroundColor").invoke(wrapper);
                if (value.applied && Integer.valueOf(value.last).equals(current)) {
                    wrapper.getClass().getMethod("setNavigationBarBackgroundColor", int.class)
                            .invoke(wrapper, value.original);
                }
            } catch (ReflectiveOperationException ignored) { }
        }
        if (window.getNavigationBarColor() == state.lastNavigation) window.setNavigationBarColor(state.navigation);
        if (window.getNavigationBarDividerColor() == state.lastNavigation) window.setNavigationBarDividerColor(state.divider);
        if (window.isNavigationBarContrastEnforced() == state.lastContrast) window.setNavigationBarContrastEnforced(state.contrast);
        WindowInsetsController controller = window.getInsetsController();
        if (controller != null && (controller.getSystemBarsAppearance()
                & WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS) == state.lastAppearance) {
            controller.setSystemBarsAppearance(state.appearance, WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        }
    }

    private static final class WrapperState {
        int original, last;
        boolean applied;
        WrapperState(int color) { original = color; }
    }

    private static final class WindowState {
        final WeakHashMap<View, WrapperState> wrapperColors = new WeakHashMap<>();
        int navigation, divider, appearance, surface;
        boolean contrast, applied;
        int lastNavigation, lastAppearance;
        boolean lastContrast;
        android.view.ViewTreeObserver tree;
        android.view.ViewTreeObserver.OnPreDrawListener observer;

        @SuppressWarnings("deprecation")
        WindowState(Window window) {
            navigation = window.getNavigationBarColor();
            divider = window.getNavigationBarDividerColor();
            contrast = window.isNavigationBarContrastEnforced();
            WindowInsetsController controller = window.getInsetsController();
            appearance = controller == null ? 0 : controller.getSystemBarsAppearance();
        }
    }
}
