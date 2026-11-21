package wex.ui.core.feature;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.XposedHelpers;

/** Content-free 8.0.76 contacts ownership. Unknown page/wrapper/constraint means no write. */
final class NavigationContactViewport {
    static final int LIST_ID = 0x7f090267, NATIVE_ID = 0x7f093c64;
    private static final String FRAGMENT = "com.tencent.mm.ui.contact.address.MvvmAddressUIFragment";
    private static final String FROSTED = "com.tencent.mm.ui.FrostedContentView";
    private static final String BOUNCE = "com.tencent.mm.ui.widget.pulldown.MMWeUIBounceView";
    private final List<HeightState> heights = new ArrayList<>();
    private WeakReference<ViewGroup> rootRef = new WeakReference<>(null);
    private WeakReference<ViewGroup> probedRoot = new WeakReference<>(null), probedList = new WeakReference<>(null);
    private WeakReference<View> probedPager = new WeakReference<>(null);
    private int originalPadding, lastPadding;
    private boolean paddingOwned;
    private int diagnosticCount, diagnosticAttempts;
    private long lastDiagnosticTime;
    private String lastDiagnostic;

    /** Never call getItem/h: those create a fragment. g only reads the current cached fragment. */
    static ViewGroup currentList(View pager, Object adapter) {
        return currentList(pager, adapter, null, 0);
    }

    static ViewGroup currentList(View pager, Object adapter, NavigationContactViewport diagnostic, int nativeHeight) {
        String reason = "pager";
        View candidate = pager;
        if (diagnostic != null) diagnostic.clearProbe();
        try {
            if (pager == null || adapter == null
                    || !"com.tencent.mm.ui.MainTabUI$TabsAdapter".equals(adapter.getClass().getName())
                    || XposedHelpers.getObjectField(adapter, "mViewPager") != pager
                    || ((Number) XposedHelpers.callMethod(pager, "getCurrentItem")).intValue() != 1) return null;
            reason = "owner";
            Object owner = XposedHelpers.getObjectField(adapter, "this$0");
            if (owner == null || !"com.tencent.mm.ui.MainTabUI".equals(owner.getClass().getName())) return null;
            reason = "fragment";
            Object fragment = XposedHelpers.callMethod(owner, "g");
            if (fragment == null || !FRAGMENT.equals(fragment.getClass().getName())) return null;
            reason = "root-list";
            Object root = XposedHelpers.callMethod(fragment, "getView"), list = XposedHelpers.getObjectField(fragment, "p");
            if (list instanceof View) candidate = (View) list;
            else if (root instanceof View) candidate = (View) root;
            if (!(root instanceof ViewGroup) || !(list instanceof ViewGroup)) return null;
            ViewGroup target = (ViewGroup) list, page = (ViewGroup) root;
            reason = "list-identity";
            if (target.getId() != LIST_ID || !"com.tencent.mm.view.recyclerview.WxRecyclerView".equals(target.getClass().getName())) return null;
            reason = "wrapper";
            List<View> ancestry = path(target, pager);
            if (reserveWrapper(ancestry, page, pager) == null) return null;
            reason = "visible";
            if (!target.isShown() || !page.isShown()) return null;
            int[] pagePosition = new int[2], pagerPosition = new int[2];
            page.getLocationInWindow(pagePosition); pager.getLocationInWindow(pagerPosition);
            reason = "page-bounds";
            if (pagePosition[0] != pagerPosition[0] || page.getWidth() != pager.getWidth()) return null;
            reason = "path";
            if (ancestry == null) return null;
            if (diagnostic != null) {
                diagnostic.probedRoot = new WeakReference<>(page);
                diagnostic.probedList = new WeakReference<>(target);
                diagnostic.probedPager = new WeakReference<>(pager);
            }
            reason = "accepted";
            return target;
        } catch (Throwable unavailable) {
            reason += "-" + unavailable.getClass().getSimpleName(); return null;
        } finally {
            if (diagnostic != null && !"accepted".equals(reason)) diagnostic.diagnose(reason, candidate, pager, nativeHeight);
        }
    }

    void diagnose(String reason, View candidate, View pager, int nativeHeight) {
        try { diagnoseBounds(reason, candidate, pager, nativeHeight); }
        catch (Throwable ignored) { } // Diagnostics must never change the ownership path.
    }

    private void diagnoseBounds(String reason, View candidate, View pager, int nativeHeight) {
        long now = android.os.SystemClock.uptimeMillis();
        if (diagnosticCount >= 12 || diagnosticAttempts >= 48 || lastDiagnosticTime != 0 && now - lastDiagnosticTime < 200L) return;
        lastDiagnosticTime = now; diagnosticAttempts++;
        StringBuilder geometry = new StringBuilder();
        View current = candidate;
        for (int depth = 0; current != null && depth < 8; depth++) {
            ViewGroup.LayoutParams lp = current.getLayoutParams();
            android.graphics.Rect visible = new android.graphics.Rect();
            boolean shown = current.getGlobalVisibleRect(visible);
            int[] position = new int[2]; current.getLocationInWindow(position);
            geometry.append(" depth=").append(depth).append(" class=").append(current.getClass().getName())
                    .append(" id=").append(current.getId()).append(" top=").append(position[1])
                    .append(" h=").append(current.getHeight()).append(" w=").append(current.getWidth())
                    .append(" lpH=").append(lp == null ? 0 : lp.height).append(" padB=").append(current.getPaddingBottom())
                    .append(" visible=").append(shown).append(" visibleB=").append(visible.bottom)
                    .append(" localB=").append(current.getBottom()).append(" ty=").append(current.getTranslationY());
            if (lp instanceof FrameLayout.LayoutParams) {
                FrameLayout.LayoutParams frame = (FrameLayout.LayoutParams) lp;
                geometry.append(" marginT=").append(frame.topMargin).append(" marginB=").append(frame.bottomMargin)
                        .append(" gravity=").append(frame.gravity);
            }
            if (current instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) current;
                geometry.append(" clipC=").append(group.getClipChildren()).append(" clipP=").append(group.getClipToPadding());
            }
            if (current == pager) break;
            current = current.getParent() instanceof View ? (View) current.getParent() : null;
        }
        String next = "trace=contacts-v1 reason=" + reason + " nativeH=" + nativeHeight + " owned=" + ownsReserve() + geometry;
        if (next.equals(lastDiagnostic)) return;
        lastDiagnostic = next; diagnosticCount++;
        NavigationBarFeature.log(next);
    }

    /** Bounded ancestor path only, never a tree scan or a blanket MATCH_PARENT rewrite. */
    private static List<View> path(View list, View pager) {
        List<View> result = new ArrayList<>();
        for (View view = list; view != null && result.size() < 8;
                view = view.getParent() instanceof View ? (View) view.getParent() : null) {
            result.add(view);
            if (view == pager) return result;
        }
        return null;
    }

    private void clearProbe() {
        probedRoot.clear(); probedList.clear(); probedPager.clear();
    }

    /** Two confirmed host paths only. The Frosted root is fragment.getView(), not list's grandparent. */
    private static View reserveWrapper(List<View> ancestry, ViewGroup root, View pager) {
        if (ancestry == null || root == null) return null;
        if (root instanceof LinearLayout && ancestry.size() >= 4 && ancestry.get(2) == root
                && ancestry.get(1) instanceof FrameLayout) return ancestry.get(1);
        if (ancestry.size() == 7 && ancestry.get(5) == root && ancestry.get(6) == pager
                && FROSTED.equals(root.getClass().getName())
                && "com.tencent.mm.ui.base.CustomViewPager".equals(pager.getClass().getName())
                && "android.widget.FrameLayout".equals(ancestry.get(1).getClass().getName())
                && "android.widget.LinearLayout".equals(ancestry.get(2).getClass().getName())
                && "android.widget.FrameLayout".equals(ancestry.get(3).getClass().getName())
                && BOUNCE.equals(ancestry.get(4).getClass().getName())) return ancestry.get(4);
        return null;
    }

    /** Captured MATCH_PARENT/full-width wrappers, not any arbitrary recycler or fixed title child. */
    private static boolean frostedConstraints(List<View> ancestry, ViewGroup root) {
        for (int i = 0; i <= 5; i++) {
            View view = ancestry.get(i);
            ViewGroup.LayoutParams lp = view.getLayoutParams();
            if (lp == null || lp.height != ViewGroup.LayoutParams.MATCH_PARENT || lp.width != ViewGroup.LayoutParams.MATCH_PARENT
                    || view.getWidth() != root.getWidth() || view.getLeft() != 0) return false;
        }
        return root.getWidth() > 0 && root.getScrollY() == 0;
    }

    /** One layout mutation at most per frame. Return true until the new viewport has laid out. */
    boolean update(ViewGroup list, View pager, int nativeHeight) {
        List<View> path = path(list, pager);
        ViewGroup root = probedRoot.get();
        View wrapper = reserveWrapper(path, root, pager);
        if (probedList.get() != list || probedPager.get() != pager || wrapper == null || nativeHeight <= 0) {
            diagnose("reserve-unavailable", list, pager, nativeHeight); return false;
        }
        boolean frosted = FROSTED.equals(root.getClass().getName());
        if (frosted && !frostedConstraints(path, root)) {
            diagnose("reserve-constraints", list, pager, nativeHeight); return false;
        }
        diagnose("before-update", list, pager, nativeHeight);
        if (rootRef.get() != null && rootRef.get() != root) restore();
        rootRef = new WeakReference<>(root);
        for (int i = heights.size() - 1; i >= 0; i--) {
            HeightState state = heights.get(i);
            if (!path.contains(state.view.get())) { state.restore(); heights.remove(i); }
        }
        // Outside-in only for the original explicit-height LinearLayout path. The captured bounce
        // path owns no LP or translation: all of its height comes from the exact root padding.
        for (int i = frosted ? -1 : path.size() - 1; i >= 0; i--) {
            View view = path.get(i);
            HeightState state = state(view);
            if (state != null && !state.unchanged(view)) { heights.remove(state); state = null; }
            if (state != null && view.getHeight() != state.last) return true;
            if (!(view.getParent() instanceof FrameLayout)) continue;
            FrameLayout parent = (FrameLayout) view.getParent();
            ViewGroup.LayoutParams raw = view.getLayoutParams();
            if (!(raw instanceof FrameLayout.LayoutParams)) continue;
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) raw;
            int vertical = lp.gravity & 0x70;
            if (lp.height <= 0 || lp.height != view.getHeight() || lp.bottomMargin != 0
                    || lp.topMargin < 0 || lp.leftMargin < 0 || lp.rightMargin < 0
                    || lp.gravity != -1 && vertical != 0 && vertical != Gravity.TOP
                    || view.getTranslationY() != 0f || parent.getScrollY() != 0
                    || view.getTop() != parent.getPaddingTop() + lp.topMargin
                    || view.getLeft() != parent.getPaddingLeft() + lp.leftMargin
                    || view.getRight() != parent.getWidth() - parent.getPaddingRight() - lp.rightMargin) continue;
            int gap = parent.getHeight() - parent.getPaddingBottom() - view.getBottom();
            if (gap != nativeHeight) continue;
            if (state == null) { state = new HeightState(view, parent, lp); heights.add(state); }
            state.last = lp.height + gap;
            lp.height = state.last; view.setLayoutParams(lp); view.requestLayout();
            return true;
        }
        int bottom = root.getPaddingBottom();
        if (paddingOwned && bottom != lastPadding) paddingOwned = false;
        // Static y0 writes native bar height to this exact getView root. In the observed Frosted
        // path it is the direct bounce child (below the fixed title), not the inner recycler frame,
        // that consumes root's bottom reserve. Compare local layout bounds, never overscroll ty.
        ViewGroup.LayoutParams wrapperParams = wrapper.getLayoutParams(), listParams = list.getLayoutParams();
        if (!paddingOwned && bottom == nativeHeight && wrapper.getBottom() == root.getHeight() - bottom
                && wrapper.getTop() >= root.getPaddingTop() && wrapper.getHeight() > 0
                && wrapperParams != null && wrapperParams.height == ViewGroup.LayoutParams.MATCH_PARENT
                && listParams != null && listParams.height == ViewGroup.LayoutParams.MATCH_PARENT) {
            originalPadding = bottom; lastPadding = 0; paddingOwned = true;
            root.setPadding(root.getPaddingLeft(), root.getPaddingTop(), root.getPaddingRight(), 0);
            root.requestLayout(); return true;
        }
        // Do not compute end padding from the previous, shorter inner wrapper frame.
        return paddingOwned && wrapper.getBottom() != root.getHeight() - root.getPaddingBottom();
    }

    boolean ownsReserve() { return paddingOwned || !heights.isEmpty(); }

    private HeightState state(View view) {
        for (HeightState state : heights) if (state.view.get() == view) return state;
        return null;
    }

    void restore() {
        for (HeightState state : heights) state.restore();
        heights.clear();
        ViewGroup root = rootRef.get();
        if (root != null && paddingOwned && root.getPaddingBottom() == lastPadding) {
            root.setPadding(root.getPaddingLeft(), root.getPaddingTop(), root.getPaddingRight(), originalPadding);
            root.requestLayout();
        }
        rootRef.clear(); paddingOwned = false;
    }

    private static final class HeightState {
        final WeakReference<View> view, parent;
        final FrameLayout.LayoutParams params;
        final int original, width, top, left, right, bottom, gravity;
        int last;
        HeightState(View view, View parent, FrameLayout.LayoutParams lp) {
            this.view = new WeakReference<>(view); this.parent = new WeakReference<>(parent); params = lp;
            original = last = lp.height; width = lp.width; top = lp.topMargin;
            left = lp.leftMargin; right = lp.rightMargin; bottom = lp.bottomMargin; gravity = lp.gravity;
        }
        void restore() {
            View target = view.get();
            if (target != null && unchanged(target)) {
                params.height = original; target.setLayoutParams(params); target.requestLayout();
            }
        }
        boolean unchanged(View view) {
            return view.getParent() == parent.get() && view.getLayoutParams() == params && params.height == last
                    && params.width == width && params.topMargin == top && params.leftMargin == left
                    && params.rightMargin == right && params.bottomMargin == bottom && params.gravity == gravity;
        }
    }
}
