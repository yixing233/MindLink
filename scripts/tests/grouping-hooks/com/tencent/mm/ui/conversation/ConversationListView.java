package com.tencent.mm.ui.conversation;

/** Exact production findList name gate. Native scrolling/header counters live in ListView. */
public final class ConversationListView extends android.widget.ListView {
    public ConversationListView(android.content.Context context) { super(context); }
    public boolean firstLayout, headerOpen;
    public int footerChecks;
    public android.view.View actionBar;
    public final java.util.List<String> events = new java.util.ArrayList<>();
    public void checkEmptyFooterHost() { footerChecks++; events.add("footer"); }
    public void resetActionBarHost() { }
    @Override public void setSelection(int position) { events.add("selection"); super.setSelection(position); }
    // Narrow native branch from saved ConversationListView.java:853-855, not an Android layout engine.
    public void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (!firstLayout && changed && !headerOpen) setSelection(1);
        firstLayout = false;
    }
}
