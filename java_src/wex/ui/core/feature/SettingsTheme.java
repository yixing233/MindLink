package wex.ui.core.feature;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.ArrayDeque;
import dev.mindlink.ui.MindLinkColors;

/** 模块设置共用的表面与状态颜色，不读取或写入业务配置。 */
public final class SettingsTheme {
    private static final Palette LIGHT = new Palette(MindLinkColors.LIGHT);
    private static final Palette DARK = new Palette(MindLinkColors.DARK);
    private static final int[][] ENABLED_STATES = {
            new int[]{-android.R.attr.state_enabled}, new int[0]
    };

    private SettingsTheme() {
    }

    private static final class Palette {
        final int page, card, ink, sub, accent, onAccent, accentSurface, surface, outline, thumb;

        Palette(MindLinkColors.Palette colors) {
            this.page = colors.surface;
            this.card = colors.surfaceContainerLow;
            this.ink = colors.onSurface;
            this.sub = colors.onSurfaceVariant;
            this.accent = colors.primary;
            this.onAccent = colors.onPrimary;
            this.accentSurface = colors.primaryContainer;
            this.surface = colors.surfaceContainerHigh;
            this.outline = colors.outline;
            this.thumb = colors.onPrimary;
        }
    }

    public static boolean isDark(Context context) {
        try {
            return (context.getResources().getConfiguration().uiMode & 0x30) == 0x20;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Palette colors(Context context) {
        return isDark(context) ? DARK : LIGHT;
    }

    public static int page(boolean dark) { return (dark ? DARK : LIGHT).page; }
    public static int page(Context context) { return colors(context).page; }
    public static int card(Context context) { return colors(context).card; }
    public static int ink(Context context) { return colors(context).ink; }
    public static int sub(Context context) { return colors(context).sub; }
    public static int accent(Context context) { return colors(context).accent; }
    public static int onAccent(Context context) { return colors(context).onAccent; }
    public static int accentSurface(Context context) { return colors(context).accentSurface; }
    public static int surface(Context context) { return colors(context).surface; }
    public static int outline(Context context) { return colors(context).outline; }

    public static int switchTrack(Context context, boolean checked) {
        return checked ? accent(context) : outline(context);
    }

    public static int switchThumb(Context context, boolean checked) {
        return checked ? onAccent(context) : colors(context).thumb;
    }

    public static LinearLayout.LayoutParams cardParams(Context context) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(context, MindLinkColors.SECTION_GAP_DP);
        return params;
    }

    /** 只处理返回的一级卡片根节点，不新增父卡，也不改内部功能预览。 */
    public static void applyCard(View view) {
        Context context = view.getContext();
        view.setBackground(round(card(context), dp(context, MindLinkColors.CARD_RADIUS_DP)));
        view.setBackgroundTintList(null);
        view.setElevation(0f);
        view.setTranslationZ(0f);
        view.setStateListAnimator(null);
        view.setPadding(dp(context, 16), dp(context, 14), dp(context, 16), dp(context, 14));
        view.setLayoutParams(cardParams(context));
    }

    public static void primaryButton(TextView view) {
        Context context = view.getContext();
        button(view, onAccent(context), accent(context));
    }

    public static void secondaryButton(TextView view) {
        Context context = view.getContext();
        button(view, accent(context), surface(context));
    }

    public static void option(TextView view, boolean selected) {
        Context context = view.getContext();
        button(view, selected ? onAccent(context) : ink(context),
                selected ? accent(context) : surface(context));
        view.setSelected(selected);
    }

    public static void tab(TextView view, boolean selected) {
        Context context = view.getContext();
        view.setTextColor(states(sub(context), selected ? accent(context) : sub(context)));
        setBackground(view, feedback(context, selected ? accentSurface(context) : Color.TRANSPARENT,
                accent(context), 10));
        view.setSelected(selected);
    }

    public static void secondarySurface(View view) {
        Context context = view.getContext();
        setBackground(view, round(surface(context), dp(context, 12)));
    }

    public static Drawable popupBackground(Context context) {
        return round(card(context), dp(context, MindLinkColors.CARD_RADIUS_DP));
    }

    private static void button(TextView view, int foreground, int background) {
        Context context = view.getContext();
        view.setTextColor(states(sub(context), foreground));
        GradientDrawable fill = new GradientDrawable();
        fill.setColor(states(surface(context), background));
        fill.setCornerRadius(dp(context, 12));
        int ripple = (foreground & 0x00FFFFFF) | 0x22000000;
        setBackground(view, new RippleDrawable(states(Color.TRANSPARENT, ripple), fill,
                round(Color.WHITE, dp(context, 12))));
    }

    private static Drawable feedback(Context context, int background, int color, int radius) {
        int ripple = (color & 0x00FFFFFF) | 0x22000000;
        return new RippleDrawable(states(Color.TRANSPARENT, ripple), round(background, dp(context, radius)),
                round(Color.WHITE, dp(context, radius)));
    }

    private static void setBackground(View view, Drawable drawable) {
        int left = view.getPaddingLeft(), top = view.getPaddingTop();
        int right = view.getPaddingRight(), bottom = view.getPaddingBottom();
        view.setBackgroundTintList(null);
        view.setBackground(drawable);
        view.setPadding(left, top, right, bottom);
    }

    public static void styleSeekBar(SeekBar view) {
        Context context = view.getContext();
        ColorStateList active = states(outline(context), accent(context));
        view.setProgressTintList(active);
        view.setSecondaryProgressTintList(states(outline(context), accentSurface(context)));
        view.setProgressBackgroundTintList(ColorStateList.valueOf(outline(context)));
        view.setThumbTintList(active);
        view.setTickMarkTintList(ColorStateList.valueOf(outline(context)));
    }

    public static void styleInput(EditText view) {
        Context context = view.getContext();
        view.setTextColor(states(sub(context), ink(context)));
        view.setHintTextColor(sub(context));
        view.setHighlightColor((accent(context) & 0x00FFFFFF) | 0x44000000);
        view.setBackgroundTintList(new ColorStateList(new int[][]{
                new int[]{-android.R.attr.state_enabled}, new int[]{android.R.attr.state_focused}, new int[0]
        }, new int[]{outline(context), accent(context), outline(context)}));
        Drawable cursor = tint(view.getTextCursorDrawable(), accent(context));
        if (cursor != null) view.setTextCursorDrawable(cursor);
        Drawable left = tint(view.getTextSelectHandleLeft(), accent(context));
        if (left != null) view.setTextSelectHandleLeft(left);
        Drawable right = tint(view.getTextSelectHandleRight(), accent(context));
        if (right != null) view.setTextSelectHandleRight(right);
        Drawable handle = tint(view.getTextSelectHandle(), accent(context));
        if (handle != null) view.setTextSelectHandle(handle);
    }

    private static Drawable tint(Drawable drawable, int color) {
        if (drawable == null) return null;
        Drawable.ConstantState state = drawable.getConstantState();
        if (state == null) return null;
        Drawable result = state.newDrawable().mutate();
        if (result == drawable) return null;
        result.setTint(color);
        return result;
    }

    /** 显示已有 Builder，不替换点击、授权、关闭或保存监听。 */
    public static AlertDialog showDialog(AlertDialog.Builder builder) {
        AlertDialog dialog = builder.show();
        try {
            Context context = dialog.getContext();
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(popupBackground(context));
                styleDialogContent(dialog.getWindow().getDecorView(), context);
            }
            for (int which : new int[]{AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL}) {
                Button button = dialog.getButton(which);
                if (button != null) {
                    button.setTextColor(states(sub(context), accent(context)));
                    setBackground(button, feedback(context, Color.TRANSPARENT, accent(context), 12));
                }
            }
        } catch (Throwable t) {
            try {
                de.robv.android.xposed.XposedBridge.log("WEX: 设置弹窗主题: " + t.getClass().getSimpleName());
            } catch (Throwable ignored) {
            }
        }
        return dialog;
    }

    private static void styleDialogContent(View root, Context context) {
        int titleId = context.getResources().getIdentifier("alertTitle", "id", "android");
        ArrayDeque<View> queue = new ArrayDeque<View>();
        queue.add(root);
        int visited = 0;
        while (!queue.isEmpty() && visited++ < 256) {
            View view = queue.removeFirst();
            if (view instanceof EditText) styleInput((EditText) view);
            else if (view instanceof SeekBar) styleSeekBar((SeekBar) view);
            else if (view instanceof TextView && (view.getId() == android.R.id.message
                    || (titleId != 0 && view.getId() == titleId))) {
                ((TextView) view).setTextColor(states(sub(context), ink(context)));
            }
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount() && queue.size() < 256; i++) queue.addLast(group.getChildAt(i));
            }
        }
    }

    private static ColorStateList states(int disabled, int enabled) {
        return new ColorStateList(ENABLED_STATES, new int[]{disabled, enabled});
    }

    public static GradientDrawable round(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
