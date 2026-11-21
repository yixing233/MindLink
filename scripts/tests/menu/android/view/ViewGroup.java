package android.view;

import android.content.Context;
import android.graphics.Rect;
import java.util.ArrayList;
import java.util.List;

public class ViewGroup extends View implements ViewParent {
    protected final List<View> children = new ArrayList<View>();
    public int additions, removals;

    public ViewGroup(Context context) { super(context); }

    public static class LayoutParams {
        public static final int MATCH_PARENT = -1;
        public static final int WRAP_CONTENT = -2;
        public int width, height;
        public LayoutParams(int width, int height) { this.width = width; this.height = height; }
    }

    public static class MarginLayoutParams extends LayoutParams {
        public int leftMargin, topMargin, rightMargin, bottomMargin;
        private int start = Integer.MIN_VALUE, end = Integer.MIN_VALUE;
        public MarginLayoutParams(int width, int height) { super(width, height); }
        public MarginLayoutParams(MarginLayoutParams other) {
            super(other.width, other.height);
            setMargins(other.leftMargin, other.topMargin, other.rightMargin, other.bottomMargin);
            start = other.start;
            end = other.end;
        }
        public void setMargins(int left, int top, int right, int bottom) {
            leftMargin = left; topMargin = top; rightMargin = right; bottomMargin = bottom;
        }
        public boolean isMarginRelative() { return start != Integer.MIN_VALUE || end != Integer.MIN_VALUE; }
        public void setMarginStart(int value) { start = value; }
        public void setMarginEnd(int value) { end = value; }
        public int getMarginStart() { return start == Integer.MIN_VALUE ? leftMargin : start; }
        public int getMarginEnd() { return end == Integer.MIN_VALUE ? rightMargin : end; }
    }

    public int getChildCount() { return children.size(); }
    public View getChildAt(int index) { return children.get(index); }
    public int indexOfChild(View view) { return children.indexOf(view); }
    public void addView(View view) { addView(view, children.size()); }
    public void addView(View view, LayoutParams params) { view.setLayoutParams(params); addView(view); }
    protected LayoutParams generateDefaultLayoutParams() { return new LayoutParams(-1, -2); }
    protected boolean checkLayoutParams(LayoutParams params) { return params != null; }
    protected LayoutParams generateLayoutParams(LayoutParams params) { return new LayoutParams(params.width, params.height); }
    public void addView(View view, int index) {
        if (view.parent != null) throw new IllegalStateException("already attached");
        LayoutParams params = view.getLayoutParams();
        if (params == null) view.setLayoutParams(generateDefaultLayoutParams());
        else if (!checkLayoutParams(params)) view.setLayoutParams(generateLayoutParams(params));
        children.add(index, view);
        view.parent = this;
        if(isAttachedToWindow())view.attachForTest();
        additions++;
    }
    public void removeAllViews() { for(View view:new ArrayList<>(children))removeView(view); }
    public void setClipToPadding(boolean value) {}
    public void setClipChildren(boolean value) {}
    public void removeView(View view) {
        if (children.remove(view)) { view.detachForTest();view.parent = null; removals++; }
    }
    @Override
    public View findViewWithTag(Object value) {
        View match = super.findViewWithTag(value);
        if (match != null) return match;
        for (View child : children) {
            match = child.findViewWithTag(value);
            if (match != null) return match;
        }
        return null;
    }
    public void offsetDescendantRectToMyCoords(View child, Rect rect) {
        View current = child;
        while (current != this) {
            rect.offset(current.getLeft() - current.getScrollX(), current.getTop() - current.getScrollY());
            if (!(current.getParent() instanceof View)) throw new IllegalArgumentException("not a descendant");
            current = (View) current.getParent();
        }
    }
}
