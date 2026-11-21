package android.graphics;

public class Rect {
    public int left, top, right, bottom;

    public Rect() { this(0, 0, 0, 0); }
    public Rect(int left, int top, int right, int bottom) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    public int width() { return right-left; }
    public int height() { return bottom-top; }
    public float exactCenterY() { return (top+bottom)*.5f; }
    public void offset(int x, int y) {
        left += x;
        right += x;
        top += y;
        bottom += y;
    }
}
