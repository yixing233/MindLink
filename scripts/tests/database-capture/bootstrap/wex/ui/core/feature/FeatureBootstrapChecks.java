package wex.ui.core.feature;

import android.content.Context;
import dev.mindlink.xposed.XposedBridge;
import dev.mindlink.xposed.callbacks.XC_LoadPackage;

import static wex.ui.core.feature.CheckSupport.*;

/** Compiles and calls actual FeatureBootstrap with per-feature injected failures. */
public final class FeatureBootstrapChecks {
    private static void exercise(boolean attached) {
        XC_LoadPackage.LoadPackageParam load = loadPackage();
        Context context = attached ? new Context() : null;
        ClassLoader selected = attached ? new ClassLoader(load.classLoader) { } : load.classLoader;
        if (attached) context.loader = selected;
        FeatureBootstrap.init(load, context);
        check(BootstrapDoubles.calls.equals(BootstrapDoubles.ORDER),
                "Every feature initializes once, in order, despite earlier Throwable/Error");
        check(BootstrapDoubles.arguments.get("resolver")[0] == load
                        && BootstrapDoubles.arguments.get("resolver")[1] == context,
                "Resolver init receives original host load/context");
        check(BootstrapDoubles.arguments.get("host-profile").length == 0,
                "HostProfileBridge initializer uses its no-argument host resolver context");
        check(BootstrapDoubles.calls.indexOf("resolver") < BootstrapDoubles.calls.indexOf("host-profile")
                        && BootstrapDoubles.calls.indexOf("host-profile") < BootstrapDoubles.calls.indexOf("profile"),
                "ConfigStorage hooks initialize after host resolver but before SelfProfile subscription");
        check(BootstrapDoubles.arguments.get("profile")[0] == context,
                "SelfProfile init receives original context");
        check(BootstrapDoubles.arguments.get("header")[0] == selected
                        && BootstrapDoubles.arguments.get("footer")[0] == selected
                        && BootstrapDoubles.arguments.get("moments-top-cards")[0] == selected,
                "Header/footer/Moments use selected loader, or initial fallback on load-only startup");
        check(load.classLoader == loadPackage().classLoader, "Bootstrap never mutates shared LoadPackageParam loader");
        if (attached) check(context.getApplicationContext() == null && selected != load.classLoader,
                "Attached loader differs with null application context during attach");
        int logs = XposedBridge.logs.size();
        FeatureBootstrap.init(loadPackage(), null);
        check(BootstrapDoubles.calls.size() == BootstrapDoubles.ORDER.size(),
                "Duplicate bootstrap never reruns successful or failed feature initialization");
        check(XposedBridge.logs.size() == logs, "Duplicate bootstrap does not report duplicate initialization");
        for (Throwable failure : BootstrapDoubles.failures.values())
            check(XposedBridge.logs.stream().anyMatch(text -> text.contains("初始化失败 " + failure.getClass().getSimpleName())),
                    "Failure is recorded by class only, without reading private message");
        check(XposedBridge.logs.stream().anyMatch(text -> text.contains("悬浮输入栏")),
                "Final feature is reached after early service initialization failure");
    }

    public static void main(String[] args) {
        switch (args[0]) {
            case "success": break;
            case "attached-loader": break;
            case "resolver-throwable": BootstrapDoubles.failures.put("resolver", new BareThrowable()); break;
            case "resolver-error": BootstrapDoubles.failures.put("resolver", new HostError()); break;
            case "host-profile-throwable": BootstrapDoubles.failures.put("host-profile", new BareThrowable()); break;
            case "host-profile-error": BootstrapDoubles.failures.put("host-profile", new HostError()); break;
            case "profile-throwable": BootstrapDoubles.failures.put("profile", new BareThrowable()); break;
            case "profile-error": BootstrapDoubles.failures.put("profile", new HostError()); break;
            case "both-fail":
                BootstrapDoubles.failures.put("resolver", new BareThrowable());
                BootstrapDoubles.failures.put("host-profile", new BareThrowable());
                BootstrapDoubles.failures.put("profile", new HostError());
                break;
            case "each-feature-fails":
                for (int i = 0; i < BootstrapDoubles.ORDER.size(); i++)
                    BootstrapDoubles.failures.put(BootstrapDoubles.ORDER.get(i),
                            i % 2 == 0 ? new BareThrowable() : new HostError());
                break;
            case "logger-fails":
                BootstrapDoubles.failures.put("resolver", new BareThrowable());
                BootstrapDoubles.failures.put("profile", new HostError());
                XposedBridge.logFailure = new BareThrowable();
                break;
            default: throw new AssertionError("Unknown case " + args[0]);
        }
        exercise("attached-loader".equals(args[0]));
        passed("bootstrap", args[0]);
    }
}
