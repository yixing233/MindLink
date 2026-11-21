package android.widget;
public class ImageView {
    public int clearCalls;
    public void setImageDrawable(Object drawable) { clearCalls++; }
}
