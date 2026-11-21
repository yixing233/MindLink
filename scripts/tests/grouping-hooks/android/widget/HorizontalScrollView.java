package android.widget;
import android.content.Context;
public class HorizontalScrollView extends FrameLayout {
    public boolean fillViewport;
    public int viewportChanges;
    public HorizontalScrollView(Context context) { super(context); }
    public void setHorizontalScrollBarEnabled(boolean value) { }
    public void setFillViewport(boolean value) { if (fillViewport != value) { fillViewport = value; viewportChanges++; requestLayout(); } }
}
