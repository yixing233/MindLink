package android.graphics;

/** Records the shape a Drawable reports for clipping and shadow casting. */
public class Outline {
    public static final int RECT = 0, ROUND_RECT = 1;
    public int kind = -1;
    public Rect rect;
    public float radius;

    public void setRect(Rect value) { kind = RECT; rect = new Rect(value); radius = 0f; }
    public void setRect(int left, int top, int right, int bottom) {
        kind = RECT; rect = new Rect(left, top, right, bottom); radius = 0f;
    }
    public void setRoundRect(Rect value, float radius) {
        kind = ROUND_RECT; rect = new Rect(value); this.radius = radius;
    }
    public void setRoundRect(int left, int top, int right, int bottom, float radius) {
        kind = ROUND_RECT; rect = new Rect(left, top, right, bottom); this.radius = radius;
    }
}
