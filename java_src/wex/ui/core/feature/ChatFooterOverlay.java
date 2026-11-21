package wex.ui.core.feature;

import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
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
        if (view == null || depth > 8 || ++budget[0] > 256 || !view.isShown()) return null;
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
        if (applied != null) updateTail();
        if (!preDraw || depth > 0 || footer.getHeight() <= 0) return false;
        View panel = component(footer, PANEL);
        // A native closed panel can remain VISIBLE behind negative margins.
        // Only a witnessed switch identifies an open panel.
        boolean open = mode == 2 || mode == 3;
        if (open && (panel == null || !panel.isShown() || panel.getHeight() <= 0
                || panel.isLayoutRequested())) return false;
        int card = inputHeight(panel);
        if (card <= 0) return false;
        Geometry next = new Geometry();
        next.height = footer.getHeight(); next.card = card; next.mode = mode; next.open = open;
        next.visible = open ? Math.max(card, bounds(panel).bottom) : card;
        next.panelHeight = open ? panel.getHeight() : 0;
        next.gap = Math.round(gapDp * density()); next.side = Math.round(sideDp * density());
        if (open && footer.getTranslationY() != 0f) {
            originalTranslation = footer.getTranslationY(); translationOwned = true;
            translationParent = footer.getParent() instanceof View ? (View) footer.getParent() : null;
            translationRoot = footer.getRootView();
            footer.setTranslationY(0f);
        }
        if (footer.isLayoutRequested()) { stable = 0; retry(); return false; }
        if (!next.same(candidate)) { candidate = next; stable = 0; retry(); return false; }
        if (++stable < 2) { retry(); return false; }
        if (open && resizePanel(panel, natural, mode, panelLimit(card, next.gap))) {
            candidate = null; stable = 0; changed = true; return true;
        }
        if (!(footer.getLayoutParams() instanceof ViewGroup.MarginLayoutParams)) return false;
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) footer.getLayoutParams();
        int top = -(next.visible + next.gap), bottom = -next.height - top;
        changed = params.topMargin != top || params.bottomMargin != bottom
                || params.leftMargin != next.side || params.rightMargin != next.side;
        applied = next;
        if (changed) {
            params.topMargin = top; params.bottomMargin = bottom;
            params.leftMargin = next.side; params.rightMargin = next.side;
            footer.setLayoutParams(params);
        }
        clearBackdrops(footer, 0, 0);
        return changed;
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
        int lift = applied.open ? 0 : Math.round(Math.max(0f, -footer.getTranslationY()));
        int obstruction = Math.max(applied.visible + applied.gap + lift,
                listAt[1] + list.getHeight() - footerAt[1]);
        if (!tail.update(Math.max(0, obstruction))) retry();
    }

    void restore() {
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
        de.robv.android.xposed.XposedBridge.log("WEX: footer-overlay " + stage + " " + error.getClass().getSimpleName());
    }
}
