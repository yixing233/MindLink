package android.os;

/** MAIN is the test entry thread; workers are real joined threads, never inline callbacks. */
public final class Looper {
    private static final Thread MAIN_THREAD = Thread.currentThread();
    private static final Looper MAIN = new Looper();
    private Looper() { }
    public static Looper getMainLooper() { return MAIN; }
    public static Looper myLooper() { return Thread.currentThread() == MAIN_THREAD ? MAIN : null; }
}
