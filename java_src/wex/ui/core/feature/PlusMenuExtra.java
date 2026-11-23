package wex.ui.core.feature;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 首页「+」菜单：两项共用原生行样式，只管理模块自己的行。
 *
 * 来源：菜单注入思路参照 WeKit 的
 * `features/items/home_screen_menu/MarkAllAsRead.kt` 与 `ModuleSettings.kt`（GPL-3.0）；
 * 本文件自行实现原生行样式复制与去重逻辑。
 */
public final class PlusMenuExtra {
    private static final String TAG_GROUP = "wex_plus_menu_extra_group";
    private static final String TAG_READ = "wex_mark_all_read_item";
    private static final String TAG_SETTINGS = "w\u0435\u0445_plus_item";
    private static final Map<ViewGroup, WeakReference<MenuGroup>> GROUPS =
            new WeakHashMap<ViewGroup, WeakReference<MenuGroup>>();
    private static boolean warned;

    private PlusMenuExtra() {
    }

    public static void sync(Context context, ViewGroup container, ViewGroup anchor,
                            View.OnClickListener settingsClick) {
        if (context == null || container == null || anchor == null || settingsClick == null) return;
        try {
            Target target = findTarget(container, anchor);
            if (target == null) {
                warn("未识别到完整原生菜单行和安全容器，跳过追加");
                return;
            }
            ViewGroup host = target.host;
            Style style = new Style(context, target);
            WeakReference<MenuGroup> reference = GROUPS.get(host);
            MenuGroup group = reference == null ? null : reference.get();
            if (group == null) GROUPS.remove(host);
            if (group != null && !(host instanceof ListView) && group.getParent() != host) {
                GROUPS.remove(host);
                group = null;
            }
            if (group == null) {
                View existing = host.findViewWithTag(TAG_GROUP);
                if (existing != null && !(existing instanceof MenuGroup)) return;
                group = (MenuGroup) existing;
            }
            if (group == null) group = new MenuGroup(context);
            group.sync(style, QuickActionCard.isEnabled(), settingsClick);
            if (!removeLooseRow(host, group, TAG_READ) || !removeLooseRow(host, group, TAG_SETTINGS)) return;
            if (!GROUPS.containsKey(host)) {
                if (host instanceof ListView) {
                    // Footer 即使滚出可见区也仍由 ListView 持有，不能靠 findViewWithTag 去重。
                    if (group.getParent() == null) {
                        group.setLayoutParams(new AbsListView.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                        ((ListView) host).addFooterView(group, null, false);
                    }
                } else if (group.getParent() == null) {
                    host.addView(group, new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                }
                GROUPS.put(host, new WeakReference<MenuGroup>(group));
            }
        } catch (Throwable t) {
            warn("菜单同步失败: " + t.getClass().getSimpleName());
        }
    }

    private static Target findTarget(ViewGroup container, ViewGroup anchor) {
        if (anchor.getParent() != container) return null;
        ViewGroup row = anchor;
        for (int depth = 0; depth < 12; depth++) {
            ViewParent parent = row.getParent();
            if (!(parent instanceof ViewGroup)) return null;
            ViewGroup host = (ViewGroup) parent;
            Parts parts = parts(row);
            if (parts != null) {
                if (host instanceof ListView) return new Target(host, row, parts);
                if (host instanceof LinearLayout
                        && ((LinearLayout) host).getOrientation() == LinearLayout.VERTICAL
                        && hasNativeSibling(host, row)) return new Target(host, row, parts);
            }
            if (host instanceof AdapterView) return null;
            row = host;
        }
        return null;
    }

    private static boolean hasNativeSibling(ViewGroup host, View row) {
        if (host.getChildCount() > 64) return false;
        for (int i = 0; i < host.getChildCount(); i++) {
            View sibling = host.getChildAt(i);
            if (sibling != row && parts(sibling) != null) return true;
        }
        return false;
    }

    private static Parts parts(View root) {
        ArrayDeque<View> queue = new ArrayDeque<View>();
        queue.add(root);
        TextView text = null;
        ImageView icon = null;
        int visited = 0;
        while (!queue.isEmpty()) {
            if (++visited > 128) return null;
            View view = queue.removeFirst();
            if (owned(view) || view.getVisibility() == View.GONE) continue;
            if (view instanceof TextView && ((TextView) view).getText().length() > 0) {
                if (text != null) return null;
                text = (TextView) view;
            }
            if (icon == null && view instanceof ImageView) icon = (ImageView) view;
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) queue.addLast(group.getChildAt(i));
            }
        }
        return text != null && icon != null ? new Parts(text, icon) : null;
    }

    private static boolean owned(View view) {
        Object tag = view.getTag();
        return TAG_GROUP.equals(tag) || TAG_READ.equals(tag) || TAG_SETTINGS.equals(tag);
    }

    private static boolean removeLooseRow(ViewGroup host, MenuGroup group, String tag) {
        View existing = host.findViewWithTag(tag);
        if (existing == null || existing.getParent() == group) return true;
        if (!(existing instanceof LinearLayout)) return false;
        if (host instanceof ListView) return ((ListView) host).removeFooterView(existing);
        if (existing.getParent() != host) return false;
        host.removeView(existing);
        return true;
    }

    private static Rect bounds(ViewGroup row, View child) {
        if (row.getWidth() <= 0 || child.getWidth() <= 0 || child.getHeight() <= 0) return null;
        Rect rect = new Rect(0, 0, child.getWidth(), child.getHeight());
        row.offsetDescendantRectToMyCoords(child, rect);
        return rect;
    }

    private static Drawable copy(Context context, Drawable source) {
        if (source == null) return null;
        try {
            Drawable.ConstantState state = source.getConstantState();
            if (state == null) return null;
            Drawable result = state.newDrawable(context.getResources());
            if (result == source) return null;
            result = result.mutate();
            if (result == source) return null;
            result.setAlpha(source.getAlpha());
            result.setLevel(source.getLevel());
            result.setLayoutDirection(source.getLayoutDirection());
            return result;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static int size(View view, boolean width, int fallback) {
        int measured = width ? view.getWidth() : view.getHeight();
        if (measured > 0) return measured;
        ViewGroup.LayoutParams params = view.getLayoutParams();
        int requested = params == null ? 0 : (width ? params.width : params.height);
        return requested > 0 ? requested : fallback;
    }

    private static int dp(Context context, int value) {
        return Math.max(1, Math.round(value * context.getResources().getDisplayMetrics().density));
    }

    private static void warn(String message) {
        if (warned) return;
        warned = true;
        try {
            dev.mindlink.xposed.XposedBridge.log("WEX: +面板: " + message);
        } catch (Throwable ignored) {
        }
    }

    private static final class Parts {
        final TextView text;
        final ImageView icon;

        Parts(TextView text, ImageView icon) {
            this.text = text;
            this.icon = icon;
        }
    }

    private static final class Target {
        final ViewGroup host;
        final ViewGroup row;
        final Parts parts;

        Target(ViewGroup host, ViewGroup row, Parts parts) {
            this.host = host;
            this.row = row;
            this.parts = parts;
        }
    }

    private static final class Style {
        final int height;
        final int[] padding;
        final int[] iconPadding;
        final int[] textPadding;
        final int iconWidth;
        final int iconHeight;
        final int iconInset;
        final int gap;
        final int direction;
        final float textSize;
        final float letterSpacing;
        final float textScale;
        final ColorStateList textColors;
        final ColorStateList iconColors;
        final Typeface typeface;
        final boolean fontPadding;
        final boolean singleLine;
        final int maxLines;
        final TextUtils.TruncateAt ellipsize;
        final Drawable background;
        final Drawable foreground;
        final Drawable selector;
        final Drawable divider;
        final int dividerHeight;
        final int dividerPadding;
        final ViewGroup.MarginLayoutParams margins;
        /** 同一菜单里的原生图标，用来按其实际墨迹大小对齐模块图标。 */
        final ImageView nativeIcon;

        Style(Context context, Target target) {
            ViewGroup row = target.row;
            TextView text = target.parts.text;
            ImageView icon = target.parts.icon;
            nativeIcon = icon;
            height = Math.max(dp(context, 48), Math.max(row.getMinimumHeight(), size(row, false, dp(context, 56))));
            padding = padding(row);
            iconPadding = padding(icon);
            textPadding = padding(text);
            iconWidth = size(icon, true, dp(context, 24));
            iconHeight = size(icon, false, dp(context, 24));
            direction = row.getLayoutDirection();
            boolean rtl = direction == View.LAYOUT_DIRECTION_RTL;
            Rect iconRect = bounds(row, icon);
            Rect textRect = bounds(row, text);
            iconInset = iconRect == null ? 0 : Math.max(0, rtl
                    ? row.getWidth() - row.getPaddingRight() - iconRect.right
                    : iconRect.left - row.getPaddingLeft());
            gap = iconRect == null || textRect == null ? dp(context, 12)
                    : Math.max(0, rtl ? iconRect.left - textRect.right : textRect.left - iconRect.right);
            textSize = text.getTextSize();
            letterSpacing = text.getLetterSpacing();
            textScale = text.getTextScaleX();
            textColors = text.getTextColors();
            iconColors = icon.getImageTintList() == null ? textColors : icon.getImageTintList();
            typeface = text.getTypeface();
            fontPadding = text.getIncludeFontPadding();
            singleLine = text.isSingleLine() || text.getMaxLines() == 1;
            maxLines = text.getMaxLines() > 0 ? text.getMaxLines() : 1;
            ellipsize = text.getEllipsize();
            background = row.getBackground();
            foreground = row.getForeground();
            selector = target.host instanceof ListView ? ((ListView) target.host).getSelector() : null;
            ViewGroup.LayoutParams params = row.getLayoutParams();
            margins = params instanceof ViewGroup.MarginLayoutParams
                    ? new ViewGroup.MarginLayoutParams((ViewGroup.MarginLayoutParams) params) : null;
            if (target.host instanceof ListView) {
                ListView list = (ListView) target.host;
                divider = list.getDivider();
                dividerHeight = Math.max(0, list.getDividerHeight());
                dividerPadding = 0;
            } else {
                LinearLayout list = (LinearLayout) target.host;
                divider = (list.getShowDividers() & LinearLayout.SHOW_DIVIDER_MIDDLE) == 0
                        ? null : list.getDividerDrawable();
                dividerHeight = divider == null ? 0 : Math.max(0, divider.getIntrinsicHeight());
                dividerPadding = list.getDividerPadding();
            }
        }

        private static int[] padding(View view) {
            return new int[]{view.getPaddingLeft(), view.getPaddingTop(), view.getPaddingRight(), view.getPaddingBottom()};
        }

        LinearLayout.LayoutParams rowParams() {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (margins != null) {
                params.setMargins(margins.leftMargin, margins.topMargin, margins.rightMargin, margins.bottomMargin);
                if (margins.isMarginRelative()) {
                    params.setMarginStart(margins.getMarginStart());
                    params.setMarginEnd(margins.getMarginEnd());
                }
            }
            return params;
        }
    }

    private static final class MenuGroup extends LinearLayout {
        final MenuRow settings;
        MenuRow read;

        MenuGroup(Context context) {
            super(context);
            setTag(TAG_GROUP);
            setOrientation(VERTICAL);
            setClickable(false);
            setFocusable(false);
            settings = new MenuRow(context, TAG_SETTINGS, "Mdl 设置", "wex_setting_icon.png");
            addView(settings);
        }

        void sync(Style style, boolean enabled, View.OnClickListener settingsClick) {
            setLayoutDirection(style.direction);
            Drawable divider = copy(getContext(), style.divider);
            setDividerDrawable(divider != null && style.dividerHeight > 0
                    ? new Divider(divider, style.dividerHeight) : null);
            setShowDividers(SHOW_DIVIDER_MIDDLE);
            setDividerPadding(style.dividerPadding);
            if (enabled && read == null) {
                read = new MenuRow(getContext(), TAG_READ, "一键已读", "mark_read.png");
                read.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Context context = view.getContext();
                        if (context instanceof android.app.Activity) {
                            MarkAllReadFeature.markAllRead((android.app.Activity) context);
                        }
                    }
                });
            }
            if (enabled) {
                if (read.getParent() == null) addView(read, 0);
                read.apply(style);
            } else if (read != null && read.getParent() == this) {
                removeView(read);
            }
            settings.apply(style);
            settings.setOnClickListener(settingsClick);
        }
    }

    private static final class MenuRow extends LinearLayout {
        final ImageView icon;
        final TextView label;
        final String iconName;

        MenuRow(Context context, String tag, String title, String iconName) {
            super(context);
            this.iconName = iconName;
            setTag(tag);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setBaselineAligned(false);
            setClickable(true);
            setFocusable(true);
            icon = new ImageView(context);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            icon.setDuplicateParentStateEnabled(true);
            addView(icon);
            label = new TextView(context);
            label.setText(title);
            label.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            label.setDuplicateParentStateEnabled(true);
            addView(label);
        }

        void apply(Style style) {
            setLayoutDirection(style.direction);
            Drawable background = copy(getContext(), style.background);
            Drawable foreground = copy(getContext(), style.foreground);
            if ((background == null || !background.isStateful()) && (foreground == null || !foreground.isStateful())) {
                Drawable selector = copy(getContext(), style.selector);
                if (selector == null || !selector.isStateful()) {
                    int color = (style.textColors.getDefaultColor() & 0x00FFFFFF) | 0x22000000;
                    selector = new RippleDrawable(ColorStateList.valueOf(color), null, new ColorDrawable(Color.WHITE));
                }
                if (foreground == null) foreground = selector;
                else foreground = new android.graphics.drawable.LayerDrawable(new Drawable[]{foreground, selector});
            }
            setBackground(background);
            setForeground(foreground);
            setPadding(style.padding[0], style.padding[1], style.padding[2], style.padding[3]);
            setMinimumHeight(style.height);
            setLayoutParams(style.rowParams());
            // 优先按原生图标实际画出的墨迹对齐大小与中心；量不到时退回铺满原生图标框。
            int[] fit = LucideIcons.matchNative(style.nativeIcon, iconName, style.iconWidth, style.iconHeight);
            int iconSize;
            if (fit != null) {
                iconSize = fit[0];
                icon.setPadding(fit[1], fit[2], fit[3], fit[4]);
            } else {
                icon.setPadding(style.iconPadding[0], style.iconPadding[1], style.iconPadding[2], style.iconPadding[3]);
                iconSize = Math.max(1, Math.min(style.iconWidth - style.iconPadding[0] - style.iconPadding[2],
                        style.iconHeight - style.iconPadding[1] - style.iconPadding[3]));
            }
            icon.setImageDrawable(LucideIcons.drawable(iconName, iconSize, style.iconColors.getDefaultColor()));
            icon.setImageTintList(style.iconColors);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(style.iconWidth, style.iconHeight);
            iconParams.setMarginStart(style.iconInset);
            iconParams.setMarginEnd(style.gap);
            icon.setLayoutParams(iconParams);
            label.setTextSize(TypedValue.COMPLEX_UNIT_PX, style.textSize);
            label.setTypeface(style.typeface);
            label.setTextColor(style.textColors);
            label.setIncludeFontPadding(style.fontPadding);
            label.setLetterSpacing(style.letterSpacing);
            label.setTextScaleX(style.textScale);
            label.setSingleLine(style.singleLine);
            label.setMaxLines(style.singleLine ? 1 : style.maxLines);
            label.setEllipsize(style.ellipsize);
            label.setPadding(style.textPadding[0], style.textPadding[1], style.textPadding[2], style.textPadding[3]);
            label.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
    }

    private static final class Divider extends Drawable {
        final Drawable drawable;
        final int height;

        Divider(Drawable drawable, int height) {
            this.drawable = drawable;
            this.height = height;
        }

        @Override
        public void draw(Canvas canvas) {
            drawable.setBounds(getBounds());
            drawable.draw(canvas);
        }

        @Override
        public int getIntrinsicHeight() {
            return height;
        }

        @Override
        public void setAlpha(int alpha) {
            drawable.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter filter) {
            drawable.setColorFilter(filter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
