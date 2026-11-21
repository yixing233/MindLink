package com.tencent.mm.ui;
public class MainTabUI {
    public Object currentFragment;
    public Object g() { return currentFragment; }
    public static class TabsAdapter {
        public final MainTabUI this$0 = new MainTabUI();
        public Object mViewPager;
        public int count = 4;
        public Runnable duringSelected;
        public int getCount() { return count; }
        public void onPageScrolled(int position, float offset, int pixels) { }
        public void onPageSelected(int position) { if (duringSelected != null) duringSelected.run(); }
        public void onPageScrollStateChanged(int state) { }
    }
}
