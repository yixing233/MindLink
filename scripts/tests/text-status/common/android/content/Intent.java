package android.content;
import android.content.pm.PackageManager;
public class Intent {
    public Intent setClassName(String packageName, String name) { return this; }
    public Intent putExtra(String key, boolean value) { return this; }
    public Object resolveActivity(PackageManager manager) { return new Object(); }
}
