package wex.ui.core.feature;

public final class FloatingFooterLayoutChecks {
    public static void main(String[] args) {
        check(FloatingFooterGeometry.panelHeightLimit(1000, 300, 200, 120, 160) == 380,
                "keyboard panel limit reserves chat content");
        check(FloatingFooterGeometry.panelHeightLimit(400, 300, 200, 120, 160) == 0,
                "small screens respect hard available cap rather than recommended minimum");
        check(FloatingFooterGeometry.panelHeightLimit(0, 300, 200, 120, 160) == -1,
                "unmeasured geometry defers instead of claiming unlimited room");
        check(FloatingFooterGeometry.targetPanelHeight(300, 80, 340) == 340,
                "drag extension is capped by available room");
        check(FloatingFooterGeometry.scrollForVisiblePanel(true, 420, 300, 280) == 280,
                "visible panel reuses the measured keyboard scroll");
        check(FloatingFooterGeometry.scrollForVisiblePanel(true, 420, 300, 0) == 300,
                "visible panel falls back to the IME inset");
        check(FloatingFooterGeometry.scrollForVisiblePanel(true, 420, 0, 280) == 0,
                "panel alone does not translate the footer");
        check(FloatingFooterGeometry.scrollForVisiblePanel(false, 420, 300, 280) == 420,
                "collapsed panel keeps the host scroll");
        check(FloatingFooterGeometry.yFromBottomWithoutPanel(500, true, 300) == 200,
                "recent-image popup excludes the visible panel");
        check(FloatingFooterGeometry.yFromBottomWithoutPanel(200, true, 300) == 0,
                "recent-image popup offset never becomes negative");
        System.out.println("PASS: footer panel ordering, keyboard coexistence, height bounds and popup offset");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
