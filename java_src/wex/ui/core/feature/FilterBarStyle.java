package wex.ui.core.feature;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/**
 * Flat conversation filters. Labels, insertion and click actions remain owned by Smali.
 *
 * 来源：四档分类（全部/好友/群聊/公众号）的交互与扁平标签样式参照 WeKit 的
 * `features/items/chat/ConversationGrouping.kt`（GPL-3.0）。
 */
public final class FilterBarStyle {
    private static final String BAR_TAG = "wex_filter_bar";
    private static final int LIGHT_SURFACE = 0xFFF7F7F7;
    private static final int DARK_SURFACE = 0xFF191919;
    // Tabler's neutral text colors; a darker WeChat green keeps small light-mode text legible.
    private static final int LIGHT_TEXT = 0xFF1A1A1A;
    private static final int DARK_TEXT = 0xFFE6E6E6;
    private static final int LIGHT_GREEN = 0xFF07813F;
    private static final int DARK_GREEN = 0xFF07C160;
    private static final int LIGHT_PRESS = 0x14000000;
    private static final int DARK_PRESS = 0x1FFFFFFF;
    private static final int[][] TEXT_STATES = {
            new int[] { android.R.attr.state_selected }, new int[0]
    };
    private static final ColorStateList LIGHT_TEXT_COLORS = new ColorStateList(
            TEXT_STATES, new int[] { LIGHT_GREEN, LIGHT_TEXT });
    private static final ColorStateList DARK_TEXT_COLORS = new ColorStateList(
            TEXT_STATES, new int[] { DARK_GREEN, DARK_TEXT });

    private FilterBarStyle() {
    }

    /** Empty four-slot bar; callers add the four createButton results without fixed heights. */
    public static LinearLayout createBar(Context context) {
        LinearLayout bar = new LinearLayout(context);
        bar.setTag(BAR_TAG);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setWeightSum(4f);
        bar.setBaselineAligned(false);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setShowDividers(LinearLayout.SHOW_DIVIDER_NONE);
        bar.setPadding(0, 0, 0, 0);
        bar.setMinimumHeight(dp(context, 48));
        bar.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        flatten(bar);
        bar.setBackgroundColor(fallbackSurface(context));
        return bar;
    }

    /** Equal-width, multiline text target. No business listeners are installed here. */
    public static TextView createButton(Context context, String text) {
        TextView button = new TextView(context);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        button.setGravity(Gravity.CENTER);
        button.setAllCaps(false);
        button.setSingleLine(false);
        button.setMaxLines(Integer.MAX_VALUE);
        button.setHorizontallyScrolling(false);
        button.setEllipsize(null);
        button.setIncludeFontPadding(true);
        button.setMinHeight(dp(context, 48));
        button.setMinimumHeight(dp(context, 48));
        button.setMinWidth(dp(context, 48));
        button.setMinimumWidth(dp(context, 48));
        button.setClickable(true);
        button.setFocusable(true);
        button.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        button.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        flatten(button);
        applyPalette(button, isDark(fallbackSurface(context)));
        // Leave room for the marker without constraining large-font line height or line count.
        button.setPadding(dp(context, 4), dp(context, 10), dp(context, 4), dp(context, 10));
        button.setText(text);
        return button;
    }

    /** Selection changes only drawable/text state, never text size, padding or layout params. */
    public static void refresh(List<?> buttons, int selected) {
        if (buttons == null) {
            return;
        }
        for (int i = 0; i < buttons.size(); i++) {
            Object item = buttons.get(i);
            if (!(item instanceof TextView)) {
                continue;
            }
            TextView button = (TextView) item;
            applyPalette(button, isDark(buttonSurface(button)));
            boolean active = i == selected;
            if (button.isSelected() != active) {
                button.setSelected(active);
            }
        }
    }

    /** Only the supplied page's own explicitly opaque, solid background may replace fallback. */
    public static void matchBackground(View bar, View page) {
        if (bar == null) {
            return;
        }
        int surface = opaqueSurface(page);
        if (surface == Color.TRANSPARENT) {
            surface = fallbackSurface(bar.getContext());
        }
        if (opaqueSurface(bar) != surface) {
            bar.setBackgroundColor(surface);
        }
        if (bar instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) bar;
            boolean dark = isDark(surface);
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                if (child instanceof TextView) {
                    applyPalette((TextView) child, dark);
                }
            }
        }
    }

    private static int fallbackSurface(Context context) {
        int night = context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return night == Configuration.UI_MODE_NIGHT_YES ? DARK_SURFACE : LIGHT_SURFACE;
    }

    // Transparent is an unambiguous sentinel: accepted colors always have alpha 255.
    private static int opaqueSurface(View view) {
        if (view == null || view.getAlpha() != 1f) {
            return Color.TRANSPARENT;
        }
        Drawable background = view.getBackground();
        if (background instanceof ColorDrawable && background.getOpacity() == PixelFormat.OPAQUE) {
            int color = ((ColorDrawable) background).getColor();
            if (Color.alpha(color) == 255) {
                return color;
            }
        }
        return Color.TRANSPARENT;
    }

    private static int buttonSurface(TextView button) {
        ViewParent parent = button.getParent();
        if (parent instanceof View && BAR_TAG.equals(((View) parent).getTag())) {
            // Read our bar, not the page or window. This retains matchBackground across refresh.
            int surface = opaqueSurface((View) parent);
            if (surface != Color.TRANSPARENT) {
                return surface;
            }
        }
        return fallbackSurface(button.getContext());
    }

    private static boolean isDark(int color) {
        return Color.red(color) * 299 + Color.green(color) * 587 + Color.blue(color) * 114
                < 128000;
    }

    private static void applyPalette(TextView button, boolean dark) {
        ColorStateList colors = dark ? DARK_TEXT_COLORS : LIGHT_TEXT_COLORS;
        if (button.getTextColors() != colors) {
            button.setTextColor(colors);
        }
        Drawable background = button.getBackground();
        if (!(background instanceof ButtonBackground) || ((ButtonBackground) background).dark != dark) {
            button.setBackground(new ButtonBackground(button.getContext(), dark));
        }
    }

    private static void flatten(View view) {
        if (Build.VERSION.SDK_INT >= 21) {
            view.setStateListAnimator(null);
            view.setElevation(0f);
            view.setTranslationZ(0f);
            view.setBackgroundTintList(null);
        }
    }

    private static int dp(Context context, int value) {
        return Math.max(1, Math.round(context.getResources().getDisplayMetrics().density * value));
    }

    private static Drawable pressFeedback(boolean dark) {
        int color = dark ? DARK_PRESS : LIGHT_PRESS;
        if (Build.VERSION.SDK_INT >= 21) {
            // An opaque rectangular mask bounds the system ripple; there is no resting fill.
            return new RippleDrawable(ColorStateList.valueOf(color), null,
                    new ColorDrawable(Color.WHITE));
        }
        StateListDrawable feedback = new StateListDrawable();
        feedback.addState(new int[] { android.R.attr.state_enabled, android.R.attr.state_pressed },
                new ColorDrawable(color));
        feedback.addState(new int[] { android.R.attr.state_enabled, android.R.attr.state_focused },
                new ColorDrawable(color));
        feedback.addState(new int[0], new ColorDrawable(Color.TRANSPARENT));
        return feedback;
    }

    private static final class ButtonBackground extends LayerDrawable {
        final boolean dark;

        ButtonBackground(Context context, boolean dark) {
            super(new Drawable[] {
                    new SelectionMarker(context, dark ? DARK_GREEN : LIGHT_GREEN),
                    pressFeedback(dark)
            });
            this.dark = dark;
        }
    }

    /** A separate short bottom marker, not an underline applied to the text glyphs. */
    private static final class SelectionMarker extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int color;
        private final float width;
        private final float thickness;
        private final float bottomInset;
        private boolean selected;
        private int alpha = 255;

        SelectionMarker(Context context, int color) {
            this.color = color;
            width = dp(context, 24);
            thickness = dp(context, 2);
            bottomInset = dp(context, 4);
            paint.setColor(color);
        }

        @Override
        public boolean isStateful() {
            return true;
        }

        @Override
        protected boolean onStateChange(int[] state) {
            boolean active = false;
            for (int value : state) {
                if (value == android.R.attr.state_selected) {
                    active = true;
                    break;
                }
            }
            if (selected == active) {
                return false;
            }
            selected = active;
            invalidateSelf();
            return true;
        }

        @Override
        public void draw(Canvas canvas) {
            if (!selected) {
                return;
            }
            Rect bounds = getBounds();
            float length = Math.min(width, bounds.width() * 0.4f);
            float bottom = bounds.bottom - bottomInset;
            if (length <= 0f || bottom <= bounds.top) {
                return;
            }
            float left = bounds.left + (bounds.width() - length) * 0.5f;
            canvas.drawRect(left, Math.max(bounds.top, bottom - thickness),
                    left + length, bottom, paint);
        }

        @Override
        public void setAlpha(int alpha) {
            int value = Math.max(0, Math.min(255, alpha));
            if (this.alpha != value) {
                this.alpha = value;
                paint.setAlpha((Color.alpha(color) * value + 127) / 255);
                invalidateSelf();
            }
        }

        @Override
        public int getAlpha() {
            return alpha;
        }

        @Override
        public void setColorFilter(ColorFilter filter) {
            paint.setColorFilter(filter);
            invalidateSelf();
        }

        @Override
        public ColorFilter getColorFilter() {
            return paint.getColorFilter();
        }

        @Override
        public int getOpacity() {
            return selected ? PixelFormat.TRANSLUCENT : PixelFormat.TRANSPARENT;
        }
    }
}
