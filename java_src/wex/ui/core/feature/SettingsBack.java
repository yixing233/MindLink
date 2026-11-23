package wex.ui.core.feature;

import android.app.Dialog;
import android.view.KeyEvent;
import android.view.View;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

/**
 * 设置面板 Dialog 的系统返回：先交给面板退出详情页，已在总览时才按 Dialog 原有逻辑关闭。
 *
 * 两条路径各管一种宿主：
 *   - 宿主开启了预测性返回时，返回不再派发 KEYCODE_BACK，只走 OnBackInvokedDispatcher；
 *   - 未开启时，应用注册的 OnBackInvokedCallback 会被系统忽略，返回仍以按键送达 Dialog。
 * 所以两者不会同时生效。回调只在面板挂到窗口期间注册，随 Dialog 关闭一起注销。
 */
final class SettingsBack {

    private SettingsBack() {
    }

    static void bind(final Dialog dialog, final View root) {
        if (dialog == null || root == null) return;
        final OnBackInvokedCallback callback = new OnBackInvokedCallback() {
            @Override public void onBackInvoked() { handle(dialog, root); }
        };
        dialog.setOnKeyListener((d, keyCode, event) -> {
            if (keyCode != KeyEvent.KEYCODE_BACK) return false;
            // 按下时就接管，否则 Dialog 会自己开始追踪并在抬起时直接关闭。
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) handle(dialog, root);
            return true;
        });
        root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            private OnBackInvokedDispatcher registered;

            @Override public void onViewAttachedToWindow(View v) {
                if (registered != null) return;
                try {
                    OnBackInvokedDispatcher dispatcher = dialog.getOnBackInvokedDispatcher();
                    // 同优先级后注册者优先，Dialog 自身的回调在 onStart 已注册，这里会盖在它上面。
                    dispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback);
                    registered = dispatcher;
                } catch (Throwable error) {
                    log("返回回调注册失败 " + error.getClass().getSimpleName());
                }
            }

            @Override public void onViewDetachedFromWindow(View v) {
                OnBackInvokedDispatcher dispatcher = registered;
                registered = null;
                if (dispatcher == null) return;
                try {
                    dispatcher.unregisterOnBackInvokedCallback(callback);
                } catch (Throwable ignore) {
                }
            }
        });
    }

    private static void handle(Dialog dialog, View root) {
        boolean consumed;
        try {
            consumed = SettingsComposeBridge.back(root);
        } catch (Throwable error) {
            log("面板返回失败 " + error.getClass().getSimpleName());
            consumed = false;
        }
        // Leaving the panel slides it back out to the right before the dialog's own back runs.
        if (!consumed) SettingsTransition.exit(dialog, root, dialog::onBackPressed);
    }

    private static void log(String message) {
        try {
            dev.mindlink.xposed.XposedBridge.log("WEX: SettingsBack: " + message);
        } catch (Throwable ignore) {
        }
    }
}
