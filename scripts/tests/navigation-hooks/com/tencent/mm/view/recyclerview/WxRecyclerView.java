package com.tencent.mm.view.recyclerview;

/** Layout/scroll-only double: never reads or exposes contact row data. */
public final class WxRecyclerView extends android.widget.FrameLayout {
    private int rows, rowHeight = 48, scroll;
    public WxRecyclerView(android.content.Context context) { super(context); setId(0x7f090267); }
    public void rowsForTest(int count) { rows = count; scroll = 0; }
    public void bottomForTest() { scroll = Math.max(0, rows * rowHeight + getPaddingTop() + getPaddingBottom() - getHeight()); }
    public int lastBottomForTest() { return getTop() + getPaddingTop() + rows * rowHeight - scroll; }
    public int maxScrollForTest() { return Math.max(0, rows * rowHeight + getPaddingTop() + getPaddingBottom() - getHeight()); }
}
