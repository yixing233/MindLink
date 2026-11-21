package wex.ui.core.feature;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.RecordingCanvas;
import android.graphics.RenderNode;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.AbsListView;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import de.robv.android.xposed.XposedHelpers;

/** Native four-page navigation with a background-only glass layer, adapted from WeKit (GPL-3.0-only). */
final class NavigationBarViews {
    private static final String TAG_BAR = "mdl_nav_bar_v2";
    private static final WeakHashMap<ViewGroup, WeakReference<Session>> SESSIONS = new WeakHashMap<>();

    private NavigationBarViews() { }

    static void install(ViewGroup nativeBar, View pager, Object adapter) {
        WeakReference<Session> reference = SESSIONS.get(nativeBar);
        Session session = reference == null ? null : reference.get();
        if (session != null && !session.disposed && session.pagerRef.get() == pager && session.adapterRef.get() == adapter) return;
        if (session != null) session.dispose();
        Session next = new Session(nativeBar, pager, adapter);
        try {
            next.attach();
            SESSIONS.put(nativeBar, new WeakReference<>(next));
            NavigationBarFeature.log("替换栏已挂载：保留四个原生标签与监听器");
        } catch (RuntimeException | Error error) { next.dispose(); throw error; }
    }

    static void refresh() {
        for (Session session : sessions()) session.reload();
    }

    static void remove(Activity activity) {
        for (Session session : sessions()) {
            View host = session.nativeRef.get();
            if (host != null && ChatToolbarLifecycle.findActivity(host.getContext()) == activity) session.dispose();
        }
    }

    static void lifecycle(Activity activity, boolean started) {
        for (Session session : sessions()) {
            View host = session.nativeRef.get();
            if (host != null && ChatToolbarLifecycle.findActivity(host.getContext()) == activity) {
                session.bar.syncForeground();
                if (!started) session.bar.lifecycleStarted = false;
                session.bar.updateVisibility();
                if (started) { session.bar.syncSelected(); session.frosted.refresh(); }
                else session.frosted.release();
            }
        }
    }

    static void badges(Object nativeBar) {
        Session session = forHost(nativeBar);
        if (session != null) session.bar.queueBadges();
    }

    static void badges() {
        for (Session session : sessions()) session.bar.queueBadges();
    }

    static void scrolled(Object adapter, int position, float offset) {
        for (Session session : sessions()) if (session.adapterRef.get() == adapter) session.bar.pageScrolled(position, offset);
    }

    static long beginSelection(Object adapter) {
        for (Session session : sessions()) if (session.adapterRef.get() == adapter) return ++session.bar.selectionEpoch;
        return -1;
    }

    static void selected(Object adapter, int position, long epoch) {
        for (Session session : sessions()) if (session.adapterRef.get() == adapter && session.bar.selectionEpoch == epoch)
            session.bar.pageSelected(position);
    }

    static void scrollState(Object adapter, int state) {
        for (Session session : sessions()) if (session.adapterRef.get() == adapter) session.bar.pageScrollState(state);
    }

    static Object switchRequested(Object pager, int position, boolean tabTap) {
        for (Session session : sessions()) if (session.pagerRef.get() == pager) return session.bar.switchRequested(position, tabTap);
        return null;
    }

    static void switchFinished(Object pager, Object token, boolean success) {
        for (Session session : sessions()) if (session.pagerRef.get() == pager && token instanceof SwitchRequest)
            session.bar.switchFinished((SwitchRequest) token, success);
    }

    private static final class SwitchRequest {
        final int position, previousTarget;
        final boolean tabTap, previousConfirmed, previousScrolling;
        final SwitchRequest previous;
        SwitchRequest(int position, boolean tabTap, NavigationBarView bar) {
            this.position = position; this.tabTap = tabTap;
            previous = bar.switchRequest; previousTarget = bar.clickTarget;
            previousConfirmed = bar.clickConfirmed; previousScrolling = bar.clickScrolling;
        }
    }

    static void nativeSelected(Object nativeBar, int position) {
        Session session = forHost(nativeBar);
        if (session != null) {
            // setTo is also called by intermediate onPageScrolled(offset == 0) frames.
            // It styles a native tab; it is not authoritative for the pager's destination.
            session.bar.removeCallbacks(session.bar.reconcileSelection);
            session.bar.post(session.bar.reconcileSelection);
        }
    }

    static void listScroll(AbsListView list, int position, int top) {
        listScroll(list, position, top, list.getChildAt(0) == null);
    }

    static void listScroll(AbsListView list, int position, int top, boolean empty) {
        Session session = containing(list);
        if (session == null || session.bar.selected != 0 || session.bar.sampler.isCapturing()) return;
        session.bar.sourceListChanged(list, position, top, empty);
        if (!empty && session.config.floating && session.config.autoHide) {
            session.bar.setScrollHidden(session.bar.scroll.update(position, top));
        }
    }

    static void listScrollState(AbsListView list, boolean touchScroll) {
        Session session = containing(list);
        if (session != null) session.bar.scroll.touchScroll(touchScroll);
    }

    static void recentHidden(boolean hidden) {
        for (Session session : sessions()) {
            session.bar.recentHidden = hidden;
            session.bar.updateVisibility();
        }
    }

    static void frostedUpdated(View view, Integer requested) {
        if (NavigationFrostedController.writing(view)) return;
        for (Session session : sessions()) session.frosted.updated(view, requested);
    }

    static void frostedDrawing(View view) {
        if (NavigationFrostedController.writing(view)) return;
        for (Session session : sessions()) session.frosted.beforeDraw(view);
    }

    private static Session forHost(Object nativeBar) {
        WeakReference<Session> reference = SESSIONS.get(nativeBar);
        Session session = reference == null ? null : reference.get();
        return session == null || session.disposed ? null : session;
    }

    private static Session containing(View view) {
        for (Session session : sessions()) {
            View pager = session.pagerRef.get();
            for (View current = view; current != null; current = current.getParent() instanceof View ? (View) current.getParent() : null) {
                if (current == pager) return session;
            }
        }
        return null;
    }

    private static List<Session> sessions() {
        List<Session> result = new ArrayList<>();
        for (WeakReference<Session> reference : new ArrayList<>(SESSIONS.values())) {
            Session session = reference.get();
            if (session != null && !session.disposed) result.add(session);
        }
        return result;
    }

    private static final class ClipState {
        final boolean children, padding;
        ClipState(ViewGroup parent) { children = parent.getClipChildren(); padding = parent.getClipToPadding(); }
        void restore(ViewGroup parent) {
            if (!parent.getClipChildren()) parent.setClipChildren(children);
            if (!parent.getClipToPadding()) parent.setClipToPadding(padding);
        }
    }

    private static final class PaddingState {
        int bottom, last;
        final boolean clip;
        PaddingState(ViewGroup view) { bottom = last = view.getPaddingBottom(); clip = view.getClipToPadding(); }
        void apply(ViewGroup view, int wanted) { apply(view, wanted, false); }
        void apply(ViewGroup view, int wanted, boolean exact) {
            if (view.getPaddingBottom() != last) bottom = view.getPaddingBottom();
            last = exact ? wanted : Math.max(bottom, wanted);
            if (view.getPaddingBottom() != last) view.setPadding(view.getPaddingLeft(), view.getPaddingTop(), view.getPaddingRight(), last);
            if (view.getClipToPadding()) view.setClipToPadding(false);
        }
        void restore(ViewGroup view) {
            if (view.getPaddingBottom() == last) view.setPadding(view.getPaddingLeft(), view.getPaddingTop(), view.getPaddingRight(), bottom);
            if (!view.getClipToPadding()) view.setClipToPadding(clip);
        }
    }

    static final class Session {
        final WeakReference<ViewGroup> nativeRef;
        final WeakReference<View> pagerRef;
        final WeakReference<Object> adapterRef;
        final NativeTabBridge nativeTabs;
        final NavigationBarView bar;
        final int originalVisibility;
        final WeakHashMap<View, Integer> children = new WeakHashMap<>();
        final WeakHashMap<ViewGroup, ClipState> clips = new WeakHashMap<>();
        final WeakHashMap<ViewGroup, PaddingState> paddings = new WeakHashMap<>();
        final WeakHashMap<ViewGroup, ClipState> contactClips = new WeakHashMap<>();
        final NavigationContactViewport contactViewport = new NavigationContactViewport();
        final NavigationFrostedController frosted;
        NavigationBarFeature.Config config;
        boolean disposed, moving, nativeOwned;
        int appliedVisibility, nativeReserveHeight;
        final View.OnAttachStateChangeListener nativeListener = new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) { }
            @Override public void onViewDetachedFromWindow(View view) {
                bar.post(() -> {
                    ViewGroup host = nativeRef.get();
                    if (!moving && (host == null || !host.isAttachedToWindow())) dispose();
                });
            }
        };

        Session(ViewGroup nativeBar, View pager, Object adapter) {
            nativeRef = new WeakReference<>(nativeBar); pagerRef = new WeakReference<>(pager); adapterRef = new WeakReference<>(adapter);
            nativeTabs = new NativeTabBridge(nativeBar);
            originalVisibility = appliedVisibility = nativeBar.getVisibility();
            config = NavigationBarFeature.config();
            bar = new NavigationBarView(this, nativeBar, pager);
            frosted = new NavigationFrostedController(new NavigationFrostedController.Home() {
                @Override public Activity activity() {
                    View host = nativeRef.get();
                    return host == null ? null : ChatToolbarLifecycle.findActivity(host.getContext());
                }
                @Override public View pager() { return pagerRef.get(); }
                @Override public View nativeBar() { return nativeRef.get(); }
                @Override public View overlay() { return bar; }
                @Override public boolean active() {
                    return !disposed && nativeOwned && config.enabled && config.floating && bar.lifecycleStarted
                            && NavigationBarFeature.resumed(activity());
                }
            }, NavigationBarFeature::log);
            nativeBar.addOnAttachStateChangeListener(nativeListener);
        }

        void reload() {
            config = NavigationBarFeature.config();
            if (!config.enabled) { dispose(); return; }
            attach(); bar.reload();
        }

        void attach() {
            if (disposed || moving) return;
            ViewGroup nativeBar = nativeRef.get(); View pager = pagerRef.get();
            if (nativeBar == null || pager == null || !(pager.getParent() instanceof ViewGroup)) throw new IllegalStateException("Navigation host detached");
            ViewGroup parent = config.floating ? (ViewGroup) pager.getParent() : nativeBar;
            if (!(parent instanceof FrameLayout) && !(parent instanceof RelativeLayout)) throw new IllegalStateException("Unsupported navigation overlay parent");
            if (bar.getParent() != parent) {
                moving = true;
                try {
                    if (bar.getParent() instanceof ViewGroup) ((ViewGroup) bar.getParent()).removeView(bar);
                    ViewGroup.LayoutParams params;
                    if (parent instanceof FrameLayout) {
                        params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
                    } else {
                        RelativeLayout.LayoutParams relative = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                        relative.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM); params = relative;
                    }
                    parent.addView(bar, params);
                } finally { moving = false; }
            }
            bar.sampler.source(config.floating ? parent : pager, pager);
            if (!clips.containsKey(parent)) clips.put(parent, new ClipState(parent));
            parent.setClipChildren(false); parent.setClipToPadding(false);
            bar.setElevation(Math.max(nativeBar.getElevation(), pager.getElevation()) + bar.dp(4));
            bar.syncForeground();
            bar.updateVisibility();
            bar.endSpaceDirty = true;
            // Do not remove the usable native tabs until the overlay has a visible, laid-out frame.
            if (nativeOwned) ownNativeBar();
            if (!config.floating) { restorePaddings(); restoreContactViewport(); }
            if (nativeOwned) frosted.refresh();
        }

        void ownNativeBar() {
            ViewGroup nativeBar = nativeRef.get();
            if (nativeBar == null || disposed) return;
            if (nativeBar.getVisibility() == View.VISIBLE && nativeBar.getId() == NavigationContactViewport.NATIVE_ID
                    && nativeBar.getHeight() > 0) nativeReserveHeight = nativeBar.getHeight();
            nativeOwned = true;
            appliedVisibility = config.floating ? View.GONE : View.VISIBLE;
            if (nativeBar.getVisibility() != appliedVisibility) nativeBar.setVisibility(appliedVisibility);
            for (int i = 0; i < nativeBar.getChildCount(); i++) {
                View child = nativeBar.getChildAt(i);
                if (child == bar) continue;
                if (!children.containsKey(child)) children.put(child, child.getVisibility());
                if (child.getVisibility() != View.INVISIBLE) child.setVisibility(View.INVISIBLE);
            }
        }

        void updateEndSpace() {
            // WeKit ReplaceNavigationBar semantics: the floating bar overlays the pager as a
            // BOTTOM-aligned sibling and never writes padding into the conversation list.
            // Those per-frame writes were the direct cause of the list jumping on every bar
            // or layout event. The contacts page (updateContactViewport/applyEndSpace) keeps
            // its own settled reserve in the same map and is managed separately, so this
            // path writes nothing and restores nothing.
        }

        /** Constant-size page probe each pre-draw also catches lazy attach and host LP changes. */
        boolean updateContactViewport() {
            View pager = pagerRef.get();
            Activity activity = ChatToolbarLifecycle.findActivity(bar.getContext());
            if (!config.enabled || !config.floating || !nativeOwned || pager == null
                    || !bar.lifecycleStarted || !NavigationBarFeature.homeVisible(activity, pager)) {
                if (bar.selected == 1) contactViewport.diagnose("session-gate", pager, pager, nativeReserveHeight);
                return false;
            }
            // Do not acquire dimensions from a partially visible offscreen page or an early selected callback.
            if (bar.pagerScrolling || bar.pagerOffset || bar.selected != 1) {
                if (bar.selected == 1) contactViewport.diagnose("pager-moving", pager, pager, nativeReserveHeight);
                return false;
            }
            ViewGroup list = NavigationContactViewport.currentList(pager, adapterRef.get(), contactViewport, nativeReserveHeight);
            if (list == null) return false;
            boolean pending = contactViewport.update(list, pager, nativeReserveHeight);
            if (contactViewport.ownsReserve()) {
                View current = list.getParent() instanceof View ? (View) list.getParent() : null;
                for (int depth = 0; current instanceof ViewGroup && current != pager && depth < 8; depth++) {
                    ViewGroup group = (ViewGroup) current;
                    if (!contactClips.containsKey(group)) contactClips.put(group, new ClipState(group));
                    group.setClipChildren(false); group.setClipToPadding(false);
                    current = current.getParent() instanceof View ? (View) current.getParent() : null;
                }
            }
            if (!pending && bar.isShown()) applyEndSpace(list);
            contactViewport.diagnose(pending ? "layout-pending" : "settled", list, pager, nativeReserveHeight);
            return pending;
        }

        private void applyEndSpace(ViewGroup list) {
            // Contacts (page 1) still own a real bottom reserve through the viewport probe;
            // that list is a RecyclerView under the contacts fragment, not the conversation
            // ListView, and its reserve is settled once rather than rewritten per frame.
            int[] barPosition = new int[2], position = new int[2];
            bar.getLocationInWindow(barPosition); list.getLocationInWindow(position);
            int surfaceTop = barPosition[1] + Math.round(bar.surface.top);
            int extra = Math.max(0, position[1] + list.getHeight() - surfaceTop + bar.dp(6));
            PaddingState state = paddings.get(list);
            if (state == null) { state = new PaddingState(list); paddings.put(list, state); }
            // Contacts use the now-expanded viewport rather than retaining an obsolete larger end reserve.
            state.apply(list, extra, true);
        }

        private void restoreContactViewport() {
            contactViewport.restore();
            for (Map.Entry<ViewGroup, ClipState> entry : contactClips.entrySet()) entry.getValue().restore(entry.getKey());
            contactClips.clear();
        }

        private void restorePaddings() {
            for (Map.Entry<ViewGroup, PaddingState> entry : paddings.entrySet()) entry.getValue().restore(entry.getKey());
            paddings.clear();
        }

        void dispose() {
            if (disposed) return;
            disposed = true;
            ViewGroup host = nativeRef.get();
            if (host != null) {
                host.removeOnAttachStateChangeListener(nativeListener);
                if (host.getVisibility() == appliedVisibility) host.setVisibility(originalVisibility);
                SESSIONS.remove(host);
            }
            for (Map.Entry<View, Integer> entry : children.entrySet()) {
                if (entry.getKey().getVisibility() == View.INVISIBLE) entry.getKey().setVisibility(entry.getValue());
            }
            children.clear(); restorePaddings(); restoreContactViewport();
            frosted.release();
            for (Map.Entry<ViewGroup, ClipState> entry : clips.entrySet()) entry.getValue().restore(entry.getKey());
            clips.clear();
            bar.stop();
            if (bar.getParent() instanceof ViewGroup) ((ViewGroup) bar.getParent()).removeView(bar);
        }
    }

    static final class NavigationBarView extends FrameLayout implements ViewTreeObserver.OnPreDrawListener {
        final Session session;
        final ViewBackdropSampler sampler;
        final LiquidGlassLayer baseGlass = new LiquidGlassLayer(), indicatorGlass = new LiquidGlassLayer();
        final LiquidGlassDecoration decoration = new LiquidGlassDecoration();
        final LiquidGlassMotion motion = new LiquidGlassMotion();
        final GlassGravity gravity = new GlassGravity(this);
        final RenderNode combined = new RenderNode("MindLink-liquid-combined");
        final RenderNode lens = new RenderNode("MindLink-liquid-indicator-frame");
        final NavigationScrollState scroll = new NavigationScrollState();
        final RectF surface = new RectF(), indicator = new RectF();
        final List<TabTarget> targets = new ArrayList<>();
        final int[] counts = {-1, -1, -1, -1};
        final boolean[] dots = new boolean[4];
        private ViewTreeObserver observer;
        private float density, scaledDensity, cellWidth;
        private int remainingNavigation;
        private final int[] rootBottomPosition = new int[2], parentBottomPosition = new int[2];
        private long frameTime;
        private boolean frameQueued, dragging, pointerDown, scrollHidden, hidden, suspended, fallbackLogged;
        private boolean activeCapture, endSpaceDirty = true, reattachQueued, pagerScrolling, pagerOffset;
        private boolean badgesQueued, liquidContent, baseRetryQueued, listKnown, listEmpty;
        private WeakReference<AbsListView> sourceList = new WeakReference<>(null);
        private int listPosition, listTop;
        private long baseRetryToken;
        private final Runnable reconcileBadges = new Runnable() {
            @Override public void run() {
                badgesQueued = false;
                if (!session.disposed && lifecycleStarted && !suspended && !hidden) refreshBadges();
            }
        };
        private final Runnable rebuildBase = new Runnable() {
            @Override public void run() {
                baseRetryQueued = false;
                if (activeCapture && !session.disposed && baseGlass.retryBudget() == baseRetryToken) invalidate();
            }
        };
        boolean recentHidden, lifecycleStarted;
        int selected;
        private long selectionEpoch;
        private SwitchRequest switchRequest;
        private int clickTarget = -1;
        private boolean clickConfirmed, clickScrolling;
        private float downX, downY, dragStart;
        private int pointerId = -1;
        private final int slop;
        private final Runnable reconcileSelection = new Runnable() {
            @Override public void run() { if (!session.disposed && lifecycleStarted) syncSelected(); }
        };
        private final Runnable animate = new Runnable() {
            @Override public void run() {
                frameQueued = false;
                if (!isAttachedToWindow() || session.disposed || !lifecycleStarted) return;
                long now = SystemClock.uptimeMillis();
                float dt = frameTime == 0 ? 0.016f : Math.min(0.064f, Math.max(0.001f, (now - frameTime) / 1000f));
                frameTime = now;
                boolean moving = motion.step(dt);
                invalidate(); for (TabTarget target : targets) target.invalidate();
                if (moving) queueFrame(); else frameTime = 0;
            }
        };

        NavigationBarView(Session session, ViewGroup nativeBar, View pager) {
            super(nativeBar.getContext()); this.session = session;
            sampler = new ViewBackdropSampler(pager, this);
            slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
            setTag(TAG_BAR); setWillNotDraw(false); setClipChildren(false); setClipToPadding(false);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            for (int i = 0; i < 4; i++) {
                TabTarget target = new TabTarget(i); targets.add(target);
                addView(target, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
            }
            try { selected = Math.max(0, Math.min(3, ((Number) XposedHelpers.callMethod(pager, "getCurrentItem")).intValue())); }
            catch (Throwable error) { selected = 0; }
            lifecycleStarted = NavigationBarFeature.resumed(ChatToolbarLifecycle.findActivity(getContext()));
            suspended = !NavigationBarFeature.homeVisible(ChatToolbarLifecycle.findActivity(getContext()), pager);
            motion.snap(selected);
            setOnApplyWindowInsetsListener((view, insets) -> { requestLayout(); endSpaceDirty = true; return insets; });
            setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(Math.round(surface.left), Math.round(surface.top), Math.round(surface.right),
                            Math.round(surface.bottom), session.config.floating ? surface.height() / 2f : 0f);
                    outline.setAlpha(0.2f);
                }
            });
            reload();
        }

        void reload() {
            density = getResources().getDisplayMetrics().density;
            scaledDensity = density * session.config.scale / 100f;
            if (session.config.nativeIcons) session.nativeTabs.reloadIcons();
            scroll.reset(); scrollHidden = false; endSpaceDirty = true;
            syncForeground(); syncSelected();
            refreshBadges(); requestLayout(); invalidate(); sampler.changed(); updateTargets(); updateVisibility();
        }

        int dp(float value) { return Math.round(value * density); }
        float px(float value) { return value * scaledDensity; }

        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            observer = getViewTreeObserver(); observer.addOnPreDrawListener(this);
            requestApplyInsets(); syncCapture();
        }

        @Override protected void onDetachedFromWindow() {
            if (!session.moving) session.frosted.release();
            stop(); super.onDetachedFromWindow();
        }

        void stop() {
            if (observer != null && observer.isAlive()) observer.removeOnPreDrawListener(this);
            observer = null; sampler.stop(); baseGlass.release(); indicatorGlass.release(); decoration.release(); gravity.stop(); activeCapture = false;
            combined.discardDisplayList(); lens.discardDisplayList();
            removeCallbacks(reconcileBadges); badgesQueued = false;
            removeCallbacks(rebuildBase); baseRetryQueued = false;
            sourceList.clear(); listKnown = false; liquidContent = false;
            removeCallbacks(animate); removeCallbacks(reconcileSelection); frameQueued = false; frameTime = 0;
            animate().cancel(); pointerDown = dragging = false; pointerId = -1;
            clearClickTarget();
            motion.snap(selected);
        }

        @Override public boolean onPreDraw() {
            if (session.disposed) return true;
            updateNavigationSpace();
            syncForeground();
            session.frosted.refresh();
            View pager = session.pagerRef.get();
            if (pager == null || !pager.isAttachedToWindow()) return true;
            ViewGroup desired = session.config.floating && pager.getParent() instanceof ViewGroup
                    ? (ViewGroup) pager.getParent() : session.nativeRef.get();
            if (getParent() != desired && !reattachQueued) {
                reattachQueued = true;
                post(() -> {
                    reattachQueued = false;
                    if (session.disposed) return;
                    try { session.attach(); }
                    catch (RuntimeException error) { NavigationBarFeature.log("底栏重新挂载失败 " + error.getClass().getSimpleName()); session.dispose(); }
                });
            }
            if (!pointerDown) syncSelected();
            if (!session.nativeOwned && !hidden && isShown() && getWidth() > 0 && getHeight() > 0 && getAlpha() > 0f) {
                session.ownNativeBar(); session.frosted.refresh();
            }
            syncCapture();
            if (!suspended && getHeight() > 0) {
                boolean viewportPending = session.updateContactViewport();
                if (endSpaceDirty) { endSpaceDirty = false; session.updateEndSpace(); }
                if (viewportPending) endSpaceDirty = true;
            }
            return true;
        }

        private void updateNavigationSpace() {
            if (!(getParent() instanceof View)) return;
            View parent = (View) getParent(), root = parent.getRootView();
            if (!parent.isAttachedToWindow() || parent.getHeight() <= 0 || root.getHeight() <= 0) return;
            WindowInsets insets = root.getRootWindowInsets();
            parent.getLocationInWindow(parentBottomPosition);
            root.getLocationInWindow(rootBottomPosition);
            int next = NavigationBottomSpace.remaining(rootBottomPosition[1] + root.getHeight(),
                    parentBottomPosition[1] + parent.getHeight(), parent.getPaddingBottom(),
                    insets == null ? 0 : insets.getInsets(WindowInsets.Type.navigationBars()).bottom);
            if (next == remainingNavigation) return;
            remainingNavigation = next;
            endSpaceDirty = true;
            requestLayout();
        }

        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            int extra = session.config.floating ? dp(20) : 0;
            // Only the post-layout parent inner bottom sets this cache. Never use old geometry during measure
            // and never add IME to a capsule in an already adjustResize-shrunken parent.
            setMeasuredDimension(MeasureSpec.getSize(widthSpec),
                    Math.round(px(session.config.floating ? 64 : 56)) + extra + remainingNavigation);
        }

        @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
            Insets insets = SystemBarsCoordinator.bars(this);
            float side = session.config.floating ? dp(12) : 0f;
            float available = getWidth() - insets.left - insets.right - side * 2;
            float width = session.config.floating ? Math.min(available, px(328)) : available;
            float left = insets.left + side + (available - width) / 2f;
            float top = session.config.floating ? dp(10) : 0f;
            float height = px(session.config.floating ? 64 : 56);
            surface.set(left, top, left + Math.max(0f, width), top + height);
            float padding = session.config.floating ? px(4) : 0f;
            cellWidth = Math.max(0f, (surface.width() - padding * 2f) / 4f);
            boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
            for (int i = 0; i < 4; i++) {
                int physical = rtl ? 3 - i : i;
                float x = surface.left + padding + physical * cellWidth;
                targets.get(i).layout(Math.round(x), Math.round(surface.top + padding), Math.round(x + cellWidth), Math.round(surface.bottom - padding));
            }
            endSpaceDirty = true; invalidateOutline(); sampler.changed();
        }

        @Override public void draw(Canvas canvas) {
            // Skip the entire module subtree, not merely backdrop.prepare(), during host recording.
            if (sampler.isCapturing()) return;
            super.draw(canvas);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (surface.width() <= 0f || surface.height() <= 0f) return;
            boolean floating = session.config.floating, dark = SettingsTheme.isDark(getContext());
            boolean liquid = floating && session.config.backdrop && session.config.liquid;
            float radius = floating ? surface.height() / 2f : 0f;
            if (floating) decoration.dropShadow(canvas, surface, radius, scaledDensity, dark);
            boolean sampled = baseGlass.drawBase(canvas, sampler, surface, radius, px(session.config.blur),
                    liquid, dark, scaledDensity, session.config.backdrop);
            if (session.config.backdrop && !sampled && !fallbackLogged) {
                fallbackLogged = true;
                NavigationBarFeature.log("当前背景不可采样，使用中性表面（不含实时模糊）");
            }
            if (sampled || !baseGlass.rebuildFailed()) {
                removeCallbacks(rebuildBase); baseRetryQueued = false;
            } else if (activeCapture) {
                long delay = baseGlass.retryDelay(), token = baseGlass.retryBudget();
                if (delay >= 0 && (!baseRetryQueued || token != baseRetryToken)) {
                    removeCallbacks(rebuildBase); baseRetryToken = token; baseRetryQueued = true;
                    postDelayed(rebuildBase, delay);
                }
            }
            if (liquid && sampled) {
                decoration.highlight(canvas, surface, radius, scaledDensity, 0.75f, gravity.x, gravity.y, -45f);
            }
        }

        private float indicatorCenter() {
            float physical = getLayoutDirection() == LAYOUT_DIRECTION_RTL ? 3f - motion.position() : motion.position();
            return surface.left + (session.config.floating ? px(4) : 0f) + (physical + 0.5f) * cellWidth;
        }

        @Override protected void dispatchDraw(Canvas canvas) {
            if (surface.width() <= 0f) return;
            boolean liquid = session.config.floating && session.config.backdrop && session.config.liquid;
            float progress = motion.pressProgress(), velocity = motion.velocity() / 10f;
            float width = cellWidth * (liquid ? motion.scaleX() / (1f - Math.max(-0.2f, Math.min(0.2f, velocity * 0.75f))) : 1f);
            float height = surface.height() - (session.config.floating ? px(8) : 0f);
            height *= liquid ? motion.scaleY() * (1f - Math.max(-0.2f, Math.min(0.2f, velocity * 0.25f))) : 1f;
            float center = indicatorCenter();
            indicator.set(center - width / 2f, surface.centerY() - height / 2f, center + width / 2f, surface.centerY() + height / 2f);
            boolean dark = SettingsTheme.isDark(getContext());
            float radius = Math.min(width, height) / 2f;
            boolean lensReady = false;
            if (liquid && canvas.isHardwareAccelerated()) {
                try {
                    combined.setPosition(0, 0, getWidth(), getHeight());
                    RecordingCanvas recording = combined.beginRecording(getWidth(), getHeight());
                    try {
                        baseGlass.drawSnapshot(recording);
                        for (TabTarget target : targets) {
                            int save = recording.save(); recording.translate(target.getLeft(), target.getTop());
                            target.drawContent(recording, true); recording.restoreToCount(save);
                        }
                    } finally { combined.endRecording(); }
                    // Commit the refracted foreground only after the actual indicator draw succeeded.
                    lens.setPosition(0, 0, getWidth(), getHeight());
                    RecordingCanvas foreground = lens.beginRecording(getWidth(), getHeight());
                    try {
                        lensReady = indicatorGlass.drawIndicator(foreground, combined, indicator, radius, progress,
                                dark, scaledDensity, true, session.config.blur == 0, 0xFF07C160);
                        if (lensReady) {
                            decoration.highlight(foreground, indicator, radius, scaledDensity, progress, gravity.x, gravity.y, 90f);
                            decoration.innerShadow(foreground, indicator, radius, scaledDensity, progress);
                        }
                    } finally { lens.endRecording(); }
                } catch (Throwable error) {
                    combined.discardDisplayList(); lens.discardDisplayList(); lensReady = false;
                }
            }
            if (liquidContent != lensReady) {
                liquidContent = lensReady;
                for (TabTarget target : targets) target.invalidate();
            }
            if (!lensReady) indicatorGlass.drawIndicator(canvas, null, indicator, radius, progress,
                    dark, scaledDensity, false, false, 0xFF07C160);
            super.dispatchDraw(canvas);
            if (lensReady) {
                try { canvas.drawRenderNode(lens); }
                catch (Throwable error) {
                    lens.discardDisplayList(); liquidContent = false;
                    indicatorGlass.drawIndicator(canvas, null, indicator, radius, progress,
                            dark, scaledDensity, false, false, 0xFF07C160);
                    for (TabTarget target : targets) target.invalidate();
                    super.dispatchDraw(canvas);
                }
            }
        }

        void queueBadges() {
            if (badgesQueued || session.disposed || !lifecycleStarted || suspended || hidden) return;
            badgesQueued = true; post(reconcileBadges);
        }

        void refreshBadges() {
            removeCallbacks(reconcileBadges); badgesQueued = false;
            boolean changed = false;
            for (int i = 0; i < 4; i++) {
                NativeTabBridge.Badge badge = session.nativeTabs.badge(i);
                if (counts[i] == badge.count && dots[i] == badge.dot) continue;
                counts[i] = badge.count; dots[i] = badge.dot; changed = true;
                targets.get(i).updateDescription(); targets.get(i).invalidate();
            }
            if (changed) { combined.discardDisplayList(); invalidate(); }
        }

        void sourceListChanged(AbsListView list, int position, int top, boolean empty) {
            if (!activeCapture || sampler.isCapturing()) return;
            if (listKnown && sourceList.get() == list && listPosition == position && listTop == top && listEmpty == empty) return;
            sourceList = new WeakReference<>(list); listKnown = true;
            listPosition = position; listTop = top; listEmpty = empty;
            sampler.sourceContentChanged();
        }

        void syncForeground() {
            Activity activity = ChatToolbarLifecycle.findActivity(getContext());
            boolean started = NavigationBarFeature.resumed(activity);
            boolean inactive = !NavigationBarFeature.homeVisible(activity, session.pagerRef.get());
            if (lifecycleStarted == started && suspended == inactive) return;
            lifecycleStarted = started; suspended = inactive;
            if (!started) { pagerScrolling = pagerOffset = false; clearClickTarget(); }
            updateVisibility();
        }

        SwitchRequest switchRequested(int position, boolean tabTap) {
            if (position < 0 || position > 3) return null;
            selectionEpoch++;
            SwitchRequest request = new SwitchRequest(position, tabTap, this);
            if (tabTap) {
                clickTarget = position; clickConfirmed = clickScrolling = false;
            } else if (position != clickTarget) clearClickTarget();
            switchRequest = request;
            return request;
        }

        void switchFinished(SwitchRequest request, boolean success) {
            if (switchRequest != request) return; // Reentrant newer requests, including same-index ones, win.
            View pager = session.pagerRef.get();
            int current = -1;
            try { current = ((Number) XposedHelpers.callMethod(pager, "getCurrentItem")).intValue(); }
            catch (Throwable ignored) { } // No getter/selected acknowledgement: do not lock motion.
            if (!success) {
                clearClickTarget();
                // Restore a failed nested request's prior goal only when the native getter still
                // confirms it; never resurrect a rejected outer request or a guessed tap.
                if (request.previousTarget >= 0 && current == request.previousTarget) {
                    clickTarget = request.previousTarget; clickConfirmed = request.previousConfirmed;
                    clickScrolling = request.previousScrolling;
                    switchRequest = request.previous;
                    confirmClickTarget(); pageSelected(current);
                }
                return;
            }
            if (request.tabTap && current == request.position) {
                clickTarget = request.position; confirmClickTarget(); pageSelected(current);
            } else if (request.tabTap && current >= 0 && current <= 3) clearClickTarget();
            if (switchRequest == request) switchRequest = null;
        }

        private void confirmClickTarget() {
            clickConfirmed = true;
            if (!pointerDown && motion.target() != clickTarget) { motion.animateTo(clickTarget); queueFrame(); }
        }

        private void clearClickTarget() { clickTarget = -1; clickConfirmed = clickScrolling = false; switchRequest = null; }

        void syncSelected() {
            View pager = session.pagerRef.get();
            if (pager == null || pagerScrolling || pagerOffset || clickScrolling) return;
            try {
                int position = ((Number) XposedHelpers.callMethod(pager, "getCurrentItem")).intValue();
                if (position < 0 || position > 3) return;
                if (clickTarget >= 0) {
                    if (position == clickTarget) confirmClickTarget();
                    else clearClickTarget();
                }
                if (position != selected) pageSelected(position);
                else if (!pointerDown && motion.target() != selected) {
                    // A whole-page sample or cancelled gesture can leave only the visual target stale.
                    motion.animateTo(selected); queueFrame();
                }
            } catch (Throwable ignored) { } // Optional host getter; keep the last confirmed selection.
        }

        void pageScrollState(int state) {
            if (state == 1 || state == 0) clearClickTarget();
            pagerScrolling = state != 0;
            if (!pagerScrolling) {
                pagerOffset = false; syncSelected();
                if (!pointerDown) { motion.animateTo(selected); queueFrame(); }
            }
        }

        void pageScrolled(int position, float offset) {
            if (position < 0 || position > 3) return;
            pagerOffset = offset > 0f && offset < 1f;
            if (clickConfirmed && (pagerOffset || position != clickTarget)) clickScrolling = true;
            if (!pointerDown && !clickConfirmed && (pagerScrolling || pagerOffset)) { motion.target(position + offset); queueFrame(); }
            // Without the optional state hook, only the confirmed endpoint's whole-page sample
            // ends this click. Intermediate integer pages must not regain getter/fraction authority.
            if (!pagerScrolling && clickConfirmed && position == clickTarget && offset == 0f) clearClickTarget();
            // Idle whole-page samples are not destinations; the getter may still be updating.
            // Reconcile only after that stack unwinds, and never during fractional motion.
            if (!pagerOffset) { removeCallbacks(reconcileSelection); post(reconcileSelection); }
            sampler.changed();
        }

        void pageSelected(int position) {
            if (position < 0 || position > 3) return;
            if (clickTarget == position) confirmClickTarget();
            else if (clickTarget >= 0) clearClickTarget();
            if (selected == position) return;
            selected = position; endSpaceDirty = true;
            // Selection can arrive at the start of a smooth scroll. Keep its fractional target.
            if (!pointerDown && !pagerScrolling && !pagerOffset) { motion.animateTo(position); queueFrame(); }
            if (position != 0) { scroll.reset(); scrollHidden = recentHidden = false; updateVisibility(); }
            updateTargets(); sampler.changed(); queueBadges();
        }

        void updateTargets() { for (TabTarget target : targets) { target.setSelected(target.index == selected); target.updateDescription(); target.invalidate(); } }
        void setScrollHidden(boolean value) { scrollHidden = value; updateVisibility(); }

        void updateVisibility() {
            boolean wanted = !lifecycleStarted || suspended || recentHidden
                    || session.config.floating && session.config.autoHide && scrollHidden;
            syncCapture();
            if (wanted == hidden && getVisibility() == (wanted ? INVISIBLE : VISIBLE)) return;
            hidden = wanted; animate().cancel();
            if (!wanted) { setVisibility(VISIBLE); endSpaceDirty = true; sampler.changed(); queueBadges(); }
            else {
                removeCallbacks(reconcileBadges); badgesQueued = false;
            }
            if (wanted && pointerDown) {
                MotionEvent cancel = MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), MotionEvent.ACTION_CANCEL, 0f, 0f, 0);
                try { dispatchTouchEvent(cancel); } finally { cancel.recycle(); }
            }
            animate().translationY(wanted ? getHeight() + dp(12) : 0f).alpha(wanted ? 0f : 1f)
                    .scaleX(wanted ? 0.85f : 1f).scaleY(wanted ? 0.85f : 1f)
                    .setDuration(suspended || !lifecycleStarted ? 0 : 300)
                    .withEndAction(() -> { if (hidden) setVisibility(INVISIBLE); syncCapture(); }).start();
            syncCapture();
        }

        private void syncCapture() {
            boolean active = lifecycleStarted && !suspended && !recentHidden && !hidden && isShown()
                    && session.config.backdrop && isAttachedToWindow();
            if (active != activeCapture) {
                activeCapture = active;
                if (active) sampler.start();
                else {
                    sampler.stop(); baseGlass.release(); indicatorGlass.release();
                    removeCallbacks(rebuildBase); baseRetryQueued = false;
                    sourceList.clear(); listKnown = false;
                }
            }
            gravity.enable(active && session.config.liquid && session.config.gravityHighlight);
            if (!lifecycleStarted) { removeCallbacks(animate); removeCallbacks(reconcileSelection); frameQueued = false; frameTime = 0; motion.snap(selected); }
        }

        private void queueFrame() {
            if (frameQueued || !isAttachedToWindow()) return;
            frameQueued = true; postOnAnimation(animate);
        }

        private int indexAt(float x) {
            if (cellWidth <= 0f) return selected;
            float padding = session.config.floating ? px(4) : 0f;
            int physical = Math.max(0, Math.min(3, (int) ((x - surface.left - padding) / cellWidth)));
            return getLayoutDirection() == LAYOUT_DIRECTION_RTL ? 3 - physical : physical;
        }

        @Override public boolean dispatchTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                if (hidden || !surface.contains(event.getX(), event.getY())) return false;
                clearClickTarget();
                pointerDown = true; dragging = false; pointerId = event.getPointerId(0);
                downX = event.getX(); downY = event.getY(); dragStart = indexAt(downX);
                motion.target(dragStart); motion.press(); queueFrame();
            }
            try { return super.dispatchTouchEvent(event); }
            finally {
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    pointerDown = dragging = false; pointerId = -1;
                    syncSelected();
                    if (clickConfirmed) motion.animateTo(clickTarget);
                    else if (!pagerScrolling && !pagerOffset) motion.target(selected);
                    motion.release(); queueFrame();
                }
            }
        }

        @Override public boolean onInterceptTouchEvent(MotionEvent event) {
            if (event.getActionMasked() == MotionEvent.ACTION_MOVE && pointerDown) {
                int index = event.findPointerIndex(pointerId);
                if (index < 0) return false;
                float dx = event.getX(index) - downX, dy = event.getY(index) - downY;
                if (!dragging && Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy)) {
                    dragging = true;
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
            }
            return dragging;
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            if (!pointerDown) return false;
            int index = event.findPointerIndex(pointerId);
            if (index < 0) { motion.target(selected); motion.release(); return true; }
            if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                float delta = (event.getX(index) - downX) / Math.max(1f, cellWidth);
                motion.target(dragStart + (getLayoutDirection() == LAYOUT_DIRECTION_RTL ? -delta : delta)); queueFrame();
            } else if (event.getActionMasked() == MotionEvent.ACTION_UP && dragging) {
                activate(Math.max(0, Math.min(3, Math.round(motion.target()))));
            }
            return true;
        }

        private void activate(int index) {
            try {
                performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
                NavigationBarFeature.fromTabTap(() -> {
                    if (!session.nativeTabs.click(index)) throw new IllegalStateException("Native tab listener is unavailable");
                });
                // Read only after the native listener returns; do not guess that a requested tab won.
                syncSelected();
                removeCallbacks(reconcileSelection); post(reconcileSelection);
            } catch (RuntimeException error) {
                NavigationBarFeature.log("原生标签点击失败 " + error.getClass().getSimpleName());
            }
        }

        private boolean longPress(int index) {
            if (session.nativeTabs.longClick(index)) return true;
            if (index != 2) return false;
            Activity activity = ChatToolbarLifecycle.findActivity(getContext());
            if (activity == null) return false;
            try {
                Intent intent = new Intent().setClassName("com.tencent.mm", "com.tencent.mm.plugin.sns.ui.improve.ImproveSnsTimelineUI");
                if (intent.resolveActivity(activity.getPackageManager()) == null) return false;
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                activity.startActivity(intent); return true;
            } catch (RuntimeException error) { NavigationBarFeature.log("发现长按入口不可用 " + error.getClass().getSimpleName()); return false; }
        }

        final class TabTarget extends View {
            final int index;
            final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG), badge = new Paint(Paint.ANTI_ALIAS_FLAG), text = new Paint(Paint.ANTI_ALIAS_FLAG);
            final Path outlined, filled;
            final RectF iconRect = new RectF(), badgeRect = new RectF();
            TabTarget(int index) {
                super(NavigationBarView.this.getContext()); this.index = index;
                outlined = NavigationIcons.create(index, false); filled = NavigationIcons.create(index, true);
                setClickable(true); setLongClickable(true); setFocusable(true);
                setOnClickListener(view -> activate(index)); setOnLongClickListener(view -> longPress(index));
                setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
                text.setTextAlign(Paint.Align.CENTER); ink.setFilterBitmap(true);
            }

            void updateDescription() {
                String label = session.nativeTabs.label(index);
                if (counts[index] > 0 && (index != 2 || session.config.finderBadge)) label += "，" + counts[index] + " 条未读";
                else if (dots[index] && (index != 2 || session.config.finderBadge)) label += "，有新提醒";
                setContentDescription(label);
            }

            @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(info); info.setClassName("android.widget.RadioButton");
                info.setCheckable(true); info.setChecked(index == selected);
            }

            @Override protected void onDraw(Canvas canvas) { drawContent(canvas, false); }

            void drawContent(Canvas canvas, boolean activeLayer) {
                boolean dark = SettingsTheme.isDark(getContext());
                int preview = Math.max(0, Math.min(3, Math.round(motion.target())));
                boolean active = index == preview;
                boolean highlighted = activeLayer || (!liquidContent || !canvas.isHardwareAccelerated()) && active;
                float press = motion.pressProgress();
                int accent = 0xFF07C160;
                NativeTabBridge.Icons nativeIcons = session.nativeTabs.icons(index);
                if (session.config.nativeIcons && nativeIcons != null) accent = nativeIcons.activeColor;
                int color = highlighted ? accent : (dark ? 0xFF999999 : 0xFF181818);
                float size = px(24), center = getWidth() / 2f;
                float iconY = session.config.hideLabels ? getHeight() / 2f : getHeight() / 2f - px(7);
                iconRect.set(center - size / 2f, iconY - size / 2f, center + size / 2f, iconY + size / 2f);
                int save = canvas.save();
                if (activeLayer && press > 0f) canvas.scale(1f + press * 0.2f, 1f + press * 0.2f, center, iconY);
                if (session.config.nativeIcons && nativeIcons != null) {
                    Bitmap bitmap = highlighted ? nativeIcons.filled : nativeIcons.outlined;
                    ink.setColor(Color.WHITE); ink.setAlpha(255); canvas.drawBitmap(bitmap, null, iconRect, ink);
                } else {
                    ink.setColor(color); ink.setStyle(Paint.Style.FILL);
                    canvas.translate(iconRect.left, iconRect.top); canvas.scale(size / 960f, size / 960f);
                    canvas.drawPath(highlighted ? filled : outlined, ink);
                }
                canvas.restoreToCount(save);
                if (!session.config.hideLabels) {
                    text.setTypeface(highlighted ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
                    text.setColor(color); text.setTextSize(px(11));
                    canvas.drawText(session.nativeTabs.label(index), center, getHeight() / 2f + px(18), text);
                }
                if (index == 2 && !session.config.finderBadge) return;
                if (counts[index] > 0) {
                    String value = counts[index] > 99 ? "99+" : String.valueOf(counts[index]);
                    text.setTextSize(px(10)); text.setTypeface(Typeface.DEFAULT);
                    float w = Math.max(px(16), text.measureText(value) + px(7));
                    float cx = iconRect.right - px(1), cy = iconRect.top + px(1);
                    badgeRect.set(cx - px(4), cy - px(8), cx - px(4) + w, cy + px(8));
                    badge.setColor(0xFFFF3B30); canvas.drawRoundRect(badgeRect, px(8), px(8), badge);
                    text.setColor(Color.WHITE); Paint.FontMetrics metrics = text.getFontMetrics();
                    canvas.drawText(value, badgeRect.centerX(), badgeRect.centerY() - (metrics.ascent + metrics.descent) / 2f, text);
                } else if (dots[index]) {
                    badge.setColor(0xFFFF3B30); canvas.drawCircle(iconRect.right, iconRect.top + px(1), px(4), badge);
                }
            }
        }
    }
}
