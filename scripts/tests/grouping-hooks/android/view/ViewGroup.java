package android.view;

import android.content.Context;
import java.util.ArrayList;
import java.util.List;

public class ViewGroup extends View {
    private final List<View> children = new ArrayList<>();
    public int additions, removals;
    public ViewGroup(Context context) { super(context); }
    public int getChildCount() { return children.size(); }
    public View getChildAt(int position) { return children.get(position); }
    public void addView(View view, LayoutParams params) {
        if (view.getParent() != null) throw new IllegalStateException("already parented");
        view.setLayoutParams(params); children.add(view); view.parentForTest(this);
        view.attachedForTest(isAttachedToWindow()); additions++;
    }
    public void removeView(View view) {
        if (children.remove(view)) { view.parentForTest(null); view.attachedForTest(false); removals++; }
    }
    public void removeAllViews() { for (View child : new ArrayList<>(children)) removeView(child); }
    public static class LayoutParams {
        public static final int MATCH_PARENT = -1, WRAP_CONTENT = -2;
        public int width, height;
        public LayoutParams(int width, int height) { this.width = width; this.height = height; }
    }
}
