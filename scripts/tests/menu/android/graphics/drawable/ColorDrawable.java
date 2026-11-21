package android.graphics.drawable;

import android.content.res.Resources;

public class ColorDrawable extends Drawable {
    public final int color;

    public ColorDrawable(int color) { this.color = color; }
    public int getColor() { return color; }

    @Override
    public ConstantState getConstantState() {
        return new ConstantState() {
            @Override public Drawable newDrawable(Resources resources) { return new ColorDrawable(color); }
        };
    }
}
