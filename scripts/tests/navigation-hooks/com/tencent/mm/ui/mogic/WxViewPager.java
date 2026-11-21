package com.tencent.mm.ui.mogic;
public class WxViewPager extends android.widget.FrameLayout {
    public Object adapter;
    public int current, reads;
    public boolean smooth, failSwitch, ignoreSwitch, failGetter;
    public Runnable duringSwitch;
    public WxViewPager(android.content.Context context) { super(context); }
    public Object getAdapter() { reads++; return adapter; }
    public int getCurrentItem() { if (failGetter) throw new IllegalStateException("getter unavailable"); return current; }
    // setCurrentItem is intentionally absent: the second optional method must still register.
    public void setCurrentItemNotify(int value, boolean animate) {
        if (failSwitch) throw new IllegalStateException("native switch failed");
        smooth=animate;
        if (ignoreSwitch) return;
        current=value; // Real WxViewPager.populate writes mCurItem before settling/selected.
        if (duringSwitch != null) duringSwitch.run();
    }
}
