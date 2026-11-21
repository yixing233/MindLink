package android.view;
import java.util.ArrayList;
import java.util.List;
public class ViewGroup extends View {
    public final List<View> children = new ArrayList<>();
    public boolean clipChildren = true;
    public boolean getClipChildren() { return clipChildren; }
    public void add(View view) { children.add(view); view.parent = this; view.windowToken = windowToken; }
    @Override public void draw(android.graphics.Canvas canvas) {
        super.draw(canvas);
        for (View child : children) if (child.isShown()) child.draw(canvas);
    }
    public int getChildCount() { return children.size(); }
    public View getChildAt(int index) { return children.get(index); }
}
