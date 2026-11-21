package android.widget;
import android.content.Context;
public class TextView extends android.view.View {
    private CharSequence text = "";
    public int color, textChanges;
    public TextView(Context context) { super(context); }
    public void setText(CharSequence value) { if (!text.equals(value)) { text = value; textChanges++; requestLayout(); } }
    public CharSequence getText() { return text; }
    public void setTextSize(int value) { }
    public void setGravity(int value) { }
    public void setMinHeight(int value) { }
    public void setTextColor(int value) { color = value; }
}
