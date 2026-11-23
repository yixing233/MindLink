package wex.ui.core.feature;

import android.app.Activity;
import android.app.Dialog;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.reflect.Method;

/**
 * 设置面板的宿主侧外壳。
 *
 * 面板本体是独立的 Material3 Compose 视图（{@link SettingsComposeBridge} → sidepanel 载荷里的
 * SettingsUi）。这里只保留宿主侧必须承担的部分：
 *   - 建面板（build），失败时兜一个可见错误页，避免 Dialog 空白；
 *   - 系统栏背景与图标明暗（windowBackground / applyBarAppearance / bindSettingsBars）；
 *   - 面板按钮触发的宿主动作（重启微信、更新定位、Toast）。
 *
 * 旧的分组 View 树（一级页分组拼装、各张设置卡片）已随 Material3 改版下线，
 * 这里曾是它们唯一的调用路径。
 */
public final class SettingsShell {

    private static final String TAG = "WEX: SettingsShell: ";
    private static final String HELPER = "wex.ui.core.xposed.SettingsPanelHelper";

    private SettingsShell() {
    }

    /**
     * Builds the panel shell: one isolated Material3 Compose view plus the window, insets and
     * change-notification contracts the old View tree used to own.
     */
    public static View build(final Activity activity, final Dialog dialog) {
        final boolean dark = isDark(activity);
        final SettingsChanges.Listener changes = new SettingsChanges.Listener() {
            @Override public void onChanged() { }
            @Override public void onFailure(String message) { toast(activity, message); }
        };
        View root;
        try {
            SettingsPanelModel model = SettingsPanelModel.build(activity);
            root = SettingsComposeBridge.build(activity, new android.os.Bundle(), model.values,
                    model.writers, model.actions, model.readers, dark);
            // 总览 → 详情两级页面：系统返回先退出详情页，在总览时才关闭面板。
            SettingsBack.bind(dialog, root);
        } catch (Throwable error) {
            log("Material3 设置面板加载失败 " + error);
            root = failureView(activity, error);
        }
        root.setBackgroundColor(windowBackground(dark));
        bindSettingsBars(activity, dialog, root, dark);
        // Page-style entry from the right, for every caller of SettingsPanelHelper.show.
        SettingsTransition.prepare(dialog, root, windowBackground(dark));
        // Dialog dismissal does not resume the underlying Activity. Keep its existing
        // dismiss callback and refresh only when this settings root is detached.
        root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) { SettingsChanges.subscribe(changes); }

            @Override public void onViewDetachedFromWindow(View v) {
                SettingsChanges.unsubscribe(changes);
                refreshTopBar(activity);
                // Each show() builds a fresh dialog, so a detached panel is never reused.
                try {
                    SettingsComposeBridge.dispose(v);
                } catch (Throwable error) {
                    log("设置面板释放失败 " + error.getClass().getSimpleName());
                }
            }
        });
        return root;
    }

    private static View failureView(Activity activity, Throwable error) {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        TextView text = new TextView(activity);
        text.setText("设置面板加载失败：" + error.getClass().getSimpleName());
        text.setTextSize(14.0f);
        text.setTextColor(SettingsTheme.sub(activity));
        int pad = dp(activity.getResources().getDisplayMetrics().density, 24);
        text.setPadding(pad, pad, pad, pad);
        box.addView(text);
        return box;
    }

    private static void refreshTopBar(Activity activity) {
        try {
            Class<?> feature = loadModuleClass(activity, "wex.ui.core.feature.TopBarFeature");
            feature.getMethod("access$applyTopBar", feature, Activity.class)
                    .invoke(null, feature.getField("INSTANCE").get(null), activity);
        } catch (Throwable t) {
            log("顶栏刷新失败 " + t.getClass().getSimpleName());
        }
    }

    /**
     * 二次确认后重启微信。
     *
     * 直接点一下就重启太危险（容易误触），这里弹一个系统风格确认框。
     * 确认后结束微信进程，不自动唤起 —— 由用户点图标冷启动。
     */
    public static void confirmRestart(final Activity activity) {
        try {
            SettingsTheme.showDialog(new android.app.AlertDialog.Builder(activity)
                    .setTitle("重启微信")
                    .setMessage("微信会立即退出，请手动重新打开。\n\n设置中的部分功能需要微信重启后才能加载。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("立即重启", new android.content.DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(android.content.DialogInterface d, int which) {
                            restartWechat(activity);
                        }
                    }));
        } catch (Throwable t) {
            log("confirmRestart 失败 " + t.getClass().getSimpleName());
            toast(activity, "无法显示重启确认，未退出微信，请稍后重试");
        }
    }

    static void updateLocation(final Activity activity) {
        if (!LocationPrivacy.isEnabled()) {
            toast(activity, "请先同意并开启联网天气与定位");
            return;
        }
        if (activity.checkSelfPermission("android.permission.ACCESS_FINE_LOCATION") != 0
                && activity.checkSelfPermission("android.permission.ACCESS_COARSE_LOCATION") != 0) {
            toast(activity, "请先在系统设置中授予微信定位权限，再更新定位");
            return;
        }
        try {
            Class<?> helper = loadModuleClass(activity, "wex.ui.core.feature.LocationHelper");
            Class<?> callbackType = Class.forName("w3.e", true, helper.getClassLoader());
            Object callback = java.lang.reflect.Proxy.newProxyInstance(callbackType.getClassLoader(),
                    new Class[]{callbackType}, new java.lang.reflect.InvocationHandler() {
                        @Override
                        public Object invoke(Object proxy, Method method, Object[] args) {
                            if ("invoke".equals(method.getName()) && args != null && args.length == 2) {
                                toast(activity, String.valueOf(args[1]));
                            }
                            return null;
                        }
                    });
            toast(activity, "正在定位...");
            helper.getMethod("updateLocation", Activity.class, callbackType)
                    .invoke(helper.getField("INSTANCE").get(null), activity, callback);
        } catch (Throwable t) {
            log("更新定位失败 " + t);
            toast(activity, "更新定位失败，请查看日志");
        }
    }

    static void toast(Activity activity, String message) {
        try {
            ImagePicker.INSTANCE.toast(activity, message);
        } catch (Throwable ignore) {
        }
    }

    /**
     * 「重启微信」的真实实现：结束微信进程，但**不自动唤起**。
     *
     * 之前调的是 ImagePicker.hotRestartWechat，那个方法只是
     *   finishAndRemoveTask() + 300ms 后 startActivity(LauncherUI)，
     * 进程根本没被杀掉 —— 属于「假重启」：hook 不会重新注册，
     * 改动涉及 hook 注册的功能（如聊天工具栏）看起来「重启了却没生效」。
     *
     * ImagePicker.forceStopSelf 使用公开 API 清理本应用任务，再终止当前进程。
     * 不调用需要系统权限的 forceStopPackage；反射失败时仍保留 killProcess 兜底。
     * 用户确认后自行点微信图标冷启动，hook 会在冷启动时重新注册。
     */
    private static void restartWechat(Activity activity) {
        try {
            Class<?> picker = loadModuleClass(activity, "wex.ui.core.feature.ImagePicker");
            Method m = picker.getDeclaredMethod("forceStopSelf", android.content.Context.class);
            m.setAccessible(true);
            m.invoke(null, activity);
        } catch (Throwable t) {
            log("结束进程失败，回退 killProcess " + t);
            try {
                android.os.Process.killProcess(android.os.Process.myPid());
            } catch (Throwable ignore) {
            }
        }
    }

    /**
     * 找到能加载模块自身类的 ClassLoader。
     *
     * 踩坑记录：不能用 activity.getClassLoader()。宿主 Activity 的 ClassLoader
     * 是微信的 PathClassLoader，而本模块的类由 LSPosed 用独立的 ClassLoader 装载，
     * 两者不是同一个 —— 用宿主的会抛 ClassNotFoundException。
     */
    private static ClassLoader moduleLoader(Activity activity) {
        ClassLoader[] candidates = new ClassLoader[]{
                SettingsShell.class.getClassLoader(),
                SettingsShell.class.getClassLoader() == null ? null
                        : SettingsShell.class.getClassLoader().getParent(),
                activity == null ? null : activity.getClassLoader(),
                Thread.currentThread().getContextClassLoader(),
                ClassLoader.getSystemClassLoader()
        };
        for (ClassLoader cl : candidates) {
            if (cl == null) {
                continue;
            }
            try {
                cl.loadClass(HELPER);
                return cl;
            } catch (Throwable ignore) {
            }
        }
        return SettingsShell.class.getClassLoader();
    }

    /** 按名字加载模块类，逐个 ClassLoader 试。 */
    static Class<?> loadModuleClassOrNull(Activity activity, String name) {
        try {
            return loadModuleClass(activity, name);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Class<?> loadModuleClass(Activity activity, String name) throws ClassNotFoundException {
        ClassLoader[] candidates = new ClassLoader[]{
                moduleLoader(activity),
                SettingsShell.class.getClassLoader(),
                activity == null ? null : activity.getClassLoader(),
                Thread.currentThread().getContextClassLoader()
        };
        for (ClassLoader cl : candidates) {
            if (cl == null) {
                continue;
            }
            try {
                return Class.forName(name, true, cl);
            } catch (Throwable ignore) {
            }
        }
        throw new ClassNotFoundException(name);
    }

    /** 面板窗口背景色（供 SettingsPanelHelper 创建 Dialog 时使用）。 */
    public static int windowBackground(boolean dark) {
        return SettingsTheme.page(dark);
    }

    /** SettingsPanelHelper calls appearance before show; reapply only after this owned root attaches. */
    private static void bindSettingsBars(final Activity activity, final Dialog dialog,
                                         final View root, final boolean dark) {
        if (dialog == null) return;
        final android.view.Window window = dialog.getWindow();
        if (window == null) return;
        final Runnable apply = new Runnable() {
            @Override public void run() {
                if (!root.isAttachedToWindow() || !root.hasWindowFocus() || !dialog.isShowing()
                        || activity.isDestroyed() || activity.isFinishing()
                        || dialog.getWindow() != window) return;
                // The old helper paints black. This full-screen module root supplies the bar surface.
                if (window.getStatusBarColor() != android.graphics.Color.TRANSPARENT) {
                    window.setStatusBarColor(android.graphics.Color.TRANSPARENT);
                }
                applyBarAppearance(window, dark);
            }
        };
        root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            android.view.ViewTreeObserver tree;
            final android.view.ViewTreeObserver.OnWindowFocusChangeListener focus = gained -> {
                if (gained) { root.removeCallbacks(apply); root.post(apply); }
                else root.removeCallbacks(apply);
            };
            @Override public void onViewAttachedToWindow(View view) {
                tree = root.getViewTreeObserver();
                tree.addOnWindowFocusChangeListener(focus);
                root.removeCallbacks(apply);
                root.post(apply);
            }
            @Override public void onViewDetachedFromWindow(View view) {
                root.removeCallbacks(apply);
                if (tree != null && tree.isAlive()) tree.removeOnWindowFocusChangeListener(focus);
                tree = null;
                // This is a dedicated Dialog Window, not the underlying Activity's bars.
            }
        });
    }

    /**
     * 设置系统栏图标的明暗，Android 11+ 使用 WindowInsetsController。
     * 旧 API 是兼容回退；本地代码检查不能证明 OEM 是否忽略它。
     *
     * @param window 面板所在 Dialog 的 Window
     * @param dark   true=深色面板（用浅色图标）；false=浅色面板（用深色图标）
     */
    public static void applyBarAppearance(android.view.Window window, boolean dark) {
        if (window == null) {
            return;
        }
        try {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.view.WindowInsetsController c = window.getInsetsController();
                if (c != null) {
                    int mask = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                            | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                    // 浅色面板要深色图标 → 置位；深色面板要浅色图标 → 清零
                    if ((c.getSystemBarsAppearance() & mask) != (dark ? 0 : mask)) {
                        c.setSystemBarsAppearance(dark ? 0 : mask, mask);
                    }
                }
                return;
            }
            // Android 11 以下回退旧 API
            android.view.View decor = window.getDecorView();
            int v = decor.getSystemUiVisibility();
            if (dark) {
                v &= ~0x2000;
                v &= ~0x10;
            } else {
                v |= 0x2000;
                v |= 0x10;
            }
            if (v != decor.getSystemUiVisibility()) decor.setSystemUiVisibility(v);
        } catch (Throwable t) {
            log("applyBarAppearance 失败 " + t);
        }
    }

    private static boolean isDark(Activity activity) {
        return SettingsTheme.isDark(activity);
    }

    private static int dp(float density, float v) {
        return (int) (v * density + 0.5f);
    }

    private static void log(String msg) {
        try {
            dev.mindlink.xposed.XposedBridge.log(TAG + msg);
        } catch (Throwable ignore) {
        }
    }
}
