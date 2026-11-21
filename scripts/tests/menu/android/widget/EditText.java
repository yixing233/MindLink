package android.widget;

import android.content.Context;
import android.graphics.drawable.Drawable;

public class EditText extends TextView {
    private int hint, highlight;
    private Drawable cursor, left, right, handle;
    public EditText(Context context) { super(context); }
    public void setHintTextColor(int value) { hint = value; }
    public int getHintTextColor() { return hint; }
    public void setHighlightColor(int value) { highlight = value; }
    public int getHighlightColor() { return highlight; }
    public Drawable getTextCursorDrawable() { return cursor; }
    public void setTextCursorDrawable(Drawable value) { cursor = value; }
    public Drawable getTextSelectHandleLeft() { return left; }
    public void setTextSelectHandleLeft(Drawable value) { left = value; }
    public Drawable getTextSelectHandleRight() { return right; }
    public void setTextSelectHandleRight(Drawable value) { right = value; }
    public Drawable getTextSelectHandle() { return handle; }
    public void setTextSelectHandle(Drawable value) { handle = value; }
}
