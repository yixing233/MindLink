package wex.ui.core.feature;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Rect;
import android.graphics.drawable.NinePatchDrawable;
import android.content.res.Resources;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;

/**
 * Bubble-only Nine-Patch metadata. File suffixes are not evidence: ImagePicker
 * copies bytes to a .png name. Compiled PNGs require a valid decoded npTc chunk;
 * raw images require a complete transparent 1px frame with opaque black marks.
 * Ordinary transparent borders, partial markers and ambiguous content runs are
 * not Nine-Patches. No image file is written, renamed or removed here.
 */
final class BubbleNinePatch {
    private BubbleNinePatch() {}

    // At most 4 MiB ARGB source per worker (plus raw crop); never decode a photo
    // at full resolution merely to discover that it is an ordinary image.
    static final long MAX_PIXELS = 1024L * 1024L;

    static void requireBudget(int width, int height) {
        if (width <= 0 || height <= 0 || (long) width * height > MAX_PIXELS)
            throw new IllegalArgumentException("nine-patch exceeds pixel budget");
    }

    /** Only an encoding hint; decoded native chunk validation is still required. */
    static boolean hasCompiledChunk(String path) throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(path, "r")) {
            if (file.length() < 8 || file.readLong() != 0x89504e470d0a1a0aL) return false;
            while (file.getFilePointer() + 12 <= file.length()) {
                long length = file.readInt() & 0xffffffffL;
                int type = file.readInt();
                long next = file.getFilePointer() + length + 4;
                if (next > file.length()) throw new IllegalArgumentException("truncated PNG chunk");
                if (type == 0x6e705463) return true; // npTc, never inferred from .9
                file.seek(next);
                if (type == 0x49454e44) break; // IEND
            }
            return false;
        }
    }

    static final class Raw {
        final int width, height;
        final int[] x, y;
        final Rect padding;
        Raw(int width, int height, int[] x, int[] y, Rect padding) {
            this.width = width; this.height = height; this.x = x; this.y = y; this.padding = padding;
        }
        byte[] chunk() {
            int colors = cells(x, width) * cells(y, height);
            ByteBuffer buffer = ByteBuffer.allocate(32 + 4 * (x.length + y.length + colors))
                    .order(ByteOrder.nativeOrder());
            buffer.put((byte) 1).put((byte) x.length).put((byte) y.length).put((byte) colors);
            buffer.putInt(0).putInt(0);
            buffer.putInt(padding.left).putInt(padding.right).putInt(padding.top).putInt(padding.bottom);
            buffer.putInt(0);
            for (int value : x) buffer.putInt(value);
            for (int value : y) buffer.putInt(value);
            for (int i = 0; i < colors; i++) buffer.putInt(1); // NO_COLOR: draw actual pixels
            return buffer.array();
        }
    }

    /** Probe only border strips at source resolution, keeping ordinary decode sampling. */
    static Raw rawFrame(String path, int width, int height) throws IOException {
        if (width < 3 || height < 3) return null;
        BitmapRegionDecoder decoder;
        try {
            decoder = BitmapRegionDecoder.newInstance(path, false);
        } catch (IOException unsupportedFormat) {
            // GIF/WebP variants without region decoding retain the ordinary image path.
            return null;
        }
        if (decoder == null) return null;
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inScaled = false;
            options.inSampleSize = 1;
            int[] top = edge(decoder, options, new Rect(0, 0, width, 1), true);
            int[] left = edge(decoder, options, new Rect(0, 0, 1, height), false);
            int[] bottom = edge(decoder, options, new Rect(0, height - 1, width, height), true);
            int[] right = edge(decoder, options, new Rect(width - 1, 0, width, height), false);
            if (top == null || left == null || bottom == null || right == null) return null;
            return parse(width - 2, height - 2, top, left, bottom, right);
        } finally {
            decoder.recycle();
        }
    }

    private static int[] edge(BitmapRegionDecoder decoder, BitmapFactory.Options options, Rect bounds, boolean horizontal) {
        Bitmap bitmap = decoder.decodeRegion(bounds, options);
        if (bitmap == null) return null;
        try {
            int length = horizontal ? bitmap.getWidth() : bitmap.getHeight();
            int[] pixels = new int[length];
            for (int i = 0; i < length; i++) pixels[i] = bitmap.getPixel(horizontal ? i : 0, horizontal ? 0 : i);
            return pixels;
        } finally {
            bitmap.recycle();
        }
    }

    private static Raw parse(int width, int height, int[] top, int[] left, int[] bottom, int[] right) {
        int[] x = runs(top), y = runs(left), contentX = runs(bottom), contentY = runs(right);
        if (x == null || y == null || contentX == null || contentY == null || x.length == 0 || y.length == 0
                || contentX.length > 2 || contentY.length > 2 || x.length > 254 || y.length > 254
                || (long) cells(x, width) * cells(y, height) > 255) return null;
        requireBudget(width + 2, height + 2);
        int[] px = contentX.length == 0 ? new int[]{x[0], x[x.length - 1]} : contentX;
        int[] py = contentY.length == 0 ? new int[]{y[0], y[y.length - 1]} : contentY;
        return new Raw(width, height, x, y, new Rect(px[0], py[0], width - px[1], height - py[1]));
    }

    /** Empty pixels must be transparent; marks must be exactly opaque black. */
    private static int[] runs(int[] edge) {
        if ((edge[0] >>> 24) != 0 || (edge[edge.length - 1] >>> 24) != 0) return null;
        ArrayList<Integer> divs = new ArrayList<Integer>();
        boolean marked = false;
        for (int i = 1; i < edge.length - 1; i++) {
            int color = edge[i];
            boolean black = color == 0xff000000;
            if (!black && (color >>> 24) != 0) return null;
            if (black != marked) {
                divs.add(i - 1);
                marked = black;
            }
        }
        if (marked) divs.add(edge.length - 2);
        int[] result = new int[divs.size()];
        for (int i = 0; i < result.length; i++) result[i] = divs.get(i);
        return result;
    }

    static Rect padding(byte[] chunk, int width, int height) {
        if (chunk == null || chunk.length < 32 || chunk[0] != 1)
            throw new IllegalArgumentException("invalid decoded nine-patch chunk");
        int nx = chunk[1] & 255, ny = chunk[2] & 255, colors = chunk[3] & 255;
        if (nx == 0 || ny == 0 || (nx & 1) != 0 || (ny & 1) != 0 || colors == 0
                || chunk.length != 32 + 4 * (nx + ny + colors))
            throw new IllegalArgumentException("invalid nine-patch grid");
        ByteBuffer b = ByteBuffer.wrap(chunk).order(ByteOrder.nativeOrder());
        Rect padding = new Rect(b.getInt(12), b.getInt(20), b.getInt(16), b.getInt(24));
        if (padding.left < 0 || padding.right < 0 || padding.top < 0 || padding.bottom < 0
                || (long) padding.left + padding.right > width || (long) padding.top + padding.bottom > height)
            throw new IllegalArgumentException("invalid nine-patch padding");
        validateDivs(b, 32, nx, width);
        validateDivs(b, 32 + 4 * nx, ny, height);
        int cellX = nx + 1 - (b.getInt(32) == 0 ? 1 : 0) - (b.getInt(32 + 4 * (nx - 1)) == width ? 1 : 0);
        int cellY = ny + 1 - (b.getInt(32 + 4 * nx) == 0 ? 1 : 0)
                - (b.getInt(32 + 4 * (nx + ny - 1)) == height ? 1 : 0);
        if (cellX * cellY != colors) throw new IllegalArgumentException("invalid nine-patch color cells");
        return padding;
    }

    private static int cells(int[] divs, int limit) {
        // Compiled npTc omits zero-width cells at each source boundary.
        return divs.length + 1 - (divs[0] == 0 ? 1 : 0) - (divs[divs.length - 1] == limit ? 1 : 0);
    }

    private static void validateDivs(ByteBuffer b, int offset, int count, int limit) {
        int previous = -1;
        for (int i = 0; i < count; i++) {
            int value = b.getInt(offset + i * 4);
            if (value <= previous || value > limit) throw new IllegalArgumentException("invalid nine-patch stretch bounds");
            previous = value;
        }
    }

    static NinePatchDrawable drawable(Resources resources, Bitmap bitmap, byte[] chunk, int alpha) {
        Rect padding = padding(chunk, bitmap.getWidth(), bitmap.getHeight());
        if (!android.graphics.NinePatch.isNinePatchChunk(chunk)) throw new IllegalArgumentException("not a native nine-patch chunk");
        // Imported files have pixel geometry, not a drawable-mdpi resource contract.
        // Match the destination density so Android does not rescale their fixed cells/padding.
        bitmap.setDensity(resources.getDisplayMetrics().densityDpi);
        NinePatchDrawable drawable = new NinePatchDrawable(resources, bitmap, chunk, padding, null);
        drawable.setAlpha(alpha);
        return drawable;
    }
}
