package android.graphics;

public final class Paint {
    public static final int ANTI_ALIAS_FLAG = 1;
    private int color;
    public Paint(int flags) { }
    public void setColor(int color) { this.color = color; }
    public int getColor() { return color; }
}
