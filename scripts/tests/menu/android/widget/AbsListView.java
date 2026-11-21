package android.widget;

import android.content.Context;
import android.view.ViewGroup;

public class AbsListView extends AdapterView {
    public AbsListView(Context context) { super(context); }
    public interface OnScrollListener { int SCROLL_STATE_TOUCH_SCROLL = 1; }
    public static class LayoutParams extends ViewGroup.LayoutParams {
        public LayoutParams(int width, int height) { super(width, height); }
    }
}
