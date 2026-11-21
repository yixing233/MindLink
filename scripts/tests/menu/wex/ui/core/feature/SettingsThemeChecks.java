package wex.ui.core.feature;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

/** 共享主题的行为/对比度检查，不代替 Android 测量、绘制或宿主触摸验证。 */
public final class SettingsThemeChecks {
    private static int count;
    private static final int[] ENABLED = {android.R.attr.state_enabled};
    private static final int[] FOCUSED = {android.R.attr.state_enabled, android.R.attr.state_focused};
    private static final int[] DISABLED = {};

    private static void check(boolean value, String message) {
        count++;
        if (!value) throw new AssertionError(message);
    }

    private static Context context(boolean dark) {
        Context context = new Context();
        context.getResources().getConfiguration().uiMode = dark ? 0x20 : 0x10;
        return context;
    }

    private static int color(ColorStateList colors, int[] state) {
        return colors.getColorForState(state, 0xFFFF00FF);
    }

    private static GradientDrawable fill(View view) {
        Drawable drawable = view.getBackground();
        if (drawable instanceof RippleDrawable) drawable = ((RippleDrawable) drawable).getDrawable(0);
        return (GradientDrawable) drawable;
    }

    private static double channel(int value) {
        double unit = value / 255.0;
        return unit <= 0.04045 ? unit / 12.92 : Math.pow((unit + 0.055) / 1.055, 2.4);
    }

    private static double luminance(int color) {
        return 0.2126 * channel((color >> 16) & 255)
                + 0.7152 * channel((color >> 8) & 255) + 0.0722 * channel(color & 255);
    }

    private static void contrast(int foreground, int background, double minimum, String label) {
        double a = luminance(foreground), b = luminance(background);
        double ratio = (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
        check(ratio >= minimum, label + " contrast " + ratio + " < " + minimum);
    }

    private static void palette(Context context, boolean dark) {
        check(SettingsTheme.isDark(context) == dark, "theme mode");
        check(SettingsTheme.page(context) == SettingsTheme.page(dark), "window matches page");
        check(SettingsTheme.card(context) != SettingsTheme.page(context), "page and first-level card hierarchy");
        check(SettingsTheme.surface(context) != SettingsTheme.card(context), "secondary surface distinct");
        int accent = SettingsTheme.accent(context);
        check(((accent >> 8) & 255) > ((accent >> 16) & 255)
                && ((accent >> 8) & 255) > (accent & 255), "one green accent family");
        contrast(SettingsTheme.ink(context), SettingsTheme.card(context), 4.5, "body");
        contrast(SettingsTheme.sub(context), SettingsTheme.card(context), 4.5, "description");
        contrast(accent, SettingsTheme.card(context), 4.5, "accent text");
        contrast(accent, SettingsTheme.surface(context), 4.5, "secondary action");
        contrast(accent, SettingsTheme.accentSurface(context), 4.5, "tab");
        contrast(SettingsTheme.onAccent(context), accent, 4.5, "filled action");
        contrast(SettingsTheme.switchThumb(context, true), SettingsTheme.switchTrack(context, true), 3.0, "checked switch");
        contrast(SettingsTheme.switchThumb(context, false), SettingsTheme.switchTrack(context, false), 3.0, "unchecked switch");
        check(SettingsTheme.switchTrack(context, true) == accent, "switch and buttons use same accent");
        check(SettingsTheme.switchTrack(context, false) == SettingsTheme.outline(context), "switch off is neutral");
    }

    private static void cards(Context context) {
        LinearLayout host = new LinearLayout(context);
        ViewGroup card = new ViewGroup(context);
        View preview = new View(context);
        Drawable userBackground = new ColorDrawable(0xFF0000FF);
        preview.setBackground(userBackground);
        card.addView(preview);
        host.addView(card);
        card.setBackgroundTintList(ColorStateList.valueOf(0xFF1976D2));
        card.setElevation(3f);
        card.setTranslationZ(4f);
        card.setStateListAnimator(new android.animation.StateListAnimator());
        SettingsTheme.applyCard(card);
        check(host.getChildCount() == 1 && card.getParent() == host, "no extra wrapper");
        check(card.getChildCount() == 1 && preview.getBackground() == userBackground, "user preview untouched");
        check(fill(card).getColor().getDefaultColor() == SettingsTheme.card(context), "uniform root surface");
        check(fill(card).getCornerRadius() == dev.mindlink.ui.MindLinkColors.CARD_RADIUS_DP * 3f, "shared Material3 root corners");
        check(card.getElevation() == 0f && card.getTranslationZ() == 0f && card.getStateListAnimator() == null,
                "no inconsistent shadow or host state animator");
        check(card.getBackgroundTintList() == null, "host tint does not override card");
        check(card.getPaddingLeft() == 48 && card.getPaddingTop() == 42
                && card.getPaddingRight() == 48 && card.getPaddingBottom() == 42, "16/14dp card insets");
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) card.getLayoutParams();
        check(params.width == -1 && params.height == -2 && params.bottomMargin == 36, "12dp spacing and growing content");
        SettingsTheme.applyCard(card);
        check(host.getChildCount() == 1 && card.getChildCount() == 1, "card normalization idempotent");
        SettingsTheme.secondarySurface(preview);
        check(fill(preview).getColor().getDefaultColor() == SettingsTheme.surface(context), "explicit secondary surface");
    }

    private static void controls(Context context) {
        final int[] clicks = {0};
        TextView button = new TextView(context);
        ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(321, -2);
        button.setLayoutParams(params);
        button.setPadding(10, 20, 30, 40);
        button.setText("原操作");
        button.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { clicks[0]++; }
        });
        SettingsTheme.primaryButton(button);
        check(color(button.getTextColors(), ENABLED) == SettingsTheme.onAccent(context), "primary text contrast color");
        check(color(fill(button).getColor(), ENABLED) == SettingsTheme.accent(context), "primary fill");
        check(color(button.getTextColors(), DISABLED) == SettingsTheme.sub(context)
                && color(fill(button).getColor(), DISABLED) == SettingsTheme.surface(context), "disabled neutral state");
        check(button.getLayoutParams() == params && button.getPaddingBottom() == 40, "button keeps layout and padding");
        check(clicks[0] == 0 && "原操作".contentEquals(button.getText()), "styling does not perform business or alter labels");
        Drawable first = button.getBackground();
        TextView second = new TextView(context);
        SettingsTheme.primaryButton(second);
        check(first != second.getBackground(), "independent press drawables");
        button.performClick();
        check(clicks[0] == 1, "listener preserved");
        SettingsTheme.secondaryButton(button);
        check(color(button.getTextColors(), ENABLED) == SettingsTheme.accent(context)
                && color(fill(button).getColor(), ENABLED) == SettingsTheme.surface(context), "secondary action");
        SettingsTheme.option(button, true);
        check(button.isSelected() && color(fill(button).getColor(), ENABLED) == SettingsTheme.accent(context), "selected option");
        SettingsTheme.option(button, false);
        check(!button.isSelected() && color(button.getTextColors(), ENABLED) == SettingsTheme.ink(context)
                && color(fill(button).getColor(), ENABLED) == SettingsTheme.surface(context), "unselected neutral option");
        SettingsTheme.option(button, true);
        check(color(button.getTextColors(), ENABLED) == SettingsTheme.onAccent(context), "refresh keeps palette");
        SettingsTheme.tab(button, true);
        check(button.isSelected() && color(fill(button).getColor(), ENABLED) == SettingsTheme.accentSurface(context), "selected tab");
        SettingsTheme.tab(button, false);
        check(!button.isSelected() && color(fill(button).getColor(), ENABLED) == 0, "inactive tab transparent");
        SeekBar bar = new SeekBar(context);
        bar.setProgress(137);
        SettingsTheme.styleSeekBar(bar);
        check(bar.getProgress() == 137, "slider value untouched");
        check(color(bar.getProgressTintList(), ENABLED) == SettingsTheme.accent(context)
                && color(bar.getThumbTintList(), ENABLED) == SettingsTheme.accent(context), "slider explicit accent");
        check(color(bar.getProgressTintList(), DISABLED) == SettingsTheme.outline(context), "disabled slider");
        check(bar.getProgressBackgroundTintList().getDefaultColor() == SettingsTheme.outline(context)
                && bar.getTickMarkTintList().getDefaultColor() == SettingsTheme.outline(context), "neutral remaining track");
    }

    private static void dialogs(Context context) {
        EditText input = new EditText(context);
        Drawable source = new ColorDrawable(0xFF1976D2);
        input.setTextCursorDrawable(source);
        input.setTextSelectHandleLeft(source);
        input.setTextSelectHandleRight(source);
        input.setTextSelectHandle(source);
        input.setText("保持输入");
        SettingsTheme.styleInput(input);
        check("保持输入".contentEquals(input.getText()), "input content intact");
        check(color(input.getBackgroundTintList(), FOCUSED) == SettingsTheme.accent(context), "input focus accent");
        check(color(input.getBackgroundTintList(), ENABLED) == SettingsTheme.outline(context), "input normal neutral");
        check(input.getTextCursorDrawable() != source && source.getTint() == null, "cursor tinted on independent drawable");
        check(input.getTextCursorDrawable().getTint() == SettingsTheme.accent(context)
                && input.getTextSelectHandle().getTint() == SettingsTheme.accent(context), "cursor and selection use same accent");
        final int[] calls = {0};
        TextView preview = new TextView(context);
        preview.setTextColor(0xFF1976D2);
        Drawable previewBackground = new ColorDrawable(0xFFE8F0FE);
        preview.setBackground(previewBackground);
        ViewGroup content = new ViewGroup(context);
        content.addView(input);
        content.addView(preview);
        AlertDialog dialog = SettingsTheme.showDialog(new AlertDialog.Builder(context)
                .setTitle("设置确认")
                .setView(content)
                .setPositiveButton("确认", new Runnable() {
                    @Override public void run() { calls[0]++; }
                }));
        check(dialog.shown && calls[0] == 0, "dialog styling does not confirm action");
        check(preview.getTextColors().getDefaultColor() == 0xFF1976D2 && preview.getBackground() == previewBackground,
                "custom preview colors not overwritten by dialog styling");
        TextView title = (TextView) ((ViewGroup) dialog.getWindow().getDecorView()).getChildAt(0);
        check(color(title.getTextColors(), ENABLED) == SettingsTheme.ink(context), "only identified native dialog title themed");
        check(((GradientDrawable) dialog.getWindow().getBackgroundDrawable()).getColor().getDefaultColor()
                == SettingsTheme.card(context), "dialog surface");
        check(color(dialog.getButton(AlertDialog.BUTTON_POSITIVE).getTextColors(), ENABLED)
                == SettingsTheme.accent(context), "dialog action accent");
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        check(calls[0] == 1, "dialog callback preserved");
    }

    public static void main(String[] args) {
        for (boolean dark : new boolean[]{false, true}) {
            Context context = context(dark);
            palette(context, dark);
            cards(context);
            controls(context);
            dialogs(context);
        }
        check(!SettingsTheme.isDark(null), "unavailable configuration fallback");
        System.out.println("PASS: settings theme " + count + " checks: light/dark contrast, uniform cards, green states, independent drawables, explicit slider/input tints and unchanged mock delegates");
    }
}
