package android.graphics;
public class Rect {
    public int left, top, right, bottom;
    public void set(int l, int t, int r, int b) { left = l; top = t; right = r; bottom = b; }
    public boolean contains(int x, int y) { return x >= left && x < right && y >= top && y < bottom; }
}
