package android.graphics.drawable;

import android.content.res.ColorStateList;
import android.content.res.Resources;

public class RippleDrawable extends Drawable {
    private final ColorStateList colors;
    private final Drawable content;
    private final Drawable mask;

    public RippleDrawable(ColorStateList colors, Drawable content, Drawable mask) {
        this.colors = colors;
        this.content = content;
        this.mask = mask;
    }
    public Drawable getDrawable(int index) { return index == 0 ? content : mask; }
    public ColorStateList getColor() { return colors; }

    @Override public boolean isStateful() { return true; }

    @Override
    public ConstantState getConstantState() {
        return new ConstantState() {
            @Override public Drawable newDrawable(Resources resources) { return new RippleDrawable(colors, null, null); }
        };
    }
}
