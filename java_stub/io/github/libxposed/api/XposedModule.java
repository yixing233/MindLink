package io.github.libxposed.api;

import android.content.pm.ApplicationInfo;

/** 编译期桩，不打包。运行期由 LSPosed 提供真实实现。 */
public abstract class XposedModule implements XposedModuleInterface {
    public XposedModule() {
    }

    public int getApiVersion() {
        throw new UnsupportedOperationException("stub");
    }

    public ApplicationInfo getModuleApplicationInfo() {
        throw new UnsupportedOperationException("stub");
    }

    public void log(int priority, String tag, String msg, Throwable tr) {
        throw new UnsupportedOperationException("stub");
    }
}
