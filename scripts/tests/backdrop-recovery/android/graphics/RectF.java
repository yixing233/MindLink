package android.graphics;

public class RectF {
    public float left, top, right, bottom;
    public RectF() { }
    public RectF(float left, float top, float right, float bottom) {
        this.left = left; this.top = top; this.right = right; this.bottom = bottom;
    }
    public RectF(RectF other) { set(other); }
    public void set(RectF other) { left = other.left; top = other.top; right = other.right; bottom = other.bottom; }
    public void set(float left, float top, float right, float bottom) {
        this.left = left; this.top = top; this.right = right; this.bottom = bottom;
    }
    public void set(int left, int top, int right, int bottom) { set((float) left, (float) top, (float) right, (float) bottom); }
    public float width() { return right - left; }
    public float height() { return bottom - top; }
    @Override public boolean equals(Object value) {
        if (!(value instanceof RectF)) return false;
        RectF other = (RectF) value;
        return left == other.left && top == other.top && right == other.right && bottom == other.bottom;
    }
    @Override public int hashCode() { return Float.hashCode(left) ^ Float.hashCode(top) ^ Float.hashCode(right) ^ Float.hashCode(bottom); }
}
