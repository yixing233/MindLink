package android.widget;
import android.content.Context;
public class LinearLayout extends android.view.ViewGroup {
    public static final int HORIZONTAL = 0;
    public LinearLayout(Context context) { super(context); }
    public void setOrientation(int value) { }
    public void setGravity(int value) { }
    public static class LayoutParams extends android.view.ViewGroup.LayoutParams {
        public float weight;
        public LayoutParams(int width, int height) { super(width, height); }
        public LayoutParams(int width, int height, float weight) { super(width, height); this.weight = weight; }
    }
}
