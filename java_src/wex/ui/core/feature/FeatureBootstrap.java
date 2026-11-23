package wex.ui.core.feature;

import android.content.Context;

import dev.mindlink.xposed.XposedBridge;
import dev.mindlink.xposed.callbacks.XC_LoadPackage;

/** Single Java entry for features that share host services and lifecycle state. */
public final class FeatureBootstrap {
    private interface Initializer {
        void run() throws Throwable;
    }

    private static final String TAG = "WEX: 新功能启动: ";
    private static boolean initialized;

    private FeatureBootstrap() {
    }

    public static synchronized void init(final XC_LoadPackage.LoadPackageParam lpparam,
                                         final Context context) {
        if (initialized) {
            return;
        }
        initialized = true;
        start("宿主目标解析", new Initializer() {
            @Override public void run() { HostTargetResolver.initialize(lpparam, context); }
        });
        start("本人配置存储", new Initializer() {
            @Override public void run() { HostProfileBridge.initialize(); }
        });
        start("本人资料", new Initializer() {
            @Override public void run() { SelfProfile.initialize(context); }
        });
        start("系统栏适配", new Initializer() {
            @Override public void run() { SystemBarsCoordinator.init(); }
        });
        start("数据库桥", new Initializer() {
            @Override
            public void run() {
                HostDatabaseBridge.initialize(lpparam, context);
            }
        });
        start("微信状态服务", new Initializer() {
            @Override public void run() { TextStatusBridge.init(); }
        });
        start("会话存储桥", new Initializer() {
            @Override
            public void run() {
                ConversationStorageBridge.initialize(lpparam, context);
            }
        });
        start("会话列表桥", new Initializer() {
            @Override
            public void run() {
                ConversationListBridge.initialize(lpparam);
            }
        });
        start("全局背景", new Initializer() {
            @Override
            public void run() {
                GlobalBackgroundFeature.init();
            }
        });
        start("首页底栏", new Initializer() {
            @Override
            public void run() {
                NavigationBarFeature.init(lpparam);
            }
        });
        start("首页侧栏", new Initializer() {
            @Override
            public void run() {
                HomeSidePanelFeature.init();
            }
        });
        start("悬浮顶栏", new Initializer() {
            @Override
            public void run() throws Throwable {
                FloatingChatHeaderFeature.init(selectedHostLoader(lpparam));
            }
        });
        start("悬浮输入栏", new Initializer() {
            @Override
            public void run() throws Throwable {
                FloatingChatFooterFeature.init(selectedHostLoader(lpparam));
            }
        });
        start("首页顶栏卡片", new Initializer() {
            @Override
            public void run() {
                HomeTopBarCards.init();
            }
        });
    }

    private static ClassLoader selectedHostLoader(XC_LoadPackage.LoadPackageParam load) {
        ClassLoader selected = HostTargetResolver.hostClassLoader();
        return selected != null ? selected : load == null ? null : load.classLoader;
    }

    private static void start(String name, Initializer initializer) {
        try {
            initializer.run();
            log(name + " 已初始化");
        } catch (Throwable error) {
            log(name + " 初始化失败 " + error.getClass().getSimpleName());
        }
    }

    private static void log(String message) {
        try {
            XposedBridge.log(TAG + message);
        } catch (Throwable ignored) {
        }
    }
}
