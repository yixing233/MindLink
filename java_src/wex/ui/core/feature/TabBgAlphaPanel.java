package wex.ui.core.feature;

import android.app.Activity;

/**
 * 旧「四页背景透明度」滑块面板的占位入口。
 *
 * 滑块界面已经并入 Material3 设置面板（更多 → 全屏壁纸 → 蒙层不透明度），
 * 这里只保留 {@link #show(Activity)} 这一个方法，因为宿主 smali 里
 * {@code TabBgAlphaAction.invoke()} 仍有一条 invoke-static 指令指向它
 * （那条调用链随旧设置面板一起失去入口，不会被执行；留着是为了不产生悬空引用）。
 */
public final class TabBgAlphaPanel {

    private static final String TAG = "WEX: TabBgAlphaPanel: ";

    private TabBgAlphaPanel() {
    }

    public static void show(final Activity activity) {
        if (activity == null) {
            return;
        }
        log("旧四页透明度面板已并入设置面板，忽略调用");
        try {
            ImagePicker.INSTANCE.toast(activity, "该入口已并入「设置 → 更多 → 全屏壁纸」");
        } catch (Throwable ignore) {
        }
    }

    private static void log(String message) {
        try {
            de.robv.android.xposed.XposedBridge.log(TAG + message);
        } catch (Throwable ignore) {
        }
    }
}
