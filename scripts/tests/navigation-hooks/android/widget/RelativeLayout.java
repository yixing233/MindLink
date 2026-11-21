package android.widget;
public class RelativeLayout extends android.view.ViewGroup {
    public static final int ALIGN_PARENT_BOTTOM=12;
    public RelativeLayout(android.content.Context context) { super(context); }
    public static class LayoutParams extends android.view.ViewGroup.LayoutParams {
        public LayoutParams(int width,int height) { super(width,height); }
        public void addRule(int value) { }
    }
}
