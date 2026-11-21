package android.widget;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

public class AdapterView extends ViewGroup {
    public AdapterView(Context context) { super(context); }
    public interface OnItemClickListener { void onItemClick(AdapterView parent, View view, int position, long id); }
    public interface OnItemLongClickListener { boolean onItemLongClick(AdapterView parent, View view, int position, long id); }
}
