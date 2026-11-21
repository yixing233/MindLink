package android.content;

public class Context {
    public Context getApplicationContext() { return this; }
    public ClassLoader getClassLoader() { return Context.class.getClassLoader(); }
    public android.content.pm.PackageManager getPackageManager() {
        return new android.content.pm.PackageManager();
    }
    public android.content.pm.ApplicationInfo getApplicationInfo() {
        return new android.content.pm.ApplicationInfo();
    }
}
