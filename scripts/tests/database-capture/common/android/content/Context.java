package android.content;

/** No preference values are needed by either source under test. */
public class Context {
    public static int preferenceReads;
    public ClassLoader loader;
    public ClassLoader getClassLoader() { return loader; }
    public Context getApplicationContext() { return null; }
    public Object getSharedPreferences(String name, int mode) {
        preferenceReads++;
        throw new AssertionError("Preferences must not be inspected by capture/bootstrap");
    }
}
