package wex.ui.core.feature;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Outline;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowInsetsController;
import android.widget.AbsListView;
import android.widget.ImageView;
import android.widget.TextView;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

import dev.mindlink.xposed.XC_MethodHook;
import dev.mindlink.xposed.XposedBridge;
import dev.mindlink.xposed.XposedHelpers;
import wex.ui.core.WexStore;

/** Dresses the native Moments actions without replacing their click/long-click listeners. */
final class MomentsTopBarCards {
    static final String KEY_ENABLED = "top_moments_cards";
    static final String KEY_PROGRESSIVE = "top_moments_progressive_blur";
    private static final String[] ACTIVITIES = {
            "com.tencent.mm.plugin.sns.ui.improve.ImproveSnsTimelineUI",
            "com.tencent.mm.plugin.sns.ui.SnsTimeLineUI"
    };
    private static final String TOOLBAR = "androidx.appcompat.widget.Toolbar";
    private static final String ACTION_BAR = "androidx.appcompat.widget.ActionBarContainer";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final WeakHashMap<Activity, Session> SESSIONS = new WeakHashMap<>();
    private static final WeakHashMap<Activity, NativeFrost> FROST = new WeakHashMap<>();
    private static final Set<Activity> RESUMED = java.util.Collections.newSetFromMap(new WeakHashMap<>());
    private static boolean initialized, enabled = true, progressive = true;
    private static int material, blur;

    private MomentsTopBarCards() { }

    static synchronized void init(ClassLoader loader) {
        if (initialized) return;
        initialized = true;
        readConfig();
        Set<Method> hooked = new HashSet<>();
        for (String name : ACTIVITIES) {
            try {
                Class<?> type = Class.forName(name, false, loader);
                hookLifecycle(type, "onResume", hooked, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        if (param.hasThrowable() || !supported(param.thisObject)) return;
                        Activity activity = (Activity) param.thisObject;
                        RESUMED.add(activity);
                        MAIN.post(() -> { readConfig(); attach(activity, 0); });
                    }
                });
                hookLifecycle(type, "onPause", hooked, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        if (!supported(param.thisObject)) return;
                        Activity activity = (Activity) param.thisObject;
                        RESUMED.remove(activity);
                        release(activity);
                    }
                });
                hookLifecycle(type, "onDestroy", hooked, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        if (!supported(param.thisObject)) return;
                        Activity activity = (Activity) param.thisObject;
                        RESUMED.remove(activity);
                        release(activity);
                        FROST.remove(activity);
                    }
                });
                // Both scrolling and the unread-header path call this API. Do not hook the
                // obfuscated scroll listener: it misses non-scroll changes and varies by version.
                for (Class<?> current = type; current != null && current != Activity.class; current = current.getSuperclass()) {
                    for (Method method : current.getDeclaredMethods()) {
                        Class<?>[] parameters = method.getParameterTypes();
                        if (!"setActionbarFrosted".equals(method.getName()) || parameters.length == 0
                                || parameters[0] != boolean.class || !hooked.add(method)) continue;
                        method.setAccessible(true);
                        XposedBridge.hookMethod(method, new XC_MethodHook() {
                            @Override protected void beforeHookedMethod(MethodHookParam param) {
                                if (!supported(param.thisObject)) return;
                                Activity activity = (Activity) param.thisObject;
                                NativeFrost state = frost(activity);
                                if (state.internal) return;
                                if (state.depth++ == 0) state.requested = param.args.clone();
                                if (SESSIONS.containsKey(activity)) param.args[0] = false;
                            }
                            @Override protected void afterHookedMethod(MethodHookParam param) {
                                if (!supported(param.thisObject)) return;
                                NativeFrost state = frost((Activity) param.thisObject);
                                if (!state.internal && state.depth > 0) state.depth--;
                            }
                        });
                    }
                }
            } catch (ClassNotFoundException ignored) {
                // The old timeline is absent (or only a shell) in some host versions.
            } catch (ReflectiveOperationException | LinkageError error) {
                log("入口监听失败 " + error.getClass().getSimpleName());
            }
        }
    }

    private static void hookLifecycle(Class<?> type, String name, Set<Method> hooked, XC_MethodHook hook)
            throws NoSuchMethodException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Method method = current.getDeclaredMethod(name);
                if (hooked.add(method)) { method.setAccessible(true); XposedBridge.hookMethod(method, hook); }
                return;
            } catch (NoSuchMethodException ignored) { }
        }
        throw new NoSuchMethodException(name);
    }

    private static boolean supported(Object object) {
        if (!(object instanceof Activity)) return false;
        for (String name : ACTIVITIES) if (is(object.getClass(), name)) return true;
        return false;
    }

    private static boolean is(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass())
            if (name.equals(current.getName())) return true;
        return false;
    }

    static void refresh() {
        MAIN.post(() -> {
            readConfig();
            for (Activity activity : new ArrayList<>(RESUMED)) attach(activity, 0);
        });
    }

    private static void readConfig() {
        enabled = WexStore.INSTANCE.getBool(KEY_ENABLED, true);
        progressive = WexStore.INSTANCE.getBool(KEY_PROGRESSIVE, true);
        material = ChatGlassSurface.mode();
        blur = ChatGlassSurface.blurRadiusDp(material);
    }

    private static void attach(Activity activity, int attempt) {
        if (!RESUMED.contains(activity) || activity.isFinishing() || activity.isDestroyed()) return;
        if (!enabled) { release(activity); return; }
        Window window = activity.getWindow();
        if (window == null) return;
        View toolbar = findToolbar(window.getDecorView());
        Session previous = SESSIONS.get(activity);
        if (previous != null && previous.toolbar == toolbar && toolbar.isAttachedToWindow()) {
            previous.configure(); previous.sync(); return;
        }
        release(activity);
        if (toolbar == null || toolbar.getWidth() <= 0 || toolbar.getHeight() <= 0
                || actions(toolbar) == null) {
            if (attempt < 8) MAIN.postDelayed(() -> attach(activity, attempt + 1), 150L);
            return;
        }
        Session session = new Session(activity, toolbar);
        SESSIONS.put(activity, session);
        NativeFrost nativeFrost = frost(activity);
        // Preserve the host's latest requested frosted state for disable/pause restoration.
        nativeFrost.call(activity, new Object[]{false});
        session.configure();
        session.sync();
        log("已启用 " + activity.getClass().getSimpleName());
    }

    private static void release(Activity activity) {
        Session session = SESSIONS.remove(activity);
        if (session != null) {
            session.dispose();
            NativeFrost state = FROST.get(activity);
            if (state != null && state.requested != null) state.call(activity, state.requested);
        }
    }

    private static NativeFrost frost(Activity activity) {
        NativeFrost state = FROST.get(activity);
        if (state == null) { state = new NativeFrost(); FROST.put(activity, state); }
        return state;
    }

    private static final class NativeFrost {
        Object[] requested;
        int depth;
        boolean internal;
        void call(Activity activity, Object[] args) {
            internal = true;
            try { XposedHelpers.callMethod(activity, "setActionbarFrosted", args); }
            catch (RuntimeException | LinkageError ignored) { }
            finally { internal = false; }
        }
    }

    static View findToolbar(View root) {
        ArrayDeque<View> queue = new ArrayDeque<>(); queue.add(root);
        View found = null;
        for (int visited = 0; !queue.isEmpty() && visited < 256; visited++) {
            View view = queue.removeFirst();
            if (is(view.getClass(), TOOLBAR) && view.getParent() instanceof View
                    && is(view.getParent().getClass(), ACTION_BAR)) {
                if (found != null) return null;
                found = view;
                continue;
            }
            if (view instanceof AbsListView || is(view.getClass(), "androidx.recyclerview.widget.RecyclerView")) continue;
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) queue.addLast(group.getChildAt(i));
            }
        }
        return found;
    }

    /** Stable back resource + the native right-hand image action, including its long-click. */
    static View[] actions(View toolbar) {
        int backId = toolbar.getResources().getIdentifier("actionbar_up_indicator", "id", "com.tencent.mm");
        View back = backId == 0 ? null : toolbar.findViewById(backId);
        if (back == null || !back.isShown() || !back.isClickable()) return null;
        View camera = null;
        int[] origin = new int[2], at = new int[2];
        toolbar.getLocationInWindow(origin);
        ArrayDeque<View> queue = new ArrayDeque<>(); queue.add(toolbar);
        for (int visited = 0; !queue.isEmpty() && visited < 128; visited++) {
            View view = queue.removeFirst();
            if (view == back || !view.isShown()) continue;
            if (view instanceof ImageView && view.isClickable() && view.getWidth() > 0
                    && view.getWidth() < toolbar.getWidth() * 0.35f && ((ImageView) view).getDrawable() != null) {
                view.getLocationInWindow(at);
                if (at[0] - origin[0] + view.getWidth() / 2f > toolbar.getWidth() * 0.65f) {
                    if (camera != null) return null; // no guessed target when the menu is ambiguous
                    camera = view;
                }
            }
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) queue.addLast(group.getChildAt(i));
            }
        }
        return camera == null ? null : new View[]{back, camera};
    }

    static final class Session implements ViewTreeObserver.OnPreDrawListener {
        final Activity activity;
        final View toolbar, bar;
        final Cards cards;
        final List<Clip> clips = new ArrayList<>();
        final WeakHashMap<ImageView, Icon> icons = new WeakHashMap<>();
        final ViewTreeObserver observer;
        Drawable toolbarBackground, barBackground;
        Move leftMove, rightMove;
        TextView title;
        ColorStateList titleColors, ownedTitleColors;
        int statusColor, statusAppearance;
        boolean statusOwned, appearanceOwned, disposed;

        Session(Activity activity, View toolbar) {
            this.activity = activity; this.toolbar = toolbar; this.bar = (View) toolbar.getParent();
            toolbarBackground = toolbar.getBackground(); barBackground = bar.getBackground();
            cards = new Cards(toolbar, activity.findViewById(android.R.id.content));
            for (View current = toolbar; current.getParent() instanceof ViewGroup && clips.size() < 3;) {
                ViewGroup group = (ViewGroup) current.getParent();
                clips.add(new Clip(group)); current = group;
            }
            observer = toolbar.getViewTreeObserver();
            observer.addOnPreDrawListener(this);
        }

        void configure() { cards.configure(material, blur, progressive); }

        @Override public boolean onPreDraw() {
            if (!disposed) sync();
            return true;
        }

        void sync() {
            if (disposed || !toolbar.isShown() || !toolbar.isAttachedToWindow()) return;
            View[] targets = actions(toolbar);
            if (targets == null) { cards.left.setEmpty(); cards.right.setEmpty(); return; }
            if (leftMove == null || leftMove.view != targets[0] || rightMove.view != targets[1]) {
                if (leftMove != null) { leftMove.restore(); rightMove.restore(); }
                leftMove = new Move(targets[0]); rightMove = new Move(targets[1]);
            }
            if (toolbar.getBackground() != cards) {
                toolbarBackground = toolbar.getBackground(); toolbar.setBackground(cards); toolbar.invalidateOutline();
            }
            if (bar.getBackground() != null) { barBackground = bar.getBackground(); bar.setBackground(null); }
            for (Clip clip : clips) clip.apply();
            boolean dark = SettingsTheme.isDark(toolbar.getContext());
            int ink = dark ? 0xFFF2F2F2 : 0xFF191919;
            tint(targets[0], ink, false); tint(targets[1], ink, true);
            View text = toolbar.findViewById(android.R.id.text1);
            if (text instanceof TextView) {
                if (title != text) { restoreTitle(); title = (TextView) text; }
                if (title.getTextColors() != ownedTitleColors) titleColors = title.getTextColors();
                if (ownedTitleColors == null || ownedTitleColors.getDefaultColor() != ink) ownedTitleColors = ColorStateList.valueOf(ink);
                if (title.getTextColors() != ownedTitleColors) title.setTextColor(ownedTitleColors);
                // Native title/ancestor alpha is intentionally preserved in both scroll states.
            }
            cards.layout(leftMove, rightMove);
            status(dark);
        }

        private void tint(View root, int ink, boolean camera) {
            ArrayDeque<View> queue = new ArrayDeque<>(); queue.add(root);
            for (int visited = 0; !queue.isEmpty() && visited < 24; visited++) {
                View view = queue.removeFirst();
                if (view instanceof ImageView) {
                    ImageView image = (ImageView) view;
                    Icon state = icons.get(image);
                    if (state == null) { state = new Icon(image, camera && image == root); icons.put(image, state); }
                    state.apply(ink);
                }
                if (view instanceof ViewGroup) {
                    ViewGroup group = (ViewGroup) view;
                    for (int i = 0; i < group.getChildCount(); i++) queue.addLast(group.getChildAt(i));
                }
            }
        }

        private void status(boolean dark) {
            Window window = activity.getWindow();
            if (!statusOwned || window.getStatusBarColor() != Color.TRANSPARENT) statusColor = window.getStatusBarColor();
            statusOwned = true;
            if (window.getStatusBarColor() != Color.TRANSPARENT) window.setStatusBarColor(Color.TRANSPARENT);
            WindowInsetsController controller = window.getInsetsController();
            if (controller == null) return;
            int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS;
            int wanted = dark ? 0 : mask;
            int current = controller.getSystemBarsAppearance() & mask;
            if (!cards.progressive) { restoreAppearance(); return; }
            if (!appearanceOwned || current != wanted) statusAppearance = current;
            appearanceOwned = true;
            if (current != wanted) controller.setSystemBarsAppearance(wanted, mask);
        }

        private void restoreAppearance() {
            WindowInsetsController controller = activity.getWindow().getInsetsController();
            if (appearanceOwned && controller != null) {
                int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS;
                int owned = SettingsTheme.isDark(toolbar.getContext()) ? 0 : mask;
                if ((controller.getSystemBarsAppearance() & mask) == owned) controller.setSystemBarsAppearance(statusAppearance, mask);
            }
            appearanceOwned = false;
        }

        private void restoreTitle() {
            if (title != null && title.getTextColors() == ownedTitleColors) title.setTextColor(titleColors);
            title = null; titleColors = ownedTitleColors = null;
        }

        void dispose() {
            if (disposed) return;
            disposed = true;
            if (observer.isAlive()) observer.removeOnPreDrawListener(this);
            if (toolbar.getBackground() == cards) { toolbar.setBackground(toolbarBackground); toolbar.invalidateOutline(); }
            if (bar.getBackground() == null) bar.setBackground(barBackground);
            if (leftMove != null) { leftMove.restore(); rightMove.restore(); }
            for (Icon icon : icons.values()) icon.restore();
            icons.clear(); restoreTitle();
            for (Clip clip : clips) clip.restore();
            if (statusOwned && activity.getWindow().getStatusBarColor() == Color.TRANSPARENT)
                activity.getWindow().setStatusBarColor(statusColor);
            restoreAppearance(); cards.stop();
        }
    }

    static final class Move {
        final View view;
        float original, applied;
        Move(View view) { this.view = view; original = applied = view.getTranslationX(); }
        void center(float center, float current) {
            if (view.getTranslationX() != applied) original = view.getTranslationX();
            applied = view.getTranslationX() + center - current;
            if (Math.abs(view.getTranslationX() - applied) > 0.1f) view.setTranslationX(applied);
        }
        void restore() { if (Math.abs(view.getTranslationX() - applied) < 0.1f) view.setTranslationX(original); }
    }

    private static final class Clip {
        final ViewGroup view;
        final boolean children, padding;
        Clip(ViewGroup view) { this.view = view; children = view.getClipChildren(); padding = view.getClipToPadding(); }
        void apply() { if (view.getClipChildren()) view.setClipChildren(false); if (view.getClipToPadding()) view.setClipToPadding(false); }
        void restore() { if (children) view.setClipChildren(true); if (padding) view.setClipToPadding(true); }
    }

    private static final class Icon {
        final ImageView view;
        final boolean camera;
        ColorFilter original, owned;
        int color;
        Method getIconColor, setIconColor;
        Drawable lastDrawable;
        int tintFrames;
        int nativeColor, appliedColor;
        boolean nativeOwned;
        final Drawable emptyCamera = new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT);
        Drawable nativeCamera;
        Icon(ImageView view, boolean camera) {
            this.view = view; original = view.getColorFilter();
            this.camera = camera;
            if (is(view.getClass(), "com.tencent.mm.ui.widget.imageview.WeImageView")) {
                try {
                    getIconColor = view.getClass().getMethod("getIconColor");
                    setIconColor = view.getClass().getMethod("setIconColor", int.class);
                } catch (ReflectiveOperationException ignored) { getIconColor = setIconColor = null; }
            }
        }
        void apply(int ink) {
            if (camera) {
                // Keep the native hit target/description/listeners while Cards draws the
                // camera glyph. Host SVG and image tint changes cannot recolor that glyph.
                // Keep View alpha/visibility intact, including accessibility visibility.
                if (view.getDrawable() != emptyCamera) {
                    nativeCamera = view.getDrawable(); view.setImageDrawable(emptyCamera);
                }
                return;
            }
            boolean hostChanged = view.getColorFilter() != owned;
            // WeImageView.onDraw writes its own Drawable filter after super.onDraw. Keep its
            // native color in sync too; ImageView.getColorFilter cannot observe that write.
            if (getIconColor != null) {
                try {
                    int current = ((Number) getIconColor.invoke(view)).intValue();
                    if (!nativeOwned || current != appliedColor) nativeColor = current;
                    if (current != ink) { setIconColor.invoke(view, ink); hostChanged = true; }
                    nativeOwned = true; appliedColor = ink;
                } catch (ReflectiveOperationException | RuntimeException ignored) { }
            }
            if (view.getColorFilter() != owned) original = view.getColorFilter();
            boolean colorChanged = owned == null || color != ink;
            if (colorChanged) { owned = new PorterDuffColorFilter(ink, PorterDuff.Mode.SRC_IN); color = ink; }
            if (view.getColorFilter() != owned) view.setColorFilter(owned);
            Drawable drawable = view.getDrawable();
            if (drawable != lastDrawable || colorChanged || hostChanged) tintFrames = 2;
            lastDrawable = drawable;
            // The host SVG learns its consumer on its first draw. A pre-draw tint before
            // that only updates its private Paint, leaving the previous icon's layer paint
            // on the ImageView. Reapply once after that draw to update the bound layer too.
            if (drawable != null && tintFrames > 0) {
                drawable.setColorFilter(owned);
                if (--tintFrames > 0) view.postInvalidateOnAnimation();
            }
        }
        void restore() {
            if (camera) {
                if (view.getDrawable() == emptyCamera) view.setImageDrawable(nativeCamera);
                return;
            }
            if (nativeOwned && getIconColor != null) {
                try {
                    if (((Number) getIconColor.invoke(view)).intValue() == appliedColor)
                        setIconColor.invoke(view, nativeColor);
                } catch (ReflectiveOperationException | RuntimeException ignored) { }
            }
            if (view.getColorFilter() == owned) view.setColorFilter(original);
            if (lastDrawable != null && view.getDrawable() == lastDrawable) lastDrawable.setColorFilter(original);
        }
    }

    static final class Cards extends Drawable {
        final View toolbar, content;
        final ChatGlassSurface glass, bandBackdrop;
        final ProgressiveTopBlur band = new ProgressiveTopBlur();
        final LiquidGlassDecoration leftShadow = new LiquidGlassDecoration(), rightShadow = new LiquidGlassDecoration();
        final RectF left = new RectF(), right = new RectF();
        final Rect dirty = new Rect();
        final int[] origin = new int[2], at = new int[2];
        final float density;
        boolean progressive;
        Drawable cameraGlyph;
        int glyphColor;
        int mode, blur, bandTop, bandBottom;
        String configured;

        Cards(View toolbar, View content) {
            this.toolbar = toolbar;
            this.content = content != null && !contains(content, toolbar) ? content : null;
            density = toolbar.getResources().getDisplayMetrics().density;
            glass = new ChatGlassSurface(toolbar); bandBackdrop = new ChatGlassSurface(toolbar);
            if (this.content != null) {
                glass.backdrop(this.content, this.content);
            }
            bandBackdrop.configure(0, 0f, density, false, ChatGlassSurface.FROSTED, 0);
        }

        private static boolean contains(View root, View child) {
            for (View current = child; current != null; current = current.getParent() instanceof View ? (View) current.getParent() : null)
                if (current == root) return true;
            return false;
        }

        void configure(int mode, int blur, boolean progressive) {
            this.mode = mode; this.blur = blur; this.progressive = progressive; configured = null;
            if (!progressive || mode != ChatGlassSurface.PLAIN) bandBackdrop.stop();
            else if (content != null) bandBackdrop.backdrop(content, content);
            if (!progressive) band.release();
            glass.refresh(); bandBackdrop.refresh(); invalidateSelf();
        }

        void layout(Move back, Move camera) {
            float height = toolbar.getHeight() - toolbar.getPaddingTop() - toolbar.getPaddingBottom();
            float diameter = Math.min(48f * density, height - 8f * density);
            if (diameter < 28f * density || toolbar.getWidth() < diameter * 2 + 24f * density) {
                left.setEmpty(); right.setEmpty(); return;
            }
            float y = toolbar.getPaddingTop() + height / 2f, edge = 12f * density;
            left.set(edge, y - diameter / 2f, edge + diameter, y + diameter / 2f);
            right.set(toolbar.getWidth() - edge - diameter, left.top, toolbar.getWidth() - edge, left.bottom);
            toolbar.getLocationInWindow(origin);
            back.view.getLocationInWindow(at);
            back.center(left.centerX(), at[0] - origin[0] + back.view.getWidth() / 2f);
            camera.view.getLocationInWindow(at);
            camera.center(right.centerX(), at[0] - origin[0] + camera.view.getWidth() / 2f);
            bandTop = -origin[1];
            bandBottom = bandTop + Math.round((origin[1] + toolbar.getHeight()) * ProgressiveTopBlur.HEIGHT_FACTOR);
            dirty.set(0, bandTop, toolbar.getWidth(), Math.max(toolbar.getHeight(), bandBottom));
        }

        @Override public void draw(Canvas canvas) {
            if (left.isEmpty() || right.isEmpty()) return;
            boolean dark = SettingsTheme.isDark(toolbar.getContext());
            String signature = mode + ":" + blur + ":" + dark + ":" + left.height();
            if (!signature.equals(configured)) {
                glass.configure(dark ? 0xFF242424 : 0xFFF7F7F7, left.height() / 2f, density, dark, mode, blur);
                configured = signature;
            }
            if (progressive) band.draw(canvas, mode == ChatGlassSurface.PLAIN ? bandBackdrop.sharedSampler() : glass.sharedSampler(),
                    toolbar.getWidth(), bandTop, bandBottom, density, dark, 1f);
            pill(canvas, leftShadow, left, dark); pill(canvas, rightShadow, right, dark);
            int ink = dark ? 0xFFF2F2F2 : 0xFF191919;
            if (cameraGlyph == null || glyphColor != ink) {
                // Render the full 24-unit grid so the stroke beyond the 2..22 path is not
                // clipped at the bitmap edges. Its visible outline stays about 22dp wide.
                cameraGlyph = new android.graphics.drawable.BitmapDrawable(toolbar.getResources(),
                        LucideIcons.bitmap("shoot.png", Math.round(24f * density), ink, 2f));
                glyphColor = ink;
            }
            int half = Math.round(12f * density), x = Math.round(right.centerX()), y = Math.round(right.centerY());
            cameraGlyph.setBounds(x - half, y - half, x + half, y + half);
            cameraGlyph.draw(canvas);
        }

        private void pill(Canvas canvas, LiquidGlassDecoration shadow, RectF rect, boolean dark) {
            shadow.dropShadow(canvas, rect, rect.height() / 2f, density, dark);
            glass.setBounds(Math.round(rect.left), Math.round(rect.top), Math.round(rect.right), Math.round(rect.bottom));
            glass.draw(canvas);
        }

        void stop() { glass.stop(); bandBackdrop.stop(); band.release(); leftShadow.release(); rightShadow.release(); }
        @Override public Rect getDirtyBounds() { return dirty.isEmpty() ? super.getDirtyBounds() : dirty; }
        @Override public void getOutline(Outline outline) { outline.setEmpty(); }
        @Override public void setAlpha(int alpha) { }
        @Override public void setColorFilter(ColorFilter filter) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    private static void log(String text) {
        try { XposedBridge.log("WEX: 朋友圈顶栏卡片: " + text); } catch (Throwable ignored) { }
    }
}
