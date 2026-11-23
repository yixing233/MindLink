package wex.ui.core.feature;

import android.view.View;

/** Layout geometry for home overlays; IME is not part of the bottom capsule. */
final class NavigationBottomSpace {
    private NavigationBottomSpace() { }

    /** Same root as the other operand; exclude the host's transient scroll and bounce transforms. */
    static int layoutTop(View view) {
        int top = 0;
        for (View current = view; current != null;
                current = current.getParent() instanceof View ? (View) current.getParent() : null) {
            top += current.getTop();
        }
        return top;
    }

    static int remaining(int rootBottom, int parentBottom, int parentPaddingBottom, int navigationBottom) {
        long innerBottom = (long) parentBottom - Math.max(0, parentPaddingBottom);
        long consumed = Math.max(0L, (long) rootBottom - innerBottom);
        return (int) Math.max(0L, Math.max(0, navigationBottom) - consumed);
    }
}
