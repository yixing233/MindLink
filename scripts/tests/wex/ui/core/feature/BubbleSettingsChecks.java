package wex.ui.core.feature;

import wex.ui.core.WexStore;

/** Run separately with a fake WexStore exposing reset(), failReads and nullStrings. */
public final class BubbleSettingsChecks {

    private static int checks;

    private BubbleSettingsChecks() {
    }

    public static void main(String[] args) {
        WexStore store = WexStore.INSTANCE;
        store.reset();
        defaults();

        store.putBool("bubble_enabled", true);
        equal(true, BubbleSettings.enabled());
        store.putString("bubble_enabled", "invalid");
        equal(false, BubbleSettings.enabled());

        store.putString("bubble_mode", " 2 ");
        equal(2, BubbleSettings.mode());
        store.putString("bubble_mode", "-99");
        equal(0, BubbleSettings.mode());
        store.putString("bubble_mode", "99");
        equal(2, BubbleSettings.mode());
        String[] invalid = {"", " ", "invalid", "1.5", "2147483648"};
        for (String raw : invalid) {
            store.putString("bubble_mode", raw);
            equal(0, BubbleSettings.mode());
        }

        store.reset();
        for (int legacy = 0; legacy <= 6; legacy++) {
            store.putString("bubble_blur_radius", String.valueOf(legacy));
            equal(legacy * 4, BubbleSettings.blurDp());
            equal(12, BubbleSettings.cornerDp());
        }
        store.putString("bubble_blur_radius", "-1");
        equal(0, BubbleSettings.blurDp());
        store.putString("bubble_blur_radius", "99");
        equal(24, BubbleSettings.blurDp());
        store.putString("bubble_blur_radius", "invalid");
        equal(16, BubbleSettings.blurDp());
        store.putString("bubble_glass_blur_dp", "31");
        store.putString("bubble_blur_radius", "0");
        equal(31, BubbleSettings.blurDp());
        store.putString("bubble_glass_blur_dp", "99");
        equal(32, BubbleSettings.blurDp());
        store.putString("bubble_glass_blur_dp", "-1");
        equal(0, BubbleSettings.blurDp());
        store.putString("bubble_blur_radius", "3");
        store.putString("bubble_glass_blur_dp", "invalid");
        equal(12, BubbleSettings.blurDp());

        store.putString("bubble_corner_radius_dp", "-1");
        equal(0, BubbleSettings.cornerDp());
        store.putString("bubble_corner_radius_dp", "99");
        equal(40, BubbleSettings.cornerDp());
        store.putString("bubble_corner_radius_dp", "invalid");
        equal(12, BubbleSettings.cornerDp());
        store.putString("bubble_glass_tint", "-1");
        equal(0, BubbleSettings.tintAlpha());
        store.putString("bubble_glass_tint", "999");
        equal(200, BubbleSettings.tintAlpha());
        store.putString("bubble_glass_tint", "invalid");
        equal(70, BubbleSettings.tintAlpha());
        store.putString("bubble_bg_alpha", "-1");
        equal(0, BubbleSettings.imageAlpha());
        store.putString("bubble_bg_alpha", "999");
        equal(255, BubbleSettings.imageAlpha());
        store.putString("bubble_bg_alpha", "invalid");
        equal(230, BubbleSettings.imageAlpha());

        equal(2, BubbleSettings.readInt("absent", 99, 0, 2));
        equal(0, BubbleSettings.readInt("absent", -99, 0, 2));
        store.putString("custom", " 7 ");
        equal(7, BubbleSettings.readInt("custom", 5, 0, 10));
        store.putString("custom", "-2147483648");
        equal(0, BubbleSettings.readInt("custom", 5, 0, 10));
        store.putString("custom", "2147483647");
        equal(10, BubbleSettings.readInt("custom", 5, 0, 10));

        store.reset();
        store.nullStrings = true;
        defaults();
        store.reset();
        store.putBool("bubble_enabled", true);
        store.putString("bubble_mode", "2");
        store.putString("bubble_glass_blur_dp", "32");
        store.failReads = true;
        defaults();
        equal(5, BubbleSettings.readInt("custom", 5, 0, 10));
        store.reset();

        System.out.println("BubbleSettingsChecks passed: " + checks);
    }

    private static void defaults() {
        equal(false, BubbleSettings.enabled());
        equal(0, BubbleSettings.mode());
        equal(16, BubbleSettings.blurDp());
        equal(12, BubbleSettings.cornerDp());
        equal(70, BubbleSettings.tintAlpha());
        equal(230, BubbleSettings.imageAlpha());
    }

    private static void equal(boolean expected, boolean actual) {
        equal(expected ? 1 : 0, actual ? 1 : 0);
    }

    private static void equal(int expected, int actual) {
        checks++;
        if (expected != actual) {
            throw new AssertionError("Expected " + expected + ", got " + actual);
        }
    }
}
