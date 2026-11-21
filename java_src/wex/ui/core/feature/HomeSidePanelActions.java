package wex.ui.core.feature;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import de.robv.android.xposed.XposedBridge;

/** Side-panel action executor (ported from WeKit HomeSidePanelActions.kt). */
final class HomeSidePanelActions {

    private static final String TAG = "WEX: 侧栏动作: ";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private HomeSidePanelActions() {
    }

    static void run(final Activity activity, HomeSidePanelLayout.ActionKind kind) {
        if (activity == null || kind == null) {
            return;
        }
        if (kind != HomeSidePanelLayout.ActionKind.RESTART_WECHAT
                && kind != HomeSidePanelLayout.ActionKind.FORCE_STOP_WECHAT) {
            HomeSidePanelFeature.close(activity);
        }
        switch (kind) {
            case ADD_FRIEND:
                start(activity, "com.tencent.mm.plugin.subapp.ui.pluginapp.AddMoreFriendsUI");
                break;
            case SCAN:
                start(activity, "com.tencent.mm.plugin.scanner.ui.BaseScanUI");
                break;
            case MOMENTS:
                start(activity, "com.tencent.mm.plugin.sns.ui.improve.ImproveSnsTimelineUI");
                break;
            case WALLET:
                start(activity, "com.tencent.mm.plugin.mall.ui.MallIndexUIv2");
                break;
            case CHANNELS:
                start(activity, "com.tencent.mm.plugin.finder.ui.FinderHomeAffinityUI");
                break;
            case WECHAT_SETTINGS:
                start(activity, "com.tencent.mm.plugin.setting.ui.setting_new.MainSettingsUI");
                break;
            case FAVORITES:
                start(activity, "com.tencent.mm.plugin.fav.ui.FavoriteIndexUI");
                break;
            case WEKIT_SETTINGS:
                startModuleSettings(activity);
                break;
            case RESTART_WECHAT:
                SettingsShell.confirmRestart(activity);
                break;
            case FORCE_STOP_WECHAT:
                ImagePicker.forceStopSelf(activity);
                break;
            case MARK_ALL_READ:
                MarkAllReadFeature.markAllRead(activity);
                break;
            default:
                break;
        }
    }

    private static void start(Activity activity, String className) {
        try {
            Intent intent = new Intent();
            intent.setClassName(activity, className);
            activity.startActivity(intent);
        } catch (ActivityNotFoundException error) {
            toast(activity, "宿主页面不可用");
        } catch (Throwable error) {
            log("跳转失败 " + className + " " + error.getClass().getSimpleName());
        }
    }

    private static void startModuleSettings(Activity activity) {
        // The module no longer ships a standalone desktop activity; open the in-WeChat panel.
        ProfileView.openSettings(activity);
    }

    private static void toast(final Activity activity, final String message) {
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                try {
                    ImagePicker.INSTANCE.toast(activity, message);
                } catch (Throwable ignored) {
                }
            }
        });
    }

    private static void log(String message) {
        try {
            XposedBridge.log(TAG + message);
        } catch (Throwable ignored) {
        }
    }
}
