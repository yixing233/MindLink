package android.widget;

import android.view.View;
import android.view.ViewGroup;
import de.robv.android.xposed.XposedBridge;
import java.util.ArrayList;
import java.util.List;

public final class HeaderViewListAdapter implements ListAdapter {
    private final Object adapter;
    public int headers = 1;
    public final List<View> footers = new ArrayList<>();
    public HeaderViewListAdapter(Object adapter) { this.adapter = adapter; }
    public Object getWrappedAdapter() { return adapter; }
    public int getCount() {
        return headers + (Integer) invoke("getCount", new Class<?>[0]) + footers.size();
    }
    // Match the framework branch that failed before the underlying adapter's getView hook.
    public View getView(int position, View recycled, ViewGroup parent) {
        if (position < headers) return new View(parent.getContext());
        int adjusted = position - headers;
        int count = (Integer) invoke("getCount", new Class<?>[0]);
        if (adjusted < count) return (View) invoke("getView",
                new Class<?>[]{int.class, View.class, ViewGroup.class}, adjusted, recycled, parent);
        return footers.get(adjusted - count);
    }
    private Object invoke(String name, Class<?>[] types, Object... args) {
        try { return XposedBridge.invoke(adapter, name, types, args); }
        catch (RuntimeException error) { throw error; }
        catch (Throwable error) { throw new AssertionError("wrapper " + name, error); }
    }
}
