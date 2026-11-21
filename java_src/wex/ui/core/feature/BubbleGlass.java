package wex.ui.core.feature;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** 只画已识别的背景 Drawable，不调用聊天窗口或消息 View 的 draw。 */
final class BubbleGlass {

    private static final String WALLPAPER_CLASS = "com.tencent.mm.ui.chatting.ChattingImageBGView";
    private static final int CACHE_BYTES = 2 * 1024 * 1024;
    private static final List<Cached> CACHE = new ArrayList<Cached>();
    private static int cacheBytes;

    private BubbleGlass() {
    }

    static final class Sample {
        final int[] pixels;
        final int width;
        final int height;
        final float scale;
        final int padding;
        final int viewWidth;
        final int viewHeight;
        final int radius;
        final BubbleRegion region;
        final BubbleSourceStamp source;

        Sample(int[] pixels, int width, int height, float scale, int padding,
               int viewWidth, int viewHeight, int radius, BubbleRegion region, BubbleSourceStamp source) {
            this.pixels = pixels;
            this.width = width;
            this.height = height;
            this.scale = scale;
            this.padding = padding;
            this.viewWidth = viewWidth;
            this.viewHeight = viewHeight;
            this.radius = radius;
            this.region = region;
            this.source = source;
        }
    }

    private static final class Cached {
        final Sample sample;
        final Bitmap bitmap;

        Cached(Sample sample, Bitmap bitmap) {
            this.sample = sample;
            this.bitmap = bitmap;
        }

        int bytes() {
            return sample.pixels.length * 4 + bitmap.getAllocationByteCount();
        }
    }

    private static final class Source {
        final View view;
        final Drawable drawable;
        final boolean image;

        Source(View view, Drawable drawable, boolean image) {
            this.view = view;
            this.drawable = drawable;
            this.image = image;
        }
    }

    private static Source findSource(View bubble) {
        View source = findWallpaper(bubble.getRootView(), bubble);
        Drawable drawable = source instanceof ImageView ? ((ImageView) source).getDrawable() : null;
        boolean image = drawable != null;
        if (source != null && drawable == null) {
            drawable = source.getBackground();
        }
        if (canSample(source, drawable, bubble)) {
            return new Source(source, drawable, image);
        }
        Object parent = bubble.getParent();
        for (int depth = 0; parent instanceof View && depth < 20; depth++) {
            View candidate = (View) parent;
            Drawable background = candidate.getBackground();
            if (canSample(candidate, background, bubble)) {
                return new Source(candidate, background, false);
            }
            parent = candidate.getParent();
        }
        // Activity 级全局背景位于 DecorView 顶层，聊天内容可能因宿主 overlay 结构无法遍历到它。
        // 最终直接复用已选图片文件，以根视图作为坐标系构造采样源。
        try {
            String path = GlobalBackgroundFeature.isEnabled() ? GlobalBackgroundFeature.imagePath() : "";
            View root = bubble.getRootView();
            BitmapDrawable global = path == null || path.length() == 0
                    ? null : GlobalWallpaper.drawableFor(root, path);
            if (global != null && root.getWidth() > 0 && root.getHeight() > 0) {
                global.setBounds(0, 0, root.getWidth(), root.getHeight());
                return new Source(root, global, false);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    static BubbleSourceStamp sourceStamp(View bubble) {
        try {
            Source source = findSource(bubble);
            return source == null ? null : stamp(source, bubble);
        } catch (Throwable ignore) {
            return null;
        }
    }

    private static BubbleSourceStamp stamp(Source source, View bubble) {
        View view = source.view;
        Drawable drawable = source.drawable;
        int[] location = new int[2];
        view.getLocationInWindow(location);
        Rect visible = new Rect();
        if (!bubble.getLocalVisibleRect(visible)) {
            visible.setEmpty();
        }
        Rect bounds = drawable.getBounds();
        int[] state = drawable.getState();
        int[] values = new int[34 + state.length];
        values[0] = location[0];
        values[1] = location[1];
        values[2] = view.getWidth();
        values[3] = view.getHeight();
        values[4] = view.getPaddingLeft();
        values[5] = view.getPaddingTop();
        values[6] = view.getPaddingRight();
        values[7] = view.getPaddingBottom();
        values[8] = drawable.getAlpha();
        values[9] = drawable.getLevel();
        values[10] = bounds.left;
        values[11] = bounds.top;
        values[12] = bounds.right;
        values[13] = bounds.bottom;
        values[14] = drawable instanceof BitmapDrawable
                ? ((BitmapDrawable) drawable).getBitmap().getGenerationId() : 0;
        values[15] = source.image ? 1 : 0;
        values[16] = drawable.getLayoutDirection();
        values[17] = visible.left;
        values[18] = visible.top;
        values[19] = visible.right;
        values[20] = visible.bottom;
        if (drawable instanceof ColorDrawable) {
            values[21] = ((ColorDrawable) drawable).getColor();
        }
        if (drawable instanceof GradientDrawable) {
            GradientDrawable gradient = (GradientDrawable) drawable;
            values[21] = Arrays.hashCode(gradient.getColors());
            values[22] = gradient.getColor() == null ? 0 : gradient.getColor().getDefaultColor();
            values[23] = gradient.getOrientation().ordinal();
            values[24] = gradient.getGradientType();
        }
        if (source.image) {
            float[] matrix = new float[9];
            ((ImageView) view).getImageMatrix().getValues(matrix);
            for (int i = 0; i < matrix.length; i++) {
                values[25 + i] = Float.floatToIntBits(matrix[i]);
            }
        }
        System.arraycopy(state, 0, values, 34, state.length);
        return new BubbleSourceStamp(view, drawable, values);
    }

    static boolean isCurrent(View bubble, BubbleSourceStamp expected) {
        return BubbleSourceStamp.same(expected, sourceStamp(bubble));
    }

    static Sample capture(View bubble, int blurDp) {
        if (bubble.getWidth() <= 0 || bubble.getHeight() <= 0) {
            return null;
        }
        Source selected = findSource(bubble);
        if (selected == null) {
            return null;
        }
        View source = selected.view;
        Drawable original = selected.drawable;
        int[] src = new int[2];
        int[] dst = new int[2];
        source.getLocationInWindow(src);
        bubble.getLocationInWindow(dst);
        Rect visible = new Rect();
        if (!bubble.getLocalVisibleRect(visible)) {
            return null;
        }
        BubbleRegion sourceRegion = new BubbleRegion(src[0] - dst[0], src[1] - dst[1],
                src[0] - dst[0] + source.getWidth(), src[1] - dst[1] + source.getHeight());
        BubbleRegion region = new BubbleRegion(0, 0, bubble.getWidth(), bubble.getHeight())
                .intersect(new BubbleRegion(visible.left, visible.top, visible.right, visible.bottom));
        region = region == null ? null : region.intersect(sourceRegion);
        if (region == null) {
            return null;
        }
        float density = bubble.getResources().getDisplayMetrics().density;
        int padding = (int) Math.ceil(blurDp * density * 3f);
        int fullWidth = region.width() + 2 * padding;
        int fullHeight = region.height() + 2 * padding;
        float scale = Math.max(4f, Math.max(fullWidth / 512f, fullHeight / 256f));
        int width = Math.max(1, (int) Math.ceil(fullWidth / scale));
        int height = Math.max(1, (int) Math.ceil(fullHeight / scale));
        int radius = Math.min(24, Math.max(0, Math.round(blurDp * density / scale)));
        // 滚动时气泡位置在变、背景本身没变。若整屏背景已缓存，直接按新位置裁切，
        // 省掉「newDrawable + 绘制 + getPixels」这一整段，让跟随刷新不再被重算拖慢。
        BubbleSourceStamp stampNow = stamp(selected, bubble);
        int[] sliced = sliceFromSheet(selected, region, padding, width, height, scale,
                src[0] - dst[0], src[1] - dst[1]);
        if (sliced != null) {
            return new Sample(sliced, width, height, scale, padding,
                    bubble.getWidth(), bubble.getHeight(), radius, region, stampNow);
        }
        rememberSheet(selected);

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        try {
            Drawable.ConstantState state = original.getConstantState();
            if (state == null) {
                return null;
            }
            Drawable drawable = state.newDrawable(source.getResources()).mutate();
            drawable.setAlpha(original.getAlpha());
            drawable.setState(original.getState());
            drawable.setLevel(original.getLevel());
            drawable.setLayoutDirection(original.getLayoutDirection());
            Canvas canvas = new Canvas(bitmap);
            canvas.scale(1f / scale, 1f / scale);
            canvas.translate(sourceRegion.left - region.left + padding, sourceRegion.top - region.top + padding);
            canvas.clipRect(0, 0, source.getWidth(), source.getHeight());
            if (selected.image) {
                ImageView iv = (ImageView) source;
                canvas.translate(iv.getPaddingLeft(), iv.getPaddingTop());
                canvas.concat(iv.getImageMatrix());
                drawable.setBounds(original.getBounds());
            } else {
                drawable.setBounds(0, 0, source.getWidth(), source.getHeight());
            }
            drawable.draw(canvas);
            int[] pixels = new int[width * height];
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
            region.extendEdges(pixels, width, height, scale, padding, sourceRegion);
            return new Sample(pixels, width, height, scale, padding,
                    bubble.getWidth(), bubble.getHeight(), radius, region, stampNow);
        } finally {
            bitmap.recycle();
        }
    }

    // ---------------------------------------------------------------- 整屏背景缓存
    //
    // 滚动时只有气泡位置变化，源背景（壁纸/背景 Drawable）本身不变。
    // 之前每次位置变化都重新 newDrawable + 绘制 + getPixels，成本高且随气泡数线性增长，
    // 于是把源区域按固定比例渲染成一张位图缓存，滑动时只做整数倍裁切。
    // 缓存键包含源 Drawable 身份、尺寸、alpha/level/state 与位图 generation，
    // 任一项变化即失效重建，保证不会用旧背景算出「冻结」的模糊。

    /** 整屏背景像素缓存的容量上限（2 MiB，按所有槽位合计）。 */
    private static final int SHEET_LIMIT_BYTES = 2 * 1024 * 1024;
    /** 整屏背景最长边像素上限，保证裁切足够快又不失真太明显。 */
    private static final int SHEET_MAX_SIDE = 768;
    /** 最多缓存几块不同的源背景（通常只有一张聊天壁纸，留余量给分屏/切换场景）。 */
    private static final int SHEET_SLOTS = 3;

    /**
     * 按源视图分槽缓存。
     *
     * 多个气泡可能挂在不同的源背景上（例如背景图 + 局部色块），
     * 单槽会在它们之间反复重建，反而比不缓存更慢，因此按 Drawable 身份分槽。
     */
    private static final List<Sheet> SHEETS = new ArrayList<Sheet>();

    /** 缓存键：源 Drawable 身份 + 尺寸 + alpha/level/state + 位图 generation。 */
    private static long sheetKeyOf(View source, Drawable drawable) {
        long key = 1125899906842597L;
        key = key * 31 + System.identityHashCode(drawable);
        key = key * 31 + source.getWidth();
        key = key * 31 + source.getHeight();
        key = key * 31 + drawable.getAlpha();
        key = key * 31 + drawable.getLevel();
        key = key * 31 + drawable.getLayoutDirection();
        if (drawable instanceof BitmapDrawable) {
            Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
            key = key * 31 + (bitmap == null ? 0 : bitmap.getGenerationId());
        }
        key = key * 31 + Arrays.hashCode(drawable.getState());
        return key;
    }

    /**
     * 首次为某块源背景建立整屏缓存。
     *
     * 只缓存「纯色 / 渐变 / 位图」三种可安全重绘的 Drawable（见 canSample），
     * 且源尺寸过大时不做缓存，避免内存与首帧开销。
     */
    private static synchronized void rememberSheet(Source selected) {
        try {
            View source = selected.view;
            Drawable drawable = selected.drawable;
            if (source.getWidth() <= 0 || source.getHeight() <= 0) {
                return;
            }
            int side = Math.max(source.getWidth(), source.getHeight());
            if (side <= 0) {
                return;
            }
            float sheetScale = Math.max(1f, side / (float) SHEET_MAX_SIDE);
            int width = Math.max(1, Math.round(source.getWidth() / sheetScale));
            int height = Math.max(1, Math.round(source.getHeight() / sheetScale));
            if ((long) width * height * 4 > SHEET_LIMIT_BYTES) {
                return;
            }
            Drawable.ConstantState state = drawable.getConstantState();
            if (state == null) {
                return;
            }
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            try {
                Drawable copy = state.newDrawable(source.getResources()).mutate();
                copy.setAlpha(drawable.getAlpha());
                copy.setState(drawable.getState());
                copy.setLevel(drawable.getLevel());
                copy.setLayoutDirection(drawable.getLayoutDirection());
                Canvas canvas = new Canvas(bitmap);
                canvas.scale(1f / sheetScale, 1f / sheetScale);
                if (selected.image) {
                    ImageView iv = (ImageView) source;
                    canvas.translate(iv.getPaddingLeft(), iv.getPaddingTop());
                    canvas.concat(iv.getImageMatrix());
                    copy.setBounds(drawable.getBounds());
                } else {
                    copy.setBounds(0, 0, source.getWidth(), source.getHeight());
                }
                copy.draw(canvas);
                int[] pixels = new int[width * height];
                bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
                long key = sheetKeyOf(source, drawable);
                // 同键的旧槽位先移除（背景已变），再按 LRU 淘汰最久未用的一块。
                for (int i = SHEETS.size() - 1; i >= 0; i--) {
                    if (SHEETS.get(i).key == key) {
                        SHEETS.remove(i);
                    }
                }
                SHEETS.add(new Sheet(pixels, width, height, sheetScale, key));
                while (SHEETS.size() > SHEET_SLOTS || totalSheetBytes() > SHEET_LIMIT_BYTES) {
                    SHEETS.remove(0);
                }
            } finally {
                bitmap.recycle();
            }
        } catch (Throwable ignore) {
            // 缓存失败不影响正确性，下次仍走完整重绘。
        }
    }

    private static class Sheet {
        final int[] pixels;
        final int width;
        final int height;
        /** 一个缓存像素对应多少源像素。 */
        final float scale;
        final long key;

        Sheet(int[] pixels, int width, int height, float scale, long key) {
            this.pixels = pixels;
            this.width = width;
            this.height = height;
            this.scale = scale;
            this.key = key;
        }
    }

    private static int totalSheetBytes() {
        int bytes = 0;
        for (int i = 0; i < SHEETS.size(); i++) {
            bytes += SHEETS.get(i).pixels.length * 4;
        }
        return bytes;
    }

    /** 命中指定键的槽位；命中即移到队尾（LRU）。 */
    private static Sheet findSheet(long key) {
        for (int i = 0; i < SHEETS.size(); i++) {
            Sheet candidate = SHEETS.get(i);
            if (candidate.key == key) {
                if (i != SHEETS.size() - 1) {
                    SHEETS.remove(i);
                    SHEETS.add(candidate);
                }
                return candidate;
            }
        }
        return null;
    }

    /**
     * 从整屏缓存里裁出气泡所在区域。
     *
     * 坐标换算：目标像素 (x,y) 对应源坐标
     * `region.left - srcLeft + (x - pad) * scale`，再除以缓存 scale 得到缓存像素下标。
     * 越界按边缘钳制，与 `BubbleRegion.extendEdges` 的语义一致。
     *
     * @return 像素数组；缓存不可用或比例不匹配时返回 null，由调用方走完整重绘。
     */
    private static synchronized int[] sliceFromSheet(Source selected, BubbleRegion region, int padding,
                                                     int width, int height, float scale,
                                                     int srcLeft, int srcTop) {
        Sheet current = findSheet(sheetKeyOf(selected.view, selected.drawable));
        if (current == null) {
            return null;
        }
        // 目标比例必须不细于缓存比例，才能直接取整像素；否则会拉伸模糊。
        if (scale + 0.001f < current.scale) {
            return null;
        }
        int originX = region.left - srcLeft;
        int originY = region.top - srcTop;
        int[] out = new int[width * height];
        for (int y = 0; y < height; y++) {
            float srcY = originY + (y - padding) * scale;
            int iy = clamp((int) (srcY / current.scale), current.height);
            int rowOut = y * width;
            int rowIn = iy * current.width;
            for (int x = 0; x < width; x++) {
                float srcX = originX + (x - padding) * scale;
                int ix = clamp((int) (srcX / current.scale), current.width);
                out[rowOut + x] = current.pixels[rowIn + ix];
            }
        }
        return out;
    }

    private static int clamp(int value, int limit) {
        if (value < 0) {
            return 0;
        }
        return value >= limit ? limit - 1 : value;
    }

    private static boolean safeDrawable(Drawable drawable) {
        return drawable instanceof BitmapDrawable || drawable instanceof ColorDrawable
                || drawable instanceof GradientDrawable;
    }

    private static boolean canSample(View source, Drawable drawable, View bubble) {
        return source != null && safeDrawable(drawable) && drawable.getAlpha() > 0
                && drawable.getConstantState() != null && overlaps(source, bubble)
                && source.getScaleX() == 1f && source.getScaleY() == 1f && source.getRotation() == 0f;
    }

    private static boolean overlaps(View source, View bubble) {
        if (!source.isShown() || source.getWidth() <= 0 || source.getHeight() <= 0) {
            return false;
        }
        int[] a = new int[2];
        int[] b = new int[2];
        source.getLocationInWindow(a);
        bubble.getLocationInWindow(b);
        return new BubbleRegion(a[0], a[1], a[0] + source.getWidth(), a[1] + source.getHeight())
                .intersect(new BubbleRegion(b[0], b[1], b[0] + bubble.getWidth(), b[1] + bubble.getHeight())) != null;
    }

    private static View findWallpaper(View root, View bubble) {
        ArrayDeque<View> queue = new ArrayDeque<View>();
        queue.add(root);
        for (int visited = 0; !queue.isEmpty() && visited < 1024; visited++) {
            View view = queue.removeFirst();
            Object tag = view.getTag();
            String className = view.getClass().getName();
            boolean knownOverlay = "mdl_global_bg_overlay".equals(tag)
                    || "wex_tab_bg_overlay".equals(tag);
            boolean knownClass = WALLPAPER_CLASS.equals(className)
                    || className.endsWith("ChattingImageBGView");
            if ((knownOverlay || knownClass) && overlaps(view, bubble)) {
                return view;
            }
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount() && queue.size() < 1024; i++) {
                    queue.addLast(group.getChildAt(i));
                }
            }
        }
        return null;
    }

    static synchronized Bitmap blur(Sample sample) {
        for (int i = 0; i < CACHE.size(); i++) {
            Cached entry = CACHE.get(i);
            Sample old = entry.sample;
            if (old.width == sample.width && old.height == sample.height && old.radius == sample.radius
                    && Arrays.equals(old.pixels, sample.pixels)) {
                CACHE.remove(i);
                CACHE.add(entry);
                return entry.bitmap;
            }
        }
        int[] pixels = BubbleBlur.blur(sample.pixels, sample.width, sample.height, sample.radius);
        Bitmap bitmap = Bitmap.createBitmap(pixels, sample.width, sample.height, Bitmap.Config.ARGB_8888);
        Cached entry = new Cached(sample, bitmap);
        CACHE.add(entry);
        cacheBytes += entry.bytes();
        while (CACHE.size() > 6 || cacheBytes > CACHE_BYTES) {
            // 旧位图可能仍是可见气泡的背景，交给 GC，不主动 recycle。
            cacheBytes -= CACHE.remove(0).bytes();
        }
        return bitmap;
    }

    static synchronized void clearCache() {
        CACHE.clear();
        cacheBytes = 0;
        SHEETS.clear();
    }

    static Drawable drawable(Bitmap bitmap, Sample sample, float corner, int tint, boolean dark, float density) {
        return new GlassDrawable(bitmap, sample, corner, tint, dark, density);
    }

    private static final class GlassDrawable extends Drawable {
        private final Bitmap bitmap;
        private final RectF crop;
        private final RectF area;
        private final float corner;
        private final int tint;
        private final boolean dark;
        private final float stroke;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Path path = new Path();
        private int alpha = 255;

        GlassDrawable(Bitmap bitmap, Sample sample, float corner, int tint, boolean dark, float density) {
            this.bitmap = bitmap;
            crop = sample == null ? null : new RectF(sample.padding / sample.scale, sample.padding / sample.scale,
                    (sample.padding + sample.region.width()) / sample.scale,
                    (sample.padding + sample.region.height()) / sample.scale);
            area = sample == null ? null : new RectF((float) sample.region.left / sample.viewWidth,
                    (float) sample.region.top / sample.viewHeight, (float) sample.region.right / sample.viewWidth,
                    (float) sample.region.bottom / sample.viewHeight);
            this.corner = corner;
            this.tint = tint;
            this.dark = dark;
            stroke = Math.max(1f, density);
        }

        @Override
        public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            RectF rect = new RectF(bounds);
            path.reset();
            path.addRoundRect(rect, corner, corner, Path.Direction.CW);
            int saved = canvas.save();
            canvas.clipPath(path);
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setAlpha(alpha);
            if (bitmap != null && crop != null && area != null) {
                RectF target = new RectF(bounds.left + bounds.width() * area.left,
                        bounds.top + bounds.height() * area.top, bounds.left + bounds.width() * area.right,
                        bounds.top + bounds.height() * area.bottom);
                int imageSave = canvas.save();
                canvas.clipRect(target);
                canvas.translate(target.left, target.top);
                canvas.scale(target.width() / crop.width(), target.height() / crop.height());
                canvas.translate(-crop.left, -crop.top);
                canvas.drawBitmap(bitmap, null, new RectF(0, 0, bitmap.getWidth(), bitmap.getHeight()), paint);
                canvas.restoreToCount(imageSave);
            }
            int rgb = dark ? 0x00182022 : 0x00FFFFFF;
            int top = bitmap == null ? Math.max(90, tint) : tint;
            paint.setShader(new LinearGradient(0, bounds.top, 0, bounds.bottom,
                    new int[]{(top << 24) | rgb, ((top * 2 / 3) << 24) | rgb},
                    null, Shader.TileMode.CLAMP));
            canvas.drawRect(rect, paint);
            paint.setShader(null);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(stroke);
            paint.setColor(dark ? 0x55FFFFFF : 0x70FFFFFF);
            paint.setAlpha(Math.round(alpha * (dark ? 0x55 : 0x70) / 255f));
            rect.inset(stroke / 2f, stroke / 2f);
            canvas.drawRoundRect(rect, corner, corner, paint);
            canvas.restoreToCount(saved);
        }

        @Override
        public void setAlpha(int value) {
            alpha = value;
            invalidateSelf();
        }

        @Override
        public void setColorFilter(ColorFilter filter) {
            paint.setColorFilter(filter);
            invalidateSelf();
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
