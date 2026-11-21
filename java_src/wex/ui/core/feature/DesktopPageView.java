package wex.ui.core.feature;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Material 3 landing page for the module launcher icon.
 *
 * The module's real settings live in the WeChat-side panel (top-bar avatar or the drawer's
 * "Mdl 设置"), so this page only explains where to find them. It replaces the isolated Compose
 * desktop payload that existed for this page alone, which removes ~24 MB from the APK.
 */
public final class DesktopPageView {
    private DesktopPageView() { }

    // Material 3 baseline tonal tokens.
    private static final int LIGHT_SURFACE = 0xFFFEF7FF;
    private static final int LIGHT_CONTAINER = 0xFFF3EDF7;
    private static final int LIGHT_ON = 0xFF1D1B20;
    private static final int LIGHT_VARIANT = 0xFF49454F;
    private static final int LIGHT_PRIMARY = 0xFF6750A4;
    private static final int LIGHT_ON_PRIMARY = 0xFFFFFFFF;
    private static final int DARK_SURFACE = 0xFF141218;
    private static final int DARK_CONTAINER = 0xFF211F26;
    private static final int DARK_ON = 0xFFE6E0E9;
    private static final int DARK_VARIANT = 0xFFCAC4D0;
    private static final int DARK_PRIMARY = 0xFFD0BCFF;
    private static final int DARK_ON_PRIMARY = 0xFF381E72;

    private static boolean dark(Context context) {
        Configuration config = context.getResources().getConfiguration();
        return (config.uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    private static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public static View build(final Activity activity) {
        if (activity == null) return null;
        boolean dark = dark(activity);
        int surface = dark ? DARK_SURFACE : LIGHT_SURFACE;
        int container = dark ? DARK_CONTAINER : LIGHT_CONTAINER;
        int on = dark ? DARK_ON : LIGHT_ON;
        int variant = dark ? DARK_VARIANT : LIGHT_VARIANT;
        int primary = dark ? DARK_PRIMARY : LIGHT_PRIMARY;
        int onPrimary = dark ? DARK_ON_PRIMARY : LIGHT_ON_PRIMARY;

        ScrollView scroll = new ScrollView(activity);
        scroll.setBackgroundColor(surface);
        scroll.setFillViewport(true);

        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(activity, 24);
        column.setPadding(pad, pad, pad, pad);
        scroll.addView(column, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(activity);
        title.setText("MindLink");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(on);
        column.addView(title);

        TextView subtitle = new TextView(activity);
        subtitle.setText("微信内的玻璃与侧栏美化模块");
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        subtitle.setTextColor(variant);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = dp(activity, 6);
        subtitleParams.bottomMargin = dp(activity, 20);
        column.addView(subtitle, subtitleParams);

        column.addView(card(activity, container, on, variant, "设置在哪里",
                "模块所有开关都在微信内：首页顶栏左上角头像，或侧栏里的「Mdl 设置」。本页面只作说明，不提供开关。"),
                fullWidth(activity, 12));

        TextView open = new TextView(activity);
        open.setText("打开微信");
        open.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        open.setTypeface(Typeface.DEFAULT_BOLD);
        open.setTextColor(onPrimary);
        open.setGravity(Gravity.CENTER);
        int vpad = dp(activity, 14);
        open.setPadding(vpad, vpad, vpad, vpad);
        GradientDrawable pill = new GradientDrawable();
        pill.setColor(primary);
        pill.setCornerRadius(dp(activity, 20));
        open.setBackground(pill);
        open.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                try {
                    android.content.Intent intent =
                            view.getContext().getPackageManager().getLaunchIntentForPackage("com.tencent.mm");
                    if (intent != null) view.getContext().startActivity(intent);
                } catch (Throwable ignored) { }
            }
        });
        column.addView(open, fullWidth(activity, 16));

        TextView note = new TextView(activity);
        note.setText("修改设置后部分效果需要重启微信才会生效。");
        note.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        note.setTextColor(variant);
        column.addView(note, fullWidth(activity, 16));

        return scroll;
    }

    private static LinearLayout.LayoutParams fullWidth(Context context, int topDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(context, topDp);
        return params;
    }

    private static LinearLayout card(Context context, int background, int on, int variant,
                                     String heading, String body) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(context, 20);
        card.setPadding(pad, pad, pad, pad);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(background);
        shape.setCornerRadius(dp(context, 24));
        card.setBackground(shape);

        TextView title = new TextView(context);
        title.setText(heading);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(on);
        card.addView(title);

        TextView text = new TextView(context);
        text.setText(body);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        text.setTextColor(variant);
        text.setLineSpacing(dp(context, 3), 1f);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(context, 8);
        card.addView(text, params);
        return card;
    }
}
