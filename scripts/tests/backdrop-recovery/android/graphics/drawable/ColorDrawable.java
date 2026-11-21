package android.graphics.drawable;

/** Only models the public observable color used by the production background token. */
public class ColorDrawable extends Drawable {
    private int color;
    public ColorDrawable(int value) { setColor(value); }
    public int getColor() { return color; }
    public void setColor(int value) { color = value; content = "color:" + Integer.toHexString(value); }
}
