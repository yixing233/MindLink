package android.widget;

import android.view.ViewGroup;

public class LinearLayout extends ViewGroup {
    public static final int HORIZONTAL = 0;
    public static final int VERTICAL = 1;
    private int orientation;

    public void setOrientation(int orientation) { this.orientation = orientation; }
    public int getOrientation() { return orientation; }
}
