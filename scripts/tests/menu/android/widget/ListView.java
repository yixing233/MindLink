package android.widget;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

public class ListView extends AbsListView {
    private final List<View> footers = new ArrayList<View>();
    private Drawable selector, divider;
    private int dividerHeight;
    public int footerAdds, footerRemoves;

    public ListView(Context context) { super(context); }
    public void addFooterView(View row, Object data, boolean selectable) {
        if (footers.contains(row)) throw new AssertionError("duplicate footer");
        footers.add(row);
        attachFooter(row);
        footerAdds++;
    }
    public boolean removeFooterView(View row) {
        if (!footers.remove(row)) return false;
        detachFooter(row);
        footerRemoves++;
        return true;
    }
    public int getFooterViewsCount() { return footers.size(); }
    public View footerAt(int index) { return footers.get(index); }
    public void hideFooters() { for (View row : footers) detachFooter(row); }
    public void showFooters() { for (View row : footers) if (row.getParent() == null) attachFooter(row); }
    public void setSelector(Drawable value) { selector = value; }
    public Drawable getSelector() { return selector; }
    public void setDivider(Drawable value) { divider = value; }
    public Drawable getDivider() { return divider; }
    public void setDividerHeight(int value) { dividerHeight = value; }
    public int getDividerHeight() { return dividerHeight; }
}
