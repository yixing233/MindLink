package wex.ui.core.feature;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowInsets;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import wex.ui.core.WexStore;

/** Full ChatFooter card and panel-above layout, ported from WeKit (GPL-3.0-only). */
public final class FloatingChatFooterFeature {
    static final String KEY_ENABLED = "floating_chat_footer_enabled";
    static final String KEY_CORNER = "floating_chat_footer_corner_radius";
    static final String KEY_SIDE = "floating_chat_footer_side_margin";
    static final String KEY_BOTTOM_GAP = "floating_chat_footer_bottom_gap";
    static final String KEY_ELEVATION = "floating_chat_footer_elevation";
    static final String KEY_PANEL_ABOVE = "floating_chat_footer_panel_above";
    private static final String FOOTER = "com.tencent.mm.pluginsdk.ui.chat.ChatFooter";
    private static final String PANEL = "com.tencent.mm.pluginsdk.ui.chat.ChatFooterBottom";
    private static final String APP_PANEL = "com.tencent.mm.pluginsdk.ui.chat.AppPanel";
    private static final String LAYOUT = "com.tencent.mm.pluginsdk.ui.chat.ChattingUILayout";
    private static final String SCROLL = "com.tencent.mm.pluginsdk.ui.chat.ChattingScrollLayout";
    private static final String LIST_HOST = "com.tencent.mm.ui.chatting.view.MMChattingListView";
    private static final String LIST_A = "com.tencent.mm.pluginsdk.ui.tools.ScrollControlRecyclerView";
    private static final String LIST_B = "com.tencent.mm.pluginsdk.ui.tools.ChattingRecyclerView";
    private static final int PANEL_SMILEY = 2, PANEL_APP = 3;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final WeakHashMap<View, WeakReference<State>> STATES = new WeakHashMap<>();
    private static final WeakHashMap<View, WeakReference<State>> SCROLL_STATES = new WeakHashMap<>();
    private static final WeakHashMap<View, Integer> NATURAL_PANEL_HEIGHTS = new WeakHashMap<>();
    private static final WeakHashMap<Object, WeakReference<View>> FULLSCREEN_PANELS = new WeakHashMap<>();
    private static final WeakHashMap<Object, Float> FULLSCREEN_TRANSLATIONS = new WeakHashMap<>();
    private static final Set<Method> HOOKS = new HashSet<>();
    private static Method switchPanelMethod, configPanelMethod, scrollPanelMethod;
    private static Class<?> footerClass;
    private static boolean resizingAppPanel;
    private static Config config = new Config();
    private static boolean initialized;
    private static boolean keyboardHook, scrollHook;
    private static WeakReference<Activity> resumedActivity = new WeakReference<>(null);
    private static Discovery discovery;
    private static final int DISCOVERY_LIMIT = 768, DISCOVERY_ATTEMPTS = 8;
    private static final String LAUNCHER_ACTIVITY = "com.tencent.mm.ui.LauncherUI";
    private static final String CHAT_ACTIVITY = "com.tencent.mm.ui.chatting.ChattingUI";

    private FloatingChatFooterFeature() { }

    public static synchronized void init(ClassLoader loader) throws Exception {
        if (initialized) return;
        config = new Config();
        Class<?> footer = XposedHelpers.findClass(FOOTER, loader);
        footerClass = footer;
        // Register the baseline before any version-dependent method lookup. No optional
        // failure may leave constructors registered twice or prevent resume discovery.
        initialized = true;
        try {
            XposedBridge.hookAllConstructors(footer, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (param.thisObject instanceof View) observe((View) param.thisObject);
                }
            });
        } catch (Throwable error) { unavailable("constructor", error); }
        optionalHook(footer, "onAttachedToWindow", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                if (param.thisObject instanceof View) observe((View) param.thisObject);
            }
        });
        hookActivity(Activity.class);
        for (String name : new String[]{LAUNCHER_ACTIVITY, CHAT_ACTIVITY}) {
            try { hookActivity(XposedHelpers.findClass(name, loader)); }
            catch (Throwable error) { unavailable("activity-class", error); }
        }
        log("stage=baseline registered enabled=" + config.enabled);
        optionalHook(footer, "getYFromBottom", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                if (param.hasThrowable()) return;
                Object result = param.getResult();
                if (result instanceof Integer) param.setResult(state(param.thisObject).yFromBottom((Integer) result));
            }
        });
        try {
            optionalHook(XposedHelpers.findClass(APP_PANEL, loader), "setPortHeighPx", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    if (config.enabled && !resizingAppPanel && param.thisObject instanceof View && param.args[0] instanceof Integer) {
                        int height = (Integer) param.args[0];
                        if (height > 0) NATURAL_PANEL_HEIGHTS.put((View) param.thisObject, height);
                        for (WeakReference<State> reference : new ArrayList<>(STATES.values())) {
                            State state = reference.get();
                            if (state != null) {
                                if (state.appResizeView == param.thisObject) state.appResizeHeight = -1;
                                if (state.overlay != null) state.overlay.nativePortChanged((View) param.thisObject, height);
                            }
                        }
                    }
                }
            }, int.class);
        } catch (Throwable error) { unavailable("app-panel", error); }
        try {
            optionalHook(XposedHelpers.findClass(LAYOUT, loader), "fitSystemWindows", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    if (!config.enabled || !(param.args[0] instanceof Rect)) return;
                    for (WeakReference<State> reference : new ArrayList<>(STATES.values())) {
                        State state = reference.get();
                        if (state != null && state.layout == param.thisObject && state.stripState != null) {
                            state.stripState.beforeFit(state.layout, ((Rect) param.args[0]).bottom);
                        }
                    }
                }
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (!config.enabled) return;
                    for (WeakReference<State> reference : new ArrayList<>(STATES.values())) {
                        State state = reference.get();
                        if (state != null && state.layout == param.thisObject && state.stripState != null) {
                            state.stripState.afterFit(state.layout, !param.hasThrowable());
                        }
                    }
                }
            }, Rect.class);
        } catch (Throwable error) { unavailable("chat-layout", error); }
        try {
            XposedBridge.hookMethod(View.class.getDeclaredMethod("onApplyWindowInsets", WindowInsets.class), new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    if (!(param.thisObject instanceof View) || !(param.args[0] instanceof WindowInsets)) return;
                    WindowInsets rewritten = stripHostNavigation((View) param.thisObject, (WindowInsets) param.args[0]);
                    if (rewritten != param.args[0]) param.args[0] = rewritten;
                }
            });
        } catch (Throwable error) { unavailable("nav-insets", error); }
        resolveHostHooks();
        probeEnable();
    }

    private static void optionalHook(Class<?> type, String name, XC_MethodHook callback, Class<?>... parameters) {
        try { hook(inherited(type, name, parameters), callback); }
        catch (Throwable error) { unavailable(name, error); }
    }

    private static void hookActivity(Class<?> type) {
        XC_MethodHook entered = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                if (!(param.thisObject instanceof Activity) || param.hasThrowable()) return;
                Activity activity = (Activity) param.thisObject;
                if (!chatActivity(activity)) return;
                resumedActivity = new WeakReference<>(activity);
                logEntry(param);
                // Even an inherited base onResume may run before host inflation finishes.
                refresh();
            }
        };
        optionalHook(type, "onResume", entered);
        // A notification tap can deliver the chat page through onNewIntent or onStart
        // only, with no further onResume for that Activity.
        optionalHook(type, "onNewIntent", entered, Intent.class);
        optionalHook(type, "onStart", entered);
        XC_MethodHook stop = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                if (resumedActivity.get() != param.thisObject) return;
                resumedActivity.clear();
                cancelDiscovery();
            }
        };
        optionalHook(type, "onPause", stop);
        optionalHook(type, "onDestroy", stop);
    }

    private static int entryLogs;

    private static void logEntry(XC_MethodHook.MethodHookParam param) {
        if (entryLogs >= 8) return;
        entryLogs++;
        log("stage=entry trigger=" + param.method.getName() + " host=" + param.thisObject.getClass().getName());
    }

    private static boolean chatActivity(Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return false;
        String name = activity.getClass().getName();
        return LAUNCHER_ACTIVITY.equals(name) || CHAT_ACTIVITY.equals(name);
    }

    private static void unavailable(String stage, Throwable error) {
        log("stage=" + stage + " unavailable=" + error.getClass().getSimpleName() + "; native fallback");
    }

    static boolean enabled() { return WexStore.INSTANCE.getBool(KEY_ENABLED, false); }
    static int cornerRadiusDp() { return BubbleSettings.readInt(KEY_CORNER, 24, 0, 32); }
    static int sideMarginDp() { return BubbleSettings.readInt(KEY_SIDE, 12, 0, 32); }
    static int bottomGapDp() { return BubbleSettings.readInt(KEY_BOTTOM_GAP, 8, 0, 24); }
    static int elevationDp() { return BubbleSettings.readInt(KEY_ELEVATION, 4, 0, 16); }
    static boolean panelAbove() { return WexStore.INSTANCE.getBool(KEY_PANEL_ABOVE, true); }

    static void setAppPanelHeight(View app, int height, boolean relayout) throws ReflectiveOperationException {
        boolean previous = resizingAppPanel;
        resizingAppPanel = true;
        try {
            inherited(app.getClass(), "setPortHeighPx", int.class).invoke(app, height);
            if (relayout) inherited(app.getClass(), "y").invoke(app);
        } finally { resizingAppPanel = previous; }
    }

    public static void refresh() {
        MAIN.post(() -> {
            config = new Config();
            for (WeakReference<State> reference : new ArrayList<>(STATES.values())) {
                State state = reference.get();
                if (state != null) { state.watch(); state.apply(); }
            }
            Activity activity = resumedActivity.get();
            if (!config.enabled || !chatActivity(activity)) { cancelDiscovery(); return; }
            if (discovery == null) {
                discovery = new Discovery(activity);
                discovery.schedule();
            }
        });
    }

    private static void cancelDiscovery() {
        if (discovery != null) discovery.cancel();
    }

    /** The config store lives on external media; a notification cold start can read it
     *  before that storage is mounted. Without a bounded re-read the input card stayed
     *  native until the user left the page and re-entered it. */
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

    /** One bounded discovery window per resume/refresh, never scans another Activity. */
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
        void schedule() { MAIN.post(this); }
        void cancel() {
            cancelled = true;
            MAIN.removeCallbacks(this);
            View root = rootRef.get();
            if (root != null) root.removeOnAttachStateChangeListener(this);
            if (discovery == this) discovery = null;
        }
        @Override public void run() {
            Activity activity = activityRef.get(); View root = rootRef.get();
            if (cancelled || !config.enabled || activity != resumedActivity.get() || !chatActivity(activity)
                    || root == null || activity.getWindow() == null || activity.getWindow().getDecorView() != root) { cancel(); return; }
            View found = root.isAttachedToWindow() ? findActiveFooter(root, activity) : null;
            if (found != null) {
                observe(found);
                log("stage=discover found attempts=" + (attempts + 1));
                cancel();
            } else if (++attempts < DISCOVERY_ATTEMPTS) {
                MAIN.postDelayed(this, 120L);
            } else {
                log("stage=discover exhausted budget=" + DISCOVERY_LIMIT);
                cancel();
            }
        }
        @Override public void onViewAttachedToWindow(View view) { }
        @Override public void onViewDetachedFromWindow(View view) { cancel(); }
    }

    private static View findActiveFooter(View root, Activity activity) {
        View found = null;
        ArrayDeque<View> queue = new ArrayDeque<>(); queue.add(root);
        int admitted = 1;
        while (!queue.isEmpty()) {
            View view = queue.removeFirst();
            if (view.getVisibility() != View.VISIBLE || view.getAlpha() <= 0f) continue;
            if (FOOTER.equals(view.getClass().getName())) {
                if (view.isAttachedToWindow() && view.isShown() && ChatToolbarLifecycle.findActivity(view.getContext()) == activity) {
                    if (found != null) return null;
                    found = view;
                }
                // Never inspect input text, panel items or chat message contents.
                continue;
            }
            String name = view.getClass().getName();
            if (LIST_HOST.equals(name) || LIST_A.equals(name) || LIST_B.equals(name)) continue;
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                if (group.getChildCount() > DISCOVERY_LIMIT - admitted) return null;
                for (int i = 0; i < group.getChildCount(); i++) { queue.addLast(group.getChildAt(i)); admitted++; }
            }
        }
        return found;
    }

    private static final class Config {
        final boolean enabled = enabled(), panelAbove = panelAbove();
        final int corner = cornerRadiusDp(), side = sideMarginDp(), gap = bottomGapDp(), elevation = elevationDp();
        final int glass = ChatGlassSurface.mode(), glassBlur = ChatGlassSurface.blurRadiusDp(glass);
    }

    private static void resolveHostHooks() {
        resolve("floating_footer_refresh", new HostTargetResolver.MethodSpec().packages("com.tencent.mm.pluginsdk.ui.chat")
                .returnType("void").usingExactStrings("MicroMsg.ChatFooter", "[refreshBottomHeight] keyborPx:%d"), new FooterHook() {
            @Override protected void after(MethodHookParam param) { state(param.thisObject).apply(); }
        });
        resolve("floating_footer_switch", new HostTargetResolver.MethodSpec().packages("com.tencent.mm.pluginsdk.ui.chat")
                .returnType("void").parameterTypes("int", "boolean", "int")
                .usingExactStrings("MicroMsg.ChatFooter", "switchPanel: %s, %s"), new FooterHook() {
            @Override protected void before(MethodHookParam param) {
                if (!(param.thisObject instanceof View) || !(param.args[0] instanceof Integer)) return;
                state(param.thisObject).beforePanel((Integer) param.args[0]);
            }
            @Override protected void after(MethodHookParam param) {
                if (!(param.thisObject instanceof View) || !(param.args[0] instanceof Integer)) return;
                if (param.hasThrowable()) state(param.thisObject).failedPanel();
                else state(param.thisObject).afterPanel((Integer) param.args[0]);
            }
        });
        resolve("floating_footer_config", new HostTargetResolver.MethodSpec().packages("com.tencent.mm.pluginsdk.ui.chat")
                .returnType("void").parameterTypes("int", "boolean", "int")
                .usingExactStrings("MicroMsg.ChatFooter", "configPanel: %s, %s, %s, %s, %s"), new FooterHook() {
            @Override protected void before(MethodHookParam param) throws Throwable {
                if (!(param.thisObject instanceof View) || !(param.args[0] instanceof Integer)) return;
                State state = state(param.thisObject); int mode = (Integer) param.args[0];
                boolean intercept = state.shouldKeepImeForPanel(mode);
                state.keyboardTrace("keep", intercept, switchPanelMethod != null);
                if (!intercept || switchPanelMethod == null) return;
                // Invoke the native switch once; establish the same visibility even
                // when the direct reflective call bypasses the registered callback.
                state.beforePanel(mode);
                try {
                    switchPanelMethod.invoke(param.thisObject, param.args[0], param.args[1], param.args[2]);
                } catch (Throwable error) {
                    state.failedPanel(); state.traceError("panel-switch", error); unavailable("panel-switch", error); return;
                }
                state.afterPanel(mode); param.setResult(null);
            }
        });
        resolve("floating_footer_keyboard", new HostTargetResolver.MethodSpec().packages("com.tencent.mm.pluginsdk.ui.chat")
                .returnType("void").parameterCount(0)
                .usingExactStrings("MicroMsg.ChatFooter", "isScrolling!! pass this event!"), new FooterHook() {
            @Override protected void before(MethodHookParam param) {
                // This entry also requests the IME for text input. Leave mode1 and
                // its native keyboard request intact, even with a visible upper panel.
                state(param.thisObject).keyboardTrace("enter", false, configPanelMethod != null);
            }
        });
        resolve("floating_footer_fullscreen", new HostTargetResolver.MethodSpec().packages("com.tencent.mm.pluginsdk.ui.chat")
                .returnType("void").parameterCount(0).usingExactStrings("updateFullScreenEdtLayoutHeight:"), new FooterHook() {
            @Override protected void before(MethodHookParam param) {
                View footer = ownerFooter(param.thisObject); State state = state(footer);
                View panel = state.panelForFullscreen();
                if (footer == null || panel == null) return;
                int[] panelLocation = new int[2], footerLocation = new int[2];
                panel.getLocationOnScreen(panelLocation); footer.getLocationOnScreen(footerLocation);
                FULLSCREEN_PANELS.put(param.thisObject, new WeakReference<>(panel));
                FULLSCREEN_TRANSLATIONS.put(param.thisObject, panel.getTranslationY());
                panel.setTranslationY(panel.getTranslationY() + footerLocation[1]
                        + FloatingFooterLayout.siblingsHeight(panel) - panelLocation[1]);
            }
            @Override protected void after(MethodHookParam param) {
                WeakReference<View> reference = FULLSCREEN_PANELS.remove(param.thisObject);
                Float translation = FULLSCREEN_TRANSLATIONS.remove(param.thisObject);
                View panel = reference == null ? null : reference.get();
                if (panel != null && translation != null) panel.setTranslationY(translation);
            }
        });
        resolve("floating_footer_scroll", new HostTargetResolver.MethodSpec().packages("com.tencent.mm.pluginsdk.ui.chat")
                .returnType("void").parameterTypes("int", "boolean", "int", "int")
                .usingExactStrings("MicroMsg.ChattingScrollLayout", "scrollContentTo: y:%s, targetScroll:%s, alwaysScroll:%s"), new FooterHook() {
            @Override protected void before(MethodHookParam param) {
                if (!(param.thisObject instanceof View)) return;
                WeakReference<State> reference = SCROLL_STATES.get((View) param.thisObject);
                State state = reference == null ? null : reference.get();
                if (state != null) state.beforeScroll(param.args);
            }
        });
    }

    /** Wrap only resolved callbacks; diagnostics never create a State or swallow a hook error. */
    private abstract static class FooterHook extends XC_MethodHook {
        String id;
        int ownerlessEvents;
        protected void before(MethodHookParam param) throws Throwable { }
        protected void after(MethodHookParam param) throws Throwable { }
        @Override protected final void beforeHookedMethod(MethodHookParam param) throws Throwable { invoke(param, true); }
        @Override protected final void afterHookedMethod(MethodHookParam param) throws Throwable { invoke(param, false); }
        private void invoke(MethodHookParam param, boolean before) throws Throwable {
            State previous = traceState(param.thisObject);
            int mode = previous == null ? -1 : previous.panelMode, depth = previous == null ? 0 : previous.panelSwitchDepth;
            Throwable callbackError = null;
            try { if (before) before(param); else after(param); }
            catch (Throwable error) { callbackError = error; throw error; }
            finally {
                State current = traceState(param.thisObject);
                if (current != null) current.callbackTrace(id, before, mode, depth, callbackError, param);
                else if (ownerlessEvents++ < 2) {
                    try { log("stage=callback id=" + id + " entered=true state=false phase=" + (before ? "before" : "after")
                            + " callbackError=" + (callbackError == null ? "none" : callbackError.getClass().getSimpleName())
                            + " hostError=" + (param.hasThrowable() ? param.getThrowable().getClass().getSimpleName() : "none")
                            + " beforeMode=" + mode + " afterMode=-1 beforeDepth=" + depth + " afterDepth=0"); }
                    catch (Throwable ignored) { }
                }
            }
        }
    }

    private static State traceState(Object owner) {
        try {
            if (owner instanceof View) {
                State state = existing((View) owner);
                if (state != null) return state;
                WeakReference<State> reference = SCROLL_STATES.get((View) owner);
                if (reference != null && reference.get() != null) return reference.get();
            }
            View footer = ownerFooter(owner);
            return footer == null ? null : existing(footer);
        } catch (Throwable ignored) { return null; }
    }

    private static String methodDescriptor(Method method) {
        StringBuilder result = new StringBuilder(method.getDeclaringClass().getName()).append('#').append(method.getName()).append('(');
        for (Class<?> type : method.getParameterTypes()) result.append(typeDescriptor(type));
        return result.append(')').append(typeDescriptor(method.getReturnType())).toString();
    }

    private static String typeDescriptor(Class<?> type) {
        if (type == void.class) return "V";
        if (type == int.class) return "I";
        if (type == boolean.class) return "Z";
        if (type == byte.class) return "B";
        if (type == short.class) return "S";
        if (type == long.class) return "J";
        if (type == float.class) return "F";
        if (type == double.class) return "D";
        if (type == char.class) return "C";
        return type.isArray() ? type.getName().replace('.', '/') : "L" + type.getName().replace('.', '/') + ";";
    }

    private static void resolve(String id, HostTargetResolver.MethodSpec spec, FooterHook callback) {
        callback.id = id;
        try {
            HostTargetResolver.resolveMethod(id, spec, new HostTargetResolver.MethodCallback() {
                @Override public void onResolved(Method method) {
                    MAIN.post(() -> {
                        try {
                            hook(method, callback);
                            if ("floating_footer_switch".equals(id)) switchPanelMethod = method;
                            if ("floating_footer_config".equals(id)) configPanelMethod = method;
                            if ("floating_footer_keyboard".equals(id)) keyboardHook = true;
                            if ("floating_footer_scroll".equals(id)) { scrollHook = true; scrollPanelMethod = method; }
                            log("stage=resolve id=" + id + " registered=true method=" + methodDescriptor(method));
                        } catch (Throwable error) { unavailable(id, error); }
                    });
                }
                @Override public void onFailure(Throwable error) { unavailable(id, error); }
            });
        } catch (Throwable error) { unavailable(id, error); }
    }

    private static boolean panelHooksReady() {
        return switchPanelMethod != null && configPanelMethod != null && keyboardHook && scrollHook;
    }

    private static void observe(View footer) {
        if (footer == null || !FOOTER.equals(footer.getClass().getName())) return;
        if (Looper.myLooper() != Looper.getMainLooper()) { WeakReference<View> reference = new WeakReference<>(footer); MAIN.post(() -> observe(reference.get())); return; }
        // A raw lookup is essential: state() may call observe() to create a missing state.
        State state = existing(footer);
        if (state == null) {
            state = new State(footer);
            STATES.put(footer, new WeakReference<>(state));
            footer.addOnAttachStateChangeListener(state);
            log("stage=observe registered attached=" + footer.isAttachedToWindow());
        }
        state.watch(); state.traceLoader(footer); state.apply();
        ChatEntrySupport.fromFooter(footer);
    }

    private static State existing(View footer) {
        WeakReference<State> reference = STATES.get(footer);
        return reference == null ? null : reference.get();
    }

    private static State state(Object footer) {
        if (!(footer instanceof View)) return State.EMPTY;
        State state = existing((View) footer);
        if (state == null && FOOTER.equals(footer.getClass().getName())) {
            observe((View) footer);
            state = existing((View) footer);
        }
        return state == null ? State.EMPTY : state;
    }

    private enum FitState { NOT_READY, INPUT_FITS, TOO_SMALL }

    private static final class State implements View.OnAttachStateChangeListener, ViewTreeObserver.OnPreDrawListener {
        static final State EMPTY = new State(null);
        final WeakReference<View> footerRef;
        ViewTreeObserver observer;
        View panel, appPanel, scroll, recycler, layout, appResizeView;
        int appResizeHeight = -1;
        Object[] firstNativeScrollArgs;
        Object[] overlayNativeScrollArgs;
        PanelState panelState;
        ChatFooterOverlay overlay;
        StripState stripState;
        Window edgeWindow, observedWindow;
        Drawable background, surface, owned;
        ViewOutlineProvider outlineProvider;
        boolean clip, captured, applying, panelReady, warned, marginsApplied, styled;
        boolean panelFailed, edgeFailed, panelTransitionCompleted, replayingScroll, loaderTraced;
        String stage;
        int stageLogs, surfaceColor, surfaceCorner = -1, outlineWidth = -1, outlineHeight = -1, outlineCorner = -1;
        private static final int TRACE_LIMIT = 24, TRACE_FRAMES = 3, TRACE_NODES = 8, TRACE_ANCESTORS = 3;
        int traceEvents, traceFrames, traceFrame, traceTransitions;
        String traceReady, traceSnapshot;
        boolean tracedFirstOpen;
        int geometryLogs, geometryMode = Integer.MIN_VALUE, geometryHeight = -1, geometryHost = -1;
        int outlineTop = -1, outlineLeft = -1, outlineRight = -1;
        float elevation;
        ViewGroup.MarginLayoutParams footerParams;
        int baseLeft, baseRight, baseTop, baseBottom, appliedLeft, appliedRight, appliedTop, appliedBottom;
        int bottomInputApplied = Integer.MIN_VALUE, bottomInputLast = Integer.MIN_VALUE;
        int panelFitFailures;
        boolean preDrawPass;
        FitState fitState = FitState.NOT_READY;
        Padding recyclerPadding;
        int naturalPanelHeight, panelMode = -1, panelSwitchDepth;
        int keyboardIme = -1, keyboardRemaining = -1, keyboardLift = -1;
        int traceNatural, traceTarget = -1;
        final ViewOutlineProvider outline = new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline target) {
                Rect bounds = surfaceBounds(view);
                if (bounds.bottom > bounds.top && bounds.right > bounds.left)
                    target.setRoundRect(bounds.left, bounds.top, bounds.right, bounds.bottom,
                            config.corner * view.getResources().getDisplayMetrics().density);
                else target.setEmpty();
                if (overlay != null) target.setAlpha(0f);
            }
        };

        State(View footer) { footerRef = new WeakReference<>(footer); }

        void watch() {
            View footer = footerRef.get();
            if (!config.enabled || footer == null || !footer.isAttachedToWindow()) { unwatch(); return; }
            ViewTreeObserver next = footer.getViewTreeObserver(); if (observer == next && next.isAlive()) return;
            unwatch(); if (next.isAlive()) { observer = next; observer.addOnPreDrawListener(this); }
        }

        @Override public boolean onPreDraw() {
            if (overlay != null) overlay.changed = false;
            preDrawPass = true;
            try { apply(); }
            finally { preDrawPass = false; }
            tracePreDraw();
            return overlay == null || !overlay.changed;
        }

        void apply() {
            View footer = footerRef.get();
            if (footer == null || applying) return;
            if (!config.enabled || !footer.isAttachedToWindow()) { restore(); return; }
            if (!footer.isShown()) return;
            applying = true;
            try {
                capture(footer);
                View nextPanel = ChatFooterOverlay.component(footer, PANEL);
                if (panelState != null) {
                    if (panelState.matches(footer, panel) && (nextPanel == null || nextPanel == panel)) nextPanel = panel;
                    else { panelState.restore(panel); panelState = null; }
                }
                if (panel != nextPanel) naturalPanelHeight = 0;
                panel = nextPanel;
                // Only the current panel owns its AppPanel; never a global tree lookup.
                appPanel = ChatFooterOverlay.component(panel, APP_PANEL);
                if (appPanel != null) {
                    Integer recorded = NATURAL_PANEL_HEIGHTS.get(appPanel);
                    if (recorded != null && recorded > 0) naturalPanelHeight = recorded;
                }
                View nextScroll = ancestor(footer, SCROLL);
                if (scroll != nextScroll) {
                    if (scroll != null) SCROLL_STATES.remove(scroll);
                    restoreRecycler();clearKeyboardLift();
                    bottomInputApplied = Integer.MIN_VALUE; bottomInputLast = Integer.MIN_VALUE;
                    scroll = nextScroll;
                }
                if (scroll != null) SCROLL_STATES.put(scroll, new WeakReference<>(this));
                if (keyboardLift >= 0) {
                    View host = footer.getParent() instanceof View ? (View) footer.getParent() : footer;
                    int ime = imeHeight(footer);
                    if (ime != keyboardIme || FloatingFooterLayout.remainingImeHeight(host, ime) != keyboardRemaining)
                        clearKeyboardLift();
                }
                if (panel != null && panel.getLayoutParams() != null && panel.getLayoutParams().height > 0 && naturalPanelHeight <= 0) naturalPanelHeight = panel.getLayoutParams().height;
                boolean supported = scroll == footer.getParent()
                        && scroll instanceof android.widget.LinearLayout
                        && footer.getLayoutParams() instanceof ViewGroup.MarginLayoutParams;
                fitState = fitState(footer);
                boolean useOverlay = supported && panelHooksReady() && !panelFailed
                        && fitState != FitState.TOO_SMALL && ChatFooterOverlay.input(footer) != null;
                if (useOverlay && overlay == null) overlay = new ChatFooterOverlay(footer);
                if (!useOverlay && overlay != null) releaseOverlay();
                if (fitState == FitState.INPUT_FITS) panelFitFailures = 0;
                else if (fitState == FitState.NOT_READY) panelFitFailures = 0;
                else if (fitState == FitState.TOO_SMALL && panelState != null && preDrawPass) panelFitFailures++;
                // A committed panel must survive transient host measurements. A
                // persistently undersized window still falls back to the native tree.
                boolean retained = panelState != null && panelFitFailures < 2;
                // NOT_READY is "no geometry evidence yet", not "window too small": a
                // native switch that happens before the first layout pass must still
                // commit the migration, otherwise the panel only appears after an
                // extra navigation. Only a measured TOO_SMALL viewport blocks it.
                boolean canAdopt = !retained && panelTransitionCompleted && panelSwitchDepth == 0
                        && fitState != FitState.TOO_SMALL;
                boolean canMove = overlay == null && config.panelAbove && panelHooksReady() && !panelFailed
                        && supported && (retained || canAdopt);
                if (!canMove && panelState != null) { panelState.restore(panel); panelState = null; }
                panelReady = false;
                if (canMove && panel != null) {
                    try {
                        panelReady = ensurePanelAbove(footer, panel);
                        if (panelReady && panelSwitchDepth == 0) syncPanelVisibility();
                    } catch (Throwable error) {
                        panelFailed = true;
                        if (panelState != null) panelState.restore(panel);
                        panelState = null;
                        traceError("panel-layout", error);
                        unavailable("panel-layout", error);
                    }
                }
                tracePanelReady(footer);
                // A missing resolver, panel, recycler or inset helper must not undo the
                // independently valid round card / side margins.
                if (panelReady && panel.getVisibility() == View.VISIBLE) resizePanel(footer, panel);
                bindBottomEdge(footer);
                margins(footer, fitState);
                style(footer);
                diagnose((footer.getLayoutParams() instanceof ViewGroup.MarginLayoutParams ? "styled+margins" : "styled/no-margin-params")
                        + (panelReady ? "/panel-above" : "/native-panel"), footer);
                if (overlay != null) restoreRecycler();
                else if (panelReady) {
                    try {
                        if (panel.getVisibility() == View.VISIBLE) resizePanel(footer, panel);
                        applyRecyclerPadding(footer);
                    } catch (Throwable error) { if (!warned) { warned = true; unavailable("panel-size", error); } }
                } else restoreRecycler();
                diagnoseGeometry(footer);
            } catch (Throwable error) {
                traceError("apply", error);
                if (!warned) { warned = true; unavailable("apply", error); }
                restore();
            } finally { applying = false; }
        }

        private void bindBottomEdge(View footer) {
            View nextLayout = ancestor(footer, LAYOUT);
            Activity activity = ChatToolbarLifecycle.findActivity(footer.getContext());
            Window nextWindow = activity == null ? null : activity.getWindow();
            if (layout != nextLayout || observedWindow != nextWindow) {
                if (stripState != null) stripState.restore(layout);
                stripState = null;
                layout = nextLayout;observedWindow = nextWindow;edgeFailed = false;
                if (edgeWindow != nextWindow || nextLayout == null) {
                    ChatWindowInsetsCoordinator.release(edgeWindow, ChatWindowInsetsCoordinator.BOTTOM);
                    edgeWindow = null;
                }
                clearKeyboardLift();
                bottomInputApplied = Integer.MIN_VALUE; bottomInputLast = Integer.MIN_VALUE;
            }
            if (edgeFailed || layout == null || nextWindow == null) return;
            try {
                if (edgeWindow == null) edgeWindow = ChatWindowInsetsCoordinator.acquire(activity, ChatWindowInsetsCoordinator.BOTTOM);
                else if (ChatWindowInsetsCoordinator.needsBottomReassert(activity)) ChatWindowInsetsCoordinator.reassertBottom(activity);
                if (stripState == null) stripState = StripState.capture(layout);
                stripState.apply(layout);
            } catch (Throwable error) { edgeFailed = true; unavailable("bottom-insets", error); }
        }

        /** A short freeform/split window cannot host the whole floating card; signed
         *  margins would push most of it outside the window and leave a clipped block.
         *  Keep the native tree whenever the floating card cannot fit the host. */
        private FitState fitState(View footer) {
            View host = footer.getParent() instanceof View ? (View) footer.getParent() : null;
            // A host that has never been laid out has no geometry evidence at all;
            // its default bounds are not a short window. Only a real layout may
            // declare the card too small.
            if (host == null || !host.isShown() || !host.isLaidOut()) return FitState.NOT_READY;
            if (host.getHeight() <= 0 || host.getWidth() <= 0) return FitState.NOT_READY;
            Rect visible = new Rect();
            if (!FloatingFooterLayout.contentBounds(host, visible)) return FitState.TOO_SMALL;
            int available = visible.height();
            float density = footer.getResources().getDisplayMetrics().density;
            int gap = Math.round(config.gap * density);
            int side = Math.round(config.side * density);
            // The threshold describes the final card, so reserve the margins
            // that are about to be written. A raw 160dp viewport minus side
            // margins is still too narrow for the floating card.
            if (visible.width() < Math.round(160 * density) + side * 2) return FitState.TOO_SMALL;
            // Ignore the panel: it is compressed independently. Never use footer height
            // here because it can still include the panel that is about to collapse.
            int card = ChatFooterOverlay.input(footer) == null ? FloatingFooterLayout.measuredSiblingsHeight(panel)
                    : new ChatFooterOverlay(footer).inputHeight(panel);
            if (card < 0) return FitState.NOT_READY;
            int remaining = Math.max(0, FloatingFooterLayout.remainingBottom(host));
            remaining = Math.max(remaining, FloatingFooterLayout.pillClearance(host));
            return card + gap + remaining <= available ? FitState.INPUT_FITS : FitState.TOO_SMALL;
        }

        private void traceLoader(View footer) {
            if (loaderTraced || !footer.isAttachedToWindow()) return;
            loaderTraced = true;
            try {
                ClassLoader selected = HostTargetResolver.hostClassLoader();
                Class<?> selectedFooter = selected == null ? null : Class.forName(FOOTER, false, selected);
                log("stage=runtime-loader observedMatchesRegistered=" + (footer.getClass() == footerClass)
                        + " observedMatchesSelected=" + (footer.getClass() == selectedFooter)
                        + " kernelMatched=" + HostDatabaseBridge.matchesRuntimeKernel(selected));
            } catch (Throwable error) { log("stage=runtime-loader unavailable=" + error.getClass().getSimpleName()); }
        }

        private boolean traceAllowed() { return footerRef.get() != null && traceEvents < TRACE_LIMIT; }

        private void trace(String fields) {
            if (!traceAllowed()) return;
            try { log("stage=transition event=" + (++traceEvents) + " transition=" + traceTransitions + " " + fields); }
            catch (Throwable ignored) { } // Evidence must never change host/layout error handling.
        }

        private void traceTransition(String cause) {
            if (!traceAllowed()) return;
            traceTransitions++; traceFrames = TRACE_FRAMES; traceFrame = 0; traceSnapshot = null;
            trace("kind=change cause=" + cause + " mode=" + panelMode + " depth=" + panelSwitchDepth);
        }

        private void callbackTrace(String id, boolean before, int mode, int depth, Throwable error, XC_MethodHook.MethodHookParam param) {
            if (!traceAllowed()) return;
            try {
                trace("kind=callback id=" + id + " phase=" + (before ? "before" : "after") + " entered=true"
                        + " callbackError=" + (error == null ? "none" : error.getClass().getSimpleName())
                        + " hostError=" + (param.hasThrowable() ? param.getThrowable().getClass().getSimpleName() : "none")
                        + " beforeMode=" + mode + " afterMode=" + panelMode + " beforeDepth=" + depth + " afterDepth=" + panelSwitchDepth);
            } catch (Throwable ignored) { }
        }

        private void traceError(String cause, Throwable error) {
            if (!traceAllowed()) return;
            traceTransition(cause);
            trace("kind=error cause=" + cause + " error=" + error.getClass().getSimpleName()
                    + " mode=" + panelMode + " depth=" + panelSwitchDepth);
        }

        private void keyboardTrace(String action, boolean eligible, boolean method) {
            if (!traceAllowed()) return;
            try {
                View footer = footerRef.get();
                trace("kind=keyboard action=" + action + " footer=" + (footer != null) + " enabled=" + config.enabled
                        + " above=" + config.panelAbove + " ready=" + panelReady + " panel=" + (panel != null)
                        + " visible=" + (panel != null && panel.getVisibility() == View.VISIBLE)
                        + " imePresent=" + (imeHeight(footer) > 0) + " eligible=" + eligible
                        + " method=" + method + " intercepted=" + (eligible && method));
            } catch (Throwable ignored) { }
        }

        private void tracePanelReady(View footer) {
            if (!traceAllowed()) return;
            String reason = panelReady ? "ready" : !config.panelAbove ? "disabled" : !panelHooksReady() ? "hooks"
                    : panelFailed ? "failed" : panel == null ? "panelmissing" : !panelTransitionCompleted && panelState == null ? "native-unconfirmed" : "layout";
            if (reason.equals(traceReady)) return;
            traceReady = reason; traceTransition("panelReady");
            trace("kind=panelReady ready=" + panelReady + " reason=" + reason + " switch=" + (switchPanelMethod != null)
                    + " config=" + (configPanelMethod != null) + " keyboard=" + keyboardHook + " scroll=" + scrollHook
                    + " direct=" + (scroll == footer.getParent()) + " linear=" + (scroll instanceof android.widget.LinearLayout)
                    + " marginParams=" + (footer.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) + " failed=" + panelFailed);
        }

        private void tracePreDraw() {
            if (traceFrames <= 0 || !traceAllowed()) return;
            traceFrames--; traceFrame++;
            traceSnapshot("preDraw", traceFrame);
        }

        /** Fixed known nodes only. No children (including AppPanel items) or content getters. */
        private void traceSnapshot(String phase, int frame) {
            if (!traceAllowed()) return;
            try {
                View footer = footerRef.get();
                List<View> nodes = new ArrayList<>(); List<String> roles = new ArrayList<>();
                traceNode(nodes, roles, footer, "footer"); traceNode(nodes, roles, panel, "panel");
                traceNode(nodes, roles, appPanel, "app"); traceNode(nodes, roles, scroll, "scroll");
                traceNode(nodes, roles, layout, "layout");
                View current = footer.getParent() instanceof View ? (View) footer.getParent() : null;
                int ancestors = 0;
                for (; ancestors < TRACE_ANCESTORS && current != null; ancestors++) {
                    traceNode(nodes, roles, current, "ancestor" + ancestors);
                    current = current.getParent() instanceof View ? (View) current.getParent() : null;
                }
                StringBuilder fields = new StringBuilder("mode=").append(panelMode).append(" depth=").append(panelSwitchDepth)
                        .append(" ready=").append(panelReady).append(" failed=").append(panelFailed)
                        .append(" panelState=").append(panelState != null).append(" moved=").append(panelState != null && panelState.moved)
                        .append(" nativeConfirmed=").append(panelTransitionCompleted)
                        .append(" panelParentRole=").append(panel == null ? "none" : panel.getParent() == footer ? "footer"
                                : panelState != null && panel.getParent() == panelState.ownedRoot.get() ? "owned-root"
                                : panelState != null && panel.getParent() == panelState.parent.get() ? "original" : "other")
                        .append(" ownedRootInsideFooter=").append(panelState != null && panelState.ownedRoot.get() != null && descendant(footer, panelState.ownedRoot.get()))
                        .append(" originalInsideFooter=").append(panelState != null && panelState.parent.get() != null && descendant(footer, panelState.parent.get()))
                        .append(" visibilityOwned=").append(panelState != null && panelState.visibilityOwned)
                        .append(" heightOwned=").append(panelState != null && panelState.heightOwned)
                        .append(" appExists=").append(appPanel != null).append(" natural=").append(naturalPanelHeight)
                        .append(" resizeNatural=").append(traceNatural).append(" target=").append(traceTarget)
                        .append(" ime=").append(imeHeight(footer)).append(" baseMargins=").append(baseLeft).append(',').append(baseTop).append(',').append(baseRight).append(',').append(baseBottom)
                        .append(" appliedMargins=").append(appliedLeft).append(',').append(appliedTop).append(',').append(appliedRight).append(',').append(appliedBottom)
                        .append(" outlineOwned=").append(footer.getOutlineProvider() == outline).append(" backgroundOwned=").append(footer.getBackground() == surface)
                        .append(" outlineBounds=").append(outlineLeft).append(',').append(outlineTop).append(',').append(outlineRight).append(',').append(outlineHeight)
                        .append(" nodeCount=").append(nodes.size()).append(" ancestorCount=").append(ancestors);
                for (int i = 0; i < nodes.size(); i++) {
                    View node = nodes.get(i); ViewGroup.LayoutParams lp = node.getLayoutParams();
                    fields.append(" node").append(i).append('=').append(roles.get(i))
                            .append("{attached=").append(node.isAttachedToWindow()).append(",shown=").append(node.isShown())
                            .append(",visibility=").append(node.getVisibility()).append(",childCount=").append(node instanceof ViewGroup ? ((ViewGroup) node).getChildCount() : 0)
                            .append(",lp=").append(lp == null ? "none" : lp instanceof android.widget.RelativeLayout.LayoutParams ? "relative" : lp instanceof ViewGroup.MarginLayoutParams ? "margin" : "plain")
                            .append(",lpSize=").append(lp == null ? 0 : lp.width).append(',').append(lp == null ? 0 : lp.height)
                            .append(",measured=").append(node.getMeasuredWidth()).append(',').append(node.getMeasuredHeight())
                            .append(",size=").append(node.getWidth()).append(',').append(node.getHeight())
                            .append(",translationY=").append(node.getTranslationY()).append(",clipOutline=").append(node.getClipToOutline());
                    if (lp instanceof ViewGroup.MarginLayoutParams) {
                        ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) lp;
                        fields.append(",margins=").append(margins.leftMargin).append(',').append(margins.topMargin).append(',').append(margins.rightMargin).append(',').append(margins.bottomMargin);
                    }
                    fields.append('}');
                }
                String snapshot = fields.toString();
                if (snapshot.equals(traceSnapshot)) return;
                traceSnapshot = snapshot;
                trace("kind=snapshot phase=" + phase + " frame=" + frame + " " + snapshot);
            } catch (Throwable error) {
                trace("kind=error cause=snapshot error=" + error.getClass().getSimpleName());
            }
        }

        private void traceNode(List<View> nodes, List<String> roles, View node, String role) {
            if (node != null && nodes.size() < TRACE_NODES && !nodes.contains(node)) { nodes.add(node); roles.add(role); }
        }

        private void diagnose(String next, View footer) {
            if (next.equals(stage)) return;
            stage = next;
            traceTransition("stage");
            if (stageLogs++ < 6) log("stage=" + next + " parent="
                    + (footer.getParent() == null ? "none" : footer.getParent().getClass().getSimpleName()));
        }

        private void diagnoseGeometry(View footer) {
            View host = footer.getParent() instanceof View ? (View) footer.getParent() : null;
            if (host == null || geometryLogs >= 6 || (geometryMode == panelMode && geometryHeight == footer.getHeight() && geometryHost == host.getHeight())) return;
            geometryMode=panelMode;geometryHeight=footer.getHeight();geometryHost=host.getHeight();geometryLogs++;
            // Wallpaper owner is not proven on this host. Bounded geometry evidence
            // only: no identifiers, view text, resource names, paths or content.
            Rect visible = FloatingFooterLayout.visibleFooterBounds(footer);
            android.view.WindowInsets insets = footer.getRootWindowInsets();
            int nav = insets == null ? 0 : insets.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom;
            int ime = insets == null ? 0 : insets.getInsets(android.view.WindowInsets.Type.ime()).bottom;
            Rect hostBounds = new Rect(); FloatingFooterLayout.contentBounds(host, hostBounds);
            int[] cardLocation = new int[2]; footer.getLocationOnScreen(cardLocation);
            int cardBottom = cardLocation[1] + visible.bottom;
            // The card is anchored to the host viewport minus its configured gap; the
            // navigation inset is only relevant for the part the host has not already
            // avoided. Comparing against the raw nav inset reported a fake delta.
            int remaining = Math.max(0, FloatingFooterLayout.remainingBottom(host));
            int clearance = panelMode == PANEL_SMILEY ? 0 : FloatingFooterLayout.pillClearance(host);
            int correction = FloatingFooterLayout.bottomCorrection(host);
            int targetBottom = hostBounds.bottom - Math.max(remaining, clearance) - Math.round(config.gap * footer.getResources().getDisplayMetrics().density);
            log("stage=geometry wallpaperOwner=unproven class="+host.getClass().getName()+" mode="+panelMode
                    +" card="+footer.getHeight()+" visibleTop="+visible.top+" visibleBottom="+visible.bottom
                    +" host="+host.getHeight()+" marginTop="+appliedTop+" marginBottom="+appliedBottom
                    +" nav="+nav+" navRemain="+remaining+" pillClear="+clearance+" correction="+correction+" ime="+ime+" hostVisible="+(hostBounds.bottom-hostBounds.top)
                    +" hostWidth="+host.getWidth()+" visibleWidth="+hostBounds.width()
                    +" minimumWidth="+Math.round((160+config.side*2)*footer.getResources().getDisplayMetrics().density)
                    +" inputBand="+new ChatFooterOverlay(footer).inputHeight(panel)+" overlay="+(overlay!=null)
                    +" cardBottom="+cardBottom+" layoutBottom="+(cardLocation[1]+footer.getHeight())
                    +" targetBottom="+targetBottom+" delta="+(cardBottom-targetBottom)
                    +" fit="+fitState.name());
            View current=host;
            for(int i=0;i<3&&current!=null;i++) {
                int[] location=new int[2];current.getLocationOnScreen(location);Rect bounds=new Rect();boolean shown=current.getGlobalVisibleRect(bounds);
                log("stage=geometry-ancestor depth="+i+" class="+current.getClass().getName()+" top="+location[1]
                        +" height="+current.getHeight()+" visible="+(shown?1:0)+" bottom="+bounds.bottom
                        +" clipChildren="+(current instanceof ViewGroup&&((ViewGroup)current).getClipChildren()?1:0)
                        +" clipPadding="+(current instanceof ViewGroup&&((ViewGroup)current).getClipToPadding()?1:0));
                current=current.getParent() instanceof View?(View)current.getParent():null;
            }
        }

        private void capture(View footer) {
            if (!captured) {
                background = footer.getBackground(); outlineProvider = footer.getOutlineProvider(); clip = footer.getClipToOutline(); elevation = footer.getElevation();
                captured = true;
            }
            // Constructors can run before any LayoutParams exist; capture on arrival
            // and rebase when the host replaces the params object.
            if (footer.getLayoutParams() instanceof ViewGroup.MarginLayoutParams && footer.getLayoutParams() != footerParams) {
                footerParams = (ViewGroup.MarginLayoutParams) footer.getLayoutParams();
                baseLeft=footerParams.leftMargin;baseRight=footerParams.rightMargin;baseTop=footerParams.topMargin;baseBottom=footerParams.bottomMargin;
                marginsApplied = false;
            }
        }

        private void style(View footer) {
            int color = SettingsTheme.isDark(footer.getContext()) ? 0xFF252825 : 0xFFF7F7F7;
            float density = footer.getResources().getDisplayMetrics().density;
            int radius = Math.round(config.corner * density);
            boolean glass = config.glass != ChatGlassSurface.PLAIN;
            Drawable current = footer.getBackground();
            if (glass) {
                // The card keeps its elevation/algebra; only the fill becomes the home-bar material.
                if (current instanceof ChatGlassSurface) surface = current;
                else {
                    // A host theme change owns its own background; never freeze it as our snapshot.
                    if (styled && current != owned) background = current;
                    surface = new ChatGlassSurface(footer);
                    surfaceColor = 0; surfaceCorner = -1;
                }
                ((ChatGlassSurface) surface).configure(color, radius, density,
                        SettingsTheme.isDark(footer.getContext()), config.glass, config.glassBlur);
                ((ChatGlassSurface) surface).visibleHeight(overlay != null && overlay.applied != null
                        ? overlay.applied.visible : -1);
            } else {
                if (surface instanceof ChatGlassSurface) { ((ChatGlassSurface) surface).stop(); surface = null; surfaceCorner = -1; }
                if (!(surface instanceof GradientDrawable)) { surface = new GradientDrawable(); surfaceCorner = -1; }
                GradientDrawable drawable = (GradientDrawable) surface;
                if (surfaceColor != color || surfaceCorner < 0) { drawable.setColor(color); surfaceColor = color; }
                if (surfaceCorner != radius) { drawable.setCornerRadius(radius); surfaceCorner = radius; }
            }
            if (footer.getBackground() != surface) {
                // Host theme changes belong to the host, not to our first-ever snapshot.
                // Our own previous surface is never evidence of a host theme change: a
                // glass/plain switch must stay restorable to the native background.
                if (styled && current != owned) background = current;
                footer.setBackground(surface);
                owned = surface;
            }
            float targetElevation = config.elevation * footer.getResources().getDisplayMetrics().density;
            if (footer.getElevation() != targetElevation) footer.setElevation(targetElevation);
            Rect bounds = surfaceBounds(footer);
            boolean validOutline = bounds.right > bounds.left && bounds.bottom > bounds.top;
            if (validOutline) {
                if (footer.getOutlineProvider() != outline) {
                    if (styled) { outlineProvider = footer.getOutlineProvider(); clip = footer.getClipToOutline(); elevation = footer.getElevation(); }
                    footer.setOutlineProvider(outline);
                }
                if (!footer.getClipToOutline()) footer.setClipToOutline(true);
            } else if (footer.getOutlineProvider() == outline) {
                // An empty software outline plus clipToOutline erases the whole
                // footer. Never let a transient off-screen measurement hide it.
                footer.setOutlineProvider(outlineProvider);
                footer.setClipToOutline(clip);
                footer.invalidateOutline();
            }
            int height = bounds.bottom;
            if (outlineWidth != footer.getWidth() || outlineHeight != height || outlineCorner != radius
                    || outlineTop != bounds.top || outlineLeft != bounds.left || outlineRight != bounds.right) {
                outlineWidth = footer.getWidth(); outlineHeight = height; outlineCorner = radius;
                outlineTop=bounds.top;outlineLeft=bounds.left;outlineRight=bounds.right;footer.invalidateOutline();
            }
            styled = true;
        }

        private Rect surfaceBounds(View footer) {
            if (overlay != null && overlay.applied != null) {
                Rect bounds = new Rect();
                bounds.right = footer.getWidth(); bounds.bottom = overlay.applied.visible;
                return bounds;
            }
            return FloatingFooterLayout.visibleFooterBounds(footer);
        }

        private void margins(View footer, FitState fitState) {
            if (!(footer.getLayoutParams() instanceof ViewGroup.MarginLayoutParams)) return;
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) footer.getLayoutParams();
            if (marginsApplied) {
                if (params.leftMargin != appliedLeft) baseLeft = params.leftMargin;
                if (params.rightMargin != appliedRight) baseRight = params.rightMargin;
                if (params.topMargin != appliedTop) baseTop = params.topMargin;
                if (params.bottomMargin != appliedBottom) baseBottom = params.bottomMargin;
            }
            if (overlay != null) {
                overlay.apply(panelMode, panelSwitchDepth, naturalPanelHeight,
                        config.gap, config.side, preDrawPass);
                if (overlay.applied != null) {
                    appliedLeft = params.leftMargin; appliedRight = params.rightMargin;
                    appliedTop = params.topMargin; appliedBottom = params.bottomMargin;
                    marginsApplied = true;
                }
                return;
            }
            float density = footer.getResources().getDisplayMetrics().density;
            int side = Math.round(config.side * density), gap = Math.round(config.gap * density);
            View host = footer.getParent() instanceof View ? (View) footer.getParent() : footer;
            Rect hostBounds = new Rect();
            int visibleLeft = 0, visibleRight = host.getWidth();
            boolean hostVisible = FloatingFooterLayout.contentBounds(host, hostBounds);
            if (hostVisible) {
                int[] hostLocation = new int[2];
                host.getLocationOnScreen(hostLocation);
                visibleLeft = Math.max(0, hostBounds.left - hostLocation[0]);
                visibleRight = Math.min(host.getWidth(), hostBounds.right - hostLocation[0]);
            }
            int clippedLeft = Math.max(0, visibleLeft - host.getPaddingLeft());
            int clippedRight = Math.max(0, host.getWidth() - host.getPaddingRight() - visibleRight);
            // A narrow freeform window must never be dressed down to a sliver: the
            // rounding margins alone can consume most of the host width. Drop the
            // decorative side margin first, then the ancestor clipping, and keep the
            // card at the widest width the window can still show.
            int availableWidth = host.getWidth() - host.getPaddingLeft() - host.getPaddingRight();
            int floorWidth = Math.round(140 * density);
            int extraLeft = side + clippedLeft, extraRight = side + clippedRight;
            if (availableWidth - baseLeft - baseRight - extraLeft - extraRight < floorWidth) {
                extraLeft = clippedLeft; extraRight = clippedRight;
                if (availableWidth - baseLeft - baseRight - extraLeft - extraRight < floorWidth) {
                    extraLeft = 0; extraRight = 0;
                }
            }
            if (fitState == FitState.TOO_SMALL && panelState == null) {
                // A short/narrow freeform window keeps the native tree. Writing
                // floating margins here is what clips the card down to a sliver.
                if (marginsApplied) {
                    params.leftMargin = baseLeft; params.rightMargin = baseRight;
                    params.topMargin = baseTop; params.bottomMargin = baseBottom;
                    marginsApplied = false;
                    footer.setLayoutParams(params);
                }
                return;
            }
            int remaining = 0;
            try {
                remaining = FloatingFooterLayout.remainingBottom(host);
                // The pill band is owned by the card: the host's own navigation padding
                // was stripped so its content can run edge to edge, therefore that
                // padding must not be counted as avoidance. Without this floor the closed
                // keyboard and the "+" panel both parked the card on the gesture pill,
                // while the smiley panel was re-fitted by its AppPanel resize.
                // The smiley panel is the accepted baseline and is re-fitted by the
                // native AppPanel resize, so its measurement is never overridden here.
                int clearance = panelMode == PANEL_SMILEY ? 0 : FloatingFooterLayout.pillClearance(host);
                if (clearance > remaining) remaining = clearance;
            }
            catch (Throwable ignored) { }
            int bottom, top;
            if (panelReady) {
                if (fitState == FitState.NOT_READY) return;
                int raw = remaining - FloatingFooterLayout.bottomCorrection(host);
                // Host refits (a restored native bottom mask, a re-fitted window) can
                // make this measurement jump between two values on alternating preDraw
                // frames. Rewriting margins for every one-frame jump keeps the card
                // bouncing: each write refits the host, and the refit changes the next
                // measurement. Adopt a changed measurement only once it repeats on the
                // next apply; a single-frame jump holds the applied margins.
                int measured = raw;
                if (bottomInputApplied != Integer.MIN_VALUE && raw != bottomInputApplied && raw != bottomInputLast)
                    measured = bottomInputApplied;
                if (bottomInputApplied == Integer.MIN_VALUE || measured == bottomInputLast || measured == bottomInputApplied)
                    bottomInputApplied = measured;
                bottomInputLast = raw;
                bottom = gap + measured;
                // Signed margins cancel the entire naturally measured card. Keep
                // net contribution zero, including a clipped ancestor's bottom
                // deficit, so weighted content keeps its real viewport as well.
                // LinearLayout positions this child by its actual laid-out height.
                // Cancelling a requested/measured value here leaves a stale card
                // shifted by the measurement delta after a panel resize.
                int card = Math.max(0, footer.getHeight());
                if (card <= 0) return;
                top = -(card + bottom);
            } else {
                // Keep native negative panel margins/translation when any panel hook or
                // structure is unavailable; correct only the measured ancestor deficit.
                bottom = baseBottom + gap + remaining - FloatingFooterLayout.bottomCorrection(host); top = baseTop;
            }
            appliedLeft=baseLeft+extraLeft;appliedRight=baseRight+extraRight;appliedTop=top;appliedBottom=bottom;
            marginsApplied = true;
            if(params.leftMargin!=appliedLeft||params.rightMargin!=appliedRight||params.topMargin!=top||params.bottomMargin!=bottom){params.leftMargin=appliedLeft;params.rightMargin=appliedRight;params.topMargin=top;params.bottomMargin=bottom;footer.setLayoutParams(params);}
        }

        private boolean ensurePanelAbove(View footer, View panel) {
            if (panelState == null) panelState = PanelState.capture(panel);
            if (panelState == null) return false;
            return panelState.reparent(footer, panel);
        }

        private void resizePanel(View footer, View panel) {
            int natural = naturalPanelHeight > 0 ? naturalPanelHeight : keyboardHeight(footer);
            if (natural <= 0 && panel.getLayoutParams() != null) natural = panel.getLayoutParams().height;
            traceNatural = natural; traceTarget = -1;
            if (natural <= 0) return;
            int ime = imeHeight(footer);
            int limit = FloatingFooterLayout.maxPanelHeight(footer, panel, ime,
                    Math.round(120 * footer.getResources().getDisplayMetrics().density) + Math.round(config.gap * footer.getResources().getDisplayMetrics().density), Math.round(160 * footer.getResources().getDisplayMetrics().density));
            if (limit < 0) return; // Defer until host and natural input bounds are measured.
            int target = FloatingFooterGeometry.targetPanelHeight(natural, 0, limit);
            traceTarget = target;
            panelState.resize(panel, target);
            resizeAppPanel(natural, target);
        }

        private void resizeAppPanel(int natural, int target) {
            if (appPanel == null || !appPanel.isAttachedToWindow() || !descendant(panel, appPanel)
                    || appResizeView == appPanel && appResizeHeight == target) return;
            try {
                resizingAppPanel = true;
                Method setter = inherited(appPanel.getClass(), "setPortHeighPx", int.class);
                setter.invoke(appPanel, target);
                appResizeView = appPanel; appResizeHeight = target;
            } catch (Throwable error) {
                if (!warned) { warned = true; log("AppPanel 内容高度同步失败 " + error.getClass().getSimpleName()); }
            } finally { resizingAppPanel = false; }
            // AppPanel's confirmed native setter owns its contents. Equal-height
            // descendants are not evidence of ownership (pages/grids may match).

        }

        void beforePanel(int mode) {
            if (panelSwitchDepth == 0 && !panelReady) firstNativeScrollArgs = null;
            panelMode = mode; panelSwitchDepth++;
            if (!panelOpened()) overlayNativeScrollArgs = null;
            if (overlay != null) overlay.transition(mode);
            traceTransition("beforePanel");
            View footer = footerRef.get();
            if (footer == null || !config.enabled || !config.panelAbove) return;
            try {
                apply();
                // Native switchPanel closes 0/1/4 by negative margins rather than
                // GONE. Its own scroll call must see the collapsed visibility.
                if (panelReady && panel != null) {
                    if (!panelOpened()) panel.setVisibility(View.GONE);
                    else resizePanel(footer, panel);
                }
            } catch (Throwable error) { panelUnavailable(error); }
        }

        void afterPanel(int mode) {
            panelMode = mode; panelTransitionCompleted = true;
            if (panelSwitchDepth > 0) panelSwitchDepth--;
            traceTransition("afterPanel");
            View footer = footerRef.get(); if (footer == null) return;
            boolean wasReady = panelReady;
            try {
                apply();
                Object[] nativeScrollArgs = firstNativeScrollArgs; firstNativeScrollArgs = null;
                if (!wasReady && panelReady && !replayingScroll && nativeScrollArgs != null
                        && scrollPanelMethod != null && scrollPanelMethod.getDeclaringClass().isInstance(scroll)) {
                    // Replay only a witnessed native first-switch scroll. Keep all
                    // native animation/control flags and replace only panel lift.
                    nativeScrollArgs[0] = FloatingFooterLayout.remainingImeHeight(scroll, imeHeight(footer));
                    replayingScroll = true;
                    try { scrollPanelMethod.invoke(scroll, nativeScrollArgs); }
                    finally { replayingScroll = false; }
                }
                if (config.enabled && config.panelAbove && panelReady && panel != null) {
                    syncPanelVisibility();
                    if (panelOpened()) resizePanel(footer, panel);
                }
                if (panelOpened() && !tracedFirstOpen && traceAllowed()) {
                    tracedFirstOpen = true; traceSnapshot("firstOpen", 0);
                }
            } catch (Throwable error) { panelUnavailable(error); }
        }

        void failedPanel() {
            int beforeMode = panelMode, beforeDepth = panelSwitchDepth;
            if (panelSwitchDepth > 0) panelSwitchDepth--;
            // A failed host transition is not evidence that an open mode completed.
            panelMode = -1; panelTransitionCompleted = false; firstNativeScrollArgs = null;
            if (config.enabled && panelReady && panel != null) panel.setVisibility(View.GONE);
            traceTransition("failedPanel");
            trace("kind=failedPanel beforeMode=" + beforeMode + " afterMode=" + panelMode
                    + " beforeDepth=" + beforeDepth + " afterDepth=" + panelSwitchDepth + " panelFailed=" + panelFailed);
        }

        private boolean panelOpened() { return panelMode == PANEL_SMILEY || panelMode == PANEL_APP; }
        private void syncPanelVisibility() {
            // VISIBLE alone cannot identify native open state: the closed panel is
            // also VISIBLE off-screen. Unknown/late-enable starts collapsed safely.
            int target = panelOpened() ? View.VISIBLE : View.GONE;
            if (panel.getVisibility() != target) panel.setVisibility(target);
        }
        private void panelUnavailable(Throwable error) {
            panelFailed = true; panelReady = false;
            traceError("panelFailed", error);
            if (panelState != null && panel != null) panelState.restore(panel);
            panelState = null; unavailable("panel-state", error); apply();
        }
        boolean shouldKeepImeForPanel(int mode) {
            View footer=footerRef.get(); return footer!=null&&config.enabled&&config.panelAbove&&(panelReady||overlay!=null)
                    &&(mode==PANEL_SMILEY||mode==PANEL_APP)&&imeHeight(footer)>0;
        }
        int yFromBottom(int original) {
            return FloatingFooterGeometry.yFromBottomWithoutPanel(original, config.enabled&&config.panelAbove&&(panelReady||overlay!=null)
                    &&panel!=null&&panel.getVisibility()==View.VISIBLE, panelHeight(panel));
        }
        View panelForFullscreen() {
            return config.enabled&&config.panelAbove&&(panelReady||overlay!=null) ? panel : null;
        }
        void beforeScroll(Object[] args) {
            if (config.enabled && overlay != null && panelOpened()
                    && args != null && args.length > 0 && args[0] instanceof Integer) {
                if (args.length == 4 && args[1] instanceof Boolean && args[2] instanceof Integer
                        && args[3] instanceof Integer && (Integer) args[0] > 0)
                    overlayNativeScrollArgs = args.clone();
                args[0] = 0;
                return;
            }
            if (config.enabled && config.panelAbove && !panelReady && panelSwitchDepth > 0 && !replayingScroll
                    && args != null && args.length == 4 && args[0] instanceof Integer && args[1] instanceof Boolean
                    && args[2] instanceof Integer && args[3] instanceof Integer) firstNativeScrollArgs = args.clone();
            if (!config.enabled || !config.panelAbove || !panelReady || panel == null
                    || args == null || args.length == 0 || !(args[0] instanceof Integer)) return;
            View footer = footerRef.get(); if (footer == null) return;
            int requested = (Integer) args[0], ime = imeHeight(footer);
            boolean visible = panel.getVisibility() == View.VISIBLE;
            if (ime <= 0) {
                clearKeyboardLift();
                if (visible) args[0] = 0;
                return;
            }
            try {
                View host = footer.getParent() instanceof View ? (View) footer.getParent() : footer;
                int remaining = FloatingFooterLayout.remainingImeHeight(host, ime);
                int lift;
                if (!visible) {
                    lift = Math.max(0, Math.min(requested, remaining));
                    keyboardIme = ime; keyboardRemaining = remaining; keyboardLift = lift;
                } else {
                    // Keep the actual native lift across keyboard-to-panel transitions.
                    // A changed IME or resize/padding budget invalidates that measurement.
                    if (keyboardIme != ime || keyboardRemaining != remaining) clearKeyboardLift();
                    lift = keyboardLift >= 0 ? Math.min(keyboardLift, remaining) : remaining;
                }
                args[0] = lift;
            } catch (Throwable error) { unavailable("panel-scroll", error); }
        }

        private void clearKeyboardLift() { keyboardIme = -1; keyboardRemaining = -1; keyboardLift = -1; }

        private void applyRecyclerPadding(View footer) {
            View current=findRecycler(scroll);if(current==null)return;
            if(recycler!=current||recyclerPadding==null){restoreRecycler();recycler=current;recyclerPadding=Padding.of(current);}
            // The floating card overlays the message surface instead of owning a layout
            // slot. No extra bottom padding: the list viewport remains full-height so
            // messages pass beneath the card and the glass can sample them.
            int extra=0;
            int target=recyclerPadding.b+extra;
            if(current.getPaddingBottom()!=target){boolean atBottom=!current.canScrollVertically(1);int old=current.getPaddingBottom();current.setPadding(current.getPaddingLeft(),current.getPaddingTop(),current.getPaddingRight(),target);if(current instanceof ViewGroup)((ViewGroup)current).setClipToPadding(false);if(atBottom&&target>old)current.scrollBy(0,target-old);}
        }

        void restore() {
            View footer = footerRef.get();
            if (overlay != null) releaseOverlay();
            restoreRecycler();
            if (panelState != null && panel != null) panelState.restore(panel);
            panelState = null; panelReady = false;
            if (captured && footer != null) {
                if (surface instanceof ChatGlassSurface) ((ChatGlassSurface) surface).stop();
                if (footer.getBackground() == surface) footer.setBackground(background);
                if (footer.getOutlineProvider() == outline) {
                    footer.setOutlineProvider(outlineProvider); footer.setClipToOutline(clip); footer.setElevation(elevation);
                }
                if (marginsApplied && footer.getLayoutParams() == footerParams && footerParams != null) {
                    if (footerParams.leftMargin == appliedLeft) footerParams.leftMargin = baseLeft;
                    if (footerParams.rightMargin == appliedRight) footerParams.rightMargin = baseRight;
                    if (footerParams.topMargin == appliedTop) footerParams.topMargin = baseTop;
                    if (footerParams.bottomMargin == appliedBottom) footerParams.bottomMargin = baseBottom;
                    footer.setLayoutParams(footerParams);
                }
            }
            if (scroll != null) SCROLL_STATES.remove(scroll);
            if (stripState != null) stripState.restore(layout);
            stripState = null; layout = null; scroll = null;
            ChatWindowInsetsCoordinator.release(edgeWindow, ChatWindowInsetsCoordinator.BOTTOM); edgeWindow = null; observedWindow = null;
            surface = null; background = null; outlineProvider = null; captured = false; styled = false;
            owned = null; surfaceColor = 0; surfaceCorner = -1;
            footerParams = null; marginsApplied = false;
            bottomInputApplied = Integer.MIN_VALUE; bottomInputLast = Integer.MIN_VALUE;
            clearKeyboardLift();
            outlineWidth = -1; outlineHeight = -1; outlineCorner = -1;
            panel = null; appPanel = null; naturalPanelHeight = 0; appResizeView = null; appResizeHeight = -1; firstNativeScrollArgs = null;
            panelFitFailures = 0; fitState = FitState.NOT_READY;
        }
        private void releaseOverlay() {
            ChatFooterOverlay previous = overlay; overlay = null;
            previous.restore();
            Object[] nativeArgs = overlayNativeScrollArgs; overlayNativeScrollArgs = null;
            View footer = footerRef.get();
            if (nativeArgs == null || !panelOpened() || footer == null || !footer.isAttachedToWindow() || !footer.isShown()
                    || footer.getParent() != scroll || scrollPanelMethod == null
                    || !scrollPanelMethod.getDeclaringClass().isInstance(scroll)) return;
            try {
                replayingScroll = true;
                scrollPanelMethod.invoke(scroll, nativeArgs);
            } catch (ReflectiveOperationException error) { unavailable("overlay-native-scroll", error); }
            finally { replayingScroll = false; }
        }
        private void restoreRecycler(){if(recycler!=null&&recyclerPadding!=null)recyclerPadding.restore(recycler);recycler=null;recyclerPadding=null;}
        private void unwatch(){if(observer!=null&&observer.isAlive())observer.removeOnPreDrawListener(this);observer=null;}
        @Override public void onViewAttachedToWindow(View view){watch();traceLoader(view);apply();}
        @Override public void onViewDetachedFromWindow(View view){
            unwatch(); restore();
            if (discovery != null && discovery.activityRef.get() == ChatToolbarLifecycle.findActivity(view.getContext())) cancelDiscovery();
            // Keep the weak map entry while the footer owns this listener. Removing it
            // here would install a second State/listener on every reattach.
        }
    }

    private static final class PanelState {
        final WeakReference<ViewGroup> parent; final int index; final ViewGroup.LayoutParams params;
        WeakReference<ViewGroup> ownedRoot = new WeakReference<>(null);
        final int visibility;
        ViewGroup.LayoutParams resizedParams; int nativeHeight, appliedHeight; boolean heightOwned;
        final List<RuleState> rules=new ArrayList<>(); boolean moved, visibilityOwned;
        PanelState(ViewGroup p,int i,ViewGroup.LayoutParams lp, int visibility){parent=new WeakReference<>(p);index=i;params=lp;this.visibility=visibility;}
        static PanelState capture(View panel){if(!(panel.getParent() instanceof ViewGroup))return null;ViewGroup p=(ViewGroup)panel.getParent();return new PanelState(p,p.indexOfChild(panel),panel.getLayoutParams(),panel.getVisibility());}
        boolean matches(View footer, View panel) {
            ViewGroup original = parent.get(), root = ownedRoot.get();
            return footer != null && panel != null && footer.isAttachedToWindow() && panel.isAttachedToWindow()
                    && original != null && original.isAttachedToWindow() && descendant(footer, original)
                    && (moved ? root != null && root.isAttachedToWindow() && panel.getParent() == root
                            && original.getParent() == root && descendant(footer, root) : panel.getParent() == original);
        }
        boolean reparent(View footer, View panel) {
            if (moved) return matches(footer, panel);
            ViewGroup original = parent.get();
            if (original == null || !descendant(footer, original)) return false;
            if (original.getParent() == footer) {
                if (!visibilityOwned) { visibilityOwned = true; panel.setVisibility(View.GONE); }
                return true;
            }
            if (!(original.getParent() instanceof android.widget.RelativeLayout)) return false;
            android.widget.RelativeLayout root = (android.widget.RelativeLayout) original.getParent();
            if (!descendant(footer, root)) return false;
            if (panel.getId() == View.NO_ID) panel.setId(View.generateViewId());
            int height = params == null ? ViewGroup.LayoutParams.WRAP_CONTENT : params.height;
            ownedRoot = new WeakReference<>(root);
            moved = true; // Restore even if a host removeView/addView implementation throws.
            original.removeView(panel);
            // Native collapsed panels stay VISIBLE behind a negative footer margin.
            // Hide before adding/measurement once that margin becomes positive.
            visibilityOwned = true; panel.setVisibility(View.GONE);
            root.addView(panel, 0, new android.widget.RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height));
            for (int i = 0; i < root.getChildCount(); i++) {
                View child = root.getChildAt(i);
                if (child == panel || !(child.getLayoutParams() instanceof android.widget.RelativeLayout.LayoutParams)) continue;
                android.widget.RelativeLayout.LayoutParams lp = (android.widget.RelativeLayout.LayoutParams) child.getLayoutParams();
                if (lp.getRule(android.widget.RelativeLayout.BELOW) == 0) {
                    rules.add(new RuleState(child, panel.getId()));
                    lp.addRule(android.widget.RelativeLayout.BELOW, panel.getId()); child.setLayoutParams(lp);
                }
            }
            return true;
        }
        void resize(View panel, int target) {
            ViewGroup.LayoutParams lp=panel.getLayoutParams();if(lp==null)return;
            if(lp!=resizedParams || !heightOwned || lp.height!=appliedHeight) { resizedParams=lp;nativeHeight=lp.height; }
            appliedHeight=target;heightOwned=true;
            if(lp.height!=target){lp.height=target;panel.setLayoutParams(lp);}
        }
        void restore(View panel) {
            if (panel == null || (!moved && !visibilityOwned)) return;
            if(heightOwned && panel.getLayoutParams()==resizedParams && resizedParams.height==appliedHeight) {
                resizedParams.height=nativeHeight;panel.setLayoutParams(resizedParams);
            }
            heightOwned=false;resizedParams=null;
            for (RuleState rule : rules) rule.restore();
            rules.clear();
            ViewGroup original = parent.get(), root = ownedRoot.get();
            // A host-removed/replaced/reparented node is no longer ours to reinsert.
            // Restore rules independently, but never adopt an orphan or steal it back.
            boolean currentOwned = moved ? root != null && panel.getParent() == root
                    && original != null && original.getParent() == root : panel.getParent() == original;
            if (moved && currentOwned) {
                root.removeView(panel);
                original.addView(panel, Math.min(Math.max(0, index), original.getChildCount()), params);
            } else if (!moved && currentOwned && panel.getLayoutParams() != params) panel.setLayoutParams(params);
            if (currentOwned) panel.setVisibility(visibility);
            moved = false; visibilityOwned = false; ownedRoot.clear();
        }
    }
    private static final class RuleState {
        final WeakReference<View> view; final int panelId;
        RuleState(View view, int panelId) { this.view = new WeakReference<>(view); this.panelId = panelId; }
        void restore() {
            View current = view.get();
            if (current != null && current.getLayoutParams() instanceof android.widget.RelativeLayout.LayoutParams) {
                android.widget.RelativeLayout.LayoutParams params = (android.widget.RelativeLayout.LayoutParams) current.getLayoutParams();
                if (params.getRule(android.widget.RelativeLayout.BELOW) == panelId) {
                    params.removeRule(android.widget.RelativeLayout.BELOW); current.setLayoutParams(params);
                }
            }
        }
    }
    private static final class StripState {
        int paddingBottom, appliedBottom, removedBottom, alpha, fitNavigation = -1;
        boolean paddingOwned;
        final WeakReference<Paint> paint;
        StripState(int paddingBottom, Paint paint) { this.paddingBottom=paddingBottom;this.paint=new WeakReference<>(paint);this.alpha=paint==null?255:paint.getAlpha(); }
        static StripState capture(View layout) { Paint paint=null; for(Field field:layout.getClass().getDeclaredFields()) if(!Modifier.isStatic(field.getModifiers())&&field.getType()==Paint.class) try{field.setAccessible(true);paint=(Paint)field.get(layout);break;}catch(Throwable ignored){} return new StripState(layout.getPaddingBottom(),paint); }
        void beforeFit(View layout, int bottom) {
            // Restore our last delta before the host adds its inset. A no-op fit
            // must not slowly eat unknown content padding on repeated callbacks.
            restorePadding(layout);
            int nav = navigationHeight(layout);
            fitNavigation = nav > 0 && bottom == nav ? nav : -1;
        }
        void afterFit(View layout, boolean succeeded) {
            try { if (succeeded) apply(layout); }
            finally { fitNavigation = -1; }
        }
        void apply(View layout) {
            int currentBottom = layout.getPaddingBottom(), nav = navigationHeight(layout);
            if (fitNavigation >= 0 || !paddingOwned || currentBottom != appliedBottom) {
                paddingBottom = currentBottom;
                // Only remove a confirmed navigation contribution. A partial or
                // IME/content padding is not ours and must remain untouched.
                removedBottom = nav > 0 && (currentBottom == nav || fitNavigation == nav)
                        ? Math.min(currentBottom, nav) : 0;
                appliedBottom = currentBottom - removedBottom;
                paddingOwned = removedBottom > 0;
            }
            if (currentBottom != appliedBottom) layout.setPadding(layout.getPaddingLeft(), layout.getPaddingTop(), layout.getPaddingRight(), appliedBottom);
            Paint current = paint.get();
            if (current != null && current.getAlpha() != 0) { alpha = current.getAlpha(); current.setAlpha(0); }
        }
        private void restorePadding(View layout) {
            if (layout != null && paddingOwned && layout.getPaddingBottom() == appliedBottom) {
                layout.setPadding(layout.getPaddingLeft(), layout.getPaddingTop(), layout.getPaddingRight(), paddingBottom);
            }
            paddingOwned = false; removedBottom = 0;
        }
        void restore(View layout) { restorePadding(layout);Paint current=paint.get();if(current!=null&&current.getAlpha()==0)current.setAlpha(alpha); }
    }
    private static final class Padding { final int l,t,r,b; final boolean clip; Padding(int l,int t,int r,int b,boolean clip){this.l=l;this.t=t;this.r=r;this.b=b;this.clip=clip;}static Padding of(View v){return new Padding(v.getPaddingLeft(),v.getPaddingTop(),v.getPaddingRight(),v.getPaddingBottom(),!(v instanceof ViewGroup)||((ViewGroup)v).getClipToPadding());}void restore(View v){v.setPadding(l,t,r,b);if(v instanceof ViewGroup)((ViewGroup)v).setClipToPadding(clip);} }

    private static int imeHeight(View view){WindowInsets insets=view==null?null:view.getRootWindowInsets();return insets==null?0:insets.getInsets(WindowInsets.Type.ime()).bottom;}
    /** While the floating card owns the bottom in gesture mode without IME, host consumers
     * in the chat window must see the window as edge-to-edge so content reaches behind
     * the gesture pill. The caller's insets object is never mutated. */
    private static WindowInsets stripHostNavigation(View view, WindowInsets insets) {
        try {
            if (!config.enabled || insets == null) return insets;
            if (insets.getInsets(WindowInsets.Type.ime()).bottom > 0) {
                // The keyboard owns the bottom now; the next gesture frame reports the
                // band again before anything is stripped.
                FloatingFooterLayout.forgetBand(); return insets;
            }
            int nav = insets.getInsets(WindowInsets.Type.navigationBars()).bottom;
            if (insets.getInsets(WindowInsets.Type.tappableElement()).bottom > 0) {
                FloatingFooterLayout.forgetBand(); return insets;
            }
            if (nav <= 0) return insets;
            Activity activity = ChatToolbarLifecycle.findActivity(view.getContext());
            if (activity == null || activity.getWindow() == null
                    || !ChatWindowInsetsCoordinator.isBottomOwned(activity.getWindow())) return insets;
            // The strip removes the very value the card needs, so record it first.
            FloatingFooterLayout.rememberBand(view, nav);
            return new WindowInsets.Builder(insets)
                    .setInsets(WindowInsets.Type.navigationBars(), android.graphics.Insets.NONE)
                    .build();
        } catch (Throwable error) { return insets; }
    }
    private static int navigationHeight(View view){WindowInsets insets=view==null?null:view.getRootWindowInsets();return insets==null?0:Math.max(0,insets.getInsets(WindowInsets.Type.navigationBars()).bottom);}
    private static int panelHeight(View panel){if(panel==null)return 0;if(panel.getHeight()>0)return panel.getHeight();return panel.getLayoutParams()==null?0:Math.max(0,panel.getLayoutParams().height);}
    private static int keyboardHeight(View footer){try{Method method=inherited(footer.getClass(),"getKeyBordHeightPX");Object value=method.invoke(footer);return value instanceof Integer?(Integer)value:0;}catch(Throwable ignored){return 0;}}
    private static boolean descendant(View root,View target){for(View current=target;current!=null;current=current.getParent() instanceof View?(View)current.getParent():null)if(current==root)return true;return false;}
    private static View ownerFooter(Object owner){if(owner==null||footerClass==null)return null;if(footerClass.isInstance(owner))return (View)owner;for(Class<?>type=owner.getClass();type!=null;type=type.getSuperclass())for(Field field:type.getDeclaredFields())if(!Modifier.isStatic(field.getModifiers())&&footerClass.isAssignableFrom(field.getType()))try{field.setAccessible(true);Object value=field.get(owner);if(value instanceof View)return (View)value;}catch(Throwable ignored){}return null;}
    private static View findRecycler(View scroll){if(scroll==null)return null;View host=findUnique(scroll,LIST_HOST,768);if(host==null)return null;View a=findUnique(host,LIST_A,512),b=findUnique(host,LIST_B,512);if(a!=null&&b!=null&&a!=b)return null;return a!=null?a:b;}
    private static View findUnique(View root, String name, int limit) {
        if (root == null) return null;
        View found = null; ArrayDeque<View> queue = new ArrayDeque<>(); queue.add(root);
        int admitted = 1;
        while (!queue.isEmpty()) {
            View view = queue.removeFirst();
            if (name.equals(view.getClass().getName())) { if (found != null && found != view) return null; found = view; }
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                if (group.getChildCount() > limit - admitted) return null;
                for (int i = 0; i < group.getChildCount(); i++) { queue.addLast(group.getChildAt(i)); admitted++; }
            }
        }
        return found;
    }
    private static View ancestor(View view,String name){for(View current=view;current!=null;current=current.getParent() instanceof View?(View)current.getParent():null)if(name.equals(current.getClass().getName()))return current;return null;}
    private static Method inherited(Class<?>type,String name,Class<?>...parameters)throws NoSuchMethodException{for(Class<?>c=type;c!=null;c=c.getSuperclass())try{Method m=c.getDeclaredMethod(name,parameters);m.setAccessible(true);return m;}catch(NoSuchMethodException ignored){}throw new NoSuchMethodException(name);}
    private static synchronized void hook(Method method,XC_MethodHook callback){if(!HOOKS.contains(method)){XposedBridge.hookMethod(method,callback);HOOKS.add(method);}}
    private static void log(String message){XposedBridge.log("WEX: 悬浮输入栏: "+message);}
}

