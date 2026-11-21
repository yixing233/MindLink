package android.widget;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.ViewGroup;

public class LinearLayout extends ViewGroup {
    public static final int HORIZONTAL = 0, VERTICAL = 1;
    public static final int SHOW_DIVIDER_MIDDLE = 2;
    private int orientation, showDividers, dividerPadding;
    private Drawable divider;

    public LinearLayout(Context context) { super(context); }
    public static class LayoutParams extends ViewGroup.MarginLayoutParams {
        public float weight;
        public LayoutParams(int width, int height) { super(width, height); }
        public LayoutParams(int width, int height, float weight) { super(width, height); this.weight = weight; }
    }
    @Override protected ViewGroup.LayoutParams generateDefaultLayoutParams() { return new LayoutParams(-1, -2); }
    @Override protected boolean checkLayoutParams(ViewGroup.LayoutParams params) { return params instanceof LayoutParams; }
    @Override protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams params) {
        LayoutParams copy = new LayoutParams(params.width, params.height);
        if (params instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) params;
            copy.setMargins(margins.leftMargin, margins.topMargin, margins.rightMargin, margins.bottomMargin);
        }
        return copy;
    }
    public void setOrientation(int value) { orientation = value; }
    public int getOrientation() { return orientation; }
    public void setGravity(int value) {}
    public void setBaselineAligned(boolean value) {}
    public void setDividerDrawable(Drawable value) { divider = value; }
    public Drawable getDividerDrawable() { return divider; }
    public void setShowDividers(int value) { showDividers = value; }
    public int getShowDividers() { return showDividers; }
    public void setDividerPadding(int value) { dividerPadding = value; }
    public int getDividerPadding() { return dividerPadding; }
}
