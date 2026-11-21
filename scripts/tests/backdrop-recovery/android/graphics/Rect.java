package android.graphics;

public class Rect {
    public int left, top, right, bottom;
    public Rect() { }
    public Rect(int left, int top, int right, int bottom) { set(left, top, right, bottom); }
    public Rect(Rect other) { this(other.left, other.top, other.right, other.bottom); }
    public void set(Rect other) { set(other.left, other.top, other.right, other.bottom); }
    public void set(int left, int top, int right, int bottom) {
        this.left = left; this.top = top; this.right = right; this.bottom = bottom;
    }
    public int width() { return right - left; }
    public int height() { return bottom - top; }
    public boolean isEmpty() { return left >= right || top >= bottom; }
    public void offset(int x, int y) { left += x; right += x; top += y; bottom += y; }
    public static boolean intersects(Rect a, Rect b) {
        return a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom;
    }
}
