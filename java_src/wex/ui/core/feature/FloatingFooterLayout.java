package wex.ui.core.feature;

import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.RelativeLayout;

import java.lang.ref.WeakReference;

/** Structural helpers used by the floating footer; no host classes are required. */
final class FloatingFooterLayout {
    private FloatingFooterLayout() { }

    // The chat window's own consumers are handed navigation-stripped insets so their
    // content can reach behind the gesture pill, which also means getRootWindowInsets()
    // reports nav = 0 for the card. Remember the band the window really reported before
    // it was stripped; without it the card cannot know what it has to clear.
    private static WeakReference<View> bandRoot = new WeakReference<>(null);
    private static int bandBottom;

    static void rememberBand(View view, int nav) {
        if (view == null || nav <= 0) return;
        bandRoot = new WeakReference<>(view.getRootView());
        bandBottom = nav;
    }

    static void forgetBand() { bandRoot.clear(); bandBottom = 0; }

    static int rememberedBand(View view) {
        if (view == null || bandBottom <= 0) return 0;
        return bandRoot.get() == view.getRootView() ? bandBottom : 0;
    }

    static boolean reparent(View footer, View panel) {
        if (footer == null || panel == null || !(footer instanceof ViewGroup)) return false;
        ViewGroup inputColumn = panel.getParent() instanceof ViewGroup ? (ViewGroup) panel.getParent() : null;
        if (inputColumn == null) return false;
        if (inputColumn.getParent() == footer) return true;
        if (!(inputColumn.getParent() instanceof RelativeLayout)) return false;
        RelativeLayout root = (RelativeLayout) inputColumn.getParent();
        if (panel.getId() == View.NO_ID) panel.setId(View.generateViewId());
        int height = panel.getLayoutParams() == null ? ViewGroup.LayoutParams.WRAP_CONTENT : panel.getLayoutParams().height;
        inputColumn.removeView(panel); panel.setVisibility(View.GONE);
        root.addView(panel, 0, new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height));
        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            if (child == panel || !(child.getLayoutParams() instanceof RelativeLayout.LayoutParams)) continue;
            RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) child.getLayoutParams();
            if (params.getRule(RelativeLayout.BELOW) == 0) { params.addRule(RelativeLayout.BELOW, panel.getId()); child.setLayoutParams(params); }
        }
        return true;
    }

    static int siblingsHeight(View panel) {
        if (!(panel.getParent() instanceof ViewGroup)) return 0;
        ViewGroup root = (ViewGroup) panel.getParent(); int sum = 0;
        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            if (child != panel && child.getVisibility() != View.GONE) sum += child.getHeight();
        }
        return sum;
    }

    /** Current laid-out input height. Only a child with a positive measurement but
     *  zero laid-out height is between measure and layout; a naturally zero row is valid. */
    static int measuredSiblingsHeight(View panel) {
        if (panel == null || !(panel.getParent() instanceof ViewGroup)) return -1;
        ViewGroup root = (ViewGroup) panel.getParent();
        int sum = root.getPaddingTop() + root.getPaddingBottom();
        boolean visible = false;
        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            if (child == panel || child.getVisibility() == View.GONE) continue;
            visible = true;
            if (child.getHeight() < 0
                    || child.getHeight() == 0 && child.getMeasuredHeight() > 0) return -1;
            sum += child.getHeight();
        }
        return visible ? sum : -1;
    }

    /** Card height using the panel's requested height until the next layout pass. */
    static int measuredCardHeight(View panel) {
        if (panel == null || !(panel.getParent() instanceof ViewGroup)) return -1;
        ViewGroup root = (ViewGroup) panel.getParent();
        int sum = root.getPaddingTop() + root.getPaddingBottom();
        boolean visible = false;
        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            if (child.getVisibility() == View.GONE) continue;
            visible = true;
            int height = child == panel && child.getLayoutParams() != null
                    && child.getLayoutParams().height >= 0
                    ? child.getLayoutParams().height : child.getHeight();
            if (height == 0 && child.getMeasuredHeight() > 0) return -1;
            if (height < 0) return -1;
            sum += height;
        }
        return visible ? sum : -1;
    }

    static int maxPanelHeight(View footer, View panel, int imeHeight, int reserve, int minimum) {
        View parent = footer.getParent() instanceof View ? (View) footer.getParent() : null;
        Rect bounds = new Rect();
        if (parent == null || !contentBounds(parent, bounds)) return -1;
        int input = siblingsHeight(panel);
        int host = bounds.bottom - bounds.top - remainingBottom(parent);
        return FloatingFooterGeometry.panelHeightLimit(host, remainingImeHeight(parent, imeHeight), input, reserve, minimum);
    }

    /** getGlobalVisibleRect is root-relative; convert it before intersecting any
     *  window/screen rectangle. Freeform windows are not rooted at (0, 0). */
    private static boolean visibleInScreen(View view, Rect bounds) {
        if (view == null || view.getHeight() <= 0 || view.getWidth() <= 0
                || !view.getGlobalVisibleRect(bounds)) return false;
        int[] root = new int[2];
        view.getRootView().getLocationOnScreen(root);
        bounds.offset(root[0], root[1]);
        return bounds.right > bounds.left && bounds.bottom > bounds.top;
    }

    /** Actual window/ancestor visible intersection, in screen coordinates. No clip flags are changed. */
    static boolean contentBounds(View view, Rect bounds) {
        if (!visibleInScreen(view, bounds)) return false;
        int[] location = new int[2]; view.getLocationOnScreen(location);
        bounds.top = Math.max(bounds.top, location[1] + view.getPaddingTop());
        bounds.bottom = Math.min(bounds.bottom, location[1] + view.getHeight() - view.getPaddingBottom());
        bounds.left = Math.max(bounds.left, location[0] + view.getPaddingLeft());
        bounds.right = Math.min(bounds.right, location[0] + view.getWidth() - view.getPaddingRight());
        Rect window = windowBounds(view);
        if (!window.isEmpty()) {
            bounds.top = Math.max(bounds.top, window.top); bounds.bottom = Math.min(bounds.bottom, window.bottom);
            bounds.left = Math.max(bounds.left, window.left); bounds.right = Math.min(bounds.right, window.right);
        }
        return bounds.bottom > bounds.top && bounds.right > bounds.left;
    }

    static Rect visibleFooterBounds(View footer) {
        Rect bounds = new Rect();
        if (!visibleInScreen(footer, bounds)) return bounds;
        Rect window = windowBounds(footer);
        if (!window.isEmpty()) {
            bounds.top = Math.max(bounds.top, window.top); bounds.bottom = Math.min(bounds.bottom, window.bottom);
            bounds.left = Math.max(bounds.left, window.left); bounds.right = Math.min(bounds.right, window.right);
        }
        int[] location = new int[2]; footer.getLocationOnScreen(location);
        bounds.offset(-location[0], -location[1]);
        bounds.left = Math.max(0, bounds.left);
        bounds.top = Math.max(0, bounds.top);
        bounds.right = Math.min(footer.getWidth(), bounds.right);
        bounds.bottom = Math.min(footer.getHeight(), bounds.bottom);
        if (bounds.right <= bounds.left || bounds.bottom <= bounds.top) bounds.setEmpty();
        return bounds;
    }

    // A clipped/short ancestor can end above the immediate host. Move the card
    // itself into that intersection rather than merely shortening its outline.
    static int bottomCorrection(View host) {
        Rect bounds = new Rect();
        if (!contentBounds(host, bounds)) return 0;
        int[] location = new int[2]; host.getLocationOnScreen(location);
        return Math.min(0, bounds.bottom - (location[1]+host.getHeight()-host.getPaddingBottom()));
    }

    static int remainingBottom(View view) {
        if (view == null || view.getRootWindowInsets() == null) return 0;
        int nav = view.getRootWindowInsets().getInsets(android.view.WindowInsets.Type.navigationBars()).bottom;
        return remainingNavigationBottom(windowBottom(view), avoidanceBottom(view), 0, nav);
    }

    /** Height of the gesture-pill band this window actually overlaps, in screen pixels.
     *  Host consumers of the chat window see the navigation inset stripped so their
     *  content can reach behind the pill, which means host padding is no longer evidence
     *  of avoidance: the card itself has to keep clear of the pill. */
    static int pillClearance(View view) {
        if (view == null || view.getHeight() <= 0) return 0;
        WindowInsets insets = view.getRootWindowInsets();
        int nav;
        if (insets == null) {
            nav = rememberedBand(view);
        } else {
            if (insets.getInsets(WindowInsets.Type.ime()).bottom > 0) { forgetBand(); return 0; }
            // A tappable three-button bar is laid out, not overlapped: the host already
            // ends above it, so only the gesture band needs an extra clearance.
            if (insets.getInsets(WindowInsets.Type.tappableElement()).bottom > 0) { forgetBand(); return 0; }
            nav = insets.getInsets(WindowInsets.Type.navigationBars()).bottom;
            if (nav <= 0) nav = rememberedBand(view);
        }
        if (nav <= 0) return 0;
        int reach = windowBottom(view) - contentBottom(view);
        return Math.max(0, nav - Math.max(0, reach));
    }

    static int remainingImeHeight(View view, int imeHeight) {
        if (view == null || imeHeight <= 0) return 0;
        int nav = view.getRootWindowInsets() == null ? 0
                : view.getRootWindowInsets().getInsets(android.view.WindowInsets.Type.navigationBars()).bottom;
        return remainingImeBottom(windowBottom(view), avoidanceBottom(view), 0, nav, imeHeight);
    }

    private static int avoidanceBottom(View view) {
        int bottom = contentBottom(view) - view.getPaddingBottom();
        // Use each ancestor's unscrolled layout boundary, not a translated visible
        // rectangle: a keyboard animation must never masquerade as adjustResize.
        for (View current = view.getParent() instanceof View ? (View) view.getParent() : null;
                current != null; current = current.getParent() instanceof View ? (View) current.getParent() : null) {
            if (current.getHeight() <= 0) continue;
            if (current instanceof ViewGroup && !((ViewGroup) current).getClipChildren()
                    && !((ViewGroup) current).getClipToPadding()) continue;
            bottom = Math.min(bottom, contentBottom(current) - current.getPaddingBottom());
        }
        return bottom;
    }

    // Footer-only geometry: margins own navigation, host scrolling owns the IME.
    // Parent padding and resize count as existing avoidance, never as extra gaps.
    static int remainingNavigationBottom(int windowBottom, int contentBottom, int paddingBottom, int navBottom) {
        long avoided = Math.max(0L, (long) windowBottom - contentBottom) + Math.max(0, paddingBottom);
        return (int) Math.max(0L, Math.max(0, navBottom) - avoided);
    }

    static int remainingImeBottom(int windowBottom, int contentBottom, int paddingBottom, int navBottom, int imeBottom) {
        long avoided = Math.max(0L, (long) windowBottom - contentBottom) + Math.max(0, paddingBottom);
        // The navigation remainder is already in the footer margin. The keyboard
        // covers that area too, so scrolling must not reserve it a second time.
        return (int) Math.max(0L, Math.max(0, imeBottom) - Math.max(avoided, Math.max(0, navBottom)));
    }

    private static int contentBottom(View view) {
        int[] location = new int[2]; view.getLocationOnScreen(location);
        return location[1] + view.getHeight() - Math.round(translationY(view));
    }

    private static float translationY(View view) {
        float translation = 0f;
        for (View current = view; current != null;
                current = current.getParent() instanceof View ? (View) current.getParent() : null) {
            translation += current.getTranslationY();
        }
        // Ignore the host's current animation/scroll translation; otherwise each
        // scroll would be mistaken for resize and the next call would undo it.
        return translation;
    }

    private static Rect windowBounds(View view) {
        // The window rectangle must share the origin of getLocationOnScreen() and of the
        // converted getGlobalVisibleRect(): screen coordinates. Activity window metrics
        // are origin-relative to the window itself on freeform/split builds, so using them
        // here silently shifts every intersection by the window position and shrinks the
        // visible card (right margin grows by the window x, bottom by the window y).
        Rect bounds = new Rect();
        View root = view.getRootView();
        int[] location = new int[2];
        root.getLocationOnScreen(location);
        bounds.left = location[0]; bounds.top = location[1];
        bounds.right = location[0] + root.getWidth();
        bounds.bottom = location[1] + root.getHeight();
        if (bounds.right <= bounds.left || bounds.bottom <= bounds.top) bounds.setEmpty();
        return bounds;
    }

    private static int windowBottom(View view) { return windowBounds(view).bottom; }
}
