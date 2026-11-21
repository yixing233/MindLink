package android.widget;
import android.content.Context;
public class RelativeLayout extends android.view.ViewGroup {
    public RelativeLayout(Context context) { super(context); }
    public static class LayoutParams extends android.view.ViewGroup.LayoutParams {
        public LayoutParams(int width, int height) { super(width, height); }
    }
}
