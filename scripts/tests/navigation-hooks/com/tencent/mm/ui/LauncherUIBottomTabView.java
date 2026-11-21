package com.tencent.mm.ui;
public class LauncherUIBottomTabView extends android.widget.FrameLayout {
    public int selected;
    public LauncherUIBottomTabView(android.content.Context context) {
        super(context);
        for (int i=0;i<4;i++) {
            final int index=i;
            android.widget.FrameLayout tab=new android.widget.FrameLayout(context);
            tab.setTag(i); tab.setOnClickListener(view->selected=index);
            tab.addView(new TabIconView(context)); addView(tab);
        }
        de.robv.android.xposed.XposedBridge.constructed(this);
    }
    public final int[] unread = new int[4];
    public boolean friendPoint, settingsPoint, failFriendUnread, failSettingsPoint;
    public int badgeReads;
    public int getMainTabUnread() { badgeReads++; return unread[0]; }
    public int getContactTabUnread() { badgeReads++; return unread[1]; }
    public int getFriendTabUnread() { badgeReads++; if (failFriendUnread) throw new IllegalStateException(); return unread[2]; }
    public int getSettingsTabUnread() { badgeReads++; return unread[3]; }
    public boolean getShowFriendPoint() { return friendPoint; }
    public boolean getSettingsPoint() { if (failSettingsPoint) throw new IllegalStateException(); return settingsPoint; }
    public void updateMainUnread(int value) { unread[0] = value; }
    public void setTo(int position) { selected=position; }
}
