package android.graphics.drawable;
import android.graphics.Canvas;
public class Drawable {
    public int opacity = -1;
    public String content = "host-background";
    public int failDraws, alpha = 255, level;
    private int[] state = new int[0];
    public int getAlpha() { return alpha; }
    public void setAlpha(int value) { alpha = value; }
    public void setColorFilter(android.graphics.ColorFilter value) { }
    public int getLevel() { return level; }
    public int[] getState() { return state; }
    public void setState(int[] value) { state = value.clone(); }
    private final android.graphics.Rect bounds = new android.graphics.Rect();
    public android.graphics.Rect getBounds() { return bounds; }
    public void setBounds(int l, int t, int r, int b) { bounds.set(l, t, r, b); }
    public void setBounds(android.graphics.Rect value) { setBounds(value.left, value.top, value.right, value.bottom); }
    public int getOpacity() { return opacity; }
    public void getOutline(android.graphics.Outline outline) { }
    public void draw(Canvas canvas) {
        canvas.mark(content); canvas.mark("background-bounds:" + bounds.right + "x" + bounds.bottom);
        if (failDraws > 0) { failDraws--; throw new IllegalStateException("background draw failure"); }
    }
}
