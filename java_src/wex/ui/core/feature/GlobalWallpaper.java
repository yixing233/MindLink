package wex.ui.core.feature;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.view.View;
import android.widget.ImageView;

import wex.ui.core.WexStore;

/**
 * 全屏壁纸蒙层（TabBgDrawHook 那套）的图片。
 *
 * 来源：DecorView 全屏 ImageView 蒙层的方案参照 WeKit 的
 * `features/items/beautify/ApplyGlobalBackground.kt`（GPL-3.0）。
 *
 * 现状说明（已与用户确认）：
 *  - 真正生效的是 TabBgDrawHook 在 DecorView 上加的全屏 ImageView，不是「四页背景」的分页方案；
 *  - 之前这个蒙层没有更换入口、也无法调不透明度；
 *  - 扫一扫/拍摄/聊天页不该出现蒙层（已由 TabBgDrawHook.isExcludedPage 过滤）。
 *
 * 这里提供单一全局壁纸（key = tab_bg_global），四页共用一张，换图只改这一处。
 */
public final class GlobalWallpaper {

    public static final String KEY_PATH = "tab_bg_global";
    private static final String KEY_ALPHA = "tab_bg_alpha_0";
    private static final int DEFAULT_ALPHA = 70;

    private static String cachedPath;
    private static Bitmap cachedBitmap;
    private static long cachedModified, cachedLength;
    private static final java.util.WeakHashMap<View, SampleImage> SAMPLES =
            new java.util.WeakHashMap<View, SampleImage>();

    private static final class SampleImage {
        final Bitmap source;
        final int width, height;
        final BitmapDrawable drawable;

        SampleImage(Bitmap source, int width, int height, BitmapDrawable drawable) {
            this.source = source; this.width = width; this.height = height; this.drawable = drawable;
        }
    }

    private GlobalWallpaper() {
    }

    /** 有全局壁纸时返回它的 drawable，否则返回 null（调用方回落到原分页逻辑）。 */
    public static synchronized BitmapDrawable getDrawable(View anyView) {
        String path = currentPath();
        if (path.length() == 0) {
            return null;
        }
        Bitmap bitmap = bitmapFor(path);
        if (bitmap == null) {
            return null;
        }
        return new BitmapDrawable(anyView.getResources(), bitmap);
    }

    /** 供新全局背景蒙层按路径加载位图（带缓存与失败置空）。 */
    public static synchronized void loadInto(ImageView view, String path) {
        Bitmap bitmap = bitmapFor(path);
        if (bitmap != null && !bitmap.isRecycled()) {
            view.setImageDrawable(new BitmapDrawable(view.getResources(), bitmap));
        } else {
            view.setImageDrawable(null);
        }
    }

    /** Stable identity per root/file revision, with the same CENTER_CROP geometry as the overlay. */
    static synchronized BitmapDrawable drawableFor(View view, String path) {
        Bitmap bitmap = bitmapFor(path);
        int width = view.getWidth(), height = view.getHeight();
        if (bitmap == null || width <= 0 || height <= 0) return null;
        SampleImage cached = SAMPLES.get(view);
        if (cached != null && cached.source == bitmap && cached.width == width && cached.height == height) {
            return cached.drawable;
        }
        float sample = Math.max(1f, Math.max(width, height) / 1024f);
        int w = Math.max(1, Math.round(width / sample)), h = Math.max(1, Math.round(height / sample));
        Bitmap cropped = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        float scale = Math.max((float) w / bitmap.getWidth(), (float) h / bitmap.getHeight());
        float bw = bitmap.getWidth() * scale, bh = bitmap.getHeight() * scale;
        android.graphics.Canvas canvas = new android.graphics.Canvas(cropped);
        canvas.drawBitmap(bitmap, null, new android.graphics.RectF((w - bw) / 2, (h - bh) / 2,
                (w + bw) / 2, (h + bh) / 2), new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG));
        BitmapDrawable drawable = new BitmapDrawable(view.getResources(), cropped);
        drawable.setGravity(android.view.Gravity.FILL);
        drawable.setBounds(0, 0, width, height);
        SAMPLES.put(view, new SampleImage(bitmap, width, height, drawable));
        return drawable;
    }

    static String currentPath() {
        try {
            String path = WexStore.INSTANCE.getString(KEY_PATH, "");
            return path == null ? "" : path;
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static synchronized Bitmap bitmapFor(String path) {
        if (path == null || path.length() == 0) {
            return null;
        }
        java.io.File file = new java.io.File(path);
        long modified = file.lastModified(), length = file.length();
        if (!path.equals(cachedPath) || cachedBitmap == null || cachedBitmap.isRecycled()
                || modified != cachedModified || length != cachedLength) {
            Bitmap decoded;
            try {
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(path, bounds);
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = 1;
                while (Math.max(bounds.outWidth, bounds.outHeight) / options.inSampleSize > 2560) {
                    options.inSampleSize *= 2;
                }
                decoded = BitmapFactory.decodeFile(path, options);
            } catch (Throwable ignored) {
                decoded = null;
            }
            if (decoded == null) {
                return null;
            }
            cachedBitmap = decoded;
            cachedPath = path;
            cachedModified = modified;
            cachedLength = length;
            SAMPLES.clear();
        }
        return cachedBitmap;
    }

    /** 当前不透明度 0~255。 */
    public static int readAlpha() {
        try {
            String raw = WexStore.INSTANCE.getString(KEY_ALPHA, String.valueOf(DEFAULT_ALPHA));
            if (raw == null || raw.length() == 0) {
                return DEFAULT_ALPHA;
            }
            int v = Integer.parseInt(raw.trim());
            return Math.max(0, Math.min(255, v));
        } catch (Throwable t) {
            return DEFAULT_ALPHA;
        }
    }

    public static void writeAlpha(int alpha) {
        int v = Math.max(0, Math.min(255, alpha));
        try {
            for (int i = 0; i < 4; i++) {
                WexStore.INSTANCE.putString("tab_bg_alpha_" + i, String.valueOf(v));
            }
        } catch (Throwable ignore) {
        }
    }
}
