package wex.ui.core.feature;

import wex.ui.core.WexStore;

/** Shared, bounded settings for chat bubble rendering and its settings card. */
public final class BubbleSettings {

    private BubbleSettings() {
    }

    public static boolean enabled() {
        try {
            return WexStore.INSTANCE.getBool("bubble_enabled", false);
        } catch (Throwable ignore) {
            return false;
        }
    }

    public static int mode() {
        return readInt("bubble_mode", 0, 0, 2);
    }

    public static int blurDp() {
        int legacyDefault = readInt("bubble_blur_radius", 4, 0, 6) * 4;
        return readInt("bubble_glass_blur_dp", legacyDefault, 0, 32);
    }

    public static int cornerDp() {
        return readInt("bubble_corner_radius_dp", 12, 0, 40);
    }

    public static int tintAlpha() {
        return readInt("bubble_glass_tint", 70, 0, 200);
    }

    public static int imageAlpha() {
        return readInt("bubble_bg_alpha", 230, 0, 255);
    }

    public static int readInt(String key, int def, int min, int max) {
        int fallback = Math.max(min, Math.min(max, def));
        try {
            String raw = WexStore.INSTANCE.getString(key, String.valueOf(fallback));
            if (raw == null || raw.trim().length() == 0) {
                return fallback;
            }
            int value = Integer.parseInt(raw.trim());
            return Math.max(min, Math.min(max, value));
        } catch (Throwable ignore) {
            return fallback;
        }
    }
}
