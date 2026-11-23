package wex.ui.core.feature;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import dev.mindlink.xposed.XposedHelpers;

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
            // The geometry pass below re-measures every ancestor, so the old wrapper-shape
            // requirement only rejected valid contacts pages.
            if (page == pager || ancestry == null || !ancestry.contains(page)) return null;
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
        for (View view = list; view != null && result.size() < 16;
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
        return ContactsViewportSupport.update(this, probedRoot.get(), list, pager, nativeHeight);
    }

    boolean ownsReserve() { return paddingOwned || !heights.isEmpty(); }

    private HeightState state(View view) {
        for (HeightState state : heights) if (state.view.get() == view) return state;
        return null;
    }

    void restore() {
        ContactsViewportSupport.restore(this);
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
