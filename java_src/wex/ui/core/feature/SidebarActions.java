package wex.ui.core.feature;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 左侧栏的快捷动作卡。
 *
 * 来源：入口集合与版式参照 WeKit 的
 * `features/items/beautify/home_screen_panel/HomeSidePanelActionCards.kt`（GPL-3.0）。
 * 与 WeKit 的差异：WeKit 通过自身的 API 层跳转，这里直接以宿主类名构造 Intent，
 * 与项目内 `TopBarScanFeature` 的快捷工具跳转方式保持一致。
 */
final class SidebarActions {

    /** 名称 / 图标 / 目标 Activity / extra(int) / extra(String)。 */
    private static final String[][] ACTIONS = {
            { "扫一扫", "qrcode", "com.tencent.mm.plugin.scanner.ui.BaseScanUI", "", "" },
            { "收付款", "wallet", "com.tencent.mm.plugin.offline.ui.WalletOfflineEntranceUI",
                    "", "offline" },
            { "添加朋友", "user-plus",
                    "com.tencent.mm.plugin.subapp.ui.pluginapp.AddMoreFriendsUI", "", "" },
            { "二维码名片", "id-badge",
                    "com.tencent.mm.plugin.setting.ui.setting.SelfQRCodeUI", "", "" },
            { "发起群聊", "users-group", "com.tencent.mm.ui.mvvm.MvvmContactListUI", "7", "" },
    };

    private SidebarActions() {
    }

    static View build(Activity activity) {
        if (activity == null) {
            return null;
        }
        float density = activity.getResources().getDisplayMetrics().density;
        boolean dark = (activity.getResources().getConfiguration().uiMode & 0x30) == 0x20;

        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(density, 14);
        card.setPadding(pad, dp(density, 12), pad, dp(density, 12));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(dark ? 0xFF1E2220 : 0xFFFFFFFF);
        bg.setCornerRadius(dp(density, 18));
        card.setBackground(bg);

        TextView title = new TextView(activity);
        title.setText("快捷动作");
        title.setTextSize(14f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(dark ? 0xFFE8ECE9 : 0xFF1A1A1A);
        card.addView(title);

        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = dp(density, 10);
        card.addView(row, rowParams);

        for (final String[] action : ACTIONS) {
            row.addView(item(activity, action, dark, density),
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        return card;
    }

    private static View item(final Activity activity, final String[] action,
                             boolean dark, float density) {
        LinearLayout cell = new LinearLayout(activity);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        cell.setClickable(true);
        cell.setFocusable(true);
        cell.setContentDescription(action[0]);

        ImageView icon = new ImageView(activity);
        int size = dp(density, 24);
        icon.setImageBitmap(LucideIcons.bitmap(action[1], size,
                dark ? 0xFFE6E6E6 : 0xFF1A1A1A));
        icon.setScaleType(ImageView.ScaleType.CENTER);
        cell.addView(icon, new LinearLayout.LayoutParams(size, size));

        TextView label = new TextView(activity);
        label.setText(action[0]);
        label.setTextSize(11f);
        label.setGravity(Gravity.CENTER);
        label.setTextColor(dark ? 0xFFB4BDB7 : 0xFF6B7280);
        label.setSingleLine(true);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.topMargin = dp(density, 5);
        cell.addView(label, labelParams);

        cell.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                launch(activity, action);
            }
        });
        return cell;
    }

    private static void launch(Activity activity, String[] action) {
        try {
            Intent intent = new Intent();
            intent.setClassName(activity, action[2]);
            if (!action[3].isEmpty()) {
                intent.putExtra("scene", Integer.parseInt(action[3]));
            }
            if (!action[4].isEmpty()) {
                intent.putExtra("key_from_scene", action[4]);
            }
            activity.startActivity(intent);
        } catch (Throwable error) {
            try {
                XposedLog.log("侧栏动作失败 " + action[0] + " " + error.getClass().getSimpleName());
            } catch (Throwable ignored) {
            }
        }
    }

    private static int dp(float density, int value) {
        return Math.round(value * density);
    }

    /** 避免直接依赖 XposedBridge，便于纯 JVM 回归测试。 */
    private static final class XposedLog {
        static void log(String message) {
            try {
                dev.mindlink.xposed.XposedBridge.log("WEX: 首页侧栏: " + message);
            } catch (Throwable ignored) {
            }
        }
    }
}
