package wex.ui.core.feature;

public final class NavigationScrollStateChecks {
    public static void main(String[] args) {
        NavigationScrollState state = new NavigationScrollState();
        state.touchScroll(true);
        check(!state.update(0, 0), "first sample establishes baseline");
        check(state.update(0, -20), "finger scrolling down hides bar");
        check(!state.update(0, 5), "finger scrolling up restores bar");
        state.touchScroll(false);
        state.reset();
        check(!state.update(0, -20), "new page baseline is visible");
        check(!state.update(0, -5), "overscroll bounce does not hide bar");
        state.touchScroll(true);
        check(state.update(1, -8), "real downward drag records direction");
        state.touchScroll(false);
        check(state.update(2, -10), "downward fling remains hidden after release");
        System.out.println("PASS: bottom bar drag direction, upward restore and overscroll suppression");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
