package android.os;

/** Explicit thread identity for the isolated database suite. */
public final class Looper {
    private static final Looper MAIN = new Looper();
    private static final Looper WORKER = new Looper();
    private static final ThreadLocal<Looper> CURRENT = ThreadLocal.withInitial(() -> WORKER);
    public static Looper myLooper() { return CURRENT.get(); }
    public static Looper getMainLooper() { return MAIN; }
    public static void worker(Runnable task) {
        Looper previous = CURRENT.get();
        CURRENT.set(WORKER);
        try { task.run(); } finally { CURRENT.set(previous); }
    }
}
