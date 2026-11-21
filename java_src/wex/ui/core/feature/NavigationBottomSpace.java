package wex.ui.core.feature;

/** Bottom-strip accounting for the navigation overlay only; IME is not part of its capsule. */
final class NavigationBottomSpace {
    private NavigationBottomSpace() { }

    static int remaining(int rootBottom, int parentBottom, int parentPaddingBottom, int navigationBottom) {
        long innerBottom = (long) parentBottom - Math.max(0, parentPaddingBottom);
        long consumed = Math.max(0L, (long) rootBottom - innerBottom);
        return (int) Math.max(0L, Math.max(0, navigationBottom) - consumed);
    }
}
