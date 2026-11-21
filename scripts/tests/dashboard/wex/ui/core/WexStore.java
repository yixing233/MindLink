package wex.ui.core;

/** Desktop tests fail on any accidental entry into the host store. */
public final class WexStore {
    public static final WexStore INSTANCE = new WexStore();
    public static int calls;
    public boolean getBool(String key, boolean fallback) { calls++; throw new AssertionError("host store used on desktop"); }
    public void logStatus(String value) { calls++; throw new AssertionError("host log written on desktop"); }
}
