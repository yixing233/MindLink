package wex.ui.core.feature;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.view.animation.DecelerateInterpolator;

/**
 * Press feedback for the floating header's side buttons: a wave that spreads from the touch
 * point and is clipped to the button's circle, over a faint state layer, then fades on release.
 *
 * <p>Installed as the clickable view's foreground, so the platform already delivers the pressed
 * state and the touch hotspot. The circle is given in the view's own coordinates; for the
 * separated header it matches the drawn pill exactly.</p>
 */
final class HeaderButtonRipple extends Drawable {
    private static final long EXPAND_MS = 320L, FADE_MS = 240L;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private float cx, cy, radius, hotX = Float.NaN, hotY = Float.NaN, startX, startY;
    private int color;
    private boolean pressed;
    private float spread, strength;
    private ValueAnimator animator;

    /** Circle in view coordinates. Cheap when unchanged; called on every header frame. */
    void circle(float x, float y, float r, boolean dark) {
        int next = dark ? 0x3DFFFFFF : 0x29000000;
        if (x == cx && y == cy && r == radius && next == color) return;
        cx = x; cy = y; radius = r; color = next;
        invalidateSelf();
    }

    @Override public boolean isStateful() { return true; }

    @Override protected boolean onStateChange(int[] state) {
        boolean now = false;
        for (int value : state) if (value == android.R.attr.state_pressed) { now = true; break; }
        if (now == pressed) return false;
        pressed = now;
        if (now) press(); else release();
        return true;
    }

    @Override public void setHotspot(float x, float y) { hotX = x; hotY = y; }

    private void press() {
        cancel();
        startX = Float.isNaN(hotX) ? cx : hotX;
        startY = Float.isNaN(hotY) ? cy : hotY;
        strength = 1f; spread = 0f;
        animator = ValueAnimator.ofFloat(0f, 1f).setDuration(EXPAND_MS);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a -> { spread = (Float) a.getAnimatedValue(); invalidateSelf(); });
        animator.start();
    }

    /** A quick tap still finishes the wave, then everything fades. */
    private void release() {
        final float from = spread;
        cancel();
        animator = ValueAnimator.ofFloat(0f, 1f).setDuration(FADE_MS);
        animator.addUpdateListener(a -> {
            float f = (Float) a.getAnimatedValue();
            spread = from + (1f - from) * Math.min(1f, f * 2f);
            strength = 1f - f;
            invalidateSelf();
        });
        animator.start();
    }

    private void cancel() {
        if (animator != null) { animator.cancel(); animator = null; }
    }

    /** Stops any running animation; the drawable is being detached. */
    void stop() { cancel(); strength = 0f; pressed = false; }

    @Override public void draw(Canvas canvas) {
        if (strength <= 0f || radius <= 0f) return;
        int save = canvas.save();
        clip.reset();
        clip.addCircle(cx, cy, radius, Path.Direction.CW);
        canvas.clipPath(clip);
        int base = Color.alpha(color);
        paint.setColor(color);
        paint.setAlpha(Math.round(base * 0.5f * strength));
        canvas.drawCircle(cx, cy, radius, paint);
        float reach = (float) Math.hypot(startX - cx, startY - cy) + radius;
        paint.setAlpha(Math.round(base * strength));
        canvas.drawCircle(startX, startY, reach * spread, paint);
        canvas.restoreToCount(save);
    }

    @Override public void setAlpha(int alpha) { }
    @Override public void setColorFilter(ColorFilter filter) { }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
