package android.os;

/** JVM worker identity; the real resolver executor is exercised by this suite. */
public final class Looper {
    private static final Looper MAIN = new Looper();
    public static Looper getMainLooper() { return MAIN; }
    public static Looper myLooper() { return null; }
}
