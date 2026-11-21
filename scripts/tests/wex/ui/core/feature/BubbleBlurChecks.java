package wex.ui.core.feature;

import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

public final class BubbleBlurChecks {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static void checkColor(int[] pixels, int color, String message) {
        for (int pixel : pixels) check(pixel == color, message);
    }

    private static void constants() {
        int[] colors = {0xff000000, 0xffffffff, 0xff286ca4, 0x80286ca4, 0x01286ca4, 0xfe286ca4};
        for (int color : colors) {
            int[] pixels = new int[35];
            Arrays.fill(pixels, color);
            for (int radius : new int[] {0, 1, 7, 24}) {
                int[] result = BubbleBlur.blur(pixels, 7, 5, radius);
                check(result != pixels, "constant result is independent");
                checkColor(result, color, "opaque and translucent constants stay unchanged");
                checkColor(pixels, color, "constant input stays unchanged");
            }
        }
        for (int alpha = 1; alpha <= 255; alpha++) {
            int color = (alpha << 24) | 0x375bad;
            int[] pixels = new int[6];
            Arrays.fill(pixels, color);
            checkColor(BubbleBlur.blur(pixels, 2, 3, 24), color, "all nonzero alpha levels keep their color");
        }
    }

    private static long contrast(int[] pixels) {
        long total = 0;
        for (int pixel : pixels) {
            int value = (pixel & 255) - 128;
            total += value * value;
        }
        return total;
    }

    private static void texture() {
        int[] pixels = new int[32 * 32];
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                pixels[y * 32 + x] = ((x / 4 + y / 4) & 1) == 0 ? 0xff000000 : 0xffffffff;
            }
        }
        int[] before = pixels.clone();
        int[] small = BubbleBlur.blur(pixels, 32, 32, 1);
        int[] large = BubbleBlur.blur(pixels, 32, 32, 6);
        check(contrast(small) < contrast(pixels), "checkerboard contrast decreases");
        check(contrast(large) < contrast(small), "larger radius reduces more contrast");
        check(!Arrays.equals(small, large), "radius changes actual pixels");
        check(Arrays.equals(pixels, before), "nonzero blur does not modify input");
        for (int pixel : large) check((pixel >>> 24) == 255, "opaque texture remains opaque");
    }

    private static void copy() {
        int[] pixels = {0, 0x00ff0000, 0x7f203040, 0xfffefdfc};
        int[] before = pixels.clone();
        int[] result = BubbleBlur.blur(pixels, 2, 2, 0);
        check(result != pixels && Arrays.equals(result, before), "radius zero copies every ARGB bit");
        result[0] = 0xff123456;
        check(Arrays.equals(pixels, before), "copy has no alias to input");
        pixels[1] = 0;
        check(result[1] == before[1], "input has no alias to copy");
    }

    private static int channel(double value) {
        return (int) Math.max(0, Math.min(255, Math.round(value)));
    }

    // 小图用直接二维卷积作为独立参照，不复用生产代码的滑动窗口。
    private static int[] reference(int[] pixels, int width, int height, int radius) {
        double[] source = new double[pixels.length * 4];
        for (int i = 0; i < pixels.length; i++) {
            int color = pixels[i];
            int alpha = color >>> 24;
            source[i * 4] = alpha;
            source[i * 4 + 1] = ((color >>> 16) & 255) * alpha;
            source[i * 4 + 2] = ((color >>> 8) & 255) * alpha;
            source[i * 4 + 3] = (color & 255) * alpha;
        }
        int area = (radius * 2 + 1) * (radius * 2 + 1);
        for (int pass = 0; pass < 3; pass++) {
            double[] next = new double[source.length];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int to = (y * width + x) * 4;
                    for (int dy = -radius; dy <= radius; dy++) {
                        int row = Math.max(0, Math.min(height - 1, y + dy)) * width;
                        for (int dx = -radius; dx <= radius; dx++) {
                            int from = (row + Math.max(0, Math.min(width - 1, x + dx))) * 4;
                            for (int c = 0; c < 4; c++) next[to + c] += source[from + c];
                        }
                    }
                    for (int c = 0; c < 4; c++) next[to + c] /= area;
                }
            }
            source = next;
        }
        int[] result = new int[pixels.length];
        for (int i = 0; i < result.length; i++) {
            double alpha = source[i * 4];
            int a = channel(alpha);
            if (a != 0) {
                result[i] = (a << 24) | (channel(source[i * 4 + 1] / alpha) << 16)
                        | (channel(source[i * 4 + 2] / alpha) << 8)
                        | channel(source[i * 4 + 3] / alpha);
            }
        }
        return result;
    }

    private static void checkReference(int[] pixels, int width, int height, int radius) {
        int[] before = pixels.clone();
        int[] expected = reference(pixels, width, height, radius);
        int[] actual = BubbleBlur.blur(pixels, width, height, radius);
        check(actual != pixels && actual.length == pixels.length, "reference result shape and ownership");
        for (int i = 0; i < actual.length; i++) {
            for (int shift = 0; shift <= 24; shift += 8) {
                int difference = ((actual[i] >>> shift) & 255) - ((expected[i] >>> shift) & 255);
                check(Math.abs(difference) <= 1, "three clamped passes match direct reference");
            }
        }
        check(Arrays.equals(pixels, before), "reference check leaves input untouched");
    }

    private static void edgesAndAlpha() {
        for (int radius = 0; radius <= 24; radius++) {
            checkColor(BubbleBlur.blur(new int[] {0x81375bad}, 1, 1, radius),
                    0x81375bad, "single pixel is unchanged at every radius");
        }
        int[] strip = {0x80224466, 0xff102030, 0, 0x0044aa11, 0xaacc8844};
        for (int radius : new int[] {1, 3, 24}) {
            checkReference(strip, 1, strip.length, radius);
            checkReference(strip, strip.length, 1, radius);
            check(Arrays.equals(BubbleBlur.blur(strip, 1, strip.length, radius),
                    BubbleBlur.blur(strip, strip.length, 1, radius)), "row and column clamp consistently");
        }
        int[] mixed = new int[9 * 7];
        for (int i = 0; i < mixed.length; i++) {
            mixed[i] = (((i * 73) & 255) << 24) | (((i * 31) & 255) << 16)
                    | (((i * 97) & 255) << 8) | ((i * 151) & 255);
        }
        for (int radius : new int[] {1, 2, 5, 24}) checkReference(mixed, 9, 7, radius);
        int[] hidden = {0x000000ff, 0xffff0000, 0x0000ff00};
        for (int pixel : BubbleBlur.blur(hidden, 3, 1, 1)) {
            check((pixel & 0xffffff) == 0xff0000, "transparent colors do not bleed into red");
            check((pixel >>> 24) > 0 && (pixel >>> 24) < 255, "alpha itself is blurred");
        }
        checkColor(BubbleBlur.blur(new int[] {0x00123456, 0x00abcdef}, 2, 1, 24),
                0, "fully transparent output is canonical transparent black");
        int[] maximum = new int[262144];
        Arrays.fill(maximum, 0xff38618a);
        checkColor(BubbleBlur.blur(maximum, 512, 512, 24), 0xff38618a, "maximum pixel count is accepted");
        checkColor(BubbleBlur.blur(maximum, 262144, 1, 1), 0xff38618a, "maximum width is accepted");
        checkColor(BubbleBlur.blur(maximum, 1, 262144, 1), 0xff38618a, "maximum height is accepted");
    }

    private static void illegal(int[] pixels, int width, int height, int radius, String message) {
        try {
            BubbleBlur.blur(pixels, width, height, radius);
            throw new AssertionError(message);
        } catch (IllegalArgumentException expected) {
        }
    }

    private static void invalidParameters() {
        illegal(null, 1, 1, 0, "null pixels rejected");
        illegal(new int[0], 0, 1, 0, "zero width rejected");
        illegal(new int[0], 1, 0, 0, "zero height rejected");
        illegal(new int[1], -1, 1, 1, "negative width rejected");
        illegal(new int[1], 1, -1, 1, "negative height rejected");
        illegal(new int[1], -1, -1, 1, "two negative dimensions rejected");
        illegal(new int[3], 2, 2, 1, "short array rejected");
        illegal(new int[5], 2, 2, 1, "long array rejected");
        illegal(new int[1], 1, 1, -1, "negative radius rejected");
        illegal(new int[1], 1, 1, 25, "excess radius rejected");
        illegal(new int[1], 1, 1, Integer.MIN_VALUE, "minimum integer radius rejected");
        illegal(new int[1], 1, 1, Integer.MAX_VALUE, "maximum integer radius rejected");
        illegal(new int[262145], 262145, 1, 0, "pixel cap also enforced for copy");
        illegal(new int[262145], 262145, 1, 1, "pixel cap enforced for blur");
        illegal(new int[0], 65536, 65536, 0, "dimension product cannot wrap to zero");
        illegal(new int[1], Integer.MAX_VALUE, Integer.MAX_VALUE, 0, "dimension product cannot wrap to one");
        illegal(new int[1], Integer.MAX_VALUE, 2, 1, "dimension product cannot wrap negative");
    }

    private static void cancellation() throws Exception {
        for (int radius : new int[] {0, 1, 24}) {
            Thread.currentThread().interrupt();
            try {
                BubbleBlur.blur(new int[4], 2, 2, radius);
                throw new AssertionError("interrupted blur must cancel");
            } catch (CancellationException expected) {
                check(Thread.currentThread().isInterrupted(), "cancellation preserves interrupt flag");
            } finally {
                Thread.interrupted();
            }
        }
        final CountDownLatch entered = new CountDownLatch(1);
        final boolean[] observed = new boolean[1];
        final int[] pixels = new int[262144];
        final FutureTask<Void> future = new FutureTask<Void>(new Callable<Void>() {
            public Void call() {
                entered.countDown();
                try {
                    while (true) BubbleBlur.blur(pixels, 512, 512, 24);
                } catch (CancellationException expected) {
                    observed[0] = Thread.currentThread().isInterrupted();
                    return null;
                }
            }
        });
        Thread worker = new Thread(future, "bubble-blur-check");
        worker.setDaemon(true);
        worker.start();
        try {
            check(entered.await(5, TimeUnit.SECONDS), "future starts");
            check(future.cancel(true), "running future accepts cancellation");
            worker.join(5000);
            check(!worker.isAlive(), "cancelled worker exits promptly");
            check(observed[0], "blur throws cancellation in worker without clearing interrupt");
            checkColor(pixels, 0, "cancelled work leaves input untouched");
            try {
                future.get();
                throw new AssertionError("cancelled future cannot return a result");
            } catch (CancellationException expected) {
            }
        } finally {
            future.cancel(true);
            worker.interrupt();
            worker.join(5000);
        }
    }

    public static void main(String[] args) throws Exception {
        constants();
        texture();
        copy();
        edgesAndAlpha();
        invalidParameters();
        cancellation();
        System.out.println("PASS: BubbleBlur constants, alpha, texture, radii, copy, clamped reference, limits and cancellation");
    }
}
