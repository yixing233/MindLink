package io.github.libxposed.api;

/** 编译期桩，不打包。参数对象的取值经反射读取。 */
public interface XposedModuleInterface {
    interface ModuleLoadedParam {
    }

    interface PackageReadyParam {
    }

    default void onModuleLoaded(ModuleLoadedParam param) {
    }

    default void onPackageReady(PackageReadyParam param) {
    }
}
