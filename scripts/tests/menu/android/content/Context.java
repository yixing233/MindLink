package android.content;

import android.content.res.Resources;

public class Context {
    public static final String CLIPBOARD_SERVICE = "clipboard";
    private final Resources resources = new Resources();
    private final android.content.pm.PackageManager packages = new android.content.pm.PackageManager();
    private final ClipboardManager clipboard = new ClipboardManager();

    public Resources getResources() { return resources; }
    public android.content.res.AssetManager getAssets() { return new android.content.res.AssetManager(); }
    public android.content.pm.PackageManager getPackageManager() { return packages; }
    public String getPackageName() { return "dev.mindlink"; }
    public android.content.pm.ApplicationInfo getApplicationInfo() { return new android.content.pm.ApplicationInfo(); }
    public Object getSystemService(String name) { return CLIPBOARD_SERVICE.equals(name) ? clipboard : null; }
}
