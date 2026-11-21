package wex.ui.core.feature;

public final class InsetGeometryChecks {
    public static void main(String[] args) {
        check(0, InsetGeometry.remainingBottom(2400, 2320, 80, 0), "host already avoids navigation");
        check(80, InsetGeometry.remainingBottom(2400, 2400, 80, 0), "edge-to-edge navigation");
        check(0, InsetGeometry.remainingBottom(2400, 1500, 80, 900), "resize keyboard is not double counted");
        check(820, InsetGeometry.remainingBottom(2400, 2320, 80, 900), "overlay keyboard remainder");
        check(850, InsetGeometry.remainingBottom(2400, 2300, 80, 950), "IME change recomputes from baseline");
        check(0, InsetGeometry.remainingBottom(2400, 2300, 0, 0), "bars hidden");
        check(-872, InsetGeometry.footerMargin(-900, 8, 20), "preserve hidden native panel negative margin");
        for (int i = 0; i < 100; i++) check(-872, InsetGeometry.footerMargin(-900, 8, 20), "no accumulating inset");
        check(-900, InsetGeometry.footerMargin(-900, 0, 0), "zero user gap");
        check(100, InsetGeometry.visibleFooterHeight(1000, 900, 0), "closed panel outline");
        check(550, InsetGeometry.visibleFooterHeight(1000, 900, -450), "half-open panel outline");
        check(1000, InsetGeometry.visibleFooterHeight(1000, 900, -900), "open panel outline");
        check(0, InsetGeometry.visibleFooterHeight(0, 900, 0), "unmeasured view");
        System.out.println("PASS: navigation/IME geometry, preserved negative margins and bounded outline");
    }

    private static void check(int expected, int actual, String name) {
        if (expected != actual) throw new AssertionError(name + ": expected " + expected + ", got " + actual);
    }
}
