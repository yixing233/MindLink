package wex.ui.core.feature;

import android.animation.ValueAnimator;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.EditText;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.Map;

/** Native integration of the device-verified footer geometry (2026-11-19). */
final class ChatFooterOverlay {
    private static final String PANEL = "com.tencent.mm.pluginsdk.ui.chat.ChatFooterBottom";
    private static final String APP = "com.tencent.mm.pluginsdk.ui.chat.AppPanel";
    final View footer;
    Geometry candidate, applied;
    int stable;
    boolean changed;
    View morePanel, moreApp;
    ViewGroup.LayoutParams moreParams;
    int originalHeight, originalPort, ownedHeight, ownedPort;
    float originalTranslation;
    boolean translationOwned;
    View translationParent, translationRoot;
    ViewGroup clipParent;
    boolean clipChildren, clipPadding;
    private final Map<View, Drawable> backgrounds = new IdentityHashMap<>();
    private final Map<View, Drawable> cleared = new IdentityHashMap<>();
    private ChatTailSpacer tail;
    private ValueAnimator expansion;
    private View animationParent, animationRoot;
    private float startTop, animationFrom, animationFraction, animationShift;
    private boolean animateNext;
    private int settlingFrames;

    ChatFooterOverlay(View footer) { this.footer = footer; }

    static final class Geometry {
        int height, card, visible, gap, side, mode, panelHeight;
        boolean open;
        boolean same(Geometry other) {
            return other != null && height == other.height && card == other.card
                    && visible == other.visible && gap == other.gap && side == other.side
                    && mode == other.mode && panelHeight == other.panelHeight && open == other.open;
        }
    }

    static View component(View root, String name) {
        return component(root, name, new int[]{0}, 0);
    }

    private static View component(View view, String name, int[] budget, int depth) {
        if (view == null || depth > 12 || ++budget[0] > 512) return null;
        if (name.equals(view.getClass().getName())) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = component(group.getChildAt(i), name, budget, depth + 1);
                if (found != null) return found;
            }
        }
        return null;
    }

    static View input(View root) { return input(root, new int[]{0}, 0); }

    private static View input(View view, int[] budget, int depth) {
        if (view == null || depth > 14 || ++budget[0] > 256 || !view.isShown()) return null;
        if (view instanceof EditText && view.getWidth() > 0) return view;
        if (PANEL.equals(view.getClass().getName()) || APP.equals(view.getClass().getName())) return null;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = input(group.getChildAt(i), budget, depth + 1);
                if (found != null) return found;
            }
        }
        return null;
    }

    int inputHeight(View panel) {
        if (!(footer instanceof ViewGroup)) return 0;
        int panelTop = panel == null ? 0 : bounds(panel).top;
        int card = 0, pad = Math.round(10f * density());
        for (View row = input(footer); row != null && row != footer;
             row = row.getParent() instanceof View ? (View) row.getParent() : null) {
            Rect bounds = bounds(row);
            if (bounds.bottom > 0 && bounds.bottom < footer.getHeight()
                    && (panelTop <= 0 || bounds.bottom <= panelTop))
                card = Math.max(card, bounds.bottom + pad);
        }
        return card;
    }

    private Rect bounds(View child) {
        Rect result = new Rect();
        child.getDrawingRect(result);
        ((ViewGroup) footer).offsetDescendantRectToMyCoords(child, result);
        return result;
    }

    void transition(int mode) {
        if (tail != null) tail.transition();
        boolean opening = mode == 2 || mode == 3;
        boolean witnessed = applied != null && (mode != applied.mode || expansion != null);
        startTop = screenTop();
        cancelExpansion();
        animateNext = opening && witnessed && footer.isLaidOut() && ValueAnimator.areAnimatorsEnabled();
        settlingFrames = 0;
        restoreMore();
        if (mode != 2 && mode != 3) translationOwned = false;
        candidate = null; stable = 0;
    }

    void nativePortChanged(View app, int height) {
        if (app == moreApp && height > 0) {
            originalPort = height;
            originalHeight = height;
        }
    }

    int moreHeight(int limit) {
        int dp = BubbleSettings.readInt("floating_chat_footer_more_height_dp", 220, 220, 360);
        return Math.min(limit, Math.round(dp * density()));
    }

    boolean apply(int mode, int depth, int natural, int gapDp, int sideDp, boolean preDraw) {
        changed = false;
        if (!preDraw || depth > 0 || footer.getHeight() <= 0) return false;
        if (expansion != null) positionExpansion();
        if (applied != null && !animateNext && !footer.isLayoutRequested()) updateTail();
        View panel = component(footer, PANEL);
        // A native closed panel can remain VISIBLE behind negative margins.
        // Only a witnessed switch identifies an open panel.
        boolean open = mode == 2 || mode == 3;
        if (open && (panel == null || !panel.isShown() || panel.getHeight() <= 0
                || panel.isLayoutRequested())) return awaitGeometry();
        int card = inputHeight(panel);
        if (card <= 0) return awaitGeometry();
        Geometry next = new Geometry();
        next.height = footer.getHeight(); next.card = card; next.mode = mode; next.open = open;
        next.visible = open ? Math.max(card, bounds(panel).bottom) : card;
        next.panelHeight = open ? panel.getHeight() : 0;
        next.gap = Math.round(gapDp * density()); next.side = Math.round(sideDp * density());
        View host = footer.getParent() instanceof View ? (View) footer.getParent() : footer;
        next.gap += Math.max(FloatingFooterLayout.remainingBottom(host), FloatingFooterLayout.pillClearance(host))
                - FloatingFooterLayout.bottomCorrection(host);
        if (open && footer.getTranslationY() != animationShift) {
            originalTranslation = footer.getTranslationY(); translationOwned = true;
            translationParent = footer.getParent() instanceof View ? (View) footer.getParent() : null;
            translationRoot = footer.getRootView();
            footer.setTranslationY(animationShift);
        }
        if (footer.isLayoutRequested()) { stable = 0; return awaitGeometry(); }
        if (!next.same(candidate)) { candidate = next; stable = 0; return awaitGeometry(); }
        if (++stable < 2) return awaitGeometry();
        if (open && resizePanel(panel, natural, mode, panelLimit(card, next.gap))) {
            candidate = null; stable = 0; changed = true; return true;
        }
        if (!(footer.getLayoutParams() instanceof ViewGroup.MarginLayoutParams)) return false;
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) footer.getLayoutParams();
        int top = -(next.visible + next.gap), bottom = -next.height - top;
        boolean retarget = expansion != null && !next.same(applied);
        float from = retarget ? screenTop() : startTop;
        changed = params.topMargin != top || params.bottomMargin != bottom
                || params.leftMargin != next.side || params.rightMargin != next.side;
        applied = next;
        if (changed) {
            params.topMargin = top; params.bottomMargin = bottom;
            params.leftMargin = next.side; params.rightMargin = next.side;
            footer.setLayoutParams(params);
        }
        if (animateNext || retarget) {
            animateNext = false;
            startExpansion(from);
        }
        clearBackdrops(footer, 0, 0);
        return changed;
    }

    private boolean awaitGeometry() {
        // Hide only the bounded native re-layout before the first animated frame.
        // A panel that never becomes measurable must not hold the window's drawing.
        if (animateNext && settlingFrames++ < 12) changed = true;
        else animateNext = false;
        retry();
        return changed;
    }

    private float screenTop() {
        int[] location = new int[2];
        footer.getLocationOnScreen(location);
        return location[1];
    }

    private void startExpansion(float from) {
        cancelExpansion();
        if (!ValueAnimator.areAnimatorsEnabled()) return;
        animationParent = footer.getParent() instanceof View ? (View) footer.getParent() : null;
        animationRoot = footer.getRootView();
        animationFrom = from;
        expansion = ValueAnimator.ofFloat(0f, 1f);
        expansion.setDuration(220L);
        expansion.setInterpolator(new LinearInterpolator());
        expansion.addUpdateListener(animation -> {
            if (animation != expansion) return;
            if (!footer.isAttachedToWindow() || footer.getParent() != animationParent
                    || footer.getRootView() != animationRoot) { cancelExpansion(); return; }
            animationFraction = (Float) animation.getAnimatedValue();
            positionExpansion();
            // Reserve the next scroll range before traversal, so pre-draw can
            // follow the end after layout in the same frame as the moving card.
            if (!footer.isLayoutRequested()) updateTail();
            if (animation.getAnimatedFraction() >= 1f) {
                expansion = null;
                animationParent = animationRoot = null;
            }
            footer.invalidateOutline();
            retry();
        });
        expansion.start();
    }

    private void positionExpansion() {
        // IME resize and ancestor translations can change the final screen position
        // after the panel switch. Rebase each pre-draw without moving the origin.
        float target = screenTop() - footer.getTranslationY();
        animationShift = (animationFrom - target) * (1f - animationFraction);
        footer.setTranslationY(animationShift);
    }

    private void cancelExpansion() {
        ValueAnimator previous = expansion;
        expansion = null;
        if (previous != null) previous.cancel();
        if (animationShift != 0f && footer.getTranslationY() == animationShift) footer.setTranslationY(0f);
        animationShift = 0f;
        animationParent = animationRoot = null;
    }

    int visibleHeight() {
        if (applied == null) return 0;
        return expansion == null ? applied.visible
                : Math.max(applied.card, Math.min(footer.getHeight(), Math.round(applied.visible - animationShift)));
    }

    private int panelLimit(int card, int gap) {
        View parent = footer.getParent() instanceof View ? (View) footer.getParent() : null;
        Rect visible = new Rect();
        if (parent == null || !FloatingFooterLayout.contentBounds(parent, visible)) return 0;
        return Math.max(0, visible.height() - card - gap);
    }

    private boolean resizePanel(View panel, int natural, int mode, int limit) {
        View app = component(panel, APP);
        if (panel == null || panel.getLayoutParams() == null) return false;
        try {
            int measured = panel.getHeight();
            int nativeHeight = Math.max(natural, morePanel == panel ? originalHeight : measured);
            int target = Math.min(limit, mode == 3 ? moreHeight(nativeHeight) : nativeHeight);
            int inner = app == null ? 0 : Math.max(0, target - app.getPaddingTop() - app.getPaddingBottom());
            int port = app == null ? 0 : field(app.getClass(), "p0").getInt(app);
            ViewGroup.LayoutParams params = panel.getLayoutParams();
            if (target <= 0 || params.height == target && measured == target
                    && (app == null || port == inner && (mode != 3 || app.getHeight() == target))) return false;
            if (morePanel != panel || moreParams != params) {
                restoreMore();
                morePanel = panel; moreApp = app; moreParams = params;
                originalHeight = Math.max(measured, natural); originalPort = port;
            }
            if (app != null) FloatingChatFooterFeature.setAppPanelHeight(app, inner, true);
            ownedPort = inner; ownedHeight = target;
            params.height = target; panel.setLayoutParams(params);
            return true;
        } catch (ReflectiveOperationException error) {
            report("more-panel", error); return false;
        }
    }

    private void restoreMore() {
        if (morePanel == null) return;
        try {
            if (morePanel.getLayoutParams() == moreParams && moreParams.height == ownedHeight) {
                moreParams.height = originalHeight; morePanel.setLayoutParams(moreParams);
            }
            if (moreApp != null && field(moreApp.getClass(), "p0").getInt(moreApp) == ownedPort)
                FloatingChatFooterFeature.setAppPanelHeight(moreApp, originalPort, true);
        } catch (ReflectiveOperationException error) { report("more-restore", error); }
        morePanel = moreApp = null; moreParams = null;
    }

    private void clearBackdrops(View view, int depth, int offset) {
        if (depth > 3 || !(view instanceof ViewGroup) || applied == null) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (!child.isShown()) continue;
            int top = offset + child.getTop();
            Drawable background = child.getBackground();
            boolean inCard = top < applied.card && top + child.getHeight() <= applied.card + 12;
            boolean inPanel = applied.open && child instanceof ViewGroup && top >= applied.card - 12;
            if (background != null && !(background instanceof ChatGlassSurface)
                    && (inCard && background instanceof ColorDrawable || inPanel)
                    && background != cleared.get(child)) {
                backgrounds.put(child, background);
                Drawable replacement = new ColorDrawable(0);
                cleared.put(child, replacement);
                child.setBackground(replacement);
            }
            clearBackdrops(child, depth + 1, top);
        }
    }

    private void updateTail() {
        if (!(footer.getParent() instanceof ViewGroup)) return;
        ViewGroup parent = (ViewGroup) footer.getParent();
        if (clipParent != parent) {
            if (tail != null) { tail.release(); tail = null; }
            restoreClips();
            clipParent = parent; clipChildren = parent.getClipChildren(); clipPadding = parent.getClipToPadding();
        }
        parent.setClipChildren(false); parent.setClipToPadding(false);
        View list = null;
        for (int i = 0; i < parent.getChildCount() && list == null; i++)
            if (parent.getChildAt(i) != footer) list = ChatTailSpacer.recycler(parent.getChildAt(i));
        if (list == null) {
            if (tail != null) { tail.release(); tail = null; }
            return;
        }
        if (tail == null || tail.view() != list) {
            if (tail != null) tail.release();
            tail = ChatTailSpacer.obtain(list);
        }
        if (tail == null) return;
        int[] footerAt = new int[2], listAt = new int[2];
        footer.getLocationOnScreen(footerAt); list.getLocationOnScreen(listAt);
        // Screen coordinates already include IME translation and viewport resize.
        // Reserving the lift again would leave an extra keyboard-sized blank tail.
        // A bottom pull-bounce is excluded: the host scrolls its pull view or translates its content,
        // and following it made the bounce settle behind the card before the tail returned.
        int obstruction = Math.max(0, Math.min(list.getHeight(),
                listAt[1] + ChatTailSpacer.pullOffset(list, parent) + list.getHeight() - footerAt[1]));
        if (!tail.update(obstruction, expansion != null)) retry();
        // The frame about to draw still shows the stale gap above a new message; skip it,
        // the decoration relayout redraws on the next frame.
        if (tail.takeRelayout()) changed = true;
    }

    void restore() {
        animateNext = false;
        cancelExpansion();
        restoreMore();
        if (tail != null) tail.release();
        tail = null;
        restoreClips();
        for (Map.Entry<View, Drawable> entry : backgrounds.entrySet())
            if (entry.getKey().getBackground() == cleared.get(entry.getKey()))
                entry.getKey().setBackground(entry.getValue());
        backgrounds.clear(); cleared.clear();
        if (translationOwned && footer.getTranslationY() == 0f && footer.getParent() == translationParent
                && footer.getRootView() == translationRoot) footer.setTranslationY(originalTranslation);
        translationOwned = false;
        translationParent = translationRoot = null;
        candidate = applied = null; stable = 0;
    }

    private void restoreClips() {
        if (clipParent != null) {
            if (!clipParent.getClipChildren()) clipParent.setClipChildren(clipChildren);
            if (!clipParent.getClipToPadding()) clipParent.setClipToPadding(clipPadding);
        }
        clipParent = null;
    }

    private void retry() { footer.postInvalidateOnAnimation(); }
    private float density() { return footer.getResources().getDisplayMetrics().density; }
    static Field field(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> owner = type; owner != null; owner = owner.getSuperclass())
            try { Field field = owner.getDeclaredField(name); field.setAccessible(true); return field; }
            catch (NoSuchFieldException ignored) { }
        throw new NoSuchFieldException(name);
    }
    static Method method(Class<?> type, String name, Class<?>... parameters) throws NoSuchMethodException {
        for (Class<?> owner = type; owner != null; owner = owner.getSuperclass())
            try { Method method = owner.getDeclaredMethod(name, parameters); method.setAccessible(true); return method; }
            catch (NoSuchMethodException ignored) { }
        throw new NoSuchMethodException(name);
    }
    private static void report(String stage, Throwable error) {
        dev.mindlink.xposed.XposedBridge.log("WEX: footer-overlay " + stage + " " + error.getClass().getSimpleName());
    }
}
