package android.graphics.drawable;

import android.content.res.ColorStateList;
import android.content.res.Resources;

public class GradientDrawable extends Drawable {
    private ColorStateList color;
    private float radius;
    public void setColor(int value) { color = ColorStateList.valueOf(value); }
    public void setColor(ColorStateList value) { color = value; }
    public ColorStateList getColor() { return color; }
    public void setCornerRadius(float value) { radius = value; }
    public float getCornerRadius() { return radius; }
    @Override public ConstantState getConstantState() {
        return new ConstantState() {
            @Override public Drawable newDrawable(Resources resources) {
                GradientDrawable result = new GradientDrawable();
                result.color = color;
                result.radius = radius;
                return result;
            }
        };
    }
}
