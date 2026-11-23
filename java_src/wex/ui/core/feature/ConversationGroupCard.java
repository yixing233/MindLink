package wex.ui.core.feature;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

/**
 * Floating-card dress for the conversation group tabs: inset from the screen edges, capsule
 * outline, shadow and the shared card material (卡片材质: plain / frosted / liquid glass), the
 * same card fit the floating chat header uses.
 *
 * <p>The tabs overlay already floats above the conversation list (it sits over a same-height
 * header spacer and can stay pinned under the action bar). The host's height includes the gaps
 * around the card, so the spacer keeps the first conversation row clear of the card and of its
 * shadow. With glass the material samples the list behind it, so rows scrolling under a pinned
 * card show through.</p>
 */
final class ConversationGroupCard {
    static final int SIDE_DP = 12, TOP_DP = 6, BOTTOM_DP = 8;
    /** With 48dp tabs this is a capsule, matching the floating bottom bar. */
    static final int CORNER_DP = 24, ELEVATION_DP = 4;
    /** Tabs are 36dp pills inside a 6dp inner padding, so the card stays 48dp tall. */
    static final int TAB_HEIGHT_DP = 36, TABS_PAD_DP = 6, TAB_GAP_DP = 2;
    /** Selection capsule slide between tabs. */
    private static final long SLIDE_MS = 280L;
    private static final WeakHashMap<View, FloatingHeaderStyle.CardState> STATES = new WeakHashMap<>();
    /** Last applied look per tab (selected, accent, dark), so a host refresh does not rebuild drawables. */
    private static final WeakHashMap<View, String> TAB_LOOKS = new WeakHashMap<>();

    private ConversationGroupCard() { }

    /** Wraps {@code tabs} in a card inset inside {@code host}; returns the card. */
    static View wrap(FrameLayout host, View tabs) {
        FrameLayout card = new FrameLayout(host.getContext());
        card.addView(tabs, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        float density = host.getResources().getDisplayMetrics().density;
        // Padding, not margins: the host's own height then measures card + gaps for the spacer.
        host.setPadding(Math.round(SIDE_DP * density), Math.round(TOP_DP * density),
                Math.round(SIDE_DP * density), Math.round(BOTTOM_DP * density));
        host.setClipToPadding(false);
        host.addView(card, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        apply(card);
        return card;
    }

    /** (Re)applies the current material; called on mount and on every host refresh. */
    static void apply(View card) {
        int mode = ChatGlassSurface.mode();
        STATES.put(card, FloatingHeaderStyle.applyCard(card, STATES.get(card), CORNER_DP, ELEVATION_DP,
                mode, ChatGlassSurface.blurRadiusDp(mode)));
    }

    /** Stops the glass sampler; the card is discarded with its host. */
    static void release(View card) {
        if (card.getBackground() instanceof ChatGlassSurface) ((ChatGlassSurface) card.getBackground()).stop();
        STATES.remove(card);
    }

    /** Re-samples the list behind the card (it was hidden or moved by the host). */
    static void refreshGlass(View card) {
        if (card != null && card.getBackground() instanceof ChatGlassSurface) ((ChatGlassSurface) card.getBackground()).refresh();
    }

    // ------------------------------------------------------------------ selected state

    /**
     * Inner padding of the tab row (the pills never touch the card's rounded edge), and the
     * row's background: the one selection capsule that slides from tab to tab.
     */
    static void styleTabs(LinearLayout tabs) {
        int pad = dp(tabs.getContext(), TABS_PAD_DP);
        tabs.setPadding(pad, pad, pad, pad);
        if (!(tabs.getBackground() instanceof SelectionPill)) tabs.setBackground(new SelectionPill());
    }

    /** Small gap between pills; equal-width tabs keep their weight. */
    static void tabMargins(ViewGroup.LayoutParams params, Context context) {
        if (!(params instanceof ViewGroup.MarginLayoutParams)) return;
        int gap = dp(context, TAB_GAP_DP);
        ((ViewGroup.MarginLayoutParams) params).setMargins(gap, 0, gap, 0);
    }

    /**
     * Selected tab: bold accent text over the row's accent capsule ({@link SelectionPill}). Every
     * tab gets a capsule-shaped press ripple of its own. {@code animate} slides the capsule from
     * the previously selected tab to this one; first builds and theme refreshes place it at once.
     */
    static void styleTab(TextView label, boolean selected, boolean animate) {
        Context context = label.getContext();
        boolean dark = SettingsTheme.isDark(context);
        int accent = SettingsTheme.accent(context);
        SelectionPill pill = label.getParent() instanceof View
                && ((View) label.getParent()).getBackground() instanceof SelectionPill
                ? (SelectionPill) ((View) label.getParent()).getBackground() : null;
        String look = selected + ":" + accent + ":" + dark;
        if (look.equals(TAB_LOOKS.get(label)) && (!selected || pill == null || pill.holds(label))) return;
        TAB_LOOKS.put(label, look);
        label.setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        if (pill != null) {
            if (selected) pill.select(label, (accent & 0x00FFFFFF) | ((dark ? 0x3D : 0x24) << 24), animate);
            else pill.release(label);
        }
        // No fill on the tab itself: the capsule lives in the row and moves between tabs.
        label.setBackground(ripple(context, dark, null));
    }

    /** The "+" tab: same capsule press feedback, never a selected fill. */
    static void styleAdd(TextView add) {
        add.setBackground(ripple(add.getContext(), SettingsTheme.isDark(add.getContext()), null));
    }

    /** Centres the newly selected tab when the row scrolls (many groups). */
    static void reveal(HorizontalScrollView bar, View tab) {
        bar.post(() -> {
            if (!(tab.getParent() instanceof View) || bar.getWidth() <= 0) return;
            View row = (View) tab.getParent();
            int target = row.getLeft() + tab.getLeft() + tab.getWidth() / 2 - bar.getWidth() / 2;
            bar.smoothScrollTo(Math.max(0, target), 0);
        });
    }

    private static Drawable ripple(Context context, boolean dark, Drawable content) {
        int press = dark ? 0x1FFFFFFF : 0x14000000;
        return new RippleDrawable(ColorStateList.valueOf(press), content, pill(0xFFFFFFFF));
    }

    /** A radius far above half the height: GradientDrawable clamps it, so any height is a capsule. */
    private static GradientDrawable pill(int color) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(1000f);
        return shape;
    }

    /**
     * The selection capsule, drawn as the tab row's background (under the tabs and their
     * ripples). It always follows the selected tab's live bounds, so text changes, equal-width
     * relayouts and new groups just move it; a selection change slides it from where it was
     * drawn, with the leading edge slightly ahead of the trailing one for a fluid stretch.
     */
    static final class SelectionPill extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF from = new RectF(), shown = new RectF(), live = new RectF();
        private final PathInterpolator lead = new PathInterpolator(0.3f, 0f, 0.1f, 1f),
                trail = new PathInterpolator(0.5f, 0f, 0.2f, 1f);
        private WeakReference<View> target = new WeakReference<>(null);
        private ValueAnimator animator;
        private float progress = 1f;
        private boolean forward = true;

        boolean holds(View label) { return target.get() == label; }

        void select(View label, int color, boolean animate) {
            boolean moved = target.get() != label;
            paint.setColor(color);
            target = new WeakReference<>(label);
            if (moved && animate && !shown.isEmpty()) {
                from.set(shown);
                if (animator != null) animator.cancel();
                progress = 0f;
                animator = ValueAnimator.ofFloat(0f, 1f).setDuration(SLIDE_MS);
                animator.addUpdateListener(a -> { progress = (Float) a.getAnimatedValue(); invalidateSelf(); });
                animator.start();
            } else if (moved) {
                if (animator != null) { animator.cancel(); animator = null; }
                progress = 1f;
            }
            invalidateSelf();
        }

        /** The tab lost the selection. The drawn spot is kept so the next select slides from it. */
        void release(View label) {
            if (target.get() != label) return;
            target = new WeakReference<>(null);
            invalidateSelf();
        }

        @Override public void draw(Canvas canvas) {
            View view = target.get();
            if (view == null || view.getParent() == null || view.getWidth() <= 0 || view.getVisibility() != View.VISIBLE) {
                // Nothing selected: nothing drawn, but keep `shown` as the next slide's origin.
                return;
            }
            live.set(view.getLeft(), view.getTop(), view.getRight(), view.getBottom());
            if (progress >= 1f || from.isEmpty()) {
                shown.set(live);
            } else {
                forward = live.centerX() >= from.centerX();
                float fast = lead.getInterpolation(progress), slow = trail.getInterpolation(progress);
                // Moving right the right edge leads; moving left the left edge leads.
                float l = lerp(from.left, live.left, forward ? slow : fast);
                float r = lerp(from.right, live.right, forward ? fast : slow);
                shown.set(l, lerp(from.top, live.top, fast), Math.max(l, r), lerp(from.bottom, live.bottom, fast));
            }
            float radius = shown.height() / 2f;
            canvas.drawRoundRect(shown, radius, radius, paint);
        }

        private static float lerp(float a, float b, float t) { return a + (b - a) * t; }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
        @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    private static int dp(Context context, int value) {
        return Math.round(context.getResources().getDisplayMetrics().density * value);
    }
}
