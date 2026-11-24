package wex.ui.core.feature;

import android.content.Context;
import dev.mindlink.xposed.callbacks.XC_LoadPackage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Isolated init doubles; all feature call order and injected Throwables are visible. */
final class BootstrapDoubles {
    static final List<String> ORDER = List.of("resolver", "host-profile", "profile", "system-bars", "database",
            "text-status", "conversation-storage", "conversation-list", "background",
            "navigation", "side-panel", "header", "footer", "home-top-cards", "moments-top-cards", "native-top-cards");
    static final List<String> calls = new ArrayList<>();
    static final Map<String, Throwable> failures = new LinkedHashMap<>();
    static final Map<String, Object[]> arguments = new LinkedHashMap<>();
    static void call(String name, Object... args) {
        calls.add(name);
        arguments.put(name, args);
        if (failures.containsKey(name)) CheckSupport.<RuntimeException>raise(failures.get(name));
    }
}
final class HostTargetResolver {
    private static ClassLoader selected;
    public static ClassLoader hostClassLoader() { return selected; }
    public static void initialize(XC_LoadPackage.LoadPackageParam load, Context context) {
        selected = context != null && context.getClassLoader() != null
                ? context.getClassLoader() : load == null ? null : load.classLoader;
        BootstrapDoubles.call("resolver", load, context);
    }
}
final class HostProfileBridge {
    public static void initialize() { BootstrapDoubles.call("host-profile"); }
}
final class SelfProfile {
    public static void initialize(Context context) { BootstrapDoubles.call("profile", context); }
}
final class SystemBarsCoordinator {
    public static void init() { BootstrapDoubles.call("system-bars"); }
}
final class HostDatabaseBridge {
    public static void initialize(XC_LoadPackage.LoadPackageParam load, Context context) {
        BootstrapDoubles.call("database", load, context);
    }
}
final class TextStatusBridge {
    public static void init() { BootstrapDoubles.call("text-status"); }
}
final class ConversationStorageBridge {
    public static void initialize(XC_LoadPackage.LoadPackageParam load, Context context) {
        BootstrapDoubles.call("conversation-storage", load, context);
    }
}
final class ConversationListBridge {
    public static void initialize(XC_LoadPackage.LoadPackageParam load) { BootstrapDoubles.call("conversation-list", load); }
}
final class GlobalBackgroundFeature {
    public static void init() { BootstrapDoubles.call("background"); }
}
final class NavigationBarFeature {
    public static void init(XC_LoadPackage.LoadPackageParam load) { BootstrapDoubles.call("navigation", load); }
}
final class HomeSidePanelFeature {
    public static void init() { BootstrapDoubles.call("side-panel"); }
}
final class FloatingChatHeaderFeature {
    public static void init(ClassLoader loader) throws Throwable { BootstrapDoubles.call("header", loader); }
}
final class FloatingChatFooterFeature {
    public static void init(ClassLoader loader) throws Throwable { BootstrapDoubles.call("footer", loader); }
}
final class HomeTopBarCards {
    static void init() { BootstrapDoubles.call("home-top-cards"); }
}
final class MomentsTopBarCards {
    static void init(ClassLoader loader) { BootstrapDoubles.call("moments-top-cards", loader); }
}
final class NativeTopBarCards {
    static void init() { BootstrapDoubles.call("native-top-cards"); }
}
