package wex.ui.core.feature;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;

import wex.ui.core.WexStore;

/**
 * 气泡外观的三种模式应用逻辑（由 x4/l pswitch_3 调用）。
 *
 * mode 0：交给原 smali 逻辑（毛玻璃渐变）——本类返回 false。
 * mode 1：自定义图片。气泡视图大小已知（setBackground 时布局未定，用 post 再量一次），
 *         解码后圆角裁切，按 bubble_bg_alpha 设不透明度。
 * mode 2：透明气泡——清空背景，画半透明白描边圆角。
 *
 * 圆角半径沿用 bubble_blur_radius（dp），默认 12。
 */
public final class BubbleApply {

    private BubbleApply() {
    }

    /** @return true = 已处理（跳过原毛玻璃逻辑）；false = 走原逻辑 */
    public static boolean apply(View view) {
        try {
            int mode = readInt("bubble_mode", 0);
            if (mode == 0) {
                return false;
            }
            float density = view.getResources().getDisplayMetrics().density;
            float radius = readInt("bubble_blur_radius", 12) * density;
            int alpha = BubbleStyleCard.readAlpha();

            if (mode == 2) {
                applyTransparent(view, density, radius);
                return true;
            }

            // mode 1: 自定义图片（对方气泡靠左，我方气泡靠右）
            boolean self = isSelfBubble(view);
            String key = self ? "bubble_bg_path_self" : "bubble_bg_path_peer";
            String path = WexStore.INSTANCE.getString(key, "");
            if (path == null || path.length() == 0) {
                // 一侧没配图时回退到旧的统一 key
                path = WexStore.INSTANCE.getString("bubble_bg_path", "");
            }
            if (path == null || path.length() == 0) {
                return false; // 没选图，回退毛玻璃
            }
            applyImage(view, path, radius, alpha);
            return true;
        } catch (Throwable t) {
            return false; // 任何异常回退到原效果，不让聊天页崩
        }
    }

    private static void applyTransparent(View view, float density, float radius) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        d.setColor(0x00000000);
        d.setCornerRadius(radius);
        d.setStroke(Math.max(1, Math.round(1.2f * density)), 0x66FFFFFF);
        view.setBackground(d);
        view.setElevation(0f);
        view.setMinimumWidth(0);
    }

    private static void applyImage(final View view, final String path,
                                   final float radius, final int alpha) {
        // 优先按 .9（NinePatch）图处理：整图替换气泡，自动拉伸
        if (tryNinePatch(view, path, alpha)) {
            return;
        }
        // setBackground 阶段视图多半还没量完，post 到下一帧再画
        Runnable task = new Runnable() {
            @Override
            public void run() {
                try {
                    int w = view.getWidth();
                    int h = view.getHeight();
                    if (w <= 0 || h <= 0) {
                        w = (int) (220 * view.getResources().getDisplayMetrics().density);
                        h = (int) (80 * view.getResources().getDisplayMetrics().density);
                    }
                    Bitmap src = decodeScaled(path, w, h);
                    if (src == null) {
                        return;
                    }
                    Bitmap out = roundCorners(src, radius);
                    if (out != src) {
                        src.recycle();
                    }
                    BitmapDrawable d = new BitmapDrawable(view.getResources(), out);
                    d.setAlpha(alpha);
                    d.setFilterBitmap(true);
                    view.setBackground(d);
                    view.setElevation(0f);
                } catch (Throwable ignore) {
                }
            }
        };
        view.post(task);
        view.postDelayed(task, 120L); // 布局稳定后再刷一次
    }


    /** .9 图：含 npTc 块则整体替换气泡背景，自动拉伸，不做圆角/裁切。 */
    private static boolean tryNinePatch(View view, String path, int alpha) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inScaled = false;
            Bitmap bmp = BitmapFactory.decodeFile(path, o);
            if (bmp == null) {
                return false;
            }
            byte[] chunk = bmp.getNinePatchChunk();
            if (chunk == null || !android.graphics.NinePatch.isNinePatchChunk(chunk)) {
                bmp.recycle();
                return false;
            }
            android.graphics.drawable.NinePatchDrawable d =
                    new android.graphics.drawable.NinePatchDrawable(
                            view.getResources(), bmp, chunk, new android.graphics.Rect(), null);
            d.setAlpha(alpha);
            d.setFilterBitmap(true);
            view.setBackground(d);
            view.setElevation(0f);
            view.setMinimumWidth(0);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static Bitmap decodeScaled(String path, int targetW, int targetH) {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, opts);
        if (opts.outWidth <= 0 || opts.outHeight <= 0) {
            return null;
        }
        int sample = 1;
        while (opts.outWidth / (sample * 2) >= targetW && opts.outHeight / (sample * 2) >= targetH) {
            sample *= 2;
        }
        opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap raw = BitmapFactory.decodeFile(path, opts);
        if (raw == null) {
            return null;
        }
        // 居中裁成目标比例
        int rw = raw.getWidth();
        int rh = raw.getHeight();
        float targetRatio = (float) targetW / (float) targetH;
        float rawRatio = (float) rw / (float) rh;
        int cw;
        int ch;
        if (rawRatio > targetRatio) {
            ch = rh;
            cw = Math.round(rh * targetRatio);
        } else {
            cw = rw;
            ch = Math.round(rw / targetRatio);
        }
        int x = (rw - cw) / 2;
        int y = (rh - ch) / 2;
        Bitmap cropped = Bitmap.createBitmap(raw, x, y, cw, ch);
        if (cropped != raw) {
            raw.recycle();
        }
        return cropped;
    }

    private static Bitmap roundCorners(Bitmap src, float radiusPx) {
        if (radiusPx <= 0) {
            return src;
        }
        int w = src.getWidth();
        int h = src.getHeight();
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        RectF rect = new RectF(0, 0, w, h);
        Path path = new Path();
        path.addRoundRect(rect, radiusPx, radiusPx, Path.Direction.CW);
        canvas.clipPath(path);
        canvas.drawBitmap(src, 0, 0, paint);
        return out;
    }

    private static int readInt(String key, int def) {
        try {
            String raw = WexStore.INSTANCE.getString(key, String.valueOf(def));
            if (raw == null || raw.length() == 0) {
                return def;
            }
            return Integer.parseInt(raw.trim());
        } catch (Throwable t) {
            return def;
        }
    }

    /** 对方气泡靠左、我方靠右：用气泡中心在父容器中的水平位置判断。 */
    private static boolean isSelfBubble(View bubble) {
        try {
            float left = 0f;
            Object v = bubble;
            while (v instanceof View) {
                View vv = (View) v;
                left += vv.getLeft();
                Object p = vv.getParent();
                if (!(p instanceof View)) {
                    break;
                }
                v = p;
            }
            if (v instanceof View) {
                int width = ((View) v).getWidth();
                if (width > 0) {
                    float cx = left + bubble.getWidth() / 2f;
                    return cx > width / 2f;
                }
            }
        } catch (Throwable ignore) {
        }
        return false;
    }
}
