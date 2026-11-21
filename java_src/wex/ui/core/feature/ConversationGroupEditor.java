package wex.ui.core.feature;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Local group editor. Closing or cancelling never writes the draft. */
final class ConversationGroupEditor {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private ConversationGroupEditor() { }

    static void edit(Activity activity, ConversationGroupStore.Group previous) {
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(activity, 20), dp(activity, 12), dp(activity, 20), 0);
        EditText name = input(activity, "分组名称", previous == null ? "" : previous.name);
        body.addView(name);
        boolean all = previous != null && previous.isAll();
        Spinner type = new Spinner(activity);
        String[] modes = {"手动选择成员", "未读", "群聊", "好友", "公众号", "只读 SQL 规则"};
        type.setAdapter(new ArrayAdapter<String>(activity, android.R.layout.simple_spinner_dropdown_item, modes));
        type.setSelection(previous == null ? 0 : previous.type.ordinal());
        body.addView(type); type.setVisibility(all ? View.GONE : View.VISIBLE);
        EditText fields = input(activity, "查询字段（默认为 r.username）", previous == null ? "r.username" : previous.selectFields);
        EditText where = input(activity, "WHERE 条件", previous == null ? "" : previous.whereClause);
        where.setSingleLine(false);
        body.addView(fields); body.addView(where);
        List<String> selected = new ArrayList<>(previous == null ? Collections.emptyList() : previous.members);
        TextView members = new TextView(activity);
        SettingsTheme.secondaryButton(members);
        members.setPadding(dp(activity, 12), dp(activity, 12), dp(activity, 12), dp(activity, 12));
        Runnable memberLabel = () -> members.setText("选择会话 · " + selected.size() + " 项");
        memberLabel.run();
        members.setOnClickListener(view -> chooseMembers(activity, selected, memberLabel));
        body.addView(members);
        Runnable updateMode = () -> {
            fields.setVisibility(!all && type.getSelectedItemPosition() == 5 ? View.VISIBLE : View.GONE);
            where.setVisibility(fields.getVisibility());
            members.setVisibility(!all && type.getSelectedItemPosition() == 0 ? View.VISIBLE : View.GONE);
        };
        type.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) { updateMode.run(); }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        updateMode.run();
        TextView failure = new TextView(activity);
        failure.setTextColor(SettingsTheme.sub(activity)); body.addView(failure);
        AlertDialog dialog = SettingsTheme.showDialog(new AlertDialog.Builder(activity)
                .setTitle(previous == null ? "添加分组" : "编辑分组")
                .setView(body).setNegativeButton("取消", null).setPositiveButton("保存", null)
                .setNeutralButton(previous != null && !all ? "删除" : "", null));
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String title = name.getText().toString().trim();
            ConversationGroupStore.Type mode = all ? ConversationGroupStore.Type.MANUAL
                    : ConversationGroupStore.Type.values()[type.getSelectedItemPosition()];
            if (!all && (mode == ConversationGroupStore.Type.MANUAL || mode == ConversationGroupStore.Type.SQL) && title.isEmpty()) {
                name.setError("请输入分组名称"); return;
            }
            String id = previous == null ? ConversationGroupStore.PREFIX + UUID.randomUUID() : previous.id;
            ConversationGroupStore.Group updated = new ConversationGroupStore.Group(id, title, mode, selected,
                    fields.getText().toString(), where.getText().toString());
            List<ConversationGroupStore.Group> next = new ArrayList<>(ConversationGroupingFeature.groups());
            int position = -1;
            for (int i = 0; i < next.size(); i++) if (next.get(i).id.equals(id)) position = i;
            if (position < 0) next.add(updated); else next.set(position, updated);
            if (ConversationGroupingFeature.saveGroups(next)) dialog.dismiss();
            else failure.setText("保存失败，未更改正在使用的分组");
        });
        if (previous != null && !all) dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(view -> {
            SettingsTheme.showDialog(new AlertDialog.Builder(activity).setTitle("删除此分组？")
                    .setMessage("只删除分类配置，不删除联系人或聊天记录。")
                    .setNegativeButton("取消", null).setPositiveButton("删除", (confirm, which) -> {
                        List<ConversationGroupStore.Group> next = new ArrayList<>(ConversationGroupingFeature.groups());
                        next.removeIf(group -> group.id.equals(previous.id));
                        if (ConversationGroupingFeature.saveGroups(next)) dialog.dismiss();
                        else failure.setText("删除未保存，请重试");
                    }));
        });
        else dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setVisibility(View.GONE);
    }

    private static void chooseMembers(Activity activity, List<String> selected, Runnable after) {
        WeakReference<Activity> owner = new WeakReference<>(activity);
        HostTargetResolver.background(() -> {
            try {
                List<Map<String, Object>> rows = HostDatabaseBridge.query(
                        "SELECT username, nickname, conRemark FROM rcontact ORDER BY nickname", null);
                MAIN.post(() -> {
                    Activity current = owner.get();
                    if (current == null || current.isDestroyed() || current.isFinishing()) return;
                    List<String> ids = new ArrayList<>(), labels = new ArrayList<>();
                    for (Map<String, Object> row : rows) {
                        Object value = row.get("username");
                        if (!(value instanceof String)) continue;
                        String id = (String) value;
                        Object remark = row.get("conRemark"), nickname = row.get("nickname");
                        String label = remark instanceof String && !((String) remark).isEmpty() ? (String) remark
                                : nickname instanceof String && !((String) nickname).isEmpty() ? (String) nickname : id;
                        ids.add(id); labels.add(label);
                    }
                    List<String> draft = new ArrayList<>(selected);
                    boolean[] checked = new boolean[ids.size()];
                    for (int i = 0; i < checked.length; i++) checked[i] = draft.contains(ids.get(i));
                    SettingsTheme.showDialog(new AlertDialog.Builder(current).setTitle("选择分组成员")
                            .setMultiChoiceItems(labels.toArray(new String[0]), checked, (dialog, index, enabled) -> {
                                if (enabled && !draft.contains(ids.get(index))) draft.add(ids.get(index));
                                else if (!enabled) draft.remove(ids.get(index));
                            }).setNegativeButton("取消", null).setPositiveButton("确定", (dialog, which) -> {
                                selected.clear(); selected.addAll(draft); after.run();
                            }));
                });
            } catch (Exception error) {
                MAIN.post(() -> {
                    Activity current = owner.get();
                    if (current != null && !current.isDestroyed()) ImagePicker.INSTANCE.toast(current, "联系人读取失败，请等待微信数据库就绪");
                });
            }
        });
    }

    private static EditText input(Activity activity, String hint, String value) {
        EditText input = new EditText(activity);
        input.setHint(hint); input.setText(value); input.setSingleLine(true); SettingsTheme.styleInput(input);
        return input;
    }
    private static int dp(Activity activity, int value) { return Math.round(activity.getResources().getDisplayMetrics().density * value); }
}
