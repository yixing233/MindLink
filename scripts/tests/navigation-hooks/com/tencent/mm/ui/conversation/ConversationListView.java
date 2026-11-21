package com.tencent.mm.ui.conversation;
public class ConversationListView extends android.widget.AbsListView {
    public ConversationListView(android.content.Context context) { super(context); }
    public void clearNativeRows() { children.clear(); }
    public void onScroll(android.widget.AbsListView list, int position, int visible, int total) { }
    public void onScrollStateChanged(android.widget.AbsListView list, int state) { }
}
