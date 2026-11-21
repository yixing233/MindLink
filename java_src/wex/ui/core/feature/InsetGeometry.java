package wex.ui.core.feature;

/** Geometry shared by system-bar avoidance and floating chat surfaces. Values are pixels. */
final class InsetGeometry {
    private InsetGeometry() { }

    static int remainingBottom(int rootBottom, int contentBottom, int navigationBottom,
                               int imeBottom) {
        int obscured = Math.max(0, Math.max(navigationBottom, imeBottom));
        int alreadyAvoided = Math.max(0, rootBottom - contentBottom);
        return Math.max(0, obscured - alreadyAvoided);
    }

    static int footerMargin(int originalMargin, int gap, int remainingInset) {
        long result = (long) originalMargin + Math.max(0, gap) + Math.max(0, remainingInset);
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, result));
    }

    static int visibleFooterHeight(int height, int offscreenPanel, float translationY) {
        int hidden = Math.max(0, Math.round(offscreenPanel + translationY));
        return Math.max(0, height - hidden);
    }
}
