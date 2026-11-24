package wex.ui.core.feature;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Outline;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
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
import java.util.List;
import java.util.WeakHashMap;

import dev.mindlink.xposed.XC_MethodHook;
import dev.mindlink.xposed.XposedBridge;

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

        private void clearDressing() {
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

    static final class Dressing extends Drawable {
        final View toolbar, container;
        final ProgressiveTopBlur band = new ProgressiveTopBlur();
        final ChatGlassSurface glass, bandBackdrop;
        final List<LiquidGlassDecoration> shadows = new ArrayList<>();
        final List<RectF> buttons = new ArrayList<>();
        final List<ViewGroup> childrenClip = new ArrayList<>(), paddingClip = new ArrayList<>();
        final Rect dirty = new Rect();
        final int[] origin = new int[2], at = new int[2];
        Drawable toolbarBackground, containerBackground;
        int top, bottom;
        boolean active;

        Dressing(View toolbar, View content) {
            this.toolbar = toolbar;
            container = parent(toolbar);
            glass = new ChatGlassSurface(toolbar);
            bandBackdrop = new ChatGlassSurface(toolbar);
            if (content != null && !contains(content, toolbar)) {
                glass.backdrop(content, content);
                bandBackdrop.backdrop(content, content);
            }
            for (View view = container; view instanceof ViewGroup && childrenClip.size() + paddingClip.size() < 8; view = parent(view)) {
                ViewGroup group = (ViewGroup) view;
                if (group.getClipChildren()) { childrenClip.add(group); group.setClipChildren(false); }
                if (group.getClipToPadding()) { paddingClip.add(group); group.setClipToPadding(false); }
            }
        }

        void update(List<View> icons) {
            toolbar.getLocationInWindow(origin);
            int newTop = -origin[1];
            int newBottom = newTop + Math.round((origin[1] + toolbar.getHeight()) * ProgressiveTopBlur.HEIGHT_FACTOR);
            boolean changed = !active || newTop != top || newBottom != bottom || buttons.size() != icons.size();
            top = newTop; bottom = newBottom;
            float density = density();
            float diameter = Math.max(0f, Math.min(48f * density, toolbar.getHeight() - 8f * density));
            for (int i = 0; i < icons.size(); i++) {
                View icon = icons.get(i);
                icon.getLocationInWindow(at);
                float x = at[0] - origin[0] + icon.getWidth() / 2f;
                float y = at[1] - origin[1] + icon.getHeight() / 2f;
                float radius = Math.min(diameter / 2f, Math.min(x, toolbar.getWidth() - x));
                if (i > 0) radius = fitNext(icons.get(i - 1), x, radius, density);
                if (i + 1 < icons.size()) radius = fitNext(icons.get(i + 1), x, radius, density);
                if (i == buttons.size()) {
                    buttons.add(new RectF());
                    shadows.add(new LiquidGlassDecoration());
                }
                RectF bounds = buttons.get(i);
                if (bounds.left != x - radius || bounds.top != y - radius
                        || bounds.right != x + radius || bounds.bottom != y + radius) changed = true;
                bounds.set(x - radius, y - radius, x + radius, y + radius);
            }
            while (buttons.size() > icons.size()) {
                buttons.remove(buttons.size() - 1);
                shadows.remove(shadows.size() - 1).release();
            }
            dirty.set(0, top, toolbar.getWidth(), Math.max(toolbar.getHeight(), bottom));
            if (toolbar.getBackground() != this) {
                toolbarBackground = toolbar.getBackground();
                toolbar.setBackground(this);
            }
            if (!active || container.getBackground() != null) {
                containerBackground = container.getBackground();
                container.setBackground(null);
            }
            active = true;
            if (changed) invalidateSelf();
        }

        private float fitNext(View next, float x, float radius, float density) {
            next.getLocationInWindow(at);
            float other = at[0] - origin[0] + next.getWidth() / 2f;
            return Math.max(0f, Math.min(radius, (Math.abs(other - x) - 4f * density) / 2f));
        }

        private float density() { return toolbar.getResources().getDisplayMetrics().density; }

        @Override public void draw(Canvas canvas) {
            if (!active) return;
            float density = density();
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
        }

        void dispose() {
            if (toolbar.getBackground() == this) toolbar.setBackground(toolbarBackground);
            if (active && container.getBackground() == null) container.setBackground(containerBackground);
            for (ViewGroup group : childrenClip) group.setClipChildren(true);
            for (ViewGroup group : paddingClip) group.setClipToPadding(true);
            bandBackdrop.stop();
            glass.stop();
            band.release();
            for (LiquidGlassDecoration shadow : shadows) shadow.release();
            active = false;
        }

        @Override public Rect getDirtyBounds() { return dirty; }
        @Override public void getOutline(Outline outline) { outline.setEmpty(); }
        @Override public void setAlpha(int alpha) { }
        @Override public void setColorFilter(ColorFilter color) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
