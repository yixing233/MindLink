package wex.ui.core.feature;

import java.util.Arrays;

public final class BubbleBackgroundChecks {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void sources() {
        Object view = new Object();
        Object drawable = new Object();
        int[] values = {10, 20, 300, 500, 255, 1};
        BubbleSourceStamp first = new BubbleSourceStamp(view, drawable, values);
        check(BubbleSourceStamp.same(null, null), "missing background stays missing");
        check(!BubbleSourceStamp.same(null, first), "late wallpaper readiness invalidates fallback");
        check(!BubbleSourceStamp.same(first, null), "removed wallpaper invalidates old capture");
        check(BubbleSourceStamp.same(first, new BubbleSourceStamp(view, drawable, values)), "unchanged source stays valid");
        check(!BubbleSourceStamp.same(first, new BubbleSourceStamp(new Object(), drawable, values)), "new source view invalidates capture");
        check(!BubbleSourceStamp.same(first, new BubbleSourceStamp(view, new Object(), values)), "replacement drawable invalidates capture");
        for (int i = 0; i < values.length; i++) {
            int[] changed = values.clone();
            changed[i]++;
            check(!BubbleSourceStamp.same(first, new BubbleSourceStamp(view, drawable, changed)), "geometry, alpha or bitmap generation invalidates capture");
        }
        int[] before = values.clone();
        values[0]++;
        check(BubbleSourceStamp.same(first, new BubbleSourceStamp(view, drawable, before)), "snapshot owns immutable parameter values");
        check(!BubbleSourceStamp.same(new BubbleSourceStamp(null, drawable, before), first), "lost view cannot validate a source");
        check(!BubbleSourceStamp.same(new BubbleSourceStamp(view, null, before), first), "lost drawable cannot validate a source");
        BubbleGeneration generation = new BubbleGeneration();
        long captured = generation.next(true, 0);
        BubbleSourceStamp changed = new BubbleSourceStamp(view, new Object(), before);
        if (!BubbleSourceStamp.same(first, changed)) generation.next(true, 0);
        check(!generation.accepts(captured, 0, true), "background change invalidates stationary bubble result");
    }

    private static void regions() {
        BubbleRegion viewport = new BubbleRegion(0, 0, 100, 100);
        BubbleRegion edge = viewport.intersect(new BubbleRegion(20, -40, 80, 50));
        check(edge != null && edge.left == 20 && edge.top == 0 && edge.right == 80 && edge.bottom == 50,
                "partially visible edge bubble uses intersection");
        BubbleRegion longBubble = viewport.intersect(new BubbleRegion(10, -5000, 90, 5000));
        check(longBubble != null && longBubble.width() == 80 && longBubble.height() == 100,
                "long bubble samples only viewport intersection");
        check(viewport.intersect(new BubbleRegion(20, 100, 80, 150)) == null, "touching edge is not visible");
        check(viewport.intersect(new BubbleRegion(200, 200, 300, 300)) == null, "offscreen bubble has no sample");
        int[] pixels = new int[16];
        pixels[5] = 1;
        pixels[6] = 2;
        pixels[9] = 3;
        pixels[10] = 4;
        BubbleRegion center = new BubbleRegion(0, 0, 2, 2);
        center.extendEdges(pixels, 4, 4, 1f, 1, center);
        check(Arrays.equals(pixels, new int[]{1, 1, 2, 2, 1, 1, 2, 2, 3, 3, 4, 4, 3, 3, 4, 4}),
                "sample padding clamps background edges instead of blurring transparent pixels");
        int[] interior = {1, 2, 3, 4};
        center.extendEdges(interior, 2, 2, 1f, 0, center);
        check(Arrays.equals(interior, new int[]{1, 2, 3, 4}), "interior sample pixels remain unchanged");
        int[] tiny = {0xff112233};
        center.extendEdges(tiny, 1, 1, 8f, 3, new BubbleRegion(0, 0, 1, 1));
        check(tiny[0] == 0xff112233, "subpixel visible region safely clamps to one pixel");
    }

    public static void main(String[] args) {
        sources();
        regions();
        System.out.println("PASS: wallpaper readiness/replacement, immutable source snapshots, visible intersection and sample edge clamp");
    }
}
