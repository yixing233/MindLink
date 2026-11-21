package android.widget;
import android.content.Context;
public class FrameLayout extends android.view.ViewGroup {
    public FrameLayout(Context context) { super(context); }
    public static class LayoutParams extends android.view.ViewGroup.LayoutParams {
        public LayoutParams(int width, int height) { super(width, height); }
    }
}
