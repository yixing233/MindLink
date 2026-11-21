package wex.ui.core.feature;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

/** 用独立 UI 替身执行真正的注入代码，不模拟 Android 绘制或真实微信业务。 */
public final class PlusMenuChecks {
    private static final String GROUP = "wex_plus_menu_extra_group";
    private static final String READ = "wex_mark_all_read_item";
    private static final String SETTINGS = "w\u0435\u0445_plus_item";
    private static int assertions;
    private static int nativeClicks;

    private static void check(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }

    private static LinearLayout vertical(Context context) {
        LinearLayout view = new LinearLayout(context);
        view.setOrientation(LinearLayout.VERTICAL);
        return view;
    }

    private static final class NativeRow {
        final ViewGroup row;
        final ViewGroup textParent;
        final ViewGroup iconParent;
        final TextView text;
        final ImageView icon;
        final LinearLayout.LayoutParams params;

        NativeRow(Context context) {
            row = new ViewGroup(context);
            row.layout(0, 0, 660, 180);
            row.setPadding(30, 12, 33, 15);
            params = new LinearLayout.LayoutParams(-1, -2);
            params.setMargins(9, 3, 12, 6);
            row.setLayoutParams(params);
            row.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) { nativeClicks++; }
            });
            iconParent = new ViewGroup(context);
            iconParent.layout(45, 30, 180, 135);
            icon = new ImageView(context);
            icon.layout(6, 0, 78, 72);
            icon.setLayoutParams(new ViewGroup.LayoutParams(72, 72));
            icon.setPadding(3, 3, 3, 3);
            iconParent.addView(icon);
            row.addView(iconParent);
            textParent = new ViewGroup(context);
            textParent.layout(135, 30, 600, 120);
            text = new TextView(context);
            text.layout(12, 0, 444, 81);
            text.setText("发起群聊");
            text.setPadding(3, 6, 9, 12);
            text.setTextSize(TypedValue.COMPLEX_UNIT_PX, 72f);
            text.setTypeface(new Typeface());
            text.setIncludeFontPadding(false);
            text.setLetterSpacing(0.04f);
            text.setTextScaleX(1.1f);
            text.setSingleLine(true);
            text.setEllipsize(TextUtils.TruncateAt.END);
            text.setTextColor(new ColorStateList(new int[][]{{View.TEST_PRESSED}, {}},
                    new int[]{0xFF22CC77, 0xFFEDEDED}));
            textParent.addView(text);
            row.addView(textParent);
        }

        void unmeasured(int requestedHeight) {
            row.layout(0, 0, 0, 0);
            params.height = requestedHeight;
            icon.layout(0, 0, 0, 0);
            text.layout(0, 0, 0, 0);
        }
    }

    private static LinearLayout group(ViewGroup host) {
        View value = host.findViewWithTag(GROUP);
        check(value instanceof LinearLayout, "module group present");
        return (LinearLayout) value;
    }

    private static TextView label(View row) { return (TextView) ((ViewGroup) row).getChildAt(1); }
    private static ImageView icon(View row) { return (ImageView) ((ViewGroup) row).getChildAt(0); }
    private static View.OnClickListener noop() {
        return new View.OnClickListener() {
            @Override public void onClick(View view) {}
        };
    }

    private static void nativeStyleAndIdempotence() {
        final Activity context = new Activity();
        LinearLayout host = vertical(context);
        NativeRow nativeRow = new NativeRow(context);
        host.addView(nativeRow.row);
        host.addView(new NativeRow(context).row);
        nativeRow.row.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), null, null));
        final int[] settingsCalls = {0};
        View.OnClickListener first = new View.OnClickListener() {
            @Override public void onClick(View view) { settingsCalls[0]++; }
        };
        QuickActionCard.enabled = true;
        PlusMenuExtra.sync(context, nativeRow.row, nativeRow.textParent, first);
        LinearLayout group = group(host);
        check(host.getChildCount() == 3 && host.getChildAt(2) == group, "one group after native rows");
        check(nativeRow.row.getLayoutParams() == nativeRow.params, "native layout params untouched");
        check(group.getChildCount() == 2, "exactly two extra rows");
        View read = group.getChildAt(0), settings = group.getChildAt(1);
        check(READ.equals(read.getTag()) && SETTINGS.equals(settings.getTag()), "stable read then settings order");
        for (View row : new View[]{read, settings}) {
            check(row.getMinimumHeight() == 180, "measured native WRAP_CONTENT height");
            check(row.getLayoutParams().height == -2, "text can grow instead of clipping");
            check(row.getPaddingLeft() == 30 && row.getPaddingTop() == 12
                    && row.getPaddingRight() == 33 && row.getPaddingBottom() == 15, "all four paddings");
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) row.getLayoutParams();
            check(params.leftMargin == 9 && params.topMargin == 3
                    && params.rightMargin == 12 && params.bottomMargin == 6, "outer margins copied");
            LinearLayout.LayoutParams iconParams = (LinearLayout.LayoutParams) icon(row).getLayoutParams();
            check(iconParams.width == 72 && iconParams.height == 72, "native icon slot, no separate scaling");
            check(iconParams.getMarginStart() == 21 && iconParams.getMarginEnd() == 24,
                    "nested icon/text converted to full row coordinates");
            check(icon(row).getPaddingLeft() == 3, "icon content inset");
            TextView text = label(row);
            check(text.getTextSize() == 72f && text.lastTextSizeUnit == TypedValue.COMPLEX_UNIT_PX,
                    "large font copies px without applying scaledDensity again");
            check(text.getTypeface() == nativeRow.text.getTypeface(), "native typeface retained");
            check(text.getTextColors() == nativeRow.text.getTextColors(), "stateful text colors retained");
            check(!text.getIncludeFontPadding() && text.getLetterSpacing() == 0.04f && text.getTextScaleX() == 1.1f,
                    "text geometry retained");
            check(text.isSingleLine() && text.getMaxLines() == 1 && text.getEllipsize() == TextUtils.TruncateAt.END,
                    "native single line and ellipsis");
            check(text.getPaddingBottom() == 12 && text.isDuplicateParentStateEnabled(), "text padding and row state");
            check(icon(row).getImageTintList() == nativeRow.text.getTextColors(), "icon follows native text colors");
        }
        check(read.getBackground() != settings.getBackground() && read.getBackground() != nativeRow.row.getBackground(),
                "independent mutable backgrounds");
        read.setPressed(true);
        check(read.getBackground().getState().length == 1 && settings.getBackground().getState().length == 0
                && nativeRow.row.getBackground().getState().length == 0, "press state cannot leak to other rows");
        int additions = host.additions, removals = host.removals;
        PlusMenuExtra.sync(context, host, nativeRow.row, first);
        check(group(host) == group && host.additions == additions && host.removals == removals,
                "second injection path does not move group");
        check(group.getChildAt(0) == read && group.getChildAt(1) == settings, "row identities stable");
        check(MarkAllReadFeature.calls == 0 && nativeClicks == 0 && settingsCalls[0] == 0, "injection never performs clicks");
        settings.performClick();
        read.performClick();
        check(settingsCalls[0] == 1 && MarkAllReadFeature.calls == 1 && MarkAllReadFeature.last == context,
                "original business delegates called only on mock click");
        check(nativeClicks == 0, "no native click to dismiss popup");
        QuickActionCard.enabled = false;
        PlusMenuExtra.sync(context, host, nativeRow.row, noop());
        check(group.getChildCount() == 1 && group.getChildAt(0) == settings, "disable removes only read row");
        settings.performClick();
        check(settingsCalls[0] == 1, "settings listener refreshed");
        QuickActionCard.enabled = true;
        PlusMenuExtra.sync(context, nativeRow.row, nativeRow.textParent, first);
        check(group.getChildCount() == 2 && group.getChildAt(0) == read && group.getChildAt(1) == settings,
                "enable restores order and identity");
        host.removeView(group);
        PlusMenuExtra.sync(context, host, nativeRow.row, first);
        check(group(host) != group && host.getChildCount() == 3, "removed group can be recreated without duplicates");
    }

    private static void heightFallbackAndRtl() {
        Context context = new Context();
        for (int requested : new int[]{198, -2, 6}) {
            LinearLayout host = vertical(context);
            NativeRow nativeRow = new NativeRow(context);
            nativeRow.unmeasured(requested);
            host.addView(nativeRow.row);
            host.addView(new NativeRow(context).row);
            QuickActionCard.enabled = true;
            PlusMenuExtra.sync(context, host, nativeRow.row, noop());
            LinearLayout group = group(host);
            int expected = requested == 198 ? 198 : requested == -2 ? 168 : 144;
            check(group.getChildAt(0).getMinimumHeight() == expected
                    && group.getChildAt(1).getMinimumHeight() == expected, "layout height then 56dp fallback and touch minimum");
            check(group.getChildAt(0).getForeground() instanceof RippleDrawable, "independent fallback press feedback");
            check(group.getChildAt(0).getForeground() != group.getChildAt(1).getForeground(), "fallback not shared");
        }
        LinearLayout host = vertical(context);
        NativeRow nativeRow = new NativeRow(context);
        nativeRow.row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        nativeRow.params.setMarginStart(15);
        nativeRow.params.setMarginEnd(18);
        nativeRow.iconParent.layout(531, 30, 636, 135);
        nativeRow.textParent.layout(45, 30, 513, 120);
        host.addView(nativeRow.row);
        host.addView(new NativeRow(context).row);
        PlusMenuExtra.sync(context, nativeRow.row, nativeRow.textParent, noop());
        View row = group(host).getChildAt(0);
        LinearLayout.LayoutParams iconParams = (LinearLayout.LayoutParams) icon(row).getLayoutParams();
        check(row.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL
                && iconParams.getMarginStart() == 18 && iconParams.getMarginEnd() == 48, "RTL row coordinate alignment");
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) row.getLayoutParams();
        check(params.getMarginStart() == 15 && params.getMarginEnd() == 18, "relative native margins copied");
    }

    private static void listFootersAndSelector() {
        Activity context = new Activity();
        ListView host = new ListView(context);
        NativeRow nativeRow = new NativeRow(context);
        nativeRow.row.setLayoutParams(new AbsListView.LayoutParams(-1, -2));
        host.displayNativeRow(nativeRow.row);
        host.setSelector(new RippleDrawable(ColorStateList.valueOf(0x33000000), null, null));
        host.setDivider(new ColorDrawable(0xFF444444));
        host.setDividerHeight(3);
        QuickActionCard.enabled = true;
        PlusMenuExtra.sync(context, nativeRow.row, nativeRow.textParent, noop());
        LinearLayout group = group(host);
        check(host.getFooterViewsCount() == 1 && host.footerAdds == 1, "both rows in one public footer");
        check(group.getLayoutParams() instanceof AbsListView.LayoutParams, "ListView footer params");
        check(group.getDividerDrawable().getIntrinsicHeight() == 3, "native divider between extra rows");
        check(group.getChildAt(0).getForeground() != host.getSelector()
                && group.getChildAt(0).getForeground().isStateful(), "independent ListView selector clone");
        host.hideFooters();
        check(host.findViewWithTag(GROUP) == null, "footer can be offscreen");
        PlusMenuExtra.sync(context, host, nativeRow.row, noop());
        check(host.footerAdds == 1 && host.getFooterViewsCount() == 1, "offscreen footer is not re-added");
        QuickActionCard.enabled = false;
        PlusMenuExtra.sync(context, host, nativeRow.row, noop());
        check(group.getChildCount() == 1 && SETTINGS.equals(group.getChildAt(0).getTag()), "offscreen toggle still synchronizes");
        host.showFooters();
        check(group(host) == group && host.footerRemoves == 0, "same registered footer survives visibility changes");
    }

    private static void safeContainersAndLegacyRows() {
        Context context = new Context();
        for (ViewGroup host : new ViewGroup[]{new ViewGroup(context), new LinearLayout(context), vertical(context)}) {
            NativeRow nativeRow = new NativeRow(context);
            host.addView(nativeRow.row);
            int count = host.getChildCount();
            PlusMenuExtra.sync(context, host, nativeRow.row, noop());
            check(host.getChildCount() == count && host.findViewWithTag(GROUP) == null,
                    "unknown, horizontal or ambiguous single-row container unchanged");
        }
        LinearLayout host = vertical(context);
        NativeRow nativeRow = new NativeRow(context);
        host.addView(nativeRow.row);
        host.addView(new NativeRow(context).row);
        for (String tag : new String[]{SETTINGS, READ}) {
            LinearLayout old = new LinearLayout(context);
            old.setTag(tag);
            host.addView(old);
        }
        QuickActionCard.enabled = true;
        PlusMenuExtra.sync(context, host, nativeRow.row, noop());
        check(host.getChildCount() == 3 && group(host).getChildCount() == 2, "old module rows replaced, native rows untouched");
        ListView list = new ListView(context);
        NativeRow listRow = new NativeRow(context);
        list.displayNativeRow(listRow.row);
        LinearLayout old = new LinearLayout(context);
        old.setTag(SETTINGS);
        list.addFooterView(old, null, false);
        PlusMenuExtra.sync(context, list, listRow.row, noop());
        check(list.getFooterViewsCount() == 1 && list.footerRemoves == 1, "old footer removed only through public API");
        int childCount = host.getChildCount();
        PlusMenuExtra.sync(context, host, listRow.row, noop());
        check(host.getChildCount() == childCount, "mismatched anchor parent is rejected");
    }

    public static void main(String[] args) {
        nativeStyleAndIdempotence();
        heightFallbackAndRtl();
        listFootersAndSelector();
        safeContainersAndLegacyRows();
        System.out.println("PASS: plus menu " + assertions + " checks: native template, height, nested/RTL geometry, px font, independent press state, stable order, footer deduplication and mock-only delegates");
    }
}
