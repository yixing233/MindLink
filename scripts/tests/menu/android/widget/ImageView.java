package android.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.view.View;

public class ImageView extends View {
    public enum ScaleType { FIT_CENTER }
    private Drawable image;
    private ColorStateList tint;
    public ImageView(Context context) { super(context); }
    public void setScaleType(ScaleType value) {}
    public void setImageDrawable(Drawable value) { image = value; }
    public Drawable getDrawable() { return image; }
    public void setImageTintList(ColorStateList value) { tint = value; }
    public ColorStateList getImageTintList() { return tint; }
}
