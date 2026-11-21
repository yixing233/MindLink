package android.graphics.drawable;

import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Rect;

public class Drawable {
    private Rect bounds = new Rect(0, 0, 0, 0);
    private int alpha = 255;
    private int level;
    private int direction;
    private int[] state = new int[0];
    private Integer tint;

    public static abstract class ConstantState {
        public Drawable newDrawable() { return newDrawable(null); }
        public abstract Drawable newDrawable(Resources resources);
    }

    public void setTint(int value) { tint = value; }
    public Integer getTint() { return tint; }

    public ConstantState getConstantState() {
        return new ConstantState() {
            @Override public Drawable newDrawable(Resources resources) { return new Drawable(); }
        };
    }

    public Drawable mutate() { return this; }
    public void draw(Canvas canvas) {}
    public void setBounds(Rect bounds) { this.bounds = bounds;onBoundsChange(bounds); }
    protected void onBoundsChange(Rect bounds) {}
    public void invalidateSelf() {}
    public Rect getBounds() { return bounds; }
    public int getAlpha() { return alpha; }
    public void setAlpha(int value) { alpha = value; }
    public int getLevel() { return level; }
    public boolean setLevel(int value) { level = value; return true; }
    public int getLayoutDirection() { return direction; }
    public boolean setLayoutDirection(int value) { direction = value; return true; }
    public boolean setState(int[] state) { this.state = state; return true; }
    public int[] getState() { return state; }
    public boolean isStateful() { return false; }
    public int getIntrinsicHeight() { return -1; }
    public void setColorFilter(ColorFilter value) {}
    public int getOpacity() { return -3; }
}
