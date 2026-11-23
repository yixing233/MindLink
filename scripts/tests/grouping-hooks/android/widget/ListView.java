package android.widget;
import android.content.Context;
import android.view.View;

public class ListView extends AdapterView {
    public Object adapter;
    public int headers = 1, visiblePosition = 1, firstTop, selection = -1, selections, stops;
    public boolean flinging;
    public Runnable onStop;
    public OnItemClickListener click;
    public OnItemLongClickListener longClick;
    public int headerAdds, headerRemoves, adapterSets;
    public RuntimeException selectionFailure;
    public static boolean dispatchSelectionHooks;
    private final java.util.List<View> headerViews = new java.util.ArrayList<>();
    public ListView(Context context) { super(context); }
    public void addHeaderView(View view, Object data, boolean selectable) {
        headerViews.add(view); headers++; headerAdds++;
        if (adapter instanceof HeaderViewListAdapter wrapper) wrapper.headers++;
    }
    public boolean removeHeaderView(View view) {
        if (!headerViews.remove(view)) return false;
        headers--; headerRemoves++;
        if (adapter instanceof HeaderViewListAdapter wrapper) wrapper.headers--;
        return true;
    }
    public Object getAdapter() { return adapter; }
    public int getHeaderViewsCount() { return headers; }
    public int getPositionForView(View row) {
        int header = headerViews.indexOf(row);
        return header < 0 ? visiblePosition : headers - headerViews.size() + header;
    }
    public int getFirstVisiblePosition() { return visiblePosition; }
    public OnItemClickListener getOnItemClickListener() { return click; }
    public OnItemLongClickListener getOnItemLongClickListener() { return longClick; }
    public void smoothScrollBy(int distance, int duration) {
        if (distance != 0 || duration != 0) throw new AssertionError("group transition must stop, not animate");
        if (onStop != null) onStop.run();
        flinging = false; stops++;
    }
    public void setSelection(int position) {
        if (dispatchSelectionHooks) {
            dispatchSelectionHooks = false;
            try { dev.mindlink.xposed.XposedBridge.invoke(this, "setSelection", new Class<?>[]{int.class}, position); }
            catch (RuntimeException error) { throw error; }
            catch (Throwable error) { throw new AssertionError(error); }
            finally { dispatchSelectionHooks = true; }
            return;
        }
        if (selectionFailure != null) throw selectionFailure;
        selection = position; visiblePosition = position; firstTop = 0; selections++;
    }
    public void setSelectionFromTop(int position, int top) {
        if (selectionFailure != null) throw selectionFailure;
        selection = position; visiblePosition = position; firstTop = top; selections++;
    }
    public void setAdapter(ListAdapter value) { adapter = value; adapterSets++; }
    public void scrollForTest(int position, int top) { visiblePosition = position; firstTop = top; }
    public void flingStepForTest(int oldPosition) {
        if (flinging) ((HeaderViewListAdapter) adapter).getView(oldPosition, null, this);
    }
}
