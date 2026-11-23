package dev.mindlink.xposed;

import android.app.Application;
import android.content.pm.ApplicationInfo;

import dev.mindlink.xposed.callbacks.XC_LoadPackage;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

/**
 * libxposed 入口（META-INF/xposed/java_init.list），参照 WeKit LxpHookEntry。
 * 只转接到现有的 wex.ui.core.xposed.HookEntry，不改变功能代码的启动顺序。
 */
public final class ModuleEntry extends XposedModule {
    static final String HOST_PACKAGE = "com.tencent.mm";
    static final String HOOK_ENTRY = "wex.ui.core.xposed.HookEntry";

    private Object entry;

    public ModuleEntry() {
        super();
    }

    @Override
    public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        XposedBridge.attach(this);
        try {
            Object hookEntry = Class.forName(HOOK_ENTRY, true, ModuleEntry.class.getClassLoader())
                    .getDeclaredConstructor().newInstance();
            if (hookEntry instanceof IXposedHookZygoteInit) {
                IXposedHookZygoteInit.StartupParam startup = new IXposedHookZygoteInit.StartupParam();
                ApplicationInfo info = getModuleApplicationInfo();
                startup.modulePath = info == null ? null : info.sourceDir;
                ((IXposedHookZygoteInit) hookEntry).initZygote(startup);
            }
            entry = hookEntry;
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        if (!(entry instanceof IXposedHookLoadPackage)) return;
        try {
            String packageName = (String) read(param, "getPackageName");
            if (!HOST_PACKAGE.equals(packageName) || !Boolean.TRUE.equals(read(param, "isFirstPackage"))) return;
            XC_LoadPackage.LoadPackageParam load = new XC_LoadPackage.LoadPackageParam();
            load.packageName = packageName;
            load.classLoader = (ClassLoader) read(param, "getClassLoader");
            load.appInfo = (ApplicationInfo) read(param, "getApplicationInfo");
            String process = Application.getProcessName();
            load.processName = process != null ? process : packageName;
            load.isFirstApplication = true;
            ((IXposedHookLoadPackage) entry).handleLoadPackage(load);
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    /** 经公开接口类型反射取值，不依赖框架实现类，也不需要猜测参数接口的完整签名。 */
    private static Object read(XposedModuleInterface.PackageReadyParam param, String getter) throws ReflectiveOperationException {
        return XposedModuleInterface.PackageReadyParam.class.getMethod(getter).invoke(param);
    }
}
