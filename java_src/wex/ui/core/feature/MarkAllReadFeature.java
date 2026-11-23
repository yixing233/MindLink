package wex.ui.core.feature;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;

import java.lang.reflect.Method;
import java.util.List;

import dev.mindlink.xposed.XposedBridge;
import wex.ui.core.WexStore;

/**
 * 一键已读：通过宿主数据库直查全部未读会话，再调用原生
 * updateUnreadByTalker 逐个标记（移植自 WeKit `WeConversationApi.markAllAsRead`）。
 *
 * 与旧实现的差异：不再读取会话筛选栏 adapter，分组开关关闭时也能工作；
 * 公众号 red-hint 清理与列表刷新通知保留为 MindLink 增强。
 */
public final class MarkAllReadFeature {

    private static final String TAG = "WEX: 一键已读: ";
    private static final String SQL_UNREAD =
            "SELECT username FROM rconversation"
                    + " WHERE unReadCount > 0 OR unReadMuteCount > 0 OR atCount > 0";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private MarkAllReadFeature() {
    }

    public static void markAllRead(final Activity activity) {
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                toast(activity, "正在标记全部已读...");
            }
        });
        new Thread(new Runnable() {
            @Override
            public void run() {
                int[] result = markAllReadInternal();
                MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        int marked = result[0];
                        int total = result[1];
                        if (total == 0) {
                            toast(activity, "没有待标记的会话");
                        } else if (marked == total) {
                            toast(activity, "已标记 " + marked + " 个会话为已读");
                        } else {
                            toast(activity, "已标记 " + marked + "/" + total + " 个会话");
                        }
                    }
                });
            }
        }, "MindLink-mark-all-read").start();
    }

    /** 返回 [成功数, 总数]，供 JVM 测试复用查询/标记流程。 */
    static int[] markAllReadInternal() {
        List<String> talkers;
        try {
            talkers = HostDatabaseBridge.queryStrings(SQL_UNREAD, null);
        } catch (Throwable error) {
            log("查询未读失败 " + error.getClass().getSimpleName() + ": " + error.getMessage());
            return new int[]{0, 0};
        }
        int ok = 0;
        for (String talker : talkers) {
            if (ConversationStorageBridge.markRead(talker)) {
                ok++;
            }
        }
        if (ok > 0) {
            ConversationStorageBridge.notifyChanged();
        }
        log("已标记 " + ok + "/" + talkers.size());
        return new int[]{ok, talkers.size()};
    }

    private static void toast(final Activity activity, final String message) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
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
