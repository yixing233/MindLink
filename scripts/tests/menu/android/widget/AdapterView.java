package android.widget;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

public class AdapterView extends ViewGroup {
    public AdapterView(Context context) { super(context); }
    @Override public void addView(View view, int index) { throw new AssertionError("AdapterView.addView"); }
    @Override public void removeView(View view) { throw new AssertionError("AdapterView.removeView"); }
    public void displayNativeRow(View row) { super.addView(row, getChildCount()); }
    protected void attachFooter(View row) { super.addView(row, getChildCount()); }
    protected void detachFooter(View row) { super.removeView(row); }
}
