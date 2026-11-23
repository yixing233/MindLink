package wex.ui.core.feature;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.PathInterpolator;

/**
 * Page-style transition for the settings dialog: it slides in from the right edge over the
 * page that opened it, and slides back out to the right on system back.
 *
 * <p>Every entry (the "+" menu, the side panel shortcut, other callers) builds the dialog through
 * {@code SettingsPanelHelper.show → SettingsShell.build}, so the transition lives there. The
 * dialog's own window animation is turned off and its window made translucent while the root
 * moves, so the page underneath stays visible beside it; the root paints its own background.
 * Once in place the window gets its opaque background back.</p>
 */
final class SettingsTransition {
    static final long ENTER_MS = 320L, EXIT_MS = 260L;
    private static final PathInterpolator ENTER = new PathInterpolator(0.2f, 0f, 0f, 1f);
    private static final PathInterpolator EXIT = new PathInterpolator(0.4f, 0f, 1f, 1f);
    private static final int TAG_EXITING = 0x7E000201;

    private SettingsTransition() { }

    /** Called before {@code setContentView/show}; {@code background} is the window's own fill. */
    static void prepare(Dialog dialog, View root, int background) {
        if (dialog == null || root == null) return;
        Window window = dialog.getWindow();
        if (window == null) return;
        try {
            window.setWindowAnimations(0);
            window.setFormat(PixelFormat.TRANSLUCENT);
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        } catch (RuntimeException error) {
            log("窗口过渡准备失败 " + error.getClass().getSimpleName());
            return;
        }
        // Off-screen until the first frame: never one frame of the panel in place before it slides.
        root.setTranslationX(root.getResources().getDisplayMetrics().widthPixels);
        root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            boolean entered;
            @Override public void onViewAttachedToWindow(View view) {
                if (entered) return;
                entered = true;
                view.post(() -> {
                    float from = view.getWidth() > 0 ? view.getWidth() : view.getTranslationX();
                    view.setTranslationX(from);
                    view.animate().translationX(0f).setDuration(ENTER_MS).setInterpolator(ENTER)
                            .setListener(new AnimatorListenerAdapter() {
                                @Override public void onAnimationEnd(Animator animation) {
                                    view.animate().setListener(null);
                                    if (view.getTranslationX() != 0f) view.setTranslationX(0f);
                                    if (dialog.isShowing() && dialog.getWindow() == window)
                                        window.setBackgroundDrawable(new ColorDrawable(background));
                                }
                            }).start();
                });
            }
            @Override public void onViewDetachedFromWindow(View view) { view.animate().cancel(); }
        });
    }

    /** Slides the panel out to the right, then runs {@code close} (the dialog's own back/dismiss). */
    static void exit(Dialog dialog, View root, Runnable close) {
        if (root == null || dialog == null || !root.isAttachedToWindow() || root.getWidth() <= 0) { close.run(); return; }
        if (Boolean.TRUE.equals(root.getTag(TAG_EXITING))) return;
        root.setTag(TAG_EXITING, Boolean.TRUE);
        Window window = dialog.getWindow();
        try { if (window != null) window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT)); }
        catch (RuntimeException ignored) { }
        root.animate().cancel();
        root.animate().translationX(root.getWidth()).setDuration(EXIT_MS).setInterpolator(EXIT)
                .setListener(new AnimatorListenerAdapter() {
                    boolean done;
                    @Override public void onAnimationEnd(Animator animation) { finish(); }
                    @Override public void onAnimationCancel(Animator animation) { finish(); }
                    private void finish() {
                        if (done) return;
                        done = true;
                        root.animate().setListener(null);
                        root.setTag(TAG_EXITING, null);
                        close.run();
                    }
                }).start();
    }

    private static void log(String message) {
        try { dev.mindlink.xposed.XposedBridge.log("WEX: SettingsTransition: " + message); }
        catch (Throwable ignored) { }
    }
}
