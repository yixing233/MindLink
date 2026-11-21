package wex.ui.core.feature;

import java.util.Arrays;

/** 气泡可见区域及采样边界，只处理背景像素。 */
final class BubbleRegion {
    final int left;
    final int top;
    final int right;
    final int bottom;

    BubbleRegion(int left, int top, int right, int bottom) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    int width() {
        return right - left;
    }

    int height() {
        return bottom - top;
    }

    BubbleRegion intersect(BubbleRegion other) {
        int l = Math.max(left, other.left);
        int t = Math.max(top, other.top);
        int r = Math.min(right, other.right);
        int b = Math.min(bottom, other.bottom);
        return l < r && t < b ? new BubbleRegion(l, t, r, b) : null;
    }

    void extendEdges(int[] pixels, int width, int height, float scale, int padding, BubbleRegion source) {
        int l = clamp((int) Math.ceil((source.left - left + padding) / scale), 0, width - 1);
        int t = clamp((int) Math.ceil((source.top - top + padding) / scale), 0, height - 1);
        int r = clamp((int) Math.floor((source.right - left + padding) / scale), l + 1, width);
        int b = clamp((int) Math.floor((source.bottom - top + padding) / scale), t + 1, height);
        for (int y = t; y < b; y++) {
            int row = y * width;
            Arrays.fill(pixels, row, row + l, pixels[row + l]);
            Arrays.fill(pixels, row + r, row + width, pixels[row + r - 1]);
        }
        for (int y = 0; y < t; y++) {
            System.arraycopy(pixels, t * width, pixels, y * width, width);
        }
        for (int y = b; y < height; y++) {
            System.arraycopy(pixels, (b - 1) * width, pixels, y * width, width);
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
