package android.widget;

import android.content.Context;
import android.view.View;

public class ScrollView extends FrameLayout {
    public ScrollView(Context context) { super(context); }
    public void setFillViewport(boolean value) {}
    private int x,y;
    @Override public int getScrollX() { return x; }
    @Override public int getScrollY() { return y; }
    public void scrollTo(int x,int y) { int oldX=this.x,oldY=this.y;this.x=x;this.y=y;scrolledForTest(x,y,oldX,oldY); }
    @Override public void addView(View view, int index) {
        if (getChildCount() != 0) throw new IllegalStateException("ScrollView can host only one direct child");
        super.addView(view, index);
    }
}
