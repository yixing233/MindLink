package wex.ui.core.feature;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.EditText;
import android.widget.ImageView;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import dev.mindlink.xposed.XC_MethodHook;
import dev.mindlink.xposed.XposedBridge;
import wex.ui.core.WexStore;

/** Standard WeChat action bars. Each native view still owns its action, ripple and menu. */
final class NativeTopBarCards {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final WeakHashMap<Activity, Watcher> WATCHERS = new WeakHashMap<>();
    private static boolean initialized;

    private NativeTopBarCards() { }

    static synchronized void init() {
        if (initialized) return;
        initialized = true;
        XposedBridge.hookAllMethods(Activity.class, "onPostResume", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                if (param.hasThrowable() || !(param.thisObject instanceof Activity)) return;
                Activity activity = (Activity) param.thisObject;
                if (!eligible(activity)) return;
                release(activity);
                Watcher watcher = new Watcher(activity);
                WATCHERS.put(activity, watcher);
                watcher.discover(0);
            }
        });
        XC_MethodHook close = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                if (param.thisObject instanceof Activity) release((Activity) param.thisObject);
            }
        };
        XposedBridge.hookAllMethods(Activity.class, "onPause", close);
        XposedBridge.hookAllMethods(Activity.class, "onDestroy", close);
    }

    private static boolean enabled() {
        try { return WexStore.INSTANCE.getBool(HomeTopBarCards.KEY_ENABLED, true); }
        catch (RuntimeException error) { return true; }
    }

    /** Settings or card material changed. */
    static void refresh() {
        MAIN.post(() -> {
            boolean on = enabled();
            for (Watcher watcher : new ArrayList<>(WATCHERS.values())) {
                if (!on) watcher.clearDressing();
                else if (watcher.toolbar != null && watcher.toolbar.isAttachedToWindow()) watcher.onPreDraw();
            }
        });
    }

    private static boolean eligible(Activity activity) {
        if (!"com.tencent.mm".equals(activity.getPackageName())) return false;
        for (Class<?> type = activity.getClass(); type != null && type != Activity.class; type = type.getSuperclass()) {
            String name = type.getName();
            if (name.equals("com.tencent.mm.ui.LauncherUI") || name.contains(".chatting.")
                    || name.endsWith(".SnsTimeLineUI") || name.endsWith(".ImproveSnsTimelineUI")) return false;
        }
        return true;
    }

    private static void release(Activity activity) {
        Watcher watcher = WATCHERS.remove(activity);
        if (watcher != null) watcher.dispose();
    }

    private static final class Watcher implements ViewTreeObserver.OnPreDrawListener {
        final Activity activity;
        View toolbar;
        ViewTreeObserver observer;
        Dressing dressing;

        Watcher(Activity activity) { this.activity = activity; }

        void discover(int attempt) {
            if (WATCHERS.get(activity) != this || activity.isFinishing() || activity.isDestroyed()
                    || activity.getWindow() == null) return;
            View decor = activity.getWindow().getDecorView();
            toolbar = MomentsTopBarCards.findToolbar(decor);
            if (toolbar == null || !toolbar.isAttachedToWindow()) {
                if (attempt < 8) MAIN.postDelayed(() -> discover(attempt + 1), 150L);
                return;
            }
            observer = toolbar.getViewTreeObserver();
            observer.addOnPreDrawListener(this);
            onPreDraw();
        }

        @Override public boolean onPreDraw() {
            if (!toolbar.isAttachedToWindow()) {
                dispose();
                MAIN.post(() -> discover(0));
                return true;
            }
            if (!enabled()) {
                clearDressing();
                return true;
            }
            try {
                List<View> icons = iconTargets(toolbar);
                if (icons.isEmpty()) {
                    clearDressing();
                } else {
                    if (dressing == null) dressing = new Dressing(toolbar, activity.findViewById(android.R.id.content));
                    dressing.update(icons);
                }
            } catch (RuntimeException | LinkageError ignored) {
                release(activity);
            }
            return true;
        }

        void dispose() {
            if (observer != null && observer.isAlive()) observer.removeOnPreDrawListener(this);
            observer = null;
            clearDressing();
        }

        void clearDressing() {
            if (dressing != null) { dressing.dispose(); dressing = null; }
        }
    }

    /** Prefer the visible image center over the size of its native click target. */
    static List<View> iconTargets(View toolbar) {
        if (!visible(toolbar) || !(toolbar instanceof ViewGroup)) return List.of();
        int backId = toolbar.getResources().getIdentifier("actionbar_up_indicator", "id", "com.tencent.mm");
        View back = backId == 0 ? null : toolbar.findViewById(backId);
        if (back == null || !visible(back) || !back.isClickable()) return List.of();
        int[] origin = new int[2], at = new int[2];
        toolbar.getLocationInWindow(origin);
        back.getLocationInWindow(at);
        if (at[0] - origin[0] + back.getWidth() / 2f > toolbar.getWidth() * .4f) return List.of();

        View left = null;
        List<View> icons = new ArrayList<>(), owners = new ArrayList<>();
        ArrayDeque<View> queue = new ArrayDeque<>();
        queue.add(toolbar);
        for (int count = 0; !queue.isEmpty() && count < 128; count++) {
            View child = queue.removeFirst();
            if (!visible(child)) continue;
            if (child instanceof EditText) return List.of();
            if (child instanceof ImageView && ((ImageView) child).getDrawable() != null
                    && child.getWidth() < toolbar.getWidth() * .3f) {
                if (left == null && contains(back, child)) left = child;
                if (!contains(back, child)) {
                    child.getLocationInWindow(at);
                    float x = at[0] - origin[0] + child.getWidth() / 2f;
                    View owner = actionOwner(child, toolbar);
                    if (x >= toolbar.getWidth() * .6f && owner != null && !owners.contains(owner)) {
                        owners.add(owner);
                        icons.add(child);
                    }
                }
            }
            if (child instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) child;
                for (int i = 0; i < group.getChildCount(); i++) queue.addLast(group.getChildAt(i));
            }
        }
        if (icons.isEmpty() || left == null) return List.of();
        icons.add(left);
        icons.sort(Comparator.comparingInt(view -> {
            view.getLocationInWindow(at);
            return at[0] + view.getWidth() / 2;
        }));
        return icons;
    }

    private static boolean visible(View view) {
        return view.isShown() && view.getAlpha() > 0f && view.getWidth() > 0 && view.getHeight() > 0;
    }

    private static View actionOwner(View image, View toolbar) {
        for (View view = image; view != null && view != toolbar; view = parent(view)) {
            if (view.getWidth() > toolbar.getWidth() * .32f) return null;
            if (view.isClickable() || view.isLongClickable()) return view;
        }
        return null;
    }

    private static View parent(View view) {
        return view.getParent() instanceof View ? (View) view.getParent() : null;
    }

    private static boolean contains(View root, View view) {
        for (View current = view; current != null; current = parent(current)) if (current == root) return true;
        return false;
    }

    private static boolean isOpaque(Drawable d) {
        if (d == null) return false;
        if (d instanceof ColorDrawable) return Color.alpha(((ColorDrawable) d).getColor()) >= 128;
        return d.getOpacity() != PixelFormat.TRANSPARENT;
    }

    private static boolean barIsDark(Drawable containerBg, Drawable toolbarBg, Context context) {
        Drawable bg = isOpaque(toolbarBg) ? toolbarBg : (isOpaque(containerBg) ? containerBg : null);
        if (bg instanceof ColorDrawable) {
            int c = ((ColorDrawable) bg).getColor();
            int r = (c >>> 16) & 0xFF, g = (c >>> 8) & 0xFF, b = c & 0xFF;
            float luma = (0.299f * r + 0.587f * g + 0.114f * b) / 255f;
            return luma < 0.45f;
        }
        return SettingsTheme.isDark(context);
    }

    private static final class ClipState {
        final ViewGroup view; final boolean children, padding;
        ClipState(ViewGroup view) {
            this.view = view;
            children = view.getClipChildren();
            padding = view.getClipToPadding();
        }
        void release() {
            if (view.getClipChildren()) view.setClipChildren(false);
            if (view.getClipToPadding()) view.setClipToPadding(false);
        }
        void restore() {
            if (!view.getClipChildren() && children) view.setClipChildren(true);
            if (!view.getClipToPadding() && padding) view.setClipToPadding(true);
        }
    }

    static final class IconShift {
        final View icon;
        float originalTx, appliedTx, originalTy, appliedTy;
        final List<ClipState> clips = new ArrayList<>();

        IconShift(View icon, View toolbar) {
            this.icon = icon;
            originalTx = appliedTx = icon.getTranslationX();
            originalTy = appliedTy = icon.getTranslationY();
            for (View current = parent(icon); current != null && current != toolbar; current = parent(current)) {
                if (current instanceof ViewGroup) {
                    ClipState state = new ClipState((ViewGroup) current);
                    state.release();
                    clips.add(state);
                }
            }
        }

        float deltaX() {
            float cur = icon.getTranslationX();
            if (cur != appliedTx) { originalTx = cur; appliedTx = cur; }
            return appliedTx - originalTx;
        }

        float deltaY() {
            float cur = icon.getTranslationY();
            if (cur != appliedTy) { originalTy = cur; appliedTy = cur; }
            return appliedTy - originalTy;
        }

        void align(View toolbar, float targetX, float targetY, int[] origin, int[] at) {
            for (ClipState state : clips) state.release();
            toolbar.getLocationInWindow(origin);
            icon.getLocationInWindow(at);
            float visX = at[0] - origin[0] + icon.getPaddingLeft()
                    + (icon.getWidth() - icon.getPaddingLeft() - icon.getPaddingRight()) / 2f;
            float visY = at[1] - origin[1] + icon.getPaddingTop()
                    + (icon.getHeight() - icon.getPaddingTop() - icon.getPaddingBottom()) / 2f;
            float curTx = icon.getTranslationX();
            if (curTx != appliedTx) originalTx = curTx;
            float dx = targetX - visX;
            if (Math.abs(dx) > 0.25f) {
                float nextTx = curTx + dx;
                icon.setTranslationX(nextTx);
                appliedTx = nextTx;
            } else {
                appliedTx = curTx;
            }
            float curTy = icon.getTranslationY();
            if (curTy != appliedTy) originalTy = curTy;
            float dy = targetY - visY;
            if (Math.abs(dy) > 0.25f) {
                float nextTy = curTy + dy;
                icon.setTranslationY(nextTy);
                appliedTy = nextTy;
            } else {
                appliedTy = curTy;
            }
        }

        void restore() {
            if (Math.abs(icon.getTranslationX() - appliedTx) < 0.1f) icon.setTranslationX(originalTx);
            if (Math.abs(icon.getTranslationY() - appliedTy) < 0.1f) icon.setTranslationY(originalTy);
            for (ClipState state : clips) state.restore();
            clips.clear();
        }
    }

    static final class Dressing extends Drawable {
        final View toolbar, container, content;
        final ProgressiveTopBlur band = new ProgressiveTopBlur();
        final ChatGlassSurface glass, bandBackdrop;
        final List<LiquidGlassDecoration> shadows = new ArrayList<>();
        final List<RectF> buttons = new ArrayList<>();
        final List<ClipState> clips = new ArrayList<>();
        final WeakHashMap<View, IconShift> shifts = new WeakHashMap<>();
        final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF strokeRect = new RectF();
        final Rect dirty = new Rect();
        final int[] origin = new int[2], at = new int[2];
        Drawable toolbarBackground, containerBackground;
        int top, bottom;
        boolean active, overlay;

        Dressing(View toolbar, View content) {
            this.toolbar = toolbar;
            this.content = content != null && !contains(content, toolbar) ? content : null;
            container = parent(toolbar);
            glass = new ChatGlassSurface(toolbar);
            bandBackdrop = new ChatGlassSurface(toolbar);
            float density = density();
            bandBackdrop.configure(0, 0f, density, false, ChatGlassSurface.FROSTED, 0);
            if (this.content != null) {
                glass.backdrop(this.content, this.content);
                bandBackdrop.backdrop(this.content, this.content);
            }
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(Math.max(1f, density));
            for (View view = toolbar; view instanceof ViewGroup && clips.size() < 10; view = parent(view)) {
                ClipState state = new ClipState((ViewGroup) view);
                state.release();
                clips.add(state);
            }
        }

        private boolean contentCoversToolbar(View content, View toolbar) {
            if (content == null || contains(content, toolbar)) return false;
            if (content.getWidth() < toolbar.getWidth()) return false;
            content.getLocationInWindow(at);
            toolbar.getLocationInWindow(origin);
            int contentTop = at[1] + content.getPaddingTop();
            int contentBottom = at[1] + content.getHeight() - content.getPaddingBottom();
            int toolbarTop = origin[1];
            int toolbarBottom = origin[1] + toolbar.getHeight();
            return contentTop <= toolbarTop && contentBottom >= toolbarBottom;
        }

        void update(List<View> icons) {
            if (toolbar.getBackground() != this) {
                toolbarBackground = toolbar.getBackground();
                if (toolbarBackground != null) toolbarBackground.setBounds(0, 0, toolbar.getWidth(), toolbar.getHeight());
                toolbar.setBackground(this);
            } else if (toolbarBackground != null) {
                toolbarBackground.setBounds(0, 0, toolbar.getWidth(), toolbar.getHeight());
            }
            Drawable currentContainerBg = container == null ? null : container.getBackground();
            if (!active || currentContainerBg != null) containerBackground = currentContainerBg;
            boolean nextOverlay = !isOpaque(containerBackground) && !isOpaque(toolbarBackground)
                    && contentCoversToolbar(content, toolbar);
            if (nextOverlay) {
                if (container != null && container.getBackground() != null) container.setBackground(null);
                if (content != null) bandBackdrop.backdrop(content, content);
            } else {
                if (container != null && container.getBackground() == null && containerBackground != null) {
                    container.setBackground(containerBackground);
                }
                bandBackdrop.stop();
                band.release();
            }

            toolbar.getLocationInWindow(origin);
            int newTop = -origin[1];
            int newBottom = newTop + Math.round((origin[1] + toolbar.getHeight()) * ProgressiveTopBlur.HEIGHT_FACTOR);
            boolean changed = !active || overlay != nextOverlay || newTop != top || newBottom != bottom
                    || buttons.size() != icons.size();
            overlay = nextOverlay;
            top = newTop; bottom = newBottom;

            float density = density();
            float centerY = toolbar.getPaddingTop()
                    + (toolbar.getHeight() - toolbar.getPaddingTop() - toolbar.getPaddingBottom()) / 2f;
            float diameter = Math.max(0f, Math.min(48f * density, toolbar.getHeight() - 8f * density));
            float edge = 10f * density;
            float gap = 4f * density;
            int n = icons.size();
            float[] nativeX = new float[n];
            for (int i = 0; i < n; i++) {
                View icon = icons.get(i);
                icon.getLocationInWindow(at);
                IconShift shift = shifts.get(icon);
                float dxApplied = shift == null ? 0f : shift.deltaX();
                nativeX[i] = at[0] - origin[0] + icon.getPaddingLeft()
                        + (icon.getWidth() - icon.getPaddingLeft() - icon.getPaddingRight()) / 2f - dxApplied;
            }
            float r = diameter / 2f;
            if (n > 0) {
                float maxSpan = toolbar.getWidth() - 2f * edge - Math.max(0, n - 1) * gap;
                r = Math.min(r, maxSpan / (2f * n));
            }
            for (int i = 1; i < n; i++) {
                r = Math.min(r, (nativeX[i] - nativeX[i - 1] - gap) / 2f);
            }
            if (r < 12f * density) {
                buttons.clear();
                while (!shadows.isEmpty()) shadows.remove(shadows.size() - 1).release();
                restoreShifts();
            } else {
                float[] cx = new float[n];
                for (int i = 0; i < n; i++) {
                    cx[i] = Math.max(edge + r, Math.min(toolbar.getWidth() - edge - r, nativeX[i]));
                }
                for (int i = n - 2; i >= 0; i--) {
                    cx[i] = Math.min(cx[i], cx[i + 1] - 2f * r - gap);
                }
                if (n > 0 && cx[0] < edge + r) {
                    cx[0] = edge + r;
                    for (int i = 1; i < n; i++) {
                        cx[i] = Math.max(cx[i], cx[i - 1] + 2f * r + gap);
                    }
                }
                Set<View> activeIcons = new HashSet<>();
                for (int i = 0; i < n; i++) {
                    if (i == buttons.size()) {
                        buttons.add(new RectF());
                        shadows.add(new LiquidGlassDecoration());
                    }
                    RectF bounds = buttons.get(i);
                    float left = cx[i] - r, topEdge = centerY - r, right = cx[i] + r, botEdge = centerY + r;
                    if (bounds.left != left || bounds.top != topEdge
                            || bounds.right != right || bounds.bottom != botEdge) changed = true;
                    bounds.set(left, topEdge, right, botEdge);

                    View icon = icons.get(i);
                    activeIcons.add(icon);
                    IconShift shift = shifts.get(icon);
                    if (shift == null) {
                        shift = new IconShift(icon, toolbar);
                        shifts.put(icon, shift);
                    }
                    shift.align(toolbar, cx[i], centerY, origin, at);
                }
                while (buttons.size() > n) {
                    buttons.remove(buttons.size() - 1);
                    shadows.remove(shadows.size() - 1).release();
                }
                for (Iterator<Map.Entry<View, IconShift>> it = shifts.entrySet().iterator(); it.hasNext(); ) {
                    Map.Entry<View, IconShift> entry = it.next();
                    if (!activeIcons.contains(entry.getKey())) {
                        entry.getValue().restore();
                        it.remove();
                    }
                }
            }
            if (overlay) dirty.set(0, top, toolbar.getWidth(), Math.max(toolbar.getHeight(), bottom));
            else dirty.set(0, 0, toolbar.getWidth(), toolbar.getHeight());
            active = true;
            if (changed) invalidateSelf();
        }

        private void restoreShifts() {
            for (IconShift shift : shifts.values()) shift.restore();
            shifts.clear();
        }

        private float density() { return toolbar.getResources().getDisplayMetrics().density; }

        @Override public void draw(Canvas canvas) {
            if (!active) return;
            float density = density();
            if (overlay) {
                boolean dark = SettingsTheme.isDark(toolbar.getContext());
                // When android.R.id.content also contains the toolbar, the sampler finds a
                // sibling scene instead of recording the toolbar back into its own background.
                band.draw(canvas, canvas.isHardwareAccelerated() ? bandBackdrop.sharedSampler() : null,
                        toolbar.getWidth(), top, bottom, density, dark, 1f);
                int material = ChatGlassSurface.mode();
                int blur = ChatGlassSurface.blurRadiusDp(material);
                for (int i = 0; i < buttons.size(); i++) {
                    RectF button = buttons.get(i);
                    if (button.width() < 24f * density) continue;
                    float radius = button.width() / 2f;
                    glass.configure(dark ? 0xFF282828 : 0xFFF7F7F7, radius, density, dark, material, blur);
                    shadows.get(i).dropShadow(canvas, button, radius, density, dark);
                    glass.setBounds(Math.round(button.left), Math.round(button.top), Math.round(button.right), Math.round(button.bottom));
                    glass.draw(canvas);
                }
            } else {
                if (toolbarBackground != null) toolbarBackground.draw(canvas);
                boolean darkBar = barIsDark(containerBackground, toolbarBackground, toolbar.getContext());
                int fill = darkBar ? 0xFF282828 : 0xFFF7F7F7;
                strokePaint.setColor(darkBar ? 0x26FFFFFF : 0xB8FFFFFF);
                float halfStroke = strokePaint.getStrokeWidth() / 2f;
                for (int i = 0; i < buttons.size(); i++) {
                    RectF button = buttons.get(i);
                    if (button.width() < 24f * density) continue;
                    float radius = button.width() / 2f;
                    shadows.get(i).dropShadow(canvas, button, radius, density, darkBar);
                    glass.configure(fill, radius, density, darkBar, ChatGlassSurface.PLAIN, 0);
                    glass.setBounds(Math.round(button.left), Math.round(button.top), Math.round(button.right), Math.round(button.bottom));
                    glass.draw(canvas);
                    strokeRect.set(button.left + halfStroke, button.top + halfStroke,
                            button.right - halfStroke, button.bottom - halfStroke);
                    float strokeRadius = Math.max(0f, radius - halfStroke);
                    canvas.drawRoundRect(strokeRect, strokeRadius, strokeRadius, strokePaint);
                }
            }
        }

        void dispose() {
            if (toolbar.getBackground() == this) toolbar.setBackground(toolbarBackground);
            if (active && container != null && container.getBackground() == null && containerBackground != null) {
                container.setBackground(containerBackground);
            }
            restoreShifts();
            for (ClipState clip : clips) clip.restore();
            clips.clear();
            bandBackdrop.stop();
            glass.stop();
            band.release();
            for (LiquidGlassDecoration shadow : shadows) shadow.release();
            active = false;
            overlay = false;
        }

        @Override protected void onBoundsChange(Rect bounds) {
            if (toolbarBackground != null) toolbarBackground.setBounds(bounds);
        }
        @Override public Rect getDirtyBounds() { return dirty; }
        @Override public void getOutline(Outline outline) { outline.setEmpty(); }
        @Override public void setAlpha(int alpha) { }
        @Override public void setColorFilter(ColorFilter filter) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
