package android.content.pm;

public class PackageManager {
    public final java.util.Map<String, PackageInfo> packages = new java.util.HashMap<>();
    public PackageManager() { packages.put("dev.mindlink", new PackageInfo("1.0.0", 1)); }
    public PackageInfo getPackageInfo(String name, int flags) throws NameNotFoundException {
        PackageInfo info = packages.get(name);
        if (info == null) throw new NameNotFoundException();
        return info;
    }
    public android.content.Intent getLaunchIntentForPackage(String name) {
        return packages.containsKey(name) ? new android.content.Intent(name) : null;
    }
    public android.graphics.drawable.Drawable getApplicationIcon(String name) throws NameNotFoundException { getPackageInfo(name,0);return new android.graphics.drawable.ColorDrawable(0xFF2C6E49); }
    public static class NameNotFoundException extends Exception {}
}
