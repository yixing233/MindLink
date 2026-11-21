package wex.ui.core.feature;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.WeakHashMap;

/**
 * Reclaims the native tab-bar space along the active Contacts page's view path.
 *
 * Source-level port of the binary patch: only the height that the native tab bar reserves is
 * taken back, every touched value is recorded and restored, and a stale frame never draws.
 */
public final class ContactsViewportSupport {
    private static final int MAX_DEPTH = 16;
    
    private static final WeakHashMap<Object, Page> pages = new WeakHashMap<>();

    private ContactsViewportSupport() {}

    public static boolean update(Object owner, ViewGroup root, ViewGroup list, View pager,
                                 int reserveHeight) {
        if (root == null || list == null || pager == null) {
            restore(owner);
            return false;
        }
        List<View> path = pathToPager(list, root, pager);
        if (path == null) {
            restore(owner);
            return false;
        }
        Page page = pages.get(owner);
        if (page != null && (page.list.get() != list || page.pager.get() != pager
                || page.root.get() != root)) {
            restore(owner);
            return true;
        }
        if (page == null) {
            page = new Page(root, list, pager);
            pages.put(owner, page);
        }
        if (page.viewportHeight != pager.getHeight()) {
            if (page.viewportHeight > 0) {
                restore(owner);
                page = new Page(root, list, pager);
                pages.put(owner, page);
                page.viewportHeight = pager.getHeight();
                pager.postInvalidateOnAnimation();
                return true;
            }
            page.viewportHeight = pager.getHeight();
            page.hostOverride = false;
        }
        for (Iterator<LayoutEdit> it = page.edits.iterator(); it.hasNext();) {
            LayoutEdit edit = it.next();
            View view = edit.view.get();
            if (view == null || !path.contains(view) || !edit.isApplied(view)) {
                it.remove();
                if (view != null && !path.contains(view)) edit.restore();
                else if (view != null) page.hostOverride = true;
            } else if (view.isLayoutRequested()) {
                return true;
            }
        }
        if (page.hostOverride) return false;
        if (!pager.isLaidOut() || !root.isLaidOut() || !list.isLaidOut()) return true;
        if (pager.getHeight() <= 0 || list.getHeight() <= 0) return false;

        int cap = Math.min(pager.getHeight() / 4,
                reserveHeight > 0 ? reserveHeight + dp(list, 8) : dp(list, 88));
        int[] location = new int[2];
        pager.getLocationInWindow(location);
        int targetBottom = location[1] + pager.getHeight() - pager.getPaddingBottom();
        list.getLocationInWindow(location);
        int missing = targetBottom - (location[1] + list.getHeight());
        if (missing < -1 && !page.edits.isEmpty()) {
            restore(owner);
            return true;
        }
        if (missing <= 1 || missing > cap) {
            page.report(targetBottom, targetBottom - missing);
            return false;
        }

        // Start at the page boundary. A layout pass must settle before touching a child.
        // Any remaining gap can then be attributed to an inner wrapper or the list itself.
        for (int i = path.size() - 2; i >= 0; i--) {
            View child = path.get(i);
            ViewGroup parent = (ViewGroup) path.get(i + 1);
            if (child.getHeight() <= 0 || child.getTranslationY() != 0f
                    || parent.getScrollY() != 0) return false;
            int gap = parent.getHeight() - child.getBottom();
            if (parent == pager) gap -= parent.getPaddingBottom();
            if (gap <= 0 || gap > missing + 1) continue;

            LayoutEdit edit = reclaim(child, parent, pager, gap, Math.min(gap, missing));
            if (edit != null) {
                page.record(edit);
                pager.postInvalidateOnAnimation();
                return true;
            }
        }
        page.report(targetBottom, targetBottom - missing);
        return false;
    }

    private static LayoutEdit reclaim(View child, ViewGroup parent, View pager, int gap,
                                     int amount) {
        ViewGroup.LayoutParams params = child.getLayoutParams();
        if (params == null) return null;
        ViewGroup.MarginLayoutParams margins = params instanceof ViewGroup.MarginLayoutParams
                ? (ViewGroup.MarginLayoutParams) params : null;
        int bottomMargin = margins == null ? 0 : margins.bottomMargin;
        int bottomPadding = parent == pager ? 0 : parent.getPaddingBottom();
        int unused = gap - bottomPadding - bottomMargin;
        if (bottomMargin < 0 || unused < -1) return null;

        boolean fillsHeight = params.height == ViewGroup.LayoutParams.MATCH_PARENT;
        // A narrowly scoped padding change preserves sibling position at the top of the page.
        if (fillsHeight && bottomPadding > 0 && unused <= 1) {
            return LayoutEdit.apply(parent, LayoutEdit.PADDING,
                    bottomPadding, bottomPadding - Math.min(bottomPadding, amount));
        }
        if (fillsHeight && margins != null && bottomMargin > 0 && unused <= 1) {
            return LayoutEdit.apply(child, LayoutEdit.MARGIN,
                    bottomMargin, bottomMargin - Math.min(bottomMargin, amount));
        }
        if (params.height == child.getHeight() && parent instanceof FrameLayout
                && params instanceof FrameLayout.LayoutParams && unused > 0) {
            FrameLayout.LayoutParams frame = (FrameLayout.LayoutParams) params;
            int vertical = frame.gravity & Gravity.VERTICAL_GRAVITY_MASK;
            if ((frame.gravity == -1 || vertical == 0 || vertical == Gravity.TOP)
                    && child.getTop() == parent.getPaddingTop() + frame.topMargin) {
                return LayoutEdit.apply(child, LayoutEdit.HEIGHT,
                        params.height, params.height + Math.min(unused, amount));
            }
        }
        return null;
    }

    private static List<View> pathToPager(View list, View root, View pager) {
        List<View> path = new ArrayList<>();
        boolean foundRoot = false;
        View current = list;
        while (current != null && path.size() < MAX_DEPTH) {
            path.add(current);
            if (current == root) foundRoot = true;
            if (current == pager) return foundRoot && root != pager ? path : null;
            ViewParent parent = current.getParent();
            current = parent instanceof ViewGroup ? (ViewGroup) parent : null;
        }
        return null;
    }

    private static void log(String message) {
        try { de.robv.android.xposed.XposedBridge.log("WEX: 联系人整页: " + message); } catch (Throwable ignored) { }
    }

    private static int dp(View view, int value) {
        return Math.round(view.getResources().getDisplayMetrics().density * value);
    }

    public static void restore(Object owner) {
        Page page = pages.remove(owner);
        if (page == null) return;
        for (int i = page.edits.size() - 1; i >= 0; i--) page.edits.get(i).restore();
    }

    private static final class Page {
        final WeakReference<View> root;
        final WeakReference<View> list;
        final WeakReference<View> pager;
        final List<LayoutEdit> edits = new ArrayList<>();
        int lastGap = Integer.MIN_VALUE;
        int viewportHeight;
        boolean hostOverride;

        Page(View root, View list, View pager) {
            this.root = new WeakReference<>(root);
            this.list = new WeakReference<>(list);
            this.pager = new WeakReference<>(pager);
        }

        void record(LayoutEdit next) {
            for (LayoutEdit edit : edits) {
                if (edit.view.get() == next.view.get() && edit.property == next.property
                        && edit.applied == next.original
                        && (edit.property == LayoutEdit.PADDING
                            || edit.layoutParams.get() == next.layoutParams.get())) {
                    edit.applied = next.applied;
                    return;
                }
            }
            edits.add(next);
        }

        void report(int targetBottom, int listBottom) {
            int gap = targetBottom - listBottom;
            if (gap == lastGap) return;
            lastGap = gap;
            log("viewport bottom=" + listBottom + " target=" + targetBottom
                    + " gap=" + gap + " edits=" + edits.size());
        }
    }

    private static final class LayoutEdit {
        static final int PADDING = 0;
        static final int MARGIN = 1;
        static final int HEIGHT = 2;
        final WeakReference<View> view;
        final int property;
        final int original;
        int applied;
        final WeakReference<ViewGroup.LayoutParams> layoutParams;

        LayoutEdit(View view, int property, int original, int applied) {
            this.view = new WeakReference<>(view);
            this.property = property;
            this.original = original;
            this.applied = applied;
            this.layoutParams = new WeakReference<>(view.getLayoutParams());
        }

        static LayoutEdit apply(View view, int property, int original, int applied) {
            LayoutEdit edit = new LayoutEdit(view, property, original, applied);
            edit.write(view, applied);
            return edit;
        }

        boolean isApplied(View view) {
            if (property == PADDING) return view.getPaddingBottom() == applied;
            ViewGroup.LayoutParams params = view.getLayoutParams();
            if (params == null || params != layoutParams.get()) return false;
            if (property == HEIGHT) return params.height == applied;
            return ((ViewGroup.MarginLayoutParams) params).bottomMargin == applied;
        }

        void restore() {
            View target = view.get();
            if (target != null && isApplied(target)) write(target, original);
        }

        private void write(View view, int value) {
            if (property == PADDING) {
                if (view.isPaddingRelative()) {
                    view.setPaddingRelative(view.getPaddingStart(), view.getPaddingTop(),
                            view.getPaddingEnd(), value);
                } else {
                    view.setPadding(view.getPaddingLeft(), view.getPaddingTop(),
                            view.getPaddingRight(), value);
                }
            } else {
                ViewGroup.LayoutParams params = view.getLayoutParams();
                if (property == HEIGHT) params.height = value;
                else ((ViewGroup.MarginLayoutParams) params).bottomMargin = value;
                view.setLayoutParams(params);
            }
        }
    }
}
