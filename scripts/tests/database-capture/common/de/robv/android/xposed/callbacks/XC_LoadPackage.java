package de.robv.android.xposed.callbacks;

public final class XC_LoadPackage {
    public static final class LoadPackageParam {
        public ClassLoader classLoader;
        public android.content.pm.ApplicationInfo appInfo;
        public String packageName = "com.tencent.mm";
        public String processName = "com.tencent.mm";
    }
}
