package android.view;

import android.content.Context;
import android.graphics.drawable.Drawable;

public class Window {
    private final ViewGroup decor;
    private Drawable background;
    public Window(Context context) { decor = new ViewGroup(context); decor.attachRootForTest(new Object()); decor.layout(0, 0, 1080, 2400); }
    public View getDecorView() { return decor; }
    public void setBackgroundDrawable(Drawable value) { background = value; }
    public Drawable getBackgroundDrawable() { return background; }
}
