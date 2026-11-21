package wex.ui.core.feature;

import android.app.Activity;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import wex.ui.core.WexStore;

/** Scrollable group tabs. A long press opens the group editor rather than modifying native rows. */
final class ConversationGroupBar extends HorizontalScrollView {
    private final LinearLayout tabs;
    private final List<String> tabIds = new ArrayList<>();
    private boolean equalWidth;

    ConversationGroupBar(Context context) {
        super(context);
        setHorizontalScrollBarEnabled(false);
        tabs = new LinearLayout(context);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setGravity(Gravity.CENTER_VERTICAL);
        addView(tabs, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        setBackgroundColor(SettingsTheme.isDark(context) ? 0xFF191919 : 0xFFF7F7F7);
        rebuild();
    }

    void rebuild() {
        List<ConversationGroupStore.Group> groups = ConversationGroupingFeature.groups();
        boolean equal = WexStore.INSTANCE.getBool(ConversationGroupingFeature.KEY_EQUAL, false);
        boolean sameTabs = tabIds.size() == groups.size() && tabs.getChildCount() == groups.size() + 1;
        for (int i = 0; sameTabs && i < groups.size(); i++) sameTabs = tabIds.get(i).equals(groups.get(i).id);
        setFillViewport(equal);
        if (!sameTabs) { tabs.removeAllViews(); tabIds.clear(); }
        Activity activity = ChatToolbarLifecycle.findActivity(getContext());
        String activeId = ConversationGroupingFeature.activeGroupId();
        for (int i = 0; i < groups.size(); i++) {
            ConversationGroupStore.Group group = groups.get(i);
            TextView label;
            if (sameTabs) label = (TextView) tabs.getChildAt(i);
            else {
                label = new TextView(getContext());
                label.setTextSize(14); label.setGravity(Gravity.CENTER);
                label.setMinHeight(dp(48)); label.setPadding(dp(16), dp(10), dp(16), dp(10));
                label.setOnClickListener(view -> ConversationGroupingFeature.setActive(group.id));
                tabs.addView(label, tabParams(equal));
                tabIds.add(group.id);
            }
            // Selection/host notifications keep the measured children and scroll position.
            if (!group.label().contentEquals(label.getText())) label.setText(group.label());
            boolean selected = group.id.equals(activeId);
            label.setTextColor(selected ? SettingsTheme.accent(getContext()) : SettingsTheme.ink(getContext()));
            label.setSelected(selected);
            label.setOnLongClickListener(view -> {
                if (activity != null) ConversationGroupEditor.edit(activity, group);
                return true;
            });
            if (sameTabs && equal != equalWidth) label.setLayoutParams(tabParams(equal));
        }
        if (!sameTabs) {
            TextView add = new TextView(getContext());
            add.setText("＋"); add.setTextSize(22); add.setGravity(Gravity.CENTER);
            add.setTextColor(SettingsTheme.accent(getContext())); add.setContentDescription("添加会话分组");
            add.setOnClickListener(view -> { if (activity != null) ConversationGroupEditor.edit(activity, null); });
            tabs.addView(add, new LinearLayout.LayoutParams(dp(48), dp(48)));
        } else ((TextView) tabs.getChildAt(groups.size())).setTextColor(SettingsTheme.accent(getContext()));
        equalWidth = equal;
    }

    private LinearLayout.LayoutParams tabParams(boolean equal) {
        return new LinearLayout.LayoutParams(equal ? 0 : LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT, equal ? 1f : 0f);
    }

    private int dp(int value) { return Math.round(getResources().getDisplayMetrics().density * value); }
}
