package wex.ui.core.feature;

import android.app.Activity;
import android.graphics.Color;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;

import java.util.WeakHashMap;

/** Directional ownership for chat edge-to-edge; top and bottom features can be toggled independently. */
final class ChatWindowInsetsCoordinator {
    static final int TOP = 1, BOTTOM = 2;
    private static final int LAYOUT_FLAGS = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
    private static final WeakHashMap<Window, State> STATES = new WeakHashMap<>();

    private ChatWindowInsetsCoordinator() { }

    @SuppressWarnings("deprecation")
    static synchronized Window acquire(Activity activity, int owner) {
        if (activity == null || activity.getWindow() == null) return null;
        Window window = activity.getWindow();
        State state = STATES.get(window);
        if (state == null) { state = new State(window, activity); STATES.put(window, state); }
        boolean newTop = (owner & TOP) != 0 && (state.owners & TOP) == 0;
        state.owners |= owner;
        window.setDecorFitsSystemWindows(false);
        state.lastLayout = window.getDecorView().getSystemUiVisibility() & LAYOUT_FLAGS;
        WindowInsetsController controller = window.getInsetsController();
        if ((owner & TOP) != 0) {
            View decor = window.getDecorView();
            if (newTop) {
                state.statusColor = window.getStatusBarColor();
                state.appearance = controller == null ? 0 : controller.getSystemBarsAppearance();
                state.statusLegacy = decor.getSystemUiVisibility() & View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            }
            window.setStatusBarColor(Color.TRANSPARENT);
            int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS;
            int desired = SettingsTheme.isDark(activity) ? 0 : mask;
            state.lastAppearance = desired;
            state.lastStatusLegacy = desired == 0 ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            int flags = decor.getSystemUiVisibility();
            if ((flags & View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR) != state.lastStatusLegacy) {
                decor.setSystemUiVisibility((flags & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR) | state.lastStatusLegacy);
            }
            if (controller != null && (controller.getSystemBarsAppearance() & mask) != desired) {
                controller.setSystemBarsAppearance(desired, mask);
            }
        }
        if ((owner & BOTTOM) != 0) {
            SystemBarsCoordinator.refreshBottom(window, activity);
        }
        window.getDecorView().requestApplyInsets();
        return window;
    }

    static synchronized boolean isTopOwned(Window window) {
        State state = STATES.get(window);
        return state != null && (state.owners & TOP) != 0;
    }

    static synchronized boolean isBottomOwned(Window window) {
        State state = STATES.get(window);
        return state != null && (state.owners & BOTTOM) != 0;
    }

    /** User-requested (2026-11-15): once the floating card owns the bottom, the
     * gesture-pill area stays transparent under gesture navigation with no IME.
     * Content extension is handled separately; an uncovered strip shows the
     * window background rather than an opaque page-color bar. */
    static boolean shouldExposeBottom(Window window) {
        if (!isBottomOwned(window)) return false;
        View decor = window.getDecorView();
        WindowInsets insets = decor == null ? null : decor.getRootWindowInsets();
        if (insets == null) return false;
        if (insets.getInsets(WindowInsets.Type.ime()).bottom > 0) return false;
        return insets.getInsets(WindowInsets.Type.tappableElement()).bottom <= 0;
    }

    /** The host may re-fit its window between applies; ownership must re-assert edge-to-edge. */
    static synchronized void reassertBottom(Activity activity) {
        if (activity == null) return;
        Window window = activity.getWindow();
        if (window == null || !isBottomOwned(window)) return;
        window.setDecorFitsSystemWindows(false);
        State state = STATES.get(window);
        state.lastLayout = window.getDecorView().getSystemUiVisibility() & LAYOUT_FLAGS;
        window.getDecorView().requestApplyInsets();
    }

    /** True only when the host re-fitted the window since the last bottom apply. */
    static synchronized boolean needsBottomReassert(Activity activity) {
        if (activity == null) return false;
        Window window = activity.getWindow();
        if (window == null || !isBottomOwned(window)) return false;
        State state = STATES.get(window);
        if (state == null) return false;
        return (window.getDecorView().getSystemUiVisibility() & LAYOUT_FLAGS) != state.lastLayout;
    }

    @SuppressWarnings("deprecation")
    static synchronized void release(Window window, int owner) {
        if (window == null) return;
        State state = STATES.get(window);
        if (state == null || (state.owners & owner) == 0) return;
        state.owners &= ~owner;
        WindowInsetsController controller = window.getInsetsController();
        int statusMask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS;
        int currentStatus = controller == null ? 0 : controller.getSystemBarsAppearance() & statusMask;
        int restoredStatus = currentStatus == state.lastAppearance ? state.appearance & statusMask : currentStatus;
        if ((owner & TOP) != 0 && (state.owners & TOP) == 0) {
            if (window.getStatusBarColor() == Color.TRANSPARENT) window.setStatusBarColor(state.statusColor);
            View decor = window.getDecorView();
            int flags = decor.getSystemUiVisibility();
            int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS;
            int currentAppearance = controller == null ? 0 : controller.getSystemBarsAppearance() & mask;
            int restoredAppearance = currentAppearance == state.lastAppearance ? state.appearance & mask : currentAppearance;
            if ((flags & View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR) == state.lastStatusLegacy) {
                decor.setSystemUiVisibility((flags & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR) | state.statusLegacy);
            }
            // A legacy write may synchronously change the controller. Decide ownership before it.
            if (controller != null && (controller.getSystemBarsAppearance() & mask) != restoredAppearance) {
                controller.setSystemBarsAppearance(restoredAppearance, mask);
            }
        }
        if ((owner & BOTTOM) != 0 && (state.owners & BOTTOM) == 0) SystemBarsCoordinator.restore(window);
        if (state.owners == 0) {
            View decor = window.getDecorView();
            int flags = decor.getSystemUiVisibility();
            if ((flags & LAYOUT_FLAGS) == state.lastLayout) {
                boolean originallyFit = (state.systemUiVisibility & LAYOUT_FLAGS) == 0;
                window.setDecorFitsSystemWindows(originallyFit);
                // setDecorFits changes only layout ownership; never replay stale host glyph/other flags.
                decor.setSystemUiVisibility((flags & ~LAYOUT_FLAGS) | (state.systemUiVisibility & LAYOUT_FLAGS));
                decor.requestApplyInsets();
            }
            STATES.remove(window);
        }
        // The final layout-only legacy call can also update the controller synchronously.
        int finalStatus = (owner & TOP) != 0 ? restoredStatus : currentStatus;
        if (controller != null && (controller.getSystemBarsAppearance() & statusMask) != finalStatus) {
            controller.setSystemBarsAppearance(finalStatus, statusMask);
        }
        if ((owner & TOP) != 0) SystemBarsCoordinator.refreshHome(state.activity.get());
    }

    private static final class State {
        final int systemUiVisibility;
        final java.lang.ref.WeakReference<Activity> activity;
        int statusColor, appearance, statusLegacy, lastAppearance, lastStatusLegacy, lastLayout;
        int owners;

        @SuppressWarnings("deprecation")
        State(Window window, Activity activity) {
            this.activity = new java.lang.ref.WeakReference<>(activity);
            systemUiVisibility = window.getDecorView().getSystemUiVisibility();
        }
    }
}
