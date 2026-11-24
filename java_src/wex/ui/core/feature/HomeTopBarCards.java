package wex.ui.core.feature;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Outline;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Layout;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.AbsListView;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import dev.mindlink.xposed.XC_MethodHook;
import dev.mindlink.xposed.XposedBridge;
import dev.mindlink.xposed.XposedHelpers;
import wex.ui.core.WexStore;

/**
 * Home (LauncherUI) top bar as two floating cards: the left one around the profile (avatar,
 * nickname, status) together with the title ("微信(1)", "通讯录"…; only the title when the
 * profile card is off), the right one around the action buttons only (search and "+").
 *
 * <p>The cards are drawn by a background drawable on the native Toolbar that first paints the
 * Toolbar's own background, so nothing in the host layout moves and touches stay native. Shape,
 * shadow and material are the floating chat header's (卡片材质: plain / frosted / liquid glass).
 * Geometry follows the live views on every pre-draw: tab switches, the profile card, the search
 * bar option hiding the native search button and unread titles all just resize the cards.</p>
 */
final class HomeTopBarCards {
    static final String KEY_ENABLED = "top_floating_cards";
    /** Hide the chats tab title ("微信", "微信(3)") once the cards show the profile. */
    static final String KEY_HIDE_TITLE = "top_cards_hide_title";
    /**
     * Transparent bar with the page content running underneath it, so lists scroll under the
     * floating cards (like the floating chat header). Off keeps the native opaque bar.
     */
    static final String KEY_OVERLAY = "top_cards_overlay";
    /**
     * HyperOS-style progressive blur behind the status bar and the cards, fading to sharp below
     * the bar (needs {@link #KEY_OVERLAY}: the content must run under the bar to be blurred).
     */
    static final String KEY_PROGRESSIVE = "top_cards_progressive_blur";
    private static final long SCAN_MS = 400L;
    private static final int CONTACTS_DISCOVER_TOP_GAP_DP = 12;
    private static final String LAUNCHER = "com.tencent.mm.ui.LauncherUI";
    private static final String TOOLBAR = "androidx.appcompat.widget.Toolbar";
    private static final String ACTION_BAR = "androidx.appcompat.widget.ActionBarContainer";
    /** TopBarFeature's profile tag; note the Cyrillic е / х the smali uses. */
    private static final String PROFILE_TAG = "w\u0435\u0445_top_profile";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final WeakHashMap<Activity, Session> SESSIONS = new WeakHashMap<>();
    private static final WeakHashMap<View, int[]> LIFTED_TOP = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> OWNED_OVERLAYS = new WeakHashMap<>();
    private static int selfPaddingDepth, selfOverlayDepth;
    private static boolean initialized;
    /** Read on init and on {@link #refresh()} only: pre-draw never touches the settings store. */
    private static volatile boolean enabled;
    private static volatile boolean hideTitle;
    private static volatile boolean overlayContent;
    private static volatile boolean progressiveBlur;
    /** TopBarFeature's custom replacement for "微信" (top_title); it is the chats title too. */
    private static volatile String customTitle = "";
    private static volatile int glassMode = ChatGlassSurface.PLAIN, glassBlur;

    private HomeTopBarCards() { }

    static synchronized void init() {
        if (initialized) return;
        initialized = true;
        readConfig();
        installPaddingGuard();
        installOverlayGuard();
        XC_MethodHook resumed = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                if (param.hasThrowable() || !(param.thisObject instanceof Activity)) return;
                Activity activity = (Activity) param.thisObject;
                // One store read per home resume: a notification cold start may read before storage mounts.
                if (LAUNCHER.equals(activity.getClass().getName())) {
                    Session existing = SESSIONS.get(activity);
                    if (existing != null && existing.alive() && enabled && overlayContent) {
                        existing.recheckUnderlayNow();
                    }
                    MAIN.post(() -> { readConfig(); attach(activity, 0); });
                }
            }
        };
        XposedBridge.hookAllMethods(Activity.class, "onResume", resumed);
        try {
            Class<?> launcher = Class.forName(LAUNCHER, false, HostTargetResolver.hostClassLoader());
            Method resume = inherited(launcher, "onResume");
            if (resume.getDeclaringClass() != Activity.class) XposedBridge.hookMethod(resume, resumed);
        } catch (ReflectiveOperationException | LinkageError error) {
            log("首页生命周期监听不可用 " + error.getClass().getSimpleName());
        }
        XposedBridge.hookAllMethods(Activity.class, "onDestroy", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                Session session = SESSIONS.remove(param.thisObject);
                if (session != null) session.dispose();
            }
        });
    }

    /** Settings or card material changed. */
    static void refresh() {
        MAIN.post(() -> {
            readConfig();
            for (Session session : new ArrayList<>(SESSIONS.values())) session.sync();
        });
    }

    private static boolean isMainThread() { return Looper.myLooper() == Looper.getMainLooper(); }

    private static void setPaddingTop(View view, int top) {
        selfPaddingDepth++;
        try {
            view.setPadding(view.getPaddingLeft(), top, view.getPaddingRight(), view.getPaddingBottom());
        } finally {
            selfPaddingDepth--;
        }
    }

    private static void installPaddingGuard() {
        XC_MethodHook guard = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                if (!isMainThread() || selfPaddingDepth > 0 || LIFTED_TOP.isEmpty()
                        || !(param.thisObject instanceof View)) return;
                int[] saved = LIFTED_TOP.get(param.thisObject);
                if (saved == null) return;
                if (param.args != null && param.args.length >= 2 && param.args[1] instanceof Integer) {
                    int top = (Integer) param.args[1];
                    if (top != 0) {
                        saved[0] = top;
                        param.args[1] = 0;
                    }
                }
            }
        };
        try {
            XposedBridge.hookMethod(View.class.getDeclaredMethod("setPadding",
                    int.class, int.class, int.class, int.class), guard);
        } catch (Throwable ignored) { }
        try {
            XposedBridge.hookMethod(View.class.getDeclaredMethod("setPaddingRelative",
                    int.class, int.class, int.class, int.class), guard);
        } catch (Throwable ignored) { }
        try {
            Method internal = View.class.getDeclaredMethod("internalSetPadding",
                    int.class, int.class, int.class, int.class);
            internal.setAccessible(true);
            XposedBridge.hookMethod(internal, guard);
        } catch (Throwable ignored) { }
    }

    private static void installOverlayGuard() {
        try {
            Class<?> overlayType = Class.forName("androidx.appcompat.widget.ActionBarOverlayLayout",
                    false, HostTargetResolver.hostClassLoader());
            Method setOverlay = overlayType.getDeclaredMethod("setOverlayMode", boolean.class);
            setOverlay.setAccessible(true);
            XposedBridge.hookMethod(setOverlay, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    if (!isMainThread() || selfOverlayDepth > 0 || OWNED_OVERLAYS.isEmpty()
                            || !(param.thisObject instanceof View)) return;
                    if (OWNED_OVERLAYS.containsKey(param.thisObject) && param.args != null
                            && param.args.length == 1 && Boolean.FALSE.equals(param.args[0])) {
                        param.args[0] = Boolean.TRUE;
                    }
                }
            });
        } catch (Throwable ignored) { }
    }

    private static void readConfig() {
        try { enabled = WexStore.INSTANCE.getBool(KEY_ENABLED, true); }
        catch (RuntimeException error) { enabled = false; }
        try {
            hideTitle = WexStore.INSTANCE.getBool(KEY_HIDE_TITLE, true);
            overlayContent = WexStore.INSTANCE.getBool(KEY_OVERLAY, true);
            progressiveBlur = WexStore.INSTANCE.getBool(KEY_PROGRESSIVE, true);
            String custom = WexStore.INSTANCE.getString("top_title", "");
            customTitle = custom == null ? "" : custom.trim();
        } catch (RuntimeException error) { hideTitle = false; overlayContent = false; progressiveBlur = false; customTitle = ""; }
        glassMode = ChatGlassSurface.mode();
        glassBlur = ChatGlassSurface.blurRadiusDp(glassMode);
    }

    private static void attach(Activity activity, int attempt) {
        if (activity.isFinishing() || activity.isDestroyed() || activity.getWindow() == null) return;
        Session session = SESSIONS.get(activity);
        if (session != null && session.alive()) { session.sync(); return; }
        if (!enabled) return;
        View toolbar = findToolbar(activity.getWindow().getDecorView());
        if (toolbar == null) {
            // The action bar may be inflated after the first resume frame.
            if (attempt < 8) MAIN.postDelayed(() -> attach(activity, attempt + 1), 150L);
            else log("未找到首页 Toolbar，保持原样");
            return;
        }
        if (session != null) session.dispose();
        session = new Session(activity, toolbar);
        SESSIONS.put(activity, session);
        session.sync();
    }

    private static View findToolbar(View root) {
        ArrayDeque<View> queue = new ArrayDeque<>(); queue.add(root);
        for (int visited = 0; !queue.isEmpty() && visited < 512; visited++) {
            View view = queue.removeFirst();
            if (is(view.getClass(), TOOLBAR) && view.getParent() instanceof View
                    && is(view.getParent().getClass(), ACTION_BAR)) return view;
            // The conversation list never contains the action bar; skip its rows.
            if (view instanceof android.widget.AbsListView) continue;
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) queue.addLast(group.getChildAt(i));
            }
        }
        return null;
    }

    private static boolean is(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass())
            if (name.equals(current.getName())) return true;
        return false;
    }

    private static Method inherited(Class<?> type, String name) throws NoSuchMethodException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try { Method method = current.getDeclaredMethod(name); method.setAccessible(true); return method; }
            catch (NoSuchMethodException ignored) { }
        }
        throw new NoSuchMethodException(name);
    }

    private static void log(String message) {
        try { XposedBridge.log("WEX: 首页顶栏卡片: " + message); } catch (Throwable ignored) { }
    }

    /** One installed toolbar: background wrapper, released clips and a pre-draw listener. */
    private static final class Session implements ViewTreeObserver.OnPreDrawListener {
        final WeakReference<View> toolbarRef;
        final WeakReference<Activity> activityRef;
        Cards cards;
        ViewTreeObserver observer;
        final List<ClipState> clips = new ArrayList<>();
        int geometryLogs;
        /** The title we made INVISIBLE, and the visibility it had; restored on other tabs and on dispose. */
        WeakReference<TextView> hiddenTitle;
        int hiddenVisibility = View.VISIBLE;
        // ---- content under the bar (top_cards_overlay) ----
        FloatingHeaderStyle.WindowHeaderState windowHeader;
        WeakReference<View> trackedOverlay, trackedBody;
        int[] trackedBodyTop;
        int preDrawSkips;
        /** The ActionBarContainer whose background we removed, and that background. */
        WeakReference<View> clearedBar;
        Drawable barBackground;
        /** Scroll containers given the bar as top padding: {original top, applied top, clipToPadding was on}. */
        final WeakHashMap<View, int[]> pads = new WeakHashMap<>();
        int lastBarBottom = Integer.MIN_VALUE;
        WeakReference<View> pagerRef, underlayPage;
        long nextScan;
        boolean holdingReveal;
        int revealFrames;
        float originalTransitionAlpha;
        ValueAnimator reveal;
        /** Frames still scanned unthrottled after the overlay was switched on, while the pages relayout. */
        int eager;
        boolean dumped;
        // ---- status bar strip (top_cards_overlay) ----
        /** Window status bar colour before we made it transparent; only meaningful while {@link #statusOwned}. */
        int statusColor;
        boolean statusOwned;
        /**
         * Full-width views that only paint the status-bar strip above the bar (HomeUI's status
         * mask), with the background each had; cleared so the list shows under the status bar.
         */
        final WeakHashMap<View, Drawable> masks = new WeakHashMap<>();
        /** A chat opened inside LauncherUI owns the status bar while it is shown. */
        WeakReference<View> chatting;
        // ---- glass recovery after the bar moved (pull-down page) ----
        int lastToolbarY = Integer.MIN_VALUE;
        boolean toolbarMoving, toolbarHidden;
        final Runnable glassRetry = () -> {
            if (cards != null) cards.refreshGlass();
        };

        Session(Activity activity, View toolbar) {
            toolbarRef = new WeakReference<>(toolbar);
            activityRef = new WeakReference<>(activity);
        }

        boolean alive() {
            View toolbar = toolbarRef.get();
            return toolbar != null && toolbar.isAttachedToWindow();
        }

        void sync() {
            View toolbar = toolbarRef.get();
            if (toolbar == null || !enabled) { dispose(); return; }
            if (cards == null) {
                cards = new Cards(toolbar);
                cards.wrap(toolbar.getBackground());
                toolbar.setBackground(cards);
                // The card shadow reaches past the Toolbar; let it, as the chat header does.
                View current = toolbar;
                for (int level = 0; level < 3 && current.getParent() instanceof ViewGroup; level++) {
                    ViewGroup parent = (ViewGroup) current.getParent();
                    clips.add(new ClipState(parent));
                    parent.setClipChildren(false);
                    current = parent;
                }
                log("已启用");
            }
            cards.material(glassMode, glassBlur);
            cards.progressive(overlayContent && progressiveBlur);
            if (cards.transparent(overlayContent)) toolbar.invalidateOutline();
            nextScan = 0L;
            watch(toolbar);
            syncTitle(toolbar);
            if (cards.layout(toolbar) && geometryLogs++ < 4) log("geometry " + cards.describe());
            if (overlayContent) syncUnderlay(toolbar, toolbar.isShown()); else releaseUnderlay();
            toolbar.invalidate();
        }

        void recheckUnderlayNow() {
            View toolbar = toolbarRef.get();
            if (toolbar == null || cards == null || !overlayContent) return;
            nextScan = 0L;
            eager = Math.max(eager, 4);
            syncUnderlay(toolbar, toolbar.isShown());
        }

        private void untrackWindowHeader() {
            View prevOverlay = trackedOverlay == null ? null : trackedOverlay.get();
            if (prevOverlay != null) OWNED_OVERLAYS.remove(prevOverlay);
            trackedOverlay = null;
            View prevBody = trackedBody == null ? null : trackedBody.get();
            if (prevBody != null) LIFTED_TOP.remove(prevBody);
            trackedBody = null;
            trackedBodyTop = null;
        }

        private void trackWindowHeader(FloatingHeaderStyle.WindowHeaderState state) {
            View nextOverlay = state == null ? null : state.overlay.get();
            View prevOverlay = trackedOverlay == null ? null : trackedOverlay.get();
            if (prevOverlay != nextOverlay) {
                if (prevOverlay != null) OWNED_OVERLAYS.remove(prevOverlay);
                trackedOverlay = nextOverlay == null ? null : new WeakReference<>(nextOverlay);
            }
            if (nextOverlay != null) OWNED_OVERLAYS.put(nextOverlay, Boolean.TRUE);

            View nextBody = state == null ? null : state.body.get();
            View prevBody = trackedBody == null ? null : trackedBody.get();
            if (prevBody != nextBody) {
                if (prevBody != null) LIFTED_TOP.remove(prevBody);
                trackedBody = nextBody == null ? null : new WeakReference<>(nextBody);
                trackedBodyTop = nextBody == null ? null : new int[]{state.bodyTop};
            } else if (nextBody != null && trackedBodyTop == null) {
                trackedBodyTop = new int[]{state.bodyTop};
            }
            if (nextBody != null) LIFTED_TOP.put(nextBody, trackedBodyTop);
        }

        private boolean underlayDrifted(View page, View bar) {
            if (windowHeader != null) {
                View body = windowHeader.body.get();
                if (body != null && body.getPaddingTop() != 0) return true;
                View overlay = windowHeader.overlay.get();
                if (overlay != null && Boolean.FALSE.equals(FloatingHeaderStyle.isOverlayMode(overlay))) return true;
            }
            for (View view : frosted.keySet()) {
                if (view.isAttachedToWindow() && view.getPaddingTop() != 0 && isDescendant(view, page)) return true;
            }
            int layoutBarBottom = bar.isShown() && bar.getHeight() > 0
                    ? NavigationBottomSpace.layoutTop(bar) + bar.getHeight() : 0;
            for (Map.Entry<View, int[]> entry : pads.entrySet()) {
                View view = entry.getKey();
                if (!view.isAttachedToWindow() || !isDescendant(view, page)) continue;
                if (view.getPaddingTop() != entry.getValue()[1]) return true;
                if (layoutBarBottom > 0 && NavigationBottomSpace.layoutTop(view) >= layoutBarBottom) return true;
            }
            return false;
        }

        private boolean syncUnderlay(View toolbar, boolean shown) {
            Activity activity = activityRef.get();
            View content = activity == null ? null : activity.findViewById(android.R.id.content);
            if (content == null || content.getWidth() <= 0) return false;
            View pager = pagerRef == null ? null : pagerRef.get();
            if (pager == null || !pager.isAttachedToWindow() || pager.getRootView() != content.getRootView()) {
                pager = homePager(content);
                if (pager == null) return false;
                pagerRef = new WeakReference<>(pager);
            }
            View bar = toolbar.getParent() instanceof View ? (View) toolbar.getParent() : null;
            if (bar == null) return false;

            // The host can repaint this while "Me" is showing, before the next toolbar has any
            // content. Keep the two native backgrounds transparent independently of the scan.
            Drawable background = bar.getBackground();
            if (background != null) {
                clearedBar = new WeakReference<>(bar); barBackground = background;
                bar.setBackground(null);
            }
            holdStatusStrip();

            View page = alignedPage(pager);
            if (page == null) {
                if (reveal != null) holdReveal(toolbar);
                revealFrames = 0;
                return false;
            }
            int slot = (page.getLeft() - pager.getPaddingLeft()) / page.getWidth();
            if (underlayPage == null || underlayPage.get() != page) {
                underlayPage = new WeakReference<>(page);
                nextScan = 0L;
                eager = Math.max(eager, 2);
                revealFrames = 0;
                cards.refreshGlass();
            }
            // Only the fourth home page owns its header. An empty toolbar elsewhere can be
            // transient, so never use it to remove the incoming page's padding.
            if (slot == 3) {
                holdReveal(toolbar);
                releaseOnScreen();
                return false;
            } else if (shown && !cards.empty()) {
                if (underlayDrifted(page, bar)) {
                    nextScan = 0L;
                    eager = Math.max(eager, 2);
                }
                boolean mutated = underlay(activity, content, bar, page, slot);
                if (holdingReveal && reveal == null && selectedPage(pager, slot)) {
                    // Let the inset pass lay out the destination before the glass samples it.
                    if (revealFrames++ == 0 || revealFrames < 4 && (page.isLayoutRequested() || toolbar.isLayoutRequested()))
                        toolbar.postInvalidateOnAnimation();
                    else revealToolbar(toolbar);
                }
                return mutated;
            } else {
                nextScan = 0L;
                revealFrames = 0;
                return false;
            }
        }

        private static boolean selectedPage(View pager, int slot) {
            try { return ((Number) XposedHelpers.callMethod(pager, "getCurrentItem")).intValue() == slot; }
            catch (RuntimeException | LinkageError unavailable) { return true; }
        }

        private void holdReveal(View toolbar) {
            if (reveal != null) { reveal.cancel(); reveal = null; }
            if (!holdingReveal) originalTransitionAlpha = toolbar.getTransitionAlpha();
            holdingReveal = true;
            if (toolbar.getTransitionAlpha() != 0f) toolbar.setTransitionAlpha(0f);
        }

        private void revealToolbar(View toolbar) {
            if (!ValueAnimator.areAnimatorsEnabled()) { finishReveal(toolbar); return; }
            // Fade the native actions and the card drawable together, leaving host alpha alone.
            ValueAnimator animation = ValueAnimator.ofFloat(0f, originalTransitionAlpha);
            reveal = animation;
            animation.setDuration(140L);
            animation.addUpdateListener(frame -> {
                if (reveal != frame) return;
                if (frame.getAnimatedFraction() >= 1f) finishReveal(toolbar);
                else toolbar.setTransitionAlpha((Float) frame.getAnimatedValue());
            });
            animation.start();
        }

        private void finishReveal(View toolbar) {
            if (reveal != null) { ValueAnimator animation = reveal; reveal = null; animation.cancel(); }
            if (holdingReveal && toolbar != null) toolbar.setTransitionAlpha(originalTransitionAlpha);
            holdingReveal = false;
            revealFrames = 0;
        }

        private static View alignedPage(View pager) {
            if (!(pager instanceof ViewGroup)) return null;
            int width = pager.getWidth() - pager.getPaddingLeft() - pager.getPaddingRight();
            int left = pager.getScrollX() + pager.getPaddingLeft();
            ViewGroup group = (ViewGroup) pager;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                if (width > 0 && child.getWidth() == width && child.getLeft() == left && child.isShown())
                    return child;
            }
            return null;
        }

        private void watch(View toolbar) {
            ViewTreeObserver next = toolbar.getViewTreeObserver();
            if (observer == next && next.isAlive()) return;
            if (observer != null && observer.isAlive()) observer.removeOnPreDrawListener(this);
            observer = next.isAlive() ? next : null;
            if (observer != null) observer.addOnPreDrawListener(this);
        }

        @Override public boolean onPreDraw() {
            View toolbar = toolbarRef.get();
            if (toolbar == null || cards == null) return true;
            Drawable current = toolbar.getBackground();
            if (current != cards) {
                // WeChat replaced the toolbar background (theme or tab change): keep it underneath.
                cards.wrap(current);
                toolbar.setBackground(cards);
            }
            if (!toolbar.isShown() || toolbar.getWidth() <= 0) {
                toolbarHidden = true;
                preDrawSkips = 0;
                if (overlayContent) syncUnderlay(toolbar, false);
                return true;
            }
            syncTitle(toolbar);
            boolean moved = cards.layout(toolbar);
            boolean layoutMutated = overlayContent && syncUnderlay(toolbar, true);
            if (moved) toolbar.invalidate();
            settleGlass(toolbar);
            if (layoutMutated && preDrawSkips < 2) {
                preDrawSkips++;
                return false;
            }
            preDrawSkips = 0;
            return true;
        }

        /** True when the view's horizontal centre is on screen: it belongs to the current page. */
        private static boolean centredOnScreen(View view, int[] at, int screenWidth) {
            int centre = at[0] + view.getWidth() / 2;
            return centre >= 0 && centre < screenWidth;
        }

        /** Undoes padding and lifted containers of the page now on screen (a page without the bar). */
        private void releaseOnScreen() {
            if (pads.isEmpty() && frosted.isEmpty()) return;
            Activity activity = activityRef.get();
            View content = activity == null ? null : activity.findViewById(android.R.id.content);
            int width = content == null ? 0 : content.getWidth();
            if (width <= 0) return;
            int[] at = new int[2];
            boolean changed = false;
            for (java.util.Iterator<java.util.Map.Entry<View, int[]>> it = pads.entrySet().iterator(); it.hasNext(); ) {
                java.util.Map.Entry<View, int[]> entry = it.next();
                View view = entry.getKey();
                if (!view.isAttachedToWindow()) continue;
                view.getLocationInWindow(at);
                if (!centredOnScreen(view, at, width)) continue;
                unpad(view, entry.getValue());
                it.remove(); changed = true;
            }
            for (java.util.Iterator<java.util.Map.Entry<View, int[]>> it = frosted.entrySet().iterator(); it.hasNext(); ) {
                java.util.Map.Entry<View, int[]> entry = it.next();
                View view = entry.getKey();
                if (!view.isAttachedToWindow()) continue;
                view.getLocationInWindow(at);
                if (!centredOnScreen(view, at, width)) continue;
                LIFTED_TOP.remove(view);
                if (view.getPaddingTop() == 0)
                    setPaddingTop(view, entry.getValue()[0]);
                it.remove(); changed = true;
            }
            if (changed) {
                // The bar's page is rescanned as soon as the bar shows again.
                lastBarBottom = Integer.MIN_VALUE; nextScan = 0L;
                if (releaseLogs++ < 3) log("无顶栏页面：已还原其顶部内边距");
            }
        }
        int releaseLogs;

        /**
         * Pulling the list down at its top opens WeChat's "最近" page: the host slides and fades
         * the action bar and lays the page over the area the cards sample. The sampler then has
         * no usable backdrop and falls back to the plain fill; once the bar is back at rest it
         * only recovered on the next host content change (a scroll, or waiting for the page to
         * hide). When the bar stops moving or shows again, re-sample now and once more after the
         * page's own delayed hide.
         */
        private void settleGlass(View toolbar) {
            int[] at = new int[2];
            toolbar.getLocationInWindow(at);
            boolean wasHidden = toolbarHidden;
            toolbarHidden = false;
            if (lastToolbarY != Integer.MIN_VALUE && at[1] != lastToolbarY) {
                toolbarMoving = true;
            } else if (toolbarMoving || wasHidden) {
                toolbarMoving = false;
                cards.refreshGlass();
                toolbar.removeCallbacks(glassRetry);
                toolbar.postDelayed(glassRetry, 320L);
            }
            lastToolbarY = at[1];
        }

        /**
         * HomeUI repaints the window status bar (tab switch, pull-down page, action bar colour)
         * with an opaque colour, so the list running under the cards stopped at the status bar as
         * a solid strip. While the home pages are shown it stays transparent; a chat opened inside
         * LauncherUI keeps whatever its own page sets.
         */
        private void holdStatusStrip() {
            Activity activity = activityRef.get();
            if (activity == null || activity.getWindow() == null || windowHeader == null) return;
            View chat = chatting == null ? null : chatting.get();
            if (chat != null && chat.isShown()) return;
            android.view.Window window = activity.getWindow();
            int color = window.getStatusBarColor();
            if (color != Color.TRANSPARENT) {
                statusColor = color; statusOwned = true;
                window.setStatusBarColor(Color.TRANSPARENT);
            }
            for (java.util.Map.Entry<View, Drawable> entry : masks.entrySet()) {
                View mask = entry.getKey(); Drawable background = mask.getBackground();
                if (background != null) { entry.setValue(background); mask.setBackground(null); }
            }
        }

        /**
         * Lets the page content run under the bar: the ActionBarContainer loses its background
         * (the Toolbar's is already skipped by {@link Cards}), the overlay layout goes into
         * overlay mode as the floating chat header does, and every scroll container the bar now
         * covers gets the bar as top padding with clipToPadding off, so its first row starts
         * below the bar but scrolls up underneath the cards. Padding is written only when the
         * wanted value changes (never per frame); the scan runs when the bar moves and at most
         * every {@link #SCAN_MS} otherwise, which also catches lazily created tab pages.
         *
         * <p>The conversation list is never padded: it already spans the whole page and keeps its
         * rows below the bar through its own scroll offset (status bar + action bar), which every
         * native {@code setSelection} adds on top of the list padding. Padding it pushed every
         * host reset (group switch, closing the pull-down page, layout changes) one bar height
         * too low, and the host then slid the action bar down by the same amount.</p>
         */
        private boolean underlay(Activity activity, View content, View bar, View page, int slot) {
            if (!bar.isShown() || bar.getHeight() <= 0) return false;
            int[] at = new int[2];
            bar.getLocationInWindow(at);
            int barTop = at[1];
            int barBottom = at[1] + bar.getHeight();
            long now = SystemClock.uptimeMillis();
            if (barBottom == lastBarBottom && now < nextScan && eager == 0) return false;
            if (eager > 0) eager--;
            lastBarBottom = barBottom; nextScan = now + SCAN_MS;

            boolean first = windowHeader == null;
            View prevOverlay = windowHeader == null ? null : windowHeader.overlay.get();
            View prevBody = windowHeader == null ? null : windowHeader.body.get();
            Boolean wasOverlayMode = prevOverlay != null ? FloatingHeaderStyle.isOverlayMode(prevOverlay) : null;
            int prevBodyTop = prevBody != null ? prevBody.getPaddingTop() : 0;

            boolean mutated = false;
            selfPaddingDepth++;
            selfOverlayDepth++;
            try {
                windowHeader = FloatingHeaderStyle.applyWindowHeader(null, bar, activity, windowHeader);
            } finally {
                selfOverlayDepth--;
                selfPaddingDepth--;
            }
            if (windowHeader != null) {
                View curOverlay = windowHeader.overlay.get();
                View curBody = windowHeader.body.get();
                if (first) {
                    if (curOverlay != null && Boolean.FALSE.equals(windowHeader.overlayMode)
                            && Boolean.TRUE.equals(FloatingHeaderStyle.isOverlayMode(curOverlay))) {
                        mutated = true;
                    }
                    if (curBody != null && windowHeader.bodyTop != 0 && curBody.getPaddingTop() == 0) {
                        mutated = true;
                    }
                } else {
                    if (curOverlay != null && Boolean.FALSE.equals(wasOverlayMode)
                            && Boolean.TRUE.equals(FloatingHeaderStyle.isOverlayMode(curOverlay))) {
                        mutated = true;
                    }
                    if (curBody != null && prevBodyTop != 0 && curBody.getPaddingTop() == 0) {
                        mutated = true;
                    }
                }
                if (trackedBody != null && curBody == trackedBody.get()
                        && trackedBodyTop != null && prevBodyTop != 0) {
                    trackedBodyTop[0] = prevBodyTop;
                }
            }
            trackWindowHeader(windowHeader);
            if (first) eager = 6; // the content moves up only on the next layout
            // Only with the bar at rest: during a pull it slides down and the strip is not its own.
            if (bar.getTranslationY() == 0f && barTop > 0) findStatusMasks(content, bar, barTop);

            boolean report = !dumped && eager == 0;
            StringBuilder covered = report ? new StringBuilder() : null;
            StringBuilder padded = report ? new StringBuilder() : null;
            int layoutBarBottom = NavigationBottomSpace.layoutTop(bar) + bar.getHeight();
            // Give Contacts and Discover a little space between the cards and their first row.
            int topGap = slot == 1 || slot == 2
                    ? Math.round(CONTACTS_DISCOVER_TOP_GAP_DP * page.getResources().getDisplayMetrics().density) : 0;
            ArrayDeque<View> queue = new ArrayDeque<>(); queue.add(page);
            for (int visited = 0; !queue.isEmpty() && visited < 400; visited++) {
                View view = queue.removeFirst();
                if (!view.isShown() || view.getWidth() <= 0 || view.getHeight() <= 0) continue;
                if (CHATTING.equals(view.getClass().getName())) { chatting = new WeakReference<>(view); continue; }
                // The host translates or stretches the list during a bounce; neither changes its inset.
                int top = NavigationBottomSpace.layoutTop(view);
                if (top >= layoutBarBottom) continue;
                view.getLocationInWindow(at);
                if (!centredOnScreen(view, at, content.getWidth())) continue;
                if (is(view.getClass(), CONVERSATION_LIST)) {
                    // Host-managed offset (see above). Drop padding an older build may have left.
                    int[] state = pads.remove(view);
                    if (state != null) unpad(view, state);
                    hideDivider(view);
                    hideReplica((ViewGroup) view);
                    if (padded != null) padded.append(" native:").append(view.getClass().getSimpleName()).append('@').append(top);
                    continue;
                }
                if (is(view.getClass(), FROSTED) && !containsList(view)) {
                    // Contacts / Discover: the page sits below the bar inside the frosted container's
                    // top padding (status bar + action bar), so nothing ever ran under the bar or the
                    // status bar. Drop that padding; the list inside then starts at the window top
                    // and gets the bar as its own top padding on the next scan.
                    if (liftFrosted(view)) {
                        mutated = true;
                        if (padded != null) padded.append(" frosted@").append(top);
                    }
                }
                if (scroller(view)) {
                    if (view.getWidth() >= content.getWidth() * 0.6f && top + view.getHeight() > layoutBarBottom) {
                        if (pad((ViewGroup) view, layoutBarBottom - top + topGap)) mutated = true;
                        if (padded != null) padded.append(' ').append(view.getClass().getSimpleName()).append('@').append(top);
                    }
                    continue;
                }
                if (view instanceof ViewGroup) {
                    ViewGroup group = (ViewGroup) view;
                    for (int i = 0; i < group.getChildCount(); i++) queue.addLast(group.getChildAt(i));
                } else if (covered != null && covered.length() < 400) {
                    // A fixed view the bar now hides (banner, header…): reported once for tuning.
                    covered.append(' ').append(view.getClass().getSimpleName()).append('@').append(top).append('+').append(view.getHeight());
                }
            }
            if (report) {
                dumped = true;
                View overlay = windowHeader == null ? null : windowHeader.overlay.get();
                log("underlay barBottom=" + barBottom + " overlay=" + (overlay == null ? "none" : overlay.getClass().getSimpleName())
                        + " chain=" + chain(bar) + " padded=[" + padded + " ] covered=[" + covered + " ]"
                        + " statusBar=" + Integer.toHexString(statusOwned ? statusColor : activity.getWindow().getStatusBarColor())
                        + " masks=" + masks.size());
            }
            return mutated;
        }

        private static final String CHATTING = "com.tencent.mm.pluginsdk.ui.chat.ChattingUILayout";
        private static final String CONVERSATION_LIST = "com.tencent.mm.ui.conversation.ConversationListView";
        private static final String FROSTED = "com.tencent.mm.ui.FrostedContentView";

        /** The pull-down page header's copy of the action bar, and its alpha before we hid it. */
        final WeakHashMap<View, Float> replicas = new WeakHashMap<>();
        int replicaLogs;

        /**
         * The conversation list's first header (the pull-down "最近" page) carries its own copy
         * of the action bar: title "微信", search and "+" at exactly the native bar's position
         * (seen in a UI dump: actionbar_up_indicator / text1 / meb / plus_icon inside the list).
         * Behind an opaque bar it was never visible; with the transparent bar it shows through
         * as a second, unhidden title. It is made transparent while the overlay runs.
         */
        private void hideReplica(ViewGroup list) {
            // The list detaches and re-attaches its header while scrolling, so the copy is not
            // always found under the list; each one found stays hidden until the overlay is
            // released (restoring and re-hiding it every scan made it flash).
            View current = findReplica(list);
            if (current == null) return;
            if (!replicas.containsKey(current)) {
                replicas.put(current, current.getAlpha());
                if (replicaLogs++ < 2) log("下拉页顶栏副本已隐藏 " + current.getClass().getSimpleName());
            }
            if (current.getAlpha() != 0f) current.setAlpha(0f);
        }

        private void restoreReplica() {
            for (java.util.Map.Entry<View, Float> entry : replicas.entrySet()) {
                View view = entry.getKey();
                if (view.getAlpha() == 0f) view.setAlpha(entry.getValue());
            }
            replicas.clear();
        }

        private static View findReplica(ViewGroup list) {
            int id = list.getResources().getIdentifier("actionbar_up_indicator", "id", list.getContext().getPackageName());
            if (id == 0) return null;
            View indicator = list.findViewById(id);
            return indicator != null && indicator.getParent() instanceof View ? (View) indicator.getParent() : null;
        }

        private static boolean isDescendant(View view, View root) {
            for (View current = view; current != null;
                 current = current.getParent() instanceof View ? (View) current.getParent() : null)
                if (current == root) return true;
            return false;
        }

        /** Frosted page containers whose top padding we removed: {host padding}. */
        final WeakHashMap<View, int[]> frosted = new WeakHashMap<>();
        /** The conversation list's hairline under the native bar, and its alpha before we hid it. */
        WeakReference<android.graphics.Paint> dividerPaint;
        WeakReference<View> dividerList;
        int dividerAlpha;

        private boolean liftFrosted(View view) {
            int top = view.getPaddingTop();
            int[] state = frosted.get(view);
            if (state == null) {
                state = new int[]{top};
                frosted.put(view, state);
            } else if (top != 0) {
                // The host may write its padding again (insets, multi-window): keep its latest value.
                state[0] = top;
            }
            LIFTED_TOP.put(view, state);
            if (top != 0) {
                setPaddingTop(view, 0);
                eager = Math.max(eager, 3); // the page moves up on the next layout; pad its list then
                return true;
            }
            return false;
        }

        /** The chats page manages its own offset; never lift a frosted container holding it. */
        private static boolean containsList(View root) {
            ArrayDeque<View> queue = new ArrayDeque<>(); queue.add(root);
            for (int visited = 0; !queue.isEmpty() && visited < 160; visited++) {
                View view = queue.removeFirst();
                if (is(view.getClass(), CONVERSATION_LIST)) return true;
                if (view instanceof AbsListView || CHATTING.equals(view.getClass().getName())) continue;
                if (view instanceof ViewGroup) {
                    ViewGroup group = (ViewGroup) view;
                    for (int i = 0; i < group.getChildCount(); i++) queue.addLast(group.getChildAt(i));
                }
            }
            return false;
        }

        /**
         * ConversationListView.dispatchDraw draws a hairline at its scroll offset (status bar +
         * action bar), i.e. under the native bar; with floating cards it is a stray line across
         * the page. Its paint is the list's only final Paint with a stroke width.
         */
        private void hideDivider(View list) {
            android.graphics.Paint paint = dividerPaint == null ? null : dividerPaint.get();
            if (paint == null || (dividerList == null ? null : dividerList.get()) != list) {
                showDivider();
                paint = findDividerPaint(list);
                if (paint == null) return;
                dividerPaint = new WeakReference<>(paint); dividerList = new WeakReference<>(list);
                dividerAlpha = paint.getAlpha();
            }
            if (paint.getAlpha() != 0) {
                dividerAlpha = paint.getAlpha();
                paint.setAlpha(0);
                list.invalidate();
            }
        }

        private void showDivider() {
            android.graphics.Paint paint = dividerPaint == null ? null : dividerPaint.get();
            View list = dividerList == null ? null : dividerList.get();
            if (paint != null && paint.getAlpha() == 0) {
                paint.setAlpha(dividerAlpha);
                if (list != null) list.invalidate();
            }
            dividerPaint = null; dividerList = null;
        }

        private static final java.util.Map<Class<?>, java.lang.reflect.Field> DIVIDER_FIELDS = new java.util.HashMap<>();

        private static android.graphics.Paint findDividerPaint(View list) {
            try {
                java.lang.reflect.Field field;
                synchronized (DIVIDER_FIELDS) {
                    field = DIVIDER_FIELDS.get(list.getClass());
                    if (field == null && !DIVIDER_FIELDS.containsKey(list.getClass())) {
                        for (Class<?> type = list.getClass(); type != null && type != android.widget.ListView.class && field == null;
                             type = type.getSuperclass()) {
                            for (java.lang.reflect.Field candidate : type.getDeclaredFields()) {
                                if (candidate.getType() != android.graphics.Paint.class
                                        || !java.lang.reflect.Modifier.isFinal(candidate.getModifiers())
                                        || java.lang.reflect.Modifier.isStatic(candidate.getModifiers())) continue;
                                candidate.setAccessible(true);
                                Object value = candidate.get(list);
                                if (value instanceof android.graphics.Paint && ((android.graphics.Paint) value).getStrokeWidth() > 0f) {
                                    field = candidate; break;
                                }
                            }
                        }
                        DIVIDER_FIELDS.put(list.getClass(), field);
                        log(field == null ? "首页分割线画笔未找到，保持原样" : "首页分割线已隐藏 " + field.getName());
                    }
                }
                Object value = field == null ? null : field.get(list);
                return value instanceof android.graphics.Paint ? (android.graphics.Paint) value : null;
            } catch (ReflectiveOperationException | RuntimeException error) {
                return null;
            }
        }

        /**
         * Views that paint only the status-bar strip above the bar: full width, starting at the
         * window top and no taller than the strip (HomeUI's status mask and its pull-down twin).
         * Lists, the bar itself and an open chat are never candidates.
         */
        private void findStatusMasks(View content, View bar, int strip) {
            if (content == null) return;
            int[] at = new int[2];
            ArrayDeque<View> queue = new ArrayDeque<>(); queue.add(content);
            for (int visited = 0; !queue.isEmpty() && visited < 400; visited++) {
                View view = queue.removeFirst();
                if (view == bar || !view.isShown() || view.getWidth() <= 0 || view.getHeight() <= 0) continue;
                String name = view.getClass().getName();
                if (CHATTING.equals(name)) { chatting = new WeakReference<>(view); continue; }
                if (view instanceof AbsListView || scroller(view)) continue;
                view.getLocationInWindow(at);
                if (at[1] >= strip) continue; // starts below the strip: neither it nor its children
                if (view != content && at[1] <= 0 && view.getHeight() <= strip + 2 && view.getHeight() * 2 >= strip
                        && view.getWidth() >= content.getWidth() * 0.9f) {
                    Drawable background = view.getBackground();
                    if (background != null && !masks.containsKey(view)) {
                        masks.put(view, background);
                        view.setBackground(null);
                        log("status mask cleared " + view.getClass().getSimpleName() + " h=" + view.getHeight());
                    }
                    continue;
                }
                if (view instanceof ViewGroup) {
                    ViewGroup group = (ViewGroup) view;
                    for (int i = 0; i < group.getChildCount(); i++) queue.addLast(group.getChildAt(i));
                }
            }
        }

        /** The home tab pager (same classes the floating bottom bar looks for). */
        private static View homePager(View content) {
            View known = NavigationBarFeature.pager();
            if (known != null && known.isAttachedToWindow() && known.getRootView() == content.getRootView()) return known;
            ArrayDeque<View> queue = new ArrayDeque<>(); queue.add(content);
            for (int visited = 0; !queue.isEmpty() && visited < 400; visited++) {
                View view = queue.removeFirst();
                String name = view.getClass().getName();
                if ("com.tencent.mm.ui.mogic.WxViewPager".equals(name) || "com.tencent.mm.ui.base.CustomViewPager".equals(name)) return view;
                if (CHATTING.equals(name) || view instanceof AbsListView) continue;
                if (view instanceof ViewGroup) {
                    ViewGroup group = (ViewGroup) view;
                    for (int i = 0; i < group.getChildCount(); i++) queue.addLast(group.getChildAt(i));
                }
            }
            return null;
        }

        private static boolean scroller(View view) {
            if (!(view instanceof ViewGroup)) return false;
            if (view instanceof AbsListView || view instanceof ScrollView) return true;
            // Known list types only: a generic canScrollVertically() match would also take a
            // pull-down wrapper around the real list, whose padding just pushes the list down.
            Class<?> type = view.getClass();
            return is(type, "androidx.recyclerview.widget.RecyclerView") || is(type, "androidx.core.widget.NestedScrollView");
        }

        private boolean pad(ViewGroup view, int inset) {
            int[] state = pads.get(view);
            if (state == null) {
                state = new int[]{view.getPaddingTop(), Integer.MIN_VALUE, view.getClipToPadding() ? 1 : 0};
                pads.put(view, state);
            } else if (view.getPaddingTop() != state[1]) {
                state[0] = view.getPaddingTop(); // the host rewrote its own padding
            }
            int wanted = state[0] + Math.max(0, inset);
            boolean changed = false;
            if (view.getPaddingTop() != wanted) {
                view.setPadding(view.getPaddingLeft(), wanted, view.getPaddingRight(), view.getPaddingBottom());
                changed = true;
            }
            state[1] = wanted;
            if (view.getClipToPadding()) view.setClipToPadding(false);
            return changed;
        }

        /** Gives a padded container back its own top padding and clip, unless the host changed them. */
        private static void unpad(View view, int[] state) {
            if (view.getPaddingTop() == state[1])
                view.setPadding(view.getPaddingLeft(), state[0], view.getPaddingRight(), view.getPaddingBottom());
            if (state[2] == 1 && view instanceof ViewGroup && !((ViewGroup) view).getClipToPadding())
                ((ViewGroup) view).setClipToPadding(true);
        }

        private static String chain(View bar) {
            StringBuilder out = new StringBuilder();
            int[] at = new int[2];
            View current = bar;
            for (int level = 0; level < 5 && current != null; level++) {
                current.getLocationInWindow(at);
                out.append(level == 0 ? "" : " < ").append(current.getClass().getSimpleName())
                        .append('@').append(at[1]).append('+').append(current.getHeight()).append(" pt=").append(current.getPaddingTop());
                current = current.getParent() instanceof View ? (View) current.getParent() : null;
            }
            return out.toString();
        }

        private void releaseUnderlay() {
            for (java.util.Map.Entry<View, int[]> entry : pads.entrySet()) unpad(entry.getKey(), entry.getValue());
            pads.clear();
            for (java.util.Map.Entry<View, int[]> entry : frosted.entrySet()) {
                View view = entry.getKey();
                LIFTED_TOP.remove(view);
                if (view.getPaddingTop() == 0)
                    setPaddingTop(view, entry.getValue()[0]);
            }
            frosted.clear();
            showDivider();
            restoreReplica();
            for (java.util.Map.Entry<View, Drawable> entry : masks.entrySet()) {
                View mask = entry.getKey();
                if (mask.getBackground() == null) mask.setBackground(entry.getValue());
            }
            masks.clear();
            Activity activity = activityRef.get();
            if (statusOwned && activity != null && activity.getWindow() != null
                    && activity.getWindow().getStatusBarColor() == Color.TRANSPARENT)
                activity.getWindow().setStatusBarColor(statusColor);
            statusOwned = false; chatting = null;
            View body = trackedBody == null ? null : trackedBody.get();
            int latestBodyTop = trackedBodyTop == null ? 0 : trackedBodyTop[0];
            untrackWindowHeader();
            selfPaddingDepth++;
            selfOverlayDepth++;
            try {
                FloatingHeaderStyle.restoreWindowHeader(windowHeader);
            } finally {
                selfOverlayDepth--;
                selfPaddingDepth--;
            }
            if (body != null && latestBodyTop > 0 && body.getPaddingTop() != latestBodyTop
                    && windowHeader != null && body.getPaddingTop() == windowHeader.bodyTop) {
                setPaddingTop(body, latestBodyTop);
            }
            windowHeader = null;
            View bar = clearedBar == null ? null : clearedBar.get();
            if (bar != null && bar.getBackground() == null && barBackground != null) bar.setBackground(barBackground);
            clearedBar = null; barBackground = null;
            lastBarBottom = Integer.MIN_VALUE; nextScan = 0L; eager = 0; preDrawSkips = 0;
            pagerRef = underlayPage = null;
            View toolbar = toolbarRef.get();
            finishReveal(toolbar);
            if (toolbar != null) toolbar.removeCallbacks(glassRetry);
            if (cards != null && cards.transparent(false) && toolbar != null) toolbar.invalidateOutline();
        }

        /**
         * The tab title ("微信(3)", "通讯录", "发现") is hidden on every first-level page: the
         * floating cards replace it. It is made INVISIBLE, not GONE: the Toolbar keeps its layout
         * (no relayout per frame), and the left card, which only measures shown views, then
         * wraps the profile alone (or disappears when there is none).
         */
        private void syncTitle(View toolbar) {
            TextView title = findTitle(toolbar);
            TextView hidden = hiddenTitle == null ? null : hiddenTitle.get();
            if (hidden != null && hidden != title) { restoreTitle(); hidden = null; }
            if (title == null) return;
            if (hideTitle) {
                if (hidden == null) { hiddenTitle = new WeakReference<>(title); hiddenVisibility = title.getVisibility(); }
                if (title.getVisibility() == View.VISIBLE) title.setVisibility(View.INVISIBLE);
            } else if (hidden != null) {
                restoreTitle();
            }
        }

        private void restoreTitle() {
            TextView hidden = hiddenTitle == null ? null : hiddenTitle.get();
            if (hidden != null && hidden.getVisibility() == View.INVISIBLE) hidden.setVisibility(hiddenVisibility);
            hiddenTitle = null; hiddenVisibility = View.VISIBLE;
        }

        private TextView findTitle(View toolbar) {
            View profile = toolbar.findViewWithTag(PROFILE_TAG);
            ArrayDeque<View> queue = new ArrayDeque<>(); queue.add(toolbar);
            for (int visited = 0; !queue.isEmpty() && visited < 160; visited++) {
                View view = queue.removeFirst();
                if (view == profile) continue;
                // Our own INVISIBLE title must still be found, so isShown() is not checked here.
                if (view.getId() == android.R.id.text1 && view instanceof TextView) return (TextView) view;
                if (view instanceof ViewGroup) {
                    ViewGroup group = (ViewGroup) view;
                    for (int i = 0; i < group.getChildCount(); i++) queue.addLast(group.getChildAt(i));
                }
            }
            return null;
        }

        private static boolean isChatsTitle(CharSequence value) {
            if (value == null) return false;
            String text = value.toString().trim();
            if (text.isEmpty()) return false;
            // "微信", "微信(3)", "微信(连接中)"; English UI "WeChat(3)"; or the user's own title.
            return text.startsWith("微信") || text.startsWith("WeChat")
                    || (!customTitle.isEmpty() && text.equals(customTitle));
        }

        void dispose() {
            if (observer != null && observer.isAlive()) observer.removeOnPreDrawListener(this);
            observer = null;
            restoreTitle();
            releaseUnderlay();
            View toolbar = toolbarRef.get();
            if (cards != null) {
                cards.stop();
                if (toolbar != null && toolbar.getBackground() == cards) toolbar.setBackground(cards.original());
                cards = null;
                log("已还原");
            }
            for (ClipState clip : clips) clip.restore();
            clips.clear();
        }
    }

    private static final class ClipState {
        final ViewGroup view; final boolean clip, padding;
        ClipState(ViewGroup view) {
            this.view = view;
            clip = view.getClipChildren();
            padding = view.getClipToPadding();
        }
        void release() {
            if (view.getClipChildren()) view.setClipChildren(false);
            if (view.getClipToPadding()) view.setClipToPadding(false);
        }
        void restore() {
            if (!view.getClipChildren() && clip) view.setClipChildren(true);
            if (!view.getClipToPadding() && padding) view.setClipToPadding(true);
        }
    }

    /**
     * The Toolbar's background: the native background first, then the two cards. Padding,
     * outline and state come from the native background so the Toolbar's layout never changes.
     */
    static final class Cards extends Drawable implements Drawable.Callback {
        private final ChatGlassSurface glass;
        private final LiquidGlassDecoration leftShadow = new LiquidGlassDecoration();
        private final List<LiquidGlassDecoration> rightShadows = new ArrayList<>();
        final List<RectF> rightButtons = new ArrayList<>();
        private final List<RectF> nextButtons = new ArrayList<>();
        private final WeakHashMap<View, IconShift> shiftedIcons = new WeakHashMap<>();
        final RectF left = new RectF(), right = new RectF();
        private final RectF nextLeft = new RectF(), nextRight = new RectF();
        private final float density;
        private Drawable original;
        private int mode = ChatGlassSurface.PLAIN, blur;
        private String configured;
        /** The page content runs under the bar: skip the native background (and its outline shadow). */
        private boolean transparent;
        /** Progressive blur band behind the status bar and the cards ({@link ProgressiveTopBlur}). */
        private final ProgressiveTopBlur band = new ProgressiveTopBlur();
        private boolean progressive;
        /** Backdrop owner for the band while the cards themselves are plain (no card sampler runs). */
        private ChatGlassSurface bandBackdrop;
        private final int[] hostAt = new int[2], barAt = new int[2];

        Cards(View toolbar) {
            glass = new ChatGlassSurface(toolbar);
            density = toolbar.getResources().getDisplayMetrics().density;
        }

        Drawable original() { return original; }

        void wrap(Drawable background) {
            if (background == this) return;
            if (original != null) original.setCallback(null);
            original = background;
            if (original != null) { original.setCallback(this); original.setBounds(getBounds()); original.setState(getState()); }
        }

        void material(int mode, int blur) {
            this.mode = mode; this.blur = blur; configured = null;
            if (mode != ChatGlassSurface.PLAIN) releaseBandBackdrop();
        }

        void progressive(boolean value) {
            if (progressive == value) return;
            progressive = value;
            if (!value) { band.release(); releaseBandBackdrop(); }
            invalidateSelf();
        }

        private void releaseBandBackdrop() {
            if (bandBackdrop != null) { bandBackdrop.stop(); bandBackdrop = null; }
        }

        /** True when it changed. */
        boolean transparent(boolean value) {
            if (transparent == value) return false;
            transparent = value; invalidateSelf();
            return true;
        }

        void stop() {
            glass.stop(); leftShadow.release(); band.release(); releaseBandBackdrop();
            for (LiquidGlassDecoration shadow : rightShadows) shadow.release();
            restoreShiftedIcons();
        }

        /** No card content at all: the bar of a page with its own full-height header ("我"). */
        boolean empty() { return left.isEmpty() && right.isEmpty(); }

        /** Re-sample the backdrop now (the bar came back to rest after the host moved it). */
        void refreshGlass() {
            glass.refresh();
            if (bandBackdrop != null) bandBackdrop.refresh();
            invalidateSelf();
        }

        String describe() {
            return "left=" + Math.round(left.left) + ".." + Math.round(left.right)
                    + " right=" + Math.round(right.left) + ".." + Math.round(right.right)
                    + " height=" + Math.round(Math.max(left.height(), right.height()));
        }

        /** Recomputes both cards from the live views; true when they moved. */
        boolean layout(View toolbar) {
            int width = toolbar.getWidth(), height = toolbar.getHeight();
            nextLeft.setEmpty(); nextRight.setEmpty();
            nextButtons.clear();
            if (width > 0 && height > 0) {
                float top = toolbar.getPaddingTop(), bottom = height - toolbar.getPaddingBottom();
                float available = Math.max(0f, bottom - top);
                float cardHeight = Math.min(available - 8f * density, 52f * density);
                float centerY = (top + bottom) / 2f;
                if (cardHeight >= 28f * density) {
                    View profile = toolbar.findViewWithTag(PROFILE_TAG);
                    // Left card: profile and title in one card. Either may be missing (profile off,
                    // title hidden by the host), then the card wraps whichever is there.
                    RectF content = profile != null && profile.isShown() ? profileExtent(toolbar, profile) : null;
                    RectF titleRun = title(toolbar, width, profile);
                    if (titleRun != null && !titleRun.isEmpty()) {
                        if (content == null) content = titleRun; else content.union(titleRun);
                    }
                    // Both cards share one height (and so one corner radius): the taller of the
                    // default and the left content, never taller than the bar.
                    if (content != null && !content.isEmpty())
                        cardHeight = Math.min(available, Math.max(cardHeight, content.height() + 8f * density));
                    if (content != null && !content.isEmpty()) {
                        nextLeft.set(content.left - 8f * density, centerY - cardHeight / 2f,
                                content.right + 14f * density, centerY + cardHeight / 2f);
                    }
                    actions(toolbar, profile, width, centerY, cardHeight);
                    float edge = 8f * density;
                    clampX(nextLeft, edge, width - edge);
                    clampX(nextRight, edge, width - edge);
                    if (!nextLeft.isEmpty() && !nextRight.isEmpty() && nextLeft.right > nextRight.left - edge)
                        nextLeft.right = nextRight.left - edge;
                    if (nextLeft.width() < nextLeft.height()) nextLeft.setEmpty();
                } else {
                    restoreShiftedIcons();
                }
            } else {
                restoreShiftedIcons();
            }
            if (nextLeft.equals(left) && nextRight.equals(right)
                    && nextButtons.equals(rightButtons)) return false;
            left.set(nextLeft); right.set(nextRight);
            rightButtons.clear(); rightButtons.addAll(nextButtons);
            while (rightShadows.size() < rightButtons.size()) rightShadows.add(new LiquidGlassDecoration());
            while (rightShadows.size() > rightButtons.size()) rightShadows.remove(rightShadows.size() - 1).release();
            return true;
        }

        /**
         * The profile's drawn content. The profile is normally the Compose toolbar, which has no
         * ImageView/TextView children for {@link #extent}, so with the chats title hidden the
         * left card used to vanish. Fall back to the profile's own box: the composable is a
         * wrap-content row with 16dp start / 4dp end padding (the name column adds 5dp more)
         * around a 32dp avatar, so trim those and keep an avatar-high band around its centre.
         */
        private RectF profileExtent(View toolbar, View profile) {
            RectF content = extent(toolbar, profile);
            if (content != null) return content;
            if (profile.getWidth() <= 0 || profile.getHeight() <= 0) return null;
            RectF box = bounds(toolbar, profile);
            float half = 18f * density, centerY = box.centerY();
            RectF run = new RectF(box.left + 16f * density, Math.max(box.top, centerY - half),
                    box.right - 10f * density, Math.min(box.bottom, centerY + half));
            return run.width() > 0f && run.height() > 0f ? run : null;
        }

        /** Keep equal-sized circles centered in the native action slots and translate inner icons to match. */
        private void actions(View toolbar, View profile, int width, float centerY, float cardHeight) {
            List<ActionSlot> slots = new ArrayList<>();
            ArrayDeque<View> queue = new ArrayDeque<>(); queue.add(toolbar);
            for (int visited = 0; !queue.isEmpty() && visited < 160; visited++) {
                View view = queue.removeFirst();
                if (view == profile || !view.isShown() || view.getWidth() <= 0 || view.getHeight() <= 0) continue;
                if (view != toolbar && view.isClickable() && view.getWidth() <= width * 0.35f) {
                    RectF b = bounds(toolbar, view);
                    if (b.centerX() > width * 0.55f) {
                        slots.add(new ActionSlot(new RectF(b), view, firstIcon(view)));
                    }
                    continue; // an action's own children are part of it
                }
                if (view instanceof ViewGroup) {
                    ViewGroup group = (ViewGroup) view;
                    for (int i = 0; i < group.getChildCount(); i++) queue.addLast(group.getChildAt(i));
                }
            }
            slots.sort((a, b) -> Float.compare(a.anchor.centerX(), b.anchor.centerX()));
            if (slots.isEmpty()) { restoreShiftedIcons(); return; }

            // Match the left card's height while preserving only the visual hairline between
            // adjacent actions. These are the native 144px hit slots, so an 8dp constraint here
            // needlessly shrinks both circles and makes them look smaller than the left card.
            float edge = 4f * density;
            float gap = Math.max(2f, Math.round(density * 2f));
            float radius = cardHeight / 2f;
            for (int i = 0; i < slots.size(); i++) {
                RectF anchor = slots.get(i).anchor;
                float x = anchor.centerX();
                radius = Math.min(radius, Math.min(x - edge, width - edge - x));
                if (i > 0) radius = Math.min(radius, (x - slots.get(i - 1).anchor.centerX() - gap) / 2f);
            }
            if (radius < 12f * density) { restoreShiftedIcons(); return; }

            Set<View> activeIcons = new HashSet<>();
            for (int i = 0; i < slots.size(); i++) {
                ActionSlot slot = slots.get(i);
                float x = slot.anchor.centerX();
                RectF circle = new RectF(x - radius, centerY - radius, x + radius, centerY + radius);
                nextRight.union(circle);
                nextButtons.add(circle);
                View icon = slot.icon;
                if (icon != null && icon != slot.action) {
                    activeIcons.add(icon);
                    IconShift shift = shiftedIcons.get(icon);
                    if (shift == null) {
                        shift = new IconShift(icon, toolbar);
                        shiftedIcons.put(icon, shift);
                    }
                    shift.align(toolbar, circle);
                }
            }
            for (Iterator<Map.Entry<View, IconShift>> it = shiftedIcons.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<View, IconShift> entry = it.next();
                if (!activeIcons.contains(entry.getKey())) {
                    entry.getValue().restore();
                    it.remove();
                }
            }
        }

        private void restoreShiftedIcons() {
            for (IconShift shift : shiftedIcons.values()) shift.restore();
            shiftedIcons.clear();
        }

        private static final class ActionSlot {
            final RectF anchor; final View action, icon;
            ActionSlot(RectF anchor, View action, View icon) {
                this.anchor = anchor; this.action = action; this.icon = icon;
            }
        }

        private static final class IconShift {
            final View icon;
            float originalTx, appliedTx, originalTy, appliedTy;
            final List<ClipState> clips = new ArrayList<>();
            IconShift(View icon, View toolbar) {
                this.icon = icon;
                originalTx = appliedTx = icon.getTranslationX();
                originalTy = appliedTy = icon.getTranslationY();
                for (View current = icon.getParent() instanceof View ? (View) icon.getParent() : null;
                     current != null && current != toolbar;
                     current = current.getParent() instanceof View ? (View) current.getParent() : null) {
                    if (current instanceof ViewGroup) {
                        ClipState state = new ClipState((ViewGroup) current);
                        state.release();
                        clips.add(state);
                    }
                }
            }
            void align(View toolbar, RectF circle) {
                for (ClipState state : clips) state.release();
                RectF ib = bounds(toolbar, icon);
                float visX = ib.left + icon.getPaddingLeft()
                        + (ib.width() - icon.getPaddingLeft() - icon.getPaddingRight()) / 2f;
                float visY = ib.top + icon.getPaddingTop()
                        + (ib.height() - icon.getPaddingTop() - icon.getPaddingBottom()) / 2f;
                float curTx = icon.getTranslationX();
                if (curTx != appliedTx) originalTx = curTx;
                float dx = circle.centerX() - visX;
                if (Math.abs(dx) > 0.25f) {
                    float nextTx = curTx + dx;
                    icon.setTranslationX(nextTx);
                    appliedTx = nextTx;
                } else {
                    appliedTx = curTx;
                }
                float curTy = icon.getTranslationY();
                if (curTy != appliedTy) originalTy = curTy;
                float dy = circle.centerY() - visY;
                if (Math.abs(dy) > 0.25f) {
                    float nextTy = curTy + dy;
                    icon.setTranslationY(nextTy);
                    appliedTy = nextTy;
                } else {
                    appliedTy = curTy;
                }
            }
            void restore() {
                if (Math.abs(icon.getTranslationX() - appliedTx) < 0.1f) icon.setTranslationX(originalTx);
                if (Math.abs(icon.getTranslationY() - appliedTy) < 0.1f) icon.setTranslationY(originalTy);
                for (ClipState state : clips) state.restore();
                clips.clear();
            }
        }

        private static View firstIcon(View root) {
            if (root instanceof ImageView && ((ImageView) root).getDrawable() != null) return root;
            if (!(root instanceof ViewGroup)) return null;
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View icon = firstIcon(group.getChildAt(i));
                if (icon != null) return icon;
            }
            return null;
        }

        /** The native title ("微信(1)", "通讯录"…), when it is compact text; never inside {@code skip}. */
        private RectF title(View toolbar, int width, View skip) {
            ArrayDeque<View> queue = new ArrayDeque<>(); queue.add(toolbar);
            for (int visited = 0; !queue.isEmpty() && visited < 160; visited++) {
                View view = queue.removeFirst();
                if (view == skip || !view.isShown()) continue;
                if (view.getId() == android.R.id.text1 && view instanceof TextView) {
                    RectF text = extent(toolbar, view);
                    return text != null && text.width() < width * 0.6f ? text : null;
                }
                if (view instanceof ViewGroup) {
                    ViewGroup group = (ViewGroup) view;
                    for (int i = 0; i < group.getChildCount(); i++) queue.addLast(group.getChildAt(i));
                }
            }
            return null;
        }

        /** Union of the visible images and the actual text runs under {@code root}. */
        private static RectF extent(View toolbar, View root) {
            RectF union = new RectF();
            ArrayDeque<View> queue = new ArrayDeque<>(); queue.add(root);
            for (int visited = 0; !queue.isEmpty() && visited < 64; visited++) {
                View view = queue.removeFirst();
                if (!view.isShown() || view.getWidth() <= 0 || view.getHeight() <= 0) continue;
                RectF part = null;
                if (view instanceof TextView) part = textRun(toolbar, (TextView) view);
                else if (view instanceof ImageView && ((ImageView) view).getDrawable() != null) part = bounds(toolbar, view);
                if (part != null && !part.isEmpty()) { if (union.isEmpty()) union.set(part); else union.union(part); }
                if (view instanceof ViewGroup) {
                    ViewGroup group = (ViewGroup) view;
                    for (int i = 0; i < group.getChildCount(); i++) queue.addLast(group.getChildAt(i));
                }
            }
            return union.isEmpty() ? null : union;
        }

        /** The laid-out text, not the TextView box (which may be match_parent). */
        private static RectF textRun(View toolbar, TextView text) {
            CharSequence value = text.getText();
            if (value == null || value.length() == 0) return null;
            RectF box = bounds(toolbar, text);
            Layout layout = text.getLayout();
            if (layout == null || layout.getLineCount() == 0) return box;
            float start = Float.MAX_VALUE, end = -Float.MAX_VALUE;
            for (int i = 0; i < layout.getLineCount(); i++) {
                start = Math.min(start, layout.getLineLeft(i));
                end = Math.max(end, layout.getLineRight(i));
            }
            float offset = text.getTotalPaddingLeft() - text.getScrollX();
            return new RectF(box.left + offset + start, box.top, Math.min(box.right, box.left + offset + end), box.bottom);
        }

        private static RectF bounds(View toolbar, View view) {
            int[] origin = new int[2], at = new int[2];
            toolbar.getLocationInWindow(origin); view.getLocationInWindow(at);
            float x = at[0] - origin[0], y = at[1] - origin[1];
            return new RectF(x, y, x + view.getWidth(), y + view.getHeight());
        }

        private static void clampX(RectF rect, float min, float max) {
            if (rect.isEmpty()) return;
            rect.left = Math.max(min, rect.left);
            rect.right = Math.min(max, rect.right);
            if (rect.right <= rect.left) rect.setEmpty();
        }

        @Override public void draw(Canvas canvas) {
            if (original != null && !transparent) original.draw(canvas);
            View host = glassHost();
            boolean dark = host != null && SettingsTheme.isDark(host.getContext());
            // The "我" page has no cards and its own header image up to the window top: no band.
            if (transparent && progressive && host != null && !empty()) drawBand(canvas, host, dark);
            if (left.isEmpty() && right.isEmpty()) return;
            float radius = Math.max(left.height(), right.height()) / 2f;
            String signature = mode + ":" + blur + ":" + dark + ":" + radius;
            if (!signature.equals(configured)) {
                glass.configure(dark ? 0xFF242424 : 0xFFF7F7F7, radius, density, dark, mode, blur);
                configured = signature;
            }
            card(canvas, leftShadow, left, dark);
            for (int i = 0; i < rightButtons.size(); i++) {
                card(canvas, rightShadows.get(i), rightButtons.get(i), dark);
            }
        }

        /**
         * The band spans from the window top (above this Toolbar, over the status bar: the bar's
         * ancestors no longer clip children) to {@link ProgressiveTopBlur#HEIGHT_FACTOR} times the
         * bar's bottom, under the cards. A pull on the list slides the bar down; the band fades
         * with it so the opening "最近" page is not covered by a band left mid-screen.
         */
        private void drawBand(Canvas canvas, View host, boolean dark) {
            View bar = host.getParent() instanceof View ? (View) host.getParent() : host;
            host.getLocationInWindow(hostAt);
            bar.getLocationInWindow(barAt);
            int top = -hostAt[1];
            int barBottom = barAt[1] + bar.getHeight() - hostAt[1];
            if (barBottom <= top) return;
            int bottom = top + Math.round((barBottom - top) * ProgressiveTopBlur.HEIGHT_FACTOR);
            float pulled = Math.abs(bar.getTranslationY()) / Math.max(1f, bar.getHeight());
            float alpha = Math.max(0f, 1f - pulled);
            band.draw(canvas, bandSampler(host), host.getWidth(), top, bottom, density, dark, alpha);
        }

        /** The card glass's own sampler when it runs; a private one while the cards are plain. */
        private ViewBackdropSampler bandSampler(View host) {
            if (mode != ChatGlassSurface.PLAIN) return glass.sharedSampler();
            if (bandBackdrop == null) {
                bandBackdrop = new ChatGlassSurface(host);
                bandBackdrop.configure(0, 0f, density, false, ChatGlassSurface.FROSTED, 0);
            }
            return bandBackdrop.sharedSampler();
        }

        private View glassHost() { return getCallback() instanceof View ? (View) getCallback() : null; }

        private void card(Canvas canvas, LiquidGlassDecoration shadow, RectF rect, boolean dark) {
            if (rect.isEmpty()) return;
            float radius = rect.height() / 2f;
            shadow.dropShadow(canvas, rect, radius, density, dark);
            glass.configure(dark ? 0xFF242424 : 0xFFF7F7F7, radius, density, dark, mode, blur);
            glass.setBounds((int) Math.floor(rect.left), (int) Math.floor(rect.top),
                    (int) Math.ceil(rect.right), (int) Math.ceil(rect.bottom));
            glass.draw(canvas);
        }

        @Override protected void onBoundsChange(Rect bounds) { if (original != null) original.setBounds(bounds); }
        @Override public boolean isStateful() { return original != null && original.isStateful(); }
        @Override protected boolean onStateChange(int[] state) { return original != null && original.setState(state); }
        @Override public boolean getPadding(Rect padding) {
            if (original != null) return original.getPadding(padding);
            padding.set(0, 0, 0, 0); return false;
        }
        @Override public void getOutline(Outline outline) {
            if (transparent) { outline.setEmpty(); return; }
            if (original != null) original.getOutline(outline); else super.getOutline(outline);
        }
        @Override public void setAlpha(int alpha) { if (original != null) original.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter filter) { if (original != null) original.setColorFilter(filter); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }

        @Override public void invalidateDrawable(Drawable who) { invalidateSelf(); }
        @Override public void scheduleDrawable(Drawable who, Runnable what, long when) { scheduleSelf(what, when); }
        @Override public void unscheduleDrawable(Drawable who, Runnable what) { unscheduleSelf(what); }
    }
}
