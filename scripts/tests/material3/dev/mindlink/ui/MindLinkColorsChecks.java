package dev.mindlink.ui;

public final class MindLinkColorsChecks {
    private static double linear(int channel) {
        double value = channel / 255.0;
        return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }

    private static double luminance(int color) {
        return .2126 * linear((color >> 16) & 255) + .7152 * linear((color >> 8) & 255) + .0722 * linear(color & 255);
    }

    private static void contrast(int foreground, int background) {
        double first = luminance(foreground), second = luminance(background);
        double ratio = (Math.max(first, second) + .05) / (Math.min(first, second) + .05);
        if (ratio < 4.5) throw new AssertionError("Material3 text contrast: " + ratio);
        if ((foreground >>> 24) != 255 || (background >>> 24) != 255) throw new AssertionError("Opaque tokens required");
    }

    public static void main(String[] args) {
        for (MindLinkColors.Palette p : new MindLinkColors.Palette[]{MindLinkColors.LIGHT, MindLinkColors.DARK}) {
            contrast(p.onSurface, p.surface);
            contrast(p.onSurface, p.surfaceContainerLow);
            contrast(p.onSurfaceVariant, p.surfaceContainerLow);
            contrast(p.onPrimary, p.primary);
            contrast(p.onPrimaryContainer, p.primaryContainer);
            contrast(p.onError, p.error);
        }
        System.out.println("PASS: shared light/dark tokens, opacity and 12 Material3 text contrast pairs");
    }
}
