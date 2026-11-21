package android.content;
import android.content.pm.PackageManager;
public class Context {
    public static final int MODE_PRIVATE = 0;
    public static String preferenceAccount = "wxid_A";
    public Context getApplicationContext() { return this; }
    public String getPackageName() { return "com.tencent.mm"; }
    public PackageManager getPackageManager() { return new PackageManager(); }
    public SharedPreferences getSharedPreferences(String name, int mode) {
        return (key, fallback) -> preferenceAccount;
    }
}
