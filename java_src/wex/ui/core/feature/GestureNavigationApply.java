package wex.ui.core.feature;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.view.View;
import android.view.ViewParent;
import android.view.Window;

import dev.mindlink.xposed.XposedBridge;

/**
 * Keeps the gesture-navigation strip transparent for the chat page.
 *
 * The system-bars coordinator keeps ownership of the window colours; this class only owns the
 * EdgeToEdgeWrapperLayout strategy/colour/padding of the chat window, and restores every value it
 * touched when the chat page goes away. Three-button navigation is left untouched.
 */
public final class GestureNavigationApply {
    private static final java.util.WeakHashMap<View, WrapperState> WRAPPERS = new java.util.WeakHashMap<>();
    private static final java.util.WeakHashMap<View, java.lang.ref.WeakReference<View>> CHAT_WRAPPERS =
            new java.util.WeakHashMap<>();

    private GestureNavigationApply() { }

    private static final class WrapperState {
        final java.lang.reflect.Method getStrategy, setStrategy, getColor, setColor;
        final Object originalStrategy, hideStrategy;
        final int originalColor;
        final java.util.WeakHashMap<View, Boolean> owners = new java.util.WeakHashMap<>();
        int originalPadding;
        boolean paddingOwned;
        int logs;

        WrapperState(View wrapper) throws Exception {
            Class<?> type = wrapper.getClass();
            getStrategy = type.getMethod("getNavigationBarStrategy");
            Class<?> strategyType = getStrategy.getReturnType();
            setStrategy = type.getMethod("setNavigationBarStrategy", strategyType);
            getColor = type.getMethod("getNavigationBarBackgroundColor");
            setColor = type.getMethod("setNavigationBarBackgroundColor", int.class);
            originalStrategy = getStrategy.invoke(wrapper);
            Object hide = null;
            for (Object value : strategyType.getEnumConstants()) {
                if ("ALWAYS_HIDE".equals(((Enum<?>) value).name())) hide = value;
            }
            if (hide == null) throw new NoSuchFieldException("ALWAYS_HIDE");
            hideStrategy = hide;
            originalColor = ((Integer) getColor.invoke(wrapper)).intValue();
        }
    }

    private static View wrapperAncestor(View layout) {
        for (View view = layout; view != null; ) {
            if ("com.tencent.mm.ui.widget.EdgeToEdgeWrapperLayout".equals(view.getClass().getName())) return view;
            ViewParent parent = view.getParent();
            view = parent instanceof View ? (View) parent : null;
        }
        return null;
    }

    private static void applyWrapper(View layout, int navigationHeight) throws Exception {
        View wrapper = wrapperAncestor(layout);
        java.lang.ref.WeakReference<View> old = CHAT_WRAPPERS.get(layout);
        if (old != null && old.get() != wrapper) releaseChat(layout);
        if (wrapper == null) return;
        WrapperState state = WRAPPERS.get(wrapper);
        if (state == null) {
            state = new WrapperState(wrapper);
            WRAPPERS.put(wrapper, state);
        }
        state.owners.put(layout, Boolean.TRUE);
        CHAT_WRAPPERS.put(layout, new java.lang.ref.WeakReference<>(wrapper));
        boolean changed = false;
        if (state.getStrategy.invoke(wrapper) != state.hideStrategy) {
            state.setStrategy.invoke(wrapper, state.hideStrategy);
            changed = true;
        }
        if (((Integer) state.getColor.invoke(wrapper)).intValue() != 0) {
            state.setColor.invoke(wrapper, 0);
            wrapper.invalidate();
        }
        // The host derives bottom padding from this strategy; clear only an already-applied exact
        // navigation inset, never application-specific spacing.
        if (navigationHeight > 0 && wrapper.getPaddingBottom() == navigationHeight) {
            if (!state.paddingOwned) state.originalPadding = wrapper.getPaddingBottom();
            state.paddingOwned = true;
            wrapper.setPadding(wrapper.getPaddingLeft(), wrapper.getPaddingTop(),
                    wrapper.getPaddingRight(), 0);
            changed = true;
        }
        if (changed) {
            wrapper.requestApplyInsets();
            if (state.logs++ < 8) {
                log("chat wrapper strategy=" + state.getStrategy.invoke(wrapper)
                        + " navigation=" + navigationHeight + " bottomPadding=" + wrapper.getPaddingBottom());
            }
        }
    }

    public static void releaseChat(View layout) {
        try {
            java.lang.ref.WeakReference<View> ref = CHAT_WRAPPERS.remove(layout);
            View wrapper = ref == null ? null : ref.get();
            WrapperState state = wrapper == null ? null : WRAPPERS.get(wrapper);
            if (state == null) return;
            state.owners.remove(layout);
            if (!state.owners.isEmpty()) return;
            WRAPPERS.remove(wrapper);
            if (state.getStrategy.invoke(wrapper) == state.hideStrategy) {
                state.setStrategy.invoke(wrapper, state.originalStrategy);
            }
            if (((Integer) state.getColor.invoke(wrapper)).intValue() == 0) {
                state.setColor.invoke(wrapper, state.originalColor);
                wrapper.invalidate();
            }
            if (state.paddingOwned && wrapper.getPaddingBottom() == 0) {
                wrapper.setPadding(wrapper.getPaddingLeft(), wrapper.getPaddingTop(),
                        wrapper.getPaddingRight(), state.originalPadding);
            }
            wrapper.requestApplyInsets();
        } catch (Exception error) {
            android.util.Log.w("MindLinkEntry", "wrapper restore failed", error);
        }
    }

    public static void applyChat(View layout, Activity activity) {
        try {
            android.view.WindowInsets insets = layout.getRootWindowInsets();
            if (insets == null) return;
            if (insets.getInsets(android.view.WindowInsets.Type.tappableElement()).bottom != 0) {
                releaseChat(layout);
                return;
            }
            int navigationHeight = insets.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom;
            applyWrapper(layout, navigationHeight);
            Window window = activity.getWindow();
            View decor = window.getDecorView();
            int ui = decor.getSystemUiVisibility();
            if (window.getNavigationBarColor() != 0) window.setNavigationBarColor(0);
            if ((ui & View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION) == 0) {
                decor.setSystemUiVisibility(ui | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
                decor.requestApplyInsets();
            }
        } catch (Exception error) {
            android.util.Log.w("MindLinkEntry", "chat gesture setup failed", error);
        }
    }

    /** Called from the navigation bar's pre-draw pass; runs once per draw and never throws. */
    public static void inspect(View bar) {
        try {
            Context context = bar.getContext();
            for (int i = 0; i < 10 && context instanceof ContextWrapper && !(context instanceof Activity); i++) {
                context = ((ContextWrapper) context).getBaseContext();
            }
            if (!(context instanceof Activity)) return;
            Window window = ((Activity) context).getWindow();
            java.lang.reflect.Field suspended = findField(bar.getClass(), "suspended");
            if (suspended == null) return;
            suspended.setAccessible(true);
            if (suspended.getBoolean(bar) || !"com.tencent.mm.ui.LauncherUI".equals(context.getClass().getName())) return;
            android.view.WindowInsets insets = bar.getRootWindowInsets();
            if (insets == null) return;
            if (insets.getInsets(android.view.WindowInsets.Type.tappableElement()).bottom != 0) return;
            View decor = window.getDecorView();
            int ui = decor.getSystemUiVisibility();
            if ((ui & View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION) == 0) {
                decor.setSystemUiVisibility(ui | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
                decor.requestApplyInsets();
            }
        } catch (Throwable ignored) { }
    }

    private static java.lang.reflect.Field findField(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try { return current.getDeclaredField(name); } catch (NoSuchFieldException ignored) { }
        }
        return null;
    }

    private static void log(String message) {
        try { XposedBridge.log("WEX: 手势导航: " + message); } catch (Throwable ignored) { }
    }
}
