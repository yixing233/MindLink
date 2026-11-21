package wex.ui.core.feature;

import java.util.concurrent.CancellationException;

/** 预乘 ARGB 的三轮分离 box 模糊，不依赖 Android。 */
public final class BubbleBlur {
    private static final int MAX_RADIUS = 24;
    private static final int MAX_PIXELS = 262144;

    private BubbleBlur() {
    }

    public static int[] blur(int[] pixels, int width, int height, int radius) {
        if (radius < 0 || radius > MAX_RADIUS) {
            throw new IllegalArgumentException("radius must be 0..24");
        }
        if (pixels == null || width <= 0 || height <= 0) {
            throw new IllegalArgumentException("pixels and positive dimensions required");
        }
        long count = (long) width * height;
        if (count > MAX_PIXELS || count != pixels.length) {
            throw new IllegalArgumentException("pixel count must match dimensions and be <= 262144");
        }
        checkCancelled();
        int[] result = new int[pixels.length];
        if (radius == 0) {
            for (int y = 0; y < height; y++) {
                checkCancelled();
                System.arraycopy(pixels, y * width, result, y * width, width);
            }
            checkCancelled();
            return result;
        }

        float[] source = new float[pixels.length * 4];
        float[] scratch = new float[source.length];
        double[] sums = new double[width * 4];
        for (int y = 0; y < height; y++) {
            checkCancelled();
            int row = y * width;
            for (int x = 0; x < width; x++) {
                int color = pixels[row + x];
                int alpha = color >>> 24;
                int offset = (row + x) * 4;
                source[offset] = alpha;
                // 不提前舍入预乘值，避免低透明度颜色失真。
                source[offset + 1] = ((color >>> 16) & 255) * alpha;
                source[offset + 2] = ((color >>> 8) & 255) * alpha;
                source[offset + 3] = (color & 255) * alpha;
            }
        }
        for (int pass = 0; pass < 3; pass++) {
            horizontal(source, scratch, width, height, radius);
            vertical(scratch, source, sums, width, height, radius);
        }
        for (int y = 0; y < height; y++) {
            checkCancelled();
            int row = y * width;
            for (int x = 0; x < width; x++) {
                int offset = (row + x) * 4;
                float alpha = source[offset];
                int a = channel(alpha);
                if (a != 0) {
                    result[row + x] = (a << 24)
                            | (channel(source[offset + 1] / alpha) << 16)
                            | (channel(source[offset + 2] / alpha) << 8)
                            | channel(source[offset + 3] / alpha);
                }
            }
        }
        checkCancelled();
        return result;
    }

    private static void horizontal(float[] source, float[] target,
                                   int width, int height, int radius) {
        int stride = width * 4;
        int edge = Math.min(radius, width - 1);
        int window = radius * 2 + 1;
        for (int y = 0; y < height; y++) {
            checkCancelled();
            int row = y * stride;
            for (int c = 0; c < 4; c++) {
                double sum = source[row + c] * (radius + 1.0);
                for (int x = 1; x <= edge; x++) {
                    sum += source[row + x * 4 + c];
                }
                sum += (double) source[row + (width - 1) * 4 + c] * (radius - edge);
                for (int x = 0; x < width; x++) {
                    target[row + x * 4 + c] = (float) (sum / window);
                    int remove = row + Math.max(0, x - radius) * 4 + c;
                    int add = row + Math.min(width - 1, x + radius + 1) * 4 + c;
                    sum -= source[remove];
                    sum += source[add];
                }
            }
        }
    }

    private static void vertical(float[] source, float[] target, double[] sums,
                                 int width, int height, int radius) {
        int stride = width * 4;
        int edge = Math.min(radius, height - 1);
        int window = radius * 2 + 1;
        checkCancelled();
        for (int x = 0; x < stride; x++) {
            sums[x] = source[x] * (radius + 1.0);
        }
        for (int y = 1; y <= edge; y++) {
            checkCancelled();
            int row = y * stride;
            for (int x = 0; x < stride; x++) {
                sums[x] += source[row + x];
            }
        }
        if (radius > edge) {
            checkCancelled();
            int row = (height - 1) * stride;
            for (int x = 0; x < stride; x++) {
                sums[x] += (double) source[row + x] * (radius - edge);
            }
        }
        for (int y = 0; y < height; y++) {
            checkCancelled();
            int row = y * stride;
            int remove = Math.max(0, y - radius) * stride;
            int add = Math.min(height - 1, y + radius + 1) * stride;
            for (int x = 0; x < stride; x++) {
                double sum = sums[x];
                target[row + x] = (float) (sum / window);
                sum -= source[remove + x];
                sum += source[add + x];
                sums[x] = sum;
            }
        }
    }

    private static int channel(float value) {
        return Math.max(0, Math.min(255, Math.round(value)));
    }

    private static void checkCancelled() {
        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException("bubble blur interrupted");
        }
    }
}
