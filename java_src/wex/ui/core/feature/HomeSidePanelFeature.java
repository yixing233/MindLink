package wex.ui.core.feature;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import wex.ui.core.WexStore;

/**
 * LauncherUI 内抽屉浮层，行为参照 WeKit HomeSidePanel（GPL-3.0）。
 *
 * 抽屉不创建 Activity/Dialog：它与首页内容处于同一个 DecorView。拖动期间抽屉平移、
 * 遮罩渐显，首页内容同步缩放/位移并圆角裁剪；返回键优先关闭抽屉。
 */
public final class HomeSidePanelFeature {

    static final String KEY_ENABLED = "home_side_panel_enabled";

    /** Editing routes mirrored into the Compose snapshot; WeKit HomeSidePanelRoute subset. */
    static final String ROUTE_EDIT_HOME = "edit_home";
    static final String ROUTE_ADD_CARD = "add_card";
    static final String ROUTE_CARD_SETTINGS = "card_settings:";

    private static final String TAG = "WEX: 侧栏: ";
    private static final String LAUNCHER = "com.tencent.mm.ui.LauncherUI";
    private static final Object STATE_LOCK = new Object();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final WeakHashMap<Activity, DrawerSession> SESSIONS =
            new WeakHashMap<Activity, DrawerSession>();
    private static final WeakHashMap<Activity, Integer> SELECTED_PAGES =
            new WeakHashMap<Activity, Integer>();
    private static final WeakHashMap<Activity, Bundle> SAVED = new WeakHashMap<>();
    private static final WeakHashMap<Activity, Runnable> RESUMES = new WeakHashMap<>();
    private static List<HomeSidePanelLayout.Card> layout;

    private HomeSidePanelFeature() {
    }

    public static void init() {
        reloadLayout();
        HomeSidePanelImagePicker.init();
        HomeSidePanelWallet.init();
        XC_MethodHook resumeHook = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                if (param.thisObject instanceof Activity && !param.hasThrowable()) postResume((Activity) param.thisObject);
            }
        };
        XC_MethodHook pauseHook = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                if (!(param.thisObject instanceof Activity)) return;
                Activity activity = (Activity) param.thisObject;
                cancelResume(activity);
                DrawerSession session = SESSIONS.get(activity);
                if (session != null) session.resumed(false);
            }
        };
        XposedBridge.hookAllMethods(Activity.class, "onResume", resumeHook);
        XposedBridge.hookAllMethods(Activity.class, "onPause", pauseHook);
        try {
            Class<?> launcher = Class.forName(LAUNCHER, false, HostTargetResolver.hostClassLoader());
            Method resume = inherited(launcher, "onResume"), pause = inherited(launcher, "onPause");
            if (resume.getDeclaringClass() != Activity.class) XposedBridge.hookMethod(resume, resumeHook);
            if (pause.getDeclaringClass() != Activity.class) XposedBridge.hookMethod(pause, pauseHook);
        } catch (ReflectiveOperationException | LinkageError error) {
            log("首页生命周期监听不可用 " + error.getClass().getSimpleName());
        }
        XposedBridge.hookAllMethods(Activity.class, "onSaveInstanceState", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                DrawerSession session = SESSIONS.get((Activity) param.thisObject);
                if (session != null && param.args.length > 0 && param.args[0] instanceof Bundle)
                    session.save((Bundle) param.args[0]);
            }
        });
        XposedBridge.hookAllMethods(Activity.class, "onCreate", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                if (!(param.thisObject instanceof Activity) || param.args.length == 0
                        || !(param.args[0] instanceof Bundle)) return;
                Activity activity = (Activity) param.thisObject;
                Bundle state = ((Bundle) param.args[0]).getBundle("mdl_home_side_panel");
                if (state != null && LAUNCHER.equals(activity.getClass().getName())) SAVED.put(activity, state);
            }
        });
        XposedBridge.hookAllMethods(Activity.class, "onDestroy", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                cancelResume((Activity) param.thisObject);
                DrawerSession session = SESSIONS.remove((Activity) param.thisObject);
                SELECTED_PAGES.remove((Activity) param.thisObject);
                SAVED.remove((Activity) param.thisObject);
                if (session != null) {
                    session.detach();
                }
            }
        });
        XposedBridge.hookAllMethods(Activity.class, "dispatchTouchEvent", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!(param.thisObject instanceof Activity) || param.args.length == 0
                        || !(param.args[0] instanceof MotionEvent)) {
                    return;
                }
                DrawerSession session = SESSIONS.get((Activity) param.thisObject);
                if (session != null && session.onTouch((MotionEvent) param.args[0])) {
                    param.setResult(Boolean.TRUE);
                }
            }
        });
        try {
            Class<?> adapter = Class.forName("com.tencent.mm.ui.MainTabUI$TabsAdapter", false,
                    HostTargetResolver.hostClassLoader());
            XposedBridge.hookAllMethods(adapter, "onPageSelected", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (param.args.length == 0 || !(param.args[0] instanceof Integer)) return;
                    Activity activity = launcherActivity(); if (activity == null) return;
                    int page = (Integer) param.args[0]; SELECTED_PAGES.put(activity, page);
                    DrawerSession session = SESSIONS.get(activity);
                    if (page != 0 && session != null) session.close(false);
                }
            });
        } catch (Throwable error) { log("首页页签监听不可用 " + error.getClass().getSimpleName()); }
        XposedBridge.hookAllMethods(Activity.class, "moveTaskToBack", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                DrawerSession session = SESSIONS.get((Activity) param.thisObject);
                if (session != null && session.consumeBack()) {
                    param.setResult(Boolean.TRUE);
                }
            }
        });
        log("LauncherUI 抽屉 Hook 已注册");
    }

    public static void open(Activity activity) {
        if (activity == null || !isEnabled()) {
            return;
        }
        Integer page = SELECTED_PAGES.get(activity);
        if (page != null && page != 0) return;
        attach(activity);
        DrawerSession session = SESSIONS.get(activity);
        if (session != null) {
            session.open(true);
            log("抽屉已由头像点击打开");
        }
    }

    static void close(Activity activity) {
        DrawerSession session = activity == null ? null : SESSIONS.get(activity);
        if (session != null) session.close(false);
    }

    static boolean isEnabled() {
        try {
            return WexStore.INSTANCE.getBool(KEY_ENABLED, false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    static List<HomeSidePanelLayout.Card> loadLayout() {
        synchronized (STATE_LOCK) {
            if (layout == null) {
                reloadLayout();
            }
            return HomeSidePanelLayout.copy(layout);
        }
    }

    static void reloadLayout() {
        synchronized (STATE_LOCK) {
            try {
                String stored = WexStore.INSTANCE.getString(HomeSidePanelLayout.KEY_LAYOUT, "");
                layout = stored == null || stored.trim().length() == 0
                        ? HomeSidePanelLayout.defaults() : HomeSidePanelLayout.decode(stored);
            } catch (Throwable error) {
                log("布局解析失败；保留原配置 " + error.getClass().getSimpleName());
                if (layout == null) layout = HomeSidePanelLayout.defaults();
            }
        }
    }

    static boolean saveLayout(List<HomeSidePanelLayout.Card> next) {
        String encoded;
        try {
            HomeSidePanelLayout.validate(next);
            encoded = HomeSidePanelLayout.encode(next);
            WexStore.INSTANCE.putString(HomeSidePanelLayout.KEY_LAYOUT, encoded);
            String confirmed = WexStore.INSTANCE.getString(HomeSidePanelLayout.KEY_LAYOUT, "");
            if (!encoded.equals(confirmed)) throw new IllegalStateException("Layout write was not confirmed");
            List<HomeSidePanelLayout.Card> decoded = HomeSidePanelLayout.decode(confirmed);
            synchronized (STATE_LOCK) { layout = decoded; }
        } catch (Throwable error) {
            log("布局保存失败；草稿未生效 " + error.getClass().getSimpleName());
            return false;
        }
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                for (DrawerSession session : new ArrayList<DrawerSession>(SESSIONS.values())) {
                    session.rebuild();
                }
            }
        });
        return true;
    }

    static void refreshOpenDrawers() {
        MAIN.post(() -> {
            for (DrawerSession session : new ArrayList<DrawerSession>(SESSIONS.values())) {
                if (session.progress > 0f) session.updateSnapshot();
            }
        });
    }

    private static Activity launcherActivity() {
        for (Activity activity : new ArrayList<Activity>(SESSIONS.keySet())) {
            if (activity != null && !activity.isFinishing() && !activity.isDestroyed()) return activity;
        }
        return null;
    }

    private static Method inherited(Class<?> type, String name) throws NoSuchMethodException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try { Method method=current.getDeclaredMethod(name);method.setAccessible(true);return method; }
            catch (NoSuchMethodException ignored) { }
        }
        throw new NoSuchMethodException(name);
    }

    private static void postResume(Activity activity) {
        if (activity == null || !LAUNCHER.equals(activity.getClass().getName())) return;
        cancelResume(activity);
        WeakReference<Activity> owner = new WeakReference<>(activity);
        Runnable resume = new Runnable() {
            @Override public void run() {
                Activity target = owner.get();
                if (target == null || RESUMES.get(target) != this) return;
                RESUMES.remove(target);
                if (target.isFinishing() || target.isDestroyed()) return;
                // Base Activity's after-hook may still be inside the host's super.onResume().
                attach(target);
                DrawerSession session = SESSIONS.get(target);
                if (session != null) session.resumed(true);
            }
        };
        RESUMES.put(activity, resume);
        MAIN.post(resume);
    }

    private static void cancelResume(Activity activity) {
        Runnable resume = RESUMES.remove(activity);
        if (resume != null) MAIN.removeCallbacks(resume);
    }

    private static void attach(Activity activity) {
        if (activity == null || !LAUNCHER.equals(activity.getClass().getName())
                || activity.isFinishing() || activity.isDestroyed() || !isEnabled()) {
            return;
        }
        DrawerSession current = SESSIONS.get(activity);
        if (current != null && current.attached()) {
            return;
        }
        try {
            Window window = activity.getWindow();
            if (window == null || !(window.getDecorView() instanceof FrameLayout)) {
                log("LauncherUI DecorView 不是 FrameLayout");
                return;
            }
            FrameLayout decor = (FrameLayout) window.getDecorView();
            View content = findContent(decor);
            if (content == null) {
                log("LauncherUI 未找到可变换内容");
                return;
            }
            DrawerSession session = new DrawerSession(activity, decor, content);
            session.attach();
            SESSIONS.put(activity, session);
            log("抽屉已挂载到 LauncherUI DecorView");
        } catch (Throwable error) {
            log("抽屉挂载失败 " + error.getClass().getSimpleName() + ": " + error.getMessage());
        }
    }

    private static View findContent(FrameLayout decor) {
        for (int i = 0; i < decor.getChildCount(); i++) {
            View child = decor.getChildAt(i);
            if (child.getId() == android.R.id.content || child instanceof ViewGroup) {
                return child;
            }
        }
        return null;
    }

    private static final class DrawerSession {
        final Activity activity;
        final FrameLayout decor;
        final View content;
        final FrameLayout overlay;
        final View dim;
        final FrameLayout drawer;
        final ProgressOutline outline = new ProgressOutline();
        final Set<String> editorImportedAssets = new java.util.HashSet<>();
        final Map<String, Boolean> walletMasks = new LinkedHashMap<>();
        final Runnable dataChanged = this::updateSnapshot;
        final Runnable awaitIdentity = new Runnable() {
            @Override public void run() {
                if (!subscribed || detached || progress <= 0f || ++identityAttempts > 12) return;
                SelfProfile.refresh(false);
                TextStatusBridge.refresh(false);
                if (!SelfProfile.hasAny()) MAIN.postDelayed(this, 500);
            }
        };
        final HomeSidePanelSnapshot.Images images;
        View compose;
        Bundle composeState;
        List<HomeSidePanelLayout.Card> displayCards = new ArrayList<>();
        List<HomeSidePanelLayout.Card> editorDraft;
        /** Editing UI route driven by Java snapshots: edit_home / add_card / card_settings:<id>. */
        String editorRoute = ROUTE_EDIT_HOME;
        Set<String> originalAssets;
        boolean activityResumed = true;
        boolean subscribed;
        boolean detached;
        boolean updating;
        boolean bridgeResumed;
        boolean bridgeFailed;
        boolean closing;
        int identityAttempts;
        ValueAnimator animator;
        float progress;
        float downX;
        float downY;
        float downProgress;
        boolean tracking;
        boolean dragging;
        int drawerWidth;

        DrawerSession(Activity activity, FrameLayout decor, View content) {
            this.activity = activity;
            this.decor = decor;
            this.content = content;
            overlay = new FrameLayout(activity);
            overlay.setClipChildren(false);
            overlay.setClipToPadding(false);
            overlay.setVisibility(View.GONE);

            dim = new View(activity);
            dim.setBackgroundColor(0xFF000000);
            dim.setAlpha(0f);
            dim.setClickable(true);
            dim.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    close(true);
                }
            });
            overlay.addView(dim, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            images = new HomeSidePanelSnapshot.Images(activity, dataChanged);
            composeState = SAVED.remove(activity);

            // Compose's original LazyColumn owns display scrolling and all editor pages.
            drawer = new FrameLayout(activity);
            drawer.setClickable(true);
            GradientDrawable background = new GradientDrawable();
            background.setColor(SettingsTheme.page(TablerIcons.isDark()));
            background.setCornerRadii(new float[]{0, 0, dp(activity, 24), dp(activity, 24),
                    dp(activity, 24), dp(activity, 24), 0, 0});
            drawer.setBackground(background);
            drawer.setElevation(dp(activity, 12));
            drawerWidth = Math.min(dp(activity, 400),
                    Math.max(dp(activity, 280), activity.getResources().getDisplayMetrics().widthPixels
                            - dp(activity, 48)));
            overlay.addView(drawer, new FrameLayout.LayoutParams(
                    drawerWidth, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START));
        }

        void attach() {
            content.setOutlineProvider(outline);
            content.setClipToOutline(true);
            decor.setClipChildren(false);
            decor.setClipToPadding(false);
            decor.addView(overlay, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            applyProgress(0f);
            displayCards = HomeSidePanelFeature.loadLayout();
        }

        boolean attached() {
            return overlay.getParent() == decor;
        }

        void detach() {
            if (detached) return;
            if (animator != null) animator.cancel();
            discardEditor();
            applyProgress(0f);
            detached = true;
            unsubscribe();
            if (compose != null) {
                saveCompose();
                try { SidePanelComposeBridge.dispose(compose); }
                catch (RuntimeException | LinkageError error) { bridgeError("释放", error); }
                compose = null;
            }
            images.close();
            content.setClipToOutline(false);
            if (overlay.getParent() == decor) decor.removeView(overlay);
        }

        /** A layout/data refresh updates the original Compose state, never its view/scroll owner. */
        void rebuild() {
            displayCards = HomeSidePanelFeature.loadLayout();
            walletMasks.keySet().retainAll(cardIds(displayCards));
            if (editorDraft != null || progress <= 0f) return;
            showDisplay();
        }

        void updateSnapshot() {
            if (Looper.myLooper() != Looper.getMainLooper()) { MAIN.post(dataChanged); return; }
            if (detached || updating || compose == null
                    || activity.isFinishing() || activity.isDestroyed()) return;
            updating = true;
            try {
                if (editorDraft != null) {
                    // Editing state: the draft (not the saved layout) feeds the Compose editor pages.
                    SidePanelComposeBridge.update(compose, HomeSidePanelSnapshot.build(true, true, editorRoute,
                            editorDraft, walletMasks, images.snapshot(editorDraft),
                            activityResumed && progress > 0f), callbacks());
                } else {
                    SidePanelComposeBridge.update(compose, HomeSidePanelSnapshot.build(true, false,
                            displayCards, walletMasks, images.snapshot(displayCards),
                            activityResumed && progress > 0f), callbacks());
                }
            } catch (RuntimeException | LinkageError error) { bridgeError("更新", error); }
            finally { updating = false; }
        }

        private void showDisplay() {
            if (detached || activity.isFinishing() || activity.isDestroyed()) return;
            if (compose == null && !bridgeFailed) {
                updating = true;
                try {
                    compose = SidePanelComposeBridge.build(activity, composeState,
                            HomeSidePanelSnapshot.build(true, false, displayCards, walletMasks,
                                    images.snapshot(displayCards), activityResumed && progress > 0f), callbacks());
                    drawer.removeAllViews();
                    drawer.addView(compose, new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                    composeState = null;
                } catch (RuntimeException | LinkageError error) {
                    bridgeFailed = true;
                    bridgeError("创建", error);
                    TextView unavailable = editorButton("侧栏组件不可用，请重启微信");
                    drawer.removeAllViews();
                    drawer.addView(unavailable, new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                } finally { updating = false; }
            } else if (compose != null) {
                if (compose.getParent() != drawer) drawer.addView(compose, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                compose.setVisibility(View.VISIBLE);
                updateSnapshot();
            }
            syncLifecycle();
        }

        private SidePanelComposeBridge.Callbacks callbacks() {
            Map<String, Runnable> actions = new LinkedHashMap<>();
            actions.put("open_profile", () -> { close(false); ProfileView.openPersonalProfile(activity); });
            actions.put("open_status", () -> TextStatusBridge.open(activity));
            actions.put("refresh_status", () -> TextStatusBridge.refresh(true));
            actions.put("open_settings", () -> { close(false); ProfileView.openSettings(activity); });
            actions.put("open_panel", () -> open(true));
            actions.put("close_panel", () -> close(true));
            actions.put("enter_edit", () -> enterEditor(null));
            actions.put("save_edit", this::saveEditor);
            actions.put("discard_edit", () -> { discardEditor(); rebuild(); });
            actions.put("add_card", () -> enterEditor(ROUTE_ADD_CARD));
            actions.put("scan", () -> HomeSidePanelActions.run(activity, HomeSidePanelLayout.ActionKind.SCAN));
            actions.put("payment_code", this::openPaymentCode);
            List<HomeSidePanelLayout.Card> cards = editorDraft == null ? displayCards : editorDraft;
            if (editorDraft != null) {
                // Add-card page: one callback per card type carrying the serial name.
                for (HomeSidePanelLayout.CardType type : HomeSidePanelLayout.CardType.values()) {
                    final String serialName = type.serialName;
                    actions.put("add_card:" + serialName, () -> addEditorCard(serialName));
                }
            }
            for (HomeSidePanelLayout.Card card : cards) {
                final String id = card.id;
                actions.put("delete:" + id, () -> deleteEditorCard(id));
                actions.put("card_settings:" + id, () -> enterEditor(ROUTE_CARD_SETTINGS + id));
                switch (card.type) {
                    case DATE_TIME:
                        actions.put("update_lunar:" + id, () -> {
                            HomeSidePanelLayout.Card latest = card(id);
                            if (latest != null && editorDraft != null) {
                                latest.showLunarCalendar = !latest.showLunarCalendar;
                                refreshEditingSnapshot();
                            }
                        });
                        break;
                    case WALLET:
                        actions.put("update_wallet_mask:" + id, () -> {
                            HomeSidePanelLayout.Card latest = card(id);
                            if (latest != null && editorDraft != null) {
                                latest.hideWalletBalance = !latest.hideWalletBalance;
                                refreshEditingSnapshot();
                            }
                        });
                        actions.put("toggle_wallet:" + id, () -> toggleWallet(id));
                        break;
                    case WEATHER:
                        actions.put("update_weather_city:" + id, WeatherCityInput.pending(activity, code -> {
                            HomeSidePanelLayout.Card latest = card(id);
                            if (latest != null && editorDraft != null) {
                                latest.weatherCity = code;
                                refreshEditingSnapshot();
                            }
                        }));
                        actions.put("refresh_weather:" + id, () -> {
                            HomeSidePanelLayout.Card latest = card(id);
                            if (latest != null) HomeSidePanelData.refreshWeather(latest);
                        });
                        break;
                    case HITOKOTO:
                        actions.put("update_hitokoto_settings:" + id, HitokotoInput.pending(activity, settings -> {
                            HomeSidePanelLayout.Card latest = card(id);
                            if (latest != null && editorDraft != null) {
                                latest.hitokotoCategories = settings.categories;
                                latest.hitokotoMinLength = settings.minLength;
                                latest.hitokotoMaxLength = settings.maxLength;
                                latest.showHitokotoSource = settings.showSource;
                                latest.showHitokotoAuthor = settings.showAuthor;
                                refreshEditingSnapshot();
                            }
                        }));
                        actions.put("refresh_hitokoto:" + id, () -> {
                            HomeSidePanelLayout.Card latest = card(id);
                            if (latest != null) HomeSidePanelData.refreshHitokoto(latest);
                        });
                        break;
                    case IMAGE:
                        // Image picking belongs to editing (WeKit image_enter_edit semantics);
                        // display mode shows the "enter edit mode to select" hint instead.
                        if (editorDraft != null) actions.put("select_image:" + id, () -> selectImage(id));
                        break;
                    case HORIZONTAL_ACTIONS:
                    case VERTICAL_ACTIONS:
                        for (HomeSidePanelLayout.Action action : card.actions) {
                            final String actionId = action.id;
                            actions.put("run:" + id + ":" + actionId, () -> runAction(id, actionId));
                        }
                        break;
                    default:
                        break;
                }
            }
            return new SidePanelComposeBridge.Callbacks(actions);
        }

        /** Re-emit the editing snapshot after a draft mutation, without leaving edit mode. */
        private void refreshEditingSnapshot() {
            if (editorDraft == null) return;
            updateSnapshot();
        }

        private HomeSidePanelLayout.Card card(String id) {
            for (HomeSidePanelLayout.Card card : editorDraft == null ? displayCards : editorDraft)
                if (card.id.equals(id)) return card;
            return null;
        }

        private void runAction(String id, String actionId) {
            HomeSidePanelLayout.Card card = card(id);
            if (card == null || editorDraft != null) return;
            for (HomeSidePanelLayout.Action action : card.actions) {
                if (action.id.equals(actionId)) { HomeSidePanelActions.run(activity, action.kind); return; }
            }
        }

        private void toggleWallet(String id) {
            HomeSidePanelLayout.Card card = card(id);
            if (card == null || editorDraft != null || !card.hideWalletBalance) return;
            Boolean current = walletMasks.get(id);
            walletMasks.put(id, current != null && !current);
            updateSnapshot();
        }

        private void openPaymentCode() {
            close(false);
            // Only native navigation, following WeKit's existing payment-code page/fallback.
            for (String page : new String[]{"com.tencent.mm.plugin.offline.ui.WalletOfflineCoinPurseUI",
                    "com.tencent.mm.plugin.mall.ui.MallIndexUIv2"}) {
                try { activity.startActivity(new Intent().setClassName(activity.getPackageName(), page)); return; }
                catch (ActivityNotFoundException ignored) { }
                catch (RuntimeException error) { break; }
            }
            ImagePicker.INSTANCE.toast(activity, "收付款页面不可用");
        }

        /** Enter (or re-enter) editing at a route: null → edit_home; add_card; card_settings:<id>.
         *  The Compose root stays visible and re-renders the editor pages from snapshots. */
        private void enterEditor(String route) {
            if (editorDraft == null) showEditor(HomeSidePanelFeature.loadLayout());
            if (editorDraft == null) return;
            editorRoute = route == null ? ROUTE_EDIT_HOME : route;
            updateSnapshot();
        }

        /** add_card:<serialName> appends a default card of that type to the draft. */
        private void addEditorCard(String serialName) {
            if (editorDraft == null) showEditor(HomeSidePanelFeature.loadLayout());
            if (editorDraft == null) return;
            try {
                editorDraft.add(HomeSidePanelLayout.card(HomeSidePanelLayout.CardType.from(serialName), false));
            } catch (IllegalArgumentException error) { return; }
            HomeSidePanelLayout.Card added = editorDraft.get(editorDraft.size() - 1);
            if (added.type == HomeSidePanelLayout.CardType.WEATHER && added.weatherCity == null) added.weatherCity = "101010100";
            editorRoute = ROUTE_EDIT_HOME;
            updateSnapshot();
        }

        private void deleteEditorCard(String id) {
            if (editorDraft == null) return;
            HomeSidePanelLayout.Card target = card(id);
            if (target != null) {
                editorDraft.remove(target);
                if (ROUTE_CARD_SETTINGS.equals(editorRoute) || editorRoute.startsWith(ROUTE_CARD_SETTINGS)) editorRoute = ROUTE_EDIT_HOME;
                updateSnapshot();
            }
        }

        private void selectImage(String id) {
            if (editorDraft == null) return;
            HomeSidePanelLayout.Card target = card(id);
            if (target == null || target.type != HomeSidePanelLayout.CardType.IMAGE) return;
            final List<HomeSidePanelLayout.Card> draft = editorDraft;
            HomeSidePanelImagePicker.pick(activity, value -> {
                if (value instanceof String) {
                    String asset = (String) value;
                    if (!detached && editorDraft == draft && draft.contains(target)) {
                        editorImportedAssets.add(asset);
                        target.imageAssetId = asset;
                        updateSnapshot();
                    } else removeDraftAsset(asset);
                } else ImagePicker.INSTANCE.toast(activity, "图片无效或超出限制");
                return null;
            });
        }

        private void saveEditor() {
            if (editorDraft == null) return;
            if (!HomeSidePanelFeature.saveLayout(editorDraft)) {
                Toast.makeText(activity, "侧栏布局保存失败，原布局未改变", Toast.LENGTH_SHORT).show();
                return;
            }
            // Do not delete previously saved user images. Only newly imported, unused draft assets are ours.
            cleanupOrphanAssets(originalAssets, imageAssets(editorDraft));
            editorDraft = null; originalAssets = null;
            walletMasks.clear();
            rebuild();
        }

        private void discardEditor() {
            if (editorDraft == null) return;
            cleanupDraftAssets(editorDraft, originalAssets);
            editorDraft = null; originalAssets = null;
        }

        private Set<String> cardIds(List<HomeSidePanelLayout.Card> cards) {
            Set<String> result = new java.util.HashSet<>();
            for (HomeSidePanelLayout.Card card : cards) result.add(card.id);
            return result;
        }

        private void subscribe() {
            if (subscribed || detached) return;
            subscribed = true;
            identityAttempts = 0;
            SelfProfile.subscribe(dataChanged);
            TextStatusBridge.subscribe(dataChanged);
            MAIN.post(awaitIdentity);
        }

        private void unsubscribe() {
            MAIN.removeCallbacks(awaitIdentity);
            MAIN.removeCallbacks(dataChanged);
            SelfProfile.unsubscribe(dataChanged);
            TextStatusBridge.unsubscribe(dataChanged);
            subscribed = false;
            // TextStatusBridge.subscribe enqueues registration even on MAIN. Remove a pending registration
            // after it runs too, unless this owner has already opened/subscribed again in that queue turn.
            MAIN.post(() -> { if (!subscribed || detached) TextStatusBridge.unsubscribe(dataChanged); });
        }

        void resumed(boolean value) {
            activityResumed = value;
            if (value && progress > 0f) subscribe(); else unsubscribe();
            syncLifecycle();
            if (value && progress > 0f) updateSnapshot();
        }

        void save(Bundle out) {
            saveCompose();
            if (composeState != null) out.putBundle("mdl_home_side_panel", composeState);
        }

        private void saveCompose() {
            if (compose == null) return;
            Bundle saved = new Bundle();
            try { SidePanelComposeBridge.save(compose, saved); composeState = saved; }
            catch (RuntimeException | LinkageError error) { bridgeError("保存状态", error); }
        }

        private void syncLifecycle() {
            // Editor pages share this visible Compose root and need its frame clock
            // and callbacks just as the display page does.
            boolean resumed = !detached && !closing && activityResumed && progress > 0f;
            if (compose == null || bridgeResumed == resumed) return;
            try { SidePanelComposeBridge.lifecycle(compose, resumed); bridgeResumed = resumed; }
            catch (RuntimeException | LinkageError error) { bridgeError("生命周期", error); }
        }

        private void bridgeError(String operation, Throwable error) {
            // The exception message is the only way to tell a resource/visibility failure
            // from a payload/CreateView failure when the drawer shows its error text.
            log("组件" + operation + "失败 " + error.getClass().getSimpleName() + ": " + error.getMessage());
        }

        /** Begin an editing session: capture the draft, keep the Compose root rendering.
         *  All editor UI lives in Compose pages (edit_home / add_card / card_settings). */
        private void showEditor(final List<HomeSidePanelLayout.Card> draft) {
            editorImportedAssets.clear();
            editorDraft = draft;
            originalAssets = imageAssets(draft);
            editorRoute = ROUTE_EDIT_HOME;
            if (compose == null) showDisplay();
            if (compose != null) compose.setVisibility(View.VISIBLE);
            syncLifecycle();
            updateSnapshot();
        }

        /** Draft-only re-emit after a per-field mutation; never re-captures the draft. */
        private void showEditor(final List<HomeSidePanelLayout.Card> draft, final java.util.Set<String> originalAssets) {
            if (editorDraft != draft) return;
            updateSnapshot();
        }

        private java.util.Set<String> imageAssets(List<HomeSidePanelLayout.Card> cards) {
            java.util.Set<String> ids=new java.util.HashSet<>();for(HomeSidePanelLayout.Card card:cards)if(card.type==HomeSidePanelLayout.CardType.IMAGE&&card.imageAssetId!=null)ids.add(card.imageAssetId);return ids;
        }
        private void cleanupDraftAssets(List<HomeSidePanelLayout.Card> draft,Set<String> original) {
            for(String id:new java.util.HashSet<>(editorImportedAssets)) removeDraftAsset(id);
            editorImportedAssets.clear();
        }
        private void cleanupOrphanAssets(Set<String> original,Set<String> current) {
            Set<String> unused=new java.util.HashSet<>(editorImportedAssets);
            unused.removeAll(current);
            for(String id:unused) removeDraftAsset(id);
            editorImportedAssets.clear();
        }
        private void removeDraftAsset(String id) {
            // Another drawer may have saved this imported asset while the picker callback was pending.
            if (!imageAssets(HomeSidePanelFeature.loadLayout()).contains(id)) HomeSidePanelImages.remove(activity,id);
        }

        private TextView editorButton(String text) {
            TextView view=new TextView(activity);view.setText(text);view.setTextSize(14f);view.setTextColor(SettingsTheme.sub(activity));
            view.setGravity(Gravity.CENTER);view.setPadding(dp(activity,10),dp(activity,8),dp(activity,10),dp(activity,8));return view;
        }

        boolean onTouch(MotionEvent event) {
            if (!LAUNCHER.equals(activity.getClass().getName())) {
                return false;
            }
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = event.getRawX();
                    downY = event.getRawY();
                    downProgress = progress;
                    tracking = progress > 0f || downX <= dp(activity, 32);
                    dragging = false;
                    return false;
                case MotionEvent.ACTION_MOVE:
                    if (!tracking) {
                        return false;
                    }
                    float dx = event.getRawX() - downX;
                    float dy = event.getRawY() - downY;
                    if (!dragging && Math.max(Math.abs(dx), Math.abs(dy)) > dp(activity, 10)) {
                        if (Math.abs(dx) < Math.abs(dy)) {
                            tracking = false;
                            return false;
                        }
                        dragging = true;
                        overlay.setVisibility(View.VISIBLE);
                        MotionEvent cancel = MotionEvent.obtain(event.getDownTime(), event.getEventTime(),
                                MotionEvent.ACTION_CANCEL, event.getX(), event.getY(), event.getMetaState());
                        try { decor.dispatchTouchEvent(cancel); } finally { cancel.recycle(); }
                    }
                    if (dragging) {
                        applyProgress(downProgress + dx / drawerWidth);
                        return true;
                    }
                    return false;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (!tracking) {
                        return false;
                    }
                    boolean consumed = dragging;
                    tracking = false;
                    dragging = false;
                    if (consumed) animateTo(progress >= 0.35f ? 1f : 0f);
                    return consumed;
                default:
                    return dragging;
            }
        }

        boolean consumeBack() {
            if (progress <= 0f && !tracking) {
                return false;
            }
            close(true);
            return true;
        }

        void open(boolean animated) {
            if (detached || activity.isFinishing() || activity.isDestroyed()) return;
            closing = false;
            // A single failed construction used to latch for the whole process, so the drawer
            // stayed dead until WeChat restarted; retry on every open instead.
            bridgeFailed = false;
            displayCards = HomeSidePanelFeature.loadLayout();
            if (editorDraft == null) showDisplay();
            overlay.setVisibility(View.VISIBLE);
            if (activityResumed) subscribe();
            updateSnapshot();
            if (animated) {
                animateTo(1f);
            } else {
                applyProgress(1f);
            }
        }

        void close(boolean animated) {
            closing = true;
            saveCompose();
            discardEditor();
            walletMasks.clear();
            unsubscribe();
            syncLifecycle();
            if (animated) {
                animateTo(0f);
            } else {
                applyProgress(0f);
            }
        }

        private void animateTo(final float target) {
            if (target > 0f) closing = false;
            if (animator != null) {
                animator.cancel();
            }
            animator = ValueAnimator.ofFloat(progress, target);
            animator.setDuration(200L);
            animator.setInterpolator(new DecelerateInterpolator());
            animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    applyProgress((Float) animation.getAnimatedValue());
                    if (target == 0f && progress <= 0.001f) {
                        overlay.setVisibility(View.GONE);
                    }
                }
            });
            animator.start();
        }

        private void applyProgress(float value) {
            float previous = progress;
            progress = Math.max(0f, Math.min(1f, value));
            if (progress > 0f) {
                overlay.setVisibility(View.VISIBLE);
                if (previous <= 0f) {
                    if (editorDraft == null) showDisplay();
                    if (activityResumed) subscribe();
                    updateSnapshot();
                }
            } else {
                overlay.setVisibility(View.GONE);
                if (previous > 0f) {
                    saveCompose();
                    discardEditor();
                    walletMasks.clear();
                    unsubscribe();
                }
            }
            syncLifecycle();
            drawer.setTranslationX(-drawerWidth * (1f - progress));
            dim.setAlpha(0.32f * progress);
            dim.setVisibility(progress > 0f ? View.VISIBLE : View.GONE);

            // WeKit 的内容变换：轻微缩小、右移/下移、圆角随进度出现。
            float scale = 1f - 0.05f * progress;
            content.setPivotX(0f);
            content.setPivotY(content.getHeight() / 2f);
            content.setScaleX(scale);
            content.setScaleY(scale);
            content.setTranslationX(dp(activity, 7) * progress);
            content.setTranslationY(dp(activity, 8) * progress);
            outline.progress = progress;
            content.invalidateOutline();
        }
    }

    private static final class ProgressOutline extends ViewOutlineProvider {
        float progress;

        @Override
        public void getOutline(View view, Outline outline) {
            float radius = dp(view.getContext(), 28) * progress;
            outline.setRoundRect(0, 0, Math.max(1, view.getWidth()),
                    Math.max(1, view.getHeight()), radius);
        }
    }

    static int dp(Context context, float value) {
        return Math.max(1, Math.round(context.getResources().getDisplayMetrics().density * value));
    }

    private static void log(String message) {
        try {
            XposedBridge.log(TAG + message);
        } catch (Throwable ignored) {
        }
    }

    /** One-slot pending carrier for a Compose settings page commit (platform types only).
     *  The Compose side stores the payload here; the host consumes it exactly once on callback. */
    static final class WeatherCityInput {
        interface Consumer { void accept(String cityCode); }
        private static volatile Consumer pending;

        private WeatherCityInput() { }

        static Runnable pending(android.app.Activity activity, Consumer consumer) {
            pending = consumer;
            return () -> {
                Consumer current = pending;
                pending = null;
                if (current != null) current.accept(WeatherCityPayload.CODE);
            };
        }
    }

    /** City code payload written by the Compose settings page before invoking the callback. */
    public static final class WeatherCityPayload {
        public static volatile String CODE = "";
        private WeatherCityPayload() { }
    }

    /** One-slot pending carrier for hitokoto settings committed from the Compose page. */
    static final class HitokotoInput {
        interface Consumer { void accept(Settings settings); }
        private static volatile Consumer pending;

        private HitokotoInput() { }

        static Runnable pending(android.app.Activity activity, Consumer consumer) {
            pending = consumer;
            return () -> {
                Consumer current = pending;
                pending = null;
                if (current != null) current.accept(Settings.capture());
            };
        }
    }

    /** Plain settings struct written by the Compose settings page before invoking the callback. */
    public static final class Settings {
        public volatile String categories = "";
        public volatile Integer minLength, maxLength;
        public volatile boolean showSource = true, showAuthor = true;

        static Settings capture() { return PENDING_SETTINGS; }
        private static final Settings PENDING_SETTINGS = new Settings();
        private Settings() { }
    }
}
