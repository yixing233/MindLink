package android.content.pm;

public class PackageInfo {
    public String versionName;
    public long versionCode;
    public PackageInfo(String name, long code) { versionName = name; versionCode = code; }
    public long getLongVersionCode() { return versionCode; }
}
